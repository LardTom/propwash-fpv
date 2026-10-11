package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.BuildAccess;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.CameraView;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PaintCost;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DronePresets;
import de.lardtom.propwash.content.ImportService;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.CraftNames;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.BuildWarning;
import de.lardtom.propwash.sim.CameraOcclusion;
import de.lardtom.propwash.sim.FpvRig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public final class BuildAccessImpl implements BuildAccess {
    public static final BuildAccessImpl INSTANCE = new BuildAccessImpl();
    public static final String ACCESSORY_SLOT_ISSUE = "accessory_mount";

    private BuildAccessImpl() {
    }

    @Override
    public boolean isDrone(ItemStack stack) {
        return stack != null && !stack.isEmpty() && DroneAssembly.isDrone(stack);
    }

    @Override
    public Optional<DroneData> read(ItemStack stack) {
        if (!this.isDrone(stack)) {
            return Optional.empty();
        }
        DroneBuild build = DroneAssembly.buildOf(stack);
        FlightTune tune = stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
        String name = CraftNames.of(stack.get(DataComponents.CUSTOM_NAME));
        return Optional.of(new DroneData(Conversions.spec(build), Conversions.paint(PaintItems.paint(stack)), Conversions.tune(tune),
                name.isEmpty() ? Optional.empty() : Optional.of(name)));
    }

    @Override
    public ItemStack createDrone(DroneData data) {
        return this.createDrone(data, WorldRules.partCompatibility());
    }

    @Override
    public boolean partCompatibility() {
        return WorldRules.partCompatibility().enforced();
    }

    public ItemStack createDrone(DroneData data, PartCompatibility rule) {
        Objects.requireNonNull(data, "data");
        DroneBuild build = requireBuild(data.build());
        List<String> problems = issues(build.problems(rule));
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Bauplan ist nicht baubar: " + problems);
        }
        ItemStack drone = DroneAssembly.preset(build);
        if (drone.isEmpty()) {
            throw new IllegalStateException("Das Drohnen-Item von Propwash ist nicht angemeldet");
        }
        storeTune(drone, DronePiloting.sanitize(FlightTune.EMPTY, Conversions.tune(data.tune()), build));
        PaintItems.store(drone, Conversions.paint(data.paint()));
        String name = data.name().map(CraftNames::clean).orElse("");
        if (!name.isEmpty()) {
            drone.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        return drone;
    }

    @Override
    public ItemStack withTune(ItemStack drone, TuneSpec tune) {
        requireDrone(drone);
        Objects.requireNonNull(tune, "tune");
        ItemStack copy = drone.copy();
        FlightTune stored = copy.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
        storeTune(copy, DronePiloting.sanitize(stored, Conversions.tune(tune), DroneAssembly.buildOf(copy)));
        return copy;
    }

    @Override
    public ItemStack withPaint(ItemStack drone, PaintSpec paint) {
        requireDrone(drone);
        Objects.requireNonNull(paint, "paint");
        ItemStack copy = drone.copy();
        PaintItems.store(copy, Conversions.paint(paint));
        return copy;
    }

    @Override
    public BuildCheck check(BuildSpec spec) {
        Objects.requireNonNull(spec, "spec");
        List<Identifier> unknown = Conversions.unknownParts(spec);
        if (!unknown.isEmpty()) {
            return new BuildCheck(spec, unknown, List.of(), List.of());
        }
        Optional<DroneBuild> build = Conversions.build(spec);
        if (build.isEmpty()) {
            return new BuildCheck(spec, List.of(), List.of(ACCESSORY_SLOT_ISSUE), List.of());
        }
        return check(spec, build.get(), WorldRules.partCompatibility());
    }

    public static BuildCheck check(BuildSpec spec, DroneBuild build, PartCompatibility rule) {
        List<DroneBuild.Issue> warnings = new ArrayList<>(build.liftedProblems(rule));
        warnings.addAll(build.warnings());
        return new BuildCheck(spec, List.of(), issues(build.problems(rule)), issues(warnings));
    }

    @Override
    public Optional<BuildStats> analyze(BuildSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return Conversions.build(spec).map(BuildAccessImpl::stats);
    }

    @Override
    public Optional<CameraView> cameraView(BuildSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return Conversions.build(spec).map(BuildAccessImpl::cameraView);
    }

    @Override
    public double footprintMm(BuildSpec spec) {
        return Footprints.of(FpvRig.of(requireBuild(spec)));
    }

    @Override
    public Optional<CameraView> cameraView(BuildSpec spec, double uptiltDeg) {
        Objects.requireNonNull(spec, "spec");
        return Conversions.build(spec).map(build -> cameraView(build, uptiltDeg));
    }

    @Override
    public Optional<CameraView.UptiltRange> uptiltRange(Identifier frame) {
        Objects.requireNonNull(frame, "frame");
        return FrameType.byId(frame).filter(type -> !type.missing()).map(type -> {
            CameraMount.Range range = CameraMount.range(type);
            return new CameraView.UptiltRange(range.minDeg(), range.maxDeg(), range.defaultDeg());
        });
    }

    public static CameraView cameraView(DroneBuild build) {
        FpvRig rig = FpvRig.of(build);
        return cameraView(build, rig, rig.tiltDeg(), BuildAnalysis.of(build).propsInViewPercent());
    }

    public static CameraView cameraView(DroneBuild build, double uptiltDeg) {
        FpvRig rig = FpvRig.of(build);
        CameraMount.Range range = CameraMount.range(build.frame());
        float tilt = range.clamp((float) uptiltDeg);
        if (tilt == (float) rig.tiltDeg()) {
            return cameraView(build);
        }
        return cameraView(build, rig, tilt, CameraOcclusion.percent(rig, tilt, rig.horizontalFovDeg(), FpvRig.ASPECT, CameraOcclusion.ROWS));
    }

    private static CameraView cameraView(DroneBuild build, FpvRig rig, double tiltDeg, double percent) {
        List<CameraView.PropDisc> props = new ArrayList<>();
        for (int slot = 0; slot < FpvRig.SLOTS; slot++) {
            double[] center = rig.discCenterMm(slot);
            props.add(new CameraView.PropDisc(new Vec3Mm(center[0], center[1], center[2]), rig.radiusMm(), rig.hubRadiusMm()));
        }
        double[] lens = rig.lensMm();
        CameraMount.Range range = CameraMount.range(build.frame());
        return new CameraView(new Vec3Mm(lens[0], lens[1], lens[2]), tiltDeg, rig.horizontalFovDeg(), FpvRig.ASPECT, props, percent,
                new CameraView.UptiltRange(range.minDeg(), range.maxDeg(), range.defaultDeg()));
    }

    @Override
    public TuneSpec defaultTune(BuildSpec spec) {
        DroneBuild build = requireBuild(spec);
        Map<String, Float> values = TuneMapping.defaults(AirframeSpec.of(build));
        values.put(TuneMapping.CAMERA_UPTILT, CameraMount.defaultUptilt(build.frame()));
        values.putAll(LedSettings.DEFAULT.values());
        return new TuneSpec(values);
    }

    @Override
    public List<DronePreset> presets() {
        List<DronePreset> out = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            String key = DronePresets.key(build).orElse(DronePresets.CUSTOM);
            out.add(DronePreset.of(Identifier.fromNamespaceAndPath(Constants.MOD_ID, key), DroneData.of(Conversions.spec(build))));
        }
        out.addAll(ApiExtensions.registeredPresets());
        return List.copyOf(out);
    }

    @Override
    public DronePreset registerPreset(DronePreset preset) {
        return ApiExtensions.registerPreset(preset);
    }

    @Override
    public ImportResult applyImport(ServerPlayer player, ImportRequest request) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        ImportResult result;
        if (player.isSpectator()) {
            result = ImportResult.of(ImportStatus.NOT_ALLOWED);
        } else if (!Conversions.unknownParts(request.data().build()).isEmpty()) {
            result = new ImportResult(ImportStatus.UNKNOWN_PARTS, null, Conversions.unknownParts(request.data().build()), null);
        } else {
            try {
                result = Objects.requireNonNullElse(ImportService.apply(player, request), ImportResult.of(ImportStatus.INVALID));
            } catch (RuntimeException e) {
                Constants.LOG.error("Import für {} ist fehlgeschlagen", player.getGameProfile().name(), e);
                result = ImportResult.of(ImportStatus.INVALID);
            }
        }
        return imported(player, request, result);
    }

    @Override
    public OptionalDouble partCondition(ItemStack drone, PartKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (!this.isDrone(drone)) {
            return OptionalDouble.empty();
        }
        PartWear wear = drone.getOrDefault(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE);
        return switch (kind) {
            case FRAME -> OptionalDouble.of(wear.frame());
            case STACK -> OptionalDouble.of(wear.stack());
            case MOTOR -> OptionalDouble.of(wear.worstMotor());
            case PROP -> OptionalDouble.of(wear.worstProp());
            case VIDEO -> OptionalDouble.of(wear.video());
            case BATTERY -> OptionalDouble.of(wear.battery());
            case ACCESSORY -> OptionalDouble.empty();
        };
    }

    @Override
    public Map<String, Integer> paintCost(ItemStack drone, PaintSpec target) {
        Objects.requireNonNull(drone, "drone");
        Objects.requireNonNull(target, "target");
        return costMap(PaintCost.of(PaintItems.paint(drone), Conversions.paint(target)));
    }

    public static Map<String, Integer> costMap(PaintCost cost) {
        Map<String, Integer> out = new LinkedHashMap<>(cost.dyes());
        if (!cost.custom().isEmpty()) {
            out.put(ImportService.CUSTOM_DYES, cost.custom().size());
        }
        return Collections.unmodifiableMap(out);
    }

    public static ImportResult imported(ServerPlayer player, ImportRequest request, ImportResult result) {
        PropwashEvents.BUILD_IMPORTED.fire(listener -> {
            try {
                listener.onBuildImported(player, request, result);
            } catch (RuntimeException e) {
                Constants.LOG.error("Listener für {} ist fehlgeschlagen", PropwashEvents.BUILD_IMPORTED.name(), e);
            }
        });
        return result;
    }

    public static void escBurned(ServerPlayer player, ItemStack drone, int cells) {
        if (!PropwashEvents.ESC_BURNED.hasListeners() || drone.isEmpty()) {
            return;
        }
        Optional<DroneData> data = INSTANCE.read(drone);
        if (data.isEmpty()) {
            return;
        }
        PropwashEvents.ESC_BURNED.fire(listener -> {
            try {
                listener.onEscBurned(player, drone.copy(), data.get(), cells);
            } catch (RuntimeException e) {
                Constants.LOG.error("Listener für {} ist fehlgeschlagen", PropwashEvents.ESC_BURNED.name(), e);
            }
        });
    }

    public static DroneBuild requireBuild(BuildSpec spec) {
        Objects.requireNonNull(spec, "spec");
        List<Identifier> unknown = Conversions.unknownParts(spec);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unbekannte Teile im Bauplan: " + unknown);
        }
        return Conversions.build(spec)
                .orElseThrow(() -> new IllegalArgumentException("Zubehör passt nicht in den angegebenen Platz: " + spec.accessories()));
    }

    public static BuildStats stats(DroneBuild build) {
        return stats(BuildAnalysis.of(build), AirframeSpec.of(build));
    }

    public static BuildStats stats(BuildAnalysis analysis, AirframeSpec airframe) {
        List<String> warnings = new ArrayList<>();
        for (BuildWarning warning : BuildWarning.values()) {
            if (analysis.warnings().contains(warning)) {
                warnings.add(warning.name().toLowerCase(Locale.ROOT));
            }
        }
        return new BuildStats(analysis.massGrams(), analysis.thrustToWeight(), analysis.hoverThrottlePercent(), analysis.hoverFlightTimeMin(),
                analysis.cruiseFlightTimeMin(), analysis.topSpeedAcroKmh(), analysis.fullThrottleCurrentA(), analysis.escLoadPercent(),
                analysis.batteryLoadPercent(), analysis.motorResponseMs(), analysis.crashSpeedMs(), analysis.sustainedMotorTempC(),
                analysis.propsInViewPercent(), warnings, analysis.mixedFlightTimeMin(), analysis.aggressiveFlightTimeMin(),
                analysis.hoverCurrentA(), analysis.cruiseCurrentA(), analysis.mixedCurrentA(), analysis.cruiseSpeedKmh(),
                analysis.landingCellVoltage(), analysis.avionicsPowerW(), analysis.motorTempMinuteC(), analysis.mixedMotorTempC(),
                airframe.propwashNoiseFactor(), analysis.fullThrottleCellVoltage(), airframe.authorityRoll, airframe.authorityPitch,
                airframe.authorityYawSteady);
    }

    private static List<String> issues(List<DroneBuild.Issue> issues) {
        List<String> out = new ArrayList<>(issues.size());
        for (DroneBuild.Issue issue : issues) {
            out.add(issue.name().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(out);
    }

    private void requireDrone(ItemStack drone) {
        if (!this.isDrone(drone)) {
            throw new IllegalArgumentException("Kein Propwash-Drohnen-Item: " + drone);
        }
    }

    private static void storeTune(ItemStack drone, FlightTune tune) {
        if (tune.values().isEmpty()) {
            drone.remove(CoreComponents.FLIGHT_TUNE.get());
        } else {
            drone.set(CoreComponents.FLIGHT_TUNE.get(), tune);
        }
    }
}

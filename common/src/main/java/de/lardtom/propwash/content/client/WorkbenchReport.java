package de.lardtom.propwash.content.client;

import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.BuildTexts;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.CameraOcclusion;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.sim.BuildWarning;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.FpvLens;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class WorkbenchReport {
    public static final int COLOR_LABEL = Palette.TEXT_MUTED;
    public static final int COLOR_VALUE = Palette.TEXT;
    public static final int COLOR_ACCENT = Palette.ACCENT;
    public static final int COLOR_GOOD = Palette.SUCCESS;
    public static final int COLOR_WARN = Palette.WARNING;
    public static final int COLOR_BAD = Palette.ERROR;
    public static final int COLOR_NOTE = Palette.ACCENT;

    private static final String KEY = "propwash.content.workbench.";
    public static final List<String> STAT_KEYS = List.of("dry_weight", "takeoff_weight", "thrust_to_weight", "hover_throttle", "top_speed",
            "flight_time");

    private final BuildSlots.Check check;
    private final PartWear wear;
    private final PartCompatibility rule;
    private final @Nullable DroneBuild build;
    private final @Nullable BuildAnalysis analysis;
    private final List<Line> issues = new ArrayList<>();
    private final Map<PartCategory, Integer> highlights = new EnumMap<>(PartCategory.class);
    private double boxedProps = Double.NaN;
    private double fisheyeProps = Double.NaN;
    private int fisheyePropsStrength = -1;

    private WorkbenchReport(BuildSlots.Check check, PartWear wear, boolean droneItemAvailable, PartCompatibility rule) {
        this.check = check;
        this.wear = wear;
        this.rule = rule;
        this.build = check.build().orElse(null);
        this.analysis = this.build == null ? null : analyse(this.build);
        this.collectIssues(droneItemAvailable);
        this.collectWear();
    }

    private double boxedPropsInView(DroneBuild build) {
        if (Double.isNaN(this.boxedProps)) {
            FpvRig rig = FpvRig.of(build);
            this.boxedProps = CameraOcclusion.percent(rig, rig.tiltDeg(), rig.horizontalFovDeg(), ImageFormat.FOUR_THREE_ASPECT, CameraOcclusion.ROWS);
        }
        return this.boxedProps;
    }

    private double fisheyePropsInView(DroneBuild build, boolean boxed, int strength) {
        if (Double.isNaN(this.fisheyeProps) || this.fisheyePropsStrength != strength) {
            FpvRig rig = FpvRig.of(build);
            this.fisheyeProps = CameraOcclusion.fisheyePercent(rig, rig.tiltDeg(), rig.horizontalFovDeg(), boxed ? ImageFormat.FOUR_THREE_ASPECT : FpvRig.ASPECT,
                    CameraOcclusion.FISHEYE_ROWS, strength / 100.0);
            this.fisheyePropsStrength = strength;
        }
        return this.fisheyeProps;
    }

    public static WorkbenchReport of(BuildSlots.Check check) {
        return new WorkbenchReport(check, PartWear.PRISTINE, DroneAssembly.droneItemAvailable(), PartCompatibility.ENFORCED);
    }

    public static WorkbenchReport of(BuildSlots.Check check, PartWear wear) {
        return new WorkbenchReport(check, wear, DroneAssembly.droneItemAvailable(), PartCompatibility.ENFORCED);
    }

    public static WorkbenchReport of(BuildSlots.Check check, PartWear wear, PartCompatibility rule) {
        return new WorkbenchReport(check, wear, DroneAssembly.droneItemAvailable(), rule);
    }

    public static WorkbenchReport of(BuildSlots.Check check, boolean droneItemAvailable) {
        return new WorkbenchReport(check, PartWear.PRISTINE, droneItemAvailable, PartCompatibility.ENFORCED);
    }

    public static WorkbenchReport of(BuildSlots.Check check, boolean droneItemAvailable, PartCompatibility rule) {
        return new WorkbenchReport(check, PartWear.PRISTINE, droneItemAvailable, rule);
    }

    public static WorkbenchReport of(BuildSlots.Check check, PartWear wear, boolean droneItemAvailable) {
        return new WorkbenchReport(check, wear, droneItemAvailable, PartCompatibility.ENFORCED);
    }

    public PartCompatibility rule() {
        return this.rule;
    }

    public boolean buildable() {
        return this.check.buildable(this.rule);
    }

    public List<DroneBuild.Issue> liftedProblems() {
        DroneBuild build = this.build;
        return build == null ? List.of() : build.liftedProblems(this.rule);
    }

    public PartWear wear() {
        return this.wear;
    }

    private void collectWear() {
        if (this.wear.isNew()) {
            return;
        }
        for (PartCategory category : PartCategory.values()) {
            float value = PartCondition.worst(this.wear, category);
            if (!PartWear.damaged(value)) {
                continue;
            }
            int color = PartWear.critical(value) ? COLOR_BAD : COLOR_WARN;
            this.issues.add(new Line(Component.translatable(KEY + "wear", PartCondition.categoryName(category),
                    PartCondition.value(value).getString()), color));
            this.mark(category, color);
        }
    }

    private static @Nullable BuildAnalysis analyse(DroneBuild build) {
        try {
            return BuildAnalysis.of(build);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public BuildSlots.Check check() {
        return this.check;
    }

    public @Nullable DroneBuild build() {
        return this.build;
    }

    public @Nullable BuildAnalysis analysis() {
        return this.analysis;
    }

    public List<Line> issues() {
        return this.issues;
    }

    public int highlight(PartCategory category) {
        return this.highlights.getOrDefault(category, 0);
    }

    private void collectIssues(boolean droneItemAvailable) {
        for (PartCategory category : this.check.mixed()) {
            this.issues.add(new Line(Component.translatable(KEY + "mixed." + category.key()), COLOR_BAD));
            this.mark(category, COLOR_BAD);
        }
        if (this.build == null) {
            return;
        }
        if (!droneItemAvailable) {
            this.issues.add(new Line(Component.translatable(KEY + "no_drone_item"), COLOR_BAD));
        }
        for (DroneBuild.Issue issue : this.build.problems(this.rule)) {
            this.issues.add(new Line(Component.translatable(issue.translationKey()), COLOR_BAD));
            for (PartCategory category : BuildSlots.affectedBy(issue)) {
                this.mark(category, COLOR_BAD);
            }
        }
        List<DroneBuild.Issue> lifted = this.build.liftedProblems(this.rule);
        if (!lifted.isEmpty()) {
            this.issues.add(new Line(Component.translatable(KEY + "lifted_header"), COLOR_WARN, true));
        }
        for (DroneBuild.Issue issue : lifted) {
            this.issues.add(new Line(Component.translatable(issue.translationKey()), COLOR_WARN));
            for (PartCategory category : BuildSlots.affectedBy(issue)) {
                this.mark(category, COLOR_WARN);
            }
        }
        List<DroneBuild.Issue> warnings = this.build.warnings();
        for (DroneBuild.Issue issue : warnings) {
            this.issues.add(new Line(Component.translatable(issue.translationKey()), COLOR_WARN));
            for (PartCategory category : BuildSlots.affectedBy(issue)) {
                this.mark(category, COLOR_WARN);
            }
        }
        for (BuildWarning warning : BuildTexts.analysisWarnings(this.build, this.analysis)) {
            int color = warning == BuildWarning.CANNOT_HOVER ? COLOR_BAD : COLOR_NOTE;
            this.issues.add(new Line(BuildTexts.warning(warning), color));
        }
    }

    private void mark(PartCategory category, int color) {
        Integer existing = this.highlights.get(category);
        if (existing == null || existing != COLOR_BAD) {
            this.highlights.put(category, color);
        }
    }

    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        BuildAnalysis a = this.analysis;
        if (a == null) {
            for (String key : STAT_KEYS) {
                rows.add(new Row(Component.translatable(KEY + "stat." + key), Component.translatable("propwash.content.number.none"), COLOR_LABEL));
            }
            return rows;
        }
        double dry = this.build == null ? a.massGrams() : this.build.flight().dryMassGrams();
        rows.add(new Row(label("dry_weight"), unit("grams", Numbers.of(dry, 0)), COLOR_VALUE));
        rows.add(new Row(label("takeoff_weight"), unit("grams", Numbers.of(a.massGrams(), 0)), COLOR_VALUE));
        double tw = a.thrustToWeight();
        rows.add(new Row(label("thrust_to_weight"), unit("ratio", Numbers.fixed(tw, 1)),
                tw < 1.15 ? COLOR_BAD : tw < 2.5 ? COLOR_WARN : COLOR_GOOD));
        double hover = a.hoverThrottlePercent();
        rows.add(new Row(label("hover_throttle"), Double.isFinite(hover) ? unit("percent", Numbers.of(hover, 0)) : none(),
                !Double.isFinite(hover) || hover > 75 ? COLOR_BAD : hover > 55 ? COLOR_WARN : COLOR_VALUE));
        rows.add(new Row(label("top_speed"), unit("kmh", Numbers.of(a.topSpeedAcroKmh(), 0)), COLOR_VALUE));
        double time = a.mixedFlightTimeMin();
        rows.add(new Row(label("flight_time"), Double.isFinite(time) ? unit("minutes", Numbers.minutes(time)) : none(),
                !Double.isFinite(time) ? COLOR_BAD : time < 1.5 ? COLOR_WARN : COLOR_VALUE));
        return rows;
    }

    private static Component minutes(double value) {
        return Double.isFinite(value) ? unit("minutes", Numbers.minutes(value)) : none();
    }

    private static Component amps(double value) {
        return Double.isFinite(value) ? unit("amps", Numbers.of(value, 1)) : none();
    }

    public List<Component> details() {
        List<Component> lines = new ArrayList<>();
        BuildAnalysis a = this.analysis;
        if (a == null) {
            return lines;
        }
        lines.add(Component.translatable(KEY + "details.title"));
        DroneBuild build = this.build;
        if (build != null) {
            lines.add(detail("takeoff_weight", unit("grams", Numbers.of(a.massGrams(), 0))));
            lines.add(detail("dry_weight", unit("grams", Numbers.of(build.flight().dryMassGrams(), 0)),
                    unit("grams", Numbers.of(build.flight().battery().massGrams(), 0))));
        }
        lines.add(detail("max_current", unit("amps", Numbers.of(a.fullThrottleCurrentA(), 0))));
        lines.add(detail("max_thrust", unit("grams", Numbers.of(a.maxThrustPerMotorGrams(), 0))));
        lines.add(detail("hover_time", minutes(a.hoverFlightTimeMin())));
        lines.add(detail("cruise_time", minutes(a.cruiseFlightTimeMin()),
                Double.isFinite(a.cruiseSpeedKmh()) ? unit("kmh", Numbers.of(a.cruiseSpeedKmh(), 0)) : none()));
        lines.add(detail("mixed_time", minutes(a.mixedFlightTimeMin())));
        lines.add(detail("aggressive_time", minutes(a.aggressiveFlightTimeMin())));
        lines.add(detail("currents", amps(a.hoverCurrentA()), amps(a.mixedCurrentA())));
        lines.add(detail("landing_voltage", Double.isFinite(a.landingCellVoltage()) ? unit("volts", Numbers.fixed(a.landingCellVoltage(), 2)) : none()));
        lines.add(detail("avionics_power", unit("watts", Numbers.of(a.avionicsPowerW(), 1))));
        lines.add(detail("angle_speed", unit("kmh", Numbers.of(a.topSpeedAngleKmh(), 0))));
        lines.add(detail("cell_voltage", unit("volts", Numbers.fixed(a.fullThrottleCellVoltage(), 2))));
        lines.add(detail("esc_load", unit("percent", Numbers.of(a.escLoadPercent(), 0))));
        lines.add(detail("battery_load", unit("percent", Numbers.of(a.batteryLoadPercent(), 0))));
        lines.add(detail("motor_response", unit("ms", Numbers.of(a.motorResponseMs(), 0))));
        lines.add(detail("roll_accel", unit("dps2", Numbers.of(a.rollAccelDps2(), 0))));
        lines.add(detail("crash_speed", unit("mps", Numbers.of(a.crashSpeedMs(), 1))));
        if (Double.isFinite(a.motorTempMinuteC())) {
            lines.add(detail("motor_temp", unit("celsius", Numbers.of(a.motorTempMinuteC(), 0))));
        }
        if (Double.isFinite(a.mixedMotorTempC())) {
            lines.add(detail("motor_temp_flight", unit("celsius", Numbers.of(a.mixedMotorTempC(), 0))));
        }
        if (Double.isFinite(a.propsInViewPercent()) && this.build != null) {
            boolean boxed = FpvImage.formatFor(this.build.video().system).boxed();
            int strength = FpvLens.INSTANCE.strengthPercent();
            double props = strength > 0 ? this.fisheyePropsInView(this.build, boxed, strength)
                    : boxed ? this.boxedPropsInView(this.build) : a.propsInViewPercent();
            lines.add(detail(boxed ? "props_in_view_4_3" : "props_in_view", unit("percent", Numbers.of(props, 1)),
                    unit("fov", Numbers.of(FovMath.cameraHorizontalDegrees(this.build.video()), 0)),
                    unit("fov", Numbers.of(CameraMount.defaultUptilt(this.build.frame()), 0))));
        }
        if (build != null && !build.accessories().isEmpty()) {
            this.accessoryDetails(build.accessories(), lines);
        }
        return lines;
    }

    private void accessoryDetails(Accessories accessories, List<Component> lines) {
        lines.add(detail("accessory_mass", unit("grams", Numbers.of(accessories.massGrams(), 0))));
        if (accessories.flightBatteryWatts() > 0.0) {
            lines.add(detail("accessory_power", unit("watts", Numbers.of(accessories.flightBatteryWatts(), 1))));
        }
        accessories.camera().ifPresent(type -> type.camera().ifPresent(camera -> {
            lines.add(detail("camera", Component.translatable(type.translationKey()),
                    Component.translatable(KEY + "unit.video_format", camera.maxHeight(), camera.maxFps())));
            if (camera.ownBattery()) {
                lines.add(detail("camera_record_time", unit("minutes_whole", Numbers.of(camera.recordMinutes(ActionCamSpec.FULL_HD), 0))));
            } else {
                lines.add(detail("camera_flight_battery", unit("watts", Numbers.of(camera.recordWatts(camera.maxHeight()), 1))));
            }
        }));
    }

    private static Component label(String key) {
        return Component.translatable(KEY + "stat." + key);
    }

    private static Component detail(String key, Component... values) {
        return Component.translatable(KEY + "details." + key, (Object[]) values);
    }

    private static Component unit(String unit, Component value) {
        return Component.translatable(KEY + "unit." + unit, value);
    }

    private static Component none() {
        return Component.translatable("propwash.content.number.none");
    }

    public record Row(Component label, Component value, int color) {
    }

    public record Line(Component text, int color, boolean heading) {
        public Line(Component text, int color) {
            this(text, color, false);
        }
    }
}

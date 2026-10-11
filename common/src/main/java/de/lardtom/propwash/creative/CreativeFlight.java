package de.lardtom.propwash.creative;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.parts.PartDataProblems;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BoundedCache;
import de.lardtom.propwash.video.FovMath;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CreativeFlight {
    public static final int STACK_MIN_CELLS = 1;
    public static final int STACK_MAX_CELLS = 12;
    public static final double GRAM_CM2_PER_KG_M2 = 1.0E7;
    private static final int CACHE_SIZE = 64;
    private static final BoundedCache<DroneBuild, DroneBuild> FLIGHT = new BoundedCache<>(CACHE_SIZE);
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private CreativeFlight() {
    }

    public record Base(FrameType frame, StackType stack, MotorType motor, PropType prop, BatteryType battery, VideoType video) {
        public PartInfo part(PartKind kind) {
            return switch (kind) {
                case FRAME -> this.frame;
                case STACK -> this.stack;
                case MOTOR -> this.motor;
                case PROP -> this.prop;
                case BATTERY -> this.battery;
                case VIDEO -> this.video;
                case ACCESSORY -> throw new IllegalArgumentException("Zubehör hat keine Basis");
            };
        }
    }

    public static DroneBuild flight(DroneBuild build) {
        if (build.creative().isEmpty()) {
            return build;
        }
        return FLIGHT.get(build, CreativeFlight::compute);
    }

    public static double @Nullable [] inertia(DroneBuild build) {
        CreativeSpec spec = build.creative();
        if (!spec.hasAny(CreativeParam.INERTIA)) {
            return null;
        }
        double[] out = new double[CreativeParam.INERTIA.size()];
        for (int i = 0; i < out.length; i++) {
            CreativeParam param = CreativeParam.INERTIA.get(i);
            OptionalDouble value = spec.value(param);
            out[i] = value.isPresent() ? param.clamp(value.getAsDouble()) / GRAM_CM2_PER_KG_M2 : Double.NaN;
        }
        return out;
    }

    public static Base base(DroneBuild build) {
        CreativeSpec spec = build.creative();
        return new Base(resolve(FrameType.class, PartKind.FRAME, spec, build.frame()),
                resolve(StackType.class, PartKind.STACK, spec, build.stack()),
                resolve(MotorType.class, PartKind.MOTOR, spec, build.motor()),
                resolve(PropType.class, PartKind.PROP, spec, build.prop()),
                resolve(BatteryType.class, PartKind.BATTERY, spec, build.battery()),
                build.video());
    }

    public static <T extends PartInfo> Optional<T> lookup(Class<T> type, String key) {
        return PartIndex.byKey(type, key).filter(part -> !part.missing());
    }

    private static <T extends PartInfo> T resolve(Class<T> type, PartKind kind, CreativeSpec spec, T fallback) {
        Optional<String> key = spec.base(kind);
        if (key.isEmpty()) {
            return fallback;
        }
        return lookup(type, key.get()).orElse(fallback);
    }

    public static double value(DroneBuild build, CreativeParam param) {
        OptionalDouble value = build.creative().value(param);
        return value.isPresent() ? param.clamp(value.getAsDouble()) : baseValue(build, param);
    }

    public static double baseValue(DroneBuild build, CreativeParam param) {
        Base base = base(build);
        CreativeSpec spec = build.creative();
        FrameDefinition frame = base.frame().definition();
        return switch (param) {
            case FRAME_MASS -> base.frame().massGrams();
            case MOTOR_MASS -> base.motor().massGrams();
            case PROP_MASS -> base.prop().massGrams();
            case BATTERY_MASS -> base.battery().massGrams();
            case STACK_MASS -> base.stack().massGrams();
            case VIDEO_MASS -> base.video().massGrams();
            case INERTIA_ROLL -> autoInertia(build).inertiaZz * GRAM_CM2_PER_KG_M2;
            case INERTIA_PITCH -> autoInertia(build).inertiaXx * GRAM_CM2_PER_KG_M2;
            case INERTIA_YAW -> autoInertia(build).inertiaYy * GRAM_CM2_PER_KG_M2;
            case WHEELBASE -> frame.wheelbaseMm();
            case ARM_FRONT_LEFT_FORWARD, ARM_FRONT_LEFT_RIGHT, ARM_FRONT_LEFT_UP, ARM_FRONT_RIGHT_FORWARD, ARM_FRONT_RIGHT_RIGHT,
                 ARM_FRONT_RIGHT_UP, ARM_REAR_LEFT_FORWARD, ARM_REAR_LEFT_RIGHT, ARM_REAR_LEFT_UP, ARM_REAR_RIGHT_FORWARD,
                 ARM_REAR_RIGHT_RIGHT, ARM_REAR_RIGHT_UP -> {
                int index = CreativeParam.ARMS.indexOf(param);
                yield param.clamp(autoArms(base, spec)[index / 3][index % 3]);
            }
            case PROP_DIAMETER -> base.prop().diameterMm;
            case PROP_PITCH -> base.prop().pitchInch;
            case PROP_BLADES -> base.prop().blades;
            case PROP_THRUST_COEFFICIENT -> base.prop().thrustCoefficient;
            case PROP_POWER_COEFFICIENT -> base.prop().powerCoefficient;
            case MOTOR_KV -> base.motor().kv;
            case MOTOR_RESISTANCE -> base.motor().resistanceOhm * 1000.0;
            case MOTOR_MAX_CURRENT -> base.motor().maxCurrent;
            case BATTERY_CELLS -> base.battery().cells;
            case BATTERY_CAPACITY -> base.battery().capacityMah;
            case BATTERY_RESISTANCE -> base.battery().cellResistanceOhm * 1000.0;
            case BATTERY_C_RATING -> base.battery().cRating;
            case ESC_CURRENT -> base.stack().escCurrentPerMotor;
            case FRONTAL_AREA -> frame.aero().frontalAreaM2() * 1.0E4;
            case DRAG_COEFFICIENT -> frame.aero().dragCoefficient();
            case SIDE_AREA -> frame.aero().sideAreaM2().isPresent() ? frame.aero().sideAreaM2().getAsDouble() * 1.0E4
                    : AirframeSpec.defaultSideAreaM2(frontalAreaM2(build)) * 1.0E4;
            case TOP_AREA -> frame.aero().topAreaM2().isPresent() ? frame.aero().topAreaM2().getAsDouble() * 1.0E4
                    : AirframeSpec.defaultTopAreaM2(frontalAreaM2(build), ducted(build)) * 1.0E4;
            case DUCTS -> base.frame().ducted ? 1.0 : 0.0;
            case IMPACT_TOLERANCE -> frame.impactTolerance();
            case CAMERA_FOV -> FovMath.cameraHorizontalDegrees(base.video());
            case CAMERA_FORWARD, CAMERA_RIGHT, CAMERA_UP -> param.clamp(autoCamera(build)[CreativeParam.CAMERA_POSITION.indexOf(param)]);
        };
    }

    private static double frontalAreaM2(DroneBuild build) {
        return value(build, CreativeParam.FRONTAL_AREA) / 1.0E4;
    }

    private static boolean ducted(DroneBuild build) {
        return value(build, CreativeParam.DUCTS) >= 0.5;
    }

    private static AirframeSpec autoInertia(DroneBuild build) {
        return AirframeSpec.of(build.withCreative(build.creative().without(CreativeParam.INERTIA)));
    }

    public static boolean freeArms(CreativeSpec spec) {
        return spec.hasAny(CreativeParam.ARMS);
    }

    public static double[][] autoArms(Base base, CreativeSpec spec) {
        FrameDefinition frame = base.frame().definition();
        int baseWheelbase = frame.wheelbaseMm();
        OptionalDouble wheelbaseValue = spec.value(CreativeParam.WHEELBASE);
        int wheelbase = (int) (wheelbaseValue.isPresent() ? CreativeParam.WHEELBASE.clamp(wheelbaseValue.getAsDouble()) : baseWheelbase);
        List<MotorArm> arms;
        double scale = 1.0;
        if (frame.geometry().hasExplicitArms()) {
            arms = frame.geometry().arms();
            scale = (double) wheelbase / baseWheelbase;
        } else {
            arms = MotorArm.trueX(wheelbase, 0.0);
        }
        double[][] out = new double[MotorArm.COUNT][3];
        for (int i = 0; i < MotorArm.COUNT; i++) {
            Vec3Mm position = arms.get(i).position();
            out[i][0] = -position.z() * scale;
            out[i][1] = position.x() * scale;
            out[i][2] = position.y();
        }
        return out;
    }

    private static double[] autoCamera(DroneBuild build) {
        Optional<Vec3Mm> camera = base(build).frame().definition().geometry().cameraMm();
        if (camera.isPresent()) {
            Vec3Mm c = camera.get();
            return new double[]{-c.z(), c.x(), c.y()};
        }
        AirframeSpec spec = AirframeSpec.of(build.withCreative(build.creative().without(CreativeParam.CAMERA_POSITION)));
        return new double[]{(spec.lensForward + spec.comZ) * 1000.0, (spec.lensRight - spec.comX) * 1000.0,
                (spec.lensUp + spec.comHeight) * 1000.0};
    }

    static DroneBuild compute(DroneBuild build) {
        CreativeSpec spec = build.creative();
        Base base = base(build);
        double[] camera = spec.hasAny(CreativeParam.CAMERA_POSITION) ? autoCamera(build) : null;
        FrameType frame = patched(FrameType.class, base.frame(), touches(spec, PartKind.FRAME), json -> patchFrame(json, base, spec, camera));
        OptionalDouble cellValue = spec.value(CreativeParam.BATTERY_CELLS);
        int flightCells = cellValue.isPresent() ? (int) CreativeParam.BATTERY_CELLS.clamp(cellValue.getAsDouble()) : base.battery().cells;
        StackType stack = patched(StackType.class, base.stack(), touches(spec, PartKind.STACK) || !fullCellRange(base.stack()),
                json -> patchStack(json, base.stack(), spec, flightCells));
        MotorType motor = patched(MotorType.class, base.motor(), touches(spec, PartKind.MOTOR), json -> patchMotor(json, spec));
        PropType prop = patched(PropType.class, base.prop(), touches(spec, PartKind.PROP), json -> patchProp(json, spec));
        BatteryType battery = patched(BatteryType.class, base.battery(), touches(spec, PartKind.BATTERY), json -> patchBattery(json, spec));
        VideoType video = patched(VideoType.class, base.video(), touches(spec, PartKind.VIDEO), json -> patchVideo(json, spec));
        return new DroneBuild(frame, stack, motor, prop, video, battery, build.accessories());
    }

    private static boolean fullCellRange(StackType stack) {
        return stack.minCells == STACK_MIN_CELLS && stack.maxCells == STACK_MAX_CELLS;
    }

    private static boolean touches(CreativeSpec spec, PartKind kind) {
        for (CreativeParam param : CreativeParam.values()) {
            if (param.part().orElse(null) == kind && spec.has(param)) {
                return true;
            }
        }
        return false;
    }

    private static <T extends PartInfo> T patched(Class<T> type, T part, boolean needed, Consumer<JsonObject> patcher) {
        if (!needed || part.missing()) {
            return part;
        }
        PartDefinition definition = part.definition();
        JsonObject json = PartJson.encode(definition);
        patcher.accept(json);
        PartDataProblems problems = new PartDataProblems("creative:" + part.key());
        PartDefinition result = PartJson.decode(definition.kind(), definition.id(), json, problems);
        if (result == null) {
            if (REPORTED.add(part.key() + ":" + problems.errorLines())) {
                Constants.LOG.warn("Drohnen-Editor: Werte für {} ungültig, Basisteil bleibt: {}", part.key(), problems.errorLines());
            }
            return part;
        }
        PartInfo detached = PartIndex.detached(result);
        return type.isInstance(detached) ? type.cast(detached) : part;
    }

    private static void number(JsonObject json, String field, CreativeSpec spec, CreativeParam param, double divisor) {
        OptionalDouble value = spec.value(param);
        if (value.isPresent()) {
            json.addProperty(field, param.clamp(value.getAsDouble()) / divisor);
        }
    }

    private static void integer(JsonObject json, String field, CreativeSpec spec, CreativeParam param) {
        OptionalDouble value = spec.value(param);
        if (value.isPresent()) {
            json.addProperty(field, (int) param.clamp(value.getAsDouble()));
        }
    }

    private static JsonObject object(JsonObject json, String field) {
        JsonElement element = json.get(field);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        JsonObject created = new JsonObject();
        json.add(field, created);
        return created;
    }

    private static JsonArray vector(double x, double y, double z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }

    private static void patchFrame(JsonObject json, Base base, CreativeSpec spec, double @Nullable [] autoCamera) {
        FrameDefinition frame = base.frame().definition();
        number(json, "mass_g", spec, CreativeParam.FRAME_MASS, 1.0);
        integer(json, "wheelbase_mm", spec, CreativeParam.WHEELBASE);
        boolean free = freeArms(spec);
        if (free || (frame.geometry().hasExplicitArms() && spec.has(CreativeParam.WHEELBASE))) {
            double[][] auto = autoArms(base, spec);
            JsonObject arms = new JsonObject();
            for (int i = 0; i < MotorArm.COUNT; i++) {
                double[] position = new double[3];
                for (int axis = 0; axis < 3; axis++) {
                    CreativeParam param = CreativeParam.ARMS.get(i * 3 + axis);
                    OptionalDouble value = free ? spec.value(param) : OptionalDouble.empty();
                    position[axis] = param.clamp(value.isPresent() ? value.getAsDouble() : auto[i][axis]);
                }
                MotorArm.Spin spin = frame.geometry().hasExplicitArms() ? frame.geometry().arms().get(i).spin() : MotorArm.PROPS_IN.get(i);
                JsonObject arm = new JsonObject();
                arm.add("pos", vector(position[1], position[2], -position[0]));
                arm.addProperty("spin", spin.key());
                arms.add(PartJson.ARM_KEYS.get(i), arm);
            }
            json.add("arms", arms);
        }
        JsonObject aero = object(json, "aero");
        number(aero, "frontal_area_m2", spec, CreativeParam.FRONTAL_AREA, 1.0E4);
        number(aero, "drag_coefficient", spec, CreativeParam.DRAG_COEFFICIENT, 1.0);
        number(aero, "side_area_m2", spec, CreativeParam.SIDE_AREA, 1.0E4);
        number(aero, "top_area_m2", spec, CreativeParam.TOP_AREA, 1.0E4);
        OptionalDouble ducts = spec.value(CreativeParam.DUCTS);
        if (ducts.isPresent()) {
            boolean on = CreativeParam.DUCTS.clamp(ducts.getAsDouble()) >= 0.5;
            if (on && !json.has("duct")) {
                json.addProperty("duct", true);
            } else if (!on) {
                json.remove("duct");
            }
        }
        number(json, "impact_tolerance", spec, CreativeParam.IMPACT_TOLERANCE, 1.0);
        if (autoCamera != null) {
            double[] position = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                CreativeParam param = CreativeParam.CAMERA_POSITION.get(axis);
                OptionalDouble value = spec.value(param);
                position[axis] = param.clamp(value.isPresent() ? value.getAsDouble() : autoCamera[axis]);
            }
            JsonObject camera = object(json, "camera");
            camera.add("pos", vector(position[1], position[2], -position[0]));
        }
    }

    private static void patchStack(JsonObject json, StackType stack, CreativeSpec spec, int flightCells) {
        number(json, "mass_g", spec, CreativeParam.STACK_MASS, 1.0);
        OptionalDouble current = spec.value(CreativeParam.ESC_CURRENT);
        if (current.isPresent()) {
            double continuous = CreativeParam.ESC_CURRENT.clamp(current.getAsDouble());
            json.addProperty("esc_continuous_a", continuous);
            OptionalDouble burst = stack.definition().escBurstAmps();
            if (burst.isPresent()) {
                json.addProperty("esc_burst_a", Math.max(continuous, burst.getAsDouble() * continuous / stack.escCurrentPerMotor));
            }
        }
        JsonArray cells = new JsonArray();
        cells.add(STACK_MIN_CELLS);
        cells.add(STACK_MAX_CELLS);
        json.add("cells", cells);
        double brownout = stack.definition().brownoutVoltage().orElse(AirframeSpec.defaultBrownoutVoltage(stack.minCells));
        json.addProperty("brownout_v", Math.min(brownout, AirframeSpec.defaultBrownoutVoltage(flightCells)));
    }

    private static void patchMotor(JsonObject json, CreativeSpec spec) {
        number(json, "mass_g", spec, CreativeParam.MOTOR_MASS, 1.0);
        integer(json, "kv", spec, CreativeParam.MOTOR_KV);
        number(json, "resistance_ohm", spec, CreativeParam.MOTOR_RESISTANCE, 1.0E3);
        number(json, "max_current_a", spec, CreativeParam.MOTOR_MAX_CURRENT, 1.0);
    }

    private static void patchProp(JsonObject json, CreativeSpec spec) {
        number(json, "mass_g", spec, CreativeParam.PROP_MASS, 1.0);
        integer(json, "diameter_mm", spec, CreativeParam.PROP_DIAMETER);
        number(json, "pitch_in", spec, CreativeParam.PROP_PITCH, 1.0);
        integer(json, "blades", spec, CreativeParam.PROP_BLADES);
        number(json, "ct", spec, CreativeParam.PROP_THRUST_COEFFICIENT, 1.0);
        number(json, "cp", spec, CreativeParam.PROP_POWER_COEFFICIENT, 1.0);
    }

    private static void patchBattery(JsonObject json, CreativeSpec spec) {
        number(json, "mass_g", spec, CreativeParam.BATTERY_MASS, 1.0);
        integer(json, "cells", spec, CreativeParam.BATTERY_CELLS);
        integer(json, "capacity_mah", spec, CreativeParam.BATTERY_CAPACITY);
        number(json, "cell_resistance_ohm", spec, CreativeParam.BATTERY_RESISTANCE, 1.0E3);
        integer(json, "c_rating", spec, CreativeParam.BATTERY_C_RATING);
    }

    private static void patchVideo(JsonObject json, CreativeSpec spec) {
        number(json, "mass_g", spec, CreativeParam.VIDEO_MASS, 1.0);
        number(json, "camera_fov_deg", spec, CreativeParam.CAMERA_FOV, 1.0);
    }
}

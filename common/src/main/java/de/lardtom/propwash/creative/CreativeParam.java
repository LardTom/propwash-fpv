package de.lardtom.propwash.creative;

import de.lardtom.propwash.api.part.PartKind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum CreativeParam {
    FRAME_MASS("frame.mass_g", Group.MASS, PartKind.FRAME, "g", 1.0, 5000.0, 1.0, 1, Kind.NUMBER),
    MOTOR_MASS("motor.mass_g", Group.MASS, PartKind.MOTOR, "g", 0.5, 500.0, 0.5, 1, Kind.NUMBER),
    PROP_MASS("prop.mass_g", Group.MASS, PartKind.PROP, "g", 0.1, 200.0, 0.1, 2, Kind.NUMBER),
    BATTERY_MASS("battery.mass_g", Group.MASS, PartKind.BATTERY, "g", 5.0, 5000.0, 1.0, 1, Kind.NUMBER),
    STACK_MASS("stack.mass_g", Group.MASS, PartKind.STACK, "g", 0.5, 500.0, 0.5, 1, Kind.NUMBER),
    VIDEO_MASS("video.mass_g", Group.MASS, PartKind.VIDEO, "g", 0.5, 500.0, 0.5, 1, Kind.NUMBER),
    INERTIA_ROLL("inertia.roll", Group.MASS, null, "g·cm²", 1.0, 5.0E6, 1.0, 0, Kind.AUTO),
    INERTIA_PITCH("inertia.pitch", Group.MASS, null, "g·cm²", 1.0, 5.0E6, 1.0, 0, Kind.AUTO),
    INERTIA_YAW("inertia.yaw", Group.MASS, null, "g·cm²", 1.0, 5.0E6, 1.0, 0, Kind.AUTO),
    WHEELBASE("frame.wheelbase_mm", Group.GEOMETRY, PartKind.FRAME, "mm", 40.0, 1500.0, 1.0, 0, Kind.INTEGER),
    ARM_FRONT_LEFT_FORWARD("arm.front_left.forward", Group.GEOMETRY, PartKind.FRAME, "mm", 5.0, 1000.0, 1.0, 1, Kind.AUTO),
    ARM_FRONT_LEFT_RIGHT("arm.front_left.right", Group.GEOMETRY, PartKind.FRAME, "mm", -1000.0, -5.0, 1.0, 1, Kind.AUTO),
    ARM_FRONT_LEFT_UP("arm.front_left.up", Group.GEOMETRY, PartKind.FRAME, "mm", -200.0, 200.0, 1.0, 1, Kind.AUTO),
    ARM_FRONT_RIGHT_FORWARD("arm.front_right.forward", Group.GEOMETRY, PartKind.FRAME, "mm", 5.0, 1000.0, 1.0, 1, Kind.AUTO),
    ARM_FRONT_RIGHT_RIGHT("arm.front_right.right", Group.GEOMETRY, PartKind.FRAME, "mm", 5.0, 1000.0, 1.0, 1, Kind.AUTO),
    ARM_FRONT_RIGHT_UP("arm.front_right.up", Group.GEOMETRY, PartKind.FRAME, "mm", -200.0, 200.0, 1.0, 1, Kind.AUTO),
    ARM_REAR_LEFT_FORWARD("arm.rear_left.forward", Group.GEOMETRY, PartKind.FRAME, "mm", -1000.0, -5.0, 1.0, 1, Kind.AUTO),
    ARM_REAR_LEFT_RIGHT("arm.rear_left.right", Group.GEOMETRY, PartKind.FRAME, "mm", -1000.0, -5.0, 1.0, 1, Kind.AUTO),
    ARM_REAR_LEFT_UP("arm.rear_left.up", Group.GEOMETRY, PartKind.FRAME, "mm", -200.0, 200.0, 1.0, 1, Kind.AUTO),
    ARM_REAR_RIGHT_FORWARD("arm.rear_right.forward", Group.GEOMETRY, PartKind.FRAME, "mm", -1000.0, -5.0, 1.0, 1, Kind.AUTO),
    ARM_REAR_RIGHT_RIGHT("arm.rear_right.right", Group.GEOMETRY, PartKind.FRAME, "mm", 5.0, 1000.0, 1.0, 1, Kind.AUTO),
    ARM_REAR_RIGHT_UP("arm.rear_right.up", Group.GEOMETRY, PartKind.FRAME, "mm", -200.0, 200.0, 1.0, 1, Kind.AUTO),
    PROP_DIAMETER("prop.diameter_mm", Group.PROPS, PartKind.PROP, "mm", 20.0, 400.0, 1.0, 0, Kind.INTEGER),
    PROP_PITCH("prop.pitch_in", Group.PROPS, PartKind.PROP, "″", 0.5, 12.0, 0.1, 1, Kind.NUMBER),
    PROP_BLADES("prop.blades", Group.PROPS, PartKind.PROP, "", 2.0, 8.0, 1.0, 0, Kind.INTEGER),
    PROP_THRUST_COEFFICIENT("prop.ct", Group.PROPS, PartKind.PROP, "", 0.01, 0.4, 0.001, 3, Kind.NUMBER),
    PROP_POWER_COEFFICIENT("prop.cp", Group.PROPS, PartKind.PROP, "", 0.005, 0.4, 0.001, 3, Kind.NUMBER),
    MOTOR_KV("motor.kv", Group.MOTORS, PartKind.MOTOR, "KV", 100.0, 60000.0, 10.0, 0, Kind.INTEGER),
    MOTOR_RESISTANCE("motor.resistance_mohm", Group.MOTORS, PartKind.MOTOR, "mΩ", 5.0, 2000.0, 1.0, 0, Kind.NUMBER),
    MOTOR_MAX_CURRENT("motor.max_current_a", Group.MOTORS, PartKind.MOTOR, "A", 1.0, 200.0, 0.5, 1, Kind.NUMBER),
    BATTERY_CELLS("battery.cells", Group.BATTERY, PartKind.BATTERY, "S", 1.0, 12.0, 1.0, 0, Kind.INTEGER),
    BATTERY_CAPACITY("battery.capacity_mah", Group.BATTERY, PartKind.BATTERY, "mAh", 50.0, 50000.0, 10.0, 0, Kind.INTEGER),
    BATTERY_RESISTANCE("battery.resistance_mohm", Group.BATTERY, PartKind.BATTERY, "mΩ", 1.0, 500.0, 0.5, 1, Kind.NUMBER),
    BATTERY_C_RATING("battery.c_rating", Group.BATTERY, PartKind.BATTERY, "C", 1.0, 300.0, 1.0, 0, Kind.INTEGER),
    ESC_CURRENT("stack.esc_current_a", Group.ESC, PartKind.STACK, "A", 1.0, 300.0, 1.0, 0, Kind.NUMBER),
    FRONTAL_AREA("frame.frontal_area_cm2", Group.AERO, PartKind.FRAME, "cm²", 5.0, 5000.0, 1.0, 0, Kind.NUMBER),
    DRAG_COEFFICIENT("frame.drag_cd", Group.AERO, PartKind.FRAME, "", 0.05, 3.0, 0.01, 2, Kind.NUMBER),
    SIDE_AREA("frame.side_area_cm2", Group.AERO, PartKind.FRAME, "cm²", 5.0, 20000.0, 1.0, 0, Kind.AUTO),
    TOP_AREA("frame.top_area_cm2", Group.AERO, PartKind.FRAME, "cm²", 5.0, 20000.0, 1.0, 0, Kind.AUTO),
    DUCTS("frame.ducts", Group.AERO, PartKind.FRAME, "", 0.0, 1.0, 1.0, 0, Kind.TOGGLE),
    IMPACT_TOLERANCE("frame.impact_tolerance", Group.AERO, PartKind.FRAME, "m/s", 1.0, 100.0, 0.5, 1, Kind.NUMBER),
    CAMERA_FOV("video.fov_deg", Group.CAMERA, PartKind.VIDEO, "°", 60.0, 160.0, 1.0, 0, Kind.INTEGER),
    CAMERA_FORWARD("camera.forward", Group.CAMERA, PartKind.FRAME, "mm", -500.0, 500.0, 1.0, 1, Kind.AUTO),
    CAMERA_RIGHT("camera.right", Group.CAMERA, PartKind.FRAME, "mm", -500.0, 500.0, 1.0, 1, Kind.AUTO),
    CAMERA_UP("camera.up", Group.CAMERA, PartKind.FRAME, "mm", -500.0, 500.0, 1.0, 1, Kind.AUTO);

    public static final List<CreativeParam> INERTIA = List.of(INERTIA_ROLL, INERTIA_PITCH, INERTIA_YAW);
    public static final List<CreativeParam> ARMS = List.of(ARM_FRONT_LEFT_FORWARD, ARM_FRONT_LEFT_RIGHT, ARM_FRONT_LEFT_UP,
            ARM_FRONT_RIGHT_FORWARD, ARM_FRONT_RIGHT_RIGHT, ARM_FRONT_RIGHT_UP, ARM_REAR_LEFT_FORWARD, ARM_REAR_LEFT_RIGHT,
            ARM_REAR_LEFT_UP, ARM_REAR_RIGHT_FORWARD, ARM_REAR_RIGHT_RIGHT, ARM_REAR_RIGHT_UP);
    public static final List<CreativeParam> CAMERA_POSITION = List.of(CAMERA_FORWARD, CAMERA_RIGHT, CAMERA_UP);

    private static final Map<String, CreativeParam> BY_KEY = byKey();

    private final String key;
    private final Group group;
    private final @Nullable PartKind part;
    private final String unit;
    private final double min;
    private final double max;
    private final double step;
    private final int decimals;
    private final Kind kind;

    CreativeParam(String key, Group group, @Nullable PartKind part, String unit, double min, double max, double step, int decimals, Kind kind) {
        this.key = key;
        this.group = group;
        this.part = part;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = decimals;
        this.kind = kind;
    }

    public String key() {
        return this.key;
    }

    public Group group() {
        return this.group;
    }

    public Optional<PartKind> part() {
        return Optional.ofNullable(this.part);
    }

    public String unit() {
        return this.unit;
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public double step() {
        return this.step;
    }

    public int decimals() {
        return this.decimals;
    }

    public Kind kind() {
        return this.kind;
    }

    public boolean auto() {
        return this.kind == Kind.AUTO;
    }

    public boolean toggle() {
        return this.kind == Kind.TOGGLE;
    }

    public boolean integer() {
        return this.kind == Kind.INTEGER || this.kind == Kind.TOGGLE;
    }

    public String translationKey() {
        return "propwash.creative.param." + this.key;
    }

    public double clamp(double value) {
        double clamped = Math.max(this.min, Math.min(this.max, value));
        return this.round(clamped);
    }

    public double round(double value) {
        if (this.integer()) {
            return Math.rint(value);
        }
        double scale = Math.pow(10.0, this.decimals + 2);
        double rounded = Math.rint(value * scale) / scale;
        return rounded == 0.0 ? 0.0 : rounded;
    }

    public boolean inRange(double value) {
        return Double.isFinite(value) && value >= this.min && value <= this.max;
    }

    public static Optional<CreativeParam> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    public static Map<Group, List<CreativeParam>> grouped() {
        Map<Group, List<CreativeParam>> out = new EnumMap<>(Group.class);
        for (Group group : Group.values()) {
            out.put(group, new ArrayList<>());
        }
        for (CreativeParam param : values()) {
            out.get(param.group).add(param);
        }
        Map<Group, List<CreativeParam>> frozen = new EnumMap<>(Group.class);
        out.forEach((group, list) -> frozen.put(group, List.copyOf(list)));
        return Collections.unmodifiableMap(frozen);
    }

    private static Map<String, CreativeParam> byKey() {
        Map<String, CreativeParam> map = new LinkedHashMap<>();
        for (CreativeParam param : values()) {
            map.put(param.key, param);
        }
        return Collections.unmodifiableMap(map);
    }

    public enum Kind {
        NUMBER,
        INTEGER,
        TOGGLE,
        AUTO
    }

    public enum Group {
        MASS,
        GEOMETRY,
        PROPS,
        MOTORS,
        BATTERY,
        ESC,
        AERO,
        CAMERA;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "propwash.creative.group." + this.key();
        }
    }
}

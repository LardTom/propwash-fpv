package de.lardtom.propwash.freecam;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeSpec;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public enum FreecamPreset {
    WHOOP("whoop", "WHOOP"),
    THREE_FIVE("3_5", "3.5″"),
    FIVE("5", "5″"),
    SEVEN("7", "7″");

    public static final FreecamPreset DEFAULT = FIVE;
    public static final double THREE_FIVE_PROP_MM = 89.0;
    public static final int THREE_FIVE_BLADES = 3;
    public static final double THREE_FIVE_PITCH_IN = 3.0;
    public static final double THREE_FIVE_KV = 2450.0;
    public static final double THREE_FIVE_MOTOR_G = 20.0;
    public static final double THREE_FIVE_WHEELBASE_MM = 150.0;
    public static final double THREE_FIVE_PROP_G = 2.5;
    public static final double THREE_FIVE_FRAME_G = 60.0;
    public static final double THREE_FIVE_FRONTAL_CM2 = 65.0;
    public static final double THREE_FIVE_DRAG_CD = 0.85;
    public static final double THREE_FIVE_CT = 0.20;
    public static final double THREE_FIVE_CP = 0.05;

    private static final DroneBuild THREE_FIVE_BUILD = threeFive();

    private final String key;
    private final String shortName;

    FreecamPreset(String key, String shortName) {
        this.key = key;
        this.shortName = shortName;
    }

    public String key() {
        return this.key;
    }

    public String shortName() {
        return this.shortName;
    }

    public String translationKey() {
        return "propwash.freecam.preset." + this.key;
    }

    public DroneBuild build() {
        return switch (this) {
            case WHOOP -> DroneBuild.WHOOP;
            case THREE_FIVE -> THREE_FIVE_BUILD;
            case FIVE -> DroneBuild.FREESTYLE;
            case SEVEN -> DroneBuild.LONG_RANGE;
        };
    }

    public static FreecamPreset byKey(@Nullable String key) {
        if (key != null) {
            String clean = key.trim().toLowerCase(Locale.ROOT);
            for (FreecamPreset preset : values()) {
                if (preset.key.equals(clean)) {
                    return preset;
                }
            }
        }
        return DEFAULT;
    }

    private static DroneBuild threeFive() {
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        base.put(PartKind.FRAME, FrameType.FREESTYLE_5.key());
        base.put(PartKind.STACK, StackType.AIO_20.key());
        base.put(PartKind.MOTOR, MotorType.M2004_3000.key());
        base.put(PartKind.PROP, PropType.P3_3B.key());
        base.put(PartKind.BATTERY, BatteryType.B4S_650.key());
        Map<String, Double> values = new TreeMap<>();
        values.put(CreativeParam.PROP_DIAMETER.key(), THREE_FIVE_PROP_MM);
        values.put(CreativeParam.PROP_BLADES.key(), (double) THREE_FIVE_BLADES);
        values.put(CreativeParam.PROP_PITCH.key(), THREE_FIVE_PITCH_IN);
        values.put(CreativeParam.MOTOR_KV.key(), THREE_FIVE_KV);
        values.put(CreativeParam.MOTOR_MASS.key(), THREE_FIVE_MOTOR_G);
        values.put(CreativeParam.WHEELBASE.key(), THREE_FIVE_WHEELBASE_MM);
        values.put(CreativeParam.FRAME_MASS.key(), THREE_FIVE_FRAME_G);
        values.put(CreativeParam.FRONTAL_AREA.key(), THREE_FIVE_FRONTAL_CM2);
        values.put(CreativeParam.DRAG_COEFFICIENT.key(), THREE_FIVE_DRAG_CD);
        values.put(CreativeParam.PROP_MASS.key(), THREE_FIVE_PROP_G);
        values.put(CreativeParam.PROP_THRUST_COEFFICIENT.key(), THREE_FIVE_CT);
        values.put(CreativeParam.PROP_POWER_COEFFICIENT.key(), THREE_FIVE_CP);
        return new DroneBuild(FrameType.FREESTYLE_5, StackType.CREATIVE_FC, MotorType.M2004_3000, PropType.P3_3B, VideoType.DIGITAL_LITE,
                BatteryType.B4S_650).withCreative(new CreativeSpec(base, values));
    }
}

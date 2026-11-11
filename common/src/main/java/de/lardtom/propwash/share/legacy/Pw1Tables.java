package de.lardtom.propwash.share.legacy;

import de.lardtom.propwash.api.build.TuneSpec;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.OptionalLong;

public final class Pw1Tables {
    public static final String PREFIX = "PW1-";
    public static final String FORMAT = "PW1";
    public static final String REVISION = "1.2";
    public static final int FRAME_VERSION = 1;
    public static final int FLAG_DEFLATE = 1;
    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int GROUP_LENGTH = 5;
    public static final int MIN_FRAME_BYTES = 7;
    public static final int MAX_PAYLOAD_BYTES = 16384;
    public static final int PAINT_SLOT_BITS = 16;
    public static final List<String> NAMESPACES = List.of("", "propwash", "justmoreparts");
    public static final List<String> TUNE_KEYS = List.of(
            "rates.type",
            "rates.roll.rcRate", "rates.roll.superRate", "rates.roll.rcExpo", "rates.roll.centerRateDps", "rates.roll.maxRateDps",
            "rates.roll.actualExpo",
            "rates.pitch.rcRate", "rates.pitch.superRate", "rates.pitch.rcExpo", "rates.pitch.centerRateDps", "rates.pitch.maxRateDps",
            "rates.pitch.actualExpo",
            "rates.yaw.rcRate", "rates.yaw.superRate", "rates.yaw.rcExpo", "rates.yaw.centerRateDps", "rates.yaw.maxRateDps",
            "rates.yaw.actualExpo",
            "rates.rcDeadband", "rates.yawDeadband", "pid.source",
            "pid.roll.p", "pid.roll.i", "pid.roll.d", "pid.roll.ff",
            "pid.pitch.p", "pid.pitch.i", "pid.pitch.d", "pid.pitch.ff",
            "pid.yaw.p", "pid.yaw.i", "pid.yaw.d", "pid.yaw.ff",
            "pid.angleStrength", "pid.angleLimitDeg", "pid.horizonStrength", "pid.itermRelax", "pid.antiGravityGain", "pid.airmode",
            "pid.tpaMode", "pid.tpaRatePercent", "pid.tpaBreakpoint", "pid.motorIdlePercent", "pid.throttleMid", "pid.throttleExpo",
            "pid.throttleLimitType", "pid.throttleLimitPercent", "pid.thrustLinearPercent", "pid.vbatSagCompensationPercent",
            "filters.pidLoop", "filters.gyroLpfHz", "filters.dtermLpf1Hz", "filters.dtermLpf2Hz", "filters.rcSmoothingHz",
            TuneSpec.CAMERA_UPTILT);
    public static final String UNKNOWN_NAMESPACE_PREFIX = "pw1.unknown.";

    private Pw1Tables() {
    }

    public static int tuneIndex(String key) {
        return TUNE_KEYS.indexOf(key) + 1;
    }

    public static Identifier unknownNamespace(long ns, String path) {
        return Identifier.fromNamespaceAndPath(UNKNOWN_NAMESPACE_PREFIX + ns, path);
    }

    public static OptionalLong unknownNamespaceIndex(Identifier id) {
        String namespace = id.getNamespace();
        if (!namespace.startsWith(UNKNOWN_NAMESPACE_PREFIX)) {
            return OptionalLong.empty();
        }
        String digits = namespace.substring(UNKNOWN_NAMESPACE_PREFIX.length());
        if (digits.isEmpty() || digits.length() > 10 || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalLong.empty();
        }
        long value = Long.parseLong(digits);
        return value >= NAMESPACES.size() && value <= 0xFFFFFFFFL ? OptionalLong.of(value) : OptionalLong.empty();
    }
}

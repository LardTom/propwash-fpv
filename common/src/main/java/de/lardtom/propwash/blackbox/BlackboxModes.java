package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.sim.FcSample;
import de.lardtom.propwash.sim.StabMode;

public final class BlackboxModes {
    public static final int ARM = 1;
    public static final int ANGLE = 1 << 1;
    public static final int HORIZON = 1 << 2;
    public static final int FAILSAFE = 1 << 6;
    public static final int GPS_RESCUE = 1 << 7;
    public static final int BEEPER = 1 << 11;
    public static final int BLACKBOX = 1 << 19;
    public static final int AIRMODE = 1 << 20;
    public static final int MODE_3D = 1 << 21;
    public static final int FLIP_OVER_AFTER_CRASH = 1 << 27;

    public static final int FAILSAFE_IDLE = 0;
    public static final int FAILSAFE_RX_LOSS_DETECTED = 1;
    public static final int FAILSAFE_LANDING = 2;
    public static final int FAILSAFE_LANDED = 3;
    public static final int FAILSAFE_GPS_RESCUE = 6;

    private BlackboxModes() {
    }

    public static int flags(FcSample s, int extra) {
        int flags = extra;
        if (s.armed) {
            flags |= ARM;
        }
        if (s.mode == StabMode.ANGLE) {
            flags |= ANGLE;
        } else if (s.mode == StabMode.HORIZON) {
            flags |= HORIZON;
        }
        if (s.failsafeStage > 0) {
            flags |= FAILSAFE;
        }
        if (s.airmode) {
            flags |= AIRMODE;
        }
        if (s.mode3d) {
            flags |= MODE_3D;
        }
        return flags;
    }

    public static int failsafePhase(FcSample s, boolean rescue) {
        if (s.failsafeStage == 1) {
            return FAILSAFE_LANDING;
        }
        if (s.failsafeStage >= 2) {
            return FAILSAFE_LANDED;
        }
        if (!s.linkOk) {
            return rescue ? FAILSAFE_GPS_RESCUE : FAILSAFE_RX_LOSS_DETECTED;
        }
        return FAILSAFE_IDLE;
    }
}

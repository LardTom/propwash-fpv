package de.lardtom.propwash.osd;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.sim.ArmBlocker;

import java.util.List;
import java.util.Locale;

public final class OsdWarnings {
    public static final double WEAK_LINK = 0.35;
    public static final double WEAK_VIDEO = 0.3;
    public static final double NO_VIDEO = 0.005;
    public static final double CYCLE_SECONDS = 1.6;

    private OsdWarnings() {
    }

    public static void collect(OsdData data, List<Warning> out) {
        out.clear();
        if (!data.active) {
            return;
        }
        if (data.failsafeStage > 0) {
            out.add(Warning.FAILSAFE);
        }
        if (!data.radioConnected && !data.rescueActive) {
            out.add(Warning.NO_RADIO);
        }
        if (data.rescueActive) {
            out.add(Warning.RESCUE);
        }
        if (data.posHoldDenied) {
            out.add(Warning.NO_GPS);
        }
        if (data.worldEdge) {
            out.add(Warning.WORLD_EDGE);
        }
        switch (data.batteryState) {
            case EMPTY -> out.add(Warning.PACK_DEPLETED);
            case CRITICAL -> out.add(Warning.BATTERY_CRITICAL);
            case LOW -> out.add(Warning.BATTERY_LOW);
            case OK -> {
            }
        }
        wear(data.wear, out);
        if (data.motorDerating) {
            out.add(Warning.MOTOR_HOT);
        }
        if (data.radioConnected && data.linkStrength < WEAK_LINK) {
            out.add(Warning.WEAK_SIGNAL);
        }
        if (data.videoSystem != null && (data.videoLost || data.videoSignal < NO_VIDEO)) {
            out.add(Warning.NO_VIDEO);
        } else if (data.videoSystem != null && data.videoSignal < WEAK_VIDEO) {
            out.add(Warning.WEAK_VIDEO);
        }
        if (!data.armed && data.hasSim) {
            for (ArmBlocker blocker : ArmBlocker.values()) {
                if (blocker.isSet(data.armBlockers)) {
                    Warning warning = Warning.forBlocker(blocker);
                    if (!out.contains(warning)) {
                        out.add(warning);
                    }
                }
            }
        }
    }

    public static void wear(PartWear wear, List<Warning> out) {
        if (wear == null || wear.isNew()) {
            return;
        }
        if (wear.brokenProps() > 0) {
            out.add(Warning.PROP_BROKEN);
        } else if (PartWear.critical(wear.worstProp())) {
            out.add(Warning.PROP_DAMAGE);
        }
        if (PartWear.broken(wear.frame())) {
            out.add(Warning.ARM_BROKEN);
        }
        if (PartWear.broken(wear.stack())) {
            out.add(Warning.FC_DAMAGE);
        }
        if (PartWear.critical(wear.worstMotor())) {
            out.add(Warning.MOTOR_DAMAGE);
        }
        if (PartWear.critical(wear.video())) {
            out.add(Warning.CAMERA_DAMAGE);
        }
        if (PartWear.critical(wear.battery())) {
            out.add(Warning.BATTERY_PUFFED);
        }
    }

    public static int visibleIndex(List<Warning> warnings, double seconds) {
        if (warnings.isEmpty()) {
            return -1;
        }
        int criticalCount = 0;
        for (Warning warning : warnings) {
            if (warning.severity() == Severity.CRITICAL) {
                criticalCount++;
            }
        }
        int pool = criticalCount > 0 ? criticalCount : warnings.size();
        int step = Double.isFinite(seconds) && seconds > 0.0 ? (int) Math.floor(seconds / CYCLE_SECONDS) : 0;
        int wanted = Math.floorMod(step, pool);
        if (criticalCount == 0) {
            return wanted;
        }
        int seen = 0;
        for (int i = 0; i < warnings.size(); i++) {
            if (warnings.get(i).severity() == Severity.CRITICAL) {
                if (seen == wanted) {
                    return i;
                }
                seen++;
            }
        }
        return 0;
    }

    public static boolean blinkOn(double seconds) {
        return !Double.isFinite(seconds) || (seconds % 0.8) < 0.5;
    }

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    public enum Warning {
        FAILSAFE(Severity.CRITICAL, true),
        NO_RADIO(Severity.CRITICAL, true),
        RESCUE(Severity.WARNING, false),
        NO_GPS(Severity.WARNING, true),
        WORLD_EDGE(Severity.WARNING, false),
        PACK_DEPLETED(Severity.CRITICAL, true),
        BATTERY_CRITICAL(Severity.CRITICAL, true),
        BATTERY_LOW(Severity.WARNING, true),
        WEAK_SIGNAL(Severity.WARNING, true),
        WEAK_VIDEO(Severity.WARNING, false),
        NO_VIDEO(Severity.CRITICAL, true),
        PROP_BROKEN(Severity.CRITICAL, true),
        ARM_BROKEN(Severity.CRITICAL, true),
        FC_DAMAGE(Severity.CRITICAL, true),
        PROP_DAMAGE(Severity.WARNING, false),
        MOTOR_DAMAGE(Severity.WARNING, false),
        CAMERA_DAMAGE(Severity.WARNING, false),
        BATTERY_PUFFED(Severity.WARNING, true),
        MOTOR_HOT(Severity.WARNING, true),
        ARM_SWITCH(Severity.INFO, false),
        ARM_THROTTLE(Severity.INFO, false),
        ARM_ANGLE(Severity.INFO, false),
        ARM_BUILD(Severity.INFO, false),
        ARM_CRASH(Severity.INFO, false),
        ARM_FLUID(Severity.INFO, false),
        ARM_REBOOT(Severity.INFO, false),
        ARM_HARDWARE(Severity.INFO, false),
        ARM_THROTTLE_3D(Severity.INFO, false);

        private final Severity severity;
        private final boolean blinks;

        Warning(Severity severity, boolean blinks) {
            this.severity = severity;
            this.blinks = blinks;
        }

        public Severity severity() {
            return this.severity;
        }

        public boolean blinks() {
            return this.blinks;
        }

        public String translationKey() {
            return "propwash.osd.warning." + this.name().toLowerCase(Locale.ROOT);
        }

        static Warning forBlocker(ArmBlocker blocker) {
            return switch (blocker) {
                case ARM_SWITCH_NOT_RESET -> ARM_SWITCH;
                case THROTTLE_NOT_LOW -> ARM_THROTTLE;
                case TILTED -> ARM_ANGLE;
                case PACK_DEPLETED -> PACK_DEPLETED;
                case BUILD_INVALID -> ARM_BUILD;
                case CRASH_COOLDOWN -> ARM_CRASH;
                case FAILSAFE -> FAILSAFE;
                case REBOOTING -> ARM_REBOOT;
                case IN_FLUID -> ARM_FLUID;
                case HARDWARE_FAULT -> ARM_HARDWARE;
                case THROTTLE_NOT_CENTERED -> ARM_THROTTLE_3D;
            };
        }
    }
}

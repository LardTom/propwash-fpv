package de.lardtom.propwash.sim;

final class Arming {
    static final double CRASH_COOLDOWN_S = 1.0;
    static final double REBOOT_S = 0.5;
    static final double BATTERY_EMPTY_SOC = 0.03;

    boolean armed;
    boolean lastSwitch;
    boolean switchNeedsReset;
    boolean pendingSwitchCheck = true;
    long crashCooldownSubsteps;
    long rebootSubsteps;
    long linkLostSubsteps;
    long landingSubsteps;
    int failsafeStage;
    int blockers;
    DisarmReason lastDisarmReason = DisarmReason.NONE;

    void reset() {
        this.armed = false;
        this.switchNeedsReset = false;
        this.pendingSwitchCheck = true;
        this.linkLostSubsteps = 0;
        this.landingSubsteps = 0;
        this.failsafeStage = 0;
    }

    static int conditions(PilotInput in, FlightControllerConfig cfg, double tiltDeg, double soc, boolean buildable,
                          boolean crashCooldown, boolean rebooting, Fluid fluid) {
        return conditions(in, cfg, tiltDeg, soc, buildable, crashCooldown, rebooting, fluid, false);
    }

    static int conditions(PilotInput in, FlightControllerConfig cfg, double tiltDeg, double soc, boolean buildable,
                          boolean crashCooldown, boolean rebooting, Fluid fluid, boolean hardwareFault) {
        int mask = 0;
        if (in.throttle > cfg.armThrottleMax) {
            mask |= ArmBlocker.THROTTLE_NOT_LOW.bit();
        }
        if (tiltDeg > cfg.armMaxTiltDeg) {
            mask |= ArmBlocker.TILTED.bit();
        }
        return mask | common(in, soc, buildable, crashCooldown, rebooting, fluid, hardwareFault);
    }

    static int conditions3d(PilotInput in, FlightControllerConfig cfg, double tiltDeg, double soc, boolean buildable,
                            boolean crashCooldown, boolean rebooting, Fluid fluid, boolean hardwareFault) {
        int mask = 0;
        if (Math.abs(in.throttle - 0.5) > cfg.deadband3d) {
            mask |= ArmBlocker.THROTTLE_NOT_CENTERED.bit();
        }
        if (tiltDeg > cfg.armMaxTiltDeg && tiltDeg < 180.0 - cfg.armMaxTiltDeg) {
            mask |= ArmBlocker.TILTED.bit();
        }
        return mask | common(in, soc, buildable, crashCooldown, rebooting, fluid, hardwareFault);
    }

    private static int common(PilotInput in, double soc, boolean buildable, boolean crashCooldown, boolean rebooting, Fluid fluid,
                              boolean hardwareFault) {
        int mask = 0;
        if (soc <= BATTERY_EMPTY_SOC) {
            mask |= ArmBlocker.PACK_DEPLETED.bit();
        }
        if (!buildable) {
            mask |= ArmBlocker.BUILD_INVALID.bit();
        }
        if (crashCooldown) {
            mask |= ArmBlocker.CRASH_COOLDOWN.bit();
        }
        if (!in.linkOk) {
            mask |= ArmBlocker.FAILSAFE.bit();
        }
        if (rebooting) {
            mask |= ArmBlocker.REBOOTING.bit();
        }
        if (fluid != Fluid.NONE) {
            mask |= ArmBlocker.IN_FLUID.bit();
        }
        if (hardwareFault) {
            mask |= ArmBlocker.HARDWARE_FAULT.bit();
        }
        return mask;
    }
}

package de.lardtom.propwash.rescue;

import org.jspecify.annotations.Nullable;

public final class RescueSupervisor {
    private static final float LINK_SEEN = 0.1F;
    private static final float LINK_LOST = 0.01F;

    public enum Trigger {
        KEY,
        SWITCH,
        FAILSAFE
    }

    public enum Abort {
        KEY,
        SWITCH,
        OVERRIDE
    }

    public static final class Frame {
        public boolean armed;
        public boolean grounded;
        public boolean connected;
        public float linkStrength;
        public boolean rescueSwitch;
        public boolean setHomeSwitch;
        public double stickDeflection;
        public boolean keyToggle;
        public boolean keySetHome;
        public boolean rescueActive;
        public boolean rescueFlying;
        public boolean hasHome;
        public boolean pilotDown;

        public Frame() {
        }
    }

    private boolean radioSeen;
    private boolean linkSeen;
    private boolean linkLost;
    private boolean failsafeLatched;
    private boolean lastRescueSwitch;
    private boolean lastSetHomeSwitch;
    private boolean wasArmed;
    private boolean armedOnce;
    private boolean overrideReady;
    private @Nullable Trigger start;
    private @Nullable Abort abort;
    private boolean setHomeOnArm;
    private boolean setHomeManual;

    public RescueSupervisor() {
    }

    public void beginSession(boolean armed, boolean sameDrone) {
        if (!sameDrone) {
            this.armedOnce = false;
        }
        this.endSession();
        this.wasArmed = armed;
        this.armedOnce |= armed;
    }

    public void endSession() {
        this.radioSeen = false;
        this.linkSeen = false;
        this.linkLost = false;
        this.failsafeLatched = false;
        this.lastRescueSwitch = false;
        this.lastSetHomeSwitch = false;
        this.overrideReady = false;
        this.start = null;
        this.abort = null;
        this.setHomeOnArm = false;
        this.setHomeManual = false;
    }

    public void forget() {
        this.endSession();
        this.wasArmed = false;
        this.armedOnce = false;
    }

    public void update(Frame f, RescueSettings cfg) {
        this.start = null;
        this.abort = null;
        this.setHomeOnArm = false;
        if (f.armed && !this.wasArmed) {
            boolean first = !this.armedOnce;
            this.armedOnce = true;
            this.setHomeOnArm = switch (cfg.homeMode) {
                case FIRST_ARM -> first || !f.hasHome;
                case EVERY_ARM -> true;
                case PILOT -> false;
                case MANUAL -> !f.hasHome;
            };
        }
        this.wasArmed = f.armed;

        if (f.connected) {
            this.radioSeen = true;
        }
        if (f.linkStrength > LINK_SEEN) {
            this.linkSeen = true;
        }
        this.linkLost = f.pilotDown || (this.radioSeen && !f.connected) || (this.linkSeen && f.linkStrength <= LINK_LOST);
        boolean radioUsable = f.connected && !this.linkLost;
        boolean rescueSwitch = radioUsable ? f.rescueSwitch : this.lastRescueSwitch;
        boolean setHomeSwitch = radioUsable ? f.setHomeSwitch : this.lastSetHomeSwitch;
        this.setHomeManual = f.keySetHome || (setHomeSwitch && !this.lastSetHomeSwitch);

        boolean active = f.rescueActive;
        if (rescueSwitch && !this.lastRescueSwitch) {
            if (!active) {
                this.start = Trigger.SWITCH;
            }
        } else if (!rescueSwitch && this.lastRescueSwitch && active) {
            this.abort = Abort.SWITCH;
            active = false;
        }
        this.lastRescueSwitch = rescueSwitch;
        this.lastSetHomeSwitch = setHomeSwitch;

        if (f.keyToggle) {
            if (active) {
                this.abort = Abort.KEY;
                active = false;
            } else if (this.start == null && this.abort == null) {
                this.start = Trigger.KEY;
            }
        }

        if (!this.linkLost) {
            this.failsafeLatched = false;
        } else if (!this.failsafeLatched && f.armed && !f.grounded) {
            this.failsafeLatched = true;
            if (cfg.failsafeAction == FailsafeAction.RETURN_HOME && !active && this.start == null) {
                this.start = Trigger.FAILSAFE;
            }
        }

        if (active && f.rescueFlying && cfg.stickOverride && radioUsable) {
            if (!this.overrideReady) {
                this.overrideReady = f.stickDeflection < 0.5 * cfg.stickOverrideThreshold;
            } else if (f.stickDeflection > cfg.stickOverrideThreshold) {
                this.abort = Abort.OVERRIDE;
            }
        }
        if (this.start != null) {
            this.overrideReady = false;
        }
    }

    public @Nullable Trigger start() {
        return this.start;
    }

    public @Nullable Abort abort() {
        return this.abort;
    }

    public boolean setHomeOnArm() {
        return this.setHomeOnArm;
    }

    public boolean setHomeManual() {
        return this.setHomeManual;
    }

    public boolean linkLost() {
        return this.linkLost;
    }
}

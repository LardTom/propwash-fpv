package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.KeyboardSettings;
import de.lardtom.propwash.input.PitchDirection;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.ThrottleCentering;
import net.minecraft.client.KeyMapping;

final class KeyboardFlight {
    private float roll;
    private float pitch;
    private float yaw;
    private float throttle;
    private boolean active;
    private boolean yawKeysRoll;
    private float pendingThrottle;
    private final ThrottleCentering centering = new ThrottleCentering();

    void reset() {
        this.roll = 0.0F;
        this.pitch = 0.0F;
        this.yaw = 0.0F;
        this.throttle = 0.0F;
        this.active = false;
        this.pendingThrottle = 0.0F;
        this.centering.reset();
    }

    void yawKeysSteerRoll(boolean roll) {
        this.yawKeysRoll = roll;
    }

    void nudgeThrottle(float delta) {
        if (Float.isFinite(delta)) {
            this.pendingThrottle += delta;
        }
    }

    Output update(float dt, boolean session, boolean keysAvailable, KeyboardSettings settings, boolean invertPitch, boolean centered, boolean armed) {
        if (!session) {
            this.pendingThrottle = 0.0F;
            if (this.active || this.throttle != 0.0F) {
                this.reset();
            }
            return new Output(StickPositions.IDLE, false);
        }
        this.active = true;
        float blend = (float) (1.0 - Math.exp(-settings.response * dt));
        float rollTarget = 0.0F;
        float pitchTarget = 0.0F;
        float yawTarget = 0.0F;
        float throttleInput = 0.0F;
        if (keysAvailable) {
            float rollKeys = direction(InputKeys.ROLL_RIGHT, InputKeys.ROLL_LEFT);
            float yawKeys = direction(InputKeys.YAW_RIGHT, InputKeys.YAW_LEFT);
            if (this.yawKeysRoll) {
                rollKeys = Math.max(-1.0F, Math.min(1.0F, rollKeys + yawKeys));
                yawKeys = 0.0F;
            }
            rollTarget = rollKeys * settings.rollPitchLimit;
            pitchTarget = PitchDirection.apply(direction(InputKeys.PITCH_FORWARD, InputKeys.PITCH_BACK), invertPitch) * settings.rollPitchLimit;
            yawTarget = yawKeys * settings.yawLimit;
            throttleInput = direction(InputKeys.THROTTLE_UP, InputKeys.THROTTLE_DOWN);
        }
        this.roll += (rollTarget - this.roll) * blend;
        this.pitch += (pitchTarget - this.pitch) * blend;
        this.yaw += (yawTarget - this.yaw) * blend;
        float rate = throttleInput < 0.0F ? settings.throttleRate * 2.0F : settings.throttleRate;
        float nudge = keysAvailable ? this.pendingThrottle : 0.0F;
        this.pendingThrottle = 0.0F;
        float next = Math.max(0.0F, Math.min(1.0F, this.throttle + throttleInput * rate * dt + nudge));
        this.throttle = this.centering.apply(this.throttle, next, throttleInput != 0.0F || nudge != 0.0F, centered, armed);
        StickPositions sticks = new StickPositions(settle(this.roll), settle(this.pitch), settle(this.yaw), this.throttle).clamped();
        return new Output(sticks, keysAvailable && InputKeys.anyFlightKeyDown());
    }

    private static float settle(float v) {
        return Math.abs(v) < 0.001F ? 0.0F : v;
    }

    private static float direction(KeyMapping positive, KeyMapping negative) {
        return (InputKeys.down(positive) ? 1.0F : 0.0F) - (InputKeys.down(negative) ? 1.0F : 0.0F);
    }

    record Output(StickPositions sticks, boolean keysHeld) {
    }
}

package de.lardtom.propwash.input;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyboardSettings {
    public float response = 7.0F;
    public float rollPitchLimit = 0.6F;
    public float yawLimit = 0.6F;
    public float throttleRate = 0.5F;
    public boolean startInAngle = true;
    public Map<String, SwitchCommand> commands = new LinkedHashMap<>();

    public KeyboardSettings copy() {
        KeyboardSettings copy = new KeyboardSettings();
        copy.response = this.response;
        copy.rollPitchLimit = this.rollPitchLimit;
        copy.yawLimit = this.yawLimit;
        copy.throttleRate = this.throttleRate;
        copy.startInAngle = this.startInAngle;
        copy.commands = SwitchCommands.copy(this.commands);
        return copy;
    }

    public void sanitize() {
        this.response = finite(this.response, 7.0F, 1.0F, 30.0F);
        this.rollPitchLimit = finite(this.rollPitchLimit, 0.6F, 0.1F, 1.0F);
        this.yawLimit = finite(this.yawLimit, 0.6F, 0.1F, 1.0F);
        this.throttleRate = finite(this.throttleRate, 0.5F, 0.05F, 3.0F);
        this.commands = SwitchCommands.sanitize(this.commands);
    }

    private static float finite(float v, float fallback, float lo, float hi) {
        return Float.isFinite(v) ? ChannelMapping.clamp(v, lo, hi) : fallback;
    }
}

package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.DeviceProfile;
import org.jspecify.annotations.Nullable;

public record RawInput(DeviceInfo device, int axisCount, int buttonCount, int hatCount, float[] joystickAxes, boolean[] joystickButtons,
                       float @Nullable [] gamepadAxes, boolean @Nullable [] gamepadButtons) {
    public boolean gamepadCapable() {
        return this.gamepadAxes != null && this.gamepadButtons != null;
    }

    public boolean usesGamepadLayout(DeviceProfile profile) {
        return profile.gamepadLayout && this.gamepadCapable();
    }

    public float[] axes(DeviceProfile profile) {
        return this.usesGamepadLayout(profile) ? this.gamepadAxes : this.joystickAxes;
    }

    public boolean[] buttons(DeviceProfile profile) {
        return this.usesGamepadLayout(profile) ? this.gamepadButtons : this.joystickButtons;
    }
}

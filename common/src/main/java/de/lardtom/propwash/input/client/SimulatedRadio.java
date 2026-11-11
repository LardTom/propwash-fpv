package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;

import java.util.Arrays;
import java.util.Objects;

public final class SimulatedRadio {
    private final String guid;
    private final String name;
    private final DeviceProfile profile;
    private float[] axes;
    private boolean[] buttons;
    private volatile DeviceInfo device;
    private volatile boolean savedProfile;

    public SimulatedRadio(String guid, String name, DeviceProfile profile, int axisCount, int buttonCount) {
        this.guid = Objects.requireNonNull(guid, "guid");
        this.name = Objects.requireNonNull(name, "name");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.axes = new float[Math.max(0, axisCount)];
        this.buttons = new boolean[Math.max(0, buttonCount)];
        this.device = new DeviceInfo(-1, name, guid, false, 0, 0);
    }

    public DeviceInfo device() {
        return this.device;
    }

    public synchronized SimulatedRadio asGamepad() {
        this.axes = Arrays.copyOf(this.axes, Math.max(this.axes.length, ProfileDefaults.PAD_AXES));
        this.buttons = Arrays.copyOf(this.buttons, Math.max(this.buttons.length, ProfileDefaults.PAD_BUTTONS));
        this.device = new DeviceInfo(-1, this.name, this.guid, true, 0, 0);
        return this;
    }

    public synchronized RawInput raw() {
        float[] axes = this.axes();
        boolean[] buttons = this.buttons();
        DeviceInfo device = this.device;
        boolean pad = device.gamepad();
        return new RawInput(device, axes.length, buttons.length, 0, axes, buttons,
                pad ? Arrays.copyOf(axes, ProfileDefaults.PAD_AXES) : null, pad ? Arrays.copyOf(buttons, ProfileDefaults.PAD_BUTTONS) : null);
    }

    public String guid() {
        return this.guid;
    }

    public String name() {
        return this.name;
    }

    public DeviceProfile profile() {
        return this.profile;
    }

    public SimulatedRadio useSavedProfile() {
        this.savedProfile = true;
        return this;
    }

    public DeviceProfile profile(InputConfig config) {
        DeviceProfile saved = this.savedProfile ? config.profile(this.guid) : null;
        return saved != null ? saved : this.profile;
    }

    public synchronized void setAxis(int index, float value) {
        this.axes[index] = value;
    }

    public void setMicros(int index, int micros) {
        this.setAxis(index, ChannelMath.fromRangeMicros(micros));
    }

    public synchronized void setButton(int index, boolean down) {
        this.buttons[index] = down;
    }

    public synchronized float[] axes() {
        return this.axes.clone();
    }

    public synchronized boolean[] buttons() {
        return this.buttons.clone();
    }
}

package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.DeviceProfile;

import java.util.Objects;

public final class SimulatedRadio {
    private final String guid;
    private final String name;
    private final DeviceProfile profile;
    private final float[] axes;
    private final boolean[] buttons;
    private final DeviceInfo device;

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

    public RawInput raw() {
        float[] axes = this.axes();
        boolean[] buttons = this.buttons();
        return new RawInput(this.device, axes.length, buttons.length, 0, axes, buttons, null, null);
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

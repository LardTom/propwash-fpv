package de.lardtom.propwash.input;

public record RadioFrame(RadioSource source, String deviceName, String deviceGuid, StickPositions sticks, long activeSwitches, long readableSwitches,
                         float[] axes, boolean[] buttons, float throttle3d, long addonSwitches, float[] addonAxes) {
    private static final float[] NO_AXES = new float[0];
    private static final boolean[] NO_BUTTONS = new boolean[0];

    public static final RadioFrame NONE = new RadioFrame(RadioSource.NONE, "", "", StickPositions.IDLE, 0L, 0L, NO_AXES, NO_BUTTONS);

    public RadioFrame(RadioSource source, String deviceName, String deviceGuid, StickPositions sticks, long activeSwitches, long readableSwitches,
                      float[] axes, boolean[] buttons) {
        this(source, deviceName, deviceGuid, sticks, activeSwitches, readableSwitches, axes, buttons,
                sticks == null ? ThrottleCentering.CENTER : sticks.throttle());
    }

    public RadioFrame(RadioSource source, String deviceName, String deviceGuid, StickPositions sticks, long activeSwitches, long readableSwitches,
                      float[] axes, boolean[] buttons, float throttle3d) {
        this(source, deviceName, deviceGuid, sticks, activeSwitches, readableSwitches, axes, buttons, throttle3d, 0L, NO_AXES);
    }

    public RadioFrame {
        source = source == null ? RadioSource.NONE : source;
        deviceName = deviceName == null ? "" : deviceName;
        deviceGuid = deviceGuid == null ? "" : deviceGuid;
        sticks = sticks == null ? StickPositions.IDLE : sticks.clamped();
        axes = axes == null ? NO_AXES : axes;
        buttons = buttons == null ? NO_BUTTONS : buttons;
        throttle3d = Float.isNaN(throttle3d) ? ThrottleCentering.CENTER : ChannelMapping.clamp(throttle3d, 0.0F, 1.0F);
        addonAxes = addonAxes == null ? NO_AXES : addonAxes;
        if (source == RadioSource.NONE) {
            deviceName = "";
            deviceGuid = "";
            sticks = StickPositions.IDLE;
            activeSwitches = 0L;
            readableSwitches = 0L;
            axes = NO_AXES;
            buttons = NO_BUTTONS;
            throttle3d = ThrottleCentering.CENTER;
        }
    }

    public boolean connected() {
        return this.source != RadioSource.NONE;
    }

    public RadioFrame withAddons(long switches, float[] values) {
        return new RadioFrame(this.source, this.deviceName, this.deviceGuid, this.sticks, this.activeSwitches, this.readableSwitches, this.axes,
                this.buttons, this.throttle3d, switches, values == null ? NO_AXES : values);
    }
}

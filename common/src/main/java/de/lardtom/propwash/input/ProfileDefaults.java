package de.lardtom.propwash.input;

import java.util.List;
import java.util.Locale;

public final class ProfileDefaults {
    public static final int PAD_LEFT_X = 0;
    public static final int PAD_LEFT_Y = 1;
    public static final int PAD_RIGHT_X = 2;
    public static final int PAD_RIGHT_Y = 3;
    public static final int PAD_LEFT_TRIGGER = 4;
    public static final int PAD_RIGHT_TRIGGER = 5;
    public static final int PAD_AXES = 6;

    public static final int PAD_SOUTH = 0;
    public static final int PAD_EAST = 1;
    public static final int PAD_WEST = 2;
    public static final int PAD_NORTH = 3;
    public static final int PAD_BACK = 4;
    public static final int PAD_GUIDE = 5;
    public static final int PAD_START = 6;
    public static final int PAD_LEFT_STICK = 7;
    public static final int PAD_RIGHT_STICK = 8;
    public static final int PAD_LEFT_SHOULDER = 9;
    public static final int PAD_RIGHT_SHOULDER = 10;
    public static final int PAD_BUTTONS = 15;

    public static final int AUX_ARM = 4;
    public static final int AUX_MODE = 5;
    public static final int AUX_BEEPER = 6;
    public static final int AUX_RESCUE = 7;
    public static final int SWITCH_HIGH_LOW_US = 1700;
    public static final int SWITCH_MID_LOW_US = 1300;
    public static final int SWITCH_MID_HIGH_US = 1675;

    private static final List<String> RADIO_WORDS = List.of(
            "edgetx", "opentx", "radiomaster", "frsky", "taranis", "horus", "jumper", "tbs", "tango", "mambo",
            "boxer", "zorro", "tx16", "tx12", "tx15", "pocket", "flysky", "literadio", "betafpv", "ethos",
            "rc transmitter", "rc joystick", "fpv", "radiolink", "futaba", "spektrum", "interlink", "rc simulator",
            "t-lite", "t-pro", "mt12", "gx12");

    private ProfileDefaults() {
    }

    public static boolean looksLikeRadio(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String word : RADIO_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static boolean prefersGamepadLayout(String name, boolean gamepad) {
        return gamepad && !looksLikeRadio(name);
    }

    public static DeviceProfile create(String name, boolean gamepad, int axisCount) {
        return prefersGamepadLayout(name, gamepad) ? gamepad(name) : radio(name, axisCount);
    }

    public static DeviceProfile radio(String name, int axisCount) {
        DeviceProfile p = new DeviceProfile();
        p.name = name == null ? "" : name;
        p.gamepadLayout = false;
        p.roll = axisOrNone(0, axisCount, false);
        p.pitch = axisOrNone(1, axisCount, false);
        p.throttle = axisOrNone(2, axisCount, false);
        p.yaw = axisOrNone(3, axisCount, false);
        p.deadband = 0.01F;
        p.throttleMode = ThrottleMode.FULL_RANGE;
        if (axisCount > AUX_ARM) {
            p.setBinding(SwitchFunction.ARM, high(AUX_ARM));
        }
        if (axisCount > AUX_MODE) {
            p.setBinding(SwitchFunction.ANGLE, high(AUX_MODE));
            p.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisMicros(AUX_MODE, SWITCH_MID_LOW_US, SWITCH_MID_HIGH_US));
        }
        if (axisCount > AUX_BEEPER) {
            p.setBinding(SwitchFunction.BEEPER, high(AUX_BEEPER));
        }
        if (axisCount > AUX_RESCUE) {
            p.setBinding(SwitchFunction.RESCUE, high(AUX_RESCUE));
        }
        return p;
    }

    public static DeviceProfile gamepad(String name) {
        DeviceProfile p = new DeviceProfile();
        p.name = name == null ? "" : name;
        p.gamepadLayout = true;
        p.roll = new ChannelMapping(PAD_RIGHT_X, false);
        p.pitch = new ChannelMapping(PAD_RIGHT_Y, true);
        p.yaw = new ChannelMapping(PAD_LEFT_X, false);
        p.throttle = new ChannelMapping(PAD_LEFT_Y, true);
        p.deadband = 0.08F;
        p.throttleMode = ThrottleMode.UPPER_HALF;
        p.throttleRate = 0.8F;
        p.setBinding(SwitchFunction.ARM, SwitchAssignment.toggleButton(PAD_RIGHT_SHOULDER));
        p.setBinding(SwitchFunction.ANGLE, SwitchAssignment.toggleButton(PAD_LEFT_SHOULDER).startingOn());
        p.setBinding(SwitchFunction.FLIP_OVER, SwitchAssignment.button(PAD_WEST, false));
        p.setBinding(SwitchFunction.RESCUE, SwitchAssignment.toggleButton(PAD_EAST));
        p.setBinding(SwitchFunction.BEEPER, SwitchAssignment.toggleButton(PAD_NORTH));
        p.setBinding(SwitchFunction.SET_HOME, SwitchAssignment.button(PAD_START, false));
        p.setBinding(SwitchFunction.RACE_RESTART, SwitchAssignment.button(PAD_BACK, false));
        return p;
    }

    private static SwitchAssignment high(int axis) {
        return SwitchAssignment.axisMicros(axis, SWITCH_HIGH_LOW_US, ChannelMath.RANGE_MAX_US);
    }

    public static void migrate(DeviceProfile profile, int fromVersion) {
        if (fromVersion < 2 && profile.calibrated()) {
            profile.setupDone = true;
        }
        if (fromVersion < 1 && profile.gamepadLayout) {
            SwitchAssignment rescue = profile.binding(SwitchFunction.RESCUE);
            if (rescue.equals(SwitchAssignment.button(PAD_EAST, false))) {
                profile.setBinding(SwitchFunction.RESCUE, SwitchAssignment.toggleButton(PAD_EAST));
            }
            SwitchAssignment angle = profile.binding(SwitchFunction.ANGLE);
            if (angle.equals(SwitchAssignment.toggleButton(PAD_LEFT_SHOULDER))) {
                profile.setBinding(SwitchFunction.ANGLE, SwitchAssignment.toggleButton(PAD_LEFT_SHOULDER).startingOn());
            }
        }
        if (fromVersion < 2) {
            for (SwitchAssignment binding : profile.switches.values()) {
                binding.migrateLegacyInvert();
            }
        }
    }

    public static DeviceProfile reset(DeviceProfile current, boolean gamepadCapable, int axisCount) {
        boolean pad = current.gamepadLayout && gamepadCapable;
        return pad ? gamepad(current.name) : radio(current.name, axisCount);
    }

    private static ChannelMapping axisOrNone(int axis, int axisCount, boolean invert) {
        return axis < axisCount ? new ChannelMapping(axis, invert) : ChannelMapping.unassigned();
    }
}

package de.lardtom.propwash.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum SwitchFunction {
    ARM,
    ANGLE,
    HORIZON,
    RESCUE,
    FLIP_OVER,
    SET_HOME,
    BEEPER,
    RACE_RESTART,
    RECORD,
    LEDS_OFF,
    ALT_HOLD,
    POS_HOLD,
    CARGO_DROP,
    MODE_3D,
    BLACKBOX,
    COMMAND_1,
    COMMAND_2,
    COMMAND_3,
    COMMAND_4,
    COMMAND_5,
    COMMAND_6,
    COMMAND_7,
    COMMAND_8;

    public static final int COMMAND_COUNT = 8;
    private static final SwitchFunction[] VALUES = values();

    public String translationKey() {
        return "propwash.input.switch." + this.name().toLowerCase(Locale.ROOT);
    }

    public boolean command() {
        return this.ordinal() >= COMMAND_1.ordinal();
    }

    public int commandIndex() {
        return this.command() ? this.ordinal() - COMMAND_1.ordinal() : -1;
    }

    public static SwitchFunction command(int index) {
        if (index < 0 || index >= COMMAND_COUNT) {
            throw new IllegalArgumentException("Befehl " + index);
        }
        return VALUES[COMMAND_1.ordinal() + index];
    }

    public static List<SwitchFunction> flightFunctions() {
        List<SwitchFunction> out = new ArrayList<>();
        for (SwitchFunction function : values()) {
            if (!function.command()) {
                out.add(function);
            }
        }
        return out;
    }

    public static List<SwitchFunction> commands() {
        List<SwitchFunction> out = new ArrayList<>();
        for (int i = 0; i < COMMAND_COUNT; i++) {
            out.add(command(i));
        }
        return out;
    }

    public boolean supportsToggle() {
        return switch (this) {
            case ARM, ANGLE, HORIZON, RESCUE, BEEPER, RECORD, LEDS_OFF, ALT_HOLD, POS_HOLD, MODE_3D, BLACKBOX -> true;
            case FLIP_OVER, SET_HOME, RACE_RESTART, CARGO_DROP -> false;
            case COMMAND_1, COMMAND_2, COMMAND_3, COMMAND_4, COMMAND_5, COMMAND_6, COMMAND_7, COMMAND_8 -> true;
        };
    }

    public boolean latchesOnGamepad() {
        return this == ARM || this == ANGLE || this == HORIZON || this == RESCUE || this == BEEPER || this == RECORD
                || this == LEDS_OFF || this == ALT_HOLD || this == POS_HOLD || this == MODE_3D || this == BLACKBOX;
    }
}

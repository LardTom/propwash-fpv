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
    RESET,
    RECORD,
    LEDS_OFF,
    ALT_HOLD,
    POS_HOLD,
    CARGO_DROP,
    MODE_3D,
    BLACKBOX,
    VTX_POWER,
    TURTLE,
    ACCESSORY_1,
    ACCESSORY_2,
    ACCESSORY_3,
    ACCESSORY_4,
    COMMAND_1,
    COMMAND_2,
    COMMAND_3,
    COMMAND_4,
    COMMAND_5,
    COMMAND_6,
    COMMAND_7,
    COMMAND_8;

    public static final int COMMAND_COUNT = 8;
    public static final int ACCESSORY_COUNT = 4;
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

    public boolean accessoryAction() {
        return this.ordinal() >= ACCESSORY_1.ordinal() && this.ordinal() <= ACCESSORY_4.ordinal();
    }

    public int accessoryNumber() {
        return this.accessoryAction() ? this.ordinal() - ACCESSORY_1.ordinal() + 1 : 0;
    }

    public static SwitchFunction accessory(int number) {
        if (number < 1 || number > ACCESSORY_COUNT) {
            throw new IllegalArgumentException("Zubehör-Aktion " + number);
        }
        return VALUES[ACCESSORY_1.ordinal() + number - 1];
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
            case ARM, ANGLE, HORIZON, RESCUE, BEEPER, RECORD, LEDS_OFF, ALT_HOLD, POS_HOLD, MODE_3D, BLACKBOX, TURTLE -> true;
            case FLIP_OVER, SET_HOME, RESET, CARGO_DROP, VTX_POWER -> false;
            case ACCESSORY_1, ACCESSORY_2, ACCESSORY_3, ACCESSORY_4 -> false;
            case COMMAND_1, COMMAND_2, COMMAND_3, COMMAND_4, COMMAND_5, COMMAND_6, COMMAND_7, COMMAND_8 -> true;
        };
    }

    public boolean latchesOnGamepad() {
        return this == ARM || this == ANGLE || this == HORIZON || this == RESCUE || this == BEEPER || this == RECORD
                || this == LEDS_OFF || this == ALT_HOLD || this == POS_HOLD || this == MODE_3D || this == BLACKBOX || this == TURTLE;
    }
}

package de.lardtom.propwash.race;

public record GateSignal(boolean enabled, int pulseTicks, boolean raceOnly) {
    public static final int MIN_PULSE = 2;
    public static final int MAX_PULSE = 100;
    public static final int DEFAULT_PULSE = 10;
    public static final int STRENGTH = 15;
    public static final int TICKS_PER_SECOND = 20;
    public static final GateSignal DEFAULT = new GateSignal(true, DEFAULT_PULSE, false);
    private static final int PULSE_MASK = 0x7F;
    private static final int OFF_BIT = 0x80;
    private static final int RACE_BIT = 0x100;

    public GateSignal {
        pulseTicks = clampPulse(pulseTicks);
    }

    public static int clampPulse(int ticks) {
        return Math.clamp(ticks, MIN_PULSE, MAX_PULSE);
    }

    public int pack() {
        return this.pulseTicks | (this.enabled ? 0 : OFF_BIT) | (this.raceOnly ? RACE_BIT : 0);
    }

    public static GateSignal unpack(int packed) {
        return new GateSignal((packed & OFF_BIT) == 0, packed & PULSE_MASK, (packed & RACE_BIT) != 0);
    }

    public GateSignal withEnabled(boolean value) {
        return new GateSignal(value, this.pulseTicks, this.raceOnly);
    }

    public GateSignal withPulse(int ticks) {
        return new GateSignal(this.enabled, ticks, this.raceOnly);
    }

    public GateSignal withRaceOnly(boolean value) {
        return new GateSignal(this.enabled, this.pulseTicks, value);
    }

    public boolean fires(boolean courseGate, boolean startGate, boolean racing) {
        if (!this.enabled) {
            return false;
        }
        return !this.raceOnly || courseGate && (startGate || racing);
    }

    public double seconds() {
        return this.pulseTicks / (double) TICKS_PER_SECOND;
    }

    public static String secondsText(int ticks) {
        int hundredths = ticks * (100 / TICKS_PER_SECOND);
        String text = (hundredths / 100) + "." + (hundredths % 100 < 10 ? "0" : "") + (hundredths % 100);
        if (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}

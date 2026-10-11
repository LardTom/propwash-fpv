package de.lardtom.propwash.blackbox;

import java.util.Locale;

public enum BlackboxRate {
    FULL(1),
    HALF(2),
    QUARTER(4),
    EIGHTH(8);

    private final int denominator;

    BlackboxRate(int denominator) {
        this.denominator = denominator;
    }

    public int denominator() {
        return this.denominator;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public int hz(int pidLoopHz) {
        return Math.max(1, pidLoopHz / this.denominator);
    }
}

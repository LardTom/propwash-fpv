package de.lardtom.propwash.ui;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record TuneParam(String key, Kind kind, double min, double max, double step, int decimals, double displayScale,
                        String unit, @Nullable Class<? extends Enum<?>> enumType, boolean allowOff) {
    public enum Kind {
        NUMBER,
        ENUM,
        BOOL
    }

    public static TuneParam number(String key, double min, double max, double step, int decimals, String unit) {
        return new TuneParam(key, Kind.NUMBER, min, max, step, decimals, 1.0, unit, null, false);
    }

    public static TuneParam scaled(String key, double min, double max, double step, int decimals, double displayScale, String unit) {
        return new TuneParam(key, Kind.NUMBER, min, max, step, decimals, displayScale, unit, null, false);
    }

    public static TuneParam filter(String key, double min, double max, double step) {
        return new TuneParam(key, Kind.NUMBER, min, max, step, 0, 1.0, "Hz", null, true);
    }

    public static TuneParam choice(String key, Class<? extends Enum<?>> type) {
        int count = type.getEnumConstants().length;
        return new TuneParam(key, Kind.ENUM, 0, count - 1, 1, 0, 1.0, "", type, false);
    }

    public static TuneParam toggle(String key) {
        return new TuneParam(key, Kind.BOOL, 0, 1, 1, 0, 1.0, "", null, false);
    }

    public double sanitize(double value) {
        if (!Double.isFinite(value)) {
            return this.min;
        }
        if (this.kind != Kind.NUMBER) {
            return Math.max(this.min, Math.min(this.max, Math.rint(value)));
        }
        if (this.allowOff && value < this.min - this.step * 0.5) {
            return 0.0;
        }
        double clamped = Math.max(this.min, Math.min(this.max, value));
        double steps = Math.rint((clamped - this.min) / this.step);
        double snapped = this.min + steps * this.step;
        double rounded = round(snapped, this.decimals + 3);
        return Math.max(this.min, Math.min(this.max, rounded));
    }

    public double sliderMin() {
        return this.allowOff ? this.min - this.step : this.min;
    }

    public double stepBy(double value, int steps) {
        if (this.kind != Kind.NUMBER) {
            return this.sanitize(value + steps);
        }
        if (this.allowOff && value <= 0.0) {
            return steps > 0 ? this.sanitize(this.min + (steps - 1) * this.step) : 0.0;
        }
        double next = value + steps * this.step;
        if (this.allowOff && next < this.min - this.step * 0.5) {
            return 0.0;
        }
        return this.sanitize(next);
    }

    public boolean isOff(double value) {
        return this.allowOff && value <= 0.0;
    }

    public double display(double value) {
        return value * this.displayScale;
    }

    public double fromDisplay(double shown) {
        return shown / this.displayScale;
    }

    public int displayDecimals() {
        double shownStep = this.step * this.displayScale;
        int d = 0;
        while (d < 4 && Math.abs(shownStep * Math.pow(10, d) - Math.rint(shownStep * Math.pow(10, d))) > 1e-6) {
            d++;
        }
        return Math.max(d, this.decimals);
    }

    public String formatNumber(double value) {
        double shown = this.display(value);
        int d = this.displayDecimals();
        return String.format(java.util.Locale.ROOT, "%." + d + "f", shown);
    }

    public List<? extends Enum<?>> constants() {
        if (this.enumType == null) {
            return List.of();
        }
        return List.of(this.enumType.getEnumConstants());
    }

    public boolean sameValue(double a, double b) {
        return Math.abs(a - b) < Math.max(1e-6, this.step * 1e-3);
    }

    private static double round(double value, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.rint(value * f) / f;
    }
}

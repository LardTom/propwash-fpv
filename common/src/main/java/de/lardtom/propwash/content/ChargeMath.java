package de.lardtom.propwash.content;

import de.lardtom.propwash.build.BatteryType;

public final class ChargeMath {
    public static final int INTERVAL = 10;
    public static final int TICKS_PER_SECOND = 20;
    public static final double REFERENCE_CAPACITY_MAH = 1300.0;
    public static final Cfg DEFAULTS = new Cfg(90.0, 0.5, 20.0, 2.0);

    public record Cfg(double referenceSeconds, double capacityExponent, double minSeconds, double redstoneMultiplier) {
        public Cfg {
            referenceSeconds = clamp(referenceSeconds, 10.0, 3600.0, 90.0);
            capacityExponent = clamp(capacityExponent, 0.0, 1.0, 0.5);
            minSeconds = clamp(minSeconds, 1.0, 3600.0, 20.0);
            redstoneMultiplier = clamp(redstoneMultiplier, 1.0, 10.0, 2.0);
        }
    }

    private ChargeMath() {
    }

    public static double seconds(BatteryType type, Cfg cfg) {
        double scaled = cfg.referenceSeconds() * Math.pow(type.capacityMah / REFERENCE_CAPACITY_MAH, cfg.capacityExponent());
        return Math.max(cfg.minSeconds(), scaled);
    }

    public static double effectiveSeconds(BatteryType type, boolean powered, Cfg cfg) {
        return seconds(type, cfg) / (powered ? cfg.redstoneMultiplier() : 1.0);
    }

    public static float step(BatteryType type, int intervalTicks, boolean powered, Cfg cfg) {
        double perTick = 1.0 / (TICKS_PER_SECOND * seconds(type, cfg));
        return (float) (Math.max(0, intervalTicks) * perTick * (powered ? cfg.redstoneMultiplier() : 1.0));
    }

    public static float advance(float charge, BatteryType type, int intervalTicks, boolean powered, Cfg cfg) {
        return Batteries.clamp(Batteries.clamp(charge) + step(type, intervalTicks, powered, cfg));
    }

    public static int remainingSeconds(float charge, BatteryType type, boolean powered, Cfg cfg) {
        float c = Batteries.clamp(charge);
        if (c >= Batteries.FULL) {
            return 0;
        }
        return (int) Math.ceil((1.0 - c) * effectiveSeconds(type, powered, cfg));
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}

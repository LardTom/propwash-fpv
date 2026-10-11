package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.PropDefinition;

import java.util.Locale;

public record PropFailure(double brittleness, double shatter) {
    public static final double DUCTILE_STIFFNESS = 0.95;
    public static final double BRITTLE_STIFFNESS = 1.2;
    public static final double SHATTER_FROM_STIFFNESS = 1.3;
    public static final double SHATTER_FULL_STIFFNESS = 2.2;
    public static final double MIXED_FROM = 0.3;
    public static final double CRACKS_FROM = 0.7;
    public static final double SHATTERS_FROM = 0.5;
    public static final PropFailure DUCTILE = new PropFailure(0.0, 0.0);

    public PropFailure {
        brittleness = unit(brittleness);
        shatter = unit(shatter);
    }

    public static PropFailure of(PropDefinition prop) {
        return of(prop.stiffness(), prop.basics().durability());
    }

    public static PropFailure of(double stiffness, double durability) {
        if (!(stiffness > 0.0) || !Double.isFinite(stiffness)) {
            return DUCTILE;
        }
        double tough = Double.isFinite(durability) && durability > 0.0 ? Math.sqrt(durability) : 1.0;
        double shatter = SimMath.smoothstep(SHATTER_FROM_STIFFNESS, SHATTER_FULL_STIFFNESS, stiffness);
        double brittle = Math.max(shatter, SimMath.smoothstep(DUCTILE_STIFFNESS, BRITTLE_STIFFNESS, stiffness) / tough);
        return new PropFailure(brittle, shatter);
    }

    public double bendShare() {
        return 1.0 - this.brittleness;
    }

    public double crackShare() {
        return this.brittleness * (1.0 - this.shatter);
    }

    public double shatterShare() {
        return this.brittleness * this.shatter;
    }

    public Mode mode() {
        if (this.shatterShare() >= SHATTERS_FROM) {
            return Mode.SHATTERS;
        }
        if (this.brittleness >= CRACKS_FROM) {
            return Mode.CRACKS;
        }
        return this.brittleness >= MIXED_FROM ? Mode.BENDS_OR_CRACKS : Mode.BENDS;
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }

    public enum Mode {
        BENDS,
        BENDS_OR_CRACKS,
        CRACKS,
        SHATTERS;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}

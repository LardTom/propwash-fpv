package de.lardtom.propwash.drone.dust;

public final class DustModel {
    public static final double AIR_DENSITY = 1.225;
    public static final double PICKUP_SPEED = 3.0;
    public static final double FULL_EXCESS_SPEED = 20.0;
    public static final double JET_DECAY_DIAMETERS = 2.0;
    public static final double MIN_REACH = 0.3;
    public static final double MAX_REACH = 2.0;
    public static final double REFERENCE_JET_DIAMETER = 0.25;
    public static final double MIN_SIZE_FACTOR = 0.5;
    public static final double MAX_SIZE_FACTOR = 2.0;
    public static final double BASE_RATE = 142.0;
    public static final double AMOUNT_EXPONENT = 1.5;
    public static final double MIN_VERTICALITY = 0.2;
    public static final double SPRAY_FACTOR = 0.6;
    public static final int MAX_PER_TICK = 8;
    public static final int ENGINE_LIMIT = 97;
    public static final double CAMERA_RANGE = 32.0;
    public static final float MIN_INTENSITY = 0.1F;
    public static final float MAX_INTENSITY = 1.0F;
    public static final float DEFAULT_INTENSITY = 0.4F;
    public static final double FADE_IN = 0.15;
    public static final double FADE_OUT_START = 0.4;
    public static final int NEUTRAL_COLOR = 0xB8B2A8;
    public static final int SOIL_COLOR = 0x866A4C;
    public static final int SPRAY_COLOR = 0xE4EEF8;

    public enum Surface { NONE, SOLID, WATER }

    public enum Material {
        LOOSE(1.0), SOIL(0.8), COVERED(0.55), HARD(0.3), SLICK(0.15);

        public final double dustiness;

        Material(double dustiness) {
            this.dustiness = dustiness;
        }
    }

    public enum Detail {
        ALL(1.0, 64), DECREASED(0.4, 24), MINIMAL(0.0, 0);

        public final double rateFactor;
        public final int cap;

        Detail(double rateFactor, int cap) {
            this.rateFactor = rateFactor;
            this.cap = cap;
        }
    }

    public record Wash(double reach, double distance, double strength, double groundSpeed, double ratePerSecond) {
        public static final Wash NONE = new Wash(0.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0);

        public boolean active() {
            return this.ratePerSecond > 0.0;
        }
    }

    private DustModel() {
    }

    public static double jetDiameter(double discAreaPerRotor, int rotors) {
        if (!(discAreaPerRotor > 0.0) || rotors <= 0) {
            return 0.0;
        }
        return Math.sqrt(4.0 * rotors * discAreaPerRotor / Math.PI);
    }

    public static double jetSpeed(double thrustNewtons, double jetDiameter) {
        if (!(thrustNewtons > 0.0) || !(jetDiameter > 0.0)) {
            return 0.0;
        }
        double area = Math.PI * jetDiameter * jetDiameter / 4.0;
        return 2.0 * Math.sqrt(thrustNewtons / (2.0 * AIR_DENSITY * area));
    }

    public static double reach(double jetSpeed, double jetDiameter) {
        if (!(jetSpeed > PICKUP_SPEED) || !(jetDiameter > 0.0)) {
            return 0.0;
        }
        double raw = JET_DECAY_DIAMETERS * jetDiameter * Math.sqrt(jetSpeed / PICKUP_SPEED - 1.0);
        return Math.clamp(raw, MIN_REACH, MAX_REACH);
    }

    public static double alongJet(double height, double verticality) {
        if (!(verticality >= MIN_VERTICALITY) || Double.isNaN(height)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0, height) / Math.min(1.0, verticality);
    }

    public static double groundSpeed(double jetSpeed, double jetDiameter, double distance) {
        if (!(jetSpeed > 0.0) || !(jetDiameter > 0.0) || !Double.isFinite(distance)) {
            return 0.0;
        }
        double relative = Math.max(0.0, distance) / (JET_DECAY_DIAMETERS * jetDiameter);
        return jetSpeed / (1.0 + relative * relative);
    }

    public static double strength(double distance, double reach, double jetSpeed) {
        if (!(reach > 0.0) || !(distance < reach)) {
            return 0.0;
        }
        double excess = Math.clamp((jetSpeed - PICKUP_SPEED) / FULL_EXCESS_SPEED, 0.0, 1.0);
        double closeness = 1.0 - Math.max(0.0, distance) / reach;
        return excess * closeness * closeness;
    }

    public static double sizeFactor(double jetDiameter) {
        if (!(jetDiameter > 0.0)) {
            return 0.0;
        }
        return Math.clamp(jetDiameter / REFERENCE_JET_DIAMETER, MIN_SIZE_FACTOR, MAX_SIZE_FACTOR);
    }

    public static double surfaceFactor(Surface surface, Material material) {
        if (surface == null || surface == Surface.NONE) {
            return 0.0;
        }
        if (surface == Surface.WATER) {
            return SPRAY_FACTOR;
        }
        return material == null ? Material.HARD.dustiness : material.dustiness;
    }

    public static double clampIntensity(double intensity) {
        if (!Double.isFinite(intensity)) {
            return DEFAULT_INTENSITY;
        }
        return Math.clamp(intensity, MIN_INTENSITY, MAX_INTENSITY);
    }

    public static double rate(double strength, double jetDiameter, Surface surface, Material material, double intensity, Detail detail) {
        if (!(strength > 0.0) || detail == null) {
            return 0.0;
        }
        return BASE_RATE * sizeFactor(jetDiameter) * Math.min(1.0, strength) * surfaceFactor(surface, material)
                * amount(intensity) * detail.rateFactor;
    }

    public static double amount(double intensity) {
        return Math.pow(clampIntensity(intensity), AMOUNT_EXPONENT);
    }

    public static float visualScale(double intensity) {
        return (float) (1.0 + 0.5 * (clampIntensity(intensity) - DEFAULT_INTENSITY));
    }

    public static double probeDepth(double reach, double verticality) {
        if (!(reach > 0.0) || !(verticality >= MIN_VERTICALITY)) {
            return 0.0;
        }
        return reach * Math.min(1.0, verticality);
    }

    public static Wash evaluate(double height, double verticality, double thrustNewtons, double jetDiameter,
                                Surface surface, Material material, double intensity, Detail detail) {
        double speed = jetSpeed(thrustNewtons, jetDiameter);
        double reach = reach(speed, jetDiameter);
        double distance = alongJet(height, verticality);
        double strength = strength(distance, reach, speed);
        if (!(strength > 0.0)) {
            return new Wash(reach, distance, 0.0, 0.0, 0.0);
        }
        return new Wash(reach, distance, strength, groundSpeed(speed, jetDiameter, distance),
                rate(strength, jetDiameter, surface, material, intensity, detail));
    }

    public static int allowed(int requested, int live, Detail detail) {
        if (requested <= 0 || detail == null) {
            return 0;
        }
        int cap = Math.min(detail.cap, ENGINE_LIMIT);
        return Math.max(0, Math.min(requested, cap - Math.max(0, live)));
    }

    public static double spawnRadius(double jetDiameter, double distance) {
        double d = Math.max(0.0, jetDiameter);
        double along = Double.isFinite(distance) ? Math.max(0.0, distance) : 0.0;
        return Math.clamp(0.5 * d + 0.25 * along, 0.1, 0.9);
    }

    public static double radialSpeed(double groundSpeed) {
        double speed = Double.isFinite(groundSpeed) ? Math.max(0.0, groundSpeed) : 0.0;
        return Math.clamp(0.02 + speed * 0.007, 0.02, 0.12);
    }

    public static float alpha(double ageFraction, float peak) {
        if (!(ageFraction > 0.0) || ageFraction >= 1.0) {
            return 0.0F;
        }
        double in = Math.min(1.0, ageFraction / FADE_IN);
        double out = ageFraction <= FADE_OUT_START ? 1.0 : 1.0 - (ageFraction - FADE_OUT_START) / (1.0 - FADE_OUT_START);
        return (float) (Math.max(0.0F, peak) * in * out);
    }

    public static float growth(double ageFraction) {
        double t = Double.isFinite(ageFraction) ? Math.clamp(ageFraction, 0.0, 1.0) : 0.0;
        return (float) (0.7 + 0.8 * t);
    }

    public static int spriteFor(int start, double ageFraction, int spriteCount) {
        int last = Math.max(1, spriteCount - 1);
        int first = Math.clamp(start, 1, last);
        double t = Double.isFinite(ageFraction) ? Math.clamp(ageFraction, 0.0, 1.0) : 0.0;
        return Math.max(1, first - (int) (t * (first - 1)));
    }

    public static int color(int rgb, Material material) {
        int base = (rgb & 0xFFFFFF) == 0 ? NEUTRAL_COLOR : rgb & 0xFFFFFF;
        if (material == Material.COVERED) {
            base = mix(base, SOIL_COLOR, 0.3);
        }
        int r = base >> 16 & 0xFF;
        int g = base >> 8 & 0xFF;
        int b = base & 0xFF;
        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
        return channel(r, luma) << 16 | channel(g, luma) << 8 | channel(b, luma);
    }

    private static int channel(int value, double luma) {
        double desaturated = value + (luma - value) * 0.1;
        double lifted = desaturated + (255.0 - desaturated) * 0.04;
        return (int) Math.round(Math.clamp(lifted, 0.0, 255.0));
    }

    static int mix(int a, int b, double t) {
        double f = Math.clamp(t, 0.0, 1.0);
        int r = (int) Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * f);
        int g = (int) Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * f);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return r << 16 | g << 8 | bl;
    }
}

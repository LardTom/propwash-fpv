package de.lardtom.propwash.audio;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record SoundReach(double shaftPowerW, double tipMach, double propDiameterMm, double rotorLevelDb, double levelDb, double hoverRangeBlocks,
                         double hoverAmplitude) {
    public static final double REFERENCE_LEVEL_DB = 80.0;
    public static final double REFERENCE_POWER_W = 60.0;
    public static final double REFERENCE_TIP_MACH = 0.21;
    public static final double REFERENCE_PROP_MM = 130.0;
    public static final double MACH_EXPONENT = 3.0;
    public static final double SIZE_SLOPE_DB = 18.0;
    public static final double ROTOR_SHARE = 0.5;
    public static final double ROTOR_BAND_DB = 1.0;
    public static final double THRESHOLD_DB = 43.0;
    public static final double NEAR_BLOCKS = 3.0;
    public static final double FADE_START = 0.5;
    public static final double THROTTLE_EXPONENT = 0.5;
    public static final double MIN_RANGE_BLOCKS = 4.0;
    public static final double MAX_RANGE_BLOCKS = 160.0;
    public static final double CLOSE_CUTOFF_HZ = 14_000.0;
    public static final double FAR_CUTOFF_HZ = 1_800.0;

    private static final double SPEED_OF_SOUND = 343.0;
    private static final double ABSORPTION_8K_DB_PER_M = 0.117;
    private static final double ABSORPTION_SLOPE = 1.8;
    private static final double CUTOFF_DROP_DB = 3.0;
    private static final double MIN_AMPLITUDE = 1.0e-4;
    private static final Map<DroneBuild, SoundReach> CACHE = new ConcurrentHashMap<>();

    public static final SoundReach REFERENCE = fromRotor(REFERENCE_POWER_W, REFERENCE_TIP_MACH, REFERENCE_PROP_MM, amplitude(10_500.0, 34_000.0, 0.23));

    public static SoundReach of(DroneBuild build) {
        if (build == null) {
            return REFERENCE;
        }
        SoundReach cached = CACHE.get(build);
        if (cached != null) {
            return cached;
        }
        SoundReach derived;
        try {
            derived = derive(build);
        } catch (RuntimeException e) {
            derived = REFERENCE;
        }
        CACHE.put(build, derived);
        return derived;
    }

    static SoundReach derive(DroneBuild build) {
        AirframeSpec spec = AirframeSpec.of(build);
        MotorTimbre timbre = MotorTimbre.of(build);
        double omega = spec.hoverOmega;
        double power = AirframeSpec.MOTORS * spec.kTorque * omega * omega * omega;
        double mach = omega * spec.propRadius / SPEED_OF_SOUND;
        if (!Double.isFinite(power) || power <= 0.0 || !Double.isFinite(mach) || mach <= 0.0) {
            return REFERENCE;
        }
        double hoverAmplitude = amplitude(timbre.hoverRpm(), timbre.maxRpm(), timbre.hoverOutput());
        return fromRotor(power, mach, spec.propDiameter * 1000.0, hoverAmplitude);
    }

    public static SoundReach fromRotor(double shaftPowerW, double tipMach, double propDiameterMm, double hoverAmplitude) {
        double rotor = levelDb(shaftPowerW, tipMach);
        double diameter = Double.isFinite(propDiameterMm) && propDiameterMm > 1.0 ? propDiameterMm : REFERENCE_PROP_MM;
        double level = sizedLevelDb(rotor, diameter);
        double amplitude = Double.isFinite(hoverAmplitude) && hoverAmplitude > MIN_AMPLITUDE ? hoverAmplitude : MIN_AMPLITUDE;
        return new SoundReach(shaftPowerW, tipMach, diameter, rotor, level, rangeForLevel(level), amplitude);
    }

    public static double sizeLevelDb(double propDiameterMm) {
        double diameter = Double.isFinite(propDiameterMm) && propDiameterMm > 1.0 ? propDiameterMm : REFERENCE_PROP_MM;
        return REFERENCE_LEVEL_DB + SIZE_SLOPE_DB * Math.log10(diameter / REFERENCE_PROP_MM);
    }

    public static double sizedLevelDb(double rotorLevelDb, double propDiameterMm) {
        double size = sizeLevelDb(propDiameterMm);
        double rotor = Double.isFinite(rotorLevelDb) ? rotorLevelDb : size;
        return size + clamp(ROTOR_SHARE * (rotor - size), -ROTOR_BAND_DB, ROTOR_BAND_DB);
    }

    public static double levelDb(double shaftPowerW, double tipMach) {
        double power = Double.isFinite(shaftPowerW) && shaftPowerW > 1.0e-6 ? shaftPowerW : 1.0e-6;
        double mach = Double.isFinite(tipMach) && tipMach > 1.0e-4 ? tipMach : 1.0e-4;
        return REFERENCE_LEVEL_DB + 10.0 * Math.log10(power / REFERENCE_POWER_W)
                + 10.0 * MACH_EXPONENT * Math.log10(mach / REFERENCE_TIP_MACH);
    }

    public static double levelAt(double levelDb, double distance) {
        return levelDb - 20.0 * Math.log10(Math.max(1.0, distance));
    }

    public static double rangeForLevel(double levelDb) {
        return Math.pow(10.0, (levelDb - THRESHOLD_DB) / 20.0);
    }

    public double rangeBlocks(double amplitude) {
        double a = Double.isFinite(amplitude) && amplitude > 0.0 ? amplitude : 0.0;
        double scaled = this.hoverRangeBlocks * Math.pow(a / this.hoverAmplitude, THROTTLE_EXPONENT);
        return clamp(scaled, MIN_RANGE_BLOCKS, MAX_RANGE_BLOCKS);
    }

    public double hoverRange() {
        return clamp(this.hoverRangeBlocks, MIN_RANGE_BLOCKS, MAX_RANGE_BLOCKS);
    }

    public static double amplitude(double rpm, double maxRpm, double load) {
        double fraction = Double.isFinite(rpm) && rpm > 0.0 ? Math.min(1.0, rpm / Math.max(1000.0, maxRpm)) : 0.0;
        double l = Double.isFinite(load) ? clamp(load, 0.0, 1.0) : 0.0;
        return Math.pow(fraction, 0.6) * (0.6 + 0.4 * l);
    }

    public static double attenuation(double distance, double range) {
        if (!Double.isFinite(distance) || !Double.isFinite(range) || range <= 0.0) {
            return 0.0;
        }
        double d = Math.max(0.0, distance);
        if (d >= range) {
            return 0.0;
        }
        double near = d <= NEAR_BLOCKS ? 1.0 : NEAR_BLOCKS / d;
        double x = d / range;
        if (x <= FADE_START) {
            return near;
        }
        double t = (x - FADE_START) / (1.0 - FADE_START);
        return near * (1.0 - t * t * (3.0 - 2.0 * t));
    }

    public static double airCutoffHz(double distance) {
        if (!Double.isFinite(distance) || distance <= 1.0) {
            return CLOSE_CUTOFF_HZ;
        }
        double ratio = CUTOFF_DROP_DB / (ABSORPTION_8K_DB_PER_M * distance);
        return clamp(8_000.0 * Math.pow(ratio, 1.0 / ABSORPTION_SLOPE), FAR_CUTOFF_HZ, CLOSE_CUTOFF_HZ);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

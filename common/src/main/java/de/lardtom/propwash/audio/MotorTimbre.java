package de.lardtom.propwash.audio;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.AirframeSpec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record MotorTimbre(
        int blades,
        int polePairs,
        double idleRpm,
        double hoverRpm,
        double maxRpm,
        double hoverOutput,
        double propDiameterM,
        boolean ducted,
        double maxCurrentA,
        float sizeClass,
        float loudness,
        float harmonicTilt,
        float rotorGain,
        float whineGain,
        float noiseGain,
        float rumbleGain,
        float ductGain,
        float ductHz,
        float detuneSpread) {
    private static final double RAD_PER_SEC_TO_RPM = 60.0 / (2.0 * Math.PI);
    private static final double SMALLEST_PROP_MM = 31.0;
    private static final double LARGEST_PROP_MM = 178.0;
    private static final double X_CLASS_PROP_MM = 330.0;
    private static final double IDLE_FRACTION = 0.075;
    private static final double LIPO_FULL_CELL = 4.2;
    private static final Map<DroneBuild, MotorTimbre> CACHE = new ConcurrentHashMap<>();

    public static final MotorTimbre DEFAULT = derive(DroneBuild.FREESTYLE);

    public static MotorTimbre of(DroneBuild build) {
        if (build == null) {
            return DEFAULT;
        }
        MotorTimbre cached = CACHE.get(build);
        if (cached != null) {
            return cached;
        }
        MotorTimbre derived;
        try {
            derived = derive(build);
        } catch (RuntimeException e) {
            derived = DEFAULT;
        }
        CACHE.put(build, derived);
        return derived;
    }

    static MotorTimbre derive(DroneBuild build) {
        MotorType motor = build.motor();
        PropType prop = build.prop();
        double unloadedRpm = motor.kv * Math.max(1, build.battery().cells) * LIPO_FULL_CELL;
        double hoverRpm = Double.NaN;
        double thrustToWeight = Double.NaN;
        double hoverOutput = Double.NaN;
        try {
            AirframeSpec params = AirframeSpec.of(build);
            unloadedRpm = params.omegaMax * RAD_PER_SEC_TO_RPM;
            hoverRpm = params.hoverOmega * RAD_PER_SEC_TO_RPM;
            BuildAnalysis analysis = BuildAnalysis.of(build);
            thrustToWeight = analysis.thrustToWeight();
            hoverOutput = analysis.hoverThrottlePercent() / 100.0;
        } catch (RuntimeException e) {
            hoverRpm = Double.NaN;
        }
        double idleRpm = unloadedRpm * IDLE_FRACTION;
        double maxRpm;
        if (Double.isFinite(hoverRpm) && hoverRpm > idleRpm && Double.isFinite(thrustToWeight) && thrustToWeight > 1.0) {
            maxRpm = Math.min(unloadedRpm * 0.92, hoverRpm * Math.sqrt(thrustToWeight));
        } else {
            maxRpm = unloadedRpm * 0.7;
        }
        if (!Double.isFinite(hoverRpm) || hoverRpm <= idleRpm || hoverRpm >= maxRpm) {
            hoverRpm = idleRpm + 0.4 * (maxRpm - idleRpm);
        }
        if (!Double.isFinite(hoverOutput) || hoverOutput <= 0.02 || hoverOutput >= 0.98) {
            hoverOutput = 0.35;
        }
        float size = (float) clamp((prop.diameterMm - SMALLEST_PROP_MM) / (LARGEST_PROP_MM - SMALLEST_PROP_MM), 0.0, 1.0);
        int blades = Math.max(1, prop.blades);
        boolean ducted = build.frame().ducted;
        float loudness = (float) (0.42 + 0.58 * Math.sqrt(size));
        float tilt = 1.15F + 0.28F * (3 - blades) + 0.35F * size;
        float rotor = 0.05F + 0.10F * size + (blades == 2 ? 0.06F : 0.0F);
        float whine = 0.10F + 0.26F * (1.0F - size);
        float noise = 0.22F + 0.30F * size + (ducted ? 0.08F : 0.0F);
        float rumble = 0.08F + 0.55F * size;
        float duct = ducted ? 0.30F : 0.0F;
        float ductHz = (float) clamp(343.0 / (2.0 * (prop.diameterMeters() + 0.012)), 800.0, 7000.0);
        float big = (float) clamp((prop.diameterMm - LARGEST_PROP_MM) / (X_CLASS_PROP_MM - LARGEST_PROP_MM), 0.0, 1.0);
        if (big > 0.0F) {
            size += big;
            loudness += 0.25F * big;
            tilt += 0.35F * big;
            rotor += 0.04F * big;
            whine *= 1.0F - 0.5F * big;
            rumble += 0.35F * big;
        }
        return new MotorTimbre(blades, polePairs(motor), idleRpm, hoverRpm, maxRpm, hoverOutput, prop.diameterMeters(), ducted,
                Math.max(1.0, motor.maxCurrent), size, loudness, tilt, rotor, whine, noise, rumble, duct, ductHz, 0.0065F);
    }

    static int polePairs(MotorType motor) {
        return motor.definition().statorDiameterMm() >= 18.0 ? 7 : 6;
    }

    public double rpmForOutput(double output) {
        double o = Double.isFinite(output) ? clamp(output, 0.0, 1.0) : 0.0;
        if (o <= this.hoverOutput) {
            return this.idleRpm + (this.hoverRpm - this.idleRpm) * (o / this.hoverOutput);
        }
        return this.hoverRpm + (this.maxRpm - this.hoverRpm) * ((o - this.hoverOutput) / (1.0 - this.hoverOutput));
    }

    public double bladePassHz(double rpm) {
        return rpm / 60.0 * this.blades;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

package de.lardtom.propwash.sim;

final class Thermal {
    static final double MOTOR_HEAT_CAPACITY = 0.5;
    static final double ESC_HEAT_CAPACITY = 0.3;
    static final double OVERHEAT_C = 150.0;
    static final double HYSTERESIS_C = 10.0;
    static final double COPPER_TEMPCO = 0.00393;
    static final double MOTOR_CONDUCTANCE_BASE = 0.075;
    static final double MOTOR_CONDUCTANCE_PER_OMEGA = 1.5e-4;
    static final double MIN_POWER_LIMIT = 0.2;
    static final double WEAR_EVENT_STEP = 0.002;

    final double[] motorTemp = new double[AirframeSpec.MOTORS];
    final double[] escTemp = new double[AirframeSpec.MOTORS];
    final boolean[] overheated = new boolean[AirframeSpec.MOTORS];
    final boolean[] derating = new boolean[AirframeSpec.MOTORS];
    final double[] limit = {1.0, 1.0, 1.0, 1.0};
    final double[] wear = new double[AirframeSpec.MOTORS];

    void reset(double ambient) {
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.motorTemp[i] = ambient;
            this.escTemp[i] = ambient;
            this.overheated[i] = false;
            this.derating[i] = false;
            this.limit[i] = 1.0;
            this.wear[i] = 0.0;
        }
    }

    void advance(AirframeSpec p, PowerSystem power, double ambient, double dt, boolean effects) {
        double stackGrams = Math.max(p.stackMassKg * 1000.0, 0.5);
        double motorC = p.motorHeatCapacity;
        double escC = ESC_HEAT_CAPACITY * stackGrams / 4.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double w = Math.abs(power.omega[i]);
            double current = power.current[i];
            double motorR = effects ? this.motorResistance(p, i) : p.motorResistance;
            double pm = current * current * motorR + p.kt * p.idleCurrent * w;
            double gm = (p.motorCoolingBase + p.motorCoolingPerOmega * w) * p.motorCoolingScale;
            double infM = ambient + pm / gm;
            this.motorTemp[i] = SimMath.clamp(infM + (this.motorTemp[i] - infM) * StrictMath.exp(-dt * gm / motorC), -40.0, 250.0);
            double pe = current * current * p.escResistance;
            double ge = 0.02 * (stackGrams / 18.0) * (1.0 + w / p.hoverOmega);
            double infE = ambient + pe / ge;
            this.escTemp[i] = SimMath.clamp(infE + (this.escTemp[i] - infE) * StrictMath.exp(-dt * ge / escC), -40.0, 250.0);
        }
    }

    double motorResistance(AirframeSpec p, int i) {
        return p.motorResistance * (1.0 + COPPER_TEMPCO * (this.motorTemp[i] - 25.0));
    }

    static double excess(AirframeSpec p, double temperature) {
        return (temperature - p.motorDerateStartC) / (p.motorMaxTempC - p.motorDerateStartC);
    }

    static double powerLimit(AirframeSpec p, double temperature) {
        double x = excess(p, temperature);
        if (!(x > 0.0)) {
            return 1.0;
        }
        return 1.0 - (1.0 - MIN_POWER_LIMIT) * Math.min(1.0, x);
    }

    static double wearRate(AirframeSpec p, double temperature) {
        double x = excess(p, temperature);
        if (!(x > 0.0)) {
            return 0.0;
        }
        double c = Math.min(1.0, x);
        return p.motorHeatWearPerSecond * c * c;
    }

    void clearLimits() {
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.limit[i] = 1.0;
            this.derating[i] = false;
        }
    }
}

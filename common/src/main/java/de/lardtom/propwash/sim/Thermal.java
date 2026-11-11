package de.lardtom.propwash.sim;

final class Thermal {
    static final double MOTOR_HEAT_CAPACITY = 0.5;
    static final double ESC_HEAT_CAPACITY = 0.5;
    static final double OVERHEAT_C = 150.0;
    static final double HYSTERESIS_C = 10.0;
    static final double COPPER_TEMPCO = 0.00393;
    static final double MOTOR_CONDUCTANCE_BASE = 0.1;
    static final double MOTOR_CONDUCTANCE_PER_SPEED = 0.0105;
    static final double BELL_RADIUS_PER_STATOR = 0.625;
    static final double MOTOR_EDDY_LOSS = 0.35;
    static final double ESC_CONDUCTANCE = 0.0275;
    static final double ESC_REFERENCE_BOARD_MM = 36.0;
    static final double ESC_REFERENCE_GRAMS = 18.0;
    static final double ESC_BOARD_WHOOP_MM = 25.5;
    static final double ESC_BOARD_20_MM = 26.0;
    static final double ESC_PROP_WASH = 0.2;
    static final double ESC_DUCTED = 0.6;
    static final double ESC_SWITCHING = 0.002;
    static final double ESC_LIMIT_C = 140.0;
    static final double ESC_DERATE_SPAN_K = 15.0;
    static final double ESC_MIN_POWER_LIMIT = 0.25;
    static final double MAX_TORQUE_LOSS = 0.1;
    static final double AIRFLOW_COOLING = 0.6;
    static final double AIRFLOW_REFERENCE_MS = 10.0;
    static final double WEAR_EVENT_STEP = 0.002;

    final double[] motorTemp = new double[AirframeSpec.MOTORS];
    final double[] escTemp = new double[AirframeSpec.MOTORS];
    final boolean[] overheated = new boolean[AirframeSpec.MOTORS];
    final boolean[] motorHot = new boolean[AirframeSpec.MOTORS];
    final boolean[] escDerating = new boolean[AirframeSpec.MOTORS];
    final double[] limit = {1.0, 1.0, 1.0, 1.0};
    final double[] torque = {1.0, 1.0, 1.0, 1.0};
    final double[] wear = new double[AirframeSpec.MOTORS];

    void reset(double ambient) {
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.motorTemp[i] = ambient;
            this.escTemp[i] = ambient;
            this.overheated[i] = false;
            this.motorHot[i] = false;
            this.escDerating[i] = false;
            this.limit[i] = 1.0;
            this.torque[i] = 1.0;
            this.wear[i] = 0.0;
        }
    }

    void advance(AirframeSpec p, PowerSystem power, double ambient, double airspeed, double dt, boolean effects) {
        double motorC = p.motorHeatCapacity;
        double escC = escHeatCapacity(p);
        double air = airflow(airspeed);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double w = Math.abs(power.omega[i]);
            double current = power.current[i];
            double pm = motorLoss(p, current, w, effects ? this.motorResistance(p, i) : p.motorResistance);
            double gm = motorCooling(p, w) * air;
            double infM = ambient + pm / gm;
            this.motorTemp[i] = SimMath.clamp(infM + (this.motorTemp[i] - infM) * StrictMath.exp(-dt * gm / motorC), -40.0, 250.0);
            double pe = escLoss(p, current, power.busVoltage);
            double ge = escCooling(p, w) * air;
            double infE = ambient + pe / ge;
            this.escTemp[i] = SimMath.clamp(infE + (this.escTemp[i] - infE) * StrictMath.exp(-dt * ge / escC), -40.0, 250.0);
        }
    }

    double motorResistance(AirframeSpec p, int i) {
        return p.motorResistance * (1.0 + COPPER_TEMPCO * (this.motorTemp[i] - 25.0));
    }

    static double airflow(double airspeed) {
        double v = airspeed > 0.0 ? airspeed : 0.0;
        return 1.0 + AIRFLOW_COOLING * StrictMath.sqrt(Math.min(v, 100.0) / AIRFLOW_REFERENCE_MS);
    }

    static double bellRadius(double statorDiameterMeters) {
        return BELL_RADIUS_PER_STATOR * statorDiameterMeters;
    }

    static double eddyLoss(double statorDiameterMeters, double statorHeightMeters) {
        return MOTOR_EDDY_LOSS * 0.25 * Math.PI * statorDiameterMeters * statorDiameterMeters * statorHeightMeters;
    }

    static double motorLoss(AirframeSpec p, double current, double omega, double resistance) {
        double w = Math.abs(omega);
        return current * current * resistance + p.kt * p.idleCurrent * w + p.motorEddyLoss * w * w;
    }

    static double motorCooling(AirframeSpec p, double omega) {
        return (p.motorCoolingBase + p.motorCoolingPerOmega * Math.abs(omega)) * p.motorCoolingScale;
    }

    static double escLoss(AirframeSpec p, double current, double busVoltage) {
        return current * current * p.escResistance + ESC_SWITCHING * Math.max(busVoltage, 0.0) * Math.abs(current);
    }

    static double escCooling(AirframeSpec p, double omega) {
        double wash = 1.0 + ESC_PROP_WASH * Math.abs(omega) / p.hoverOmega;
        return p.escCooling * wash * (p.ducted ? ESC_DUCTED : 1.0);
    }

    static double escStillAir(double boardMm) {
        double size = boardMm / ESC_REFERENCE_BOARD_MM;
        return ESC_CONDUCTANCE * size * size;
    }

    static double boardFromMass(double stackGrams) {
        return ESC_REFERENCE_BOARD_MM * StrictMath.cbrt(Math.max(stackGrams, 0.5) / ESC_REFERENCE_GRAMS);
    }

    static double escHeatCapacity(AirframeSpec p) {
        return ESC_HEAT_CAPACITY * Math.max(p.stackMassKg * 1000.0, 0.5) / 4.0;
    }

    static double excess(AirframeSpec p, double temperature) {
        return (temperature - p.motorDerateStartC) / (p.motorMaxTempC - p.motorDerateStartC);
    }

    static double torqueFactor(AirframeSpec p, double temperature) {
        double x = excess(p, temperature);
        if (!(x > 0.0)) {
            return 1.0;
        }
        return 1.0 - MAX_TORQUE_LOSS * Math.min(1.0, x);
    }

    static double escPowerLimit(double temperature) {
        double x = (temperature - ESC_LIMIT_C) / ESC_DERATE_SPAN_K;
        if (!(x > 0.0)) {
            return 1.0;
        }
        return 1.0 - (1.0 - ESC_MIN_POWER_LIMIT) * Math.min(1.0, x);
    }

    static double wearRate(AirframeSpec p, double temperature) {
        double x = excess(p, temperature);
        if (!(x > 0.0)) {
            return 0.0;
        }
        double c = Math.min(1.0, x);
        return p.motorHeatWearPerSecond * c * c;
    }
}

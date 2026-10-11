package de.lardtom.propwash.sim;

final class OperatingPoints {
    static final int MOTOR_STEPS = 60;
    static final int BUS_STEPS = 50;
    static final int HOVER_STEPS = 100;
    static final int THRUST_STEPS = 40;
    static final int SPEED_STEPS = 50;
    static final int TILT_STEPS = 40;
    static final double MAX_TILT = Math.toRadians(89.0);
    static final double MAX_SPEED = 150.0;

    private OperatingPoints() {
    }

    static final class Point {
        double busVoltage;
        double duty;
        double omega;
        double phaseCurrent;
        double batteryCurrent;
        double thrustPerMotor;
        boolean feasible;
    }

    static double emf(AirframeSpec p, double soc) {
        return p.cells * CellCurve.ocv(p.chemistry, soc);
    }

    static double internalResistance(AirframeSpec p, double batteryCurrent, double soc) {
        double rate = 1.0 + ModelConstants.C_RATE_PENALTY * Math.max(0.0, batteryCurrent / p.continuousCurrent - 1.0);
        double low = 1.0 + ModelConstants.LOW_SOC_GAIN * Math.max(0.0, ModelConstants.LOW_SOC_KNEE - soc) / ModelConstants.LOW_SOC_KNEE;
        return p.packResistance * rate * low;
    }

    static double currentLimit(AirframeSpec p, double busVoltage, double omega) {
        return Math.min(p.escCurrentLimit,
                PowerSystem.inputLimitedCurrent(p.phaseResistance, p.kt * omega, busVoltage, p.escInputLimit));
    }

    static double phaseCurrent(AirframeSpec p, double duty, double busVoltage, double omega) {
        return Math.min((duty * busVoltage - p.kt * omega) / p.phaseResistance, currentLimit(p, busVoltage, omega));
    }

    static double motorInputCurrent(AirframeSpec p, double busVoltage, double omega, double current) {
        return (p.phaseResistance * current + p.kt * omega) * current / busVoltage;
    }

    static double motorOmega(AirframeSpec p, double duty, double busVoltage, double axialSpeed, double rhoRatio) {
        if (motorBalance(p, duty, busVoltage, axialSpeed, rhoRatio, 0.0) <= 0.0) {
            return 0.0;
        }
        double lo = 0.0;
        double hi = p.omegaMax;
        for (int k = 0; k < MOTOR_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (motorBalance(p, duty, busVoltage, axialSpeed, rhoRatio, mid) > 0.0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    private static double motorBalance(AirframeSpec p, double duty, double busVoltage, double axialSpeed, double rhoRatio, double omega) {
        double current = phaseCurrent(p, duty, busVoltage, omega);
        return p.kt * (current - p.idleCurrent) - RotorAero.airTorque(p, omega, axialSpeed, rhoRatio);
    }

    static void uniformDuty(AirframeSpec p, double duty, double soc, boolean polarized, double axialSpeed, double crossSpeed, Point out) {
        uniformDuty(p, duty, soc, polarized, 0.0, axialSpeed, crossSpeed, out);
    }

    static void uniformDuty(AirframeSpec p, double duty, double soc, boolean polarized, double fixedPolarization, double axialSpeed,
                            double crossSpeed, Point out) {
        double e = emf(p, soc);
        double lo = 0.05 * e;
        double hi = e;
        for (int k = 0; k < BUS_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            double omega = motorOmega(p, duty, mid, axialSpeed, 1.0);
            double current = phaseCurrent(p, duty, mid, omega);
            double iBat = 4.0 * motorInputCurrent(p, mid, omega, current) + p.auxPower / mid;
            double r = internalResistance(p, iBat, soc);
            double vPol = polarized ? p.polarizationResistance * iBat : fixedPolarization;
            if (e - vPol - r * iBat - mid > 0.0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double v = 0.5 * (lo + hi);
        double omega = motorOmega(p, duty, v, axialSpeed, 1.0);
        double current = phaseCurrent(p, duty, v, omega);
        out.busVoltage = v;
        out.duty = duty;
        out.omega = omega;
        out.phaseCurrent = current;
        out.batteryCurrent = 4.0 * motorInputCurrent(p, v, omega, current) + p.auxPower / v;
        out.thrustPerMotor = RotorAero.thrust(p, omega, axialSpeed, crossSpeed, 1.0, 1.0);
        out.feasible = true;
    }

    static void steadyMotor(AirframeSpec p, double omega, double current, double soc, boolean polarized, double fixedPolarization,
                            boolean sag, Point out) {
        double e = emf(p, PowerSystem.restingSoc(soc, sag));
        double drive = p.phaseResistance * current + p.kt * omega;
        double v = e;
        for (int k = 0; k < HOVER_STEPS; k++) {
            double vv = Math.max(v, 0.05 * e);
            double duty = drive / vv;
            double iBat = 4.0 * duty * current + p.auxPower / vv;
            double r = internalResistance(p, iBat, soc);
            double vPol = polarized ? p.polarizationResistance * iBat : fixedPolarization;
            v = 0.5 * v + 0.5 * (e - vPol - r * iBat);
        }
        v = Math.max(v, 0.05 * e);
        double duty = drive / v;
        out.busVoltage = v;
        out.duty = duty;
        out.omega = omega;
        out.phaseCurrent = current;
        out.batteryCurrent = 4.0 * duty * current + p.auxPower / v;
        out.thrustPerMotor = p.kThrust * p.ductGain * omega * omega;
        out.feasible = duty <= 1.0 && current <= currentLimit(p, v, omega) + 1e-9;
    }

    static double hoverCurrent(AirframeSpec p) {
        return p.kTorque * p.hoverOmega * p.hoverOmega / p.kt + p.idleCurrent;
    }

    static double hoverCurrent(AirframeSpec p, double gravityScale) {
        return p.kTorque * p.hoverOmega * p.hoverOmega * gravityScale / p.kt + p.idleCurrent;
    }

    static void hover(AirframeSpec p, double soc, boolean polarized, double fixedPolarization, boolean sag, Point out) {
        steadyMotor(p, p.hoverOmega, hoverCurrent(p), soc, polarized, fixedPolarization, sag, out);
    }

    static double hoverStick(AirframeSpec p, double soc) {
        Point pt = new Point();
        hover(p, soc, true, 0.0, true, pt);
        return (pt.duty - ModelConstants.IDLE_REFERENCE) / (1.0 - ModelConstants.IDLE_REFERENCE);
    }

    static double omegaForThrust(AirframeSpec p, double thrust, double axialSpeed, double crossSpeed) {
        if (thrust <= 0.0) {
            return 0.0;
        }
        double lo = 0.0;
        double hi = 2.0 * p.omegaMax;
        for (int k = 0; k < THRUST_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (RotorAero.thrust(p, mid, axialSpeed, crossSpeed, 1.0, 1.0) < thrust) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    static double bodyDragY(AirframeSpec p, double speed, double tilt) {
        return 0.5 * ModelConstants.RHO0 * p.dragCoefficient * p.areaY * speed * speed * StrictMath.sin(tilt);
    }

    static double bodyDragZ(AirframeSpec p, double speed, double tilt) {
        return 0.5 * ModelConstants.RHO0 * p.dragCoefficient * p.areaZ * speed * speed * StrictMath.cos(tilt);
    }

    static double requiredThrust(AirframeSpec p, double speed, double tilt) {
        return p.massKg * ModelConstants.G * StrictMath.cos(tilt) + bodyDragY(p, speed, tilt);
    }

    static double levelFlightOmega(AirframeSpec p, double speed, double tilt) {
        double sin = StrictMath.sin(tilt);
        double cos = StrictMath.cos(tilt);
        return omegaForThrust(p, requiredThrust(p, speed, tilt) / 4.0, speed * sin, speed * cos);
    }

    private static double forwardBalance(AirframeSpec p, double speed, double tilt) {
        double sin = StrictMath.sin(tilt);
        double cos = StrictMath.cos(tilt);
        double total = requiredThrust(p, speed, tilt);
        double omega = omegaForThrust(p, total / 4.0, speed * sin, speed * cos);
        double h = RotorAero.hForceMagnitude(total, speed * cos, omega, p.propRadius);
        return p.massKg * ModelConstants.G * sin - h - bodyDragZ(p, speed, tilt);
    }

    static double speedAtTilt(AirframeSpec p, double tilt) {
        if (tilt <= 0.0) {
            return 0.0;
        }
        double lo = 0.0;
        double hi = MAX_SPEED;
        if (forwardBalance(p, hi, tilt) > 0.0) {
            return hi;
        }
        for (int k = 0; k < SPEED_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (forwardBalance(p, mid, tilt) > 0.0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    static double fullThrust(AirframeSpec p, double speed, double tilt, double soc, Point scratch) {
        double sin = StrictMath.sin(tilt);
        double cos = StrictMath.cos(tilt);
        uniformDuty(p, 1.0, soc, true, speed * sin, speed * cos, scratch);
        return 4.0 * scratch.thrustPerMotor;
    }

    static double topSpeedTilt(AirframeSpec p, double soc) {
        Point scratch = new Point();
        if (fullThrust(p, 0.0, 0.0, soc, scratch) < requiredThrust(p, 0.0, 0.0)) {
            return 0.0;
        }
        double lo = 0.0;
        double hi = MAX_TILT;
        for (int k = 0; k < TILT_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            double v = speedAtTilt(p, mid);
            if (requiredThrust(p, v, mid) <= fullThrust(p, v, mid, soc, scratch)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    static double tiltForSpeed(AirframeSpec p, double speed, double maxTilt) {
        double lo = 0.0;
        double hi = maxTilt;
        for (int k = 0; k < TILT_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (speedAtTilt(p, mid) < speed) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    static double flightTimeMinutes(AirframeSpec p, double omega, double current) {
        Point pt = new Point();
        double capacity = p.capacityMah;
        double limit = 0.8 * capacity;
        double used = 0.0;
        for (int second = 0; second < 360000; second++) {
            double soc = 1.0 - used / capacity;
            steadyMotor(p, omega, current, soc, true, 0.0, true, pt);
            double step = Math.max(pt.batteryCurrent, 1e-6) / 3.6;
            if (used + step >= limit) {
                return (second + (limit - used) / step) / 60.0;
            }
            used += step;
        }
        return 6000.0;
    }

    static final int HEAT_SEGMENTS = 40;

    static double fullThrottleMotorTemp(AirframeSpec p, double ambient) {
        return heatRun(p, ambient, true);
    }

    static double hoverMotorTemp(AirframeSpec p, double ambient) {
        return heatRun(p, ambient, false);
    }

    private static double heatRun(AirframeSpec p, double ambient, boolean full) {
        Point pt = new Point();
        double temp = ambient;
        double max = ambient;
        double current = hoverCurrent(p);
        for (int k = 0; k < HEAT_SEGMENTS; k++) {
            double soc = 1.0 - (k + 0.5) / HEAT_SEGMENTS;
            if (full) {
                uniformDuty(p, 1.0, soc, true, 0.0, 0.0, pt);
            } else {
                steadyMotor(p, p.hoverOmega, current, soc, true, 0.0, true, pt);
            }
            double seconds = p.capacityMah / HEAT_SEGMENTS * 3.6 / Math.max(pt.batteryCurrent, 1e-3);
            double power = pt.phaseCurrent * pt.phaseCurrent * p.motorResistance + p.kt * p.idleCurrent * pt.omega;
            double g = (p.motorCoolingBase + p.motorCoolingPerOmega * pt.omega) * p.motorCoolingScale;
            double inf = ambient + power / g;
            temp = inf + (temp - inf) * StrictMath.exp(-seconds * g / p.motorHeatCapacity);
            if (!Double.isFinite(temp)) {
                return Double.NaN;
            }
            max = Math.max(max, temp);
        }
        return max;
    }
}

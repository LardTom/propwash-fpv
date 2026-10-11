package de.lardtom.propwash.sim;

final class PowerSystem {
    static final int MAX_PASSES = 6;
    static final int FREE = 0;
    static final int PHASE_LIMIT = 1;
    static final int INPUT_LIMIT = 2;
    static final double MIN_BUS_VOLTAGE = 1e-6;

    final double[] omega = new double[AirframeSpec.MOTORS];
    final double[] current = new double[AirframeSpec.MOTORS];
    final double[] airTorque = new double[AirframeSpec.MOTORS];
    final double[] reactionTorque = new double[AirframeSpec.MOTORS];
    final double[] resistance = new double[AirframeSpec.MOTORS];
    final boolean[] limited = new boolean[AirframeSpec.MOTORS];
    final double[] limit = new double[AirframeSpec.MOTORS];
    final int[] mode = new int[AirframeSpec.MOTORS];
    final double[] inputCurrent = new double[AirframeSpec.MOTORS];
    final double[] loadScale = {1.0, 1.0, 1.0, 1.0};
    final double[] driveSign = {1.0, 1.0, 1.0, 1.0};
    double batteryResistanceScale = 1.0;
    boolean reversible;

    private AirframeSpec p;
    private double polarizationCoeff;
    private double heatCoeff;

    double soc = 1.0;
    double mAhUsed;
    double polarization;
    double lastBatteryCurrent;
    double heatCurrent;
    double lastBusVoltage;
    double busVoltage;
    double batteryCurrent;
    double emf;
    double internalResistance;
    boolean anyLimited;
    boolean stiffBus;
    double extraPower;
    double fullLoad;
    double packSoc = 1.0;
    double packMah;
    double packCurrent;

    void configure(AirframeSpec params, double h) {
        this.p = params;
        this.polarizationCoeff = 1.0 - StrictMath.exp(-h / ModelConstants.POL_TAU);
        this.heatCoeff = 1.0 - StrictMath.exp(-h / ModelConstants.C_RATE_TAU);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.resistance[i] = params.phaseResistance;
        }
    }

    void setStateOfCharge(double chargeFraction) {
        double c = SimMath.clamp01(SimMath.finiteOr(chargeFraction, 1.0));
        this.soc = c;
        this.mAhUsed = (1.0 - c) * this.p.capacityMah;
        this.polarization = 0.0;
        this.lastBatteryCurrent = 0.0;
        this.heatCurrent = 0.0;
        this.emf = OperatingPoints.emf(this.p, this.soc);
        this.lastBusVoltage = this.emf;
        this.busVoltage = this.emf;
        this.batteryCurrent = 0.0;
        this.packSoc = c;
        this.packMah = (1.0 - c) * this.p.capacityMah;
        this.packCurrent = 0.0;
    }

    void stopRotors() {
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.omega[i] = 0.0;
            this.current[i] = 0.0;
            this.airTorque[i] = 0.0;
            this.reactionTorque[i] = 0.0;
            this.limited[i] = false;
            this.mode[i] = FREE;
            this.inputCurrent[i] = 0.0;
            this.driveSign[i] = 1.0;
        }
    }

    static double restingSoc(double soc, boolean sag) {
        return sag ? soc : 1.0;
    }

    static double inputLimitedCurrent(double r, double backEmf, double busVoltage, double inputLimit) {
        if (!(busVoltage > 0.0) || !(inputLimit > 0.0)) {
            return 0.0;
        }
        double e = Math.max(0.0, backEmf);
        return 2.0 * busVoltage * inputLimit / (e + Math.sqrt(e * e + 4.0 * r * busVoltage * inputLimit));
    }

    void solveBus(double[] duty, boolean active, boolean sag) {
        AirframeSpec params = this.p;
        double e = OperatingPoints.emf(params, restingSoc(this.soc, sag)) - (this.stiffBus ? 0.0 : this.polarization);
        double rInt = this.stiffBus ? 0.0 : OperatingPoints.internalResistance(params, this.heatCurrent, this.soc) * this.batteryResistanceScale;
        this.emf = e;
        this.internalResistance = rInt;
        double aux = params.auxPower / Math.max(this.lastBusVoltage, 1.0);
        double ke = params.kt;
        double inLimit = params.escInputLimit;
        double v;
        this.anyLimited = false;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.limited[i] = false;
            this.mode[i] = FREE;
            this.limit[i] = params.escCurrentLimit;
        }
        if (active) {
            v = e;
            for (int pass = 0; pass < MAX_PASSES; pass++) {
                double fixed = aux;
                double power = 0.0;
                double freeEmf = 0.0;
                double freeSq = 0.0;
                for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                    int m = this.mode[i];
                    if (m == FREE) {
                        double d = duty[i];
                        freeEmf += d * ke * this.omega[i] / this.resistance[i];
                        freeSq += d * d / this.resistance[i];
                    } else if (m == INPUT_LIMIT) {
                        fixed += inLimit;
                    } else {
                        double c = this.current[i];
                        power += (this.resistance[i] * c + ke * this.omega[i]) * c;
                    }
                }
                v = busRoot(1.0 + rInt * freeSq, e - rInt * fixed + rInt * freeEmf, rInt * power);
                boolean changed = false;
                for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                    int m = this.classify(i, duty[i], v, ke);
                    if (m != this.mode[i]) {
                        changed = true;
                    }
                    this.mode[i] = m;
                }
                if (!changed) {
                    break;
                }
            }
            double vMax = e + 0.1 * params.cells;
            if (!(v >= 0.0) || v > vMax) {
                v = SimMath.clamp(SimMath.finiteOr(v, 0.0), 0.0, vMax);
                for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                    this.mode[i] = this.classify(i, duty[i], v, ke);
                }
            }
            double total = aux;
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                double c = this.current[i];
                double in;
                if (this.mode[i] == FREE) {
                    in = duty[i] * c;
                } else {
                    in = v > MIN_BUS_VOLTAGE ? (this.resistance[i] * c + ke * this.omega[i]) * c / v : 0.0;
                }
                this.inputCurrent[i] = in;
                this.limited[i] = this.mode[i] != FREE;
                this.anyLimited |= this.limited[i];
                total += in;
            }
            this.batteryCurrent = total;
        } else {
            v = SimMath.clamp(e - rInt * aux, 0.0, e + 0.1 * params.cells);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                this.current[i] = 0.0;
                this.inputCurrent[i] = 0.0;
            }
            this.batteryCurrent = aux;
        }
        this.busVoltage = v;
    }

    static double busRoot(double a, double b, double q) {
        if (q == 0.0) {
            return b / a;
        }
        double disc = b * b - 4.0 * a * q;
        return disc >= 0.0 ? (b + Math.sqrt(disc)) / (2.0 * a) : b / (2.0 * a);
    }

    private int classify(int i, double duty, double v, double ke) {
        if (this.reversible) {
            return this.classifyReversible(i, duty, v, ke);
        }
        AirframeSpec params = this.p;
        double r = this.resistance[i];
        double backEmf = ke * this.omega[i];
        double c = (duty * v - backEmf) / r;
        double phaseLimit = params.escCurrentLimit;
        double inLimited = inputLimitedCurrent(r, backEmf, v, params.escInputLimit);
        double lim = Math.min(phaseLimit, inLimited);
        this.limit[i] = lim;
        if (c > lim) {
            this.current[i] = lim;
            return inLimited < phaseLimit ? INPUT_LIMIT : PHASE_LIMIT;
        }
        if (c < -phaseLimit) {
            this.current[i] = -phaseLimit;
            return PHASE_LIMIT;
        }
        this.current[i] = c;
        return FREE;
    }

    private int classifyReversible(int i, double duty, double v, double ke) {
        double s = duty < 0.0 || duty == 0.0 && this.omega[i] < 0.0 ? -1.0 : 1.0;
        this.driveSign[i] = s;
        AirframeSpec params = this.p;
        double r = this.resistance[i];
        double backEmf = s * ke * this.omega[i];
        double c = (s * duty * v - backEmf) / r;
        double phaseLimit = params.escCurrentLimit;
        double inLimited = inputLimitedCurrent(r, backEmf, v, params.escInputLimit);
        double lim = Math.min(phaseLimit, inLimited);
        this.limit[i] = lim;
        if (c > lim) {
            this.current[i] = s * lim;
            return inLimited < phaseLimit ? INPUT_LIMIT : PHASE_LIMIT;
        }
        if (c < -phaseLimit) {
            this.current[i] = -s * phaseLimit;
            return PHASE_LIMIT;
        }
        this.current[i] = s * c;
        return FREE;
    }

    boolean atHardLimit(int i) {
        return this.mode[i] == PHASE_LIMIT;
    }

    void stepRotors(double h, double[] lambda, double rhoRatio, boolean active) {
        if (this.reversible) {
            this.stepRotorsReversible(h, lambda, rhoRatio, active);
            return;
        }
        AirframeSpec params = this.p;
        double j = params.rotorInertiaEff;
        double kt = params.kt;
        double kq = params.kTorque * rhoRatio;
        double i0 = params.idleCurrent;
        double omegaMax = params.omegaMax;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double w = this.omega[i];
            double fq = RotorAero.torqueFactor(lambda[i]) * this.loadScale[i];
            double q = kq * fq * w * w;
            double shaft;
            double slope;
            if (active) {
                shaft = kt * (this.current[i] - i0 * SimMath.smoothSign(w));
                double electrical;
                int m = this.mode[i];
                if (m == FREE) {
                    electrical = kt * kt / this.resistance[i];
                } else if (m == INPUT_LIMIT) {
                    double c = this.current[i];
                    electrical = kt * kt * c / Math.max(2.0 * this.resistance[i] * c + kt * w, 1e-9);
                } else {
                    electrical = 0.0;
                }
                slope = -(electrical + 2.0 * kq * fq * w) / j;
            } else {
                shaft = -kt * i0 * SimMath.smoothSign(w);
                slope = -2.0 * kq * fq * w / j;
            }
            double f = (shaft - q) / j;
            double next = SimMath.clamp(w + h * f / (1.0 - h * slope), 0.0, omegaMax);
            this.airTorque[i] = q;
            this.reactionTorque[i] = q + j * (next - w) / h;
            this.omega[i] = next;
        }
    }

    private void stepRotorsReversible(double h, double[] lambda, double rhoRatio, boolean active) {
        AirframeSpec params = this.p;
        double j = params.rotorInertiaEff;
        double kt = params.kt;
        double kq = params.kTorque * rhoRatio;
        double i0 = params.idleCurrent;
        double omegaMax = params.omegaMax;
        double sync = ModelConstants.STARTUP_OMEGA_FRACTION * omegaMax;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double w = this.omega[i];
            double aw = Math.abs(w);
            double kqDir = w < 0.0 ? kq * params.reverseTorque : kq;
            double fq = RotorAero.torqueFactor(lambda[i]) * this.loadScale[i];
            double q = (w < 0.0 ? -1.0 : 1.0) * kqDir * fq * aw * aw;
            double shaft;
            double slope;
            if (active) {
                double startup = aw < sync ? ModelConstants.STARTUP_TORQUE + (1.0 - ModelConstants.STARTUP_TORQUE) * aw / sync : 1.0;
                shaft = startup * kt * this.current[i] - kt * i0 * SimMath.smoothSign(w);
                double electrical;
                int m = this.mode[i];
                if (m == FREE) {
                    electrical = kt * kt / this.resistance[i];
                } else if (m == INPUT_LIMIT) {
                    double s = this.driveSign[i];
                    double c = s * this.current[i];
                    electrical = kt * kt * c / Math.max(2.0 * this.resistance[i] * c + kt * s * w, 1e-9);
                } else {
                    electrical = 0.0;
                }
                slope = -(startup * electrical + 2.0 * kqDir * fq * aw) / j;
            } else {
                shaft = -kt * i0 * SimMath.smoothSign(w);
                slope = -2.0 * kqDir * fq * aw / j;
            }
            double f = (shaft - q) / j;
            double next = SimMath.clamp(w + h * f / (1.0 - h * slope), -omegaMax, omegaMax);
            this.airTorque[i] = q;
            this.reactionTorque[i] = q + j * (next - w) / h;
            this.omega[i] = next;
        }
    }

    boolean anyReversed() {
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (this.omega[i] < 0.0) {
                return true;
            }
        }
        return false;
    }

    void integrateBattery(double h, boolean infinite) {
        this.integrateBattery(h, infinite, 1.0);
    }

    void integrateBattery(double h, boolean infinite, double scale) {
        this.mAhUsed += this.batteryCurrent * h / 3.6 / scale;
        if (!infinite) {
            this.soc = Math.max(ModelConstants.SOC_MIN, 1.0 - this.mAhUsed / this.p.capacityMah);
        }
        this.polarization += (this.p.polarizationResistance * this.batteryCurrent - this.polarization) * this.polarizationCoeff;
        this.heatCurrent += (Math.abs(this.batteryCurrent) - this.heatCurrent) * this.heatCoeff;
        this.lastBatteryCurrent = this.batteryCurrent;
        this.lastBusVoltage = this.busVoltage;
        this.integratePack(h, infinite, scale);
    }

    void integratePack(double h, boolean infinite, double scale) {
        AirframeSpec params = this.p;
        double current = EnergyModel.packCurrent(params, this.batteryCurrent, this.busVoltage, this.extraPower, this.fullLoad);
        this.packCurrent = current;
        this.packMah += current * h / 3.6 / scale;
        if (!infinite) {
            this.packSoc = Math.max(ModelConstants.SOC_MIN, 1.0 - this.packMah / params.capacityMah);
        }
    }

    double packVoltage(boolean sag) {
        AirframeSpec params = this.p;
        double shift = OperatingPoints.emf(params, restingSoc(this.soc, sag)) - OperatingPoints.emf(params, restingSoc(this.packSoc, sag));
        return Math.max(0.0, this.busVoltage - shift);
    }

    void forceBatteryCurrent(double amps, double h, boolean sag, boolean infinite) {
        AirframeSpec params = this.p;
        double e = OperatingPoints.emf(params, restingSoc(this.soc, sag)) - this.polarization;
        double rInt = OperatingPoints.internalResistance(params, amps, this.soc) * this.batteryResistanceScale;
        this.emf = e;
        this.internalResistance = rInt;
        this.batteryCurrent = amps;
        this.busVoltage = e - rInt * amps;
        this.integrateBattery(h, infinite);
    }
}

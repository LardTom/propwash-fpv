package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.VideoType;

public final class EnergyModel {
    static final double AVIONICS_BASE_W = 0.6;
    static final double DRIVE_LOSS_W = 1.03;
    static final double DRIVE_LOSS_MASS_EXP = 0.5;
    static final double DRIVE_LOSS_SPEED_EXP = 1.0;
    static final double ANALOG_BASE_W = 1.2;
    static final double ANALOG_PER_RF_W = 4.3;
    static final double DIGITAL_BASE_W = 4.5;
    static final double DIGITAL_PER_GRAM_W = 0.1;
    static final double DIGITAL_PER_RF_W = 2.5;
    static final int DEFAULT_RF_MW = 200;
    static final double LOW_RATE_USABLE = 0.87;
    static final double LOW_RATE_PRACTICAL_C = 6.0;
    static final int PUNCH_STEPS = 24;
    static final int SEGMENTS = 24;
    static final double IDLE_SOC = 0.5;
    static final double MINUTE_S = 60.0;
    static final int HEAT_STEPS = 40;
    static final double[] PROP_SIZE_MM = {31.0, 51.0, 76.0, 89.0, 102.0, 130.0, 178.0, 254.0, 330.0};
    static final double[] PROP_SIZE_FACTOR = {0.84, 0.78, 0.72, 0.67, 0.80, 1.02, 1.0, 0.93, 0.93};
    static final double[] PROP_FULL_FACTOR = {0.84, 1.05, 1.0, 1.0, 1.0, 1.02, 1.0, 0.93, 0.93};
    static final double BLADE_LOSS = 0.38;
    static final double BLADE_INTERFERENCE_MM = 45.0;
    static final double BLADE_INTERFERENCE_FADE_MM = 10.0;
    static final double WHOOP_SMALL_PROP_M = 0.035;
    static final double AIRFLOW_COOLING = 0.6;
    static final double AIRFLOW_REFERENCE_MS = 10.0;

    private EnergyModel() {
    }

    enum Profile {
        HOVER,
        CRUISE,
        MIXED,
        AGGRESSIVE
    }

    record Mix(double cruise, double punch, double idle, double punchThrust) {
    }

    static Mix mix(AirframeSpec p, Profile profile) {
        boolean aggressive = profile == Profile.AGGRESSIVE;
        return switch (p.role) {
            case WHOOP -> p.propDiameter <= WHOOP_SMALL_PROP_M
                    ? aggressive ? new Mix(0.46, 0.49, 0.05, 5.0) : new Mix(0.44, 0.37, 0.19, 5.0)
                    : aggressive ? new Mix(0.50, 0.40, 0.10, 3.0) : new Mix(0.75, 0.20, 0.05, 3.0);
            case TOOTHPICK -> aggressive ? new Mix(0.65, 0.23, 0.12, 4.5) : new Mix(0.80, 0.12, 0.08, 4.0);
            case CINEWHOOP, CINELIFTER -> aggressive ? new Mix(0.75, 0.17, 0.08, 3.5) : new Mix(0.83, 0.12, 0.05, 2.5);
            case FREESTYLE -> aggressive ? new Mix(0.72, 0.14, 0.14, 7.0) : new Mix(0.80, 0.10, 0.10, 5.5);
            case RACE -> aggressive ? new Mix(0.50, 0.38, 0.12, 7.0) : new Mix(0.58, 0.30, 0.12, 6.5);
            case LONG_RANGE -> aggressive ? new Mix(0.58, 0.32, 0.10, 5.0) : new Mix(0.66, 0.26, 0.08, 3.8);
            case X_CLASS -> aggressive ? new Mix(0.45, 0.45, 0.10, 5.0) : new Mix(0.58, 0.34, 0.08, 4.2);
        };
    }

    static double cruiseTopFraction(FrameRole role) {
        return switch (role) {
            case WHOOP, LONG_RANGE -> 0.55;
            case CINEWHOOP, CINELIFTER, X_CLASS -> 0.5;
            case TOOTHPICK -> 0.45;
            case FREESTYLE, RACE -> 0.4;
        };
    }

    static double cruiseSpeed(FrameRole role) {
        return switch (role) {
            case WHOOP -> 18.5 / 3.6;
            case TOOTHPICK -> 40.0 / 3.6;
            case CINEWHOOP, CINELIFTER -> 30.0 / 3.6;
            case FREESTYLE -> 55.0 / 3.6;
            case RACE -> 60.0 / 3.6;
            case LONG_RANGE -> 63.0 / 3.6;
            case X_CLASS -> 65.0 / 3.6;
        };
    }

    public static double videoSupplyW(VideoType video) {
        return videoPower(video);
    }

    public static double standbyW(VideoType video) {
        return AVIONICS_BASE_W + videoPower(video);
    }

    static double videoPower(VideoType video) {
        VideoDefinition d = video.definition();
        if (d.powerW().isPresent()) {
            return d.powerW().getAsDouble();
        }
        double rf = (d.vtxPowerMw() > 0 ? d.vtxPowerMw() : DEFAULT_RF_MW) / 1000.0;
        if (d.link() == VideoLink.DIGITAL) {
            return DIGITAL_BASE_W + DIGITAL_PER_GRAM_W * video.massGrams() + DIGITAL_PER_RF_W * rf;
        }
        return ANALOG_BASE_W + ANALOG_PER_RF_W * rf;
    }

    static double sizeFactor(double diameterMm) {
        return interpolate(PROP_SIZE_FACTOR, diameterMm);
    }

    static double interpolate(double[] table, double diameterMm) {
        double[] d = PROP_SIZE_MM;
        if (diameterMm <= d[0]) {
            return table[0];
        }
        for (int i = 1; i < d.length; i++) {
            if (diameterMm <= d[i]) {
                double t = StrictMath.log(diameterMm / d[i - 1]) / StrictMath.log(d[i] / d[i - 1]);
                return table[i - 1] + (table[i] - table[i - 1]) * t;
            }
        }
        return table[table.length - 1];
    }

    static double bladeLoss(int blades, double diameterMm) {
        double small = SimMath.clamp01((BLADE_INTERFERENCE_MM - diameterMm) / BLADE_INTERFERENCE_FADE_MM);
        return BLADE_LOSS * Math.max(0, blades - 3) * small;
    }

    static double propFactor(double diameterMm) {
        return sizeFactor(diameterMm);
    }

    static double propFullFactor(double diameterMm) {
        return interpolate(PROP_FULL_FACTOR, diameterMm);
    }

    static double fullLoad(AirframeSpec p, double omega) {
        double hover = p.hoverOmega * p.hoverOmega;
        double span = p.fullLoadOmega * p.fullLoadOmega - hover;
        if (!(span > 0.0)) {
            return Math.abs(omega) >= p.fullLoadOmega ? 1.0 : 0.0;
        }
        double x = SimMath.clamp01((omega * omega - hover) / span);
        return x * x;
    }

    static double fullLoad(AirframeSpec p, double[] omega, boolean active) {
        if (!active) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            sum += fullLoad(p, omega[i]);
        }
        return sum / AirframeSpec.MOTORS;
    }

    static double loadFactor(AirframeSpec p, double fullLoad) {
        return p.propFactor + (p.propFullFactor - p.propFactor) * fullLoad;
    }

    static double driveLossAtHover(double motorGrams, int blades, double diameterMm, double shaftPowerHover) {
        return DRIVE_LOSS_W * StrictMath.pow(Math.max(motorGrams, 0.1), DRIVE_LOSS_MASS_EXP) + bladeLoss(blades, diameterMm) * shaftPowerHover;
    }

    static double driveLoss(AirframeSpec p, double omega) {
        double w = Math.abs(omega);
        if (!(w > 0.0)) {
            return 0.0;
        }
        return p.driveLossHover * StrictMath.pow(w / p.hoverOmega, DRIVE_LOSS_SPEED_EXP);
    }

    static double extraPower(AirframeSpec p, double[] omega, boolean active) {
        double sum = p.avionicsPower - p.auxPower;
        if (active) {
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                sum += driveLoss(p, omega[i]);
            }
        }
        return sum;
    }

    static double uniformExtraPower(AirframeSpec p, double omega) {
        return p.avionicsPower - p.auxPower + AirframeSpec.MOTORS * driveLoss(p, omega);
    }

    static double packCurrent(AirframeSpec p, double batteryCurrent, double busVoltage, double extraPower, double fullLoad) {
        double v = Math.max(busVoltage, 0.05 * p.fullPackVoltage);
        double motors = Math.max(0.0, batteryCurrent - p.auxPower / v);
        return Math.max(0.0, batteryCurrent + (loadFactor(p, fullLoad) - 1.0) * motors + extraPower / v);
    }

    static double packCurrent(AirframeSpec p, OperatingPoints.Point pt) {
        return packCurrent(p, pt.batteryCurrent, pt.busVoltage, uniformExtraPower(p, pt.omega), fullLoad(p, pt.omega));
    }

    static double loadedVoltage(AirframeSpec p, double soc, double current) {
        double r = OperatingPoints.dcResistance(p, current, soc);
        return OperatingPoints.emf(p, soc) - r * current;
    }

    static final class Points {
        final AirframeSpec p;
        final double cruiseOmega;
        final double cruiseCurrent;
        final double cruiseSpeed;
        final OperatingPoints.Point scratch = new OperatingPoints.Point();

        Points(AirframeSpec p, double topSpeed, double topTilt) {
            this.p = p;
            if (topSpeed > 0.0 && topTilt > 0.0) {
                double target = Math.min(cruiseSpeed(p.role), cruiseTopFraction(p.role) * topSpeed);
                double tilt = OperatingPoints.tiltForSpeed(p, target, topTilt);
                double speed = OperatingPoints.speedAtTilt(p, tilt);
                double omega = OperatingPoints.levelFlightOmega(p, speed, tilt);
                this.cruiseOmega = omega;
                this.cruiseCurrent = RotorAero.airTorque(p, omega, speed * StrictMath.sin(tilt), 1.0) / p.kt + p.idleCurrent;
                this.cruiseSpeed = speed;
            } else {
                this.cruiseOmega = p.hoverOmega;
                this.cruiseCurrent = OperatingPoints.hoverCurrent(p);
                this.cruiseSpeed = 0.0;
            }
        }

        double hover(double soc) {
            OperatingPoints.hover(this.p, soc, true, 0.0, true, this.scratch);
            return packCurrent(this.p, this.scratch);
        }

        double cruise(double soc) {
            OperatingPoints.steadyMotor(this.p, this.cruiseOmega, this.cruiseCurrent, soc, true, 0.0, true, this.scratch);
            return packCurrent(this.p, this.scratch);
        }

        double full(double soc) {
            OperatingPoints.uniformDuty(this.p, 1.0, soc, true, 0.0, 0.0, this.scratch);
            return packCurrent(this.p, this.scratch);
        }

        void punchPoint(double thrustToWeight, double soc) {
            double thrust = thrustToWeight * this.p.massKg * ModelConstants.G / AirframeSpec.MOTORS;
            double omega = OperatingPoints.omegaForThrust(this.p, thrust, 0.0, 0.0);
            double current = RotorAero.airTorque(this.p, omega, 0.0, 1.0) / this.p.kt + this.p.idleCurrent;
            OperatingPoints.steadyMotor(this.p, omega, current, soc, true, 0.0, true, this.scratch);
            if (!this.scratch.feasible) {
                OperatingPoints.uniformDuty(this.p, 1.0, soc, true, 0.0, 0.0, this.scratch);
            }
        }

        double punchLimit(Profile profile) {
            if (profile == Profile.MIXED) {
                return Math.min(this.p.continuousCurrent, LOW_RATE_PRACTICAL_C * this.p.capacityMah / 1000.0);
            }
            return this.p.continuousCurrent;
        }

        double punch(double thrustToWeight, double soc, Profile profile) {
            this.punchPoint(thrustToWeight, soc);
            double current = packCurrent(this.p, this.scratch);
            double limit = this.punchLimit(profile);
            if (this.p.usableFraction < LOW_RATE_USABLE || current <= limit) {
                return current;
            }
            double lo = 1.0;
            double hi = thrustToWeight;
            for (int k = 0; k < PUNCH_STEPS; k++) {
                double mid = 0.5 * (lo + hi);
                this.punchPoint(mid, soc);
                if (packCurrent(this.p, this.scratch) > limit) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
            this.punchPoint(lo, soc);
            return packCurrent(this.p, this.scratch);
        }

        double idle(double soc) {
            OperatingPoints.uniformDuty(this.p, ModelConstants.IDLE_REFERENCE, soc, true, 0.0, 0.0, this.scratch);
            return packCurrent(this.p, this.scratch);
        }

        double pack;
        double motion;
        double calm;
        double heat;
        private double idlePack = Double.NaN;
        private double idleMotion;
        private double idleHeat;

        private void add(double weight, double packCurrent) {
            this.pack += weight * packCurrent;
            this.motion += weight * this.scratch.batteryCurrent;
            this.heat += weight * motorHeat(this.p, this.scratch);
        }

        void evaluate(Profile profile, double soc) {
            this.single(profile, soc);
            if (profile == Profile.HOVER) {
                return;
            }
            double pack = this.pack;
            double motion = this.motion;
            double calm = this.calm;
            double heat = this.heat;
            this.evaluate(Profile.values()[profile.ordinal() - 1], soc);
            this.pack = Math.max(pack, this.pack);
            this.motion = Math.max(motion, this.motion);
            this.calm = Math.max(calm, this.calm);
            this.heat = Math.max(heat, this.heat);
        }

        void single(Profile profile, double soc) {
            this.pack = 0.0;
            this.motion = 0.0;
            this.heat = 0.0;
            switch (profile) {
                case HOVER -> {
                    this.add(1.0, this.hover(soc));
                    this.calm = this.scratch.batteryCurrent;
                }
                case CRUISE -> {
                    this.add(1.0, this.cruise(soc));
                    this.calm = this.scratch.batteryCurrent;
                }
                default -> {
                    Mix m = mix(this.p, profile);
                    if (m.cruise() > 0.0) {
                        this.add(m.cruise(), this.cruise(soc));
                    }
                    this.calm = this.scratch.batteryCurrent;
                    if (m.punch() > 0.0) {
                        this.add(m.punch(), this.punch(m.punchThrust(), soc, profile));
                    }
                    if (m.idle() > 0.0) {
                        if (Double.isNaN(this.idlePack)) {
                            this.idlePack = this.idle(IDLE_SOC);
                            this.idleMotion = this.scratch.batteryCurrent;
                            this.idleHeat = motorHeat(this.p, this.scratch);
                        }
                        this.pack += m.idle() * this.idlePack;
                        this.motion += m.idle() * this.idleMotion;
                        this.heat += m.idle() * this.idleHeat;
                    }
                }
            }
        }

        double cooling(Profile profile) {
            double omega = switch (profile) {
                case HOVER -> this.p.hoverOmega;
                case CRUISE -> this.cruiseOmega;
                case MIXED, AGGRESSIVE -> {
                    Mix m = mix(this.p, profile);
                    yield m.cruise() * this.cruiseOmega + (1.0 - m.cruise()) * this.p.hoverOmega;
                }
            };
            double speed = profile == Profile.HOVER ? 0.0 : this.cruiseSpeed;
            double airflow = 1.0 + AIRFLOW_COOLING * StrictMath.sqrt(speed / AIRFLOW_REFERENCE_MS);
            return (this.p.motorCoolingBase + this.p.motorCoolingPerOmega * omega) * this.p.motorCoolingScale * airflow;
        }
    }

    static double motorHeat(AirframeSpec p, OperatingPoints.Point pt) {
        return pt.phaseCurrent * pt.phaseCurrent * p.motorResistance + p.kt * p.idleCurrent * pt.omega;
    }

    record Flight(double minutes, double averageCurrent, double landingCellVoltage, double motorTempC) {
        static final Flight NONE = new Flight(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    static Flight fly(Points pts, Profile profile, double ambient) {
        AirframeSpec p = pts.p;
        double capacity = p.capacityMah;
        double usable = p.usableFraction * capacity;
        double step = usable / SEGMENTS;
        double landing = p.landingCellVoltage * p.cells;
        double cutoff = p.chemistry.cutoffCellVoltage * p.cells;
        double seconds = 0.0;
        double used = 0.0;
        double landed = Double.NaN;
        double temp = ambient;
        double cooling = pts.cooling(profile);
        for (int k = 0; k < SEGMENTS; k++) {
            double start = 1.0 - used / capacity;
            double end = 1.0 - (used + step) / capacity;
            pts.evaluate(profile, 0.5 * (start + end));
            double current = Math.max(pts.pack, 1e-6);
            double part = Math.min(reach(loadedVoltage(p, start, pts.calm), loadedVoltage(p, end, pts.calm), landing),
                    reach(loadedVoltage(p, start, pts.motion), loadedVoltage(p, end, pts.motion), cutoff));
            double dt = part * step * 3.6 / current;
            double inf = ambient + pts.heat / cooling;
            temp = inf + (temp - inf) * StrictMath.exp(-dt * cooling / p.motorHeatCapacity);
            seconds += dt;
            used += part * step;
            landed = loadedVoltage(p, 1.0 - used / capacity, pts.motion);
            if (part < 1.0) {
                break;
            }
        }
        if (Double.isNaN(landed)) {
            return Flight.NONE;
        }
        if (!(seconds > 0.0)) {
            return new Flight(0.0, pts.pack, landed / p.cells, ambient);
        }
        return new Flight(seconds / MINUTE_S, used * 3.6 / seconds, landed / p.cells, temp);
    }

    static double reach(double start, double end, double limit) {
        if (end >= limit) {
            return 1.0;
        }
        if (start <= limit) {
            return 0.0;
        }
        return SimMath.clamp01((start - limit) / Math.max(start - end, 1e-9));
    }

    record Heat(double minute, double sustained) {
    }

    static Heat fullThrottleMotorTemp(AirframeSpec p, double ambient, double windowSeconds) {
        OperatingPoints.Point pt = new OperatingPoints.Point();
        double capacity = p.capacityMah;
        double temp = ambient;
        double max = ambient;
        double minute = Double.NaN;
        double seconds = 0.0;
        double step = capacity / HEAT_STEPS;
        for (int k = 0; k < HEAT_STEPS; k++) {
            double soc = 1.0 - (k + 0.5) / HEAT_STEPS;
            OperatingPoints.uniformDuty(p, 1.0, soc, true, 0.0, 0.0, pt);
            double current = Math.max(packCurrent(p, pt), 1e-3);
            double dt = step * 3.6 / current;
            double power = motorHeat(p, pt);
            double g = (p.motorCoolingBase + p.motorCoolingPerOmega * pt.omega) * p.motorCoolingScale;
            double inf = ambient + power / g;
            if (Double.isNaN(minute) && seconds + dt >= windowSeconds) {
                double partial = inf + (temp - inf) * StrictMath.exp(-(windowSeconds - seconds) * g / p.motorHeatCapacity);
                minute = Math.max(max, partial);
            }
            temp = inf + (temp - inf) * StrictMath.exp(-dt * g / p.motorHeatCapacity);
            if (!Double.isFinite(temp)) {
                return new Heat(Double.NaN, Double.NaN);
            }
            max = Math.max(max, temp);
            seconds += dt;
        }
        return new Heat(Double.isNaN(minute) ? max : minute, max);
    }
}

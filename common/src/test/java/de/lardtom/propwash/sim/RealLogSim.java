package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.calibration.Measure;
import de.lardtom.propwash.calibration.Signals;
import de.lardtom.propwash.calibration.Trace;
import java.util.HashMap;
import java.util.Map;

public final class RealLogSim {
    static final double PRE_ROLL = 1.0;
    static final double FAR_BELOW = -1.0e6;
    static final double TERMINAL_MAX_S = 30.0;
    static final double TERMINAL_SETTLED = 0.005;
    static final double SOC_FLOOR = 0.35;
    static final int SOC_STEPS = 40;
    static final double RPM_PER_RAD = 60.0 / (2.0 * Math.PI);
    static final double TUNNEL_SETTLE = 0.3;
    static final double TUNNEL_AVERAGE = 0.2;
    static final double TUNNEL_DUTY_STEP = 0.01;
    static final double TUNNEL_CLIMB_STEP = 0.25;
    static final double TUNNEL_VOLT_STEP = 0.1;

    public final DroneBuild build;
    public final AirframeSpec params;
    private final OperatingPoints.Point point = new OperatingPoints.Point();
    private final Map<String, Double> tunnelCache = new HashMap<>();

    public RealLogSim(DroneBuild build) {
        this.build = build;
        this.params = AirframeSpec.of(build);
    }

    public double massG() {
        return this.params.massKg * 1000.0;
    }

    public double hoverRpm() {
        return this.params.hoverOmega * RPM_PER_RAD;
    }

    public double hoverDuty(double busVoltage) {
        AirframeSpec p = this.params;
        return (p.phaseResistance * OperatingPoints.hoverCurrent(p) + p.kt * p.hoverOmega) / busVoltage;
    }

    public double staticRpm(double duty, double busVoltage) {
        return OperatingPoints.motorOmega(this.params, duty, busVoltage, 0.0, 1.0) * RPM_PER_RAD;
    }

    public double staticThrustToWeight(double duty, double busVoltage) {
        double omega = OperatingPoints.motorOmega(this.params, duty, busVoltage, 0.0, 1.0);
        double thrust = RotorAero.thrust(this.params, omega, 0.0, 0.0, 1.0, 1.0);
        return AirframeSpec.MOTORS * thrust / (this.params.massKg * ModelConstants.G);
    }

    public double thrustToWeightAt(double duty, double busVoltage, double climb) {
        long d = Math.round(duty / TUNNEL_DUTY_STEP);
        long c = Math.round(climb / TUNNEL_CLIMB_STEP);
        long v = Double.isNaN(busVoltage) ? -1 : Math.round(busVoltage / TUNNEL_VOLT_STEP);
        String key = d + "/" + c + "/" + v;
        Double hit = this.tunnelCache.get(key);
        if (hit != null) {
            return hit;
        }
        double value = this.tunnel(d * TUNNEL_DUTY_STEP, v < 0 ? Double.NaN : v * TUNNEL_VOLT_STEP, c * TUNNEL_CLIMB_STEP);
        this.tunnelCache.put(key, value);
        return value;
    }

    public double tunnel(double duty, double busVoltage, double climb) {
        double soc = Double.isNaN(busVoltage) ? 1.0 : this.socForLoad(busVoltage, duty);
        FlatWorld w = this.world(soc);
        long settle = Math.round(TUNNEL_SETTLE * 1e9 / DroneSim.SUBSTEP_NANOS);
        long total = settle + Math.round(TUNNEL_AVERAGE * 1e9 / DroneSim.SUBSTEP_NANOS);
        double sum = 0.0;
        long n = 0;
        for (long k = 0; k < total; k++) {
            double bus = w.sim.power.busVoltage;
            double d = Double.isNaN(busVoltage) || bus <= 0.0 ? duty : Math.min(1.0, duty * busVoltage / bus);
            w.sim.setDutyOverride(d, d, d, d);
            step(w);
            w.sim.body.vy = climb;
            if (k >= settle) {
                sum += bodyUpSpecific(w.sim);
                n++;
            }
        }
        return sum / n;
    }

    public double dutyForThrust(double thrustToWeight, double busVoltage) {
        double lo = 0.0;
        double hi = 1.0;
        if (this.staticThrustToWeight(hi, busVoltage) < thrustToWeight) {
            return hi;
        }
        for (int k = 0; k < SOC_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (this.staticThrustToWeight(mid, busVoltage) < thrustToWeight) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    public double maxVoltage() {
        OperatingPoints.hover(this.params, 1.0, false, 0.0, true, this.point);
        return this.point.busVoltage;
    }

    public double socForLoad(double busVoltage, double duty) {
        double lo = SOC_FLOOR;
        double hi = 1.0;
        for (int k = 0; k < SOC_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            OperatingPoints.uniformDuty(this.params, duty, mid, false, 0.0, 0.0, this.point);
            if (this.point.busVoltage < busVoltage) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    public double socForHoverVoltage(double busVoltage) {
        double lo = SOC_FLOOR;
        double hi = 1.0;
        for (int k = 0; k < SOC_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            OperatingPoints.hover(this.params, mid, false, 0.0, true, this.point);
            if (this.point.busVoltage < busVoltage) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    static SimTuning tuning() {
        SimTuning t = new SimTuning();
        t.gyroNoiseDps = 0.0;
        t.gyroVibrationDps = 0.0;
        t.turbulenceScale = 0.0;
        t.windScale = 0.0;
        t.groundEffect = false;
        t.crashDisarm = false;
        t.propStrikes = false;
        t.thermalEffects = false;
        t.sleepEnabled = false;
        t.escOvervoltage = false;
        t.partCompatibility = false;
        return t;
    }

    FlatWorld world(double soc) {
        FlatWorld w = FlatWorld.of(this.build, tuning());
        w.groundY = FAR_BELOW;
        w.place(0.0, 0.0, 0.0, 0.0);
        w.sim.setBatteryCharge(soc);
        w.input.throttle = 0.0;
        w.arm();
        if (!w.sim.arming.armed) {
            StringBuilder why = new StringBuilder();
            for (ArmBlocker blocker : ArmBlocker.values()) {
                if ((w.sim.arming.blockers & blocker.bit()) != 0) {
                    why.append(' ').append(blocker.name());
                }
            }
            throw new IllegalStateException("Sim lässt sich nicht scharfschalten:" + why);
        }
        return w;
    }

    static double bodyUpSpecific(DroneSim sim) {
        RigidBody b = sim.body;
        double wx = sim.specificForceWorld(0);
        double wy = sim.specificForceWorld(1);
        double wz = sim.specificForceWorld(2);
        return (b.r01 * wx + b.r11 * wy + b.r21 * wz) / ModelConstants.G;
    }

    static double meanRpm(DroneSim sim) {
        double s = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            s += Math.abs(sim.power.omega[i]);
        }
        return s / AirframeSpec.MOTORS * RPM_PER_RAD;
    }

    static void step(FlatWorld w) {
        w.frame(DroneSim.SUBSTEP_NANOS);
        w.sim.body.setUpright(0.0);
        w.sim.body.setBodyRates(0.0, 0.0, 0.0);
    }

    static void holdAt(FlatWorld w, double duty, double seconds, double climb) {
        w.sim.setDutyOverride(duty, duty, duty, duty);
        long steps = Math.round(seconds * 1e9 / DroneSim.SUBSTEP_NANOS);
        for (long k = 0; k < steps; k++) {
            step(w);
            if (!Double.isNaN(climb)) {
                w.sim.body.vy = climb;
            }
        }
    }

    static void hold(FlatWorld w, double duty, double seconds) {
        w.sim.setDutyOverride(duty, duty, duty, duty);
        long steps = Math.round(seconds * 1e9 / DroneSim.SUBSTEP_NANOS);
        for (long k = 0; k < steps; k++) {
            step(w);
        }
    }

    public record Replay(Trace trace, double soc, double simVoltage, double dutyScale, String disarm) {
    }

    public Replay replay(Trace real, double accLpfHz) {
        return this.replay(real, accLpfHz, Double.NaN, Double.NaN);
    }

    public Replay replay(Trace real, double accLpfHz, double realHoverDuty, double realHoverVbat) {
        int preFrom = Math.max(0, real.step - (int) (0.15 * real.rate));
        int preTo = Math.max(1, real.step - (int) (0.02 * real.rate));
        double preV = Signals.mean(real.vbat, preFrom, real.step);
        double preReal = Signals.mean(real.duty, preFrom, preTo);
        double soc = Double.isNaN(preV) ? 1.0 : this.socForLoad(preV, Double.isNaN(preReal) ? 0.3 : preReal);
        FlatWorld w = this.world(soc);
        OperatingPoints.hover(this.params, soc, false, 0.0, true, this.point);
        double hover = Math.min(1.0, this.point.duty);
        double climb = Double.isNaN(real.startClimb) ? 0.0 : real.startClimb;
        holdAt(w, hover, PRE_ROLL, climb);
        double gain = 1.0;
        if (!Double.isNaN(realHoverDuty) && realHoverDuty > 0.0) {
            gain = this.hoverDuty(realHoverVbat) / realHoverDuty;
        } else {
            double preFz = Signals.mean(real.fz, preFrom, preTo);
            if (!Double.isNaN(preFz) && !Double.isNaN(preV) && preReal > 0.0) {
                gain = this.dutyForThrust(Math.max(0.05, preFz), preV) / preReal;
            }
        }
        double simV = w.sim.power.busVoltage;
        double scale = Double.isNaN(preV) || simV <= 0.0 ? 1.0 : preV / simV;
        Trace out = new Trace(real.rate, real.step, real.length());
        out.startClimb = real.startClimb;
        Signals.Pt2 filter = new Signals.Pt2(DroneSim.PHYSICS_HZ, accLpfHz);
        filter.apply(bodyUpSpecific(w.sim));
        long nanosPerSample = Math.round(1e9 / real.rate);
        long done = 0;
        long target = 0;
        double fz = Double.NaN;
        double d = hover;
        Measure.Transition tr = Measure.transition(real);
        for (int k = 0; k < real.length(); k++) {
            double blend = k <= tr.start() ? 1.0 : k >= tr.end() ? 0.0 : (tr.end() - k) / (double) Math.max(1, tr.end() - tr.start());
            double g = 1.0 + (gain - 1.0) * blend;
            if (!Double.isNaN(real.duty[k])) {
                d = Math.max(0.0, Math.min(1.0, real.duty[k] * g * scale));
            }
            w.sim.setDutyOverride(d, d, d, d);
            target += nanosPerSample;
            while (done + DroneSim.SUBSTEP_NANOS <= target) {
                step(w);
                done += DroneSim.SUBSTEP_NANOS;
                fz = filter.apply(bodyUpSpecific(w.sim));
            }
            out.duty[k] = d;
            out.fz[k] = fz;
            out.rpm[k] = meanRpm(w.sim);
            out.vbat[k] = w.sim.power.busVoltage;
            out.climb[k] = w.sim.body.vy;
        }
        Telemetry tm = w.sim.telemetry();
        return new Replay(out, soc, simV, scale, tm.armed ? "" : tm.lastDisarmReason.name());
    }

    public double terminalDescent(double idleDuty, double busVoltage) {
        double soc = Double.isNaN(busVoltage) ? 1.0 : this.socForHoverVoltage(busVoltage);
        FlatWorld w = this.world(soc);
        OperatingPoints.hover(this.params, soc, false, 0.0, true, this.point);
        hold(w, Math.min(1.0, this.point.duty), PRE_ROLL);
        double last = 0.0;
        for (double t = 0.0; t < TERMINAL_MAX_S; t += 0.25) {
            hold(w, idleDuty, 0.25);
            double v = -w.sim.body.vy;
            if (t > 1.0 && Math.abs(v - last) < TERMINAL_SETTLED * Math.max(1.0, v)) {
                return v;
            }
            last = v;
        }
        return last;
    }
}

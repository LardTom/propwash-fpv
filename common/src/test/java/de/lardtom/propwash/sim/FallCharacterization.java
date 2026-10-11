package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.Locale;
import java.util.function.Consumer;

final class FallCharacterization {
    static final double FPS = 144.0;
    static final double ALTITUDE = 3000.0;
    static final double SOC = 0.8;
    static final double[] DROP_TIMES = {0.5, 1.0, 2.0, 3.0};
    static final double TERMINAL_SECONDS = 40.0;
    static final double PUNCH_SECONDS = 1.0;

    private FallCharacterization() {
    }

    static final class Result {
        String name;
        double gravityScale;
        double massG;
        double hoverStickPct;
        double idleRpm;
        double hoverRpm;
        double idleThrustPct;
        double idleThrustDescentPct;
        final double[] dropSpeed = new double[DROP_TIMES.length];
        final double[] dropHeight = new double[DROP_TIMES.length];
        boolean airmode;
        double bellyIdle;
        double bellyIdleThrustPct;
        double bellyIdleDragPct;
        double bellyIdleWash;
        double bellyIdleNoWash;
        double bellyDisarmed;
        double noseIdle;
        double invertedIdle;
        double invertedFull;
        double climbFull;
        double punchSpeed;
        double coastTime;
        double coastHeight;
        double fallBackTime;
    }

    static SimTuning tuning(double gravityScale) {
        SimTuning t = new SimTuning();
        t.gravityScale = gravityScale;
        return t;
    }

    static FlatWorld hover(DroneBuild b, SimTuning t) {
        FlatWorld w = FlightCharacterization.hover(b, new FlightControllerConfig(), t, ALTITUDE, SOC, FPS);
        w.input.mode = StabMode.ACRO;
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        return w;
    }

    static Result measure(String name, DroneBuild b, double gravityScale) {
        Result r = new Result();
        r.name = name;
        r.gravityScale = gravityScale;
        AirframeSpec p = AirframeSpec.of(b);
        double weight = p.massKg * ModelConstants.G * gravityScale;
        r.massG = p.massKg * 1000.0;
        r.hoverRpm = p.hoverOmega * Math.sqrt(gravityScale) * 60.0 / SimMath.TWO_PI;
        idle(b, gravityScale, weight, r);
        drop(b, gravityScale, weight, r);
        r.bellyIdleNoWash = terminal(b, gravityScale, t -> {
            t.propWash = false;
            t.wakeField = false;
        }, false, 0.0);
        r.bellyDisarmed = disarmed(b, gravityScale);
        r.noseIdle = terminal(b, gravityScale, t -> {
        }, true, 0.0);
        r.invertedIdle = inverted(b, gravityScale, 0.0);
        r.invertedFull = inverted(b, gravityScale, 1.0);
        r.climbFull = climb(b, gravityScale);
        punch(b, gravityScale, r);
        return r;
    }

    static void idle(DroneBuild b, double gravityScale, double weight, Result r) {
        TestStand stand = new TestStand(b, tuning(gravityScale));
        stand.sim.setBatteryCharge(SOC);
        double idle = stand.sim.config().pid.motorIdlePercent / 100.0;
        stand.duty(idle);
        stand.substeps(6000);
        double sum = 0.0;
        double rpm = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            sum += stand.sim.thrust[i];
            rpm += stand.rpm(i);
        }
        r.idleRpm = rpm / AirframeSpec.MOTORS;
        r.idleThrustPct = 100.0 * sum / weight;
        r.hoverStickPct = 100.0 * stand.sim.telemetry().hoverThrottleStick;
    }

    static void drop(DroneBuild b, double gravityScale, double weight, Result r) {
        FlatWorld w = hover(b, tuning(gravityScale));
        r.airmode = w.sim.fc.airmodeLatched;
        RigidBody body = w.sim.body;
        double y0 = body.py;
        double dt = 1.0 / FPS;
        w.input.throttle = 0.0;
        int next = 0;
        double t = 0.0;
        int frames = (int) Math.round(TERMINAL_SECONDS * FPS);
        double vSum = 0.0;
        double thrustSum = 0.0;
        double dragSum = 0.0;
        double washSum = 0.0;
        int n = 0;
        for (int f = 0; f < frames; f++) {
            w.frame(dt);
            t += dt;
            while (next < DROP_TIMES.length && t >= DROP_TIMES[next] - 1e-9) {
                r.dropSpeed[next] = -body.vy;
                r.dropHeight[next] = y0 - body.py;
                next++;
            }
            if (t > TERMINAL_SECONDS - 2.0) {
                vSum += -body.vy;
                double thrust = 0.0;
                for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                    thrust += w.sim.thrust[i];
                }
                thrustSum += thrust * body.r11;
                dragSum += body.r10 * w.sim.bodyAero.fx + body.r11 * w.sim.bodyAero.fy + body.r12 * w.sim.bodyAero.fz;
                washSum += w.sim.telemetry().propWash;
                n++;
            }
        }
        r.bellyIdle = vSum / n;
        r.bellyIdleThrustPct = 100.0 * thrustSum / n / weight;
        r.bellyIdleDragPct = 100.0 * dragSum / n / weight;
        r.bellyIdleWash = washSum / n;
        r.idleThrustDescentPct = r.bellyIdleThrustPct;
    }

    static double terminal(DroneBuild b, double gravityScale, Consumer<SimTuning> change, boolean noseDown, double throttle) {
        SimTuning t = tuning(gravityScale);
        change.accept(t);
        FlatWorld w = hover(b, t);
        RigidBody body = w.sim.body;
        if (noseDown) {
            orient(body, Math.sqrt(0.5), Math.sqrt(0.5));
        }
        w.input.throttle = throttle;
        return meanSpeed(w, TERMINAL_SECONDS);
    }

    static double inverted(DroneBuild b, double gravityScale, double throttle) {
        FlatWorld w = hover(b, tuning(gravityScale));
        orient(w.sim.body, 0.0, 1.0);
        w.input.throttle = throttle;
        return meanSpeed(w, TERMINAL_SECONDS);
    }

    static double disarmed(DroneBuild b, double gravityScale) {
        FlatWorld w = hover(b, tuning(gravityScale));
        w.sim.forceDisarm(DisarmReason.SWITCH);
        w.input.throttle = 0.0;
        return meanSpeed(w, TERMINAL_SECONDS);
    }

    static double climb(DroneBuild b, double gravityScale) {
        FlatWorld w = hover(b, tuning(gravityScale));
        w.input.throttle = 1.0;
        double dt = 1.0 / FPS;
        double max = 0.0;
        for (int f = 0; f < (int) Math.round(15.0 * FPS); f++) {
            w.frame(dt);
            max = Math.max(max, w.sim.body.vy);
        }
        return max;
    }

    static void punch(DroneBuild b, double gravityScale, Result r) {
        FlatWorld w = hover(b, tuning(gravityScale));
        RigidBody body = w.sim.body;
        double dt = 1.0 / FPS;
        w.input.throttle = 1.0;
        for (int f = 0; f < (int) Math.round(PUNCH_SECONDS * FPS); f++) {
            w.frame(dt);
        }
        r.punchSpeed = body.vy;
        double yCut = body.py;
        w.input.throttle = 0.0;
        double t = 0.0;
        double apex = yCut;
        r.coastTime = Double.NaN;
        r.fallBackTime = Double.NaN;
        while (t < 20.0) {
            w.frame(dt);
            t += dt;
            apex = Math.max(apex, body.py);
            if (Double.isNaN(r.coastTime) && body.vy <= 0.0) {
                r.coastTime = t;
            }
            if (body.py <= yCut) {
                r.fallBackTime = t;
                break;
            }
        }
        r.coastHeight = apex - yCut;
    }

    static void orient(RigidBody body, double qw, double qx) {
        body.qw = qw;
        body.qx = qx;
        body.qy = 0.0;
        body.qz = 0.0;
        body.vx = 0.0;
        body.vy = 0.0;
        body.vz = 0.0;
        body.lx = 0.0;
        body.ly = 0.0;
        body.lz = 0.0;
        body.updateDerived();
    }

    static double meanSpeed(FlatWorld w, double seconds) {
        RigidBody body = w.sim.body;
        double dt = 1.0 / FPS;
        int frames = (int) Math.round(seconds * FPS);
        double sum = 0.0;
        int n = 0;
        for (int f = 0; f < frames; f++) {
            w.frame(dt);
            if (f >= frames - (int) Math.round(2.0 * FPS)) {
                sum += SimMath.hypot3(body.vx, body.vy, body.vz);
                n++;
            }
        }
        return sum / n;
    }

    static String header() {
        return String.format(Locale.ROOT,
                "%-26s %4s %7s %5s %6s %6s %5s %5s | %-23s | %-23s | %3s | %5s %5s %5s %5s %5s | %5s %5s %5s %5s %5s | %5s %5s %5s %5s %5s%n",
                "Aufbau", "g×", "Masse", "Gas%", "U/min", "U/min", "Idle", "IdleS", "v nach 0,5/1/2/3 s [m/s]", "Höhe 0,5/1/2/3 s [m]",
                "AM", "Bauch", "ohneW", "Schub", "Rumpf", "Wash", "aus", "Nase", "kopf0", "kopf1", "Steig", "v_cut", "t_apx", "h_apx",
                "t_bal", "h_bal");
    }

    static String row(Result r) {
        double g = ModelConstants.G * r.gravityScale;
        return String.format(Locale.ROOT,
                "%-26s %4.2f %7.1f %5.1f %6.0f %6.0f %5.1f %5.1f | %5.2f %5.2f %5.2f %5.2f | %5.2f %5.2f %5.2f %5.2f | %3s | %5.1f %5.1f %5.1f %5.1f %5.2f | %5.1f %5.1f %5.1f %5.1f %5.1f | %5.1f %5.2f %5.2f %5.2f %5.2f%n",
                r.name, r.gravityScale, r.massG, r.hoverStickPct, r.idleRpm, r.hoverRpm, r.idleThrustPct, r.idleThrustDescentPct,
                r.dropSpeed[0], r.dropSpeed[1], r.dropSpeed[2], r.dropSpeed[3], r.dropHeight[0], r.dropHeight[1], r.dropHeight[2],
                r.dropHeight[3], r.airmode ? "an" : "aus", r.bellyIdle, r.bellyIdleNoWash, r.bellyIdleThrustPct, r.bellyIdleDragPct,
                r.bellyIdleWash, r.bellyDisarmed, r.noseIdle, r.invertedIdle, r.invertedFull, r.climbFull, r.punchSpeed, r.coastTime,
                r.coastHeight, r.punchSpeed / g, r.punchSpeed * r.punchSpeed / (2.0 * g));
    }
}

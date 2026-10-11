package de.lardtom.propwash.sim.review;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StepResult;
import de.lardtom.propwash.sim.Telemetry;
import org.joml.Vector3d;

final class FeelReviewRig {
    final DroneSim sim;
    final PilotInput in = new PilotInput();
    final SimEnvironment env = new SimEnvironment();
    final StepResult out = new StepResult();
    final Vector3d pos = new Vector3d();
    final Vector3d vel = new Vector3d();
    private double iAlt;

    FeelReviewRig(DroneBuild build, FlightControllerConfig cfg, SimTuning tuning) {
        this.sim = new DroneSim(build, cfg, tuning, 42L);
    }

    static SimTuning quiet() {
        SimTuning t = new SimTuning();
        t.gyroNoiseDps = 0.0;
        t.gyroVibrationDps = 0.0;
        t.propWash = false;
        t.wakeField = false;
        t.groundEffect = false;
        return t;
    }

    Telemetry tm() {
        return this.sim.telemetry();
    }

    void arm() {
        this.in.armSwitch = false;
        this.sim.step(0L, this.in, this.env, this.out);
        this.in.armSwitch = true;
        this.sim.step(0L, this.in, this.env, this.out);
    }

    void frame(double dt) {
        this.sim.step(Math.round(dt * 1e9), this.in, this.env, this.out);
        this.sim.getPosition(this.pos);
        this.sim.getVelocity(this.vel);
    }

    void holdAltitude(double target, double dt) {
        this.sim.getPosition(this.pos);
        this.sim.getVelocity(this.vel);
        double err = target - this.pos.y;
        double base = this.tm().hoverThrottleStick;
        double raw = base + 0.08 * err - 0.06 * this.vel.y + this.iAlt;
        if (raw > 0.0 && raw < 1.0) {
            this.iAlt += 0.05 * err * dt;
        }
        this.iAlt = Math.max(-0.3, Math.min(0.3, this.iAlt));
        this.in.throttle = Math.max(0.0, Math.min(1.0, raw));
    }

    static FeelReviewRig hovering(DroneBuild b, FlightControllerConfig cfg, SimTuning t, double soc, double alt, double seconds, double fps) {
        FeelReviewRig r = new FeelReviewRig(b, cfg, t);
        r.sim.reset(0.0, alt, 0.0, 0.0);
        r.sim.setBatteryCharge(soc);
        r.in.mode = StabMode.ANGLE;
        r.in.throttle = 0.0;
        r.arm();
        double dt = 1.0 / fps;
        for (int f = 0; f < (int) Math.round(seconds * fps); f++) {
            r.holdAltitude(alt, dt);
            r.frame(dt);
        }
        return r;
    }
}

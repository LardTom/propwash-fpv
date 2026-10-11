package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

final class Tests {
    private Tests() {
    }

    static SimTuning quietTuning() {
        SimTuning t = new SimTuning();
        t.gyroNoiseDps = 0.0;
        t.gyroVibrationDps = 0.0;
        t.propWash = false;
        t.wakeField = false;
        return t;
    }

    static FlatWorld hovering(DroneBuild build, SimTuning tuning, double altitude, StabMode mode, double soc) {
        FlatWorld w = FlatWorld.of(build, tuning);
        w.place(0.0, altitude, 0.0, 0.0);
        w.sim.setBatteryCharge(soc);
        w.input.mode = StabMode.ANGLE;
        w.input.throttle = 0.0;
        w.arm();
        AltitudeHold hold = new AltitudeHold(altitude);
        double dt = 1.0 / 144.0;
        for (int i = 0; i < 432; i++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        w.input.mode = mode;
        return w;
    }

    static double rollRateDps(DroneSim sim) {
        return sim.body.wbz * SimMath.DEG;
    }

    static double pitchRateDps(DroneSim sim) {
        return sim.body.wbx * SimMath.DEG;
    }

    static double yawRateDps(DroneSim sim) {
        return -sim.body.wby * SimMath.DEG;
    }

    static double rollDeg(DroneSim sim) {
        return sim.telemetry().rollDeg;
    }
}

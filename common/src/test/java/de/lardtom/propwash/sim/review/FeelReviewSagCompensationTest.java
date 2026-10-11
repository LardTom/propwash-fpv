package de.lardtom.propwash.sim.review;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeelReviewSagCompensationTest {
    static final double FPS = 144.0;
    static final double DT = 1.0 / FPS;

    static final class Run {
        int framesAllMotorsPinned;
        double rotationAfterReleaseDeg;
        double stopMs;
    }

    static Run punchRoll(DroneBuild b, double soc, double sagComp) {
        FlightControllerConfig cfg = new FlightControllerConfig();
        cfg.pid.vbatSagCompensationPercent = sagComp;
        FeelReviewRig r = FeelReviewRig.hovering(b, cfg, FeelReviewRig.quiet(), soc, 500, 3.0, FPS);
        Telemetry t = r.tm();
        r.in.mode = StabMode.ACRO;
        r.in.throttle = 1.0;
        for (int f = 0; f < (int) (0.6 * FPS); f++) {
            r.frame(DT);
        }
        Run run = new Run();
        r.in.roll = 1.0;
        for (int f = 0; f < (int) (0.3 * FPS); f++) {
            r.frame(DT);
            double lo = Math.min(Math.min(t.motorOutput[0], t.motorOutput[1]), Math.min(t.motorOutput[2], t.motorOutput[3]));
            if (f > 10 && lo >= 0.999) {
                run.framesAllMotorsPinned++;
            }
        }
        r.in.roll = 0.0;
        run.stopMs = -1;
        for (int f = 0; f < (int) (0.5 * FPS); f++) {
            r.frame(DT);
            run.rotationAfterReleaseDeg += t.gyroDps[0] * DT;
            if (run.stopMs < 0 && Math.abs(t.gyroDps[0]) < 20.0) {
                run.stopMs = (f + 1) * DT * 1000.0;
            }
        }
        return run;
    }

    @Test
    void sagCompensationMustNotRemoveRollAuthorityAtFullThrottle() {
        StringBuilder log = new StringBuilder();
        boolean ok = true;
        for (DroneBuild b : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.CINEWHOOP}) {
            Run off = punchRoll(b, 0.3, 0.0);
            Run on = punchRoll(b, 0.3, 100.0);
            log.append(String.format("%s soc 0.3 full throttle + full roll: sagComp 0 -> pinned frames %d, stop %.0f ms, extra %.1f deg | "
                            + "sagComp 100 -> pinned frames %d, stop %.0f ms, extra %.1f deg%n", b.frame(), off.framesAllMotorsPinned, off.stopMs,
                    off.rotationAfterReleaseDeg, on.framesAllMotorsPinned, on.stopMs, on.rotationAfterReleaseDeg));
            ok &= on.framesAllMotorsPinned <= off.framesAllMotorsPinned + 1;
            ok &= on.rotationAfterReleaseDeg <= 2.0 * off.rotationAfterReleaseDeg + 2.0;
        }
        System.out.print(log);
        assertTrue(ok, "sag compensation clips all motors at 100 % and costs roll authority:\n" + log);
    }
}

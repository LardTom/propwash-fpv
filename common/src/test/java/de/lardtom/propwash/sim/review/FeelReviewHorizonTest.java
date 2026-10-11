package de.lardtom.propwash.sim.review;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.StabMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeelReviewHorizonTest {
    static final double FPS = 144.0;
    static final double DT = 1.0 / FPS;

    static double steadyRoll(StabMode mode, double stick) {
        FeelReviewRig r = FeelReviewRig.hovering(DroneBuild.FREESTYLE, new FlightControllerConfig(), FeelReviewRig.quiet(), 0.8, 500, 3.0, FPS);
        r.in.mode = mode;
        r.in.roll = stick;
        for (int f = 0; f < (int) (4.0 * FPS); f++) {
            r.holdAltitude(500, DT);
            r.frame(DT);
        }
        return r.tm().rollDeg;
    }

    @Test
    void horizonWithSmallStickLeansAtLeastAsFarAsAngle() {
        StringBuilder log = new StringBuilder();
        boolean ok = true;
        for (double stick : new double[]{0.1, 0.2, 0.3}) {
            double angle = steadyRoll(StabMode.ANGLE, stick);
            double horizon = steadyRoll(StabMode.HORIZON, stick);
            log.append(String.format("roll stick %.1f held: ANGLE %.1f deg, HORIZON %.1f deg%n", stick, angle, horizon));
            ok &= horizon >= angle - 0.5;
        }
        System.out.print(log);
        assertTrue(ok, "HORIZON self-levels towards flat instead of towards the stick angle:\n" + log);
    }
}

package de.lardtom.propwash.sim.review;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.FlightControllerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeelReviewThrustLinearTest {
    static final double FPS = 144.0;
    static final double DT = 1.0 / FPS;

    static double[] hover(DroneBuild b, double tl) {
        FlightControllerConfig cfg = new FlightControllerConfig();
        cfg.pid.thrustLinearPercent = tl;
        FeelReviewRig r = FeelReviewRig.hovering(b, cfg, FeelReviewRig.quiet(), 0.5, 500, 4.0, FPS);
        double sum = 0.0;
        int n = 0;
        for (int f = 0; f < (int) (2.0 * FPS); f++) {
            r.holdAltitude(500, DT);
            r.frame(DT);
            sum += r.in.throttle;
            n++;
        }
        return new double[]{sum / n, r.tm().airmodeActive ? 1.0 : 0.0};
    }

    @Test
    void thrustLinearMustNotShiftHoverThrottle() {
        StringBuilder log = new StringBuilder();
        boolean ok = true;
        for (DroneBuild b : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.FREESTYLE}) {
            double[] base = hover(b, 0.0);
            double[] tl40 = hover(b, 40.0);
            log.append(String.format("%s hover throttle TL 0: %.1f %%, TL 40: %.1f %% (airmode latched in hover: %s)%n", b.frame(),
                    100 * base[0], 100 * tl40[0], tl40[1] > 0.5));
            ok &= Math.abs(tl40[0] - base[0]) <= 0.03;
            ok &= tl40[1] > 0.5;
        }
        System.out.print(log);
        assertTrue(ok, "thrust linearization shifts the hover point (Betaflight compensates the throttle):\n" + log);
    }
}

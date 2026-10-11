package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EulerPathTest {
    private static final double UPTILT = 25.0;

    static List<Quaterniond> maneuver(double headingDeg, double pitchRatePerTick, double rollRatePerTick, double yawRatePerTick, int ticks) {
        List<Quaterniond> out = new ArrayList<>();
        Quaterniond body = new Quaterniond().rotationY(Math.toRadians(headingDeg));
        for (int i = 0; i <= ticks; i++) {
            out.add(new Quaterniond(body).rotateX(Math.toRadians(UPTILT)));
            body.rotateX(Math.toRadians(pitchRatePerTick));
            body.rotateZ(Math.toRadians(rollRatePerTick));
            body.rotateY(Math.toRadians(yawRatePerTick));
        }
        return out;
    }

    @Test
    void levelFlightNeedsNoCorrection() {
        List<Quaterniond> path = maneuver(30.0, 0.0, 0.0, 4.0, 80);
        List<CameraAngles> angles = EulerPath.continuous(path);
        assertEquals(EulerPath.canonical(path), angles);
        assertTrue(EulerPath.worstInterpolationError(path, angles, 10) < 0.5);
    }

    @Test
    void rollsAndYawSpinsInterpolateExactly() {
        List<Quaterniond> roll = maneuver(-60.0, 0.0, 36.0, 0.0, 40);
        assertTrue(EulerPath.worstInterpolationError(roll, EulerPath.continuous(roll), 10) < 2.0);
        List<Quaterniond> spin = maneuver(10.0, 0.0, 0.0, 30.0, 40);
        assertTrue(EulerPath.worstInterpolationError(spin, EulerPath.continuous(spin), 10) < 0.01);
    }

    @Test
    void flipsDoNotTwistBetweenKeyframes() {
        double worstCanonical = 0.0;
        double worstContinuous = 0.0;
        double[] headings = {0.0, 37.0, -115.0, 170.0};
        double[][] rates = {{36.0, 0.0, 0.0}, {-36.0, 0.0, 0.0}, {36.0, 1.5, 0.0}, {-30.0, -2.0, 0.5}, {40.0, 4.0, 2.0},
                {24.0, -6.0, 0.0}, {45.0, 0.0, -3.0}, {18.0, 0.7, 0.2}};
        for (double heading : headings) {
            for (double[] rate : rates) {
                List<Quaterniond> path = maneuver(heading, rate[0], rate[1], rate[2], 60);
                double canonical = EulerPath.worstInterpolationError(path, EulerPath.canonical(path), 20);
                double continuous = EulerPath.worstInterpolationError(path, EulerPath.continuous(path), 20);
                worstCanonical = Math.max(worstCanonical, canonical);
                worstContinuous = Math.max(worstContinuous, continuous);
            }
        }
        assertTrue(worstCanonical > 90.0, "ohne Korrektur verdrehen Flips, war " + worstCanonical);
        assertTrue(worstContinuous < 25.0, "mit Korrektur höchstens 25°, war " + worstContinuous);
    }

    @Test
    void keyframeAnglesStayCloseToTheRealCamera() {
        List<Quaterniond> path = maneuver(80.0, 33.0, 2.5, 1.0, 120);
        List<CameraAngles> angles = EulerPath.continuous(path);
        for (int i = 0; i < path.size(); i++) {
            assertTrue(CameraAngles.degreesBetween(angles.get(i).rotation(), path.get(i)) <= EulerPath.KEYFRAME_LIMIT_DEGREES + 1.0E-6, "Tick " + i);
            assertTrue(angles.get(i).pitch() >= -90.0 && angles.get(i).pitch() <= 90.0);
        }
    }
}

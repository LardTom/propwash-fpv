package de.lardtom.propwash.trick;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrickKinematicTest {
    private static final double ORBIT_RADIUS = 6.0;
    private static final double ORBIT_RATE = 1.0;

    private static Kinematic.Path hover(double x, double y, double z) {
        return (t, p, q) -> {
            p.set(x, y, z);
            q.identity();
        };
    }

    private static Kinematic.Path orbit(double noseOffsetRad) {
        return (t, p, q) -> {
            double a = ORBIT_RATE * t;
            p.set(ORBIT_RADIUS * Math.sin(a), 60.0, ORBIT_RADIUS * Math.cos(a));
            Kinematic.heading(a + Math.PI + noseOffsetRad, Math.toRadians(15.0), 0.0, q);
        };
    }

    private static Kinematic.Path pitchSpin(double rateDps, double speed) {
        return (t, p, q) -> {
            p.set(0.0, 60.0 - 2.0 * t * t, speed * t);
            Kinematic.heading(0.0, Math.toRadians(rateDps * t), 0.0, q);
        };
    }

    @Test
    void orbitAroundAPointWithTheNoseInside() {
        Kinematic k = new Kinematic();
        k.run(7.0, orbit(0.0));
        assertEquals(List.of(TrickType.ORBIT), k.types());
        Kinematic twice = new Kinematic();
        twice.run(13.5, orbit(0.0));
        assertEquals(List.of(TrickType.ORBIT, TrickType.ORBIT), twice.types());
    }

    @Test
    void circlingWithTheNoseAheadIsJustATurn() {
        Kinematic k = new Kinematic();
        k.run(14.0, orbit(-Math.PI / 2.0));
        assertEquals(List.of(), k.types());
    }

    @Test
    void strafingStraightIsNoOrbit() {
        Kinematic k = new Kinematic();
        k.run(8.0, (t, p, q) -> {
            p.set(5.0 * t, 60.0, 0.0);
            q.identity();
        });
        assertEquals(List.of(), k.types());
    }

    @Test
    void cleanFlipIsDetectedFromAKinematicPath() {
        Kinematic k = new Kinematic();
        k.run(0.5, hover(0.0, 60.0, 0.0));
        k.run(0.6, pitchSpin(600.0, 0.0));
        k.run(1.0, hover(0.0, 59.28, 0.0));
        assertEquals(List.of(TrickType.FLIP), k.types());
    }

    @Test
    void frameRateDoesNotMatter() {
        for (double hz : new double[]{20.0, 30.0, 60.0, 144.0, 240.0}) {
            Kinematic k = new Kinematic();
            k.hz = hz;
            k.run(0.5, hover(0.0, 60.0, 0.0));
            k.run(0.6, pitchSpin(600.0, 0.0));
            k.run(1.0, hover(0.0, 59.28, 0.0));
            assertEquals(List.of(TrickType.FLIP), k.types(), hz + " Hz");
        }
    }

    @Test
    void crashTumbleNeverScores() {
        Kinematic k = new Kinematic();
        k.run(0.5, hover(0.0, 60.0, 0.0));
        k.crashCount = 1;
        k.run(1.2, pitchSpin(700.0, 3.0));
        k.run(1.0, hover(0.0, 57.12, 3.6));
        assertEquals(List.of(), k.types());
        assertEquals(0, k.session.score().flightScore());
    }

    @Test
    void disarmedTumbleNeverScores() {
        Kinematic k = new Kinematic();
        k.armed = false;
        k.run(0.5, hover(0.0, 60.0, 0.0));
        k.run(1.2, pitchSpin(700.0, 3.0));
        k.run(1.0, hover(0.0, 57.12, 3.6));
        assertEquals(List.of(), k.types());
    }

    @Test
    void rescueAutopilotSuppressesTricks() {
        Kinematic k = new Kinematic();
        k.assisted = true;
        k.run(0.5, hover(0.0, 60.0, 0.0));
        k.run(0.6, pitchSpin(600.0, 0.0));
        k.run(1.0, hover(0.0, 59.28, 0.0));
        assertEquals(List.of(), k.types());
    }

    @Test
    void teleportInTheMiddleCancelsTheManoeuvre() {
        Kinematic k = new Kinematic();
        k.run(0.5, hover(0.0, 60.0, 0.0));
        k.run(0.3, pitchSpin(600.0, 0.0));
        k.run(0.3, (t, p, q) -> {
            p.set(400.0, 60.0, 0.0);
            Kinematic.heading(0.0, Math.toRadians(180.0 + 600.0 * t), 0.0, q);
        });
        k.run(1.0, hover(400.0, 60.0, 0.0));
        assertEquals(List.of(), k.types());
    }

    @Test
    void touchingABlockDuringAFlipRuinsIt() {
        Kinematic k = new Kinematic();
        k.run(0.5, hover(0.0, 60.0, 0.0));
        k.run(0.3, pitchSpin(600.0, 0.0));
        k.impactCount++;
        k.run(0.3, (t, p, q) -> {
            p.set(0.0, 59.8, 0.0);
            Kinematic.heading(0.0, Math.toRadians(180.0 + 600.0 * t), 0.0, q);
        });
        k.run(1.0, hover(0.0, 59.8, 0.0));
        assertEquals(List.of(), k.types());
    }

    @Test
    void gapWithContactDoesNotCount() {
        Kinematic k = new Kinematic();
        k.nearby = (t, s) -> {
            s.clearanceKnown = true;
            boolean inside = t >= 1.0 && t <= 1.15;
            for (int i = 0; i < TrickSample.PROBES; i++) {
                s.clearance[i] = inside && i < 2 ? 0.7 : Double.POSITIVE_INFINITY;
                s.clearanceDirY[i] = i == 2 ? 1.0 : i == 3 ? -1.0 : 0.0;
            }
        };
        Kinematic.Path straight = (t, p, q) -> {
            p.set(0.0, 60.0, 10.0 * t);
            q.identity();
        };
        k.run(2.0, straight);
        assertEquals(List.of(TrickType.GAP), k.types());
        Kinematic touched = new Kinematic();
        touched.nearby = k.nearby;
        touched.run(1.05, straight);
        touched.impactCount++;
        touched.run(1.0, (t, p, q) -> {
            p.set(0.0, 60.0, 10.5 + 10.0 * t);
            q.identity();
        });
        assertEquals(List.of(), touched.types());
    }

    @Test
    void longTunnelIsNoGap() {
        Kinematic k = new Kinematic();
        k.nearby = (t, s) -> {
            s.clearanceKnown = true;
            for (int i = 0; i < TrickSample.PROBES; i++) {
                s.clearance[i] = i < 2 ? 1.0 : 4.0;
                s.clearanceDirY[i] = i == 2 ? 1.0 : i == 3 ? -1.0 : 0.0;
            }
        };
        k.run(6.0, (t, p, q) -> {
            p.set(0.0, 60.0, 5.0 * t);
            q.identity();
        });
        k.nearby = null;
        k.run(1.0, (t, p, q) -> {
            p.set(0.0, 60.0, 30.0 + 5.0 * t);
            q.identity();
        });
        assertEquals(List.of(), k.types());
    }

    @Test
    void slowFlightNextToWallsIsNoProximity() {
        Kinematic k = new Kinematic();
        k.nearby = (t, s) -> {
            s.clearanceKnown = true;
            for (int i = 0; i < TrickSample.PROBES; i++) {
                s.clearance[i] = i == 0 ? 0.5 : Double.POSITIVE_INFINITY;
                s.clearanceDirY[i] = i == 2 ? 1.0 : i == 3 ? -1.0 : 0.0;
            }
        };
        k.run(4.0, (t, p, q) -> {
            p.set(0.0, 60.0, 3.0 * t);
            q.identity();
        });
        assertEquals(List.of(), k.types());
    }

    @Test
    void steadyHoverAndCruiseStayQuiet() {
        Kinematic k = new Kinematic();
        k.run(5.0, hover(0.0, 60.0, 0.0));
        k.run(10.0, (t, p, q) -> {
            p.set(0.0, 60.0 + Math.sin(t), 15.0 * t);
            Kinematic.heading(0.0, Math.toRadians(40.0), Math.toRadians(20.0 * Math.sin(t)), q);
        });
        assertEquals(List.of(), k.types());
    }
}

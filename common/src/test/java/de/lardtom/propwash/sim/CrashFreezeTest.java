package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashFreezeTest {
    static final double FPS = 144.0;
    static final double DT = 1.0 / FPS;
    static final double HANG_LIMIT_S = 0.1;
    static final double UNSUPPORTED_GAP = 0.02;

    static final class Track {
        double maxHang;
        double hang;
        double time;
        boolean crashed;
        boolean hitX;
        boolean hitY;
        boolean hitCeiling;
        boolean everGrounded;
        double maxBottom = Double.NEGATIVE_INFINITY;

        String describe(BoxWorld w) {
            return String.format(Locale.ROOT, "hang %.3f s, gap %.3f m, lowest %.3f m, speed %.3f m/s, grounded %s, sleeping %s, armed %s, crash %s",
                    this.maxHang, w.restGap(), w.lowest(), w.speed(), w.sim.telemetry().grounded, w.sim.telemetry().sleeping,
                    w.sim.telemetry().armed, this.crashed);
        }
    }

    private static SimTuning tuning(boolean crashDisarm, boolean propStrikes) {
        SimTuning t = Tests.quietTuning();
        t.crashDisarm = crashDisarm;
        t.propStrikes = propStrikes;
        return t;
    }

    private static Track run(BoxWorld w, double seconds, Consumer<Track> perFrame) {
        return run(w, seconds, perFrame, null);
    }

    private static Track run(BoxWorld w, double seconds, Consumer<Track> perFrame, Predicate<BoxWorld> stop) {
        Track t = new Track();
        long frames = Math.round(seconds * FPS);
        for (long f = 0; f < frames; f++) {
            if (stop != null && stop.test(w)) {
                break;
            }
            if (perFrame != null) {
                perFrame.accept(t);
            }
            w.frame(DT);
            t.time += DT;
            t.crashed |= w.sim.events().contains(SimEventType.CRASH);
            t.hitX |= w.report.hitX || w.report.hitZ;
            t.hitY |= w.report.hitY || w.sim.telemetry().grounded;
            t.hitCeiling |= w.report.hitY && w.report.requestedY > 0.0;
            t.everGrounded |= w.sim.telemetry().grounded;
            t.maxBottom = Math.max(t.maxBottom, w.bottom());
            boolean unsupported = w.restGap() > UNSUPPORTED_GAP;
            boolean stalled = Math.abs(w.sim.body.vy) < 0.05;
            if (unsupported && stalled && !thrusting(w)) {
                t.hang += DT;
                t.maxHang = Math.max(t.maxHang, t.hang);
            } else {
                t.hang = 0.0;
            }
        }
        return t;
    }

    private static boolean thrusting(BoxWorld w) {
        Telemetry t = w.sim.telemetry();
        return t.totalThrustN > 0.9 * w.sim.params().massKg * ModelConstants.G;
    }

    private static void assertRestsOnSomething(BoxWorld w, Track t, String label) {
        String info = label + ": " + t.describe(w);
        assertTrue(t.maxHang < HANG_LIMIT_S, "hängt in der Luft – " + info);
        assertTrue(w.restGap() <= 0.01, "liegt nicht auf – " + info);
        assertTrue(w.speed() < 0.1, "kommt nicht zur Ruhe – " + info);
    }

    private static void hoverAt(BoxWorld w, double bottom) {
        w.input.mode = StabMode.ANGLE;
        w.input.throttle = 0.0;
        w.arm();
        AltitudeHold hold = new AltitudeHold(bottom - w.foot);
        for (int f = 0; f < 288; f++) {
            w.input.throttle = hold.throttle(w.sim, DT);
            w.frame(DT);
        }
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
    }

    @Test
    void disarmedDroneSlidingOffLedgeFallsToGround() {
        for (double v : new double[]{2.0, 4.0, 8.0}) {
            BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
            w.ground(0.0);
            w.box(-4.0, 0.0, -4.0, 0.0, 3.0, 4.0);
            w.placeBottom(-0.1, 3.0, 0.0, 0.0);
            w.launch(v, 0.0, 0.0);
            Track t = run(w, 4.0, null);
            assertRestsOnSomething(w, t, "Kante " + v + " m/s");
            assertTrue(w.lowest() < 0.01, "nicht am Boden unter der Kante: " + t.describe(w));
        }
    }

    @Test
    void crashOnWallTopEdgeFallsToGround() {
        for (boolean disarm : new boolean[]{true, false}) {
            for (boolean strikes : new boolean[]{true, false}) {
                for (double[] v : new double[][]{{6.0, -0.3}, {6.0, -3.0}, {14.0, -0.3}, {14.0, -3.0}, {20.0, -3.0}}) {
                    BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, tuning(disarm, strikes));
                    w.ground(0.0);
                    w.box(0.0, 0.0, -4.0, 1.0, 4.0, 4.0);
                    w.placeBottom(0.3, 4.3, 0.0, 0.0);
                    hoverAt(w, 4.3);
                    w.x = 0.3;
                    w.y = 4.01 - w.foot;
                    w.z = 0.0;
                    w.sim.teleport(w.x, w.y, w.z, false);
                    w.launch(v[0], v[1], 0.0);
                    Track t = run(w, 5.0, tr -> {
                        if (tr.hitY) {
                            w.input.throttle = 0.0;
                        }
                    });
                    String label = String.format(Locale.ROOT, "Mauerkrone %.0f m/s, sinkend %.1f m/s, Crash-Disarm %s, Prop-Strikes %s", v[0], -v[1], disarm, strikes);
                    assertTrue(t.hitY, label + ": Mauerkrone nicht getroffen");
                    assertRestsOnSomething(w, t, label);
                    assertTrue(w.lowest() < 0.01, label + ": nicht am Boden: " + t.describe(w));
                }
            }
        }
    }

    @Test
    void wallFaceCrashAtSpeedsAndAnglesFallsToGround() {
        for (boolean disarm : new boolean[]{true, false}) {
            for (boolean strikes : new boolean[]{true, false}) {
                for (double v : new double[]{9.0, 15.0, 25.0, 40.0}) {
                    for (double angle : new double[]{0.0, 30.0, 60.0}) {
                        BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, tuning(disarm, strikes));
                        w.ground(0.0);
                        w.box(0.0, 0.0, -20.0, 1.0, 12.0, 20.0);
                        w.placeBottom(-1.5, 4.0, 0.0, 0.0);
                        hoverAt(w, 4.0);
                        double a = Math.toRadians(angle);
                        w.launch(v * Math.cos(a), 0.0, v * Math.sin(a));
                        Track t = run(w, 5.0, tr -> {
                            if (tr.hitX) {
                                w.input.throttle = 0.0;
                            }
                        });
                        String label = String.format(Locale.ROOT, "Wand %.0f m/s unter %.0f°, Crash-Disarm %s, Prop-Strikes %s", v, angle, disarm, strikes);
                        assertTrue(t.hitX, label + ": Wand nicht getroffen");
                        assertTrue(t.crashed, label + ": kein Absturz erkannt");
                        assertRestsOnSomething(w, t, label);
                        assertTrue(w.lowest() < 0.01, label + ": nicht am Boden: " + t.describe(w));
                    }
                }
            }
        }
    }

    @Test
    void ceilingHitFallsBackToGround() {
        for (boolean disarm : new boolean[]{true, false}) {
            BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, tuning(disarm, true));
            w.ground(0.0);
            w.box(-20.0, 6.0, -20.0, 20.0, 7.0, 20.0);
            w.placeBottom(0.0, 3.0, 0.0, 0.0);
            hoverAt(w, 3.0);
            w.input.throttle = 1.0;
            Track t = run(w, 5.0, tr -> {
                if (tr.hitCeiling) {
                    w.input.throttle = 0.0;
                }
            });
            String label = "Decke, Crash-Disarm " + disarm;
            assertTrue(t.hitCeiling, label + ": Decke nicht getroffen");
            assertRestsOnSomething(w, t, label);
            assertTrue(w.lowest() < 0.01, label + ": nicht am Boden: " + t.describe(w));
        }
    }

    @Test
    void armedDroneSlidingOverBlockEdgeDescends() {
        for (double factor : new double[]{0.0, 0.5, 0.7}) {
            BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
            w.ground(0.0);
            w.box(-8.0, 0.0, -4.0, 0.0, 2.0, 4.0);
            w.placeBottom(-1.0, 2.0, 0.0, 0.0);
            w.input.throttle = 0.0;
            w.arm();
            run(w, 0.3, null);
            w.input.throttle = factor * w.sim.telemetry().hoverThrottleStick;
            run(w, 0.3, null);
            assertTrue(w.sim.telemetry().grounded, "steht nicht auf dem Block bei Gas " + factor + ", Unterkante " + w.bottom() + ", Lücke " + w.supportGap());
            w.launch(5.0, 0.0, 0.0);
            run(w, 1.0, null, world -> world.x - world.halfWidth > 0.0);
            assertTrue(w.sim.telemetry().armed, "Abgleiten darf nicht entschärfen");
            double start = w.bottom();
            run(w, 0.6, null);
            assertTrue(w.bottom() < start - 0.3, String.format(Locale.ROOT,
                    "Gas %.2f × Schwebegas: gleitet hinter der Kante auf unsichtbarem Boden, Unterkante %.3f m nach 0,6 s (Start %.3f m)",
                    factor, w.bottom(), start));
        }
    }

    @Test
    void restingDroneFallsWhenSupportIsRemoved() {
        BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
        w.ground(0.0);
        double[] platform = w.box(-1.0, 0.0, -1.0, 1.0, 2.0, 1.0);
        w.placeBottom(0.0, 2.05, 0.0, 0.0);
        run(w, 3.0, null);
        assertTrue(w.sim.telemetry().sleeping, "schläft nicht auf dem Podest");
        w.boxes.remove(platform);
        Track t = run(w, 2.0, null);
        assertRestsOnSomething(w, t, "Podest entfernt");
        assertTrue(w.lowest() < 0.01, "fällt nicht nach dem Entfernen: " + t.describe(w));
    }

    @Test
    void serverResetIntoAirFromRestFalls() {
        for (double drop : new double[]{4.0, -1.5}) {
            BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
            w.ground(0.0);
            w.box(-1.0, 0.0, -1.0, 1.0, 2.0, 1.0);
            w.placeBottom(0.0, 2.05, 0.0, 0.0);
            run(w, 3.0, null);
            assertTrue(w.sim.telemetry().sleeping, "schläft nicht auf dem Podest");
            SimSnapshot s = new SimSnapshot();
            w.sim.copyStateTo(s);
            s.px += 3.0;
            s.py += drop;
            s.vx = 0.0;
            s.vy = 0.0;
            s.vz = 0.0;
            w.sim.copyStateFrom(s);
            w.x += 3.0;
            w.y += drop;
            Track t = run(w, 3.0, null);
            String label = String.format(Locale.ROOT, "Server-Reset %+.1f m neben das Podest", drop);
            assertRestsOnSomething(w, t, label);
            assertTrue(w.lowest() < 0.01, label + ": nicht am Boden: " + t.describe(w));
        }
    }

    @Test
    void armedBumpKeepsFlying() {
        BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
        w.ground(0.0);
        w.box(0.0, 0.0, -20.0, 1.0, 12.0, 20.0);
        w.placeBottom(-1.0, 4.0, 0.0, 0.0);
        hoverAt(w, 4.0);
        AltitudeHold hold = new AltitudeHold(w.y);
        w.launch(3.0, 0.0, 0.0);
        Track t = run(w, 2.0, tr -> w.input.throttle = hold.throttle(w.sim, DT));
        assertTrue(t.hitX, "Wand nicht getroffen");
        assertFalse(t.crashed, "leichter Stoß als Absturz gewertet");
        assertTrue(w.sim.telemetry().armed, "leichter Stoß entschärft");
        assertFalse(w.sim.telemetry().grounded, "nach dem Stoß am Boden gemeldet");
        assertTrue(Math.abs(w.bottom() - 4.0) < 0.5, "Höhe nach dem Stoß nicht gehalten: " + t.describe(w));
        double before = w.bottom();
        w.input.throttle = 1.0;
        run(w, 0.5, null);
        assertTrue(w.bottom() > before + 1.0, "steigt nach dem Stoß nicht mehr: " + t.describe(w));
    }
}

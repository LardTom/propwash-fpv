package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurtleEdgeRimTest {
    private static final double FPS = 60.0;
    private static final double TOP = 3.0;
    private static final double HALF_BEAM = 0.25;
    private static final double FALL_SECONDS = 3.0;
    private static final double REST_SECONDS = 6.0;
    private static final double[] RIM = {0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95, 0.99};
    private static final DroneBuild[] BUILDS = DroneBuild.PRESETS.toArray(new DroneBuild[0]);

    enum Side {
        EAST(1.0, 0.0),
        WEST(-1.0, 0.0),
        SOUTH(0.0, 1.0),
        NORTH(0.0, -1.0);

        final double ux;
        final double uz;

        Side(double ux, double uz) {
            this.ux = ux;
            this.uz = uz;
        }

        double[] box(double minU, double maxU, double minV, double maxV) {
            if (this.uz == 0.0) {
                return this.ux > 0.0 ? new double[]{minU, minV, maxU, maxV} : new double[]{-maxU, minV, -minU, maxV};
            }
            return this.uz > 0.0 ? new double[]{minV, minU, maxV, maxU} : new double[]{minV, -maxU, maxV, -minU};
        }
    }

    record Outcome(double fellAt, double finalSpeed, double edgeTravel, boolean onEdge, boolean armed) {
        boolean fell() {
            return !Double.isNaN(this.fellAt);
        }
    }

    static LedgeWorld ledge(DroneBuild build, boolean gate, Side side, double rim, boolean inverted) {
        LedgeWorld w = LedgeWorld.of(build);
        w.floor(0.0);
        if (gate) {
            double[] b = side.box(-HALF_BEAM, HALF_BEAM, -2.0, 2.0);
            w.gate(b[0], TOP - 0.5, b[1], b[2], TOP, b[3]);
        } else {
            double[] b = side.box(-1.0, HALF_BEAM, -2.0, 2.0);
            w.block(b[0], TOP - 1.0, b[1], b[2], TOP, b[3]);
        }
        double u = HALF_BEAM + rim * w.halfWidth;
        w.placeBottom(side.ux * u, TOP, side.uz * u, inverted);
        return w;
    }

    static Outcome run(LedgeWorld w, boolean turtle) {
        for (int i = 0; i < 3; i++) {
            w.frame(1.0 / FPS);
        }
        boolean onEdge = w.sim.telemetry().grounded && Math.abs(w.bottom() - TOP) < 1.0e-4;
        if (turtle) {
            w.input.turtle = true;
            w.arm();
        }
        boolean armed = w.sim.telemetry().armed;
        double fellAt = Double.NaN;
        double travel = 0.0;
        double lastTilt = w.sim.telemetry().tiltDeg;
        int frames = (int) Math.round(REST_SECONDS * FPS);
        for (int f = 0; f < frames; f++) {
            w.frame(1.0 / FPS);
            double tilt = w.sim.telemetry().tiltDeg;
            if (Double.isNaN(fellAt)) {
                travel += Math.abs(tilt - lastTilt);
                if (w.bottom() < TOP - 1.0) {
                    fellAt = f / FPS;
                }
            }
            lastTilt = tilt;
        }
        RigidBody b = w.sim.body;
        return new Outcome(fellAt, Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz), travel, onEdge, armed);
    }

    private static String label(DroneBuild build, boolean gate, Side side, double rim, String how) {
        return String.format(Locale.ROOT, "%s %s %s, Schwerpunkt %.0f %% der halben Hitbox-Breite hinter der Kante, %s", build.frame(),
                gate ? "Gate-Balken" : "Blockkante", side, rim * 100.0, how);
    }

    private static void sweep(boolean inverted, boolean turtle, Side[] sides, String how) {
        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (DroneBuild build : BUILDS) {
            for (boolean gate : new boolean[]{true, false}) {
                for (Side side : sides) {
                    for (double rim : RIM) {
                        cases++;
                        Outcome o = run(ledge(build, gate, side, rim, inverted), turtle);
                        String name = label(build, gate, side, rim, how);
                        if (turtle && o.onEdge() && !o.armed()) {
                            failures.add(name + ": Turtle nicht scharf " + o);
                        } else if (!o.fell() || o.fellAt() > FALL_SECONDS) {
                            failures.add(name + ": liegt noch oben " + o);
                        } else if (o.finalSpeed() >= 0.05) {
                            failures.add(name + ": rollt am Boden weiter " + o);
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " von " + cases + " Fällen:\n" + String.join("\n", failures));
    }

    @Test
    void theSupportGridReachesTheRimOfTheHitbox() {
        for (DroneBuild build : BUILDS) {
            double width = AirframeSpec.of(build).hitboxWidth;
            double spacing = SimEnvironment.supportSpacing(width);
            double outer = SimEnvironment.supportOffset(SimEnvironment.SUPPORT_SIDE - 1, spacing);
            assertEquals(-outer, SimEnvironment.supportOffset(0, spacing), 1.0e-12, build.frame().toString());
            assertTrue(outer < 0.5 * width, build.frame() + ": äußere Spalte außerhalb der Hitbox " + outer);
            assertTrue(outer > 0.5 * width - 1.0e-5, build.frame() + ": äußere Spalte " + outer + " bei halber Breite " + 0.5 * width);
        }
    }

    @Test
    void turtleUpsideDownWithTheCentreOfMassNearTheRimFallsOffEveryEdge() {
        sweep(true, true, Side.values(), "kopfüber, Turtle scharf, Knüppel mittig");
    }

    @Test
    void disarmedUpsideDownWithTheCentreOfMassNearTheRimFallsOff() {
        sweep(true, false, new Side[]{Side.EAST, Side.SOUTH}, "kopfüber, unscharf");
    }

    @Test
    void disarmedUprightWithTheCentreOfMassNearTheRimTipsOver() {
        sweep(false, false, new Side[]{Side.WEST, Side.NORTH}, "aufrecht, unscharf");
    }
}

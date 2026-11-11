package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurtleEdgeTest {
    private static final double FPS = 60.0;
    private static final double TOP = 3.0;
    private static final double HALF_BEAM = 0.25;
    private static final double BEYOND = 0.06;

    record Outcome(double startY, double minY, double finalY, double finalSpeed, double firstUpright, double uprightY, boolean armed) {
        boolean fell() {
            return this.finalY < 0.5;
        }
    }

    static LedgeWorld beam(DroneBuild build, boolean gate, double comX, boolean inverted) {
        LedgeWorld w = LedgeWorld.of(build);
        w.floor(0.0);
        if (gate) {
            w.gate(-HALF_BEAM, TOP - 0.5, -2.0, HALF_BEAM, TOP, 2.0);
        } else {
            w.block(-1.0, TOP - 1.0, -2.0, HALF_BEAM, TOP, 2.0);
        }
        w.placeBottom(comX, TOP, 0.0, inverted);
        return w;
    }

    static Outcome run(LedgeWorld w, boolean turtle, double roll, double seconds) {
        for (int i = 0; i < 3; i++) {
            w.frame(1.0 / FPS);
        }
        if (turtle) {
            w.input.turtle = true;
            w.arm();
        }
        boolean armed = w.sim.telemetry().armed;
        w.input.roll = roll;
        double startY = w.y;
        double minY = w.y;
        double upright = Double.NaN;
        double uprightY = Double.NaN;
        int frames = (int) Math.round(seconds * FPS);
        for (int f = 0; f < frames; f++) {
            w.frame(1.0 / FPS);
            minY = Math.min(minY, w.y);
            if (Double.isNaN(upright) && w.sim.telemetry().tiltDeg < 30.0) {
                upright = f / FPS;
                uprightY = w.y;
            }
        }
        RigidBody b = w.sim.body;
        double speed = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
        return new Outcome(startY, minY, w.y, speed, upright, uprightY, armed);
    }

    @Test
    void turtleUpsideDownOnAGateBeamEdgeFallsOffAndComesToRest() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.WHOOP, DroneBuild.LONG_RANGE}) {
            double comX = HALF_BEAM + Math.min(BEYOND, 0.2 * AirframeSpec.of(build).hitboxWidth);
            Outcome o = run(beam(build, true, comX, true), true, 0.0, 4.0);
            assertTrue(o.armed(), build.frame() + ": Turtle nicht scharf");
            assertTrue(o.fell(), build.frame() + " liegt noch auf dem Gate-Balken: " + o);
            assertTrue(o.finalSpeed() < 0.05, build.frame() + " rollt am Boden weiter: " + o);
        }
    }

    @Test
    void turtleUpsideDownOnABlockEdgeFallsOff() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER}) {
            Outcome o = run(beam(build, false, HALF_BEAM + BEYOND, true), true, 0.0, 4.0);
            assertTrue(o.armed(), build.frame() + ": Turtle nicht scharf");
            assertTrue(o.fell(), build.frame() + " liegt noch auf der Blockkante: " + o);
            assertTrue(o.finalSpeed() < 0.05, build.frame() + " rollt am Boden weiter: " + o);
        }
    }

    @Test
    void turtleOnTheMiddleOfAGateBeamFlipsLikeOnTheGround() {
        Outcome o = run(beam(DroneBuild.FREESTYLE, true, 0.0, true), true, 1.0, 2.0);
        TurtleModeTest.Flip flat = TurtleModeTest.flip(DroneBuild.FREESTYLE, 1.0, 0.0, 0.0, 0.0, 2.0);
        assertTrue(o.armed());
        assertTrue(o.firstUpright() < 1.0, "auf dem Gate-Balken nicht umgedreht: " + o);
        assertTrue(Math.abs(o.firstUpright() - flat.seconds()) < 0.15, "Balken " + o.firstUpright() + " s, Boden " + flat.seconds() + " s");
        assertTrue(o.uprightY() > TOP - 0.1, "beim Umdrehen schon vom Balken: " + o);
    }

    @Test
    void theCentreOfMassDecidesOnAnEdge() {
        LedgeWorld inside = beam(DroneBuild.FREESTYLE, false, HALF_BEAM - 0.05, false);
        Outcome stays = run(inside, false, 0.0, 3.0);
        assertEquals(stays.startY(), stays.finalY(), 1.0e-6, "Schwerpunkt über dem Block: bleibt liegen " + stays);
        assertEquals(HALF_BEAM - 0.05, inside.x, 1.0e-3);
        assertTrue(inside.sim.telemetry().tiltDeg < 2.0, "kippelt: " + inside.sim.telemetry().tiltDeg);
        Outcome falls = run(beam(DroneBuild.FREESTYLE, false, HALF_BEAM + BEYOND, false), false, 0.0, 3.0);
        assertTrue(falls.fell(), "Schwerpunkt über der Kante hinaus: kippt herunter " + falls);
        assertTrue(falls.finalSpeed() < 0.05, "liegt am Ende still: " + falls);
        Outcome upsideDown = run(beam(DroneBuild.FREESTYLE, true, HALF_BEAM + BEYOND, true), false, 0.0, 3.0);
        assertTrue(upsideDown.fell(), "kopfüber und unscharf über der Gate-Kante: " + upsideDown);
    }

    @Test
    void bridgingAGapNarrowerThanTheDroneStaysPut() {
        LedgeWorld w = LedgeWorld.of(DroneBuild.FREESTYLE);
        w.floor(0.0);
        w.block(-1.0, TOP - 1.0, -2.0, -0.06, TOP, 2.0);
        w.block(0.06, TOP - 1.0, -2.0, 1.0, TOP, 2.0);
        w.placeBottom(0.0, TOP, 0.0, true);
        Outcome o = run(w, false, 0.0, 3.0);
        assertEquals(o.startY(), o.finalY(), 0.02, "über der Lücke: " + o);
        assertEquals(0.0, w.x, 1.0e-3);
        assertTrue(o.finalSpeed() < 1.0e-3, "über der Lücke: " + o);
    }

    @Test
    void flatGroundTurtleIsBitIdenticalWithTheSupportGrid() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER}) {
            assertEquals(trace(build, false), trace(build, true), build.frame().toString());
        }
    }

    private static List<Long> trace(DroneBuild build, boolean grid) {
        LedgeWorld w = LedgeWorld.of(build);
        w.floor(0.0);
        w.grid = grid;
        w.placeBottom(0.0, 0.0, 0.0, true);
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            w.frame(1.0 / FPS);
        }
        w.input.turtle = true;
        w.arm();
        w.input.roll = 1.0;
        for (int i = 0; i < 180; i++) {
            w.frame(1.0 / FPS);
            out.add(Double.doubleToRawLongBits(w.x));
            out.add(Double.doubleToRawLongBits(w.y));
            out.add(Double.doubleToRawLongBits(w.sim.body.qx));
            out.add(Double.doubleToRawLongBits(w.sim.body.qz));
        }
        w.input.roll = 0.0;
        w.input.turtle = false;
        for (int i = 0; i < 120; i++) {
            w.frame(1.0 / FPS);
            out.add(Double.doubleToRawLongBits(w.x));
            out.add(Double.doubleToRawLongBits(w.y));
        }
        return out;
    }
}

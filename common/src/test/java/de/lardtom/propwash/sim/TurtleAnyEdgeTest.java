package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurtleAnyEdgeTest {
    private static final double FPS = 60.0;
    private static final double BEYOND = 0.06;
    private static final double INSIDE = 0.05;
    private static final DroneBuild[] BUILDS = {DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.WHOOP, DroneBuild.LONG_RANGE};

    enum Shape {
        SINGLE_BLOCK(1.0, 0.0),
        PILLAR(3.0, 0.0),
        PILLAR_CORNER(3.0, 0.0),
        CLIFF(8.0, 0.0),
        SLAB(1.5, 1.0),
        STAIRS(2.0, 1.5),
        FENCE(1.5, 0.0),
        WALL(1.5, 0.0),
        GATE_BEAM(3.0, 0.0),
        CUBE_BEAM(3.0, 0.0),
        POLE(3.0, 0.0);

        final double top;
        final double below;

        Shape(double top, double below) {
            this.top = top;
            this.below = below;
        }

        double edge() {
            return switch (this) {
                case FENCE, CUBE_BEAM, POLE -> 0.125;
                case WALL, GATE_BEAM -> 0.25;
                default -> 0.5;
            };
        }

        boolean diagonal() {
            return this == PILLAR_CORNER;
        }

        void build(LedgeWorld w) {
            double e = this.edge();
            switch (this) {
                case SINGLE_BLOCK, PILLAR, PILLAR_CORNER -> w.block(-e, 0.0, -e, e, this.top, e);
                case CLIFF -> w.block(-6.0, 0.0, -6.0, e, this.top, 6.0);
                case SLAB -> {
                    w.block(-6.0, 0.0, -6.0, 6.0, 1.0, 6.0);
                    w.block(-6.0, 1.0, -6.0, e, 1.5, 6.0);
                }
                case STAIRS -> {
                    w.block(-6.0, 0.0, -6.0, 6.0, 1.0, 6.0);
                    w.block(-6.0, 1.0, -6.0, 1.5, 1.5, 6.0);
                    w.block(-6.0, 1.5, -6.0, e, 2.0, 6.0);
                }
                case FENCE, POLE -> w.block(-e, 0.0, -e, e, this.top, e);
                case WALL -> w.block(-e, 0.0, -6.0, e, this.top, 6.0);
                case GATE_BEAM -> w.gate(-e, this.top - 0.5, -2.0, e, this.top, 2.0);
                case CUBE_BEAM -> w.gate(-e, this.top - 0.25, -1.5, e, this.top, 1.5);
            }
        }
    }

    record Outcome(double startBottom, double finalBottom, double finalSpeed, double finalTilt, double firstUpright, double x, double z,
                   boolean armed) {
        boolean fell(Shape shape) {
            return this.finalBottom < shape.top - 0.5 * Math.min(1.0, shape.top - shape.below);
        }

        boolean onTop(Shape shape) {
            return Math.abs(this.finalBottom - shape.top) < 0.05;
        }
    }

    static LedgeWorld place(Shape shape, DroneBuild build, double offset) {
        LedgeWorld w = LedgeWorld.of(build);
        w.floor(0.0);
        shape.build(w);
        double e = shape.edge();
        if (shape.diagonal()) {
            double d = offset / Math.sqrt(2.0);
            w.placeBottom(e + d, shape.top, e + d, true);
        } else {
            w.placeBottom(e + offset, shape.top, 0.0, true);
        }
        return w;
    }

    static double beyond(DroneBuild build) {
        return Math.min(BEYOND, 0.2 * AirframeSpec.of(build).hitboxWidth);
    }

    static Outcome run(LedgeWorld w, double roll, double throttle, double seconds) {
        for (int i = 0; i < 3; i++) {
            w.frame(1.0 / FPS);
        }
        double startBottom = w.bottom();
        w.input.turtle = true;
        w.arm();
        boolean armed = w.sim.telemetry().armed && w.sim.telemetry().turtle;
        w.input.roll = roll;
        w.input.throttle = throttle;
        double upright = Double.NaN;
        int frames = (int) Math.round(seconds * FPS);
        for (int f = 0; f < frames; f++) {
            w.frame(1.0 / FPS);
            if (Double.isNaN(upright) && w.sim.telemetry().tiltDeg < 25.0) {
                upright = f / FPS;
            }
        }
        RigidBody b = w.sim.body;
        double speed = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz);
        return new Outcome(startBottom, w.bottom(), speed, w.sim.telemetry().tiltDeg, upright, w.x, w.z, armed);
    }

    @Test
    void turtleWithCentredSticksFallsOverEveryKindOfEdge() {
        for (Shape shape : Shape.values()) {
            for (DroneBuild build : BUILDS) {
                String label = shape + " " + build.frame();
                Outcome o = run(place(shape, build, beyond(build)), 0.0, 0.0, 4.0);
                assertTrue(o.armed(), label + ": Turtle nicht scharf");
                assertEquals(shape.top, o.startBottom(), 0.02, label + ": lag nicht oben auf der Kante " + o);
                assertTrue(o.fell(shape), label + ": liegt noch oben " + o);
                assertTrue(o.finalSpeed() < 0.05, label + ": rollt unten weiter " + o);
            }
        }
    }

    @Test
    void throttleWithCentredSticksChangesNothingAndTheDroneStillFalls() {
        for (Shape shape : Shape.values()) {
            for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.WHOOP}) {
                String label = shape + " " + build.frame();
                Outcome idle = run(place(shape, build, beyond(build)), 0.0, 0.0, 3.0);
                Outcome gas = run(place(shape, build, beyond(build)), 0.0, 1.0, 3.0);
                assertTrue(gas.fell(shape), label + ": mit Vollgas oben geblieben " + gas);
                assertEquals(idle, gas, label + ": Vollgas bei mittigen Knüppeln ändert den Fall");
            }
        }
    }

    @Test
    void turtleTowardTheEdgeRollsTheDroneOff() {
        for (Shape shape : Shape.values()) {
            if (shape.diagonal()) {
                continue;
            }
            for (DroneBuild build : BUILDS) {
                String label = shape + " " + build.frame();
                Outcome o = run(place(shape, build, -INSIDE), -1.0, 1.0, 4.0);
                assertTrue(o.armed(), label + ": Turtle nicht scharf");
                assertEquals(shape.top, o.startBottom(), 0.02, label + ": lag nicht oben " + o);
                assertTrue(o.fell(shape), label + ": zur Kante gedreht, aber oben geblieben " + o);
                assertTrue(o.x() > shape.edge(), label + ": nicht über die Kante gerollt " + o);
                assertTrue(o.finalSpeed() < 0.05, label + ": rollt unten weiter " + o);
            }
        }
    }

    @Test
    void turtleAwayFromTheEdgeFlipsUprightOnTop() {
        for (Shape shape : new Shape[]{Shape.SINGLE_BLOCK, Shape.PILLAR, Shape.CLIFF, Shape.SLAB, Shape.STAIRS, Shape.GATE_BEAM}) {
            for (DroneBuild build : BUILDS) {
                String label = shape + " " + build.frame();
                Outcome o = run(place(shape, build, -INSIDE), 1.0, 1.0, 3.0);
                assertTrue(o.onTop(shape), label + ": nicht oben geblieben " + o);
                assertTrue(o.firstUpright() < 1.0, label + ": nicht aufgedreht " + o);
                assertTrue(o.finalTilt() < 30.0, label + ": liegt nicht aufrecht " + o);
            }
        }
    }

    @Test
    void inTheMiddleOfASingleBlockTurtleFlipsUprightAndStaysOnTop() {
        for (DroneBuild build : BUILDS) {
            for (double roll : new double[]{1.0, -1.0}) {
                LedgeWorld w = LedgeWorld.of(build);
                w.floor(0.0);
                Shape.SINGLE_BLOCK.build(w);
                w.placeBottom(0.0, Shape.SINGLE_BLOCK.top, 0.0, true);
                Outcome o = run(w, roll, 1.0, 3.0);
                String label = build.frame() + " Roll " + roll;
                assertTrue(o.onTop(Shape.SINGLE_BLOCK), label + ": nicht oben geblieben " + o);
                assertTrue(o.finalTilt() < 30.0, label + ": nicht aufgedreht " + o);
                assertTrue(o.finalSpeed() < 0.05, label + ": bewegt sich noch " + o);
            }
        }
    }

    @Test
    void overhangNeedsTheCentreOfMassOutsideTheSupport() {
        GroundSupport s = new GroundSupport();
        RigidBody b = body(0.0, 0.0);
        assertFalse(load(s, grid(c -> c[0] >= 2)).overhang(b, 1.0), "Schwerpunkt über der Auflage");
        assertTrue(load(s, grid(c -> c[0] <= 1)).overhang(b, 1.0), "Schwerpunkt über der Kante hinaus");
        assertEquals(-0.5 * spacing(), s.edgeX, 1.0e-9);
        assertEquals(0.0, s.edgeZ, 1.0e-9);
        assertTrue(load(s, grid(c -> c[0] <= 1 && c[1] <= 1)).overhang(b, 1.0), "über Eck");
        assertEquals(-0.5 * spacing(), s.edgeX, 1.0e-9);
        assertEquals(-0.5 * spacing(), s.edgeZ, 1.0e-9);
        assertTrue(load(s, grid(c -> c[0] == 0 && c[1] == 0)).overhang(b, 1.0), "nur eine Ecke trägt");
        assertFalse(load(s, grid(c -> c[0] == 2 && c[1] == 2)).overhang(b, 1.0), "Pfosten unter dem Schwerpunkt");
        assertFalse(load(s, grid(c -> c[0] != 2)).overhang(b, 1.0), "Lücke schmaler als die Drohne");
        assertFalse(load(s, grid(c -> false)).overhang(b, 1.0), "keine Auflage im Raster");
        double[] step = grid(c -> c[0] <= 1);
        for (int k = 0; k < step.length; k++) {
            if (Double.isInfinite(step[k])) {
                step[k] = 0.5;
            }
        }
        assertTrue(load(s, step).overhang(b, 1.0), "Stufe: tiefer als 3 cm");
        for (int k = 0; k < step.length; k++) {
            if (step[k] == 0.5) {
                step[k] = 0.98;
            }
        }
        assertFalse(load(s, step).overhang(b, 1.0), "Absatz unter 3 cm trägt");
    }

    @Test
    void overhangFollowsTheCentreOfMassAcrossTheGrid() {
        GroundSupport s = load(new GroundSupport(), grid(c -> c[0] <= 1));
        List<Boolean> seen = new ArrayList<>();
        for (int i = -4; i <= 4; i++) {
            seen.add(s.overhang(body(i * 0.25 * spacing(), 0.0), 1.0));
        }
        assertEquals(List.of(false, false, false, true, true, true, true, true, true), seen);
    }

    private interface Cell {
        boolean supported(int[] cell);
    }

    private static double spacing() {
        return 0.06;
    }

    private static double[] grid(Cell supported) {
        double[] out = new double[SimEnvironment.SUPPORT_CELLS];
        Arrays.fill(out, Double.NEGATIVE_INFINITY);
        for (int iz = 0; iz < SimEnvironment.SUPPORT_SIDE; iz++) {
            for (int ix = 0; ix < SimEnvironment.SUPPORT_SIDE; ix++) {
                if (supported.supported(new int[]{ix, iz})) {
                    out[SimEnvironment.supportCell(ix, iz)] = 1.0;
                }
            }
        }
        return out;
    }

    private static GroundSupport load(GroundSupport s, double[] heights) {
        SimEnvironment env = new SimEnvironment();
        env.supportSpacing = spacing();
        env.supportX = 0.0;
        env.supportZ = 0.0;
        System.arraycopy(heights, 0, env.support, 0, heights.length);
        s.load(env);
        return s;
    }

    private static RigidBody body(double x, double z) {
        RigidBody b = new RigidBody();
        b.px = x;
        b.pz = z;
        return b;
    }
}

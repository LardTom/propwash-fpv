package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryLedgeTest {
    private static final double FPS = 120.0;
    private static final double TOP = 0.5;
    private static final double PAST_EDGE = 0.010;
    private static final double ON_STEP = 0.010;
    private static final double TOUCH = 0.002;
    private static final double LEVEL_DEG = 1.0;
    private static final double SHALLOW_DROP = 0.1;
    private static final double STRADDLE = 0.020;

    enum Edge {
        HANGING, STRADDLING, ON_STEP
    }

    record Stand(double maxRise, double foot, double lowestCorner, double tiltDeg, boolean armed) {
        String text() {
            return String.format("Fuß %.1f mm über der Erwartung (höchstens %.1f mm), tiefste Zubehör-Ecke %.1f mm über der Stufe, %.1f° schräg",
                    this.foot * 1000.0, this.maxRise * 1000.0, this.lowestCorner * 1000.0, this.tiltDeg);
        }
    }

    static List<DroneBuild> builds() {
        return List.of(DroneBuild.CINEWHOOP.withoutAccessories().withAccessory(AccessoryGroundTest.dispenser()), AccessoryGroundTest.cineWithGimbal());
    }

    static double depth(AirframeSpec p) {
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k < p.accessoryPointCount(); k++) {
            low = Math.min(low, p.accessoryY(k));
        }
        return p.hitboxFootOffset - low;
    }

    static double cornerZ(LedgeWorld w, boolean nearest) {
        AirframeSpec p = w.sim.params();
        RigidBody b = w.sim.body;
        double best = nearest ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (int k = 0; k < p.accessoryPointCount(); k++) {
            double z = b.r20 * p.accessoryX(k) + b.r21 * p.accessoryY(k) + b.r22 * p.accessoryZ(k);
            best = nearest ? Math.min(best, z) : Math.max(best, z);
        }
        return best;
    }

    static double lowestCorner(LedgeWorld w) {
        AirframeSpec p = w.sim.params();
        RigidBody b = w.sim.body;
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k < p.accessoryPointCount(); k++) {
            low = Math.min(low, w.y + b.r10 * p.accessoryX(k) + b.r11 * p.accessoryY(k) + b.r12 * p.accessoryZ(k));
        }
        return low;
    }

    static LedgeWorld ledge(DroneBuild build, double drop, Edge where, double foot) {
        LedgeWorld w = LedgeWorld.of(build);
        w.floor(TOP - drop);
        w.placeBottom(0.0, foot, 0.0, false);
        double near = cornerZ(w, true);
        double far = cornerZ(w, false);
        assertTrue(near > PAST_EDGE + ON_STEP, build.frame().key() + ": Zubehör liegt nicht ganz vor der Mitte (" + near + ")");
        double edge = switch (where) {
            case HANGING -> near - PAST_EDGE;
            case STRADDLING -> near + STRADDLE;
            case ON_STEP -> far + ON_STEP;
        };
        w.block(-5.0, TOP - 1.0, -5.0, 5.0, TOP, edge);
        return w;
    }

    static Stand settle(LedgeWorld w, double seconds, double foot) {
        double rise = Double.NEGATIVE_INFINITY;
        int frames = (int) Math.round(seconds * FPS);
        for (int f = 0; f < frames; f++) {
            w.frame(1.0 / FPS);
            rise = Math.max(rise, w.bottom() - foot);
        }
        return new Stand(rise, w.bottom() - foot, lowestCorner(w) - TOP, AccessoryGroundTest.tiltDeg(w.sim.body), w.sim.telemetry().armed);
    }

    static void checkLevel(List<String> failures, String what, Stand s) {
        if (s.maxRise() > TOUCH) {
            failures.add(what + " springt hoch: " + s.text());
        } else if (s.foot() > TOUCH) {
            failures.add(what + " schwebt: " + s.text());
        } else if (s.foot() < -TOUCH) {
            failures.add(what + " sinkt in die Stufe: " + s.text());
        } else if (s.tiltDeg() > LEVEL_DEG) {
            failures.add(what + " steht schräg: " + s.text());
        }
    }

    @Test
    void pilotedDroneLiesOnTheStepWhenItsAccessoryHangsOverTheEdge() {
        List<String> failures = new ArrayList<>();
        for (DroneBuild build : builds()) {
            LedgeWorld w = ledge(build, TOP, Edge.HANGING, TOP);
            String what = AccessoryGroundTest.label(build) + " an der Stufenkante";
            checkLevel(failures, what + " (unscharf)", settle(w, 3.0, TOP));
            w.arm();
            Stand armed = settle(w, 1.0, TOP);
            if (!armed.armed()) {
                failures.add(what + ": nicht scharf (Sperren " + w.sim.telemetry().armBlockers + ")");
            }
            checkLevel(failures, what + " (scharf)", armed);
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void accessoryOverAShallowDropStandsOnTheLowerGround() {
        List<String> failures = new ArrayList<>();
        for (DroneBuild build : builds()) {
            double foot = Math.max(TOP, TOP - SHALLOW_DROP + depth(AirframeSpec.of(build)));
            LedgeWorld w = ledge(build, SHALLOW_DROP, Edge.HANGING, foot);
            checkLevel(failures, AccessoryGroundTest.label(build) + " über 10 cm tieferem Boden", settle(w, 3.0, foot));
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void pilotedDroneStillStandsOnItsAccessoryOnTheStep() {
        List<String> failures = new ArrayList<>();
        for (DroneBuild build : builds()) {
            for (Edge where : List.of(Edge.ON_STEP, Edge.STRADDLING)) {
                double foot = TOP + depth(AirframeSpec.of(build));
                LedgeWorld w = ledge(build, TOP, where, foot);
                String what = AccessoryGroundTest.label(build) + (where == Edge.ON_STEP ? " mit Zubehör auf der Stufe" : " mit Zubehör 2 cm auf der Stufe");
                Stand s = settle(w, 3.0, foot);
                checkLevel(failures, what, s);
                if (Math.abs(s.lowestCorner()) > TOUCH) {
                    failures.add(what + " steht nicht auf dem Zubehör: " + s.text());
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void pointSamplesAreCopiedSanitizedAndCleared() {
        SimEnvironment src = new SimEnvironment();
        src.supportSpacing = 0.1;
        src.pointCount = 2;
        src.pointX[0] = 1.0;
        src.pointZ[0] = 2.0;
        src.pointSupport[0] = Double.POSITIVE_INFINITY;
        src.pointX[1] = 3.0;
        src.pointZ[1] = 4.0;
        src.pointSupport[1] = Double.NEGATIVE_INFINITY;
        SimEnvironment copy = new SimEnvironment();
        src.copyTo(copy);
        assertEquals(2, copy.pointCount);
        assertEquals(3.0, copy.pointX[1]);
        SimEnvironment clean = new SimEnvironment();
        GroundSupport.sanitize(src, clean);
        assertEquals(2, clean.pointCount);
        assertTrue(Double.isNaN(clean.pointSupport[0]), "+∞ heißt wie im Raster: in einem Block");
        assertEquals(Double.NEGATIVE_INFINITY, clean.pointSupport[1]);
        src.pointZ[1] = Double.NaN;
        GroundSupport.sanitize(src, clean);
        assertEquals(0, clean.pointCount, "Punkt ohne Lage");
        src.pointZ[1] = 4.0;
        src.pointCount = SimEnvironment.POINT_SUPPORT + 1;
        GroundSupport.sanitize(src, clean);
        assertEquals(0, clean.pointCount, "zu viele Punkte");
        src.pointCount = 2;
        src.supportSpacing = 0.0;
        GroundSupport.sanitize(src, clean);
        assertEquals(0, clean.pointCount, "ohne Raster keine Punkte");
        copy.clearSupport();
        assertEquals(0, copy.pointCount);
    }
}

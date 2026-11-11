package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LandingSelfTestSequenceTest {
    static final double DROP = 0.12;
    static final double UPRIGHT = 30.0;
    static final double ON_SURFACE = 0.03;
    static final double JITTER = 0.6;
    static final int RUNS = 8;
    static final double[] FRAMES = {1.0 / 144.0, 1.0 / 60.0, 1.0 / 20.0, 0.1};

    record Case(String name, boolean slab, double tiltDeg, double axisDeg, double vx, double vy, double vz) {
    }

    static final List<Case> CASES = List.of(
            new Case("senkrecht auf Stein", false, 6.0, 30.0, 0.0, -0.6, 0.0),
            new Case("mit Fahrt auf Stein", false, 10.0, 200.0, 2.5, -0.5, 0.4),
            new Case("mit Fahrt in kurzes Gras", false, 8.0, 120.0, 1.5, -0.7, -0.3),
            new Case("auf der Kante einer Stufe", true, 5.0, 300.0, 1.0, -0.6, 0.0));

    record Outcome(double tiltDeg, double lowest) {
    }

    static double frame(BoxWorld w, double dt, SplittableRandom r) {
        double h = dt * (1.0 + JITTER * (r.nextDouble() - 0.5));
        w.frame(h);
        return h;
    }

    static void resetAbove(BoxWorld w, double bottom) {
        SimSnapshot s = new SimSnapshot();
        w.sim.copyStateTo(s);
        Quaternionf q = new Quaternionf().rotateY((float) Math.PI);
        w.y = bottom - w.foot;
        s.px = w.x;
        s.py = w.y;
        s.pz = w.z;
        s.vx = 0.0;
        s.vy = 0.0;
        s.vz = 0.0;
        s.lx = 0.0;
        s.ly = 0.0;
        s.lz = 0.0;
        s.qw = q.w;
        s.qx = q.x;
        s.qy = q.y;
        s.qz = q.z;
        w.sim.copyStateFrom(s);
    }

    static void tilt(BoxWorld w, Case c, boolean keepContact) {
        SimSnapshot s = new SimSnapshot();
        w.sim.copyStateTo(s);
        double axis = Math.toRadians(c.axisDeg());
        Quaternionf q = new Quaternionf((float) s.qx, (float) s.qy, (float) s.qz, (float) s.qw);
        new Quaternionf().rotationAxis((float) Math.toRadians(c.tiltDeg()), (float) Math.cos(axis), 0.0F, (float) Math.sin(axis)).mul(q, q);
        s.qw = q.w;
        s.qx = q.x;
        s.qy = q.y;
        s.qz = q.z;
        s.vx = c.vx();
        s.vy = c.vy();
        s.vz = c.vz();
        s.lx = 0.0;
        s.ly = 0.0;
        s.lz = 0.0;
        s.sleeping = false;
        if (!keepContact) {
            s.groundedFrames = 0;
        }
        w.sim.copyStateFrom(s);
    }

    static Outcome land(Case c, double dt, boolean keepContact, SplittableRandom r) {
        BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, new SimTuning());
        w.ground(0.0);
        double top = 0.0;
        if (c.slab()) {
            w.box(0.0, 0.0, -1.5, 3.0, 0.5, 1.5);
            top = 0.5;
        }
        w.placeBottom(-3.0, 0.0, 0.0, 0.0);
        w.input.mode = StabMode.ACRO;
        for (double t = 0.0; t < 2.0; ) {
            t += frame(w, dt, r);
        }
        w.input.throttle = 0.0;
        w.input.armSwitch = false;
        w.frame(dt);
        w.input.armSwitch = true;
        for (double t = 0.0; t < 0.15; ) {
            t += frame(w, dt, r);
        }
        assertTrue(w.sim.telemetry().armed, "nicht scharf: " + c.name());
        w.x = 0.0;
        w.z = 0.0;
        resetAbove(w, top + DROP);
        tilt(w, c, keepContact);
        w.input.throttle = 0.9 * w.sim.telemetry().hoverThrottleStick;
        for (double t = 0.0; !w.sim.telemetry().grounded && t < 4.0; ) {
            t += frame(w, dt, r);
        }
        w.input.throttle = 0.0;
        for (double t = 0.0; t < 1.0; ) {
            t += frame(w, dt, r);
        }
        w.input.armSwitch = false;
        for (double t = 0.0; t < 6.0; ) {
            t += frame(w, dt, r);
        }
        return new Outcome(w.sim.telemetry().tiltDeg, w.lowest() - top);
    }

    @Test
    void selfTestLandingsEndUprightOnTheSurface() {
        SplittableRandom r = new SplittableRandom(18L);
        List<String> failures = new ArrayList<>();
        for (Case c : CASES) {
            for (double dt : FRAMES) {
                for (boolean keepContact : new boolean[]{false, true}) {
                    for (int i = 0; i < RUNS; i++) {
                        Outcome o = land(c, dt, keepContact, r);
                        if (o.tiltDeg() > UPRIGHT || Math.abs(o.lowest()) > ON_SURFACE) {
                            failures.add(String.format(Locale.ROOT, "%s, %.0f Bilder/s, Bodenkontakt aus der Rücksetzung %s: Neigung %.1f°, tiefster Punkt %.3f m",
                                    c.name(), 1.0 / dt, keepContact ? "behalten" : "gelöscht", o.tiltDeg(), o.lowest()));
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}

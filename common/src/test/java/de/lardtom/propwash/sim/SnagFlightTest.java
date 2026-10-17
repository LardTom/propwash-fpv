package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.interact.SnagMaterial;
import de.lardtom.propwash.interact.SnagSampler;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnagFlightTest {
    private static final double ALT = 40.0;
    private static final SnagSampler.World EMPTY = (x, y, z, sampler) -> {
    };

    private static SnagSampler.World region(SnagMaterial m, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return (x, y, z, sampler) -> {
            double x0 = Math.max(x, minX);
            double y0 = Math.max(y, minY);
            double z0 = Math.max(z, minZ);
            double x1 = Math.min(x + 1.0, maxX);
            double y1 = Math.min(y + 1.0, maxY);
            double z1 = Math.min(z + 1.0, maxZ);
            if (x0 < x1 && y0 < y1 && z0 < z1) {
                sampler.box(m, x0, y0, z0, x1, y1, z1);
            }
        };
    }

    private static final class Run {
        final FlatWorld w;
        final SnagSampler sampler = new SnagSampler();
        final SnagSampler.World world;
        final AltitudeHold hold = new AltitudeHold(ALT);
        boolean holdAltitude = true;
        double maxX = Double.NEGATIVE_INFINITY;

        Run(DroneBuild build, SnagSampler.World world) {
            this.w = Tests.hovering(build, Tests.quietTuning(), ALT, StabMode.ANGLE, 1.0);
            this.world = world;
        }

        void push(double vx) {
            SimSnapshot s = new SimSnapshot();
            this.w.sim.copyStateTo(s);
            s.vx = vx;
            s.vy = 0.0;
            s.vz = 0.0;
            this.w.sim.copyStateFrom(s);
        }

        void run(double seconds, double fps) {
            double dt = 1.0 / fps;
            int frames = (int) Math.round(seconds * fps);
            for (int i = 0; i < frames; i++) {
                this.sampler.sample(this.w.sim, this.world, this.w.env.snag);
                if (this.holdAltitude) {
                    this.hold.apply(this.w, dt);
                }
                this.w.frame(dt);
                this.maxX = Math.max(this.maxX, this.w.sim.body.px);
            }
        }

        double speed() {
            Vector3d v = new Vector3d();
            this.w.sim.getVelocity(v);
            return v.length();
        }
    }

    @Test
    void noSnagStaysBitIdentical() {
        FlatWorld plain = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), ALT, StabMode.ACRO, 1.0);
        FlatWorld probed = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), ALT, StabMode.ACRO, 1.0);
        SnagSampler sampler = new SnagSampler();
        double dt = 1.0 / 144.0;
        for (int i = 0; i < 720; i++) {
            double roll = i < 200 ? 0.6 : (i < 400 ? -0.3 : 0.0);
            plain.input.roll = roll;
            probed.input.roll = roll;
            plain.input.throttle = 0.55;
            probed.input.throttle = 0.55;
            sampler.sample(probed.sim, EMPTY, probed.env.snag);
            assertFalse(probed.env.snag.active());
            plain.frame(dt);
            probed.frame(dt);
        }
        RigidBody a = plain.sim.body;
        RigidBody b = probed.sim.body;
        assertEquals(a.px, b.px, 0.0);
        assertEquals(a.py, b.py, 0.0);
        assertEquals(a.pz, b.pz, 0.0);
        assertEquals(a.qw, b.qw, 0.0);
        assertEquals(a.qx, b.qx, 0.0);
        assertEquals(plain.sim.telemetry().motorRpm[0], probed.sim.telemetry().motorRpm[0], 0.0);
    }

    @Test
    void cobwebCatchesAFastFreestyleDroneWithinTheBlock() {
        Run r = new Run(DroneBuild.FREESTYLE, region(SnagMaterial.WEB, 3.0, ALT - 3.0, -3.0, 4.0, ALT + 3.0, 3.0));
        r.push(20.0);
        r.run(1.5, 120.0);
        String info = String.format(Locale.ROOT, "maxX %.2f, x %.2f, v %.2f", r.maxX, r.w.sim.body.px, r.speed());
        assertTrue(r.maxX < 4.0, "die Mitte bleibt im Netz: " + info);
        assertTrue(r.w.sim.body.px > 3.0, "die Drohne steckt im Netz: " + info);
        assertTrue(r.speed() < 0.6, "gefangen: " + info);
        assertTrue(r.w.sim.telemetry().armed, "kein Absturz: " + info);
        assertTrue(r.w.sim.impacts().size() == 0, "kein Aufprall");
    }

    @Test
    void cobwebCatchesEvenAtFortyMetresPerSecond() {
        Run r = new Run(DroneBuild.FREESTYLE, region(SnagMaterial.WEB, 3.0, ALT - 3.0, -3.0, 4.0, ALT + 3.0, 3.0));
        r.push(40.0);
        r.run(1.5, 60.0);
        assertTrue(r.maxX < 4.3, String.format(Locale.ROOT, "maxX %.2f", r.maxX));
        assertTrue(r.speed() < 1.0);
    }

    @Test
    void fullThrottleBreaksFreeUpwards() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.WHOOP}) {
            Run r = new Run(build, region(SnagMaterial.WEB, -2.0, ALT - 0.5, -2.0, 2.0, ALT + 0.5, 2.0));
            r.run(1.0, 120.0);
            assertTrue(r.speed() < 0.3, build + " hängt im Netz");
            double held = r.w.sim.body.py;
            r.holdAltitude = false;
            r.w.input.throttle = 1.0;
            double escaped = -1.0;
            for (int i = 0; i < 6 * 120 && escaped < 0.0; i++) {
                r.run(1.0 / 120.0, 120.0);
                if (r.w.sim.body.py > ALT + 0.5 + r.w.sim.params().propRadius + 0.05) {
                    escaped = i / 120.0;
                }
            }
            assertTrue(escaped > 0.0, build + " kommt mit Vollgas frei (gehalten bei " + held + ", jetzt " + r.w.sim.body.py + ")");
            assertTrue(escaped > 0.1, build + " nicht sofort: " + escaped);
        }
    }

    @Test
    void idleDroneSinksSlowlyThroughTheWeb() {
        Run r = new Run(DroneBuild.FREESTYLE, region(SnagMaterial.WEB, -2.0, ALT - 0.5, -2.0, 2.0, ALT + 0.5, 2.0));
        r.run(0.5, 120.0);
        r.holdAltitude = false;
        r.w.input.throttle = 0.0;
        double maxSink = 0.0;
        double start = r.w.sim.body.py;
        for (int i = 0; i < 120; i++) {
            r.run(1.0 / 120.0, 120.0);
            maxSink = Math.max(maxSink, -r.w.sim.body.vy);
        }
        double fell = start - r.w.sim.body.py;
        assertTrue(maxSink < 1.0, "sinkt langsam: " + maxSink);
        assertTrue(fell > 0.05 && fell < 0.9, "sinkt, aber nicht frei fallend: " + fell);
    }

    @Test
    void vineSheetBrakesLightlyAtEveryFrameRate() {
        double[] losses = new double[3];
        double[] rates = {30.0, 60.0, 144.0};
        for (int k = 0; k < rates.length; k++) {
            Run r = new Run(DroneBuild.FREESTYLE, region(SnagMaterial.FOLIAGE, 3.0, ALT - 3.0, -3.0, 3.0625, ALT + 3.0, 3.0));
            Run free = new Run(DroneBuild.FREESTYLE, EMPTY);
            r.push(15.0);
            free.push(15.0);
            r.run(0.6, rates[k]);
            free.run(0.6, rates[k]);
            assertTrue(r.w.sim.body.px > 4.0, "fliegt durch");
            losses[k] = 1.0 - r.speed() / free.speed();
        }
        for (double loss : losses) {
            assertTrue(loss > 0.005 && loss < 0.2, "leichte Bremse: " + loss);
        }
        double min = Math.min(losses[0], Math.min(losses[1], losses[2]));
        double max = Math.max(losses[0], Math.max(losses[1], losses[2]));
        assertTrue(max < 3.0 * min, String.format(Locale.ROOT, "Bildrate egal: %.3f %.3f %.3f", losses[0], losses[1], losses[2]));
    }

    @Test
    void stringLineCatchesThePropsHarderThanVines() {
        Run probe = new Run(DroneBuild.FREESTYLE, EMPTY);
        double propY = ALT + probe.w.sim.params().motorY(0);
        Run string = new Run(DroneBuild.FREESTYLE, region(SnagMaterial.STRING, 3.47, propY - 0.047, -3.0, 3.53, propY + 0.047, 3.0));
        Run vine = new Run(DroneBuild.FREESTYLE, region(SnagMaterial.FOLIAGE, 3.47, propY - 0.047, -3.0, 3.53, propY + 0.047, 3.0));
        Run free = new Run(DroneBuild.FREESTYLE, EMPTY);
        for (Run r : new Run[]{string, vine, free}) {
            r.push(10.0);
            r.run(0.5, 120.0);
        }
        double stringLoss = 1.0 - string.speed() / free.speed();
        double vineLoss = 1.0 - vine.speed() / free.speed();
        assertTrue(stringLoss > vineLoss, stringLoss + " > " + vineLoss);
        assertTrue(stringLoss > 0.005 && stringLoss < 0.4, "Faden bremst spürbar, fängt aber nicht: " + stringLoss);
        assertTrue(string.w.sim.body.px > 4.0, "fliegt weiter");
    }

    @Test
    void entangledPropsTurnSlower() {
        Run free = new Run(DroneBuild.FREESTYLE, EMPTY);
        Run web = new Run(DroneBuild.FREESTYLE, region(SnagMaterial.WEB, -2.0, ALT - 0.5, -2.0, 2.0, ALT + 0.5, 2.0));
        for (Run r : new Run[]{free, web}) {
            r.holdAltitude = false;
            r.w.input.throttle = 0.8;
            r.run(0.3, 120.0);
        }
        double freeRpm = free.w.sim.telemetry().motorRpm[0];
        double webRpm = web.w.sim.telemetry().motorRpm[0];
        assertTrue(webRpm < 0.95 * freeRpm, "Fäden bremsen die Props: " + webRpm + " < " + freeRpm);
    }
}

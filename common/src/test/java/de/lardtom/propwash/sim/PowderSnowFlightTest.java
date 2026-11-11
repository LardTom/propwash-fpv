package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.CauldronContent;
import de.lardtom.propwash.interact.SnagMaterial;
import de.lardtom.propwash.interact.SnagSampler;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowderSnowFlightTest {
    private static final double ALT = 40.0;
    private static final double FPS = 120.0;
    private static final SnagSampler.World EMPTY = (x, y, z, sampler) -> {
    };

    private static SnagSampler.World snow(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return (x, y, z, sampler) -> {
            double x0 = Math.max(x, minX);
            double y0 = Math.max(y, minY);
            double z0 = Math.max(z, minZ);
            double x1 = Math.min(x + 1.0, maxX);
            double y1 = Math.min(y + 1.0, maxY);
            double z1 = Math.min(z + 1.0, maxZ);
            if (x0 < x1 && y0 < y1 && z0 < z1) {
                sampler.box(SnagMaterial.POWDER_SNOW, x0, y0, z0, x1, y1, z1);
            }
        };
    }

    private static final class Run {
        final FlatWorld w;
        final SnagSampler sampler = new SnagSampler();
        final SnagSampler.World world;
        final AltitudeHold hold = new AltitudeHold(ALT);
        boolean holdAltitude = true;
        boolean touched;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxSink;

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

        void run(double seconds) {
            double dt = 1.0 / FPS;
            int frames = (int) Math.round(seconds * FPS);
            for (int i = 0; i < frames; i++) {
                this.touched |= this.sampler.sample(this.w.sim, this.world, this.w.env.snag);
                if (this.holdAltitude) {
                    this.hold.apply(this.w, dt);
                }
                this.w.frame(dt);
                this.maxX = Math.max(this.maxX, this.w.sim.body.px);
                this.maxSink = Math.max(this.maxSink, -this.w.sim.body.vy);
            }
        }

        double speed() {
            Vector3d v = new Vector3d();
            this.w.sim.getVelocity(v);
            return v.length();
        }

        String info() {
            return String.format(Locale.ROOT, "x %.2f (max %.2f), y %.2f, v %.2f m/s, vy %.2f m/s, höchstens %.2f m/s sinkend", this.w.sim.body.px,
                    this.maxX, this.w.sim.body.py, this.speed(), this.w.sim.body.vy, this.maxSink);
        }
    }

    @Test
    void idleDroneSinksSlowlyLikeAnEntityInPowderSnow() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.FREESTYLE}) {
            Run r = new Run(build, snow(-2.0, ALT - 3.0, -2.0, 2.0, ALT + 3.0, 2.0));
            Run free = new Run(build, EMPTY);
            for (Run run : new Run[]{r, free}) {
                run.run(0.5);
                run.holdAltitude = false;
                run.w.input.throttle = 0.0;
                run.maxSink = 0.0;
            }
            double start = r.w.sim.body.py;
            r.run(1.5);
            free.run(1.5);
            double fell = start - r.w.sim.body.py;
            assertTrue(r.touched, build + " spürt den Pulverschnee");
            assertTrue(r.maxSink < 1.0, build + " sinkt langsam: " + r.info());
            assertTrue(-r.w.sim.body.vy > 0.4, build + " sinkt weiter ein, hängt nicht fest: " + r.info());
            assertTrue(fell > 0.5 && fell < 1.5, build + " sinkt ein: " + fell + " m, " + r.info());
            assertTrue(free.maxSink > 5.0, build + " fällt ohne Pulverschnee frei: " + free.info());
            assertTrue(r.w.sim.telemetry().armed, build + " bleibt scharf");
            assertEquals(0, r.w.sim.impacts().size(), build + " ohne Aufprall");
        }
    }

    @Test
    void fastDroneIsBrakedWithinTwoMetres() {
        Run r = new Run(DroneBuild.FREESTYLE, snow(3.0, ALT - 3.0, -3.0, 12.0, ALT + 3.0, 3.0));
        Run free = new Run(DroneBuild.FREESTYLE, EMPTY);
        r.push(20.0);
        free.push(20.0);
        r.run(1.0);
        free.run(1.0);
        assertTrue(r.maxX > 3.5, "die Drohne fliegt in den Pulverschnee hinein, er ist keine Wand: " + r.info());
        assertTrue(r.maxX < 5.0, "nach höchstens 2 m gebremst: " + r.info());
        assertTrue(r.speed() < 1.0, "steht fast: " + r.info());
        assertTrue(free.speed() > 10.0, "ohne Pulverschnee viel schneller: " + free.info());
        assertTrue(r.w.sim.telemetry().armed, "kein Absturz");
        assertEquals(0, r.w.sim.impacts().size(), "kein Aufprall");
    }

    @Test
    void fullThrottleClimbsOutButSlowerThanInAir() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.FREESTYLE}) {
            double top = ALT + 1.0;
            Run r = new Run(build, snow(-2.0, ALT - 1.0, -2.0, 2.0, top, 2.0));
            Run free = new Run(build, EMPTY);
            double[] times = new double[2];
            double[] climb = new double[2];
            Run[] runs = {r, free};
            for (int k = 0; k < runs.length; k++) {
                Run run = runs[k];
                run.run(0.5);
                run.holdAltitude = false;
                run.w.input.throttle = 1.0;
                double clear = top + run.w.sim.params().propRadius + 0.05;
                times[k] = -1.0;
                for (int i = 0; i < 4 * FPS && times[k] < 0.0; i++) {
                    run.run(1.0 / FPS);
                    if (run.w.sim.body.py < top) {
                        climb[k] = Math.max(climb[k], run.w.sim.body.vy);
                    }
                    if (run.w.sim.body.py > clear) {
                        times[k] = (i + 1) / FPS;
                    }
                }
            }
            String info = String.format(Locale.ROOT, "%s: im Pulverschnee %.2f s mit höchstens %.2f m/s, in Luft %.2f s mit %.2f m/s", build,
                    times[0], climb[0], times[1], climb[1]);
            assertTrue(times[0] > 0.0 && times[0] < 3.0, "kommt mit Vollgas heraus: " + info);
            assertTrue(times[0] > times[1], "braucht länger als in Luft: " + info);
            assertTrue(climb[0] < 0.7 * climb[1], "steigt im Pulverschnee langsamer: " + info);
        }
    }

    @Test
    void cauldronBrakesOnlyInsideTheBasinBelowItsFillHeight() {
        FlatWorld w = Tests.hovering(DroneBuild.WHOOP, Tests.quietTuning(), ALT, StabMode.ANGLE, 1.0);
        double px = w.sim.body.px;
        double py = w.sim.body.py;
        double pz = w.sim.body.pz;
        double basin = 0.5;
        double inWall = CauldronContent.WALL / 2.0;
        for (int level = CauldronContent.MIN_LEVEL; level <= CauldronContent.MAX_LEVEL; level++) {
            double top = CauldronContent.POWDER_SNOW.top(level);
            assertTrue(touches(w, cauldron(px - basin, py - top + 0.05, pz - basin, level)), "Stufe " + level + ": 5 cm unter der Füllhöhe");
            assertTrue(touches(w, cauldron(px - basin, py - CauldronContent.FLOOR - 0.05, pz - basin, level)), "Stufe " + level
                    + ": 5 cm über dem Boden");
            assertFalse(touches(w, cauldron(px - basin, py - top - 0.05, pz - basin, level)), "Stufe " + level + ": 5 cm über der Füllhöhe");
            assertFalse(touches(w, cauldron(px - inWall, py - top + 0.05, pz - basin, level)), "Stufe " + level + ": in der Wand");
        }
    }

    private static SnagSampler.World cauldron(double ox, double oy, double oz, int level) {
        double wall = CauldronContent.WALL;
        return snow(ox + wall, oy + CauldronContent.FLOOR, oz + wall, ox + 1.0 - wall, oy + CauldronContent.POWDER_SNOW.top(level), oz + 1.0 - wall);
    }

    private static boolean touches(FlatWorld w, SnagSampler.World world) {
        return new SnagSampler().sample(w.sim, world, new SnagField());
    }
}

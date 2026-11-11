package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.rescue.RescueWorld;
import de.lardtom.propwash.rescue.VoxelFlight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceResponseTest {
    private static final double DT = 1.0 / 144.0;
    private static final double SOLID_PROBE = 1.0;
    private static final SurfaceResponse STONE = SurfaceResponse.normal(0.6);
    private static final double CEILING_START = 2.8;
    private static final double CEILING_HEIGHT = 4.0;
    private static final double CEILING_SPEED = 8.0;
    private static final double NEAR_FLOOR = 1.0;
    private static final double BOUNCE_SECONDS = 0.25;
    private static final double WEAR_WINDOW_SECONDS = 1.0;

    static final class Run {
        final FlatWorld w;
        SurfaceResponse ground = STONE;
        SurfaceResponse wall = STONE;
        SurfaceResponse ceiling = STONE;
        double ceilingY = Double.POSITIVE_INFINITY;
        boolean crash;
        boolean hard;
        boolean bump;
        boolean strike;
        int impacts;
        boolean touched;
        double impactSpeed;
        double rebound;

        Run(FlatWorld w) {
            this.w = w;
        }

        void frame(double dt) {
            FlatWorld f = this.w;
            double before = f.sim.body.vy;
            double distance = f.y - f.groundY;
            f.env.groundDistance = distance <= FlatWorld.RAYCAST_RANGE ? Math.max(0.0, distance) : Double.POSITIVE_INFINITY;
            f.sim.step(Math.round(dt * 1e9), f.input, f.env, f.result);
            double dx = f.result.dx;
            double dy = f.result.dy;
            double dz = f.result.dz;
            double ay = dy;
            if (dy < 0.0) {
                ay = Math.max(dy, Math.min(0.0, f.groundY - f.bottom()));
            } else if (dy > 0.0) {
                ay = Math.min(dy, Math.max(0.0, this.ceilingY - f.bottom() - f.sim.params().hitboxHeight));
            }
            f.y += ay;
            double ax = dx;
            double half = f.sim.params().hitboxWidth / 2.0;
            if (dx > 0.0 && f.wallX < Double.POSITIVE_INFINITY) {
                ax = Math.min(dx, Math.max(0.0, f.wallX - (f.x + half)));
            }
            f.x += ax;
            f.z += dz;
            MoveReport r = f.report;
            r.clear();
            r.requestedX = dx;
            r.requestedY = dy;
            r.requestedZ = dz;
            r.actualX = ax;
            r.actualY = ay;
            r.actualZ = dz;
            r.hitX = Math.abs(ax - dx) > 1e-7;
            r.hitY = Math.abs(ay - dy) > 1e-7;
            r.hitZ = false;
            boolean grounded = f.sim.telemetry().grounded;
            if (r.hitY && dy > 0.0) {
                this.ceiling.writeTo(r);
            } else if (r.hitX || r.hitY || grounded || f.y - f.groundY < SOLID_PROBE) {
                (r.hitX && !grounded ? this.wall : this.ground).writeTo(r);
            }
            r.supportGap = Math.min(FlatWorld.SUPPORT_PROBE, Math.max(0.0, f.bottom() - f.groundY));
            f.sim.afterMove(r);
            this.collect(before);
        }

        private void collect(double before) {
            DroneSim s = this.w.sim;
            EventQueue q = s.events();
            this.crash |= q.contains(SimEventType.CRASH);
            this.hard |= q.contains(SimEventType.HARD_IMPACT);
            this.bump |= q.contains(SimEventType.BUMP);
            this.strike |= q.contains(SimEventType.PROP_STRIKE);
            this.impacts += s.impacts().size();
            if (!this.touched && before < -1.0 && s.body.vy > before + 1.0) {
                this.touched = true;
                this.impactSpeed = -before;
            }
            if (this.touched) {
                this.rebound = Math.max(this.rebound, s.body.vy);
            }
        }

        void run(double seconds) {
            for (double t = 0.0; t < seconds; t += DT) {
                this.frame(DT);
            }
        }

        boolean quiet() {
            return !this.crash && !this.hard && !this.bump && !this.strike && this.impacts == 0;
        }
    }

    private static Run drop(SurfaceResponse ground, double speed) {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        double height = 0.6;
        w.place(0.0, w.groundY - w.sim.params().hitboxFootOffset + height, 0.0, 0.0);
        w.sim.body.vy = -Math.sqrt(Math.max(0.0, speed * speed - 2.0 * ModelConstants.G * height));
        Run run = new Run(w);
        run.ground = ground;
        return run;
    }

    @Test
    void slimeBouncesBackWithoutCrashOrStrike() {
        Run run = drop(SurfaceResponse.BOUNCY, 8.0);
        run.run(0.4);
        assertTrue(run.touched);
        assertEquals(8.0, run.impactSpeed, 0.3);
        assertTrue(run.rebound >= 5.5, "Rückprall " + run.rebound);
        assertTrue(run.quiet(), "Ereignisse auf Slime");
        assertEquals(0, run.w.sim.telemetry().crashCount);
    }

    @Test
    void woolCatchesHardFallWithoutEvents() {
        Run run = drop(SurfaceResponse.CUSHION, 20.0);
        run.run(1.5);
        assertTrue(run.touched);
        assertEquals(20.0, run.impactSpeed, 0.5);
        assertTrue(run.rebound <= 0.06 * run.impactSpeed, "Rückprall " + run.rebound);
        assertTrue(run.quiet(), "Ereignisse auf Wolle");
        assertEquals(0, run.w.sim.telemetry().crashCount);
        assertTrue(run.w.sim.telemetry().grounded);
        assertTrue(Math.abs(run.w.sim.body.vy) < 0.05, "vy " + run.w.sim.body.vy);
    }

    @Test
    void stoneStillCrashesAtTwentyMetresPerSecond() {
        Run run = drop(STONE, 20.0);
        run.run(0.5);
        assertTrue(run.touched);
        assertTrue(run.crash);
        assertTrue(run.impacts > 0);
    }

    @Test
    void honeyCatchesHardFallWithoutReboundOrCrash() {
        Run honey = drop(SurfaceResponse.STICKY, 20.0);
        honey.run(1.0);
        assertTrue(honey.touched);
        assertTrue(honey.quiet(), "Ereignisse auf Honig");
        assertEquals(0, honey.w.sim.telemetry().crashCount);
        assertTrue(honey.rebound < 0.2, "Rückprall " + honey.rebound);
        assertTrue(honey.w.sim.telemetry().grounded);
    }

    private static Run nearWall(SurfaceResponse wall, double heading) {
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 42L));
        w.place(0.0, 3.0, 0.0, heading);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(3.0);
        for (int f = 0; f < 288; f++) {
            hold.apply(w, DT);
            w.frame(DT);
        }
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        w.wallX = w.x + w.sim.params().hitboxWidth / 2.0 + 0.001;
        Run run = new Run(w);
        run.wall = wall;
        return run;
    }

    private static Run intoWall(SurfaceResponse wall, double heading, double speed) {
        Run run = nearWall(wall, heading);
        run.w.sim.body.vx = speed;
        run.w.sim.body.vz = 0.0;
        boolean hit = false;
        for (int f = 0; f < 120; f++) {
            run.frame(0.001);
            hit |= run.w.report.hitX;
        }
        assertTrue(hit);
        return run;
    }

    @Test
    void slimeWallBouncesOnEverySide() {
        Run run = intoWall(SurfaceResponse.BOUNCY, Math.toRadians(45.0), 10.0);
        assertTrue(run.quiet(), "Ereignisse an der Slime-Wand");
        assertTrue(run.w.sim.body.vx <= -5.0, "vx " + run.w.sim.body.vx);
        assertTrue(run.w.sim.telemetry().armed);
    }

    @Test
    void woolWallCatchesWithoutCrashOrStrike() {
        Run wool = intoWall(SurfaceResponse.CUSHION, Math.toRadians(45.0), 15.0);
        assertTrue(wool.quiet(), "Ereignisse an der Wollwand");
        assertEquals(0, wool.w.sim.telemetry().propStrikeCount);
        assertTrue(wool.w.sim.telemetry().armed);
        assertTrue(Math.abs(wool.w.sim.body.vx) < 1.5, "vx " + wool.w.sim.body.vx);
        Run stone = intoWall(STONE, Math.toRadians(45.0), 15.0);
        assertTrue(stone.crash);
        assertTrue(stone.strike);
    }

    private static Run underCeiling(SurfaceResponse ceiling) {
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 42L));
        double y = w.groundY + CEILING_START - w.sim.params().hitboxFootOffset;
        w.place(0.0, y, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(y);
        for (int f = 0; f < 288; f++) {
            hold.apply(w, DT);
            w.frame(DT);
        }
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        Run run = new Run(w);
        run.ceiling = ceiling;
        run.ceilingY = w.groundY + CEILING_HEIGHT;
        return run;
    }

    private record CeilingRun(double ceilingAt, double floorAt, double after, boolean quietAbove, boolean armedAbove, int samplesAbove,
                              int harmfulBelow, PartWear wear) {
    }

    private static CeilingRun upIntoCeiling(SurfaceResponse ceiling) {
        Run run = underCeiling(ceiling);
        FlatWorld w = run.w;
        w.sim.body.vy = CEILING_SPEED;
        double ceilingAt = Double.NaN;
        double floorAt = Double.NaN;
        double after = Double.NaN;
        boolean below = false;
        boolean quietAbove = true;
        boolean armedAbove = true;
        int samplesAbove = 0;
        int harmfulBelow = 0;
        PartWear wear = PartWear.PRISTINE;
        for (double t = 0.0; t < 2.0; t += DT) {
            run.frame(DT);
            MoveReport r = w.report;
            below |= w.bottom() - w.groundY < NEAR_FLOOR;
            if (Double.isNaN(ceilingAt) && r.hitY && r.requestedY > 0.0) {
                ceilingAt = t;
            }
            if (!Double.isNaN(ceilingAt) && Double.isNaN(after) && t >= ceilingAt + BOUNCE_SECONDS) {
                after = w.sim.body.vy;
            }
            ImpactLog log = w.sim.impacts();
            for (int i = 0; i < log.size(); i++) {
                ImpactSample sample = log.get(i);
                if (!below) {
                    samplesAbove++;
                } else if (CrashDamage.harmful(DroneBuild.FREESTYLE, sample)) {
                    harmfulBelow++;
                    floorAt = Double.isNaN(floorAt) ? t : floorAt;
                    wear = CrashDamage.apply(wear, DroneBuild.FREESTYLE, sample, 1.0, 42L);
                }
            }
            if (!below) {
                quietAbove = run.quiet();
                armedAbove &= w.sim.telemetry().armed;
            }
        }
        assertTrue(Double.isFinite(ceilingAt), "Decke nicht erreicht");
        return new CeilingRun(ceilingAt, floorAt, after, quietAbove, armedAbove, samplesAbove, harmfulBelow, wear);
    }

    @Test
    void slimeCeilingStaysQuietOnlyTheStoneFloorBelowWears() {
        CeilingRun slime = upIntoCeiling(SurfaceResponse.BOUNCY);
        assertTrue(slime.quietAbove(), "Ereignisse an der Slime-Decke");
        assertEquals(0, slime.samplesAbove(), "Einträge im Aufprall-Protokoll an der Slime-Decke");
        assertTrue(slime.armedAbove(), "an der Slime-Decke entschärft");
        assertTrue(slime.after() <= -4.0, "vy nach der Slime-Decke " + slime.after());
        assertTrue(slime.harmfulBelow() > 0, "Steinboden ohne schädlichen Treffer");
        assertTrue(slime.floorAt() - slime.ceilingAt() < WEAR_WINDOW_SECONDS,
                "Treffer am Steinboden erst nach " + (slime.floorAt() - slime.ceilingAt()) + " s");
        assertFalse(slime.wear().isNew(), "Steinboden ohne Verschleiß");
        CeilingRun stone = upIntoCeiling(STONE);
        assertFalse(stone.quietAbove(), "Steindecke mit derselben Geschwindigkeit ohne Ereignis");
        assertTrue(stone.samplesAbove() > 0, "Steindecke ohne Eintrag im Aufprall-Protokoll");
    }

    @Test
    void takesOffFromWool() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.placeOnGround(0.0, 0.0, 0.0);
        Run run = new Run(w);
        run.ground = SurfaceResponse.CUSHION;
        run.run(0.5);
        w.input.mode = StabMode.ANGLE;
        w.input.throttle = 0.0;
        w.input.armSwitch = false;
        run.frame(0.0);
        w.input.armSwitch = true;
        run.frame(0.0);
        assertTrue(w.sim.telemetry().armed);
        double start = w.bottom();
        w.input.throttle = Math.min(1.0, w.sim.telemetry().hoverThrottleStick + 0.2);
        run.run(2.0);
        assertTrue(w.bottom() - start > 2.0, "Höhe " + (w.bottom() - start));
        assertTrue(run.quiet());
    }

    private static void woolFrame(BoxWorld w) {
        w.env.groundDistance = Math.max(0.0, w.y);
        w.sim.step(Math.round(DT * 1e9), w.input, w.env, w.result);
        double dx = w.result.dx;
        double dy = w.result.dy;
        double dz = w.result.dz;
        double ay = w.clip(1, dy);
        w.y += ay;
        double ax = w.clip(0, dx);
        w.x += ax;
        double az = w.clip(2, dz);
        w.z += az;
        MoveReport r = w.report;
        r.clear();
        r.requestedX = dx;
        r.requestedY = dy;
        r.requestedZ = dz;
        r.actualX = ax;
        r.actualY = ay;
        r.actualZ = az;
        r.hitX = Math.abs(ax - dx) > BoxWorld.EPS;
        r.hitY = Math.abs(ay - dy) > BoxWorld.EPS;
        r.hitZ = Math.abs(az - dz) > BoxWorld.EPS;
        SurfaceResponse.CUSHION.writeTo(r);
        Telemetry t = w.sim.telemetry();
        if (t.grounded || t.sleeping) {
            r.supportGap = w.supportGap();
        }
        w.sim.afterMove(r);
    }

    private static void settlesInWool(BoxWorld w, double depth) {
        int airborne = 0;
        for (int f = 0; f < 216; f++) {
            woolFrame(w);
            if (f >= 36 && !w.sim.telemetry().grounded) {
                airborne++;
            }
        }
        Telemetry t = w.sim.telemetry();
        assertEquals(0, airborne, "nicht am Boden in " + airborne + " von 180 Bildern, " + depth + " m in der Wolle, Unterkante " + w.bottom());
        assertTrue(t.sleeping, "ruht nicht, " + depth + " m in der Wolle, Unterkante " + w.bottom());
        assertTrue(w.bottom() > -0.001 && w.bottom() <= 0.0, "Unterkante " + w.bottom());
        assertEquals(0, t.crashCount);
    }

    @Test
    void droneSunkIntoWoolStaysGroundedAndFallsAsleep() {
        for (double depth : new double[]{0.0003, 0.0056}) {
            BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
            w.ground(0.0);
            w.placeBottom(0.0, -depth, 0.0, 0.0);
            settlesInWool(w, depth);
        }
        BoxWorld w = BoxWorld.of(DroneBuild.FREESTYLE, Tests.quietTuning());
        w.ground(0.0);
        w.placeBottom(0.0, 0.0, 0.0, 0.0);
        for (int f = 0; f < 216 && !w.sim.telemetry().sleeping; f++) {
            woolFrame(w);
        }
        assertTrue(w.sim.telemetry().sleeping, "ruht nicht auf der Wolle");
        SimSnapshot sunk = new SimSnapshot();
        w.sim.copyStateTo(sunk);
        sunk.py -= 0.003;
        w.sim.copyStateFrom(sunk);
        w.y -= 0.003;
        settlesInWool(w, 0.003);
    }

    @Test
    void defaultScaleKeepsFlightBitIdentical() {
        FlatWorld a = FlatWorld.of(DroneBuild.FREESTYLE);
        FlatWorld b = FlatWorld.of(DroneBuild.FREESTYLE);
        Run ra = new Run(a);
        Run rb = new Run(b);
        ra.ground = new SurfaceResponse(-1.0, 1.0, 1.0);
        rb.ground = new SurfaceResponse(-1.0, 1.0, Double.NaN);
        for (FlatWorld f : new FlatWorld[]{a, b}) {
            f.place(0.0, f.groundY - f.sim.params().hitboxFootOffset + 1.5, 0.0, 0.3);
            f.sim.body.vy = -9.0;
            f.sim.body.vx = 3.0;
        }
        FlatWorld c = FlatWorld.of(DroneBuild.FREESTYLE);
        c.place(0.0, c.groundY - c.sim.params().hitboxFootOffset + 1.5, 0.0, 0.3);
        c.sim.body.vy = -9.0;
        c.sim.body.vx = 3.0;
        for (int i = 0; i < 432; i++) {
            ra.frame(DT);
            rb.frame(DT);
            c.frame(DT);
            assertEquals(a.sim.body.py, b.sim.body.py);
            assertEquals(a.sim.body.vx, b.sim.body.vx);
            assertEquals(a.sim.body.vy, b.sim.body.vy);
            assertEquals(c.sim.body.py, b.sim.body.py);
            assertEquals(c.sim.body.vy, b.sim.body.vy);
            assertEquals(c.sim.body.qw, b.sim.body.qw);
        }
        assertEquals(c.sim.telemetry().crashCount, a.sim.telemetry().crashCount);
    }

    @Test
    void reportDefaultsAndSanitizing() {
        MoveReport r = new MoveReport();
        assertEquals(1.0, r.surfaceImpactScale);
        r.surfaceImpactScale = 0.0;
        r.clear();
        assertEquals(1.0, r.surfaceImpactScale);
        assertEquals(1.0, SurfaceResponse.impactScale(Double.NaN));
        assertEquals(0.0, SurfaceResponse.impactScale(-3.0));
        assertEquals(1.0, SurfaceResponse.impactScale(Double.POSITIVE_INFINITY));
        assertEquals(0.5, SurfaceResponse.impactScale(0.5));
        assertEquals(DroneSim.lossSpeed(8.0, 1.0), 8.0);
        assertEquals(Double.MAX_VALUE, DroneSim.lossSpeed(8.0, 0.0));
        assertEquals(16.0, DroneSim.lossSpeed(8.0, 0.5));
        assertEquals(3.0, DroneSim.lossApproach(3.0, 8.0, 1.0));
        assertEquals(Double.POSITIVE_INFINITY, DroneSim.lossApproach(Double.POSITIVE_INFINITY, 8.0, 1.0));
        assertEquals(ModelConstants.BOUNCE_MIN, DroneSim.lossApproach(Double.POSITIVE_INFINITY, 8.0, 0.0));
    }

    @Test
    void tableValuesAndGrip() {
        assertEquals(new SurfaceResponse(0.8, 1.0, 0.0), SurfaceResponse.BOUNCY);
        assertEquals(new SurfaceResponse(0.05, 1.5, 0.0), SurfaceResponse.CUSHION);
        assertEquals(new SurfaceResponse(0.0, 1.5, 0.0), SurfaceResponse.STICKY);
        for (float slip : new float[]{0.6F, 0.8F, 0.98F, 0.989F, 0.4F, 0.0F}) {
            double old = Math.max(0.05, Math.min(1.5, (1.0 - slip) / 0.4));
            assertEquals(old, SurfaceResponse.normal(slip).grip());
            assertEquals(-1.0, SurfaceResponse.normal(slip).restitution());
            assertEquals(1.0, SurfaceResponse.normal(slip).impactScale());
        }
        MoveReport r = new MoveReport();
        SurfaceResponse.CUSHION.writeTo(r);
        assertEquals(0.05, r.surfaceRestitution);
        assertEquals(1.5, r.surfaceGrip);
        assertEquals(0.0, r.surfaceImpactScale);
    }

    @Test
    void largestAreaWinsAndTiesPickTheSofterResponse() {
        SurfaceResponse.Mix mix = new SurfaceResponse.Mix();
        assertNull(mix.result());
        mix.add(STONE, 0.3);
        mix.add(SurfaceResponse.BOUNCY, 0.2);
        mix.add(SurfaceResponse.BOUNCY, 0.2);
        assertSame(SurfaceResponse.BOUNCY, mix.result());
        mix.clear();
        assertTrue(mix.isEmpty());
        mix.add(STONE, 0.25);
        mix.add(SurfaceResponse.BOUNCY, 0.25);
        assertSame(SurfaceResponse.BOUNCY, mix.result());
        mix.add(SurfaceResponse.CUSHION, 0.25);
        assertSame(SurfaceResponse.CUSHION, mix.result());
        mix.add(SurfaceResponse.STICKY, 0.25);
        assertSame(SurfaceResponse.STICKY, mix.result());
        mix.clear();
        mix.add(SurfaceResponse.CUSHION, 0.25);
        mix.add(STONE, 0.5);
        mix.add(SurfaceResponse.BOUNCY, Double.NaN);
        mix.add(SurfaceResponse.BOUNCY, -1.0);
        mix.add(null, 4.0);
        assertSame(STONE, mix.result());
        assertTrue(SurfaceResponse.STICKY.softerThan(SurfaceResponse.CUSHION));
        assertTrue(SurfaceResponse.CUSHION.softerThan(SurfaceResponse.BOUNCY));
        assertTrue(SurfaceResponse.BOUNCY.softerThan(STONE));
        assertTrue(SurfaceResponse.STICKY.softerThan(STONE));
        assertFalse(STONE.softerThan(SurfaceResponse.normal(0.98)));
    }

    private static final RescueWorld FLOOR = new RescueWorld() {
        @Override
        public boolean solid(int x, int y, int z) {
            return y < 64;
        }

        @Override
        public int surfaceY(int x, int z) {
            return 64;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 128;
        }
    };

    private static VoxelFlight voxelDrop(VoxelFlight.SurfaceCells cells, double speed) {
        VoxelFlight flight = new VoxelFlight(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 42L), FLOOR);
        flight.surfaces(cells);
        double height = 0.6;
        flight.place(0.5, 64.0 + height, 0.5, 0.0);
        flight.sim.body.vy = -Math.sqrt(speed * speed - 2.0 * ModelConstants.G * height);
        return flight;
    }

    private static int voxelCrashes(VoxelFlight flight) {
        int crashes = 0;
        for (int i = 0; i < 216; i++) {
            flight.frame(Math.round(DT * 1e9));
            crashes += flight.sim.events().count(SimEventType.CRASH) + flight.sim.events().count(SimEventType.PROP_STRIKE);
        }
        return crashes;
    }

    @Test
    void voxelFlightUsesTheSurfaceOfTheCells() {
        assertEquals(0, voxelCrashes(voxelDrop((x, y, z) -> SurfaceResponse.CUSHION, 20.0)));
        assertTrue(voxelCrashes(voxelDrop(null, 20.0)) > 0);
        assertTrue(voxelCrashes(voxelDrop((x, y, z) -> y == 63 ? null : SurfaceResponse.CUSHION, 20.0)) > 0);
        VoxelFlight plain = voxelDrop(null, 9.0);
        VoxelFlight normal = voxelDrop((x, y, z) -> null, 9.0);
        for (int i = 0; i < 288; i++) {
            plain.frame(Math.round(DT * 1e9));
            normal.frame(Math.round(DT * 1e9));
            assertEquals(plain.sim.body.py, normal.sim.body.py);
            assertEquals(plain.sim.body.vy, normal.sim.body.vy);
        }
        VoxelFlight slime = voxelDrop((x, y, z) -> SurfaceResponse.BOUNCY, 8.0);
        double rebound = 0.0;
        for (int i = 0; i < 72; i++) {
            slime.frame(Math.round(DT * 1e9));
            rebound = Math.max(rebound, slime.sim.body.vy);
            assertFalse(slime.sim.events().contains(SimEventType.CRASH));
        }
        assertTrue(rebound >= 5.5, "Rückprall " + rebound);
    }

    private static final int WALL_X = 2;

    private static final RescueWorld ROOM = new RescueWorld() {
        @Override
        public boolean solid(int x, int y, int z) {
            return y < 64 || x >= WALL_X && y < 72;
        }

        @Override
        public int surfaceY(int x, int z) {
            return x >= WALL_X ? 72 : 64;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 128;
        }
    };

    private static VoxelFlight voxelWall(VoxelFlight.SurfaceCells cells, double bottom, double speed) {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 42L);
        VoxelFlight flight = new VoxelFlight(sim, ROOM);
        flight.surfaces(cells);
        flight.place(WALL_X - 0.5 * sim.params().hitboxWidth - 0.03, bottom, 0.5, 0.0);
        flight.sim.body.vx = speed;
        return flight;
    }

    private static double[] wallRun(VoxelFlight flight, int frames) {
        double[] vx = new double[frames];
        for (int i = 0; i < frames; i++) {
            flight.frame(Math.round(DT * 1e9));
            vx[i] = flight.sim.body.vx;
        }
        return vx;
    }

    private static double lowest(double[] values) {
        double low = Double.POSITIVE_INFINITY;
        for (double v : values) {
            low = Math.min(low, v);
        }
        return low;
    }

    @Test
    void honeyWallCatchesWithoutRebound() {
        VoxelFlight.SurfaceCells honeyWall = (x, y, z) -> x >= WALL_X ? SurfaceResponse.STICKY : null;
        VoxelFlight.SurfaceCells stone = (x, y, z) -> null;
        double[] honeyVx = wallRun(voxelWall(honeyWall, 66.0, 6.0), 36);
        double[] plainVx = wallRun(voxelWall(stone, 66.0, 6.0), 36);
        assertTrue(lowest(plainVx) < -0.3, "Steinwand ohne Abprall: vx " + lowest(plainVx));
        assertTrue(lowest(honeyVx) > -0.05, "Honigwand prallt ab: vx " + lowest(honeyVx));
    }

    @Test
    void stoneWallOverHoneyFloorStaysAStoneWall() {
        VoxelFlight.SurfaceCells honeyFloor = (x, y, z) -> y == 63 ? SurfaceResponse.STICKY : null;
        double[] overHoney = wallRun(voxelWall(honeyFloor, 64.3, 6.0), 24);
        assertTrue(lowest(overHoney) < -0.3, "Steinwand über Honigboden klebt: vx " + lowest(overHoney));
    }
}

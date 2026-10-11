package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakFeelTest {
    private static final double PANE_JOULES = 36.0;
    private static final double PANE_SHARDS = 10.0;
    private static final double BLOCK_JOULES = 120.0;
    private static final double BLOCK_SHARDS = 120.0 * 10.0 / 36.0;
    private static final int FRONT_MASK = 1 << CrashDamage.FRONT_RIGHT_MOTOR | 1 << CrashDamage.FRONT_LEFT_MOTOR;

    private static DroneSim flying(DroneBuild build, double pitchDeg, double speed, SimTuning tuning) {
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), tuning, 7L);
        sim.reset(0.0, 40.0, 0.0, 0.0);
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        double half = Math.toRadians(pitchDeg) / 2.0;
        snapshot.qw = Math.cos(half);
        snapshot.qx = Math.sin(half);
        snapshot.qy = 0.0;
        snapshot.qz = 0.0;
        snapshot.vx = 0.0;
        snapshot.vy = 0.0;
        snapshot.vz = speed;
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            snapshot.rotorOmega[i] = 1.5 * sim.params().hoverOmega;
        }
        snapshot.armed = true;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
        return sim;
    }

    private static DroneSim flying(DroneBuild build, double pitchDeg, double speed) {
        return flying(build, pitchDeg, speed, Tests.quietTuning());
    }

    private static BreakReport hit(DroneSim sim, double joules, double shards, double strength, long seed) {
        sim.breakThrough(0.0, 0.0, -1.0, joules, BreakContact.open(shards, strength, seed));
        return sim.lastBreak();
    }

    @Test
    void forwardFlightLeadsWithTheLowFrontProps() {
        DroneSim sim = flying(DroneBuild.FREESTYLE, 40.0, 25.0);
        assertEquals(40.0, sim.body.tiltRad() * SimMath.DEG, 1.0E-6);
        BreakReport report = hit(sim, PANE_JOULES, PANE_SHARDS, 1.0, 3L);
        assertEquals(FRONT_MASK, report.contacts() & ImpactSample.STRUCK_MASK, "vorn liegen die beiden vorderen Prop-Spitzen an");
        assertEquals(FRONT_MASK, report.struck());
    }

    @Test
    void zeroStrengthIsTheEnergyOnlyBreakOfBefore() {
        DroneSim plain = flying(DroneBuild.FREESTYLE, 40.0, 25.0);
        DroneSim scaled = flying(DroneBuild.FREESTYLE, 40.0, 25.0);
        assertNull(plain.lastBreak());
        double a = plain.breakThrough(0.0, 0.0, -1.0, PANE_JOULES);
        double b = scaled.breakThrough(0.0, 0.0, -1.0, PANE_JOULES, new BreakContact(PANE_SHARDS, -1.0, 39.0, 0.0, 1.0, 41.0, 0.2, 0.0, 5L));
        assertEquals(a, b, 0.0);
        Vector3d va = new Vector3d();
        Vector3d vb = new Vector3d();
        plain.getVelocity(va);
        scaled.getVelocity(vb);
        assertEquals(va.z, vb.z, 0.0);
        assertEquals(Math.sqrt(625.0 - 2.0 * PANE_JOULES / plain.params().massKg), va.z, 1.0E-9);
        BreakReport report = scaled.lastBreak();
        assertNotNull(report);
        assertEquals(1, scaled.breakCount());
        assertEquals(0.0, report.lostShards(), 0.0);
        assertEquals(0.0, report.rateChange(), 0.0);
        assertEquals(0, report.struck());
        assertEquals(0.0, scaled.body.wbx, 0.0);
        assertEquals(1.5 * scaled.params().hoverOmega, scaled.power.omega[CrashDamage.FRONT_LEFT_MOTOR], 1.0E-9);
        assertEquals(0, scaled.events().count(SimEventType.PROP_STRIKE));
    }

    @Test
    void shardsTakeMomentumAndABlockTakesMoreThanAPane() {
        double m = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 7L).params().massKg;
        double previousShare = 1.0;
        for (double speed : new double[]{23.0, 28.0, 34.0, 40.0}) {
            BreakReport pane = hit(flying(DroneBuild.FREESTYLE, 40.0, speed), PANE_JOULES, PANE_SHARDS, 1.0, 3L);
            BreakReport block = hit(flying(DroneBuild.FREESTYLE, 40.0, speed), BLOCK_JOULES, BLOCK_SHARDS, 1.0, 3L);
            double afterFracture = Math.sqrt(speed * speed - 2.0 * PANE_JOULES / m);
            assertEquals(speed - afterFracture, pane.lostFracture(), 1.0E-9);
            assertEquals(afterFracture * pane.plugKg() / (m + pane.plugKg()), pane.lostShards(), 1.0E-9);
            assertEquals(BLOCK_SHARDS / PANE_SHARDS, block.plugKg() / pane.plugKg(), 1.0E-9);
            assertTrue(block.lostFracture() > 2.0 * pane.lostFracture(), "Block schluckt mehr Bruchenergie bei " + speed);
            assertTrue(block.lostShards() / (speed - block.lostFracture()) > 2.5 * pane.lostShards() / (speed - pane.lostFracture()),
                    "Block reißt je Tempo mehr Scherben mit bei " + speed);
            assertTrue(block.lost() > 2.0 * pane.lost(), "Block kostet insgesamt mehr bei " + speed);
            double share = pane.lost() / speed;
            assertTrue(share > 0.08 && share < 0.45, "Scheibe kostet spürbar Tempo, aber keinen Stillstand: " + share + " bei " + speed);
            assertTrue(share < previousShare, "je schneller, desto kleiner der Anteil");
            previousShare = share;
            assertTrue(pane.lostShards() > 0.03 * speed && pane.lostShards() < 0.15 * speed, "Scherben nehmen 3–15 %: " + pane.lostShards());
        }
    }

    @Test
    void aHeavyDroneLosesLessThanALightOne() {
        BreakReport racer = hit(flying(DroneBuild.RACER, 40.0, 30.0), PANE_JOULES, PANE_SHARDS, 1.0, 3L);
        BreakReport longRange = hit(flying(DroneBuild.LONG_RANGE, 40.0, 30.0), PANE_JOULES, PANE_SHARDS, 1.0, 3L);
        assertTrue(longRange.lostFracture() < 0.7 * racer.lostFracture(), longRange.lostFracture() + " < " + racer.lostFracture());
    }

    @Test
    void aTiltedHitTripsTheNoseDownAndALevelOneBarelyTumbles() {
        double tilted = 0.0;
        double level = 0.0;
        for (long seed = 0; seed < 40; seed++) {
            BreakReport forward = hit(flying(DroneBuild.FREESTYLE, 40.0, 25.0), PANE_JOULES, PANE_SHARDS, 1.0, seed);
            assertTrue(forward.rateX() > 1.0, "Nase nach unten bei Saat " + seed + ": " + forward.rateX());
            assertTrue(forward.rateChange() < BreakImpulse.MAX_RATE_CHANGE + 1.0E-9);
            tilted += forward.rateChange() / 40.0;
            DroneSim flat = flying(DroneBuild.FREESTYLE, 0.0, 25.0, strikesOff());
            level += hit(flat, PANE_JOULES, PANE_SHARDS, 1.0, seed).rateChange() / 40.0;
        }
        assertTrue(tilted > 3.0 && tilted < 9.0, "typischer Stoß 170–520 °/s: " + tilted + " rad/s");
        assertTrue(level < 0.6 * tilted, "waagerecht und mittig trifft der Stoß fast durch den Schwerpunkt: " + level + " gegen " + tilted);
    }

    private static SimTuning strikesOff() {
        SimTuning tuning = Tests.quietTuning();
        tuning.propStrikes = false;
        return tuning;
    }

    @Test
    void glassOnOneSideYawsTheDroneTowardsThatSide() {
        for (long seed = 0; seed < 40; seed++) {
            DroneSim right = flying(DroneBuild.FREESTYLE, 0.0, 25.0, strikesOff());
            right.breakThrough(0.0, 0.0, -1.0, PANE_JOULES, new BreakContact(PANE_SHARDS, 0.05, 30.0, -1.0, 1.0, 50.0, 1.0, 1.0, seed));
            DroneSim left = flying(DroneBuild.FREESTYLE, 0.0, 25.0, strikesOff());
            left.breakThrough(0.0, 0.0, -1.0, PANE_JOULES, new BreakContact(PANE_SHARDS, -1.0, 30.0, -1.0, -0.05, 50.0, 1.0, 1.0, seed));
            DroneSim full = flying(DroneBuild.FREESTYLE, 0.0, 25.0, strikesOff());
            full.breakThrough(0.0, 0.0, -1.0, PANE_JOULES, BreakContact.open(PANE_SHARDS, 1.0, seed));
            assertTrue(right.body.wwy > 1.0, "Glas rechts dreht um die Hochachse: " + right.body.wwy);
            assertTrue(left.body.wwy < -1.0, "Glas links dreht andersherum: " + left.body.wwy);
            assertTrue(right.lastBreak().share() < 0.7 && right.lastBreak().share() > 0.2);
            assertEquals(1.0, full.lastBreak().share(), 0.0);
            assertTrue(right.lastBreak().plugKg() < 0.7 * full.lastBreak().plugKg(), "halbe Drohne, weniger Scherben");
            assertTrue(Math.abs(full.body.wwy) < Math.abs(right.body.wwy), "mittig giert sie weniger");
        }
    }

    @Test
    void theKickIsCappedAndGrowsWithTheRule() {
        BreakReport hard = hit(flying(DroneBuild.FREESTYLE, 50.0, 12.5), PANE_JOULES, PANE_SHARDS, 3.0, 9L);
        assertEquals(BreakImpulse.MAX_RATE_CHANGE, hard.rateChange(), 1.0E-9);
        BreakReport half = hit(flying(DroneBuild.FREESTYLE, 40.0, 30.0), PANE_JOULES, PANE_SHARDS, 0.5, 9L);
        BreakReport normal = hit(flying(DroneBuild.FREESTYLE, 40.0, 30.0), PANE_JOULES, PANE_SHARDS, 1.0, 9L);
        BreakReport twice = hit(flying(DroneBuild.FREESTYLE, 40.0, 30.0), PANE_JOULES, PANE_SHARDS, 2.0, 9L);
        assertTrue(half.lostShards() < normal.lostShards() && normal.lostShards() < twice.lostShards());
        assertTrue(half.rateChange() < normal.rateChange() && normal.rateChange() < twice.rateChange());
        assertEquals(half.lostFracture(), twice.lostFracture(), 0.0, "die Bruchenergie hängt nicht an der Regel");
        assertEquals(hit(flying(DroneBuild.FREESTYLE, 40.0, 30.0), PANE_JOULES, PANE_SHARDS, 9.0, 9L).lostShards(),
                hit(flying(DroneBuild.FREESTYLE, 40.0, 30.0), PANE_JOULES, PANE_SHARDS, 3.0, 9L).lostShards(), 0.0);
    }

    @Test
    void openPropsChopTheShardsDuctsAndBellyHitsDoNot() {
        DroneSim open = flying(DroneBuild.FREESTYLE, 40.0, 25.0);
        double before = open.power.omega[CrashDamage.FRONT_LEFT_MOTOR];
        BreakReport report = hit(open, PANE_JOULES, PANE_SHARDS, 1.0, 3L);
        assertEquals(2, report.struckCount());
        assertEquals((1.0 - BreakImpulse.STRIKE_LOSS) * before, open.power.omega[CrashDamage.FRONT_LEFT_MOTOR], 1.0E-9);
        assertEquals((1.0 - BreakImpulse.STRIKE_LOSS) * before, open.power.omega[CrashDamage.FRONT_RIGHT_MOTOR], 1.0E-9);
        assertEquals(before, open.power.omega[0], 1.0E-9, "hintere Props drehen weiter");
        assertEquals(2, open.events().count(SimEventType.PROP_STRIKE));
        assertEquals(1, open.impacts().size());
        ImpactSample sample = open.impacts().get(0);
        assertEquals(FRONT_MASK, sample.struck());
        assertTrue(sample.touches(ImpactSample.HULL_TIP + CrashDamage.FRONT_LEFT_MOTOR));
        assertEquals((float) report.lostFracture(), sample.speed(), 1.0E-5F);
        assertEquals(1.5F, sample.strikeSpin(), 1.0E-4F);

        DroneSim ducted = flying(DroneBuild.CINEWHOOP, 40.0, 25.0);
        assertEquals(0, hit(ducted, PANE_JOULES, PANE_SHARDS, 1.0, 3L).struck());
        assertTrue(ducted.lastBreak().rateChange() > 0.5, "auch der Schutzring wird gestoßen");

        assertEquals(0, hit(flying(DroneBuild.FREESTYLE, 40.0, 25.0, strikesOff()), PANE_JOULES, PANE_SHARDS, 1.0, 3L).struck());

        DroneSim falling = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 7L);
        falling.reset(0.0, 40.0, 0.0, 0.0);
        SimSnapshot snapshot = new SimSnapshot();
        falling.copyStateTo(snapshot);
        snapshot.vy = -20.0;
        snapshot.sleeping = false;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            snapshot.rotorOmega[i] = 1.5 * falling.params().hoverOmega;
        }
        falling.copyStateFrom(snapshot);
        falling.breakThrough(0.0, 1.0, 0.0, PANE_JOULES, BreakContact.open(PANE_SHARDS, 1.0, 3L));
        assertEquals(0, falling.lastBreak().struck(), "mit dem Bauch durch ein Glasdach trifft kein Prop");
        assertTrue((falling.lastBreak().contacts() & 1 << ImpactSample.HULL_BOTTOM) != 0);
    }

    @Test
    void theCrashDecisionIgnoresTheShards() {
        double tolerance = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 7L).params().impactTolerance;
        for (double strength : new double[]{0.0, 1.0, 3.0}) {
            DroneSim pane = flying(DroneBuild.FREESTYLE, 40.0, 25.0);
            BreakReport through = hit(pane, PANE_JOULES, PANE_SHARDS, strength, 3L);
            assertFalse(through.crash(), "Scheibe mit 25 m/s bei " + strength);
            assertTrue(through.lostFracture() < tolerance);
            assertEquals(0, pane.events().count(SimEventType.CRASH));
            assertTrue(pane.telemetry().armed || pane.events().count(SimEventType.DISARMED) == 0);

            DroneSim block = flying(DroneBuild.FREESTYLE, 40.0, 24.0);
            BreakReport smashed = hit(block, BLOCK_JOULES, BLOCK_SHARDS, strength, 3L);
            assertTrue(smashed.crash(), "Glasblock mit 24 m/s bei " + strength);
            assertEquals(1, block.events().count(SimEventType.CRASH));

            DroneSim fast = flying(DroneBuild.FREESTYLE, 40.0, 40.0);
            BreakReport hardHit = hit(fast, BLOCK_JOULES, BLOCK_SHARDS, strength, 3L);
            assertFalse(hardHit.crash(), "Glasblock mit 40 m/s bei " + strength + ": " + hardHit.lostFracture());
            assertEquals(1, fast.events().count(SimEventType.HARD_IMPACT));
        }
    }

    private static float[] propLoss(double throttleSpin, double speed) {
        float front = 0.0F;
        float rear = 0.0F;
        int runs = 80;
        for (long seed = 0; seed < runs; seed++) {
            DroneSim sim = flying(DroneBuild.FREESTYLE, 40.0, speed);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                sim.power.omega[i] = throttleSpin * sim.params().hoverOmega;
            }
            hit(sim, PANE_JOULES, PANE_SHARDS, 1.0, seed);
            PartWear wear = CrashDamage.apply(PartWear.PRISTINE, DroneBuild.FREESTYLE, sim.impacts().get(0), 1.0, seed);
            front += (2.0F - wear.props()[PartWear.FRONT_LEFT] - wear.props()[PartWear.FRONT_RIGHT]) / (2 * runs);
            rear += (2.0F - wear.props()[PartWear.REAR_LEFT] - wear.props()[PartWear.REAR_RIGHT]) / (2 * runs);
            assertEquals(PartWear.NEW, wear.battery(), 0.02F);
        }
        return new float[]{front, rear};
    }

    @Test
    void shardsNickTheFrontPropsAtFullThrottleOnly() {
        float[] cruise = propLoss(1.2, 25.0);
        float[] punch = propLoss(2.2, 25.0);
        float[] barely = propLoss(2.2, 13.0);
        assertEquals(0.0F, cruise[0], 1.0E-6F, "im Reiseflug federn die Props die Scherben ab");
        assertTrue(punch[0] > 0.002F && punch[0] < 0.2F, "bei Vollgas im Mittel leicht angeschlagen: " + punch[0]);
        assertTrue(punch[1] < 0.5F * punch[0], "hintere Props kaum: " + punch[1] + " gegen " + punch[0]);
        assertTrue(barely[0] > punch[0], "ein knapper Durchbruch setzt den Props stärker zu: " + barely[0] + " gegen " + punch[0]);
    }

    private static double[] passage(double strength) {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 60.0, StabMode.ANGLE, 1.0);
        AltitudeHold hold = new AltitudeHold(60.0);
        double dt = 1.0 / 144.0;
        w.input.pitch = 0.8;
        for (int i = 0; i < 144 * 12; i++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        RigidBody b = w.sim.body;
        double speed = Math.sqrt(b.vx * b.vx + b.vz * b.vz);
        double dx = b.vx / speed;
        double dz = b.vz / speed;
        Telemetry t = w.sim.telemetry();
        double pitch = t.pitchDeg;
        double roll = t.rollDeg;
        w.input.mode = StabMode.ACRO;
        w.input.pitch = 0.0;
        w.sim.breakThrough(-dx, 0.0, -dz, PANE_JOULES, BreakContact.open(PANE_SHARDS, strength, 21L));
        double worst = 0.0;
        for (int i = 0; i < 144; i++) {
            w.frame(dt);
            worst = Math.max(worst, Math.max(Math.abs(t.pitchDeg - pitch), Math.abs(t.rollDeg - roll)));
        }
        double rate = Math.sqrt(b.wbx * b.wbx + b.wby * b.wby + b.wbz * b.wbz) * SimMath.DEG;
        return new double[]{speed, w.sim.lastBreak().lost(), worst, rate, t.armed ? 1.0 : 0.0};
    }

    @Test
    void theFlightControllerCatchesAPaneAtFullSpeed() {
        double[] off = passage(0.0);
        double[] normal = passage(1.0);
        double[] strong = passage(2.0);
        assertTrue(off[0] > 20.0, "Anflug mit " + off[0] + " m/s");
        assertTrue(off[2] < 2.5, "ohne Stoß bleibt die Lage: " + off[2]);
        assertTrue(normal[2] > 4.0 && normal[2] < 16.0, "spürbar, aber fliegbar: " + normal[2] + "°");
        assertTrue(strong[2] > normal[2], "200 % kippt weiter: " + strong[2]);
        assertTrue(normal[1] > off[1] + 1.0, "Scherben kosten Tempo: " + normal[1] + " gegen " + off[1]);
        assertEquals(1.0, normal[4], "bleibt scharf");
        assertTrue(normal[3] < 40.0, "nach einer Sekunde dreht sie nicht mehr: " + normal[3] + " °/s");
    }
}

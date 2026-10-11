package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WearTest {
    private static final int FRONT_TIPS = (1 << ImpactSample.HULL_TIP + 1) | (1 << ImpactSample.HULL_TIP + 3);
    private static final int BELLY = (1 << ImpactSample.HULL_BOTTOM) | (1 << ImpactSample.HULL_MOTOR) | (1 << ImpactSample.HULL_MOTOR + 1)
            | (1 << ImpactSample.HULL_MOTOR + 2) | (1 << ImpactSample.HULL_MOTOR + 3);

    private static ImpactSample frontal(double speed) {
        return new ImpactSample((float) speed, 0.0F, 0.0F, 0.0F, -1.0F, FRONT_TIPS | (1 << ImpactSample.HULL_ELECTRONICS), 0, 0.0F);
    }

    private static ImpactSample belly(double speed) {
        return new ImpactSample((float) speed, 0.0F, 0.0F, 1.0F, 0.0F, BELLY, 0, 0.0F);
    }

    private static ImpactSample random(SplittableRandom rng, double speed) {
        return switch (rng.nextInt(4)) {
            case 0 -> frontal(speed);
            case 1 -> belly(speed);
            case 2 -> new ImpactSample((float) speed, (float) (speed * 0.5), -1.0F, 0.0F, 0.0F,
                    (1 << ImpactSample.HULL_TIP) | (1 << ImpactSample.HULL_TIP + 1), 0, 0.0F);
            default -> new ImpactSample((float) speed, 0.0F, 0.0F, -1.0F, 0.0F, 1 << ImpactSample.HULL_ELECTRONICS, 0, 0.0F);
        };
    }

    private static PartWear session(DroneBuild build, int crashes, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        PartWear wear = PartWear.PRISTINE;
        for (int i = 0; i < crashes; i++) {
            double speed = build.frame().impactTolerance * (0.8 + 0.8 * rng.nextDouble() * rng.nextDouble());
            wear = CrashDamage.apply(wear, build, random(rng, speed), 1.0, rng.nextLong());
        }
        return wear;
    }

    private static double averageLoss(PartWear wear, int first, int count) {
        double sum = 0.0;
        for (int slot = first; slot < first + count; slot++) {
            sum += 1.0 - wear.slot(slot);
        }
        return sum / count;
    }

    @Test
    void slowBumpsLeaveThePartsAlone() {
        PartWear wear = CrashDamage.apply(PartWear.PRISTINE, DroneBuild.FREESTYLE, frontal(1.4), 1.0, 1L);
        assertSame(PartWear.PRISTINE, wear);
        assertFalse(CrashDamage.harmful(DroneBuild.FREESTYLE, belly(1.0)));
    }

    @Test
    void everyCrashDamagesEveryPart() {
        DroneBuild build = DroneBuild.FREESTYLE;
        for (long seed = 0; seed < 20; seed++) {
            PartWear wear = CrashDamage.apply(PartWear.PRISTINE, build, frontal(build.frame().impactTolerance), 1.0, seed);
            for (int slot = 0; slot < PartWear.SLOTS; slot++) {
                assertTrue(wear.slot(slot) < PartWear.NEW, "Platz " + slot + " unbeschädigt: " + wear);
            }
        }
    }

    @Test
    void harderCrashesDamageMore() {
        DroneBuild build = DroneBuild.FREESTYLE;
        double previous = 0.0;
        for (double speed = 6.0; speed <= 24.0; speed += 3.0) {
            PartWear wear = CrashDamage.apply(PartWear.PRISTINE, build, frontal(speed), 1.0, 7L);
            double loss = averageLoss(wear, PartWear.SLOT_MOTOR, PartWear.POSITIONS);
            assertTrue(loss > previous, speed + " m/s: " + loss + " <= " + previous);
            previous = loss;
        }
    }

    @Test
    void hitDirectionDecidesWhatBreaks() {
        DroneBuild build = DroneBuild.FREESTYLE;
        PartWear front = CrashDamage.apply(PartWear.PRISTINE, build, frontal(14.0), 1.0, 3L);
        PartWear bottom = CrashDamage.apply(PartWear.PRISTINE, build, belly(14.0), 1.0, 3L);
        assertTrue(front.video() < bottom.video(), "Kamera vorne " + front.video() + " unten " + bottom.video());
        assertTrue(front.prop(PartWear.FRONT_LEFT) < front.prop(PartWear.REAR_LEFT));
        assertTrue(front.prop(PartWear.FRONT_RIGHT) < front.prop(PartWear.REAR_RIGHT));
        assertTrue(bottom.stack() < front.stack());
    }

    @Test
    void lifespansFollowPropsMotorsCameraFrameStackBattery() {
        double props = 0.0;
        double motors = 0.0;
        double video = 0.0;
        double arms = 0.0;
        double stack = 0.0;
        double battery = 0.0;
        int sessions = 40;
        for (long seed = 0; seed < sessions; seed++) {
            PartWear wear = session(DroneBuild.FREESTYLE, 12, seed);
            props += averageLoss(wear, PartWear.SLOT_PROP, PartWear.POSITIONS);
            motors += averageLoss(wear, PartWear.SLOT_MOTOR, PartWear.POSITIONS);
            video += 1.0 - wear.video();
            double arm = 0.0;
            for (int p = 0; p < PartWear.POSITIONS; p++) {
                arm += 1.0 - wear.arm(p);
            }
            arms += arm / PartWear.POSITIONS;
            stack += 1.0 - wear.stack();
            battery += 1.0 - wear.battery();
        }
        assertTrue(props > 2.0 * motors, "Props " + props + " Motoren " + motors);
        assertTrue(motors > video, "Motoren " + motors + " Kamera " + video);
        assertTrue(motors > arms, "Motoren " + motors + " Arme " + arms);
        assertTrue(arms > stack, "Arme " + arms + " Stack " + stack);
        assertTrue(video > stack, "Kamera " + video + " Stack " + stack);
        assertTrue(stack > 0.0 && battery > 0.0);
    }

    @Test
    void aNormalSessionMeansNewPropsButRarelyNewMotorsOrFrames() {
        int propSwaps = 0;
        int motorDeaths = 0;
        int frameDeaths = 0;
        int sessions = 50;
        for (long seed = 0; seed < sessions; seed++) {
            PartWear wear = session(DroneBuild.FREESTYLE, 12, 1000L + seed);
            if (wear.damagedProps() > 0) {
                propSwaps++;
            }
            if (PartWear.critical(wear.worstMotor())) {
                motorDeaths++;
            }
            if (PartWear.critical(wear.frame())) {
                frameDeaths++;
            }
        }
        assertTrue(propSwaps >= sessions * 0.8, "Sitzungen mit Propwechsel: " + propSwaps);
        assertTrue(motorDeaths <= sessions * 0.1, "Sitzungen mit kaputtem Motor: " + motorDeaths);
        assertEquals(0, frameDeaths, "Sitzungen mit kaputtem Rahmen");
    }

    private static ImpactSample onPack(DroneBuild build, double speed) {
        boolean top = FrameGeometry.of(build.frame()).batteryOnTop();
        return new ImpactSample((float) speed, 0.0F, 0.0F, top ? -1.0F : 1.0F, 0.0F, top ? 0 : 1 << ImpactSample.HULL_BOTTOM, 0, 0.0F);
    }

    @Test
    void veryHardHitsCanPuffTheBattery() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.WHOOP, DroneBuild.LONG_RANGE}) {
            int puffed = 0;
            double hard = build.frame().impactTolerance * 4.0;
            for (long seed = 0; seed < 200; seed++) {
                PartWear wear = CrashDamage.apply(PartWear.PRISTINE, build, onPack(build, hard), 1.0, seed);
                if (wear.battery() < 0.8F) {
                    puffed++;
                }
            }
            assertTrue(puffed > 10 && puffed < 190, build.frame() + " aufgebläht: " + puffed);
            for (long seed = 0; seed < 200; seed++) {
                PartWear wear = CrashDamage.apply(PartWear.PRISTINE, build, onPack(build, build.frame().impactTolerance * 1.3), 1.0, seed);
                assertTrue(wear.battery() > 0.95F, "normaler Absturz bläht den Akku: " + wear.battery());
            }
        }
    }

    @Test
    void ductsProtectProps() {
        DroneBuild open = DroneBuild.FREESTYLE;
        DroneBuild ducted = DroneBuild.CINEWHOOP;
        PartWear a = CrashDamage.apply(PartWear.PRISTINE, open, frontal(open.frame().impactTolerance * 1.5), 1.0, 9L);
        PartWear b = CrashDamage.apply(PartWear.PRISTINE, ducted, frontal(ducted.frame().impactTolerance * 1.5), 1.0, 9L);
        assertTrue(averageLoss(b, PartWear.SLOT_PROP, 4) < averageLoss(a, PartWear.SLOT_PROP, 4));
    }

    @Test
    void strengthScalesAndZeroSwitchesItOff() {
        DroneBuild build = DroneBuild.FREESTYLE;
        ImpactSample hit = frontal(10.0);
        assertSame(PartWear.PRISTINE, CrashDamage.apply(PartWear.PRISTINE, build, hit, 0.0, 1L));
        PartWear normal = CrashDamage.apply(PartWear.PRISTINE, build, hit, 1.0, 1L);
        PartWear double_ = CrashDamage.apply(PartWear.PRISTINE, build, hit, 2.0, 1L);
        double a = averageLoss(normal, PartWear.SLOT_MOTOR, 4);
        double b = averageLoss(double_, PartWear.SLOT_MOTOR, 4);
        assertEquals(2.0, b / a, 0.1);
        assertEquals(CrashDamage.MAX_STRENGTH, CrashDamage.clampStrength(50.0));
        assertEquals(0.0, CrashDamage.clampStrength(Double.NaN));
    }

    @Test
    void garbageImpactsAreSanitized() {
        ImpactSample bad = new ImpactSample(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, 0.0F, 0.0F, -1, -1, Float.NaN);
        ImpactSample clean = bad.sanitized();
        assertEquals(0.0F, clean.speed());
        assertEquals(1.0F, clean.normalY());
        assertEquals(ImpactSample.CONTACT_MASK, clean.contacts());
        assertEquals(ImpactSample.STRUCK_MASK, clean.struck());
        PartWear wear = CrashDamage.apply(PartWear.PRISTINE, DroneBuild.FREESTYLE,
                new ImpactSample(1.0E9F, 0.0F, 0.0F, 1.0F, 0.0F, 0, 0, 0.0F), 1.0, 1L);
        for (int slot = 0; slot < PartWear.SLOTS; slot++) {
            float value = wear.slot(slot);
            assertTrue(value >= 0.0F && value <= 1.0F);
        }
    }

    @Test
    void brokenPropGivesAlmostNoThrustAndWornPropsVibrate() {
        for (double brittleness : new double[]{0.0, 0.5, 1.0}) {
            assertTrue(WearEffects.propThrust(0.0F, brittleness) < 0.1);
            assertTrue(WearEffects.propThrust(0.5F, brittleness) >= 0.9);
            assertEquals(1.0, WearEffects.propThrust(1.0F, brittleness), 1.0E-9);
            assertEquals(0.0, WearEffects.vibration(PartWear.PRISTINE, brittleness), 1.0E-9);
            PartWear worn = PartWear.PRISTINE.withProp(0, 0.4F);
            PartWear bent = PartWear.PRISTINE.withMotor(1, 0.4F);
            assertTrue(WearEffects.vibration(worn, brittleness) > 0.0);
            assertTrue(WearEffects.vibration(bent, brittleness) > 0.0);
            assertTrue(WearEffects.vibration(PartWear.PRISTINE.withProp(0, 0.0F), brittleness) > WearEffects.vibration(worn, brittleness));
        }
        assertTrue(WearEffects.hardwareFault(PartWear.PRISTINE.withStack(0.0F)));
        assertTrue(WearEffects.hardwareFault(PartWear.PRISTINE.withBattery(0.0F)));
        assertFalse(WearEffects.hardwareFault(PartWear.PRISTINE.withProp(2, 0.0F)));
        assertTrue(WearEffects.batteryResistance(PartWear.PRISTINE.withBattery(0.3F)) > 1.5);
    }

    @Test
    void bentPropsLoseThrustCrackedPropsShake() {
        float condition = 0.5F;
        assertTrue(WearEffects.propThrust(condition, 0.0) < WearEffects.propThrust(condition, 1.0));
        assertTrue(WearEffects.propVibration(condition, 0.0) < WearEffects.propVibration(condition, 1.0));
        assertEquals(WearEffects.BROKEN_PROP_THRUST, WearEffects.propThrust(0.0F, 0.0), 1.0E-12);
        assertEquals(WearEffects.BROKEN_PROP_VIBRATION, WearEffects.propVibration(0.0F, 1.0), 1.0E-12);
    }

    @Test
    void brokenPropMakesTheDroneUnflyable() {
        FlatWorld w = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 6.0, StabMode.ANGLE, 0.8);
        AltitudeHold hold = new AltitudeHold(6.0);
        double dt = 1.0 / 144.0;
        w.sim.setWear(PartWear.PRISTINE.withProp(PartWear.FRONT_LEFT, 0.0F));
        double maxTilt = 0.0;
        double lowest = w.y;
        for (int i = 0; i < 216; i++) {
            hold.apply(w, dt);
            w.frame(dt);
            maxTilt = Math.max(maxTilt, w.sim.telemetry().tiltDeg);
            lowest = Math.min(lowest, w.y);
        }
        assertTrue(maxTilt > 60.0 || lowest < 4.5, "Drohne fliegt mit gebrochenem Prop weiter: Neigung " + maxTilt + ", tiefster Punkt " + lowest);
    }

    @Test
    void wornPropsStillFlyButNeedMoreThrottle() {
        FlatWorld fresh = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 6.0, StabMode.ANGLE, 0.8);
        FlatWorld worn = Tests.hovering(DroneBuild.FREESTYLE, Tests.quietTuning(), 6.0, StabMode.ANGLE, 0.8);
        worn.sim.setWear(PartWear.uniform(1.0F, 1.0F, 1.0F, 0.3F, 1.0F, 1.0F));
        AltitudeHold a = new AltitudeHold(6.0);
        AltitudeHold b = new AltitudeHold(6.0);
        double dt = 1.0 / 144.0;
        double throttleFresh = 0.0;
        double throttleWorn = 0.0;
        for (int i = 0; i < 576; i++) {
            a.apply(fresh, dt);
            b.apply(worn, dt);
            fresh.frame(dt);
            worn.frame(dt);
            if (i >= 432) {
                throttleFresh += fresh.input.throttle;
                throttleWorn += worn.input.throttle;
            }
        }
        assertTrue(worn.sim.telemetry().tiltDeg < 15.0, "abgenutzte Props kippen: " + worn.sim.telemetry().tiltDeg);
        assertTrue(Math.abs(worn.y - 6.0) < 0.5, "Höhe " + worn.y);
        assertTrue(throttleWorn > throttleFresh * 1.02, "Gas frisch " + throttleFresh + " abgenutzt " + throttleWorn);
        assertTrue(worn.sim.telemetry().vibration > 0.0);
        assertEquals(0.0, fresh.sim.telemetry().vibration, 1.0E-9);
    }

    @Test
    void brokenElectronicsBlockArming() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.sim.setWear(PartWear.PRISTINE.withStack(0.0F));
        w.arm();
        assertFalse(w.sim.telemetry().armed);
        assertTrue(ArmBlocker.HARDWARE_FAULT.isSet(w.sim.telemetry().armBlockers));
        w.sim.setWear(PartWear.PRISTINE);
        w.input.armSwitch = false;
        w.frame(0L);
        w.arm();
        assertTrue(w.sim.telemetry().armed);
        w.sim.setWear(PartWear.PRISTINE.withBattery(0.0F));
        assertFalse(w.sim.telemetry().armed);
        assertEquals(DisarmReason.HARDWARE, w.sim.telemetry().lastDisarmReason);
    }

    @Test
    void wallCrashIsLoggedAsImpact() {
        FlatWorld w = FlatWorld.of(DroneBuild.FREESTYLE);
        w.place(0.0, 3.0, 0.0, 0.0);
        w.wallX = w.x + w.sim.params().hitboxWidth / 2.0 + 0.001;
        w.sim.body.vx = 12.0;
        boolean logged = false;
        for (int f = 0; f < 50 && !logged; f++) {
            w.frame(1_000_000L);
            ImpactLog log = w.sim.impacts();
            if (log.size() > 0) {
                logged = true;
                assertEquals(12.0, log.strongest(), 1.0);
                ImpactSample sample = log.get(0);
                assertTrue(CrashDamage.harmful(DroneBuild.FREESTYLE, sample));
                assertEquals(1.0, Math.abs(sample.normalX()), 1.0E-3);
            }
        }
        assertTrue(logged);
        w.frame(1_000_000L);
        assertEquals(0, w.sim.impacts().size());
    }
}

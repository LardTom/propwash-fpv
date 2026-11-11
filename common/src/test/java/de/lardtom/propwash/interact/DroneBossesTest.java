package de.lardtom.propwash.interact;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.Knockback;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.CrashDamage;
import de.lardtom.propwash.sim.ImpactSample;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneBossesTest {
    private static final List<DroneBuild> BUILDS = List.of(DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER,
            DroneBuild.LONG_RANGE);
    private static final Vec3 SIDE = new Vec3(1.0, 0.0, 0.0);

    @Test
    void worldRuleIsOnByDefaultRaceRelevantAndInEveryPreset() {
        RuleSpec spec = WorldRuleSpecs.DRONES_BOSS_INTERACTIONS;
        assertEquals("drones_boss_interactions", spec.name());
        assertEquals(RuleSpec.Kind.BOOL, spec.kind());
        assertEquals(1, spec.defaultValue());
        assertEquals(RuleSpec.Group.GAMEPLAY, spec.group());
        assertTrue(spec.raceRelevant());
        assertEquals(spec, WorldRuleSpecs.byName("drones_boss_interactions").orElseThrow());
        for (RulePreset preset : RulePreset.values()) {
            assertEquals(1, preset.value(spec), preset.id());
        }
    }

    @Test
    void worldRuleHasTextsInBothLanguages() throws IOException {
        RuleSpec spec = WorldRuleSpecs.DRONES_BOSS_INTERACTIONS;
        JsonObject de = lang("de_de");
        JsonObject en = lang("en_us");
        assertEquals("Bosse kämpfen gegen Drohnen", de.get(spec.translationKey()).getAsString());
        assertEquals("Bosses fight drones", en.get(spec.translationKey()).getAsString());
        for (JsonObject texts : List.of(de, en)) {
            String description = texts.get(spec.translationKey() + ".description").getAsString();
            assertFalse(description.isBlank());
        }
        assertTrue(de.get(spec.translationKey() + ".description").getAsString().contains("Wärter"));
        assertTrue(en.get(spec.translationKey() + ".description").getAsString().contains("warden"));
    }

    @Test
    void wardenGetsAngryAtADroneLikeAtAPlayer() {
        WardenGrudge grudge = new WardenGrudge();
        UUID drone = UUID.randomUUID();
        assertEquals(35, grudge.add(drone, WardenGrudge.HEAR_ANGER, 0));
        assertFalse(WardenGrudge.angry(grudge.anger(drone, 0)));
        assertEquals(68, grudge.add(drone, WardenGrudge.HEAR_ANGER, 40));
        assertTrue(grudge.target(40).isEmpty());
        assertEquals(101, grudge.add(drone, WardenGrudge.HEAR_ANGER, 80));
        assertEquals(drone, grudge.target(80).orElseThrow());
        assertEquals(101 - 25, grudge.anger(drone, 80 + 25 * WardenGrudge.DECAY_TICKS));
        assertTrue(grudge.target(80 + 25 * WardenGrudge.DECAY_TICKS).isEmpty());
        assertEquals(0, grudge.anger(drone, 80 + 200 * WardenGrudge.DECAY_TICKS));
        for (int i = 0; i < 10; i++) {
            grudge.add(drone, WardenGrudge.HEAR_ANGER, 5000 + i);
        }
        assertEquals(WardenGrudge.MAX_ANGER, grudge.anger(drone, 5009));
    }

    @Test
    void wardenPicksTheAngriestDroneAndForgetsGoneOnes() {
        WardenGrudge grudge = new WardenGrudge();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        grudge.add(a, 90, 0);
        grudge.add(b, 120, 0);
        assertEquals(b, grudge.target(0).orElseThrow());
        grudge.forget(b);
        assertEquals(a, grudge.target(0).orElseThrow());
        grudge.forget(a);
        assertTrue(grudge.empty());
    }

    @Test
    void sonicBoomFollowsTheVanillaTiming() {
        WardenGrudge grudge = new WardenGrudge();
        UUID drone = UUID.randomUUID();
        assertTrue(grudge.mayCharge(100));
        grudge.startCharge(drone, 100);
        assertTrue(grudge.charging());
        assertEquals(drone, grudge.chargeTarget());
        assertFalse(grudge.mayCharge(101));
        assertFalse(grudge.boomDue(100 + WardenGrudge.CHARGE_TICKS - 1));
        assertTrue(grudge.boomDue(100 + WardenGrudge.CHARGE_TICKS));
        grudge.boomed(134);
        assertFalse(grudge.charging());
        long ready = 134 + (WardenGrudge.BOOM_TICKS - WardenGrudge.CHARGE_TICKS) + WardenGrudge.COOLDOWN_TICKS;
        assertFalse(grudge.mayCharge(ready - 1));
        assertTrue(grudge.mayCharge(ready));
        assertTrue(WardenGrudge.inRange(14.9, 19.9, 0.0));
        assertFalse(WardenGrudge.inRange(15.1, 0.0, 0.0));
        assertFalse(WardenGrudge.inRange(0.0, 20.1, 0.0));
    }

    @Test
    void sonicBoomCrashesEveryBuiltInDroneAndBreaksParts() {
        for (DroneBuild build : BUILDS) {
            ImpactSample sample = BossHits.sample(BossHit.SONIC_BOOM, build, SIDE);
            assertTrue(CrashDamage.crash(build, sample), build.frame().name());
            assertTrue(BossHits.crash(BossHit.SONIC_BOOM, build));
            int grounded = 0;
            for (long seed = 1; seed <= 20; seed++) {
                PartWear after = BossHits.wear(PartWear.PRISTINE, build, BossHit.SONIC_BOOM, SIDE, 1.0F, seed);
                assertTrue(total(after) < total(PartWear.PRISTINE) - 0.5F, build.frame().name() + " Verschleiß " + total(after));
                assertTrue(after.worstMotor() < 1.0F && after.video() < 1.0F, build.frame().name());
                if (build.frame().ducted) {
                    assertTrue(after.worstProp() < 0.6F, build.frame().name() + " Propeller im Kanal " + after.worstProp());
                } else if (after.brokenProps() >= 3) {
                    grounded++;
                }
            }
            if (!build.frame().ducted) {
                assertTrue(grounded >= 15, build.frame().name() + ": nur " + grounded + " von 20 Treffern brechen drei Propeller");
            }
        }
    }

    @Test
    void hitsFollowTheCrashDamageRule() {
        for (BossHit hit : BossHit.values()) {
            assertEquals(PartWear.PRISTINE, BossHits.wear(PartWear.PRISTINE, DroneBuild.FREESTYLE, hit, SIDE, 0.0F, 7L), hit.name());
        }
        PartWear normal = BossHits.wear(PartWear.PRISTINE, DroneBuild.FREESTYLE, BossHit.DRAGON_RAM, SIDE, 1.0F, 7L);
        PartWear strong = BossHits.wear(PartWear.PRISTINE, DroneBuild.FREESTYLE, BossHit.DRAGON_RAM, SIDE, 3.0F, 7L);
        assertTrue(total(strong) < total(normal));
    }

    @Test
    void dragonRamCrashesButWingsOnlyGraze() {
        for (DroneBuild build : BUILDS) {
            assertTrue(CrashDamage.crash(build, BossHits.sample(BossHit.DRAGON_RAM, build, SIDE)), build.frame().name());
            assertFalse(CrashDamage.crash(build, BossHits.sample(BossHit.DRAGON_WING, build, SIDE)), build.frame().name());
            assertFalse(BossHits.crash(BossHit.DRAGON_WING, build));
            PartWear grazed = BossHits.wear(PartWear.PRISTINE, build, BossHit.DRAGON_WING, SIDE, 1.0F, 3L);
            assertEquals(0, grazed.brokenProps(), build.frame().name());
        }
    }

    @Test
    void dragonBreathEatsTheElectronicsWithinSecondsWithoutCrashing() {
        PartWear wear = PartWear.PRISTINE;
        int doses = 0;
        while (!PartWear.broken(wear.video()) && doses < 20) {
            wear = BossHits.wear(wear, DroneBuild.FREESTYLE, BossHit.DRAGON_BREATH, new Vec3(0.0, 1.0, 0.0), 1.0F, doses);
            doses++;
        }
        assertEquals(7, doses);
        assertTrue(PartWear.broken(wear.stack()));
        assertTrue(wear.battery() < 0.4F);
        assertTrue(wear.worstProp() > 0.6F);
        assertFalse(BossHits.crash(BossHit.DRAGON_BREATH, DroneBuild.FREESTYLE));
        PartWear doubled = BossHits.wear(PartWear.PRISTINE, DroneBuild.FREESTYLE, BossHit.DRAGON_BREATH, Vec3.ZERO, 2.0F, 0L);
        assertEquals(1.0F - 2.0F * BossHits.BREATH_ELECTRONICS, doubled.video(), 1.0E-6F);
    }

    @Test
    void pushesPointAwayAndStayWithinTheImpulseLimit() {
        Vec3 boom = BossHits.velocityChange(BossHit.SONIC_BOOM, new Vec3(3.0, 0.0, 4.0));
        assertEquals(0.6 * BossHit.SONIC_BOOM.push(), boom.x, 1.0E-9);
        assertEquals(BossHit.SONIC_BOOM.lift(), boom.y, 1.0E-9);
        assertEquals(0.8 * BossHit.SONIC_BOOM.push(), boom.z, 1.0E-9);
        Vec3 close = BossHits.wingVelocity(new Vec3(0.5, 3.0, 0.0));
        assertEquals(BossHits.WING_MAX, close.x, 1.0E-9);
        assertEquals(BossHit.DRAGON_WING.lift(), close.y, 1.0E-9);
        Vec3 far = BossHits.wingVelocity(new Vec3(0.0, -2.0, -10.0));
        assertEquals(-8.0, far.z, 1.0E-9);
        Vec3 ram = BossHits.ramVelocity(new Vec3(0.0, 0.0, 2.0), new Vec3(0.0, 0.0, 30.0));
        assertTrue(ram.z > 40.0);
        assertEquals(boom.length() * 0.6, BossHits.impulse(boom, 0.6).length(), 1.0E-9);
        Vec3 impulse = BossHits.impulse(boom, 3.0);
        assertEquals(Knockback.MAX_IMPULSE, impulse.length(), 1.0E-9);
        assertTrue(impulse.x > 0.0 && impulse.z > 0.0);
        Vec3 light = BossHits.impulse(new Vec3(0.0, 0.0, 2.0), 0.5);
        assertEquals(1.0, light.z, 1.0E-9);
    }

    @Test
    void dragonTurbulenceShakesNearbyDronesAndPushesDown() {
        double mass = 0.6;
        assertEquals(Vec3.ZERO, BossHits.turbulence(new Vec3(BossHits.TURBULENCE_RADIUS + 0.1, 0.0, 0.0), 20.0, mass, 0.5, 0.5, 0.5));
        Vec3 near = BossHits.turbulence(new Vec3(3.0, 0.0, 0.0), 20.0, mass, 0.5, 0.5, 0.5);
        Vec3 far = BossHits.turbulence(new Vec3(10.0, 0.0, 0.0), 20.0, mass, 0.5, 0.5, 0.5);
        Vec3 slow = BossHits.turbulence(new Vec3(3.0, 0.0, 0.0), 0.0, mass, 0.5, 0.5, 0.5);
        assertTrue(near.length() > far.length());
        assertTrue(near.length() > slow.length());
        assertTrue(near.x > 0.0 && near.y < 0.0);
        double weight = mass * 9.81;
        assertTrue(near.length() > 0.5 * weight && near.length() < 3.0 * weight, "Kraft " + near.length() + " N");
        for (double r = 0.0; r <= 1.0; r += 0.25) {
            Vec3 f = BossHits.turbulence(new Vec3(2.0, 1.0, -1.0), 40.0, mass, r, 1.0 - r, r);
            assertTrue(f.isFinite() && f.length() <= BossHits.TURBULENCE_GAIN * 1.5 * weight + 1.0E-9);
        }
    }

    @Test
    void breezeChargesAndBossBlastsReachThePilotWithoutAnAddon() {
        assertFalse(Knockback.forwards(false, false, true));
        assertTrue(Knockback.forwards(false, true, true));
        assertFalse(Knockback.forwards(false, true, false));
        assertTrue(Knockback.forwards(true, false, false));
        assertTrue(Knockback.forwards(true, false, true));
    }

    private static float total(PartWear wear) {
        float sum = 0.0F;
        for (float slot : wear.slots()) {
            sum += slot;
        }
        return sum;
    }

    private static JsonObject lang(String language) throws IOException {
        Path here = Path.of("").toAbsolutePath();
        Path root = Files.isDirectory(here.resolve("common/src/lang")) ? here : here.getParent();
        try (Reader reader = Files.newBufferedReader(root.resolve("common/src/lang/boss-interactions/" + language + ".json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }
}

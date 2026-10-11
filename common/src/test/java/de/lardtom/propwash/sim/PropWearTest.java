package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropWearTest {
    private static final int FRONT_TIPS = (1 << ImpactSample.HULL_TIP + 1) | (1 << ImpactSample.HULL_TIP + 3);
    private static final int BELLY = (1 << ImpactSample.HULL_BOTTOM) | (1 << ImpactSample.HULL_MOTOR) | (1 << ImpactSample.HULL_MOTOR + 1)
            | (1 << ImpactSample.HULL_MOTOR + 2) | (1 << ImpactSample.HULL_MOTOR + 3);

    enum Material {
        SOFT(PropMaterial.POLYCARBONATE, 0.85, 1.35, PropFailure.Mode.BENDS),
        PC(PropMaterial.POLYCARBONATE, 1.0, 1.0, PropFailure.Mode.BENDS),
        BI_BLEND(PropMaterial.POLYCARBONATE, 1.1, 1.15, PropFailure.Mode.BENDS_OR_CRACKS),
        HARD(PropMaterial.POLYCARBONATE, 1.12, 0.8, PropFailure.Mode.CRACKS),
        GLASS_NYLON(PropMaterial.GLASS_FIBER_NYLON, 1.2, 0.8, PropFailure.Mode.CRACKS),
        CARBON_NYLON(PropMaterial.CARBON_FIBER_NYLON, 1.4, 0.9, PropFailure.Mode.CRACKS),
        CARBON(PropMaterial.CARBON, 2.2, 1.4, PropFailure.Mode.SHATTERS);

        final PropMaterial material;
        final double stiffness;
        final double durability;
        final PropFailure.Mode mode;

        Material(PropMaterial material, double stiffness, double durability, PropFailure.Mode mode) {
            this.material = material;
            this.stiffness = stiffness;
            this.durability = durability;
            this.mode = mode;
        }

        DroneBuild on(DroneBuild build) {
            PropDefinition base = build.prop().definition();
            PropType prop = AddonParts.part(PropDefinition.builder(AddonParts.id("wear_" + this.name().toLowerCase(java.util.Locale.ROOT)))
                    .basics(base.basics()).durability(this.durability).size(base.diameterMm(), base.pitchInch(), base.blades())
                    .coefficients(base.thrustCoefficient(), base.powerCoefficient()).material(this.material).stiffness(this.stiffness).build());
            return build.withProp(prop);
        }
    }

    private static ImpactSample frontal(double speed, float spin) {
        return new ImpactSample((float) speed, 0.0F, 0.0F, 0.0F, -1.0F, FRONT_TIPS | (1 << ImpactSample.HULL_ELECTRONICS), spin > 0.0F ? 0b1010 : 0,
                spin);
    }

    private static ImpactSample random(SplittableRandom rng, double speed, boolean ducted) {
        ImpactSample hit = switch (rng.nextInt(4)) {
            case 0 -> new ImpactSample((float) speed, 0.0F, 0.0F, 0.0F, -1.0F, FRONT_TIPS | (1 << ImpactSample.HULL_ELECTRONICS), 0, 0.0F);
            case 1 -> new ImpactSample((float) speed, 0.0F, 0.0F, 1.0F, 0.0F, BELLY, 0, 0.0F);
            case 2 -> new ImpactSample((float) speed, (float) (speed * 0.5), -1.0F, 0.0F, 0.0F,
                    (1 << ImpactSample.HULL_TIP) | (1 << ImpactSample.HULL_TIP + 1), 0, 0.0F);
            default -> new ImpactSample((float) speed, 0.0F, 0.0F, -1.0F, 0.0F, 1 << ImpactSample.HULL_ELECTRONICS, 0, 0.0F);
        };
        int tips = hit.contacts() & 0xF;
        float spin = (float) (0.8 + rng.nextDouble());
        if (!ducted && tips != 0) {
            hit = new ImpactSample(hit.speed(), hit.tangential(), hit.normalX(), hit.normalY(), hit.normalZ(), hit.contacts(), tips, spin);
        }
        return hit;
    }

    static double[] lifetime(DroneBuild build, int sessions) {
        double toCritical = 0.0;
        double toBroken = 0.0;
        for (long seed = 0; seed < sessions; seed++) {
            SplittableRandom rng = new SplittableRandom(seed);
            PartWear wear = PartWear.PRISTINE;
            int critical = -1;
            int broken = -1;
            for (int crash = 1; crash <= 400 && broken < 0; crash++) {
                double speed = build.frame().impactTolerance * (0.8 + 0.8 * rng.nextDouble() * rng.nextDouble());
                wear = CrashDamage.apply(wear, build, random(rng, speed, build.frame().ducted), 1.0, rng.nextLong());
                float prop = wear.prop(PartWear.FRONT_LEFT);
                if (critical < 0 && PartWear.critical(prop)) {
                    critical = crash;
                }
                if (PartWear.broken(prop)) {
                    broken = crash;
                }
            }
            toCritical += (critical < 0 ? 400 : critical) / (double) sessions;
            toBroken += (broken < 0 ? 400 : broken) / (double) sessions;
        }
        return new double[]{toCritical, toBroken};
    }

    static BoxWorld wallRun(DroneBuild build, double speed) {
        BoxWorld w = BoxWorld.of(build, new SimTuning());
        w.ground(0.0);
        w.box(1.5, 0.0, -20.0, 3.0, 30.0, 20.0);
        w.placeBottom(0.0, 3.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        double dt = 1.0 / 144.0;
        for (int i = 0; i < 20; i++) {
            w.frame(dt);
        }
        double hover = w.sim.telemetry().hoverThrottleStick;
        w.input.throttle = Double.isFinite(hover) ? hover : 0.3;
        w.launch(speed, 0.0, 0.0);
        return w;
    }

    static PartWear wallCrash(DroneBuild build, double speed, long seed) {
        BoxWorld w = wallRun(build, speed);
        PartWear wear = PartWear.PRISTINE;
        long salt = seed;
        double dt = 1.0 / 144.0;
        for (int i = 0; i < 216; i++) {
            w.frame(dt);
            if (w.sim.telemetry().crashCount > 0) {
                w.input.throttle = 0.0;
            }
            ImpactLog log = w.sim.impacts();
            for (int k = 0; k < log.size(); k++) {
                ImpactSample s = log.get(k);
                if (CrashDamage.harmful(build, s)) {
                    salt = salt * 6364136223846793005L + 1442695040888963407L;
                    wear = CrashDamage.apply(wear, build, s, 1.0, salt);
                }
            }
        }
        return wear;
    }

    @Test
    void stiffnessAndToughnessDecideHowAPropFails() {
        for (Material m : Material.values()) {
            DroneBuild build = m.on(DroneBuild.FREESTYLE);
            assertEquals(m.mode, PropFailure.of(build.prop().definition()).mode(), m.name());
        }
        for (PropType prop : PropType.values()) {
            if (prop.builtin()) {
                assertEquals(PropFailure.Mode.BENDS, PropFailure.of(prop.definition()).mode(), prop.key());
            }
        }
        PropFailure carbon = PropFailure.of(2.2, 1.4);
        assertEquals(0.0, carbon.bendShare(), 1.0E-12);
        assertEquals(1.0, carbon.shatterShare(), 1.0E-12);
        assertSame(PropFailure.DUCTILE, PropFailure.of(Double.NaN, 1.0));
    }

    @Test
    void lightBumpsAndGrazingStrikesLeaveThePropsAlone() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.LONG_RANGE, DroneBuild.RACER, DroneBuild.WHOOP, DroneBuild.CINEWHOOP}) {
            double tolerance = build.frame().impactTolerance;
            for (double share : new double[]{0.15, 0.25, 0.4, 0.55, 0.7}) {
                PartWear wear = wallCrash(build, share * tolerance, 3L);
                for (int p = 0; p < PartWear.POSITIONS; p++) {
                    String what = build.frame().key() + " " + share + " × Crash-Toleranz, Prop " + p + ": " + wear;
                    if (share <= 0.4) {
                        assertEquals(PartWear.NEW, wear.prop(p), what);
                    } else {
                        assertTrue(wear.prop(p) >= 0.99F, what);
                    }
                }
            }
        }
        for (Material m : Material.values()) {
            DroneBuild build = m.on(DroneBuild.FREESTYLE);
            for (float spin : new float[]{0.5F, 1.0F, 1.5F}) {
                PartWear wear = CrashDamage.apply(PartWear.PRISTINE, build, frontal(2.5, spin), 1.0, 9L);
                for (int p = 0; p < PartWear.POSITIONS; p++) {
                    assertEquals(PartWear.NEW, wear.prop(p), m + " Spin " + spin);
                }
            }
        }
    }

    @Test
    void hardImpactsStillBreakProps() {
        int broken = 0;
        int seeds = 20;
        for (long seed = 0; seed < seeds; seed++) {
            PartWear wear = wallCrash(DroneBuild.FREESTYLE, 22.0, seed);
            int count = 0;
            for (int p = 0; p < PartWear.POSITIONS; p++) {
                if (PartWear.broken(wear.prop(p))) {
                    count++;
                }
            }
            if (count >= 2) {
                broken++;
            }
        }
        assertTrue(broken >= seeds * 0.8, "22 m/s in die Wand, zwei Props auf der Wandseite kaputt: " + broken + " von " + seeds);
        PartWear crash = wallCrash(DroneBuild.FREESTYLE, 12.0, 4L);
        float worst = 1.0F;
        float best = 0.0F;
        for (int p = 0; p < PartWear.POSITIONS; p++) {
            worst = Math.min(worst, crash.prop(p));
            best = Math.max(best, crash.prop(p));
        }
        assertTrue(PartWear.damaged(worst), "12 m/s: " + crash);
        assertTrue(best - worst > 0.1F, "Wandseite stärker als die andere Seite: " + crash);
    }

    @Test
    void softPcBendsGraduallyInsteadOfBreaking() {
        DroneBuild build = Material.SOFT.on(DroneBuild.FREESTYLE);
        double previous = 1.0;
        for (double speed = 9.0; speed <= 17.0; speed += 2.0) {
            double mean = 0.0;
            for (long seed = 0; seed < 40; seed++) {
                PartWear wear = CrashDamage.apply(PartWear.PRISTINE, build, frontal(speed, 1.2F), 1.0, seed);
                float prop = wear.prop(PartWear.FRONT_LEFT);
                assertTrue(prop > 0.0F, "weiches PC bricht bei " + speed + " m/s");
                mean += prop / 40.0;
            }
            assertTrue(mean < previous, speed + " m/s: " + mean);
            previous = mean;
        }
        assertTrue(previous < 0.6, "bei 17 m/s stark verbogen: " + previous);
        assertTrue(WearEffects.propThrust(0.3F, 0.0) < WearEffects.propThrust(0.3F, 1.0), "verbogen kostet mehr Schub");
    }

    @Test
    void glassNylonCracksInStepsAndPcDoesNot() {
        DroneBuild glass = Material.GLASS_NYLON.on(DroneBuild.FREESTYLE);
        DroneBuild pc = Material.PC.on(DroneBuild.FREESTYLE);
        int glassJumps = 0;
        int pcJumps = 0;
        for (long seed = 0; seed < 200; seed++) {
            ImpactSample hit = frontal(10.0, 1.0F);
            float g = CrashDamage.apply(PartWear.PRISTINE, glass, hit, 1.0, seed).prop(PartWear.FRONT_LEFT);
            float p = CrashDamage.apply(PartWear.PRISTINE, pc, hit, 1.0, seed).prop(PartWear.FRONT_LEFT);
            if (1.0F - g >= CrashDamage.CRACK_MIN) {
                glassJumps++;
            }
            if (1.0F - p >= CrashDamage.CRACK_MIN) {
                pcJumps++;
            }
        }
        assertTrue(glassJumps >= 40, "Risse bei Glasfaser-Nylon: " + glassJumps);
        assertTrue(pcJumps <= 20, "Risse bei PC: " + pcJumps);
        assertTrue(WearEffects.propVibration(0.5F, 1.0) > WearEffects.propVibration(0.5F, 0.0), "angebrochen vibriert stärker");
    }

    @Test
    void carbonShattersRarelyButCompletely() {
        DroneBuild carbon = Material.CARBON.on(DroneBuild.FREESTYLE);
        int shattered = 0;
        for (long seed = 0; seed < 200; seed++) {
            float prop = CrashDamage.apply(PartWear.PRISTINE, carbon, frontal(8.0, 1.0F), 1.0, seed).prop(PartWear.FRONT_LEFT);
            if (PartWear.broken(prop)) {
                shattered++;
            } else {
                assertTrue(prop > 0.95F, "Carbon ohne Bruch fast unbeschädigt: " + prop);
            }
        }
        assertTrue(shattered <= 10, "Carbon zerspringt bei 8 m/s: " + shattered + " von 200");
        for (long seed = 0; seed < 50; seed++) {
            PartWear wear = CrashDamage.apply(PartWear.PRISTINE, carbon, frontal(24.0, 1.0F), 1.0, seed);
            assertTrue(PartWear.broken(wear.prop(PartWear.FRONT_LEFT)), "Carbon bei 24 m/s: " + wear);
        }
        PartWear soft = CrashDamage.apply(PartWear.PRISTINE, Material.PC.on(DroneBuild.FREESTYLE), frontal(10.0, 1.0F), 1.0, 2L);
        PartWear stiff = CrashDamage.apply(PartWear.PRISTINE, carbon, frontal(10.0, 1.0F), 1.0, 2L);
        assertTrue(stiff.motor(PartWear.FRONT_LEFT) < soft.motor(PartWear.FRONT_LEFT), "Carbon gibt den Stoß an den Motor weiter");
    }

    @Test
    void lifetimesFollowTheMaterials() {
        int sessions = 120;
        double[] soft = lifetime(Material.SOFT.on(DroneBuild.FREESTYLE), sessions);
        double[] pc = lifetime(Material.PC.on(DroneBuild.FREESTYLE), sessions);
        double[] builtin = lifetime(DroneBuild.FREESTYLE, sessions);
        double[] bi = lifetime(Material.BI_BLEND.on(DroneBuild.FREESTYLE), sessions);
        double[] hard = lifetime(Material.HARD.on(DroneBuild.FREESTYLE), sessions);
        double[] glass = lifetime(Material.GLASS_NYLON.on(DroneBuild.FREESTYLE), sessions);
        double[] carbon = lifetime(Material.CARBON.on(DroneBuild.FREESTYLE), sessions);
        assertEquals(pc[0], builtin[0], 1.0E-9);
        assertTrue(pc[0] > 12.0 && pc[0] < 25.0, "PC kritisch nach " + pc[0] + " Abstürzen");
        assertTrue(soft[0] > bi[0] && bi[0] > pc[0] && pc[0] > hard[0] && pc[0] > glass[0], "kritisch: weich " + soft[0] + ", Bi-Blend " + bi[0]
                + ", PC " + pc[0] + ", hart " + hard[0] + ", Glasfaser " + glass[0]);
        assertTrue(carbon[1] > 2.0 * pc[1], "Carbon bricht nach " + carbon[1] + ", PC nach " + pc[1]);
        assertEquals(carbon[0], carbon[1], 0.5, "Carbon: kritisch und kaputt fallen zusammen");
        double[] whoop = lifetime(DroneBuild.WHOOP, 40);
        assertTrue(whoop[0] > 5.0 * pc[0], "Ducts schützen: " + whoop[0]);
    }

    @Test
    void strikesScaleWithSpinAndApproach() {
        assertEquals(0.0, CrashDamage.strikeLoad(0.0, 1.0), 1.0E-12);
        assertTrue(CrashDamage.strikeLoad(2.0, 0.5) > 3.9 * CrashDamage.strikeLoad(1.0, 0.5));
        assertEquals(CrashDamage.strikeLoad(CrashDamage.MAX_STRIKE_SPIN, 1.0), CrashDamage.strikeLoad(9.0, 1.0), 1.0E-12);
        assertTrue(CrashDamage.strikeLoad(1.0, 1.5) > CrashDamage.strikeLoad(1.0, 0.2));
        assertTrue(CrashDamage.strikeLoad(1.0, 0.25) < CrashDamage.ELASTIC_LOAD, "Streifschlag im Schweben bleibt elastisch");
        DroneBuild build = DroneBuild.FREESTYLE;
        ImpactSample punch = new ImpactSample(3.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0, 1 << 3, 2.0F);
        PartWear wear = CrashDamage.apply(PartWear.PRISTINE, build, punch, 1.0, 5L);
        int position = PartWear.positionOfSimMotor(3);
        assertTrue(wear.prop(position) < PartWear.NEW, "Vollgas-Strike: " + wear);
        for (int p = 0; p < PartWear.POSITIONS; p++) {
            if (p != position) {
                assertEquals(PartWear.NEW, wear.prop(p));
            }
        }
        assertSame(PartWear.PRISTINE, CrashDamage.apply(PartWear.PRISTINE, build, punch, 0.0, 5L));
    }
}

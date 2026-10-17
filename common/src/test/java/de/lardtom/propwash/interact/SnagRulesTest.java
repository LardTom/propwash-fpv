package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.interact.net.DroneBellC2S;
import de.lardtom.propwash.interact.net.SnagWearC2S;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.SnagField;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnagRulesTest {
    @Test
    void segmentFractionClipsAgainstTheBox() {
        assertEquals(0.25, SnagMath.segmentFraction(0, 0.5, 0.5, 4, 0.5, 0.5, 1, 0, 0, 2, 1, 1), 1.0E-12);
        assertEquals(1.0, SnagMath.segmentFraction(0.2, 0.2, 0.2, 0.8, 0.8, 0.8, 0, 0, 0, 1, 1, 1), 1.0E-12);
        assertEquals(0.0, SnagMath.segmentFraction(0, 2, 0, 4, 2, 0, 1, 0, 0, 2, 1, 1), 0.0);
        assertEquals(0.0, SnagMath.segmentFraction(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1, 0, 0, 2, 1, 1), 0.0);
        assertEquals(0.5, SnagMath.segmentFraction(2, 0.5, 0.5, 0, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1.0E-12);
    }

    @Test
    void overlappingSpansCountOnce() {
        assertEquals(0.5, SnagMath.unionLength(new double[]{0.3, 0.0, 0.2}, new double[]{0.5, 0.25, 0.4}, 3), 1.0E-12);
        assertEquals(0.3, SnagMath.unionLength(new double[]{0.0, 0.7}, new double[]{0.1, 0.9}, 2), 1.0E-12);
        assertEquals(0.0, SnagMath.unionLength(new double[0], new double[0], 0), 0.0);
    }

    @Test
    void webHoldsHardestFoliageBrakesLightest() {
        assertTrue(SnagMaterial.WEB.hold() > 0.0);
        assertEquals(0.0, SnagMaterial.STRING.hold());
        assertEquals(0.0, SnagMaterial.FOLIAGE.hold());
        assertTrue(SnagMaterial.WEB.linear() > SnagMaterial.STRING.linear());
        assertTrue(SnagMaterial.STRING.linear() > SnagMaterial.FOLIAGE.linear());
        assertTrue(SnagMaterial.STRING.propLoad() > SnagMaterial.WEB.propLoad());
        assertTrue(SnagMaterial.WEB.propLoad() > SnagMaterial.FOLIAGE.propLoad());
        assertTrue(SnagMaterial.FOLIAGE.propWear() < SnagMaterial.WEB.propWear());
        assertEquals(SnagMaterial.STRING.propWear(), SnagMaterial.maxPropWear());
    }

    @Test
    void fullImmersionGivesTheMaterialValues() {
        double[][] exposure = new double[SnagField.POINTS][SnagMaterial.COUNT];
        for (double[] row : exposure) {
            row[SnagMaterial.WEB.ordinal()] = 1.0;
        }
        SnagField field = new SnagField();
        SnagMath.fill(exposure, field);
        double linear = 0.0;
        double quadratic = 0.0;
        for (int i = 0; i < SnagField.POINTS; i++) {
            linear += field.linear[i];
            quadratic += field.quadratic[i];
        }
        assertEquals(SnagMaterial.WEB.linear(), linear, 1.0E-12);
        assertEquals(SnagMaterial.WEB.quadratic(), quadratic, 1.0E-12);
        assertEquals(SnagMaterial.WEB.hold(), field.hold, 1.0E-12);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            assertEquals(SnagMaterial.WEB.propLoad(), field.propLoad[i], 1.0E-12);
        }
        assertTrue(field.active());
        SnagMath.fill(new double[SnagField.POINTS][SnagMaterial.COUNT], field);
        assertFalse(field.active());
    }

    @Test
    void wearFollowsExposureRpmAndTime() {
        double[][] exposure = new double[SnagField.POINTS][SnagMaterial.COUNT];
        exposure[0][SnagMaterial.FOLIAGE.ordinal()] = 1.0;
        exposure[2][SnagMaterial.WEB.ordinal()] = 0.5;
        double[] wear = new double[AirframeSpec.MOTORS];
        SnagMath.addWear(exposure, new double[]{1.0, 1.0, 0.5, 1.0}, 2.0, wear);
        assertEquals(2.0 * SnagMaterial.FOLIAGE.propWear(), wear[0], 1.0E-12);
        assertEquals(0.0, wear[1]);
        assertEquals(0.5 * 0.5 * 2.0 * SnagMaterial.WEB.propWear(), wear[2], 1.0E-12);
        SnagMath.addWear(exposure, new double[]{1.0, 1.0, 1.0, 1.0}, -1.0, wear);
        assertEquals(2.0 * SnagMaterial.FOLIAGE.propWear(), wear[0], 1.0E-12);
        assertTrue(SnagMath.wearAllowance(1.0) >= SnagMaterial.maxPropWear());
        assertTrue(SnagMath.wearAllowance(1000.0) <= SnagMath.MAX_WEAR_PER_PACKET);
    }

    @Test
    void serverCapsTheReportedWearAndScalesItWithTheRule() {
        float[] reported = {0.5F, 0.01F, 0.0F, Float.NaN};
        PartWear after = SnagWear.apply(PartWear.PRISTINE, reported, 0.05F, 1.0F);
        assertEquals(PartWear.PRISTINE.prop(0) - 0.05F, after.prop(0), 1.0E-6);
        assertEquals(PartWear.PRISTINE.prop(1) - 0.01F, after.prop(1), 1.0E-6);
        assertEquals(PartWear.PRISTINE.prop(2), after.prop(2));
        assertEquals(PartWear.PRISTINE.prop(3), after.prop(3));
        PartWear half = SnagWear.apply(PartWear.PRISTINE, reported, 0.05F, 0.5F);
        assertEquals(PartWear.PRISTINE.prop(0) - 0.025F, half.prop(0), 1.0E-6);
        assertEquals(PartWear.PRISTINE, SnagWear.apply(PartWear.PRISTINE, reported, 0.05F, 0.0F));
        assertEquals(PartWear.PRISTINE.motor(0), after.motor(0));
    }

    @Test
    void sampledWearLandsOnTheMatchingPropPosition() {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 1L);
        sim.reset(0.0, 10.0, 0.0, 0.0);
        AirframeSpec p = sim.params();
        int motor = 1;
        double mx = p.motorX(motor);
        double mz = p.motorZ(motor);
        SnagSampler sampler = new SnagSampler();
        SnagSampler.World tuft = (x, y, z, s) -> s.box(SnagMaterial.FOLIAGE, mx - 0.01, 9.0, mz - 0.01, mx + 0.01, 11.0, mz + 0.01);
        SnagField field = new SnagField();
        assertTrue(sampler.sample(sim, tuft, field));
        assertTrue(field.propLoad[motor] > 0.0);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (i != motor) {
                assertEquals(0.0, field.propLoad[i], "nur der eine Prop: " + i);
            }
        }
        SimSnapshot s = new SimSnapshot();
        sim.copyStateTo(s);
        s.armed = true;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            s.rotorOmega[i] = p.omegaMax;
        }
        sim.copyStateFrom(s);
        SnagWear wear = new SnagWear();
        sim.telemetry().armed = true;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            sim.telemetry().motorRpm[i] = p.omegaMax * 60.0 / (2.0 * Math.PI);
        }
        wear.add(sampler, sim.telemetry(), p.omegaMax, 1.0);
        float[] positions = wear.drain();
        int position = PartWear.positionOfSimMotor(motor);
        for (int i = 0; i < PartWear.POSITIONS; i++) {
            if (i == position) {
                assertEquals(SnagMaterial.FOLIAGE.propWear(), positions[i], 1.0E-6);
            } else {
                assertEquals(0.0F, positions[i]);
            }
        }
        assertFalse(wear.any());
    }

    @Test
    void aThinSheetBetweenTwoSamplesIsStillFelt() {
        DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 1L);
        sim.reset(0.0, 10.0, 0.0, 0.0);
        SnagSampler sampler = new SnagSampler();
        SnagSampler.World sheet = (x, y, z, s) -> {
            if (x == 1) {
                s.box(SnagMaterial.FOLIAGE, 1.0, y, z, 1.0625, y + 1.0, z + 1.0);
            }
        };
        SnagField field = new SnagField();
        assertFalse(sampler.sample(sim, sheet, field));
        SimSnapshot s = new SimSnapshot();
        sim.copyStateTo(s);
        s.px = 2.0;
        sim.copyStateFrom(s);
        assertTrue(sampler.sample(sim, sheet, field), "die Mitte ist durch die Ebene geflogen");
        double e = sampler.exposure(SnagField.CENTER, SnagMaterial.FOLIAGE);
        assertTrue(e > 0.0 && e < 0.2, "nur der Anteil der Strecke in der Ebene: " + e);
        s.px = 3.0;
        sim.copyStateFrom(s);
        assertFalse(sampler.sample(sim, sheet, field));
        s.px = -40.0;
        sim.copyStateFrom(s);
        assertFalse(sampler.sample(sim, sheet, field), "ein Sprung zählt nicht als Durchflug");
    }

    @Test
    void bellRulesNeedSpeedCooldownBudgetAndReach() {
        assertEquals(BellRules.Decision.RING, BellRules.decide(3.0, true, true, 0.1, 3.0));
        assertEquals(BellRules.Decision.TOO_SLOW, BellRules.decide(0.5, true, true, 0.1, 0.5));
        assertEquals(BellRules.Decision.TOO_SLOW, BellRules.decide(Double.NaN, true, true, 0.1, 3.0));
        assertEquals(BellRules.Decision.COOLDOWN, BellRules.decide(3.0, false, true, 0.1, 3.0));
        assertEquals(BellRules.Decision.BUDGET, BellRules.decide(3.0, true, false, 0.1, 3.0));
        assertEquals(BellRules.Decision.OUT_OF_REACH, BellRules.decide(3.0, true, true, 20.0, 3.0));
        assertTrue(BellRules.cooledDown(Long.MIN_VALUE, 100L));
        assertFalse(BellRules.cooledDown(98L, 100L));
        assertTrue(BellRules.cooledDown(100L - BellRules.COOLDOWN_TICKS, 100L));
        assertEquals(1.0, BellRules.clampInto(5.0, 0.0, 1.0));
        assertEquals(0.5, BellRules.clampInto(Double.NaN, 0.0, 1.0));
    }

    @Test
    void payloadsRoundTrip() {
        for (Direction face : Direction.values()) {
            DroneBellC2S bell = new DroneBellC2S(7, 2, new BlockPos(-29_999_999, 300, 12), face, 1.5F, 64.25F, -3.0F, 9.5F);
            ByteBuf buf = Unpooled.buffer();
            DroneBellC2S.CODEC.encode(buf, bell);
            assertEquals(bell, DroneBellC2S.CODEC.decode(buf));
        }
        ByteBuf bad = Unpooled.buffer();
        DroneBellC2S.CODEC.encode(bad, new DroneBellC2S(1, 0, BlockPos.ZERO, Direction.UP, 0.0F, 0.0F, 0.0F, 1.0F));
        bad.setByte(bad.writerIndex() - 17, 9);
        assertThrows(IllegalArgumentException.class, () -> DroneBellC2S.CODEC.decode(bad));
        SnagWearC2S wear = new SnagWearC2S(9, 4, new float[]{0.01F, Float.NaN, 2.0F, -1.0F});
        assertArrayEquals(new float[]{0.01F, 0.0F, 1.0F, 0.0F}, wear.propWear());
        ByteBuf wb = Unpooled.buffer();
        SnagWearC2S.CODEC.encode(wb, wear);
        SnagWearC2S back = SnagWearC2S.CODEC.decode(wb);
        assertEquals(9, back.droneId());
        assertEquals(4, back.epoch());
        assertArrayEquals(wear.propWear(), back.propWear());
        assertTrue(new SnagWearC2S(1, 0, new float[4]).empty());
    }
}

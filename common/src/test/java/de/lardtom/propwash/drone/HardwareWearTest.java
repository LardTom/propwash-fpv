package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.net.HardwareWearC2S;
import de.lardtom.propwash.sim.AirframeSpec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardwareWearTest {
    private static DroneBuild overvolted() {
        FrameDefinition base = FrameType.FREESTYLE_5.definition();
        FrameDefinition.Builder b = FrameDefinition.builder(Identifier.fromNamespaceAndPath("propwash_weartest", "wide_fit"))
                .basics(base.basics()).role(base.role()).wheelbase(base.wheelbaseMm()).maxProp(base.maxPropMm())
                .aero(base.aero().frontalAreaM2(), base.aero().dragCoefficient()).batteryFit(new BatteryFitRule(4, 8, 400.0, 6, 0));
        base.motorMounts().forEach(b::motorMount);
        base.stackMounts().forEach(b::stackMount);
        FrameType frame = (FrameType) PartIndex.detached(b.build());
        BatteryType battery = (BatteryType) PartIndex.detached(BatteryDefinition.builder(Identifier.fromNamespaceAndPath("propwash_weartest", "b8s"))
                .mass(280.0).cells(8).capacity(1300).cellResistance(0.0055).cRating(120).build());
        DroneBuild fs = DroneBuild.FREESTYLE;
        return new DroneBuild(frame, fs.stack(), fs.motor(), fs.prop(), fs.video(), battery);
    }

    @Test
    void burntEscOnlyCountsWhenTheBuildReallyOvervolts() {
        DroneBuild hot = overvolted();
        assertTrue(AirframeSpec.of(hot).escOvervoltage);
        PartWear burnt = DroneWear.applyHardware(PartWear.PRISTINE, hot, true, new float[4], 0.0F);
        assertEquals(0.0F, burnt.stack());
        assertTrue(PartWear.broken(burnt.stack()));
        assertSame(PartWear.PRISTINE, DroneWear.applyHardware(PartWear.PRISTINE, DroneBuild.FREESTYLE, true, new float[4], 0.0F));
    }

    @Test
    void heatWearIsCappedByTheAllowance() {
        DroneBuild fs = DroneBuild.FREESTYLE;
        float allowance = DroneWear.heatWearAllowance(fs, 1.0);
        assertEquals(AirframeSpec.of(fs).motorHeatWearPerSecond * DroneWear.HEAT_WEAR_SLACK, allowance, 1e-7);
        PartWear worn = DroneWear.applyHardware(PartWear.PRISTINE, fs, false, new float[]{0.002F, 0.5F, 0.0F, Float.NaN}, allowance);
        assertEquals(0.998F, worn.motor(0), 1e-6);
        assertEquals(1.0F - PartWear.quantize(allowance), worn.motor(1), 0.0011F);
        assertEquals(1.0F, worn.motor(2));
        assertEquals(1.0F, worn.motor(3));
        assertEquals(DroneWear.MAX_HEAT_WEAR_PER_PACKET, DroneWear.heatWearAllowance(fs, 1e6), 1e-7);
    }

    @Test
    void payloadRoundTripsAndClampsValues() {
        HardwareWearC2S payload = new HardwareWearC2S(7, 3, true, new float[]{0.1F, -1.0F, Float.POSITIVE_INFINITY, 2.0F});
        assertArrayEquals(new float[]{0.1F, 0.0F, 0.0F, 1.0F}, payload.motorHeatWear());
        ByteBuf buf = Unpooled.buffer();
        HardwareWearC2S.CODEC.encode(buf, payload);
        HardwareWearC2S read = HardwareWearC2S.CODEC.decode(buf);
        assertEquals(7, read.entityId());
        assertEquals(3, read.epoch());
        assertTrue(read.escBurned());
        assertArrayEquals(payload.motorHeatWear(), read.motorHeatWear());
        assertTrue(new HardwareWearC2S(1, 1, false, new float[4]).empty());
        assertFalse(new HardwareWearC2S(1, 1, false, new float[]{0.0F, 0.001F, 0.0F, 0.0F}).empty());
    }
}

package de.lardtom.propwash.osd;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.sim.ArmBlocker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdWearTest {
    private static List<OsdWarnings.Warning> warnings(PartWear wear) {
        List<OsdWarnings.Warning> out = new ArrayList<>();
        OsdWarnings.wear(wear, out);
        return out;
    }

    @Test
    void newPartsShowNoWarning() {
        assertTrue(warnings(PartWear.PRISTINE).isEmpty());
        assertTrue(warnings(PartWear.PRISTINE.withProp(0, 0.8F)).isEmpty());
    }

    @Test
    void damageShowsTheMatchingWarning() {
        assertEquals(List.of(OsdWarnings.Warning.PROP_DAMAGE), warnings(PartWear.PRISTINE.withProp(1, 0.3F)));
        assertEquals(List.of(OsdWarnings.Warning.PROP_BROKEN), warnings(PartWear.PRISTINE.withProp(1, 0.3F).withProp(2, 0.0F)));
        assertTrue(warnings(PartWear.PRISTINE.withArm(3, 0.0F)).contains(OsdWarnings.Warning.ARM_BROKEN));
        assertTrue(warnings(PartWear.PRISTINE.withStack(0.0F)).contains(OsdWarnings.Warning.FC_DAMAGE));
        assertTrue(warnings(PartWear.PRISTINE.withMotor(0, 0.2F)).contains(OsdWarnings.Warning.MOTOR_DAMAGE));
        assertTrue(warnings(PartWear.PRISTINE.withVideo(0.2F)).contains(OsdWarnings.Warning.CAMERA_DAMAGE));
        assertTrue(warnings(PartWear.PRISTINE.withBattery(0.2F)).contains(OsdWarnings.Warning.BATTERY_PUFFED));
        assertEquals(OsdWarnings.Warning.ARM_HARDWARE, OsdWarnings.Warning.forBlocker(ArmBlocker.HARDWARE_FAULT));
    }

    @Test
    void activeDataCollectsWearWarnings() {
        OsdData data = new OsdData();
        data.active = true;
        data.radioConnected = true;
        data.linkStrength = 1.0F;
        data.wear = PartWear.PRISTINE.withProp(0, 0.0F);
        List<OsdWarnings.Warning> out = new ArrayList<>();
        OsdWarnings.collect(data, out);
        assertTrue(out.contains(OsdWarnings.Warning.PROP_BROKEN));
        OsdData copy = new OsdData();
        data.copyTo(copy);
        assertEquals(data.wear, copy.wear);
    }

    @Test
    void conditionKeyNamesTheWorstPart() {
        assertEquals(OsdCondition.INTACT, OsdCondition.key(PartWear.PRISTINE));
        long key = OsdCondition.key(PartWear.PRISTINE.withMotor(2, 0.45F).withProp(0, 0.8F));
        assertEquals(OsdCondition.Part.MOTORS, OsdCondition.part(key));
        assertEquals(45, OsdCondition.percent(key));
        assertEquals(OsdCondition.Part.PROPS, OsdCondition.part(OsdCondition.key(PartWear.PRISTINE.withProp(3, 0.0F))));
        assertEquals(0, OsdCondition.percent(OsdCondition.key(PartWear.PRISTINE.withProp(3, 0.0F))));
    }

    @Test
    void crashesCountOnlyDuringTheFlight() {
        FlightStats stats = new FlightStats();
        OsdData data = new OsdData();
        data.crashCount = 4;
        stats.trackCrashes(data);
        assertEquals(0, stats.crashes);
        data.armed = true;
        stats.accumulate(data, 0.1);
        data.crashCount = 6;
        stats.trackCrashes(data);
        assertEquals(2, stats.crashes);
        stats.reset();
        assertEquals(0, stats.crashes);
        assertFalse(stats.crashes > 0);
    }
}

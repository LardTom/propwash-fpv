package de.lardtom.propwash.build;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoriesTest {
    @Test
    void oneAccessoryPerMount() {
        Accessories both = Accessories.of(AccessoryType.ACTION_CAM_LIGHT, AccessoryType.LED_STRIP);
        assertEquals(2, both.size());
        Accessories swapped = both.with(AccessoryType.ACTION_CAM_HEAVY);
        assertEquals(AccessoryType.ACTION_CAM_HEAVY, swapped.at(AccessoryMount.TOP).orElseThrow());
        assertEquals(AccessoryType.LED_STRIP, swapped.at(AccessoryMount.BOTTOM).orElseThrow());
        assertEquals(Accessories.of(AccessoryType.LED_STRIP), swapped.without(AccessoryMount.TOP));
        assertTrue(Accessories.NONE.isEmpty());
        assertEquals(Accessories.NONE, new Accessories(Map.of()));
        assertEquals(AccessoryType.ACTION_CAM_HEAVY, swapped.camera().orElseThrow());
        assertTrue(Accessories.of(AccessoryType.LED_STRIP).camera().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Accessories(Map.of(AccessoryMount.BOTTOM, AccessoryType.ACTION_CAM_HEAVY)));
    }

    @Test
    void accessoriesWeighInAndLeavePresetsAlone() {
        DroneBuild base = DroneBuild.FREESTYLE;
        DroneBuild heavy = base.withAccessory(AccessoryType.ACTION_CAM_HEAVY);
        assertEquals(base.totalMassGrams() + AccessoryType.ACTION_CAM_HEAVY.massGrams(), heavy.totalMassGrams(), 1.0E-9);
        assertNotEquals(base, heavy);
        assertEquals(base, heavy.withoutAccessories());
        assertEquals(heavy, heavy.withBattery(BatteryType.B6S_1100).withBattery(base.battery()));
        assertEquals(Accessories.NONE, new DroneBuild(base.frame(), base.stack(), base.motor(), base.prop(), base.video(), base.battery(),
                null).accessories());
        for (DroneBuild preset : DroneBuild.PRESETS) {
            assertTrue(preset.accessories().isEmpty(), preset.toString());
        }
    }

    @Test
    void camerasFollowRealDevices() {
        ActionCamSpec light = AccessoryType.ACTION_CAM_LIGHT.camera().orElseThrow();
        ActionCamSpec heavy = AccessoryType.ACTION_CAM_HEAVY.camera().orElseThrow();
        assertTrue(AccessoryType.ACTION_CAM_LIGHT.massGrams() >= 30.0 && AccessoryType.ACTION_CAM_LIGHT.massGrams() <= 55.0);
        assertTrue(AccessoryType.ACTION_CAM_HEAVY.massGrams() >= 120.0 && AccessoryType.ACTION_CAM_HEAVY.massGrams() <= 155.0);
        assertFalse(light.ownBattery());
        assertTrue(AccessoryType.ACTION_CAM_LIGHT.flightBatteryWatts > 0.0);
        assertEquals(light.recordWatts(ActionCamSpec.FULL_HD), AccessoryType.ACTION_CAM_LIGHT.flightBatteryWatts, 1.0E-9);
        assertFalse(light.stabilization());
        assertEquals(ActionCamSpec.FULL_HD, light.maxHeight());
        assertEquals(60, light.maxFps());
        assertTrue(light.lowLightNoise() > heavy.lowLightNoise());
        assertTrue(light.imageQuality() < heavy.imageQuality());
        assertTrue(light.sensorInches() < heavy.sensorInches());
        assertTrue(Double.isInfinite(light.recordMinutes(ActionCamSpec.FULL_HD)));
        assertTrue(heavy.ownBattery());
        assertEquals(0.0, AccessoryType.ACTION_CAM_HEAVY.flightBatteryWatts);
        assertTrue(heavy.stabilization() && heavy.horizonLevelling());
        assertTrue(heavy.maxHeight() > ActionCamSpec.FULL_HD);
        assertTrue(heavy.supportedHeights().contains(heavy.maxHeight()));
        assertFalse(light.supportedHeights().contains(1440));
        double fullHd = heavy.recordMinutes(ActionCamSpec.FULL_HD);
        double max = heavy.recordMinutes(heavy.maxHeight());
        assertTrue(fullHd > max && max > 30.0 && fullHd < 180.0, fullHd + " / " + max);
        assertTrue(AccessoryType.LED_STRIP.isLight());
        assertFalse(AccessoryType.LED_STRIP.isCamera());
    }

    @Test
    void everyFrameMountHasAnAnchor() {
        for (FrameType frame : FrameType.values()) {
            Set<AccessoryMount> tabled = EnumSet.noneOf(AccessoryMount.class);
            for (AccessoryMount mount : AccessoryMount.values()) {
                AccessoryAnchor.table(frame, mount).ifPresent(anchor -> tabled.add(mount));
            }
            assertEquals(frame.accessoryMounts, tabled, frame.key());
            for (AccessoryMount mount : frame.accessoryMounts) {
                AccessoryAnchor anchor = AccessoryAnchor.of(frame, mount);
                assertTrue(anchor.tiltDegrees() >= AccessoryAnchor.MIN_TILT && anchor.tiltDegrees() <= AccessoryAnchor.MAX_TILT);
                assertTrue(anchor.z() < 0.0, frame.key() + " " + mount.key() + " sitzt vorn");
                assertEquals(mount == AccessoryMount.TOP, anchor.y() > 20.0, frame.key() + " " + mount.key());
            }
        }
        assertFalse(FrameType.WHOOP_65.accessoryMounts.contains(AccessoryMount.TOP));
    }

    @Test
    void anchorsPlaceLensLedsAndCorners() {
        AccessoryAnchor top = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.TOP);
        double[] lens = top.lensMm(AccessoryType.ACTION_CAM_HEAVY);
        double[] center = top.centerOf(AccessoryType.ACTION_CAM_HEAVY);
        assertTrue(lens[2] < center[2], "Linse vorn");
        assertTrue(center[1] > top.y() + AccessoryType.HINGE_CLEARANCE_MM, "Kamera über dem Gelenk");
        double[] view = top.viewDirection();
        assertTrue(view[2] < 0.0 && view[1] > 0.0, "Uptilt nach oben");
        AccessoryAnchor bottom = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        double[] first = bottom.ledMm(AccessoryType.LED_STRIP, 0);
        double[] last = bottom.ledMm(AccessoryType.LED_STRIP, AccessoryType.LED_STRIP.ledCount - 1);
        assertEquals(bottom.lengthMm(), last[0] - first[0] + bottom.lengthMm() / AccessoryType.LED_STRIP.ledCount, 1.0E-9);
        double[] light = bottom.ledDirection();
        assertTrue(light[1] < 0.0 && light[2] < 0.0, "LEDs strahlen nach unten vorn");
        double highest = Double.NEGATIVE_INFINITY;
        for (double[] corner : top.corners(AccessoryType.ACTION_CAM_HEAVY)) {
            highest = Math.max(highest, corner[1]);
        }
        assertTrue(highest > top.y() + AccessoryType.ACTION_CAM_HEAVY.heightMm);
        assertEquals(bottom.lengthMm() / AccessoryType.LED_STRIP.widthMm, bottom.stretch(AccessoryType.LED_STRIP), 1.0E-9);
        assertEquals(1.0, top.stretch(AccessoryType.ACTION_CAM_HEAVY));
    }
}

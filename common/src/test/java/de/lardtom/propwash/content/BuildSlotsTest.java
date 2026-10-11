package de.lardtom.propwash.content;

import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.core.PartCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildSlotsTest {
    @Test
    void slotCategoriesMatchSlotCounts() {
        assertEquals(12, BuildSlots.REQUIRED_COUNT);
        assertEquals(12 + AccessoryMount.values().length, BuildSlots.COUNT);
        for (PartCategory category : PartCategory.values()) {
            int count = 0;
            for (int slot = 0; slot < BuildSlots.COUNT; slot++) {
                if (BuildSlots.categoryOf(slot) == category) {
                    count++;
                    assertEquals(slot - BuildSlots.firstSlot(category), BuildSlots.positionInCategory(slot));
                }
            }
            assertEquals(category.slotCount(), count, category.key());
            assertEquals(category == PartCategory.ACCESSORY, category.optional(), category.key());
        }
        assertThrows(IndexOutOfBoundsException.class, () -> BuildSlots.categoryOf(BuildSlots.COUNT));
    }

    @Test
    void accessorySlotsTakeOnlyTheirMount() {
        for (AccessoryMount mount : AccessoryMount.values()) {
            int slot = BuildSlots.accessorySlot(mount);
            assertEquals(PartCategory.ACCESSORY, BuildSlots.categoryOf(slot));
            assertEquals(mount, BuildSlots.mountOf(slot).orElseThrow());
            for (AccessoryType type : AccessoryType.values()) {
                assertEquals(type.mount == mount, BuildSlots.accepts(slot, type), type.key() + " in " + mount.key());
            }
        }
        assertTrue(BuildSlots.mountOf(BuildSlots.FRAME).isEmpty());
        assertArrayEquals(new int[]{BuildSlots.accessorySlot(AccessoryMount.TOP)}, BuildSlots.slotsFor(AccessoryType.ACTION_CAM_HEAVY));
        assertEquals(DroneBuild.MOTOR_COUNT, BuildSlots.slotsFor(MotorType.M2207_1750).length);
    }

    @Test
    void accessoriesAreOptionalAndRoundTrip() {
        DroneBuild build = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY).withAccessory(AccessoryType.LED_STRIP);
        List<PartInfo> layout = BuildSlots.layout(build);
        assertEquals(AccessoryType.ACTION_CAM_HEAVY, layout.get(BuildSlots.accessorySlot(AccessoryMount.TOP)));
        assertEquals(AccessoryType.LED_STRIP, layout.get(BuildSlots.accessorySlot(AccessoryMount.BOTTOM)));
        BuildSlots.Check check = BuildSlots.check(layout);
        assertEquals(build, check.build().orElseThrow());
        assertTrue(check.buildable());
        assertEquals(BuildSlots.COUNT, check.filledSlots());
        List<PartInfo> bare = new ArrayList<>(layout);
        bare.set(BuildSlots.accessorySlot(AccessoryMount.TOP), null);
        assertEquals(DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP), BuildSlots.check(bare).build().orElseThrow());
    }

    @Test
    void cameraOnAWhoopIsNotBuildable() {
        List<PartInfo> slots = new ArrayList<>(BuildSlots.layout(DroneBuild.WHOOP));
        slots.set(BuildSlots.accessorySlot(AccessoryMount.TOP), AccessoryType.ACTION_CAM_LIGHT);
        BuildSlots.Check check = BuildSlots.check(slots);
        assertTrue(check.complete());
        assertFalse(check.buildable());
        assertTrue(check.build().orElseThrow().problems().contains(DroneBuild.Issue.ACCESSORY_MOUNT));
        slots.set(BuildSlots.accessorySlot(AccessoryMount.TOP), null);
        slots.set(BuildSlots.accessorySlot(AccessoryMount.BOTTOM), AccessoryType.LED_STRIP);
        assertTrue(BuildSlots.check(slots).buildable());
    }

    @Test
    void everyPresetRoundTripsThroughTheSlots() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            List<PartInfo> layout = BuildSlots.layout(preset);
            assertEquals(BuildSlots.COUNT, layout.size());
            BuildSlots.Check check = BuildSlots.check(layout);
            assertTrue(check.complete(), preset.toString());
            assertEquals(preset, check.build().orElseThrow());
            assertTrue(check.missing().isEmpty());
            assertTrue(check.mixed().isEmpty());
            assertEquals(BuildSlots.REQUIRED_COUNT, check.filledSlots());
            assertEquals(preset.buildable(), check.buildable());
        }
    }

    @Test
    void emptySlotsReportEveryCategoryMissing() {
        List<PartInfo> slots = new ArrayList<>();
        for (int i = 0; i < BuildSlots.COUNT; i++) {
            slots.add(null);
        }
        BuildSlots.Check check = BuildSlots.check(slots);
        assertTrue(check.empty());
        assertFalse(check.complete());
        assertFalse(check.buildable());
        Set<PartCategory> required = EnumSet.allOf(PartCategory.class);
        required.remove(PartCategory.ACCESSORY);
        assertEquals(required, check.missing());
    }

    @Test
    void oneMissingMotorBlocksTheBuild() {
        List<PartInfo> slots = new ArrayList<>(BuildSlots.layout(DroneBuild.FREESTYLE));
        slots.set(BuildSlots.MOTOR + 2, null);
        BuildSlots.Check check = BuildSlots.check(slots);
        assertFalse(check.complete());
        assertEquals(Set.of(PartCategory.MOTOR), check.missing());
        assertEquals(BuildSlots.REQUIRED_COUNT - 1, check.filledSlots());
    }

    @Test
    void mixedMotorsAndPropsAreRejected() {
        List<PartInfo> slots = new ArrayList<>(BuildSlots.layout(DroneBuild.FREESTYLE));
        slots.set(BuildSlots.MOTOR + 1, MotorType.M2306_1860);
        slots.set(BuildSlots.PROP + 3, PropType.P3_3B);
        BuildSlots.Check check = BuildSlots.check(slots);
        assertFalse(check.complete());
        assertEquals(Set.of(PartCategory.MOTOR, PartCategory.PROP), check.mixed());
        assertTrue(check.missing().isEmpty());
    }

    @Test
    void partsInTheWrongSlotCountAsMissing() {
        List<PartInfo> slots = new ArrayList<>(BuildSlots.layout(DroneBuild.RACER));
        slots.set(BuildSlots.FRAME, PropType.P51_3B);
        BuildSlots.Check check = BuildSlots.check(slots);
        assertEquals(Set.of(PartCategory.FRAME), check.missing());
        assertFalse(BuildSlots.accepts(BuildSlots.FRAME, PropType.P51_3B));
        assertTrue(BuildSlots.accepts(BuildSlots.PROP, PropType.P51_3B));
        assertFalse(BuildSlots.accepts(BuildSlots.PROP, null));
    }

    @Test
    void incompatibleButCompleteBuildIsReportedAsNotBuildable() {
        List<PartInfo> slots = new ArrayList<>(BuildSlots.layout(DroneBuild.WHOOP));
        for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
            slots.set(BuildSlots.MOTOR + i, MotorType.M2207_1750);
        }
        BuildSlots.Check check = BuildSlots.check(slots);
        assertTrue(check.complete());
        assertFalse(check.buildable());
        assertTrue(check.build().orElseThrow().problems().contains(DroneBuild.Issue.MOTOR_MOUNT));
    }

    @Test
    void everyIssueHighlightsItsParts() {
        for (DroneBuild.Issue issue : DroneBuild.Issue.values()) {
            Set<PartCategory> affected = BuildSlots.affectedBy(issue);
            assertEquals(issue == DroneBuild.Issue.MISSING_PART ? 0 : 2, affected.size(), issue.name());
        }
    }

    @Test
    void wrongSlotCountIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> BuildSlots.check(List.of()));
    }
}

package de.lardtom.propwash.build;

import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartIndexTest {
    private static List<String> names(PartInfo[] parts) {
        List<String> out = new ArrayList<>();
        for (PartInfo part : parts) {
            if (part.builtin()) {
                out.add(part.name());
            }
        }
        return out;
    }

    @Test
    void builtinOrderMatchesTheOldEnums() {
        assertEquals(List.of("WHOOP_65", "CINEWHOOP_3", "FREESTYLE_5", "RACE_5", "LONG_RANGE_7"), names(FrameType.values()));
        assertEquals(List.of("AIO_WHOOP", "AIO_20", "STACK_30", "CREATIVE_FC"), names(StackType.values()));
        assertEquals(List.of("M0802_22000", "M1404_4600", "M2004_3000", "M2207_1750", "M2306_1860", "M2807_1300"), names(MotorType.values()));
        assertEquals(List.of("P31_4B", "P31_3B", "P3_3B", "P51_3B", "P7_2B", "P51_29_3B"), names(PropType.values()));
        assertEquals(List.of("ANALOG_NANO", "ANALOG_RACE", "DIGITAL_LITE", "DIGITAL_HD"), names(VideoType.values()));
        assertEquals(List.of("B1S_300_HV", "B4S_650", "B6S_1100", "B6S_1300", "B6S_4000_LION"), names(BatteryType.values()));
        assertEquals(List.of("LED_STRIP", "ACTION_CAM_LIGHT", "ACTION_CAM_HEAVY", "ACTION_CAM_FLASHBACK", "GRIPPER"), names(AccessoryType.values()));
        assertEquals(List.of("LIPO", "LIHV", "LION"), List.of(BatteryType.Chemistry.values()).stream().map(BatteryType.Chemistry::name).toList());
    }

    @Test
    void builtinIdsKeepTheirKeysAndTranslationKeys() {
        for (PartKind kind : PartKind.values()) {
            for (PartInfo part : PartIndex.all(kind)) {
                if (!part.builtin()) {
                    continue;
                }
                assertEquals("propwash", part.id().getNamespace(), part.name());
                assertEquals(part.id().getPath(), part.key(), part.name());
                assertEquals("part.propwash." + part.key(), part.translationKey(), part.name());
                assertEquals(kind, part.definition().kind(), part.name());
                assertSame(part, PartIndex.byKey(part.key()).orElseThrow());
                assertSame(part, PartIndex.byKey("propwash:" + part.key()).orElseThrow());
                assertSame(part, PartIndex.byId(part.id()).orElseThrow());
            }
        }
        assertEquals("m2207_1750", MotorType.M2207_1750.key());
        assertEquals(Identifier.fromNamespaceAndPath("propwash", "m2207_1750"), MotorType.M2207_1750.id());
    }

    @Test
    void unknownOrMalformedKeysResolveToNothing() {
        assertTrue(PartIndex.byKey("m9999_1").isEmpty());
        assertTrue(PartIndex.byKey("other:m2207_1750").isEmpty());
        assertTrue(PartIndex.byKey("Not A Key").isEmpty());
        assertTrue(PartIndex.byKey("").isEmpty());
        assertTrue(FrameType.byKey("m2207_1750").isEmpty());
        assertEquals("justmoreparts:m2806_1300", PartIndex.keyOf(Identifier.fromNamespaceAndPath("justmoreparts", "m2806_1300")));
    }

    @Test
    void legacyFieldsComeFromTheDefinitions() {
        for (FrameType frame : FrameType.values()) {
            assertEquals(frame.definition().wheelbaseMm(), frame.wheelbaseMm);
            assertEquals(frame.definition().ducted(), frame.ducted);
            assertEquals(frame.definition().maxPropMm(), frame.maxPropMm);
            assertEquals(frame.definition().basics().massGrams(), frame.massGrams());
        }
        assertTrue(FrameType.WHOOP_65.ducted);
        assertTrue(FrameType.CINEWHOOP_3.ducted);
        assertFalse(FrameType.FREESTYLE_5.ducted);
        assertEquals(1.10, FrameType.WHOOP_65.dragCoefficient);
        assertEquals(new BatteryFit.Rule(6, 6, 500.0, 6, 1300), BatteryFit.rule(FrameType.LONG_RANGE_7));
        assertEquals(new AccessoryAnchor(0.0, 34.7, -69.0, 22.0, 0.0), AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.TOP));
        assertEquals(AccessoryAnchor.fallback(FrameType.WHOOP_65, AccessoryMount.TOP), AccessoryAnchor.of(FrameType.WHOOP_65, AccessoryMount.TOP));
        assertSame(ActionCamSpec.HEAVY, AccessoryType.ACTION_CAM_HEAVY.camera().orElseThrow());
        assertSame(BatteryType.Chemistry.LIHV, BatteryType.B1S_300_HV.chemistry);
        assertSame(MountSize.Motor.M28, MotorType.M2807_1300.mount);
        assertEquals(List.of(MountSize.Motor.M08, MountSize.Motor.M14, MountSize.Motor.M22, MountSize.Motor.M28),
                List.of(MountSize.Motor.values()).subList(0, 4));
    }

    @Test
    void detachedAddonPartsStayOutOfTheRegistry() {
        int frames = FrameType.values().length;
        FrameType xClass = (FrameType) PartIndex.detached(FrameDefinition.builder(Identifier.fromNamespaceAndPath("justmoreparts", "xclass_13"))
                .mass(1450.0).role(FrameRole.X_CLASS).shape(FrameShape.TRUE_X).wheelbase(820).props(250, 330)
                .motorMount(Identifier.fromNamespaceAndPath("justmoreparts", "m42")).stackMount(Mounts.STACK_S30)
                .accessoryMount(AccessorySlot.TOP, AccessorySlot.BOTTOM).aero(0.080, 1.0).impactTolerance(6.0)
                .batteryFit(new BatteryFitRule(6, 8, 2500.0, 8, 4000)).cameraTilt(15.0)
                .build());
        FrameType deadcat = (FrameType) PartIndex.detached(FrameDefinition.builder(Identifier.fromNamespaceAndPath("justmoreparts", "deadcat_5"))
                .mass(118.0).role(FrameRole.FREESTYLE).shape(FrameShape.DEADCAT).wheelbase(230).maxProp(130)
                .motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30).aero(0.012, 0.9)
                .arms(List.of(MotorArm.of(-95.0, 0.0, -60.0, MotorArm.Spin.CW), MotorArm.of(95.0, 0.0, -60.0, MotorArm.Spin.CCW),
                        MotorArm.of(-80.0, 0.0, 80.0, MotorArm.Spin.CCW), MotorArm.of(80.0, 0.0, 80.0, MotorArm.Spin.CW)))
                .camera(new Vec3Mm(0.0, 14.0, -58.0)).batteryFit(new BatteryFitRule(4, 6, 260.0, 6, 0))
                .build());
        BatteryType lion8s = (BatteryType) PartIndex.detached(BatteryDefinition.builder(Identifier.fromNamespaceAndPath("justmoreparts", "b8s_4500_lion"))
                .mass(560.0).cells(8).capacity(4500).cellResistance(0.014).cRating(12).chemistry(BatteryDefinition.LION).connector("XT60")
                .build());
        assertEquals(frames, FrameType.values().length);
        assertTrue(FrameType.byKey("justmoreparts:xclass_13").isEmpty());
        assertFalse(xClass.builtin());
        assertEquals("justmoreparts:xclass_13", xClass.key());
        assertEquals("part.justmoreparts.xclass_13", xClass.translationKey());
        assertEquals(13.0, xClass.definition().nominalPropInches());
        assertEquals(4, deadcat.definition().geometry().arms().size());
        assertSame(BatteryType.Chemistry.LION, lion8s.chemistry);
        assertEquals(54.0, lion8s.maxContinuousCurrent());
    }

    @Test
    void paintSlotsOfTheApiMatchTheInternalSlots() {
        List<String> keys = new ArrayList<>();
        for (PaintSlot slot : PaintSlot.values()) {
            keys.add(slot.key());
        }
        assertEquals(keys, PaintSpec.SLOTS);
    }

    @Test
    void addonChemistriesAreSortedByIdSoTheFingerprintIgnoresTheLoadOrder() {
        List<BatteryType.Chemistry> builtins = List.of(BatteryType.Chemistry.values());
        BatteryType.Chemistry jmp = addonChemistry("justmoreparts", "lihv_ss");
        BatteryType.Chemistry example = addonChemistry("propwash_example", "lihv_445");
        BatteryType.Chemistry early = addonChemistry("justmoreparts", "a_cell");
        List<BatteryType.Chemistry> one = PartIndex.chemistryOrder(builtins, List.of(jmp, example, early));
        List<BatteryType.Chemistry> other = PartIndex.chemistryOrder(builtins, List.of(example, early, jmp));
        List<BatteryType.Chemistry> expected = new ArrayList<>(builtins);
        expected.addAll(List.of(early, jmp, example));
        assertEquals(expected, one);
        assertEquals(one, other);
        assertEquals(PartIndex.fingerprint(one, PartIndex.all()), PartIndex.fingerprint(other, PartIndex.all()));
        assertFalse(PartIndex.fingerprint(one, PartIndex.all()).equals(PartIndex.fingerprint(builtins, PartIndex.all())));
        assertEquals(PartIndex.fingerprint(), PartIndex.fingerprint(PartIndex.chemistries(), PartIndex.all()));
    }

    private static BatteryType.Chemistry addonChemistry(String namespace, String path) {
        CellChemistry lihv = BatteryType.Chemistry.byId(Identifier.fromNamespaceAndPath("propwash", "lihv")).orElseThrow().definition();
        return new BatteryType.Chemistry(new CellChemistry(Identifier.fromNamespaceAndPath(namespace, path), lihv.fullCellVoltage(),
                lihv.nominalCellVoltage(), lihv.emptyCellVoltage(), lihv.cutoffCellVoltage(), lihv.storageCellVoltage(),
                lihv.densityGramsPerCm3(), lihv.ocvShape(), lihv.usableFraction()), null);
    }
}

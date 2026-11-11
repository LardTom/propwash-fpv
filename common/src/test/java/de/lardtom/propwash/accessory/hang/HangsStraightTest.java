package de.lardtom.propwash.accessory.hang;

import com.google.gson.JsonObject;
import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.parts.PartDataProblems;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.parts.PartRegistryImpl;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangsStraightTest {
    private static final double EPS = 1.0E-9;
    private static final Vec3 COM = new Vec3(10.0, 70.0, -4.0);
    private static final Identifier HOLDER = Identifier.fromNamespaceAndPath("hangtest", "spear_holder");
    private static final Identifier BOOSTER = Identifier.fromNamespaceAndPath("hangtest", "booster");

    private static AccessoryDefinition.Builder holder() {
        return AccessoryDefinition.builder(HOLDER).basics(PartBasics.of(18.0)).slot(AccessorySlot.BOTTOM).size(24.0, 10.0, 60.0).frontalArea(0.0004);
    }

    private static AccessoryDefinition.Builder booster() {
        return AccessoryDefinition.builder(BOOSTER).basics(PartBasics.of(16.0)).slot(AccessorySlot.TOP).size(18.0, 14.0, 36.0).frontalArea(0.0003);
    }

    private static AccessoryType type(AccessoryDefinition definition) {
        return (AccessoryType) PartIndex.detached(definition);
    }

    private static AccessoryDefinition decode(String text) {
        PartDataProblems problems = new PartDataProblems("hangtest.json");
        JsonObject json = PartJson.parse(text, problems);
        assertNotNull(json, problems.errors().toString());
        PartDefinition definition = PartJson.decode(PartKind.ACCESSORY, HOLDER, json, problems);
        assertEquals(List.of(), problems.errors(), text);
        assertEquals(List.of(), problems.warnings(), text);
        return (AccessoryDefinition) definition;
    }

    private static String json(String extra) {
        return """
                {"format": 1, "mass_g": 18.0, "slot": "bottom", "size_mm": [24.0, 10.0, 60.0], "frontal_area_m2": 0.0004%s}
                """.formatted(extra);
    }

    private static void assertVec(double[] expected, double[] actual, String what) {
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(expected[axis], actual[axis], EPS, what + " Achse " + axis);
        }
    }

    private static void assertVec(Vec3 expected, Vec3 actual, double eps, String what) {
        assertEquals(expected.x, actual.x, eps, what + " x");
        assertEquals(expected.y, actual.y, eps, what + " y");
        assertEquals(expected.z, actual.z, eps, what + " z");
    }

    @Test
    void definitionDefaultsToTodaysBehaviour() {
        AccessoryDefinition twelve = new AccessoryDefinition(HOLDER, PartBasics.of(18.0), AccessorySlot.BOTTOM, 24.0, 10.0, 60.0, 0.0, 0.0004, 0,
                Optional.empty(), 0.0, 0.0);
        AccessoryDefinition thirteen = new AccessoryDefinition(HOLDER, PartBasics.of(18.0), AccessorySlot.BOTTOM, 24.0, 10.0, 60.0, 0.0, 0.0004, 0,
                Optional.empty(), 0.0, 0.0, List.of());
        assertFalse(twelve.hangsStraight());
        assertFalse(thirteen.hangsStraight());
        assertFalse(holder().build().hangsStraight());
        assertEquals(twelve, thirteen);
        assertEquals(twelve, holder().build());
        assertEquals(twelve, holder().hangsStraight(false).build());
        AccessoryDefinition straight = holder().hangsStraight(true).build();
        assertTrue(straight.hangsStraight());
        assertNotEquals(twelve, straight);
        assertNotEquals(PartIndex.hashOf(twelve), PartIndex.hashOf(straight));
        assertEquals(straight, new AccessoryDefinition(HOLDER, PartBasics.of(18.0), AccessorySlot.BOTTOM, 24.0, 10.0, 60.0, 0.0, 0.0004, 0,
                Optional.empty(), 0.0, 0.0, List.of(), true));
    }

    @Test
    void codecIsAdditive() {
        assertFalse(decode(json("")).hangsStraight());
        assertFalse(decode(json(", \"hangs_straight\": false")).hangsStraight());
        assertTrue(decode(json(", \"hangs_straight\": true")).hangsStraight());
        AccessoryDefinition plain = holder().build();
        assertFalse(PartJson.encode(plain).has("hangs_straight"), PartJson.encode(plain).toString());
        AccessoryDefinition straight = holder().hangsStraight(true).build();
        JsonObject written = PartRegistryImpl.INSTANCE.toJson(straight);
        assertTrue(written.get("hangs_straight").getAsBoolean(), written.toString());
        assertEquals(straight, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, HOLDER, written));
        assertEquals(plain, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, HOLDER, PartJson.encode(plain)));
        AccessoryDefinition ring = holder().leds(8).ledPath(new Vec3Mm(-10.0, -10.0, 0.0), new Vec3Mm(10.0, -10.0, 0.0)).hangsStraight(true).build();
        assertEquals(ring, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, HOLDER, PartJson.encode(ring)));
        for (AccessoryType builtin : AccessoryType.all()) {
            if (builtin.builtin()) {
                assertFalse(builtin.definition().hangsStraight(), builtin.key());
                assertFalse(PartJson.encode(builtin.definition()).has("hangs_straight"), builtin.key());
            }
        }
        JsonObject wrong = PartJson.encode(plain);
        wrong.addProperty("hangs_straight", "ja");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, HOLDER, wrong));
        assertTrue(error.getMessage().contains("hangs_straight") && error.getMessage().contains("true oder false"), error.getMessage());
    }

    @Test
    void gripperAndLedStripStayAsTheyWere() {
        AccessoryType gripper = AccessoryType.GRIPPER;
        AccessoryType strip = AccessoryType.LED_STRIP;
        assertTrue(gripper.hangsStraight());
        assertFalse(gripper.definition().hangsStraight());
        assertFalse(strip.hangsStraight());
        assertFalse(AccessoryType.ACTION_CAM_HEAVY.hangsStraight());
        for (FrameType frame : FrameType.all()) {
            AccessoryAnchor bottom = AccessoryAnchor.of(frame, AccessoryMount.BOTTOM);
            assertEquals(0.0, bottom.tiltFor(gripper), frame.key());
            assertEquals(1.0, bottom.stretch(gripper), frame.key());
            assertEquals(bottom.tiltDegrees(), bottom.tiltFor(strip), frame.key());
            double expected = bottom.lengthMm() > 0.0 ? bottom.lengthMm() / strip.widthMm : 1.0;
            assertEquals(expected, bottom.stretch(strip), EPS, frame.key());
            assertVec(bottom.toFrame(strip.ledMm(0, bottom.lengthMm() > 0.0 ? bottom.lengthMm() : strip.widthMm)), bottom.ledMm(strip, 0),
                    frame.key() + " erste LED");
        }
        AccessoryAnchor freestyle = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        assertEquals(30.0, freestyle.tiltFor(strip), EPS);
        assertEquals(34.0 / strip.widthMm, freestyle.stretch(strip), EPS);
        AccessoryAnchor top = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.TOP);
        assertEquals(22.0, top.tiltFor(AccessoryType.ACTION_CAM_HEAVY), EPS);
        assertVec(top.toFrame(AccessoryType.ACTION_CAM_HEAVY.lensMm()), top.lensMm(AccessoryType.ACTION_CAM_HEAVY), "Linse");
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        MountPose pose = MountPoses.pose(COM, new Quaternionf(), p, freestyle, gripper);
        assertVec(new Vec3(0.0, 0.0, -1.0), pose.forward(), 1.0E-6, "Greifer vorwärts");
        MountPose lit = MountPoses.pose(COM, new Quaternionf(), p, freestyle, strip);
        assertVec(new Vec3(0.0, Math.sin(Math.toRadians(30.0)), -Math.cos(Math.toRadians(30.0))), lit.forward(), 1.0E-6, "LED-Streifen vorwärts");
    }

    @Test
    void addonPartsTiltAndStretchWithoutTheFlag() {
        AccessoryType tilted = type(holder().build());
        AccessoryType boosted = type(booster().build());
        AccessoryAnchor bottom = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        AccessoryAnchor top = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.TOP);
        AccessoryAnchor whoop = AccessoryAnchor.of(FrameType.WHOOP_65, AccessoryMount.BOTTOM);
        assertFalse(tilted.hangsStraight());
        assertEquals(30.0, bottom.tiltFor(tilted), EPS);
        assertEquals(34.0 / 24.0, bottom.stretch(tilted), EPS);
        assertEquals(22.0, top.tiltFor(boosted), EPS);
        assertEquals(1.0, top.stretch(boosted), EPS);
        assertEquals(90.0, whoop.tiltFor(tilted), EPS);
        assertEquals(10.0 / 24.0, whoop.stretch(tilted), EPS);
    }

    @Test
    void straightPartsHaveNoTiltAndNoStretchOnEveryFrame() {
        AccessoryType holder = type(holder().hangsStraight(true).build());
        AccessoryType booster = type(booster().hangsStraight(true).build());
        assertTrue(holder.hangsStraight());
        assertTrue(booster.hangsStraight());
        int checked = 0;
        for (FrameType frame : FrameType.all()) {
            for (AccessoryMount mount : frame.accessoryMounts) {
                AccessoryType type = mount == AccessoryMount.TOP ? booster : holder;
                if (!type.fits(mount)) {
                    continue;
                }
                AccessoryAnchor anchor = AccessoryAnchor.of(frame, mount);
                String what = frame.key() + " " + mount.key();
                assertEquals(0.0, anchor.tiltFor(type), what);
                assertEquals(1.0, anchor.stretch(type), what);
                double[] local = type.centerMm();
                assertVec(new double[]{anchor.x() + local[0], anchor.y() + local[1], anchor.z() + local[2]}, anchor.centerOf(type), what + " Mitte");
                double[] min = type.boxMinMm();
                double[] max = type.boxMaxMm();
                double[][] corners = anchor.corners(type);
                assertVec(new double[]{anchor.x() + min[0], anchor.y() + min[1], anchor.z() + min[2]}, corners[0], what + " Ecke 0");
                assertVec(new double[]{anchor.x() + max[0], anchor.y() + max[1], anchor.z() + max[2]}, corners[7], what + " Ecke 7");
                assertEquals(type.widthMm, corners[1][0] - corners[0][0], EPS, what + " Breite");
                checked++;
            }
        }
        assertTrue(checked >= 5, "geprüfte Halterungen: " + checked);
        AccessoryAnchor whoop = AccessoryAnchor.of(FrameType.WHOOP_65, AccessoryMount.BOTTOM);
        assertEquals(90.0, whoop.tiltDegrees(), EPS);
        assertEquals(whoop.y() - 10.0, whoop.corners(holder)[0][1], EPS);
    }

    @Test
    void mountPoseFollowsTheFlag() {
        AccessoryType straight = type(holder().hangsStraight(true).build());
        AccessoryType tilted = type(holder().build());
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        AccessoryAnchor bottom = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        Quaternionf level = new Quaternionf();
        MountPose pose = MountPoses.pose(COM, level, p, bottom, straight);
        assertVec(new Vec3(0.0, 0.0, -1.0), pose.forward(), 1.0E-6, "vorwärts");
        assertVec(new Vec3(0.0, 1.0, 0.0), pose.up(), 1.0E-6, "oben");
        assertVec(new Vec3(1.0, 0.0, 0.0), pose.right(), 1.0E-6, "rechts");
        Vec3 front = MountPoses.toWorld(COM, level, p, bottom.x(), bottom.y() - 5.0, bottom.z() - 30.0);
        assertVec(front, pose.front(), 1.0E-6, "Mündung");
        Vec3 center = MountPoses.toWorld(COM, level, p, bottom.x(), bottom.y() - 5.0, bottom.z());
        assertVec(center, pose.center(), 1.0E-6, "Mitte");
        MountPose old = MountPoses.pose(COM, level, p, bottom, tilted);
        assertEquals(Math.sin(Math.toRadians(30.0)), old.forward().y, 1.0E-6, "ohne Angabe wie bisher 30° geneigt");
        assertVec(pose.anchor(), old.anchor(), 1.0E-9, "Anbaupunkt");
        Quaternionf yawed = new Quaternionf().rotateY((float) Math.toRadians(-90.0));
        MountPose east = MountPoses.pose(COM, yawed, p, bottom, straight);
        assertVec(new Vec3(1.0, 0.0, 0.0), east.forward(), 1.0E-5, "nach Osten");
        AccessoryAnchor whoop = AccessoryAnchor.of(FrameType.WHOOP_65, AccessoryMount.BOTTOM);
        MountPose small = MountPoses.pose(COM, level, AirframeSpec.of(DroneBuild.WHOOP), whoop, straight);
        assertVec(new Vec3(0.0, 0.0, -1.0), small.forward(), 1.0E-6, "Whoop vorwärts");
        MountPose smallOld = MountPoses.pose(COM, level, AirframeSpec.of(DroneBuild.WHOOP), whoop, tilted);
        assertEquals(1.0, smallOld.forward().y, 1.0E-6, "Whoop ohne Angabe 90° geneigt");
    }

    @Test
    void ledsAndLensFollowTheUntiltedBody() {
        AccessoryType lit = type(holder().leds(6).hangsStraight(true).build());
        AccessoryAnchor bottom = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.BOTTOM);
        for (int i = 0; i < lit.ledCount; i++) {
            double[] local = lit.ledMm(i, lit.widthMm);
            assertVec(new double[]{bottom.x() + local[0], bottom.y() + local[1], bottom.z() + local[2]}, bottom.ledMm(lit, i), "LED " + i);
        }
        double[] center = lit.centerMm();
        assertVec(new double[]{bottom.x() + center[0], bottom.y() + center[1], bottom.z() + center[2]}, bottom.lensMm(lit), "ohne Kamera Mitte");
    }

    @Test
    void ladenVariantsAndCopiesKeepTheFlag() {
        AccessoryType straight = type(holder().hangsStraight(true).build());
        AccessoryType laden = straight.laden(120L);
        assertTrue(laden.hangsStraight());
        assertTrue(laden.definition().hangsStraight());
        assertEquals(straight, laden.unladen());
        AccessoryType plain = type(holder().build());
        assertFalse(plain.laden(120L).hangsStraight());
    }
}

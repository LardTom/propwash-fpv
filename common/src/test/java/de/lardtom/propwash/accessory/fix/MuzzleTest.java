package de.lardtom.propwash.accessory.fix;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.accessory.dispense.DispenseOrigin;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuzzleTest {
    private static final double EPS = 1.0E-9;
    private static final Identifier MOUNT = Identifier.fromNamespaceAndPath("apiaccfix", "dispenser_mount");
    private static final double GROUND = 64.0;

    private static AccessoryDefinition.Builder dispenser(double heightMm) {
        return AccessoryDefinition.builder(MOUNT).basics(PartBasics.of(150.0)).slot(AccessorySlot.FRONT).size(62.0, heightMm, 64.0).frontalArea(0.0036);
    }

    private static AccessoryType type(AccessoryDefinition definition) {
        return (AccessoryType) PartIndex.detached(definition);
    }

    private static double[] oldFront(AccessoryType type) {
        double[] min = type.boxMinMm();
        double[] max = type.boxMaxMm();
        return new double[]{(min[0] + max[0]) / 2.0, (min[1] + max[1]) / 2.0, min[2]};
    }

    private static Vec3 restingCom(AirframeSpec p) {
        return new Vec3(0.5, GROUND - p.hitboxFootOffset, 0.5);
    }

    @Test
    void defaultMuzzleIsTheMiddleOfTheFrontFaceOnEveryMount() {
        int checked = 0;
        for (AccessoryType builtin : AccessoryType.all()) {
            assertTrue(builtin.definition().muzzleMm().isEmpty(), builtin.key());
            double[] muzzle = builtin.muzzleMm();
            double[] old = oldFront(builtin);
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(old[axis], muzzle[axis], 0.0, builtin.key() + " Achse " + axis);
            }
            assertFalse(PartJson.encode(builtin.definition()).has("muzzle_mm"), builtin.key());
            checked++;
        }
        assertTrue(checked >= 5);
        AccessoryType front = type(dispenser(58.0).build());
        assertEquals(0.0, front.muzzleMm()[0], EPS);
        assertEquals(-29.0, front.muzzleMm()[1], EPS);
        assertEquals(-64.0, front.muzzleMm()[2], EPS);
        for (DroneBuild preset : DroneBuild.PRESETS) {
            for (AccessoryMount mount : preset.frame().accessoryMounts) {
                for (AccessoryType builtin : AccessoryType.all()) {
                    if (!builtin.fits(mount)) {
                        continue;
                    }
                    AccessoryAnchor anchor = AccessoryAnchor.of(preset.frame(), mount);
                    AccessoryAnchor tilted = new AccessoryAnchor(anchor.x(), anchor.y(), anchor.z(), anchor.tiltFor(builtin), anchor.lengthMm());
                    double[] o = oldFront(builtin);
                    double[] expected = tilted.toFrame(o[0], o[1], o[2]);
                    Quaternionf q = new Quaternionf().rotateY(0.4F).rotateX(-0.2F);
                    AirframeSpec p = AirframeSpec.of(preset);
                    MountPose pose = MountPoses.pose(restingCom(p), q, p, anchor, builtin);
                    Vec3 old = MountPoses.toWorld(restingCom(p), q, p, expected[0], expected[1], expected[2]);
                    assertEquals(old, pose.front(), preset.frame().key() + " " + mount.key() + " " + builtin.key());
                }
            }
        }
    }

    @Test
    void definedMuzzleMovesTheFrontOfTheMountPose() {
        AccessoryType raised = type(dispenser(58.0).muzzle(4.0, 20.0, -6.0).build());
        assertEquals(Optional.of(new Vec3Mm(4.0, 20.0, -6.0)), raised.definition().muzzleMm());
        assertEquals(4.0, raised.muzzleMm()[0], EPS);
        assertEquals(-9.0, raised.muzzleMm()[1], EPS);
        assertEquals(-70.0, raised.muzzleMm()[2], EPS);
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        AccessoryAnchor anchor = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.FRONT);
        MountPose pose = MountPoses.pose(restingCom(p), new Quaternionf(), p, anchor, raised);
        Vec3 expected = MountPoses.toWorld(restingCom(p), new Quaternionf(), p, anchor.x() + 4.0, anchor.y() - 9.0, anchor.z() - 70.0);
        assertEquals(expected.x, pose.front().x, 1.0E-6);
        assertEquals(expected.y, pose.front().y, 1.0E-6);
        assertEquals(expected.z, pose.front().z, 1.0E-6);
        AccessoryType laden = raised.laden(3000L);
        assertEquals(raised.definition().muzzleMm(), laden.definition().muzzleMm());
        assertEquals(raised.muzzleMm()[1], laden.muzzleMm()[1], 0.0);
    }

    @Test
    void muzzleMustStayAtTheBox() {
        assertTrue(dispenser(58.0).muzzle(31.0 + AccessoryDefinition.MAX_MUZZLE_OUTSIDE_MM, -29.0 - 20.0, -20.0).build().muzzleMm().isPresent());
        assertTrue(dispenser(58.0).muzzle(0.0, 0.0, 64.0 + 20.0).build().muzzleMm().isPresent());
        for (Vec3Mm outside : List.of(new Vec3Mm(51.5, 0.0, 0.0), new Vec3Mm(0.0, 49.5, 0.0), new Vec3Mm(0.0, -49.5, 0.0),
                new Vec3Mm(0.0, 0.0, -20.5), new Vec3Mm(0.0, 0.0, 84.5))) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> dispenser(58.0).muzzle(outside).build());
            assertTrue(error.getMessage().contains("muzzle_mm"), error.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> dispenser(58.0).muzzle(Double.NaN, 0.0, 0.0));
    }

    @Test
    void oldConstructorsHaveNoMuzzleAndTheCodecIsAdditive() {
        AccessoryDefinition fourteen = new AccessoryDefinition(MOUNT, PartBasics.of(150.0), AccessorySlot.FRONT, 62.0, 58.0, 64.0, 0.0, 0.0036, 0,
                Optional.empty(), 0.0, 0.0, List.of(), false);
        AccessoryDefinition twelve = new AccessoryDefinition(MOUNT, PartBasics.of(150.0), AccessorySlot.FRONT, 62.0, 58.0, 64.0, 0.0, 0.0036, 0,
                Optional.empty(), 0.0, 0.0);
        assertEquals(Optional.empty(), fourteen.muzzleMm());
        assertEquals(fourteen, twelve);
        assertEquals(fourteen, dispenser(58.0).build());
        AccessoryDefinition aimed = dispenser(58.0).muzzle(0.0, 20.0, 0.0).build();
        assertNotEquals(fourteen, aimed);
        assertNotEquals(PartIndex.hashOf(fourteen), PartIndex.hashOf(aimed));
        assertFalse(PartJson.encode(fourteen).has("muzzle_mm"));
        JsonObject written = PartRegistryImpl.INSTANCE.toJson(aimed);
        assertEquals(3, written.getAsJsonArray("muzzle_mm").size());
        assertEquals(aimed, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, written));
        assertEquals(fourteen, PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, PartJson.encode(fourteen)));
        JsonObject wrong = PartJson.encode(fourteen);
        wrong.addProperty("muzzle_mm", "vorn");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, wrong));
        assertTrue(error.getMessage().contains("muzzle_mm"), error.getMessage());
        JsonObject far = PartJson.encode(fourteen);
        JsonArray point = new JsonArray();
        point.add(0.0);
        point.add(80.0);
        point.add(0.0);
        far.add("muzzle_mm", point);
        IllegalArgumentException outside = assertThrows(IllegalArgumentException.class,
                () -> PartRegistryImpl.INSTANCE.fromJson(PartKind.ACCESSORY, MOUNT, far));
        assertTrue(outside.getMessage().contains("außerhalb der Box"), outside.getMessage());
    }

    @Test
    void parkedDroneStandsOnItsAccessoryAndTheMuzzleIsAboveTheGround() {
        for (FrameType frame : List.of(FrameType.CINEWHOOP_3, FrameType.FREESTYLE_5)) {
            DroneBuild build = frame == FrameType.CINEWHOOP_3 ? DroneBuild.CINEWHOOP : DroneBuild.FREESTYLE;
            for (double height : new double[]{26.0, 58.0}) {
                AccessoryType type = type(dispenser(height).build());
                DroneBuild fitted = build.withAccessory(type);
                AirframeSpec p = AirframeSpec.of(fitted);
                AccessoryAnchor anchor = AccessoryAnchor.of(frame, AccessoryMount.FRONT);
                Vec3 com = restingCom(p);
                MountPose pose = MountPoses.pose(com, new Quaternionf(), p, anchor, type);
                String what = frame.key() + " " + height + " mm";
                assertTrue(pose.front().y < GROUND, what + ": ohne Ausgleich steckt die Mündung im Boden (" + pose.front().y + ")");
                double lowest = DispenseOrigin.lowestY(com, new Quaternionf(), p, anchor, type);
                DispenseOrigin origin = DispenseOrigin.standing(pose.front(), GROUND, lowest);
                assertEquals(height / 2000.0, origin.muzzle().y - GROUND, 1.0E-5, what);
                assertEquals(pose.front().x, origin.muzzle().x, 0.0);
                assertEquals(pose.front().z, origin.muzzle().z, 0.0);
                assertEquals(GROUND, origin.above(GROUND - 0.49), 0.0);
                assertEquals(GROUND + 0.3, origin.above(GROUND + 0.3), 0.0);
            }
        }
    }

    @Test
    void flyingDronesKeepTheirMuzzle() {
        AccessoryType type = type(dispenser(58.0).build());
        DroneBuild fitted = DroneBuild.FREESTYLE.withAccessory(type);
        AirframeSpec p = AirframeSpec.of(fitted);
        AccessoryAnchor anchor = AccessoryAnchor.of(FrameType.FREESTYLE_5, AccessoryMount.FRONT);
        Vec3 com = restingCom(p).add(0.0, 3.0, 0.0);
        Quaternionf pitched = new Quaternionf().rotateX((float) Math.toRadians(-35.0));
        MountPose pose = MountPoses.pose(com, pitched, p, anchor, type);
        double lowest = DispenseOrigin.lowestY(com, pitched, p, anchor, type);
        assertTrue(lowest > GROUND + 2.0);
        DispenseOrigin origin = DispenseOrigin.standing(pose.front(), lowest - 0.5, lowest);
        assertEquals(pose.front(), origin.muzzle());
        assertEquals(0.0, origin.liftMetres(), 0.0);
        assertEquals(0.0, DispenseOrigin.lift(Double.NaN, 1.0), 0.0);
        assertEquals(0.0, DispenseOrigin.lift(1.0, 2.0), 0.0);
        assertEquals(0.5, DispenseOrigin.lift(2.0, 1.5), EPS);
        double tiltedLowest = DispenseOrigin.lowestY(restingCom(p), pitched, p, anchor, type);
        double levelLowest = DispenseOrigin.lowestY(restingCom(p), new Quaternionf(), p, anchor, type);
        assertTrue(tiltedLowest < levelLowest, "nach vorn geneigt hängt die Box tiefer: " + tiltedLowest + " gegen " + levelLowest);
    }
}

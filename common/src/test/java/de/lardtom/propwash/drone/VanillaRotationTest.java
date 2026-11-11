package de.lardtom.propwash.drone;

import de.lardtom.propwash.sim.SimSnapshot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaRotationTest {
    private static final float EPS = 1.0E-5F;

    private static Vector3f look(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vector3f((float) (-Math.sin(y) * Math.cos(p)), (float) -Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
    }

    private static float heading(Quaternionf q) {
        Vector3f nose = q.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return (float) Math.toDegrees(Math.atan2(-nose.x, nose.z));
    }

    private static void assertVector(Vector3f expected, Vector3f actual, String at) {
        assertEquals(expected.x, actual.x, EPS, at + " x");
        assertEquals(expected.y, actual.y, EPS, at + " y");
        assertEquals(expected.z, actual.z, EPS, at + " z");
    }

    @Test
    void noseFollowsTheVanillaLookWithoutRoll() {
        for (int yaw = -180; yaw <= 180; yaw += 15) {
            for (int pitch = -90; pitch <= 90; pitch += 15) {
                String at = yaw + "/" + pitch;
                Quaternionf q = VanillaRotation.attitude(yaw, pitch, new Quaternionf());
                assertEquals(1.0F, q.lengthSquared(), EPS, at);
                assertVector(look(yaw, pitch), q.transform(new Vector3f(0.0F, 0.0F, -1.0F)), at);
                Vector3f right = q.transform(new Vector3f(1.0F, 0.0F, 0.0F));
                assertEquals(0.0F, right.y, EPS, at + " Rollen");
                Vector3f up = q.transform(new Vector3f(0.0F, 1.0F, 0.0F));
                assertTrue(up.y >= -EPS, at + " kopfüber");
                if (Math.abs(pitch) < 90) {
                    float off = (heading(q) - yaw + 540.0F) % 360.0F - 180.0F;
                    assertEquals(0.0F, off, 1.0E-3F, at + " Gier");
                }
            }
        }
    }

    @Test
    void yawZeroIsTheSpawnAttitudeAndMinusNinetyFacesEast() {
        Quaternionf spawn = new Quaternionf().rotationY((float) Math.PI);
        Quaternionf zero = VanillaRotation.attitude(0.0F, 0.0F, new Quaternionf());
        assertEquals(1.0F, Math.abs(spawn.dot(zero)), EPS);
        assertVector(new Vector3f(1.0F, 0.0F, 0.0F), VanillaRotation.attitude(-90.0F, 0.0F, new Quaternionf()).transform(new Vector3f(0.0F, 0.0F, -1.0F)),
                "-90");
        assertVector(new Vector3f(-1.0F, 0.0F, 0.0F), VanillaRotation.attitude(90.0F, 0.0F, new Quaternionf()).transform(new Vector3f(0.0F, 0.0F, -1.0F)),
                "90");
        assertVector(new Vector3f(0.0F, 0.0F, -1.0F), VanillaRotation.attitude(180.0F, 0.0F, new Quaternionf()).transform(new Vector3f(0.0F, 0.0F, -1.0F)),
                "180");
        assertVector(new Vector3f(0.0F, -1.0F, 0.0F), VanillaRotation.attitude(30.0F, 90.0F, new Quaternionf()).transform(new Vector3f(0.0F, 0.0F, -1.0F)),
                "Nase senkrecht nach unten");
    }

    @Test
    void pitchIsClampedLikeVanilla() {
        Quaternionf clamped = VanillaRotation.attitude(45.0F, 120.0F, new Quaternionf());
        Quaternionf limit = VanillaRotation.attitude(45.0F, 90.0F, new Quaternionf());
        assertEquals(1.0F, Math.abs(clamped.dot(limit)), EPS);
        Quaternionf up = VanillaRotation.attitude(45.0F, -200.0F, new Quaternionf());
        assertEquals(1.0F, Math.abs(up.dot(VanillaRotation.attitude(45.0F, -90.0F, new Quaternionf()))), EPS);
    }

    @Test
    void onlyARotationTurnsTheDrone() {
        Vec3 pos = new Vec3(1.0, 2.0, 3.0);
        Set<Relative> tpWithoutRotation = Relative.union(Relative.direction(true, true, true), Relative.position(true, true, true), Relative.ROTATION);
        assertFalse(VanillaRotation.turns(new PositionMoveRotation(pos, Vec3.ZERO, 0.0F, 0.0F), tpWithoutRotation), "/tp ~ ~ ~");
        Set<Relative> tpAbsolute = Relative.union(Relative.direction(true, true, true), Relative.position(true, true, true));
        assertTrue(VanillaRotation.turns(new PositionMoveRotation(pos, Vec3.ZERO, -90.0F, 0.0F), tpAbsolute), "/tp ~ ~ ~ -90 0");
        assertTrue(VanillaRotation.turns(new PositionMoveRotation(pos, Vec3.ZERO, 0.0F, 0.0F), tpAbsolute), "/tp ~ ~ ~ 0 0");
        assertTrue(VanillaRotation.turns(new PositionMoveRotation(pos, Vec3.ZERO, 10.0F, 0.0F), tpWithoutRotation), "/tp ~ ~ ~ ~10 ~");
        assertTrue(VanillaRotation.turns(new PositionMoveRotation(pos, Vec3.ZERO, 0.0F, -5.0F), tpWithoutRotation), "/tp ~ ~ ~ ~ ~-5");
        assertTrue(VanillaRotation.turns(new PositionMoveRotation(pos, Vec3.ZERO, 12.0F, 34.0F), EnumSet.noneOf(Relative.class)), "/tp <Ziel>");
        Set<Relative> yawOnly = Relative.union(Relative.DELTA, Set.of(Relative.X_ROT));
        assertTrue(VanillaRotation.turns(new PositionMoveRotation(pos, Vec3.ZERO, 0.0F, 0.0F), yawOnly), "Gier absolut");
        assertFalse(VanillaRotation.turns(0.0F, true, 0.0F, true), "/rotate ohne Änderung");
        assertTrue(VanillaRotation.turns(45.0F, false, 0.0F, false), "/rotate 45 0");
        assertTrue(VanillaRotation.turns(0.0F, true, 20.0F, false), "/rotate ~ 20");
    }

    @Test
    void carryMotionKeepsTheWorldVelocityAndTheBodyRates() {
        Quaterniond from = new Quaterniond().rotationYXZ(0.4, -0.3, 0.2);
        Quaterniond to = new Quaterniond().rotationYXZ(-1.9, 0.35, 0.0);
        SimSnapshot snapshot = new SimSnapshot();
        snapshot.qw = from.w;
        snapshot.qx = from.x;
        snapshot.qy = from.y;
        snapshot.qz = from.z;
        snapshot.vx = 6.0;
        snapshot.vy = -1.25;
        snapshot.vz = 2.5;
        snapshot.lx = 0.012;
        snapshot.ly = -0.004;
        snapshot.lz = 0.007;
        snapshot.px = 10.0;
        snapshot.py = 64.0;
        snapshot.pz = -3.0;
        Vector3d bodyBefore = new Quaterniond(from).conjugate().transform(new Vector3d(snapshot.lx, snapshot.ly, snapshot.lz));
        VanillaRotation.carryMotion(snapshot, to);
        assertEquals(6.0, snapshot.vx);
        assertEquals(-1.25, snapshot.vy);
        assertEquals(2.5, snapshot.vz);
        assertEquals(10.0, snapshot.px);
        assertEquals(64.0, snapshot.py);
        assertEquals(-3.0, snapshot.pz);
        assertEquals(to.w, snapshot.qw);
        assertEquals(to.x, snapshot.qx);
        assertEquals(to.y, snapshot.qy);
        assertEquals(to.z, snapshot.qz);
        Vector3d bodyAfter = new Quaterniond(to).conjugate().transform(new Vector3d(snapshot.lx, snapshot.ly, snapshot.lz));
        assertEquals(bodyBefore.x, bodyAfter.x, 1.0E-12);
        assertEquals(bodyBefore.y, bodyAfter.y, 1.0E-12);
        assertEquals(bodyBefore.z, bodyAfter.z, 1.0E-12);
        assertEquals(new Vector3d(0.012, -0.004, 0.007).length(), new Vector3d(snapshot.lx, snapshot.ly, snapshot.lz).length(), 1.0E-12);
    }

    @Test
    void carryMotionWithoutUsableAttitudeOnlySetsTheTarget() {
        SimSnapshot snapshot = new SimSnapshot();
        snapshot.lx = 0.5;
        Quaterniond to = new Quaterniond().rotationY(1.0);
        VanillaRotation.carryMotion(snapshot, to);
        assertEquals(0.5, snapshot.lx);
        assertEquals(to.w, snapshot.qw);
        assertEquals(to.y, snapshot.qy);
    }
}

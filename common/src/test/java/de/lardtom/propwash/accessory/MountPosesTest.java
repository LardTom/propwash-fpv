package de.lardtom.propwash.accessory;

import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountPosesTest {
    private static final Vec3 COM = new Vec3(100.0, 64.0, -20.0);

    @Test
    void frontMountSitsAheadOfTheCentreAndPointsForward() {
        DroneBuild build = DroneBuild.FREESTYLE;
        AirframeSpec p = AirframeSpec.of(build);
        AccessoryAnchor anchor = AccessoryAnchor.of(build.frame(), AccessoryMount.FRONT);
        assertEquals(-72.0, anchor.z(), 1.0E-9);
        Quaternionf facingNorth = new Quaternionf();
        MountPose pose = MountPoses.pose(COM, facingNorth, p, anchor, null);
        assertTrue(pose.anchor().z < COM.z - 0.05, "Halterung vorne liegt nicht vor dem Schwerpunkt: " + pose.anchor());
        assertEquals(-1.0, pose.forward().z, 1.0E-6);
        assertEquals(1.0, pose.right().x, 1.0E-6);
        Quaternionf facingEast = new Quaternionf().rotateY((float) Math.toRadians(-90.0));
        MountPose east = MountPoses.pose(COM, facingEast, p, anchor, null);
        assertEquals(1.0, east.forward().x, 1.0E-5);
        assertTrue(east.anchor().x > COM.x + 0.05);
    }

    @Test
    void framePointsRoundTrip() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        Quaternionf q = new Quaternionf().rotateY(0.7F).rotateX(-0.3F).rotateZ(0.2F);
        Vec3 world = MountPoses.toWorld(COM, q, p, 12.0, -30.0, -80.0);
        Vec3 back = MountPoses.toFrameMetres(COM, q, p, world);
        assertEquals(0.012, back.x, 1.0E-5);
        assertEquals(-0.030, back.y, 1.0E-5);
        assertEquals(-0.080, back.z, 1.0E-5);
    }

    @Test
    void contactDirectionPicksTheMount() {
        DroneBuild build = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY).withAccessory(AccessoryType.LED_STRIP);
        AirframeSpec p = AirframeSpec.of(build);
        Vec3 com = new Vec3(-p.comX, p.comHeight, -p.comZ);
        assertEquals(Optional.of(AccessoryMount.TOP), MountPoses.mountToward(build, p, com.add(0.0, 0.2, -0.1), 0.5));
        assertEquals(Optional.of(AccessoryMount.BOTTOM), MountPoses.mountToward(build, p, com.add(0.0, -0.1, -0.2), 0.5));
        assertEquals(Optional.empty(), MountPoses.mountToward(build, p, com.add(0.0, 0.0, 0.3), 0.5));
        assertEquals(Optional.empty(), MountPoses.mountToward(DroneBuild.FREESTYLE, p, com.add(0.0, 0.0, -0.3), 0.5));
    }
}

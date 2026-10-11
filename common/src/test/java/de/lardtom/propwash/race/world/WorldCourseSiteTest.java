package de.lardtom.propwash.race.world;

import de.lardtom.propwash.race.GateGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldCourseSiteTest {
    private static final Rotation[] BY_TURNS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
    private static final double[][] LOCAL = {{13.0, 1.0, 15.5}, {25.5, 1.0, 45.0}, {0.0, 0.0, 0.0}, {7.25, 3.0, 2.75}};

    @Test
    void rotationsMatchVanillaTemplateTransform() {
        for (int turns = 0; turns < 4; turns++) {
            Rotation rotation = BY_TURNS[turns];
            for (double[] l : LOCAL) {
                Vec3 expected = StructureTemplate.transform(new Vec3(l[0], l[1], l[2]), Mirror.NONE, rotation, BlockPos.ZERO);
                double[] actual = WorldCourseSite.rotateEntity(l[0], l[1], l[2], turns);
                assertArrayEquals(new double[]{expected.x, expected.y, expected.z}, actual, 1.0E-9, rotation.name());
                BlockPos block = new BlockPos((int) Math.floor(l[0]), (int) l[1], (int) Math.floor(l[2]));
                BlockPos expectedBlock = StructureTemplate.transform(block, Mirror.NONE, rotation, BlockPos.ZERO);
                assertArrayEquals(new int[]{expectedBlock.getX(), expectedBlock.getY(), expectedBlock.getZ()},
                        WorldCourseSite.rotateBlock(block.getX(), block.getY(), block.getZ(), turns), rotation.name());
            }
        }
    }

    @Test
    void quarterTurnsFollowEntityRotation() {
        float[] local = {0.0F, 90.0F, 180.0F, 270.0F};
        for (int turns = 0; turns < 4; turns++) {
            for (float yaw : local) {
                float world = GateGeometry.snapYaw(yaw + turns * 90.0F);
                assertEquals(turns, WorldCourseSite.quarterTurns(yaw, world), "yaw " + yaw + " turns " + turns);
            }
        }
    }

    @Test
    void locateFindsTheTemplateOriginFromAnyGate() {
        int ox = 1203;
        int oy = 71;
        int oz = -877;
        for (int turns = 0; turns < 4; turns++) {
            WorldCourseSite reference = null;
            for (double[] l : LOCAL) {
                double[] rotated = WorldCourseSite.rotateEntity(l[0], l[1], l[2], turns);
                float localYaw = 270.0F;
                float worldYaw = GateGeometry.snapYaw(localYaw + turns * 90.0F);
                WorldCourseSite site = WorldCourseSite.locate(l[0], l[1], l[2], localYaw, ox + rotated[0], oy + rotated[1], oz + rotated[2], worldYaw);
                assertEquals(new WorldCourseSite(ox, oy, oz, turns), site);
                if (reference != null) {
                    assertEquals(reference.key(), site.key());
                }
                reference = site;
            }
            int[] board = reference.blockToWorld(6, 1, 2);
            int[] rotated = WorldCourseSite.rotateBlock(6, 1, 2, turns);
            assertArrayEquals(new int[]{ox + rotated[0], oy + 1, oz + rotated[2]}, board);
        }
    }

    @Test
    void siteNeedsGentleSlopeInsideTheWorld() {
        assertTrue(WorldCourseSite.acceptable(new int[]{64, 65, 66, 68, 69}, 5, -64, 300));
        assertFalse(WorldCourseSite.acceptable(new int[]{64, 65, 66, 68, 70}, 5, -64, 300));
        assertFalse(WorldCourseSite.acceptable(new int[]{295, 296}, 5, -64, 290));
        assertFalse(WorldCourseSite.acceptable(new int[0], 5, -64, 290));
    }

    @Test
    void variantKeysAreStable() {
        assertEquals("field_slalom", WorldCourseVariant.FIELD_SLALOM.key());
        assertEquals("propwash.race.world.drone_arena", WorldCourseVariant.DRONE_ARENA.nameKey());
        assertEquals(WorldCourseVariant.CANYON_RUN, WorldCourseVariant.byKey("canyon_run").orElseThrow());
        assertTrue(WorldCourseVariant.byKey("nope").isEmpty());
    }
}

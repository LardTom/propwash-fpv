package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.interact.SnagMaterial;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.Fluid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CauldronContentTest {
    private static final double EPS = 1.0E-6;

    @Test
    void contentHeightFollowsTheFillLevelLikeVanilla() {
        assertEquals(9.0 / 16.0, CauldronContent.WATER.top(1));
        assertEquals(12.0 / 16.0, CauldronContent.WATER.top(2));
        assertEquals(15.0 / 16.0, CauldronContent.WATER.top(3));
        assertEquals(9.0 / 16.0, CauldronContent.POWDER_SNOW.top(1));
        assertEquals(15.0 / 16.0, CauldronContent.POWDER_SNOW.top(3));
        assertEquals(15.0 / 16.0, CauldronContent.LAVA.top(1));
        assertEquals(15.0 / 16.0, CauldronContent.LAVA.top(3));
        assertEquals(9.0 / 16.0, CauldronContent.WATER.top(0));
        assertEquals(15.0 / 16.0, CauldronContent.WATER.top(7));
    }

    @Test
    void waterAndLavaOnlyInsideTheBasinBelowTheFillHeight() {
        for (int level = CauldronContent.MIN_LEVEL; level <= CauldronContent.MAX_LEVEL; level++) {
            double top = CauldronContent.WATER.top(level);
            assertEquals(Fluid.WATER, CauldronContent.WATER.fluidAt(level, 0.5, CauldronContent.FLOOR, 0.5));
            assertEquals(Fluid.WATER, CauldronContent.WATER.fluidAt(level, 0.5, top, 0.5));
            assertEquals(Fluid.NONE, CauldronContent.WATER.fluidAt(level, 0.5, top + EPS, 0.5));
            assertEquals(Fluid.NONE, CauldronContent.WATER.fluidAt(level, 0.5, CauldronContent.FLOOR - EPS, 0.5));
            assertEquals(Fluid.WATER, CauldronContent.WATER.fluidAt(level, CauldronContent.WALL, top - EPS, 1.0 - CauldronContent.WALL));
            assertEquals(Fluid.NONE, CauldronContent.WATER.fluidAt(level, CauldronContent.WALL - EPS, top - EPS, 0.5));
            assertEquals(Fluid.NONE, CauldronContent.WATER.fluidAt(level, 0.5, top - EPS, 1.0 - CauldronContent.WALL + EPS));
        }
        assertEquals(Fluid.LAVA, CauldronContent.LAVA.fluidAt(1, 0.5, 0.5, 0.5));
        assertEquals(Fluid.LAVA, CauldronContent.LAVA.fluidAt(1, 0.5, CauldronContent.LAVA_TOP, 0.5));
        assertEquals(Fluid.NONE, CauldronContent.LAVA.fluidAt(1, 0.5, CauldronContent.LAVA_TOP + EPS, 0.5));
        assertEquals(Fluid.NONE, CauldronContent.LAVA.fluidAt(1, 0.05, 0.5, 0.5));
    }

    @Test
    void powderSnowIsNoFluidLikeThePowderSnowBlock() {
        assertEquals(Fluid.NONE, CauldronContent.POWDER_SNOW.fluid());
        for (int level = CauldronContent.MIN_LEVEL; level <= CauldronContent.MAX_LEVEL; level++) {
            assertEquals(Fluid.NONE, CauldronContent.POWDER_SNOW.fluidAt(level, 0.5, CauldronContent.FLOOR, 0.5));
            assertEquals(Fluid.NONE, CauldronContent.POWDER_SNOW.fluidAt(level, 0.5, 0.5, 0.5));
            assertEquals(Double.NEGATIVE_INFINITY, CauldronContent.POWDER_SNOW.waterSurface(level, 0.5, 0.5, 0.5));
        }
    }

    @Test
    void powderSnowBrakesLikeThePowderSnowBlockWaterAndLavaDoNot() {
        assertEquals(SnagMaterial.POWDER_SNOW, CauldronContent.POWDER_SNOW.snag());
        assertNull(CauldronContent.WATER.snag());
        assertNull(CauldronContent.LAVA.snag());
    }

    @Test
    void waterSurfaceIsRelativeToThePointLikeInAWaterBlock() {
        assertEquals(12.0 / 16.0 - 0.3, CauldronContent.WATER.waterSurface(2, 0.5, 0.3, 0.5), 1.0E-12);
        assertEquals(15.0 / 16.0 - 1.2, CauldronContent.WATER.waterSurface(3, 0.5, 1.2, 0.5), 1.0E-12);
        assertEquals(Double.NEGATIVE_INFINITY, CauldronContent.WATER.waterSurface(3, 0.05, 0.5, 0.5));
        assertEquals(Double.NEGATIVE_INFINITY, CauldronContent.LAVA.waterSurface(1, 0.5, 0.5, 0.5));
    }

    @Test
    void whoopOnTheBasinFloorIsInsideEveryFillLevel() {
        AirframeSpec whoop = AirframeSpec.of(DroneBuild.WHOOP);
        double com = CauldronContent.FLOOR - whoop.hitboxFootOffset;
        assertTrue(com > CauldronContent.FLOOR && com < CauldronContent.WATER.top(1), "Schwerpunkt " + com);
        for (int level = CauldronContent.MIN_LEVEL; level <= CauldronContent.MAX_LEVEL; level++) {
            assertEquals(Fluid.WATER, CauldronContent.WATER.fluidAt(level, 0.5, com, 0.5));
            assertEquals(Fluid.NONE, CauldronContent.POWDER_SNOW.fluidAt(level, 0.5, com, 0.5));
        }
        assertEquals(Fluid.LAVA, CauldronContent.LAVA.fluidAt(1, 0.5, com, 0.5));
    }
}

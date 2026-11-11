package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.CauldronContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CauldronFluidTest {
    private static final double COM = CauldronContent.FLOOR - AirframeSpec.of(DroneBuild.WHOOP).hitboxFootOffset;

    private static FlatWorld inCauldron(CauldronContent content, int level) {
        FlatWorld world = Tests.hovering(DroneBuild.WHOOP, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
        world.env.fluid = content.fluidAt(level, 0.5, COM, 0.5);
        world.env.waterSurface = content.waterSurface(level, 0.5, COM, 0.5);
        return world;
    }

    @Test
    void waterCauldronDisarmsLikeWater() {
        for (int level = CauldronContent.MIN_LEVEL; level <= CauldronContent.MAX_LEVEL; level++) {
            FlatWorld world = inCauldron(CauldronContent.WATER, level);
            world.run(0.2, 100.0);
            assertTrue(world.sim.telemetry().armed);
            world.run(0.2, 100.0);
            assertFalse(world.sim.telemetry().armed);
            assertEquals(DisarmReason.FLUID, world.sim.telemetry().lastDisarmReason);
            assertTrue(ArmBlocker.IN_FLUID.isSet(world.sim.telemetry().armBlockers));
        }
    }

    @Test
    void lavaCauldronDisarmsImmediatelyLikeLava() {
        FlatWorld world = inCauldron(CauldronContent.LAVA, 1);
        world.frame(0L);
        assertFalse(world.sim.telemetry().armed);
        assertEquals(DisarmReason.FLUID, world.sim.telemetry().lastDisarmReason);
        assertTrue(ArmBlocker.IN_FLUID.isSet(world.sim.telemetry().armBlockers));
    }

    @Test
    void powderSnowCauldronLeavesTheDroneArmed() {
        FlatWorld world = inCauldron(CauldronContent.POWDER_SNOW, CauldronContent.MAX_LEVEL);
        world.run(1.0, 100.0);
        assertTrue(world.sim.telemetry().armed);
        assertFalse(ArmBlocker.IN_FLUID.isSet(world.sim.telemetry().armBlockers));
    }
}

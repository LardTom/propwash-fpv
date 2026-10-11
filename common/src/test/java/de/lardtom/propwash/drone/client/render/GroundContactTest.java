package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.DroneBuild;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundContactTest {
    private static final DroneBuild[] PRESETS = {DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER,
            DroneBuild.LONG_RANGE};

    private static float lowest(DronePhysique physique, Quaternionf orientation) {
        return GroundContact.lowest(physique.comAboveFoot(), physique.lowestPoint(orientation));
    }

    @Test
    void levelDroneStaysWhereItIs() {
        for (DroneBuild build : PRESETS) {
            DronePhysique physique = DronePhysique.derive(build);
            assertTrue(physique.hasHull(), build.frame().name());
            for (int yaw = 0; yaw < 360; yaw += 45) {
                float lowest = lowest(physique, new Quaternionf().rotateY((float) Math.toRadians(yaw)));
                assertEquals(0.0F, lowest, 1.0E-6F, build.frame().name());
                assertEquals(0.0F, GroundContact.reach(lowest, 0.0), 0.0F, build.frame().name());
                assertEquals(0.0F, GroundContact.offset(lowest, 0.0, 0.0), 0.0F, build.frame().name());
            }
        }
    }

    @Test
    void droneOnTheGroundRestsOnItsLowestPointInEveryPose() {
        for (DroneBuild build : PRESETS) {
            DronePhysique physique = DronePhysique.derive(build);
            for (int yaw = 0; yaw < 360; yaw += 45) {
                for (int roll = -180; roll <= 180; roll += 15) {
                    for (int pitch = -90; pitch <= 90; pitch += 15) {
                        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch))
                                .rotateZ((float) Math.toRadians(roll));
                        float lowest = lowest(physique, q);
                        float rested = lowest + GroundContact.offset(lowest, 0.0, 0.0);
                        String at = build.frame().name() + " " + yaw + "/" + pitch + "/" + roll;
                        assertEquals(0.0F, rested, GroundContact.MIN_OFFSET + 1.0E-6F, at);
                    }
                }
            }
        }
    }

    @Test
    void sinkDepthMatchesTheHull() {
        DronePhysique freestyle = DronePhysique.derive(DroneBuild.FREESTYLE);
        float rolled = -lowest(freestyle, new Quaternionf().rotateZ((float) Math.toRadians(36.0)));
        assertTrue(rolled > 0.04F && rolled < 0.08F, "5 Zoll 36° gerollt: " + rolled);
        float inverted = -lowest(freestyle, new Quaternionf().rotateZ((float) Math.PI));
        assertTrue(inverted > 0.005F && inverted < 0.04F, "5 Zoll kopfüber: " + inverted);
        DronePhysique whoop = DronePhysique.derive(DroneBuild.WHOOP);
        float whoopRolled = -lowest(whoop, new Quaternionf().rotateZ((float) Math.toRadians(36.0)));
        assertTrue(whoopRolled > 0.001F && whoopRolled < 0.01F, "Whoop 36° gerollt: " + whoopRolled);
        float whoopInverted = lowest(whoop, new Quaternionf().rotateZ((float) Math.PI));
        assertTrue(whoopInverted > 0.002F && whoopInverted < 0.01F, "Whoop kopfüber schwebt: " + whoopInverted);
        float racerInverted = lowest(DronePhysique.derive(DroneBuild.RACER), new Quaternionf().rotateZ((float) Math.PI));
        assertTrue(racerInverted > 0.005F && racerInverted < 0.02F, "Racer kopfüber schwebt: " + racerInverted);
    }

    @Test
    void liftFadesWithTheGapBelow() {
        float lowest = -0.05F;
        assertEquals(0.05F, GroundContact.offset(lowest, 0.0, 0.0), 1.0E-7F);
        assertEquals(0.03F, GroundContact.offset(lowest, 0.0, 0.02), 1.0E-7F);
        assertEquals(0.0F, GroundContact.offset(lowest, 0.0, 0.05), 1.0E-7F);
        assertEquals(0.0F, GroundContact.offset(lowest, 0.0, 0.4), 0.0F);
        float previous = 0.05F;
        for (int i = 0; i <= 100; i++) {
            float lift = GroundContact.offset(lowest, 0.0, i * 0.001);
            assertTrue(lift >= 0.0F && lift <= previous + 1.0E-7F && previous - lift <= 0.001F + 1.0E-6F, "Sprung bei " + i);
            previous = lift;
        }
    }

    @Test
    void floatingHullSettlesOnlyNearTheGround() {
        float lowest = 0.012F;
        assertEquals(-0.012F, GroundContact.offset(lowest, 0.0, 0.0), 1.0E-7F);
        assertEquals(-0.008F, GroundContact.offset(lowest, 0.0, 0.004), 1.0E-7F);
        assertEquals(0.0F, GroundContact.offset(lowest, 0.0, 0.012), 1.0E-7F);
        assertEquals(0.0F, GroundContact.offset(lowest, 0.0, 1.0), 0.0F);
        float previous = -0.012F;
        for (int i = 0; i <= 40; i++) {
            float offset = GroundContact.offset(lowest, 0.0, i * 0.0005);
            assertTrue(offset <= 0.0F && offset >= previous - 1.0E-7F && offset - previous <= 0.0005F + 1.0E-6F, "Sprung bei " + i);
            previous = offset;
        }
    }

    @Test
    void interpolatedPositionCountsAgainstTheGap() {
        float lowest = -0.05F;
        assertEquals(0.0F, GroundContact.reach(lowest, 0.05), 0.0F);
        assertEquals(0.0F, GroundContact.offset(lowest, 0.06, 0.0), 0.0F);
        assertEquals(0.02F, GroundContact.offset(lowest, 0.03, 0.0), 1.0E-7F);
        assertEquals(0.05F, GroundContact.offset(lowest, -0.2, 0.2), 1.0E-6F);
        assertEquals(0.05F, GroundContact.offset(lowest, -0.3, 0.2), 1.0E-6F);
        assertEquals(0.25F, GroundContact.reach(lowest, -0.2), 1.0E-6F);
        assertEquals(-0.01F, GroundContact.offset(0.03F, 0.02, 0.0), 1.0E-7F);
    }

    @Test
    void brokenValuesNeverMoveTheModel() {
        assertEquals(0.0F, GroundContact.lowest(Float.NaN, -0.02F), 0.0F);
        assertEquals(0.0F, GroundContact.lowest(0.02F, Float.NEGATIVE_INFINITY), 0.0F);
        assertEquals(0.0F, GroundContact.offset(Float.NaN, 0.0, 0.0), 0.0F);
        assertEquals(0.0F, GroundContact.offset(-0.05F, Double.NaN, 0.0), 0.0F);
        assertEquals(0.0F, GroundContact.offset(-0.05F, 0.0, Double.NaN), 0.0F);
        assertEquals(0.0F, GroundContact.offset(-0.0003F, 0.0, 0.0), 0.0F);
        assertEquals(0.05F, GroundContact.offset(-0.05F, 0.0, -1.0), 1.0E-7F);
    }
}

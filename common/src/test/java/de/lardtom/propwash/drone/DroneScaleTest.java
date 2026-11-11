package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.world.entity.EntityDimensions;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneScaleTest {
    private static final double EPS = 1.0E-6;

    @Test
    void scaleStaysInsideTheVanillaRange() {
        assertEquals(1.0F, DroneScale.sanitize(1.0));
        assertEquals(3.0F, DroneScale.sanitize(3.0));
        assertEquals(0.0625F, DroneScale.sanitize(0.0625));
        assertEquals(16.0F, DroneScale.sanitize(16.0));
        assertEquals(0.0625F, DroneScale.sanitize(0.01));
        assertEquals(0.0625F, DroneScale.sanitize(-2.0));
        assertEquals(16.0F, DroneScale.sanitize(40.0));
        assertEquals(1.0F, DroneScale.sanitize(Double.NaN));
        assertEquals(1.0F, DroneScale.sanitize(Double.POSITIVE_INFINITY));
        assertFalse(DroneScale.scaled(1.0F));
        assertFalse(DroneScale.scaled(1.00001F));
        assertTrue(DroneScale.scaled(0.5F));
        assertTrue(DroneScale.scaled(3.0F));
        assertEquals(1.0F, DroneScale.of(null));
    }

    @Test
    void percentRoundsForTheAssistText() {
        assertEquals(100, DroneScale.percent(1.0F));
        assertEquals(300, DroneScale.percent(3.0F));
        assertEquals(50, DroneScale.percent(0.5F));
        assertEquals(25, DroneScale.percent(0.25F));
        assertEquals(6, DroneScale.percent(0.0625F));
        assertEquals(1600, DroneScale.percent(40.0F));
    }

    @Test
    void hitboxAtNormalSizeIsUnchanged() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.WHOOP, DroneBuild.LONG_RANGE}) {
            AirframeSpec spec = AirframeSpec.of(build);
            EntityDimensions before = EntityDimensions.fixed((float) spec.hitboxWidth, (float) spec.hitboxHeight);
            EntityDimensions now = DroneScale.dimensions(spec.hitboxWidth, spec.hitboxHeight, 1.0F);
            assertEquals(before.width(), now.width());
            assertEquals(before.height(), now.height());
            assertEquals(before.eyeHeight(), now.eyeHeight());
        }
    }

    @Test
    void hitboxGrowsAndShrinksWithTheScale() {
        AirframeSpec spec = AirframeSpec.of(DroneBuild.FREESTYLE);
        for (float scale : new float[]{0.0625F, 0.25F, 0.5F, 3.0F, 16.0F}) {
            EntityDimensions box = DroneScale.dimensions(spec.hitboxWidth, spec.hitboxHeight, scale);
            assertEquals(spec.hitboxWidth * scale, box.width(), 1.0E-5, "Breite bei " + scale);
            assertEquals(spec.hitboxHeight * scale, box.height(), 1.0E-5, "Höhe bei " + scale);
            assertTrue(box.fixed());
        }
    }

    @Test
    void smallestGateFitsTheNormalDroneButNotTheThreeTimesSevenInch() {
        double opening = GateSize.MIN_OPENING;
        AirframeSpec seven = AirframeSpec.of(DroneBuild.LONG_RANGE);
        assertTrue(seven.hitboxWidth < opening && seven.hitboxHeight < opening, "normale 7-Zoll passt durch " + opening);
        EntityDimensions big = DroneScale.dimensions(seven.hitboxWidth, seven.hitboxHeight, 3.0F);
        assertTrue(big.width() > opening, "3× breiter als die Öffnung: " + big.width());
        AirframeSpec five = AirframeSpec.of(DroneBuild.FREESTYLE);
        assertTrue(DroneScale.dimensions(five.hitboxWidth, five.hitboxHeight, 3.0F).width() < opening, "3× Freestyle passt noch knapp");
        assertTrue(DroneScale.dimensions(five.hitboxWidth, five.hitboxHeight, 3.5F).width() > opening, "3,5× Freestyle passt nicht mehr");
        assertTrue(DroneScale.dimensions(five.hitboxWidth, five.hitboxHeight, 7.0F).width() > GateSize.SMALL.openingWidth(),
                "7× Freestyle passt nicht durch das kleine Gate");
        EntityDimensions tiny = DroneScale.dimensions(five.hitboxWidth, five.hitboxHeight, 0.25F);
        assertTrue(tiny.width() < five.hitboxWidth && tiny.height() < five.hitboxHeight);
    }

    @Test
    void cameraAtNormalSizeIsCentreOfMassPlusLens() {
        Vector3d com = new Vector3d(10.5, 64.03, -3.25);
        Vector3f lens = new Vector3f(0.0F, 0.012F, -0.055F);
        Vector3d eye = DroneScale.eye(com, lens, 0.03, 1.0F, new Vector3d());
        assertEquals(com.x + lens.x, eye.x);
        assertEquals(com.y + lens.y, eye.y);
        assertEquals(com.z + lens.z, eye.z);
        assertEquals(0.0, DroneScale.raise(0.03, 1.0F));
    }

    @Test
    void cameraScalesAroundTheFoot() {
        double foot = 64.0;
        double comAboveFoot = 0.03;
        Vector3d com = new Vector3d(2.0, foot + comAboveFoot, 5.0);
        Vector3f lens = new Vector3f(0.004F, 0.012F, -0.055F);
        for (float scale : new float[]{0.25F, 0.5F, 3.0F, 16.0F}) {
            Vector3d eye = DroneScale.eye(com, lens, comAboveFoot, scale, new Vector3d());
            assertEquals(com.x + lens.x * scale, eye.x, EPS);
            assertEquals(foot + (comAboveFoot + lens.y) * scale, eye.y, EPS, "Höhe über dem Fuß bei " + scale);
            assertEquals(com.z + lens.z * scale, eye.z, EPS);
            assertEquals(comAboveFoot * (scale - 1.0), DroneScale.raise(comAboveFoot, scale), EPS);
        }
    }

    @Test
    void cameraOfTheRealFreestyleSitsThreeTimesAsHighAtThreeTimes() {
        AirframeSpec spec = AirframeSpec.of(DroneBuild.FREESTYLE);
        double comAboveFoot = -spec.hitboxFootOffset;
        Vector3d com = new Vector3d(0.0, comAboveFoot, 0.0);
        Vector3f lens = new Vector3f((float) spec.lensRight, (float) spec.lensUp, (float) -spec.lensForward);
        Vector3d normal = DroneScale.eye(com, lens, comAboveFoot, 1.0F, new Vector3d());
        Vector3d big = DroneScale.eye(com, lens, comAboveFoot, 3.0F, new Vector3d());
        assertEquals(normal.y * 3.0, big.y, 1.0E-5);
        assertEquals(normal.z * 3.0, big.z, 1.0E-5);
        assertTrue(big.y < spec.hitboxHeight * 3.0, "Kamera bleibt in der skalierten Hitbox: " + big.y);
    }
}

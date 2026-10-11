package de.lardtom.propwash.input.client.screen.remote;

import de.lardtom.propwash.drone.client.render.RemoteGimbalModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteProjectionTest {
    private static final float LEFT_X = 4.0F;

    private static float[] tip(RemoteProjection projection, float stickX, float stickY) {
        return projection.stickTip(LEFT_X, RemoteGimbalModel.PIVOT_Y, RemoteGimbalModel.PIVOT_Z, RemoteGimbalModel.STICK_LENGTH, stickX, stickY);
    }

    @Test
    void stickViewShowsFullUpAboveTheGimbalCentre() {
        for (float[] box : new float[][]{{0, 0, 120, 80}, {0, 0, 300, 160}, {10, 20, 74, 76}}) {
            RemoteProjection p = RemoteProjection.fit(RemoteProjection.Region.WHOLE, box[0], box[1], box[2], box[3]);
            float centre = p.screenY(LEFT_X, RemoteGimbalModel.PIVOT_Y, RemoteGimbalModel.PIVOT_Z);
            float up = tip(p, 0.0F, 1.0F)[1];
            float neutral = tip(p, 0.0F, 0.0F)[1];
            float down = tip(p, 0.0F, -1.0F)[1];
            float travel = RemoteGimbalModel.STICK_LENGTH * p.pixelScale();
            assertTrue(up < centre - 0.1F * travel, "Gas ganz oben muss über der Knüppelmitte liegen");
            assertTrue(down > centre + 0.3F * travel, "Gas ganz unten muss deutlich unter der Mitte liegen");
            assertTrue(up < neutral && neutral < down);
            assertTrue(neutral - up > 0.3F * travel && down - neutral > 0.3F * travel, "oben und unten deutlich von der Ruhelage getrennt");
        }
    }

    @Test
    void sidewaysStickMovesTheTipSideways() {
        RemoteProjection p = RemoteProjection.fit(RemoteProjection.Region.WHOLE, 0, 0, 200, 120);
        float centreX = p.screenX(LEFT_X, RemoteGimbalModel.PIVOT_Y, RemoteGimbalModel.PIVOT_Z);
        float right = tip(p, 1.0F, 0.0F)[0];
        float left = tip(p, -1.0F, 0.0F)[0];
        assertTrue(right > centreX && left < centreX);
        assertEquals(right - centreX, centreX - left, 1.0E-3F);
    }

    @Test
    void closeUpsKeepTheSteeperView() {
        assertEquals(15.0F, RemoteProjection.Region.WHOLE.tiltDegrees(), 0.0F);
        assertEquals(30.0F, RemoteProjection.Region.SWITCHES.tiltDegrees(), 0.0F);
        assertEquals(30.0F, RemoteProjection.Region.BUTTONS.tiltDegrees(), 0.0F);
        RemoteProjection p = RemoteProjection.fit(RemoteProjection.Region.SWITCHES, 0, 0, 100, 60);
        assertEquals(30.0F, p.tiltDegrees(), 0.0F);
    }
}

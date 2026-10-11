package de.lardtom.propwash.guide;

import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.CameraSpec;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePlayback;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneProjection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenePlaybackTest {
    private static Scene scene() {
        return SceneBuilder.scene("playback")
                .icon("propwash:drone")
                .step("one", 40.0F, s -> s.block(0, 0, 0, BlockSpec.of("minecraft:stone")))
                .step("two", 40.0F, s -> {
                })
                .step("three", 40.0F, s -> {
                })
                .build();
    }

    @Test
    void advancesAndStopsAtTheEnd() {
        ScenePlayback playback = new ScenePlayback(scene());
        assertTrue(playback.playing());
        playback.advance(3.0F);
        assertEquals(3.0F, playback.time(), 1.0E-5F);
        playback.advance(100.0F);
        assertEquals(3.0F + ScenePlayback.MAX_FRAME_TICKS, playback.time(), 1.0E-5F);
        playback.seek(119.0F);
        playback.advance(4.0F);
        assertTrue(playback.finished());
        assertFalse(playback.playing());
        playback.toggle();
        assertTrue(playback.playing());
        assertEquals(0.0F, playback.time());
    }

    @Test
    void nextAndPreviousJumpBetweenSteps() {
        ScenePlayback playback = new ScenePlayback(scene());
        playback.next();
        assertEquals(1, playback.step());
        assertEquals(40.0F, playback.time());
        playback.seek(70.0F);
        playback.previous();
        assertEquals(40.0F, playback.time());
        playback.previous();
        assertEquals(0.0F, playback.time());
        playback.previous();
        assertEquals(0.0F, playback.time());
        playback.jumpTo(2);
        playback.next();
        assertTrue(playback.finished());
        playback.previous();
        assertEquals(80.0F, playback.time());
        playback.replay();
        assertEquals(0.0F, playback.time());
        assertTrue(playback.playing());
    }

    @Test
    void pauseStopsTime() {
        ScenePlayback playback = new ScenePlayback(scene());
        playback.pause();
        playback.advance(4.0F);
        assertEquals(0.0F, playback.time());
        playback.play();
        playback.advance(Float.NaN);
        assertEquals(0.0F, playback.time());
    }

    @Test
    void projectionMatchesCameraConventions() {
        CameraSpec camera = new CameraSpec(new ScenePos(1.0F, 2.0F, 3.0F), 0.0F, 0.0F, 2.0F);
        SceneProjection projection = new SceneProjection(camera, 10.0F, 100.0F, 50.0F);
        float[] center = projection.project(new ScenePos(1.0F, 2.0F, 3.0F));
        assertEquals(100.0F, center[0], 1.0E-4F);
        assertEquals(50.0F, center[1], 1.0E-4F);
        float[] right = projection.project(new ScenePos(2.0F, 2.0F, 3.0F));
        assertEquals(120.0F, right[0], 1.0E-3F);
        float[] up = projection.project(new ScenePos(1.0F, 3.0F, 3.0F));
        assertEquals(30.0F, up[1], 1.0E-3F);
        float[] near = projection.project(new ScenePos(1.0F, 2.0F, 4.0F));
        assertTrue(near[2] > center[2]);

        SceneProjection side = new SceneProjection(new CameraSpec(ScenePos.ZERO, 90.0F, 0.0F, 1.0F), 10.0F, 0.0F, 0.0F);
        assertEquals(-10.0F, side.project(new ScenePos(0.0F, 0.0F, 1.0F))[0], 1.0E-3F);
        assertTrue(side.project(new ScenePos(1.0F, 0.0F, 0.0F))[2] > 0.0F);

        SceneProjection top = new SceneProjection(new CameraSpec(ScenePos.ZERO, 0.0F, 89.0F, 1.0F), 10.0F, 0.0F, 0.0F);
        assertTrue(top.project(new ScenePos(0.0F, 0.0F, 1.0F))[1] > 9.0F);
        assertEquals(20.0F, SceneProjection.basePixelsPerBlock(40.0F, 80.0F, 1.0F), 1.0E-4F);
    }

    @Test
    void cameraSpecClampsAndWraps() {
        CameraSpec camera = new CameraSpec(ScenePos.ZERO, 0.0F, 120.0F, 100.0F);
        assertEquals(CameraSpec.MAX_PITCH, camera.pitch());
        assertEquals(CameraSpec.MAX_ZOOM, camera.zoom());
        assertEquals(-10.0F, CameraSpec.wrapDegrees(350.0F), 1.0E-4F);
        assertEquals(170.0F, CameraSpec.wrapDegrees(-190.0F), 1.0E-4F);
        CameraSpec a = new CameraSpec(ScenePos.ZERO, 170.0F, 0.0F, 1.0F);
        CameraSpec b = new CameraSpec(ScenePos.ZERO, -170.0F, 0.0F, 1.0F);
        assertEquals(180.0F, a.lerp(b, 0.5F).yaw(), 1.0E-3F);
    }
}

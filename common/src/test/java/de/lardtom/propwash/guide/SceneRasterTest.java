package de.lardtom.propwash.guide;

import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.CameraSpec;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.SurvivalScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneRasterTest {
    private static final int[][] WINDOWS = {
            {854, 480, 1}, {854, 480, 2}, {1280, 720, 2}, {1920, 1080, 3}, {1920, 1080, 4}, {2000, 1300, 2}, {2560, 1600, 4},
            {2880, 1800, 3}, {3440, 1440, 3}, {2560, 1080, 2}, {1024, 1280, 2}};
    private static final float[][] ORBITS = {{0.0F, 0.0F, 1.0F}, {35.0F, 15.0F, 1.6F}, {-80.0F, -5.0F, 0.45F}, {170.0F, 40.0F, 3.5F}};
    private static final float TOLERANCE = 0.05F;
    private static final float SAMPLE = 10.0F;

    private record Viewport(float left, float top, float width, float height) {
    }

    private static Viewport viewport(int windowWidth, int windowHeight, int guiScale) {
        int guiWidth = (int) Math.ceil((double) windowWidth / guiScale);
        int guiHeight = (int) Math.ceil((double) windowHeight / guiScale);
        int w = Math.min(guiWidth - 12, 760);
        int h = Math.min(guiHeight - 12, 470);
        int left = (guiWidth - w) / 2 + 6;
        int top = (guiHeight - h) / 2 + 26;
        return new Viewport(left, top, Math.max(40, w - 12), Math.max(40, h - 26 - 62));
    }

    private static List<ScenePos> targets(SceneState state) {
        List<ScenePos> targets = new ArrayList<>();
        for (SceneState.CalloutView callout : state.callouts()) {
            targets.add(callout.anchor());
        }
        for (SceneState.KeyHintView hint : state.keyHints()) {
            if (hint.anchor() != null) {
                targets.add(hint.anchor());
            }
        }
        for (ActorState actor : state.visibleActors()) {
            targets.add(actor.position());
        }
        return targets;
    }

    @Test
    void rasterMatchesCalloutProjectionAtEverySizeScaleAndOrbit() {
        float worst = 0.0F;
        int checked = 0;
        float[] raster = new float[3];
        for (Scene scene : GuideScenes.all()) {
            SceneFraming framing = SceneFraming.of(scene);
            for (float t = 0.0F; t <= scene.duration(); t += SAMPLE) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                List<ScenePos> targets = targets(state);
                for (int[] window : WINDOWS) {
                    Viewport vp = viewport(window[0], window[1], window[2]);
                    for (float[] orbit : ORBITS) {
                        CameraSpec camera = state.camera().withOffsets(orbit[0], orbit[1], 1.0F);
                        SceneProjection projection = framing.projection(camera, state.time(),
                                SceneProjection.basePixelsPerBlock(vp.width(), vp.height(), scene.viewRadius()), vp.left(), vp.top(), vp.width(),
                                vp.height(), orbit[2]);
                        Matrix4f matrix = projection.rasterMatrix(vp.left(), vp.top(), window[2], new Matrix4f());
                        for (ScenePos target : targets) {
                            float[] gui = projection.project(target);
                            SceneProjection.rasterToGui(matrix, vp.left(), vp.top(), window[2], target.x(), target.y(), target.z(), raster);
                            float error = Math.max(Math.abs(gui[0] - raster[0]), Math.abs(gui[1] - raster[1]));
                            worst = Math.max(worst, error);
                            checked++;
                            assertTrue(error <= TOLERANCE, scene.id() + " t=" + t + " " + window[0] + "x" + window[1] + "@" + window[2]
                                    + " Orbit " + orbit[0] + ": " + gui[0] + "/" + gui[1] + " gegen " + raster[0] + "/" + raster[1]);
                        }
                    }
                }
            }
        }
        assertTrue(checked > 1000, "zu wenige Punkte geprüft: " + checked);
        assertTrue(worst <= TOLERANCE, "größte Abweichung " + worst);
    }

    @Test
    void rasterEqualsVanillaPipBaseWhenCentered() {
        CameraSpec camera = new CameraSpec(new ScenePos(1.0F, 2.0F, -3.0F), 40.0F, 25.0F, 1.3F);
        float left = 12.0F;
        float top = 30.0F;
        float width = 600.0F;
        float height = 300.0F;
        int guiScale = 3;
        SceneProjection projection = new SceneProjection(camera, 20.0F, left + width / 2.0F, top + height / 2.0F);
        Matrix4f raster = projection.rasterMatrix(left, top, guiScale, new Matrix4f());
        float k = guiScale * projection.pixelsPerBlock();
        Matrix4f vanilla = new Matrix4f().translate(width * guiScale / 2.0F, height * guiScale / 2.0F, 0.0F).scale(k, k, -k)
                .rotate(new Quaternionf().rotationX((float) Math.PI).mul(projection.view()))
                .translate(-camera.target().x(), -camera.target().y(), -camera.target().z());
        for (int i = 0; i < 16; i++) {
            assertEquals(vanilla.get(i / 4, i % 4), raster.get(i / 4, i % 4), 1.0E-3F, "Element " + i);
        }
    }

    @Test
    void framingOffsetReachesTheRaster() {
        Scene scene = GuideScenes.byId(SurvivalScene.ID);
        assertNotNull(scene);
        SceneFraming framing = SceneFraming.of(scene);
        Viewport vp = viewport(2000, 1300, 2);
        float t = scene.stepStart(1) + 70.0F;
        SceneState state = SceneEvaluator.evaluate(scene, t);
        SceneProjection projection = framing.projection(state.camera(), t,
                SceneProjection.basePixelsPerBlock(vp.width(), vp.height(), scene.viewRadius()), vp.left(), vp.top(), vp.width(), vp.height(), 1.0F);
        float offsetX = projection.centerX() - (vp.left() + vp.width() / 2.0F);
        float offsetY = projection.centerY() - (vp.top() + vp.height() / 2.0F);
        assertTrue(Math.abs(offsetX) + Math.abs(offsetY) > 4.0F, "Bildausschnitt verschiebt die Mitte nicht, Test ohne Aussage");
        float[] out = SceneProjection.rasterToGui(projection.rasterMatrix(vp.left(), vp.top(), 2, new Matrix4f()), vp.left(), vp.top(), 2,
                state.camera().target().x(), state.camera().target().y(), state.camera().target().z(), new float[3]);
        assertEquals(projection.centerX(), out[0], TOLERANCE);
        assertEquals(projection.centerY(), out[1], TOLERANCE);
    }

    @Test
    void rasterOriginIsTheViewportCorner() {
        SceneProjection projection = new SceneProjection(new CameraSpec(ScenePos.ZERO, 0.0F, 0.0F, 1.0F), 10.0F, 110.0F, 60.0F);
        for (int guiScale = 1; guiScale <= 4; guiScale++) {
            Matrix4f raster = projection.rasterMatrix(100.0F, 40.0F, guiScale, new Matrix4f());
            Vector3f p = raster.transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
            assertEquals(10.0F * guiScale, p.x, 1.0E-3F);
            assertEquals(20.0F * guiScale, p.y, 1.0E-3F);
            Vector3f up = raster.transformPosition(0.0F, 1.0F, 0.0F, new Vector3f());
            assertEquals(10.0F * guiScale, 20.0F * guiScale - up.y, 1.0E-3F);
        }
    }
}

package de.lardtom.propwash.guide;

import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.GateFreeScene;
import de.lardtom.propwash.guide.scenes.SceneKit;
import de.lardtom.propwash.race.FreeformFill;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.LapTracker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeformSceneTest {
    private static Scene scene() {
        Scene scene = GuideScenes.byId(GateFreeScene.ID);
        assertNotNull(scene);
        return scene;
    }

    private static int step(Scene scene, String name) {
        for (int i = 0; i < scene.stepCount(); i++) {
            if (scene.steps().get(i).name().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("Schritt " + name + " fehlt");
    }

    @Test
    void theCaveMouthInTheDioramaIsExactlyTheStoredRegion() {
        Scene scene = scene();
        int freeform = step(scene, "freeform");
        SceneState end = SceneEvaluator.evaluate(scene, scene.stepEnd(freeform) - 1.0F);
        ActorState gate = end.actor(GateFreeScene.CAVE_GATE);
        assertNotNull(gate);
        ActorSpec.Gate spec = (ActorSpec.Gate) gate.spec();
        assertTrue(spec.isFreeform());
        FreeformRegion region = FreeformRegion.decode(spec.region());
        assertEquals(GateFreeScene.CAVE_REGION, region);
        FreeformFill.Probe probe = (x, y, z) -> {
            if (y > 12) {
                return FreeformFill.Cell.OUTSIDE;
            }
            return end.block(x, y, z) != null ? FreeformFill.Cell.SOLID : FreeformFill.Cell.OPEN;
        };
        FreeformFill.Result fill = FreeformFill.fill(probe, FreeformRegion.AXIS_Z, GateFreeScene.CAVE_MIN_X, GateFreeScene.CAVE_MIN_Y,
                GateFreeScene.CAVE_Z, 20);
        assertEquals(FreeformFill.Status.OK, fill.status());
        assertEquals(region, fill.region(), "Flutfüllung der Blöcke ergibt die gespeicherte Fläche");
        ScenePos pos = gate.position();
        assertEquals(fill.anchorX(), pos.x(), 1.0E-6);
        assertEquals(fill.anchorY(), pos.y(), 1.0E-6);
        assertEquals(fill.anchorZ(), pos.z(), 1.0E-6);
        assertTrue(gate.lit(), "beim Durchflug leuchtet das Gate grün");
        assertTrue(scene.items().ids().contains(SceneKit.FREEFORM_GATE));
    }

    @Test
    void theDroneFliesThroughTheOpeningAndTheLapTrackerCountsIt() {
        Scene scene = scene();
        int freeform = step(scene, "freeform");
        float start = scene.stepStart(freeform) + GateFreeScene.FREEFORM_FLY;
        float end = start + GateFreeScene.FREEFORM_FLY_LENGTH;
        GateGeometry gate = GateGeometry.freeform(GateFreeScene.CAVE_POS.x(), GateFreeScene.CAVE_POS.y(), GateFreeScene.CAVE_POS.z(),
                GateFreeScene.CAVE_REGION, GateFreeScene.CAVE_VARIANT);
        List<ScenePos> points = new ArrayList<>();
        for (float t = start; t <= end; t += 0.25F) {
            ActorState drone = SceneEvaluator.evaluate(scene, t).actor(GateFreeScene.DRONE);
            assertNotNull(drone);
            points.add(drone.position());
        }
        int forward = 0;
        for (int i = 1; i < points.size(); i++) {
            ScenePos a = points.get(i - 1);
            ScenePos b = points.get(i);
            if (gate.stageCrossing(0, a.x(), a.y(), a.z(), b.x(), b.y(), b.z()) >= 0.0) {
                forward++;
            }
            assertTrue(gate.stages().getFirst().crossing(a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), false) < 0.0, "nie rückwärts");
        }
        assertEquals(1, forward, "genau ein Durchflug");
        LapTracker tracker = new LapTracker();
        tracker.setCourse(1, List.of(gate), 0L);
        long now = 0L;
        for (ScenePos p : points) {
            now += 12_500_000L;
            tracker.update(now, p.x(), p.y(), p.z());
        }
        assertEquals(LapTracker.EventType.START, tracker.drainEvents().getFirst().type());
    }
}

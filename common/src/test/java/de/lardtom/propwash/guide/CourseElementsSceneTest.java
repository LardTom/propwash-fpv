package de.lardtom.propwash.guide;

import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.CourseElementsScene;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.Passage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseElementsSceneTest {
    private static Scene scene() {
        Scene scene = GuideScenes.byId(CourseElementsScene.ID);
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

    private static GateGeometry geometry(SceneState state, String id) {
        ActorState actor = state.actor(id);
        assertNotNull(actor, id);
        ActorSpec.Gate spec = (ActorSpec.Gate) actor.spec();
        assertTrue(spec.isElement(), id);
        CourseElement kind = CourseElement.byKey(spec.element());
        double width = kind.outerWidth(spec.openingWidth() > 0.0F ? spec.openingWidth() : kind.defaultWidth());
        double height = kind.outerHeight(spec.openingHeight() > 0.0F ? spec.openingHeight() : kind.defaultHeight());
        ScenePos pos = actor.position();
        return GateGeometry.element(pos.x(), pos.y(), pos.z(), actor.yaw(), kind, spec.variant(), width, height, GateTilt.NONE);
    }

    private static List<ScenePos> flight(Scene scene) {
        int fly = step(scene, "fly");
        float start = scene.stepStart(fly) + CourseElementsScene.FLY_START;
        float end = scene.stepStart(fly) + CourseElementsScene.FLY_START + CourseElementsScene.FLY_LENGTH;
        List<ScenePos> points = new ArrayList<>();
        for (float t = start; t <= end; t += 0.25F) {
            ActorState drone = SceneEvaluator.evaluate(scene, t).actor(CourseElementsScene.DRONE);
            assertNotNull(drone);
            points.add(drone.position());
        }
        return points;
    }

    private static int complete(GateGeometry element, List<ScenePos> path, int from) {
        int stage = 0;
        for (int i = Math.max(1, from); i < path.size(); i++) {
            ScenePos a = path.get(i - 1);
            ScenePos b = path.get(i);
            if (element.stageCrossing(stage, a.x(), a.y(), a.z(), b.x(), b.y(), b.z()) >= 0.0) {
                stage++;
                if (stage == element.kind().stages()) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void noFoul(GateGeometry element, List<ScenePos> path) {
        for (int stage = 0; stage < element.kind().stages(); stage++) {
            if (element.kind() == CourseElement.CUBE && stage == 0) {
                continue;
            }
            for (Passage foul : element.fouls(stage)) {
                for (int i = 1; i < path.size(); i++) {
                    ScenePos a = path.get(i - 1);
                    ScenePos b = path.get(i);
                    assertTrue(foul.crossing(a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), true) < 0.0, element.kind() + " falsche Seite bei " + a);
                }
            }
        }
    }

    @Test
    void theDroneFliesEveryElementTheWayItCounts() {
        Scene scene = scene();
        SceneState placed = SceneEvaluator.evaluate(scene, scene.stepStart(step(scene, "fly")) + 1.0F);
        List<ScenePos> path = flight(scene);
        int at = 0;
        for (int i = 0; i < CourseElementsScene.POLES.length; i++) {
            GateGeometry pole = geometry(placed, CourseElementsScene.POLES[i]);
            assertEquals(CourseElementsScene.POLE_SIDE[i], pole.variant());
            int done = complete(pole, path, at);
            assertTrue(done > at, "Stange " + (i + 1) + " auf ihrer Seite");
            noFoul(pole, path.subList(Math.max(0, at - 1), done + 1));
            at = done;
        }
        GateGeometry flag = geometry(placed, CourseElementsScene.FLAG);
        int flagDone = complete(flag, path, at);
        assertTrue(flagDone > at, "Flagge umrundet");
        at = flagDone;
        GateGeometry cube = geometry(placed, CourseElementsScene.CUBE);
        int cubeDone = complete(cube, path, at);
        assertTrue(cubeDone > at, "Würfel von oben und zur Ausgangsseite");
        noFoul(cube, path.subList(at, path.size()));
    }

    @Test
    void polesAlternateAndTheSceneFitsTheFlightSchool() {
        Scene scene = scene();
        assertTrue(scene.stepCount() >= 3 && scene.stepCount() <= 8);
        assertTrue(scene.duration() <= 60.0F * 20.0F);
        for (int i = 1; i < CourseElementsScene.POLE_SIDE.length; i++) {
            assertTrue(CourseElementsScene.POLE_SIDE[i] != CourseElementsScene.POLE_SIDE[i - 1], "Slalom wechselt die Seite");
        }
    }
}

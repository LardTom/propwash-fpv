package de.lardtom.propwash.trick.tutorial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.trick.TrickType;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickTutorialTest {
    private static final int MAX_STEP_TEXT = 190;

    private static JsonObject lang(String file) throws IOException {
        Path path = Path.of("common/src/lang/trick", file);
        if (!Files.exists(path)) {
            path = Path.of("src/lang/trick", file);
        }
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyTrickBuildsAScenePerStageWithTexts() throws IOException {
        JsonObject en = lang("en_us.json");
        JsonObject de = lang("de_de.json");
        List<Executable> checks = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (TrickType type : TrickType.REPERTOIRE) {
            checks.add(() -> {
                TrickTutorial tutorial = TrickTutorial.create(type);
                Scene scene = tutorial.scene();
                List<String> stages = tutorial.choreography().stages();
                assertEquals(stages.size(), scene.stepCount(), type + ": Schritte");
                assertTrue(scene.stepCount() >= 3, type + ": nur " + scene.stepCount() + " Schritte");
                assertTrue(scene.duration() >= 40.0F && scene.duration() <= 400.0F, type + ": Dauer " + scene.duration());
                List<String> keys = new ArrayList<>();
                keys.add(tutorial.titleKey());
                for (int step = 0; step < scene.stepCount(); step++) {
                    keys.add(tutorial.stepTitleKey(step));
                    keys.add(tutorial.stepTextKey(step));
                }
                for (String key : keys) {
                    used.add(key);
                    assertTrue(en.has(key), type + ": englischer Text fehlt " + key);
                    assertTrue(de.has(key), type + ": deutscher Text fehlt " + key);
                }
                for (int step = 0; step < scene.stepCount(); step++) {
                    String key = tutorial.stepTextKey(step);
                    if (en.has(key) && de.has(key)) {
                        assertTrue(en.get(key).getAsString().length() <= MAX_STEP_TEXT, key + " englisch zu lang");
                        assertTrue(de.get(key).getAsString().length() <= MAX_STEP_TEXT, key + " deutsch zu lang");
                    }
                }
            });
        }
        assertAll(checks);
        for (String key : en.keySet()) {
            if (key.startsWith(TrickChoreography.TEXT_PREFIX)) {
                assertTrue(used.contains(key), "ungenutzter Text " + key);
            }
        }
        assertEquals(en.keySet(), de.keySet(), "Schlüssel englisch und deutsch verschieden");
    }

    private static Quaterniond sceneModel(float yaw, float pitch, float roll) {
        return new Quaterniond().rotateY(Math.PI - Math.toRadians(yaw)).rotateX(Math.toRadians(-pitch)).rotateZ(Math.toRadians(roll));
    }

    @Test
    void sceneAttitudeMatchesTheDroneInFlight() {
        List<TrickType> smooth = List.of(TrickType.FLIP, TrickType.POWER_LOOP, TrickType.RUBIKS_CUBE, TrickType.MCTWIST, TrickType.ORBIT,
                TrickType.HAMMERHEAD, TrickType.SIDE_TRIPPY, TrickType.SPLIT_S, TrickType.VANNY_ROLL);
        for (TrickType type : TrickType.REPERTOIRE) {
            TrickFlight flight = TrickFlight.fly(TrickChoreographies.of(type), null);
            float[] rotation = {0.0F, 0.0F, 0.0F};
            for (TrickFlight.Frame frame : flight.frames()) {
                float[] next = TrickTutorial.sceneRotation(frame.qx(), frame.qy(), frame.qz(), frame.qw(), rotation);
                Quaterniond back = sceneModel(next[0], next[1], next[2]);
                Quaterniond inFlight = new Quaterniond(frame.qx(), frame.qy(), frame.qz(), frame.qw()).normalize().rotateY(Math.PI);
                double dot = Math.abs(back.x * inFlight.x + back.y * inFlight.y + back.z * inFlight.z + back.w * inFlight.w);
                assertTrue(dot > 0.9999, type + " bei " + frame.time() + " s: Lage weicht von der Drohne im Flug ab (" + dot + ")");
                for (int i = 0; i < 3 && smooth.contains(type); i++) {
                    assertTrue(Math.abs(next[i] - rotation[i]) < 45.0F, type + " bei " + frame.time() + " s: Sprung im Winkel " + i);
                }
                rotation = next;
            }
        }
    }

    @Test
    void droneFliesCameraFirstInEveryTrick() {
        List<Executable> checks = new ArrayList<>();
        Set<TrickType> checked = new HashSet<>();
        for (TrickType type : TrickType.REPERTOIRE) {
            checks.add(() -> {
                TrickTutorial tutorial = TrickTutorial.create(type);
                Scene scene = tutorial.scene();
                List<String> stages = tutorial.choreography().stages();
                for (TrickMove move : tutorial.choreography().moves()) {
                    if (!(move instanceof TrickMove.Fly fly) || fly.tilt() < 10.0 || fly.bank() != 0.0 || fly.turn() != 0.0) {
                        continue;
                    }
                    int step = stages.indexOf(fly.stage());
                    float from = scene.stepStart(step) + (scene.stepEnd(step) - scene.stepStart(step)) * 0.5F;
                    for (float t = from; t + 1.0F <= scene.stepEnd(step); t += 1.0F) {
                        ActorState a = SceneEvaluator.evaluate(scene, t).actor(TrickTutorial.DRONE);
                        ActorState b = SceneEvaluator.evaluate(scene, t + 1.0F).actor(TrickTutorial.DRONE);
                        assertNotNull(a);
                        assertNotNull(b);
                        double dx = b.position().x() - a.position().x();
                        double dz = b.position().z() - a.position().z();
                        double speed = Math.hypot(dx, dz);
                        if (speed < 0.02) {
                            continue;
                        }
                        Vector3d camera = sceneModel(a.yaw(), a.pitch(), a.roll()).transform(new Vector3d(0.0, 0.0, -1.0));
                        double ahead = (camera.x * dx + camera.z * dz) / (speed * Math.max(1.0E-9, Math.hypot(camera.x, camera.z)));
                        assertTrue(ahead > 0.8, type + " in " + fly.stage() + " bei " + t + ": Kamera zeigt nicht in Flugrichtung (" + ahead + ")");
                        synchronized (checked) {
                            checked.add(type);
                        }
                    }
                }
            });
        }
        assertAll(checks);
        assertTrue(checked.containsAll(List.of(TrickType.POWER_LOOP, TrickType.SPLIT_S, TrickType.VANNY_ROLL, TrickType.MATTY_FLIP,
                TrickType.GAP, TrickType.HAMMERHEAD)), "geprüft: " + checked);
        assertTrue(checked.size() >= 30, "nur " + checked.size() + " Tricks mit Vorwärtsflug geprüft");
    }

    private static Vector3d modelAxis(ActorState actor, double x, double y, double z) {
        return sceneModel(actor.yaw(), actor.pitch(), actor.roll()).transform(new Vector3d(x, y, z));
    }

    private static ActorState droneAt(TrickTutorial tutorial, String stage, float fraction) {
        Scene scene = tutorial.scene();
        int step = tutorial.choreography().stages().indexOf(stage);
        assertTrue(step >= 0, tutorial.trick() + " ohne Schritt " + stage);
        ActorState actor = SceneEvaluator.evaluate(scene, scene.stepStart(step) + (scene.stepEnd(step) - scene.stepStart(step)) * fraction)
                .actor(TrickTutorial.DRONE);
        assertNotNull(actor);
        return actor;
    }

    @Test
    void sticksTurnTheDroneTheVisibleWay() {
        TrickTutorial flip = TrickTutorial.create(TrickType.FLIP);
        ActorState flipStart = droneAt(flip, "rotation", 0.0F);
        ActorState flipEarly = droneAt(flip, "rotation", 0.15F);
        assertTrue(flipEarly.rightY() > 0.5F, "Flip ohne Nick nach vorn: " + flipEarly.rightY());
        assertTrue(modelAxis(flipEarly, 0.0, 0.0, -1.0).y < modelAxis(flipStart, 0.0, 0.0, -1.0).y - 0.2,
                "Nick nach vorn senkt die Kamera nicht");

        TrickTutorial loop = TrickTutorial.create(TrickType.POWER_LOOP);
        ActorState loopStart = droneAt(loop, "pull_up", 0.0F);
        ActorState loopEarly = droneAt(loop, "pull_up", 0.3F);
        assertTrue(loopEarly.rightY() < -0.2F, "Power Loop ohne Nick nach hinten: " + loopEarly.rightY());
        assertTrue(modelAxis(loopEarly, 0.0, 0.0, -1.0).y > modelAxis(loopStart, 0.0, 0.0, -1.0).y + 0.2,
                "Nick nach hinten hebt die Kamera nicht");

        TrickTutorial roll = TrickTutorial.create(TrickType.ROLL);
        ActorState rollEarly = droneAt(roll, "rotation", 0.15F);
        assertTrue(rollEarly.rightX() > 0.5F, "Rolle ohne Roll nach rechts: " + rollEarly.rightX());
        assertTrue(modelAxis(rollEarly, 1.0, 0.0, 0.0).y < -0.2, "Roll nach rechts senkt die rechte Seite nicht");

        TrickTutorial yaw = TrickTutorial.create(TrickType.YAW_SPIN);
        ActorState yawStart = droneAt(yaw, "spin", 0.0F);
        ActorState yawEarly = droneAt(yaw, "spin", 0.08F);
        assertTrue(yawEarly.leftX() > 0.5F, "Yaw-Spin ohne Gier nach rechts: " + yawEarly.leftX());
        assertTrue(modelAxis(yawEarly, 0.0, 0.0, -1.0).dot(modelAxis(yawStart, 1.0, 0.0, 0.0)) > 0.2, "Gier nach rechts dreht nicht nach rechts");
    }

    @Test
    void flipSceneTurnsTheDroneAndMovesThePitchStick() {
        TrickTutorial tutorial = TrickTutorial.create(TrickType.FLIP);
        Scene scene = tutorial.scene();
        int rotation = tutorial.choreography().stages().indexOf("rotation");
        assertTrue(rotation > 0);
        SceneState before = SceneEvaluator.evaluate(scene, scene.stepStart(rotation));
        SceneState middle = SceneEvaluator.evaluate(scene, (scene.stepStart(rotation) + scene.stepEnd(rotation)) * 0.5F);
        SceneState after = SceneEvaluator.evaluate(scene, scene.stepEnd(rotation));
        ActorState a = before.actor(TrickTutorial.DRONE);
        ActorState m = middle.actor(TrickTutorial.DRONE);
        ActorState b = after.actor(TrickTutorial.DRONE);
        assertNotNull(a);
        assertNotNull(m);
        assertNotNull(b);
        assertTrue(Math.abs(m.pitch() - a.pitch()) > 90.0F, "Drohne dreht nicht: " + a.pitch() + " → " + m.pitch());
        assertTrue(Math.abs(b.pitch() - a.pitch()) > 300.0F, "kein ganzer Flip: " + a.pitch() + " → " + b.pitch());
        assertTrue(Math.abs(m.rightY()) > 0.5F, "Nick-Knüppel in der Drehung: " + m.rightY());
        assertTrue(Math.abs(m.rightX()) < 0.1F && Math.abs(m.leftX()) < 0.1F, "Roll oder Gier in einem Flip");
        assertTrue(m.armed() && m.throttle() > 0.0F);
        assertTrue(tutorial.pathMarks() > 10, "Flugbahn ohne Spur");
        assertNotNull(before.actor(TrickTutorial.PATH_PREFIX + "0"));
    }

    @Test
    void obstaclesStandBesideThePathWithoutTouchingTheDrone() {
        for (TrickType type : List.of(TrickType.GAP, TrickType.PROXIMITY, TrickType.WALL_RIDE)) {
            TrickTutorial tutorial = TrickTutorial.create(type);
            assertTrue(tutorial.obstacleBlocks() > 0, type + " ohne Hindernis");
            Scene scene = tutorial.scene();
            for (float t = 0.0F; t <= scene.duration(); t += 1.0F) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                ActorState drone = state.actor(TrickTutorial.DRONE);
                assertNotNull(drone);
                ScenePos d = drone.position();
                for (ActorState actor : state.visibleActors()) {
                    if (!actor.id().startsWith("obstacle_")) {
                        continue;
                    }
                    ScenePos o = actor.position();
                    float reach = 0.5F + TrickTutorial.DRONE_CLEARANCE * 0.8F;
                    boolean inside = Math.abs(d.x() - o.x()) < reach && d.y() >= o.y() - 0.5F && d.y() <= o.y() + 1.5F
                            && Math.abs(d.z() - o.z()) < reach;
                    assertTrue(!inside, type + " bei " + t + ": Drohne im Hindernis " + actor.id());
                }
            }
        }
    }
}

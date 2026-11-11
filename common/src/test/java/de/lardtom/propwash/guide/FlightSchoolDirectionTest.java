package de.lardtom.propwash.guide;

import de.lardtom.propwash.bando.BandoScene;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.ConfiguratorScene;
import de.lardtom.propwash.guide.scenes.GateMenuScene;
import de.lardtom.propwash.guide.scenes.RecoveryScene;
import de.lardtom.propwash.guide.scenes.RepairScene;
import de.lardtom.propwash.guide.scenes.RescueScene;
import de.lardtom.propwash.guide.scenes.SceneKit;
import de.lardtom.propwash.guide.scenes.TricksScene;
import de.lardtom.propwash.guide.scene.ScenePos;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightSchoolDirectionTest {
    private static final double MIN_SPEED = 0.02;
    private static final float MIN_TILT = 6.0F;
    private static final float MAX_ATTITUDE = 60.0F;
    private static final float MAX_ATTITUDE_RATE = 4.0F;
    private static final float MIN_TURN_RATE = 3.0F;
    private static final float MIN_BANK = 4.0F;
    private static final Set<String> FLYING = Set.of("race", "course_elements", "rescue", "action_cam", "gripper", "gate_free", "fpv_los",
            "recovery", "tricks", "repair", GateMenuScene.ID, BandoScene.ID);

    private static List<Scene> scenes() {
        List<Scene> scenes = new ArrayList<>(GuideScenes.all());
        scenes.add(GateMenuScene.create());
        scenes.add(BandoScene.create());
        return scenes;
    }

    private static Quaterniond sceneModel(ActorState actor) {
        return new Quaterniond().rotateY(Math.PI - Math.toRadians(actor.yaw())).rotateX(Math.toRadians(-actor.pitch()))
                .rotateZ(Math.toRadians(actor.roll()));
    }

    private static Vector3d camera(ActorState actor) {
        return sceneModel(actor).transform(new Vector3d(0.0, 0.0, -1.0));
    }

    private static Vector3d up(ActorState actor) {
        return sceneModel(actor).transform(new Vector3d(0.0, 1.0, 0.0));
    }

    private static double along(Vector3d v, double dx, double dz) {
        double horizontal = Math.hypot(v.x, v.z);
        double length = Math.hypot(dx, dz);
        if (horizontal < 1.0E-6 || length < 1.0E-9) {
            return 0.0;
        }
        return (v.x * dx + v.z * dz) / (horizontal * length);
    }

    private static boolean steady(ActorState a, ActorState b) {
        return a.visible() && b.visible() && a.armed()
                && Math.abs(SceneKit.wrap(a.pitch())) <= MAX_ATTITUDE && Math.abs(SceneKit.wrap(a.roll())) <= MAX_ATTITUDE
                && Math.abs(b.pitch() - a.pitch()) <= MAX_ATTITUDE_RATE && Math.abs(b.roll() - a.roll()) <= MAX_ATTITUDE_RATE;
    }

    private record Tally(int forward, int backward, int leanAhead, int leanBack, int bankIn, int bankOut, List<String> faults) {
        String row(String id) {
            return id + ": Kamera vorwärts " + this.forward + ", rückwärts " + this.backward + "; Neigung in Flugrichtung " + this.leanAhead
                    + ", dagegen " + this.leanBack + "; Schräglage in die Kurve " + this.bankIn + ", nach außen " + this.bankOut;
        }
    }

    private static Tally tally(Scene scene) {
        int forward = 0;
        int backward = 0;
        int leanAhead = 0;
        int leanBack = 0;
        int bankIn = 0;
        int bankOut = 0;
        List<String> faults = new ArrayList<>();
        SceneState previous = SceneEvaluator.evaluate(scene, 0.0F);
        for (float t = 1.0F; t <= scene.duration(); t += 1.0F) {
            SceneState next = SceneEvaluator.evaluate(scene, t);
            for (ActorState a : previous.actors().values()) {
                if (!(a.spec() instanceof ActorSpec.Drone)) {
                    continue;
                }
                ActorState b = next.actor(a.id());
                if (b == null || !steady(a, b)) {
                    continue;
                }
                double dx = b.position().x() - a.position().x();
                double dz = b.position().z() - a.position().z();
                if (Math.hypot(dx, dz) < MIN_SPEED || Math.abs(SceneKit.wrap(a.pitch())) < MIN_TILT) {
                    continue;
                }
                String where = scene.id() + " " + a.id() + " bei " + (t - 1.0F);
                double ahead = along(camera(a), dx, dz);
                if (ahead > 0.0) {
                    forward++;
                } else {
                    backward++;
                    faults.add(where + ": Kamera zeigt nicht in Flugrichtung (" + Math.round(ahead * 100.0) / 100.0 + ")");
                }
                double lean = along(up(a), dx, dz);
                if (lean > 0.0) {
                    leanAhead++;
                } else {
                    leanBack++;
                    faults.add(where + ": Drohne neigt sich gegen die Flugrichtung (" + Math.round(lean * 100.0) / 100.0 + ")");
                }
                float turn = SceneKit.wrap(b.yaw() - a.yaw());
                if (Math.abs(turn) >= MIN_TURN_RATE && Math.abs(SceneKit.wrap(a.roll())) >= MIN_BANK) {
                    double insideX = turn > 0.0F ? -dz : dz;
                    double insideZ = turn > 0.0F ? dx : -dx;
                    if (along(up(a), insideX, insideZ) > 0.0) {
                        bankIn++;
                    } else {
                        bankOut++;
                        faults.add(where + ": Schräglage aus der Kurve heraus (Gier " + Math.round(turn) + "°/Tick, Roll " + Math.round(a.roll()) + "°)");
                    }
                }
            }
            previous = next;
        }
        return new Tally(forward, backward, leanAhead, leanBack, bankIn, bankOut, faults);
    }

    @Test
    void dronesFlyCameraFirstInEveryScene() {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (Scene scene : scenes()) {
            tallies.put(scene.id(), tally(scene));
        }
        StringBuilder table = new StringBuilder();
        List<String> faults = new ArrayList<>();
        tallies.forEach((id, tally) -> {
            if (tally.forward() + tally.backward() > 0) {
                table.append('\n').append(tally.row(id));
            }
            faults.addAll(tally.faults());
        });
        assertTrue(faults.isEmpty(), faults.size() + " Ticks falsch, zum Beispiel:\n" + String.join("\n", faults.subList(0, Math.min(12,
                faults.size()))) + "\nÜbersicht:" + table);
        for (String id : FLYING) {
            Tally tally = tallies.get(id);
            assertNotNull(tally, "Szene fehlt: " + id);
            assertTrue(tally.forward() >= 8, "zu wenig Vorwärtsflug geprüft in " + id + ":" + table);
        }
        assertTrue(tallies.values().stream().mapToInt(Tally::bankIn).sum() >= 40, "zu wenig Kurven geprüft:" + table);
    }

    @Test
    void headingMatchesTheMinecraftYawOfTheDrone() {
        ScenePos origin = new ScenePos(0.0F, 1.0F, 0.0F);
        assertEquals(0.0F, SceneKit.heading(origin, new ScenePos(0.0F, 1.0F, 2.0F)), 1.0E-3F);
        assertEquals(90.0F, SceneKit.heading(origin, new ScenePos(-2.0F, 1.0F, 0.0F)), 1.0E-3F);
        assertEquals(-90.0F, SceneKit.heading(origin, new ScenePos(2.0F, 1.0F, 0.0F)), 1.0E-3F);
        assertEquals(180.0F, Math.abs(SceneKit.heading(origin, new ScenePos(0.0F, 1.0F, -2.0F))), 1.0E-3F);
        for (ScenePos target : List.of(new ScenePos(1.5F, 2.0F, -0.7F), new ScenePos(-3.0F, 0.0F, 4.0F), new ScenePos(0.2F, 1.0F, 5.0F))) {
            assertEquals(SceneKit.pilotYaw(origin, target), SceneKit.heading(origin, target), 1.0E-3F, "Richtung zu " + target);
        }
    }

    @Test
    void crashesHitTheObstacleNoseFirst() {
        for (Scene scene : List.of(GuideScenes.byId(RecoveryScene.ID), GuideScenes.byId(RepairScene.ID))) {
            assertNotNull(scene);
            ScenePos impact = scene.id().equals(RecoveryScene.ID) ? RecoveryScene.IMPACT : RepairScene.IMPACT;
            ScenePos hover = scene.id().equals(RecoveryScene.ID) ? RecoveryScene.HOVER : RepairScene.HOVER;
            float hit = scene.id().equals(RecoveryScene.ID) ? 50.0F : RepairScene.CRASH_AT - 2.0F;
            ActorState drone = SceneEvaluator.evaluate(scene, hit).actor("drone");
            assertNotNull(drone);
            double ahead = along(camera(drone), impact.x() - hover.x(), impact.z() - hover.z());
            assertTrue(ahead > 0.95, scene.id() + ": Kamera zeigt beim Aufprall nicht zum Hindernis (" + ahead + ")");
            assertTrue(camera(drone).y < -0.2, scene.id() + ": Nase beim Anflug nicht gesenkt (" + camera(drone).y + ")");
        }
    }

    @Test
    void flipsAndLoopsTurnTheWayTheTextsSay() {
        Scene scene = GuideScenes.byId(TricksScene.ID);
        assertNotNull(scene);
        ActorState flip = SceneEvaluator.evaluate(scene, scene.stepStart(0) + 60.0F + TricksScene.FLIP_TICKS * 0.35F).actor(TricksScene.DRONE);
        assertNotNull(flip);
        assertTrue(camera(flip).y < -0.3, "Flip mit Nick nach vorn senkt die Kamera nicht: " + camera(flip).y);
        int loops = scene.steps().stream().map(step -> step.name()).toList().indexOf("loops");
        ActorState loop = SceneEvaluator.evaluate(scene, scene.stepStart(loops) + 44.0F).actor(TricksScene.DRONE);
        assertNotNull(loop);
        assertTrue(camera(loop).y > 0.3, "Power Loop mit Nick zurück hebt die Kamera nicht: " + camera(loop).y);
        ActorState approach = SceneEvaluator.evaluate(scene, scene.stepStart(loops) + 31.0F).actor(TricksScene.DRONE);
        assertNotNull(approach);
        assertTrue(camera(approach).x > 0.9, "Power Loop: Kamera zeigt nicht zur Mauer (+X): " + camera(approach).x);
    }

    @Test
    void configuratorSticksTiltTheDroneTheVisibleWay() {
        Scene scene = GuideScenes.byId(ConfiguratorScene.ID);
        assertNotNull(scene);
        ActorState rolling = SceneEvaluator.evaluate(scene, 72.0F).actor(ConfiguratorScene.DRONE);
        ActorState radio = SceneEvaluator.evaluate(scene, 72.0F).actor(ConfiguratorScene.RADIO);
        assertNotNull(rolling);
        assertNotNull(radio);
        assertTrue(radio.rightX() > 0.5F);
        Vector3d right = sceneModel(rolling).transform(new Vector3d(1.0, 0.0, 0.0));
        assertTrue(right.y < -0.2, "Roll-Knüppel rechts senkt die rechte Seite der Drohne nicht: " + right.y);
        ActorState pitching = SceneEvaluator.evaluate(scene, 106.0F).actor(ConfiguratorScene.DRONE);
        assertNotNull(pitching);
        assertTrue(camera(pitching).y < -0.2, "Nick-Knüppel vorn senkt die Kamera nicht: " + camera(pitching).y);
    }
}

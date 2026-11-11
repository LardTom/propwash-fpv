package de.lardtom.propwash.guide;

import de.lardtom.propwash.bando.BandoScene;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.GateMenuScene;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightSchoolHeadingTest {
    private record Excuse(float from, float to, String reason) {
    }

    private static final float SAMPLE = 1.0F;
    private static final double MIN_SPEED = 0.03;
    private static final double MIN_LEVEL = 0.2;
    private static final double MAX_STEEPNESS = 75.0;
    private static final double BACKWARD = -0.25;
    private static final int MIN_RUN = 3;
    private static final double MIN_UPRIGHT = 0.5;
    private static final Map<String, Excuse> EXCUSES = Map.of(
            "tricks/juicy/drone", new Excuse(36.0F, 50.0F, "Juicy Flick: die Nase zeigt nach dem Trick zurück, der Schwung trägt die Drohne weiter"),
            "hold/poshold/drone", new Excuse(100.0F, 170.0F, "GPS-Halt: die Drohne kehrt nach der Böe ohne Gieren an ihren Platz zurück"),
            "bind/start/drone", new Excuse(56.0F, 106.0F, "Schweben nach dem Abheben: leichtes Pendeln um den Platz"));

    private static List<Scene> scenes() {
        List<Scene> scenes = new ArrayList<>(GuideScenes.all());
        scenes.add(GateMenuScene.create());
        scenes.add(BandoScene.create());
        return scenes;
    }

    private static Quaterniond model(ActorState drone) {
        return new Quaterniond().rotateY(Math.PI - Math.toRadians(drone.yaw())).rotateX(Math.toRadians(-drone.pitch()))
                .rotateZ(Math.toRadians(drone.roll()));
    }

    private static Vector3d camera(ActorState drone) {
        return model(drone).transform(new Vector3d(0.0, 0.0, -1.0));
    }

    private static Vector3d up(ActorState drone) {
        return model(drone).transform(new Vector3d(0.0, 1.0, 0.0));
    }

    private static Vector3d travel(ActorState from, ActorState to) {
        return new Vector3d(to.position().x() - from.position().x(), 0.0, to.position().z() - from.position().z()).div(SAMPLE);
    }

    private static double ahead(Vector3d direction, Vector3d travel) {
        double level = Math.hypot(direction.x, direction.z);
        return (direction.x * travel.x + direction.z * travel.z) / (level * travel.length());
    }

    @Test
    void dronesFlyCameraFirstEvenWithoutTilt() {
        Map<String, String> backwards = new TreeMap<>();
        Map<String, Integer> excused = new TreeMap<>();
        int checked = 0;
        for (Scene scene : scenes()) {
            Map<String, Run> runs = new HashMap<>();
            for (float t = 0.0F; t + SAMPLE <= scene.duration(); t += SAMPLE) {
                SceneState a = SceneEvaluator.evaluate(scene, t);
                SceneState b = SceneEvaluator.evaluate(scene, t + SAMPLE);
                for (ActorState from : a.actors().values()) {
                    if (!(from.spec() instanceof ActorSpec.Drone)) {
                        continue;
                    }
                    ActorState to = b.actor(from.id());
                    String where = scene.id() + "/" + scene.steps().get(a.step()).name() + "/" + from.id();
                    float offset = t - scene.stepStart(a.step());
                    double ahead = 1.0;
                    if (from.visible() && from.armed() && to != null && to.visible()) {
                        Vector3d travel = travel(from, to);
                        Vector3d camera = camera(from);
                        double climb = Math.abs(to.position().y() - from.position().y()) / SAMPLE;
                        if (travel.length() >= MIN_SPEED && Math.toDegrees(Math.atan2(climb, travel.length())) <= MAX_STEEPNESS
                                && up(from).y >= MIN_UPRIGHT && Math.hypot(camera.x, camera.z) >= MIN_LEVEL) {
                            checked++;
                            ahead = ahead(camera, travel);
                        }
                    }
                    Excuse excuse = EXCUSES.get(where);
                    if (ahead < BACKWARD && excuse != null && offset >= excuse.from() && offset <= excuse.to()) {
                        excused.merge(where, 1, Integer::sum);
                        ahead = 1.0;
                    }
                    Run run = runs.get(from.id());
                    if (run != null && (ahead >= BACKWARD || !run.where.equals(where))) {
                        report(backwards, runs.remove(from.id()));
                    }
                    if (ahead < BACKWARD) {
                        runs.computeIfAbsent(from.id(), id -> new Run(where, offset)).add(ahead);
                    }
                }
            }
            runs.values().forEach(run -> report(backwards, run));
        }
        assertTrue(checked > 800, "zu wenige Flugproben: " + checked);
        assertTrue(backwards.isEmpty(), "Drohnen fliegen mit den Antennen voran statt mit der Kamera: " + backwards);
        assertEquals(EXCUSES.keySet(), excused.keySet(), "Ausnahme ohne Rückwärtsprobe, bitte streichen: " + excused);
    }

    private static void report(Map<String, String> backwards, Run run) {
        if (run.ticks < MIN_RUN) {
            return;
        }
        backwards.merge(run.where, String.format(Locale.ROOT, "%d Ticks ab Tick %.0f des Schritts (%.2f)", run.ticks, run.from, run.worst),
                (old, now) -> old + "; " + now);
    }

    private static final class Run {
        private final String where;
        private final float from;
        private int ticks;
        private double worst = 1.0;

        private Run(String where, float from) {
            this.where = where;
            this.from = from;
        }

        private void add(double ahead) {
            this.ticks++;
            this.worst = Math.min(this.worst, ahead);
        }
    }
}

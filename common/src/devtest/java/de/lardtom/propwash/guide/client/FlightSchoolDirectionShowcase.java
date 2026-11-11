package de.lardtom.propwash.guide.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import de.lardtom.propwash.guide.scenes.ConfiguratorScene;
import de.lardtom.propwash.guide.scenes.RaceScene;
import de.lardtom.propwash.guide.scenes.RecoveryScene;
import de.lardtom.propwash.guide.scenes.RescueScene;
import de.lardtom.propwash.guide.scenes.TricksScene;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FlightSchoolDirectionShowcase {
    private static final String PREFIX = "bildschirmfotos: Flugschule Flugrichtung ";
    private static final float FRONT = 0.35F;
    private static final float PITCH = 16.0F;
    private static final float VIEW_RADII = 1.0F;

    private record Shot(String file, String scene, String drone, int step, float offset, float zoom) {
    }

    private static final List<Shot> SHOTS = List.of(
            new Shot("race", RaceScene.ID, RaceScene.DRONE, 3, RaceScene.passTime(1) + 7.0F, 1.5F),
            new Shot("race_dive", RaceScene.ID, RaceScene.DIVER, 6, RaceScene.DIVE_START_TIME + 8.0F, 1.5F),
            new Shot("rescue", RescueScene.ID, RescueScene.DRONE, 3, 90.0F, 1.5F),
            new Shot("recovery_crash", RecoveryScene.ID, RecoveryScene.DRONE, 0, 46.0F, 1.5F),
            new Shot("recovery_range", RecoveryScene.ID, RecoveryScene.DRONE, 4, 75.0F, 1.5F),
            new Shot("tricks_juicy", TricksScene.ID, TricksScene.DRONE, 1, 18.0F, 1.5F),
            new Shot("tricks_power_loop", TricksScene.ID, TricksScene.DRONE, 3, 44.0F, 1.5F),
            new Shot("configurator_pitch", ConfiguratorScene.ID, ConfiguratorScene.DRONE, 0, 106.0F, 1.5F));

    private FlightSchoolDirectionShowcase() {
    }

    public static void register() {
        for (Shot shot : SHOTS) {
            Screenshots.step(PREFIX + shot.file(), "flight_school_direction_" + shot.file(), 20, new CloseUp(shot));
        }
    }

    private record Prepared(Scene scene, SceneFraming framing) {
    }

    static @Nullable Prepared closeUp(Shot shot) {
        Scene scene = GuideScenes.byId(shot.scene());
        if (scene == null || shot.step() >= scene.stepCount()) {
            return null;
        }
        float time = scene.stepStart(shot.step()) + shot.offset();
        ActorState now = SceneEvaluator.evaluate(scene, time).actor(shot.drone());
        ActorState before = SceneEvaluator.evaluate(scene, time - 2.0F).actor(shot.drone());
        if (now == null || before == null || !(now.spec() instanceof ActorSpec.Drone drone)) {
            return null;
        }
        float radius = SceneFraming.droneRadius(drone) * VIEW_RADII;
        float dx = now.position().x() - before.position().x();
        float dz = now.position().z() - before.position().z();
        float length = (float) Math.hypot(dx, dz);
        if (length < 1.0E-4F) {
            dx = 0.0F;
            dz = 1.0F;
        } else {
            dx /= length;
            dz /= length;
        }
        float best = 0.0F;
        for (float side : new float[]{1.0F, -1.0F}) {
            float cx = side * dz + FRONT * dx;
            float cz = -side * dx + FRONT * dz;
            float yaw = (float) Math.toDegrees(Math.atan2(cx, cz));
            double radians = Math.toRadians(yaw);
            if (dx * Math.cos(radians) - dz * Math.sin(radians) > 0.0) {
                best = yaw;
            }
        }
        ScenePos target = now.position().add(0.0F, 0.15F, 0.0F);
        List<SceneStep> steps = new ArrayList<>(scene.steps());
        SceneStep original = steps.get(shot.step());
        List<TimedCue> cues = new ArrayList<>(original.cues());
        cues.add(new TimedCue(shot.offset(), 0.0F, new Cue.Camera(target, best, PITCH, 1.0F, Ease.LINEAR)));
        steps.set(shot.step(), new SceneStep(original.name(), original.duration(), cues));
        List<SceneStep> frame = new ArrayList<>();
        for (int i = 0; i < scene.stepCount(); i++) {
            SceneStep step = scene.steps().get(i);
            frame.add(new SceneStep(step.name(), step.duration(), i == 0
                    ? List.of(new TimedCue(0.0F, 0.0F, new Cue.Spawn(shot.drone(), now.spec(), now.position(), now.yaw(), Cue.Appear.INSTANT)))
                    : List.of()));
        }
        Scene framed = new Scene(scene.id(), scene.icon(), scene.items(), radius, scene.camera(), frame);
        return new Prepared(new Scene(scene.id(), scene.icon(), scene.items(), radius, scene.camera(), steps), SceneFraming.of(framed));
    }

    private static final class CloseUp implements Screenshots.Scene {
        private final Shot shot;
        private @Nullable Prepared prepared;

        private CloseUp(Shot shot) {
            this.shot = shot;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.prepared == null) {
                this.prepared = closeUp(this.shot);
                if (this.prepared == null) {
                    return StepResult.fail("Szene oder Drohne für Bildschirmfoto fehlt: " + this.shot.scene() + "/" + this.shot.drone());
                }
            }
            if (!(mc.gui.screen() instanceof SceneScreen)) {
                mc.gui.setScreen(new SceneScreen(this.prepared.scene(), null, SceneScreen.Options.DEFAULT, this.prepared.framing()));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Prepared prepared = this.prepared;
            mc.mouseHandler.onMove(mc.getWindow().handle(), 1.0, 1.0, 0.0, 0.0);
            if (prepared != null && mc.gui.screen() instanceof SceneScreen screen) {
                screen.playback().seek(prepared.scene().stepStart(this.shot.step()) + this.shot.offset());
                screen.playback().pause();
                screen.snapView(0.0F, 0.0F, this.shot.zoom());
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            Screenshots.parkMouse(Minecraft.getInstance());
            this.prepared = null;
        }
    }
}

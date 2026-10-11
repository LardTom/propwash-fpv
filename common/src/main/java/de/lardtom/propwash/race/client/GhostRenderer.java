package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.AfterWaterRenderTypes;
import de.lardtom.propwash.drone.client.render.DroneGhost;
import de.lardtom.propwash.race.GhostLook;
import de.lardtom.propwash.race.GhostTrack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

final class GhostRenderer {
    static final int PERSONAL_BODY = 0x8A55C8FF;
    static final int PERSONAL_PROP = 0x4DBFE9FF;
    static final int RECORD_BODY = 0x8AFFC23D;
    static final int RECORD_PROP = 0x4DFFE08A;
    static final int PERSONAL_MODEL = 0x9CB4E6FF;
    static final int RECORD_MODEL = 0x9CFFE39A;
    static final int ASSISTED_BODY = 0x8AC58CFF;
    static final int ASSISTED_PROP = 0x4DD9B8FF;
    static final int ASSISTED_MODEL = 0x9CD2A8FF;
    private static final int MAX_FALLBACKS = 4;
    private static final Map<GhostTrack, GhostLook> FALLBACKS = new IdentityHashMap<>();
    private static final float ARM_REACH = 0.3F;
    private static final double HALF_HEIGHT = 0.075;
    private static final double[] POSITION = new double[3];
    private static final Quaternionf ROTATION = new Quaternionf();

    private GhostRenderer() {
    }

    static boolean pose(@Nullable GhostTrack track, long lapTime, double[] position, Quaternionf rotation) {
        return track != null && track.pose(lapTime, position, rotation);
    }

    static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        RaceConfig config = RaceConfig.CONFIG.get();
        if (!tracker.tracker().racing() || tracker.ghosts().courseId() != tracker.courseId()) {
            return;
        }
        long lapTime = tracker.tracker().lapElapsedNanos(tracker.now());
        Vec3 camera = levelState.cameraRenderState.pos;
        GhostTrack record = tracker.ghosts().record();
        if (config.recordGhost && pose(record, lapTime, POSITION, ROTATION)) {
            draw(collector, poseStack, camera, lookFor(record), RECORD_MODEL, RECORD_BODY, RECORD_PROP);
        }
        GhostTrack personal = tracker.ghosts().shownPersonal();
        if (config.ghost && pose(personal, lapTime, POSITION, ROTATION)) {
            if (tracker.ghosts().showsAssisted()) {
                draw(collector, poseStack, camera, lookFor(personal), ASSISTED_MODEL, ASSISTED_BODY, ASSISTED_PROP);
            } else {
                draw(collector, poseStack, camera, lookFor(personal), PERSONAL_MODEL, PERSONAL_BODY, PERSONAL_PROP);
            }
        }
    }

    static GhostLook lookFor(GhostTrack track) {
        GhostLook stored = track.look();
        if (stored != null) {
            return stored;
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        boolean useDrone = drone != null
                && (drone.build().frame().key().equals(track.frame()) || !GhostLook.hasPreset(track.frame()));
        if (useDrone) {
            GhostLook cached = FALLBACKS.get(track);
            if (cached == null || !cached.matches(drone.build(), drone.paint())) {
                cached = GhostLook.of(drone.build(), drone.paint());
                remember(track, cached);
            }
            return cached;
        }
        return GhostLook.preset(track.frame());
    }

    private static void remember(GhostTrack track, GhostLook look) {
        if (FALLBACKS.size() >= MAX_FALLBACKS && !FALLBACKS.containsKey(track)) {
            FALLBACKS.clear();
        }
        FALLBACKS.put(track, look);
    }

    private static void draw(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, GhostLook look, int model, int body, int prop) {
        poseStack.pushPose();
        poseStack.translate(POSITION[0] - camera.x, POSITION[1] - camera.y, POSITION[2] - camera.z);
        boolean drawn = DroneGhost.submit(look.build(), look.paint(), ROTATION, model, poseStack, collector);
        poseStack.popPose();
        if (!drawn) {
            drawBoxes(collector, poseStack, camera, body, prop);
        }
    }

    private static void drawBoxes(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, int body, int prop) {
        RenderType type = AfterWaterRenderTypes.ghost(BoxDraw.WHITE_TEXTURE);
        int light = LightCoordsUtil.FULL_BRIGHT;
        poseStack.pushPose();
        poseStack.translate(POSITION[0] - camera.x, POSITION[1] + HALF_HEIGHT - camera.y, POSITION[2] - camera.z);
        poseStack.rotate(ROTATION);
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            BoxDraw.centered(pose, buffer, 0.0, 0.0, 0.0, 0.06, 0.035, 0.1, body, light);
            BoxDraw.centered(pose, buffer, 0.0, 0.03, -0.12, 0.03, 0.03, 0.02, body, light);
            for (int i = 0; i < 4; i++) {
                double x = (i & 1) == 0 ? -ARM_REACH * 0.7071 : ARM_REACH * 0.7071;
                double z = (i & 2) == 0 ? -ARM_REACH * 0.7071 : ARM_REACH * 0.7071;
                BoxDraw.centered(pose, buffer, x, 0.02, z, 0.035, 0.03, 0.035, body, light);
                BoxDraw.centered(pose, buffer, x, 0.055, z, 0.13, 0.004, 0.13, prop, light);
            }
        });
        poseStack.rotateDegrees(Axis.YP, 45.0F);
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            BoxDraw.centered(pose, buffer, 0.0, 0.0, 0.0, ARM_REACH, 0.012, 0.02, body, light);
            BoxDraw.centered(pose, buffer, 0.0, 0.0, 0.0, 0.02, 0.012, ARM_REACH, body, light);
        });
        poseStack.popPose();
    }
}

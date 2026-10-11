package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PartWear;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class DroneGhost {
    public static final float THROTTLE = 0.55F;
    private static final int MAX_CACHED = 8;
    private static final Map<Key, DroneRenderState> STATES = new HashMap<>();
    private static int generation = Integer.MIN_VALUE;
    private static @Nullable DroneRenderConfig config;
    private static boolean warned;

    private DroneGhost() {
    }

    public static boolean submit(DroneBuild build, DronePaint paint, Quaternionfc orientation, int color, PoseStack poseStack,
                                 SubmitNodeCollector collector) {
        try {
            DroneRenderState state = state(build, paint);
            state.orientation.set(orientation.x(), orientation.y(), orientation.z(), orientation.w());
            if (!Float.isFinite(state.orientation.x) || !Float.isFinite(state.orientation.w) || state.orientation.lengthSquared() < 1.0E-6F) {
                state.orientation.identity();
            }
            state.orientation.normalize();
            DroneVisuals.placeInWorld(state);
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            state.outlineColor = 0;
            DroneVisuals.submit(state, poseStack, new GhostCollector(collector, color));
            return true;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                Constants.LOG.error("Geist-Drohne konnte nicht gezeichnet werden", e);
            }
            return false;
        }
    }

    public static SubmitNodeCollector tinted(SubmitNodeCollector collector, int color) {
        return new GhostCollector(collector, color);
    }

    static synchronized DroneRenderState state(DroneBuild build, DronePaint paint) {
        DroneRenderResources res = DroneVisuals.resources();
        DroneRenderConfig current = DroneRenderConfig.get();
        if (res.generation() != generation || current != config) {
            generation = res.generation();
            config = current;
            STATES.clear();
        }
        Key key = new Key(build == null ? DroneBuild.FREESTYLE : build, paint == null ? DronePaint.DEFAULT : paint);
        DroneRenderState state = STATES.get(key);
        if (state == null) {
            state = fill(key.build(), key.paint());
            if (STATES.size() >= MAX_CACHED) {
                STATES.clear();
            }
            STATES.put(key, state);
        }
        return state;
    }

    private static DroneRenderState fill(DroneBuild build, DronePaint paint) {
        DroneRenderState state = new DroneRenderState();
        state.paint = paint;
        state.wear = PartWear.PRISTINE;
        state.batteryCharge = 1.0F;
        state.batteryMissing = false;
        state.orientation.identity();
        float[] angles = new float[DroneLayout.SLOTS];
        float[] outputs = new float[DroneLayout.SLOTS];
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            angles[slot] = 0.35F + slot * 0.7F;
            outputs[slot] = THROTTLE;
        }
        DroneVisuals.fill(state, build, true, false, angles, outputs, 0.0F);
        state.hidden = false;
        state.isInvisible = false;
        return state;
    }

    private record Key(DroneBuild build, DronePaint paint) {
    }
}

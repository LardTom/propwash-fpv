package de.lardtom.propwash.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.core.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import java.util.List;

public final class ClientBus {
    public static final Event<ClientListener> TICK_END = new Event<>("client_tick_end");

    public static final Event<FrameListener> FRAME_START = new Event<>("frame_start");

    public static final Event<ClientListener> LOGIN = new Event<>("client_login");

    public static final Event<ClientListener> DISCONNECT = new Event<>("client_disconnect");

    public static final Event<KeyMappingsListener> KEY_MAPPINGS_REGISTERED = new Event<>("key_mappings_registered");

    public static final Event<Runnable> REGISTER_HUD_LAYERS = new Event<>("register_hud_layers");

    public static final Event<WorldGeometryListener> SUBMIT_WORLD_GEOMETRY = new Event<>("submit_world_geometry");

    private static boolean inSession;

    private ClientBus() {
    }

    public static boolean inSession() {
        return inSession;
    }

    public static void fireLogin(Minecraft minecraft) {
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> fireLogin(minecraft));
            return;
        }
        if (inSession) {
            return;
        }
        inSession = true;
        LOGIN.fire(l -> l.accept(minecraft));
    }

    public static void fireDisconnect(Minecraft minecraft) {
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> fireDisconnect(minecraft));
            return;
        }
        if (!inSession) {
            return;
        }
        inSession = false;
        DISCONNECT.fire(l -> l.accept(minecraft));
    }

    @FunctionalInterface
    public interface ClientListener {
        void accept(Minecraft minecraft);
    }

    @FunctionalInterface
    public interface FrameListener {
        void onFrame(Minecraft minecraft, DeltaTracker deltaTracker);
    }

    @FunctionalInterface
    public interface KeyMappingsListener {
        void accept(List<KeyMapping> propwashMappings);
    }

    @FunctionalInterface
    public interface WorldGeometryListener {
        void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState);
    }
}

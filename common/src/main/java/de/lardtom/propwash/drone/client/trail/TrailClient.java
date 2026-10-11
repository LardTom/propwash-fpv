package de.lardtom.propwash.drone.client.trail;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class TrailClient {
    public static final Object TOAST_TOKEN = new Object();
    private static final long TOAST_MILLIS = 2500L;
    private static boolean initialized;
    private static KeyMapping toggleKey;

    private TrailClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        TrailConfig.CONFIG.load();
        toggleKey = ClientRegistrar.keyMapping(KeyDefaults.DRONE_TRAILS.name(), KeyDefaults.DRONE_TRAILS.key());
        QuadcopterEntity.CLIENT_TICK.register(TrailRenderer::onDroneTick);
        ClientBus.TICK_END.register(TrailClient::onClientTick);
        ClientBus.DISCONNECT.register(minecraft -> TrailRenderer.reset());
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(TrailRenderer::submit);
    }

    public static boolean ruleOn() {
        return ClientWorldRules.INSTANCE.on(WorldRuleSpecs.DRONE_TRAILS);
    }

    public static boolean shown() {
        return TrailRenderer.active();
    }

    public static void setEnabled(boolean enabled) {
        TrailConfig config = TrailConfig.get().copy();
        config.enabled = enabled;
        TrailConfig.CONFIG.set(config);
    }

    public static KeyMapping toggleKey() {
        return toggleKey;
    }

    private static void onClientTick(Minecraft minecraft) {
        TrailRenderer.onClientTick(minecraft);
        if (toggleKey == null) {
            return;
        }
        while (toggleKey.consumeClick()) {
            toggle(minecraft);
        }
    }

    public static void toggle(Minecraft minecraft) {
        if (!ruleOn()) {
            toast(minecraft, Component.translatable("propwash.trails.toast.rule_off"), Palette.TEXT_MUTED);
            return;
        }
        boolean enabled = !TrailConfig.get().enabled;
        setEnabled(enabled);
        toast(minecraft, Component.translatable(enabled ? "propwash.trails.toast.on" : "propwash.trails.toast.off"),
                enabled ? Palette.SUCCESS : Palette.TEXT_MUTED);
    }

    private static void toast(Minecraft minecraft, Component message, int accent) {
        ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, message, null, accent, TOAST_MILLIS);
    }
}

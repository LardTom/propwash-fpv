package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.registry.client.VanillaHudLayer;
import de.lardtom.propwash.ui.client.config.ConfigTabs;

import java.util.List;

public final class OsdClient {
    public static final JsonConfig<OsdConfig> CONFIG = JsonConfig.create("osd", OsdConfig.class, OsdConfig::defaults, OsdConfig::sanitize);
    public static final List<VanillaHudLayer> HIDDEN_WHILE_FLYING = List.of(
            VanillaHudLayer.CAMERA_OVERLAYS,
            VanillaHudLayer.CROSSHAIR,
            VanillaHudLayer.HOTBAR,
            VanillaHudLayer.HEALTH,
            VanillaHudLayer.ARMOR,
            VanillaHudLayer.FOOD,
            VanillaHudLayer.AIR,
            VanillaHudLayer.VEHICLE_HEALTH,
            VanillaHudLayer.EXPERIENCE_LEVEL,
            VanillaHudLayer.INFO_BAR,
            VanillaHudLayer.HELD_ITEM_NAME,
            VanillaHudLayer.EFFECTS);

    private static boolean initialized;

    private OsdClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CONFIG.load();
        ClientRegistrar.reloadListener(OsdFonts.RELOAD_LISTENER_ID, new OsdFonts.ReloadListener());
        ClientRegistrar.hudLayer(OsdHud.ID, OsdHud::extract);
        ClientRegistrar.hudLayer(RadioHud.ID, RadioHud::extract);
        for (VanillaHudLayer layer : HIDDEN_WHILE_FLYING) {
            ClientRegistrar.hideVanillaHud(layer, OsdHud::hideVanilla);
        }
        ClientBus.FRAME_START.register(OsdHud::onFrame);
        ClientBus.FRAME_START.register(RadioHud::onFrame);
        ClientBus.DISCONNECT.register(OsdHud::onDisconnect);
        ClientBus.DISCONNECT.register(RadioHud::onDisconnect);
        ConfigTabs.register(OsdConfigTab::new);
    }
}

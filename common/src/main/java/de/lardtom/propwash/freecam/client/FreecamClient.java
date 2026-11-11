package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.client.CameraClip;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.ui.client.FirstUseHints;
import de.lardtom.propwash.ui.client.UiClient;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class FreecamClient {
    public static final int INTRO_TIMES = 1;
    private static final long INTRO_MILLIS = 6000L;
    private static @Nullable KeyMapping key;
    private static int introShown;

    private FreecamClient() {
    }

    public static void init() {
        FreecamConfig.CONFIG.load();
        key = ClientRegistrar.keyMapping(KeyDefaults.FREECAM.name(), KeyDefaults.FREECAM.key());
        CameraClip.register(FreecamSession::active);
        registerTab();
        ClientBus.TICK_END.register(FreecamClient::onTick);
        ClientBus.FRAME_START.register((minecraft, deltaTracker) -> FreecamSession.INSTANCE.onFrame(minecraft));
        ClientBus.DISCONNECT.register(minecraft -> FreecamSession.INSTANCE.end(minecraft, FreecamRules.End.DISCONNECT));
    }

    static void registerTab() {
        ConfigTabs.registerFreecamOnly(FreecamTab::new);
    }

    public static @Nullable KeyMapping key() {
        return key;
    }

    public static Component keyLabel() {
        KeyMapping mapping = key;
        return mapping == null ? Component.literal("F6") : mapping.getTranslatedKeyMessage();
    }

    public static Component title(FreecamPreset preset) {
        return Component.translatable("propwash.freecam.title", Component.translatable(preset.translationKey()));
    }

    public static Component osdName(FreecamPreset preset) {
        return Component.translatable("propwash.freecam.osd_name", Component.translatable(preset.translationKey()));
    }

    public static int introShown() {
        return introShown;
    }

    private static void onTick(Minecraft minecraft) {
        KeyMapping mapping = key;
        if (mapping != null) {
            boolean pressed = false;
            while (mapping.consumeClick()) {
                pressed = true;
            }
            if (pressed && minecraft.gui.screen() == null && minecraft.player != null) {
                FreecamSession.INSTANCE.toggle(minecraft);
            }
        }
        FreecamSession.INSTANCE.onTick(minecraft);
    }

    static void started(Minecraft minecraft) {
        if (FirstUseHints.fresh(FirstUseHints.FREECAM_INTRO, INTRO_TIMES)) {
            FirstUseHints.shown(FirstUseHints.FREECAM_INTRO, INTRO_TIMES);
            introShown++;
            ThemedToast.show(minecraft.gui.toastManager(), FreecamSession.TOAST_TOKEN, Component.translatable("propwash.freecam.tab"),
                    Component.translatable("propwash.freecam.intro", InputKeys.label(InputKeys.ARM), UiClient.configuratorKey().getTranslatedKeyMessage(),
                            keyLabel()), Palette.INFO, INTRO_MILLIS);
        }
    }
}

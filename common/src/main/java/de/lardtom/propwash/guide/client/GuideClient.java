package de.lardtom.propwash.guide.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.GuideModule;
import de.lardtom.propwash.guide.client.render.SceneRenderState;
import de.lardtom.propwash.guide.client.render.SceneRenderer;
import de.lardtom.propwash.guide.client.screen.GuideTab;
import de.lardtom.propwash.guide.client.screen.SceneIndexScreen;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.client.training.TrainingClient;
import de.lardtom.propwash.guide.client.training.TrainingScreen;
import de.lardtom.propwash.guide.client.training.TrainingSession;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.registry.client.VanillaHudLayer;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

public final class GuideClient {
    private static boolean initialized;

    private GuideClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        GuideModule.init();
        GuideKeys.register();
        ClientRegistrar.pictureInPictureRenderer(SceneRenderState.class, SceneRenderer::new);
        ConfigTabs.register(GuideTab::new);
        ClientBus.TICK_END.register(GuideClient::onTick);
        TrainingClient.init();
        for (VanillaHudLayer layer : VanillaHudLayer.values()) {
            ClientRegistrar.hideVanillaHud(layer, GuideClient::flightSchoolOpen);
        }
    }

    public static boolean flightSchoolOpen() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof SceneScreen || screen instanceof SceneIndexScreen || screen instanceof TrainingScreen;
    }

    private static void onTick(Minecraft minecraft) {
        HoldToOpen.runPending();
        KeyMapping mapping = GuideKeys.mapping();
        if (mapping == null) {
            return;
        }
        while (mapping.consumeClick()) {
            if (minecraft.gui.screen() != null || minecraft.player == null) {
                continue;
            }
            if (PilotSession.INSTANCE.isActive() || TrainingSession.INSTANCE.active()) {
                GuideOpener.openTraining(null);
                continue;
            }
            ItemStack held = minecraft.player.getMainHandItem();
            GuideOpener.openIndex(null, GuideOpener.scenesFor(held).isEmpty() ? ItemStack.EMPTY : held);
        }
    }
}

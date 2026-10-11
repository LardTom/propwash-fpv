package de.lardtom.propwash.input.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.client.screen.KeysTab;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.input.client.screen.remote.RemoteDemoRenderState;
import de.lardtom.propwash.input.client.screen.remote.RemoteDemoRenderer;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.ui.client.PilotGuard;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;

public final class InputClient {
    private InputClient() {
    }

    public static void init() {
        InputConfig.CONFIG.load();
        InputKeys.bootstrap();
        ConfigTabs.register(ReceiverTab::new);
        ConfigTabs.register(ModesTab::new);
        ConfigTabs.register(KeysTab::new);
        ClientRegistrar.pictureInPictureRenderer(RemoteDemoRenderState.class, RemoteDemoRenderer::new);
        ClientBus.TICK_END.register(InputClient::onClientTick);
        ClientBus.DISCONNECT.register(minecraft -> TransmitterInput.INSTANCE.onDisconnect());
    }

    public static void openReceiverWizard(Minecraft minecraft) {
        if (!PilotGuard.prepareOpen(minecraft)) {
            return;
        }
        minecraft.gui.setScreen(ReceiverWizardScreen.standalone(minecraft.gui.screen()));
    }

    private static void onClientTick(Minecraft minecraft) {
        while (InputKeys.RECEIVER_WIZARD.consumeClick()) {
            if (minecraft.gui.screen() == null && !DeviceOnboarding.INSTANCE.openFromKey(minecraft)) {
                openReceiverWizard(minecraft);
            }
        }
        DeviceOnboarding.INSTANCE.tick(minecraft);
    }
}

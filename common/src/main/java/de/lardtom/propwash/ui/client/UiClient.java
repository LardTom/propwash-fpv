package de.lardtom.propwash.ui.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.LedTab;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.tab.PidTab;
import de.lardtom.propwash.ui.client.tab.RatesTab;
import de.lardtom.propwash.ui.client.tab.SettingsTab;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class UiClient {
    private static KeyMapping configuratorKey;

    private UiClient() {
    }

    public static void init() {
        UiConfig.CONFIG.load();
        configuratorKey = ClientRegistrar.keyMapping("configurator", InputConstants.KEY_K);
        ConfigTabs.setOpener(ConfiguratorScreen::open);
        ClientRegistrar.configScreen(parent -> ConfiguratorScreen.create(parent, null));
        ConfigTabs.register(OverviewTab::new);
        ConfigTabs.register(SettingsTab::new);
        ConfigTabs.register(PidTab::new);
        ConfigTabs.register(RatesTab::new);
        ConfigTabs.register(LedTab::new);
        ClientBus.TICK_END.register(UiClient::onTick);
        ClientBus.DISCONNECT.register(minecraft -> TemplateApplier.reset());
    }

    public static KeyMapping configuratorKey() {
        return configuratorKey;
    }

    private static void onTick(Minecraft minecraft) {
        while (configuratorKey.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null) {
                ConfigTabs.open(null, null);
            }
        }
        TemplateApplier.tick(minecraft);
    }
}

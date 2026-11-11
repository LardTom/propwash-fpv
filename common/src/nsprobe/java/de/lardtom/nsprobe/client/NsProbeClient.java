package de.lardtom.nsprobe.client;

import de.lardtom.nsprobe.NsProbe;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

public final class NsProbeClient {
    private NsProbeClient() {
    }

    public static KeyMapping keyMapping() {
        return new KeyMapping(NsProbe.KEY, -1, KeyMapping.Category.MISC);
    }

    public static void init() {
        try {
            PropwashClientApi.registerHudElement("propwash:probe", (graphics, context) -> {
            });
            NsProbe.LOG.error("nsprobe: HUD element propwash:probe was accepted");
        } catch (IllegalArgumentException e) {
            NsProbe.LOG.info("nsprobe: HUD element refused as expected: {}", e.getMessage());
        }
        PropwashClientApi.registerConfigTab(ProbeTab::new);
    }

    private static final class ProbeTab extends ConfigTab {
        @Override
        public String id() {
            return "propwash:nsprobe";
        }

        @Override
        public Component title() {
            return Component.literal("nsprobe");
        }

        @Override
        protected void build() {
        }
    }
}

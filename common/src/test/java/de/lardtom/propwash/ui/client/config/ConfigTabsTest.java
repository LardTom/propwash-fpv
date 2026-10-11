package de.lardtom.propwash.ui.client.config;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTabsTest {
    @Test
    void tabsSortByOrderThenIdIndependentOfRegistration() {
        int before = ConfigTabs.registeredCount();
        ConfigTabs.register(() -> new FixedTab("zulu", 50));
        ConfigTabs.register(() -> new FixedTab("alpha", 50));
        ConfigTabs.register(() -> new FixedTab("first", 10));
        assertEquals(before + 3, ConfigTabs.registeredCount());
        List<String> ids = ConfigTabs.create().stream().map(ConfigTab::id).filter(id -> List.of("zulu", "alpha", "first").contains(id)).toList();
        assertEquals(List.of("first", "alpha", "zulu"), ids);
    }

    @Test
    void discardIsANoOpByDefault() {
        FixedTab tab = new FixedTab("plain", 1);
        tab.discard();
        assertEquals(0, tab.built);
    }

    private static final class FixedTab extends ConfigTab {
        private final String id;
        private final int order;
        private int built;

        private FixedTab(String id, int order) {
            this.id = id;
            this.order = order;
        }

        @Override
        public String id() {
            return this.id;
        }

        @Override
        public Component title() {
            return Component.literal(this.id);
        }

        @Override
        public int order() {
            return this.order;
        }

        @Override
        protected void build() {
            this.built++;
        }
    }
}

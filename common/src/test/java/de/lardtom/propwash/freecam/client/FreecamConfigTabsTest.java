package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreecamConfigTabsTest {
    @BeforeAll
    static void registerFreecamTab() {
        FreecamClient.registerTab();
    }

    @Test
    void normalConfiguratorShowsEveryCreatedAndCountedTabWithoutTheFreecamTab() {
        List<String> normal = ids(ConfigTabs.create(false));
        assertFalse(normal.contains(FreecamTab.ID), "Tab freecam im normalen K-Menü: " + normal);
        assertEquals(ids(ConfigTabs.create()), normal, "ConfigTabs.create() liefert Tabs, die das normale K-Menü nicht zeigt");
        assertEquals(ConfigTabs.registeredCount(), normal.size(), "registeredCount() zählt Tabs, die das normale K-Menü nicht zeigt: " + normal);
    }

    @Test
    void freecamConfiguratorShowsTheFreecamTabAndOnlyFreecamTabs() {
        List<ConfigTab> tabs = ConfigTabs.create(true);
        assertTrue(ids(tabs).contains(FreecamTab.ID), "Tab freecam fehlt in der Freecam: " + ids(tabs));
        for (ConfigTab tab : tabs) {
            assertTrue(tab.freecam(), "Tab " + tab.id() + " in der Freecam");
        }
        assertEquals(1, ids(tabs).stream().filter(FreecamTab.ID::equals).count(), "Tab freecam doppelt: " + ids(tabs));
    }

    private static List<String> ids(List<ConfigTab> tabs) {
        return tabs.stream().map(ConfigTab::id).toList();
    }
}

package de.lardtom.propwash.ui.client.config;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.PartAvailability;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public final class ConfigTabs {
    private static final List<Supplier<? extends ConfigTab>> FACTORIES = new ArrayList<>();
    private static Opener opener = (parent, tabId) -> {
    };

    private ConfigTabs() {
    }

    public static void register(Supplier<? extends ConfigTab> factory) {
        FACTORIES.add(factory);
    }

    public static List<ConfigTab> create() {
        List<ConfigTab> tabs = new ArrayList<>();
        for (Supplier<? extends ConfigTab> factory : List.copyOf(FACTORIES)) {
            try {
                ConfigTab tab = factory.get();
                if (tab != null && PartAvailability.activeNamespacedId(safeId(tab))) {
                    tabs.add(tab);
                }
            } catch (RuntimeException e) {
                Constants.LOG.error("Konfigurator-Tab konnte nicht erzeugt werden", e);
            }
        }
        tabs.sort(Comparator.comparingInt(ConfigTabs::safeOrder).thenComparing(ConfigTabs::safeId));
        return tabs;
    }

    public static int registeredCount() {
        return FACTORIES.size();
    }

    private static int safeOrder(ConfigTab tab) {
        try {
            return tab.order();
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String safeId(ConfigTab tab) {
        try {
            String id = tab.id();
            return id != null ? id : "";
        } catch (RuntimeException e) {
            return tab.getClass().getName();
        }
    }

    public static void open(@Nullable Screen parent, @Nullable String tabId) {
        opener.open(parent, tabId);
    }

    public static void setOpener(Opener opener) {
        ConfigTabs.opener = opener;
    }

    @FunctionalInterface
    public interface Opener {
        void open(@Nullable Screen parent, @Nullable String tabId);
    }
}

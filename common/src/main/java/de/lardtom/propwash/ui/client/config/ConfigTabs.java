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
    public static final String FREECAM_TAB = "freecam";
    private static final List<Supplier<? extends ConfigTab>> FACTORIES = new ArrayList<>();
    private static final List<Supplier<? extends ConfigTab>> FREECAM_ONLY = new ArrayList<>();
    private static final Comparator<ConfigTab> ORDER = Comparator.comparingInt(ConfigTabs::safeOrder).thenComparing(ConfigTabs::safeId);
    private static Opener opener = (parent, tabId) -> {
    };

    private ConfigTabs() {
    }

    public static void register(Supplier<? extends ConfigTab> factory) {
        FACTORIES.add(factory);
    }

    public static void registerFreecamOnly(Supplier<? extends ConfigTab> factory) {
        FREECAM_ONLY.add(factory);
    }

    public static List<ConfigTab> create() {
        return create(FACTORIES);
    }

    public static List<ConfigTab> create(boolean freecam) {
        if (!freecam) {
            return create();
        }
        List<ConfigTab> tabs = create(FREECAM_ONLY);
        for (ConfigTab tab : create()) {
            if (safeFreecam(tab)) {
                tabs.add(tab);
            }
        }
        tabs.sort(ORDER);
        return tabs;
    }

    private static List<ConfigTab> create(List<Supplier<? extends ConfigTab>> factories) {
        List<ConfigTab> tabs = new ArrayList<>();
        for (Supplier<? extends ConfigTab> factory : List.copyOf(factories)) {
            try {
                ConfigTab tab = factory.get();
                if (tab != null && PartAvailability.activeNamespacedId(safeId(tab))) {
                    tabs.add(tab);
                }
            } catch (RuntimeException e) {
                Constants.LOG.error("Konfigurator-Tab konnte nicht erzeugt werden", e);
            }
        }
        tabs.sort(ORDER);
        return tabs;
    }

    private static boolean safeFreecam(ConfigTab tab) {
        try {
            return tab.freecam();
        } catch (RuntimeException e) {
            return false;
        }
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

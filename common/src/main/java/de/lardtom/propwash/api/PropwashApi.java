package de.lardtom.propwash.api;

import de.lardtom.propwash.api.build.BuildAccess;
import de.lardtom.propwash.api.part.PartRegistry;
import de.lardtom.propwash.api.registry.CreativeEntries;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.registry.LootInjections;
import de.lardtom.propwash.api.registry.PartItems;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.apiimpl.LootInjectionsImpl;
import de.lardtom.propwash.apiimpl.ModTexts;
import de.lardtom.propwash.parts.AddonItems;
import de.lardtom.propwash.parts.PartRegistryImpl;

import java.util.List;
import java.util.Optional;

public final class PropwashApi {
    public static final String MOD_ID = "propwash";
    public static final String API_VERSION = "1.5.0";
    public static final ApiVersion VERSION = ApiVersion.parse(API_VERSION);

    private PropwashApi() {
    }

    public static boolean isCompatible(String requiredVersion) {
        return VERSION.satisfies(ApiVersion.parse(requiredVersion));
    }

    public static void requireVersion(String modId, String requiredVersion) {
        if (!isCompatible(requiredVersion)) {
            throw new IllegalStateException(modId + " braucht die Propwash-API " + requiredVersion + ", vorhanden ist " + API_VERSION
                    + " (gleiche Hauptversion, mindestens diese Nebenversion)");
        }
    }

    public static PartRegistry parts() {
        return PartRegistryImpl.INSTANCE;
    }

    public static PartItems partItems() {
        return AddonItems.INSTANCE;
    }

    public static BuildAccess builds() {
        return BuildAccessImpl.INSTANCE;
    }

    public static CreativeEntries creativeTab() {
        return ApiExtensions.CREATIVE;
    }

    public static ExtensionTab extensionTab(String namespace) {
        return ExtensionTabs.INSTANCE.tab(namespace);
    }

    public static List<ExtensionTab> extensionTabs() {
        return ExtensionTabs.INSTANCE.all();
    }

    public static LootInjections loot() {
        return LootInjectionsImpl.INSTANCE;
    }

    public static Optional<String> text(String languageCode, String key) {
        return ModTexts.INSTANCE.text(languageCode, key);
    }
}

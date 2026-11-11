package de.lardtom.propwash.api;

import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.accessory.DroneQueries;
import de.lardtom.propwash.accessory.FlightHooks;
import de.lardtom.propwash.api.accessory.AccessoryRegistry;
import de.lardtom.propwash.api.build.BuildAccess;
import de.lardtom.propwash.api.drone.DroneAccess;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.flight.FlightExtensions;
import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.part.PartRegistry;
import de.lardtom.propwash.api.race.RaceData;
import de.lardtom.propwash.api.registry.CreativeEntries;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.registry.LootInjections;
import de.lardtom.propwash.api.registry.PartItems;
import de.lardtom.propwash.api.share.ShareCodes;
import de.lardtom.propwash.api.worldrule.WorldRules;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.apiimpl.LootInjectionsImpl;
import de.lardtom.propwash.apiimpl.ModTexts;
import de.lardtom.propwash.apiimpl.RaceDataImpl;
import de.lardtom.propwash.parts.AddonItems;
import de.lardtom.propwash.parts.PartRegistryImpl;
import de.lardtom.propwash.share.ShareCodesImpl;

import java.util.List;
import java.util.Optional;

public final class PropwashApi {
    public static final String MOD_ID = "propwash";
    public static final String API_VERSION = "1.8.0";
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

    public static AccessoryRegistry accessories() {
        return AccessoryBehaviors.INSTANCE;
    }

    public static DroneAccess drones() {
        return DroneQueries.INSTANCE;
    }

    public static FlightExtensions flight() {
        return FlightHooks.INSTANCE;
    }

    public static SwitchRegistry switches() {
        return Api17Registries.SWITCHES;
    }

    public static WorldRules worldRules() {
        return Api17Registries.WORLD_RULES;
    }

    public static DroneDataAccess droneData() {
        return Api17Registries.DRONE_DATA;
    }

    public static ShareCodes shareCodes() {
        return ShareCodesImpl.INSTANCE;
    }

    public static RaceData race() {
        return RaceDataImpl.INSTANCE;
    }

    public static Optional<String> text(String languageCode, String key) {
        return ModTexts.INSTANCE.text(languageCode, key);
    }
}

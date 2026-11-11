package de.lardtom.propwash.neoforge.fabricsync;

import net.neoforged.fml.ModList;

public final class FabricMenuBridge {
    private static final String FABRIC_MENU_API = "fabric_menu_api_v1";

    private FabricMenuBridge() {
    }

    public static boolean enabled() {
        return !ModList.get().isLoaded(FABRIC_MENU_API);
    }
}

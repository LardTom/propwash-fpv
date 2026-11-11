package de.lardtom.propwash.zone;

import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;

public final class ZoneModule {
    public static final RegHolder<ZoneMarkerItem> ZONE_MARKER = PropwashRegistries.item(ZoneMarker.ID, ZoneMarkerItem::new, p -> p.stacksTo(1));
    private static boolean initialized;

    private ZoneModule() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ZoneServer.init();
        ServerBus.REGISTER_COMMANDS.register((dispatcher, context, selection) -> ZoneCommands.register(dispatcher));
    }
}

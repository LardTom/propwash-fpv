package de.lardtom.propwash.zone;

import de.lardtom.propwash.core.event.ServerBus;

public final class ZoneModule {
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

package de.lardtom.propwash.webexport;

import de.lardtom.propwash.core.event.ServerBus;

public final class WebExportModule {
    private static boolean initialized;

    private WebExportModule() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerBus.REGISTER_COMMANDS.register((dispatcher, context, selection) -> WebExportCommand.register(dispatcher));
    }
}

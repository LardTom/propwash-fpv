package de.lardtom.propwash.share;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ShareModule {
    public static final String STRICT_PROPERTY = "propwash.share.strict";
    private static boolean initialized;

    private ShareModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashEvents.REGISTRY_FROZEN.register(registry -> reload());
        PropwashNet.registerC2S(ShareCreativeC2S.TYPE, ShareCreativeC2S.CODEC, ShareServer::onCreative);
    }

    public static ShareTables reload() {
        ShareTables tables = ShareTables.load(Services.PLATFORM.resourceModIds(), new PlatformLoader(), ShareTables.REGISTERED,
                ShareTables.registeredParts());
        ShareTables.install(tables);
        report(tables.problems());
        Constants.LOG.info("Sharecode-Tabellen geladen: {} Namensräume mit Tabelle, {} im Verzeichnis-Abzug", tables.installedTables().size(),
                tables.directory().addons().size());
        return tables;
    }

    public static boolean strict() {
        return Boolean.getBoolean(STRICT_PROPERTY) || Boolean.getBoolean("propwash.selftest");
    }

    static void report(List<String> problems) {
        boolean strict = strict();
        for (String problem : problems) {
            if (strict || problem.startsWith("FEHLER")) {
                Constants.LOG.error("Sharecode-Tabellen: {}", problem);
            } else {
                Constants.LOG.warn("Sharecode-Tabellen: {}", problem);
            }
        }
    }

    private static final class PlatformLoader implements ShareTables.Loader {
        @Override
        public Map<String, byte[]> tables(String modId) {
            return Services.PLATFORM.readModResources(modId, "data", "/" + ShareTableJson.FOLDER + "/" + ShareTableJson.TABLE_FILE);
        }

        @Override
        public Optional<String> version(String modId) {
            return Services.PLATFORM.modVersion(modId);
        }

        @Override
        public Optional<ModMeta> meta(String modId) {
            return Services.PLATFORM.modMeta(modId);
        }

        @Override
        public AddonDirectory directory() {
            return AddonDirectory.builtin();
        }
    }
}

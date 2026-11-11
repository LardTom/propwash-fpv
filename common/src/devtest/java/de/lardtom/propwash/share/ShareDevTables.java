package de.lardtom.propwash.share;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.DevBootstrap;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public final class ShareDevTables {
    public static final String EXAMPLE = ExampleAddon.NAMESPACE;
    public static final String TABLE_PATH = "data/" + EXAMPLE + "/" + ShareTableJson.FOLDER + "/" + ShareTableJson.TABLE_FILE;
    private static final String SUFFIX = "/" + ShareTableJson.FOLDER + "/" + ShareTableJson.TABLE_FILE;
    private static boolean initialized;
    private static int installs;

    private ShareDevTables() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerBus.STARTED.register(server -> ensure());
    }

    public static boolean active() {
        return JoinTestEnv.exampleAddon();
    }

    public static synchronized boolean ensure() {
        if (!active()) {
            return false;
        }
        if (ShareTables.current().installedTable(EXAMPLE).isPresent()) {
            return true;
        }
        List<String> mods = new ArrayList<>(Services.PLATFORM.resourceModIds());
        if (!mods.contains(EXAMPLE)) {
            mods.add(EXAMPLE);
        }
        ShareTables tables = ShareTables.load(mods, new Loader(), ShareTables.REGISTERED, ShareTables.registeredParts());
        ShareTables.install(tables);
        installs++;
        for (String problem : tables.problems()) {
            Constants.LOG.warn("Sharecode-Tabellen (Entwicklung): {}", problem);
        }
        Constants.LOG.info("Sharecode-Tabellen mit Beispiel-Add-on {}: {} Namensräume mit Tabelle, {} Meldungen", EXAMPLE, tables.installedTables().size(),
                tables.problems().size());
        return tables.installedTable(EXAMPLE).isPresent();
    }

    public static int installs() {
        return installs;
    }

    static final class Loader implements ShareTables.Loader {
        @Override
        public Map<String, byte[]> tables(String modId) {
            if (modId.equals(EXAMPLE)) {
                SortedMap<String, byte[]> out = new TreeMap<>();
                for (String mod : Services.PLATFORM.resourceModIds()) {
                    for (Map.Entry<String, byte[]> entry : Services.PLATFORM.readModResources(mod, "data", SUFFIX).entrySet()) {
                        if (entry.getKey().equals(TABLE_PATH)) {
                            out.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                return out;
            }
            SortedMap<String, byte[]> out = new TreeMap<>(Services.PLATFORM.readModResources(modId, "data", SUFFIX));
            out.keySet().removeIf(path -> path.startsWith("data/" + EXAMPLE + "/"));
            return out;
        }

        @Override
        public Optional<String> version(String modId) {
            if (modId.equals(EXAMPLE)) {
                return Optional.of(DevBootstrap.EXAMPLE_VERSION);
            }
            return Services.PLATFORM.modVersion(modId);
        }

        @Override
        public Optional<ModMeta> meta(String modId) {
            if (modId.equals(EXAMPLE)) {
                return Optional.of(new ModMeta(EXAMPLE, DevBootstrap.EXAMPLE_NAME, DevBootstrap.EXAMPLE_VERSION, DevBootstrap.EXAMPLE_HOMEPAGE, false));
            }
            return Services.PLATFORM.modMeta(modId);
        }

        @Override
        public AddonDirectory directory() {
            return AddonDirectory.builtin();
        }
    }
}

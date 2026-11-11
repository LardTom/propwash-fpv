package de.lardtom.propwash.webexport;

import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;

import java.util.Locale;
import java.util.Optional;
import java.util.SortedMap;

public interface ModResources {
    boolean loaded(String modId);

    SortedMap<String, byte[]> read(String modId, String folder, String suffix);

    Optional<ModMeta> meta(String modId);

    String loader();

    Optional<String> version(String modId);

    static ModResources platform() {
        return new ModResources() {
            @Override
            public boolean loaded(String modId) {
                return Services.PLATFORM.isModLoaded(modId);
            }

            @Override
            public SortedMap<String, byte[]> read(String modId, String folder, String suffix) {
                return Services.PLATFORM.readModResources(modId, folder, suffix);
            }

            @Override
            public Optional<ModMeta> meta(String modId) {
                return Services.PLATFORM.modMeta(modId);
            }

            @Override
            public String loader() {
                return Services.PLATFORM.getPlatformName().toLowerCase(Locale.ROOT);
            }

            @Override
            public Optional<String> version(String modId) {
                return Services.PLATFORM.modVersion(modId);
            }
        };
    }
}

package de.lardtom.propwash.guard;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RegistryOrigins {
    private static final Map<String, Optional<String>> ORIGINS = new ConcurrentHashMap<>();

    private RegistryOrigins() {
    }

    public static void record(String registry, String id) {
        String key = key(registry, id);
        if (!ORIGINS.containsKey(key)) {
            ORIGINS.putIfAbsent(key, NamespaceGuard.registrant());
        }
    }

    public static Optional<Optional<String>> get(String registry, String id) {
        return Optional.ofNullable(ORIGINS.get(key(registry, id)));
    }

    private static String key(String registry, String id) {
        return registry + " " + id;
    }
}

package de.lardtom.propwash.guard.client;

import de.lardtom.propwash.guard.NamespaceGuard;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyMappingOrigins {
    public static final String PREFIX = "key.propwash.";
    private static final Map<String, Optional<String>> ORIGINS = new ConcurrentHashMap<>();

    private KeyMappingOrigins() {
    }

    public static void record(String name) {
        if (name != null && name.startsWith(PREFIX) && !ORIGINS.containsKey(name)) {
            ORIGINS.putIfAbsent(name, NamespaceGuard.registrant());
        }
    }

    public static Optional<String> get(String name) {
        return ORIGINS.getOrDefault(name, Optional.empty());
    }
}

package de.lardtom.propwash.zone;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.IntFunction;

public final class ZoneNames {
    private static volatile @Nullable IntFunction<@Nullable String> server;
    private static volatile @Nullable IntFunction<@Nullable String> client;

    private ZoneNames() {
    }

    public static void server(@Nullable IntFunction<@Nullable String> lookup) {
        server = lookup;
    }

    public static void client(@Nullable IntFunction<@Nullable String> lookup) {
        client = lookup;
    }

    public static Optional<String> name(int id) {
        IntFunction<@Nullable String> first = server;
        String found = first == null ? null : first.apply(id);
        if (found == null) {
            IntFunction<@Nullable String> second = client;
            found = second == null ? null : second.apply(id);
        }
        return Optional.ofNullable(found);
    }
}

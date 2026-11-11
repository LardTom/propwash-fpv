package de.lardtom.propwash.guard;

import java.util.Map;

public final class OfficialAddons {
    public static final Map<String, String> RESERVED_SUBCOMMANDS = Map.of();

    private OfficialAddons() {
    }

    public static boolean allowed(String subcommand, String mod, boolean loaded) {
        String owner = RESERVED_SUBCOMMANDS.get(subcommand);
        return owner != null && owner.equals(mod) && loaded;
    }
}

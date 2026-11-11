package de.lardtom.propwash.guard;

import java.util.Locale;

public enum GuardCategory {
    REGISTRY("registry ids", true),
    TRANSLATION("translation keys", false),
    KEY_MAPPING("key mappings", false),
    NETWORK("network payloads", true),
    ENUM("enum values", true),
    DATA("data files", true),
    ASSET("assets", false),
    COMMAND("commands", true),
    REFUSED("refused registrations", true);

    private final String logName;
    private final boolean server;

    GuardCategory(String logName, boolean server) {
        this.logName = logName;
        this.server = server;
    }

    public String logName() {
        return this.logName;
    }

    public boolean server() {
        return this.server;
    }

    public String translationKey() {
        return "propwash.guard.category." + this.name().toLowerCase(Locale.ROOT);
    }
}

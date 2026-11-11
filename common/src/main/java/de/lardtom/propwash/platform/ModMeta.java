package de.lardtom.propwash.platform;

public record ModMeta(String id, String name, String version, String homepage, boolean clientOptional) {
    public static final String CLIENT_KEY = "propwash:client";
    public static final String CLIENT_OPTIONAL = "optional";
}

package de.lardtom.propwash.core;

import de.lardtom.propwash.Constants;

public final class DevExtensions {
    public static final String COMMON = "de.lardtom.propwash.dev.DevBootstrap";
    public static final String CLIENT = "de.lardtom.propwash.dev.client.DevClientBootstrap";

    private DevExtensions() {
    }

    public static boolean requested() {
        return Boolean.getBoolean("propwash.selftest") || Boolean.getBoolean("propwash.smoketest") || Boolean.getBoolean("propwash.serversmoke");
    }

    public static boolean present() {
        return find(COMMON) != null;
    }

    public static void common() {
        run(COMMON);
    }

    public static void client() {
        run(CLIENT);
    }

    private static void run(String name) {
        Class<?> type = find(name);
        if (type == null) {
            if (requested()) {
                Constants.LOG.error("Propwash-Entwicklertests angefordert, aber {} fehlt im Klassenpfad (Release-Jar enthält sie nicht)", name);
            }
            return;
        }
        try {
            type.getMethod("init").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Propwash-Entwicklertests konnten nicht starten: " + name, e);
        }
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, true, DevExtensions.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}

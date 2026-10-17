package de.lardtom.propwash.dev;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JoinTestEnv {
    public static final String EXAMPLE = "PROPWASH_DEV_EXAMPLE";
    public static final String PROTOCOL = "PROPWASH_DEV_PROTOCOL";
    public static final String TARGET = "PROPWASH_JOINTEST";
    public static final String LABEL = "PROPWASH_JOINTEST_LABEL";
    public static final String EXPECT = "PROPWASH_JOINTEST_EXPECT";
    public static final String HOLD = "PROPWASH_JOINTEST_HOLD";
    public static final String PILOT = "PROPWASH_JOINTEST_PILOT";
    public static final String TRAIL = "PROPWASH_JOINTEST_TRAIL";
    public static final String ASSIST = "PROPWASH_JOINTEST_ASSIST";
    public static final String ZONES = "PROPWASH_JOINTEST_ZONES";
    public static final String STOP_FILE = "jointest.stop";
    public static final String JOINED = "joined";

    private JoinTestEnv() {
    }

    public static boolean exampleAddon() {
        String value = get(EXAMPLE);
        return value == null || !value.trim().toLowerCase(Locale.ROOT).matches("off|false|0|no");
    }

    public static int protocolOverride() {
        return integer(PROTOCOL, 0);
    }

    public static @Nullable String target() {
        String value = get(TARGET);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String label() {
        String value = get(LABEL);
        return value == null || value.isBlank() ? "join" : value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public static List<String> expectations() {
        String value = get(EXPECT);
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String part : value.split("\\|")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    public static int holdSeconds() {
        return integer(HOLD, 0);
    }

    public static int pilotSeconds() {
        return integer(PILOT, 0);
    }

    public static int trailSeconds() {
        return integer(TRAIL, 0);
    }

    public static boolean assist() {
        return integer(ASSIST, 0) > 0;
    }

    public static boolean zones() {
        return integer(ZONES, 0) > 0;
    }

    private static int integer(String name, int fallback) {
        String value = get(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static @Nullable String get(String name) {
        String property = System.getProperty(name.toLowerCase(Locale.ROOT).replace('_', '.'));
        return property != null ? property : System.getenv(name);
    }
}

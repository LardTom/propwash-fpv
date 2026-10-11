package de.lardtom.propwash.ui.client.theme;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ThemeStats {
    public enum Kind {
        BUTTON,
        CYCLE,
        SLIDER,
        CHECKBOX,
        EDIT_BOX,
        TOOLTIP
    }

    private static final Map<Kind, long[]> THEMED = new EnumMap<>(Kind.class);
    private static final Map<Kind, long[]> VANILLA = new EnumMap<>(Kind.class);
    private static final Map<Class<?>, Long> FRAMES = new HashMap<>();
    private static @Nullable Map<String, String> cuts;
    private static boolean tooltipTracked;
    private static int @Nullable [] tooltip;

    static {
        for (Kind kind : Kind.values()) {
            THEMED.put(kind, new long[1]);
            VANILLA.put(kind, new long[1]);
        }
    }

    private ThemeStats() {
    }

    public static void themed(Kind kind) {
        THEMED.get(kind)[0]++;
    }

    public static void vanilla(Kind kind) {
        VANILLA.get(kind)[0]++;
    }

    public static long themedCount(Kind kind) {
        return THEMED.get(kind)[0];
    }

    public static long vanillaCount(Kind kind) {
        return VANILLA.get(kind)[0];
    }

    public static long themedTotal() {
        long sum = 0L;
        for (long[] count : THEMED.values()) {
            sum += count[0];
        }
        return sum;
    }

    public static long vanillaTotal() {
        long sum = 0L;
        for (long[] count : VANILLA.values()) {
            sum += count[0];
        }
        return sum;
    }

    public static void frame(ThemedScreen screen) {
        FRAMES.merge(screen.getClass(), 1L, Long::sum);
    }

    public static long frames(Class<?> screenClass) {
        return FRAMES.getOrDefault(screenClass, 0L);
    }

    public static void trackTooltip(boolean enabled) {
        tooltipTracked = enabled;
        tooltip = null;
    }

    public static void tooltip(int x, int y, int width, int height) {
        if (tooltipTracked) {
            tooltip = new int[]{x, y, width, height};
        }
    }

    public static int @Nullable [] takeTooltip() {
        int[] placed = tooltip;
        tooltip = null;
        return placed;
    }

    public static void trackCuts(boolean enabled) {
        cuts = enabled ? new LinkedHashMap<>() : null;
    }

    public static void cut(String full, String shown) {
        Map<String, String> tracked = cuts;
        if (tracked != null) {
            tracked.put(full, shown);
        }
    }

    public static Map<String, String> takeCuts() {
        Map<String, String> tracked = cuts;
        if (tracked == null) {
            return Map.of();
        }
        cuts = new LinkedHashMap<>();
        return tracked;
    }
}

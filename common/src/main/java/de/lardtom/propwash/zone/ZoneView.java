package de.lardtom.propwash.zone;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ZoneView {
    public enum Show {
        TOOL,
        TOOL_OR_FLIGHT,
        ALWAYS;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Style {
        FULL,
        EDGES,
        HIGHLIGHT
    }

    public static final Show DEFAULT_SHOW = Show.TOOL_OR_FLIGHT;

    private ZoneView() {
    }

    public static boolean outlines(@Nullable Show show, boolean holdingTool, boolean editing, boolean flying) {
        return show == Show.ALWAYS || show == Show.TOOL_OR_FLIGHT && flying || holdingTool || editing;
    }

    public static boolean overlap(ZoneBox a, ZoneBox b) {
        return a.minX() < b.maxX() && b.minX() < a.maxX() && a.minY() < b.maxY() && b.minY() < a.maxY() && a.minZ() < b.maxZ() && b.minZ() < a.maxZ();
    }

    public static int highlight(int focus, Collection<Zone> zones, double x, double y, double z) {
        if (focus != 0) {
            return focus;
        }
        List<Zone> inside = ZoneResolver.containing(zones, x, y, z);
        return inside.isEmpty() ? 0 : inside.getFirst().id();
    }

    public static Map<Integer, Style> styles(List<Zone> zones, @Nullable ZoneBox draft, int highlight) {
        Map<Integer, Style> out = new LinkedHashMap<>();
        for (Zone zone : zones) {
            if (zone.id() == highlight) {
                out.put(zone.id(), Style.HIGHLIGHT);
                continue;
            }
            boolean overlapping = draft != null && overlap(zone.box(), draft);
            for (int i = 0; i < zones.size() && !overlapping; i++) {
                Zone other = zones.get(i);
                overlapping = other.id() != zone.id() && overlap(zone.box(), other.box());
            }
            out.put(zone.id(), overlapping ? Style.EDGES : Style.FULL);
        }
        return out;
    }
}

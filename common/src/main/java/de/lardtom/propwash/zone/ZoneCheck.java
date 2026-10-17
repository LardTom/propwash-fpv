package de.lardtom.propwash.zone;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;

public final class ZoneCheck {
    public static final String NAME_EMPTY = "name_empty";
    public static final String NAME_TAKEN = "name_taken";
    public static final String TOO_SMALL = "too_small";
    public static final String TOO_LARGE = "too_large";
    public static final String OUTSIDE = "outside";
    public static final String TOO_MANY = "too_many";
    public static final String UNKNOWN = "unknown";
    public static final String DENIED = "denied";

    private ZoneCheck() {
    }

    public static @Nullable String problem(Zone zone) {
        if (zone.name().isEmpty()) {
            return NAME_EMPTY;
        }
        ZoneBox box = zone.box();
        if (!box.finite() || zone.dimension().length() > ZoneLimits.MAX_DIMENSION) {
            return OUTSIDE;
        }
        if (box.sizeX() < ZoneLimits.MIN_SIZE || box.sizeY() < ZoneLimits.MIN_SIZE || box.sizeZ() < ZoneLimits.MIN_SIZE) {
            return TOO_SMALL;
        }
        if (box.sizeX() > ZoneLimits.MAX_SIZE || box.sizeY() > ZoneLimits.MAX_SIZE || box.sizeZ() > ZoneLimits.MAX_SIZE) {
            return TOO_LARGE;
        }
        if (Math.abs(box.minX()) > ZoneLimits.MAX_COORDINATE || Math.abs(box.maxX()) > ZoneLimits.MAX_COORDINATE
                || Math.abs(box.minZ()) > ZoneLimits.MAX_COORDINATE || Math.abs(box.maxZ()) > ZoneLimits.MAX_COORDINATE
                || box.minY() < ZoneLimits.MIN_Y || box.maxY() > ZoneLimits.MAX_Y) {
            return OUTSIDE;
        }
        return null;
    }

    public static @Nullable String problem(Zone zone, Collection<Zone> existing) {
        String own = problem(zone);
        if (own != null) {
            return own;
        }
        boolean known = zone.id() == Zone.NEW;
        int count = 0;
        for (Zone other : existing) {
            if (other.id() == zone.id()) {
                known = true;
                continue;
            }
            count++;
            if (sameName(other.name(), zone.name())) {
                return NAME_TAKEN;
            }
        }
        if (!known) {
            return UNKNOWN;
        }
        if (zone.id() == Zone.NEW && count >= ZoneLimits.MAX_ZONES) {
            return TOO_MANY;
        }
        return null;
    }

    public static boolean sameName(String a, String b) {
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }
}

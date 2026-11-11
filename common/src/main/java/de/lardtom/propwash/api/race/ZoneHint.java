package de.lardtom.propwash.api.race;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record ZoneHint(Status status, List<Zone> zones) {
    public static final ZoneHint NONE = new ZoneHint(Status.NONE, List.of());

    public enum Status {
        NONE,
        RACE_LEGAL,
        ASSIST;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Zone(int id, String name, boolean raceLegal, boolean changesPhysics) {
        public Zone {
            Objects.requireNonNull(name, "name");
        }

        public boolean countsAsAssist() {
            return !this.raceLegal && this.changesPhysics;
        }
    }

    public ZoneHint {
        Objects.requireNonNull(status, "status");
        zones = List.copyOf(zones);
    }

    public static ZoneHint of(List<Zone> zones) {
        if (zones.isEmpty()) {
            return NONE;
        }
        boolean assist = false;
        for (Zone zone : zones) {
            assist |= zone.countsAsAssist();
        }
        return new ZoneHint(assist ? Status.ASSIST : Status.RACE_LEGAL, zones);
    }
}

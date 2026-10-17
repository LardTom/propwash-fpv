package de.lardtom.propwash.osd;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static de.lardtom.propwash.osd.OsdElement.ALTITUDE;
import static de.lardtom.propwash.osd.OsdElement.ARMED_STATUS;
import static de.lardtom.propwash.osd.OsdElement.BATTERY_VOLTAGE;
import static de.lardtom.propwash.osd.OsdElement.BEST_LAP;
import static de.lardtom.propwash.osd.OsdElement.CELL_VOLTAGE;
import static de.lardtom.propwash.osd.OsdElement.CLOCK;
import static de.lardtom.propwash.osd.OsdElement.CROSSHAIR;
import static de.lardtom.propwash.osd.OsdElement.CURRENT;
import static de.lardtom.propwash.osd.OsdElement.FLASHBACK_BUDGET;
import static de.lardtom.propwash.osd.OsdElement.FLIGHT_MODE;
import static de.lardtom.propwash.osd.OsdElement.FLIGHT_TIME;
import static de.lardtom.propwash.osd.OsdElement.GATE;
import static de.lardtom.propwash.osd.OsdElement.GPS_SATS;
import static de.lardtom.propwash.osd.OsdElement.HEADING;
import static de.lardtom.propwash.osd.OsdElement.HOME_DIRECTION;
import static de.lardtom.propwash.osd.OsdElement.HOME_DISTANCE;
import static de.lardtom.propwash.osd.OsdElement.LAP;
import static de.lardtom.propwash.osd.OsdElement.LAP_TIME;
import static de.lardtom.propwash.osd.OsdElement.LAST_LAP;
import static de.lardtom.propwash.osd.OsdElement.LEDS_OFF;
import static de.lardtom.propwash.osd.OsdElement.LINK_QUALITY;
import static de.lardtom.propwash.osd.OsdElement.MAH_DRAWN;
import static de.lardtom.propwash.osd.OsdElement.ON_TIME;
import static de.lardtom.propwash.osd.OsdElement.POST_FLIGHT_STATS;
import static de.lardtom.propwash.osd.OsdElement.POWER;
import static de.lardtom.propwash.osd.OsdElement.RECORDING;
import static de.lardtom.propwash.osd.OsdElement.RELAY;
import static de.lardtom.propwash.osd.OsdElement.RSSI_DBM;
import static de.lardtom.propwash.osd.OsdElement.SPEED;
import static de.lardtom.propwash.osd.OsdElement.SPLIT_DELTA;
import static de.lardtom.propwash.osd.OsdElement.THROTTLE;
import static de.lardtom.propwash.osd.OsdElement.TRICKS;
import static de.lardtom.propwash.osd.OsdElement.VIDEO_SIGNAL;
import static de.lardtom.propwash.osd.OsdElement.VIDEO_SYSTEM;
import static de.lardtom.propwash.osd.OsdElement.VTX_CHANNEL;
import static de.lardtom.propwash.osd.OsdElement.VTX_POWER;
import static de.lardtom.propwash.osd.OsdElement.WARNINGS;

public enum OsdPreset {
    MINIMAL(false,
            at(CROSSHAIR),
            at(LINK_QUALITY, 1, 1),
            at(VIDEO_SIGNAL, 1, 2),
            at(FLIGHT_MODE, 28, 2),
            at(LAP, 28, 3),
            at(LAP_TIME, 28, 4),
            at(WARNINGS, 15, 3),
            at(BATTERY_VOLTAGE, 1, 14),
            at(FLIGHT_TIME, 28, 14),
            at(RECORDING, 28, 1),
            at(FLASHBACK_BUDGET, 28, 5),
            at(LEDS_OFF, 1, 3),
            at(VTX_CHANNEL, 1, 5),
            at(RELAY, 1, 6),
            at(VTX_POWER, 1, 7),
            at(TRICKS, 15, 13),
            park(RSSI_DBM, 6, 1),
            park(SPEED, 1, 8),
            park(VIDEO_SYSTEM, 28, 12),
            park(ON_TIME, 21, 15)),
    STANDARD(false,
            at(CROSSHAIR),
            at(LINK_QUALITY, 1, 1),
            at(VIDEO_SIGNAL, 1, 2),
            at(GATE, 1, 4),
            at(HOME_DIRECTION, 13, 1),
            at(HOME_DISTANCE, 14, 1),
            at(FLIGHT_MODE, 28, 1),
            at(POST_FLIGHT_STATS, 15, 1),
            at(LAP, 28, 3),
            at(LAP_TIME, 28, 4),
            at(BEST_LAP, 28, 5),
            at(SPLIT_DELTA, 15, 5),
            at(ARMED_STATUS, 15, 9),
            at(WARNINGS, 15, 10),
            at(SPEED, 1, 12),
            at(MAH_DRAWN, 1, 13),
            at(BATTERY_VOLTAGE, 1, 14),
            at(CELL_VOLTAGE, 5, 14),
            at(GPS_SATS, 28, 11),
            at(ALTITUDE, 28, 12),
            at(THROTTLE, 28, 13),
            at(FLIGHT_TIME, 28, 14),
            at(RECORDING, 28, 2),
            at(FLASHBACK_BUDGET, 28, 6),
            at(LEDS_OFF, 1, 3),
            at(VTX_CHANNEL, 1, 5),
            at(RELAY, 1, 6),
            at(VTX_POWER, 1, 7),
            at(TRICKS, 15, 13),
            park(HEADING, 15, 2),
            park(RSSI_DBM, 6, 1),
            park(CURRENT, 1, 8),
            park(POWER, 6, 8),
            park(VIDEO_SYSTEM, 28, 8),
            park(CLOCK, 28, 15),
            park(ON_TIME, 21, 15),
            park(LAST_LAP, 28, 7)),
    FULL(true,
            at(POST_FLIGHT_STATS, 15, 1),
            at(FLASHBACK_BUDGET, 15, 4)),
    RACE(false,
            at(CROSSHAIR),
            at(LINK_QUALITY, 1, 1),
            at(VIDEO_SIGNAL, 1, 2),
            at(GATE, 1, 4),
            at(LAP, 28, 2),
            at(LAP_TIME, 28, 3),
            at(LAST_LAP, 28, 4),
            at(BEST_LAP, 28, 5),
            at(SPLIT_DELTA, 15, 4),
            at(ARMED_STATUS, 15, 9),
            at(WARNINGS, 15, 10),
            at(SPEED, 1, 13),
            at(BATTERY_VOLTAGE, 1, 14),
            at(THROTTLE, 28, 14),
            at(RECORDING, 28, 1),
            at(FLASHBACK_BUDGET, 28, 6),
            at(LEDS_OFF, 1, 3),
            at(VTX_CHANNEL, 1, 5),
            at(RELAY, 1, 6),
            at(VTX_POWER, 1, 7),
            park(RSSI_DBM, 6, 1),
            park(MAH_DRAWN, 1, 8),
            park(VIDEO_SYSTEM, 28, 7),
            park(ON_TIME, 21, 15),
            park(FLIGHT_TIME, 28, 13));

    private final Set<OsdElement> enabled = EnumSet.noneOf(OsdElement.class);
    private final Map<OsdElement, Place> places = new EnumMap<>(OsdElement.class);
    private final String key;

    OsdPreset(boolean all, Place... places) {
        if (all) {
            this.enabled.addAll(EnumSet.allOf(OsdElement.class));
        }
        for (Place place : places) {
            if (place.on()) {
                this.enabled.add(place.element());
            }
            if (place.element().movable()) {
                this.places.put(place.element(), place);
            }
        }
        this.key = this.name().toLowerCase(Locale.ROOT);
    }

    private static Place at(OsdElement element) {
        return new Place(element, element.baseCol(), element.baseRow(), true);
    }

    private static Place at(OsdElement element, int col, int row) {
        return new Place(element, OsdGrid.clampCol(col), OsdGrid.clampRow(row), true);
    }

    private static Place park(OsdElement element, int col, int row) {
        return new Place(element, OsdGrid.clampCol(col), OsdGrid.clampRow(row), false);
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.osd.preset." + this.key;
    }

    public boolean enabled(OsdElement element) {
        return this.enabled.contains(element);
    }

    public int col(OsdElement element) {
        Place place = this.places.get(element);
        return place == null ? element.baseCol() : place.col();
    }

    public int row(OsdElement element) {
        Place place = this.places.get(element);
        return place == null ? element.baseRow() : place.row();
    }

    public OsdConfig.Slot slot(OsdElement element) {
        return new OsdConfig.Slot(this.enabled(element), this.col(element), this.row(element));
    }

    public int enabledCount() {
        return this.enabled.size();
    }

    public boolean matches(OsdConfig config) {
        for (OsdElement element : OsdElement.values()) {
            OsdConfig.Slot slot = config.slot(element);
            boolean on = this.enabled(element);
            if (slot.enabled != on) {
                return false;
            }
            if (on && element.movable() && (slot.col != this.col(element) || slot.row != this.row(element))) {
                return false;
            }
        }
        return true;
    }

    public OsdPreset next() {
        OsdPreset[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    public boolean parked(OsdElement element) {
        Place place = this.places.get(element);
        return place != null && !place.on();
    }

    private record Place(OsdElement element, int col, int row, boolean on) {
    }
}

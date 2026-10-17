package de.lardtom.propwash.osd;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum OsdElement {
    CROSSHAIR(15, 8, Align.CENTER, true, false, Group.FLIGHT),
    ARTIFICIAL_HORIZON(15, 8, Align.CENTER, true, false, Group.FLIGHT),
    HORIZON_SIDEBARS(15, 8, Align.CENTER, true, false, Group.FLIGHT),
    HOME_MARKER(15, 8, Align.CENTER, true, false, Group.HOME),
    COMPASS_BAR(15, 0, Align.CENTER, true, true, Group.FLIGHT),
    HEADING(15, 1, Align.CENTER, true, true, Group.FLIGHT),
    HOME_DIRECTION(13, 2, Align.CENTER, true, true, Group.HOME),
    HOME_DISTANCE(14, 2, Align.LEFT, true, true, Group.HOME),
    LINK_QUALITY(1, 1, Align.LEFT, true, true, Group.LINK),
    VIDEO_SIGNAL(1, 2, Align.LEFT, true, true, Group.LINK),
    RSSI_DBM(1, 3, Align.LEFT, false, true, Group.LINK),
    GATE(1, 4, Align.LEFT, true, true, Group.RACE),
    SPEED(1, 7, Align.LEFT, true, true, Group.FLIGHT),
    PITCH_ANGLE(1, 9, Align.LEFT, false, true, Group.FLIGHT),
    ROLL_ANGLE(1, 10, Align.LEFT, false, true, Group.FLIGHT),
    BATTERY_BAR(1, 11, Align.LEFT, true, true, Group.BATTERY),
    CURRENT(1, 12, Align.LEFT, true, true, Group.BATTERY),
    POWER(5, 12, Align.LEFT, false, true, Group.BATTERY),
    MAH_DRAWN(1, 13, Align.LEFT, true, true, Group.BATTERY),
    BATTERY_VOLTAGE(1, 14, Align.LEFT, true, true, Group.BATTERY),
    CELL_VOLTAGE(5, 14, Align.LEFT, true, true, Group.BATTERY),
    COORDINATES(1, 15, Align.LEFT, false, true, Group.FLIGHT),
    POST_FLIGHT_STATS(15, 3, Align.CENTER, true, true, Group.STATUS),
    SPLIT_DELTA(15, 5, Align.CENTER, true, true, Group.RACE),
    ARMED_STATUS(15, 9, Align.CENTER, true, true, Group.STATUS),
    WARNINGS(15, 10, Align.CENTER, true, true, Group.STATUS),
    STICK_OVERLAY(15, 12, Align.CENTER, false, true, Group.STATUS),
    CRAFT_NAME(15, 14, Align.CENTER, true, true, Group.STATUS),
    VIDEO_SYSTEM(28, 1, Align.RIGHT, true, true, Group.LINK),
    CLOCK(28, 11, Align.RIGHT, false, true, Group.STATUS),
    ON_TIME(28, 3, Align.RIGHT, false, true, Group.STATUS),
    LAP(28, 4, Align.RIGHT, true, true, Group.RACE),
    LAP_TIME(28, 5, Align.RIGHT, true, true, Group.RACE),
    LAST_LAP(28, 6, Align.RIGHT, true, true, Group.RACE),
    BEST_LAP(28, 7, Align.RIGHT, true, true, Group.RACE),
    ALTITUDE(28, 8, Align.RIGHT, true, true, Group.FLIGHT),
    VARIO(28, 9, Align.RIGHT, true, true, Group.FLIGHT),
    G_FORCE(28, 10, Align.RIGHT, false, true, Group.FLIGHT),
    FLIGHT_MODE(28, 12, Align.RIGHT, true, true, Group.STATUS),
    THROTTLE(28, 13, Align.RIGHT, true, true, Group.STATUS),
    FLIGHT_TIME(28, 14, Align.RIGHT, true, true, Group.STATUS),
    RECORDING(28, 2, Align.RIGHT, true, true, Group.STATUS),
    LEDS_OFF(1, 5, Align.LEFT, true, true, Group.STATUS),
    GPS_SATS(28, 15, Align.RIGHT, true, true, Group.HOME),
    VTX_CHANNEL(1, 6, Align.LEFT, true, true, Group.LINK),
    RELAY(1, 8, Align.LEFT, true, true, Group.LINK),
    TRICKS(15, 13, Align.CENTER, true, true, Group.STATUS),
    FLASHBACK_BUDGET(28, 3, Align.RIGHT, true, true, Group.STATUS),
    VTX_POWER(8, 6, Align.LEFT, true, true, Group.LINK);

    private final int baseCol;
    private final int baseRow;
    private final Align align;
    private final boolean classicEnabled;
    private final boolean movable;
    private final Group group;
    private final String key;

    OsdElement(int baseCol, int baseRow, Align align, boolean classicEnabled, boolean movable, Group group) {
        this.baseCol = baseCol;
        this.baseRow = baseRow;
        this.align = align;
        this.classicEnabled = classicEnabled;
        this.movable = movable;
        this.group = group;
        this.key = this.name().toLowerCase(Locale.ROOT);
    }

    public int baseCol() {
        return this.baseCol;
    }

    public int baseRow() {
        return this.baseRow;
    }

    public Align align() {
        return this.align;
    }

    public boolean classicEnabled() {
        return this.classicEnabled;
    }

    public boolean movable() {
        return this.movable;
    }

    public Group group() {
        return this.group;
    }

    public boolean usesLineOpacity() {
        return this == CROSSHAIR || this == ARTIFICIAL_HORIZON || this == HORIZON_SIDEBARS;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.osd.element." + this.key;
    }

    public static @Nullable OsdElement byKey(String key) {
        for (OsdElement element : values()) {
            if (element.key.equals(key)) {
                return element;
            }
        }
        return null;
    }

    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum Group {
        FLIGHT,
        HOME,
        LINK,
        BATTERY,
        RACE,
        STATUS
    }
}

package de.lardtom.propwash.zone;

public final class ZoneMarker {
    public static final String ID = "zone_marker";
    public static final double REACH = 64.0;

    private ZoneMarker() {
    }

    public static ZoneBox box(int ax, int ay, int az, int bx, int by, int bz) {
        ZoneBox box = ZoneBox.blocks(ax, ay, az, bx, by, bz);
        if (ay == by) {
            return box.sized(box.sizeX(), ZoneDraft.NEW_HEIGHT, box.sizeZ());
        }
        return box;
    }
}

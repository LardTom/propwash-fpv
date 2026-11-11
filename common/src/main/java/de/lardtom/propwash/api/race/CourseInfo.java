package de.lardtom.propwash.api.race;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CourseInfo(UUID id, int number, String name, Optional<UUID> owner, String ownerName, Identifier dimension, int layout,
                         List<Integer> layouts, int laps, int elements, int checkpoints, boolean worldCourse, Optional<BlockPos> start,
                         ZoneHint zones, Optional<BoardEntry> record, CourseMode mode) {
    public static final int LAPS_PILOT_CHOICE = -1;
    public static final int LAPS_ENDLESS = 0;

    public CourseInfo {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name;
        Objects.requireNonNull(owner, "owner");
        ownerName = ownerName == null ? "" : ownerName;
        Objects.requireNonNull(dimension, "dimension");
        layouts = List.copyOf(layouts);
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(zones, "zones");
        Objects.requireNonNull(record, "record");
        mode = mode == null ? CourseMode.LAPS : mode;
    }

    public CourseInfo(UUID id, int number, String name, Optional<UUID> owner, String ownerName, Identifier dimension, int layout,
                      List<Integer> layouts, int laps, int elements, int checkpoints, boolean worldCourse, Optional<BlockPos> start,
                      ZoneHint zones, Optional<BoardEntry> record) {
        this(id, number, name, owner, ownerName, dimension, layout, layouts, laps, elements, checkpoints, worldCourse, start, zones, record,
                CourseMode.LAPS);
    }

    public boolean pointToPoint() {
        return this.mode.pointToPoint();
    }

    public boolean named() {
        return !this.name.isEmpty();
    }

    public String label() {
        return this.named() ? this.name : "#" + this.number;
    }

    public boolean lapsSet() {
        return this.laps >= LAPS_ENDLESS;
    }

    public boolean endless() {
        return this.laps == LAPS_ENDLESS;
    }
}

package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum GateMount implements StringRepresentable {
    STANDING(Surface.FLOOR, 4),
    FLOOR_FLAT(Surface.FLOOR, 2),
    WALL_DIVE(Surface.WALL, 2),
    WALL_SIDE(Surface.WALL, 2),
    WALL_WINDOW(Surface.WALL, 2),
    HANGING(Surface.CEILING, 4),
    CEILING_FLAT(Surface.CEILING, 2);

    public static final Codec<GateMount> CODEC = StringRepresentable.fromEnum(GateMount::values);
    public static final double HANG_DROP = 1.0;
    private static final int FLIP_BIT = 0x80;
    private static final int ORDINAL_MASK = 0x7F;

    private final Surface surface;
    private final int turns;

    GateMount(Surface surface, int turns) {
        this.surface = surface;
        this.turns = turns;
    }

    public enum Surface {
        FLOOR,
        WALL,
        CEILING;

        public GateMount defaultMount() {
            return switch (this) {
                case FLOOR -> STANDING;
                case WALL -> WALL_DIVE;
                case CEILING -> HANGING;
            };
        }

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public static Surface byOrdinal(int ordinal) {
            Surface[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FLOOR;
        }
    }

    public Surface surface() {
        return this.surface;
    }

    public int turns() {
        return this.turns;
    }

    public boolean reversible() {
        return this.turns == 2;
    }

    public boolean upright() {
        return this == STANDING || this == HANGING || this == WALL_SIDE || this == WALL_WINDOW;
    }

    public boolean horizontal() {
        return this == FLOOR_FLAT || this == CEILING_FLAT || this == WALL_DIVE;
    }

    public boolean stackable() {
        return this == STANDING;
    }

    public GateStacking.Base base() {
        return this == STANDING || this == WALL_DIVE ? GateStacking.Base.BAR : GateStacking.Base.MOUNTED;
    }

    public GateMount next() {
        GateMount[] values = values();
        for (int step = 1; step <= values.length; step++) {
            GateMount candidate = values[(this.ordinal() + step) % values.length];
            if (candidate.surface == this.surface) {
                return candidate;
            }
        }
        return this;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return this.key();
    }

    public static GateMount byOrdinal(int ordinal) {
        GateMount[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : STANDING;
    }

    public static GateMount byKey(String key) {
        for (GateMount mount : values()) {
            if (mount.key().equals(key)) {
                return mount;
            }
        }
        return STANDING;
    }

    public static boolean normalizeFlip(GateMount mount, boolean flipped) {
        return mount.reversible() && flipped;
    }

    public static int pack(GateMount mount, boolean flipped) {
        return mount.ordinal() | (normalizeFlip(mount, flipped) ? FLIP_BIT : 0);
    }

    public static GateMount unpackMount(int packed) {
        return byOrdinal(packed & ORDINAL_MASK);
    }

    public static boolean unpackFlipped(int packed) {
        return normalizeFlip(unpackMount(packed), (packed & FLIP_BIT) != 0);
    }
}

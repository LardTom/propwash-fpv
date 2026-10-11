package de.lardtom.propwash.bando.plan;

public enum Mat {
    EMPTY(Kind.NONE),
    AIR(Kind.NONE),
    WATER(Kind.NONE),
    FOUNDATION(Kind.STRUCTURE),
    FLOOR(Kind.STRUCTURE),
    WALL(Kind.STRUCTURE),
    WALL_ALT(Kind.STRUCTURE),
    COLUMN(Kind.STRUCTURE),
    BEAM(Kind.STRUCTURE),
    BRACE(Kind.STRUCTURE),
    ROOF(Kind.STRUCTURE),
    ROOF_STEP(Kind.STRUCTURE),
    CATWALK(Kind.STRUCTURE),
    RAIL(Kind.STRUCTURE),
    PIPE(Kind.STRUCTURE),
    TANK(Kind.STRUCTURE),
    STACK(Kind.STRUCTURE),
    TRIM(Kind.STRUCTURE),
    CHAIN(Kind.ATTACHED),
    LADDER(Kind.ATTACHED),
    WINDOW(Kind.ATTACHED),
    DEBRIS(Kind.LOOSE),
    DEBRIS_LOW(Kind.LOOSE),
    CARGO(Kind.LOOSE),
    YARD(Kind.LOOSE),
    FOLIAGE(Kind.ATTACHED),
    VINE(Kind.DECOR),
    MOSS(Kind.DECOR),
    FLOAT(Kind.DECOR),
    CHEST(Kind.LOOSE);

    private static final Mat[] VALUES = values();

    private final Kind kind;

    Mat(Kind kind) {
        this.kind = kind;
    }

    public static Mat byId(int id) {
        return VALUES[id];
    }

    public Kind kind() {
        return this.kind;
    }

    public boolean blocksFlight() {
        return this.kind != Kind.NONE && this.kind != Kind.DECOR;
    }

    public boolean placed() {
        return this != EMPTY;
    }

    public boolean holdsUp() {
        return this.kind == Kind.STRUCTURE || this.kind == Kind.LOOSE || this.kind == Kind.ATTACHED;
    }

    public boolean flatTop() {
        return this.holdsUp() && this != ROOF && this != ROOF_STEP && this != DEBRIS_LOW && this != RAIL && this != BRACE && this != CHAIN
                && this != LADDER && this != WINDOW && this != CHEST;
    }

    public boolean carries() {
        return this.flatTop() && this != FOLIAGE;
    }

    public boolean slab() {
        return this == ROOF || this == CATWALK || this == DEBRIS_LOW;
    }

    public enum Kind {
        NONE,
        STRUCTURE,
        ATTACHED,
        LOOSE,
        DECOR
    }
}

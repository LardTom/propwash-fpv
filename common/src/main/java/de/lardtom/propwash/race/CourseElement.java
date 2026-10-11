package de.lardtom.propwash.race;

import java.util.Locale;

public enum CourseElement {
    GATE(1, 1, 0.0, 0.0, 0.0),
    FLAG(2, 3, 0.25, 5.0, 4.0),
    POLE(2, 1, 0.25, 4.0, 3.0),
    CUBE(4, 2, 0.25, 3.0, 3.0),
    FREEFORM(2, 1, 0.0, 1.0, 1.0),
    GATE_POLE(GatePoles.COLORS, 0, GatePoles.THICKNESS, GateSize.MIN_OPENING, GatePoles.DEFAULT_LENGTH);

    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int EXIT_FRONT = 0;
    public static final int EXIT_RIGHT = 1;
    public static final int EXIT_BACK = 2;
    public static final int EXIT_LEFT = 3;
    public static final int AWAY = 0;
    public static final int TOWARD = 1;
    public static final double MIN_SIZE = GateSize.MIN_OPENING;
    public static final double MAX_SIZE = GateSize.MAX_OPENING;
    public static final double REACH = MAX_SIZE + 1.0;
    public static final double BANNER_WIDTH = 1.0;
    public static final double BANNER_MAX_HEIGHT = 2.0;
    public static final double BANNER_SHARE = 0.55;
    public static final double BANNER_DEPTH = 0.0625;
    public static final double BANNER_TOP_GAP = 0.125;
    public static final double BELOW = 1.0;

    private final int variants;
    private final int stages;
    private final double thickness;
    private final double defaultWidth;
    private final double defaultHeight;

    CourseElement(int variants, int stages, double thickness, double defaultWidth, double defaultHeight) {
        this.variants = variants;
        this.stages = stages;
        this.thickness = thickness;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean gate() {
        return this == GATE;
    }

    public boolean signals() {
        return this == GATE || this == FREEFORM;
    }

    public String itemId() {
        return switch (this) {
            case GATE -> "gate_medium";
            case FLAG -> "race_flag";
            case POLE -> "slalom_pole";
            case CUBE -> "dive_cube";
            case FREEFORM -> "freeform_gate";
            case GATE_POLE -> "gate_pole";
        };
    }

    public int variants() {
        return this.variants;
    }

    public int stages() {
        return this.stages;
    }

    public double thickness() {
        return this.thickness;
    }

    public double defaultWidth() {
        return this.defaultWidth;
    }

    public double defaultHeight() {
        return this.defaultHeight;
    }

    public boolean courseable() {
        return this != GATE_POLE;
    }

    public boolean mountable() {
        return this == GATE || this == GATE_POLE;
    }

    public boolean strictMiss() {
        return this == GATE || this == CUBE || this == FREEFORM;
    }

    public boolean startable() {
        return this == GATE || this == FREEFORM;
    }

    public boolean freeform() {
        return this == FREEFORM;
    }

    public boolean sizedFrame() {
        return this == CUBE;
    }

    public int variant(int value) {
        return Math.floorMod(value, this.variants);
    }

    public int nextVariant(int value) {
        return this.variant(value + 1);
    }

    public int opposite(int value) {
        return switch (this) {
            case GATE -> 0;
            case FLAG, POLE, FREEFORM -> this.variant(value) == LEFT ? RIGHT : LEFT;
            case CUBE -> this.variant(value + 2);
            case GATE_POLE -> this.variant(value);
        };
    }

    public static int slalomSide(int previous, int own) {
        int opposite = POLE.opposite(previous);
        return POLE.variant(own) == POLE.variant(previous) ? opposite : POLE.variant(own);
    }

    public String variantKey(int value) {
        int v = this.variant(value);
        return switch (this) {
            case GATE -> "none";
            case FLAG, POLE -> v == LEFT ? "left" : "right";
            case CUBE -> switch (v) {
                case EXIT_RIGHT -> "right";
                case EXIT_BACK -> "back";
                case EXIT_LEFT -> "left";
                default -> "front";
            };
            case FREEFORM -> v == AWAY ? "away" : "toward";
            case GATE_POLE -> GatePoles.colorKey(v);
        };
    }

    public double outerWidth(double value) {
        if (this == GATE_POLE) {
            return this.thickness;
        }
        return this.sizedFrame() ? GateSize.snapOpening(value) + 2.0 * this.thickness : GateSize.snapOpening(value);
    }

    public double outerHeight(double value) {
        return this == GATE_POLE ? GatePoles.snapLength(value) : this.outerWidth(value);
    }

    public double innerWidth(double outer) {
        if (this == GATE_POLE) {
            return this.defaultWidth;
        }
        return GateSize.snapOpening(this.sizedFrame() ? outer - 2.0 * this.thickness : outer);
    }

    public double innerHeight(double outer) {
        return this == GATE_POLE ? GatePoles.snapLength(outer) : this.innerWidth(outer);
    }

    public double depth(double width) {
        return this == CUBE ? width : this.thickness;
    }

    public static CourseElement byOrdinal(int ordinal) {
        CourseElement[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : GATE;
    }

    public static CourseElement byKey(String key) {
        if (key != null) {
            for (CourseElement element : values()) {
                if (element.key().equals(key)) {
                    return element;
                }
            }
        }
        return GATE;
    }
}

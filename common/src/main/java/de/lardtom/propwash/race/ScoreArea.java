package de.lardtom.propwash.race;

import java.util.Locale;

public record ScoreArea(Role role, int side, double reach, double below, double above, boolean open) {
    public static final double STEP = 0.5;
    public static final double MIN_REACH = 0.5;
    public static final double MAX_REACH = 16.0;
    public static final double DEFAULT_REACH = 4.0;
    public static final double MAX_EXTENT = 32.0;
    public static final double DEFAULT_BELOW = CourseElement.BELOW;
    public static final double DEFAULT_ABOVE = 0.0;
    public static final ScoreArea DEFAULT = new ScoreArea(Role.NONE, CourseElement.LEFT, DEFAULT_REACH, DEFAULT_BELOW, DEFAULT_ABOVE, false);
    private static final int ROLE_BITS = 0x3;
    private static final int SIDE_BIT = 1 << 2;
    private static final int REACH_SHIFT = 3;
    private static final int REACH_MASK = 0x3F;
    private static final int BELOW_SHIFT = 9;
    private static final int ABOVE_SHIFT = 16;
    private static final int EXTENT_MASK = 0x7F;
    private static final int OPEN_BIT = 1 << 23;

    public enum Role {
        NONE,
        PASS,
        TURN;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public static Role byKey(String key) {
            for (Role role : values()) {
                if (role.key().equals(key)) {
                    return role;
                }
            }
            return NONE;
        }

        public static Role byOrdinal(int ordinal) {
            Role[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
        }

        public CourseElement scoringKind() {
            return this == TURN ? CourseElement.FLAG : CourseElement.POLE;
        }
    }

    public ScoreArea {
        role = role == null ? Role.NONE : role;
        side = side == CourseElement.RIGHT ? CourseElement.RIGHT : CourseElement.LEFT;
        reach = snap(reach, MIN_REACH, MAX_REACH, DEFAULT_REACH);
        below = snap(below, 0.0, MAX_EXTENT, DEFAULT_BELOW);
        above = snap(above, 0.0, MAX_EXTENT, DEFAULT_ABOVE);
    }

    public static double snap(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        double snapped = Math.round(value / STEP) * STEP;
        return Math.max(min, Math.min(max, snapped));
    }

    public static boolean scored(CourseElement kind) {
        return kind == CourseElement.FLAG || kind == CourseElement.POLE || kind == CourseElement.GATE_POLE;
    }

    public ScoreArea withRole(Role next) {
        return new ScoreArea(next, this.side, this.reach, this.below, this.above, this.open);
    }

    public ScoreArea withSide(int next) {
        return new ScoreArea(this.role, next, this.reach, this.below, this.above, this.open);
    }

    public ScoreArea withReach(double next) {
        return new ScoreArea(this.role, this.side, next, this.below, this.above, this.open);
    }

    public ScoreArea withBelow(double next) {
        return new ScoreArea(this.role, this.side, this.reach, next, this.above, this.open);
    }

    public ScoreArea withAbove(double next) {
        return new ScoreArea(this.role, this.side, this.reach, this.below, next, this.open);
    }

    public ScoreArea withOpen(boolean next) {
        return new ScoreArea(this.role, this.side, this.reach, this.below, this.above, next);
    }

    public ScoreArea normalized(CourseElement kind) {
        return switch (kind == null ? CourseElement.GATE : kind) {
            case FLAG, POLE -> new ScoreArea(Role.NONE, CourseElement.LEFT, DEFAULT_REACH, this.below, this.above, false);
            case GATE_POLE -> new ScoreArea(this.role, this.side, this.reach, this.below, this.above, false);
            case FREEFORM -> this.open ? DEFAULT.withOpen(true) : DEFAULT;
            case GATE, CUBE -> DEFAULT;
        };
    }

    public ScoreArea forCourse(CourseElement kind) {
        ScoreArea normalized = this.normalized(kind).withOpen(false);
        return kind == CourseElement.GATE_POLE && normalized.role == Role.NONE ? normalized.withRole(Role.PASS) : normalized;
    }

    public boolean isDefault() {
        return this.equals(DEFAULT);
    }

    public boolean scoring() {
        return this.role != Role.NONE;
    }

    public int passSign() {
        return this.side == CourseElement.LEFT ? 1 : -1;
    }

    public int pack() {
        int reachSteps = (int) Math.round(this.reach / STEP) - 1;
        int belowSteps = (int) Math.round(this.below / STEP);
        int aboveSteps = (int) Math.round(this.above / STEP);
        return (this.role.ordinal() & ROLE_BITS)
                | (this.side == CourseElement.RIGHT ? SIDE_BIT : 0)
                | ((reachSteps & REACH_MASK) << REACH_SHIFT)
                | ((belowSteps & EXTENT_MASK) << BELOW_SHIFT)
                | ((aboveSteps & EXTENT_MASK) << ABOVE_SHIFT)
                | (this.open ? OPEN_BIT : 0);
    }

    public static ScoreArea unpack(int packed) {
        Role role = Role.byOrdinal(packed & ROLE_BITS);
        int side = (packed & SIDE_BIT) != 0 ? CourseElement.RIGHT : CourseElement.LEFT;
        double reach = (((packed >> REACH_SHIFT) & REACH_MASK) + 1) * STEP;
        double below = ((packed >> BELOW_SHIFT) & EXTENT_MASK) * STEP;
        double above = ((packed >> ABOVE_SHIFT) & EXTENT_MASK) * STEP;
        return new ScoreArea(role, side, reach, below, above, (packed & OPEN_BIT) != 0);
    }

    public static String sideKey(int side) {
        return side == CourseElement.RIGHT ? "right" : "left";
    }

    public static int sideByKey(String key) {
        return "right".equals(key) ? CourseElement.RIGHT : CourseElement.LEFT;
    }
}

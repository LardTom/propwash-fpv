package de.lardtom.propwash.trick;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum TrickType {
    FLIP(100, true, true),
    DOUBLE_FLIP(250, true, true),
    TRIPLE_FLIP(450, true, true),
    ROLL(100, true, true),
    DOUBLE_ROLL(250, true, true),
    TRIPLE_ROLL(450, true, true),
    POWER_LOOP(250, true, true),
    MATTY_FLIP(350, true, true),
    SPLIT_S(200, true, true),
    DIVE(150, true, true),
    INVERTED_HANG(120, true, true),
    YAW_SPIN(80, false, true),
    DOUBLE_YAW_SPIN(180, false, true),
    ORBIT(250, false, true),
    GAP(200, false, false),
    PROXIMITY(100, false, false),
    JUICY_FLICK(200, true, true),
    RUBIKS_CUBE(450, true, true),
    TRIPPY_SPIN(350, true, true),
    MCTWIST(500, true, true),
    CORKSCREW(200, true, true),
    REWIND(150, true, true),
    KNIFE_EDGE(150, false, true),
    INVERTED_YAW_SPIN(250, true, true),
    SNAP_ROLL(150, true, true),
    IMMELMANN(200, true, true),
    EJECT(60, false, true),
    FUNKY_SPLIT(250, true, true),
    GUILLOTINE(450, true, true),
    SIDE_TRIPPY(400, true, true),
    SHINOBI(600, false, false),
    SBANG(300, false, false),
    INVERTED_FLIGHT(150, true, true),
    TIC_TOC(400, true, true),
    ANTI_MATTY(375, true, true),
    HALF_MATTY(400, true, true),
    POWER_MATTY(500, true, true),
    SPLIT_S_540(300, true, true),
    SPLITBACK(350, true, true),
    FLIP_REWIND(300, true, true),
    SEGMENTED(150, true, true),
    VANNY_ROLL(200, true, true),
    COIN_DROP(200, true, true),
    TORNADO(300, true, true),
    BACKWARDS_DIVE(200, true, true),
    HAMMERHEAD(250, true, true),
    MAVERICK_LOOP(200, false, true),
    POWER_FLIP(500, true, true),
    POWER_ROLL(550, true, true),
    INVERTED_360_POWER_LOOP(650, true, true),
    SIDE_LOOP(300, true, true),
    BARANI(700, true, true),
    IMMELMATT(200, false, false),
    WALL_RIDE(200, false, false),
    REVERSE_KNIFE_EDGE(300, false, true),
    INVERTED_HOVER(200, true, true),
    POWER_LOOP_3D(450, true, true),
    FLIP_3D(200, true, true),
    ROLL_3D(200, true, true);

    public static final double DIVE_BASE_DROP = 10.0;
    public static final double DIVE_POINTS_PER_METER = 5.0;
    public static final double DIVE_MAX_EXTRA = 250.0;
    public static final double HANG_BASE_SECONDS = 0.9;
    public static final double HANG_MAX_SECONDS = 3.0;
    public static final double HANG_POINTS_PER_SECOND = 80.0;
    public static final double GAP_REFERENCE_WIDTH = 2.2;
    public static final double GAP_MAX_FACTOR = 1.6;
    public static final double PROXIMITY_BASE_SECONDS = 0.35;
    public static final double PROXIMITY_POINTS_PER_SECOND = 150.0;
    public static final double PROXIMITY_MAX_EXTRA = 300.0;
    public static final double PROXIMITY_REFERENCE = 0.9;
    public static final double PROXIMITY_MAX_FACTOR = 1.5;
    public static final int MAX_EXTRA_TURNS = 2;
    public static final double CORKSCREW_PER_TURN = 100.0;
    public static final double SPIN_PER_TURN = 150.0;
    public static final double REWIND_BASE_ANGLE = 180.0;
    public static final double REWIND_POINTS_PER_DEGREE = 0.5;
    public static final double REWIND_MAX_EXTRA = 200.0;
    public static final double KNIFE_BASE_SECONDS = 0.5;
    public static final double KNIFE_POINTS_PER_SECOND = 100.0;
    public static final double KNIFE_MAX_EXTRA = 250.0;
    public static final double GUILLOTINE_BASE_DROP = 8.0;
    public static final double GUILLOTINE_POINTS_PER_METER = 10.0;
    public static final double GUILLOTINE_MAX_EXTRA = 300.0;
    public static final double SIDE_TRIPPY_PER_TURN = 150.0;
    public static final double INVERTED_FLIGHT_BASE_SECONDS = 1.0;
    public static final double INVERTED_FLIGHT_POINTS_PER_SECOND = 100.0;
    public static final double INVERTED_FLIGHT_MAX_EXTRA = 300.0;
    public static final int TIC_TOC_BASE_HALVES = 4;
    public static final double TIC_TOC_PER_HALF = 100.0;
    public static final double TIC_TOC_MAX_EXTRA = 200.0;
    public static final double EXTRA_TURN = 100.0;

    public static final List<TrickType> REPERTOIRE = List.of(
            FLIP, DOUBLE_FLIP, TRIPLE_FLIP, ROLL, DOUBLE_ROLL, TRIPLE_ROLL, SNAP_ROLL, CORKSCREW, SEGMENTED, VANNY_ROLL,
            JUICY_FLICK, RUBIKS_CUBE, REWIND, FLIP_REWIND, INVERTED_HANG,
            POWER_LOOP, POWER_FLIP, POWER_ROLL, INVERTED_360_POWER_LOOP, MAVERICK_LOOP, SIDE_LOOP, BARANI, IMMELMANN, IMMELMATT,
            HAMMERHEAD,
            MATTY_FLIP, ANTI_MATTY, HALF_MATTY, POWER_MATTY, SPLIT_S, SPLIT_S_540, SPLITBACK,
            YAW_SPIN, DOUBLE_YAW_SPIN, INVERTED_YAW_SPIN, TRIPPY_SPIN, MCTWIST, COIN_DROP, ORBIT, SIDE_TRIPPY,
            DIVE, GUILLOTINE, TORNADO, BACKWARDS_DIVE,
            KNIFE_EDGE, REVERSE_KNIFE_EDGE, WALL_RIDE, GAP, PROXIMITY,
            EJECT, SBANG, FUNKY_SPLIT, SHINOBI,
            INVERTED_FLIGHT, INVERTED_HOVER, TIC_TOC, POWER_LOOP_3D, FLIP_3D, ROLL_3D);

    private final int basePoints;
    private final boolean recovers;
    private final boolean proximityStyle;
    private final String key;

    TrickType(int basePoints, boolean recovers, boolean proximityStyle) {
        this.basePoints = basePoints;
        this.recovers = recovers;
        this.proximityStyle = proximityStyle;
        this.key = this.name().toLowerCase(Locale.ROOT);
    }

    public int basePoints() {
        return this.basePoints;
    }

    public boolean recovers() {
        return this.recovers;
    }

    public boolean proximityStyle() {
        return this.proximityStyle;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.trick.type." + this.key;
    }

    public String descriptionKey() {
        return this.translationKey() + ".desc";
    }

    public boolean scaled() {
        return switch (this) {
            case DIVE, INVERTED_HANG, GAP, PROXIMITY, CORKSCREW, REWIND, KNIFE_EDGE, INVERTED_YAW_SPIN, TRIPPY_SPIN, GUILLOTINE,
                 SIDE_TRIPPY, INVERTED_FLIGHT, TIC_TOC, COIN_DROP, TORNADO, BACKWARDS_DIVE, WALL_RIDE, REVERSE_KNIFE_EDGE,
                 INVERTED_HOVER, FLIP_3D, ROLL_3D -> true;
            default -> false;
        };
    }

    public boolean line() {
        return this == SBANG || this == SHINOBI || this == IMMELMATT;
    }

    public boolean mode3dOnly() {
        return switch (this) {
            case INVERTED_FLIGHT, TIC_TOC, INVERTED_HOVER, POWER_LOOP_3D, FLIP_3D, ROLL_3D -> true;
            default -> false;
        };
    }

    public TrickFamily family() {
        return switch (this) {
            case FLIP, DOUBLE_FLIP, TRIPLE_FLIP, ROLL, DOUBLE_ROLL, TRIPLE_ROLL, SNAP_ROLL, CORKSCREW, SEGMENTED, VANNY_ROLL ->
                    TrickFamily.FLIPS_ROLLS;
            case JUICY_FLICK, RUBIKS_CUBE, REWIND, FLIP_REWIND, INVERTED_HANG -> TrickFamily.AIR;
            case POWER_LOOP, POWER_FLIP, POWER_ROLL, INVERTED_360_POWER_LOOP, MAVERICK_LOOP, SIDE_LOOP, BARANI, IMMELMANN, IMMELMATT,
                 HAMMERHEAD -> TrickFamily.LOOPS;
            case MATTY_FLIP, ANTI_MATTY, HALF_MATTY, POWER_MATTY, SPLIT_S, SPLIT_S_540, SPLITBACK -> TrickFamily.MATTY_SPLIT;
            case YAW_SPIN, DOUBLE_YAW_SPIN, INVERTED_YAW_SPIN, TRIPPY_SPIN, MCTWIST, COIN_DROP, ORBIT, SIDE_TRIPPY -> TrickFamily.SPINS;
            case DIVE, GUILLOTINE, TORNADO, BACKWARDS_DIVE -> TrickFamily.DIVES;
            case KNIFE_EDGE, REVERSE_KNIFE_EDGE, WALL_RIDE, GAP, PROXIMITY -> TrickFamily.OBSTACLES;
            case EJECT, SBANG, FUNKY_SPLIT, SHINOBI -> TrickFamily.SBANG_DEX;
            case INVERTED_FLIGHT, INVERTED_HOVER, TIC_TOC, POWER_LOOP_3D, FLIP_3D, ROLL_3D -> TrickFamily.THREE_D;
        };
    }

    public TrickType variantOf() {
        return switch (this) {
            case DOUBLE_FLIP, TRIPLE_FLIP, FLIP_3D -> FLIP;
            case DOUBLE_ROLL, TRIPLE_ROLL, ROLL_3D -> ROLL;
            case POWER_FLIP, POWER_ROLL, INVERTED_360_POWER_LOOP, POWER_LOOP_3D, SIDE_LOOP -> POWER_LOOP;
            case SPLIT_S_540, SPLITBACK -> SPLIT_S;
            case ANTI_MATTY, HALF_MATTY, POWER_MATTY -> MATTY_FLIP;
            case FLIP_REWIND -> REWIND;
            case REVERSE_KNIFE_EDGE, WALL_RIDE -> KNIFE_EDGE;
            case BACKWARDS_DIVE -> DIVE;
            case DOUBLE_YAW_SPIN, COIN_DROP -> YAW_SPIN;
            case INVERTED_HOVER -> INVERTED_FLIGHT;
            default -> this;
        };
    }

    public static Set<TrickType> withVariants(TrickType... bases) {
        Set<TrickType> set = EnumSet.noneOf(TrickType.class);
        for (TrickType base : bases) {
            for (TrickType type : values()) {
                if (type.variantOf() == base.variantOf()) {
                    set.add(type);
                }
            }
        }
        return set;
    }

    public String aliasKey() {
        return this.translationKey() + ".aka";
    }

    public double points(double magnitude, double minClearance) {
        double m = Double.isFinite(magnitude) ? magnitude : 0.0;
        return switch (this) {
            case DIVE, BACKWARDS_DIVE -> this.basePoints + Math.clamp((m - DIVE_BASE_DROP) * DIVE_POINTS_PER_METER, 0.0, DIVE_MAX_EXTRA);
            case INVERTED_HANG -> this.basePoints
                    + HANG_POINTS_PER_SECOND * (Math.clamp(m, HANG_BASE_SECONDS, HANG_MAX_SECONDS) - HANG_BASE_SECONDS);
            case GAP -> this.basePoints * (m > 0.0 ? Math.clamp(GAP_REFERENCE_WIDTH / m, 1.0, GAP_MAX_FACTOR) : 1.0);
            case PROXIMITY -> (this.basePoints + Math.clamp((m - PROXIMITY_BASE_SECONDS) * PROXIMITY_POINTS_PER_SECOND, 0.0, PROXIMITY_MAX_EXTRA))
                    * (minClearance > 0.0 && Double.isFinite(minClearance)
                    ? Math.clamp(PROXIMITY_REFERENCE / minClearance, 1.0, PROXIMITY_MAX_FACTOR) : 1.0);
            case CORKSCREW -> this.basePoints + CORKSCREW_PER_TURN * extraTurns(m);
            case COIN_DROP, TORNADO, FLIP_3D, ROLL_3D -> this.basePoints + EXTRA_TURN * extraTurns(m);
            case INVERTED_YAW_SPIN, TRIPPY_SPIN -> this.basePoints + SPIN_PER_TURN * extraTurns(m);
            case REWIND -> this.basePoints + Math.clamp((m - REWIND_BASE_ANGLE) * REWIND_POINTS_PER_DEGREE, 0.0, REWIND_MAX_EXTRA);
            case KNIFE_EDGE, WALL_RIDE, REVERSE_KNIFE_EDGE -> this.basePoints + Math.clamp((m - KNIFE_BASE_SECONDS) * KNIFE_POINTS_PER_SECOND, 0.0, KNIFE_MAX_EXTRA);
            case GUILLOTINE -> this.basePoints
                    + Math.clamp((m - GUILLOTINE_BASE_DROP) * GUILLOTINE_POINTS_PER_METER, 0.0, GUILLOTINE_MAX_EXTRA);
            case SIDE_TRIPPY -> this.basePoints + SIDE_TRIPPY_PER_TURN * extraTurns(m);
            case INVERTED_FLIGHT, INVERTED_HOVER -> this.basePoints
                    + Math.clamp((m - INVERTED_FLIGHT_BASE_SECONDS) * INVERTED_FLIGHT_POINTS_PER_SECOND, 0.0, INVERTED_FLIGHT_MAX_EXTRA);
            case TIC_TOC -> this.basePoints + Math.clamp((Math.floor(m) - TIC_TOC_BASE_HALVES) * TIC_TOC_PER_HALF, 0.0, TIC_TOC_MAX_EXTRA);
            default -> this.basePoints;
        };
    }

    private static int extraTurns(double turns) {
        return Math.clamp((int) Math.floor(turns) - 1, 0, MAX_EXTRA_TURNS);
    }

    public static TrickType flips(int turns) {
        return turns >= 3 ? TRIPLE_FLIP : turns == 2 ? DOUBLE_FLIP : FLIP;
    }

    public static TrickType rolls(int turns) {
        return turns >= 3 ? TRIPLE_ROLL : turns == 2 ? DOUBLE_ROLL : ROLL;
    }
}

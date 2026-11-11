package de.lardtom.propwash.trick.tutorial;

import de.lardtom.propwash.trick.TrickType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrickChoreographies {
    public static final double FAST = 650.0;
    public static final String APPROACH = "approach";
    public static final String EXIT = "exit";
    private static final double HOVER = Double.NaN;
    private static final Map<TrickType, TrickChoreography> ALL = create();

    private TrickChoreographies() {
    }

    public static TrickChoreography of(TrickType type) {
        TrickChoreography choreography = ALL.get(type);
        if (choreography == null) {
            throw new IllegalArgumentException("Keine Choreografie für " + type);
        }
        return choreography;
    }

    private static Map<TrickType, TrickChoreography> create() {
        Map<TrickType, TrickChoreography> all = new EnumMap<>(TrickType.class);
        TrickChoreography flip = turn(TrickType.FLIP, 0.0, FAST, 360.0);
        put(all, flip);
        put(all, turn(TrickType.FLIP, 0.0, FAST, 720.0).as(TrickType.DOUBLE_FLIP));
        put(all, turn(TrickType.FLIP, 0.0, 700.0, 1080.0).as(TrickType.TRIPLE_FLIP));
        put(all, turn(TrickType.ROLL, FAST, 0.0, 360.0));
        put(all, turn(TrickType.ROLL, FAST, 0.0, 720.0).as(TrickType.DOUBLE_ROLL));
        put(all, turn(TrickType.ROLL, 700.0, 0.0, 1080.0).as(TrickType.TRIPLE_ROLL));
        put(all, hover(TrickType.SNAP_ROLL).rotate("rotation", FAST, -FAST, 0.0, 0.3, 360.0).build());
        put(all, cruise(TrickType.CORKSCREW, 30.0, 2.0).rotate("rotation", 300.0, 0.0, 0.0, 0.5, 720.0).build());
        put(all, hover(TrickType.SEGMENTED).rotate("first_half", 0.0, FAST, 0.0, 0.3, 180.0).hold("pause", 0.0, 0.0, 0.0, 0.1, 0.6)
                .rotate("second_half", 0.0, FAST, 0.0, 0.3, 180.0).build());
        put(all, cruise(TrickType.VANNY_ROLL, 35.0, 2.0).rotate("yaw_out", 0.0, 0.0, 600.0, 0.4, 180.0).rotate("rotation", FAST, 0.0, 0.0, 0.35, 360.0)
                .rotate("yaw_back", 0.0, 0.0, 600.0, 0.4, 180.0).build());

        put(all, cruise(TrickType.JUICY_FLICK, 30.0, 1.5).rotate("half_flip", 0.0, FAST, 0.0, 0.6, 180.0).rotate("flick", FAST, 0.0, 0.0, 0.3, 180.0)
                .build());
        put(all, cruise(TrickType.RUBIKS_CUBE, 30.0, 1.5).rotate("first_half", 0.0, FAST, 0.0, 0.4, 180.0).rotate("rotation", FAST, 0.0, 0.0, 0.2, 360.0)
                .rotate("second_half", 0.0, FAST, 0.0, 0.4, 180.0).build());
        put(all, hover(TrickType.REWIND).rotate("half_roll", FAST, 0.0, 0.0, 0.3, 180.0).rotate("reverse", -FAST, 0.0, 0.0, 0.3, 180.0).build());
        put(all, hover(TrickType.FLIP_REWIND).rotate("rotation", 0.0, FAST, 0.0, 0.3, 360.0).rotate("reverse", 0.0, -FAST, 0.0, 0.3, 360.0).build());
        put(all, hover(TrickType.INVERTED_HANG).rotate("half_flip", 0.0, FAST, 0.0, 0.1, 180.0).hold("hang", 0.0, 0.0, 0.0, 0.1, 2.0)
                .rotate("roll_out", FAST, 0.0, 0.0, 0.3, 180.0).shared("roll_out", "roll_out_upright").build());

        TrickChoreography powerLoop = powerLoop(TrickType.POWER_LOOP).hold("over_top", 0.0, -220.0, 0.0, 0.85, 0.85).build();
        put(all, powerLoop);
        put(all, finishLoop(powerLoop(TrickType.POWER_FLIP).rotate("flip", 0.0, -FAST, 0.0, 0.3, 360.0)));
        put(all, finishLoop(powerLoop(TrickType.POWER_ROLL).rotate("roll", FAST, 0.0, 0.0, 0.3, 360.0)));
        put(all, finishLoop(powerLoop(TrickType.INVERTED_360_POWER_LOOP).rotate("spin", 0.0, 0.0, 700.0, 0.1, 360.0)));
        put(all, new Builder(TrickType.MAVERICK_LOOP).fly(APPROACH, 15.0, 0.0, 0.0, 2.0).shared(APPROACH, "approach_slow")
                .add(new TrickMove.Maverick("loop", 4.0, 5.0)).build());
        put(all, new Builder(TrickType.SIDE_LOOP).fly(APPROACH, 0.0, 35.0, 0.0, 2.0).shared(APPROACH, "approach_sideways")
                .hold("pull_up", -220.0, 0.0, 0.0, 0.85, 0.8).hold("over_top", -220.0, 0.0, 0.0, 0.85, 0.85).build());
        put(all, cruise(TrickType.BARANI, 35.0, 2.0).hold("pull_up", 0.0, -300.0, 0.0, 0.85, 0.6).rotate("spin", 0.0, 0.0, 650.0, 0.1, 180.0)
                .rotate("finish", 0.0, 250.0, 0.0, 1.0, 180.0).build());
        put(all, immelmann(TrickType.IMMELMANN).build());
        put(all, immelmann(TrickType.IMMELMATT).hold("half_roll", 0.0, 0.0, 0.0, 0.4, 0.2).rotate("half_flip", 0.0, FAST, 0.0, 0.1, 190.0)
                .rotate("punch", 0.0, 200.0, 0.0, 1.0, 100.0).rotate("finish", 0.0, FAST, 0.0, 0.2, 80.0)
                .parts(TrickType.IMMELMANN, TrickType.MATTY_FLIP).build());
        put(all, new Builder(TrickType.HAMMERHEAD).fly(APPROACH, 40.0, 0.0, 0.0, 2.0).shared(APPROACH, "approach_fast")
                .rotate("pull_up", 0.0, -300.0, 0.0, 1.0, 100.0).flyThrottle("pull_up", -60.0, 0.0, 0.0, 1.0, 0.3)
                .hold("stall", 0.0, 0.0, 0.0, 0.0, 1.6).rotate("turn", 0.0, 0.0, 600.0, 0.0, 180.0).hold("fall", 0.0, 0.0, 0.0, 0.0, 0.8)
                .rotate("pull_out", 0.0, -300.0, 0.0, 0.9, 70.0).flyThrottle("pull_out", 0.0, 0.0, 0.0, 0.8, 0.8).build());

        TrickChoreography matty = cruise(TrickType.MATTY_FLIP, 35.0, 2.0).rotate("half_flip", 0.0, FAST, 0.0, 0.1, 170.0)
                .hold("punch", 0.0, 0.0, 0.0, 1.0, 0.5).rotate("finish", 0.0, FAST, 0.0, 0.2, 180.0).build();
        put(all, matty);
        put(all, matty.backwards().as(TrickType.ANTI_MATTY));
        put(all, cruise(TrickType.HALF_MATTY, 35.0, 2.0).rotate("half_roll", FAST, 0.0, 0.0, 0.2, 180.0).shared("half_roll", "half_roll_inverted")
                .rotate("punch", 0.0, 250.0, 0.0, 1.0, 180.0).build());
        put(all, cruise(TrickType.POWER_MATTY, 35.0, 2.0).rotate("flips", 0.0, FAST, 0.0, 0.1, 540.0).rotate("punch", 0.0, 200.0, 0.0, 1.0, 100.0)
                .rotate("finish", 0.0, FAST, 0.0, 0.2, 60.0).build());
        put(all, pullThrough(cruise(TrickType.SPLIT_S, 35.0, 2.0).rotate("half_roll", FAST, 0.0, 0.0, 0.2, 180.0)
                .shared("half_roll", "half_roll_inverted")));
        put(all, pullThrough(cruise(TrickType.SPLIT_S_540, 35.0, 2.0).rotate("roll", FAST, 0.0, 0.0, 0.2, 540.0)));
        put(all, pullThrough(cruise(TrickType.SPLITBACK, 35.0, 2.0).rotate("half_roll", FAST, 0.0, 0.0, 0.2, 180.0)
                .shared("half_roll", "half_roll_inverted").rotate("flip", 0.0, -FAST, 0.0, 0.3, 360.0)));

        put(all, hover(TrickType.YAW_SPIN).rotate("spin", 0.0, 0.0, 500.0, HOVER, 370.0).build());
        put(all, hover(TrickType.YAW_SPIN).rotate("spin", 0.0, 0.0, 500.0, HOVER, 720.0).build().as(TrickType.DOUBLE_YAW_SPIN));
        put(all, hover(TrickType.INVERTED_YAW_SPIN).hold("entry", 0.0, 0.0, 0.0, 0.9, 0.5).shared("entry", "entry_punch")
                .rotate("half_roll", FAST, 0.0, 0.0, 0.2, 180.0).shared("half_roll", "half_roll_inverted").rotate("spin", 0.0, 0.0, 750.0, 0.15, 360.0)
                .rotate("roll_out", FAST, 0.0, 0.0, 0.3, 180.0).shared("roll_out", "roll_out_upright").build());
        put(all, hover(TrickType.TRIPPY_SPIN).rotate("half_roll", FAST, 0.0, 0.0, 0.2, 180.0).shared("half_roll", "half_roll_inverted")
                .rotate("spin", 0.0, 0.0, 500.0, 0.05, 720.0).rotate("roll_out", FAST, 0.0, 0.0, 0.4, 180.0).shared("roll_out", "roll_out_upright").build());
        put(all, new Builder(TrickType.MCTWIST).fly(APPROACH, 20.0, 0.0, 0.0, 1.5).shared(APPROACH, "approach_slow")
                .rotate("half_flip", 0.0, FAST, 0.0, 0.3, 180.0).rotate("spin", 0.0, 0.0, 700.0, 0.1, 360.0)
                .rotate("second_half", 0.0, FAST, 0.0, 0.3, 180.0).build());
        put(all, new Builder(TrickType.COIN_DROP).flyThrottle(APPROACH, 0.0, 0.0, 0.0, 0.9, 0.8).shared(APPROACH, "approach_climb")
                .flyThrottle("fall", 0.0, 0.0, 0.0, 0.0, 2.5).rotate("spin", 0.0, 0.0, 500.0, 0.0, 720.0).build());
        put(all, hover(TrickType.ORBIT).add(new TrickMove.Orbit("orbit", 6.0, 6.0, 1.15, 1.0)).build());
        put(all, cruise(TrickType.SIDE_TRIPPY, 30.0, 1.5).rotate("bank", FAST, 0.0, 0.0, 0.6, 90.0).rotate("circle", 0.0, 320.0, 0.0, 0.8, 420.0)
                .rotate("roll_out", -FAST, 0.0, 0.0, 0.5, 90.0).build());

        TrickChoreography dive = apex(TrickType.DIVE).rotate("tip_over", 0.0, FAST, 0.0, 0.05, 75.0).shared("tip_over", "tip_over_down")
                .hold("dive", 0.0, 0.0, 0.0, 0.05, 1.8).rotate("pull_out", 0.0, -FAST, 0.0, 0.8, 80.0).flyThrottle("pull_out", 0.0, 0.0, 0.0, 1.0, 0.6)
                .shared("pull_out", "pull_out_back").build();
        put(all, dive);
        put(all, dive.backwards().withShared("tip_over", null).withShared("pull_out", null).as(TrickType.BACKWARDS_DIVE));
        put(all, apex(TrickType.GUILLOTINE).rotate("tip_over", 0.0, FAST, 0.0, 0.05, 75.0).shared("tip_over", "tip_over_down")
                .hold("dive", 0.0, 0.0, 0.0, 0.05, 1.6)
                .rotate("flip", 0.0, FAST, 0.0, 0.05, 170.0).rotate("flip", 0.0, FAST, 0.0, 1.0, 90.0).flyThrottle("pull_out", 0.0, 0.0, 0.0, 1.0, 0.5)
                .build());
        put(all, hover(TrickType.TORNADO).rotate("tip_over", 0.0, FAST, 0.0, 0.3, 80.0).hold("tip_over", 0.0, 0.0, 0.0, 0.3, 0.4)
                .rotate("rolls", FAST, 0.0, 0.0, 0.5, 720.0).rotate("pull_out", 0.0, -FAST, 0.0, 1.0, 80.0)
                .flyThrottle("pull_out", 0.0, 0.0, 0.0, 1.0, 0.6).shared("pull_out", "pull_out_back").build());

        TrickChoreography knife = knifeEdge(TrickType.KNIFE_EDGE, false).build();
        put(all, knife);
        put(all, knifeEdge(TrickType.KNIFE_EDGE, true).build().as(TrickType.REVERSE_KNIFE_EDGE));
        put(all, knife.insert(1, new TrickMove.Obstacle(0.5, -10.0, 1.0, 4.0, 10.0, 40.0)).as(TrickType.WALL_RIDE));
        put(all, new Builder(TrickType.GAP).fly(APPROACH, 30.0, 0.0, 0.0, 1.5).shared(APPROACH, "approach_cruise")
                .add(new TrickMove.Obstacle(0.75, -10.0, 8.0, 3.0, 10.0, 9.0)).add(new TrickMove.Obstacle(-3.0, -10.0, 8.0, -0.75, 10.0, 9.0))
                .fly("pass", 30.0, 0.0, 0.0, 2.0).build());
        put(all, new Builder(TrickType.PROXIMITY).fly(APPROACH, 35.0, 0.0, 0.0, 2.0).shared(APPROACH, "approach_cruise")
                .add(new TrickMove.Obstacle(-3.0, -10.0, 5.0, -0.6, 10.0, 18.0)).fly("pass", 35.0, 0.0, 0.0, 2.0).build());

        put(all, eject(cruise(TrickType.EJECT, 30.0, 1.5).level(APPROACH, 0.5)).build());
        put(all, eject(cruise(TrickType.SBANG, 30.0, 1.5).level(APPROACH, 0.4)).hold("snap_back", 0.0, 0.0, 0.0, 0.3, 0.15)
                .rotate("split_s", FAST, 0.0, 0.0, 0.2, 180.0).rotate("split_s", 0.0, -300.0, 0.0, 0.6, 180.0).hold("split_s", 0.0, 0.0, 0.0, 0.35, 0.3)
                .rotate("juicy_flick", 0.0, FAST, 0.0, 0.6, 180.0).rotate("juicy_flick", FAST, 0.0, 0.0, 0.3, 180.0)
                .parts(TrickType.EJECT, TrickType.SPLIT_S, TrickType.JUICY_FLICK).build());
        put(all, cruise(TrickType.FUNKY_SPLIT, 30.0, 1.5).rotate("half_roll", FAST, 0.0, 0.0, 0.2, 180.0).shared("half_roll", "half_roll_inverted")
                .rotate("spin", 0.0, 0.0, 600.0, 0.15, 180.0)
                .rotate("roll_out", FAST, 0.0, 0.0, 0.3, 180.0).build());
        put(all, cruise(TrickType.SHINOBI, 35.0, 2.0).rotate("split_s", FAST, 0.0, 0.0, 0.2, 180.0).rotate("split_s", 0.0, -300.0, 0.0, 0.6, 180.0)
                .rotate("rubiks_cube", 0.0, FAST, 0.0, 0.4, 180.0).rotate("rubiks_cube", FAST, 0.0, 0.0, 0.2, 360.0)
                .rotate("rubiks_cube", 0.0, FAST, 0.0, 0.4, 180.0).hold("anti_matty", 0.0, -650.0, 0.0, 0.1, 0.22)
                .hold("anti_matty", 0.0, 0.0, 0.0, 0.8, 0.5).hold("anti_matty", 0.0, -650.0, 0.0, 0.1, 0.33)
                .parts(TrickType.SPLIT_S, TrickType.RUBIKS_CUBE, TrickType.ANTI_MATTY).build());

        put(all, new Builder(TrickType.INVERTED_FLIGHT).threeD().fly(APPROACH, 25.0, 0.0, 0.0, 1.5).shared(APPROACH, "approach_3d")
                .rotate("half_flip", 0.0, FAST, 0.0, -0.2, 180.0).shared("half_flip", "half_flip_reverse_thrust")
                .add(new TrickMove.InvertedHold("inverted", 2.0, 0.0)).rotate("roll_out", FAST, 0.0, 0.0, 0.0, 180.0).shared("roll_out", "roll_out_upright")
                .build());
        put(all, new Builder(TrickType.INVERTED_HOVER).threeD().level(APPROACH, 1.0).shared(APPROACH, "approach_3d")
                .rotate("half_flip", 0.0, FAST, 0.0, -0.2, 180.0).shared("half_flip", "half_flip_reverse_thrust")
                .add(new TrickMove.InvertedHover("inverted", 2.0)).rotate("roll_out", FAST, 0.0, 0.0, 0.0, 180.0).shared("roll_out", "roll_out_upright")
                .build());
        put(all, new Builder(TrickType.TIC_TOC).threeD().level(APPROACH, 1.0).shared(APPROACH, "approach_3d")
                .rotate("first_half", 0.0, FAST, 0.0, -0.3, 170.0).rotate("rock", 0.0, -FAST, 0.0, 0.3, 170.0)
                .rotate("rock", 0.0, FAST, 0.0, -0.3, 170.0).rotate("rock", 0.0, -FAST, 0.0, 0.3, 170.0).build());
        put(all, powerLoop.map(TrickChoreographies::reverseThrust).threeD().withShared(APPROACH, "approach_3d").withShared("pull_up", null)
                .as(TrickType.POWER_LOOP_3D));
        put(all, flip3d(TrickType.FLIP_3D, 0.0, FAST));
        put(all, flip3d(TrickType.ROLL_3D, FAST, 0.0));
        return all;
    }

    private static void put(Map<TrickType, TrickChoreography> all, TrickChoreography choreography) {
        if (all.put(choreography.trick(), choreography) != null) {
            throw new IllegalStateException("Doppelte Choreografie für " + choreography.trick());
        }
    }

    private static TrickMove reverseThrust(TrickMove move) {
        if (move instanceof TrickMove.Hold hold) {
            return new TrickMove.Hold(hold.stage(), hold.roll(), -hold.pitch(), hold.yaw(), -hold.throttle(), hold.seconds());
        }
        return move;
    }

    private static TrickChoreography turn(TrickType trick, double roll, double pitch, double degrees) {
        return hover(trick).flyThrottle("entry", 0.0, 0.0, 0.0, 0.75, 0.3).shared("entry", "entry_punch").rotate("rotation", roll, pitch, 0.0, 0.3, degrees)
                .build();
    }

    private static TrickChoreography flip3d(TrickType trick, double roll, double pitch) {
        return new Builder(trick).threeD().level(APPROACH, 1.0).shared(APPROACH, "approach_3d").rotate("entry", roll, pitch, 0.0, 0.3, 90.0)
                .rotate("reverse_thrust", roll, pitch, 0.0, -0.4, 180.0).shared("reverse_thrust", "reverse_thrust_3d")
                .rotate("finish", roll, pitch, 0.0, 0.3, 90.0).shared("finish", "finish_3d").build();
    }

    private static Builder hover(TrickType trick) {
        return new Builder(trick).level(APPROACH, 1.0).shared(APPROACH, "approach_hover");
    }

    private static Builder cruise(TrickType trick, double tilt, double seconds) {
        return new Builder(trick).fly(APPROACH, tilt, 0.0, 0.0, seconds).shared(APPROACH, "approach_cruise");
    }

    private static Builder apex(TrickType trick) {
        return new Builder(trick).flyThrottle(APPROACH, 0.0, 0.0, 0.0, 0.9, 0.8).flyThrottle(APPROACH, 0.0, 0.0, 0.0, 0.0, 1.8)
                .shared(APPROACH, "approach_climb");
    }

    private static Builder powerLoop(TrickType trick) {
        return cruise(trick, 35.0, 2.0).hold("pull_up", 0.0, -220.0, 0.0, 0.85, 0.8).shared("pull_up", "pull_up_loop");
    }

    private static TrickChoreography finishLoop(Builder b) {
        return b.hold("finish", 0.0, -220.0, 0.0, 0.85, 0.85).shared("finish", "finish_loop").build();
    }

    private static TrickChoreography pullThrough(Builder b) {
        return b.rotate("pull_through", 0.0, -300.0, 0.0, 0.6, 180.0).shared("pull_through", "pull_through").build();
    }

    private static Builder immelmann(TrickType trick) {
        return cruise(trick, 35.0, 2.0).rotate("half_loop", 0.0, -350.0, 0.0, 0.75, 180.0).rotate("half_roll", FAST, 0.0, 0.0, 0.3, 180.0);
    }

    private static Builder eject(Builder b) {
        return b.rotate("punch", 0.0, FAST, 0.0, 1.0, 70.0).hold("punch", 0.0, 0.0, 0.0, 1.0, 0.25).rotate("snap_back", 0.0, -FAST, 0.0, 0.3, 65.0)
                .shared("punch", "eject_punch").shared("snap_back", "eject_snap");
    }

    private static Builder knifeEdge(TrickType trick, boolean reverse) {
        Builder b = new Builder(trick).fly(APPROACH, 40.0, 0.0, 0.0, 2.5).level(APPROACH, 0.25).shared(APPROACH, "approach_fast");
        if (reverse) {
            b.rotate("turn_around", 0.0, 0.0, 700.0, 0.3, 180.0);
        }
        return b.rotate("bank", FAST, 0.0, 0.0, 0.0, 80.0).hold("knife_edge", 0.0, 0.0, 0.0, 0.0, 0.8).rotate("roll_back", -FAST, 0.0, 0.0, 0.4, 80.0)
                .shared("bank", "knife_bank").shared("roll_back", "knife_back");
    }

    static final class Builder {
        private final TrickType trick;
        private final List<TrickMove> moves = new ArrayList<>();
        private final Map<String, String> shared = new LinkedHashMap<>();
        private List<TrickType> parts = List.of();
        private boolean mode3d;

        Builder(TrickType trick) {
            this.trick = trick;
        }

        Builder add(TrickMove move) {
            this.moves.add(move);
            return this;
        }

        Builder threeD() {
            this.mode3d = true;
            return this;
        }

        Builder shared(String stage, String text) {
            this.shared.put(stage, text);
            return this;
        }

        Builder parts(TrickType... types) {
            this.parts = List.of(types);
            return this;
        }

        Builder level(String stage, double seconds) {
            return this.add(new TrickMove.Level(stage, seconds));
        }

        Builder fly(String stage, double tilt, double bank, double turn, double seconds) {
            return this.add(new TrickMove.Fly(stage, tilt, bank, turn, Double.NaN, seconds));
        }

        Builder flyThrottle(String stage, double tilt, double bank, double turn, double throttle, double seconds) {
            return this.add(new TrickMove.Fly(stage, tilt, bank, turn, throttle, seconds));
        }

        Builder rotate(String stage, double roll, double pitch, double yaw, double throttle, double degrees) {
            return this.add(new TrickMove.Rotate(stage, roll, pitch, yaw, throttle, degrees));
        }

        Builder hold(String stage, double roll, double pitch, double yaw, double throttle, double seconds) {
            return this.add(new TrickMove.Hold(stage, roll, pitch, yaw, throttle, seconds));
        }

        TrickChoreography build() {
            List<TrickMove> all = new ArrayList<>(this.moves);
            if (all.isEmpty() || !EXIT.equals(all.getLast().stage())) {
                all.add(new TrickMove.Level(EXIT, 1.5));
            }
            Map<String, String> texts = new LinkedHashMap<>(this.shared);
            texts.putIfAbsent(EXIT, "exit_level");
            return new TrickChoreography(this.trick, all, this.mode3d, this.parts, texts, null);
        }
    }
}

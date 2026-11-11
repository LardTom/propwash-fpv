package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.race.FreeformRegion;

import java.util.Arrays;
import java.util.List;

import static de.lardtom.propwash.guide.scenes.SceneKit.COURSE_TOOL;
import static de.lardtom.propwash.guide.scenes.SceneKit.FREEFORM_GATE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_LARGE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_MEDIUM;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_SMALL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE_BRICKS;

public final class GateMenuScene {
    public static final String ID = "gate_menu";
    public static final String PILOT = "pilot";
    public static final String GATE = "gate";
    public static final String WIDE = "wide";
    public static final String DRONE = "drone";
    public static final String FREEFORM = "freeform";
    public static final ScenePos PILOT_POS = new ScenePos(1.5F, 1.0F, 3.6F);
    public static final ScenePos GATE_POS = new ScenePos(-0.5F, 1.0F, -0.5F);
    public static final float YAW = 180.0F;
    public static final float TURNED_YAW = 150.0F;
    public static final float TILT = 20.0F;
    public static final float WIDE_OPENING = 5.0F;
    public static final float WIDE_HEIGHT = 2.5F;
    public static final int LAMP_X = -1;
    public static final int LAMP_Y = 1;
    public static final int LAMP_Z = -1;
    public static final ScenePos LIFTED_POS = GATE_POS.add(0.0F, 1.0F, 0.0F);
    public static final BlockSpec LAMP = BlockSpec.of("minecraft:redstone_lamp").with("lit", "false");
    public static final BlockSpec LAMP_LIT = BlockSpec.of("minecraft:redstone_lamp").with("lit", "true");
    public static final DroneBuild BUILD = DroneBuild.RACER;
    public static final float DRONE_SCALE = 3.4F;
    public static final float FLY_AT = 46.0F;
    public static final float FLY_LENGTH = 40.0F;
    public static final ScenePos FLY_START = new ScenePos(-0.5F, 3.2F, 2.2F);
    public static final List<ScenePos> FLY_PATH = List.of(
            new ScenePos(-0.5F, 3.5F, 0.9F),
            new ScenePos(-0.5F, 3.6F, -0.5F),
            new ScenePos(-0.5F, 3.5F, -1.9F),
            new ScenePos(-0.5F, 3.3F, -3.1F));
    public static final float PASS_AT = FLY_AT + FLY_LENGTH * 0.5F;
    public static final float PULSE = 40.0F;
    public static final int WALL_Z = -3;
    public static final int WALL_X0 = -3;
    public static final int WALL_X1 = 2;
    public static final int WALL_TOP = 4;
    public static final int OPENING_X = -1;
    public static final int OPENING_Y = 2;
    public static final int OPENING_SIZE = 2;
    public static final FreeformRegion OPENING = opening();
    public static final ScenePos OPENING_POS = new ScenePos(OPENING_X + OPENING_SIZE * 0.5F, OPENING_Y + OPENING_SIZE * 0.5F, WALL_Z + 0.5F);
    public static final float FREEFORM_PLACED = 40.0F;
    public static final float NUDGE = 0.25F;
    public static final float FINE_NUDGE = 1.0F / 16.0F;
    public static final int NUDGES = 3;
    public static final float NUDGE_AT = 22.0F;
    public static final float NUDGE_EVERY = 12.0F;
    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 2.6F, -0.5F);
    private static final ScenePos WALL_VIEW = new ScenePos(0.3F, 2.8F, -1.8F);
    private static final ScenePos LAMP_VIEW = new ScenePos(-0.3F, 2.6F, 0.2F);

    private GateMenuScene() {
    }

    private static FreeformRegion opening() {
        boolean[] cells = new boolean[OPENING_SIZE * OPENING_SIZE];
        Arrays.fill(cells, true);
        FreeformRegion region = FreeformRegion.of(FreeformRegion.AXIS_Z, OPENING_SIZE, OPENING_SIZE, 0, 0, cells);
        if (region == null) {
            throw new IllegalStateException("Öffnung der Flugschul-Szene ungültig");
        }
        return region;
    }

    public static boolean inOpening(int x, int y) {
        return x >= OPENING_X && x < OPENING_X + OPENING_SIZE && y >= OPENING_Y && y < OPENING_Y + OPENING_SIZE;
    }

    private static void wall(SceneBuilder.StepBuilder s, float start) {
        for (int y = 1; y <= WALL_TOP; y++) {
            for (int x = WALL_X0; x <= WALL_X1; x++) {
                if (!inOpening(x, y)) {
                    s.at(start + (y - 1) * 3.0F + (x - WALL_X0) * 0.4F).drop(x, y, WALL_Z, STONE_BRICKS, 6.0F);
                }
            }
        }
        s.at(start);
    }

    public static ScenePos gateTop() {
        return GATE_POS.add(0.0F, 4.6F, 0.0F);
    }

    public static ScenePos wideTop() {
        return GATE_POS.add(0.0F, WIDE_HEIGHT + 2.0F, 0.0F);
    }

    public static ScenePos liftedTop() {
        return LIFTED_POS.add(0.0F, WIDE_HEIGHT + 2.0F, 0.0F);
    }

    public static ScenePos lampFront() {
        return new ScenePos(LAMP_X + 0.5F, LAMP_Y + 0.5F, LAMP_Z + 1.05F);
    }

    public static Scene create() {
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.2F, 0.0F);
        ScenePos wallTop = new ScenePos(OPENING_POS.x(), WALL_TOP + 1.4F, WALL_Z + 0.6F);
        return SceneBuilder.scene(ID)
                .icon(GATE_MEDIUM)
                .items(GATE_SMALL, GATE_LARGE, COURSE_TOOL, FREEFORM_GATE)
                .view(5.3F)
                .camera(OVERVIEW, 26.0F, 34.0F, 1.0F)
                .step("open", 160.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -5, -5, 5, 5, GRASS, GRASS, 0.6F, 10.0F);
                    s.at(14.0F).spawn(PILOT, new ActorSpec.Pilot("", GATE_MEDIUM), PILOT_POS, SceneKit.pilotYaw(PILOT_POS, GATE_POS), Cue.Appear.POP,
                            10.0F);
                    s.at(22.0F).keysAt("place", overHead, 20.0F, KeyToken.use());
                    s.at(28.0F).spawn(GATE, new ActorSpec.Gate("medium"), GATE_POS, YAW, Cue.Appear.POP, 10.0F);
                    s.at(52.0F).crouch(PILOT, true);
                    s.at(54.0F).keysAt("open", overHead, 80.0F, KeyToken.sneak(), KeyToken.use());
                    s.at(60.0F).callout("open", Anchor.at(gateTop()), Cue.Side.ABOVE, 0.0F);
                    s.at(130.0F).crouch(PILOT, false);
                })
                .step("adjust", 200.0F, s -> {
                    s.at(4.0F).turn(GATE, TURNED_YAW, 0.0F, 0.0F, 16.0F);
                    s.at(8.0F).callout("turn", Anchor.at(gateTop()), Cue.Side.LEFT, 62.0F);
                    s.at(30.0F).turn(GATE, TURNED_YAW, TILT, 0.0F, 16.0F);
                    s.at(56.0F).turn(GATE, YAW, 0.0F, 0.0F, 14.0F);
                    s.at(78.0F).despawn(GATE, 6.0F);
                    s.at(80.0F).spawn(WIDE, new ActorSpec.Gate("medium", ActorSpec.Gate.STANDING, false, WIDE_OPENING, WIDE_HEIGHT), GATE_POS, YAW,
                            Cue.Appear.POP, 10.0F);
                    s.at(86.0F).callout("size", Anchor.at(wideTop()), Cue.Side.ABOVE, 0.0F);
                    s.at(108.0F).keysAt("mouse", overHead, 50.0F, KeyToken.mapping(SceneKit.KEY_GATE_EDIT_MOUSE));
                    s.at(150.0F).callout("apply", overHead, Cue.Side.RIGHT, 0.0F);
                })
                .step("redstone", 210.0F, s -> {
                    s.at(0.0F).camera(LAMP_VIEW, -34.0F, 20.0F, 1.2F, 24.0F);
                    s.at(4.0F).pop(LAMP_X, LAMP_Y, LAMP_Z, LAMP, 8.0F);
                    s.at(4.0F).move(WIDE, 10.0F, Ease.OUT, LIFTED_POS);
                    s.at(16.0F).highlightBlock(LAMP_X, LAMP_Y, LAMP_Z, FLY_AT - 18.0F);
                    s.at(16.0F).callout("page", Anchor.at(liftedTop()), Cue.Side.ABOVE, FLY_AT - 18.0F);
                    s.at(20.0F).equip(PILOT, Cue.Slot.HAND, REMOTE);
                    s.at(24.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), FLY_START, YAW, Cue.Appear.POP, 8.0F);
                    s.at(FLY_AT - 6.0F).arm(DRONE, true);
                    s.at(FLY_AT - 6.0F).throttle(DRONE, 0.6F, 4.0F);
                    SceneKit.fly(s, DRONE, FLY_START, FLY_PATH, Ease.IN_OUT, FLY_AT, FLY_LENGTH, FLY_PATH.size() * 3, 30.0F);
                    s.at(PASS_AT).block(LAMP_X, LAMP_Y, LAMP_Z, LAMP_LIT);
                    s.at(PASS_AT).light(WIDE, true);
                    s.at(PASS_AT + 2.0F).callout("lamp", Anchor.at(lampFront()), Cue.Side.RIGHT, 0.0F);
                    s.at(PASS_AT + PULSE).block(LAMP_X, LAMP_Y, LAMP_Z, LAMP);
                    s.at(PASS_AT + PULSE).light(WIDE, false);
                    s.at(PASS_AT + PULSE + 8.0F).callout("pulse", Anchor.at(liftedTop()), Cue.Side.ABOVE, 0.0F);
                })
                .step("position", 170.0F, s -> {
                    s.at(0.0F).despawn(DRONE, 8.0F);
                    s.at(0.0F).camera(OVERVIEW, 26.0F, 34.0F, 1.0F, 24.0F);
                    s.at(8.0F).callout("position", Anchor.at(liftedTop()), Cue.Side.ABOVE, 62.0F);
                    s.at(16.0F).callout("wheel", overHead, Cue.Side.RIGHT, 50.0F);
                    for (int i = 1; i <= NUDGES; i++) {
                        s.at(NUDGE_AT + (i - 1) * NUDGE_EVERY).move(WIDE, 4.0F, Ease.OUT, LIFTED_POS.add(i * NUDGE, 0.0F, 0.0F));
                    }
                    s.at(76.0F).callout("arrows", overHead, Cue.Side.RIGHT, 0.0F);
                    s.at(84.0F).move(WIDE, 4.0F, Ease.OUT, LIFTED_POS.add(NUDGES * NUDGE, 0.0F, NUDGE));
                    s.at(96.0F).move(WIDE, 4.0F, Ease.OUT, LIFTED_POS.add(NUDGES * NUDGE, 0.0F, 2.0F * NUDGE));
                    s.at(116.0F).callout("fine", Anchor.at(liftedTop()), Cue.Side.ABOVE, 0.0F);
                    s.at(122.0F).move(WIDE, 4.0F, Ease.OUT, LIFTED_POS.add(NUDGES * NUDGE + FINE_NUDGE, 0.0F, 2.0F * NUDGE));
                })
                .step("freeform", 200.0F, s -> {
                    s.at(0.0F).despawn(WIDE, 8.0F);
                    s.at(0.0F).camera(WALL_VIEW, 24.0F, 18.0F, 1.05F, 24.0F);
                    wall(s, 4.0F);
                    s.at(24.0F).equip(PILOT, Cue.Slot.HAND, FREEFORM_GATE);
                    s.at(24.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, OPENING_POS), 0.0F, 0.0F, 10.0F);
                    s.at(30.0F).keysAt("place_freeform", overHead, 26.0F, KeyToken.use());
                    s.at(FREEFORM_PLACED).spawn(FREEFORM, ActorSpec.Gate.freeform(OPENING.encode(), 0), OPENING_POS, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(70.0F).crouch(PILOT, true);
                    s.at(72.0F).keysAt("freeform", overHead, 70.0F, KeyToken.sneak(), KeyToken.use());
                    s.at(78.0F).callout("freeform", Anchor.at(wallTop), Cue.Side.ABOVE, 0.0F);
                    s.at(150.0F).crouch(PILOT, false);
                })
                .build();
    }
}

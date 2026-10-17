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

import java.util.List;

import static de.lardtom.propwash.guide.scenes.SceneKit.COURSE_TOOL;
import static de.lardtom.propwash.guide.scenes.SceneKit.FREEFORM_GATE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_LARGE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_MEDIUM;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_SMALL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.MOSS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE;

public final class GateFreeScene {
    public static final String ID = "gate_free";
    public static final String PILOT = "pilot";
    public static final String GATE = "gate";
    public static final String WIDE = "wide";
    public static final String TALL = "tall";
    public static final ScenePos PILOT_POS = new ScenePos(0.5F, 1.0F, 4.4F);
    public static final ScenePos GATE_POS = new ScenePos(-1.5F, 1.0F, -0.5F);
    public static final ScenePos WIDE_POS = new ScenePos(2.5F, 1.0F, -2.5F);
    public static final ScenePos TALL_POS = new ScenePos(-4.5F, 1.0F, -3.0F);
    public static final float YAW = 180.0F;
    public static final float DIAGONAL_YAW = 135.0F;
    public static final float PITCH = 25.0F;
    public static final float ROLL = 45.0F;
    public static final float WIDE_OPENING = 6.0F;
    public static final float WIDE_HEIGHT = 1.5F;
    public static final float TALL_OPENING = 1.5F;
    public static final float TALL_HEIGHT = 5.0F;
    public static final String CAVE_GATE = "cave_gate";
    public static final String DRONE = "drone";
    public static final DroneBuild BUILD = DroneBuild.RACER;
    public static final float DRONE_SCALE = 3.4F;
    public static final int CAVE_Z = -3;
    public static final int CAVE_DEPTH = 2;
    public static final int CAVE_X0 = -4;
    public static final int CAVE_X1 = 5;
    public static final int CAVE_TOP = 5;
    public static final int CAVE_MIN_X = -1;
    public static final int CAVE_MIN_Y = 1;
    public static final int[][] CAVE_ROWS = {{-1, 1}, {-1, 2}, {-1, 2}, {0, 1}};
    public static final FreeformRegion CAVE_REGION = caveRegion();
    public static final ScenePos CAVE_POS = new ScenePos(CAVE_MIN_X + CAVE_REGION.width() * 0.5F, CAVE_MIN_Y + CAVE_REGION.height() * 0.5F,
            CAVE_Z + 0.5F);
    public static final int CAVE_VARIANT = 1;
    public static final float FREEFORM_FLY = 156.0F;
    public static final float FREEFORM_FLY_LENGTH = 36.0F;
    public static final ScenePos FLY_START = new ScenePos(0.5F, 1.6F, 2.2F);
    public static final List<ScenePos> FLY_PATH = List.of(
            new ScenePos(0.5F, 2.3F, 0.4F),
            new ScenePos(0.5F, 2.5F, -1.6F),
            new ScenePos(0.5F, 2.5F, -3.6F),
            new ScenePos(0.5F, 2.4F, -4.6F));
    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 2.6F, -0.5F);
    private static final ScenePos CAVE_VIEW = new ScenePos(0.6F, 2.8F, -2.0F);

    private GateFreeScene() {
    }

    private static FreeformRegion caveRegion() {
        int width = 4;
        int height = CAVE_ROWS.length;
        boolean[] cells = new boolean[width * height];
        for (int b = 0; b < height; b++) {
            for (int x = CAVE_ROWS[b][0]; x <= CAVE_ROWS[b][1]; x++) {
                cells[b * width + (x - CAVE_MIN_X)] = true;
            }
        }
        FreeformRegion region = FreeformRegion.of(FreeformRegion.AXIS_Z, width, height, 0, 0, cells);
        if (region == null) {
            throw new IllegalStateException("Höhlenöffnung der Flugschule ungültig");
        }
        return region;
    }

    public static boolean opening(int x, int y) {
        int b = y - CAVE_MIN_Y;
        return b >= 0 && b < CAVE_ROWS.length && x >= CAVE_ROWS[b][0] && x <= CAVE_ROWS[b][1];
    }

    private static void caveWall(SceneBuilder.StepBuilder s, float start) {
        for (int depth = 0; depth < CAVE_DEPTH; depth++) {
            for (int y = 1; y <= CAVE_TOP; y++) {
                for (int x = CAVE_X0; x <= CAVE_X1; x++) {
                    if (opening(x, y)) {
                        continue;
                    }
                    BlockSpec block = (x * 7 + y * 3 + depth) % 5 == 0 ? MOSS : STONE;
                    s.at(start + depth * 4.0F + (y - 1) * 2.5F + (x - CAVE_X0) * 0.3F).drop(x, y, CAVE_Z - depth, block, 6.0F);
                }
            }
        }
        s.at(start);
    }

    public static Scene create() {
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.2F, 0.0F);
        ScenePos top = GATE_POS.add(0.0F, 4.6F, 0.0F);
        return SceneBuilder.scene(ID)
                .icon(GATE_MEDIUM)
                .items(GATE_SMALL, GATE_LARGE, COURSE_TOOL, FREEFORM_GATE)
                .view(5.3F)
                .camera(OVERVIEW, 26.0F, 34.0F, 1.0F)
                .step("edit", 170.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -6, -5, 6, 5, GRASS, GRASS, 0.6F, 10.0F);
                    s.at(16.0F).spawn(PILOT, new ActorSpec.Pilot("", GATE_MEDIUM), PILOT_POS, SceneKit.pilotYaw(PILOT_POS, GATE_POS), Cue.Appear.POP,
                            10.0F);
                    s.at(24.0F).spawn(GATE, new ActorSpec.Gate("medium"), GATE_POS, YAW, Cue.Appear.POP, 10.0F);
                    s.at(40.0F).keysAt("edit", overHead, 60.0F, KeyToken.sneak(), KeyToken.use());
                    s.at(106.0F).keysAt("mouse", overHead, 58.0F, KeyToken.mapping(SceneKit.KEY_GATE_EDIT_MOUSE));
                    s.at(46.0F).callout("edit", Anchor.at(top), Cue.Side.ABOVE, 0.0F);
                })
                .step("angles", 150.0F, s -> {
                    s.at(4.0F).turn(GATE, DIAGONAL_YAW, 0.0F, 0.0F, 18.0F);
                    s.at(8.0F).callout("diagonal", Anchor.at(top), Cue.Side.LEFT, 36.0F);
                    s.at(50.0F).turn(GATE, DIAGONAL_YAW, PITCH, 0.0F, 18.0F);
                    s.at(54.0F).callout("tilted", Anchor.at(top), Cue.Side.LEFT, 36.0F);
                    s.at(96.0F).turn(GATE, DIAGONAL_YAW, 0.0F, ROLL, 18.0F);
                    s.at(100.0F).callout("diamond", Anchor.at(GATE_POS.add(0.0F, 6.2F, 0.0F)), Cue.Side.LEFT, 0.0F);
                })
                .step("size", 120.0F, s -> {
                    s.at(4.0F).spawn(WIDE, new ActorSpec.Gate("medium", ActorSpec.Gate.STANDING, false, WIDE_OPENING, WIDE_HEIGHT), WIDE_POS, YAW,
                            Cue.Appear.POP, 10.0F);
                    s.at(26.0F).spawn(TALL, new ActorSpec.Gate("small", ActorSpec.Gate.STANDING, false, TALL_OPENING, TALL_HEIGHT), TALL_POS, YAW,
                            Cue.Appear.POP, 10.0F);
                    s.at(12.0F).callout("wide", Anchor.at(WIDE_POS.add(0.0F, WIDE_HEIGHT + 1.2F, 0.0F)), Cue.Side.RIGHT, 0.0F);
                    s.at(34.0F).callout("tall", Anchor.at(TALL_POS.add(0.0F, TALL_HEIGHT + 1.2F, 0.0F)), Cue.Side.LEFT, 0.0F);
                })
                .step("step", 120.0F, s -> {
                    s.at(4.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, WIDE_POS), 0.0F, 0.0F, 10.0F);
                    s.at(10.0F).keys("step", 80.0F, KeyToken.mapping(SceneKit.KEY_GATE_ROTATE));
                    s.at(18.0F).turn(WIDE, YAW + 45.0F, 0.0F, 0.0F, 10.0F);
                    s.at(20.0F).callout("step", Anchor.at(WIDE_POS.add(0.0F, WIDE_HEIGHT + 1.2F, 0.0F)), Cue.Side.RIGHT, 0.0F);
                })
                .step("freeform", 210.0F, s -> {
                    ScenePos caveTop = new ScenePos(0.5F, CAVE_TOP + 0.6F, CAVE_Z + 0.6F);
                    s.at(0.0F).despawn(GATE, 8.0F);
                    s.at(0.0F).despawn(WIDE, 8.0F);
                    s.at(0.0F).despawn(TALL, 8.0F);
                    s.at(0.0F).camera(CAVE_VIEW, 24.0F, 18.0F, 1.05F, 24.0F);
                    caveWall(s, 4.0F);
                    s.at(30.0F).equip(PILOT, Cue.Slot.HAND, FREEFORM_GATE);
                    s.at(30.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, CAVE_POS), 0.0F, 0.0F, 10.0F);
                    s.at(38.0F).keysAt("freeform", overHead, 40.0F, KeyToken.use());
                    s.at(48.0F).spawn(CAVE_GATE, ActorSpec.Gate.freeform(CAVE_REGION.encode(), CAVE_VARIANT), CAVE_POS, 0.0F, Cue.Appear.POP,
                            10.0F);
                    s.at(54.0F).callout("freeform_edge", Anchor.at(caveTop), Cue.Side.ABOVE, 56.0F);
                    s.at(112.0F).keys("freeform_flip", 34.0F, KeyToken.mapping(SceneKit.KEY_GATE_ROTATE));
                    s.at(114.0F).callout("freeform_arrows", Anchor.at(new ScenePos(0.5F, 2.5F, CAVE_Z + 1.6F)), Cue.Side.RIGHT, 32.0F);
                    s.at(146.0F).equip(PILOT, Cue.Slot.HAND, REMOTE);
                    s.at(146.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), FLY_START, 180.0F, Cue.Appear.POP, 8.0F);
                    s.at(152.0F).arm(DRONE, true);
                    s.at(152.0F).throttle(DRONE, 0.6F, 4.0F);
                    s.at(152.0F).light(CAVE_GATE, true);
                    SceneKit.fly(s, DRONE, FLY_START, FLY_PATH, Ease.IN_OUT, FREEFORM_FLY, FREEFORM_FLY_LENGTH, FLY_PATH.size() * 3, 30.0F);
                    s.at(FREEFORM_FLY + 2.0F).callout("freeform_size", Anchor.at(caveTop), Cue.Side.ABOVE, 0.0F);
                })
                .build();
    }
}

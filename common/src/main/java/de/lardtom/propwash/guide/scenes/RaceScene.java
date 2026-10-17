package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import java.util.List;

import static de.lardtom.propwash.guide.scenes.SceneKit.COURSE_TOOL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_LARGE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_MEDIUM;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_SMALL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE_BRICKS;

public final class RaceScene {
    public static final String ID = "race";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final String[] GATES = {"gate1", "gate2", "gate3"};
    public static final DroneBuild BUILD = DroneBuild.RACER;
    public static final float GATE_SPACING = 4.0F;
    public static final float COURSE_Z = -0.5F;
    public static final ScenePos[] GATE_POS = {
            new ScenePos(0.5F - GATE_SPACING, 1.0F, COURSE_Z),
            new ScenePos(0.5F, 1.0F, COURSE_Z),
            new ScenePos(0.5F + GATE_SPACING, 1.0F, COURSE_Z)};
    public static final float[] GATE_YAW = {270.0F, 270.0F, 270.0F};
    public static final String[] GATE_SIZE = {"small", "medium", "small"};
    public static final String[] GATE_ITEM = {GATE_SMALL, GATE_MEDIUM, GATE_SMALL};
    public static final float[] GATE_HEIGHT = {3.0F, 4.0F, 3.0F};
    public static final float[] GATE_WIDTH = {3.0F, 4.0F, 3.0F};
    public static final ScenePos PILOT_POS = new ScenePos(0.5F, 1.0F, 4.4F);
    public static final ScenePos START = new ScenePos(-4.4F, 1.0F, 3.0F);
    public static final ScenePos LAUNCH = new ScenePos(-4.4F, 2.4F, 2.4F);
    public static final List<ScenePos> LAP = List.of(
            new ScenePos(-5.2F, 2.5F, 0.8F),
            new ScenePos(-3.5F, 2.5F, COURSE_Z),
            new ScenePos(-1.5F, 2.8F, COURSE_Z - 0.05F),
            new ScenePos(0.5F, 3.0F, COURSE_Z),
            new ScenePos(2.5F, 2.8F, COURSE_Z + 0.05F),
            new ScenePos(4.5F, 2.5F, COURSE_Z),
            new ScenePos(5.7F, 2.5F, 1.0F),
            new ScenePos(4.6F, 2.4F, 2.8F),
            new ScenePos(2.0F, 2.4F, 3.3F),
            new ScenePos(-1.5F, 2.4F, 3.3F),
            new ScenePos(-4.3F, 2.4F, 2.6F),
            new ScenePos(-5.2F, 2.5F, 0.8F),
            new ScenePos(-3.5F, 2.6F, COURSE_Z),
            new ScenePos(-2.0F, 2.6F, COURSE_Z));
    public static final int[] PASS_INDEX = {2, 4, 6, 13};
    public static final float LAP_START = 16.0F;
    public static final float SEGMENT = 14.0F;

    public static final float DRONE_SCALE = 3.4F;
    public static final String[] LADDER = {"ladder2", "ladder3"};
    public static final String DOUBLE = "double2";
    public static final float SMALL_HEIGHT = 3.0F;
    public static final float SHARED_BAR = 0.5F;
    public static final ScenePos[] LADDER_POS = {
            GATE_POS[1].add(0.0F, GATE_HEIGHT[1] - SHARED_BAR, 0.0F),
            GATE_POS[1].add(0.0F, GATE_HEIGHT[1] - SHARED_BAR + SMALL_HEIGHT - SHARED_BAR, 0.0F)};
    public static final float[] LADDER_YAW = {90.0F, 270.0F};
    public static final ScenePos DOUBLE_POS = GATE_POS[2].add(0.0F, GATE_HEIGHT[2] - SHARED_BAR, 0.0F);
    public static final float DOUBLE_YAW = 270.0F;
    public static final float[] STACK_TIME = {28.0F, 58.0F, 96.0F};
    private static final float BEAM_HALF = 0.25F;

    public static final int WALL_Z = -4;
    public static final int WALL_X0 = -5;
    public static final int WALL_X1 = 4;
    public static final int WALL_TOP = 6;
    public static final int CEILING_X0 = 1;
    public static final int CEILING_X1 = 4;
    public static final int CEILING_Z1 = -1;
    public static final String SIDE_GATE = "side_gate";
    public static final String DIVE_GATE = "dive_gate";
    public static final String HANGING_GATE = "hanging_gate";
    public static final String DIVER = "diver";
    public static final ScenePos WALL_POINT = new ScenePos(-2.5F, 3.5F, WALL_Z + 1.0F);
    public static final ScenePos SIDE_POS = new ScenePos(-2.5F, 1.5F, WALL_Z + 3.0F);
    public static final ScenePos DIVE_POS = new ScenePos(-2.5F, 4.5F, WALL_Z + 1.0F);
    public static final float WALL_YAW = 0.0F;
    public static final ScenePos HANG_POINT = new ScenePos(2.5F, WALL_TOP, -1.5F);
    public static final ScenePos HANGING_POS = new ScenePos(2.5F, WALL_TOP - 4.0F, -1.5F);
    public static final float HANGING_YAW = 0.0F;
    public static final ScenePos DIVE_START = new ScenePos(-2.5F, 8.2F, 1.4F);
    public static final List<ScenePos> DIVE_PATH = List.of(
            new ScenePos(-2.5F, 7.4F, 0.0F),
            new ScenePos(-2.5F, 6.0F, -0.9F),
            new ScenePos(-2.5F, 4.5F, -1.0F),
            new ScenePos(-2.5F, 3.0F, -0.7F),
            new ScenePos(-2.5F, 2.2F, 0.6F),
            new ScenePos(-2.5F, 2.2F, 2.4F));
    public static final float DIVE_START_TIME = 150.0F;
    public static final float DIVE_LENGTH = 50.0F;

    private static final ScenePos OVERVIEW = new ScenePos(0.5F, 2.2F, 0.4F);
    private static final ScenePos WALL_VIEW = new ScenePos(-0.3F, 3.6F, -1.2F);
    private static final ScenePos LAP_VIEW = new ScenePos(0.5F, 2.7F, COURSE_Z);

    private RaceScene() {
    }

    public static float passTime(int pass) {
        return LAP_START + SEGMENT * PASS_INDEX[pass];
    }

    private static ScenePos gateTop(int gate) {
        return GATE_POS[gate].add(0.0F, GATE_HEIGHT[gate] - BEAM_HALF, 0.0F);
    }

    public static ScenePos ladderTop() {
        return LADDER_POS[1].add(0.0F, SMALL_HEIGHT - BEAM_HALF, 0.0F);
    }

    private static void box(SceneBuilder.StepBuilder s, int gate, float length) {
        ScenePos pos = GATE_POS[gate];
        boolean alongZ = GATE_YAW[gate] == 90.0F || GATE_YAW[gate] == 270.0F;
        float half = GATE_WIDTH[gate] / 2.0F;
        float halfX = alongZ ? 0.3F : half;
        float halfZ = alongZ ? half : 0.3F;
        s.highlight(pos.add(-halfX, 0.0F, -halfZ), pos.add(halfX, GATE_HEIGHT[gate], halfZ), length);
    }

    public static Scene create() {
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.2F, 0.0F);
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.16F, 0.0F);
        float lapLength = SEGMENT * LAP.size();
        return SceneBuilder.scene(ID)
                .icon(GATE_SMALL)
                .items(GATE_MEDIUM, GATE_LARGE, COURSE_TOOL)
                .view(5.3F)
                .camera(OVERVIEW, 24.0F, 46.0F, 1.0F)
                .step("place", 140.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -5, -5, 5, 4, GRASS, GRASS, 0.6F, 10.0F);
                    s.at(18.0F).spawn(PILOT, new ActorSpec.Pilot("", GATE_SMALL), PILOT_POS, SceneKit.pilotYaw(PILOT_POS, GATE_POS[0]),
                            Cue.Appear.POP, 10.0F);
                    for (int i = 0; i < 3; i++) {
                        float t = 30.0F + i * 22.0F;
                        s.at(t - 8.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, GATE_POS[i]), 0.0F, 0.0F, 8.0F);
                        s.at(t - 8.0F).equip(PILOT, Cue.Slot.HAND, GATE_ITEM[i]);
                        s.at(t).spawn(GATES[i], new ActorSpec.Gate(GATE_SIZE[i]), GATE_POS[i], GATE_YAW[i], Cue.Appear.POP, 10.0F);
                    }
                    s.at(24.0F).keysAt("place", overHead, 76.0F, KeyToken.use());
                    s.at(34.0F).callout("place", Anchor.at(gateTop(0)), Cue.Side.LEFT, 56.0F);
                    s.at(92.0F).callout("sizes", Anchor.at(gateTop(1)), Cue.Side.ABOVE, 0.0F);
                })
                .step("course", 150.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, COURSE_TOOL);
                    s.at(6.0F).keysAt("link", overHead, 0.0F, KeyToken.use());
                    for (int i = 0; i < 3; i++) {
                        float t = 10.0F + i * 36.0F;
                        s.at(t).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, GATE_POS[i]), 0.0F, 0.0F, 10.0F);
                        box(s.at(t + 10.0F), i, 26.0F);
                        s.at(t + 12.0F).callout(GATES[i], Anchor.at(gateTop(i)), i == 2 ? Cue.Side.RIGHT : Cue.Side.ABOVE, 0.0F);
                    }
                })
                .step("start", 120.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, REMOTE);
                    s.at(0.0F).equip(PILOT, Cue.Slot.HEAD, SceneKit.GOGGLES_ANALOG);
                    s.at(0.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, START), 0.0F, 0.0F, 12.0F);
                    s.at(4.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), START, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(0.0F).camera(new ScenePos(-3.6F, 1.9F, 1.4F), 30.0F, 30.0F, 1.7F, 24.0F);
                    for (int i = 0; i < 6; i++) {
                        s.at(24.0F + i * 10.0F).light(GATES[0], true);
                        s.at(29.0F + i * 10.0F).light(GATES[0], false);
                    }
                    s.at(22.0F).callout("pulse", Anchor.at(gateTop(0)), Cue.Side.ABOVE, 0.0F);
                    s.at(50.0F).keys("arm", 50.0F, KeyToken.mapping(SceneKit.KEY_ARM));
                    s.at(54.0F).callout("ready", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(80.0F).arm(DRONE, true);
                    s.at(80.0F).throttle(DRONE, 0.15F, 10.0F);
                    s.at(84.0F).light(GATES[0], true);
                })
                .step("fly", LAP_START + lapLength + 16.0F, s -> {
                    s.at(0.0F).camera(LAP_VIEW, -32.0F, 24.0F, 1.6F, 40.0F);
                    s.at(0.0F).throttle(DRONE, 0.7F, 12.0F);
                    s.at(0.0F).move(DRONE, LAP_START, Ease.IN_OUT, LAUNCH);
                    SceneKit.fly(s, DRONE, LAUNCH, LAP, Ease.LINEAR, LAP_START, lapLength, LAP.size() * 3, 22.0F);
                    s.at(passTime(0)).light(GATES[0], false);
                    s.at(passTime(0)).light(GATES[1], true);
                    s.at(passTime(1)).light(GATES[1], false);
                    s.at(passTime(1)).light(GATES[2], true);
                    s.at(passTime(2)).light(GATES[2], false);
                    s.at(passTime(2)).light(GATES[0], true);
                    s.at(passTime(3)).light(GATES[0], false);
                    s.at(passTime(3)).light(GATES[1], true);
                    s.at(4.0F).callout("clock", Anchor.at(gateTop(0)), Cue.Side.ABOVE, passTime(0) - 2.0F);
                    s.at(passTime(0) + 2.0F).callout("green", Anchor.at(gateTop(1)), Cue.Side.LEFT, 42.0F);
                    s.at(passTime(0) + 2.0F).callout("white", Anchor.at(gateTop(2)), Cue.Side.RIGHT, 42.0F);
                    s.at(passTime(1) + 4.0F).callout("next", onDrone, Cue.Side.RIGHT, 50.0F);
                })
                .step("lap", 120.0F, s -> {
                    ScenePos end = LAP.getLast();
                    s.at(0.0F).move(DRONE, 30.0F, Ease.OUT, end.add(0.0F, 0.1F, -0.8F));
                    s.at(0.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 20.0F);
                    s.at(0.0F).throttle(DRONE, 0.45F, 20.0F);
                    s.at(0.0F).camera(new ScenePos(-2.4F, 2.4F, -0.4F), 30.0F, 32.0F, 1.6F, 30.0F);
                    s.at(4.0F).callout("lap", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(40.0F).keys("reset", 0.0F, KeyToken.mapping(SceneKit.KEY_RESET));
                })
                .step("stack", 180.0F, s -> {
                    s.at(0.0F).despawn(DRONE, 10.0F);
                    s.at(0.0F).equip(PILOT, Cue.Slot.HEAD, "");
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, GATE_SMALL);
                    s.at(0.0F).camera(new ScenePos(1.5F, 4.6F, 0.4F), 24.0F, 18.0F, 0.85F, 30.0F);
                    s.at(4.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, GATE_POS[1]), 0.0F, 0.0F, 10.0F);
                    s.at(8.0F).keysAt("stack", overHead, 110.0F, KeyToken.use());
                    for (int i = 0; i < LADDER.length; i++) {
                        s.at(STACK_TIME[i]).spawn(LADDER[i], new ActorSpec.Gate("small"), LADDER_POS[i], LADDER_YAW[i], Cue.Appear.DROP, 10.0F);
                    }
                    s.at(STACK_TIME[1] + 12.0F).callout("ladder", Anchor.at(ladderTop()), Cue.Side.LEFT, 0.0F);
                    s.at(STACK_TIME[2] - 12.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, GATE_POS[2]), 0.0F, 0.0F, 10.0F);
                    s.at(STACK_TIME[2]).spawn(DOUBLE, new ActorSpec.Gate("small"), DOUBLE_POS, DOUBLE_YAW, Cue.Appear.DROP, 10.0F);
                    s.at(STACK_TIME[2] + 12.0F).callout("double", Anchor.at(DOUBLE_POS.add(0.0F, SMALL_HEIGHT - BEAM_HALF, 0.0F)),
                            Cue.Side.RIGHT, 0.0F);
                })
                .step("mount", 230.0F, s -> {
                    for (String gate : GATES) {
                        s.at(0.0F).despawn(gate, 10.0F);
                    }
                    for (String gate : LADDER) {
                        s.at(0.0F).despawn(gate, 10.0F);
                    }
                    s.at(0.0F).despawn(DOUBLE, 10.0F);
                    s.at(0.0F).camera(WALL_VIEW, 18.0F, 22.0F, 0.95F, 30.0F);
                    for (int y = 1; y <= WALL_TOP; y++) {
                        s.at(4.0F + (y - 1) * 3.0F).floor(WALL_X0, WALL_Z, WALL_X1, WALL_Z, y, STONE_BRICKS, 0.3F, 8.0F);
                    }
                    s.at(24.0F).floor(CEILING_X0, WALL_Z + 1, CEILING_X1, CEILING_Z1, WALL_TOP, STONE_BRICKS, 0.4F, 8.0F);
                    s.at(30.0F).equip(PILOT, Cue.Slot.HAND, GATE_MEDIUM);
                    s.at(30.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, WALL_POINT), 0.0F, 0.0F, 10.0F);
                    s.at(34.0F).keysAt("wall", overHead, 30.0F, KeyToken.use());
                    s.at(44.0F).spawn(SIDE_GATE, new ActorSpec.Gate("medium", "wall_side", false), SIDE_POS, WALL_YAW, Cue.Appear.POP, 10.0F);
                    s.at(48.0F).callout("side", Anchor.at(SIDE_POS.add(0.0F, 4.0F - BEAM_HALF, 1.6F)), Cue.Side.ABOVE, 22.0F);
                    s.at(62.0F).keys("mount", 36.0F, KeyToken.sneak(), KeyToken.mapping(SceneKit.KEY_GATE_ROTATE));
                    s.at(72.0F).despawn(SIDE_GATE, 6.0F);
                    s.at(74.0F).spawn(DIVE_GATE, new ActorSpec.Gate("medium", "wall_dive", false), DIVE_POS, WALL_YAW, Cue.Appear.POP, 10.0F);
                    s.at(78.0F).callout("dive", Anchor.at(DIVE_POS.add(0.0F, BEAM_HALF, 3.6F)), Cue.Side.RIGHT, 30.0F);
                    s.at(100.0F).keys("rotate", 30.0F, KeyToken.mapping(SceneKit.KEY_GATE_ROTATE));
                    s.at(102.0F).callout("flip", Anchor.at(DIVE_POS.add(0.0F, BEAM_HALF, 3.6F)), Cue.Side.RIGHT, 26.0F);
                    s.at(112.0F).equip(PILOT, Cue.Slot.HAND, GATE_SMALL);
                    s.at(112.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, HANG_POINT), 0.0F, 0.0F, 10.0F);
                    s.at(116.0F).keysAt("ceiling", overHead, 26.0F, KeyToken.use());
                    s.at(124.0F).spawn(HANGING_GATE, new ActorSpec.Gate("small", "hanging", false), HANGING_POS, HANGING_YAW, Cue.Appear.POP,
                            10.0F);
                    s.at(128.0F).callout("hanging", Anchor.at(HANGING_POS.add(0.0F, 3.0F - BEAM_HALF, 0.0F)), Cue.Side.RIGHT, 30.0F);
                    s.at(DIVE_START_TIME - 10.0F).equip(PILOT, Cue.Slot.HAND, REMOTE);
                    s.at(DIVE_START_TIME - 10.0F).spawn(DIVER, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), DIVE_START, 0.0F, Cue.Appear.POP,
                            8.0F);
                    s.at(DIVE_START_TIME - 6.0F).keys("flight_mode", 28.0F, KeyToken.mapping(SceneKit.KEY_FLIGHT_MODE));
                    s.at(DIVE_START_TIME - 4.0F).arm(DIVER, true);
                    s.at(DIVE_START_TIME - 4.0F).throttle(DIVER, 0.4F, 4.0F);
                    s.at(DIVE_START_TIME - 4.0F).light(DIVE_GATE, true);
                    SceneKit.fly(s, DIVER, DIVE_START, DIVE_PATH, Ease.IN_OUT, DIVE_START_TIME, DIVE_LENGTH, DIVE_PATH.size() * 3, 40.0F);
                    s.at(DIVE_START_TIME + 2.0F).callout("diving", Anchor.on(DIVER, 0.0F, 0.16F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(DIVE_START_TIME + DIVE_LENGTH * 0.55F).light(DIVE_GATE, false);
                })
                .build();
    }
}

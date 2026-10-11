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
import static de.lardtom.propwash.guide.scenes.SceneKit.DIVE_CUBE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_POLE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.RACE_FLAG;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.SLALOM_POLE;

public final class CourseElementsScene {
    public static final String ID = "course_elements";
    public static final String PILOT = "pilot";
    public static final String DRONE = "drone";
    public static final String FLAG = "flag";
    public static final String CUBE = "cube";
    public static final String[] POLES = {"pole1", "pole2", "pole3"};
    public static final String POLE = "pole";
    public static final String FLAG_KEY = "flag";
    public static final String CUBE_KEY = "cube";
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int EXIT_FRONT = 0;
    public static final DroneBuild BUILD = DroneBuild.RACER;
    public static final float DRONE_SCALE = 3.4F;
    public static final float POLE_YAW = 90.0F;
    public static final ScenePos[] POLE_POS = {
            new ScenePos(3.0F, 1.0F, 2.0F),
            new ScenePos(0.5F, 1.0F, 2.0F),
            new ScenePos(-2.0F, 1.0F, 2.0F)};
    public static final int[] POLE_SIDE = {LEFT, RIGHT, LEFT};
    public static final ScenePos FLAG_POS = new ScenePos(-4.2F, 1.0F, 1.4F);
    public static final float FLAG_YAW = 90.0F;
    public static final int FLAG_TURN = RIGHT;
    public static final ScenePos CUBE_POS = new ScenePos(2.75F, 1.0F, -1.75F);
    public static final float CUBE_YAW = 270.0F;
    public static final float CUBE_OPENING = 2.5F;
    public static final int CUBE_EXIT = EXIT_FRONT;
    public static final ScenePos PILOT_POS = new ScenePos(0.5F, 1.0F, 5.0F);
    public static final ScenePos START = new ScenePos(5.2F, 1.0F, 3.3F);
    public static final ScenePos LAUNCH = new ScenePos(5.2F, 2.2F, 3.3F);
    public static final List<ScenePos> PATH = List.of(
            new ScenePos(4.0F, 2.2F, 3.3F),
            new ScenePos(3.0F, 2.2F, 3.3F),
            new ScenePos(1.75F, 2.2F, 2.0F),
            new ScenePos(0.5F, 2.2F, 0.7F),
            new ScenePos(-0.75F, 2.2F, 2.0F),
            new ScenePos(-2.0F, 2.2F, 3.3F),
            new ScenePos(-3.2F, 2.2F, 3.2F),
            new ScenePos(-4.2F, 2.2F, 3.0F),
            new ScenePos(-5.3F, 2.2F, 2.5F),
            new ScenePos(-5.8F, 2.2F, 1.4F),
            new ScenePos(-5.3F, 2.2F, 0.3F),
            new ScenePos(-4.2F, 2.2F, -0.2F),
            new ScenePos(-2.6F, 2.6F, -0.6F),
            new ScenePos(-0.6F, 3.8F, -1.3F),
            new ScenePos(1.4F, 5.0F, -1.75F),
            new ScenePos(2.75F, 5.3F, -1.75F),
            new ScenePos(2.75F, 3.6F, -1.75F),
            new ScenePos(2.9F, 2.2F, -1.75F),
            new ScenePos(3.8F, 2.0F, -1.75F),
            new ScenePos(5.0F, 2.0F, -1.75F));
    public static final float FLY_START = 14.0F;
    public static final float FLY_LENGTH = 220.0F;
    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 2.4F, 0.6F);

    private CourseElementsScene() {
    }

    public static ActorSpec.Gate pole(int side) {
        return ActorSpec.Gate.element(POLE, side, 0.0F, 0.0F);
    }

    public static ActorSpec.Gate flag() {
        return ActorSpec.Gate.element(FLAG_KEY, FLAG_TURN, 0.0F, 0.0F);
    }

    public static ActorSpec.Gate cube() {
        return ActorSpec.Gate.element(CUBE_KEY, CUBE_EXIT, CUBE_OPENING, CUBE_OPENING);
    }

    private static float passTime(int point) {
        return FLY_START + FLY_LENGTH * (point + 1) / (PATH.size());
    }

    public static Scene create() {
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.2F, 0.0F);
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.16F, 0.0F);
        ScenePos poleTop = POLE_POS[1].add(0.0F, 3.4F, 0.0F);
        ScenePos flagTop = FLAG_POS.add(0.0F, 4.4F, 0.0F);
        ScenePos cubeTop = CUBE_POS.add(0.0F, 3.4F, 0.0F);
        return SceneBuilder.scene(ID)
                .icon(SLALOM_POLE)
                .items(RACE_FLAG, DIVE_CUBE, GATE_POLE)
                .view(5.6F)
                .camera(OVERVIEW, 20.0F, 40.0F, 1.0F)
                .step("slalom", 150.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -6, -5, 6, 5, GRASS, GRASS, 0.6F, 10.0F);
                    s.at(16.0F).spawn(PILOT, new ActorSpec.Pilot("", SLALOM_POLE), PILOT_POS, SceneKit.pilotYaw(PILOT_POS, POLE_POS[0]),
                            Cue.Appear.POP, 10.0F);
                    for (int i = 0; i < POLES.length; i++) {
                        float t = 28.0F + i * 16.0F;
                        s.at(t - 8.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, POLE_POS[i]), 0.0F, 0.0F, 8.0F);
                        s.at(t).spawn(POLES[i], pole(POLE_SIDE[i]), POLE_POS[i], POLE_YAW, Cue.Appear.POP, 10.0F);
                    }
                    s.at(24.0F).keysAt("place", overHead, 60.0F, KeyToken.use());
                    s.at(84.0F).equip(PILOT, Cue.Slot.HAND, COURSE_TOOL);
                    s.at(88.0F).keysAt("link", overHead, 56.0F, KeyToken.use());
                    s.at(90.0F).callout("slalom", Anchor.at(poleTop), Cue.Side.ABOVE, 0.0F);
                })
                .step("flag", 130.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, RACE_FLAG);
                    s.at(0.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, FLAG_POS), 0.0F, 0.0F, 10.0F);
                    s.at(0.0F).camera(new ScenePos(-4.0F, 2.6F, 1.4F), 32.0F, 38.0F, 1.35F, 24.0F);
                    s.at(12.0F).spawn(FLAG, flag(), FLAG_POS, FLAG_YAW, Cue.Appear.POP, 10.0F);
                    s.at(18.0F).callout("flag", Anchor.at(flagTop), Cue.Side.RIGHT, 0.0F);
                    s.at(56.0F).keys("turn", 60.0F, KeyToken.sneak(), KeyToken.mapping(SceneKit.KEY_GATE_ROTATE));
                })
                .step("cube", 130.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, DIVE_CUBE);
                    s.at(0.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, CUBE_POS), 0.0F, 0.0F, 10.0F);
                    s.at(0.0F).camera(new ScenePos(2.6F, 2.8F, -1.0F), -28.0F, 34.0F, 1.2F, 24.0F);
                    s.at(12.0F).spawn(CUBE, cube(), CUBE_POS, CUBE_YAW, Cue.Appear.POP, 10.0F);
                    s.at(18.0F).callout("cube", Anchor.at(cubeTop), Cue.Side.LEFT, 0.0F);
                    s.at(56.0F).keys("exit", 60.0F, KeyToken.sneak(), KeyToken.mapping(SceneKit.KEY_GATE_ROTATE));
                })
                .step("fly", FLY_START + FLY_LENGTH + 20.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, REMOTE);
                    s.at(0.0F).equip(PILOT, Cue.Slot.HEAD, SceneKit.GOGGLES_ANALOG);
                    s.at(0.0F).camera(OVERVIEW, 20.0F, 44.0F, 1.0F, 20.0F);
                    s.at(0.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), START, 270.0F, Cue.Appear.POP, 8.0F);
                    s.at(4.0F).arm(DRONE, true);
                    s.at(4.0F).throttle(DRONE, 0.6F, 10.0F);
                    s.at(4.0F).move(DRONE, FLY_START - 4.0F, Ease.IN_OUT, LAUNCH);
                    SceneKit.fly(s, DRONE, LAUNCH, PATH, Ease.LINEAR, FLY_START, FLY_LENGTH, PATH.size() * 4, 26.0F);
                    s.at(FLY_START).light(POLES[0], true);
                    s.at(passTime(1)).light(POLES[0], false);
                    s.at(passTime(1)).light(POLES[1], true);
                    s.at(passTime(3)).light(POLES[1], false);
                    s.at(passTime(3)).light(POLES[2], true);
                    s.at(passTime(5)).light(POLES[2], false);
                    s.at(passTime(5)).light(FLAG, true);
                    s.at(passTime(11)).light(FLAG, false);
                    s.at(passTime(11)).light(CUBE, true);
                    s.at((passTime(18) + passTime(19)) * 0.5F).light(CUBE, false);
                    s.at(FLY_START + 4.0F).callout("sides", onDrone, Cue.Side.RIGHT, passTime(5) - FLY_START - 4.0F);
                    s.at(passTime(6)).callout("around", onDrone, Cue.Side.RIGHT, passTime(11) - passTime(6));
                    s.at(passTime(12)).callout("dive", onDrone, Cue.Side.LEFT, passTime(18) - passTime(12));
                })
                .step("edit", 110.0F, s -> {
                    s.at(0.0F).despawn(DRONE, 10.0F);
                    s.at(0.0F).equip(PILOT, Cue.Slot.HEAD, "");
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, COURSE_TOOL);
                    s.at(0.0F).camera(OVERVIEW, 20.0F, 40.0F, 1.0F, 20.0F);
                    s.at(4.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, POLE_POS[1]), 0.0F, 0.0F, 10.0F);
                    s.at(10.0F).keysAt("edit", overHead, 80.0F, KeyToken.sneak(), KeyToken.use());
                    s.at(16.0F).callout("edit", Anchor.at(poleTop), Cue.Side.ABOVE, 0.0F);
                })
                .build();
    }
}

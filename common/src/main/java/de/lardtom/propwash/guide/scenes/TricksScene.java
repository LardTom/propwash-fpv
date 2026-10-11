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

import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE_BRICKS;

public final class TricksScene {
    public static final String ID = "tricks";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final float DRONE_SCALE = 1.6F;
    public static final float FLIP_TICKS = 12.0F;
    public static final float HALF_TICKS = 7.0F;
    public static final ScenePos PILOT_POS = new ScenePos(-2.6F, 1.0F, 2.6F);
    public static final ScenePos PAD = new ScenePos(-0.8F, 1.06F, 0.8F);
    public static final ScenePos HOVER = new ScenePos(-0.6F, 2.9F, 0.6F);
    public static final ScenePos HIGH = new ScenePos(-0.4F, 3.6F, 0.4F);
    public static final ScenePos LOOP_START = new ScenePos(-2.4F, 2.0F, 0.0F);
    public static final ScenePos GUILLOTINE_TOP = new ScenePos(-1.6F, 4.1F, -0.3F);
    public static final ScenePos GUILLOTINE_BOTTOM = new ScenePos(-1.8F, 1.8F, -0.7F);
    public static final ScenePos GUILLOTINE_EXIT = new ScenePos(-2.2F, 1.9F, -2.4F);
    public static final float GUILLOTINE_FLIP_TICKS = 10.0F;
    public static final float RIGHT = 90.0F;
    public static final float LEFT = -90.0F;

    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 2.6F, 0.0F);

    private TricksScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.2F, 0.0F);
        return SceneBuilder.scene(ID)
                .icon("minecraft:firework_rocket")
                .items(SceneKit.DRONE_ITEM)
                .view(3.6F)
                .camera(OVERVIEW, 28.0F, 22.0F, 1.0F)
                .step("flip_roll", 170.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -3, -3, 3, 3, GRASS, GRASS, 0.8F, 10.0F);
                    s.at(14.0F).spawn(PILOT, new ActorSpec.Pilot(SceneKit.GOGGLES_ANALOG, REMOTE), PILOT_POS,
                            SceneKit.pilotYaw(PILOT_POS, HOVER), Cue.Appear.POP, 10.0F);
                    s.at(18.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), PAD, RIGHT, Cue.Appear.POP, 10.0F);
                    s.at(26.0F).arm(DRONE, true);
                    s.at(26.0F).throttle(DRONE, 0.5F, 6.0F);
                    s.at(28.0F).move(DRONE, 22.0F, Ease.IN_OUT, HOVER);
                    s.at(30.0F).camera(HOVER.add(0.2F, -0.6F, -0.2F), 30.0F, 28.0F, 1.4F, 24.0F);
                    s.at(54.0F).callout("flip", onDrone, Cue.Side.RIGHT, 50.0F);
                    s.at(60.0F).throttle(DRONE, 0.3F, 2.0F);
                    s.at(60.0F).turn(DRONE, RIGHT, -360.0F, 0.0F, FLIP_TICKS, Ease.IN_OUT);
                    s.at(60.0F + FLIP_TICKS).turn(DRONE, RIGHT, 0.0F, 0.0F, 0.0F);
                    s.at(60.0F + FLIP_TICKS).throttle(DRONE, 0.5F, 4.0F);
                    s.at(108.0F).callout("roll", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(114.0F).throttle(DRONE, 0.3F, 2.0F);
                    s.at(114.0F).turn(DRONE, RIGHT, 0.0F, 360.0F, FLIP_TICKS, Ease.IN_OUT);
                    s.at(114.0F + FLIP_TICKS).turn(DRONE, RIGHT, 0.0F, 0.0F, 0.0F);
                    s.at(114.0F + FLIP_TICKS).throttle(DRONE, 0.5F, 4.0F);
                })
                .step("juicy", 150.0F, s -> {
                    ScenePos from = HOVER;
                    ScenePos to = HOVER.add(2.2F, 0.1F, -1.2F);
                    s.at(0.0F).turn(DRONE, SceneKit.heading(from, to), -15.0F, 0.0F, 10.0F);
                    s.at(0.0F).camera(new ScenePos(0.5F, 2.4F, 0.0F), 24.0F, 28.0F, 1.4F, 16.0F);
                    s.at(4.0F).callout("flick", onDrone, Cue.Side.RIGHT, 52.0F);
                    s.at(10.0F).move(DRONE, 40.0F, Ease.LINEAR, from.add(0.6F, 0.1F, -0.35F), from.add(1.4F, 0.2F, -0.8F), to);
                    float yaw = SceneKit.heading(from, to);
                    s.at(22.0F).throttle(DRONE, 0.7F, 2.0F);
                    s.at(22.0F).turn(DRONE, yaw, -180.0F, 0.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(22.0F + HALF_TICKS).throttle(DRONE, 0.3F, 2.0F);
                    s.at(22.0F + HALF_TICKS + 2.0F).turn(DRONE, yaw, -180.0F, 180.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(22.0F + 2.0F * HALF_TICKS + 2.0F).turn(DRONE, yaw + 180.0F, 0.0F, 0.0F, 0.0F);
                    s.at(58.0F).callout("juicy", onDrone, Cue.Side.LEFT, 0.0F);
                    s.at(100.0F).move(DRONE, 36.0F, Ease.IN_OUT, HOVER);
                    s.at(100.0F).turn(DRONE, RIGHT, 0.0F, 0.0F, 20.0F);
                })
                .step("rewind_rubik", 190.0F, s -> {
                    s.at(0.0F).camera(HOVER.add(0.2F, -0.6F, -0.2F), 34.0F, 28.0F, 1.55F, 16.0F);
                    s.at(4.0F).callout("rewind", onDrone, Cue.Side.RIGHT, 56.0F);
                    s.at(12.0F).turn(DRONE, RIGHT, 0.0F, 180.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(12.0F + HALF_TICKS + 5.0F).turn(DRONE, RIGHT, 0.0F, 0.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(70.0F).callout("rubik", onDrone, Cue.Side.RIGHT, 0.0F);
                    float t = 80.0F;
                    s.at(t).throttle(DRONE, 0.3F, 2.0F);
                    s.at(t).turn(DRONE, RIGHT, -180.0F, 0.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(t + HALF_TICKS).turn(DRONE, RIGHT, -180.0F, 360.0F, FLIP_TICKS, Ease.IN_OUT);
                    s.at(t + HALF_TICKS + FLIP_TICKS).turn(DRONE, RIGHT, -360.0F, 360.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(t + 2.0F * HALF_TICKS + FLIP_TICKS).turn(DRONE, RIGHT, 0.0F, 0.0F, 0.0F);
                    s.at(t + 2.0F * HALF_TICKS + FLIP_TICKS).throttle(DRONE, 0.5F, 4.0F);
                    s.at(t + 2.0F * HALF_TICKS + FLIP_TICKS).move(DRONE, 8.0F, Ease.OUT, HOVER.add(0.0F, 0.15F, 0.0F), HOVER);
                })
                .step("loops", 200.0F, s -> {
                    for (int z = -1; z <= 1; z++) {
                        for (int y = 1; y <= 2; y++) {
                            s.at(2.0F + (z + 1) * 3.0F + y).drop(0, y, z, STONE_BRICKS, 8.0F);
                        }
                    }
                    s.at(0.0F).camera(new ScenePos(0.2F, 2.6F, 0.0F), 10.0F, 22.0F, 1.1F, 20.0F);
                    s.at(0.0F).move(DRONE, 24.0F, Ease.IN_OUT, LOOP_START.add(0.4F, 0.5F, 0.0F), LOOP_START);
                    s.at(0.0F).turn(DRONE, RIGHT, 0.0F, 0.0F, 20.0F);
                    s.at(26.0F).callout("power_loop", Anchor.on(DRONE, 0.0F, 0.2F, 0.0F), Cue.Side.ABOVE, 64.0F);
                    s.at(30.0F).throttle(DRONE, 0.9F, 4.0F);
                    s.at(30.0F).move(DRONE, 40.0F, Ease.LINEAR, new ScenePos(-1.2F, 2.2F, 0.0F), new ScenePos(-0.6F, 3.2F, 0.0F),
                            new ScenePos(0.5F, 3.9F, 0.0F), new ScenePos(1.5F, 3.3F, 0.0F), new ScenePos(1.9F, 2.4F, 0.0F),
                            new ScenePos(2.6F, 2.1F, 0.0F));
                    s.at(36.0F).turn(DRONE, RIGHT, 360.0F, 0.0F, 30.0F, Ease.IN_OUT);
                    s.at(66.0F).turn(DRONE, RIGHT, 0.0F, 0.0F, 0.0F);
                    s.at(70.0F).throttle(DRONE, 0.5F, 4.0F);
                    s.at(96.0F).callout("split_s", onDrone, Cue.Side.LEFT, 0.0F);
                    s.at(96.0F).move(DRONE, 20.0F, Ease.IN_OUT, new ScenePos(2.2F, 3.8F, 0.0F));
                    s.at(96.0F).turn(DRONE, LEFT, 0.0F, 0.0F, 14.0F);
                    s.at(120.0F).move(DRONE, 30.0F, Ease.LINEAR, new ScenePos(1.6F, 3.8F, 0.0F), new ScenePos(1.0F, 3.2F, 0.0F),
                            new ScenePos(1.3F, 2.5F, 0.0F), new ScenePos(2.2F, 2.3F, 0.0F));
                    s.at(120.0F).turn(DRONE, LEFT, 0.0F, 180.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(120.0F + HALF_TICKS).turn(DRONE, LEFT, -180.0F, 180.0F, 18.0F, Ease.IN_OUT);
                    s.at(145.0F + HALF_TICKS).turn(DRONE, RIGHT, 0.0F, 0.0F, 0.0F);
                    s.at(170.0F).move(DRONE, 28.0F, Ease.IN_OUT, HOVER);
                })
                .step("spins", 190.0F, s -> {
                    s.at(0.0F).move(DRONE, 20.0F, Ease.IN_OUT, HIGH);
                    s.at(0.0F).camera(new ScenePos(-0.4F, 2.8F, 0.4F), 30.0F, 24.0F, 1.2F, 20.0F);
                    s.at(18.0F).callout("inverted_spin", onDrone, Cue.Side.RIGHT, 62.0F);
                    s.at(24.0F).throttle(DRONE, 0.2F, 2.0F);
                    s.at(24.0F).turn(DRONE, RIGHT, 0.0F, 180.0F, HALF_TICKS, Ease.IN_OUT);
                    for (int i = 1; i <= 3; i++) {
                        s.at(24.0F + HALF_TICKS + (i - 1) * 5.0F).turn(DRONE, RIGHT + 120.0F * i, 0.0F, 180.0F, 5.0F, Ease.LINEAR);
                    }
                    s.at(24.0F + HALF_TICKS).move(DRONE, 15.0F, Ease.IN, HIGH.add(0.0F, -0.4F, 0.0F));
                    s.at(24.0F + HALF_TICKS + 15.0F).turn(DRONE, RIGHT, 0.0F, 0.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(24.0F + 2.0F * HALF_TICKS + 15.0F).throttle(DRONE, 0.6F, 4.0F);
                    s.at(70.0F).move(DRONE, 16.0F, Ease.IN_OUT, HIGH.add(0.0F, 0.6F, 0.0F));
                    s.at(90.0F).callout("trippy", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(96.0F).throttle(DRONE, 0.15F, 2.0F);
                    s.at(96.0F).turn(DRONE, RIGHT, 0.0F, 180.0F, HALF_TICKS, Ease.IN_OUT);
                    for (int i = 1; i <= 6; i++) {
                        s.at(96.0F + HALF_TICKS + (i - 1) * 5.0F).turn(DRONE, RIGHT + 120.0F * i, 0.0F, 180.0F, 5.0F, Ease.LINEAR);
                    }
                    s.at(96.0F + HALF_TICKS).move(DRONE, 30.0F, Ease.IN, new ScenePos(-0.4F, 2.1F, 0.4F));
                    s.at(96.0F + HALF_TICKS + 30.0F).turn(DRONE, RIGHT, 0.0F, 0.0F, HALF_TICKS, Ease.IN_OUT);
                    s.at(96.0F + 2.0F * HALF_TICKS + 30.0F).throttle(DRONE, 0.5F, 4.0F);
                    s.at(150.0F).move(DRONE, 30.0F, Ease.IN_OUT, HOVER);
                })
                .step("new_school", 110.0F, s -> {
                    float exit = SceneKit.heading(GUILLOTINE_BOTTOM, GUILLOTINE_EXIT);
                    s.at(0.0F).camera(new ScenePos(-1.0F, 2.8F, -0.6F), 30.0F, 18.0F, 1.0F, 20.0F);
                    s.at(0.0F).move(DRONE, 24.0F, Ease.IN_OUT, GUILLOTINE_TOP);
                    s.at(0.0F).turn(DRONE, exit, 0.0F, 0.0F, 12.0F);
                    s.at(0.0F).throttle(DRONE, 0.6F, 4.0F);
                    s.at(4.0F).callout("guillotine", Anchor.on(DRONE, 0.0F, 0.2F, 0.0F), Cue.Side.RIGHT, 62.0F);
                    s.at(26.0F).throttle(DRONE, 0.1F, 2.0F);
                    s.at(26.0F).turn(DRONE, exit, -90.0F, 0.0F, 5.0F, Ease.IN_OUT);
                    s.at(31.0F).move(DRONE, 16.0F, Ease.IN, GUILLOTINE_BOTTOM);
                    s.at(47.0F).turn(DRONE, exit, -360.0F, 0.0F, GUILLOTINE_FLIP_TICKS, Ease.IN_OUT);
                    s.at(47.0F).move(DRONE, 18.0F, Ease.OUT, new ScenePos(-2.0F, 1.7F, -1.5F), GUILLOTINE_EXIT);
                    s.at(52.0F).throttle(DRONE, 0.9F, 2.0F);
                    s.at(47.0F + GUILLOTINE_FLIP_TICKS).turn(DRONE, exit, 0.0F, 0.0F, 0.0F);
                    s.at(66.0F).callout("sbang", onDrone, Cue.Side.LEFT, 0.0F);
                    s.at(66.0F).throttle(DRONE, 0.5F, 4.0F);
                    s.at(86.0F).move(DRONE, 22.0F, Ease.IN_OUT, HOVER);
                    s.at(86.0F).turn(DRONE, RIGHT, 0.0F, 0.0F, 16.0F);
                })
                .step("combo", 190.0F, s -> {
                    s.at(0.0F).camera(HOVER.add(0.2F, -0.6F, -0.2F), 30.0F, 28.0F, 1.4F, 16.0F);
                    s.at(4.0F).callout("chain", onDrone, Cue.Side.RIGHT, 60.0F);
                    s.at(140.0F).camera(OVERVIEW, 28.0F, 22.0F, 1.0F, 30.0F);
                    s.at(10.0F).throttle(DRONE, 0.3F, 2.0F);
                    s.at(10.0F).turn(DRONE, RIGHT, -360.0F, 0.0F, FLIP_TICKS, Ease.IN_OUT);
                    s.at(10.0F + FLIP_TICKS).turn(DRONE, RIGHT, 0.0F, 0.0F, 0.0F);
                    s.at(10.0F + FLIP_TICKS).turn(DRONE, RIGHT, 0.0F, 360.0F, FLIP_TICKS, Ease.IN_OUT);
                    s.at(10.0F + 2.0F * FLIP_TICKS).turn(DRONE, RIGHT, 0.0F, 0.0F, 0.0F);
                    s.at(10.0F + 2.0F * FLIP_TICKS).throttle(DRONE, 0.5F, 4.0F);
                    s.at(68.0F).callout("combo", onDrone, Cue.Side.RIGHT, 56.0F);
                    s.at(130.0F).keys("configure", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                    s.at(132.0F).callout("tab", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(150.0F).move(DRONE, 30.0F, Ease.IN_OUT, PAD);
                    s.at(150.0F).throttle(DRONE, 0.2F, 20.0F);
                    s.at(182.0F).throttle(DRONE, 0.0F, 4.0F);
                    s.at(184.0F).arm(DRONE, false);
                })
                .build();
    }
}

package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.input.StickChannel;

import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;

public final class ConfiguratorScene {
    public static final String ID = "configurator";
    public static final String DRONE = "drone";
    public static final String SECOND = "second";
    public static final String RADIO = "radio";
    public static final String COMMAND_ITEM = "command";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final ScenePos CENTER = new ScenePos(1.0F, 2.0F, 0.7F);
    public static final ScenePos LEFT_POS = new ScenePos(-0.2F, 2.0F, 0.5F);
    public static final ScenePos RIGHT_POS = new ScenePos(2.2F, 2.0F, 0.5F);
    public static final ScenePos RADIO_POS = new ScenePos(1.0F, 1.0F, 1.7F);
    public static final float RADIO_SCALE = 0.9F;
    public static final float DRONE_SCALE = 2.2F;
    public static final float RADIO_TILT = 35.0F;
    public static final float HOVER = -0.15F;
    public static final float CLIMB = 0.65F;
    public static final float ROLL_ANGLE = 32.0F;
    public static final float PITCH_ANGLE = -28.0F;

    private static final ScenePos SWITCH_VIEW = new ScenePos(1.6F, 1.7F, 0.8F);
    private static final float SWITCH_ZOOM = 1.35F;
    private static final ScenePos STICKS_VIEW = new ScenePos(1.1F, 1.9F, 1.05F);
    private static final float STICKS_YAW = 10.0F;
    private static final float STICKS_PITCH = 28.0F;
    private static final float STICKS_ZOOM = 1.55F;
    private static final float RATES_ZOOM = 1.35F;
    private static final ScenePos RATES_VIEW = new ScenePos(1.0F, 1.8F, 0.9F);

    private ConfiguratorScene() {
    }

    public static ScenePos radioPoint(float x, float y, float z) {
        double tilt = Math.toRadians(RADIO_TILT);
        float cos = (float) Math.cos(tilt);
        float sin = (float) Math.sin(tilt);
        return new ScenePos(x * RADIO_SCALE, (y * cos + z * sin) * RADIO_SCALE, (-y * sin + z * cos) * RADIO_SCALE);
    }

    public static ScenePos stickTip(boolean left) {
        return radioPoint(left ? -0.25F : 0.25F, 0.375F, 0.33F);
    }

    private static void sticks(SceneBuilder.StepBuilder s, float at, float leftX, float leftY, float rightX, float rightY) {
        s.at(at).sticks(RADIO, leftX, leftY, rightX, rightY, 5.0F);
    }

    private static void wobble(SceneBuilder.StepBuilder s, String drone, float at) {
        float[] angles = {-14.0F, 9.0F, -6.0F, 4.0F, -2.0F, 0.0F};
        for (int i = 0; i < angles.length; i++) {
            s.at(at + i * 6.0F).turn(drone, 0.0F, 0.0F, angles[i], 6.0F);
        }
    }

    public static Scene create() {
        float lift = 0.16F * DRONE_SCALE / ActorSpec.Drone.STAGE_SCALE;
        Anchor onDrone = Anchor.on(DRONE, 0.0F, lift, 0.0F);
        Anchor onSecond = Anchor.on(SECOND, 0.0F, lift, 0.0F);
        ScenePos left = stickTip(true);
        ScenePos right = stickTip(false);
        Anchor leftStick = Anchor.on(RADIO, left.x(), left.y(), left.z());
        Anchor rightStick = Anchor.on(RADIO, right.x(), right.y(), right.z());
        Anchor rollStick = Anchor.onStick(RADIO, StickChannel.ROLL, right);
        Anchor pitchStick = Anchor.onStick(RADIO, StickChannel.PITCH, right);
        Anchor yawStick = Anchor.onStick(RADIO, StickChannel.YAW, left);
        Anchor throttleStick = Anchor.onStick(RADIO, StickChannel.THROTTLE, left);
        ScenePos top = radioPoint(0.0F, 0.9F, 0.0F);
        Anchor radioTop = Anchor.on(RADIO, top.x(), top.y(), top.z());
        return SceneBuilder.scene(ID)
                .icon(SceneKit.part(StackType.STACK_30))
                .items(SceneKit.stacks())
                .items(REMOTE, SceneKit.DRONE_ITEM)
                .view(2.2F)
                .camera(STICKS_VIEW, STICKS_YAW, STICKS_PITCH, STICKS_ZOOM)
                .step("sticks", 204.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -5, -5, 6, 5, GRASS, GRASS, 0.5F, 10.0F);
                    s.at(12.0F).spawn(RADIO, new ActorSpec.Remote(RADIO_SCALE), RADIO_POS, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(12.0F).turn(RADIO, 0.0F, RADIO_TILT, 0.0F, 0.0F);
                    s.at(14.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), CENTER, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(14.0F).arm(DRONE, true);
                    s.at(14.0F).throttle(DRONE, 0.45F, 10.0F);
                    s.at(14.0F).sticks(RADIO, 0.0F, HOVER, 0.0F, 0.0F, 10.0F);
                    s.at(26.0F).callout("left", leftStick, Cue.Side.LEFT, 34.0F);
                    s.at(26.0F).callout("right", rightStick, Cue.Side.RIGHT, 34.0F);

                    sticks(s, 62.0F, 0.0F, HOVER, 1.0F, 0.0F);
                    s.at(63.0F).turn(DRONE, 0.0F, 0.0F, ROLL_ANGLE, 7.0F);
                    s.at(64.0F).move(DRONE, 24.0F, Ease.IN_OUT, CENTER.add(0.35F, 0.0F, 0.0F));
                    sticks(s, 80.0F, 0.0F, HOVER, 0.0F, 0.0F);
                    s.at(81.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 9.0F);
                    s.at(62.0F).callout("roll", onDrone, Cue.Side.LEFT, 32.0F);
                    s.at(62.0F).callout("stick_roll", rollStick, Cue.Side.RIGHT, 32.0F);

                    sticks(s, 96.0F, 0.0F, HOVER, 0.0F, 1.0F);
                    s.at(97.0F).turn(DRONE, 0.0F, PITCH_ANGLE, 0.0F, 7.0F);
                    s.at(98.0F).move(DRONE, 24.0F, Ease.IN_OUT, CENTER.add(0.35F, 0.0F, -0.35F));
                    sticks(s, 114.0F, 0.0F, HOVER, 0.0F, 0.0F);
                    s.at(115.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 9.0F);
                    s.at(96.0F).callout("pitch", onDrone, Cue.Side.LEFT, 32.0F);
                    s.at(96.0F).callout("stick_pitch", pitchStick, Cue.Side.RIGHT, 32.0F);

                    sticks(s, 130.0F, 1.0F, HOVER, 0.0F, 0.0F);
                    s.at(132.0F).turn(DRONE, 70.0F, 0.0F, 0.0F, 16.0F);
                    sticks(s, 148.0F, -1.0F, HOVER, 0.0F, 0.0F);
                    s.at(150.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 14.0F);
                    sticks(s, 162.0F, 0.0F, HOVER, 0.0F, 0.0F);
                    s.at(130.0F).callout("yaw", onDrone, Cue.Side.RIGHT, 36.0F);
                    s.at(130.0F).callout("stick_yaw", yawStick, Cue.Side.LEFT, 36.0F);

                    sticks(s, 168.0F, 0.0F, CLIMB, 0.0F, 0.0F);
                    s.at(170.0F).throttle(DRONE, 0.75F, 8.0F);
                    s.at(170.0F).move(DRONE, 18.0F, Ease.IN_OUT, CENTER.add(0.35F, 0.45F, -0.35F));
                    sticks(s, 188.0F, 0.0F, HOVER, 0.0F, 0.0F);
                    s.at(188.0F).throttle(DRONE, 0.45F, 8.0F);
                    s.at(168.0F).callout("throttle", onDrone, Cue.Side.RIGHT, 36.0F);
                    s.at(168.0F).callout("stick_throttle", throttleStick, Cue.Side.LEFT, 36.0F);
                })
                .step("rates", 160.0F, s -> {
                    s.at(0.0F).camera(RATES_VIEW, 12.0F, 30.0F, RATES_ZOOM, 18.0F);
                    s.at(0.0F).move(DRONE, 16.0F, Ease.IN_OUT, LEFT_POS);
                    s.at(0.0F).spawn(SECOND, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), RIGHT_POS, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(0.0F).arm(SECOND, true);
                    s.at(0.0F).throttle(SECOND, 0.45F, 10.0F);
                    s.at(16.0F).callout("low", onDrone, Cue.Side.LEFT, 0.0F);
                    s.at(16.0F).callout("high", onSecond, Cue.Side.RIGHT, 0.0F);
                    s.at(20.0F).callout("input", rollStick, Cue.Side.RIGHT, 0.0F);
                    for (int round = 0; round < 2; round++) {
                        float t = 30.0F + round * 64.0F;
                        sticks(s, t, 0.0F, HOVER, 1.0F, 0.0F);
                        s.at(t + 4.0F).turn(DRONE, 0.0F, 0.0F, 360.0F, 40.0F, Ease.LINEAR);
                        s.at(t + 4.0F).turn(SECOND, 0.0F, 0.0F, 720.0F, 40.0F, Ease.LINEAR);
                        sticks(s, t + 42.0F, 0.0F, HOVER, 0.0F, 0.0F);
                        s.at(t + 46.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 0.0F);
                        s.at(t + 46.0F).turn(SECOND, 0.0F, 0.0F, 0.0F, 0.0F);
                    }
                    s.at(104.0F).keys("rates", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                })
                .step("pid", 150.0F, s -> {
                    s.at(0.0F).camera(RATES_VIEW, 12.0F, 30.0F, RATES_ZOOM, 24.0F);
                    s.at(8.0F).callout("wobble", onDrone, Cue.Side.LEFT, 80.0F);
                    s.at(8.0F).callout("smooth", onSecond, Cue.Side.RIGHT, 80.0F);
                    for (int round = 0; round < 2; round++) {
                        float t = 16.0F + round * 64.0F;
                        sticks(s, t, 0.0F, HOVER, 1.0F, 0.0F);
                        sticks(s, t + 10.0F, 0.0F, HOVER, -1.0F, 0.0F);
                        sticks(s, t + 20.0F, 0.0F, HOVER, 0.0F, 0.0F);
                        s.at(t + 2.0F).turn(DRONE, 0.0F, 0.0F, 30.0F, 8.0F);
                        s.at(t + 2.0F).turn(SECOND, 0.0F, 0.0F, 30.0F, 8.0F);
                        s.at(t + 12.0F).turn(SECOND, 0.0F, 0.0F, 0.0F, 10.0F);
                        wobble(s, DRONE, t + 12.0F);
                    }
                    s.at(96.0F).callout("derived", onSecond, Cue.Side.RIGHT, 0.0F);
                })
                .step("modes", 190.0F, s -> {
                    s.at(0.0F).camera(STICKS_VIEW, STICKS_YAW, STICKS_PITCH, STICKS_ZOOM, 24.0F);
                    s.at(0.0F).despawn(SECOND, 10.0F);
                    s.at(0.0F).move(DRONE, 16.0F, Ease.IN_OUT, CENTER);
                    s.at(16.0F).callout("angle", onDrone, Cue.Side.RIGHT, 70.0F);
                    sticks(s, 24.0F, 0.0F, HOVER, 1.0F, 0.0F);
                    s.at(26.0F).turn(DRONE, 0.0F, 0.0F, 30.0F, 10.0F);
                    s.at(26.0F).move(DRONE, 30.0F, Ease.IN_OUT, CENTER.add(0.35F, 0.0F, 0.0F));
                    sticks(s, 44.0F, 0.0F, HOVER, 0.0F, 0.0F);
                    s.at(46.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 14.0F);
                    s.at(88.0F).move(DRONE, 16.0F, Ease.IN_OUT, CENTER);
                    s.at(90.0F).keys("mode", 30.0F, KeyToken.mapping(SceneKit.KEY_FLIGHT_MODE));
                    s.at(104.0F).callout("acro", onDrone, Cue.Side.RIGHT, 0.0F);
                    sticks(s, 112.0F, 0.0F, HOVER, 1.0F, 0.0F);
                    s.at(114.0F).turn(DRONE, 0.0F, 0.0F, 45.0F, 12.0F);
                    sticks(s, 126.0F, 0.0F, HOVER, 0.0F, 0.0F);
                    s.at(118.0F).move(DRONE, 34.0F, Ease.IN, CENTER.add(0.45F, -0.1F, 0.0F));
                    sticks(s, 156.0F, 0.0F, HOVER, -1.0F, 0.0F);
                    s.at(158.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 12.0F);
                    sticks(s, 170.0F, 0.0F, HOVER, 0.0F, 0.0F);
                })
                .step("world_rules", 150.0F, s -> {
                    ScenePos drift = CENTER.add(0.6F, 0.05F, 0.0F);
                    s.at(0.0F).camera(RATES_VIEW, 12.0F, 30.0F, RATES_ZOOM, 24.0F);
                    s.at(0.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 10.0F);
                    s.at(0.0F).move(DRONE, 20.0F, Ease.IN_OUT, CENTER);
                    s.at(0.0F).sticks(RADIO, 0.0F, HOVER, 0.0F, 0.0F, 8.0F);
                    s.at(20.0F).callout("world_rules", onDrone, Cue.Side.RIGHT, 0.0F);
                    for (int gust = 0; gust < 2; gust++) {
                        float t = 36.0F + gust * 48.0F;
                        s.at(t).turn(DRONE, 0.0F, 0.0F, -14.0F, 8.0F);
                        s.at(t).move(DRONE, 20.0F, Ease.IN_OUT, drift);
                        s.at(t + 20.0F).turn(DRONE, 0.0F, 0.0F, 8.0F, 8.0F);
                        s.at(t + 22.0F).move(DRONE, 20.0F, Ease.IN_OUT, CENTER);
                        s.at(t + 38.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 8.0F);
                    }
                    s.at(36.0F).callout("wind", radioTop, Cue.Side.LEFT, 0.0F);
                    s.at(110.0F).keys("world_rules", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                })
                .step("switches", 150.0F, s -> {
                    ScenePos landing = new ScenePos(2.6F, 1.0F, -0.4F);
                    s.at(0.0F).camera(SWITCH_VIEW, STICKS_YAW, STICKS_PITCH, SWITCH_ZOOM, 30.0F);
                    s.at(0.0F).move(DRONE, 30.0F, Ease.IN_OUT, CENTER.add(0.8F, -0.8F, -0.2F), landing);
                    s.at(0.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 10.0F);
                    s.at(0.0F).sticks(RADIO, 0.0F, -0.6F, 0.0F, 0.0F, 20.0F);
                    s.at(20.0F).throttle(DRONE, 0.05F, 10.0F);
                    s.at(20.0F).sticks(RADIO, 0.0F, -1.0F, 0.0F, 0.0F, 10.0F);
                    s.at(30.0F).arm(DRONE, false);
                    s.at(12.0F).callout("switches", radioTop, Cue.Side.LEFT, 50.0F);
                    s.at(64.0F).callout("assign", radioTop, Cue.Side.LEFT, 0.0F);
                    s.at(34.0F).callout("guard", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(40.0F).keys("open", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                })
                .step("commands", 150.0F, s -> {
                    ScenePos clockPos = RADIO_POS.add(top.x() + 0.55F, top.y() + 0.2F, top.z());
                    Anchor clock = Anchor.on(COMMAND_ITEM, 0.0F, 0.0F, 0.0F);
                    s.at(0.0F).camera(SWITCH_VIEW, STICKS_YAW, STICKS_PITCH, SWITCH_ZOOM, 20.0F);
                    s.at(10.0F).spawn(COMMAND_ITEM, new ActorSpec.Item(SceneKit.CLOCK, 0.7F, true), clockPos, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(12.0F).callout("command_on", clock, Cue.Side.RIGHT, 56.0F);
                    s.at(70.0F).callout("command_off", clock, Cue.Side.RIGHT, 0.0F);
                    s.at(24.0F).callout("command_rights", onDrone, Cue.Side.LEFT, 0.0F);
                    s.at(96.0F).keys("commands", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                    s.at(136.0F).despawn(COMMAND_ITEM, 10.0F);
                })
                .build();
    }
}

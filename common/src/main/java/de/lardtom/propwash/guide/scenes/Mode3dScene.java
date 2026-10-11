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

public final class Mode3dScene {
    public static final String ID = "mode_3d";
    public static final String DRONE = "drone";
    public static final String RADIO = "radio";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final float DRONE_SCALE = 2.0F;
    public static final ScenePos PAD = new ScenePos(1.0F, 1.06F, 0.2F);
    public static final ScenePos HOVER = new ScenePos(1.0F, 2.1F, 0.2F);
    public static final ScenePos HIGH = new ScenePos(1.0F, 2.7F, 0.2F);
    public static final ScenePos LOW = new ScenePos(1.0F, 1.6F, 0.2F);
    public static final float IDLE = 0.08F;
    public static final float FLIP_TICKS = 14.0F;

    private static final ScenePos VIEW = new ScenePos(1.0F, 1.9F, 0.8F);
    private static final float VIEW_YAW = 28.0F;
    private static final float VIEW_PITCH = 9.0F;
    private static final float VIEW_ZOOM = 1.15F;

    private Mode3dScene() {
    }

    private static void left(SceneBuilder.StepBuilder s, float at, float throttle) {
        s.at(at).sticks(RADIO, 0.0F, throttle, 0.0F, 0.0F, 6.0F);
    }

    public static Scene create() {
        float lift = 0.16F * DRONE_SCALE / ActorSpec.Drone.STAGE_SCALE;
        Anchor onDrone = Anchor.on(DRONE, 0.0F, lift, 0.0F);
        ScenePos tip = ConfiguratorScene.stickTip(true);
        Anchor leftStick = Anchor.on(RADIO, tip.x(), tip.y(), tip.z());
        ScenePos top = ConfiguratorScene.radioPoint(0.0F, 0.9F, 0.0F);
        Anchor radioTop = Anchor.on(RADIO, top.x(), top.y(), top.z());
        return SceneBuilder.scene(ID)
                .icon(REMOTE)
                .items(REMOTE, SceneKit.DRONE_ITEM)
                .view(2.6F)
                .camera(VIEW, VIEW_YAW, VIEW_PITCH, VIEW_ZOOM)
                .step("unlock", 160.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -4, -4, 5, 4, GRASS, GRASS, 0.5F, 10.0F);
                    s.at(12.0F).spawn(RADIO, new ActorSpec.Remote(ConfiguratorScene.RADIO_SCALE), ConfiguratorScene.RADIO_POS, 0.0F, Cue.Appear.POP,
                            10.0F);
                    s.at(12.0F).turn(RADIO, 0.0F, ConfiguratorScene.RADIO_TILT, 0.0F, 0.0F);
                    s.at(12.0F).sticks(RADIO, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F);
                    s.at(14.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), PAD, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(24.0F).keys("configure", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                    s.at(26.0F).callout("unlock", radioTop, Cue.Side.ABOVE, 64.0F);
                    s.at(94.0F).keys("mode_key", 0.0F, KeyToken.mapping(SceneKit.KEY_MODE_3D));
                    s.at(96.0F).callout("bind", radioTop, Cue.Side.ABOVE, 0.0F);
                })
                .step("throttle", 220.0F, s -> {
                    left(s, 0.0F, 0.0F);
                    s.at(4.0F).arm(DRONE, true);
                    s.at(4.0F).throttle(DRONE, IDLE, 6.0F);
                    s.at(8.0F).callout("center", leftStick, Cue.Side.LEFT, 48.0F);
                    left(s, 58.0F, 0.6F);
                    s.at(58.0F).throttle(DRONE, 0.6F, 6.0F);
                    s.at(60.0F).move(DRONE, 40.0F, Ease.IN_OUT, HIGH);
                    s.at(60.0F).callout("up", leftStick, Cue.Side.LEFT, 46.0F);
                    left(s, 110.0F, -0.6F);
                    s.at(110.0F).throttle(DRONE, -0.6F, 8.0F);
                    s.at(114.0F).move(DRONE, 34.0F, Ease.IN, LOW);
                    s.at(112.0F).callout("down", leftStick, Cue.Side.LEFT, 54.0F);
                    s.at(126.0F).callout("weak", onDrone, Cue.Side.RIGHT, 54.0F);
                    left(s, 170.0F, 0.25F);
                    s.at(170.0F).throttle(DRONE, 0.45F, 6.0F);
                    s.at(172.0F).move(DRONE, 40.0F, Ease.IN_OUT, HOVER);
                    s.at(176.0F).callout("hover", leftStick, Cue.Side.LEFT, 0.0F);
                })
                .step("inverted", 200.0F, s -> {
                    s.at(0.0F).sticks(RADIO, 0.0F, 0.0F, 1.0F, 0.0F, 4.0F);
                    s.at(0.0F).throttle(DRONE, IDLE, 4.0F);
                    s.at(4.0F).turn(DRONE, 0.0F, 0.0F, 180.0F, FLIP_TICKS);
                    s.at(2.0F).callout("flip", onDrone, Cue.Side.RIGHT, 40.0F);
                    left(s, 20.0F, -0.3F);
                    s.at(20.0F).throttle(DRONE, -0.45F, 6.0F);
                    s.at(44.0F).callout("hover_inverted", leftStick, Cue.Side.LEFT, 70.0F);
                    s.at(50.0F).move(DRONE, 60.0F, Ease.LINEAR, HOVER.add(0.0F, 0.06F, 0.0F), HOVER.add(0.0F, -0.04F, 0.0F), HOVER);
                    s.at(118.0F).callout("controls", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(120.0F).turn(DRONE, 35.0F, 0.0F, 180.0F, 20.0F);
                    s.at(150.0F).turn(DRONE, 0.0F, 0.0F, 180.0F, 20.0F);
                })
                .step("punch", 180.0F, s -> {
                    s.at(0.0F).sticks(RADIO, 0.0F, 0.0F, -1.0F, 0.0F, 4.0F);
                    s.at(0.0F).throttle(DRONE, IDLE, 4.0F);
                    s.at(4.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, FLIP_TICKS);
                    left(s, 20.0F, 0.6F);
                    s.at(20.0F).throttle(DRONE, 0.6F, 6.0F);
                    s.at(22.0F).move(DRONE, 34.0F, Ease.IN_OUT, HIGH);
                    left(s, 64.0F, -1.0F);
                    s.at(64.0F).throttle(DRONE, -1.0F, 4.0F);
                    s.at(66.0F).move(DRONE, 18.0F, Ease.IN, LOW);
                    s.at(64.0F).callout("punch", onDrone, Cue.Side.RIGHT, 56.0F);
                    left(s, 86.0F, 0.25F);
                    s.at(86.0F).throttle(DRONE, 0.45F, 6.0F);
                    s.at(88.0F).move(DRONE, 40.0F, Ease.IN_OUT, HOVER);
                    s.at(122.0F).callout("save", leftStick, Cue.Side.LEFT, 0.0F);
                })
                .step("takeoff", 230.0F, s -> {
                    s.at(0.0F).throttle(DRONE, 0.0F, 2.0F);
                    s.at(0.0F).arm(DRONE, false);
                    left(s, 0.0F, 0.0F);
                    s.at(0.0F).move(DRONE, 18.0F, Ease.IN, PAD.add(0.0F, 0.1F, 0.0F));
                    s.at(0.0F).turn(DRONE, 20.0F, 30.0F, 150.0F, 14.0F);
                    s.at(16.0F).turn(DRONE, 20.0F, 0.0F, 180.0F, 6.0F);
                    s.at(24.0F).callout("upside_down", onDrone, Cue.Side.RIGHT, 50.0F);
                    s.at(78.0F).keysAt("arm", Anchor.on(DRONE, 0.0F, 0.6F, 0.0F), 36.0F, KeyToken.mapping(SceneKit.KEY_ARM));
                    s.at(78.0F).callout("arm_center", leftStick, Cue.Side.LEFT, 46.0F);
                    s.at(104.0F).arm(DRONE, true);
                    s.at(104.0F).throttle(DRONE, -IDLE, 4.0F);
                    left(s, 128.0F, -0.7F);
                    s.at(128.0F).throttle(DRONE, -0.7F, 6.0F);
                    s.at(130.0F).move(DRONE, 40.0F, Ease.IN_OUT, HOVER);
                    s.at(130.0F).callout("lift", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(130.0F).turn(DRONE, 0.0F, 0.0F, 180.0F, 20.0F);
                    s.at(186.0F).sticks(RADIO, 0.0F, 0.0F, 1.0F, 0.0F, 4.0F);
                    s.at(186.0F).throttle(DRONE, IDLE, 4.0F);
                    s.at(190.0F).turn(DRONE, 0.0F, 0.0F, 360.0F, FLIP_TICKS);
                    s.at(206.0F).sticks(RADIO, 0.0F, 0.25F, 0.0F, 0.0F, 4.0F);
                    s.at(206.0F).throttle(DRONE, 0.45F, 6.0F);
                })
                .step("switch_off", 160.0F, s -> {
                    s.at(0.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 0.0F);
                    s.at(6.0F).keys("mode_off", 0.0F, KeyToken.mapping(SceneKit.KEY_MODE_3D));
                    left(s, 8.0F, 0.0F);
                    s.at(8.0F).throttle(DRONE, 0.55F, 6.0F);
                    s.at(12.0F).move(DRONE, 46.0F, Ease.IN_OUT, HIGH);
                    s.at(8.0F).callout("off", leftStick, Cue.Side.LEFT, 60.0F);
                    s.at(70.0F).callout("rescue", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(100.0F).callout("rule", radioTop, Cue.Side.ABOVE, 0.0F);
                })
                .build();
    }
}

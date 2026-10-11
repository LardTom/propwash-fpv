package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.AccessoryType;
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

import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_DIGITAL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;

public final class ActionCamScene {
    public static final String ID = "action_cam";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final String CAM = "cam";
    public static final String RADIO = "radio";
    public static final String FLASHBACK_DRONE = "flashback_drone";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY);
    public static final DroneBuild FLASHBACK_BUILD = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_FLASHBACK);
    public static final ScenePos DRONE_POS = new ScenePos(0.6F, 1.0F, 0.5F);
    public static final ScenePos CAM_POS = new ScenePos(-0.9F, 1.7F, 0.9F);
    public static final ScenePos PILOT_POS = new ScenePos(-1.6F, 1.0F, -1.0F);
    public static final ScenePos HOVER = new ScenePos(0.6F, 2.2F, 0.3F);
    public static final ScenePos RADIO_POS = new ScenePos(-0.5F, 1.0F, 1.5F);
    public static final float RADIO_SCALE = 0.9F;
    public static final float RADIO_TILT = 35.0F;
    public static final List<ScenePos> LOOP = List.of(
            new ScenePos(1.6F, 2.5F, -0.6F),
            new ScenePos(1.9F, 2.7F, 0.9F),
            new ScenePos(0.4F, 2.5F, 1.4F),
            new ScenePos(-0.4F, 2.3F, 0.4F),
            new ScenePos(0.6F, 2.2F, 0.3F));

    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 1.8F, 0.2F);
    private static final float OVERVIEW_YAW = 20.0F;

    private ActionCamScene() {
    }

    public static Scene create() {
        Anchor onCam = Anchor.on(DRONE, 0.0F, 0.2F, 0.0F);
        Anchor onItem = Anchor.on(CAM, 0.0F, 0.2F, 0.0F);
        Anchor onFlashback = Anchor.on(FLASHBACK_DRONE, 0.0F, 0.2F, 0.0F);
        Anchor onHead = Anchor.on(PILOT, 0.0F, 1.95F, 0.0F);
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.2F, 0.0F);
        float cos = (float) Math.cos(Math.toRadians(RADIO_TILT));
        float sin = (float) Math.sin(Math.toRadians(RADIO_TILT));
        Anchor radioTop = Anchor.on(RADIO, 0.0F, 0.9F * cos * RADIO_SCALE, -0.9F * sin * RADIO_SCALE);
        ScenePos landing = new ScenePos(0.6F, 1.0F, 0.4F);
        return SceneBuilder.scene(ID)
                .icon(SceneKit.part(AccessoryType.ACTION_CAM_HEAVY))
                .items(SceneKit.part(AccessoryType.ACTION_CAM_LIGHT), SceneKit.part(AccessoryType.ACTION_CAM_HEAVY),
                        SceneKit.part(AccessoryType.ACTION_CAM_FLASHBACK))
                .view(2.4F)
                .camera(OVERVIEW, OVERVIEW_YAW, 22.0F, 1.0F)
                .step("mount", 130.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -3, -3, 3, 3, GRASS, GRASS, 1.0F, 10.0F);
                    s.at(12.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true), DRONE_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(16.0F).camera(DRONE_POS.add(-0.3F, 0.6F, 0.2F), OVERVIEW_YAW, 20.0F, 1.6F, 22.0F);
                    s.at(22.0F).spawn(CAM, new ActorSpec.Item(SceneKit.part(AccessoryType.ACTION_CAM_LIGHT), 1.5F, true), CAM_POS, 0.0F,
                            Cue.Appear.POP, 8.0F);
                    s.at(30.0F).callout("mount", onCam, Cue.Side.RIGHT, 0.0F);
                    s.at(62.0F).callout("types", onItem, Cue.Side.LEFT, 0.0F);
                })
                .step("record", 150.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, OVERVIEW_YAW, 22.0F, 1.0F, 20.0F);
                    s.at(0.0F).despawn(CAM, 8.0F);
                    s.at(6.0F).spawn(PILOT, new ActorSpec.Pilot(GOGGLES_DIGITAL, REMOTE), PILOT_POS, SceneKit.pilotYaw(PILOT_POS, DRONE_POS),
                            Cue.Appear.POP, 10.0F);
                    s.at(6.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, DRONE_POS), 10.0F);
                    s.at(20.0F).arm(DRONE, true);
                    s.at(20.0F).throttle(DRONE, 0.55F, 12.0F);
                    s.at(24.0F).move(DRONE, 24.0F, Ease.IN_OUT, HOVER);
                    s.at(24.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, HOVER), 24.0F);
                    s.at(50.0F).keysAt("record", overHead, 60.0F, KeyToken.mapping(SceneKit.KEY_RECORD));
                    s.at(56.0F).callout("rec", onCam, Cue.Side.RIGHT, 0.0F);
                    s.at(90.0F).callout("clean", onHead, Cue.Side.ABOVE, 0.0F);
                })
                .step("switch", 170.0F, s -> {
                    s.at(0.0F).spawn(RADIO, new ActorSpec.Remote(RADIO_SCALE), RADIO_POS, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(0.0F).turn(RADIO, 0.0F, RADIO_TILT, 0.0F, 0.0F);
                    s.at(12.0F).callout("switch", radioTop, Cue.Side.LEFT, 0.0F);
                    SceneKit.fly(s, DRONE, HOVER, LOOP, Ease.IN_OUT, 20.0F, 120.0F, 24, 12.0F);
                    SceneKit.follow(s, PILOT, PILOT_POS, HOVER, LOOP, Ease.IN_OUT, 20.0F, 120.0F, 24);
                    s.at(70.0F).callout("button", onCam, Cue.Side.RIGHT, 0.0F);
                })
                .step("library", 150.0F, s -> {
                    s.at(0.0F).despawn(RADIO, 8.0F);
                    s.at(0.0F).move(DRONE, 30.0F, Ease.IN_OUT, landing);
                    s.at(0.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 20.0F);
                    s.at(0.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, landing), 24.0F);
                    s.at(18.0F).throttle(DRONE, 0.08F, 12.0F);
                    s.at(32.0F).arm(DRONE, false);
                    s.at(34.0F).callout("saved", onCam, Cue.Side.RIGHT, 0.0F);
                    s.at(70.0F).keysAt("library", overHead, 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                    s.at(76.0F).callout("library", onHead, Cue.Side.ABOVE, 0.0F);
                })
                .step("flashback", 170.0F, s -> {
                    s.at(0.0F).despawn(DRONE, 8.0F);
                    s.at(8.0F).spawn(FLASHBACK_DRONE, new ActorSpec.Drone(FLASHBACK_BUILD, true), landing, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(12.0F).spawn(CAM, new ActorSpec.Item(SceneKit.part(AccessoryType.ACTION_CAM_FLASHBACK), 1.5F, true), CAM_POS, 0.0F,
                            Cue.Appear.POP, 8.0F);
                    s.at(20.0F).callout("flashback", onItem, Cue.Side.LEFT, 0.0F);
                    s.at(70.0F).despawn(CAM, 8.0F);
                    s.at(70.0F).arm(FLASHBACK_DRONE, true);
                    s.at(70.0F).throttle(FLASHBACK_DRONE, 0.55F, 12.0F);
                    s.at(74.0F).move(FLASHBACK_DRONE, 24.0F, Ease.IN_OUT, HOVER);
                    s.at(74.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, HOVER), 24.0F);
                    s.at(96.0F).keysAt("record", overHead, 60.0F, KeyToken.mapping(SceneKit.KEY_RECORD));
                    s.at(100.0F).callout("scene", onFlashback, Cue.Side.RIGHT, 0.0F);
                })
                .build();
    }
}

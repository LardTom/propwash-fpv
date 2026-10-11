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

import java.util.List;

import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.OAK_LEAVES;
import static de.lardtom.propwash.guide.scenes.SceneKit.OAK_LOG;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE_BRICKS;

public final class RescueScene {
    public static final String ID = "rescue";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final DroneBuild BUILD = DroneBuild.LONG_RANGE;
    public static final ScenePos HOME = new ScenePos(-2.5F, 1.06F, 2.5F);
    public static final ScenePos PILOT_POS = new ScenePos(-1.3F, 1.0F, 3.4F);
    public static final ScenePos FAR = new ScenePos(3.4F, 3.0F, -3.0F);
    public static final float ALTITUDE = 6.5F;
    public static final float DRONE_SCALE = 2.0F;

    private static final BlockSpec PAD = BlockSpec.of("minecraft:lime_carpet");
    private static final ScenePos OVERVIEW = new ScenePos(0.4F, 2.8F, 0.1F);
    private static final ScenePos HIGH_VIEW = new ScenePos(0.4F, 3.7F, 0.1F);
    private static final float SIDE_YAW = 54.0F;

    private RescueScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.18F, 0.0F);
        ScenePos climbed = new ScenePos(FAR.x(), ALTITUDE, FAR.z());
        ScenePos aboveHome = new ScenePos(HOME.x(), ALTITUDE, HOME.z());
        List<ScenePos> away = List.of(new ScenePos(-1.4F, 2.6F, 1.3F), new ScenePos(0.8F, 3.1F, -1.2F), FAR);
        List<ScenePos> cruise = List.of(new ScenePos(0.6F, ALTITUDE + 0.1F, -0.3F), aboveHome);
        return SceneBuilder.scene(ID)
                .icon("minecraft:recovery_compass")
                .items(SceneKit.DRONE_ITEM)
                .view(4.3F)
                .camera(OVERVIEW, 30.0F, 30.0F, 1.0F)
                .step("home", 120.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -4, -4, 4, 3, GRASS, GRASS, 0.8F, 10.0F);
                    s.at(14.0F).pop(-3, 1, 2, PAD, 8.0F);
                    for (int y = 1; y <= 3; y++) {
                        s.at(12.0F + y * 2.0F).drop(1, y, -2, OAK_LOG, 8.0F);
                    }
                    for (int x = 0; x <= 2; x++) {
                        for (int z = -3; z <= -1; z++) {
                            s.at(20.0F + (x + z + 3) * 1.0F).pop(x, 4, z, OAK_LEAVES, 8.0F);
                        }
                    }
                    s.at(26.0F).pop(1, 5, -2, OAK_LEAVES, 8.0F);
                    s.at(18.0F).spawn(PILOT, new ActorSpec.Pilot(SceneKit.GOGGLES_DIGITAL, REMOTE), PILOT_POS, SceneKit.pilotYaw(PILOT_POS, FAR),
                            Cue.Appear.POP, 10.0F);
                    s.at(22.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), HOME, 45.0F, Cue.Appear.POP, 10.0F);
                    s.at(0.0F).camera(new ScenePos(-1.8F, 1.9F, 2.0F), 30.0F, 30.0F, 1.7F, 30.0F);
                    s.at(34.0F).keys("arm", 40.0F, KeyToken.mapping(SceneKit.KEY_ARM));
                    s.at(44.0F).arm(DRONE, true);
                    s.at(44.0F).throttle(DRONE, 0.15F, 8.0F);
                    s.at(46.0F).highlight(new ScenePos(-3.0F, 1.0F, 2.0F), new ScenePos(-2.0F, 1.35F, 3.0F), 60.0F);
                    s.at(48.0F).callout("home", Anchor.at(HOME.add(0.0F, 0.02F, 0.0F)), Cue.Side.LEFT, 0.0F);
                    s.at(78.0F).keys("set_home", 0.0F, KeyToken.mapping(SceneKit.KEY_SET_HOME));
                })
                .step("away", 120.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 30.0F, 30.0F, 1.15F, 30.0F);
                    s.at(0.0F).throttle(DRONE, 0.6F, 10.0F);
                    SceneKit.fly(s, DRONE, HOME, away, Ease.IN_OUT, 4.0F, 86.0F, 18, 18.0F);
                    s.at(24.0F).callout("away", onDrone, Cue.Side.RIGHT, 50.0F);
                    s.at(76.0F).callout("rescue_key", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(76.0F).keys("rescue", 0.0F, KeyToken.mapping(SceneKit.KEY_RESCUE));
                })
                .step("failsafe", 110.0F, s -> {
                    ScenePos middle = new ScenePos((PILOT_POS.x() + FAR.x()) * 0.5F, 2.4F, (PILOT_POS.z() + FAR.z()) * 0.5F);
                    s.at(0.0F).camera(middle, SIDE_YAW, 24.0F, 1.3F, 24.0F);
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 1; y <= 3; y++) {
                            s.at((x + 1) * 3.0F + y * 2.0F).pop(x, y, 0, STONE_BRICKS, 8.0F);
                        }
                    }
                    s.at(14.0F).highlight(new ScenePos(-1.0F, 1.0F, 0.0F), new ScenePos(2.0F, 4.0F, 1.0F), 40.0F);
                    s.at(16.0F).callout("blocked", Anchor.at(new ScenePos(0.5F, 4.1F, 0.5F)), Cue.Side.ABOVE, 0.0F);
                    s.at(44.0F).callout("failsafe", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(20.0F).move(DRONE, 60.0F, Ease.IN_OUT, FAR.add(0.1F, 0.15F, 0.1F), FAR);
                })
                .step("return", 190.0F, s -> {
                    s.at(0.0F).camera(HIGH_VIEW, 30.0F, 30.0F, 1.15F, 30.0F);
                    s.at(40.0F).camera(new ScenePos(1.0F, 3.8F, -0.8F), 30.0F, 30.0F, 1.2F, 30.0F);
                    s.at(72.0F).camera(new ScenePos(-0.4F, 3.6F, 0.9F), 30.0F, 30.0F, 1.2F, 36.0F);
                    s.at(110.0F).camera(new ScenePos(-1.6F, 3.8F, 1.8F), 30.0F, 30.0F, 1.45F, 24.0F);
                    s.at(0.0F).move(DRONE, 40.0F, Ease.IN_OUT, climbed);
                    s.at(0.0F).turn(DRONE, SceneKit.heading(FAR, HOME), 0.0F, 0.0F, 24.0F);
                    s.at(4.0F).callout("climb", onDrone, Cue.Side.RIGHT, 38.0F);
                    s.at(8.0F).callout("pad", Anchor.at(HOME.add(0.0F, 0.02F, 0.0F)), Cue.Side.RIGHT, 0.0F);
                    SceneKit.fly(s, DRONE, climbed, cruise, Ease.IN_OUT, 44.0F, 90.0F, 18, 16.0F);
                    s.at(46.0F).callout("cruise", onDrone, Cue.Side.RIGHT, 86.0F);
                    s.at(70.0F).callout("alone", Anchor.on(PILOT, 0.0F, 1.9F, 0.0F), Cue.Side.LEFT, 60.0F);
                    s.at(134.0F).camera(new ScenePos(-1.6F, 2.6F, 1.8F), 30.0F, 30.0F, 1.45F, 40.0F);
                    s.at(136.0F).move(DRONE, 40.0F, Ease.IN_OUT, HOME);
                    s.at(136.0F).throttle(DRONE, 0.3F, 20.0F);
                    s.at(138.0F).callout("land", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(176.0F).throttle(DRONE, 0.0F, 6.0F);
                    s.at(178.0F).arm(DRONE, false);
                })
                .step("settings", 120.0F, s -> {
                    s.at(4.0F).callout("override", onDrone, Cue.Side.RIGHT, 56.0F);
                    s.at(62.0F).callout("settings", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(66.0F).keys("configure", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                })
                .build();
    }
}

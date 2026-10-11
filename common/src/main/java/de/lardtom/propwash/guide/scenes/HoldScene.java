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
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE;
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE_BRICKS;

public final class HoldScene {
    public static final String ID = "hold";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final float DRONE_SCALE = 2.0F;
    public static final ScenePos PAD = new ScenePos(-1.5F, 1.06F, 1.5F);
    public static final ScenePos PILOT_POS = new ScenePos(-3.4F, 1.0F, 3.4F);
    public static final ScenePos HOVER = new ScenePos(-0.5F, 2.8F, 0.5F);
    public static final ScenePos HIGH = new ScenePos(-0.5F, 4.0F, 0.5F);
    public static final ScenePos CAVE = new ScenePos(2.5F, 2.2F, -2.0F);

    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 2.6F, 0.0F);

    private HoldScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.18F, 0.0F);
        return SceneBuilder.scene(ID)
                .icon("minecraft:compass")
                .items(SceneKit.DRONE_ITEM, REMOTE)
                .view(4.3F)
                .camera(OVERVIEW, 30.0F, 30.0F, 1.0F)
                .step("althold", 160.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -4, -4, 4, 3, GRASS, GRASS, 0.8F, 10.0F);
                    s.at(16.0F).spawn(PILOT, new ActorSpec.Pilot(SceneKit.GOGGLES_ANALOG, REMOTE), PILOT_POS,
                            SceneKit.pilotYaw(PILOT_POS, HOVER), Cue.Appear.POP, 10.0F);
                    s.at(20.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), PAD, 45.0F, Cue.Appear.POP, 10.0F);
                    s.at(30.0F).arm(DRONE, true);
                    s.at(30.0F).throttle(DRONE, 0.45F, 8.0F);
                    s.at(32.0F).move(DRONE, 30.0F, Ease.IN_OUT, HOVER);
                    s.at(62.0F).throttle(DRONE, 0.35F, 6.0F);
                    s.at(64.0F).keys("alt_hold", 0.0F, KeyToken.mapping(SceneKit.KEY_ALT_HOLD));
                    s.at(66.0F).callout("baro", onDrone, Cue.Side.RIGHT, 50.0F);
                    s.at(70.0F).move(DRONE, 90.0F, Ease.LINEAR, HOVER.add(0.0F, 0.18F, 0.0F), HOVER.add(0.0F, -0.12F, 0.0F),
                            HOVER.add(0.0F, 0.1F, 0.0F), HOVER.add(0.0F, -0.08F, 0.0F), HOVER);
                    s.at(118.0F).callout("bobbing", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("climb", 140.0F, s -> {
                    s.at(4.0F).callout("stick_mid", onDrone, Cue.Side.RIGHT, 44.0F);
                    s.at(50.0F).throttle(DRONE, 0.6F, 8.0F);
                    s.at(52.0F).move(DRONE, 60.0F, Ease.IN_OUT, HIGH);
                    s.at(52.0F).callout("climb", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(114.0F).throttle(DRONE, 0.35F, 6.0F);
                })
                .step("poshold", 200.0F, s -> {
                    s.at(0.0F).keys("pos_hold", 0.0F, KeyToken.mapping(SceneKit.KEY_POS_HOLD));
                    s.at(4.0F).callout("gps", onDrone, Cue.Side.RIGHT, 56.0F);
                    s.at(60.0F).move(DRONE, 40.0F, Ease.IN_OUT, HIGH.add(0.6F, 0.0F, -0.2F));
                    s.at(60.0F).turn(DRONE, 45.0F, 0.0F, 14.0F, 12.0F);
                    s.at(62.0F).callout("wind", onDrone, Cue.Side.RIGHT, 60.0F);
                    s.at(100.0F).turn(DRONE, 45.0F, 0.0F, -8.0F, 12.0F);
                    s.at(100.0F).move(DRONE, 70.0F, Ease.IN_OUT, HIGH.add(0.25F, 0.05F, 0.15F), HIGH.add(-0.1F, 0.0F, 0.2F), HIGH);
                    s.at(170.0F).turn(DRONE, 45.0F, 0.0F, 0.0F, 10.0F);
                    s.at(124.0F).callout("circle", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("no_gps", 210.0F, s -> {
                    for (int y = 1; y <= 3; y++) {
                        s.at(y * 3.0F).drop(3, y, -3, STONE_BRICKS, 8.0F);
                        s.at(2.0F + y * 3.0F).drop(3, y, -1, STONE_BRICKS, 8.0F);
                    }
                    for (int x = 1; x <= 3; x++) {
                        for (int z = -3; z <= -1; z++) {
                            s.at(14.0F + (x + z + 3) * 2.0F).drop(x, 4, z, STONE, 8.0F);
                        }
                    }
                    s.at(24.0F).highlight(new ScenePos(1.0F, 4.0F, -3.0F), new ScenePos(4.0F, 5.0F, 0.0F), 50.0F);
                    s.at(30.0F).move(DRONE, 60.0F, Ease.IN_OUT, CAVE.add(-1.2F, 0.4F, 1.0F), CAVE);
                    s.at(30.0F).turn(DRONE, 135.0F, 0.0F, 0.0F, 20.0F);
                    s.at(28.0F).callout("roof", Anchor.at(new ScenePos(2.5F, 5.05F, -2.0F)), Cue.Side.ABOVE, 70.0F);
                    s.at(100.0F).callout("no_gps", onDrone, Cue.Side.LEFT, 0.0F);
                    s.at(104.0F).move(DRONE, 100.0F, Ease.LINEAR, CAVE.add(0.3F, 0.05F, 0.2F), CAVE.add(-0.2F, -0.05F, 0.4F),
                            CAVE.add(-0.4F, 0.0F, 0.1F));
                })
                .step("limits", 150.0F, s -> {
                    s.at(0.0F).move(DRONE, 50.0F, Ease.IN_OUT, CAVE.add(-1.5F, 0.6F, 1.5F), HOVER);
                    s.at(0.0F).turn(DRONE, 45.0F, 0.0F, 0.0F, 20.0F);
                    s.at(8.0F).callout("never_perfect", onDrone, Cue.Side.RIGHT, 70.0F);
                    s.at(80.0F).callout("tuning", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(84.0F).keys("configure", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                    s.at(110.0F).move(DRONE, 36.0F, Ease.IN_OUT, PAD);
                    s.at(110.0F).throttle(DRONE, 0.2F, 20.0F);
                    s.at(146.0F).throttle(DRONE, 0.0F, 4.0F);
                    s.at(148.0F).arm(DRONE, false);
                })
                .build();
    }
}

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
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE_BRICKS;

public final class RecoveryScene {
    public static final String ID = "recovery";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final ScenePos PILOT_POS = new ScenePos(-1.9F, 1.0F, 2.4F);
    public static final ScenePos HOVER = new ScenePos(-1.0F, 2.4F, 1.0F);
    public static final ScenePos IMPACT = new ScenePos(0.78F, 2.3F, -0.5F);
    public static final ScenePos WRECK = new ScenePos(0.25F, 1.0F, -0.1F);
    public static final ScenePos HIDDEN = new ScenePos(2.4F, 2.0F, -1.7F);
    public static final ScenePos LOST = new ScenePos(2.4F, 1.0F, -1.7F);
    public static final ScenePos AIR_START = WRECK.add(-0.3F, 2.2F, 0.6F);
    public static final ScenePos AIR_LOW = AIR_START.add(0.2F, -0.6F, 0.1F);

    private static final ScenePos OVERVIEW = new ScenePos(0.2F, 2.0F, 0.3F);
    private static final BlockSpec[] SIGNAL = {
            BlockSpec.of("minecraft:lime_concrete"),
            BlockSpec.of("minecraft:lime_concrete"),
            BlockSpec.of("minecraft:yellow_concrete"),
            BlockSpec.of("minecraft:orange_concrete"),
            BlockSpec.of("minecraft:red_concrete")};

    private RecoveryScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.18F, 0.0F);
        float crashYaw = SceneKit.heading(HOVER, IMPACT);
        ScenePos pillarTop = new ScenePos(1.5F, 4.05F, -0.5F);
        return SceneBuilder.scene(ID)
                .icon(SceneKit.DRONE_ITEM)
                .items(REMOTE)
                .view(2.8F)
                .camera(OVERVIEW, 34.0F, 26.0F, 1.0F)
                .step("crash", 120.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -3, -3, 3, 3, GRASS, GRASS, 1.0F, 10.0F);
                    for (int y = 1; y <= 3; y++) {
                        s.at(10.0F + y * 3.0F).drop(1, y, -1, STONE_BRICKS, 8.0F);
                    }
                    s.at(14.0F).spawn(PILOT, new ActorSpec.Pilot(SceneKit.GOGGLES_ANALOG, REMOTE), PILOT_POS,
                            SceneKit.pilotYaw(PILOT_POS, IMPACT), Cue.Appear.POP, 10.0F);
                    s.at(18.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true), HOVER, crashYaw, Cue.Appear.POP, 8.0F);
                    s.at(18.0F).arm(DRONE, true);
                    s.at(18.0F).throttle(DRONE, 0.6F, 6.0F);
                    s.at(28.0F).turn(DRONE, crashYaw, -22.0F, 0.0F, 8.0F);
                    s.at(30.0F).move(DRONE, 22.0F, Ease.IN, IMPACT);
                    s.at(52.0F).arm(DRONE, false);
                    s.at(52.0F).throttle(DRONE, 0.0F, 0.0F);
                    s.at(52.0F).move(DRONE, 20.0F, Ease.IN, IMPACT.add(-0.35F, -0.2F, 0.2F), WRECK);
                    s.at(52.0F).turn(DRONE, crashYaw + 40.0F, 25.0F, 180.0F, 18.0F);
                    s.at(70.0F).turn(DRONE, crashYaw + 40.0F, 0.0F, 180.0F, 6.0F);
                    s.at(56.0F).highlight(IMPACT.add(-0.1F, -0.3F, -0.3F), IMPACT.add(0.22F, 0.3F, 0.3F), 14.0F);
                    s.at(62.0F).callout("crash", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(0.0F).camera(new ScenePos(0.1F, 1.9F, 0.2F), 34.0F, 24.0F, 1.3F, 40.0F);
                })
                .step("flip", 160.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(0.1F, 1.6F, 0.0F), 40.0F, 22.0F, 1.7F, 24.0F);
                    s.at(6.0F).keysAt("flip", Anchor.on(DRONE, 0.0F, 0.55F, 0.0F), 44.0F, KeyToken.mapping(SceneKit.KEY_FLIP));
                    s.at(8.0F).callout("flip", onDrone, Cue.Side.RIGHT, 60.0F);
                    s.at(24.0F).move(DRONE, 10.0F, Ease.OUT, WRECK.add(0.0F, 0.4F, 0.0F));
                    s.at(34.0F).move(DRONE, 10.0F, Ease.IN, WRECK);
                    s.at(24.0F).turn(DRONE, crashYaw + 40.0F, 0.0F, 360.0F, 18.0F);
                    s.at(44.0F).turn(DRONE, crashYaw + 40.0F, 0.0F, 0.0F, 0.0F);
                    s.at(76.0F).keysAt("arm", Anchor.on(DRONE, 0.0F, 0.55F, 0.0F), 40.0F, KeyToken.mapping(SceneKit.KEY_ARM));
                    s.at(76.0F).callout("arm", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(100.0F).arm(DRONE, true);
                    s.at(100.0F).throttle(DRONE, 0.55F, 12.0F);
                    s.at(110.0F).move(DRONE, 30.0F, Ease.IN_OUT, AIR_START);
                })
                .step("air", 150.0F, s -> {
                    s.at(0.0F).camera(AIR_LOW.add(0.0F, -0.7F, 0.0F), 30.0F, 12.0F, 1.15F, 24.0F);
                    s.at(10.0F).arm(DRONE, false);
                    s.at(10.0F).throttle(DRONE, 0.0F, 0.0F);
                    s.at(10.0F).move(DRONE, 26.0F, Ease.IN, AIR_LOW);
                    s.at(10.0F).turn(DRONE, crashYaw + 40.0F, 30.0F, 140.0F, 26.0F);
                    s.at(16.0F).callout("air_off", onDrone, Cue.Side.RIGHT, 30.0F);
                    s.at(40.0F).keysAt("arm", Anchor.on(DRONE, 0.0F, 0.55F, 0.0F), 50.0F, KeyToken.mapping(SceneKit.KEY_ARM));
                    s.at(44.0F).callout("air_arm", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(52.0F).arm(DRONE, true);
                    s.at(52.0F).throttle(DRONE, 0.6F, 6.0F);
                    s.at(52.0F).turn(DRONE, crashYaw + 40.0F, 0.0F, 0.0F, 12.0F);
                    s.at(56.0F).move(DRONE, 40.0F, Ease.OUT, AIR_START.add(0.3F, 0.15F, 0.0F));
                })
                .step("range", 170.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW.add(0.3F, 0.0F, 0.6F), 20.0F, 30.0F, 1.0F, 24.0F);
                    for (int i = 0; i < SIGNAL.length; i++) {
                        s.at(4.0F + i * 4.0F).pop(i - 1, 0, 2, SIGNAL[i], 8.0F);
                    }
                    ScenePos startLine = new ScenePos(-0.5F, 2.2F, 2.5F);
                    s.at(0.0F).move(DRONE, 24.0F, Ease.IN_OUT, startLine);
                    s.at(0.0F).turn(DRONE, 90.0F, 0.0F, 0.0F, 20.0F);
                    SceneKit.fly(s, DRONE, startLine, List.of(new ScenePos(1.5F, 2.3F, 2.5F), new ScenePos(3.5F, 2.4F, 2.5F)), Ease.IN_OUT, 30.0F,
                            110.0F, 12, 12.0F);
                    s.at(34.0F).callout("full", Anchor.at(new ScenePos(0.0F, 1.1F, 2.5F)), Cue.Side.BELOW, 0.0F);
                    s.at(74.0F).callout("fade", Anchor.at(new ScenePos(2.5F, 1.1F, 2.5F)), Cue.Side.ABOVE, 0.0F);
                    s.at(126.0F).callout("lost", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("walls", 140.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 34.0F, 26.0F, 1.0F, 24.0F);
                    SceneKit.fly(s, DRONE, new ScenePos(3.5F, 2.4F, 2.5F), List.of(new ScenePos(3.2F, 2.2F, 0.4F), HIDDEN), Ease.IN_OUT, 0.0F, 50.0F,
                            10, 12.0F);
                    s.at(50.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 10.0F);
                    s.at(40.0F).highlight(new ScenePos(1.0F, 1.0F, -1.0F), new ScenePos(2.0F, 4.0F, 0.0F), 40.0F);
                    s.at(42.0F).callout("walls", Anchor.at(pillarTop), Cue.Side.ABOVE, 0.0F);
                    s.at(60.0F).callout("video", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("beeper", 150.0F, s -> {
                    s.at(0.0F).move(DRONE, 30.0F, Ease.IN_OUT, LOST);
                    s.at(20.0F).throttle(DRONE, 0.0F, 10.0F);
                    s.at(30.0F).arm(DRONE, false);
                    s.at(20.0F).camera(new ScenePos(1.0F, 1.8F, -0.2F), 60.0F, 30.0F, 1.5F, 30.0F);
                    s.at(36.0F).keys("beeper", 0.0F, KeyToken.mapping(SceneKit.KEY_BEEPER));
                    s.at(40.0F).callout("beeper", onDrone, Cue.Side.RIGHT, 0.0F);
                    for (int i = 0; i < 8; i++) {
                        float size = 0.28F;
                        s.at(44.0F + i * 12.0F).highlight(LOST.add(-size, 0.0F, -size), LOST.add(size, 0.3F, size), 8.0F);
                    }
                })
                .build();
    }
}

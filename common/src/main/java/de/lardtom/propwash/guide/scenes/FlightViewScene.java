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

import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_ANALOG;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_CREATIVE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_DIGITAL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.OAK_LEAVES;
import static de.lardtom.propwash.guide.scenes.SceneKit.OAK_LOG;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;

public final class FlightViewScene {
    public static final String ID = "fpv_los";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final ScenePos PILOT_POS = new ScenePos(2.4F, 1.0F, 2.4F);
    public static final ScenePos START = new ScenePos(0.3F, 1.0F, 1.0F);
    public static final ScenePos HOVER = new ScenePos(0.3F, 2.3F, 0.8F);
    public static final ScenePos FAR = new ScenePos(0.3F, 2.5F, -1.4F);
    public static final List<ScenePos> LOOP = List.of(
            new ScenePos(1.4F, 2.8F, -2.0F),
            new ScenePos(2.6F, 3.0F, -0.5F),
            new ScenePos(1.7F, 2.7F, 0.9F),
            new ScenePos(-0.3F, 2.5F, 1.4F),
            new ScenePos(-1.5F, 2.8F, 0.6F),
            new ScenePos(0.3F, 2.4F, 0.2F));
    public static final float LOOP_START = 16.0F;
    public static final float LOOP_LENGTH = 150.0F;

    private static final ScenePos OVERVIEW = new ScenePos(0.3F, 2.0F, 0.3F);
    private static final float OVERVIEW_YAW = -26.0F;
    private static final float CHASE_YAW = -14.0F;

    private FlightViewScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.16F, 0.0F);
        Anchor onHead = Anchor.on(PILOT, 0.0F, 1.95F, 0.0F);
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.2F, 0.0F);
        ScenePos loopEnd = LOOP.getLast();
        ScenePos landing = new ScenePos(0.3F, 1.0F, 0.9F);
        return SceneBuilder.scene(ID)
                .icon(GOGGLES_DIGITAL)
                .items(REMOTE, GOGGLES_ANALOG, GOGGLES_CREATIVE, SceneKit.DRONE_ITEM)
                .view(2.7F)
                .camera(OVERVIEW, OVERVIEW_YAW, 26.0F, 1.0F)
                .step("fpv", 150.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -3, -3, 3, 3, GRASS, GRASS, 1.0F, 10.0F);
                    s.at(12.0F).drop(-2, 1, -2, OAK_LOG, 8.0F);
                    s.at(14.0F).drop(-2, 2, -2, OAK_LOG, 8.0F);
                    for (int x = -3; x <= -1; x++) {
                        for (int z = -3; z <= -1; z++) {
                            s.at(16.0F + (x + z + 6) * 1.0F).pop(x, 3, z, OAK_LEAVES, 8.0F);
                        }
                    }
                    s.at(24.0F).pop(-2, 4, -2, OAK_LEAVES, 8.0F);
                    s.at(14.0F).spawn(PILOT, new ActorSpec.Pilot(GOGGLES_DIGITAL, REMOTE), PILOT_POS, SceneKit.pilotYaw(PILOT_POS, START),
                            Cue.Appear.POP, 10.0F);
                    s.at(14.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, HOVER), 10.0F);
                    s.at(16.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true), START, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(26.0F).callout("fpv", onHead, Cue.Side.ABOVE, 24.0F);
                    s.at(32.0F).arm(DRONE, true);
                    s.at(32.0F).throttle(DRONE, 0.55F, 12.0F);
                    s.at(36.0F).move(DRONE, 20.0F, Ease.IN_OUT, HOVER);
                    s.at(50.0F).camera(HOVER.add(0.0F, 0.3F, -0.6F), CHASE_YAW, 12.0F, 2.8F, 16.0F);
                    s.at(62.0F).turn(DRONE, 0.0F, -18.0F, 0.0F, 10.0F);
                    s.at(66.0F).move(DRONE, 56.0F, Ease.IN_OUT, FAR);
                    s.at(66.0F).camera(FAR.add(0.0F, 0.3F, -0.6F), CHASE_YAW, 12.0F, 2.8F, 56.0F);
                    s.at(70.0F).callout("osd", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(112.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 12.0F);
                })
                .step("los", 190.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, OVERVIEW_YAW, 26.0F, 1.0F, 20.0F);
                    s.at(0.0F).equip(PILOT, Cue.Slot.HEAD, "");
                    s.at(18.0F).callout("switch", onHead, Cue.Side.ABOVE, 40.0F);
                    SceneKit.fly(s, DRONE, FAR, LOOP, Ease.IN_OUT, LOOP_START, LOOP_LENGTH, 30, 16.0F);
                    SceneKit.follow(s, PILOT, PILOT_POS, FAR, LOOP, Ease.IN_OUT, LOOP_START, LOOP_LENGTH, 30);
                    s.at(64.0F).callout("follow", onHead, Cue.Side.ABOVE, 0.0F);
                    s.at(62.0F).highlight(PILOT_POS.add(-0.4F, 0.0F, -0.4F), PILOT_POS.add(0.4F, 2.0F, 0.4F), 40.0F);
                })
                .step("hud", 110.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(1.3F, 2.2F, 1.3F), OVERVIEW_YAW, 16.0F, 1.5F, 20.0F);
                    s.at(0.0F).move(DRONE, 30.0F, Ease.IN_OUT, loopEnd.add(0.0F, 0.2F, -0.2F));
                    s.at(0.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, loopEnd.add(0.0F, 0.2F, -0.2F)), 20.0F);
                    s.at(6.0F).callout("hud", Anchor.on(PILOT, 0.0F, 1.2F, 0.0F), Cue.Side.LEFT, 0.0F);
                    s.at(40.0F).callout("locked", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("stop", 110.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW.add(0.4F, 0.0F, 0.4F), OVERVIEW_YAW, 24.0F, 1.1F, 20.0F);
                    s.at(0.0F).move(DRONE, 34.0F, Ease.IN_OUT, landing);
                    s.at(0.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 20.0F);
                    s.at(0.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, landing), 30.0F);
                    s.at(20.0F).throttle(DRONE, 0.08F, 14.0F);
                    s.at(36.0F).arm(DRONE, false);
                    s.at(38.0F).callout("stop", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(42.0F).keysAt("stop", overHead, 0.0F, KeyToken.mapping(SceneKit.KEY_EXIT));
                    s.at(42.0F).keys("stop_click", 0.0F, KeyToken.use());
                })
                .build();
    }
}

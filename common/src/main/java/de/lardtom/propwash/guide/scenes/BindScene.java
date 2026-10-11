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

import static de.lardtom.propwash.guide.scenes.SceneKit.BARRIER;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_ANALOG;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_CREATIVE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_DIGITAL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.WORKBENCH;

public final class BindScene {
    public static final String ID = "bind";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final ScenePos DRONE_POS = new ScenePos(0.9F, 1.0F, -0.1F);
    public static final ScenePos PILOT_POS = new ScenePos(-0.7F, 1.0F, 1.5F);

    private static final String ICON_GOGGLES = "icon_goggles";
    private static final String ICON_WRONG = "icon_wrong";
    private static final String ICON_NO = "icon_no";
    private static final String ICON_BENCH = "icon_bench";
    private static final ScenePos OVERVIEW = new ScenePos(0.1F, 1.7F, 0.6F);

    private BindScene() {
    }

    public static Scene create() {
        float facing = SceneKit.pilotYaw(PILOT_POS, DRONE_POS);
        float down = SceneKit.headPitch(PILOT_POS, DRONE_POS.add(0.0F, 0.1F, 0.0F));
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.14F, 0.0F);
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.15F, 0.0F);
        ScenePos droneBox = DRONE_POS;
        return SceneBuilder.scene(ID)
                .icon(REMOTE)
                .items(GOGGLES_ANALOG, GOGGLES_DIGITAL, GOGGLES_CREATIVE, SceneKit.DRONE_ITEM)
                .view(2.3F)
                .camera(OVERVIEW, 48.0F, 22.0F, 1.15F)
                .step("remote", 120.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -2, -2, 2, 2, GRASS, GRASS, 1.5F, 10.0F);
                    s.at(16.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true), DRONE_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(20.0F).spawn(PILOT, new ActorSpec.Pilot("", REMOTE), PILOT_POS, facing, Cue.Appear.POP, 10.0F);
                    s.at(22.0F).look(PILOT, down, 12.0F);
                    s.at(34.0F).callout("remote", onDrone, Cue.Side.RIGHT, 34.0F);
                    s.at(38.0F).keysAt("bind_remote", overHead, 0.0F, KeyToken.use());
                    s.at(64.0F).highlight(droneBox.add(-0.32F, 0.0F, -0.32F), droneBox.add(0.32F, 0.3F, 0.32F), 26.0F);
                    s.at(70.0F).callout("remote_done", Anchor.on(PILOT, 0.0F, 1.1F, 0.0F), Cue.Side.LEFT, 0.0F);
                })
                .step("wear", 110.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(-0.5F, 2.1F, 1.2F), 40.0F, 14.0F, 1.7F, 22.0F);
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, GOGGLES_DIGITAL);
                    s.at(0.0F).look(PILOT, -12.0F, 12.0F);
                    s.at(8.0F).callout("no_bind", Anchor.on(PILOT, 0.0F, 1.1F, 0.0F), Cue.Side.LEFT, 44.0F);
                    s.at(12.0F).keysAt("wear", overHead, 0.0F, KeyToken.use());
                    s.at(34.0F).equip(PILOT, Cue.Slot.HEAD, GOGGLES_DIGITAL);
                    s.at(34.0F).equip(PILOT, Cue.Slot.HAND, REMOTE);
                    s.at(58.0F).callout("wear", Anchor.on(PILOT, 0.0F, 1.8F, 0.0F), Cue.Side.LEFT, 0.0F);
                })
                .step("match", 150.0F, s -> {
                    ScenePos icon = DRONE_POS.add(0.0F, 0.72F, 0.0F);
                    s.at(0.0F).camera(OVERVIEW, 48.0F, 22.0F, 1.15F, 22.0F);
                    s.at(0.0F).look(PILOT, down, 12.0F);
                    s.at(8.0F).spawn(ICON_GOGGLES, new ActorSpec.Item(GOGGLES_DIGITAL, 1.5F, true), icon, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(14.0F).callout("system", onDrone, Cue.Side.RIGHT, 34.0F);
                    s.at(50.0F).despawn(ICON_GOGGLES, 8.0F);
                    s.at(56.0F).equip(PILOT, Cue.Slot.HEAD, GOGGLES_ANALOG);
                    s.at(60.0F).spawn(ICON_WRONG, new ActorSpec.Item(GOGGLES_ANALOG, 1.5F, true), icon, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(62.0F).spawn(ICON_NO, new ActorSpec.Item(BARRIER, 1.1F, false), icon.add(0.0F, 0.56F, 0.0F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(66.0F).callout("mismatch", onDrone, Cue.Side.RIGHT, 34.0F);
                    s.at(102.0F).despawn(ICON_WRONG, 8.0F);
                    s.at(102.0F).despawn(ICON_NO, 8.0F);
                    s.at(106.0F).equip(PILOT, Cue.Slot.HEAD, "");
                    s.at(110.0F).callout("los", Anchor.on(PILOT, 0.0F, 1.8F, 0.0F), Cue.Side.LEFT, 30.0F);
                    s.at(142.0F).equip(PILOT, Cue.Slot.HEAD, GOGGLES_DIGITAL);
                })
                .step("keep", 110.0F, s -> {
                    ScenePos bench = DRONE_POS.add(-0.42F, 0.7F, 0.0F);
                    s.at(4.0F).spawn(ICON_BENCH, new ActorSpec.Item(WORKBENCH, 1.5F, true), bench, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(12.0F).move(DRONE, 18.0F, Ease.IN_OUT, DRONE_POS.add(-0.2F, 0.45F, 0.0F));
                    s.at(34.0F).move(DRONE, 18.0F, Ease.IN_OUT, DRONE_POS);
                    s.at(54.0F).callout("keep", onDrone, Cue.Side.RIGHT, 26.0F);
                    s.at(82.0F).callout("takeover", Anchor.on(PILOT, 0.0F, 1.1F, 0.0F), Cue.Side.LEFT, 0.0F);
                    s.at(98.0F).despawn(ICON_BENCH, 10.0F);
                })
                .step("start", 120.0F, s -> {
                    s.at(0.0F).look(PILOT, -6.0F, 12.0F);
                    s.at(4.0F).keysAt("start", overHead, 34.0F, KeyToken.use());
                    s.at(12.0F).arm(DRONE, true);
                    s.at(12.0F).throttle(DRONE, 0.12F, 8.0F);
                    s.at(22.0F).throttle(DRONE, 0.55F, 14.0F);
                    s.at(22.0F).move(DRONE, 30.0F, Ease.IN_OUT, DRONE_POS.add(0.0F, 1.1F, 0.0F));
                    s.at(22.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, DRONE_POS.add(0.0F, 1.1F, 0.0F)), 20.0F);
                    s.at(26.0F).camera(OVERVIEW.add(0.0F, 0.4F, 0.0F), 48.0F, 18.0F, 1.1F, 26.0F);
                    s.at(30.0F).callout("fpv", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(56.0F).move(DRONE, 50.0F, Ease.IN_OUT, DRONE_POS.add(0.25F, 1.35F, -0.2F), DRONE_POS.add(-0.1F, 1.2F, 0.1F));
                    s.at(56.0F).turn(DRONE, 150.0F, 4.0F, -6.0F, 50.0F);
                })
                .build();
    }
}

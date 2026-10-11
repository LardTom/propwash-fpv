package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.AccessoryType;
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

public final class GripperScene {
    public static final String ID = "gripper";
    public static final String DRONE = "drone";
    public static final String GRIPPER = "gripper";
    public static final String CARGO = "cargo";
    public static final String CARGO_SECOND = "cargo_second";
    public static final String CARGO_LAST = "cargo_last";
    public static final String CARGO_ITEM = "minecraft:iron_ingot";
    public static final String CARGO_SECOND_ITEM = "minecraft:apple";
    public static final String CARGO_LAST_ITEM = "minecraft:gold_ingot";
    public static final BlockSpec HOPPER = BlockSpec.of("minecraft:hopper");
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE.withAccessory(AccessoryType.GRIPPER);
    public static final float DRONE_SCALE = 2.0F;
    public static final ScenePos DRONE_POS = new ScenePos(0.3F, 1.0F, 0.1F);
    public static final ScenePos ITEM_POS = new ScenePos(-1.0F, 1.7F, 0.7F);
    public static final ScenePos HOVER = new ScenePos(0.3F, 2.0F, 0.1F);
    public static final ScenePos CARGO_POS = new ScenePos(1.25F, 1.0F, 1.05F);
    public static final ScenePos CARGO_SECOND_POS = new ScenePos(1.55F, 1.0F, 1.3F);
    public static final ScenePos OVER_CARGO = new ScenePos(1.4F, 1.45F, 1.17F);
    public static final int HOPPER_X = -2;
    public static final int HOPPER_Z = 1;
    public static final ScenePos OVER_HOPPER = new ScenePos(HOPPER_X + 0.5F, 2.75F, HOPPER_Z + 0.5F);
    public static final ScenePos INTO_HOPPER = new ScenePos(HOPPER_X + 0.5F, 2.05F, HOPPER_Z + 0.5F);
    public static final ScenePos LANDING = new ScenePos(0.0F, 1.0F, 0.4F);
    public static final ScenePos HANG = new ScenePos(0.0F, -0.1F, 0.0F);
    public static final ScenePos BESIDE = new ScenePos(0.25F, 0.0F, 0.15F);
    public static final float CARGO_SCALE = 0.55F;

    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 1.6F, 0.2F);
    private static final float OVERVIEW_YAW = 25.0F;
    private static final float CLOSE_ZOOM = 1.6F;

    private GripperScene() {
    }

    public static Scene create() {
        Anchor belly = Anchor.on(DRONE, 0.0F, 0.04F, -0.08F);
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.12F, 0.0F);
        Anchor onItem = Anchor.on(GRIPPER, 0.0F, 0.2F, 0.0F);
        Anchor onCargo = Anchor.on(CARGO, 0.0F, 0.05F, 0.0F);
        List<ScenePos> toCargo = List.of(new ScenePos(0.9F, 2.1F, 0.6F), new ScenePos(1.3F, 1.8F, 1.0F), OVER_CARGO);
        List<ScenePos> toHopper = List.of(new ScenePos(0.6F, 2.4F, 1.7F), new ScenePos(-0.6F, 2.7F, 1.8F), OVER_HOPPER);
        return SceneBuilder.scene(ID)
                .icon(SceneKit.part(AccessoryType.GRIPPER))
                .items(SceneKit.part(AccessoryType.GRIPPER))
                .view(2.6F)
                .camera(OVERVIEW, OVERVIEW_YAW, 24.0F, 1.0F)
                .step("mount", 120.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -3, -3, 3, 3, GRASS, GRASS, 1.0F, 10.0F);
                    s.at(12.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true, DRONE_SCALE), DRONE_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(16.0F).camera(DRONE_POS.add(-0.3F, 0.5F, 0.2F), OVERVIEW_YAW, 12.0F, 1.6F, 22.0F);
                    s.at(22.0F).spawn(GRIPPER, new ActorSpec.Item(SceneKit.part(AccessoryType.GRIPPER), 1.5F, true), ITEM_POS, 0.0F,
                            Cue.Appear.POP, 8.0F);
                    s.at(30.0F).callout("mount", belly, Cue.Side.RIGHT, 0.0F);
                    s.at(62.0F).callout("capacity", onItem, Cue.Side.LEFT, 0.0F);
                })
                .step("pickup", 180.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, OVERVIEW_YAW, 24.0F, 1.0F, 20.0F);
                    s.at(0.0F).despawn(GRIPPER, 8.0F);
                    s.at(4.0F).spawn(CARGO, new ActorSpec.Item(CARGO_ITEM, CARGO_SCALE, false), CARGO_POS, 30.0F, Cue.Appear.DROP, 10.0F);
                    s.at(8.0F).spawn(CARGO_SECOND, new ActorSpec.Item(CARGO_SECOND_ITEM, CARGO_SCALE, false), CARGO_SECOND_POS, 75.0F,
                            Cue.Appear.DROP, 10.0F);
                    s.at(20.0F).arm(DRONE, true);
                    s.at(20.0F).throttle(DRONE, 0.55F, 12.0F);
                    s.at(24.0F).move(DRONE, 20.0F, Ease.IN_OUT, HOVER);
                    SceneKit.fly(s, DRONE, HOVER, toCargo, Ease.IN_OUT, 44.0F, 40.0F, 10, 12.0F);
                    s.at(70.0F).camera(OVER_CARGO.add(-0.2F, 0.2F, 0.0F), OVERVIEW_YAW + 10.0F, 18.0F, CLOSE_ZOOM, 24.0F);
                    s.at(86.0F).callout("low", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(100.0F).move(CARGO, 10.0F, Ease.OUT, OVER_CARGO.add(HANG));
                    s.at(100.0F).move(CARGO_SECOND, 10.0F, Ease.OUT, OVER_CARGO.add(HANG).add(0.05F, -0.02F, 0.05F));
                    s.at(112.0F).move(DRONE, 20.0F, Ease.IN_OUT, OVER_CARGO.add(0.0F, 0.5F, 0.0F));
                    s.at(112.0F).move(CARGO, 20.0F, Ease.IN_OUT, OVER_CARGO.add(0.0F, 0.5F, 0.0F).add(HANG));
                    s.at(112.0F).move(CARGO_SECOND, 20.0F, Ease.IN_OUT, OVER_CARGO.add(0.0F, 0.5F, 0.0F).add(HANG).add(0.05F, -0.02F, 0.05F));
                    s.at(120.0F).callout("heavier", onCargo, Cue.Side.LEFT, 0.0F);
                })
                .step("drop", 160.0F, s -> {
                    ScenePos start = OVER_CARGO.add(0.0F, 0.5F, 0.0F);
                    s.at(0.0F).pop(HOPPER_X, 1, HOPPER_Z, HOPPER, 10.0F);
                    s.at(10.0F).camera(OVER_HOPPER.add(0.5F, -0.3F, 0.0F), OVERVIEW_YAW - 10.0F, 20.0F, CLOSE_ZOOM - 0.2F, 40.0F);
                    SceneKit.fly(s, DRONE, start, toHopper, Ease.IN_OUT, 10.0F, 50.0F, 12, 14.0F);
                    s.at(10.0F).move(CARGO, 50.0F, Ease.IN_OUT, shifted(toHopper, HANG));
                    s.at(10.0F).move(CARGO_SECOND, 50.0F, Ease.IN_OUT, shifted(toHopper, HANG.add(0.05F, -0.02F, 0.05F)));
                    s.at(66.0F).keysAt("drop", Anchor.on(DRONE, 0.0F, 0.45F, 0.0F), 40.0F, KeyToken.mapping(SceneKit.KEY_CARGO_DROP));
                    s.at(76.0F).move(CARGO, 12.0F, Ease.IN, INTO_HOPPER);
                    s.at(78.0F).move(CARGO_SECOND, 12.0F, Ease.IN, INTO_HOPPER.add(0.05F, 0.0F, 0.05F));
                    s.at(90.0F).despawn(CARGO, 6.0F);
                    s.at(92.0F).despawn(CARGO_SECOND, 6.0F);
                    s.at(106.0F).callout("switch", onDrone, Cue.Side.LEFT, 0.0F);
                })
                .step("auto", 150.0F, s -> {
                    s.at(0.0F).spawn(CARGO_LAST, new ActorSpec.Item(CARGO_LAST_ITEM, CARGO_SCALE, false), OVER_HOPPER.add(HANG), 0.0F,
                            Cue.Appear.POP, 8.0F);
                    s.at(10.0F).move(DRONE, 40.0F, Ease.IN_OUT, LANDING.add(0.0F, 0.6F, 0.0F), LANDING);
                    s.at(10.0F).move(CARGO_LAST, 40.0F, Ease.IN_OUT, LANDING.add(0.0F, 0.6F, 0.0F).add(HANG), LANDING.add(BESIDE));
                    s.at(10.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 20.0F);
                    s.at(10.0F).camera(LANDING.add(0.0F, 0.4F, 0.0F), OVERVIEW_YAW, 22.0F, CLOSE_ZOOM, 30.0F);
                    s.at(36.0F).throttle(DRONE, 0.08F, 12.0F);
                    s.at(52.0F).arm(DRONE, false);
                    s.at(56.0F).callout("land", Anchor.on(CARGO_LAST, 0.0F, 0.05F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(96.0F).callout("crash", onDrone, Cue.Side.LEFT, 0.0F);
                })
                .build();
    }

    static ScenePos[] shifted(List<ScenePos> path, ScenePos offset) {
        ScenePos[] out = new ScenePos[path.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = path.get(i).add(offset);
        }
        return out;
    }
}

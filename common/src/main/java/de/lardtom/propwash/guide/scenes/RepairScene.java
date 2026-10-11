package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropRepair;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.DronePart;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import static de.lardtom.propwash.guide.scenes.SceneKit.CRAFTING_TABLE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.STONE_BRICKS;
import static de.lardtom.propwash.guide.scenes.SceneKit.WORKBENCH;
import static de.lardtom.propwash.guide.scenes.SceneKit.WORKBENCH_BLOCK;

public final class RepairScene {
    public static final String ID = "repair";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final String PROPS = SceneKit.part(PropType.P51_3B);
    public static final String MOTOR = SceneKit.part(MotorType.M2207_1750);
    public static final String FRAME = SceneKit.part(FrameType.FREESTYLE_5);
    public static final ScenePos PILOT_POS = new ScenePos(-1.9F, 1.0F, 2.2F);
    public static final ScenePos NEAR = new ScenePos(-0.6F, 1.0F, 1.3F);
    public static final ScenePos HOVER = new ScenePos(-1.0F, 2.3F, 0.9F);
    public static final ScenePos IMPACT = new ScenePos(0.72F, 2.1F, -0.5F);
    public static final ScenePos WRECK = new ScenePos(0.35F, 1.0F, 0.1F);
    public static final float CRASH_AT = 50.0F;
    public static final float SWAP_AT = 60.0F;
    public static final float[] AFTER_CRASH = {0.0F, 0.35F, 0.85F, 0.9F};
    public static final int BENCH_X = -2;
    public static final int BENCH_Z = -1;
    public static final int CRAFT_X = 0;
    public static final int CRAFT_Z = 2;
    public static final String OLD_PROP_A = "old_prop_a";
    public static final String OLD_PROP_B = "old_prop_b";
    public static final String REPAIRED_PROP = "repaired_prop";
    public static final float[] OLD_PROPS = {AFTER_CRASH[1], AFTER_CRASH[2]};
    public static final float REPAIRED = PropRepair.combine(OLD_PROPS[0], OLD_PROPS[1]);
    public static final float MERGE_AT = 92.0F;

    private static final ScenePos OVERVIEW = new ScenePos(0.0F, 1.8F, 0.4F);
    private static final ScenePos LIFTED = new ScenePos(0.0F, 0.55F, 0.0F);

    private RepairScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.18F, 0.0F);
        Anchor frontLeft = Anchor.on(DRONE, -0.2F, 0.12F, -0.2F);
        Anchor frontRight = Anchor.on(DRONE, 0.2F, 0.12F, -0.2F);
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.15F, 0.0F);
        ScenePos benchTop = new ScenePos(BENCH_X + 0.5F, 2.0F, BENCH_Z + 0.5F);
        ScenePos craftTop = new ScenePos(CRAFT_X + 0.5F, 2.0F, CRAFT_Z + 0.5F);
        ScenePos merged = craftTop.add(0.0F, 0.45F, 0.0F);
        float crashYaw = SceneKit.heading(HOVER, IMPACT);
        float wreckYaw = crashYaw + 35.0F;
        return SceneBuilder.scene(ID)
                .icon(PROPS)
                .items(SceneKit.parts(PropType.values()))
                .items(SceneKit.DRONE_ITEM, WORKBENCH)
                .view(2.8F)
                .camera(OVERVIEW, 34.0F, 26.0F, 1.0F)
                .step("crash", 120.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -3, -3, 3, 3, GRASS, GRASS, 1.0F, 10.0F);
                    for (int y = 1; y <= 3; y++) {
                        s.at(10.0F + y * 3.0F).drop(1, y, -1, STONE_BRICKS, 8.0F);
                    }
                    s.at(14.0F).spawn(PILOT, new ActorSpec.Pilot(SceneKit.GOGGLES_DIGITAL, REMOTE), PILOT_POS,
                            SceneKit.pilotYaw(PILOT_POS, IMPACT), Cue.Appear.POP, 10.0F);
                    s.at(18.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true), HOVER, crashYaw, Cue.Appear.POP, 8.0F);
                    s.at(18.0F).arm(DRONE, true);
                    s.at(18.0F).throttle(DRONE, 0.6F, 6.0F);
                    s.at(26.0F).turn(DRONE, crashYaw, -22.0F, 0.0F, 8.0F);
                    s.at(28.0F).move(DRONE, 22.0F, Ease.IN, IMPACT);
                    s.at(CRASH_AT).propConditions(DRONE, AFTER_CRASH);
                    s.at(CRASH_AT).arm(DRONE, false);
                    s.at(CRASH_AT).throttle(DRONE, 0.0F, 0.0F);
                    s.at(CRASH_AT).move(DRONE, 20.0F, Ease.IN, IMPACT.add(-0.3F, -0.2F, 0.2F), WRECK);
                    s.at(CRASH_AT).turn(DRONE, wreckYaw, 20.0F, 35.0F, 16.0F);
                    s.at(68.0F).turn(DRONE, wreckYaw, 0.0F, 0.0F, 8.0F);
                    s.at(54.0F).highlight(IMPACT.add(-0.1F, -0.3F, -0.3F), IMPACT.add(0.22F, 0.3F, 0.3F), 14.0F);
                    s.at(60.0F).callout("crash", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(0.0F).camera(new ScenePos(0.1F, 1.9F, 0.2F), 34.0F, 24.0F, 1.3F, 40.0F);
                })
                .step("damage", 130.0F, s -> {
                    s.at(0.0F).camera(WRECK.add(0.0F, 0.35F, 0.0F), 20.0F, 38.0F, 2.4F, 26.0F);
                    s.at(8.0F).highlight(WRECK.add(-0.45F, 0.0F, -0.45F), WRECK.add(0.45F, 0.3F, 0.45F), 40.0F);
                    s.at(10.0F).callout("broken", frontLeft, Cue.Side.LEFT, 56.0F);
                    s.at(70.0F).callout("worn", frontRight, Cue.Side.RIGHT, 0.0F);
                })
                .step("material", 140.0F, s -> {
                    s.at(0.0F).camera(WRECK.add(0.0F, 0.35F, 0.0F), 20.0F, 38.0F, 2.4F, 20.0F);
                    s.at(8.0F).callout("bend", frontRight, Cue.Side.RIGHT, 40.0F);
                    s.at(50.0F).callout("crack", frontLeft, Cue.Side.LEFT, 40.0F);
                    s.at(92.0F).callout("shatter", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("warning", 110.0F, s -> {
                    s.at(0.0F).camera(WRECK.add(0.0F, 0.3F, 0.0F), 40.0F, 30.0F, 1.8F, 20.0F);
                    s.at(6.0F).callout("osd", onDrone, Cue.Side.RIGHT, 50.0F);
                    s.at(58.0F).callout("tooltip", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("swap", 150.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(-0.1F, 1.5F, 0.7F), 34.0F, 26.0F, 1.6F, 24.0F);
                    s.at(0.0F).move(PILOT, 26.0F, Ease.IN_OUT, NEAR);
                    s.at(0.0F).turn(PILOT, SceneKit.pilotYaw(NEAR, WRECK), 0.0F, 0.0F, 20.0F);
                    s.at(20.0F).look(PILOT, SceneKit.headPitch(NEAR, WRECK), 14.0F);
                    s.at(26.0F).equip(PILOT, Cue.Slot.HAND, PROPS);
                    s.at(30.0F).crouch(PILOT, true);
                    s.at(32.0F).keysAt("swap", overHead, 0.0F, KeyToken.sneak(), KeyToken.use());
                    s.at(34.0F).callout("menu", onDrone, Cue.Side.RIGHT, 24.0F);
                    s.at(SWAP_AT).detach(DRONE, DronePart.PROPS, LIFTED, 14.0F);
                    s.at(SWAP_AT + 16.0F).propConditions(DRONE, 1.0F, 1.0F, 1.0F, 1.0F);
                    s.at(SWAP_AT + 16.0F).attach(DRONE, DronePart.PROPS, LIFTED, 24.0F, 3.0F);
                    s.at(SWAP_AT + 20.0F).callout("swap", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(130.0F).crouch(PILOT, false);
                })
                .step("combine", 150.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, "");
                    s.at(0.0F).camera(new ScenePos(0.3F, 2.0F, 2.0F), 34.0F, 38.0F, 2.0F, 24.0F);
                    s.at(0.0F).turn(PILOT, SceneKit.pilotYaw(NEAR, craftTop), 0.0F, 0.0F, 16.0F);
                    s.at(8.0F).drop(CRAFT_X, 1, CRAFT_Z, CRAFTING_TABLE, 10.0F);
                    s.at(20.0F).spawn(OLD_PROP_A, new ActorSpec.Item(PROPS, 1.8F, true), merged.add(-0.45F, 0.0F, 0.0F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(20.0F).itemCondition(OLD_PROP_A, OLD_PROPS[0]);
                    s.at(24.0F).spawn(OLD_PROP_B, new ActorSpec.Item(PROPS, 1.8F, true), merged.add(0.45F, 0.0F, 0.0F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(24.0F).itemCondition(OLD_PROP_B, OLD_PROPS[1]);
                    s.at(26.0F).highlightBlock(CRAFT_X, 1, CRAFT_Z, 30.0F);
                    s.at(30.0F).callout("pair", Anchor.at(craftTop.add(0.0F, 0.02F, 0.0F)), Cue.Side.LEFT, 56.0F);
                    s.at(MERGE_AT - 14.0F).move(OLD_PROP_A, 14.0F, Ease.IN, merged);
                    s.at(MERGE_AT - 14.0F).move(OLD_PROP_B, 14.0F, Ease.IN, merged);
                    s.at(MERGE_AT).despawn(OLD_PROP_A, 4.0F);
                    s.at(MERGE_AT).despawn(OLD_PROP_B, 4.0F);
                    s.at(MERGE_AT).spawn(REPAIRED_PROP, new ActorSpec.Item(PROPS, 2.0F, true), merged, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(MERGE_AT).itemCondition(REPAIRED_PROP, REPAIRED);
                    s.at(MERGE_AT + 4.0F).callout("sum", Anchor.at(craftTop.add(0.0F, 0.02F, 0.0F)), Cue.Side.RIGHT, 0.0F);
                    s.at(144.0F).despawn(REPAIRED_PROP, 6.0F);
                    s.at(144.0F).remove(CRAFT_X, 1, CRAFT_Z, 6.0F);
                })
                .step("bench", 140.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, "");
                    s.at(0.0F).camera(new ScenePos(-1.3F, 1.8F, -0.3F), 34.0F, 26.0F, 1.5F, 24.0F);
                    s.at(8.0F).drop(BENCH_X, 1, BENCH_Z, WORKBENCH_BLOCK, 10.0F);
                    s.at(20.0F).spawn("motor", new ActorSpec.Item(MOTOR, 1.1F, true), benchTop.add(-0.3F, 0.35F, 0.0F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(26.0F).spawn("frame", new ActorSpec.Item(FRAME, 1.1F, true), benchTop.add(0.3F, 0.35F, 0.0F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(24.0F).highlightBlock(BENCH_X, 1, BENCH_Z, 30.0F);
                    s.at(30.0F).callout("bench", Anchor.at(benchTop.add(0.0F, 0.02F, 0.0F)), Cue.Side.ABOVE, 56.0F);
                    s.at(88.0F).callout("recycle", Anchor.at(benchTop.add(0.0F, 0.02F, 0.0F)), Cue.Side.RIGHT, 0.0F);
                    s.at(126.0F).despawn("motor", 10.0F);
                    s.at(126.0F).despawn("frame", 10.0F);
                })
                .step("fly", 120.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 30.0F, 24.0F, 1.2F, 24.0F);
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, REMOTE);
                    s.at(0.0F).look(PILOT, SceneKit.headPitch(NEAR, WRECK.add(0.0F, 1.0F, 0.0F)), 20.0F);
                    s.at(10.0F).arm(DRONE, true);
                    s.at(10.0F).throttle(DRONE, 0.55F, 10.0F);
                    s.at(18.0F).move(DRONE, 30.0F, Ease.IN_OUT, WRECK.add(0.0F, 1.1F, 0.2F));
                    s.at(30.0F).callout("fly", onDrone, Cue.Side.RIGHT, 40.0F);
                    s.at(74.0F).callout("creative", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .build();
    }
}

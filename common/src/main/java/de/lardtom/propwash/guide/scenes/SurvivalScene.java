package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import static de.lardtom.propwash.guide.scenes.SceneKit.CHARGER;
import static de.lardtom.propwash.guide.scenes.SceneKit.COURSE_TOOL;
import static de.lardtom.propwash.guide.scenes.SceneKit.CRAFTING_TABLE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_LARGE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_MEDIUM;
import static de.lardtom.propwash.guide.scenes.SceneKit.GATE_SMALL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_ANALOG;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_CREATIVE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_DIGITAL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.WORKBENCH;
import static de.lardtom.propwash.guide.scenes.SceneKit.WORKBENCH_BLOCK;

public final class SurvivalScene {
    public static final String ID = "survival";
    public static final String DRONE = "drone";
    public static final String CHEST = "chest";
    public static final String RESULT = "result";
    public static final DroneBuild BUILD = DroneBuild.WHOOP;
    public static final ScenePos TABLE_TOP = new ScenePos(-0.5F, 2.0F, 0.5F);
    public static final ScenePos CHEST_POS = new ScenePos(1.5F, 1.7F, -0.5F);
    public static final String[] INGREDIENTS = {"minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:redstone"};
    public static final String[] CATALOG = {
            SceneKit.part(MotorType.M2207_1750),
            SceneKit.part(PropType.P51_3B),
            SceneKit.part(StackType.STACK_30),
            SceneKit.part(VideoType.ANALOG_RACE),
            SceneKit.part(BatteryType.B6S_1300),
            SceneKit.GOGGLES_CREATIVE};
    public static final int CREATIVE_GOGGLES = CATALOG.length - 1;
    public static final String CREATIVE_GOGGLES_ACTOR = "catalog" + CREATIVE_GOGGLES;
    public static final float CATALOG_SPACING = 0.78F;
    public static final float CATALOG_SCALE = 1.0F;
    public static final float CATALOG_HEIGHT = 1.05F;
    public static final String[] LOOT = {
            SceneKit.part(BatteryType.B4S_650),
            SceneKit.part(MotorType.M1404_4600),
            SceneKit.part(PropType.P3_3B),
            SceneKit.part(VideoType.ANALOG_NANO)};

    private static final ScenePos OVERVIEW = new ScenePos(0.4F, 1.9F, 0.2F);
    private static final ScenePos RIGHT = new ScenePos(0.866F, 0.0F, -0.5F);
    private static final String BARRIER = "no_recipe";

    private SurvivalScene() {
    }

    public static ScenePos catalogPos(int index) {
        float offset = (index - (CATALOG.length - 1) / 2.0F) * CATALOG_SPACING;
        return TABLE_TOP.add(RIGHT.scale(offset)).add(0.0F, CATALOG_HEIGHT, 0.0F);
    }

    public static Scene create() {
        return SceneBuilder.scene(ID)
                .icon("minecraft:crafting_table")
                .anyPart()
                .items(WORKBENCH, CHARGER, REMOTE, GOGGLES_ANALOG, GOGGLES_DIGITAL, GATE_SMALL, GATE_MEDIUM, GATE_LARGE, COURSE_TOOL)
                .view(2.2F)
                .camera(OVERVIEW, 30.0F, 24.0F, 1.0F)
                .step("craft", 140.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -2, -2, 2, 2, GRASS, GRASS, 1.5F, 10.0F);
                    s.at(16.0F).drop(-1, 1, 0, CRAFTING_TABLE, 10.0F);
                    s.at(20.0F).camera(TABLE_TOP.add(0.3F, 0.35F, 0.0F), 30.0F, 22.0F, 1.6F, 24.0F);
                    for (int i = 0; i < INGREDIENTS.length; i++) {
                        String id = "ingredient" + i;
                        ScenePos start = TABLE_TOP.add(RIGHT.scale((i - 1) * 0.55F)).add(0.0F, 0.7F + (i == 1 ? 0.18F : 0.0F), 0.0F);
                        s.at(28.0F + i * 6.0F).spawn(id, new ActorSpec.Item(INGREDIENTS[i], 1.2F, true), start, 0.0F, Cue.Appear.POP, 8.0F);
                        s.at(60.0F + i * 3.0F).move(id, 16.0F, Ease.IN, TABLE_TOP.add(0.0F, 0.1F, 0.0F));
                        s.at(74.0F + i * 3.0F).despawn(id, 4.0F);
                    }
                    s.at(34.0F).callout("ingredients", Anchor.at(TABLE_TOP.add(RIGHT.scale(0.55F)).add(0.0F, 0.85F, 0.0F)), Cue.Side.RIGHT, 30.0F);
                    s.at(84.0F).spawn(RESULT, new ActorSpec.Item(SceneKit.part(FrameType.FREESTYLE_5), 1.5F, true), TABLE_TOP.add(0.0F, 0.6F, 0.0F),
                            0.0F, Cue.Appear.POP, 10.0F);
                    s.at(90.0F).callout("craft", Anchor.on(RESULT, 0.0F, 0.3F, 0.0F), Cue.Side.RIGHT, 0.0F);
                })
                .step("book", 130.0F, s -> {
                    s.at(0.0F).despawn(RESULT, 8.0F);
                    s.at(0.0F).camera(TABLE_TOP.add(0.0F, 0.45F, 0.0F), 30.0F, 16.0F, 1.1F, 20.0F);
                    s.at(0.0F).highlightBlock(-1, 1, 0, 40.0F);
                    for (int i = 0; i < CATALOG.length; i++) {
                        s.at(8.0F + i * 5.0F).spawn("catalog" + i, new ActorSpec.Item(CATALOG[i], CATALOG_SCALE, i != CREATIVE_GOGGLES),
                                catalogPos(i), 0.0F, Cue.Appear.POP, 8.0F);
                        s.at(116.0F).despawn("catalog" + i, 10.0F);
                    }
                    s.at(12.0F).keys("book", 0.0F, KeyToken.use());
                    s.at(16.0F).callout("book", Anchor.on("catalog2", 0.0F, 0.35F, 0.0F), Cue.Side.ABOVE, 38.0F);
                    ScenePos goggles = catalogPos(CREATIVE_GOGGLES);
                    s.at(54.0F).spawn(BARRIER, new ActorSpec.Item(SceneKit.BARRIER, 0.7F, false), goggles.add(0.0F, 0.02F, 0.0F), 0.0F,
                            Cue.Appear.POP, 8.0F);
                    s.at(56.0F).callout("no_creative", Anchor.on(CREATIVE_GOGGLES_ACTOR, 0.0F, 0.2F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(116.0F).despawn(BARRIER, 10.0F);
                })
                .step("chests", 130.0F, s -> {
                    s.at(0.0F).camera(CHEST_POS.add(-0.2F, 0.3F, 0.2F), 30.0F, 22.0F, 1.5F, 20.0F);
                    s.at(6.0F).spawn(CHEST, new ActorSpec.Item("minecraft:chest", 3.0F, false), CHEST_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    for (int i = 0; i < LOOT.length; i++) {
                        String id = "loot" + i;
                        float side = (i - (LOOT.length - 1) / 2.0F) * 0.45F;
                        ScenePos out = CHEST_POS.add(RIGHT.scale(side)).add(0.0F, 0.85F + (i % 2) * 0.2F, 0.0F);
                        s.at(28.0F + i * 5.0F).spawn(id, new ActorSpec.Item(LOOT[i], 1.1F, true), CHEST_POS.add(0.0F, 0.2F, 0.0F), 0.0F,
                                Cue.Appear.POP, 6.0F);
                        s.at(28.0F + i * 5.0F).move(id, 14.0F, Ease.OUT, CHEST_POS.add(RIGHT.scale(side * 0.5F)).add(0.0F, 1.2F, 0.0F), out);
                        s.at(116.0F).despawn(id, 10.0F);
                    }
                    s.at(52.0F).callout("chests", Anchor.on(CHEST, 0.0F, 0.1F, 0.0F), Cue.Side.BELOW, 0.0F);
                    s.at(116.0F).despawn(CHEST, 10.0F);
                })
                .step("workshop", 150.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW.add(0.2F, 0.0F, 0.2F), 30.0F, 24.0F, 1.1F, 20.0F);
                    s.at(0.0F).remove(-1, 1, 0, 8.0F);
                    s.at(8.0F).drop(0, 1, 1, WORKBENCH_BLOCK, 10.0F);
                    s.at(14.0F).drop(1, 1, -1, SceneKit.charger("south", "idle"), 10.0F);
                    s.at(20.0F).callout("workshop", Anchor.at(new ScenePos(0.5F, 2.0F, 1.5F)), Cue.Side.RIGHT, 40.0F);
                    s.at(56.0F).camera(new ScenePos(0.6F, 2.2F, 1.3F), 30.0F, 22.0F, 2.0F, 24.0F);
                    s.at(62.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true), new ScenePos(0.5F, 2.0F, 1.5F), 30.0F, Cue.Appear.POP, 10.0F);
                    s.at(66.0F).callout("whoop", Anchor.on(DRONE, 0.0F, 0.12F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(90.0F).arm(DRONE, true);
                    s.at(90.0F).throttle(DRONE, 0.6F, 10.0F);
                    s.at(96.0F).move(DRONE, 40.0F, Ease.IN_OUT, new ScenePos(0.6F, 2.5F, 1.4F), new ScenePos(0.4F, 2.7F, 1.1F));
                })
                .build();
    }
}

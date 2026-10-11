package de.lardtom.propwash.bando;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scenes.SceneKit;

import java.util.List;

public final class BandoScene {
    public static final String ID = "bandos";
    public static final String DRONE = "drone";
    public static final String CHEST = "chest";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final ScenePos CHEST_POS = new ScenePos(-1.5F, 1.0F, -0.5F);
    public static final ScenePos ROOF_HOLE = new ScenePos(1.5F, 5.0F, 0.0F);
    public static final ScenePos WINDOW = new ScenePos(-1.0F, 2.5F, 2.5F);
    public static final String[] LOOT = {
            SceneKit.part(PropType.P51_3B),
            SceneKit.part(MotorType.M1404_4600),
            "propwash:lipo_cell"};

    private static final BlockSpec GROUND = BlockSpec.of("minecraft:grass_block");
    private static final BlockSpec RIM = BlockSpec.of("minecraft:coarse_dirt");
    private static final BlockSpec FLOOR = BlockSpec.of("minecraft:polished_andesite");
    private static final BlockSpec COLUMN = BlockSpec.of("minecraft:polished_basalt");
    private static final BlockSpec BEAM_X = BlockSpec.of("minecraft:polished_basalt").with("axis", "x");
    private static final BlockSpec BEAM_Z = BlockSpec.of("minecraft:polished_basalt").with("axis", "z");
    private static final BlockSpec WALL = BlockSpec.of("minecraft:bricks");
    private static final BlockSpec ROOF = BlockSpec.of("minecraft:waxed_weathered_cut_copper_slab");
    private static final BlockSpec RUBBLE = BlockSpec.of("minecraft:cobblestone_slab");
    private static final ScenePos OVERVIEW = new ScenePos(0.5F, 2.4F, 0.5F);
    private static final int X0 = -3;
    private static final int X1 = 3;
    private static final int Z0 = -2;
    private static final int Z1 = 2;
    private static final int EAVE = 4;

    private BandoScene() {
    }

    public static Scene create() {
        return SceneBuilder.scene(ID)
                .icon("minecraft:cracked_stone_bricks")
                .view(4.6F)
                .camera(OVERVIEW, 35.0F, 26.0F, 1.0F)
                .step("find", 150.0F, s -> {
                    SceneKit.ground(s.at(0.0F), X0 - 1, Z0 - 2, X1 + 1, Z1 + 1, GROUND, RIM, 0.6F, 8.0F);
                    float t = 10.0F;
                    for (int x = X0; x <= X1; x++) {
                        for (int z = Z0; z <= Z1; z++) {
                            s.at(t + (x - X0) * 0.8F).drop(x, 0, z, FLOOR, 6.0F);
                        }
                    }
                    t = 18.0F;
                    for (int x = X0; x <= X1; x += 3) {
                        for (int z = Z0; z <= Z1; z += Z1 - Z0) {
                            for (int y = 1; y <= EAVE - 1; y++) {
                                s.at(t + y * 2.0F + (x - X0)).drop(x, y, z, COLUMN, 6.0F);
                            }
                        }
                    }
                    t = 34.0F;
                    for (int x = X0; x <= X1; x++) {
                        s.at(t + (x - X0)).drop(x, EAVE, Z0, BEAM_X, 6.0F);
                        s.at(t + (x - X0)).drop(x, EAVE, Z1, BEAM_X, 6.0F);
                    }
                    for (int z = Z0 + 1; z < Z1; z++) {
                        s.at(t + 8.0F).drop(X0, EAVE, z, BEAM_Z, 6.0F);
                        s.at(t + 8.0F).drop(X1, EAVE, z, BEAM_Z, 6.0F);
                        s.at(t + 9.0F).drop(0, EAVE, z, BEAM_Z, 6.0F);
                    }
                    t = 48.0F;
                    for (int x = X0 + 1; x < X1; x++) {
                        if (x == 0) {
                            continue;
                        }
                        for (int y = 1; y <= 2; y++) {
                            if (x == -1 && y == 2) {
                                continue;
                            }
                            s.at(t + (x - X0) + y).drop(x, y, Z1, WALL, 6.0F);
                        }
                    }
                    t = 58.0F;
                    for (int x = X0; x <= X1; x++) {
                        for (int z = Z0; z <= Z1; z++) {
                            boolean hole = (x == 1 || x == 2) && (z == -1 || z == 0);
                            boolean broken = x == -3 && z == 2 || x == 3 && z == -2;
                            if (!hole && !broken) {
                                s.at(t + (x - X0) * 0.7F + (z - Z0) * 0.4F).drop(x, EAVE + 1, z, ROOF, 6.0F);
                            }
                        }
                    }
                    s.at(70.0F).drop(2, 1, -1, RUBBLE, 6.0F);
                    s.at(72.0F).drop(1, 1, 0, RUBBLE, 6.0F);
                    s.at(84.0F).callout("ruin", Anchor.at(new ScenePos(X1 + 0.5F, EAVE + 1.0F, Z0 + 0.5F)), Cue.Side.RIGHT, 0.0F);
                })
                .step("fly", 160.0F, s -> {
                    ScenePos start = new ScenePos(0.5F, 1.6F, -5.0F);
                    s.at(0.0F).camera(OVERVIEW.add(0.0F, 0.4F, -0.4F), 50.0F, 22.0F, 1.0F, 20.0F);
                    s.at(4.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true), start, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(12.0F).arm(DRONE, true);
                    s.at(12.0F).throttle(DRONE, 0.6F, 10.0F);
                    List<ScenePos> path = List.of(
                            new ScenePos(0.5F, 2.2F, -2.8F),
                            new ScenePos(1.5F, 2.6F, -1.2F),
                            new ScenePos(1.9F, 3.6F, 0.0F),
                            new ScenePos(2.0F, 5.6F, 0.0F),
                            new ScenePos(2.0F, 6.6F, -1.6F));
                    SceneKit.fly(s, DRONE, start, path, Ease.IN_OUT, 24.0F, 90.0F, 12, 25.0F);
                    s.at(30.0F).callout("gap", Anchor.at(new ScenePos(0.5F, 2.2F, Z0 + 0.5F)), Cue.Side.LEFT, 40.0F);
                    s.at(74.0F).callout("roof", Anchor.at(ROOF_HOLE.add(0.5F, 0.6F, 0.0F)), Cue.Side.RIGHT, 0.0F);
                    s.at(118.0F).callout("window", Anchor.at(WINDOW), Cue.Side.LEFT, 0.0F);
                })
                .step("chest", 140.0F, s -> {
                    s.at(0.0F).despawn(DRONE, 10.0F);
                    s.at(0.0F).camera(CHEST_POS.add(0.0F, 0.9F, 0.0F), 180.0F, 30.0F, 1.4F, 20.0F);
                    s.at(8.0F).spawn(CHEST, new ActorSpec.Item("minecraft:chest", 2.2F, false), CHEST_POS.add(0.0F, 0.45F, 0.0F), 180.0F,
                            Cue.Appear.POP, 10.0F);
                    for (int i = 0; i < LOOT.length; i++) {
                        String id = "loot" + i;
                        float side = (i - (LOOT.length - 1) / 2.0F) * 0.55F;
                        ScenePos from = CHEST_POS.add(0.0F, 0.6F, 0.0F);
                        ScenePos to = CHEST_POS.add(side, 1.5F + (i % 2) * 0.2F, -0.3F);
                        s.at(30.0F + i * 6.0F).spawn(id, new ActorSpec.Item(LOOT[i], 1.0F, true), from, 0.0F, Cue.Appear.POP, 6.0F);
                        s.at(30.0F + i * 6.0F).move(id, 14.0F, Ease.OUT, to);
                        s.at(126.0F).despawn(id, 10.0F);
                    }
                    s.at(50.0F).callout("chest", Anchor.on(CHEST, 0.0F, 0.2F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(126.0F).despawn(CHEST, 10.0F);
                })
                .step("locate", 120.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW.add(0.0F, 0.6F, 0.0F), 215.0F, 30.0F, 0.9F, 30.0F);
                    s.at(20.0F).callout("locate", Anchor.at(new ScenePos(0.5F, EAVE + 1.8F, 0.5F)), Cue.Side.ABOVE, 0.0F);
                })
                .build();
    }
}

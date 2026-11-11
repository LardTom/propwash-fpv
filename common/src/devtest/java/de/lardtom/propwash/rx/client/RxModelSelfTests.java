package de.lardtom.propwash.rx.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxOutput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RxModelSelfTests {
    static final int LEVELS = 16;
    private static final @Nullable Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final String PLUG = "propwash:block/rx_block_output";

    private RxModelSelfTests() {
    }

    record Look(List<String> quads, Set<String> sprites, int glow, boolean missing) {
    }

    static void register() {
        SelfTest.clientStep(RxSelfTests.GROUP + "Blockmodell zeigt die Stärke 0–15 – eigenes Aussehen und eigene Textur je Stufe, Leuchten steigt, "
                + "LED folgt weiter dem Link", 200, RxModelSelfTests::check);
        SelfTest.clientStep(RxSelfTests.GROUP + "Blockmodell zeigt die Richtung – Stecker mit Pfeil an der Seite von facing, jede Richtung dreht den "
                + "ganzen Block, alter Block ohne Richtung mit Steckern an allen vier Seiten", 200, RxModelSelfTests::direction);
    }

    static Set<Direction> plugSides(BlockStateModelSet models, BlockState state) {
        BlockStateModel model = models.get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        Map<Integer, float[]> sums = new HashMap<>();
        int element = 0;
        for (BlockStateModelPart part : parts) {
            float[] sum = new float[3];
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    if (!quad.materialInfo().sprite().contents().name().toString().equals(PLUG)) {
                        continue;
                    }
                    for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                        Vector3fc p = quad.position(i);
                        sum[0] += p.x();
                        sum[1] += p.z();
                        sum[2]++;
                    }
                }
            }
            if (sum[2] > 0) {
                sums.put(element, sum);
            }
            element++;
        }
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (float[] sum : sums.values()) {
            float x = sum[0] / sum[2] - 0.5F;
            float z = sum[1] / sum[2] - 0.5F;
            if (Math.max(Math.abs(x), Math.abs(z)) < 0.3F) {
                out.add(Direction.UP);
            } else if (Math.abs(x) > Math.abs(z)) {
                out.add(x > 0 ? Direction.EAST : Direction.WEST);
            } else {
                out.add(z > 0 ? Direction.SOUTH : Direction.NORTH);
            }
        }
        return out;
    }

    static StepResult direction(SelfTest.StepContext ctx) {
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        BlockState base = RxModule.RX_BLOCK.block().get().defaultBlockState().setValue(RxBlock.LINK, RxLinkState.LINKED).setValue(RxBlock.POWER, 9);
        List<String> problems = new ArrayList<>();
        Set<List<String>> looks = new HashSet<>();
        int quads = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = RxBlock.facing(base, facing);
            Look look = look(models, state);
            if (look.missing()) {
                problems.add(facing.getSerializedName() + ": Modell oder Textur fehlt");
            }
            if (!look.sprites().contains(PLUG)) {
                problems.add(facing.getSerializedName() + ": kein Stecker");
            }
            looks.add(look.quads());
            quads = look.quads().size();
            Set<Direction> sides = plugSides(models, state);
            if (!sides.equals(EnumSet.of(facing))) {
                problems.add("facing=" + facing.getSerializedName() + ": Stecker an " + sides);
            }
        }
        if (looks.size() != 4) {
            problems.add("die vier Richtungen sehen nur " + looks.size() + "-mal verschieden aus");
        }
        BlockState old = base.setValue(RxBlock.OUTPUT, RxOutput.ALL);
        Set<Direction> oldSides = plugSides(models, old);
        if (!oldSides.equals(EnumSet.copyOf(Direction.Plane.HORIZONTAL.stream().toList()))) {
            problems.add("alter Block: Stecker an " + oldSides);
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        ctx.log(String.format(Locale.ROOT, "4 Richtungen mit Stecker vorne (%d Flächen je Block), alter Block mit Steckern an %s", quads, oldSides));
        return StepResult.pass();
    }

    static Look look(BlockStateModelSet models, BlockState state) {
        BlockStateModel model = models.get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        List<String> quads = new ArrayList<>();
        Set<String> sprites = new HashSet<>();
        int glow = 0;
        boolean missing = model == models.missingModel();
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    TextureAtlasSprite sprite = quad.materialInfo().sprite();
                    String name = sprite.contents().name().toString();
                    missing |= sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
                    sprites.add(name);
                    glow += quad.materialInfo().lightEmission();
                    quads.add(String.format(Locale.ROOT, "%s %s %d %s %s %s %s", side, name, quad.materialInfo().lightEmission(), quad.position0(),
                            quad.position1(), quad.position2(), quad.position3()));
                }
            }
        }
        quads.sort(String::compareTo);
        return new Look(quads, sprites, glow, missing);
    }

    static StepResult check(SelfTest.StepContext ctx) {
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        BlockState base = RxModule.RX_BLOCK.block().get().defaultBlockState();
        List<String> problems = new ArrayList<>();
        Map<RxLinkState, List<Look>> all = new HashMap<>();
        for (RxLinkState link : RxLinkState.values()) {
            List<Look> looks = new ArrayList<>();
            for (int power = 0; power < LEVELS; power++) {
                Look look = look(models, base.setValue(RxBlock.LINK, link).setValue(RxBlock.POWER, power));
                if (look.missing()) {
                    problems.add(link.getSerializedName() + "/" + power + ": Modell oder Textur fehlt");
                }
                for (int before = 0; before < power; before++) {
                    if (looks.get(before).quads().equals(look.quads())) {
                        problems.add(link.getSerializedName() + ": Stärke " + power + " sieht aus wie Stärke " + before);
                        break;
                    }
                }
                if (power > 0 && look.glow() <= looks.get(power - 1).glow()) {
                    problems.add(link.getSerializedName() + ": Leuchten bei Stärke " + power + " (" + look.glow() + ") nicht über Stärke " + (power - 1)
                            + " (" + looks.get(power - 1).glow() + ")");
                }
                looks.add(look);
            }
            for (int power = 0; power < LEVELS; power++) {
                Set<String> own = new HashSet<>(looks.get(power).sprites());
                for (int other = 0; other < LEVELS; other++) {
                    if (other != power) {
                        own.removeAll(looks.get(other).sprites());
                    }
                }
                if (own.isEmpty()) {
                    problems.add(link.getSerializedName() + ": Stärke " + power + " ohne eigene Textur");
                }
            }
            all.put(link, looks);
        }
        for (int power = 0; power < LEVELS; power++) {
            Set<List<String>> leds = new HashSet<>();
            for (RxLinkState link : RxLinkState.values()) {
                leds.add(all.get(link).get(power).quads());
            }
            if (leds.size() != RxLinkState.values().length) {
                problems.add("Stärke " + power + ": LED-Zustände nicht unterscheidbar");
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems.subList(0, Math.min(8, problems.size()))) + (problems.size() > 8 ? " … (" + problems.size()
                    + " Befunde)" : ""));
        }
        Look none = all.get(RxLinkState.NONE).get(LEVELS - 1);
        ctx.log(String.format(Locale.ROOT, "%d Zustände mit eigenem Modell, Leuchten %d → %d, %d Flächen bei Stärke 15",
                RxLinkState.values().length * LEVELS, all.get(RxLinkState.NONE).getFirst().glow(), none.glow(), none.quads().size()));
        return StepResult.pass();
    }
}

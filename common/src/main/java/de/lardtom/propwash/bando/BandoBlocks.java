package de.lardtom.propwash.bando;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.bando.plan.BandoPalettes;
import de.lardtom.propwash.bando.plan.Mat;
import de.lardtom.propwash.bando.plan.Meta;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class BandoBlocks {
    private static final Map<String, Optional<BlockState>> PARSED = new ConcurrentHashMap<>();
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final BandoPalettes.Palette palette;
    private final Rotation rotation;
    private final Map<Long, Optional<BlockState>> resolved = new ConcurrentHashMap<>();

    public BandoBlocks(BandoPalettes.Palette palette, int turns) {
        this.palette = palette;
        this.rotation = rotation(turns);
    }

    public static Rotation rotation(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Optional<BlockState> parse(String state) {
        return PARSED.computeIfAbsent(state, s -> {
            try {
                return Optional.of(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState());
            } catch (CommandSyntaxException e) {
                Constants.LOG.error("Bando-Palette: ungültiger Block {}: {}", s, e.getMessage());
                return Optional.empty();
            }
        });
    }

    public @Nullable BlockState state(Mat mat, int meta, long hash) {
        List<BandoPalettes.Entry> entries = this.palette.entries(mat);
        int index = BandoPalettes.pickIndex(entries, hash);
        if (index < 0) {
            return null;
        }
        long key = ((long) mat.ordinal() << 40) | ((long) (meta & 0xFF) << 32) | index;
        return this.resolved.computeIfAbsent(key, k -> parse(entries.get(index).state()).map(base -> this.orient(base, mat, meta)))
                .orElse(null);
    }

    private BlockState orient(BlockState base, Mat mat, int meta) {
        BlockState state = base;
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            state = state.setValue(BlockStateProperties.AXIS, switch (meta & 3) {
                case Meta.AXIS_X -> Direction.Axis.X;
                case Meta.AXIS_Z -> Direction.Axis.Z;
                default -> Direction.Axis.Y;
            });
        }
        boolean faces = mat == Mat.ROOF_STEP || mat == Mat.LADDER || mat == Mat.CHEST;
        if (faces && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, FACINGS[meta & 3]);
        }
        if (mat == Mat.ROOF_STEP && state.hasProperty(BlockStateProperties.HALF)) {
            state = state.setValue(BlockStateProperties.HALF, (meta & Meta.TOP) != 0 ? Half.TOP : Half.BOTTOM);
        }
        if ((meta & Meta.SOLID) != 0 && mat.slab()) {
            if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                state = state.setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);
            } else if (state.hasProperty(BlockStateProperties.LAYERS)) {
                state = state.setValue(BlockStateProperties.LAYERS, SnowLayerBlock.MAX_HEIGHT);
            }
        }
        if (mat == Mat.VINE && state.is(Blocks.VINE)) {
            state = state.setValue(VineBlock.NORTH, (meta & Meta.FACE_NORTH) != 0)
                    .setValue(VineBlock.EAST, (meta & Meta.FACE_EAST) != 0)
                    .setValue(VineBlock.SOUTH, (meta & Meta.FACE_SOUTH) != 0)
                    .setValue(VineBlock.WEST, (meta & Meta.FACE_WEST) != 0)
                    .setValue(VineBlock.UP, (meta & Meta.FACE_UP) != 0);
        }
        return state.rotate(this.rotation);
    }

    public static boolean needsShapeUpdate(BlockState state) {
        return state.getBlock() instanceof CrossCollisionBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof StairBlock;
    }
}

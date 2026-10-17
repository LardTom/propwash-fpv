package de.lardtom.propwash.interact;

import de.lardtom.propwash.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class SnagBlocks implements SnagSampler.World {
    public static final TagKey<Block> WEB = TagKey.create(Registries.BLOCK, Constants.id("drone_snag_web"));
    public static final TagKey<Block> STRING = TagKey.create(Registries.BLOCK, Constants.id("drone_snag_string"));
    public static final TagKey<Block> FOLIAGE = TagKey.create(Registries.BLOCK, Constants.id("drone_snag_foliage"));
    public static final double STRING_HALF_WIDTH = 1.0 / 32.0;
    public static final double STRING_HEIGHT = 1.5 / 16.0;

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private @Nullable Level level;

    public SnagBlocks at(@Nullable Level level) {
        this.level = level;
        return this;
    }

    public static @Nullable SnagMaterial material(BlockState state) {
        if (state.isAir()) {
            return null;
        }
        if (state.is(WEB)) {
            return SnagMaterial.WEB;
        }
        if (state.is(STRING)) {
            return SnagMaterial.STRING;
        }
        if (state.is(FOLIAGE)) {
            return SnagMaterial.FOLIAGE;
        }
        return null;
    }

    public static @Nullable BlockState snagAt(Level level, BlockPos pos) {
        if (!loaded(level, pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return material(state) == null ? null : state;
    }

    public static boolean anyNear(Level level, AABB box) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x0 = (int) Math.floor(box.minX);
        int y0 = (int) Math.floor(box.minY);
        int z0 = (int) Math.floor(box.minZ);
        int x1 = (int) Math.floor(box.maxX);
        int y1 = (int) Math.floor(box.maxY);
        int z1 = (int) Math.floor(box.maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 512) {
            return false;
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    cursor.set(x, y, z);
                    if (snagAt(level, cursor) != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void boxes(int x, int y, int z, SnagSampler sampler) {
        Level l = this.level;
        if (l == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = this.cursor.set(x, y, z);
        if (!loaded(l, pos)) {
            return;
        }
        BlockState state = l.getBlockState(pos);
        SnagMaterial material = material(state);
        if (material == null) {
            return;
        }
        VoxelShape shape = state.getShape(l, pos);
        if (shape.isEmpty()) {
            return;
        }
        if (state.getBlock() instanceof TripWireBlock) {
            stringLines(state, shape, x, y, z, sampler);
            return;
        }
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> sampler.box(material, x + minX, y + minY, z + minZ,
                x + maxX, y + maxY, z + maxZ));
    }

    private static void stringLines(BlockState state, VoxelShape shape, int x, int y, int z, SnagSampler sampler) {
        double minY = y + shape.min(Direction.Axis.Y);
        double maxY = Math.min(y + shape.max(Direction.Axis.Y), minY + STRING_HEIGHT);
        boolean alongZ = state.getValue(TripWireBlock.NORTH) || state.getValue(TripWireBlock.SOUTH);
        boolean alongX = state.getValue(TripWireBlock.EAST) || state.getValue(TripWireBlock.WEST);
        if (!alongX && !alongZ) {
            alongX = true;
            alongZ = true;
        }
        double c = 0.5;
        if (alongZ) {
            sampler.box(SnagMaterial.STRING, x + c - STRING_HALF_WIDTH, minY, z, x + c + STRING_HALF_WIDTH, maxY, z + 1.0);
        }
        if (alongX) {
            sampler.box(SnagMaterial.STRING, x, minY, z + c - STRING_HALF_WIDTH, x + 1.0, maxY, z + c + STRING_HALF_WIDTH);
        }
    }

    private static boolean loaded(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos)
                && level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }
}

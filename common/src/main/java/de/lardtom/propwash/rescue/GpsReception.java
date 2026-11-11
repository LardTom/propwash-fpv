package de.lardtom.propwash.rescue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

final class GpsReception {
    static final double RAY_LENGTH = 96.0;
    static final int AZIMUTHS = 8;
    static final double[] ELEVATIONS_DEG = {90.0, 60.0, 30.0};
    static final double[] WEIGHTS = {1.5, 1.0, 0.6};
    static final double OPEN_SKY_SATELLITES = 18.0;
    static final int FIX_SATELLITES = 5;

    @FunctionalInterface
    interface Rays {
        boolean clear(Vec3 from, Vec3 to);
    }

    private GpsReception() {
    }

    static boolean fix(ServerLevel level, double x, double y, double z) {
        if (level.dimensionType().hasCeiling()) {
            return false;
        }
        if (y > level.getMaxY() + 1) {
            return true;
        }
        LoadedBlocks blocks = new LoadedBlocks(level);
        return fix(skyView((from, to) -> blocks.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                CollisionContext.empty())).getType() == HitResult.Type.MISS, x, y, z));
    }

    static boolean fix(double skyView) {
        double sky = Double.isFinite(skyView) ? Math.max(0.0, Math.min(1.0, skyView)) : 0.0;
        return OPEN_SKY_SATELLITES * Math.pow(sky, 1.6) >= FIX_SATELLITES;
    }

    static double skyView(Rays rays, double x, double y, double z) {
        Vec3 from = new Vec3(x, y, z);
        double open = 0.0;
        double total = 0.0;
        for (int e = 0; e < ELEVATIONS_DEG.length; e++) {
            double elevation = Math.toRadians(ELEVATIONS_DEG[e]);
            int count = ELEVATIONS_DEG[e] >= 90.0 ? 1 : AZIMUTHS;
            for (int a = 0; a < count; a++) {
                double azimuth = (a + 0.5 * (e % 2)) * 2.0 * Math.PI / AZIMUTHS;
                double horizontal = Math.cos(elevation);
                Vec3 dir = new Vec3(horizontal * Math.sin(azimuth), Math.sin(elevation), horizontal * Math.cos(azimuth));
                total += WEIGHTS[e];
                if (rays.clear(from, from.add(dir.scale(RAY_LENGTH)))) {
                    open += WEIGHTS[e];
                }
            }
        }
        return total > 0.0 ? open / total : 1.0;
    }

    private record LoadedBlocks(ServerLevel level) implements BlockGetter {
        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (this.level.isOutsideBuildHeight(pos)) {
                return Blocks.VOID_AIR.defaultBlockState();
            }
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return this.level.getHeight();
        }

        @Override
        public int getMinY() {
            return this.level.getMinY();
        }
    }
}

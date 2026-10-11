package de.lardtom.propwash.bando;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Map;
import java.util.function.Predicate;

public final class BandoGuard {
    static final int START_REACH = 8;

    private BandoGuard() {
    }

    public static boolean refuses(WorldGenRegion region, BlockPos pos, BlockState state) {
        ChunkPos center = region.getCenter();
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (chunkX == center.x() && chunkZ == center.z()) {
            return false;
        }
        if (welcome(state) || !region.isWithinWriteZone(pos)) {
            return false;
        }
        ChunkAccess target = region.getChunk(chunkX, chunkZ);
        if (!target.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
            return false;
        }
        return any(region, target, bando -> bando.shields(pos.getX(), pos.getY(), pos.getZ()));
    }

    public static void tidy(WorldGenLevel level, ChunkAccess chunk) {
        if (!(level instanceof WorldGenRegion region)) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        BoundingBox box = new BoundingBox(pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(), pos.getMaxBlockX(), level.getMaxY(),
                pos.getMaxBlockZ());
        any(region, chunk, bando -> {
            bando.tidy(level, box);
            return false;
        });
    }

    private static boolean any(WorldGenRegion region, ChunkAccess target, Predicate<BandoPiece> test) {
        ChunkPos center = region.getCenter();
        for (Map.Entry<Structure, LongSet> entry : target.getAllReferences().entrySet()) {
            if (!(entry.getKey() instanceof BandoStructure)) {
                continue;
            }
            for (long key : entry.getValue()) {
                int startX = ChunkPos.getX(key);
                int startZ = ChunkPos.getZ(key);
                if (Math.max(Math.abs(startX - center.x()), Math.abs(startZ - center.z())) > START_REACH
                        || !region.hasChunk(startX, startZ)) {
                    continue;
                }
                ChunkAccess origin = region.getChunk(startX, startZ, ChunkStatus.STRUCTURE_STARTS, false);
                StructureStart start = origin == null ? null : origin.getStartForStructure(entry.getKey());
                if (start == null || !start.isValid()) {
                    continue;
                }
                for (StructurePiece piece : start.getPieces()) {
                    if (piece instanceof BandoPiece bando && test.test(bando)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean welcome(BlockState state) {
        return state.is(BlockTags.LOGS) || state.getBlock() instanceof LeavesBlock || state.getBlock() instanceof VegetationBlock
                || state.is(Blocks.SNOW);
    }
}

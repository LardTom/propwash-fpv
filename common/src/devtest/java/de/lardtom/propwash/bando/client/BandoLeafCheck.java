package de.lardtom.propwash.bando.client;

import de.lardtom.propwash.bando.BandoPiece;
import de.lardtom.propwash.bando.plan.LeafDistance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;

final class BandoLeafCheck {
    private static final int EXAMPLES = 6;

    record Result(int leaves, int orphans, List<String> orphansAt, int decaying) {
    }

    private BandoLeafCheck() {
    }

    static Result check(ServerLevel level, BandoPiece piece) {
        BoundingBox area = piece.leafArea();
        int reach = LeafDistance.DECAY - 1;
        int minY = Math.max(area.minY() - reach, level.getMinY());
        int maxY = Math.min(area.maxY() + reach, level.getMaxY());
        Set<Long> crown = new HashSet<>();
        for (BlockPos at : BlockPos.betweenClosed(area.minX() - reach, minY, area.minZ() - reach, area.maxX() + reach, maxY, area.maxZ() + reach)) {
            if (level.getBlockState(at).hasProperty(LeavesBlock.DISTANCE)) {
                crown.add(at.asLong());
            }
        }
        LongUnaryOperator[] neighbours = new LongUnaryOperator[Direction.values().length];
        for (Direction direction : Direction.values()) {
            neighbours[direction.ordinal()] = at -> BlockPos.offset(at, direction);
        }
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        Set<Long> orphans = LeafDistance.orphans(crown, neighbours, at -> {
            probe.set(at);
            BlockState state = level.getBlockState(probe);
            return state.is(BlockTags.PREVENTS_NEARBY_LEAF_DECAY) && !piece.ownBlock(state, probe);
        });
        Map<Long, Boolean> decays = new HashMap<>();
        Map<Long, Set<BlockPos>> ticks = new HashMap<>();
        for (long key : orphans) {
            if (!decays.containsKey(key) && natural(level.getBlockState(BlockPos.of(key)))) {
                cluster(level, piece, orphans, key, neighbours, decays, ticks);
            }
        }
        int leaves = 0;
        int count = 0;
        int decaying = 0;
        List<String> at = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long key : crown) {
            pos.set(key);
            BlockState state = level.getBlockState(pos);
            if (!area.isInside(pos) || !natural(state)) {
                continue;
            }
            leaves++;
            if (!orphans.contains(key)) {
                continue;
            }
            if (decays.getOrDefault(key, false)) {
                decaying++;
                continue;
            }
            count++;
            if (at.size() < EXAMPLES) {
                at.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " (Abstand "
                        + state.getValue(LeavesBlock.DISTANCE) + ")");
            }
        }
        return new Result(leaves, count, at, decaying);
    }

    private static boolean natural(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static void cluster(ServerLevel level, BandoPiece piece, Set<Long> orphans, long start, LongUnaryOperator[] neighbours,
                                Map<Long, Boolean> decays, Map<Long, Set<BlockPos>> ticks) {
        List<Long> members = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        boolean own = false;
        boolean pending = false;
        boolean settled = true;
        while (!queue.isEmpty()) {
            long key = queue.poll();
            BlockPos pos = BlockPos.of(key);
            BlockState state = level.getBlockState(pos);
            members.add(key);
            own |= touchesOwnTrunk(level, piece, pos);
            pending |= leafTicks(level, pos, ticks).contains(pos);
            settled &= state.getValue(LeavesBlock.DISTANCE) >= LeafDistance.DECAY;
            for (LongUnaryOperator side : neighbours) {
                long next = side.applyAsLong(key);
                if (orphans.contains(next) && seen.add(next) && natural(level.getBlockState(BlockPos.of(next)))) {
                    queue.add(next);
                }
            }
        }
        boolean decaying = !own && (pending || settled);
        for (long member : members) {
            decays.put(member, decaying);
        }
    }

    private static Set<BlockPos> leafTicks(ServerLevel level, BlockPos pos, Map<Long, Set<BlockPos>> cache) {
        ChunkPos chunk = ChunkPos.containing(pos);
        return cache.computeIfAbsent(chunk.pack(), key -> {
            Set<BlockPos> found = new HashSet<>();
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
            if (loaded != null && loaded.getBlockTicks() instanceof LevelChunkTicks<Block> container) {
                for (SavedTick<Block> tick : container.pack(level.getGameTime())) {
                    if (tick.type() instanceof LeavesBlock) {
                        found.add(tick.pos());
                    }
                }
            }
            return found;
        });
    }

    private static boolean touchesOwnTrunk(ServerLevel level, BandoPiece piece, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState state = level.getBlockState(next);
            if (state.is(BlockTags.PREVENTS_NEARBY_LEAF_DECAY) && piece.ownBlock(state, next)) {
                return true;
            }
        }
        return false;
    }
}

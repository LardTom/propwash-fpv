package de.lardtom.propwash.bando;

import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPalettes;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoSite;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.LeafDistance;
import de.lardtom.propwash.bando.plan.Mat;
import de.lardtom.propwash.bando.plan.Meta;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongUnaryOperator;

public final class BandoPiece extends StructurePiece {
    private static final String VARIANT = "Variant";
    private static final String SEED = "Seed";
    private static final String TURNS = "Turns";
    private static final String ORIGIN_X = "OX";
    private static final String ORIGIN_Y = "OY";
    private static final String ORIGIN_Z = "OZ";
    private static final String SIZE_X = "SX";
    private static final String SIZE_Z = "SZ";
    private static final int FILL_DEPTH = 64;

    private final BandoVariant variant;
    private final long seed;
    private final BandoTransform transform;
    private volatile @Nullable SoftReference<BandoPlan> plan;
    private volatile @Nullable BandoBlocks blocks;
    private volatile boolean @Nullable [] site;

    public BandoPiece(BandoPlan plan, BandoTransform transform) {
        super(BandoModule.PIECE.get(), 0, box(plan, transform));
        this.variant = plan.variant();
        this.seed = plan.seed();
        this.transform = transform;
        this.plan = new SoftReference<>(plan);
    }

    public BandoPiece(CompoundTag tag) {
        super(BandoModule.PIECE.get(), tag);
        this.variant = BandoVariant.byKey(tag.getStringOr(VARIANT, "")).orElse(BandoVariant.BARN);
        this.seed = tag.getLongOr(SEED, 0L);
        int sizeX = tag.getIntOr(SIZE_X, 0);
        int sizeZ = tag.getIntOr(SIZE_Z, 0);
        if (sizeX <= 0 || sizeZ <= 0) {
            BandoPlan generated = BandoGenerator.generate(this.variant, this.seed);
            sizeX = generated.sizeX();
            sizeZ = generated.sizeZ();
            this.plan = new SoftReference<>(generated);
        }
        this.transform = new BandoTransform(tag.getIntOr(ORIGIN_X, 0), tag.getIntOr(ORIGIN_Y, 0), tag.getIntOr(ORIGIN_Z, 0),
                tag.getIntOr(TURNS, 0), sizeX, sizeZ);
    }

    public static BoundingBox box(BandoPlan plan, BandoTransform t) {
        int bottom = BandoSite.boxBottom(plan);
        return new BoundingBox(t.originX(), t.worldY(bottom), t.originZ(), t.originX() + t.widthX() - 1, t.worldY(plan.sizeY() - 1),
                t.originZ() + t.widthZ() - 1);
    }

    public BandoVariant variant() {
        return this.variant;
    }

    public long seed() {
        return this.seed;
    }

    public BandoTransform transform() {
        return this.transform;
    }

    public BandoPlan plan() {
        SoftReference<BandoPlan> reference = this.plan;
        BandoPlan cached = reference == null ? null : reference.get();
        if (cached == null) {
            cached = BandoGenerator.generate(this.variant, this.seed);
            this.plan = new SoftReference<>(cached);
        }
        return cached;
    }

    public BandoBlocks blocks() {
        BandoBlocks resolver = this.blocks;
        if (resolver == null) {
            resolver = new BandoBlocks(BandoPalettes.pick(this.variant, this.seed), this.transform.turns());
            this.blocks = resolver;
        }
        return resolver;
    }

    private boolean[] site(BandoPlan p) {
        boolean[] columns = this.site;
        if (columns == null) {
            columns = BandoSite.siteColumns(p);
            this.site = columns;
        }
        return columns;
    }

    public @Nullable BlockPos chestPos() {
        BandoPlan p = this.plan();
        if (!p.hasChest()) {
            return null;
        }
        return new BlockPos(this.transform.worldX(p.chestX(), p.chestZ()), this.transform.worldY(p.chestY()),
                this.transform.worldZ(p.chestX(), p.chestZ()));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString(VARIANT, this.variant.key());
        tag.putLong(SEED, this.seed);
        tag.putInt(TURNS, this.transform.turns());
        tag.putInt(ORIGIN_X, this.transform.originX());
        tag.putInt(ORIGIN_Y, this.transform.originY());
        tag.putInt(ORIGIN_Z, this.transform.originZ());
        tag.putInt(SIZE_X, this.transform.sizeX());
        tag.putInt(SIZE_Z, this.transform.sizeZ());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        this.place(level, chunkBB, random);
    }

    public int place(WorldGenLevel level, BoundingBox area, RandomSource random) {
        BandoPlan p = this.plan();
        BandoBlocks resolver = this.blocks();
        BandoTransform t = this.transform;
        int minX = Math.max(this.boundingBox.minX(), area.minX());
        int maxX = Math.min(this.boundingBox.maxX(), area.maxX());
        int minZ = Math.max(this.boundingBox.minZ(), area.minZ());
        int maxZ = Math.min(this.boundingBox.maxZ(), area.maxZ());
        int placed = 0;
        List<BlockPos> shapes = level instanceof WorldGenRegion ? null : new ArrayList<>();
        List<BlockPos> cut = new ArrayList<>();
        boolean[] site = this.site(p);
        boolean underpin = p.variant().family().terrain() != BandoFamily.Terrain.SHORE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                int x = t.localX(wx, wz);
                int z = t.localZ(wx, wz);
                if (underpin && site[z * p.sizeX() + x]) {
                    placed += this.underpin(level, p, x, z, area, resolver, pos.set(wx, t.worldY(p.ground()), wz));
                }
                int bottom = -1;
                for (int y = 0; y < p.sizeY(); y++) {
                    Mat mat = p.mat(x, y, z);
                    if (mat == Mat.EMPTY || mat == Mat.CHEST) {
                        continue;
                    }
                    if (bottom < 0 && mat.holdsUp() && mat != Mat.YARD) {
                        bottom = y;
                    }
                    int wy = t.worldY(y);
                    if (wy < area.minY() || wy > area.maxY()) {
                        continue;
                    }
                    pos.set(wx, wy, wz);
                    boolean crown = mat.kind() == Mat.Kind.STRUCTURE && leaves(level.getBlockState(pos));
                    if (this.put(level, pos, mat, p.meta(x, y, z), p.mat(x, y - 1, z), resolver, shapes)) {
                        placed++;
                        if (crown) {
                            cut.add(pos.immutable());
                        }
                    } else {
                        uproot(level, pos);
                    }
                    if (p.mat(x, y + 1, z) == Mat.EMPTY && wy + 1 <= area.maxY()) {
                        uproot(level, pos.above());
                    }
                }
                if (site[z * p.sizeX() + x]) {
                    placed += clearAbove(level, p, t, x, z, wx, wz, area, pos);
                }
                placed += this.foundation(level, p, x, z, bottom, area, resolver, pos.set(wx, 0, wz));
            }
        }
        if (!cut.isEmpty()) {
            settleLeaves(level, cut, area);
        }
        if (shapes != null) {
            for (BlockPos at : shapes) {
                BlockState state = level.getBlockState(at);
                BlockState shaped = Block.updateFromNeighbourShapes(state, level, at);
                if (shaped != state) {
                    level.setBlock(at, shaped, 2);
                }
            }
        }
        BlockPos chest = this.chestPos();
        if (chest != null && area.isInside(chest)) {
            BlockState state = resolver.state(Mat.CHEST, p.chestFacing(), 0L);
            if (state != null && !level.getBlockState(chest).is(Blocks.CHEST)) {
                if (onGround(p.mat(p.chestX(), p.chestY() - 1, p.chestZ())) && !sturdy(level, chest.below())) {
                    BlockState base = resolver.state(Mat.FOUNDATION, Meta.AXIS_Y, BandoPalettes.hash(this.seed, chest.getX(), chest.getY() - 1, chest.getZ()));
                    if (base != null) {
                        level.setBlock(chest.below(), base, 2);
                    }
                }
                level.setBlock(chest, waterlogged(state, level.getFluidState(chest)), 2);
                RandomizableContainer.setBlockEntityLootTable(level, random, chest, BandoModule.LOOT);
                placed++;
            }
        }
        return placed;
    }

    public boolean shields(int wx, int wy, int wz) {
        BoundingBox box = this.boundingBox;
        if (wx < box.minX() || wx > box.maxX() || wz < box.minZ() || wz > box.maxZ() || wy > box.maxY()) {
            return false;
        }
        BandoTransform t = this.transform;
        if (wy < t.worldY(0) - FILL_DEPTH) {
            return false;
        }
        BandoPlan p = this.plan();
        int x = t.localX(wx, wz);
        int z = t.localZ(wx, wz);
        if (x < 0 || z < 0 || x >= p.sizeX() || z >= p.sizeZ()) {
            return false;
        }
        return this.site(p)[z * p.sizeX() + x];
    }

    public int tidy(WorldGenLevel level, BoundingBox chunk) {
        BoundingBox box = this.boundingBox;
        int minX = Math.max(box.minX(), chunk.minX());
        int maxX = Math.min(box.maxX(), chunk.maxX());
        int minZ = Math.max(box.minZ(), chunk.minZ());
        int maxZ = Math.min(box.maxZ(), chunk.maxZ());
        if (minX > maxX || minZ > maxZ) {
            return 0;
        }
        BandoPlan p = this.plan();
        BandoTransform t = this.transform;
        boolean[] site = this.site(p);
        int from = BandoSite.clearFrom(p);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cleared = 0;
        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                int x = t.localX(wx, wz);
                int z = t.localZ(wx, wz);
                if (x < 0 || z < 0 || x >= p.sizeX() || z >= p.sizeZ() || !site[z * p.sizeX() + x]) {
                    continue;
                }
                int pillar = p.fill(x, z) == Mat.EMPTY ? -1 : lowest(p, x, z);
                for (int y = Math.max(from, pillar); y < p.sizeY(); y++) {
                    Mat mat = p.mat(x, y, z);
                    int wy = t.worldY(y);
                    if (mat != Mat.EMPTY && mat != Mat.AIR || wy < chunk.minY() || wy > chunk.maxY()) {
                        continue;
                    }
                    pos.set(wx, wy, wz);
                    BlockState state = level.getBlockState(pos);
                    if (!terrain(state) || grows(state)) {
                        continue;
                    }
                    level.setBlock(pos, wy < level.getSeaLevel() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                    cleared++;
                    if (wy + 1 <= chunk.maxY()) {
                        uproot(level, pos.above());
                    }
                }
            }
        }
        return cleared;
    }

    private static int lowest(BandoPlan p, int x, int z) {
        for (int y = 0; y < p.sizeY(); y++) {
            Mat mat = p.mat(x, y, z);
            if (mat != Mat.EMPTY && mat != Mat.CHEST && mat.holdsUp() && mat != Mat.YARD) {
                return y;
            }
        }
        return -1;
    }

    private int foundation(WorldGenLevel level, BandoPlan p, int x, int z, int bottom, BoundingBox area, BandoBlocks resolver,
                           BlockPos.MutableBlockPos pos) {
        Mat fill = p.fill(x, z);
        if (fill == Mat.EMPTY) {
            return 0;
        }
        BlockState state = resolver.state(fill, Meta.AXIS_Y, BandoPalettes.hash(this.seed, pos.getX(), 0, pos.getZ()));
        if (state == null) {
            return 0;
        }
        pos.setY(this.transform.worldY(Math.max(bottom, 0)) - 1);
        int depth = 0;
        while (pos.getY() > Math.max(level.getMinY() + 1, area.minY()) && replaceable(level.getBlockState(pos)) && depth < FILL_DEPTH) {
            level.setBlock(pos, waterlogged(state, level.getFluidState(pos)), 2);
            pos.move(0, -1, 0);
            depth++;
        }
        return depth;
    }

    private static void settleLeaves(WorldGenLevel level, List<BlockPos> cut, BoundingBox area) {
        BoundingBox box = BoundingBox.encapsulatingPositions(cut).orElseThrow().inflatedBy(LeafDistance.DECAY);
        int minY = Math.max(box.minY(), level.getMinY());
        int maxY = Math.min(box.maxY(), level.getMaxY());
        Set<Long> leaves = new HashSet<>();
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), minY, box.minZ(), box.maxX(), maxY, box.maxZ())) {
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.DISTANCE)) {
                leaves.add(at.asLong());
            }
        }
        LongUnaryOperator[] neighbours = new LongUnaryOperator[Direction.values().length];
        for (Direction direction : Direction.values()) {
            neighbours[direction.ordinal()] = at -> BlockPos.offset(at, direction);
        }
        Map<Long, Integer> distance = LeafDistance.settle(leaves, neighbours,
                at -> LeavesBlock.getOptionalDistanceAt(level.getBlockState(BlockPos.of(at))).orElse(LeafDistance.DECAY));
        for (Map.Entry<Long, Integer> leaf : distance.entrySet()) {
            BlockPos at = BlockPos.of(leaf.getKey());
            if (!area.isInside(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (state.getValue(LeavesBlock.DISTANCE) != leaf.getValue().intValue()) {
                level.setBlock(at, state.setValue(LeavesBlock.DISTANCE, leaf.getValue()), 2);
            }
        }
    }

    private int underpin(WorldGenLevel level, BandoPlan p, int x, int z, BoundingBox area, BandoBlocks resolver, BlockPos.MutableBlockPos pos) {
        Mat ground = p.mat(x, p.ground(), z);
        if (ground != Mat.YARD && ground != Mat.EMPTY || p.fill(x, z) != Mat.EMPTY || pos.getY() < area.minY() || pos.getY() > area.maxY()) {
            return 0;
        }
        BlockState existing = level.getBlockState(pos);
        if (!replaceable(existing) || tree(existing)) {
            return 0;
        }
        long hash = BandoPalettes.hash(this.seed, pos.getX(), 0, pos.getZ());
        BlockState base = resolver.state(Mat.FOUNDATION, Meta.AXIS_Y, hash);
        BlockState yard = resolver.state(Mat.YARD, Meta.AXIS_Y, BandoPalettes.hash(this.seed, pos.getX(), pos.getY(), pos.getZ()));
        BlockState top = yard != null ? yard : base;
        if (base == null || top == null) {
            return 0;
        }
        int topY = pos.getY();
        int filled = 0;
        pos.move(0, -1, 0);
        while (pos.getY() > Math.max(level.getMinY() + 1, area.minY()) && replaceable(level.getBlockState(pos)) && filled < FILL_DEPTH) {
            level.setBlock(pos, waterlogged(base, level.getFluidState(pos)), 2);
            pos.move(0, -1, 0);
            filled++;
        }
        pos.setY(topY);
        level.setBlock(pos, waterlogged(top, existing.getFluidState()), 2);
        return filled + 1;
    }

    private static boolean grows(BlockState state) {
        Block block = state.getBlock();
        return block instanceof VegetationBlock || block instanceof CocoaBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock
                || block instanceof BambooStalkBlock || block instanceof MossyCarpetBlock || state.is(Blocks.MOSS_CARPET);
    }

    private static int clearAbove(WorldGenLevel level, BandoPlan p, BandoTransform t, int x, int z, int wx, int wz, BoundingBox area,
                                  BlockPos.MutableBlockPos pos) {
        int cleared = 0;
        for (int y = BandoSite.clearFrom(p); y < p.sizeY(); y++) {
            int wy = t.worldY(y);
            if (p.mat(x, y, z) != Mat.EMPTY || wy < area.minY() || wy > area.maxY()) {
                continue;
            }
            pos.set(wx, wy, wz);
            if (!terrain(level.getBlockState(pos))) {
                continue;
            }
            level.setBlock(pos, wy < level.getSeaLevel() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            cleared++;
            if (wy + 1 <= area.maxY()) {
                uproot(level, pos.above());
            }
        }
        return cleared;
    }

    private static boolean tree(BlockState state) {
        return state.is(BlockTags.LOGS) || leaves(state);
    }

    private static boolean leaves(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static void uproot(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof VegetationBlock || state.is(Blocks.SNOW)) || state.canSurvive(level, pos)) {
            return;
        }
        boolean lower = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 2);
        if (lower) {
            BlockPos up = pos.above();
            BlockState upper = level.getBlockState(up);
            if (upper.is(state.getBlock())) {
                level.setBlock(up, upper.getFluidState().createLegacyBlock(), 2);
            }
        }
    }

    private static boolean onGround(Mat below) {
        return below == Mat.EMPTY || below == Mat.YARD;
    }

    private static boolean supported(WorldGenLevel level, BlockPos pos, Mat below) {
        if (onGround(below)) {
            return sturdy(level, pos.below());
        }
        return !rests(below) || !replaceable(level.getBlockState(pos.below()));
    }

    private static boolean sturdy(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean rests(Mat mat) {
        return mat.kind() == Mat.Kind.LOOSE || mat == Mat.MOSS || mat == Mat.FOLIAGE;
    }

    private static boolean terrain(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !tree(state);
    }

    private boolean put(WorldGenLevel level, BlockPos pos, Mat mat, int meta, Mat below, BandoBlocks resolver, @Nullable List<BlockPos> shapes) {
        BlockState existing = level.getBlockState(pos);
        FluidState fluid = existing.getFluidState();
        if (mat == Mat.YARD ? !terrain(existing) : rests(mat) && !supported(level, pos, below)) {
            return false;
        }
        if (tree(existing) && !(mat.kind() == Mat.Kind.STRUCTURE && leaves(existing))) {
            return false;
        }
        if (mat == Mat.AIR) {
            if (existing.isAir() || !fluid.isEmpty()) {
                return false;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            return true;
        }
        if (mat == Mat.WATER) {
            if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
                return false;
            }
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            level.scheduleTick(pos, Fluids.WATER, 0);
            return true;
        }
        if (mat.kind() == Mat.Kind.DECOR && !(existing.isAir() || existing.canBeReplaced()) || mat.kind() == Mat.Kind.DECOR && mat != Mat.FLOAT && !fluid.isEmpty()) {
            return false;
        }
        BlockState state = resolver.state(mat, meta, BandoPalettes.hash(this.seed, pos.getX(), pos.getY(), pos.getZ()));
        if (state == null) {
            return false;
        }
        state = waterlogged(state, fluid);
        level.setBlock(pos, state, 2);
        if (BandoBlocks.needsShapeUpdate(state)) {
            if (shapes == null) {
                level.getChunk(pos).markPosForPostProcessing(pos);
            } else {
                shapes.add(pos.immutable());
            }
        }
        return true;
    }

    private static BlockState waterlogged(BlockState state, FluidState fluid) {
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            return state;
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return state.setValue(BlockStateProperties.WATERLOGGED, fluid.is(FluidTags.WATER) && fluid.isSource());
        }
        return state;
    }

    private static boolean replaceable(BlockState state) {
        return state.isAir() || state.liquid() || state.canBeReplaced() || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }
}

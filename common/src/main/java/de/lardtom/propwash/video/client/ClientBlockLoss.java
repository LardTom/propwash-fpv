package de.lardtom.propwash.video.client;

import de.lardtom.propwash.relay.SignalRepeaterBlock;
import de.lardtom.propwash.video.Multipath;
import de.lardtom.propwash.video.link.Medium;
import de.lardtom.propwash.video.link.ObstructionTracer;
import de.lardtom.propwash.video.link.RfMaterial;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

final class ClientBlockLoss implements ObstructionTracer.World, Multipath.Reflectivity {
    static final double LEGACY_SOLID_DB = 7.0;
    static final double LEGACY_LIQUID_DB = 3.5;
    static final double LEGACY_FOLIAGE_DB = 1.2;
    static final double LEGACY_THIN_DB = 1.5;
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("ores"));
    private static final Set<SoundType> METAL_SOUNDS = Set.of(SoundType.METAL, SoundType.COPPER, SoundType.COPPER_BULB,
            SoundType.NETHERITE_BLOCK, SoundType.ANVIL, SoundType.LANTERN, SoundType.HEAVY_CORE, SoundType.VAULT,
            SoundType.TRIAL_SPAWNER, SoundType.SPAWNER, SoundType.IRON, SoundType.COPPER_GOLEM_STATUE);
    private static final Set<SoundType> WOOD_SOUNDS = Set.of(SoundType.WOOD, SoundType.CHERRY_WOOD, SoundType.BAMBOO_WOOD,
            SoundType.NETHER_WOOD, SoundType.HANGING_SIGN, SoundType.NETHER_WOOD_HANGING_SIGN, SoundType.BAMBOO_WOOD_HANGING_SIGN,
            SoundType.CHERRY_WOOD_HANGING_SIGN, SoundType.CHISELED_BOOKSHELF, SoundType.SHELF, SoundType.LADDER,
            SoundType.SCAFFOLDING, SoundType.BAMBOO, SoundType.STEM);
    private static final Set<SoundType> PLANT_SOUNDS = Set.of(SoundType.GRASS, SoundType.WET_GRASS, SoundType.CROP,
            SoundType.HARD_CROP, SoundType.VINE, SoundType.SWEET_BERRY_BUSH, SoundType.BAMBOO_SAPLING, SoundType.AZALEA,
            SoundType.FLOWERING_AZALEA, SoundType.CAVE_VINES, SoundType.SPORE_BLOSSOM, SoundType.PINK_PETALS, SoundType.LEAF_LITTER,
            SoundType.MOSS_CARPET, SoundType.BIG_DRIPLEAF, SoundType.SMALL_DRIPLEAF, SoundType.HANGING_ROOTS, SoundType.ROOTS,
            SoundType.NETHER_SPROUTS, SoundType.FUNGUS, SoundType.WEEPING_VINES, SoundType.TWISTING_VINES, SoundType.LILY_PAD,
            SoundType.CACTUS_FLOWER, SoundType.GLOW_LICHEN, SoundType.CHERRY_SAPLING, SoundType.NETHER_WART, SoundType.SCULK_VEIN,
            SoundType.RED_SHRUB, SoundType.SHELF_MUSHROOM);
    private static final Set<SoundType> LEAF_SOUNDS = Set.of(SoundType.AZALEA_LEAVES, SoundType.CHERRY_LEAVES, SoundType.POPLAR_LEAVES);
    private static final Set<SoundType> EARTH_SOUNDS = Set.of(SoundType.GRAVEL, SoundType.ROOTED_DIRT, SoundType.MUD,
            SoundType.SOUL_SOIL, SoundType.NYLIUM, SoundType.NETHERRACK, SoundType.BASALT, SoundType.SUSPICIOUS_GRAVEL,
            SoundType.MOSS, SoundType.SCULK, SoundType.DRIPSTONE_BLOCK, SoundType.CALCITE, SoundType.NETHER_ORE,
            SoundType.NETHER_GOLD_ORE, SoundType.ANCIENT_DEBRIS, SoundType.MANGROVE_ROOTS, SoundType.MUDDY_MANGROVE_ROOTS);
    private static final Set<SoundType> SAND_SOUNDS = Set.of(SoundType.SAND, SoundType.SUSPICIOUS_SAND, SoundType.SOUL_SAND);
    private static final Set<SoundType> CLOTH_SOUNDS = Set.of(SoundType.WOOL, SoundType.STRAW_BED);
    private static final Set<SoundType> WATERY_SOUNDS = Set.of(SoundType.SLIME_BLOCK, SoundType.HONEY_BLOCK, SoundType.WET_SPONGE,
            SoundType.FROGSPAWN, SoundType.CORAL_BLOCK, SoundType.WART_BLOCK, SoundType.SHROOMLIGHT, SoundType.FROGLIGHT,
            SoundType.CREAKING_HEART);

    private final Reference2ObjectOpenHashMap<BlockState, Medium> cache = new Reference2ObjectOpenHashMap<>();
    private @Nullable Level level;
    private @Nullable LevelChunk chunk;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;

    ClientBlockLoss() {
    }

    void bind(@Nullable Level level) {
        if (level != this.level) {
            this.level = level;
            this.cache.clear();
        }
        this.forgetChunk();
    }

    void forgetChunk() {
        this.chunk = null;
        this.chunkX = Integer.MIN_VALUE;
        this.chunkZ = Integer.MIN_VALUE;
    }

    @Override
    public Medium medium(int x, int y, int z) {
        Level current = this.level;
        if (current == null || y < current.getMinY() || y > current.getMaxY()) {
            return Medium.AIR;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != this.chunkX || cz != this.chunkZ) {
            this.chunk = current.getChunkSource().getChunkNow(cx, cz);
            this.chunkX = cx;
            this.chunkZ = cz;
        }
        LevelChunk c = this.chunk;
        if (c == null) {
            return Medium.AIR;
        }
        int index = current.getSectionIndex(y);
        LevelChunkSection[] sections = c.getSections();
        if (index < 0 || index >= sections.length) {
            return Medium.AIR;
        }
        LevelChunkSection section = sections[index];
        if (section.hasOnlyAir()) {
            return Medium.AIR;
        }
        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        if (state.isAir()) {
            return Medium.AIR;
        }
        Medium cached = this.cache.get(state);
        if (cached != null) {
            return cached;
        }
        Medium created = classify(state);
        this.cache.put(state, created);
        return created;
    }

    @Override
    public double reflectivity(int x, int y, int z) {
        return this.medium(x, y, z).reflectivity();
    }

    static Medium classify(BlockState state) {
        if (state.isAir()) {
            return Medium.AIR;
        }
        double reflectivity = legacyReflectivity(state);
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            if (fluid.is(FluidTags.WATER)) {
                return new Medium(RfMaterial.WATER, null, reflectivity);
            }
            if (fluid.is(FluidTags.LAVA)) {
                return new Medium(RfMaterial.EARTH, null, reflectivity);
            }
        }
        RfMaterial material = material(state);
        if (material == RfMaterial.AIR) {
            return Medium.AIR;
        }
        VoxelShape shape;
        try {
            shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            return new Medium(material, null, reflectivity);
        }
        if (shape.isEmpty()) {
            return Medium.AIR;
        }
        if (Block.isShapeFullBlock(shape)) {
            return new Medium(material, null, reflectivity);
        }
        List<AABB> boxes = shape.toAabbs();
        double[] flat = new double[boxes.size() * 6];
        int i = 0;
        for (AABB box : boxes) {
            flat[i++] = box.minX;
            flat[i++] = box.minY;
            flat[i++] = box.minZ;
            flat[i++] = box.maxX;
            flat[i++] = box.maxY;
            flat[i++] = box.maxZ;
        }
        return new Medium(material, flat, reflectivity);
    }

    static RfMaterial material(BlockState state) {
        if (state.is(Blocks.BARRIER) || state.is(Blocks.LIGHT) || state.is(Blocks.STRUCTURE_VOID) || state.getBlock() instanceof SignalRepeaterBlock) {
            return RfMaterial.AIR;
        }
        SoundType sound = state.getSoundType();
        if (state.is(BlockTags.LEAVES) || LEAF_SOUNDS.contains(sound)) {
            return RfMaterial.FOLIAGE;
        }
        if (state.is(BlockTags.LOGS) || state.is(Blocks.CACTUS) || state.is(Blocks.MUSHROOM_STEM)) {
            return RfMaterial.TRUNK;
        }
        if (state.is(BlockTags.ICE)) {
            return RfMaterial.ICE;
        }
        if (state.is(BlockTags.SNOW) || sound == SoundType.SNOW || sound == SoundType.POWDER_SNOW) {
            return RfMaterial.SNOW;
        }
        if (state.is(BlockTags.IMPERMEABLE) || sound == SoundType.GLASS) {
            return RfMaterial.GLASS;
        }
        if (state.is(BlockTags.BARS) || state.is(BlockTags.CHAINS) || sound == SoundType.COPPER_GRATE || sound == SoundType.CHAIN) {
            return RfMaterial.METAL_MESH;
        }
        if (METAL_SOUNDS.contains(sound) || state.is(BlockTags.ANVIL) || state.is(BlockTags.RAILS) || state.is(BlockTags.CAULDRONS)) {
            return RfMaterial.METAL;
        }
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.WOOL_SLABS)
                || state.is(BlockTags.WOOL_STAIRS) || state.is(BlockTags.BEDS) || state.is(BlockTags.BANNERS) || CLOTH_SOUNDS.contains(sound)) {
            return RfMaterial.CLOTH;
        }
        if (state.is(BlockTags.CONCRETE) || state.is(BlockTags.CONCRETE_SLABS) || state.is(BlockTags.CONCRETE_STAIRS)) {
            return RfMaterial.CONCRETE;
        }
        if (state.is(BlockTags.SAND) || state.is(BlockTags.CONCRETE_POWDERS) || SAND_SOUNDS.contains(sound)) {
            return RfMaterial.SAND;
        }
        if (WATERY_SOUNDS.contains(sound) || state.is(BlockTags.CORAL_BLOCKS)) {
            return RfMaterial.WATER;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.SUBSTRATE_OVERWORLD)
                || state.is(BlockTags.NYLIUM) || state.is(ORES) || state.is(Blocks.CLAY) || state.is(Blocks.BEDROCK)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.END_STONE) || EARTH_SOUNDS.contains(sound)) {
            return RfMaterial.EARTH;
        }
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS) || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.WOODEN_SHELVES)
                || WOOD_SOUNDS.contains(sound)) {
            return RfMaterial.TIMBER;
        }
        if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.CAVE_VINES) || state.is(BlockTags.CORAL_PLANTS)
                || state.is(Blocks.COBWEB) || PLANT_SOUNDS.contains(sound)) {
            return RfMaterial.PLANT;
        }
        return RfMaterial.MASONRY;
    }

    static double legacyReflectivity(BlockState state) {
        if (state.isAir()) {
            return 0.0;
        }
        double loss;
        if (!state.getFluidState().isEmpty() && !state.canOcclude()) {
            loss = LEGACY_LIQUID_DB;
        } else if (state.is(BlockTags.LEAVES)) {
            loss = LEGACY_FOLIAGE_DB;
        } else if (state.canOcclude()) {
            loss = LEGACY_SOLID_DB;
        } else {
            loss = LEGACY_THIN_DB;
        }
        return Math.min(1.0, loss / LEGACY_SOLID_DB);
    }
}

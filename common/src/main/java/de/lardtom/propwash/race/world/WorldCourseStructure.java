package de.lardtom.propwash.race.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class WorldCourseStructure extends Structure {
    public static final int EDGE_INSET = 1;

    public record BiomeTemplate(HolderSet<Biome> biomes, Identifier template) {
        public static final Codec<BiomeTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.holderSet(Registries.BIOME).fieldOf("biomes").forGetter(BiomeTemplate::biomes),
                Identifier.CODEC.fieldOf("template").forGetter(BiomeTemplate::template)
        ).apply(i, BiomeTemplate::new));
    }

    public static final MapCodec<WorldCourseStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Identifier.CODEC.fieldOf("template").forGetter(WorldCourseStructure::template),
            BiomeTemplate.CODEC.listOf().optionalFieldOf("biome_templates", List.of()).forGetter(WorldCourseStructure::biomeTemplates),
            Codec.intRange(0, 32).optionalFieldOf("max_slope", 6).forGetter(WorldCourseStructure::maxSlope),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(WorldCourseStructure::chance)
    ).apply(i, WorldCourseStructure::new));

    private final Identifier template;
    private final List<BiomeTemplate> biomeTemplates;
    private final int maxSlope;
    private final float chance;

    public WorldCourseStructure(StructureSettings settings, Identifier template, List<BiomeTemplate> biomeTemplates, int maxSlope, float chance) {
        super(settings);
        this.template = template;
        this.biomeTemplates = List.copyOf(biomeTemplates);
        this.maxSlope = maxSlope;
        this.chance = chance;
    }

    public Identifier template() {
        return this.template;
    }

    public List<BiomeTemplate> biomeTemplates() {
        return this.biomeTemplates;
    }

    public int maxSlope() {
        return this.maxSlope;
    }

    public float chance() {
        return this.chance;
    }

    public Identifier templateFor(Holder<Biome> biome) {
        for (BiomeTemplate entry : this.biomeTemplates) {
            if (entry.biomes().contains(biome)) {
                return entry.template();
            }
        }
        return this.template;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!WorldCourses.generationEnabled()) {
            return Optional.empty();
        }
        WorldgenRandom random = context.random();
        if (this.chance < 1.0F && random.nextFloat() >= this.chance) {
            return Optional.empty();
        }
        if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
            return Optional.empty();
        }
        Rotation rotation = Rotation.getRandom(random);
        ChunkPos chunk = context.chunkPos();
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState randomState = context.randomState();
        int centerY = generator.getFirstOccupiedHeight(centerX, centerZ, Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
        Holder<Biome> centerBiome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(centerX), QuartPos.fromBlock(centerY),
                QuartPos.fromBlock(centerZ));
        Identifier templateId = this.templateFor(centerBiome);
        Optional<StructureTemplate> template = context.structureTemplateManager().get(templateId);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        BoundingBox local = template.get().getBoundingBox(WorldCoursePiece.settings(rotation), BlockPos.ZERO);
        int originX = centerX - (local.minX() + local.maxX()) / 2;
        int originZ = centerZ - (local.minZ() + local.maxZ()) / 2;
        int minX = originX + local.minX() + EDGE_INSET;
        int maxX = originX + local.maxX() - EDGE_INSET;
        int minZ = originZ + local.minZ() + EDGE_INSET;
        int maxZ = originZ + local.maxZ() - EDGE_INSET;
        int[] xs = {minX, (minX + maxX) / 2, maxX};
        int[] zs = {minZ, (minZ + maxZ) / 2, maxZ};
        int[] heights = new int[9];
        int n = 0;
        for (int x : xs) {
            for (int z : zs) {
                int surface = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
                int ground = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState);
                if (surface != ground) {
                    return Optional.empty();
                }
                Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z));
                if (!context.validBiome().test(biome)) {
                    return Optional.empty();
                }
                heights[n++] = surface;
            }
        }
        Arrays.sort(heights);
        if (!WorldCourseSite.acceptable(heights, this.maxSlope, height.getMinY(), height.getMaxY() - local.getYSpan())) {
            return Optional.empty();
        }
        int y = heights[heights.length / 2];
        BlockPos origin = new BlockPos(originX, y, originZ);
        return Optional.of(new GenerationStub(new BlockPos(centerX, y, centerZ),
                builder -> builder.addPiece(new WorldCoursePiece(context.structureTemplateManager(), templateId, origin, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return WorldCourses.STRUCTURE_TYPE.get();
    }
}

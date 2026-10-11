package de.lardtom.propwash.bando;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoSite;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.List;
import java.util.Optional;

public final class BandoStructure extends Structure {
    public static final Codec<BandoFamily> FAMILY_CODEC = Codec.STRING.comapFlatMap(
            key -> BandoFamily.byKey(key).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unbekannte Bando-Familie: " + key)),
            BandoFamily::key);

    public record Overlap(Holder<StructureSet> set, int reach) {
        public static final Codec<Overlap> CODEC = RecordCodecBuilder.create(i -> i.group(
                StructureSet.CODEC.fieldOf("other_set").forGetter(Overlap::set),
                Codec.intRange(0, BandoSite.MAX_REACH).fieldOf("reach").forGetter(Overlap::reach)
        ).apply(i, Overlap::new));
    }

    public static final MapCodec<BandoStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            FAMILY_CODEC.fieldOf("family").forGetter(BandoStructure::family),
            Overlap.CODEC.listOf().optionalFieldOf("avoid_overlap", List.of()).forGetter(BandoStructure::avoidOverlap)
    ).apply(i, BandoStructure::new));

    private final BandoFamily family;
    private final List<Overlap> avoidOverlap;

    public BandoStructure(StructureSettings settings, BandoFamily family, List<Overlap> avoidOverlap) {
        super(settings);
        this.family = family;
        this.avoidOverlap = List.copyOf(avoidOverlap);
    }

    public BandoFamily family() {
        return this.family;
    }

    public List<Overlap> avoidOverlap() {
        return this.avoidOverlap;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
            return Optional.empty();
        }
        WorldgenRandom random = context.random();
        long seed = random.nextLong();
        int turns = random.nextInt(4);
        int roll = random.nextInt(1 << 16);
        float chance = random.nextFloat();
        ChunkPos chunk = context.chunkPos();
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor height = context.heightAccessor();
        RandomState randomState = context.randomState();
        int sea = generator.getSeaLevel();
        int surface = generator.getFirstOccupiedHeight(centerX, centerZ, Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
        int floor = generator.getFirstOccupiedHeight(centerX, centerZ, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState);
        BandoVariant variant;
        if (this.family == BandoFamily.COAST) {
            variant = BandoSite.coastVariant(surface - floor);
            if (variant == BandoVariant.PLATFORM && chance >= BandoSite.PLATFORM_CHANCE) {
                return Optional.empty();
            }
        } else {
            if (this.family.terrain() == BandoFamily.Terrain.LAND && surface != floor) {
                return Optional.empty();
            }
            variant = BandoVariant.pick(this.family, roll);
        }
        BandoPlan plan = BandoGenerator.generate(variant, seed);
        BandoSite.Sampler sampler = new BandoSite.Sampler() {
            @Override
            public int surface(int x, int z) {
                return generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, randomState);
            }

            @Override
            public int floor(int x, int z) {
                return generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState);
            }

            @Override
            public boolean biome(int x, int y, int z) {
                return context.validBiome().test(context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)));
            }

            @Override
            public boolean keepAway(int x, int y, int z) {
                return context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)).is(BandoModule.KEEP_AWAY);
            }
        };
        Optional<BandoTransform> site = BandoSite.place(plan, turns, centerX, centerZ, sea, height.getMinY(), height.getMaxY(), sampler);
        if (site.isEmpty()) {
            return Optional.empty();
        }
        BandoTransform transform = site.get();
        if (this.overlapsOthers(context, transform)) {
            return Optional.empty();
        }
        int siteY = transform.worldY(plan.ground());
        return Optional.of(new GenerationStub(new BlockPos(centerX, siteY, centerZ), builder -> builder.addPiece(new BandoPiece(plan, transform))));
    }

    private boolean overlapsOthers(GenerationContext context, BandoTransform t) {
        int minX = t.originX();
        int minZ = t.originZ();
        int maxX = t.originX() + t.widthX() - 1;
        int maxZ = t.originZ() + t.widthZ() - 1;
        for (Overlap overlap : this.avoidOverlap) {
            StructureSet set = overlap.set().value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            int[] chunks = BandoSite.reachChunks(minX, minZ, maxX, maxZ, overlap.reach());
            for (int cx = chunks[0]; cx <= chunks[2]; cx++) {
                for (int cz = chunks[1]; cz <= chunks[3]; cz++) {
                    ChunkPos start = spread.getPotentialStructureChunk(context.seed(), cx, cz);
                    if (start.x() == cx && start.z() == cz && spread.applyAdditionalChunkRestrictions(cx, cz, context.seed())
                            && couldStart(context, set, start)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean couldStart(GenerationContext context, StructureSet set, ChunkPos chunk) {
        int y = context.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        Holder<Biome> middle = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(chunk.getMiddleBlockX()), QuartPos.fromBlock(y),
                QuartPos.fromBlock(chunk.getMiddleBlockZ()));
        Holder<Biome> corner = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(chunk.getMinBlockX()), QuartPos.fromBlock(y),
                QuartPos.fromBlock(chunk.getMinBlockZ()));
        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
            if (entry.structure().value().biomes().contains(middle) || entry.structure().value().biomes().contains(corner)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public StructureType<?> type() {
        return BandoModule.STRUCTURE_TYPE.get();
    }
}

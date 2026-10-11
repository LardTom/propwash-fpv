package de.lardtom.propwash.race.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.util.List;
import java.util.Optional;

public final class WorldCoursePlacement extends RandomSpreadStructurePlacement {
    public record Avoid(Holder<StructureSet> set, int chunks) {
        public static final Codec<Avoid> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.holder(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, false).fieldOf("other_set").forGetter(Avoid::set),
                Codec.intRange(1, 16).fieldOf("chunk_count").forGetter(Avoid::chunks)
        ).apply(i, Avoid::new));
    }

    public static final MapCodec<WorldCoursePlacement> CODEC = RecordCodecBuilder.<WorldCoursePlacement>mapCodec(i -> placementCodec(i)
            .and(i.group(
                    Codec.intRange(0, 4096).fieldOf("spacing").forGetter(WorldCoursePlacement::spacing),
                    Codec.intRange(0, 4096).fieldOf("separation").forGetter(WorldCoursePlacement::separation),
                    RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(WorldCoursePlacement::spreadType),
                    Avoid.CODEC.listOf().optionalFieldOf("avoid", List.of()).forGetter(WorldCoursePlacement::avoid)
            ))
            .apply(i, WorldCoursePlacement::new))
            .validate(p -> p.spacing() <= p.separation() ? DataResult.error(() -> "Spacing has to be larger than separation") : DataResult.success(p));

    private final List<Avoid> avoid;

    public WorldCoursePlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
                                Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, List<Avoid> avoid) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, spreadType);
        this.avoid = List.copyOf(avoid);
    }

    public List<Avoid> avoid() {
        return this.avoid;
    }

    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
        if (!super.isStructureChunk(state, sourceX, sourceZ)) {
            return false;
        }
        for (Avoid entry : this.avoid) {
            if (state.hasStructureChunkInRange(entry.set(), sourceX, sourceZ, entry.chunks())) {
                return false;
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<RandomSpreadStructurePlacement> codec() {
        return (MapCodec<RandomSpreadStructurePlacement>) (MapCodec<?>) CODEC;
    }
}

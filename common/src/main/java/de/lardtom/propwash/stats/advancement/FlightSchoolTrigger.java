package de.lardtom.propwash.stats.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

public final class FlightSchoolTrigger extends SimpleCriterionTrigger<FlightSchoolTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, TrainingTally tally) {
        this.trigger(player, instance -> instance.matches(tally));
    }

    public static boolean matches(Optional<MinMaxBounds.Ints> chapters, Optional<Boolean> perfect, TrainingTally tally) {
        return Bounds.within(chapters, tally.chapters()) && (perfect.isEmpty() || perfect.get() == tally.perfect());
    }

    public record Instance(Optional<Holder<LootItemCondition>> player, Optional<MinMaxBounds.Ints> chapters, Optional<Boolean> perfect)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("chapters").forGetter(Instance::chapters),
                Codec.BOOL.optionalFieldOf("perfect").forGetter(Instance::perfect)
        ).apply(i, Instance::new));

        public boolean matches(TrainingTally tally) {
            return FlightSchoolTrigger.matches(this.chapters, this.perfect, tally);
        }
    }
}

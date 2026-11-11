package de.lardtom.propwash.stats.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

public final class CargoTrigger extends SimpleCriterionTrigger<CargoTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, double distance) {
        this.trigger(player, instance -> Bounds.within(instance.distance(), distance));
    }

    public record Instance(Optional<Holder<LootItemCondition>> player, Optional<MinMaxBounds.Doubles> distance)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("distance").forGetter(Instance::distance)
        ).apply(i, Instance::new));
    }
}

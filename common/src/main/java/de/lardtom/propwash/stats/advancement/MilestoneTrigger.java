package de.lardtom.propwash.stats.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

public final class MilestoneTrigger extends SimpleCriterionTrigger<MilestoneTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, Milestone milestone) {
        this.trigger(player, instance -> instance.milestone() == milestone);
    }

    public record Instance(Optional<Holder<LootItemCondition>> player, Milestone milestone) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Milestone.CODEC.fieldOf("milestone").forGetter(Instance::milestone)
        ).apply(i, Instance::new));
    }
}

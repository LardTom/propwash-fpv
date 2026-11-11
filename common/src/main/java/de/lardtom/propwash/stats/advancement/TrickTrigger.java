package de.lardtom.propwash.stats.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.Optional;

public final class TrickTrigger extends SimpleCriterionTrigger<TrickTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String trick, int combo) {
        this.trigger(player, instance -> instance.matches(trick, combo));
    }

    public static boolean matches(Optional<List<String>> tricks, Optional<MinMaxBounds.Ints> combo, String trick, int value) {
        return (tricks.isEmpty() || tricks.get().contains(trick)) && Bounds.within(combo, value);
    }

    public record Instance(Optional<Holder<LootItemCondition>> player, Optional<List<String>> tricks, Optional<MinMaxBounds.Ints> combo)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                FeatCodecs.TRICK.listOf().optionalFieldOf("tricks").forGetter(Instance::tricks),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("combo").forGetter(Instance::combo)
        ).apply(i, Instance::new));

        public boolean matches(String trick, int combo) {
            return TrickTrigger.matches(this.tricks, this.combo, trick, combo);
        }
    }
}

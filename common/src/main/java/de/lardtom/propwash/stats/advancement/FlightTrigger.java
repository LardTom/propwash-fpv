package de.lardtom.propwash.stats.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

public final class FlightTrigger extends SimpleCriterionTrigger<FlightTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, Report report) {
        this.trigger(player, instance -> instance.matches(report));
    }

    public static boolean matches(Optional<MinMaxBounds.Doubles> distance, Optional<MinMaxBounds.Doubles> altitude,
                                  Optional<MinMaxBounds.Doubles> dive, Optional<MinMaxBounds.Ints> crashes, Report report) {
        return Bounds.within(distance, report.meters()) && Bounds.within(altitude, report.altitude()) && Bounds.within(dive, report.dive())
                && Bounds.within(crashes, report.crashes());
    }

    public record Report(double meters, double altitude, double dive, int crashes) {
    }

    public record Instance(Optional<Holder<LootItemCondition>> player, Optional<MinMaxBounds.Doubles> distance,
                           Optional<MinMaxBounds.Doubles> altitude, Optional<MinMaxBounds.Doubles> dive, Optional<MinMaxBounds.Ints> crashes)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("distance").forGetter(Instance::distance),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("altitude").forGetter(Instance::altitude),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("dive").forGetter(Instance::dive),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("crashes").forGetter(Instance::crashes)
        ).apply(i, Instance::new));

        public boolean matches(Report report) {
            return FlightTrigger.matches(this.distance, this.altitude, this.dive, this.crashes, report);
        }
    }
}

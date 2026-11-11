package de.lardtom.propwash.api.worldrule;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public interface WorldRules {
    String PRESET_REALISTIC = "realistic";
    String PRESET_TRAINING = "training";
    String PRESET_ARCADE = "arcade";
    List<String> PRESETS = List.of(PRESET_REALISTIC, PRESET_TRAINING, PRESET_ARCADE);

    WorldRule<Boolean> registerBoolean(Identifier id, boolean defaultValue);

    WorldRule<Integer> registerInt(Identifier id, int defaultValue, int min, int max);

    <T> void presetValue(WorldRule<T> rule, String presetId, T value);

    default void raceNeutral(WorldRule<?> rule) {
    }

    default boolean raceRelevant(WorldRule<?> rule) {
        return rule != null;
    }

    List<WorldRule<?>> registered();

    Optional<WorldRule<?>> find(Identifier id);
}

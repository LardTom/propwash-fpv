package de.lardtom.propwash.trick.tutorial;

import de.lardtom.propwash.trick.TrickType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

public record TrickChoreography(TrickType trick, List<TrickMove> moves, boolean mode3d, List<TrickType> parts, Map<String, String> sharedTexts,
                                @Nullable TrickType sharedWith) {
    public static final String TEXT_PREFIX = "propwash.trick.tutorial.";
    public static final String STAGE_PREFIX = TEXT_PREFIX + "stage.";
    public static final String SHARED_PREFIX = TEXT_PREFIX + "text.";

    public TrickChoreography {
        Objects.requireNonNull(trick, "trick");
        moves = List.copyOf(moves);
        parts = List.copyOf(parts);
        sharedTexts = Map.copyOf(sharedTexts);
    }

    public List<String> stages() {
        List<String> stages = new ArrayList<>();
        for (TrickMove move : this.moves) {
            String stage = move.stage();
            if (!stage.isEmpty() && (stages.isEmpty() || !stages.getLast().equals(stage))) {
                stages.add(stage);
            }
        }
        return stages;
    }

    public String titleKey() {
        return this.trick.translationKey();
    }

    public static String stageTitleKey(String stage) {
        return STAGE_PREFIX + stage;
    }

    public String stageTextKey(String stage) {
        String shared = this.sharedTexts.get(stage);
        return shared != null ? SHARED_PREFIX + shared : TEXT_PREFIX + this.trick.key() + "." + stage;
    }

    public List<String> translationKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(this.titleKey());
        for (String stage : this.stages()) {
            keys.add(stageTitleKey(stage));
            keys.add(this.stageTextKey(stage));
        }
        return keys;
    }

    public TrickChoreography mirrored() {
        return this.map(TrickMove::mirrored);
    }

    public TrickChoreography backwards() {
        return this.map(TrickMove::backwards);
    }

    public TrickChoreography map(UnaryOperator<TrickMove> change) {
        List<TrickMove> changed = new ArrayList<>(this.moves.size());
        for (TrickMove move : this.moves) {
            changed.add(change.apply(move));
        }
        return new TrickChoreography(this.trick, changed, this.mode3d, this.parts, this.sharedTexts, this.sharedWith);
    }

    public TrickChoreography insert(int index, TrickMove move) {
        List<TrickMove> changed = new ArrayList<>(this.moves);
        changed.add(index, move);
        return new TrickChoreography(this.trick, changed, this.mode3d, this.parts, this.sharedTexts, this.sharedWith);
    }

    public TrickChoreography threeD() {
        return new TrickChoreography(this.trick, this.moves, true, this.parts, this.sharedTexts, this.sharedWith);
    }

    public TrickChoreography as(TrickType variant) {
        return new TrickChoreography(variant, this.moves, this.mode3d, this.parts, this.sharedTexts, this.trick);
    }

    public TrickChoreography withShared(String stage, @Nullable String text) {
        Map<String, String> texts = new LinkedHashMap<>(this.sharedTexts);
        if (text == null) {
            texts.remove(stage);
        } else {
            texts.put(stage, text);
        }
        return new TrickChoreography(this.trick, this.moves, this.mode3d, this.parts, texts, this.sharedWith);
    }
}

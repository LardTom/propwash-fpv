package de.lardtom.propwash.guide.scene;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SceneState(float time, int step, float stepProgress, List<BlockView> blocks, Map<String, ActorState> actors,
                         List<CalloutView> callouts, List<HighlightView> highlights, List<KeyHintView> keyHints, CameraSpec camera) {
    public SceneState {
        blocks = List.copyOf(blocks);
        actors = Collections.unmodifiableMap(new LinkedHashMap<>(actors));
        callouts = List.copyOf(callouts);
        highlights = List.copyOf(highlights);
        keyHints = List.copyOf(keyHints);
    }

    public @Nullable ActorState actor(String id) {
        return this.actors.get(id);
    }

    public Collection<ActorState> visibleActors() {
        return this.actors.values().stream().filter(ActorState::visible).toList();
    }

    public @Nullable BlockView block(int x, int y, int z) {
        for (BlockView view : this.blocks) {
            BlockPlacement at = view.at();
            if (at.x() == x && at.y() == y && at.z() == z) {
                return view;
            }
        }
        return null;
    }

    public record BlockView(BlockPlacement at, BlockSpec block, float scale, float lift) {
    }

    public record CalloutView(String textKey, ScenePos anchor, Cue.Side side, float alpha, float age, int cue) {
        public CalloutView(String textKey, ScenePos anchor, Cue.Side side, float alpha, float age) {
            this(textKey, anchor, side, alpha, age, -1);
        }

        public String layoutId() {
            return this.cue >= 0 ? "#" + this.cue : this.textKey;
        }
    }

    public record HighlightView(ScenePos min, ScenePos max, float alpha, float age) {
    }

    public record KeyHintView(String textKey, List<KeyToken> keys, @Nullable ScenePos anchor, float alpha) {
    }
}

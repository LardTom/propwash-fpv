package de.lardtom.propwash.guide.scene;

import org.jspecify.annotations.Nullable;

import java.util.Map;

public record Anchor(@Nullable String actor, ScenePos offset) {
    public static Anchor at(float x, float y, float z) {
        return new Anchor(null, new ScenePos(x, y, z));
    }

    public static Anchor at(ScenePos pos) {
        return new Anchor(null, pos);
    }

    public static Anchor on(String actor, float dx, float dy, float dz) {
        return new Anchor(actor, new ScenePos(dx, dy, dz));
    }

    public @Nullable ScenePos resolve(Map<String, ActorState> actors) {
        if (this.actor == null) {
            return this.offset;
        }
        ActorState state = actors.get(this.actor);
        if (state == null || !state.visible()) {
            return null;
        }
        return state.position().add(this.offset);
    }
}

package de.lardtom.propwash.guide.scene;

import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMode;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record Anchor(@Nullable String actor, ScenePos offset, @Nullable StickChannel stick) {
    public Anchor(@Nullable String actor, ScenePos offset) {
        this(actor, offset, null);
    }

    public static Anchor at(float x, float y, float z) {
        return new Anchor(null, new ScenePos(x, y, z));
    }

    public static Anchor at(ScenePos pos) {
        return new Anchor(null, pos);
    }

    public static Anchor on(String actor, float dx, float dy, float dz) {
        return new Anchor(actor, new ScenePos(dx, dy, dz));
    }

    public static Anchor onStick(String actor, StickChannel stick, ScenePos mode2Tip) {
        return new Anchor(actor, mode2Tip, stick);
    }

    public boolean mirrored(StickMode mode) {
        return this.stick != null && !StickMode.orDefault(mode).sameSide(this.stick, StickMode.MODE_2);
    }

    public @Nullable ScenePos resolve(Map<String, ActorState> actors) {
        return this.resolve(actors, StickMode.MODE_2);
    }

    public @Nullable ScenePos resolve(Map<String, ActorState> actors, StickMode mode) {
        ScenePos local = this.mirrored(mode) ? new ScenePos(-this.offset.x(), this.offset.y(), this.offset.z()) : this.offset;
        if (this.actor == null) {
            return local;
        }
        ActorState state = actors.get(this.actor);
        if (state == null || !state.visible()) {
            return null;
        }
        return state.position().add(local);
    }
}

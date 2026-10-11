package de.lardtom.propwash.guide.scene;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public sealed interface Cue {
    enum Appear {
        DROP,
        POP,
        INSTANT
    }

    enum Side {
        LEFT,
        RIGHT,
        ABOVE,
        BELOW
    }

    enum Slot {
        HEAD,
        HAND
    }

    default boolean transientCue() {
        return false;
    }

    record PlaceBlock(BlockPlacement at, BlockSpec block, Appear appear) implements Cue {
        public PlaceBlock {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(appear, "appear");
        }
    }

    record RemoveBlock(BlockPlacement at) implements Cue {
        public RemoveBlock {
            Objects.requireNonNull(at, "at");
        }
    }

    record Spawn(String actor, ActorSpec spec, ScenePos position, float yaw, Appear appear) implements Cue {
        public Spawn {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(appear, "appear");
        }
    }

    record Despawn(String actor) implements Cue {
        public Despawn {
            Objects.requireNonNull(actor, "actor");
        }
    }

    record Move(String actor, List<ScenePos> path, Ease ease) implements Cue {
        public Move {
            Objects.requireNonNull(actor, "actor");
            path = List.copyOf(path);
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Pfad ohne Punkte");
            }
            Objects.requireNonNull(ease, "ease");
        }
    }

    record Turn(String actor, float yaw, float pitch, float roll, Ease ease) implements Cue {
        public Turn {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(ease, "ease");
        }
    }

    record Sticks(String actor, float leftX, float leftY, float rightX, float rightY, Ease ease) implements Cue {
        public Sticks {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(ease, "ease");
        }
    }

    record Arm(String actor, boolean armed) implements Cue {
        public Arm {
            Objects.requireNonNull(actor, "actor");
        }
    }

    record Throttle(String actor, float throttle) implements Cue {
        public Throttle {
            Objects.requireNonNull(actor, "actor");
        }
    }

    record AttachPart(String actor, DronePart part, ScenePos from, float stagger) implements Cue {
        public AttachPart {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(part, "part");
            Objects.requireNonNull(from, "from");
        }
    }

    record DetachPart(String actor, DronePart part, ScenePos to) implements Cue {
        public DetachPart {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(part, "part");
            Objects.requireNonNull(to, "to");
        }
    }

    record PropCondition(String actor, int slot, float condition) implements Cue {
        public PropCondition {
            Objects.requireNonNull(actor, "actor");
            if (slot < 0 || slot >= DronePart.SLOTS) {
                throw new IllegalArgumentException("Prop-Platz " + slot);
            }
            condition = Float.isFinite(condition) ? Math.max(0.0F, Math.min(1.0F, condition)) : 1.0F;
        }
    }

    record Equip(String actor, Slot slot, String item) implements Cue {
        public Equip {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(slot, "slot");
            item = item == null ? "" : item;
        }
    }

    record Crouch(String actor, boolean crouching) implements Cue {
        public Crouch {
            Objects.requireNonNull(actor, "actor");
        }
    }

    record Look(String actor, float headPitch, Ease ease) implements Cue {
        public Look {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(ease, "ease");
        }

        public Look(String actor, float headPitch) {
            this(actor, headPitch, Ease.IN_OUT);
        }
    }

    record Light(String actor, boolean lit) implements Cue {
        public Light {
            Objects.requireNonNull(actor, "actor");
        }
    }

    record Camera(ScenePos target, float yaw, float pitch, float zoom, Ease ease) implements Cue {
        public Camera {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(ease, "ease");
        }
    }

    record Callout(String textKey, Anchor anchor, Side side) implements Cue {
        public Callout {
            Objects.requireNonNull(textKey, "textKey");
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(side, "side");
        }

        @Override
        public boolean transientCue() {
            return true;
        }
    }

    record Highlight(ScenePos min, ScenePos max) implements Cue {
        public Highlight {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
        }

        @Override
        public boolean transientCue() {
            return true;
        }
    }

    record Keys(String textKey, List<KeyToken> keys, @Nullable Anchor anchor) implements Cue {
        public Keys {
            Objects.requireNonNull(textKey, "textKey");
            keys = List.copyOf(keys);
        }

        @Override
        public boolean transientCue() {
            return true;
        }
    }
}

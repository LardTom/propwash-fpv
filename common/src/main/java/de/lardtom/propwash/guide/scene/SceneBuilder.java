package de.lardtom.propwash.guide.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class SceneBuilder {
    private final String id;
    private String icon = "";
    private final Set<String> items = new LinkedHashSet<>();
    private boolean anyPart;
    private float viewRadius = 3.0F;
    private CameraSpec camera = new CameraSpec(ScenePos.ZERO, -30.0F, 30.0F, 1.0F);
    private final List<SceneStep> steps = new ArrayList<>();

    private SceneBuilder(String id) {
        this.id = Scene.canonicalId(Objects.requireNonNull(id, "id"));
    }

    public static SceneBuilder scene(String id) {
        return new SceneBuilder(id);
    }

    public SceneBuilder icon(String itemId) {
        this.icon = Objects.requireNonNull(itemId, "itemId");
        this.items.add(itemId);
        return this;
    }

    public SceneBuilder items(String... ids) {
        this.items.addAll(Arrays.asList(ids));
        return this;
    }

    public SceneBuilder anyPart() {
        this.anyPart = true;
        return this;
    }

    public SceneBuilder view(float radius) {
        this.viewRadius = radius;
        return this;
    }

    public SceneBuilder camera(ScenePos target, float yaw, float pitch, float zoom) {
        this.camera = new CameraSpec(target, yaw, pitch, zoom);
        return this;
    }

    public SceneBuilder step(String name, float duration, Consumer<StepBuilder> body) {
        StepBuilder step = new StepBuilder(name, duration);
        body.accept(step);
        this.steps.add(new SceneStep(name, duration, step.cues));
        return this;
    }

    public String key(String kind, String name) {
        return Scene.keyPrefix(this.id) + kind + "." + name;
    }

    public Scene build() {
        if (this.icon.isEmpty()) {
            throw new IllegalStateException("Szene " + this.id + " ohne Symbol");
        }
        return new Scene(this.id, this.icon, new ItemMatch(this.items, this.anyPart), this.viewRadius, this.camera, this.steps);
    }

    public final class StepBuilder {
        private final String name;
        private final float duration;
        private final List<TimedCue> cues = new ArrayList<>();
        private float cursor;

        private StepBuilder(String name, float duration) {
            this.name = name;
            this.duration = duration;
        }

        public StepBuilder at(float tick) {
            this.cursor = tick;
            return this;
        }

        public StepBuilder after(float ticks) {
            this.cursor += ticks;
            return this;
        }

        public float now() {
            return this.cursor;
        }

        private StepBuilder add(float length, Cue cue) {
            if (this.cursor < 0.0F || this.cursor + Math.max(0.0F, length) > this.duration + 1.0E-3F) {
                throw new IllegalArgumentException("Cue " + cue.getClass().getSimpleName() + " in " + SceneBuilder.this.id + "/" + this.name
                        + " endet nach dem Schritt (" + this.cursor + " + " + length + " > " + this.duration + ")");
            }
            this.cues.add(new TimedCue(this.cursor, length, cue));
            return this;
        }

        public StepBuilder block(int x, int y, int z, BlockSpec block) {
            return this.add(0.0F, new Cue.PlaceBlock(new BlockPlacement(x, y, z), block, Cue.Appear.INSTANT));
        }

        public StepBuilder drop(int x, int y, int z, BlockSpec block, float length) {
            return this.add(length, new Cue.PlaceBlock(new BlockPlacement(x, y, z), block, Cue.Appear.DROP));
        }

        public StepBuilder pop(int x, int y, int z, BlockSpec block, float length) {
            return this.add(length, new Cue.PlaceBlock(new BlockPlacement(x, y, z), block, Cue.Appear.POP));
        }

        public StepBuilder floor(int x0, int z0, int x1, int z1, int y, BlockSpec block, float stagger, float length) {
            float base = this.cursor;
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    int wave = (x - Math.min(x0, x1)) + (z - Math.min(z0, z1));
                    this.cursor = base + wave * stagger;
                    this.drop(x, y, z, block, length);
                }
            }
            this.cursor = base;
            return this;
        }

        public StepBuilder remove(int x, int y, int z, float length) {
            return this.add(length, new Cue.RemoveBlock(new BlockPlacement(x, y, z)));
        }

        public StepBuilder spawn(String actor, ActorSpec spec, ScenePos position, float yaw, Cue.Appear appear, float length) {
            return this.add(length, new Cue.Spawn(actor, spec, position, yaw, appear));
        }

        public StepBuilder despawn(String actor, float length) {
            return this.add(length, new Cue.Despawn(actor));
        }

        public StepBuilder move(String actor, float length, Ease ease, ScenePos... path) {
            return this.add(length, new Cue.Move(actor, List.of(path), ease));
        }

        public StepBuilder turn(String actor, float yaw, float pitch, float roll, float length) {
            return this.turn(actor, yaw, pitch, roll, length, Ease.IN_OUT);
        }

        public StepBuilder turn(String actor, float yaw, float pitch, float roll, float length, Ease ease) {
            return this.add(length, new Cue.Turn(actor, yaw, pitch, roll, ease));
        }

        public StepBuilder sticks(String actor, float leftX, float leftY, float rightX, float rightY, float length) {
            return this.add(length, new Cue.Sticks(actor, leftX, leftY, rightX, rightY, Ease.IN_OUT));
        }

        public StepBuilder arm(String actor, boolean armed) {
            return this.add(0.0F, new Cue.Arm(actor, armed));
        }

        public StepBuilder throttle(String actor, float throttle, float length) {
            return this.add(length, new Cue.Throttle(actor, throttle));
        }

        public StepBuilder attach(String actor, DronePart part, ScenePos from, float length, float stagger) {
            return this.add(length, new Cue.AttachPart(actor, part, from, stagger));
        }

        public StepBuilder detach(String actor, DronePart part, ScenePos to, float length) {
            return this.add(length, new Cue.DetachPart(actor, part, to));
        }

        public StepBuilder propCondition(String actor, int slot, float condition) {
            return this.add(0.0F, new Cue.PropCondition(actor, slot, condition));
        }

        public StepBuilder itemCondition(String actor, float condition) {
            return this.propCondition(actor, 0, condition);
        }

        public StepBuilder propConditions(String actor, float... conditions) {
            for (int slot = 0; slot < conditions.length; slot++) {
                this.propCondition(actor, slot, conditions[slot]);
            }
            return this;
        }

        public StepBuilder equip(String actor, Cue.Slot slot, String item) {
            return this.add(0.0F, new Cue.Equip(actor, slot, item));
        }

        public StepBuilder crouch(String actor, boolean crouching) {
            return this.add(0.0F, new Cue.Crouch(actor, crouching));
        }

        public StepBuilder look(String actor, float headPitch, float length) {
            return this.look(actor, headPitch, length, Ease.IN_OUT);
        }

        public StepBuilder look(String actor, float headPitch, float length, Ease ease) {
            return this.add(length, new Cue.Look(actor, headPitch, ease));
        }

        public StepBuilder light(String actor, boolean lit) {
            return this.add(0.0F, new Cue.Light(actor, lit));
        }

        public StepBuilder camera(ScenePos target, float yaw, float pitch, float zoom, float length) {
            return this.add(length, new Cue.Camera(target, yaw, pitch, zoom, Ease.IN_OUT));
        }

        public StepBuilder callout(String name, Anchor anchor, Cue.Side side, float length) {
            return this.add(length, new Cue.Callout(SceneBuilder.this.key("callout", name), anchor, side));
        }

        public StepBuilder highlight(ScenePos min, ScenePos max, float length) {
            return this.add(length, new Cue.Highlight(min, max));
        }

        public StepBuilder highlightBlock(int x, int y, int z, float length) {
            return this.highlight(new ScenePos(x, y, z), new ScenePos(x + 1.0F, y + 1.0F, z + 1.0F), length);
        }

        public StepBuilder keys(String name, float length, KeyToken... keys) {
            return this.add(length, new Cue.Keys(SceneBuilder.this.key("hint", name), List.of(keys), null));
        }

        public StepBuilder keysAt(String name, Anchor anchor, float length, KeyToken... keys) {
            return this.add(length, new Cue.Keys(SceneBuilder.this.key("hint", name), List.of(keys), anchor));
        }
    }
}

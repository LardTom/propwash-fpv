package de.lardtom.propwash.guide.scene;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class ActorState {
    private final String id;
    private final ActorSpec spec;
    private ScenePos position;
    private float yaw;
    private float pitch;
    private float roll;
    private float presence;
    private boolean removed;
    private boolean armed;
    private float throttle;
    private String headItem;
    private String handItem;
    private boolean crouching;
    private float headPitch;
    private boolean lit;
    private float leftX;
    private float leftY = -1.0F;
    private float rightX;
    private float rightY;
    private final Map<DronePart, PartState> parts = new EnumMap<>(DronePart.class);
    private final float[] propConditions = {1.0F, 1.0F, 1.0F, 1.0F};

    public ActorState(String id, ActorSpec spec, ScenePos position, float yaw) {
        this.id = id;
        this.spec = spec;
        this.position = position;
        this.yaw = yaw;
        this.presence = 1.0F;
        if (spec instanceof ActorSpec.Pilot pilot) {
            this.headItem = pilot.headItem();
            this.handItem = pilot.handItem();
        } else {
            this.headItem = "";
            this.handItem = "";
        }
        if (spec instanceof ActorSpec.Drone drone) {
            for (DronePart part : DronePart.values()) {
                this.parts.put(part, drone.assembled() ? PartState.attached(part) : PartState.hidden(part));
            }
        }
    }

    public String id() {
        return this.id;
    }

    public ActorSpec spec() {
        return this.spec;
    }

    public ScenePos position() {
        return this.position;
    }

    public float yaw() {
        return this.yaw;
    }

    public float pitch() {
        return this.pitch;
    }

    public float roll() {
        return this.roll;
    }

    public float presence() {
        return this.presence;
    }

    public boolean visible() {
        return !this.removed && this.presence > 0.0F;
    }

    public boolean armed() {
        return this.armed;
    }

    public float throttle() {
        return this.throttle;
    }

    public String headItem() {
        return this.headItem;
    }

    public String handItem() {
        return this.handItem;
    }

    public boolean crouching() {
        return this.crouching;
    }

    public float headPitch() {
        return this.headPitch;
    }

    public boolean lit() {
        return this.lit;
    }

    public float leftX() {
        return this.leftX;
    }

    public float leftY() {
        return this.leftY;
    }

    public float rightX() {
        return this.rightX;
    }

    public float rightY() {
        return this.rightY;
    }

    public float propCondition(int slot) {
        return this.propConditions[Math.floorMod(slot, this.propConditions.length)];
    }

    void propCondition(int slot, float condition) {
        this.propConditions[Math.floorMod(slot, this.propConditions.length)] = condition;
    }

    public PartState part(DronePart part) {
        PartState state = this.parts.get(part);
        return state != null ? state : PartState.attached(part);
    }

    public boolean fullyAssembled() {
        if (!(this.spec instanceof ActorSpec.Drone)) {
            return false;
        }
        for (DronePart part : DronePart.values()) {
            if (!this.part(part).attached()) {
                return false;
            }
        }
        return true;
    }

    void position(ScenePos position) {
        this.position = position;
    }

    void rotation(float yaw, float pitch, float roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    void presence(float presence) {
        this.presence = presence;
    }

    void removed(boolean removed) {
        this.removed = removed;
    }

    boolean isRemoved() {
        return this.removed;
    }

    void armed(boolean armed) {
        this.armed = armed;
    }

    void throttle(float throttle) {
        this.throttle = throttle;
    }

    void headItem(String item) {
        this.headItem = item == null ? "" : item;
    }

    void handItem(String item) {
        this.handItem = item == null ? "" : item;
    }

    void crouching(boolean crouching) {
        this.crouching = crouching;
    }

    void headPitch(float pitch) {
        this.headPitch = pitch;
    }

    void lit(boolean lit) {
        this.lit = lit;
    }

    void sticks(float leftX, float leftY, float rightX, float rightY) {
        this.leftX = Math.clamp(leftX, -1.0F, 1.0F);
        this.leftY = Math.clamp(leftY, -1.0F, 1.0F);
        this.rightX = Math.clamp(rightX, -1.0F, 1.0F);
        this.rightY = Math.clamp(rightY, -1.0F, 1.0F);
    }

    void part(DronePart part, PartState state) {
        this.parts.put(part, state);
    }

    public record PartState(DronePart part, boolean visible, ScenePos from, float[] progress) {
        public static PartState hidden(DronePart part) {
            return new PartState(part, false, ScenePos.ZERO, filled(part, -1.0F));
        }

        public static PartState attached(DronePart part) {
            return new PartState(part, true, ScenePos.ZERO, filled(part, 1.0F));
        }

        private static float[] filled(DronePart part, float value) {
            float[] values = new float[part.slots()];
            Arrays.fill(values, value);
            return values;
        }

        public float progress(int slot) {
            if (this.progress.length == 0) {
                return 1.0F;
            }
            return this.progress[Math.floorMod(slot, this.progress.length)];
        }

        public boolean slotVisible(int slot) {
            return this.visible && this.progress(slot) >= 0.0F;
        }

        public ScenePos offset(int slot) {
            float eased = Ease.OUT.apply(this.progress(slot));
            return this.from.scale(1.0F - eased);
        }

        public boolean moving() {
            if (!this.visible) {
                return false;
            }
            for (float p : this.progress) {
                if (p >= 0.0F && p < 1.0F) {
                    return true;
                }
            }
            return false;
        }

        public boolean attached() {
            if (!this.visible) {
                return false;
            }
            for (float p : this.progress) {
                if (p < 1.0F) {
                    return false;
                }
            }
            return true;
        }
    }
}

package de.lardtom.propwash.build;

import java.util.Optional;

public enum PaintSlot {
    FRAME("frame", PaintChannel.FRAME, -1),
    TPU("tpu", PaintChannel.TPU, -1),
    MOTORS("motors", PaintChannel.MOTOR, -1),
    PROP_FRONT_LEFT("prop_fl", PaintChannel.PROP, 0),
    PROP_FRONT_RIGHT("prop_fr", PaintChannel.PROP, 1),
    PROP_REAR_LEFT("prop_rl", PaintChannel.PROP, 2),
    PROP_REAR_RIGHT("prop_rr", PaintChannel.PROP, 3),
    STACK("stack", PaintChannel.STACK, -1),
    BATTERY("battery", PaintChannel.BATTERY, -1),
    CAMERA("camera", PaintChannel.CAMERA, -1),
    ANTENNA("antenna", PaintChannel.ANTENNA, -1),
    ACCESSORIES("accessories", PaintChannel.ACCESSORY, -1);

    public static final int COUNT = values().length;
    public static final int PROPS = 4;

    private static final PaintSlot[] PROP_SLOTS = {PROP_FRONT_LEFT, PROP_FRONT_RIGHT, PROP_REAR_LEFT, PROP_REAR_RIGHT};

    private final String key;
    private final PaintChannel channel;
    private final int propPosition;

    PaintSlot(String key, PaintChannel channel, int propPosition) {
        this.key = key;
        this.channel = channel;
        this.propPosition = propPosition;
    }

    public String key() {
        return this.key;
    }

    public PaintChannel channel() {
        return this.channel;
    }

    public boolean isProp() {
        return this.propPosition >= 0;
    }

    public int propPosition() {
        return this.propPosition;
    }

    public static PaintSlot prop(int position) {
        return PROP_SLOTS[position];
    }

    public static PaintSlot of(PaintChannel channel, int propPosition) {
        if (channel == PaintChannel.PROP) {
            return prop(Math.max(0, Math.min(PROPS - 1, propPosition)));
        }
        for (PaintSlot slot : values()) {
            if (slot.channel == channel) {
                return slot;
            }
        }
        throw new IllegalArgumentException("Kein Lack-Platz für " + channel);
    }

    public static Optional<PaintSlot> byKey(String key) {
        for (PaintSlot slot : values()) {
            if (slot.key.equals(key)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}

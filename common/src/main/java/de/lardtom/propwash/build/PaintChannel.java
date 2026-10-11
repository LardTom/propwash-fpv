package de.lardtom.propwash.build;

import java.util.Optional;

public enum PaintChannel {
    FRAME("frame", 200),
    TPU("tpu", 242),
    MOTOR("motor", 208),
    PROP("prop", 224),
    STACK("stack", 200),
    BATTERY("battery", 200),
    CAMERA("camera", 232),
    ANTENNA("antenna", 230),
    ACCESSORY("accessory", 200);

    private final String key;
    private final int reference;

    PaintChannel(String key, int reference) {
        this.key = key;
        this.reference = reference;
    }

    public String key() {
        return this.key;
    }

    public int reference() {
        return this.reference;
    }

    public int tint(int rgb) {
        return tint(rgb, this.reference);
    }

    public static int tint(int rgb, int reference) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = (rgb >> shift) & 0xFF;
            out = (out << 8) | Math.min(255, (c * 255 + reference / 2) / reference);
        }
        return out;
    }

    public static Optional<PaintChannel> byKey(String key) {
        for (PaintChannel channel : values()) {
            if (channel.key.equals(key)) {
                return Optional.of(channel);
            }
        }
        return Optional.empty();
    }
}

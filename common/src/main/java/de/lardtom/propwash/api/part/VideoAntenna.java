package de.lardtom.propwash.api.part;

import java.util.Objects;

public record VideoAntenna(int count, double lengthMm, AntennaCap cap) {
    public static final int MAX_COUNT = 2;
    public static final double MIN_LENGTH_MM = 10.0;
    public static final double MAX_LENGTH_MM = 250.0;
    private static final VideoAntenna NONE = new VideoAntenna(0, 0.0, AntennaCap.WHIP);

    public VideoAntenna {
        if (count == 0) {
            lengthMm = 0.0;
            cap = AntennaCap.WHIP;
        }
        Objects.requireNonNull(cap, "cap");
        if (count < 0 || count > MAX_COUNT) {
            throw new IllegalArgumentException("Antennen-Anzahl muss 0 (keine), 1 oder " + MAX_COUNT + " sein: " + count);
        }
        if (count > 0 && !(lengthMm >= MIN_LENGTH_MM && lengthMm <= MAX_LENGTH_MM)) {
            throw new IllegalArgumentException("Antennen-Länge muss zwischen " + MIN_LENGTH_MM + " und " + MAX_LENGTH_MM + " mm liegen: " + lengthMm);
        }
    }

    public static VideoAntenna none() {
        return NONE;
    }

    public boolean isNone() {
        return this.count == 0;
    }
}

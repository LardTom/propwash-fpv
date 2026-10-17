package de.lardtom.propwash.input;

public final class PitchDirection {
    private PitchDirection() {
    }

    public static float apply(float pitch, boolean invert) {
        return invert ? 0.0F - pitch : pitch;
    }
}

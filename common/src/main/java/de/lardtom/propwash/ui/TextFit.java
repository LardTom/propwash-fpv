package de.lardtom.propwash.ui;

public final class TextFit {
    public static final float MIN_SQUEEZE = 0.7F;

    private TextFit() {
    }

    public enum Mode {
        PLAIN,
        SQUEEZED,
        CUT
    }

    public static Mode mode(int textWidth, int available) {
        if (textWidth <= available) {
            return Mode.PLAIN;
        }
        return available > 0 && available >= textWidth * MIN_SQUEEZE ? Mode.SQUEEZED : Mode.CUT;
    }

    public static float factor(int textWidth, int available) {
        return mode(textWidth, available) == Mode.SQUEEZED ? (float) available / textWidth : 1.0F;
    }
}

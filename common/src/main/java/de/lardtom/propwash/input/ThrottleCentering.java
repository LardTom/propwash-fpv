package de.lardtom.propwash.input;

public final class ThrottleCentering {
    public static final float CENTER = 0.5F;

    private boolean centered;
    private boolean detent;

    public boolean centered() {
        return this.centered;
    }

    public void reset() {
        this.centered = false;
        this.detent = false;
    }

    public float apply(float previous, float next, boolean moving, boolean centered, boolean armed) {
        if (centered != this.centered) {
            this.centered = centered;
            this.detent = false;
            if (!armed) {
                return centered ? CENTER : 0.0F;
            }
        }
        if (!centered) {
            return next;
        }
        if (!moving) {
            this.detent = false;
            return next;
        }
        if (this.detent) {
            return CENTER;
        }
        if (previous < CENTER && next >= CENTER || previous > CENTER && next <= CENTER) {
            this.detent = true;
            return CENTER;
        }
        return next;
    }
}

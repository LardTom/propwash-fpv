package de.lardtom.propwash.input;

public final class KeyRepeatFilter {
    private boolean held;

    public boolean pressed(int clicks, boolean down) {
        boolean pressed = clicks > 0 && !this.held;
        this.held = down;
        return pressed;
    }

    public void sync(boolean down) {
        this.held = down;
    }

    public boolean held() {
        return this.held;
    }
}

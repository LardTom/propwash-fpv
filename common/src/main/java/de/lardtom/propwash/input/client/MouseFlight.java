package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.KeyRepeatFilter;
import de.lardtom.propwash.input.MouseSettings;
import de.lardtom.propwash.input.MouseSticks;

final class MouseFlight {
    private static final long MAX_EVENT_AGE_NANOS = 1_000_000_000L;

    private final MouseSticks sticks = new MouseSticks();
    private final KeyRepeatFilter centerKey = new KeyRepeatFilter();
    private boolean active;

    void reset() {
        if (this.active) {
            this.sticks.reset();
            this.active = false;
        }
    }

    MouseSticks.Output update(boolean flying, MouseCapture.Frame frame, float dt, MouseSettings settings, boolean selfLevel) {
        boolean center = InputKeys.pressed(InputKeys.MOUSE_CENTER, this.centerKey);
        if (!flying) {
            this.reset();
            return MouseSticks.Output.CENTER;
        }
        this.active = true;
        if (center) {
            this.sticks.center();
        }
        long now = frame.now();
        for (int i = 0; i < frame.events(); i++) {
            long time = frame.times()[i];
            long clamped = time > now || time <= 0L ? now : Math.max(time, now - MAX_EVENT_AGE_NANOS);
            this.sticks.motion(clamped, frame.xs()[i], frame.ys()[i]);
        }
        return this.sticks.update(now, dt, settings, selfLevel);
    }
}

package de.lardtom.propwash.input;

import java.util.List;
import java.util.Locale;

public final class MouseDemo {
    public static final float STEP = 1.0F / 60.0F;
    public static final float SPEED_CM_PER_SECOND = 12.0F;
    public static final int DPI = 800;
    private static final float MOVE = 0.4F;
    private static final float PAUSE = 1.2F;
    private static final List<Phase> PHASES = phases();
    public static final float PERIOD = PHASES.getLast().end();

    private MouseDemo() {
    }

    public enum Cue {
        START,
        RIGHT,
        STOP_SIDE,
        LEFT,
        STOP_BACK,
        FORWARD,
        STOP_FORWARD,
        BACK,
        STOP_END;

        public String translationKey() {
            return "propwash.input.mouse.demo." + this.name().toLowerCase(Locale.ROOT);
        }

        public boolean moving() {
            return this == RIGHT || this == LEFT || this == FORWARD || this == BACK;
        }
    }

    public record Phase(float start, float end, float vx, float vy, Cue cue) {
    }

    public record Frame(float seconds, float mouseX, float mouseY, Cue cue, MouseSticks.Output sticks) {
    }

    public static Frame frame(MouseSettings user, boolean selfLevel, float seconds) {
        MouseSettings settings = demoSettings(user);
        float t = Float.isFinite(seconds) ? seconds % PERIOD : 0.0F;
        if (t < 0.0F) {
            t += PERIOD;
        }
        MouseSticks sticks = new MouseSticks();
        MouseSticks.Output out = MouseSticks.Output.CENTER;
        float countsPerCm = DPI / MouseSticks.CM_PER_INCH;
        float x = 0.0F;
        float y = 0.0F;
        int steps = Math.round(t / STEP);
        for (int i = 0; i < steps; i++) {
            Phase phase = phaseAt(i * STEP + STEP * 0.5F);
            x += phase.vx() * STEP;
            y += phase.vy() * STEP;
            out = sticks.update(phase.vx() * STEP * countsPerCm, -phase.vy() * STEP * countsPerCm, STEP, settings, selfLevel);
        }
        return new Frame(t, x, y, phaseAt(t).cue(), out);
    }

    public static MouseSettings demoSettings(MouseSettings user) {
        MouseSettings settings = user.copy();
        settings.sensitivity = MouseSettings.DEFAULT_SENSITIVITY;
        settings.dpi = DPI;
        settings.sanitize();
        return settings;
    }

    public static Phase phaseAt(float seconds) {
        for (Phase phase : PHASES) {
            if (seconds < phase.end()) {
                return phase;
            }
        }
        return PHASES.getLast();
    }

    public static List<Phase> all() {
        return PHASES;
    }

    private static List<Phase> phases() {
        float v = SPEED_CM_PER_SECOND;
        float t = 0.0F;
        Phase start = new Phase(t, t += 0.6F, 0.0F, 0.0F, Cue.START);
        Phase right = new Phase(t, t += MOVE, v, 0.0F, Cue.RIGHT);
        Phase stopSide = new Phase(t, t += PAUSE, 0.0F, 0.0F, Cue.STOP_SIDE);
        Phase left = new Phase(t, t += MOVE, -v, 0.0F, Cue.LEFT);
        Phase stopBack = new Phase(t, t += PAUSE, 0.0F, 0.0F, Cue.STOP_BACK);
        Phase forward = new Phase(t, t += MOVE, 0.0F, v, Cue.FORWARD);
        Phase stopForward = new Phase(t, t += PAUSE, 0.0F, 0.0F, Cue.STOP_FORWARD);
        Phase back = new Phase(t, t += MOVE, 0.0F, -v, Cue.BACK);
        Phase end = new Phase(t, t + PAUSE, 0.0F, 0.0F, Cue.STOP_END);
        return List.of(start, right, stopSide, left, stopBack, forward, stopForward, back, end);
    }
}

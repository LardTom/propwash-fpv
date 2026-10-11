package de.lardtom.propwash.guide.scene;

import java.util.Objects;

public record TimedCue(float start, float duration, Cue cue) {
    public TimedCue {
        Objects.requireNonNull(cue, "cue");
        if (!Float.isFinite(start) || start < 0.0F) {
            throw new IllegalArgumentException("Startzeit muss >= 0 sein: " + start);
        }
        if (!Float.isFinite(duration)) {
            throw new IllegalArgumentException("Dauer ungültig: " + duration);
        }
    }

    public float end(float stepDuration) {
        if (this.duration > 0.0F) {
            return this.start + this.duration;
        }
        return this.cue.transientCue() ? stepDuration : this.start;
    }
}

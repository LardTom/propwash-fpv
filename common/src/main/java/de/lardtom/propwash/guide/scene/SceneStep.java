package de.lardtom.propwash.guide.scene;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record SceneStep(String name, float duration, List<TimedCue> cues) {
    public SceneStep {
        Objects.requireNonNull(name, "name");
        if (!(duration > 0.0F) || !Float.isFinite(duration)) {
            throw new IllegalArgumentException("Schritt " + name + " braucht eine Dauer > 0");
        }
        cues = cues.stream().sorted(Comparator.comparingDouble(TimedCue::start)).toList();
    }
}

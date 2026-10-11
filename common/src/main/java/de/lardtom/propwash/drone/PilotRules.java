package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.VideoSystem;
import org.jspecify.annotations.Nullable;

public final class PilotRules {
    public enum Decision {
        LINE_OF_SIGHT(PilotMode.LINE_OF_SIGHT),
        FPV(PilotMode.FPV),
        NO_VIDEO(PilotMode.LINE_OF_SIGHT);

        private final PilotMode mode;

        Decision(PilotMode mode) {
            this.mode = mode;
        }

        public PilotMode mode() {
            return this.mode;
        }

        public boolean connects() {
            return this != NO_VIDEO;
        }

        public boolean blind() {
            return this == NO_VIDEO;
        }
    }

    private PilotRules() {
    }

    public static Decision decide(@Nullable VideoSystem goggles, VideoSystem droneVideo) {
        if (goggles == null) {
            return Decision.LINE_OF_SIGHT;
        }
        return goggles.receives(droneVideo) ? Decision.FPV : Decision.NO_VIDEO;
    }

    public static boolean changed(@Nullable Decision before, Decision now) {
        return before != null && before != now;
    }
}

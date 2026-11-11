package de.lardtom.propwash.stats;

import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import org.jspecify.annotations.Nullable;

public final class StatClass {
    public static final FrameRole FALLBACK = FrameRole.FREESTYLE;

    private StatClass() {
    }

    public static FrameRole of(@Nullable FrameType frame) {
        return frame == null ? FALLBACK : frame.definition().role();
    }

    public static FrameRole of(@Nullable DroneBuild build) {
        return build == null ? FALLBACK : of(build.frame());
    }
}

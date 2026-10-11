package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import org.jspecify.annotations.Nullable;

public final class VideoTabRules {
    private VideoTabRules() {
    }

    public static boolean showsSettings(@Nullable VideoSystem worn) {
        return worn != null;
    }

    public static boolean showsCreative(@Nullable VideoSystem worn) {
        return worn == VideoSystem.CREATIVE;
    }

    public static VideoConfig defaults(VideoConfig current, @Nullable VideoSystem worn) {
        if (!showsSettings(worn)) {
            return current.copy();
        }
        VideoConfig defaults = new VideoConfig();
        if (!showsCreative(worn)) {
            defaults.keepCreative(current);
        }
        return defaults;
    }

    public static VideoConfig saved(VideoConfig stored, VideoConfig draft, @Nullable VideoSystem worn) {
        if (!showsSettings(worn)) {
            return stored.copy();
        }
        VideoConfig saved = draft.copy();
        if (!showsCreative(worn)) {
            saved.keepCreative(stored);
        }
        saved.validate();
        return saved;
    }
}

package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.video.AnalogBreakup;
import de.lardtom.propwash.video.EffectParams;
import de.lardtom.propwash.video.FeedModel;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class VideoBreakupProbe {
    private VideoBreakupProbe() {
    }

    public static void force(float quality, @Nullable VideoSystem system) {
        VideoState state = VideoState.INSTANCE;
        state.overrideTestQuality(quality);
        state.overrideTestTransmitter(system);
        state.overrideTestReceiver(system != null, system);
    }

    public static void emphasize(AnalogBreakup.@Nullable Stage stage) {
        if (stage == null) {
            return;
        }
        FeedModel feed = VideoState.INSTANCE.feed();
        switch (stage) {
            case GLITCHES -> feed.forceGlitch(0.12);
            case SNOW -> feed.forceSyncShift(0.12, 0.05F, 0.32F, 0.58F);
            default -> {
            }
        }
    }

    public static void clear() {
        VideoState state = VideoState.INSTANCE;
        state.overrideTestQuality(Float.NaN);
        state.overrideTestTransmitter(null);
        state.overrideTestReceiver(false, null);
    }

    public static AnalogBreakup.Stage analogStage() {
        return VideoState.INSTANCE.feed().analogStage();
    }

    public static boolean linkIndicatorShown() {
        VideoState state = VideoState.INSTANCE;
        return state.active() && VideoOverlay.signalLossShown(state, state.params());
    }

    public static String describe() {
        VideoState state = VideoState.INSTANCE;
        EffectParams p = state.params();
        FeedModel feed = state.feed();
        return String.format(Locale.ROOT,
                "status=%s system=%s link=%.2f analog=%s digital=%s farbe=%.2f schnee=%.2f sync=%.3f roll=%.2f glitch=%.2f block=%.2f aufl=%.2f verlust=%.2f osdShift=%.4f",
                state.status(), state.system(), feed.link(), feed.analogStage(), feed.digitalStage(), 1.0F - p.desaturation, p.noise,
                p.syncShift, p.roll, p.glitch, p.blockiness, p.resolution, p.noSignal, p.osdShift);
    }
}

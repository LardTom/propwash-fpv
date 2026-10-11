package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.audio.MotorSynth;
import org.jspecify.annotations.Nullable;

public final class MotorAudioTap {
    public static final int SAMPLE_RATE = MotorSynth.SAMPLE_RATE;
    private static volatile @Nullable Listener listener;
    private static volatile long published;

    private MotorAudioTap() {
    }

    public static void listen(@Nullable Listener next) {
        listener = next;
    }

    public static void release(Listener owner) {
        if (listener == owner) {
            listener = null;
        }
    }

    public static boolean listening() {
        return listener != null;
    }

    public static long published() {
        return published;
    }

    static void publish(int sourceId, short[] pcm, int frames) {
        Listener current = listener;
        if (current == null || sourceId < 0) {
            return;
        }
        try {
            current.onPcm(sourceId, pcm, frames);
            published += frames;
        } catch (RuntimeException e) {
            listener = null;
            Constants.LOG.warn("Propwash: Motorton-Abgriff abgeschaltet: {}", e.toString());
        }
    }

    @FunctionalInterface
    public interface Listener {
        void onPcm(int sourceId, short[] pcm, int frames);
    }
}

package de.lardtom.propwash.video.client;

public final class BossFeedProbe {
    private BossFeedProbe() {
    }

    public static float brightness() {
        return VideoState.INSTANCE.params().brightness;
    }

    public static float noise() {
        return VideoState.INSTANCE.params().noise;
    }
}

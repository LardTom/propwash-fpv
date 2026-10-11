package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.config.JsonConfig;

public final class AudioConfig {
    public static final int MIN_VOICES = 1;
    public static final int MAX_VOICES = 6;
    public static final JsonConfig<AudioConfig> CONFIG = JsonConfig.create("audio", AudioConfig.class, AudioConfig::new, AudioConfig::validate);

    public double motorVolume = 0.8;
    public double beepVolume = 0.7;
    public boolean doppler = true;
    public int voiceLimit = 4;
    public boolean synthesizer = true;

    public AudioConfig copy() {
        AudioConfig c = new AudioConfig();
        c.motorVolume = this.motorVolume;
        c.beepVolume = this.beepVolume;
        c.doppler = this.doppler;
        c.voiceLimit = this.voiceLimit;
        c.synthesizer = this.synthesizer;
        return c;
    }

    public static AudioConfig get() {
        return CONFIG.get();
    }

    static void validate(AudioConfig c) {
        c.motorVolume = clamp01(c.motorVolume, 0.8);
        c.beepVolume = clamp01(c.beepVolume, 0.7);
        c.voiceLimit = Math.max(MIN_VOICES, Math.min(MAX_VOICES, c.voiceLimit));
    }

    private static double clamp01(double v, double fallback) {
        if (!Double.isFinite(v)) {
            return fallback;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}

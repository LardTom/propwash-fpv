package de.lardtom.propwash.video.record.mp4;

import java.util.List;

public record Mp4Info(List<String> topLevelBoxes, String handler, String codec, int width, int height, int movieTimescale,
                      long movieDuration, int mediaTimescale, long mediaDuration, long sampleDurationTotal, int sampleCount,
                      int syncSampleCount, boolean startsWithSync, int profile, int level, byte[] sps, String audioCodec,
                      int audioSampleRate, int audioChannels, long audioMediaDuration, int audioSampleCount, int audioObjectType,
                      long audioEditMediaTime, long audioEditDuration, byte[] audioConfig, int trackCount) {
    public double durationSeconds() {
        return this.mediaTimescale <= 0 ? 0.0 : this.mediaDuration / (double) this.mediaTimescale;
    }

    public double movieSeconds() {
        return this.movieTimescale <= 0 ? 0.0 : this.movieDuration / (double) this.movieTimescale;
    }

    public double frameRate() {
        double seconds = this.durationSeconds();
        return seconds <= 0.0 ? 0.0 : this.sampleCount / seconds;
    }

    public boolean hasAudio() {
        return !this.audioCodec.isEmpty();
    }

    public double audioSeconds() {
        if (this.audioSampleRate <= 0) {
            return 0.0;
        }
        if (this.audioEditDuration > 0L && this.movieTimescale > 0) {
            return this.audioEditDuration / (double) this.movieTimescale;
        }
        return Math.max(0L, this.audioMediaDuration - this.audioEditMediaTime) / (double) this.audioSampleRate;
    }
}

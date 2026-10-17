package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.video.record.EncoderChoice;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingQuality;
import de.lardtom.propwash.video.record.RecordingResources;
import de.lardtom.propwash.video.record.Stabilizer;
import de.lardtom.propwash.video.record.external.FfmpegLocator;
import de.lardtom.propwash.video.record.path.KeyframeDensity;
import de.lardtom.propwash.video.record.path.Simplification;
import org.jspecify.annotations.Nullable;

public final class RecordingConfig {
    public static final JsonConfig<RecordingConfig> CONFIG =
            JsonConfig.create("action_cam", RecordingConfig.class, RecordingConfig::new, RecordingConfig::validate);
    public static final int MIN_MEMORY_MB = 64;
    public static final int MAX_MEMORY_MB = 8192;
    public static final int MAX_THREADS = 16;
    public static final int DEFAULT_STRENGTH = 50;
    public static final int MAX_PATH_LENGTH = 1024;

    public int height = 1080;
    public int fps = 60;
    public Stabilizer.Mode stabilization = Stabilizer.Mode.SMOOTH;
    public int stabilizationStrength = DEFAULT_STRENGTH;
    public RecordingQuality quality = RecordingQuality.DEFAULT;
    public boolean grain = true;
    public boolean autoRecord = false;
    public boolean audio = true;
    public boolean showIndicator = true;
    public EncoderChoice encoder = EncoderChoice.AUTO;
    public String ffmpegPath = "";
    public int bufferMb = 0;
    public int encoderThreads = 0;
    public boolean flashbackAutoRecord = true;
    public double flashbackSimplify = -1.0;
    public boolean flashbackKeyframeLimit = true;
    public @Nullable KeyframeDensity flashbackDensity;

    public static RecordingConfig get() {
        return CONFIG.get();
    }

    public RecordingConfig copy() {
        RecordingConfig copy = new RecordingConfig();
        copy.height = this.height;
        copy.fps = this.fps;
        copy.stabilization = this.stabilization;
        copy.stabilizationStrength = this.stabilizationStrength;
        copy.quality = this.quality;
        copy.grain = this.grain;
        copy.autoRecord = this.autoRecord;
        copy.audio = this.audio;
        copy.showIndicator = this.showIndicator;
        copy.encoder = this.encoder;
        copy.ffmpegPath = this.ffmpegPath;
        copy.bufferMb = this.bufferMb;
        copy.encoderThreads = this.encoderThreads;
        copy.flashbackAutoRecord = this.flashbackAutoRecord;
        copy.flashbackSimplify = this.flashbackSimplify;
        copy.flashbackKeyframeLimit = this.flashbackKeyframeLimit;
        return copy;
    }

    public void validate() {
        if (!RecordingFormat.HEIGHTS.contains(this.height)) {
            this.height = 1080;
        }
        if (!RecordingFormat.RATES.contains(this.fps)) {
            this.fps = 60;
        }
        if (this.stabilization == null) {
            this.stabilization = Stabilizer.Mode.SMOOTH;
        }
        if (this.quality == null) {
            this.quality = RecordingQuality.DEFAULT;
        }
        this.stabilizationStrength = Math.max(0, Math.min(100, this.stabilizationStrength));
        if (this.encoder == null) {
            this.encoder = EncoderChoice.AUTO;
        }
        this.ffmpegPath = FfmpegLocator.clean(this.ffmpegPath);
        if (this.ffmpegPath.length() > MAX_PATH_LENGTH) {
            this.ffmpegPath = "";
        }
        this.bufferMb = this.bufferMb <= 0 ? 0 : Math.max(MIN_MEMORY_MB, Math.min(MAX_MEMORY_MB, this.bufferMb));
        this.encoderThreads = Math.max(0, Math.min(MAX_THREADS, this.encoderThreads));
        if (!(this.flashbackSimplify >= 0.0)) {
            this.flashbackSimplify = this.flashbackDensity == null ? Simplification.DEFAULT_SLIDER : Simplification.sliderOf(this.flashbackDensity);
        }
        this.flashbackSimplify = Math.min(1.0, this.flashbackSimplify);
        this.flashbackDensity = null;
    }

    public int threads() {
        if (this.encoderThreads > 0) {
            return this.encoderThreads;
        }
        return RecordingResources.encoderThreads(Runtime.getRuntime().availableProcessors());
    }

    public long memoryCapBytes() {
        if (this.bufferMb > 0) {
            return this.bufferMb * RecordingResources.MB;
        }
        long heap = Runtime.getRuntime().maxMemory();
        return RecordingResources.autoBuffer(RecordingResources.physicalMemory(), heap == Long.MAX_VALUE ? 0L : heap);
    }

    public Simplification flashbackSimplification() {
        return Simplification.ofSlider(this.flashbackSimplify);
    }

    public float strength() {
        return this.stabilizationStrength / 100.0F;
    }
}

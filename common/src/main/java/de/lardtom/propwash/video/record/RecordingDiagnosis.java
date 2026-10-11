package de.lardtom.propwash.video.record;

import java.util.Locale;

public record RecordingDiagnosis(Limit limit, Tip tip, int targetFps, double gameFps, double encoderFps, long lostFrames, int backlogFrames,
                                 double saveSeconds) {
    public static final RecordingDiagnosis NONE = new RecordingDiagnosis(Limit.NONE, Tip.NONE, 0, 0.0, 0.0, 0L, 0, 0.0);
    public static final double GAME_GAP_SHARE = 0.05;
    public static final double SPOOL_SLOW_MS_PER_FRAME = 0.8;

    public static RecordingDiagnosis of(PipelineStats.Snapshot window, RecordingJob.Backlog backlog, int previousBacklog, Tip encoderTip) {
        int fps = Math.max(1, window.fps());
        double encoderFps = window.encoderFps();
        double save = encoderFps > 0.0 ? backlog.queued() / encoderFps : 0.0;
        long memoryLost = window.memoryDrops() + window.decimated();
        if (memoryLost > 0L) {
            if (backlog.spoolFailed() || backlog.spoolFull()) {
                return new RecordingDiagnosis(Limit.MEMORY, Tip.SPACE, fps, window.gameFps(), encoderFps, memoryLost, backlog.queued(), save);
            }
            if (window.spoolWrites() > 0L && window.spoolWriteMs() * fps > 1000.0 * SPOOL_SLOW_MS_PER_FRAME) {
                return new RecordingDiagnosis(Limit.DISK, Tip.DISK, fps, window.gameFps(), encoderFps, memoryLost, backlog.queued(), save);
            }
            return new RecordingDiagnosis(Limit.MEMORY, encoderTip, fps, window.gameFps(), encoderFps, memoryLost, backlog.queued(), save);
        }
        if (window.ringFullDrops() > 0L) {
            return new RecordingDiagnosis(Limit.READBACK, Tip.GRAPHICS, fps, window.gameFps(), encoderFps, window.ringFullDrops(),
                    backlog.queued(), save);
        }
        if (backlog.queued() > fps && backlog.queued() >= previousBacklog) {
            return new RecordingDiagnosis(Limit.ENCODER, encoderTip, fps, window.gameFps(), encoderFps, 0L, backlog.queued(), save);
        }
        if (window.slots() > fps / 2 && window.gapShare() > GAME_GAP_SHARE) {
            return new RecordingDiagnosis(Limit.GAME, fps > 30 ? Tip.FRAME_RATE : Tip.GRAPHICS, fps, window.gameFps(), encoderFps,
                    window.gameGapSlots(), backlog.queued(), save);
        }
        return new RecordingDiagnosis(Limit.NONE, Tip.NONE, fps, window.gameFps(), encoderFps, 0L, backlog.queued(), save);
    }

    public static Tip encoderTip(boolean hardwareInUse, EncoderChoice choice, boolean ffmpegMissing, RecordingFormat format) {
        if (!hardwareInUse && choice == EncoderChoice.BUILTIN) {
            return Tip.ENABLE_HARDWARE;
        }
        if (!hardwareInUse && ffmpegMissing) {
            return Tip.HARDWARE;
        }
        return Lower.of(format) == Lower.NONE ? Tip.BUSY : Tip.LOWER;
    }

    public boolean losing() {
        return this.limit == Limit.READBACK || this.limit == Limit.DISK || this.limit == Limit.MEMORY;
    }

    public String describe() {
        return String.format(Locale.ROOT, "%s (Tipp %s): Spiel %.1f FPS, Encoder %.1f B/s bei Ziel %d, verloren %d, Rückstand %d Bilder ≈ %.1f s",
                this.limit, this.tip, this.gameFps, this.encoderFps, this.targetFps, this.lostFrames, this.backlogFrames, this.saveSeconds);
    }

    public enum Limit {
        NONE,
        GAME,
        READBACK,
        ENCODER,
        DISK,
        MEMORY;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Tip {
        NONE,
        FRAME_RATE,
        GRAPHICS,
        HARDWARE,
        ENABLE_HARDWARE,
        LOWER,
        BUSY,
        DISK,
        SPACE;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public boolean suggestsLower() {
            return this == HARDWARE || this == ENABLE_HARDWARE || this == LOWER;
        }
    }

    public enum Lower {
        NONE,
        RESOLUTION,
        FRAME_RATE,
        BOTH;

        public static Lower of(RecordingFormat format) {
            boolean smaller = format.height() > RecordingFormat.HEIGHTS.getFirst();
            boolean slower = format.fps() > RecordingFormat.RATES.getFirst();
            return smaller ? slower ? BOTH : RESOLUTION : slower ? FRAME_RATE : NONE;
        }

        public static int lowerHeight(RecordingFormat format) {
            int lower = RecordingFormat.HEIGHTS.getFirst();
            for (int height : RecordingFormat.HEIGHTS) {
                if (height < format.height()) {
                    lower = height;
                }
            }
            return lower;
        }
    }
}

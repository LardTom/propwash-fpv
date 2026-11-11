package de.lardtom.propwash.race;

import de.lardtom.propwash.video.record.external.HardwareEncoders;

public final class ReplayExport {
    private ReplayExport() {
    }

    public enum Block {
        NONE("tooltip"),
        LOADING("loading"),
        BUSY("busy"),
        SAVING("saving_blocked"),
        CHECKING("checking"),
        NO_FFMPEG("no_ffmpeg"),
        FFMPEG_BROKEN("ffmpeg_broken");

        private final String key;

        Block(String key) {
            this.key = key;
        }

        public String translationKey() {
            return "propwash.race.replay.video." + this.key;
        }
    }

    public static Block ffmpeg(HardwareEncoders.State state) {
        return switch (state) {
            case CHECKING -> Block.CHECKING;
            case NO_FFMPEG -> Block.NO_FFMPEG;
            case UNUSABLE -> Block.FFMPEG_BROKEN;
            case OFF, NO_HARDWARE, READY, BROKEN -> Block.NONE;
        };
    }

    public static long time(long begin, long end, long elapsedNanos) {
        return Math.min(end, begin + Math.max(0L, elapsedNanos));
    }

    public static float fraction(long time, long begin, long end) {
        if (end <= begin) {
            return 0.0F;
        }
        return (float) Math.max(0.0, Math.min(1.0, (time - begin) / (double) (end - begin)));
    }
}

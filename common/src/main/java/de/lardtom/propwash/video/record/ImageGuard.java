package de.lardtom.propwash.video.record;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class ImageGuard {
    private final boolean fallbacks;
    private final List<ImagePath> broken = new ArrayList<>();
    private ImagePath path = ImagePath.GPU;
    private long acceptedFrames;
    private long blankFrames;
    private long droppedFrames;

    public ImageGuard(boolean fallbacks) {
        this.fallbacks = fallbacks;
    }

    public static boolean blank(ByteBuffer i420, RecordingFormat format) {
        return zero(i420, format.width() * format.height());
    }

    public static boolean zero(ByteBuffer data, int bytes) {
        int end = Math.min(bytes, data.limit());
        int i = 0;
        for (; i + Long.BYTES <= end; i += Long.BYTES) {
            if (data.getLong(i) != 0L) {
                return false;
            }
        }
        for (; i < end; i++) {
            if (data.get(i) != 0) {
                return false;
            }
        }
        return true;
    }

    public Verdict check(ImagePath source, boolean blank) {
        if (this.path == ImagePath.NONE || source != this.path) {
            this.droppedFrames++;
            return Verdict.DROP;
        }
        if (!blank) {
            this.acceptedFrames++;
            return Verdict.ACCEPT;
        }
        this.blankFrames++;
        this.broken.add(source);
        this.path = this.next(source);
        return this.path == ImagePath.NONE ? Verdict.ABORT : Verdict.SWITCH;
    }

    private ImagePath next(ImagePath from) {
        if (!this.fallbacks) {
            return ImagePath.NONE;
        }
        return switch (from) {
            case GPU -> ImagePath.READ;
            case READ -> ImagePath.CPU;
            case CPU, NONE -> ImagePath.NONE;
        };
    }

    public ImagePath path() {
        return this.path;
    }

    public List<ImagePath> broken() {
        return List.copyOf(this.broken);
    }

    public long acceptedFrames() {
        return this.acceptedFrames;
    }

    public long blankFrames() {
        return this.blankFrames;
    }

    public long droppedFrames() {
        return this.droppedFrames;
    }

    public enum Verdict {
        ACCEPT,
        DROP,
        SWITCH,
        ABORT
    }
}

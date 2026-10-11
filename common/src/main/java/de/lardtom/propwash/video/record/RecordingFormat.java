package de.lardtom.propwash.video.record;

import de.lardtom.propwash.build.ActionCamSpec;

import java.util.ArrayList;
import java.util.List;

public record RecordingFormat(int width, int height, int fps) {
    public static final int MAX_HEIGHT = 1440;
    public static final List<Integer> HEIGHTS = List.of(720, 1080, 1440);
    public static final List<Integer> RATES = List.of(30, 60);
    public static final RecordingFormat DEFAULT = of(1080, 60);

    public RecordingFormat {
        if (width <= 0 || height <= 0 || width % 8 != 0 || height % 4 != 0) {
            throw new IllegalArgumentException("size " + width + "x" + height);
        }
        if (fps <= 0) {
            throw new IllegalArgumentException("fps " + fps);
        }
    }

    public static RecordingFormat of(int height, int fps) {
        int h = Math.max(4, height / 4 * 4);
        int w = Math.max(8, Math.round(h * 16.0F / 9.0F / 8.0F) * 8);
        return new RecordingFormat(w, h, fps);
    }

    public static List<Integer> heightsFor(ActionCamSpec spec) {
        List<Integer> out = new ArrayList<>();
        for (int height : HEIGHTS) {
            if (height <= Math.min(MAX_HEIGHT, spec.maxHeight())) {
                out.add(height);
            }
        }
        if (out.isEmpty()) {
            out.add(HEIGHTS.get(0));
        }
        return List.copyOf(out);
    }

    public static List<Integer> ratesFor(ActionCamSpec spec) {
        List<Integer> out = new ArrayList<>();
        for (int rate : RATES) {
            if (rate <= spec.maxFps()) {
                out.add(rate);
            }
        }
        if (out.isEmpty()) {
            out.add(RATES.get(0));
        }
        return List.copyOf(out);
    }

    public static RecordingFormat forCamera(ActionCamSpec spec, int wantedHeight, int wantedFps) {
        int height = HEIGHTS.get(0);
        for (int candidate : heightsFor(spec)) {
            if (candidate <= wantedHeight) {
                height = candidate;
            }
        }
        int fps = RATES.get(0);
        for (int candidate : ratesFor(spec)) {
            if (candidate <= wantedFps) {
                fps = candidate;
            }
        }
        return of(height, fps);
    }

    public int frameBytes() {
        return this.width * this.height * 3 / 2;
    }

    public long bitrate() {
        double pixelsPerSecond = (double) this.width * this.height * this.fps;
        double bitsPerPixel = this.fps > 30 ? 0.26 : 0.34;
        return Math.round(pixelsPerSecond * bitsPerPixel / 100_000.0) * 100_000L;
    }

    public long frameNanos() {
        return 1_000_000_000L / this.fps;
    }

    public String label() {
        return this.height + "p" + this.fps;
    }
}

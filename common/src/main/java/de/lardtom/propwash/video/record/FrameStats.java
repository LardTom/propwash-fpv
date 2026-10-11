package de.lardtom.propwash.video.record;

import java.nio.ByteBuffer;

public final class FrameStats {
    private static final int COLUMNS = 32;
    private static final int ROWS = 6;

    private volatile Snapshot last = Snapshot.EMPTY;
    private long frames;

    public synchronized void reset() {
        this.frames = 0L;
        this.last = Snapshot.EMPTY;
    }

    public Snapshot last() {
        return this.last;
    }

    public void sample(byte[] i420, RecordingFormat format, long slot) {
        this.sample(ByteBuffer.wrap(i420), format, slot);
    }

    public synchronized void sample(ByteBuffer i420, RecordingFormat format, long slot) {
        int width = format.width();
        int height = format.height();
        double[] top = region(i420, width, height, 0, height / 4);
        double[] bottom = region(i420, width, height, height * 3 / 4, height);
        double min = 255.0;
        double max = 0.0;
        for (int row = 0; row < ROWS * 2; row++) {
            int y = (int) ((row + 0.5) * height / (ROWS * 2));
            for (int col = 0; col < COLUMNS; col++) {
                int x = (int) ((col + 0.5) * width / COLUMNS);
                int value = i420.get(y * width + x) & 0xFF;
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        this.frames++;
        this.last = new Snapshot(slot, this.frames, top[0], top[1], top[2], bottom[0], bottom[1], bottom[2], max - min);
    }

    private static double[] region(ByteBuffer i420, int width, int height, int fromRow, int toRow) {
        int chromaWidth = width / 2;
        int chromaHeight = height / 2;
        int uOffset = width * height;
        int vOffset = uOffset + chromaWidth * chromaHeight;
        double y = 0.0;
        double u = 0.0;
        double v = 0.0;
        int count = 0;
        for (int row = 0; row < ROWS; row++) {
            int py = fromRow + (int) ((row + 0.5) * (toRow - fromRow) / ROWS);
            for (int col = 0; col < COLUMNS; col++) {
                int px = (int) ((col + 0.5) * width / COLUMNS);
                y += i420.get(py * width + px) & 0xFF;
                int c = (py / 2) * chromaWidth + px / 2;
                u += i420.get(uOffset + c) & 0xFF;
                v += i420.get(vOffset + c) & 0xFF;
                count++;
            }
        }
        return new double[]{y / count, u / count, v / count};
    }

    public record Snapshot(long slot, long frames, double topY, double topCb, double topCr, double bottomY, double bottomCb, double bottomCr,
                           double lumaSpread) {
        public static final Snapshot EMPTY = new Snapshot(-1L, 0L, 0.0, 128.0, 128.0, 0.0, 128.0, 128.0, 0.0);
    }
}

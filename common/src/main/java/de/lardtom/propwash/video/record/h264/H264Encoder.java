package de.lardtom.propwash.video.record.h264;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.LockSupport;

public final class H264Encoder implements AutoCloseable {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final int LOG2_MAX_FRAME_NUM = 8;
    private static final int[][] LEVELS = {
            {10, 1485, 99, 64}, {11, 3000, 396, 192}, {12, 6000, 396, 384}, {13, 11880, 396, 768},
            {20, 11880, 396, 2000}, {21, 19800, 792, 4000}, {22, 20250, 1620, 4000}, {30, 40500, 1620, 10000},
            {31, 108000, 3600, 14000}, {32, 216000, 5120, 20000}, {40, 245760, 8192, 20000}, {41, 245760, 8192, 50000},
            {42, 522240, 8704, 50000}, {50, 589824, 22080, 135000}, {51, 983040, 36864, 240000}, {52, 2073600, 36864, 240000}
    };

    private final int width;
    private final int height;
    private final int fps;
    private final EncoderSettings settings;
    private final EncodeContext context;
    private final SliceEncoder[] slices;
    private final int[][] sliceRows;
    private final int[][] halfScratch;
    private final ExecutorService pool;
    private final RateControl rate;
    private final int threads;
    private final AtomicIntegerArray deblockProgress;
    private final AtomicInteger deblockNextRow = new AtomicInteger();
    private final short[] mvScratch;
    private final byte[] sps;
    private final byte[] pps;
    private long frameIndex;
    private boolean halfPending;
    private int framesSinceIdr;
    private int idrCount;
    private boolean closed;

    public H264Encoder(int width, int height, int fps, EncoderSettings settings) {
        if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0) {
            throw new IllegalArgumentException("size " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.fps = Math.max(1, fps);
        this.settings = settings;
        int codedWidth = (width + 15) & ~15;
        int codedHeight = (height + 15) & ~15;
        this.context = new EncodeContext(codedWidth, codedHeight, LOG2_MAX_FRAME_NUM);
        int mbRows = codedHeight / 16;
        int sliceCount = Math.max(1, Math.min(settings.slices(), mbRows));
        this.slices = new SliceEncoder[sliceCount];
        this.sliceRows = new int[sliceCount][2];
        for (int i = 0; i < sliceCount; i++) {
            this.slices[i] = new SliceEncoder(this.context);
            this.sliceRows[i][0] = mbRows * i / sliceCount;
            this.sliceRows[i][1] = mbRows * (i + 1) / sliceCount;
        }
        int threads = Math.max(1, Math.min(settings.threads(), sliceCount));
        this.halfScratch = new int[Math.max(sliceCount, threads)][codedWidth + 2 * Picture.PAD];
        this.pool = threads > 1 ? Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "Propwash video encoder " + THREAD_IDS.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }) : null;
        this.rate = new RateControl(settings.bitrate(), this.fps, settings.quality(), settings.minQp(), settings.maxQp());
        this.context.minQp = settings.minQp();
        this.context.maxQp = settings.maxQp();
        this.context.adaptiveQuant = settings.adaptiveQuant();
        this.threads = threads;
        this.deblockProgress = new AtomicIntegerArray(mbRows);
        this.mvScratch = new short[this.context.mbCount()];
        this.sps = this.buildSps(codedWidth, codedHeight);
        this.pps = buildPps();
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int fps() {
        return this.fps;
    }

    public byte[] sps() {
        return this.sps.clone();
    }

    public byte[] pps() {
        return this.pps.clone();
    }

    public int level() {
        return this.sps[3] & 0xFF;
    }

    public long frames() {
        return this.frameIndex;
    }

    public int currentQp() {
        return this.rate.qp();
    }

    public double bufferFill() {
        return this.rate.fill();
    }

    public EncodedFrame encode(byte[] i420, int preset) {
        return this.encode(ByteBuffer.wrap(i420), preset);
    }

    public EncodedFrame encode(ByteBuffer i420, int preset) {
        if (this.closed) {
            throw new IllegalStateException("closed");
        }
        this.load(i420);
        EncodeContext c = this.context;
        boolean idr = this.framesSinceIdr == 0 || this.framesSinceIdr >= this.settings.gop();
        if (idr) {
            this.framesSinceIdr = 0;
        }
        c.idr = idr;
        c.intra = idr;
        c.frameNum = this.framesSinceIdr % (1 << LOG2_MAX_FRAME_NUM);
        c.idrPicId = this.idrCount & 1;
        c.qp = this.rate.qpFor(idr);
        c.preset = Math.max(0, Math.min(2, preset));
        c.deblock = this.settings.deblock();
        if (this.halfPending && !idr && c.preset != SliceEncoder.PRESET_FASTEST) {
            this.computeHalfPlanes(c.ref);
        }
        byte[][] nals = this.runSlices();
        if (c.deblock) {
            this.deblock();
        }
        this.finishReference();
        long bits = 0L;
        for (byte[] nal : nals) {
            bits += nal.length * 8L;
        }
        this.rate.update(idr, c.qp, bits);
        if (idr) {
            this.idrCount++;
        }
        this.framesSinceIdr++;
        this.frameIndex++;
        return new EncodedFrame(nals, idr, c.qp);
    }

    public EncodedFrame repeat() {
        if (this.frameIndex == 0L) {
            throw new IllegalStateException("no reference");
        }
        EncodeContext c = this.context;
        int frameNum = this.framesSinceIdr % (1 << LOG2_MAX_FRAME_NUM);
        BitWriter w = new BitWriter(64);
        w.ue(0);
        w.ue(0);
        w.ue(0);
        w.bits(frameNum, LOG2_MAX_FRAME_NUM);
        w.bits(0, 1);
        w.bits(0, 1);
        w.bits(0, 1);
        w.se(c.qp - 26);
        w.ue(1);
        w.ue(c.mbCount());
        w.trailing();
        byte[] nal = Nal.wrap(2, Nal.SLICE, w);
        this.rate.repeated();
        this.framesSinceIdr++;
        this.frameIndex++;
        return new EncodedFrame(new byte[][]{nal}, false, c.qp);
    }

    int macroblockCount() {
        return this.context.mbCount();
    }

    int macroblockWidth() {
        return this.context.mbWidth;
    }

    int macroblockQp(int mb) {
        return this.context.mbQp[mb];
    }

    int motionX(int mb) {
        return this.context.mvX[mb];
    }

    int motionY(int mb) {
        return this.context.mvY[mb];
    }

    boolean macroblockIntra(int mb) {
        return this.context.kind[mb] == EncodeContext.INTRA;
    }

    byte[] reconstructionI420() {
        Picture p = this.context.ref;
        int w = this.width;
        int h = this.height;
        byte[] out = new byte[w * h * 3 / 2];
        for (int row = 0; row < h; row++) {
            System.arraycopy(p.y, p.origin + row * p.stride, out, row * w, w);
        }
        int cw = w / 2;
        int ch = h / 2;
        for (int row = 0; row < ch; row++) {
            System.arraycopy(p.u, p.originC + row * p.strideC, out, w * h + row * cw, cw);
            System.arraycopy(p.v, p.originC + row * p.strideC, out, w * h + cw * ch + row * cw, cw);
        }
        return out;
    }

    private byte[][] runSlices() {
        int count = this.slices.length;
        byte[][] out = new byte[count][];
        if (this.pool == null) {
            for (int i = 0; i < count; i++) {
                out[i] = this.slices[i].encode(this.sliceRows[i][0], this.sliceRows[i][1]);
            }
            return out;
        }
        List<Future<byte[]>> futures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int index = i;
            futures.add(this.pool.submit(() -> this.slices[index].encode(this.sliceRows[index][0], this.sliceRows[index][1])));
        }
        for (int i = 0; i < count; i++) {
            out[i] = await(futures.get(i));
        }
        return out;
    }

    private void deblock() {
        EncodeContext c = this.context;
        int rows = c.mbHeight;
        if (this.pool == null || rows < 2) {
            Deblocker.filterRows(c, 0, rows);
            return;
        }
        for (int row = 0; row < rows; row++) {
            this.deblockProgress.set(row, 0);
        }
        this.deblockNextRow.set(0);
        int workers = Math.min(this.threads, rows);
        List<Future<?>> futures = new ArrayList<>(workers);
        for (int i = 0; i < workers; i++) {
            futures.add(this.pool.submit(this::deblockWorker));
        }
        for (Future<?> future : futures) {
            await(future);
        }
    }

    private void deblockWorker() {
        EncodeContext c = this.context;
        int width = c.mbWidth;
        int[] bs = new int[4];
        while (true) {
            int row = this.deblockNextRow.getAndIncrement();
            if (row >= c.mbHeight) {
                return;
            }
            for (int mbx = 0; mbx < width; mbx++) {
                if (row > 0) {
                    int needed = Math.min(width, mbx + 2);
                    int spins = 0;
                    while (this.deblockProgress.get(row - 1) < needed) {
                        if (++spins < 200) {
                            Thread.onSpinWait();
                        } else {
                            LockSupport.parkNanos(20_000L);
                        }
                    }
                }
                Deblocker.filterMacroblock(c, mbx, row, bs);
                this.deblockProgress.lazySet(row, mbx + 1);
            }
        }
    }

    private void updateGlobalMotion() {
        EncodeContext c = this.context;
        int count = 0;
        short[] scratch = this.mvScratch;
        for (int mb = 0; mb < c.mbCount(); mb++) {
            if (c.kind[mb] == EncodeContext.INTER) {
                scratch[count++] = c.mvX[mb];
            }
        }
        if (count < c.mbCount() / 8) {
            c.globalMvX = 0;
            c.globalMvY = 0;
            return;
        }
        java.util.Arrays.sort(scratch, 0, count);
        c.globalMvX = scratch[count / 2];
        count = 0;
        for (int mb = 0; mb < c.mbCount(); mb++) {
            if (c.kind[mb] == EncodeContext.INTER) {
                scratch[count++] = c.mvY[mb];
            }
        }
        java.util.Arrays.sort(scratch, 0, count);
        c.globalMvY = scratch[count / 2];
    }

    private void finishReference() {
        EncodeContext c = this.context;
        Picture done = c.recon;
        c.recon = c.ref;
        c.ref = done;
        if (!c.intra) {
            this.updateGlobalMotion();
            System.arraycopy(c.mvX, 0, c.prevMvX, 0, c.mvX.length);
            System.arraycopy(c.mvY, 0, c.prevMvY, 0, c.mvY.length);
        }
        done.padRows(0, done.height);
        done.padTopBottom();
        this.halfPending = true;
    }

    private void computeHalfPlanes(Picture done) {
        this.halfPending = false;
        int bands = this.halfScratch.length;
        int top = Picture.HALF_TOP;
        int bottom = done.halfBottom();
        if (this.pool == null || bands == 1) {
            done.computeHalfPlanes(top, bottom, this.halfScratch[0]);
            done.padHalfPlanes();
            return;
        }
        List<Future<?>> futures = new ArrayList<>(bands);
        for (int i = 0; i < bands; i++) {
            int from = top + (bottom - top) * i / bands;
            int to = top + (bottom - top) * (i + 1) / bands;
            int[] scratch = this.halfScratch[i];
            futures.add(this.pool.submit(() -> done.computeHalfPlanes(from, to, scratch)));
        }
        for (Future<?> future : futures) {
            await(future);
        }
        done.padHalfPlanes();
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private void load(ByteBuffer i420) {
        int w = this.width;
        int h = this.height;
        if (i420.limit() < w * h * 3 / 2) {
            throw new IllegalArgumentException("frame too small: " + i420.limit());
        }
        EncodeContext c = this.context;
        copyPlane(i420, 0, w, h, c.srcY, c.width, c.height);
        int cw = w / 2;
        int ch = h / 2;
        copyPlane(i420, w * h, cw, ch, c.srcU, c.width / 2, c.height / 2);
        copyPlane(i420, w * h + cw * ch, cw, ch, c.srcV, c.width / 2, c.height / 2);
    }

    private static void copyPlane(ByteBuffer in, int offset, int w, int h, byte[] out, int ow, int oh) {
        for (int row = 0; row < oh; row++) {
            int srcRow = Math.min(row, h - 1);
            in.get(offset + srcRow * w, out, row * ow, w);
            if (ow > w) {
                byte edge = in.get(offset + srcRow * w + w - 1);
                java.util.Arrays.fill(out, row * ow + w, row * ow + ow, edge);
            }
        }
    }

    static int lambdaFor(int qp) {
        return Math.max(1, (int) Math.round(0.85 * Math.pow(2.0, (qp - 12) / 6.0)));
    }

    private byte[] buildSps(int codedWidth, int codedHeight) {
        int mbs = codedWidth / 16 * (codedHeight / 16);
        long mbps = (long) mbs * this.fps;
        int kbps = (int) Math.min(Integer.MAX_VALUE, this.settings.bitrate() / 1000L);
        int level = 52;
        for (int[] entry : LEVELS) {
            int maxDim = (int) Math.sqrt(entry[2] * 8.0);
            if (entry[1] >= mbps && entry[2] >= mbs && entry[3] >= kbps && codedWidth / 16 <= maxDim && codedHeight / 16 <= maxDim) {
                level = entry[0];
                break;
            }
        }
        BitWriter w = new BitWriter(64);
        w.bits(66, 8);
        w.bits(0b11000000, 8);
        w.bits(level, 8);
        w.ue(0);
        w.ue(LOG2_MAX_FRAME_NUM - 4);
        w.ue(2);
        w.ue(1);
        w.bits(0, 1);
        w.ue(codedWidth / 16 - 1);
        w.ue(codedHeight / 16 - 1);
        w.bits(1, 1);
        w.bits(1, 1);
        boolean crop = codedWidth != this.width || codedHeight != this.height;
        w.bit(crop);
        if (crop) {
            w.ue(0);
            w.ue((codedWidth - this.width) / 2);
            w.ue(0);
            w.ue((codedHeight - this.height) / 2);
        }
        w.bits(1, 1);
        w.bits(1, 1);
        w.bits(1, 8);
        w.bits(0, 1);
        w.bits(1, 1);
        w.bits(5, 3);
        w.bits(0, 1);
        w.bits(1, 1);
        w.bits(1, 8);
        w.bits(1, 8);
        w.bits(1, 8);
        w.bits(0, 1);
        w.bits(1, 1);
        w.bits(1000, 32);
        w.bits(this.fps * 2000, 32);
        w.bits(1, 1);
        w.bits(0, 1);
        w.bits(0, 1);
        w.bits(0, 1);
        w.bits(1, 1);
        w.bits(1, 1);
        w.ue(0);
        w.ue(0);
        w.ue(9);
        w.ue(9);
        w.ue(0);
        w.ue(1);
        w.trailing();
        return Nal.wrap(3, Nal.SPS, w);
    }

    private static byte[] buildPps() {
        BitWriter w = new BitWriter(16);
        w.ue(0);
        w.ue(0);
        w.bits(0, 1);
        w.bits(0, 1);
        w.ue(0);
        w.ue(0);
        w.ue(0);
        w.bits(0, 1);
        w.bits(0, 2);
        w.se(0);
        w.se(0);
        w.se(0);
        w.bits(1, 1);
        w.bits(0, 1);
        w.bits(0, 1);
        w.trailing();
        return Nal.wrap(3, Nal.PPS, w);
    }

    @Override
    public void close() {
        this.closed = true;
        if (this.pool != null) {
            this.pool.shutdownNow();
        }
    }
}

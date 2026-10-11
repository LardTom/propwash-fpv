package de.lardtom.propwash.video.record;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;

public final class PipelineStats {
    public static final int PRESETS = 4;

    private final int fps;
    private final long gcCountStart;
    private final long gcMillisStart;
    private volatile long renderFrames;
    private volatile long frameNanos;
    private volatile long frameNanosMax;
    private volatile long captures;
    private volatile long captureNanos;
    private volatile long captureNanosMax;
    private volatile long readbacks;
    private volatile long readbackNanos;
    private volatile long readbackNanosMax;
    private volatile long latencyFrames;
    private volatile long latencyFramesMax;
    private volatile long ringFullDrops;
    private volatile long memoryDrops;
    private volatile long gameGapSlots;
    private volatile long decimated;
    private volatile long slots;
    private volatile long encoded;
    private volatile long encodeNanos;
    private volatile long encodeNanosMax;
    private volatile long writeNanos;
    private volatile long repeats;
    private volatile int queueMax;
    private volatile long spoolWrites;
    private volatile long spoolWriteNanos;
    private volatile long spoolReads;
    private volatile long heapPeak;
    private volatile long poolBytes;
    private final AtomicLongArray presets = new AtomicLongArray(PRESETS);

    public PipelineStats(int fps) {
        this.fps = Math.max(1, fps);
        long[] gc = gc();
        this.gcCountStart = gc[0];
        this.gcMillisStart = gc[1];
    }

    public int fps() {
        return this.fps;
    }

    public void renderFrame(long intervalNanos) {
        this.renderFrames++;
        if (intervalNanos > 0L) {
            this.frameNanos += intervalNanos;
            if (intervalNanos > this.frameNanosMax) {
                this.frameNanosMax = intervalNanos;
            }
        }
    }

    public void captured(long nanos) {
        this.captures++;
        this.captureNanos += nanos;
        if (nanos > this.captureNanosMax) {
            this.captureNanosMax = nanos;
        }
    }

    public void readback(long nanos, long latency) {
        this.readbacks++;
        this.readbackNanos += nanos;
        if (nanos > this.readbackNanosMax) {
            this.readbackNanosMax = nanos;
        }
        this.latencyFrames += latency;
        if (latency > this.latencyFramesMax) {
            this.latencyFramesMax = latency;
        }
    }

    public void ringFull() {
        this.ringFullDrops++;
    }

    public void memoryFull() {
        this.memoryDrops++;
    }

    public void decimated() {
        this.decimated++;
    }

    public void slot(long gap) {
        this.slots += 1L + Math.max(0L, gap);
        this.gameGapSlots += Math.max(0L, gap);
    }

    public void encoded(long nanos, int preset) {
        this.encoded++;
        this.encodeNanos += nanos;
        if (nanos > this.encodeNanosMax) {
            this.encodeNanosMax = nanos;
        }
        this.presets.incrementAndGet(Math.max(0, Math.min(PRESETS - 1, preset)));
    }

    public void repeated() {
        this.repeats++;
    }

    public void written(long nanos) {
        this.writeNanos += nanos;
    }

    public void queue(int depth) {
        if (depth > this.queueMax) {
            this.queueMax = depth;
        }
    }

    public synchronized void spoolWrite(long nanos) {
        this.spoolWrites++;
        this.spoolWriteNanos += nanos;
    }

    public void spoolRead() {
        this.spoolReads++;
    }

    public void pool(long bytes) {
        this.poolBytes = bytes;
    }

    public void sampleHeap() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (used > this.heapPeak) {
            this.heapPeak = used;
        }
    }

    public Snapshot snapshot() {
        long[] gc = gc();
        long[] presetCounts = new long[PRESETS];
        for (int i = 0; i < PRESETS; i++) {
            presetCounts[i] = this.presets.get(i);
        }
        return new Snapshot(this.fps, this.renderFrames, this.frameNanos, this.frameNanosMax, this.captures, this.captureNanos,
                this.captureNanosMax, this.readbacks, this.readbackNanos, this.readbackNanosMax, this.latencyFrames, this.latencyFramesMax,
                this.ringFullDrops, this.memoryDrops, this.gameGapSlots, this.decimated, this.slots, this.encoded, this.encodeNanos, this.encodeNanosMax,
                this.writeNanos, this.repeats, this.queueMax, this.spoolWrites, this.spoolWriteNanos, this.spoolReads, this.heapPeak,
                this.poolBytes, gc[0] - this.gcCountStart, gc[1] - this.gcMillisStart, presetCounts);
    }

    private static long[] gc() {
        long count = 0L;
        long millis = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0L, bean.getCollectionCount());
            millis += Math.max(0L, bean.getCollectionTime());
        }
        return new long[]{count, millis};
    }

    public record Snapshot(int fps, long renderFrames, long frameNanos, long frameNanosMax, long captures, long captureNanos,
                           long captureNanosMax, long readbacks, long readbackNanos, long readbackNanosMax, long latencyFrames,
                           long latencyFramesMax, long ringFullDrops, long memoryDrops, long gameGapSlots, long decimated, long slots,
                           long encoded,
                           long encodeNanos, long encodeNanosMax, long writeNanos, long repeats, int queueMax, long spoolWrites,
                           long spoolWriteNanos, long spoolReads, long heapPeak, long poolBytes, long gcCount, long gcMillis,
                           long[] presets) {
        public double gameFps() {
            return this.frameNanos <= 0L ? 0.0 : this.renderFrames * 1.0E9 / this.frameNanos;
        }

        public double frameMs() {
            return average(this.frameNanos, this.renderFrames);
        }

        public double captureMs() {
            return average(this.captureNanos, this.captures);
        }

        public double readbackMs() {
            return average(this.readbackNanos, this.readbacks);
        }

        public double renderThreadMs() {
            return this.captureMs() + this.readbackMs();
        }

        public double latency() {
            return this.readbacks == 0L ? 0.0 : this.latencyFrames / (double) this.readbacks;
        }

        public double encodeMs() {
            return average(this.encodeNanos, this.encoded);
        }

        public double encoderFps() {
            return this.encodeNanos <= 0L ? 0.0 : this.encoded * 1.0E9 / this.encodeNanos;
        }

        public double writeMs() {
            return average(this.writeNanos, this.encoded);
        }

        public double spoolWriteMs() {
            return average(this.spoolWriteNanos, this.spoolWrites);
        }

        public long lostFrames() {
            return this.ringFullDrops + this.memoryDrops + this.decimated;
        }

        public double gapShare() {
            return this.slots <= 0L ? 0.0 : this.gameGapSlots / (double) this.slots;
        }

        public Snapshot since(Snapshot earlier) {
            long[] p = new long[PRESETS];
            for (int i = 0; i < PRESETS; i++) {
                p[i] = this.presets[i] - earlier.presets[i];
            }
            return new Snapshot(this.fps, this.renderFrames - earlier.renderFrames, this.frameNanos - earlier.frameNanos, this.frameNanosMax,
                    this.captures - earlier.captures, this.captureNanos - earlier.captureNanos, this.captureNanosMax,
                    this.readbacks - earlier.readbacks, this.readbackNanos - earlier.readbackNanos, this.readbackNanosMax,
                    this.latencyFrames - earlier.latencyFrames, this.latencyFramesMax, this.ringFullDrops - earlier.ringFullDrops,
                    this.memoryDrops - earlier.memoryDrops, this.gameGapSlots - earlier.gameGapSlots, this.decimated - earlier.decimated,
                    this.slots - earlier.slots,
                    this.encoded - earlier.encoded, this.encodeNanos - earlier.encodeNanos, this.encodeNanosMax,
                    this.writeNanos - earlier.writeNanos, this.repeats - earlier.repeats, this.queueMax,
                    this.spoolWrites - earlier.spoolWrites, this.spoolWriteNanos - earlier.spoolWriteNanos, this.spoolReads - earlier.spoolReads,
                    this.heapPeak, this.poolBytes, this.gcCount - earlier.gcCount, this.gcMillis - earlier.gcMillis, p);
        }

        public String summary() {
            return String.format(Locale.ROOT,
                    "Spiel %.1f FPS (Bildzeit %.2f ms, max %.1f ms), Renderthread %.3f ms/Bild (Auftrag %.3f, max %.2f; Auslesen %.3f, max %.2f), "
                            + "GPU-Latenz %.2f Bilder (max %d), Bildplätze %d (vom Spiel ausgelassen %d = %.1f %%), verloren %d (Auslese-Ring %d, "
                            + "Speicher %d, ausgedünnt %d), Encoder %.1f B/s (%.2f ms/Bild, max %.1f ms, Presets %d/%d/%d/%d), Wiederholbilder %d, "
                            + "Schreiben %.3f ms/Bild, Warteschlange max %d, ausgelagert %d (%.2f ms/Bild), zurückgelesen %d, Puffer %d MB, "
                            + "Heap-Spitze %d MB, GC %d× %d ms",
                    this.gameFps(), this.frameMs(), this.frameNanosMax / 1.0E6, this.renderThreadMs(), this.captureMs(),
                    this.captureNanosMax / 1.0E6, this.readbackMs(), this.readbackNanosMax / 1.0E6, this.latency(), this.latencyFramesMax,
                    this.slots, this.gameGapSlots, this.gapShare() * 100.0, this.lostFrames(), this.ringFullDrops, this.memoryDrops,
                    this.decimated, this.encoderFps(), this.encodeMs(), this.encodeNanosMax / 1.0E6, this.presets[0], this.presets[1], this.presets[2],
                    this.presets[3], this.repeats, this.writeMs(), this.queueMax, this.spoolWrites, this.spoolWriteMs(), this.spoolReads,
                    this.poolBytes / (1024L * 1024L), this.heapPeak / (1024L * 1024L), this.gcCount, this.gcMillis);
        }

        private static double average(long nanos, long count) {
            return count <= 0L ? 0.0 : nanos / (double) count / 1.0E6;
        }
    }
}

package de.lardtom.propwash.video.record;

import de.lardtom.propwash.video.record.aac.AacEncoder;
import de.lardtom.propwash.video.record.mp4.Mp4Writer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

public final class RecordingJob {
    public static final String COMPRESSOR = BuiltinEncoder.LABEL;
    public static final int FRAME_TICKS = 1000;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final long TAKE_TIMEOUT_MILLIS = 100L;

    private final Options options;
    private final RecordingFormat format;
    private final FrameQueue queue;
    private final Path partFile;
    private final Thread worker;
    private final AudioLane audio;
    private final PipelineStats stats;
    private volatile State state = State.RECORDING;
    private volatile long endSlot = -1L;
    private volatile boolean cancelled;
    private volatile long encodedSlots;
    private volatile long encodedFrames;
    private volatile long repeatedFrames;
    private volatile long submitted;
    private volatile long lastSubmittedSlot = -1L;
    private volatile long bytesWritten;
    private volatile int preset;
    private volatile String error = "";
    private volatile String encoderLabel = BuiltinEncoder.LABEL;
    private volatile boolean hardware;
    private volatile boolean encoderFailed;
    private volatile Path result;
    private volatile long finishedNanos;
    private volatile long encodeNanos;
    private volatile long firstSlot = -1L;
    private volatile long audioSamples;
    private byte[] pendingSample;
    private boolean pendingSync;
    private long samplesWritten;

    private RecordingJob(Options options) {
        this.options = options;
        this.format = options.format();
        String name = options.file().getFileName().toString();
        this.partFile = options.file().resolveSibling(name + ".part");
        Path spool = options.spoolDirectory().resolve(name + ".frames");
        this.stats = new PipelineStats(this.format.fps());
        this.queue = new FrameQueue(this.format.frameBytes(), options.memoryCapBytes(), options.spoolCapBytes(), spool, this.stats);
        this.audio = options.audio() ? new AudioLane(AacEncoder.DEFAULT_BITRATE) : null;
        this.worker = new Thread(this::run, "Propwash video recording " + IDS.incrementAndGet());
        this.worker.setDaemon(true);
        this.worker.setPriority(Thread.NORM_PRIORITY - 1);
    }

    public static RecordingJob start(Options options) throws IOException {
        Files.createDirectories(options.file().getParent());
        RecordingJob job = new RecordingJob(options);
        job.worker.start();
        return job;
    }

    public RecordingFormat format() {
        return this.format;
    }

    public Path file() {
        return this.options.file();
    }

    public RecordingQuality quality() {
        return this.options.quality();
    }

    public PipelineStats stats() {
        return this.stats;
    }

    public String encoderLabel() {
        return this.encoderLabel;
    }

    public boolean hardware() {
        return this.hardware;
    }

    public boolean externalEncoder() {
        return this.options.encoder() != BuiltinEncoder.FACTORY;
    }

    public boolean encoderFailed() {
        return this.encoderFailed;
    }

    public boolean hasAudio() {
        return this.audio != null;
    }

    public long audioSamples() {
        return this.audioSamples;
    }

    public void submitAudio(short[] pcm, int offset, int count) {
        if (this.audio != null && this.state == State.RECORDING) {
            this.audio.submit(pcm, offset, count);
        }
    }

    public void submitSilence(int count) {
        if (this.audio != null && this.state == State.RECORDING) {
            this.audio.submitSilence(count);
        }
    }

    public boolean recording() {
        return this.state == State.RECORDING;
    }

    public ByteBuffer acquire() {
        if (this.state != State.RECORDING) {
            return null;
        }
        return this.queue.acquire();
    }

    public boolean memoryFull() {
        return this.queue.memoryFull();
    }

    public void release(ByteBuffer buffer) {
        this.queue.release(buffer);
    }

    public void submit(long slot, ByteBuffer buffer) {
        if (this.state != State.RECORDING || slot <= this.lastSubmittedSlot) {
            this.queue.release(buffer);
            return;
        }
        this.lastSubmittedSlot = slot;
        this.submitted++;
        this.queue.submit(slot, buffer);
    }

    public void countDrop() {
        this.queue.countDrop();
    }

    public int captureStride() {
        return Backpressure.stride(this.queue.fill(), this.queue.spoolFailed() || this.options.spoolCapBytes() <= 0L);
    }

    public Backlog backlog() {
        return new Backlog(this.queue.size(), this.queue.spilled(), this.queue.spoolFailed(), this.queue.spoolFull(), this.queue.fill());
    }

    public void finish(long endSlot) {
        if (this.state == State.RECORDING) {
            this.endSlot = Math.max(endSlot, this.lastSubmittedSlot + 1L);
            this.state = State.FINISHING;
            synchronized (this.queue) {
                this.queue.notifyAll();
            }
        }
    }

    public void cancel() {
        this.cancelled = true;
        if (this.state == State.RECORDING || this.state == State.FINISHING) {
            this.state = State.CANCELLED;
        }
        synchronized (this.queue) {
            this.queue.notifyAll();
        }
    }

    public boolean running() {
        return this.state == State.RECORDING || this.state == State.FINISHING;
    }

    public void awaitDone(long millis) throws InterruptedException {
        this.worker.join(millis);
    }

    public Progress progress() {
        long first = Math.max(0L, this.firstSlot);
        long total;
        if (this.endSlot >= 0L) {
            total = Math.max(0L, this.endSlot - first);
        } else {
            total = Math.max(0L, this.lastSubmittedSlot + 1L - first);
        }
        return new Progress(this.state, this.encodedSlots, total, this.submitted, this.queue.size(), this.queue.spilled(),
                this.queue.spilledTotal(), this.queue.dropped(), this.repeatedFrames, this.bytesWritten, this.preset, this.result,
                this.error, this.encodeNanos);
    }

    private void run() {
        Mp4Writer writer = null;
        FrameEncoder encoder = null;
        try {
            encoder = this.options.encoder().create(this.format, this.options.quality(), this.options.threads());
            this.encoderLabel = encoder.label();
            this.hardware = encoder.hardware();
            writer = new Mp4Writer(this.partFile, this.format.width(), this.format.height(), this.format.fps() * FRAME_TICKS,
                    encoder.sps(), encoder.pps(), encoder.label());
            if (this.audio != null) {
                writer.addAudioTrack(AudioLane.RATE, 1, AacEncoder.audioSpecificConfig(), this.audio.bitrate());
            }
            Mp4Writer out = writer;
            FrameEncoder.SampleSink sink = (sample, sync) -> this.sample(out, sample, sync);
            long nextSlot = -1L;
            boolean broken = false;
            while (!this.cancelled && !broken) {
                this.pumpAudio(writer);
                FrameQueue.Taken taken = this.queue.take(TAKE_TIMEOUT_MILLIS);
                if (taken == null) {
                    if (this.state == State.FINISHING && this.queue.size() == 0) {
                        break;
                    }
                    continue;
                }
                try {
                    if (nextSlot < 0L) {
                        this.firstSlot = taken.slot();
                        nextSlot = taken.slot();
                    }
                    while (nextSlot < taken.slot() && !this.cancelled) {
                        encoder.repeat(sink);
                        this.repeated();
                        nextSlot++;
                    }
                    int backlog = this.queue.size();
                    this.stats.queue(backlog + 1);
                    long start = System.nanoTime();
                    this.preset = this.presetFor(backlog);
                    encoder.encode(taken.data(), this.preset, sink);
                    long spent = System.nanoTime() - start;
                    this.encodeNanos += spent;
                    this.stats.encoded(spent, this.preset);
                    this.encodedFrames++;
                    nextSlot++;
                    this.encodedSlots = nextSlot - this.firstSlot;
                } catch (IOException e) {
                    if (!encoder.hardware() || this.samplesWritten + (this.pendingSample == null ? 0 : 1) == 0L) {
                        throw e;
                    }
                    this.encoderFailed = true;
                    this.error = "hardware encoder: " + e.getMessage();
                    broken = true;
                } finally {
                    if (taken.pooled()) {
                        this.queue.release(taken.data());
                    }
                }
            }
            if (this.cancelled) {
                throw new InterruptedException("cancelled");
            }
            if (this.encodedFrames == 0L) {
                throw new IOException("no frames captured");
            }
            if (!broken) {
                try {
                    while (nextSlot < this.endSlot && !this.cancelled) {
                        encoder.repeat(sink);
                        this.repeated();
                        nextSlot++;
                        this.encodedSlots = nextSlot - this.firstSlot;
                    }
                    encoder.finish(sink);
                } catch (IOException e) {
                    if (!encoder.hardware() || this.samplesWritten + (this.pendingSample == null ? 0 : 1) == 0L) {
                        throw e;
                    }
                    this.encoderFailed = true;
                    this.error = "hardware encoder: " + e.getMessage();
                    broken = true;
                }
            }
            if (this.pendingSample == null && this.samplesWritten == 0L) {
                throw new IOException("encoder produced no frames" + (this.error.isEmpty() ? "" : " (" + this.error + ")"));
            }
            long slots = Math.max(1L, (broken ? this.samplesWritten + 1L : nextSlot - this.firstSlot));
            this.flushPending(writer, (int) Math.max(1L, slots - this.samplesWritten) * FRAME_TICKS);
            this.encodedSlots = slots;
            if (!writer.hasParameterSets()) {
                writer.parameterSets(encoder.sps(), encoder.pps());
            }
            if (this.audio != null) {
                this.audio.start(this.firstSlot, this.format.fps());
                this.audio.finish(writer, slots * AudioLane.RATE / this.format.fps());
                this.audioSamples = this.audio.accepted();
            }
            writer.finish();
            Path target = uniqueTarget(this.options.file());
            Files.move(this.partFile, target, StandardCopyOption.ATOMIC_MOVE);
            this.result = target;
            this.state = State.DONE;
        } catch (InterruptedException e) {
            this.state = State.CANCELLED;
            this.discard(writer);
        } catch (Exception | OutOfMemoryError e) {
            this.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.state = this.cancelled ? State.CANCELLED : State.FAILED;
            this.discard(writer);
        } finally {
            if (encoder != null) {
                encoder.close();
            }
            this.queue.close();
            this.finishedNanos = System.nanoTime();
        }
    }

    private void sample(Mp4Writer writer, byte[] data, boolean sync) throws IOException {
        this.flushPending(writer, FRAME_TICKS);
        this.pendingSample = data;
        this.pendingSync = sync;
    }

    private void flushPending(Mp4Writer writer, int duration) throws IOException {
        if (this.pendingSample == null) {
            return;
        }
        long started = System.nanoTime();
        writer.writeSample(this.pendingSample, duration, this.pendingSync);
        this.stats.written(System.nanoTime() - started);
        this.pendingSample = null;
        this.samplesWritten++;
        this.bytesWritten = writer.bytesWritten();
    }

    private void repeated() {
        this.repeatedFrames++;
        this.stats.repeated();
    }

    private void discard(Mp4Writer writer) {
        if (writer != null) {
            writer.abort();
        }
        try {
            Files.deleteIfExists(this.partFile);
        } catch (IOException ignored) {
        }
    }

    private void pumpAudio(Mp4Writer writer) throws IOException {
        if (this.audio == null) {
            return;
        }
        if (this.firstSlot >= 0L) {
            this.audio.start(this.firstSlot, this.format.fps());
        }
        this.audio.pump(writer);
        this.bytesWritten = writer.bytesWritten();
    }

    private int presetFor(int backlog) {
        int fps = this.format.fps();
        if (backlog < fps / 2) {
            return 0;
        }
        if (backlog < fps * 2) {
            return 1;
        }
        return 2;
    }

    static Path uniqueTarget(Path wanted) {
        if (!Files.exists(wanted)) {
            return wanted;
        }
        String name = wanted.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            Path candidate = wanted.resolveSibling(base + "_" + i + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return wanted.resolveSibling(base + "_" + System.nanoTime() + extension);
    }

    public enum State {
        RECORDING,
        FINISHING,
        DONE,
        FAILED,
        CANCELLED
    }

    public record Backlog(int queued, int spilled, boolean spoolFailed, boolean spoolFull, double fill) {
    }

    public record Options(Path file, Path spoolDirectory, RecordingFormat format, int threads, long memoryCapBytes, long spoolCapBytes,
                          boolean audio, RecordingQuality quality, FrameEncoder.Factory encoder) {
        public Options {
            if (quality == null) {
                quality = RecordingQuality.DEFAULT;
            }
            if (encoder == null) {
                encoder = BuiltinEncoder.FACTORY;
            }
        }

        public Options(Path file, Path spoolDirectory, RecordingFormat format, int threads, long memoryCapBytes, long spoolCapBytes,
                       boolean audio, RecordingQuality quality) {
            this(file, spoolDirectory, format, threads, memoryCapBytes, spoolCapBytes, audio, quality, BuiltinEncoder.FACTORY);
        }

        public Options(Path file, Path spoolDirectory, RecordingFormat format, int threads, long memoryCapBytes, long spoolCapBytes,
                       boolean audio) {
            this(file, spoolDirectory, format, threads, memoryCapBytes, spoolCapBytes, audio, RecordingQuality.DEFAULT, BuiltinEncoder.FACTORY);
        }

        public Options(Path file, Path spoolDirectory, RecordingFormat format, int threads, long memoryCapBytes, long spoolCapBytes) {
            this(file, spoolDirectory, format, threads, memoryCapBytes, spoolCapBytes, false, RecordingQuality.DEFAULT, BuiltinEncoder.FACTORY);
        }
    }

    public record Progress(State state, long encodedSlots, long totalSlots, long submittedFrames, int queuedFrames, int spilledFrames,
                           long spilledTotal, long droppedFrames, long repeatedFrames, long bytesWritten, int preset, Path result,
                           String error, long encodeNanos) {
        public float fraction() {
            if (this.state == State.DONE) {
                return 1.0F;
            }
            return this.totalSlots <= 0L ? 0.0F : Math.min(1.0F, this.encodedSlots / (float) this.totalSlots);
        }

        public boolean finished() {
            return this.state == State.DONE || this.state == State.FAILED || this.state == State.CANCELLED;
        }
    }
}

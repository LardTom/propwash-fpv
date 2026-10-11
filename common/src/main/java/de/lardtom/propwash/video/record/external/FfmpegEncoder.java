package de.lardtom.propwash.video.record.external;

import de.lardtom.propwash.video.record.FrameEncoder;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingQuality;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class FfmpegEncoder implements FrameEncoder {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final int CHUNK = 1 << 20;
    private static final int ERROR_TAIL = 2048;
    private static final long FINISH_TIMEOUT_MILLIS = 30_000L;
    public static final long STALL_MILLIS = 10_000L;
    private static final long WATCH_MILLIS = 250L;

    private final FfmpegCodec codec;
    private final RecordingFormat format;
    private final Process process;
    private final OutputStream stdin;
    private final Thread reader;
    private final Thread errors;
    private final Thread watchdog;
    private final long stallNanos;
    private volatile long busySince;
    private volatile boolean stalled;
    private final LinkedBlockingQueue<AnnexB.AccessUnit> ready = new LinkedBlockingQueue<>();
    private final StringBuilder errorTail = new StringBuilder();
    private final byte[] chunk = new byte[CHUNK];
    private final byte[] lastFrame;
    private volatile byte[] sps = new byte[0];
    private volatile byte[] pps = new byte[0];
    private volatile IOException readFailure;
    private boolean hasFrame;
    private long sent;
    private long received;
    private volatile boolean closed;

    private FfmpegEncoder(FfmpegCodec codec, RecordingFormat format, List<String> command, long stallMillis) throws IOException {
        this.codec = codec;
        this.stallNanos = Math.max(1L, stallMillis) * 1_000_000L;
        this.format = format;
        this.lastFrame = new byte[format.frameBytes()];
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        this.process = builder.start();
        this.stdin = this.process.getOutputStream();
        int id = IDS.incrementAndGet();
        this.reader = new Thread(this::readLoop, "Propwash ffmpeg reader " + id);
        this.reader.setDaemon(true);
        this.errors = new Thread(this::errorLoop, "Propwash ffmpeg errors " + id);
        this.errors.setDaemon(true);
        this.watchdog = new Thread(this::watchLoop, "Propwash ffmpeg watchdog " + id);
        this.watchdog.setDaemon(true);
        this.reader.start();
        this.errors.start();
        this.watchdog.start();
    }

    public static FfmpegEncoder start(Path ffmpeg, FfmpegCodec codec, int variant, RecordingFormat format, RecordingQuality quality)
            throws IOException {
        return start(ffmpeg, codec, variant, format, quality, STALL_MILLIS);
    }

    public static FfmpegEncoder start(Path ffmpeg, FfmpegCodec codec, int variant, RecordingFormat format, RecordingQuality quality,
                                      long stallMillis) throws IOException {
        return new FfmpegEncoder(codec, format, codec.command(ffmpeg.toString(), format, quality, variant), stallMillis);
    }

    public static Factory factory(Path ffmpeg, FfmpegCodec codec, int variant) {
        return (format, quality, threads) -> start(ffmpeg, codec, variant, format, quality);
    }

    @Override
    public String label() {
        return "Propwash H.264 (" + this.codec.label() + ")";
    }

    @Override
    public boolean hardware() {
        return true;
    }

    public FfmpegCodec codec() {
        return this.codec;
    }

    @Override
    public byte[] sps() {
        return this.sps.clone();
    }

    @Override
    public byte[] pps() {
        return this.pps.clone();
    }

    public long sent() {
        return this.sent;
    }

    public long received() {
        return this.received;
    }

    @Override
    public void encode(ByteBuffer frame, int preset, SampleSink sink) throws IOException {
        int bytes = this.format.frameBytes();
        if (frame.limit() < bytes) {
            throw new IOException("frame too small: " + frame.limit());
        }
        frame.get(0, this.lastFrame, 0, bytes);
        this.hasFrame = true;
        this.send(sink);
    }

    @Override
    public void repeat(SampleSink sink) throws IOException {
        if (!this.hasFrame) {
            return;
        }
        this.send(sink);
    }

    private void send(SampleSink sink) throws IOException {
        this.check();
        int bytes = this.format.frameBytes();
        this.busySince = System.nanoTime();
        try {
            for (int at = 0; at < bytes; at += CHUNK) {
                this.stdin.write(this.lastFrame, at, Math.min(CHUNK, bytes - at));
            }
        } catch (IOException e) {
            throw this.failure(this.stalled ? "stalled" : "write", e);
        } finally {
            this.busySince = 0L;
        }
        this.sent++;
        this.drain(sink);
    }

    @Override
    public void finish(SampleSink sink) throws IOException {
        try {
            this.stdin.close();
        } catch (IOException e) {
            throw this.failure("close", e);
        }
        try {
            this.reader.join(FINISH_TIMEOUT_MILLIS);
            if (!this.process.waitFor(FINISH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                this.kill();
                throw new IOException("ffmpeg did not finish");
            }
            this.errors.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        this.drain(sink);
        this.check();
        int exit = this.process.exitValue();
        if (exit != 0) {
            throw new IOException("ffmpeg exit " + exit + this.errorSuffix());
        }
    }

    private void drain(SampleSink sink) throws IOException {
        AnnexB.AccessUnit unit;
        while ((unit = this.ready.poll()) != null) {
            this.received++;
            sink.accept(unit.toSample(), unit.keyframe());
        }
    }

    private void check() throws IOException {
        IOException failure = this.readFailure;
        if (failure != null) {
            throw failure;
        }
        if (!this.process.isAlive() && this.process.exitValue() != 0) {
            throw new IOException("ffmpeg exit " + this.process.exitValue() + this.errorSuffix());
        }
    }

    private IOException failure(String what, IOException cause) {
        return new IOException("ffmpeg " + what + " failed: " + cause.getMessage() + this.errorSuffix(), cause);
    }

    private String errorSuffix() {
        synchronized (this.errorTail) {
            String text = this.errorTail.toString().strip();
            return text.isEmpty() ? "" : " (" + text.replace('\n', ' ') + ")";
        }
    }

    private void readLoop() {
        AnnexB parser = new AnnexB(this::accept);
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = this.process.getInputStream()) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                parser.feed(buffer, 0, read);
            }
            parser.finish();
        } catch (IOException e) {
            this.readFailure = new IOException("ffmpeg read failed: " + e.getMessage(), e);
        }
    }

    private void accept(AnnexB.AccessUnit unit) {
        if (this.sps.length == 0) {
            byte[] foundSps = unit.find(AnnexB.SPS);
            byte[] foundPps = unit.find(AnnexB.PPS);
            if (foundSps != null && foundPps != null && foundSps.length >= 4) {
                this.pps = foundPps;
                this.sps = foundSps;
            }
        }
        this.ready.add(unit);
    }

    private void kill() {
        this.process.descendants().forEach(ProcessHandle::destroyForcibly);
        this.process.destroyForcibly();
    }

    public boolean stalled() {
        return this.stalled;
    }

    private void watchLoop() {
        try {
            while (!this.closed && this.process.isAlive()) {
                Thread.sleep(WATCH_MILLIS);
                long since = this.busySince;
                if (since != 0L && System.nanoTime() - since > this.stallNanos) {
                    this.stalled = true;
                    this.kill();
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void errorLoop() {
        byte[] buffer = new byte[1024];
        try (InputStream in = this.process.getErrorStream()) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                synchronized (this.errorTail) {
                    this.errorTail.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    if (this.errorTail.length() > ERROR_TAIL) {
                        this.errorTail.delete(0, this.errorTail.length() - ERROR_TAIL);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.watchdog.interrupt();
        try {
            this.stdin.close();
        } catch (IOException ignored) {
        }
        if (this.process.isAlive()) {
            try {
                if (!this.process.waitFor(2000L, TimeUnit.MILLISECONDS)) {
                    this.kill();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.kill();
            }
        }
    }
}

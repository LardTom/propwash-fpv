package de.lardtom.propwash.video.record;

import de.lardtom.propwash.video.record.external.FfmpegCodec;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import de.lardtom.propwash.video.record.mp4.Mp4Info;
import de.lardtom.propwash.video.record.mp4.Mp4Reader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameEncoderJobTest {
    @TempDir
    Path dir;

    private static void fill(ByteBuffer frame, RecordingFormat format, int index) {
        int w = format.width();
        int h = format.height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                frame.put(y * w + x, (byte) (16 + ((x * 3 + y + index * 7) % 200)));
            }
        }
        for (int i = w * h; i < format.frameBytes(); i++) {
            frame.put(i, (byte) (100 + (i + index) % 50));
        }
    }

    private static RecordingJob record(RecordingJob.Options options, int slots, int skipEvery) throws Exception {
        RecordingJob job = RecordingJob.start(options);
        for (int slot = 0; slot < slots; slot++) {
            if (skipEvery > 0 && slot % skipEvery == skipEvery - 1) {
                continue;
            }
            ByteBuffer buffer = null;
            for (int attempt = 0; attempt < 800 && buffer == null; attempt++) {
                buffer = job.acquire();
                if (buffer == null) {
                    if (!job.recording()) {
                        break;
                    }
                    Thread.sleep(5L);
                }
            }
            if (buffer == null) {
                break;
            }
            fill(buffer, options.format(), slot);
            job.submit(slot, buffer);
        }
        job.finish(slots);
        job.awaitDone(120_000L);
        return job;
    }

    private RecordingJob.Options options(String name, RecordingFormat format, FrameEncoder.Factory factory) {
        return new RecordingJob.Options(this.dir.resolve(name), this.dir.resolve(".spool"), format, 2, format.frameBytes() * 12L, 1L << 28,
                false, RecordingQuality.STANDARD, factory);
    }

    @Test
    void encoderThatSwallowsFramesStillGivesTheRecordedLength() throws Exception {
        RecordingFormat format = new RecordingFormat(160, 96, 30);
        FrameEncoder.Factory lossy = (f, quality, threads) -> new Faulty(new BuiltinEncoder(f, quality, threads), 7, Integer.MAX_VALUE);
        RecordingJob job = record(this.options("lossy.mp4", format, lossy), 90, 0);
        RecordingJob.Progress progress = job.progress();
        assertEquals(RecordingJob.State.DONE, progress.state(), progress.error());
        Mp4Info info = Mp4Reader.read(progress.result());
        assertTrue(info.sampleCount() < 90, "samples " + info.sampleCount());
        assertEquals(3.0, info.durationSeconds(), 1.0E-9);
        assertTrue(info.startsWithSync());
    }

    @Test
    void hardwareFailureKeepsTheVideoSoFar() throws Exception {
        RecordingFormat format = new RecordingFormat(160, 96, 30);
        FrameEncoder.Factory breaking = (f, quality, threads) -> new Faulty(new BuiltinEncoder(f, quality, threads), 0, 40);
        RecordingJob job = record(this.options("broken.mp4", format, breaking), 90, 0);
        RecordingJob.Progress progress = job.progress();
        assertEquals(RecordingJob.State.DONE, progress.state(), progress.error());
        assertTrue(job.encoderFailed());
        assertTrue(progress.error().contains("hardware encoder"), progress.error());
        Mp4Info info = Mp4Reader.read(progress.result());
        assertEquals(40, info.sampleCount());
        assertEquals(40.0 / 30.0, info.durationSeconds(), 1.0E-9);
    }

    @Test
    void hardwareFailureBeforeTheFirstFrameFailsCleanly() throws Exception {
        RecordingFormat format = new RecordingFormat(160, 96, 30);
        FrameEncoder.Factory dead = (f, quality, threads) -> new Faulty(new BuiltinEncoder(f, quality, threads), 0, 0);
        RecordingJob job = record(this.options("dead.mp4", format, dead), 30, 0);
        assertEquals(RecordingJob.State.FAILED, job.progress().state());
        assertFalse(Files.exists(this.dir.resolve("dead.mp4")));
        assertFalse(Files.exists(this.dir.resolve("dead.mp4.part")));
    }

    @Test
    void hardwareEncoderThroughFfmpegWritesAPlayableMp4() throws Exception {
        RecordingFormat format = new RecordingFormat(320, 180, 30);
        HardwareEncoders.Status status = HardwareEncoders.probe(new HardwareEncoders.Settings(true, "", List.of()), format);
        Assumptions.assumeTrue(status.state() == HardwareEncoders.State.READY, "kein Hardware-Encoder: " + status);
        assertNotNull(status.codec());
        RecordingJob job = record(this.options("hardware.mp4", format,
                de.lardtom.propwash.video.record.external.FfmpegEncoder.factory(status.ffmpeg(), status.codec(), status.variant())), 75, 9);
        RecordingJob.Progress progress = job.progress();
        assertEquals(RecordingJob.State.DONE, progress.state(), progress.error());
        assertTrue(job.hardware());
        assertTrue(job.encoderLabel().contains(status.codec().label()));
        Mp4Info info = Mp4Reader.read(progress.result());
        assertEquals(75, info.sampleCount());
        assertEquals(2.5, info.durationSeconds(), 1.0E-9);
        assertTrue(info.startsWithSync());
        assertTrue(info.profile() == 77 || info.profile() == 100 || info.profile() == 66, "profile " + info.profile());
        Path ffprobe = status.ffmpeg().resolveSibling(FfmpegCodec.Platform.current() == FfmpegCodec.Platform.WINDOWS ? "ffprobe.exe" : "ffprobe");
        if (Files.isExecutable(ffprobe)) {
            Process process = new ProcessBuilder(ffprobe.toString(), "-v", "error", "-count_frames", "-select_streams", "v:0",
                    "-show_entries", "stream=nb_read_frames,width,height,pix_fmt", "-of", "csv=p=0", progress.result().toString())
                    .redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            assertEquals(0, process.waitFor());
            assertEquals("320,180,yuv420p,75", out);
        }
    }

    private static final class Faulty implements FrameEncoder {
        private final BuiltinEncoder inner;
        private final int swallowEvery;
        private final int failAfter;
        private int frames;

        Faulty(BuiltinEncoder inner, int swallowEvery, int failAfter) {
            this.inner = inner;
            this.swallowEvery = swallowEvery;
            this.failAfter = failAfter;
        }

        @Override
        public String label() {
            return "Faulty";
        }

        @Override
        public boolean hardware() {
            return true;
        }

        @Override
        public byte[] sps() {
            return this.inner.sps();
        }

        @Override
        public byte[] pps() {
            return this.inner.pps();
        }

        @Override
        public void encode(ByteBuffer frame, int preset, SampleSink sink) throws IOException {
            this.step();
            if (this.swallowEvery > 0 && this.frames % this.swallowEvery == 0 && this.frames > 1) {
                this.inner.encode(frame, preset, (sample, sync) -> {
                });
                return;
            }
            this.inner.encode(frame, preset, sink);
        }

        @Override
        public void repeat(SampleSink sink) throws IOException {
            this.step();
            this.inner.repeat(sink);
        }

        private void step() throws IOException {
            if (this.frames >= this.failAfter) {
                throw new IOException("simulated failure");
            }
            this.frames++;
        }

        @Override
        public void finish(SampleSink sink) {
        }

        @Override
        public void close() {
            this.inner.close();
        }
    }
}

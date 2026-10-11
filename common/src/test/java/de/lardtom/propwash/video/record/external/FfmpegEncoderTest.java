package de.lardtom.propwash.video.record.external;

import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingQuality;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegEncoderTest {
    private static Path script(Path dir, String body) throws IOException {
        Path file = Files.writeString(dir.resolve("ffmpeg"), "#!/bin/sh\n" + body + "\n");
        assertTrue(file.toFile().setExecutable(true));
        return file;
    }

    @Test
    void hangingFfmpegIsStoppedByTheWatchdog(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(FfmpegCodec.Platform.current() != FfmpegCodec.Platform.WINDOWS);
        Path fake = script(dir, "sleep 30");
        RecordingFormat format = new RecordingFormat(320, 180, 30);
        ByteBuffer frame = ByteBuffer.allocate(format.frameBytes());
        long started = System.nanoTime();
        try (FfmpegEncoder encoder = FfmpegEncoder.start(fake, FfmpegCodec.VIDEOTOOLBOX, 0, format, RecordingQuality.STANDARD, 400L)) {
            IOException failure = assertThrows(IOException.class, () -> {
                for (int i = 0; i < 20; i++) {
                    encoder.encode(frame, 0, (sample, sync) -> {
                    });
                }
            });
            assertTrue(encoder.stalled(), failure.getMessage());
        }
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "watchdog too slow");
    }

    @Test
    void ffmpegThatExitsWithAnErrorReportsIt(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(FfmpegCodec.Platform.current() != FfmpegCodec.Platform.WINDOWS);
        Path fake = script(dir, "echo 'Unknown encoder' >&2\nexit 1");
        RecordingFormat format = new RecordingFormat(64, 64, 30);
        ByteBuffer frame = ByteBuffer.allocate(format.frameBytes());
        try (FfmpegEncoder encoder = FfmpegEncoder.start(fake, FfmpegCodec.NVENC, 0, format, RecordingQuality.STANDARD)) {
            IOException failure = assertThrows(IOException.class, () -> {
                for (int i = 0; i < 200; i++) {
                    encoder.encode(frame, 0, (sample, sync) -> {
                    });
                }
                encoder.finish((sample, sync) -> {
                });
            });
            assertTrue(failure.getMessage().contains("ffmpeg"), failure.getMessage());
        }
    }

    @Test
    void probeWithoutFfmpegSaysSo(@TempDir Path dir) {
        HardwareEncoders.Status status = HardwareEncoders.probe(new HardwareEncoders.Settings(true, dir.resolve("missing").toString(),
                List.of(dir)), new RecordingFormat(64, 64, 30));
        assertTrue(status.state() == HardwareEncoders.State.NO_FFMPEG || status.ffmpeg() != null && !status.ffmpeg().startsWith(dir),
                status.toString());
    }

    @Test
    void fakeFfmpegWithoutEncodersIsNoHardware(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(FfmpegCodec.Platform.current() != FfmpegCodec.Platform.WINDOWS);
        Path fake = script(dir, "echo 'Encoders:'\necho ' V....D libx264              libx264 H.264'\nexit 0");
        HardwareEncoders.Status status = HardwareEncoders.probe(new HardwareEncoders.Settings(true, fake.toString(), List.of()),
                new RecordingFormat(64, 64, 30));
        assertEquals(HardwareEncoders.State.NO_HARDWARE, status.state(), status.toString());
        assertEquals(fake, status.ffmpeg());
    }

    @Test
    void ffmpegThatNeverAnswersDoesNotHangTheProbe(@TempDir Path dir) throws IOException {
        Assumptions.assumeTrue(FfmpegCodec.Platform.current() != FfmpegCodec.Platform.WINDOWS);
        Path fake = script(dir, "sleep 60");
        long started = System.nanoTime();
        HardwareEncoders.Status status = HardwareEncoders.probe(new HardwareEncoders.Settings(true, fake.toString(), List.of()),
                new RecordingFormat(64, 64, 30));
        assertEquals(HardwareEncoders.State.UNUSABLE, status.state(), status.toString());
        assertTrue(status.detail().contains("timed out"), status.detail());
        assertEquals(fake, status.ffmpeg());
        assertTrue(System.nanoTime() - started < 20_000_000_000L, "probe hung");
    }
}

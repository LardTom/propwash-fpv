package de.lardtom.propwash.video.record.mp4;

import de.lardtom.propwash.video.record.h264.EncodedFrame;
import de.lardtom.propwash.video.record.h264.EncoderSettings;
import de.lardtom.propwash.video.record.h264.H264Encoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mp4WriterTest {
    @TempDir
    Path dir;

    private static byte[] frame(int width, int height, int index) {
        byte[] out = new byte[width * height * 3 / 2];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[y * width + x] = (byte) ((x * 2 + y + index * 4) & 0xFF);
            }
        }
        java.util.Arrays.fill(out, width * height, out.length, (byte) 128);
        return out;
    }

    @Test
    void threeSecondRecordingHasTheRightBoxesDurationAndFrames() throws IOException {
        Path file = this.dir.resolve("three.mp4");
        int fps = 30;
        byte[] sps;
        try (H264Encoder encoder = new H264Encoder(320, 180, fps, new EncoderSettings(1, 2, 1_000_000L, 30, 16, 44, true))) {
            sps = encoder.sps();
            Mp4Writer writer = new Mp4Writer(file, 320, 180, fps * 1000, encoder.sps(), encoder.pps(), "Propwash H.264");
            int written = 0;
            int index = 0;
            while (written < 90) {
                EncodedFrame frame = written % 10 == 9 ? encoder.repeat() : encoder.encode(frame(320, 180, index++), 1);
                writer.writeSample(frame.toSample(), 1000, frame.keyframe());
                written++;
            }
            writer.finish();
            assertEquals(90, writer.samples());
            assertEquals(90_000L, writer.durationTicks());
        }
        Mp4Info info = Mp4Reader.read(file);
        assertEquals(List.of("ftyp", "mdat", "moov"), info.topLevelBoxes());
        assertEquals("vide", info.handler());
        assertEquals("avc1", info.codec());
        assertEquals(320, info.width());
        assertEquals(180, info.height());
        assertEquals(90, info.sampleCount());
        assertEquals(30_000, info.mediaTimescale());
        assertEquals(90_000L, info.mediaDuration());
        assertEquals(90_000L, info.sampleDurationTotal());
        assertEquals(3.0, info.durationSeconds(), 1.0E-9);
        assertEquals(3000L, info.movieDuration());
        assertEquals(3.0, info.movieSeconds(), 1.0E-9);
        assertEquals(30.0, info.frameRate(), 1.0E-9);
        assertTrue(info.startsWithSync());
        assertEquals(3, info.syncSampleCount());
        assertEquals(66, info.profile());
        assertArrayEquals(sps, info.sps());
    }

    @Test
    void variableSampleDurationsAddUp() throws IOException {
        Path file = this.dir.resolve("vfr.mp4");
        try (H264Encoder encoder = new H264Encoder(64, 64, 60, EncoderSettings.of(1, 500_000L, 60))) {
            Mp4Writer writer = new Mp4Writer(file, 64, 64, 60_000, encoder.sps(), encoder.pps(), "x");
            int[] durations = {1000, 1000, 2000, 1000, 3000, 3000, 1000};
            for (int i = 0; i < durations.length; i++) {
                EncodedFrame frame = encoder.encode(frame(64, 64, i), 0);
                writer.writeSample(frame.toSample(), durations[i], frame.keyframe());
            }
            writer.finish();
        }
        Mp4Info info = Mp4Reader.read(file);
        assertEquals(7, info.sampleCount());
        assertEquals(12_000L, info.mediaDuration());
        assertEquals(12_000L, info.sampleDurationTotal());
        assertEquals(200L, info.movieDuration());
    }

    @Test
    void mdatSizeCoversAllSamples() throws IOException {
        Path file = this.dir.resolve("size.mp4");
        long samplesBytes = 0L;
        try (H264Encoder encoder = new H264Encoder(64, 64, 30, EncoderSettings.of(1, 500_000L, 30))) {
            Mp4Writer writer = new Mp4Writer(file, 64, 64, 30_000, encoder.sps(), encoder.pps(), "x");
            for (int i = 0; i < 5; i++) {
                byte[] sample = encoder.encode(frame(64, 64, i), 0).toSample();
                samplesBytes += sample.length;
                writer.writeSample(sample, 1000, i == 0);
            }
            writer.finish();
        }
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int ftypSize = buffer.getInt(0);
        assertEquals("mdat", new String(bytes, ftypSize + 4, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(1, buffer.getInt(ftypSize));
        long mdatSize = buffer.getLong(ftypSize + 8);
        assertEquals(16L + samplesBytes, mdatSize);
        assertEquals("moov", new String(bytes, (int) (ftypSize + mdatSize) + 4, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(bytes.length, ftypSize + mdatSize + buffer.getInt((int) (ftypSize + mdatSize)));
    }

    @Test
    void readerRejectsFilesWithoutMoov() throws IOException {
        Path file = this.dir.resolve("broken.mp4");
        try (H264Encoder encoder = new H264Encoder(64, 64, 30, EncoderSettings.of(1, 500_000L, 30))) {
            Mp4Writer writer = new Mp4Writer(file, 64, 64, 30_000, encoder.sps(), encoder.pps(), "x");
            writer.writeSample(encoder.encode(frame(64, 64, 0), 0).toSample(), 1000, true);
            writer.close();
        }
        assertThrows(IOException.class, () -> Mp4Reader.read(file));
    }

    @Test
    void abortRemovesThePartialFile() throws IOException {
        Path file = this.dir.resolve("aborted.mp4");
        try (H264Encoder encoder = new H264Encoder(64, 64, 30, EncoderSettings.of(1, 500_000L, 30))) {
            Mp4Writer writer = new Mp4Writer(file, 64, 64, 30_000, encoder.sps(), encoder.pps(), "x");
            writer.writeSample(encoder.encode(frame(64, 64, 0), 0).toSample(), 1000, true);
            writer.abort();
        }
        assertFalse(Files.exists(file));
    }
}

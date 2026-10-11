package de.lardtom.propwash.video.record.mp4;

import de.lardtom.propwash.video.record.AudioLane;
import de.lardtom.propwash.video.record.aac.AacEncoder;
import de.lardtom.propwash.video.record.h264.EncodedFrame;
import de.lardtom.propwash.video.record.h264.EncoderSettings;
import de.lardtom.propwash.video.record.h264.H264Encoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mp4AudioTrackTest {
    private static final int FPS = 30;

    @TempDir
    Path dir;

    private static byte[] frame(int index) {
        byte[] out = new byte[160 * 96 * 3 / 2];
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 160; x++) {
                out[y * 160 + x] = (byte) ((x + y * 2 + index * 5) & 0xFF);
            }
        }
        Arrays.fill(out, 160 * 96, out.length, (byte) 128);
        return out;
    }

    private static short[] chunk(long start, int count) {
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            out[i] = (short) Math.round(9000.0 * Math.sin(2.0 * Math.PI * 440.0 * (start + i) / AudioLane.RATE));
        }
        return out;
    }

    private Path record(int seconds, long firstSlot, boolean silentTail) throws IOException {
        Path file = this.dir.resolve("clip_" + seconds + "_" + firstSlot + ".mp4");
        AudioLane lane = new AudioLane(AacEncoder.DEFAULT_BITRATE);
        try (H264Encoder encoder = new H264Encoder(160, 96, FPS, EncoderSettings.of(1, 500_000L, FPS))) {
            Mp4Writer writer = new Mp4Writer(file, 160, 96, FPS * 1000, encoder.sps(), encoder.pps(), "Propwash H.264");
            writer.addAudioTrack(AudioLane.RATE, 1, AacEncoder.audioSpecificConfig(), lane.bitrate());
            long samples = 0L;
            long audioUntil = silentTail ? (long) seconds * AudioLane.RATE / 2 : (long) seconds * AudioLane.RATE + firstSlot * AudioLane.RATE / FPS;
            for (int slot = 0; slot < seconds * FPS; slot++) {
                EncodedFrame frame = encoder.encode(frame(slot), 1);
                writer.writeSample(frame.toSample(), 1000, frame.keyframe());
                int count = AudioLane.RATE / FPS;
                if (samples < audioUntil) {
                    lane.submit(chunk(samples, count), 0, count);
                    samples += count;
                }
                if (slot == 0) {
                    lane.start(firstSlot, FPS);
                }
                lane.pump(writer);
            }
            lane.finish(writer, (long) seconds * AudioLane.RATE);
            writer.finish();
        }
        return file;
    }

    @Test
    void videoAndAudioTracksReadBack() throws IOException {
        Path file = this.record(2, 0L, false);
        Mp4Info info = Mp4Reader.read(file);
        assertEquals(2, info.trackCount());
        assertEquals("vide", info.handler());
        assertEquals("avc1", info.codec());
        assertEquals(60, info.sampleCount());
        assertTrue(info.hasAudio());
        assertEquals("mp4a", info.audioCodec());
        assertEquals(AacEncoder.OBJECT_TYPE_LC, info.audioObjectType());
        assertEquals(AudioLane.RATE, info.audioSampleRate());
        assertEquals(1, info.audioChannels());
        assertArrayEquals(AacEncoder.audioSpecificConfig(), info.audioConfig());
        assertEquals(AacEncoder.PRIMING, info.audioEditMediaTime());
        assertEquals(2.0, info.audioSeconds(), 0.001);
        assertEquals(2.0, info.durationSeconds(), 0.001);
        assertEquals(2.0, info.movieSeconds(), 0.001);
        long frames = (2L * AudioLane.RATE + AacEncoder.FRAME - 1) / AacEncoder.FRAME + 1;
        assertEquals(frames, info.audioSampleCount());
        assertEquals(frames * AacEncoder.FRAME, info.audioMediaDuration());
    }

    @Test
    void theAudioTrackCarriesARollGroupSoAppleHonoursThePriming() throws IOException {
        Path file = this.record(1, 0L, false);
        String boxes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        assertTrue(boxes.contains("sgpd") && boxes.contains("sbgp") && boxes.contains("roll"));
        assertTrue(boxes.contains("elst") && boxes.contains("esds") && boxes.contains("smhd"));
    }

    @Test
    void lateFirstFrameDropsTheLeadingAudio() throws IOException {
        Path file = this.record(2, 3L, false);
        Mp4Info info = Mp4Reader.read(file);
        assertEquals(2.0, info.audioSeconds(), 0.001);
    }

    @Test
    void missingAudioIsPaddedToTheVideoLength() throws IOException {
        Path file = this.record(2, 0L, true);
        Mp4Info info = Mp4Reader.read(file);
        assertEquals(2.0, info.audioSeconds(), 0.001);
        assertEquals(info.durationSeconds(), info.audioSeconds(), 0.001);
    }

    @Test
    void writerWithoutAudioStaysVideoOnly() throws IOException {
        Path file = this.dir.resolve("video.mp4");
        try (H264Encoder encoder = new H264Encoder(160, 96, FPS, EncoderSettings.of(1, 500_000L, FPS))) {
            Mp4Writer writer = new Mp4Writer(file, 160, 96, FPS * 1000, encoder.sps(), encoder.pps(), "Propwash H.264");
            EncodedFrame frame = encoder.encode(frame(0), 1);
            writer.writeSample(frame.toSample(), 1000, true);
            writer.finish();
        }
        Mp4Info info = Mp4Reader.read(file);
        assertEquals(1, info.trackCount());
        assertFalse(info.hasAudio());
    }
}

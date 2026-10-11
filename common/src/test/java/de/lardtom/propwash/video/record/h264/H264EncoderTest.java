package de.lardtom.propwash.video.record.h264;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H264EncoderTest {
    static byte[] frame(int width, int height, int index) {
        byte[] out = new byte[width * height * 3 / 2];
        int shiftX = index * 3;
        int shiftY = index;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sx = x + shiftX;
                int sy = y + shiftY;
                int value = 60 + (sx * 255 / (width * 2)) + (((sx / 12) + (sy / 12)) % 2 == 0 ? 40 : 0);
                if (Math.abs(x - (40 + index * 5) % width) < 20 && Math.abs(y - height / 2) < 20) {
                    value = 230;
                }
                out[y * width + x] = (byte) Math.min(255, value);
            }
        }
        int cw = width / 2;
        int ch = height / 2;
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                out[width * height + y * cw + x] = (byte) (110 + ((x + index) * 40 / cw));
                out[width * height + cw * ch + y * cw + x] = (byte) (150 - (y * 30 / ch));
            }
        }
        return out;
    }

    static double psnr(byte[] a, byte[] b, int count) {
        double mse = 0.0;
        for (int i = 0; i < count; i++) {
            int d = (a[i] & 0xFF) - (b[i] & 0xFF);
            mse += d * d;
        }
        mse /= count;
        return mse == 0.0 ? 99.0 : 10.0 * Math.log10(255.0 * 255.0 / mse);
    }

    @Test
    void spsDescribesProfileSizeCropAndTiming() {
        try (H264Encoder encoder = new H264Encoder(1920, 1080, 60, EncoderSettings.of(1, 40_000_000L, 60))) {
            byte[] sps = encoder.sps();
            assertEquals(0x67, sps[0] & 0xFF);
            BitReader r = new BitReader(Nal.unwrap(sps, 0, sps.length));
            assertEquals(66, r.bits(8));
            assertEquals(0b11000000, r.bits(8));
            int level = r.bits(8);
            assertEquals(42, level);
            assertEquals(level, encoder.level());
            assertEquals(0, r.ue());
            int log2MaxFrameNum = r.ue() + 4;
            assertEquals(8, log2MaxFrameNum);
            assertEquals(2, r.ue());
            assertEquals(1, r.ue());
            assertEquals(0, r.bits(1));
            assertEquals(120, r.ue() + 1);
            assertEquals(68, r.ue() + 1);
            assertEquals(1, r.bits(1));
            assertEquals(1, r.bits(1));
            assertEquals(1, r.bits(1));
            assertEquals(0, r.ue());
            assertEquals(0, r.ue());
            assertEquals(0, r.ue());
            assertEquals(4, r.ue());
            assertEquals(1, r.bits(1));
            assertEquals(1, r.bits(1));
            assertEquals(1, r.bits(8));
            assertEquals(0, r.bits(1));
            assertEquals(1, r.bits(1));
            assertEquals(5, r.bits(3));
            assertEquals(0, r.bits(1));
            assertEquals(1, r.bits(1));
            assertEquals(1, r.bits(8));
            assertEquals(1, r.bits(8));
            assertEquals(1, r.bits(8));
            assertEquals(0, r.bits(1));
            assertEquals(1, r.bits(1));
            long unitsInTick = r.bitsLong(32);
            long timeScale = r.bitsLong(32);
            assertEquals(60.0, timeScale / (2.0 * unitsInTick), 1.0E-9);
        }
    }

    @Test
    void smallStreamsPickALowLevel() {
        try (H264Encoder encoder = new H264Encoder(1280, 720, 30, EncoderSettings.of(1, 8_000_000L, 30))) {
            assertEquals(31, encoder.level());
        }
    }

    @Test
    void keyframesFollowTheGop() {
        EncoderSettings settings = new EncoderSettings(1, 2, 2_000_000L, 5, 16, 44, true);
        try (H264Encoder encoder = new H264Encoder(160, 96, 30, settings)) {
            for (int i = 0; i < 12; i++) {
                EncodedFrame frame = encoder.encode(frame(160, 96, i), SliceEncoder.PRESET_QUALITY);
                assertEquals(i % 5 == 0, frame.keyframe(), "frame " + i);
                assertEquals(2, frame.nals().length);
                for (byte[] nal : frame.nals()) {
                    int type = Nal.type(nal, 0);
                    int refIdc = (nal[0] >> 5) & 3;
                    assertEquals(frame.keyframe() ? Nal.IDR : Nal.SLICE, type);
                    assertTrue(refIdc > 0);
                }
            }
            assertEquals(12, encoder.frames());
        }
    }

    @Test
    void sliceHeadersStartAtTheirRowAndCountFrames() {
        EncoderSettings settings = new EncoderSettings(1, 3, 2_000_000L, 30, 16, 44, true);
        try (H264Encoder encoder = new H264Encoder(160, 96, 30, settings)) {
            for (int i = 0; i < 3; i++) {
                EncodedFrame frame = encoder.encode(frame(160, 96, i), SliceEncoder.PRESET_QUALITY);
                int expectedFirstMb = 0;
                for (int s = 0; s < frame.nals().length; s++) {
                    byte[] nal = frame.nals()[s];
                    BitReader r = new BitReader(Nal.unwrap(nal, 0, nal.length));
                    assertEquals(expectedFirstMb, r.ue());
                    assertEquals(i == 0 ? 2 : 0, r.ue());
                    assertEquals(0, r.ue());
                    assertEquals(i, r.bits(8));
                    expectedFirstMb += 10 * 2;
                }
            }
        }
    }

    @Test
    void reconstructionStaysCloseToTheSource() {
        EncoderSettings settings = new EncoderSettings(2, 4, 6_000_000L, 30, 16, 40, true);
        try (H264Encoder encoder = new H264Encoder(320, 192, 30, settings)) {
            double worst = 99.0;
            for (int i = 0; i < 10; i++) {
                byte[] source = frame(320, 192, i);
                encoder.encode(source, SliceEncoder.PRESET_QUALITY);
                byte[] recon = encoder.reconstructionI420();
                worst = Math.min(worst, psnr(source, recon, 320 * 192));
            }
            assertTrue(worst > 36.0, "PSNR " + worst);
        }
    }

    @Test
    void repeatedFramesAreTinyAndDoNotChangeThePicture() {
        try (H264Encoder encoder = new H264Encoder(320, 192, 30, EncoderSettings.of(1, 2_000_000L, 30))) {
            encoder.encode(frame(320, 192, 0), SliceEncoder.PRESET_FAST);
            byte[] before = encoder.reconstructionI420();
            EncodedFrame repeat = encoder.repeat();
            assertFalse(repeat.keyframe());
            assertEquals(1, repeat.nals().length);
            assertTrue(repeat.sampleSize() < 24, "repeat size " + repeat.sampleSize());
            assertArrayEquals(before, encoder.reconstructionI420());
            BitReader r = new BitReader(Nal.unwrap(repeat.nals()[0], 0, repeat.nals()[0].length));
            assertEquals(0, r.ue());
            assertEquals(0, r.ue());
            assertEquals(0, r.ue());
            assertEquals(1, r.bits(8));
            EncodedFrame next = encoder.encode(frame(320, 192, 1), SliceEncoder.PRESET_FAST);
            BitReader n = new BitReader(Nal.unwrap(next.nals()[0], 0, next.nals()[0].length));
            n.ue();
            n.ue();
            n.ue();
            assertEquals(2, n.bits(8));
        }
    }

    @Test
    void threadCountDoesNotChangeTheBitstream() {
        byte[][] single = encodeAll(new EncoderSettings(1, 4, 3_000_000L, 30, 16, 44, true));
        byte[][] threaded = encodeAll(new EncoderSettings(4, 4, 3_000_000L, 30, 16, 44, true));
        assertEquals(single.length, threaded.length);
        for (int i = 0; i < single.length; i++) {
            assertArrayEquals(single[i], threaded[i], "frame " + i);
        }
    }

    @Test
    void everyPresetProducesAStream() {
        for (int preset : new int[]{SliceEncoder.PRESET_QUALITY, SliceEncoder.PRESET_FAST, SliceEncoder.PRESET_FASTEST}) {
            try (H264Encoder encoder = new H264Encoder(160, 96, 30, EncoderSettings.of(1, 1_000_000L, 30))) {
                for (int i = 0; i < 4; i++) {
                    EncodedFrame frame = encoder.encode(frame(160, 96, i), preset);
                    assertTrue(frame.sampleSize() > 0);
                }
                assertTrue(psnr(frame(160, 96, 3), encoder.reconstructionI420(), 160 * 96) > 30.0, "preset " + preset);
            }
        }
    }

    @Test
    void annexBAndLengthPrefixedFormsCarryTheSameNals() {
        try (H264Encoder encoder = new H264Encoder(160, 96, 30, EncoderSettings.of(1, 1_000_000L, 30))) {
            EncodedFrame frame = encoder.encode(frame(160, 96, 0), SliceEncoder.PRESET_QUALITY);
            byte[] sample = frame.toSample();
            byte[] annexB = frame.toAnnexB();
            assertEquals(sample.length, annexB.length);
            int offset = 0;
            for (byte[] nal : frame.nals()) {
                int length = ((sample[offset] & 0xFF) << 24) | ((sample[offset + 1] & 0xFF) << 16) | ((sample[offset + 2] & 0xFF) << 8)
                        | (sample[offset + 3] & 0xFF);
                assertEquals(nal.length, length);
                assertArrayEquals(new byte[]{0, 0, 0, 1}, Arrays.copyOfRange(annexB, offset, offset + 4));
                assertArrayEquals(nal, Arrays.copyOfRange(sample, offset + 4, offset + 4 + length));
                offset += 4 + length;
            }
        }
    }

    private static byte[][] encodeAll(EncoderSettings settings) {
        try (H264Encoder encoder = new H264Encoder(320, 192, 30, settings)) {
            byte[][] out = new byte[6][];
            for (int i = 0; i < out.length; i++) {
                out[i] = encoder.encode(frame(320, 192, i), SliceEncoder.PRESET_QUALITY).toSample();
            }
            return out;
        }
    }
}

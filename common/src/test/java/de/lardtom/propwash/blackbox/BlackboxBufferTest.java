package de.lardtom.propwash.blackbox;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxBufferTest {
    @Test
    void variableBytesMatchTheBetaflightLayout() {
        BlackboxBuffer b = new BlackboxBuffer(16);
        b.unsignedVB(0);
        b.unsignedVB(127);
        b.unsignedVB(128);
        b.unsignedVB(300);
        b.signedVB(-1);
        b.signedVB(1);
        b.signedVB(-64);
        b.unsignedVB(-1);
        assertArrayEquals(new byte[]{0, 127, (byte) 0x80, 1, (byte) 0xAC, 2, 1, 2, 127, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F},
                b.toByteArray());
    }

    @Test
    void numbersRoundTrip() {
        Random random = new Random(7);
        int[] values = new int[2000];
        BlackboxBuffer b = new BlackboxBuffer(16);
        for (int i = 0; i < values.length; i++) {
            values[i] = switch (i % 4) {
                case 0 -> random.nextInt();
                case 1 -> random.nextInt(200) - 100;
                case 2 -> random.nextInt(70000) - 35000;
                default -> i % 8 == 3 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            };
            b.signedVB(values[i]);
            b.unsignedVB(values[i]);
            b.neg14Bit(values[i] % 8000);
        }
        BlackboxDecoder d = BlackboxDecoder.primitives(b.toByteArray());
        for (int value : values) {
            assertEquals(value, d.svb());
            assertEquals(value & 0xFFFFFFFFL, d.uvb());
            long raw = d.uvb();
            long extended = (raw & 0x2000) != 0 ? raw - 0x4000 : raw;
            assertEquals(value % 8000, -extended);
        }
        assertTrue(d.atEnd());
    }

    @Test
    void tag2x3s32CoversEverySelector() {
        int[][] cases = {{0, 0, 0}, {-2, 1, -1}, {1, -2, 0}, {-8, 7, 3}, {2, 0, -3}, {-32, 31, 9}, {12, -20, 0}, {32, 0, 0},
                {-129, 127, -128}, {40000, -40000, 1}, {8388608, -8388609, 300}, {Integer.MAX_VALUE, Integer.MIN_VALUE, -1}};
        BlackboxBuffer b = new BlackboxBuffer(16);
        for (int[] c : cases) {
            b.tag2x3s32(c[0], c[1], c[2]);
        }
        BlackboxDecoder d = BlackboxDecoder.primitives(b.toByteArray());
        long[] out = new long[8];
        for (int[] c : cases) {
            d.tag2x3s32(out);
            assertEquals(c[0], out[0]);
            assertEquals(c[1], out[1]);
            assertEquals(c[2], out[2]);
        }
        assertTrue(d.atEnd());
    }

    @Test
    void tag8x4s16CoversEveryNibbleAlignment() {
        int[] sizes = {0, 5, -100, 20000};
        BlackboxBuffer b = new BlackboxBuffer(16);
        int combos = 0;
        int[][] all = new int[256][];
        for (int s0 = 0; s0 < 4; s0++) {
            for (int s1 = 0; s1 < 4; s1++) {
                for (int s2 = 0; s2 < 4; s2++) {
                    for (int s3 = 0; s3 < 4; s3++) {
                        int[] v = {sizes[s0] * (combos % 2 == 0 ? 1 : -1), sizes[s1], -sizes[s2], sizes[s3] == 0 ? 0 : sizes[s3] - 1};
                        all[combos++] = v;
                        b.tag8x4s16(v[0], v[1], v[2], v[3]);
                    }
                }
            }
        }
        BlackboxDecoder d = BlackboxDecoder.primitives(b.toByteArray());
        long[] out = new long[8];
        for (int[] v : all) {
            d.tag8x4s16(out);
            for (int i = 0; i < 4; i++) {
                assertEquals(v[i], out[i]);
            }
        }
        assertTrue(d.atEnd());
    }

    @Test
    void tag8x8svbSkipsZerosAndHasNoHeaderForASingleValue() {
        BlackboxBuffer b = new BlackboxBuffer(16);
        b.tag8x8svb(new int[]{0, 3, 0, -700}, 4);
        b.tag8x8svb(new int[]{-5}, 1);
        assertArrayEquals(new byte[]{0x0A, 6, (byte) 0xF7, 0x0A, 9}, b.toByteArray());
    }
}

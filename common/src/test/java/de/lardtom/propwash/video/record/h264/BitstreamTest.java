package de.lardtom.propwash.video.record.h264;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitstreamTest {
    @Test
    void expGolombRoundTripsAndLengthsMatch() {
        BitWriter writer = new BitWriter(8);
        int[] values = {0, 1, 2, 3, 7, 8, 254, 255, 256, 65535, 1_000_000};
        int[] signed = {0, 1, -1, 2, -2, 100, -100, 32767, -32768};
        for (int value : values) {
            writer.ue(value);
        }
        for (int value : signed) {
            writer.se(value);
        }
        writer.trailing();
        BitReader reader = new BitReader(writer.toByteArray());
        for (int value : values) {
            long before = reader.position();
            assertEquals(value, reader.ue());
            assertEquals(BitWriter.ueLength(value), reader.position() - before, "ue length of " + value);
        }
        for (int value : signed) {
            long before = reader.position();
            assertEquals(value, reader.se());
            assertEquals(BitWriter.seLength(value), reader.position() - before, "se length of " + value);
        }
        assertEquals(1, reader.bits(1));
    }

    @Test
    void knownExpGolombCodes() {
        BitWriter writer = new BitWriter();
        writer.ue(0);
        writer.ue(1);
        writer.ue(2);
        writer.ue(3);
        writer.align();
        assertArrayEquals(new byte[]{(byte) 0b10100110, (byte) 0b01000000}, writer.toByteArray());
    }

    @Test
    void emulationPreventionIsInsertedAndRemoved() {
        byte[] rbsp = {0, 0, 0, 0, 0, 1, 0, 0, 2, 0, 0, 3, 0, 0, 4, 5};
        byte[] nal = Nal.wrap(3, Nal.SPS, rbsp, rbsp.length);
        assertEquals(0x67, nal[0] & 0xFF);
        for (int i = 3; i < nal.length; i++) {
            boolean startCode = nal[i - 2] == 0 && nal[i - 1] == 0 && (nal[i] & 0xFF) <= 2;
            assertFalse(startCode, "start code emulation at " + i);
        }
        assertArrayEquals(rbsp, Nal.unwrap(nal, 0, nal.length));
    }

    @Test
    void coeffTokenTablesArePrefixFree() {
        for (int table = 0; table < 3; table++) {
            assertPrefixFree("coeff_token " + table, codes(H264Tables.COEFF_TOKEN_BITS[table], H264Tables.COEFF_TOKEN_LEN[table], 17));
        }
        assertPrefixFree("coeff_token FLC", codes(H264Tables.COEFF_TOKEN_BITS[3], H264Tables.COEFF_TOKEN_LEN[3], 17));
        assertPrefixFree("chroma DC coeff_token", codes(H264Tables.CHROMA_DC_COEFF_TOKEN_BITS, H264Tables.CHROMA_DC_COEFF_TOKEN_LEN, 5));
    }

    @Test
    void totalZerosAndRunBeforeTablesArePrefixFree() {
        for (int total = 0; total < H264Tables.TOTAL_ZEROS_LEN.length; total++) {
            assertEquals(16 - total, H264Tables.TOTAL_ZEROS_LEN[total].length, "total_zeros row " + total);
            assertPrefixFree("total_zeros " + (total + 1), rowCodes(H264Tables.TOTAL_ZEROS_BITS[total], H264Tables.TOTAL_ZEROS_LEN[total]));
        }
        for (int total = 0; total < 3; total++) {
            assertPrefixFree("chroma DC total_zeros " + (total + 1),
                    rowCodes(H264Tables.CHROMA_DC_TOTAL_ZEROS_BITS[total], H264Tables.CHROMA_DC_TOTAL_ZEROS_LEN[total]));
        }
        for (int zeros = 0; zeros < H264Tables.RUN_BEFORE_LEN.length; zeros++) {
            assertPrefixFree("run_before " + (zeros + 1), rowCodes(H264Tables.RUN_BEFORE_BITS[zeros], H264Tables.RUN_BEFORE_LEN[zeros]));
        }
    }

    @Test
    void codedBlockPatternMappingIsABijection() {
        boolean[] seen = new boolean[48];
        for (int cbp = 0; cbp < 48; cbp++) {
            int code = H264Tables.INTER_CBP_TO_CODE[cbp];
            assertFalse(seen[code], "code " + code);
            seen[code] = true;
        }
        assertEquals(0, H264Tables.INTER_CBP_TO_CODE[0]);
        assertEquals(12, H264Tables.INTER_CBP_TO_CODE[47]);
    }

    @Test
    void cavlcBlockCanBeParsedBack() {
        int[][] blocks = {
                {5, 0, -2, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, -1},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {40, -31, 12, 7, -3, 2, 2, -1, 1, 1, 0, -1, 1, 0, 1, 1},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {-2047, 2047, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}
        };
        Cavlc cavlc = new Cavlc();
        for (int nC : new int[]{0, 2, 5, 9}) {
            for (int[] block : blocks) {
                BitWriter writer = new BitWriter();
                int total = cavlc.write(writer, block, 0, 16, nC);
                writer.trailing();
                int expected = 0;
                for (int value : block) {
                    expected += value != 0 ? 1 : 0;
                }
                assertEquals(expected, total);
                assertArrayEquals(block, CavlcParser.parse(new BitReader(writer.toByteArray()), 16, nC), "nC " + nC);
            }
        }
    }

    private static List<long[]> codes(int[] bits, int[] lengths, int rows) {
        List<long[]> out = new ArrayList<>();
        for (int total = 0; total < rows; total++) {
            for (int ones = 0; ones < 4; ones++) {
                int index = total * 4 + ones;
                if (ones > total || ones > 3 || lengths[index] == 0) {
                    continue;
                }
                out.add(new long[]{bits[index], lengths[index]});
            }
        }
        return out;
    }

    private static List<long[]> rowCodes(int[] bits, int[] lengths) {
        List<long[]> out = new ArrayList<>();
        for (int i = 0; i < lengths.length; i++) {
            out.add(new long[]{bits[i], lengths[i]});
        }
        return out;
    }

    private static void assertPrefixFree(String name, List<long[]> codes) {
        double kraft = 0.0;
        for (int i = 0; i < codes.size(); i++) {
            long[] a = codes.get(i);
            assertTrue(a[1] > 0 && a[0] < (1L << a[1]), name + ": code " + a[0] + " does not fit " + a[1] + " bits");
            kraft += Math.pow(2.0, -a[1]);
            for (int j = 0; j < codes.size(); j++) {
                if (i == j) {
                    continue;
                }
                long[] b = codes.get(j);
                if (a[1] <= b[1]) {
                    long prefix = b[0] >>> (b[1] - a[1]);
                    assertFalse(prefix == a[0], name + ": " + a[0] + "/" + a[1] + " is a prefix of " + b[0] + "/" + b[1]);
                }
            }
        }
        assertTrue(kraft <= 1.0 + 1.0E-12, name + ": Kraft sum " + kraft);
    }
}

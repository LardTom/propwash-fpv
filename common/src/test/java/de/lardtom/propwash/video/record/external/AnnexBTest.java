package de.lardtom.propwash.video.record.external;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnexBTest {
    private static final byte[] SPS = {0x67, 0x4D, 0x40, 0x28, 0x11, 0x22};
    private static final byte[] PPS = {0x68, (byte) 0xEE, 0x3C, (byte) 0x80};
    private static final byte[] AUD = {0x09, (byte) 0xF0};
    private static final byte[] SEI = {0x06, 0x05, 0x01, 0x02, (byte) 0x80};

    private static byte[] slice(int type, boolean first, int length, int seed) {
        byte[] nal = new byte[length];
        nal[0] = (byte) (0x60 | type);
        nal[1] = (byte) (first ? 0x88 : 0x44);
        for (int i = 2; i < length; i++) {
            nal[i] = (byte) (1 + (i * 31 + seed) % 250);
        }
        return nal;
    }

    private static byte[] stream(boolean longStartCodes, byte[]... nals) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] nal : nals) {
            if (longStartCodes) {
                out.write(0);
            }
            out.writeBytes(new byte[]{0, 0, 1});
            out.writeBytes(nal);
        }
        return out.toByteArray();
    }

    private static List<AnnexB.AccessUnit> parse(byte[] data, int chunk) {
        List<AnnexB.AccessUnit> units = new ArrayList<>();
        AnnexB parser = new AnnexB(units::add);
        for (int at = 0; at < data.length; at += chunk) {
            parser.feed(data, at, Math.min(chunk, data.length - at));
        }
        parser.finish();
        return units;
    }

    @Test
    void splitsAccessUnitsAtPrefixNalsAndFirstSlices() {
        byte[] idr = slice(AnnexB.IDR, true, 900, 1);
        byte[] p1a = slice(AnnexB.SLICE, true, 300, 2);
        byte[] p1b = slice(AnnexB.SLICE, false, 200, 3);
        byte[] p2 = slice(AnnexB.SLICE, true, 70_000, 4);
        byte[] data = stream(true, AUD, SPS, PPS, SEI, idr, AUD, p1a, p1b, p2);
        for (int chunk : new int[]{1, 3, 7, 4096, data.length}) {
            List<AnnexB.AccessUnit> units = parse(data, chunk);
            assertEquals(3, units.size(), "chunk " + chunk);
            assertTrue(units.get(0).keyframe());
            assertFalse(units.get(1).keyframe());
            assertArrayEquals(SPS, units.get(0).find(AnnexB.SPS));
            assertArrayEquals(PPS, units.get(0).find(AnnexB.PPS));
            assertEquals(2, units.get(1).nals().stream().filter(nal -> AnnexB.type(nal) == AnnexB.SLICE).count());
            assertArrayEquals(p2, units.get(2).nals().getFirst());
        }
    }

    @Test
    void samplesAreLengthPrefixedWithoutParameterSets() {
        byte[] idr = slice(AnnexB.IDR, true, 64, 9);
        List<AnnexB.AccessUnit> units = parse(stream(false, SPS, PPS, SEI, idr), 5);
        assertEquals(1, units.size());
        byte[] sample = units.getFirst().toSample();
        assertEquals(4 + SEI.length + 4 + idr.length, sample.length);
        assertEquals(SEI.length, sample[3]);
        assertEquals(AnnexB.SEI, sample[4] & 0x1F);
        assertEquals(AnnexB.IDR, sample[4 + SEI.length + 4] & 0x1F);
    }

    @Test
    void trailingZerosBelongToTheNextStartCode() {
        byte[] first = slice(AnnexB.IDR, true, 32, 5);
        byte[] second = slice(AnnexB.SLICE, true, 32, 6);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0, 0, 0, 1});
        out.writeBytes(first);
        out.writeBytes(new byte[]{0, 0, 0, 0, 0, 1});
        out.writeBytes(second);
        List<AnnexB.AccessUnit> units = parse(out.toByteArray(), 2);
        assertEquals(2, units.size());
        assertArrayEquals(first, units.get(0).nals().getFirst());
        assertArrayEquals(second, units.get(1).nals().getFirst());
    }

    @Test
    void parameterSetsWithoutPictureAreNoFrame() {
        assertEquals(0, parse(stream(true, SPS, PPS), 4).size());
    }
}

package de.lardtom.propwash.video.record.h264;

final class CavlcParser {
    private CavlcParser() {
    }

    static int[] parse(BitReader in, int maxCoeff, int nC) {
        int[] bits;
        int[] lengths;
        int rows;
        if (nC == Cavlc.CHROMA_DC) {
            bits = H264Tables.CHROMA_DC_COEFF_TOKEN_BITS;
            lengths = H264Tables.CHROMA_DC_COEFF_TOKEN_LEN;
            rows = 5;
        } else {
            int table = nC < 2 ? 0 : nC < 4 ? 1 : nC < 8 ? 2 : 3;
            bits = H264Tables.COEFF_TOKEN_BITS[table];
            lengths = H264Tables.COEFF_TOKEN_LEN[table];
            rows = 17;
        }
        int total = -1;
        int trailingOnes = -1;
        int code = 0;
        for (int length = 1; length <= 16 && total < 0; length++) {
            code = (code << 1) | in.bits(1);
            for (int index = 0; index < rows * 4; index++) {
                if (lengths[index] == length && bits[index] == code && (index & 3) <= index >> 2) {
                    total = index >> 2;
                    trailingOnes = index & 3;
                    break;
                }
            }
        }
        if (total < 0) {
            throw new IllegalStateException("no coeff_token");
        }
        int[] out = new int[maxCoeff];
        if (total == 0) {
            return out;
        }
        int[] levels = new int[total];
        int suffixLength = total > 10 && trailingOnes < 3 ? 1 : 0;
        for (int i = 0; i < total; i++) {
            if (i < trailingOnes) {
                levels[i] = in.bits(1) == 1 ? -1 : 1;
                continue;
            }
            int prefix = 0;
            while (in.bits(1) == 0) {
                prefix++;
            }
            int levelCode = Math.min(15, prefix) << suffixLength;
            int suffixSize = prefix == 14 && suffixLength == 0 ? 4 : prefix >= 15 ? prefix - 3 : suffixLength;
            if (suffixLength > 0 || prefix >= 14) {
                levelCode += suffixSize > 0 ? in.bits(suffixSize) : 0;
            }
            if (prefix >= 15 && suffixLength == 0) {
                levelCode += 15;
            }
            if (i == trailingOnes && trailingOnes < 3) {
                levelCode += 2;
            }
            levels[i] = (levelCode & 1) == 0 ? (levelCode + 2) >> 1 : (-levelCode - 1) >> 1;
            if (suffixLength == 0) {
                suffixLength = 1;
            }
            if (Math.abs(levels[i]) > (3 << (suffixLength - 1)) && suffixLength < 6) {
                suffixLength++;
            }
        }
        int zerosLeft = 0;
        if (total < maxCoeff) {
            int[][] zeroBits = nC == Cavlc.CHROMA_DC ? H264Tables.CHROMA_DC_TOTAL_ZEROS_BITS : H264Tables.TOTAL_ZEROS_BITS;
            int[][] zeroLengths = nC == Cavlc.CHROMA_DC ? H264Tables.CHROMA_DC_TOTAL_ZEROS_LEN : H264Tables.TOTAL_ZEROS_LEN;
            zerosLeft = match(in, zeroBits[total - 1], zeroLengths[total - 1]);
        }
        int position = total + zerosLeft - 1;
        for (int i = 0; i < total; i++) {
            out[position] = levels[i];
            int run = 0;
            if (i < total - 1 && zerosLeft > 0) {
                int row = Math.min(zerosLeft, 7) - 1;
                run = match(in, H264Tables.RUN_BEFORE_BITS[row], H264Tables.RUN_BEFORE_LEN[row]);
            } else if (i == total - 1) {
                run = zerosLeft;
            }
            zerosLeft -= run;
            position -= run + 1;
        }
        return out;
    }

    private static int match(BitReader in, int[] bits, int[] lengths) {
        int code = 0;
        for (int length = 1; length <= 16; length++) {
            code = (code << 1) | in.bits(1);
            for (int i = 0; i < lengths.length; i++) {
                if (lengths[i] == length && bits[i] == code) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("no code");
    }
}

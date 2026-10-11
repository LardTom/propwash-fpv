package de.lardtom.propwash.video.record.h264;

final class Cavlc {
    static final int CHROMA_DC = -1;
    static final int MAX_LEVEL = 2047;

    private final int[] levels = new int[16];
    private final int[] runs = new int[16];

    int write(BitWriter out, int[] coeffs, int offset, int count, int nC) {
        int total = 0;
        int highest = -1;
        int previous = -1;
        for (int i = count - 1; i >= 0; i--) {
            int c = coeffs[offset + i];
            if (c != 0) {
                if (highest < 0) {
                    highest = i;
                } else {
                    this.runs[total - 1] = previous - i - 1;
                }
                this.levels[total] = c;
                previous = i;
                total++;
            }
        }
        int trailingOnes = 0;
        for (int i = 0; i < total && i < 3; i++) {
            int c = this.levels[i];
            if (c == 1 || c == -1) {
                trailingOnes++;
            } else {
                break;
            }
        }
        int token = total * 4 + trailingOnes;
        if (nC == CHROMA_DC) {
            out.bits(H264Tables.CHROMA_DC_COEFF_TOKEN_BITS[token], H264Tables.CHROMA_DC_COEFF_TOKEN_LEN[token]);
        } else {
            int table = nC < 2 ? 0 : nC < 4 ? 1 : nC < 8 ? 2 : 3;
            out.bits(H264Tables.COEFF_TOKEN_BITS[table][token], H264Tables.COEFF_TOKEN_LEN[table][token]);
        }
        if (total == 0) {
            return 0;
        }
        for (int i = 0; i < trailingOnes; i++) {
            out.bits(this.levels[i] < 0 ? 1 : 0, 1);
        }
        int suffixLength = total > 10 && trailingOnes < 3 ? 1 : 0;
        for (int i = trailingOnes; i < total; i++) {
            int level = this.levels[i];
            int levelCode = level > 0 ? 2 * level - 2 : -2 * level - 1;
            if (i == trailingOnes && trailingOnes < 3) {
                levelCode -= 2;
            }
            if (suffixLength == 0) {
                if (levelCode < 14) {
                    out.bits(1, levelCode + 1);
                } else if (levelCode < 30) {
                    out.bits(1, 15);
                    out.bits(levelCode - 14, 4);
                } else {
                    out.bits(1, 16);
                    out.bits(levelCode - 30, 12);
                }
            } else {
                if (levelCode < (15 << suffixLength)) {
                    out.bits(1, (levelCode >> suffixLength) + 1);
                    out.bits(levelCode & ((1 << suffixLength) - 1), suffixLength);
                } else {
                    out.bits(1, 16);
                    out.bits(levelCode - (15 << suffixLength), 12);
                }
            }
            if (suffixLength == 0) {
                suffixLength = 1;
            }
            int magnitude = Math.abs(level);
            if (magnitude > (3 << (suffixLength - 1)) && suffixLength < 6) {
                suffixLength++;
            }
        }
        int totalZeros = highest + 1 - total;
        if (total < count) {
            if (nC == CHROMA_DC) {
                out.bits(H264Tables.CHROMA_DC_TOTAL_ZEROS_BITS[total - 1][totalZeros],
                        H264Tables.CHROMA_DC_TOTAL_ZEROS_LEN[total - 1][totalZeros]);
            } else {
                out.bits(H264Tables.TOTAL_ZEROS_BITS[total - 1][totalZeros], H264Tables.TOTAL_ZEROS_LEN[total - 1][totalZeros]);
            }
        }
        int zerosLeft = totalZeros;
        for (int i = 0; i < total - 1 && zerosLeft > 0; i++) {
            int run = this.runs[i];
            int row = Math.min(zerosLeft, 7) - 1;
            out.bits(H264Tables.RUN_BEFORE_BITS[row][run], H264Tables.RUN_BEFORE_LEN[row][run]);
            zerosLeft -= run;
        }
        return total;
    }

    static int nC(boolean availableA, int nA, boolean availableB, int nB) {
        if (availableA && availableB) {
            return (nA + nB + 1) >> 1;
        }
        if (availableA) {
            return nA;
        }
        if (availableB) {
            return nB;
        }
        return 0;
    }
}

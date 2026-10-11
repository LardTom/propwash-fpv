package de.lardtom.propwash.video.record.h264;

final class Transform {
    private Transform() {
    }

    static void forward4x4(int[] in, int[] out) {
        for (int i = 0; i < 4; i++) {
            int o = i * 4;
            int s03 = in[o] + in[o + 3];
            int d03 = in[o] - in[o + 3];
            int s12 = in[o + 1] + in[o + 2];
            int d12 = in[o + 1] - in[o + 2];
            out[o] = s03 + s12;
            out[o + 1] = 2 * d03 + d12;
            out[o + 2] = s03 - s12;
            out[o + 3] = d03 - 2 * d12;
        }
        for (int j = 0; j < 4; j++) {
            int a = out[j];
            int b = out[4 + j];
            int c = out[8 + j];
            int d = out[12 + j];
            int s03 = a + d;
            int d03 = a - d;
            int s12 = b + c;
            int d12 = b - c;
            out[j] = s03 + s12;
            out[4 + j] = 2 * d03 + d12;
            out[8 + j] = s03 - s12;
            out[12 + j] = d03 - 2 * d12;
        }
    }

    static void inverse4x4(int[] d, int[] out) {
        for (int i = 0; i < 4; i++) {
            int o = i * 4;
            int e = d[o] + d[o + 2];
            int f = d[o] - d[o + 2];
            int g = (d[o + 1] >> 1) - d[o + 3];
            int h = d[o + 1] + (d[o + 3] >> 1);
            out[o] = e + h;
            out[o + 1] = f + g;
            out[o + 2] = f - g;
            out[o + 3] = e - h;
        }
        for (int j = 0; j < 4; j++) {
            int a = out[j];
            int b = out[4 + j];
            int c = out[8 + j];
            int dd = out[12 + j];
            int e = a + c;
            int f = a - c;
            int g = (b >> 1) - dd;
            int h = b + (dd >> 1);
            out[j] = (e + h + 32) >> 6;
            out[4 + j] = (f + g + 32) >> 6;
            out[8 + j] = (f - g + 32) >> 6;
            out[12 + j] = (e - h + 32) >> 6;
        }
    }

    static void hadamard4x4(int[] in, int[] out) {
        for (int i = 0; i < 4; i++) {
            int o = i * 4;
            int s01 = in[o] + in[o + 1];
            int d01 = in[o] - in[o + 1];
            int s23 = in[o + 2] + in[o + 3];
            int d23 = in[o + 2] - in[o + 3];
            out[o] = s01 + s23;
            out[o + 1] = s01 - s23;
            out[o + 2] = d01 - d23;
            out[o + 3] = d01 + d23;
        }
        for (int j = 0; j < 4; j++) {
            int a = out[j];
            int b = out[4 + j];
            int c = out[8 + j];
            int d = out[12 + j];
            int s01 = a + b;
            int d01 = a - b;
            int s23 = c + d;
            int d23 = c - d;
            out[j] = s01 + s23;
            out[4 + j] = s01 - s23;
            out[8 + j] = d01 - d23;
            out[12 + j] = d01 + d23;
        }
    }

    static int quant(int coef, int mf, int bias, int shift) {
        if (coef == 0) {
            return 0;
        }
        int level = (int) (((long) Math.abs(coef) * mf + bias) >> shift);
        if (level > Cavlc.MAX_LEVEL) {
            level = Cavlc.MAX_LEVEL;
        }
        return coef < 0 ? -level : level;
    }

    static int dequant(int level, int qp, int positionClass) {
        if (level == 0) {
            return 0;
        }
        int scale = 16 * H264Tables.DEQUANT_V[qp % 6][positionClass];
        int q = qp / 6;
        if (qp >= 24) {
            return (level * scale) << (q - 4);
        }
        return (level * scale + (1 << (3 - q))) >> (4 - q);
    }

    static int dequantLumaDc(int f, int qp) {
        int scale = 16 * H264Tables.DEQUANT_V[qp % 6][0];
        int q = qp / 6;
        if (qp >= 36) {
            return (f * scale) << (q - 6);
        }
        return (f * scale + (1 << (5 - q))) >> (6 - q);
    }

    static int dequantChromaDc(int f, int qp) {
        int scale = 16 * H264Tables.DEQUANT_V[qp % 6][0];
        return ((f * scale) << (qp / 6)) >> 5;
    }

    static int decimateScore(int[] zigzag, int start, int end) {
        int index = end - 1;
        while (index >= start && zigzag[index] == 0) {
            index--;
        }
        int score = 0;
        while (index >= start) {
            int level = zigzag[index--];
            if (level > 1 || level < -1) {
                return 9;
            }
            int run = 0;
            while (index >= start && zigzag[index] == 0) {
                index--;
                run++;
            }
            score += H264Tables.DECIMATE_SCORE[run];
        }
        return score;
    }
}

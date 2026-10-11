package de.lardtom.propwash.video.record.h264;

final class Deblocker {
    private Deblocker() {
    }

    static void filterRows(EncodeContext c, int firstRow, int endRow) {
        int[] bs = new int[4];
        for (int mby = firstRow; mby < endRow; mby++) {
            for (int mbx = 0; mbx < c.mbWidth; mbx++) {
                filterMacroblock(c, mbx, mby, bs);
            }
        }
    }

    static void filterMacroblock(EncodeContext c, int mbx, int mby, int[] bs) {
        Picture r = c.recon;
        int s = r.stride;
        int sc = r.strideC;
        int mb = mby * c.mbWidth + mbx;
        int qp = c.mbQp[mb];
        int lumaOrigin = r.origin + mby * 16 * s + mbx * 16;
        int chromaOrigin = r.originC + mby * 8 * sc + mbx * 8;
        for (int edge = 0; edge < 4; edge++) {
            if (edge == 0 && mbx == 0) {
                continue;
            }
            boolean any = false;
            for (int k = 0; k < 4; k++) {
                int value = edge == 0
                        ? strength(c, mb - 1, k * 4 + 3, mb, k * 4, true)
                        : strength(c, mb, k * 4 + edge - 1, mb, k * 4 + edge, false);
                bs[k] = value;
                any |= value != 0;
            }
            if (!any) {
                continue;
            }
            int qpP = edge == 0 ? c.mbQp[mb - 1] : qp;
            edgeFilter(r, lumaOrigin + edge * 4, chromaOrigin + edge * 2, 1, s, (edge & 1) == 0, bs, qpP, qp);
        }
        for (int edge = 0; edge < 4; edge++) {
            if (edge == 0 && mby == 0) {
                continue;
            }
            boolean any = false;
            for (int k = 0; k < 4; k++) {
                int value = edge == 0
                        ? strength(c, mb - c.mbWidth, 12 + k, mb, k, true)
                        : strength(c, mb, (edge - 1) * 4 + k, mb, edge * 4 + k, false);
                bs[k] = value;
                any |= value != 0;
            }
            if (!any) {
                continue;
            }
            int qpP = edge == 0 ? c.mbQp[mb - c.mbWidth] : qp;
            edgeFilter(r, lumaOrigin + edge * 4 * s, chromaOrigin + edge * 2 * sc, s, 1, (edge & 1) == 0, bs, qpP, qp);
        }
    }

    private static void edgeFilter(Picture r, int luma, int chroma, int step, int along, boolean withChroma, int[] bs,
                                   int qpP, int qpQ) {
        int qpAv = (qpP + qpQ + 1) >> 1;
        int alpha = H264Tables.DEBLOCK_ALPHA[qpAv];
        if (alpha != 0) {
            int beta = H264Tables.DEBLOCK_BETA[qpAv];
            int[] tc0 = H264Tables.DEBLOCK_TC0[qpAv];
            for (int i = 0; i < 16; i++) {
                int b = bs[i >> 2];
                if (b != 0) {
                    luma(r.y, luma + i * along, step, b, alpha, beta, tc0);
                }
            }
        }
        if (!withChroma) {
            return;
        }
        int stepC = step == 1 ? 1 : r.strideC;
        int qpC = (H264Tables.chromaQp(qpP) + H264Tables.chromaQp(qpQ) + 1) >> 1;
        int alphaC = H264Tables.DEBLOCK_ALPHA[qpC];
        if (alphaC == 0) {
            return;
        }
        int betaC = H264Tables.DEBLOCK_BETA[qpC];
        int[] tc0C = H264Tables.DEBLOCK_TC0[qpC];
        int alongChroma = step == 1 ? r.strideC : 1;
        for (int i = 0; i < 8; i++) {
            int b = bs[i >> 1];
            if (b != 0) {
                chroma(r.u, chroma + i * alongChroma, stepC, b, alphaC, betaC, tc0C);
                chroma(r.v, chroma + i * alongChroma, stepC, b, alphaC, betaC, tc0C);
            }
        }
    }

    private static int strength(EncodeContext c, int mbP, int blockP, int mbQ, int blockQ, boolean mbEdge) {
        if (c.kind[mbP] == EncodeContext.INTRA || c.kind[mbQ] == EncodeContext.INTRA) {
            return mbEdge ? 4 : 3;
        }
        if (c.nzY[mbP * 16 + blockP] != 0 || c.nzY[mbQ * 16 + blockQ] != 0) {
            return 2;
        }
        if (mbP != mbQ) {
            if (Math.abs(c.mvX[mbP] - c.mvX[mbQ]) >= 4 || Math.abs(c.mvY[mbP] - c.mvY[mbQ]) >= 4) {
                return 1;
            }
        }
        return 0;
    }

    private static void luma(byte[] pl, int q0i, int step, int bs, int alpha, int beta, int[] tc0Row) {
        int p0 = pl[q0i - step] & 0xFF;
        int q0 = pl[q0i] & 0xFF;
        if (Math.abs(p0 - q0) >= alpha) {
            return;
        }
        int p1 = pl[q0i - 2 * step] & 0xFF;
        if (Math.abs(p1 - p0) >= beta) {
            return;
        }
        int q1 = pl[q0i + step] & 0xFF;
        if (Math.abs(q1 - q0) >= beta) {
            return;
        }
        int p2 = pl[q0i - 3 * step] & 0xFF;
        int q2 = pl[q0i + 2 * step] & 0xFF;
        int ap = Math.abs(p2 - p0);
        int aq = Math.abs(q2 - q0);
        if (bs < 4) {
            int tc0 = tc0Row[bs - 1];
            int tc = tc0 + (ap < beta ? 1 : 0) + (aq < beta ? 1 : 0);
            int delta = clip3(-tc, tc, (((q0 - p0) << 2) + (p1 - q1) + 4) >> 3);
            pl[q0i - step] = Picture.clip(p0 + delta);
            pl[q0i] = Picture.clip(q0 - delta);
            if (ap < beta) {
                pl[q0i - 2 * step] = (byte) (p1 + clip3(-tc0, tc0, (p2 + ((p0 + q0 + 1) >> 1) - (p1 << 1)) >> 1));
            }
            if (aq < beta) {
                pl[q0i + step] = (byte) (q1 + clip3(-tc0, tc0, (q2 + ((p0 + q0 + 1) >> 1) - (q1 << 1)) >> 1));
            }
            return;
        }
        boolean strong = Math.abs(p0 - q0) < ((alpha >> 2) + 2);
        if (ap < beta && strong) {
            int p3 = pl[q0i - 4 * step] & 0xFF;
            pl[q0i - step] = (byte) ((p2 + 2 * p1 + 2 * p0 + 2 * q0 + q1 + 4) >> 3);
            pl[q0i - 2 * step] = (byte) ((p2 + p1 + p0 + q0 + 2) >> 2);
            pl[q0i - 3 * step] = (byte) ((2 * p3 + 3 * p2 + p1 + p0 + q0 + 4) >> 3);
        } else {
            pl[q0i - step] = (byte) ((2 * p1 + p0 + q1 + 2) >> 2);
        }
        if (aq < beta && strong) {
            int q3 = pl[q0i + 3 * step] & 0xFF;
            pl[q0i] = (byte) ((p1 + 2 * p0 + 2 * q0 + 2 * q1 + q2 + 4) >> 3);
            pl[q0i + step] = (byte) ((p0 + q0 + q1 + q2 + 2) >> 2);
            pl[q0i + 2 * step] = (byte) ((2 * q3 + 3 * q2 + q1 + q0 + p0 + 4) >> 3);
        } else {
            pl[q0i] = (byte) ((2 * q1 + q0 + p1 + 2) >> 2);
        }
    }

    private static void chroma(byte[] pl, int q0i, int step, int bs, int alpha, int beta, int[] tc0Row) {
        int p0 = pl[q0i - step] & 0xFF;
        int q0 = pl[q0i] & 0xFF;
        if (Math.abs(p0 - q0) >= alpha) {
            return;
        }
        int p1 = pl[q0i - 2 * step] & 0xFF;
        if (Math.abs(p1 - p0) >= beta) {
            return;
        }
        int q1 = pl[q0i + step] & 0xFF;
        if (Math.abs(q1 - q0) >= beta) {
            return;
        }
        if (bs < 4) {
            int tc = tc0Row[bs - 1] + 1;
            int delta = clip3(-tc, tc, (((q0 - p0) << 2) + (p1 - q1) + 4) >> 3);
            pl[q0i - step] = Picture.clip(p0 + delta);
            pl[q0i] = Picture.clip(q0 - delta);
            return;
        }
        pl[q0i - step] = (byte) ((2 * p1 + p0 + q1 + 2) >> 2);
        pl[q0i] = (byte) ((2 * q1 + q0 + p1 + 2) >> 2);
    }

    private static int clip3(int lo, int hi, int value) {
        return value < lo ? lo : value > hi ? hi : value;
    }
}

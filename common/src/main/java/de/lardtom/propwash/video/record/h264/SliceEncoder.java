package de.lardtom.propwash.video.record.h264;

final class SliceEncoder {
    static final int PRESET_QUALITY = 0;
    static final int PRESET_FAST = 1;
    static final int PRESET_FASTEST = 2;

    private static final int MAX_FULL = Picture.PAD - 24;
    private static final int MAX_AQ_DOWN = 10;
    private static final int MAX_AQ_UP = 8;
    private static final int[] LAMBDA = buildLambda();
    private static final int[] A_PLANE = {0, 0, 1, 1, 0, 1, 1, 1, 2, 2, 3, 3, 2, 2, 3, 2};
    private static final int[] A_DX = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    private static final int[] B_PLANE = {-1, 1, -1, 0, 2, 2, 3, 2, -1, 3, -1, 2, 0, 1, 1, 1};
    private static final int[] B_DX = {0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0};
    private static final int[] B_DY = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1};
    private static final int[] DIAMOND_X = {0, 1, 0, -1};
    private static final int[] DIAMOND_Y = {-1, 0, 1, 0};
    private static final int[] SQUARE_X = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] SQUARE_Y = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] CORNER_X = {-1, 1, -1, 1};
    private static final int[] CORNER_Y = {-1, -1, 1, 1};
    private static final int[] HEX_X = {-2, -1, 1, 2, 1, -1};
    private static final int[] HEX_Y = {0, -2, -2, 0, 2, 2};
    private static final int[] GRID_X = {-4, -4, -4, -4, -4, 4, 4, 4, 4, 4, -2, 0, 2, -2, 0, 2};
    private static final int[] GRID_Y = {-2, -1, 0, 1, 2, -2, -1, 0, 1, 2, -3, -4, -3, 3, 4, 3};
    private static final int WIDE_SEARCH_COST = 256 * 5;

    private final EncodeContext c;
    private final Cavlc cavlc = new Cavlc();
    private final BitWriter out = new BitWriter(1 << 16);

    private final int[] src = new int[256];
    private final int[] srcU = new int[64];
    private final int[] srcV = new int[64];
    private final int[] pred = new int[256];
    private final int[] predU = new int[64];
    private final int[] predV = new int[64];
    private final int[][] lumaCandidates = new int[4][256];
    private final int[][] chromaCandidatesU = new int[4][64];
    private final int[][] chromaCandidatesV = new int[4][64];
    private final int[][] lumaLevels = new int[16][16];
    private final int[] dcLevels = new int[16];
    private final int[][] chromaDc = new int[2][4];
    private final int[][][] chromaAc = new int[2][4][16];
    private final int[] block = new int[16];
    private final int[] coef = new int[16];
    private final int[] work = new int[16];
    private final int[] dcMatrix = new int[16];
    private final int[] dcWork = new int[16];
    private final int[] chromaDcCoef = new int[4];
    private final Intra4x4 intra = Intra4x4.create();
    private final int[] intraPred = new int[16];
    private final int[] intraModes = new int[16];
    private final int[] intraPredicted = new int[16];

    private int firstRow;
    private int qp;
    private int lambda;
    private int lastQp;
    private int skipRun;
    private int cbpLuma;
    private int cbpChroma;
    private int lumaMode;
    private int chromaMode;
    private int mvpX;
    private int mvpY;
    private int skipX;
    private int skipY;
    private int bestX;
    private int bestY;
    private int bestCost;
    private int bestFx;
    private int bestFy;
    private final int[] candidatesX = new int[12];
    private final int[] candidatesY = new int[12];
    private boolean availLeft;
    private boolean availTop;
    private boolean availTopRight;
    private boolean availTopLeft;

    SliceEncoder(EncodeContext context) {
        this.c = context;
    }

    byte[] encode(int firstRow, int endRow) {
        this.firstRow = firstRow;
        this.out.reset();
        this.writeHeader(firstRow * this.c.mbWidth);
        this.skipRun = 0;
        this.lastQp = this.c.qp;
        for (int mby = firstRow; mby < endRow; mby++) {
            for (int mbx = 0; mbx < this.c.mbWidth; mbx++) {
                this.encodeMacroblock(mbx, mby);
            }
        }
        if (this.skipRun > 0) {
            this.out.ue(this.skipRun);
        }
        this.out.trailing();
        int refIdc = this.c.idr ? 3 : 2;
        return Nal.wrap(refIdc, this.c.idr ? Nal.IDR : Nal.SLICE, this.out);
    }

    private void writeHeader(int firstMb) {
        BitWriter w = this.out;
        w.ue(firstMb);
        w.ue(this.c.intra ? 2 : 0);
        w.ue(0);
        w.bits(this.c.frameNum, this.c.log2MaxFrameNum);
        if (this.c.idr) {
            w.ue(this.c.idrPicId);
        }
        if (!this.c.intra) {
            w.bits(0, 1);
            w.bits(0, 1);
        }
        if (this.c.idr) {
            w.bits(0, 1);
            w.bits(0, 1);
        } else {
            w.bits(0, 1);
        }
        w.se(this.c.qp - 26);
        if (this.c.deblock) {
            w.ue(0);
            w.se(0);
            w.se(0);
        } else {
            w.ue(1);
        }
    }

    private void encodeMacroblock(int mbx, int mby) {
        int mb = mby * this.c.mbWidth + mbx;
        this.availLeft = mbx > 0;
        this.availTop = mby > this.firstRow;
        this.availTopRight = this.availTop && mbx + 1 < this.c.mbWidth;
        this.availTopLeft = this.availTop && mbx > 0;
        this.loadSource(mbx, mby);
        this.qp = this.adaptiveQp();
        this.lambda = LAMBDA[this.qp];
        if (this.c.intra) {
            int i16 = this.chooseIntraLuma(mbx, mby) + this.lambda * 4;
            this.chooseIntraChroma(mbx, mby);
            int i4 = this.c.preset == PRESET_FASTEST ? Integer.MAX_VALUE : this.tryIntra4x4(mbx, mby, mb, i16);
            if (i4 < i16) {
                this.codeIntra4x4(mbx, mby, mb, false);
            } else {
                this.codeIntra(mbx, mby, mb, false);
            }
            return;
        }
        this.predictMotion(mbx, mby);
        int px = mbx * 16;
        int py = mby * 16;
        this.mcLuma(px, py, this.skipX, this.skipY, this.pred);
        this.mcChroma(mbx * 8, mby * 8, this.skipX, this.skipY);
        int skipSad = sad(this.src, this.pred, Integer.MAX_VALUE);
        int threshold = Math.max(64, this.lambda * 24);
        if (skipSad < threshold * 4 || this.c.preset == PRESET_FASTEST) {
            this.transformInter();
            if (this.cbpLuma == 0 && this.cbpChroma == 0) {
                this.commitSkip(mb, px, py);
                return;
            }
        }
        this.motionSearch(px, py);
        int interCost = this.bestCost;
        int intraCost = Integer.MAX_VALUE;
        int intraFloor = this.c.preset == PRESET_FASTEST ? 256 * 24 : 256 * 3 + this.lambda * 16;
        if (interCost > intraFloor) {
            intraCost = this.chooseIntraLuma(mbx, mby) + this.lambda * 10;
        }
        if (intraCost < interCost) {
            this.chooseIntraChroma(mbx, mby);
            this.flushSkipRun();
            this.codeIntra(mbx, mby, mb, true);
            return;
        }
        this.mcLuma(px, py, this.bestX, this.bestY, this.pred);
        this.mcChroma(mbx * 8, mby * 8, this.bestX, this.bestY);
        this.transformInter();
        if (this.cbpLuma == 0 && this.cbpChroma == 0 && this.bestX == this.skipX && this.bestY == this.skipY) {
            this.commitSkip(mb, px, py);
            return;
        }
        this.flushSkipRun();
        this.codeInter(mbx, mby, mb);
    }

    private void writeQpDelta(int mb) {
        this.out.se(this.qp - this.lastQp);
        this.lastQp = this.qp;
        this.c.mbQp[mb] = (byte) this.qp;
    }

    private int adaptiveQp() {
        int base = this.c.qp;
        double strength = this.c.adaptiveQuant;
        if (strength <= 0.0) {
            return base;
        }
        int[] y = this.src;
        long sum = 0L;
        long squares = 0L;
        for (int i = 0; i < 256; i++) {
            int v = y[i];
            sum += v;
            squares += v * v;
        }
        double energy = squares - (double) (sum * sum) / 256.0;
        energy += variance64(this.srcU) + variance64(this.srcV);
        double offset = strength * 1.0397 * (Math.log(energy + 1.0) * 1.4426950408889634 - 14.427);
        double mean = sum / 256.0;
        double dark = Math.max(0.0, Math.min(1.0, (64.0 - mean) / 40.0));
        offset -= 2.0 * strength * dark;
        int delta = (int) Math.round(Math.max(-MAX_AQ_DOWN, Math.min(MAX_AQ_UP, offset)));
        return Math.max(this.c.minQp, Math.min(this.c.maxQp, base + delta));
    }

    private static double variance64(int[] block) {
        long sum = 0L;
        long squares = 0L;
        for (int i = 0; i < 64; i++) {
            int v = block[i];
            sum += v;
            squares += v * v;
        }
        return squares - (double) (sum * sum) / 64.0;
    }

    private static int[] buildLambda() {
        int[] table = new int[52];
        for (int qp = 0; qp < 52; qp++) {
            table[qp] = H264Encoder.lambdaFor(qp);
        }
        return table;
    }

    private void flushSkipRun() {
        this.out.ue(this.skipRun);
        this.skipRun = 0;
    }

    private void loadSource(int mbx, int mby) {
        int w = this.c.width;
        int p = mby * 16 * w + mbx * 16;
        byte[] y = this.c.srcY;
        for (int row = 0; row < 16; row++) {
            int o = row * 16;
            int q = p + row * w;
            for (int col = 0; col < 16; col++) {
                this.src[o + col] = y[q + col] & 0xFF;
            }
        }
        int wc = w / 2;
        int pc = mby * 8 * wc + mbx * 8;
        byte[] u = this.c.srcU;
        byte[] v = this.c.srcV;
        for (int row = 0; row < 8; row++) {
            int o = row * 8;
            int q = pc + row * wc;
            for (int col = 0; col < 8; col++) {
                this.srcU[o + col] = u[q + col] & 0xFF;
                this.srcV[o + col] = v[q + col] & 0xFF;
            }
        }
    }

    private static int sad(int[] a, int[] b, int limit) {
        int sum = 0;
        for (int row = 0; row < 16; row++) {
            int o = row * 16;
            for (int col = 0; col < 16; col++) {
                sum += Math.abs(a[o + col] - b[o + col]);
            }
            if (sum >= limit) {
                return sum;
            }
        }
        return sum;
    }

    private static int sad64(int[] a, int[] b) {
        int sum = 0;
        for (int i = 0; i < 64; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return sum;
    }

    private int chooseIntraLuma(int mbx, int mby) {
        int best = Integer.MAX_VALUE;
        int bestMode = 2;
        for (int mode = 0; mode < 4; mode++) {
            if (!this.lumaModeAvailable(mode)) {
                continue;
            }
            this.predictLuma16(mode, mbx * 16, mby * 16, this.lumaCandidates[mode]);
            int cost = sad(this.src, this.lumaCandidates[mode], best);
            if (cost < best) {
                best = cost;
                bestMode = mode;
            }
        }
        this.lumaMode = bestMode;
        return best;
    }

    private boolean lumaModeAvailable(int mode) {
        return switch (mode) {
            case 0 -> this.availTop;
            case 1 -> this.availLeft;
            case 2 -> true;
            default -> this.availTop && this.availLeft && this.availTopLeft;
        };
    }

    private boolean chromaModeAvailable(int mode) {
        return switch (mode) {
            case 0 -> true;
            case 1 -> this.availLeft;
            case 2 -> this.availTop;
            default -> this.availTop && this.availLeft && this.availTopLeft;
        };
    }

    private void chooseIntraChroma(int mbx, int mby) {
        int best = Integer.MAX_VALUE;
        int bestMode = 0;
        for (int mode = 0; mode < 4; mode++) {
            if (!this.chromaModeAvailable(mode)) {
                continue;
            }
            this.predictChroma8(mode, mbx * 8, mby * 8, this.c.recon.u, this.chromaCandidatesU[mode]);
            this.predictChroma8(mode, mbx * 8, mby * 8, this.c.recon.v, this.chromaCandidatesV[mode]);
            int cost = sad64(this.srcU, this.chromaCandidatesU[mode]) + sad64(this.srcV, this.chromaCandidatesV[mode]);
            if (cost < best) {
                best = cost;
                bestMode = mode;
            }
        }
        this.chromaMode = bestMode;
    }

    private void predictLuma16(int mode, int px, int py, int[] dst) {
        Picture r = this.c.recon;
        byte[] y = r.y;
        int s = r.stride;
        int o = r.origin + py * s + px;
        switch (mode) {
            case 0 -> {
                for (int col = 0; col < 16; col++) {
                    int value = y[o - s + col] & 0xFF;
                    for (int row = 0; row < 16; row++) {
                        dst[row * 16 + col] = value;
                    }
                }
            }
            case 1 -> {
                for (int row = 0; row < 16; row++) {
                    int value = y[o + row * s - 1] & 0xFF;
                    for (int col = 0; col < 16; col++) {
                        dst[row * 16 + col] = value;
                    }
                }
            }
            case 2 -> {
                int dc;
                int sumTop = 0;
                int sumLeft = 0;
                if (this.availTop) {
                    for (int i = 0; i < 16; i++) {
                        sumTop += y[o - s + i] & 0xFF;
                    }
                }
                if (this.availLeft) {
                    for (int i = 0; i < 16; i++) {
                        sumLeft += y[o + i * s - 1] & 0xFF;
                    }
                }
                if (this.availTop && this.availLeft) {
                    dc = (sumTop + sumLeft + 16) >> 5;
                } else if (this.availLeft) {
                    dc = (sumLeft + 8) >> 4;
                } else if (this.availTop) {
                    dc = (sumTop + 8) >> 4;
                } else {
                    dc = 128;
                }
                java.util.Arrays.fill(dst, 0, 256, dc);
            }
            default -> {
                int h = 0;
                int v = 0;
                for (int i = 0; i < 8; i++) {
                    h += (i + 1) * ((y[o - s + 8 + i] & 0xFF) - (y[o - s + 6 - i] & 0xFF));
                    v += (i + 1) * ((y[o + (8 + i) * s - 1] & 0xFF) - (y[o + (6 - i) * s - 1] & 0xFF));
                }
                int a = 16 * ((y[o + 15 * s - 1] & 0xFF) + (y[o - s + 15] & 0xFF));
                int b = (5 * h + 32) >> 6;
                int cc = (5 * v + 32) >> 6;
                for (int row = 0; row < 16; row++) {
                    for (int col = 0; col < 16; col++) {
                        dst[row * 16 + col] = clip((a + b * (col - 7) + cc * (row - 7) + 16) >> 5);
                    }
                }
            }
        }
    }

    private void predictChroma8(int mode, int cx, int cy, byte[] plane, int[] dst) {
        Picture r = this.c.recon;
        int s = r.strideC;
        int o = r.originC + cy * s + cx;
        switch (mode) {
            case 0 -> {
                for (int by = 0; by < 2; by++) {
                    for (int bx = 0; bx < 2; bx++) {
                        int sumTop = 0;
                        int sumLeft = 0;
                        for (int i = 0; i < 4; i++) {
                            if (this.availTop) {
                                sumTop += plane[o - s + bx * 4 + i] & 0xFF;
                            }
                            if (this.availLeft) {
                                sumLeft += plane[o + (by * 4 + i) * s - 1] & 0xFF;
                            }
                        }
                        int dc;
                        if (bx == by) {
                            if (this.availTop && this.availLeft) {
                                dc = (sumTop + sumLeft + 4) >> 3;
                            } else if (this.availLeft) {
                                dc = (sumLeft + 2) >> 2;
                            } else if (this.availTop) {
                                dc = (sumTop + 2) >> 2;
                            } else {
                                dc = 128;
                            }
                        } else if (bx == 1) {
                            if (this.availTop) {
                                dc = (sumTop + 2) >> 2;
                            } else if (this.availLeft) {
                                dc = (sumLeft + 2) >> 2;
                            } else {
                                dc = 128;
                            }
                        } else {
                            if (this.availLeft) {
                                dc = (sumLeft + 2) >> 2;
                            } else if (this.availTop) {
                                dc = (sumTop + 2) >> 2;
                            } else {
                                dc = 128;
                            }
                        }
                        for (int row = 0; row < 4; row++) {
                            for (int col = 0; col < 4; col++) {
                                dst[(by * 4 + row) * 8 + bx * 4 + col] = dc;
                            }
                        }
                    }
                }
            }
            case 1 -> {
                for (int row = 0; row < 8; row++) {
                    int value = plane[o + row * s - 1] & 0xFF;
                    for (int col = 0; col < 8; col++) {
                        dst[row * 8 + col] = value;
                    }
                }
            }
            case 2 -> {
                for (int col = 0; col < 8; col++) {
                    int value = plane[o - s + col] & 0xFF;
                    for (int row = 0; row < 8; row++) {
                        dst[row * 8 + col] = value;
                    }
                }
            }
            default -> {
                int h = 0;
                int v = 0;
                for (int i = 0; i < 4; i++) {
                    h += (i + 1) * ((plane[o - s + 4 + i] & 0xFF) - (plane[o - s + 2 - i] & 0xFF));
                    v += (i + 1) * ((plane[o + (4 + i) * s - 1] & 0xFF) - (plane[o + (2 - i) * s - 1] & 0xFF));
                }
                int a = 16 * ((plane[o + 7 * s - 1] & 0xFF) + (plane[o - s + 7] & 0xFF));
                int b = (34 * h + 32) >> 6;
                int cc = (34 * v + 32) >> 6;
                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        dst[row * 8 + col] = clip((a + b * (col - 3) + cc * (row - 3) + 16) >> 5);
                    }
                }
            }
        }
    }

    private void codeIntra(int mbx, int mby, int mb, boolean inPSlice) {
        int[] lumaPred = this.lumaCandidates[this.lumaMode];
        if (lumaPred != this.pred) {
            System.arraycopy(lumaPred, 0, this.pred, 0, 256);
        }
        System.arraycopy(this.chromaCandidatesU[this.chromaMode], 0, this.predU, 0, 64);
        System.arraycopy(this.chromaCandidatesV[this.chromaMode], 0, this.predV, 0, 64);
        int qp = this.qp;
        int qbits = 15 + qp / 6;
        int[] mf = H264Tables.QUANT_MF[qp % 6];
        int bias = (1 << qbits) / 3;
        boolean anyAc = false;
        for (int blk = 0; blk < 16; blk++) {
            int bx = blk & 3;
            int by = blk >> 2;
            this.loadResidualBlock(this.src, this.pred, 16, bx * 4, by * 4);
            Transform.forward4x4(this.block, this.coef);
            this.dcMatrix[blk] = this.coef[0];
            int[] levels = this.lumaLevels[blk];
            levels[0] = 0;
            for (int k = 1; k < 16; k++) {
                int pos = H264Tables.ZIGZAG[k];
                int level = Transform.quant(this.coef[pos], mf[H264Tables.POSITION_CLASS[pos]], bias, qbits);
                levels[k] = level;
                if (level != 0) {
                    anyAc = true;
                }
            }
        }
        Transform.hadamard4x4(this.dcMatrix, this.dcWork);
        int dcBias = (1 << (qbits + 1)) / 3;
        for (int k = 0; k < 16; k++) {
            int pos = H264Tables.ZIGZAG[k];
            int value = (this.dcWork[pos] + 1) >> 1;
            this.dcLevels[k] = Transform.quant(value, mf[0], dcBias, qbits + 1);
        }
        this.cbpLuma = anyAc ? 15 : 0;
        if (!anyAc) {
            for (int blk = 0; blk < 16; blk++) {
                java.util.Arrays.fill(this.lumaLevels[blk], 0);
            }
        }
        this.reconstructIntraLuma(mbx, mby, qp);
        int qpc = H264Tables.chromaQp(qp);
        this.cbpChroma = this.transformChroma(qpc, true);
        this.reconstructChroma(mbx, mby, qpc);

        int mbType = 1 + this.lumaMode + 4 * this.cbpChroma + (this.cbpLuma == 15 ? 12 : 0);
        this.out.ue(inPSlice ? mbType + 5 : mbType);
        this.out.ue(this.chromaMode);
        this.writeQpDelta(mb);
        int nC = this.lumaNc(mb, mbx, 0, 0);
        this.cavlc.write(this.out, this.dcLevels, 0, 16, nC);
        byte[] nzY = this.c.nzY;
        int base = mb * 16;
        for (int idx = 0; idx < 16; idx++) {
            int bx = H264Tables.BLOCK_X[idx];
            int by = H264Tables.BLOCK_Y[idx];
            if (this.cbpLuma == 0) {
                nzY[base + by * 4 + bx] = 0;
                continue;
            }
            int n = this.lumaNc(mb, mbx, bx, by);
            int total = this.cavlc.write(this.out, this.lumaLevels[by * 4 + bx], 1, 15, n);
            nzY[base + by * 4 + bx] = (byte) total;
        }
        this.writeChroma(mb, mbx);
        this.c.kind[mb] = EncodeContext.INTRA;
        this.c.intra4x4[mb] = 0;
        this.c.mvX[mb] = 0;
        this.c.mvY[mb] = 0;
    }

    private static boolean topRightInside(int idx) {
        return idx != 3 && idx != 7 && idx != 11 && idx != 13 && idx != 15;
    }

    private int predictedIntraMode(int mb, int bx, int by) {
        boolean haveA = bx > 0 || this.availLeft;
        boolean haveB = by > 0 || this.availTop;
        if (!haveA || !haveB) {
            return Intra4x4.DC;
        }
        int modeA;
        if (bx > 0) {
            modeA = this.intraModes[by * 4 + bx - 1];
        } else {
            modeA = this.c.intra4x4[mb - 1] != 0 ? this.c.intraModes[(mb - 1) * 16 + by * 4 + 3] : Intra4x4.DC;
        }
        int modeB;
        if (by > 0) {
            modeB = this.intraModes[(by - 1) * 4 + bx];
        } else {
            int above = mb - this.c.mbWidth;
            modeB = this.c.intra4x4[above] != 0 ? this.c.intraModes[above * 16 + 12 + bx] : Intra4x4.DC;
        }
        return Math.min(modeA, modeB);
    }

    private int tryIntra4x4(int mbx, int mby, int mb, int limit) {
        Picture r = this.c.recon;
        int s = r.stride;
        int qp = this.qp;
        int qbits = 15 + qp / 6;
        int[] mf = H264Tables.QUANT_MF[qp % 6];
        int bias = (1 << qbits) / 3;
        int lambda = this.lambda;
        Intra4x4 p = this.intra;
        int total = 0;
        for (int idx = 0; idx < 16; idx++) {
            int bx = H264Tables.BLOCK_X[idx];
            int by = H264Tables.BLOCK_Y[idx];
            int o = r.origin + (mby * 16 + by * 4) * s + mbx * 16 + bx * 4;
            p.hasTop = by > 0 || this.availTop;
            p.hasLeft = bx > 0 || this.availLeft;
            p.hasCorner = bx > 0 && by > 0 || bx == 0 && by > 0 && this.availLeft || by == 0 && bx > 0 && this.availTop
                    || bx == 0 && by == 0 && this.availTopLeft;
            boolean topRight;
            if (by == 0 && bx == 3) {
                topRight = this.availTopRight;
            } else if (by == 0) {
                topRight = this.availTop;
            } else {
                topRight = topRightInside(idx);
            }
            if (p.hasTop) {
                for (int k = 0; k < 4; k++) {
                    p.top[k] = r.y[o - s + k] & 0xFF;
                }
                for (int k = 4; k < 8; k++) {
                    p.top[k] = topRight ? r.y[o - s + k] & 0xFF : p.top[3];
                }
            }
            if (p.hasLeft) {
                for (int k = 0; k < 4; k++) {
                    p.left[k] = r.y[o + k * s - 1] & 0xFF;
                }
            }
            if (p.hasCorner) {
                p.corner = r.y[o - s - 1] & 0xFF;
            }
            int predicted = this.predictedIntraMode(mb, bx, by);
            int bestMode = Intra4x4.DC;
            int bestCost = Integer.MAX_VALUE;
            for (int mode = 0; mode < Intra4x4.MODES; mode++) {
                if (!p.available(mode)) {
                    continue;
                }
                p.predict(mode, this.block);
                int cost = lambda * (mode == predicted ? 1 : 4);
                for (int row = 0; row < 4; row++) {
                    int so = (by * 4 + row) * 16 + bx * 4;
                    for (int col = 0; col < 4; col++) {
                        cost += Math.abs(this.src[so + col] - this.block[row * 4 + col]);
                    }
                }
                if (cost < bestCost) {
                    bestCost = cost;
                    bestMode = mode;
                    System.arraycopy(this.block, 0, this.intraPred, 0, 16);
                }
            }
            total += bestCost;
            if (total >= limit) {
                return Integer.MAX_VALUE;
            }
            int raster = by * 4 + bx;
            this.intraModes[raster] = bestMode;
            this.intraPredicted[raster] = predicted;
            for (int row = 0; row < 4; row++) {
                int so = (by * 4 + row) * 16 + bx * 4;
                for (int col = 0; col < 4; col++) {
                    this.block[row * 4 + col] = this.src[so + col] - this.intraPred[row * 4 + col];
                }
            }
            Transform.forward4x4(this.block, this.coef);
            int[] levels = this.lumaLevels[raster];
            for (int k = 0; k < 16; k++) {
                int pos = H264Tables.ZIGZAG[k];
                levels[k] = Transform.quant(this.coef[pos], mf[H264Tables.POSITION_CLASS[pos]], bias, qbits);
            }
            for (int k = 0; k < 16; k++) {
                int pos = H264Tables.ZIGZAG[k];
                this.work[pos] = Transform.dequant(levels[k], qp, H264Tables.POSITION_CLASS[pos]);
            }
            Transform.inverse4x4(this.work, this.coef);
            for (int row = 0; row < 4; row++) {
                int q = o + row * s;
                for (int col = 0; col < 4; col++) {
                    r.y[q + col] = Picture.clip(this.intraPred[row * 4 + col] + this.coef[row * 4 + col]);
                }
            }
        }
        return total;
    }

    private void codeIntra4x4(int mbx, int mby, int mb, boolean inPSlice) {
        System.arraycopy(this.chromaCandidatesU[this.chromaMode], 0, this.predU, 0, 64);
        System.arraycopy(this.chromaCandidatesV[this.chromaMode], 0, this.predV, 0, 64);
        int cbp = 0;
        for (int b8 = 0; b8 < 4; b8++) {
            boolean any = false;
            for (int sub = 0; sub < 4 && !any; sub++) {
                int idx = b8 * 4 + sub;
                int[] levels = this.lumaLevels[H264Tables.BLOCK_Y[idx] * 4 + H264Tables.BLOCK_X[idx]];
                for (int k = 0; k < 16; k++) {
                    if (levels[k] != 0) {
                        any = true;
                        break;
                    }
                }
            }
            if (any) {
                cbp |= 1 << b8;
            }
        }
        this.cbpLuma = cbp;
        int qpc = H264Tables.chromaQp(this.qp);
        this.cbpChroma = this.transformChroma(qpc, true);
        this.reconstructChroma(mbx, mby, qpc);
        this.out.ue(inPSlice ? 5 : 0);
        for (int idx = 0; idx < 16; idx++) {
            int raster = H264Tables.BLOCK_Y[idx] * 4 + H264Tables.BLOCK_X[idx];
            int mode = this.intraModes[raster];
            int predicted = this.intraPredicted[raster];
            if (mode == predicted) {
                this.out.bits(1, 1);
            } else {
                this.out.bits(0, 1);
                this.out.bits(mode < predicted ? mode : mode - 1, 3);
            }
        }
        this.out.ue(this.chromaMode);
        int pattern = this.cbpLuma | (this.cbpChroma << 4);
        this.out.ue(H264Tables.INTRA_CBP_TO_CODE[pattern]);
        byte[] nzY = this.c.nzY;
        int base = mb * 16;
        if (pattern != 0) {
            this.writeQpDelta(mb);
            for (int idx = 0; idx < 16; idx++) {
                int bx = H264Tables.BLOCK_X[idx];
                int by = H264Tables.BLOCK_Y[idx];
                if ((this.cbpLuma & (1 << (idx >> 2))) == 0) {
                    nzY[base + by * 4 + bx] = 0;
                    continue;
                }
                int n = this.lumaNc(mb, mbx, bx, by);
                int total = this.cavlc.write(this.out, this.lumaLevels[by * 4 + bx], 0, 16, n);
                nzY[base + by * 4 + bx] = (byte) total;
            }
            this.writeChroma(mb, mbx);
        } else {
            java.util.Arrays.fill(nzY, base, base + 16, (byte) 0);
            java.util.Arrays.fill(this.c.nzU, mb * 4, mb * 4 + 4, (byte) 0);
            java.util.Arrays.fill(this.c.nzV, mb * 4, mb * 4 + 4, (byte) 0);
            this.c.mbQp[mb] = (byte) this.lastQp;
        }
        this.c.kind[mb] = EncodeContext.INTRA;
        this.c.intra4x4[mb] = 1;
        for (int i = 0; i < 16; i++) {
            this.c.intraModes[mb * 16 + i] = (byte) this.intraModes[i];
        }
        this.c.mvX[mb] = 0;
        this.c.mvY[mb] = 0;
    }

    private void codeInter(int mbx, int mby, int mb) {
        int cbp = this.cbpLuma | (this.cbpChroma << 4);
        this.out.ue(0);
        this.out.se(this.bestX - this.mvpX);
        this.out.se(this.bestY - this.mvpY);
        this.out.ue(H264Tables.INTER_CBP_TO_CODE[cbp]);
        byte[] nzY = this.c.nzY;
        int base = mb * 16;
        if (cbp != 0) {
            this.writeQpDelta(mb);
            for (int idx = 0; idx < 16; idx++) {
                int bx = H264Tables.BLOCK_X[idx];
                int by = H264Tables.BLOCK_Y[idx];
                int b8 = idx >> 2;
                if ((this.cbpLuma & (1 << b8)) == 0) {
                    nzY[base + by * 4 + bx] = 0;
                    continue;
                }
                int n = this.lumaNc(mb, mbx, bx, by);
                int total = this.cavlc.write(this.out, this.lumaLevels[by * 4 + bx], 0, 16, n);
                nzY[base + by * 4 + bx] = (byte) total;
            }
            this.writeChroma(mb, mbx);
        } else {
            java.util.Arrays.fill(nzY, base, base + 16, (byte) 0);
            java.util.Arrays.fill(this.c.nzU, mb * 4, mb * 4 + 4, (byte) 0);
            java.util.Arrays.fill(this.c.nzV, mb * 4, mb * 4 + 4, (byte) 0);
            this.c.mbQp[mb] = (byte) this.lastQp;
        }
        this.reconstructInter(mbx, mby);
        this.c.kind[mb] = EncodeContext.INTER;
        this.c.intra4x4[mb] = 0;
        this.c.mvX[mb] = (short) this.bestX;
        this.c.mvY[mb] = (short) this.bestY;
    }

    private void commitSkip(int mb, int px, int py) {
        this.skipRun++;
        Picture r = this.c.recon;
        int s = r.stride;
        int o = r.origin + py * s + px;
        for (int row = 0; row < 16; row++) {
            int q = o + row * s;
            int p = row * 16;
            for (int col = 0; col < 16; col++) {
                r.y[q + col] = (byte) this.pred[p + col];
            }
        }
        int sc = r.strideC;
        int oc = r.originC + (py / 2) * sc + px / 2;
        for (int row = 0; row < 8; row++) {
            int q = oc + row * sc;
            int p = row * 8;
            for (int col = 0; col < 8; col++) {
                r.u[q + col] = (byte) this.predU[p + col];
                r.v[q + col] = (byte) this.predV[p + col];
            }
        }
        java.util.Arrays.fill(this.c.nzY, mb * 16, mb * 16 + 16, (byte) 0);
        java.util.Arrays.fill(this.c.nzU, mb * 4, mb * 4 + 4, (byte) 0);
        java.util.Arrays.fill(this.c.nzV, mb * 4, mb * 4 + 4, (byte) 0);
        this.c.kind[mb] = EncodeContext.SKIP;
        this.c.mbQp[mb] = (byte) this.lastQp;
        this.c.intra4x4[mb] = 0;
        this.c.mvX[mb] = (short) this.skipX;
        this.c.mvY[mb] = (short) this.skipY;
    }

    private void writeChroma(int mb, int mbx) {
        if (this.cbpChroma > 0) {
            this.cavlc.write(this.out, this.chromaDc[0], 0, 4, Cavlc.CHROMA_DC);
            this.cavlc.write(this.out, this.chromaDc[1], 0, 4, Cavlc.CHROMA_DC);
        }
        for (int plane = 0; plane < 2; plane++) {
            byte[] nz = plane == 0 ? this.c.nzU : this.c.nzV;
            for (int blk = 0; blk < 4; blk++) {
                if (this.cbpChroma < 2) {
                    nz[mb * 4 + blk] = 0;
                    continue;
                }
                int n = this.chromaNc(nz, mb, mbx, blk & 1, blk >> 1);
                int total = this.cavlc.write(this.out, this.chromaAc[plane][blk], 1, 15, n);
                nz[mb * 4 + blk] = (byte) total;
            }
        }
    }

    private int lumaNc(int mb, int mbx, int bx, int by) {
        byte[] nz = this.c.nzY;
        boolean haveA;
        int nA = 0;
        if (bx > 0) {
            haveA = true;
            nA = nz[mb * 16 + by * 4 + bx - 1];
        } else {
            haveA = this.availLeft;
            if (haveA) {
                nA = nz[(mb - 1) * 16 + by * 4 + 3];
            }
        }
        boolean haveB;
        int nB = 0;
        if (by > 0) {
            haveB = true;
            nB = nz[mb * 16 + (by - 1) * 4 + bx];
        } else {
            haveB = this.availTop;
            if (haveB) {
                nB = nz[(mb - this.c.mbWidth) * 16 + 12 + bx];
            }
        }
        return Cavlc.nC(haveA, nA, haveB, nB);
    }

    private int chromaNc(byte[] nz, int mb, int mbx, int bx, int by) {
        boolean haveA;
        int nA = 0;
        if (bx > 0) {
            haveA = true;
            nA = nz[mb * 4 + by * 2];
        } else {
            haveA = this.availLeft;
            if (haveA) {
                nA = nz[(mb - 1) * 4 + by * 2 + 1];
            }
        }
        boolean haveB;
        int nB = 0;
        if (by > 0) {
            haveB = true;
            nB = nz[mb * 4 + bx];
        } else {
            haveB = this.availTop;
            if (haveB) {
                nB = nz[(mb - this.c.mbWidth) * 4 + 2 + bx];
            }
        }
        return Cavlc.nC(haveA, nA, haveB, nB);
    }

    private void loadResidualBlock(int[] source, int[] prediction, int stride, int x, int y) {
        for (int row = 0; row < 4; row++) {
            int o = (y + row) * stride + x;
            for (int col = 0; col < 4; col++) {
                this.block[row * 4 + col] = source[o + col] - prediction[o + col];
            }
        }
    }

    private void reconstructIntraLuma(int mbx, int mby, int qp) {
        for (int k = 0; k < 16; k++) {
            this.dcMatrix[H264Tables.ZIGZAG[k]] = this.dcLevels[k];
        }
        Transform.hadamard4x4(this.dcMatrix, this.dcWork);
        for (int blk = 0; blk < 16; blk++) {
            int[] levels = this.lumaLevels[blk];
            for (int k = 1; k < 16; k++) {
                int pos = H264Tables.ZIGZAG[k];
                this.work[pos] = Transform.dequant(levels[k], qp, H264Tables.POSITION_CLASS[pos]);
            }
            this.work[0] = Transform.dequantLumaDc(this.dcWork[blk], qp);
            Transform.inverse4x4(this.work, this.coef);
            this.storeLumaBlock(mbx, mby, blk & 3, blk >> 2);
        }
    }

    private void storeLumaBlock(int mbx, int mby, int bx, int by) {
        Picture r = this.c.recon;
        int s = r.stride;
        int o = r.origin + (mby * 16 + by * 4) * s + mbx * 16 + bx * 4;
        for (int row = 0; row < 4; row++) {
            int p = (by * 4 + row) * 16 + bx * 4;
            int q = o + row * s;
            for (int col = 0; col < 4; col++) {
                r.y[q + col] = Picture.clip(this.pred[p + col] + this.coef[row * 4 + col]);
            }
        }
    }

    private int transformChroma(int qpc, boolean intra) {
        int qbits = 15 + qpc / 6;
        int[] mf = H264Tables.QUANT_MF[qpc % 6];
        int bias = intra ? (1 << qbits) / 3 : (1 << qbits) / 6;
        int dcBias = intra ? (1 << (qbits + 1)) / 3 : (1 << (qbits + 1)) / 6;
        boolean anyDc = false;
        boolean anyAc = false;
        for (int plane = 0; plane < 2; plane++) {
            int[] source = plane == 0 ? this.srcU : this.srcV;
            int[] prediction = plane == 0 ? this.predU : this.predV;
            boolean planeAc = false;
            int score = 0;
            for (int blk = 0; blk < 4; blk++) {
                int bx = blk & 1;
                int by = blk >> 1;
                this.loadResidualBlock(source, prediction, 8, bx * 4, by * 4);
                Transform.forward4x4(this.block, this.coef);
                this.chromaDcCoef[blk] = this.coef[0];
                int[] levels = this.chromaAc[plane][blk];
                levels[0] = 0;
                for (int k = 1; k < 16; k++) {
                    int pos = H264Tables.ZIGZAG[k];
                    int level = Transform.quant(this.coef[pos], mf[H264Tables.POSITION_CLASS[pos]], bias, qbits);
                    levels[k] = level;
                    if (level != 0) {
                        planeAc = true;
                    }
                }
                if (!intra) {
                    score += Transform.decimateScore(levels, 1, 16);
                }
            }
            if (planeAc && !intra && score < 7) {
                for (int blk = 0; blk < 4; blk++) {
                    java.util.Arrays.fill(this.chromaAc[plane][blk], 0);
                }
                planeAc = false;
            }
            anyAc |= planeAc;
            int a = this.chromaDcCoef[0];
            int b = this.chromaDcCoef[1];
            int cc = this.chromaDcCoef[2];
            int d = this.chromaDcCoef[3];
            int r0 = a + b;
            int r1 = a - b;
            int r2 = cc + d;
            int r3 = cc - d;
            int[] dc = this.chromaDc[plane];
            dc[0] = Transform.quant(r0 + r2, mf[0], dcBias, qbits + 1);
            dc[1] = Transform.quant(r1 + r3, mf[0], dcBias, qbits + 1);
            dc[2] = Transform.quant(r0 - r2, mf[0], dcBias, qbits + 1);
            dc[3] = Transform.quant(r1 - r3, mf[0], dcBias, qbits + 1);
            if (dc[0] != 0 || dc[1] != 0 || dc[2] != 0 || dc[3] != 0) {
                anyDc = true;
            }
        }
        if (anyAc) {
            return 2;
        }
        for (int plane = 0; plane < 2; plane++) {
            for (int blk = 0; blk < 4; blk++) {
                java.util.Arrays.fill(this.chromaAc[plane][blk], 0);
            }
        }
        return anyDc ? 1 : 0;
    }

    private void reconstructChroma(int mbx, int mby, int qpc) {
        Picture r = this.c.recon;
        int s = r.strideC;
        for (int plane = 0; plane < 2; plane++) {
            int[] dc = this.chromaDc[plane];
            int f0 = dc[0] + dc[1] + dc[2] + dc[3];
            int f1 = dc[0] - dc[1] + dc[2] - dc[3];
            int f2 = dc[0] + dc[1] - dc[2] - dc[3];
            int f3 = dc[0] - dc[1] - dc[2] + dc[3];
            this.chromaDcCoef[0] = Transform.dequantChromaDc(f0, qpc);
            this.chromaDcCoef[1] = Transform.dequantChromaDc(f1, qpc);
            this.chromaDcCoef[2] = Transform.dequantChromaDc(f2, qpc);
            this.chromaDcCoef[3] = Transform.dequantChromaDc(f3, qpc);
            byte[] target = plane == 0 ? r.u : r.v;
            int[] prediction = plane == 0 ? this.predU : this.predV;
            for (int blk = 0; blk < 4; blk++) {
                int bx = blk & 1;
                int by = blk >> 1;
                int[] levels = this.chromaAc[plane][blk];
                for (int k = 1; k < 16; k++) {
                    int pos = H264Tables.ZIGZAG[k];
                    this.work[pos] = Transform.dequant(levels[k], qpc, H264Tables.POSITION_CLASS[pos]);
                }
                this.work[0] = this.chromaDcCoef[blk];
                Transform.inverse4x4(this.work, this.coef);
                int o = r.originC + (mby * 8 + by * 4) * s + mbx * 8 + bx * 4;
                for (int row = 0; row < 4; row++) {
                    int p = (by * 4 + row) * 8 + bx * 4;
                    int q = o + row * s;
                    for (int col = 0; col < 4; col++) {
                        target[q + col] = Picture.clip(prediction[p + col] + this.coef[row * 4 + col]);
                    }
                }
            }
        }
    }

    private void transformInter() {
        int qp = this.qp;
        int qbits = 15 + qp / 6;
        int[] mf = H264Tables.QUANT_MF[qp % 6];
        int bias = (1 << qbits) / 6;
        int cbp = 0;
        int mbScore = 0;
        for (int b8 = 0; b8 < 4; b8++) {
            boolean any = false;
            int score = 0;
            for (int sub = 0; sub < 4; sub++) {
                int idx = b8 * 4 + sub;
                int bx = H264Tables.BLOCK_X[idx];
                int by = H264Tables.BLOCK_Y[idx];
                this.loadResidualBlock(this.src, this.pred, 16, bx * 4, by * 4);
                Transform.forward4x4(this.block, this.coef);
                int[] levels = this.lumaLevels[by * 4 + bx];
                for (int k = 0; k < 16; k++) {
                    int pos = H264Tables.ZIGZAG[k];
                    int level = Transform.quant(this.coef[pos], mf[H264Tables.POSITION_CLASS[pos]], bias, qbits);
                    levels[k] = level;
                    if (level != 0) {
                        any = true;
                    }
                }
                if (score < 9) {
                    score += Transform.decimateScore(levels, 0, 16);
                }
            }
            if (any && score < 4) {
                this.clear8x8(b8);
                any = false;
            }
            if (any) {
                cbp |= 1 << b8;
                mbScore += score;
            }
        }
        if (cbp != 0 && mbScore < 6) {
            for (int b8 = 0; b8 < 4; b8++) {
                this.clear8x8(b8);
            }
            cbp = 0;
        }
        this.cbpLuma = cbp;
        this.cbpChroma = this.transformChroma(H264Tables.chromaQp(qp), false);
    }

    private void clear8x8(int b8) {
        for (int sub = 0; sub < 4; sub++) {
            int idx = b8 * 4 + sub;
            java.util.Arrays.fill(this.lumaLevels[H264Tables.BLOCK_Y[idx] * 4 + H264Tables.BLOCK_X[idx]], 0);
        }
    }

    private void reconstructInter(int mbx, int mby) {
        int qp = this.qp;
        for (int blk = 0; blk < 16; blk++) {
            int bx = blk & 3;
            int by = blk >> 2;
            int b8 = (by >> 1) * 2 + (bx >> 1);
            if ((this.cbpLuma & (1 << b8)) == 0) {
                java.util.Arrays.fill(this.coef, 0);
            } else {
                int[] levels = this.lumaLevels[blk];
                for (int k = 0; k < 16; k++) {
                    int pos = H264Tables.ZIGZAG[k];
                    this.work[pos] = Transform.dequant(levels[k], qp, H264Tables.POSITION_CLASS[pos]);
                }
                Transform.inverse4x4(this.work, this.coef);
            }
            this.storeLumaBlock(mbx, mby, bx, by);
        }
        this.reconstructChroma(mbx, mby, H264Tables.chromaQp(qp));
    }

    private void predictMotion(int mbx, int mby) {
        int mb = mby * this.c.mbWidth + mbx;
        int w = this.c.mbWidth;
        int ax = 0;
        int ay = 0;
        int refA = -1;
        if (this.availLeft) {
            if (this.c.kind[mb - 1] != EncodeContext.INTRA) {
                ax = this.c.mvX[mb - 1];
                ay = this.c.mvY[mb - 1];
                refA = 0;
            }
        }
        int bx = 0;
        int by = 0;
        int refB = -1;
        if (this.availTop) {
            if (this.c.kind[mb - w] != EncodeContext.INTRA) {
                bx = this.c.mvX[mb - w];
                by = this.c.mvY[mb - w];
                refB = 0;
            }
        }
        int cx = 0;
        int cy = 0;
        int refC = -1;
        boolean haveC;
        int cIndex;
        if (this.availTopRight) {
            haveC = true;
            cIndex = mb - w + 1;
        } else if (this.availTopLeft) {
            haveC = true;
            cIndex = mb - w - 1;
        } else {
            haveC = false;
            cIndex = -1;
        }
        if (haveC && this.c.kind[cIndex] != EncodeContext.INTRA) {
            cx = this.c.mvX[cIndex];
            cy = this.c.mvY[cIndex];
            refC = 0;
        }
        if (!this.availTop && !haveC && this.availLeft) {
            bx = ax;
            by = ay;
            refB = refA;
            cx = ax;
            cy = ay;
            refC = refA;
        }
        int matches = (refA == 0 ? 1 : 0) + (refB == 0 ? 1 : 0) + (refC == 0 ? 1 : 0);
        if (matches == 1) {
            if (refA == 0) {
                this.mvpX = ax;
                this.mvpY = ay;
            } else if (refB == 0) {
                this.mvpX = bx;
                this.mvpY = by;
            } else {
                this.mvpX = cx;
                this.mvpY = cy;
            }
        } else {
            this.mvpX = median(ax, bx, cx);
            this.mvpY = median(ay, by, cy);
        }
        if (!this.availLeft || !this.availTop || (refA == 0 && ax == 0 && ay == 0) || (refB == 0 && bx == 0 && by == 0)) {
            this.skipX = 0;
            this.skipY = 0;
        } else {
            this.skipX = this.mvpX;
            this.skipY = this.mvpY;
        }
    }

    private static int median(int a, int b, int c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    private void motionSearch(int px, int py) {
        int lambda = this.lambda;
        int mbWidth = this.c.mbWidth;
        int mb = (py / 16) * mbWidth + px / 16;
        this.bestCost = Integer.MAX_VALUE;
        this.bestFx = 0;
        this.bestFy = 0;
        int n = 0;
        int[] candX = this.candidatesX;
        int[] candY = this.candidatesY;
        candX[n] = this.mvpX;
        candY[n++] = this.mvpY;
        candX[n] = 0;
        candY[n++] = 0;
        candX[n] = this.skipX;
        candY[n++] = this.skipY;
        candX[n] = this.c.prevMvX[mb];
        candY[n++] = this.c.prevMvY[mb];
        candX[n] = this.c.globalMvX;
        candY[n++] = this.c.globalMvY;
        if (px / 16 + 1 < mbWidth) {
            candX[n] = this.c.prevMvX[mb + 1];
            candY[n++] = this.c.prevMvY[mb + 1];
        }
        if (mb + mbWidth < this.c.mbCount()) {
            candX[n] = this.c.prevMvX[mb + mbWidth];
            candY[n++] = this.c.prevMvY[mb + mbWidth];
        }
        if (this.availLeft) {
            candX[n] = this.c.mvX[mb - 1];
            candY[n++] = this.c.mvY[mb - 1];
        }
        if (this.availTop) {
            candX[n] = this.c.mvX[mb - mbWidth];
            candY[n++] = this.c.mvY[mb - mbWidth];
        }
        if (this.availTopRight) {
            candX[n] = this.c.mvX[mb - mbWidth + 1];
            candY[n++] = this.c.mvY[mb - mbWidth + 1];
        }
        for (int i = 0; i < n; i++) {
            int fx = clampFull((candX[i] + 2) >> 2);
            int fy = clampFull((candY[i] + 2) >> 2);
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (clampFull((candX[j] + 2) >> 2) == fx && clampFull((candY[j] + 2) >> 2) == fy) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                this.tryFull(px, py, fx, fy, lambda);
            }
        }
        boolean fastest = this.c.preset == PRESET_FASTEST;
        this.hexagonDescent(px, py, lambda, fastest ? 8 : 32);
        if (this.c.preset == PRESET_QUALITY && this.bestCost > WIDE_SEARCH_COST + lambda * 32) {
            int centerX = this.bestFx;
            int centerY = this.bestFy;
            for (int scale = 1; scale <= 32; scale <<= 1) {
                for (int k = 0; k < GRID_X.length; k++) {
                    this.tryFull(px, py, centerX + GRID_X[k] * scale, centerY + GRID_Y[k] * scale, lambda);
                }
            }
            this.hexagonDescent(px, py, lambda, 32);
        }
        if (!fastest) {
            int centerX = this.bestFx;
            int centerY = this.bestFy;
            for (int d = 0; d < CORNER_X.length; d++) {
                this.tryFull(px, py, centerX + CORNER_X[d], centerY + CORNER_Y[d], lambda);
            }
        }
        this.bestX = this.bestFx * 4;
        this.bestY = this.bestFy * 4;
        if (fastest) {
            return;
        }
        this.refineSubpel(px, py, 2, lambda);
        if (this.c.preset == PRESET_QUALITY) {
            this.refineSubpel(px, py, 1, lambda);
        }
    }

    private void hexagonDescent(int px, int py, int lambda, int steps) {
        for (int iteration = 0; iteration < steps; iteration++) {
            int centerX = this.bestFx;
            int centerY = this.bestFy;
            for (int d = 0; d < HEX_X.length; d++) {
                this.tryFull(px, py, centerX + HEX_X[d], centerY + HEX_Y[d], lambda);
            }
            if (this.bestFx == centerX && this.bestFy == centerY) {
                break;
            }
        }
        for (int iteration = 0; iteration < 4; iteration++) {
            int centerX = this.bestFx;
            int centerY = this.bestFy;
            for (int d = 0; d < DIAMOND_X.length; d++) {
                this.tryFull(px, py, centerX + DIAMOND_X[d], centerY + DIAMOND_Y[d], lambda);
            }
            if (this.bestFx == centerX && this.bestFy == centerY) {
                break;
            }
        }
    }

    private void tryFull(int px, int py, int fx, int fy, int lambda) {
        if (fx < -MAX_FULL || fx > MAX_FULL || fy < -MAX_FULL || fy > MAX_FULL) {
            return;
        }
        int cost = this.fullCost(px, py, fx, fy, lambda, this.bestCost);
        if (cost < this.bestCost) {
            this.bestCost = cost;
            this.bestFx = fx;
            this.bestFy = fy;
        }
    }

    private void refineSubpel(int px, int py, int step, int lambda) {
        int centerX = this.bestX;
        int centerY = this.bestY;
        int[] dx = step == 1 ? DIAMOND_X : SQUARE_X;
        int[] dy = step == 1 ? DIAMOND_Y : SQUARE_Y;
        for (int d = 0; d < dx.length; d++) {
            int mx = centerX + dx[d] * step;
            int my = centerY + dy[d] * step;
            int bits = BitWriter.seLength(mx - this.mvpX) + BitWriter.seLength(my - this.mvpY);
            int limit = this.bestCost - lambda * bits;
            if (limit <= 0) {
                continue;
            }
            int cost = this.subpelSad(px, py, mx, my, limit) + lambda * bits;
            if (cost < this.bestCost) {
                this.bestCost = cost;
                this.bestX = mx;
                this.bestY = my;
            }
        }
    }

    private int subpelSad(int px, int py, int mvx, int mvy, int limit) {
        Picture ref = this.c.ref;
        int s = ref.stride;
        int base = ref.origin + (py + (mvy >> 2)) * s + px + (mvx >> 2);
        int code = (mvy & 3) * 4 + (mvx & 3);
        byte[] a = plane(ref, A_PLANE[code]);
        int aOff = base + A_DX[code];
        int bIndex = B_PLANE[code];
        int[] source = this.src;
        int sum = 0;
        if (bIndex < 0) {
            for (int row = 0; row < 16; row++) {
                int q = aOff + row * s;
                int o = row * 16;
                for (int col = 0; col < 16; col++) {
                    sum += Math.abs(source[o + col] - (a[q + col] & 0xFF));
                }
                if (sum >= limit) {
                    return sum;
                }
            }
            return sum;
        }
        byte[] b = plane(ref, bIndex);
        int bOff = base + B_DX[code] + B_DY[code] * s;
        for (int row = 0; row < 16; row++) {
            int qa = aOff + row * s;
            int qb = bOff + row * s;
            int o = row * 16;
            for (int col = 0; col < 16; col++) {
                int value = ((a[qa + col] & 0xFF) + (b[qb + col] & 0xFF) + 1) >> 1;
                sum += Math.abs(source[o + col] - value);
            }
            if (sum >= limit) {
                return sum;
            }
        }
        return sum;
    }

    private static int clampFull(int value) {
        return Math.max(-MAX_FULL, Math.min(MAX_FULL, value));
    }

    private int fullCost(int px, int py, int fx, int fy, int lambda, int limit) {
        int bits = BitWriter.seLength(fx * 4 - this.mvpX) + BitWriter.seLength(fy * 4 - this.mvpY);
        int mvCost = lambda * bits;
        if (mvCost >= limit) {
            return Integer.MAX_VALUE;
        }
        Picture ref = this.c.ref;
        byte[] f = ref.y;
        int s = ref.stride;
        int p = ref.origin + (py + fy) * s + px + fx;
        int sum = mvCost;
        for (int row = 0; row < 16; row++) {
            int o = row * 16;
            for (int col = 0; col < 16; col++) {
                sum += Math.abs(this.src[o + col] - (f[p + col] & 0xFF));
            }
            if (sum >= limit) {
                return sum;
            }
            p += s;
        }
        return sum;
    }

    private void mcLuma(int px, int py, int mvx, int mvy, int[] dst) {
        Picture ref = this.c.ref;
        int s = ref.stride;
        int base = ref.origin + (py + (mvy >> 2)) * s + px + (mvx >> 2);
        int code = (mvy & 3) * 4 + (mvx & 3);
        byte[] a = plane(ref, A_PLANE[code]);
        int aOff = base + A_DX[code];
        int bIndex = B_PLANE[code];
        if (bIndex < 0) {
            for (int row = 0; row < 16; row++) {
                int q = aOff + row * s;
                int o = row * 16;
                for (int col = 0; col < 16; col++) {
                    dst[o + col] = a[q + col] & 0xFF;
                }
            }
            return;
        }
        byte[] b = plane(ref, bIndex);
        int bOff = base + B_DX[code] + B_DY[code] * s;
        for (int row = 0; row < 16; row++) {
            int qa = aOff + row * s;
            int qb = bOff + row * s;
            int o = row * 16;
            for (int col = 0; col < 16; col++) {
                dst[o + col] = ((a[qa + col] & 0xFF) + (b[qb + col] & 0xFF) + 1) >> 1;
            }
        }
    }

    private static byte[] plane(Picture ref, int index) {
        return switch (index) {
            case 0 -> ref.y;
            case 1 -> ref.halfH;
            case 2 -> ref.halfV;
            default -> ref.halfC;
        };
    }

    private void mcChroma(int cx, int cy, int mvx, int mvy) {
        Picture ref = this.c.ref;
        int s = ref.strideC;
        int base = ref.originC + (cy + (mvy >> 3)) * s + cx + (mvx >> 3);
        int fx = mvx & 7;
        int fy = mvy & 7;
        int w00 = (8 - fx) * (8 - fy);
        int w10 = fx * (8 - fy);
        int w01 = (8 - fx) * fy;
        int w11 = fx * fy;
        for (int plane = 0; plane < 2; plane++) {
            byte[] p = plane == 0 ? ref.u : ref.v;
            int[] dst = plane == 0 ? this.predU : this.predV;
            for (int row = 0; row < 8; row++) {
                int q = base + row * s;
                int o = row * 8;
                for (int col = 0; col < 8; col++) {
                    int i = q + col;
                    dst[o + col] = (w00 * (p[i] & 0xFF) + w10 * (p[i + 1] & 0xFF) + w01 * (p[i + s] & 0xFF)
                            + w11 * (p[i + s + 1] & 0xFF) + 32) >> 6;
                }
            }
        }
    }

    private static int clip(int value) {
        return value < 0 ? 0 : value > 255 ? 255 : value;
    }
}

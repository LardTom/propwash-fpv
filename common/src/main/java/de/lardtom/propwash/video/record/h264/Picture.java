package de.lardtom.propwash.video.record.h264;

final class Picture {
    static final int PAD = 192;
    static final int PAD_C = 96;

    final int width;
    final int height;
    final int stride;
    final int strideC;
    final int origin;
    final int originC;
    final byte[] y;
    final byte[] u;
    final byte[] v;
    final byte[] halfH;
    final byte[] halfV;
    final byte[] halfC;

    Picture(int width, int height) {
        this.width = width;
        this.height = height;
        this.stride = width + 2 * PAD;
        this.strideC = width / 2 + 2 * PAD_C;
        this.origin = PAD * this.stride + PAD;
        this.originC = PAD_C * this.strideC + PAD_C;
        int lumaSize = this.stride * (height + 2 * PAD);
        int chromaSize = this.strideC * (height / 2 + 2 * PAD_C);
        this.y = new byte[lumaSize];
        this.u = new byte[chromaSize];
        this.v = new byte[chromaSize];
        this.halfH = new byte[lumaSize];
        this.halfV = new byte[lumaSize];
        this.halfC = new byte[lumaSize];
    }

    void copyFrom(Picture other) {
        System.arraycopy(other.y, 0, this.y, 0, this.y.length);
        System.arraycopy(other.u, 0, this.u, 0, this.u.length);
        System.arraycopy(other.v, 0, this.v, 0, this.v.length);
    }

    void padRows(int fromRow, int toRow) {
        padPlane(this.y, this.stride, this.origin, this.width, this.height, PAD, fromRow, toRow);
        padPlane(this.u, this.strideC, this.originC, this.width / 2, this.height / 2, PAD_C, fromRow / 2, toRow / 2);
        padPlane(this.v, this.strideC, this.originC, this.width / 2, this.height / 2, PAD_C, fromRow / 2, toRow / 2);
    }

    void padTopBottom() {
        padVertical(this.y, this.stride, this.origin, this.height, PAD);
        padVertical(this.u, this.strideC, this.originC, this.height / 2, PAD_C);
        padVertical(this.v, this.strideC, this.originC, this.height / 2, PAD_C);
    }

    private static void padPlane(byte[] plane, int stride, int origin, int width, int height, int pad, int fromRow, int toRow) {
        int end = Math.min(toRow, height);
        for (int row = Math.max(0, fromRow); row < end; row++) {
            int start = origin + row * stride;
            byte left = plane[start];
            byte right = plane[start + width - 1];
            for (int i = 1; i <= pad; i++) {
                plane[start - i] = left;
                plane[start + width - 1 + i] = right;
            }
        }
    }

    private static void padVertical(byte[] plane, int stride, int origin, int height, int pad) {
        int firstRow = origin - pad;
        int lastRow = origin + (height - 1) * stride - pad;
        for (int i = 1; i <= pad; i++) {
            System.arraycopy(plane, firstRow, plane, firstRow - i * stride, stride);
            System.arraycopy(plane, lastRow, plane, lastRow + i * stride, stride);
        }
    }

    static final int HALF_TOP = -3;
    static final int HALF_LEFT = -3;

    int halfBottom() {
        return this.height + 2;
    }

    void computeHalfPlanes(int fromRow, int toRow, int[] scratch) {
        int from = Math.max(fromRow, HALF_TOP);
        int to = Math.min(toRow, this.halfBottom());
        int right = this.width + 2;
        int x0 = HALF_LEFT - 2;
        int x1 = right + 3;
        byte[] f = this.y;
        int s = this.stride;
        for (int row = from; row < to; row++) {
            int base = this.origin + row * s;
            for (int x = x0; x < x1; x++) {
                int p = base + x;
                int v1 = (f[p - 2 * s] & 0xFF) - 5 * (f[p - s] & 0xFF) + 20 * (f[p] & 0xFF) + 20 * (f[p + s] & 0xFF)
                        - 5 * (f[p + 2 * s] & 0xFF) + (f[p + 3 * s] & 0xFF);
                scratch[x - x0] = v1;
                this.halfV[p] = clip((v1 + 16) >> 5);
            }
            for (int x = HALF_LEFT; x < right; x++) {
                int p = base + x;
                int b1 = (f[p - 2] & 0xFF) - 5 * (f[p - 1] & 0xFF) + 20 * (f[p] & 0xFF) + 20 * (f[p + 1] & 0xFF)
                        - 5 * (f[p + 2] & 0xFF) + (f[p + 3] & 0xFF);
                this.halfH[p] = clip((b1 + 16) >> 5);
                int i = x - x0;
                int j1 = scratch[i - 2] - 5 * scratch[i - 1] + 20 * scratch[i] + 20 * scratch[i + 1] - 5 * scratch[i + 2] + scratch[i + 3];
                this.halfC[p] = clip((j1 + 512) >> 10);
            }
            this.replicateColumns(this.halfH, base, HALF_LEFT, right);
            this.replicateColumns(this.halfC, base, HALF_LEFT, right);
            this.replicateColumns(this.halfV, base, x0, x1);
        }
    }

    private void replicateColumns(byte[] plane, int base, int first, int end) {
        java.util.Arrays.fill(plane, base - PAD, base + first, plane[base + first]);
        java.util.Arrays.fill(plane, base + end, base + this.width + PAD, plane[base + end - 1]);
    }

    void padHalfPlanes() {
        int s = this.stride;
        int top = this.origin + HALF_TOP * s - PAD;
        int bottom = this.origin + (this.halfBottom() - 1) * s - PAD;
        int above = PAD + HALF_TOP;
        int below = PAD - (this.halfBottom() - this.height);
        for (byte[] plane : new byte[][]{this.halfH, this.halfV, this.halfC}) {
            for (int i = 1; i <= above; i++) {
                System.arraycopy(plane, top, plane, top - i * s, s);
            }
            for (int i = 1; i <= below; i++) {
                System.arraycopy(plane, bottom, plane, bottom + i * s, s);
            }
        }
    }

    static byte clip(int value) {
        return (byte) (value < 0 ? 0 : value > 255 ? 255 : value);
    }
}

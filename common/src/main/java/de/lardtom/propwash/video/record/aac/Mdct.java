package de.lardtom.propwash.video.record.aac;

final class Mdct {
    private final int length;
    private final int half;
    private final int fftSize;
    private final double[] window;
    private final double[] preCos;
    private final double[] preSin;
    private final double[] postCos;
    private final double[] postSin;
    private final double[] twiddleCos;
    private final double[] twiddleSin;
    private final int[] reversed;
    private final double[] folded;
    private final double[] re;
    private final double[] im;

    Mdct(int length) {
        if (length < 16 || Integer.bitCount(length) != 1) {
            throw new IllegalArgumentException("length " + length);
        }
        this.length = length;
        this.half = length / 2;
        this.fftSize = length / 4;
        this.window = new double[length];
        for (int i = 0; i < length; i++) {
            this.window[i] = Math.sin(Math.PI * (i + 0.5) / length);
        }
        this.preCos = new double[this.fftSize];
        this.preSin = new double[this.fftSize];
        this.postCos = new double[this.fftSize];
        this.postSin = new double[this.fftSize];
        for (int t = 0; t < this.fftSize; t++) {
            double pre = -Math.PI * (t + 0.25) / this.half;
            this.preCos[t] = Math.cos(pre);
            this.preSin[t] = Math.sin(pre);
            double post = -Math.PI * t / this.half;
            this.postCos[t] = Math.cos(post);
            this.postSin[t] = Math.sin(post);
        }
        this.twiddleCos = new double[this.fftSize / 2];
        this.twiddleSin = new double[this.fftSize / 2];
        for (int i = 0; i < this.fftSize / 2; i++) {
            double angle = -2.0 * Math.PI * i / this.fftSize;
            this.twiddleCos[i] = Math.cos(angle);
            this.twiddleSin[i] = Math.sin(angle);
        }
        this.reversed = new int[this.fftSize];
        int bits = Integer.numberOfTrailingZeros(this.fftSize);
        for (int i = 0; i < this.fftSize; i++) {
            this.reversed[i] = Integer.reverse(i) >>> (32 - bits);
        }
        this.folded = new double[this.half];
        this.re = new double[this.fftSize];
        this.im = new double[this.fftSize];
    }

    int length() {
        return this.length;
    }

    double window(int index) {
        return this.window[index];
    }

    void forward(float[] block, double[] out, double scale) {
        int n = this.length;
        int m = this.half;
        int quarter = m / 2;
        for (int j = 0; j < quarter; j++) {
            double c = block[3 * quarter - 1 - j] * this.window[3 * quarter - 1 - j];
            double d = block[3 * quarter + j] * this.window[3 * quarter + j];
            this.folded[j] = -c - d;
            double a = block[j] * this.window[j];
            double b = block[m - 1 - j] * this.window[m - 1 - j];
            this.folded[quarter + j] = a - b;
        }
        if (n != 2 * m) {
            throw new IllegalStateException();
        }
        for (int t = 0; t < this.fftSize; t++) {
            int target = this.reversed[t];
            double wr = this.folded[2 * t];
            double wi = this.folded[m - 1 - 2 * t];
            this.re[target] = wr * this.preCos[t] - wi * this.preSin[t];
            this.im[target] = wr * this.preSin[t] + wi * this.preCos[t];
        }
        this.fft();
        for (int k = 0; k < this.fftSize; k++) {
            double br = this.re[k] * this.postCos[k] - this.im[k] * this.postSin[k];
            double bi = this.re[k] * this.postSin[k] + this.im[k] * this.postCos[k];
            out[2 * k] = br * scale;
            out[m - 1 - 2 * k] = -bi * scale;
        }
    }

    private void fft() {
        int size = this.fftSize;
        for (int span = 1; span < size; span <<= 1) {
            int step = size / (span * 2);
            for (int start = 0; start < size; start += span * 2) {
                for (int j = 0; j < span; j++) {
                    double wc = this.twiddleCos[j * step];
                    double ws = this.twiddleSin[j * step];
                    int a = start + j;
                    int b = a + span;
                    double tr = this.re[b] * wc - this.im[b] * ws;
                    double ti = this.re[b] * ws + this.im[b] * wc;
                    this.re[b] = this.re[a] - tr;
                    this.im[b] = this.im[a] - ti;
                    this.re[a] += tr;
                    this.im[a] += ti;
                }
            }
        }
    }
}

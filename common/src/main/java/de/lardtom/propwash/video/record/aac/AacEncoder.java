package de.lardtom.propwash.video.record.aac;

import de.lardtom.propwash.video.record.h264.BitWriter;

import java.util.Arrays;

public final class AacEncoder {
    public static final int FRAME = 1024;
    public static final int PRIMING = FRAME;
    public static final int SAMPLE_RATE = AacTables.SAMPLE_RATE_48000;
    public static final int OBJECT_TYPE_LC = 2;
    public static final int DEFAULT_BITRATE = 160_000;
    public static final int MAX_QUANT = 8191;
    static final int SF_OFFSET = 100;
    static final int SF_DIFF_ZERO = 60;
    static final int ZERO_CB = 0;
    static final int ESC_CB = 11;
    static final int ESC_VALUE = 16;
    static final int[] DIMENSION = {0, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 2};
    static final int[] LARGEST = {0, 1, 1, 2, 2, 4, 4, 7, 7, 12, 12, 8191};
    static final boolean[] UNSIGNED = {false, false, false, true, true, false, false, true, true, true, true, true};
    private static final int CODEBOOKS = 12;
    private static final int SECTION_HEADER_BITS = 9;
    private static final int MAX_FRAME_BITS = 6144 - 64;
    private static final double MAGIC = 0.4054;
    private static final double SILENCE_RMS = 1000.0;
    private static final double CUTOFF_HZ = 17_000.0;
    private static final int QUALITY_MIN = -24;
    private static final int QUALITY_MAX = 48;
    private static final int QUALITY_START = -8;

    private final int bitrate;
    private final int bands;
    private final int[] offsets = AacTables.SWB_OFFSET_LONG_48;
    private final Mdct mdct = new Mdct(2 * FRAME);
    private final float[] block = new float[2 * FRAME];
    private final double[] spectrum = new double[FRAME];
    private final double[] magnitude = new double[FRAME];
    private final int[] quant = new int[FRAME];
    private final double[] rms;
    private final int[] scale;
    private final int[] codebook;
    private final int[] bandLargest;
    private final long[][] bandBits;
    private final long[][] cost;
    private final int[][] from;
    private final BitWriter writer = new BitWriter(2048);
    private int quality = QUALITY_START;
    private long frames;
    private long totalBits;

    public AacEncoder(int sampleRate, int bitrate) {
        if (sampleRate != SAMPLE_RATE) {
            throw new IllegalArgumentException("only " + SAMPLE_RATE + " Hz is supported, got " + sampleRate);
        }
        if (bitrate < 16_000 || bitrate > 256_000) {
            throw new IllegalArgumentException("bitrate " + bitrate);
        }
        this.bitrate = bitrate;
        int cutoffLine = (int) Math.round(CUTOFF_HZ * 2.0 * FRAME / sampleRate);
        int usable = 0;
        while (usable < this.offsets.length - 1 && this.offsets[usable + 1] <= cutoffLine) {
            usable++;
        }
        this.bands = Math.max(1, usable);
        this.rms = new double[this.bands];
        this.scale = new int[this.bands];
        this.codebook = new int[this.bands];
        this.bandLargest = new int[this.bands];
        this.bandBits = new long[this.bands][CODEBOOKS];
        this.cost = new long[this.bands][CODEBOOKS];
        this.from = new int[this.bands][CODEBOOKS];
    }

    public int bitrate() {
        return this.bitrate;
    }

    public int bands() {
        return this.bands;
    }

    public long frames() {
        return this.frames;
    }

    public long averageBitrate() {
        return this.frames == 0L ? 0L : this.totalBits * SAMPLE_RATE / (this.frames * FRAME);
    }

    public static byte[] audioSpecificConfig() {
        int bits = OBJECT_TYPE_LC << 11 | AacTables.SAMPLE_RATE_INDEX_48000 << 7 | 1 << 3;
        return new byte[]{(byte) (bits >>> 8), (byte) bits};
    }

    public byte[] encode(float[] pcm, int offset) {
        System.arraycopy(this.block, FRAME, this.block, 0, FRAME);
        System.arraycopy(pcm, offset, this.block, FRAME, FRAME);
        return this.encodeBlock();
    }

    public byte[] flush() {
        System.arraycopy(this.block, FRAME, this.block, 0, FRAME);
        Arrays.fill(this.block, FRAME, 2 * FRAME, 0.0F);
        return this.encodeBlock();
    }

    private byte[] encodeBlock() {
        this.mdct.forward(this.block, this.spectrum, 2.0);
        for (int i = 0; i < FRAME; i++) {
            this.magnitude[i] = Math.pow(Math.abs(this.spectrum[i]), 0.75);
        }
        for (int b = 0; b < this.bands; b++) {
            double energy = 0.0;
            for (int i = this.offsets[b]; i < this.offsets[b + 1]; i++) {
                energy += this.spectrum[i] * this.spectrum[i];
            }
            this.rms[b] = Math.sqrt(energy / (this.offsets[b + 1] - this.offsets[b]));
        }
        int target = (int) ((long) this.bitrate * FRAME / SAMPLE_RATE);
        int limit = Math.min(MAX_FRAME_BITS, target * 2);
        int q = this.quality;
        int bits = this.plan(q);
        while (bits > limit && q < QUALITY_MAX + 32) {
            q += 3;
            bits = this.plan(q);
        }
        if (bits > target * 21 / 20) {
            this.quality = Math.min(QUALITY_MAX, this.quality + 1);
        } else if (bits < target * 19 / 20) {
            this.quality = Math.max(QUALITY_MIN, this.quality - 1);
        }
        byte[] frame = this.write();
        this.frames++;
        this.totalBits += frame.length * 8L;
        return frame;
    }

    private int plan(int q) {
        int previous = -1;
        for (int b = 0; b < this.bands; b++) {
            int start = this.offsets[b];
            int end = this.offsets[b + 1];
            if (this.rms[b] < SILENCE_RMS) {
                this.zeroBand(b, start, end);
                continue;
            }
            int sf = (int) Math.round(SF_OFFSET + 4.0 * Math.log(this.rms[b]) / Math.log(2.0)) + q;
            sf = Math.max(0, Math.min(255, sf));
            if (previous >= 0) {
                sf = Math.max(previous - SF_DIFF_ZERO, Math.min(previous + SF_DIFF_ZERO, sf));
            }
            int largest = this.quantize(sf, start, end);
            while (largest > MAX_QUANT && sf < 255 && (previous < 0 || sf < previous + SF_DIFF_ZERO)) {
                sf++;
                largest = this.quantize(sf, start, end);
            }
            if (largest > MAX_QUANT) {
                for (int i = start; i < end; i++) {
                    this.quant[i] = Math.max(-MAX_QUANT, Math.min(MAX_QUANT, this.quant[i]));
                }
                largest = MAX_QUANT;
            }
            if (largest == 0) {
                this.zeroBand(b, start, end);
                continue;
            }
            this.scale[b] = sf;
            this.bandLargest[b] = largest;
            previous = sf;
        }
        this.chooseCodebooks();
        return this.countBits();
    }

    private void zeroBand(int band, int start, int end) {
        Arrays.fill(this.quant, start, end, 0);
        this.bandLargest[band] = 0;
        this.scale[band] = -1;
    }

    private int quantize(int sf, int start, int end) {
        double inverse = Math.pow(2.0, -0.1875 * (sf - SF_OFFSET));
        int largest = 0;
        for (int i = start; i < end; i++) {
            int value = (int) (this.magnitude[i] * inverse + MAGIC);
            this.quant[i] = this.spectrum[i] < 0.0 ? -value : value;
            largest = Math.max(largest, value);
        }
        return largest;
    }

    private void chooseCodebooks() {
        int last = this.lastCodedBand();
        if (last < 0) {
            return;
        }
        long unreachable = Long.MAX_VALUE / 4L;
        for (int b = 0; b <= last; b++) {
            int start = this.offsets[b];
            int end = this.offsets[b + 1];
            for (int cb = 0; cb < CODEBOOKS; cb++) {
                if (cb == ZERO_CB) {
                    this.bandBits[b][cb] = this.bandLargest[b] == 0 ? 0L : unreachable;
                } else if (this.bandLargest[b] > LARGEST[cb]) {
                    this.bandBits[b][cb] = unreachable;
                } else {
                    this.bandBits[b][cb] = spectralBits(cb, this.quant, start, end) + (this.bandLargest[b] == 0 ? 1L : 0L);
                }
            }
        }
        for (int cb = 0; cb < CODEBOOKS; cb++) {
            this.cost[0][cb] = this.bandBits[0][cb] >= unreachable ? unreachable : this.bandBits[0][cb] + SECTION_HEADER_BITS;
            this.from[0][cb] = -1;
        }
        for (int b = 1; b <= last; b++) {
            int bestPrevious = 0;
            for (int cb = 1; cb < CODEBOOKS; cb++) {
                if (this.cost[b - 1][cb] < this.cost[b - 1][bestPrevious]) {
                    bestPrevious = cb;
                }
            }
            for (int cb = 0; cb < CODEBOOKS; cb++) {
                if (this.bandBits[b][cb] >= unreachable) {
                    this.cost[b][cb] = unreachable;
                    this.from[b][cb] = -1;
                    continue;
                }
                long stay = this.cost[b - 1][cb];
                long change = this.cost[b - 1][bestPrevious] + SECTION_HEADER_BITS;
                if (stay <= change) {
                    this.cost[b][cb] = stay + this.bandBits[b][cb];
                    this.from[b][cb] = cb;
                } else {
                    this.cost[b][cb] = change + this.bandBits[b][cb];
                    this.from[b][cb] = bestPrevious;
                }
            }
        }
        int best = 0;
        for (int cb = 1; cb < CODEBOOKS; cb++) {
            if (this.cost[last][cb] < this.cost[last][best]) {
                best = cb;
            }
        }
        for (int b = last; b >= 0; b--) {
            this.codebook[b] = best;
            best = this.from[b][best];
        }
        int previousSf = -1;
        for (int b = 0; b <= last; b++) {
            if (this.codebook[b] == ZERO_CB) {
                continue;
            }
            if (this.scale[b] < 0) {
                this.scale[b] = previousSf;
            }
            previousSf = this.scale[b];
        }
        for (int b = 0; b <= last; b++) {
            if (this.codebook[b] != ZERO_CB && this.scale[b] < 0) {
                this.scale[b] = this.firstScale();
            }
        }
    }

    private int lastCodedBand() {
        for (int b = this.bands - 1; b >= 0; b--) {
            if (this.bandLargest[b] > 0) {
                return b;
            }
        }
        return -1;
    }

    private int firstScale() {
        for (int b = 0; b < this.bands; b++) {
            if (this.bandLargest[b] > 0) {
                return this.scale[b];
            }
        }
        return SF_OFFSET;
    }

    private int countBits() {
        int last = this.lastCodedBand();
        int bits = 3 + 4 + 8 + 11 + 3 + 3 + 7;
        if (last < 0) {
            return bits;
        }
        int b = 0;
        while (b <= last) {
            int cb = this.codebook[b];
            int end = b;
            while (end + 1 <= last && this.codebook[end + 1] == cb) {
                end++;
            }
            bits += 4 + 5 * (1 + (end - b + 1) / 31);
            b = end + 1;
        }
        int previous = this.firstScale();
        for (b = 0; b <= last; b++) {
            int cb = this.codebook[b];
            if (cb == ZERO_CB) {
                continue;
            }
            int diff = this.scale[b] - previous;
            bits += AacTables.SF_BITS[diff + SF_DIFF_ZERO];
            previous = this.scale[b];
            bits += (int) spectralBits(cb, this.quant, this.offsets[b], this.offsets[b + 1]);
        }
        return bits;
    }

    private byte[] write() {
        BitWriter w = this.writer;
        w.reset();
        int last = this.lastCodedBand();
        int maxSfb = last + 1;
        int globalGain = this.firstScale();
        w.bits(0, 3);
        w.bits(0, 4);
        w.bits(globalGain, 8);
        w.bits(0, 1);
        w.bits(0, 2);
        w.bits(0, 1);
        w.bits(maxSfb, 6);
        w.bits(0, 1);
        int b = 0;
        while (b < maxSfb) {
            int cb = this.codebook[b];
            int end = b;
            while (end + 1 < maxSfb && this.codebook[end + 1] == cb) {
                end++;
            }
            w.bits(cb, 4);
            int length = end - b + 1;
            while (length >= 31) {
                w.bits(31, 5);
                length -= 31;
            }
            w.bits(length, 5);
            b = end + 1;
        }
        int previous = globalGain;
        for (b = 0; b < maxSfb; b++) {
            if (this.codebook[b] == ZERO_CB) {
                continue;
            }
            int index = this.scale[b] - previous + SF_DIFF_ZERO;
            w.bits(AacTables.SF_CODES[index], AacTables.SF_BITS[index]);
            previous = this.scale[b];
        }
        w.bits(0, 1);
        w.bits(0, 1);
        w.bits(0, 1);
        for (b = 0; b < maxSfb; b++) {
            int cb = this.codebook[b];
            if (cb != ZERO_CB) {
                writeSpectral(w, cb, this.quant, this.offsets[b], this.offsets[b + 1]);
            }
        }
        w.bits(7, 3);
        w.align();
        return w.toByteArray();
    }

    static int index(int cb, int[] values, int at) {
        return switch (cb) {
            case 1, 2 -> 27 * (values[at] + 1) + 9 * (values[at + 1] + 1) + 3 * (values[at + 2] + 1) + (values[at + 3] + 1);
            case 3, 4 -> 27 * Math.abs(values[at]) + 9 * Math.abs(values[at + 1]) + 3 * Math.abs(values[at + 2]) + Math.abs(values[at + 3]);
            case 5, 6 -> 9 * (values[at] + 4) + (values[at + 1] + 4);
            case 7, 8 -> 8 * Math.abs(values[at]) + Math.abs(values[at + 1]);
            case 9, 10 -> 13 * Math.abs(values[at]) + Math.abs(values[at + 1]);
            case 11 -> 17 * Math.min(ESC_VALUE, Math.abs(values[at])) + Math.min(ESC_VALUE, Math.abs(values[at + 1]));
            default -> throw new IllegalArgumentException("codebook " + cb);
        };
    }

    static long spectralBits(int cb, int[] values, int start, int end) {
        int dimension = DIMENSION[cb];
        int[] lengths = AacTables.BITS[cb - 1];
        long bits = 0L;
        for (int i = start; i < end; i += dimension) {
            bits += lengths[index(cb, values, i)];
            if (UNSIGNED[cb]) {
                for (int j = 0; j < dimension; j++) {
                    int v = Math.abs(values[i + j]);
                    if (v != 0) {
                        bits++;
                    }
                    if (cb == ESC_CB && v >= ESC_VALUE) {
                        bits += escapeBits(v);
                    }
                }
            }
        }
        return bits;
    }

    static int escapeBits(int value) {
        int n = 31 - Integer.numberOfLeadingZeros(value);
        return 2 * n - 3;
    }

    private static void writeSpectral(BitWriter w, int cb, int[] values, int start, int end) {
        int dimension = DIMENSION[cb];
        int[] codes = AacTables.CODES[cb - 1];
        int[] lengths = AacTables.BITS[cb - 1];
        for (int i = start; i < end; i += dimension) {
            int index = index(cb, values, i);
            w.bits(codes[index], lengths[index]);
            if (!UNSIGNED[cb]) {
                continue;
            }
            for (int j = 0; j < dimension; j++) {
                int v = values[i + j];
                if (v != 0) {
                    w.bits(v < 0 ? 1 : 0, 1);
                }
            }
            if (cb == ESC_CB) {
                for (int j = 0; j < dimension; j++) {
                    int v = Math.abs(values[i + j]);
                    if (v >= ESC_VALUE) {
                        writeEscape(w, v);
                    }
                }
            }
        }
    }

    private static void writeEscape(BitWriter w, int value) {
        int n = 31 - Integer.numberOfLeadingZeros(value);
        for (int i = 4; i < n; i++) {
            w.bits(1, 1);
        }
        w.bits(0, 1);
        w.bits(value - (1 << n), n);
    }
}

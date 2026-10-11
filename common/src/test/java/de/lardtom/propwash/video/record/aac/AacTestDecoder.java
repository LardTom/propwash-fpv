package de.lardtom.propwash.video.record.aac;

import java.util.HashMap;
import java.util.Map;

final class AacTestDecoder {
    private static final int FRAME = AacEncoder.FRAME;
    private static final int N = 2 * FRAME;
    private static final double[][] COS = new double[N][];
    private final double[] overlap = new double[FRAME];
    private final double[] window = new double[N];
    private final int[] offsets = AacTables.SWB_OFFSET_LONG_48;
    private final Map<Long, Integer>[] books;
    private final Map<Long, Integer> scalefactors;
    int lastMaxSfb;
    int lastGlobalGain;
    int escapes;
    int maxAbsQuant;

    @SuppressWarnings("unchecked")
    AacTestDecoder() {
        for (int i = 0; i < N; i++) {
            this.window[i] = Math.sin(Math.PI * (i + 0.5) / N);
        }
        this.books = new Map[11];
        for (int cb = 0; cb < 11; cb++) {
            this.books[cb] = table(AacTables.CODES[cb], AacTables.BITS[cb]);
        }
        this.scalefactors = table(AacTables.SF_CODES, AacTables.SF_BITS);
    }

    private static Map<Long, Integer> table(int[] codes, int[] bits) {
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < codes.length; i++) {
            map.put(((long) bits[i] << 32) | codes[i], i);
        }
        return map;
    }

    double[] decode(byte[] frame) {
        Reader r = new Reader(frame);
        if (r.bits(3) != 0) {
            throw new IllegalStateException("not a single channel element");
        }
        r.bits(4);
        int globalGain = r.bits(8);
        if (r.bits(1) != 0 || r.bits(2) != 0 || r.bits(1) != 0) {
            throw new IllegalStateException("unexpected ics_info");
        }
        int maxSfb = r.bits(6);
        if (r.bits(1) != 0) {
            throw new IllegalStateException("predictor data");
        }
        this.lastMaxSfb = maxSfb;
        this.lastGlobalGain = globalGain;
        int[] codebook = new int[maxSfb];
        int band = 0;
        while (band < maxSfb) {
            int cb = r.bits(4);
            int length = 0;
            int increment;
            do {
                increment = r.bits(5);
                length += increment;
            } while (increment == 31);
            if (length == 0 || band + length > maxSfb || cb == 12) {
                throw new IllegalStateException("bad section " + cb + "/" + length);
            }
            for (int i = 0; i < length; i++) {
                codebook[band++] = cb;
            }
        }
        int[] scale = new int[maxSfb];
        int offset = globalGain;
        for (int b = 0; b < maxSfb; b++) {
            if (codebook[b] != 0) {
                offset += this.huffman(r, this.scalefactors) - 60;
                if (offset < 0 || offset > 255) {
                    throw new IllegalStateException("scalefactor " + offset);
                }
                scale[b] = offset;
            }
        }
        if (r.bits(1) != 0 || r.bits(1) != 0 || r.bits(1) != 0) {
            throw new IllegalStateException("pulse/tns/gain control");
        }
        double[] spectrum = new double[FRAME];
        int[] values = new int[4];
        for (int b = 0; b < maxSfb; b++) {
            int cb = codebook[b];
            if (cb == 0) {
                continue;
            }
            int dimension = AacEncoder.DIMENSION[cb];
            double gain = Math.pow(2.0, 0.25 * (scale[b] - AacEncoder.SF_OFFSET));
            for (int i = this.offsets[b]; i < this.offsets[b + 1]; i += dimension) {
                int index = this.huffman(r, this.books[cb - 1]);
                unpack(cb, index, values);
                if (AacEncoder.UNSIGNED[cb]) {
                    for (int j = 0; j < dimension; j++) {
                        if (values[j] != 0 && r.bits(1) == 1) {
                            values[j] = -values[j];
                        }
                    }
                    if (cb == AacEncoder.ESC_CB) {
                        for (int j = 0; j < dimension; j++) {
                            if (Math.abs(values[j]) == AacEncoder.ESC_VALUE) {
                                int prefix = 0;
                                while (r.bits(1) == 1) {
                                    prefix++;
                                }
                                int n = prefix + 4;
                                int magnitude = (1 << n) + r.bits(n);
                                values[j] = values[j] < 0 ? -magnitude : magnitude;
                                this.escapes++;
                            }
                        }
                    }
                }
                for (int j = 0; j < dimension; j++) {
                    int q = values[j];
                    this.maxAbsQuant = Math.max(this.maxAbsQuant, Math.abs(q));
                    spectrum[i + j] = Math.signum(q) * Math.pow(Math.abs(q), 4.0 / 3.0) * gain;
                }
            }
        }
        if (r.bits(3) != 7) {
            throw new IllegalStateException("missing END element");
        }
        if (r.remaining() >= 8) {
            throw new IllegalStateException(r.remaining() + " bits left after END");
        }
        return this.synthesize(spectrum);
    }

    private double[] synthesize(double[] spectrum) {
        double[] time = new double[N];
        double n0 = (N / 2.0 + 1.0) / 2.0;
        for (int n = 0; n < N; n++) {
            double[] row = COS[n];
            if (row == null) {
                row = new double[FRAME];
                for (int k = 0; k < FRAME; k++) {
                    row[k] = Math.cos(2.0 * Math.PI / N * (n + n0) * (k + 0.5));
                }
                COS[n] = row;
            }
            double sum = 0.0;
            for (int k = 0; k < FRAME; k++) {
                if (spectrum[k] != 0.0) {
                    sum += spectrum[k] * row[k];
                }
            }
            time[n] = 2.0 / N * sum * this.window[n];
        }
        double[] out = new double[FRAME];
        for (int i = 0; i < FRAME; i++) {
            out[i] = this.overlap[i] + time[i];
            this.overlap[i] = time[FRAME + i];
        }
        return out;
    }

    private static void unpack(int cb, int index, int[] values) {
        switch (cb) {
            case 1, 2 -> {
                values[0] = index / 27 - 1;
                values[1] = index / 9 % 3 - 1;
                values[2] = index / 3 % 3 - 1;
                values[3] = index % 3 - 1;
            }
            case 3, 4 -> {
                values[0] = index / 27;
                values[1] = index / 9 % 3;
                values[2] = index / 3 % 3;
                values[3] = index % 3;
            }
            case 5, 6 -> {
                values[0] = index / 9 - 4;
                values[1] = index % 9 - 4;
            }
            case 7, 8 -> {
                values[0] = index / 8;
                values[1] = index % 8;
            }
            case 9, 10 -> {
                values[0] = index / 13;
                values[1] = index % 13;
            }
            case 11 -> {
                values[0] = index / 17;
                values[1] = index % 17;
            }
            default -> throw new IllegalStateException("codebook " + cb);
        }
    }

    private int huffman(Reader r, Map<Long, Integer> table) {
        long code = 0L;
        for (int length = 1; length <= 19; length++) {
            code = code << 1 | r.bits(1);
            Integer index = table.get(((long) length << 32) | code);
            if (index != null) {
                return index;
            }
        }
        throw new IllegalStateException("no huffman code");
    }

    private static final class Reader {
        private final byte[] data;
        private int position;

        Reader(byte[] data) {
            this.data = data;
        }

        int bits(int count) {
            int value = 0;
            for (int i = 0; i < count; i++) {
                if (this.position >= this.data.length * 8) {
                    throw new IllegalStateException("read past end");
                }
                int bit = (this.data[this.position >> 3] >> (7 - (this.position & 7))) & 1;
                value = value << 1 | bit;
                this.position++;
            }
            return value;
        }

        int remaining() {
            return this.data.length * 8 - this.position;
        }
    }
}

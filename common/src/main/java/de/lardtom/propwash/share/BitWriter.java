package de.lardtom.propwash.share;

import java.util.Arrays;

public final class BitWriter {
    private byte[] data = new byte[32];
    private long length;

    public long length() {
        return this.length;
    }

    public BitWriter bit(boolean value) {
        return this.bits(value ? 1 : 0, 1);
    }

    public BitWriter bits(long value, int count) {
        for (int i = count - 1; i >= 0; i--) {
            int index = (int) (this.length >>> 3);
            if (index >= this.data.length) {
                this.data = Arrays.copyOf(this.data, this.data.length * 2);
            }
            if (((value >>> i) & 1L) != 0) {
                this.data[index] |= (byte) (0x80 >>> (this.length & 7));
            }
            this.length++;
        }
        return this;
    }

    public BitWriter golomb(long value, int k) {
        if (value < 0) {
            throw new IllegalArgumentException("negativ: " + value);
        }
        long shifted = value + (1L << k);
        int width = 64 - Long.numberOfLeadingZeros(shifted);
        this.bits(0, width - k - 1);
        return this.bits(shifted, width);
    }

    public BitWriter bytes(byte[] values) {
        for (byte value : values) {
            this.bits(value & 0xFF, 8);
        }
        return this;
    }

    public BitWriter append(BitWriter other) {
        for (long i = 0; i < other.length; i++) {
            this.bit((other.data[(int) (i >>> 3)] & (0x80 >>> (i & 7))) != 0);
        }
        return this;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(this.data, (int) ((this.length + 7) >>> 3));
    }

    public static int golombLength(long value, int k) {
        long shifted = value + (1L << k);
        int width = 64 - Long.numberOfLeadingZeros(shifted);
        return 2 * width - k - 1;
    }
}

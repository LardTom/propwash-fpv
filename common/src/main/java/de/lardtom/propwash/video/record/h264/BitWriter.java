package de.lardtom.propwash.video.record.h264;

import java.util.Arrays;

public final class BitWriter {
    private byte[] data;
    private int length;
    private long accumulator;
    private int pending;

    public BitWriter() {
        this(4096);
    }

    public BitWriter(int capacity) {
        this.data = new byte[Math.max(16, capacity)];
    }

    public void reset() {
        this.length = 0;
        this.accumulator = 0L;
        this.pending = 0;
    }

    public void bits(int value, int count) {
        if (count == 0) {
            return;
        }
        if (count > 32 || count < 0) {
            throw new IllegalArgumentException("count " + count);
        }
        this.accumulator = (this.accumulator << count) | (value & ((1L << count) - 1L));
        this.pending += count;
        while (this.pending >= 8) {
            this.pending -= 8;
            this.put((byte) (this.accumulator >>> this.pending));
        }
        this.accumulator &= (1L << this.pending) - 1L;
    }

    public void bit(boolean value) {
        this.bits(value ? 1 : 0, 1);
    }

    public void ue(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("ue " + value);
        }
        long v = value + 1L;
        int len = 64 - Long.numberOfLeadingZeros(v);
        int zeros = len - 1;
        while (zeros > 16) {
            this.bits(0, 16);
            zeros -= 16;
        }
        this.bits(0, zeros);
        if (len > 32) {
            this.bits((int) (v >>> 32), len - 32);
            this.bits((int) v, 32);
        } else {
            this.bits((int) v, len);
        }
    }

    public void se(int value) {
        this.ue(value > 0 ? 2 * value - 1 : -2 * value);
    }

    public static int ueLength(int value) {
        return 2 * (32 - Integer.numberOfLeadingZeros(value + 1)) - 1;
    }

    public static int seLength(int value) {
        return ueLength(value > 0 ? 2 * value - 1 : -2 * value);
    }

    public void trailing() {
        this.bits(1, 1);
        this.align();
    }

    public void align() {
        if (this.pending > 0) {
            this.bits(0, 8 - this.pending);
        }
    }

    public boolean aligned() {
        return this.pending == 0;
    }

    public long bitLength() {
        return this.length * 8L + this.pending;
    }

    public int byteLength() {
        return this.length;
    }

    public byte[] toByteArray() {
        if (this.pending != 0) {
            throw new IllegalStateException("not byte aligned");
        }
        return Arrays.copyOf(this.data, this.length);
    }

    public void append(BitWriter other) {
        if (other.pending != 0 || this.pending != 0) {
            throw new IllegalStateException("not byte aligned");
        }
        this.ensure(other.length);
        System.arraycopy(other.data, 0, this.data, this.length, other.length);
        this.length += other.length;
    }

    byte[] raw() {
        return this.data;
    }

    private void put(byte b) {
        if (this.length == this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
        }
        this.data[this.length++] = b;
    }

    private void ensure(int extra) {
        if (this.length + extra > this.data.length) {
            this.data = Arrays.copyOf(this.data, Math.max(this.data.length * 2, this.length + extra));
        }
    }
}

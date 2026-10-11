package de.lardtom.propwash.video.record.mp4;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class BoxBuilder {
    private byte[] data = new byte[1024];
    private int length;
    private final int[] starts = new int[32];
    private int depth;

    BoxBuilder open(String type) {
        this.starts[this.depth++] = this.length;
        this.u32(0);
        this.fourCc(type);
        return this;
    }

    BoxBuilder full(String type, int version, int flags) {
        this.open(type);
        this.u32((version << 24) | (flags & 0xFFFFFF));
        return this;
    }

    BoxBuilder close() {
        int start = this.starts[--this.depth];
        int size = this.length - start;
        this.data[start] = (byte) (size >>> 24);
        this.data[start + 1] = (byte) (size >>> 16);
        this.data[start + 2] = (byte) (size >>> 8);
        this.data[start + 3] = (byte) size;
        return this;
    }

    BoxBuilder u8(int value) {
        this.ensure(1);
        this.data[this.length++] = (byte) value;
        return this;
    }

    BoxBuilder u16(int value) {
        this.ensure(2);
        this.data[this.length++] = (byte) (value >>> 8);
        this.data[this.length++] = (byte) value;
        return this;
    }

    BoxBuilder u32(long value) {
        this.ensure(4);
        this.data[this.length++] = (byte) (value >>> 24);
        this.data[this.length++] = (byte) (value >>> 16);
        this.data[this.length++] = (byte) (value >>> 8);
        this.data[this.length++] = (byte) value;
        return this;
    }

    BoxBuilder u64(long value) {
        this.u32(value >>> 32);
        this.u32(value & 0xFFFFFFFFL);
        return this;
    }

    BoxBuilder zeros(int count) {
        this.ensure(count);
        Arrays.fill(this.data, this.length, this.length + count, (byte) 0);
        this.length += count;
        return this;
    }

    BoxBuilder bytes(byte[] value) {
        this.ensure(value.length);
        System.arraycopy(value, 0, this.data, this.length, value.length);
        this.length += value.length;
        return this;
    }

    BoxBuilder fourCc(String type) {
        byte[] ascii = type.getBytes(StandardCharsets.US_ASCII);
        if (ascii.length != 4) {
            throw new IllegalArgumentException(type);
        }
        return this.bytes(ascii);
    }

    BoxBuilder matrix() {
        return this.u32(0x00010000).u32(0).u32(0)
                .u32(0).u32(0x00010000).u32(0)
                .u32(0).u32(0).u32(0x40000000);
    }

    byte[] toByteArray() {
        if (this.depth != 0) {
            throw new IllegalStateException("open boxes: " + this.depth);
        }
        return Arrays.copyOf(this.data, this.length);
    }

    private void ensure(int extra) {
        if (this.length + extra > this.data.length) {
            this.data = Arrays.copyOf(this.data, Math.max(this.data.length * 2, this.length + extra));
        }
    }
}

package de.lardtom.propwash.blackbox;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BlackboxBuffer {
    private byte[] data;
    private int size;
    private int pending = -1;

    public BlackboxBuffer(int capacity) {
        this.data = new byte[Math.max(16, capacity)];
    }

    public int size() {
        return this.size;
    }

    public void clear() {
        this.size = 0;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(this.data, this.size);
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(this.data, 0, this.size);
    }

    public void write(int b) {
        if (this.size == this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
        }
        this.data[this.size++] = (byte) b;
    }

    public void ascii(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        for (byte b : bytes) {
            this.write(b);
        }
    }

    public void unsignedVB(int value) {
        long v = value & 0xFFFFFFFFL;
        while (v > 127) {
            this.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
        this.write((int) v);
    }

    public void signedVB(int value) {
        this.unsignedVB((value << 1) ^ (value >> 31));
    }

    public void neg14Bit(int value) {
        this.unsignedVB(-value & 0x3FFF);
    }

    public void tag2x3s32(int a, int b, int c) {
        int selector;
        if (outside(a, 32) || outside(b, 32) || outside(c, 32)) {
            selector = 3;
        } else if (outside(a, 8) || outside(b, 8) || outside(c, 8)) {
            selector = 2;
        } else if (outside(a, 2) || outside(b, 2) || outside(c, 2)) {
            selector = 1;
        } else {
            selector = 0;
        }
        switch (selector) {
            case 0 -> this.write(((a & 0x03) << 4) | ((b & 0x03) << 2) | (c & 0x03));
            case 1 -> {
                this.write(0x40 | (a & 0x0F));
                this.write(((b & 0x0F) << 4) | (c & 0x0F));
            }
            case 2 -> {
                this.write(0x80 | (a & 0x3F));
                this.write(b & 0x3F);
                this.write(c & 0x3F);
            }
            default -> {
                int widthA = byteWidth(a);
                int widthB = byteWidth(b);
                int widthC = byteWidth(c);
                this.write(0xC0 | (widthC - 1) << 4 | (widthB - 1) << 2 | (widthA - 1));
                this.littleEndian(a, widthA);
                this.littleEndian(b, widthB);
                this.littleEndian(c, widthC);
            }
        }
    }

    public void tag8x4s16(int a, int b, int c, int d) {
        int ka = nibbleKind(a);
        int kb = nibbleKind(b);
        int kc = nibbleKind(c);
        int kd = nibbleKind(d);
        this.write(ka | kb << 2 | kc << 4 | kd << 6);
        this.pending = -1;
        this.packed(ka, a);
        this.packed(kb, b);
        this.packed(kc, c);
        this.packed(kd, d);
        if (this.pending >= 0) {
            this.write(this.pending);
        }
    }

    private void packed(int kind, int v) {
        int pending = this.pending;
        switch (kind) {
            case 1 -> {
                if (pending < 0) {
                    this.pending = (v & 0x0F) << 4;
                } else {
                    this.write(pending | (v & 0x0F));
                    this.pending = -1;
                }
            }
            case 2 -> {
                if (pending < 0) {
                    this.write(v & 0xFF);
                } else {
                    this.write(pending | ((v >> 4) & 0x0F));
                    this.pending = (v & 0x0F) << 4;
                }
            }
            case 3 -> {
                if (pending < 0) {
                    this.write((v >> 8) & 0xFF);
                    this.write(v & 0xFF);
                } else {
                    this.write(pending | ((v >> 12) & 0x0F));
                    this.write((v >> 4) & 0xFF);
                    this.pending = (v & 0x0F) << 4;
                }
            }
            default -> {
            }
        }
    }

    public void tag8x8svb(int[] values, int count) {
        if (count == 1) {
            this.signedVB(values[0]);
            return;
        }
        int header = 0;
        for (int i = 0; i < count; i++) {
            if (values[i] != 0) {
                header |= 1 << i;
            }
        }
        this.write(header);
        for (int i = 0; i < count; i++) {
            if (values[i] != 0) {
                this.signedVB(values[i]);
            }
        }
    }

    private void littleEndian(int value, int bytes) {
        for (int i = 0; i < bytes; i++) {
            this.write((value >> (8 * i)) & 0xFF);
        }
    }

    private static boolean outside(int value, int limit) {
        return value >= limit || value < -limit;
    }

    private static int byteWidth(int value) {
        if (value >= -128 && value < 128) {
            return 1;
        }
        if (value >= -32768 && value < 32768) {
            return 2;
        }
        if (value >= -8388608 && value < 8388608) {
            return 3;
        }
        return 4;
    }

    private static int nibbleKind(int value) {
        if (value == 0) {
            return 0;
        }
        if (value >= -8 && value < 8) {
            return 1;
        }
        if (value >= -128 && value < 128) {
            return 2;
        }
        return 3;
    }
}

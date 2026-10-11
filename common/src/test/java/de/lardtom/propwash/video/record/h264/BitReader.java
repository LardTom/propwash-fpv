package de.lardtom.propwash.video.record.h264;

final class BitReader {
    private final byte[] data;
    private long position;

    BitReader(byte[] data) {
        this.data = data;
    }

    int bits(int count) {
        int value = 0;
        for (int i = 0; i < count; i++) {
            int index = (int) (this.position >>> 3);
            int bit = (this.data[index] >> (7 - (int) (this.position & 7))) & 1;
            value = (value << 1) | bit;
            this.position++;
        }
        return value;
    }

    long bitsLong(int count) {
        long value = 0L;
        for (int i = 0; i < count; i++) {
            value = (value << 1) | this.bits(1);
        }
        return value;
    }

    int ue() {
        int zeros = 0;
        while (this.bits(1) == 0) {
            zeros++;
        }
        return (1 << zeros) - 1 + (zeros == 0 ? 0 : this.bits(zeros));
    }

    int se() {
        int code = this.ue();
        return (code & 1) == 1 ? (code + 1) / 2 : -(code / 2);
    }

    long position() {
        return this.position;
    }
}

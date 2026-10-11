package de.lardtom.propwash.video.record.external;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class AnnexB {
    public static final int SLICE = 1;
    public static final int IDR = 5;
    public static final int SEI = 6;
    public static final int SPS = 7;
    public static final int PPS = 8;
    public static final int AUD = 9;

    private final Consumer<AccessUnit> output;
    private byte[] buffer = new byte[1 << 16];
    private int length;
    private int nalStart = -1;
    private int scanFrom;
    private final List<byte[]> current = new ArrayList<>();
    private boolean currentHasSlice;

    public AnnexB(Consumer<AccessUnit> output) {
        this.output = output;
    }

    public void feed(byte[] data, int offset, int count) {
        if (count <= 0) {
            return;
        }
        if (this.length + count > this.buffer.length) {
            this.compact();
            if (this.length + count > this.buffer.length) {
                this.buffer = Arrays.copyOf(this.buffer, Math.max(this.buffer.length * 2, this.length + count));
            }
        }
        System.arraycopy(data, offset, this.buffer, this.length, count);
        this.length += count;
        this.scan();
    }

    public void finish() {
        if (this.nalStart >= 0) {
            this.nal(this.nalStart, this.length);
            this.nalStart = -1;
        }
        this.length = 0;
        this.scanFrom = 0;
        this.emit();
    }

    private void scan() {
        int i = Math.max(0, this.scanFrom);
        int end = this.length - 3;
        while (i <= end) {
            if ((this.buffer[i + 2] & 0xFF) > 1) {
                i += 3;
                continue;
            }
            if (this.buffer[i] == 0 && this.buffer[i + 1] == 0 && this.buffer[i + 2] == 1) {
                if (this.nalStart >= 0) {
                    this.nal(this.nalStart, i);
                }
                this.nalStart = i + 3;
                i += 3;
                continue;
            }
            i++;
        }
        this.scanFrom = Math.max(0, this.length - 3);
    }

    private void compact() {
        int keep = this.nalStart >= 0 ? this.nalStart : this.scanFrom;
        keep = Math.max(0, Math.min(keep, this.length));
        if (keep == 0) {
            return;
        }
        System.arraycopy(this.buffer, keep, this.buffer, 0, this.length - keep);
        this.length -= keep;
        this.scanFrom = Math.max(0, this.scanFrom - keep);
        if (this.nalStart >= 0) {
            this.nalStart -= keep;
        }
    }

    private void nal(int from, int to) {
        while (to > from && this.buffer[to - 1] == 0) {
            to--;
        }
        if (to <= from) {
            return;
        }
        byte[] nal = Arrays.copyOfRange(this.buffer, from, to);
        int type = nal[0] & 0x1F;
        boolean slice = type == SLICE || type == IDR;
        boolean firstSlice = slice && nal.length > 1 && (nal[1] & 0x80) != 0;
        boolean prefix = type == AUD || type == SPS || type == PPS || type == SEI || type >= 14 && type <= 18;
        if (this.currentHasSlice && (prefix || firstSlice)) {
            this.emit();
        }
        this.current.add(nal);
        if (slice) {
            this.currentHasSlice = true;
        }
    }

    private void emit() {
        if (this.current.isEmpty()) {
            return;
        }
        List<byte[]> nals = List.copyOf(this.current);
        this.current.clear();
        boolean hadSlice = this.currentHasSlice;
        this.currentHasSlice = false;
        if (hadSlice) {
            this.output.accept(new AccessUnit(nals));
        }
    }

    public static int type(byte[] nal) {
        return nal.length == 0 ? -1 : nal[0] & 0x1F;
    }

    public record AccessUnit(List<byte[]> nals) {
        public boolean keyframe() {
            for (byte[] nal : this.nals) {
                if (type(nal) == IDR) {
                    return true;
                }
            }
            return false;
        }

        public byte[] find(int type) {
            for (byte[] nal : this.nals) {
                if (type(nal) == type) {
                    return nal;
                }
            }
            return null;
        }

        public byte[] toSample() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] nal : this.nals) {
                int type = type(nal);
                if (type == SPS || type == PPS || type == AUD) {
                    continue;
                }
                out.write(nal.length >>> 24);
                out.write(nal.length >>> 16);
                out.write(nal.length >>> 8);
                out.write(nal.length);
                out.writeBytes(nal);
            }
            return out.toByteArray();
        }
    }
}

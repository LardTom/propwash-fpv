package de.lardtom.propwash.video.record.h264;

import java.io.ByteArrayOutputStream;

public final class Nal {
    public static final int SLICE = 1;
    public static final int IDR = 5;
    public static final int SPS = 7;
    public static final int PPS = 8;

    private Nal() {
    }

    public static byte[] wrap(int refIdc, int type, BitWriter rbsp) {
        return wrap(refIdc, type, rbsp.raw(), rbsp.byteLength());
    }

    public static byte[] wrap(int refIdc, int type, byte[] rbsp, int length) {
        byte[] out = new byte[1 + length + length / 2 + 4];
        out[0] = (byte) (((refIdc & 3) << 5) | (type & 31));
        int n = 1;
        int zeros = 0;
        for (int i = 0; i < length; i++) {
            int b = rbsp[i] & 0xFF;
            if (zeros >= 2 && b <= 3) {
                out[n++] = 3;
                zeros = 0;
            }
            out[n++] = (byte) b;
            zeros = b == 0 ? zeros + 1 : 0;
        }
        byte[] exact = new byte[n];
        System.arraycopy(out, 0, exact, 0, n);
        return exact;
    }

    public static byte[] unwrap(byte[] nal, int offset, int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        int zeros = 0;
        for (int i = offset + 1; i < offset + length; i++) {
            int b = nal[i] & 0xFF;
            if (zeros >= 2 && b == 3) {
                zeros = 0;
                continue;
            }
            out.write(b);
            zeros = b == 0 ? zeros + 1 : 0;
        }
        return out.toByteArray();
    }

    public static int type(byte[] nal, int offset) {
        return nal[offset] & 31;
    }
}

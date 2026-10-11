package de.lardtom.propwash.video.record.h264;

public record EncodedFrame(byte[][] nals, boolean keyframe, int qp) {
    public int sampleSize() {
        int size = 0;
        for (byte[] nal : this.nals) {
            size += 4 + nal.length;
        }
        return size;
    }

    public byte[] toSample() {
        byte[] out = new byte[this.sampleSize()];
        int offset = 0;
        for (byte[] nal : this.nals) {
            int length = nal.length;
            out[offset] = (byte) (length >>> 24);
            out[offset + 1] = (byte) (length >>> 16);
            out[offset + 2] = (byte) (length >>> 8);
            out[offset + 3] = (byte) length;
            System.arraycopy(nal, 0, out, offset + 4, length);
            offset += 4 + length;
        }
        return out;
    }

    public byte[] toAnnexB() {
        int size = 0;
        for (byte[] nal : this.nals) {
            size += 4 + nal.length;
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] nal : this.nals) {
            out[offset + 3] = 1;
            System.arraycopy(nal, 0, out, offset + 4, nal.length);
            offset += 4 + nal.length;
        }
        return out;
    }
}

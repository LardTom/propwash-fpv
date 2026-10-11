package de.lardtom.propwash.video.record.h264;

final class EncodeContext {
    static final byte INTRA = 0;
    static final byte INTER = 1;
    static final byte SKIP = 2;

    final int width;
    final int height;
    final int mbWidth;
    final int mbHeight;
    final byte[] srcY;
    final byte[] srcU;
    final byte[] srcV;
    final byte[] kind;
    final short[] mvX;
    final short[] mvY;
    final short[] prevMvX;
    final short[] prevMvY;
    final byte[] nzY;
    final byte[] nzU;
    final byte[] nzV;
    final byte[] intra4x4;
    final byte[] intraModes;
    final byte[] mbQp;
    final int log2MaxFrameNum;

    Picture ref;
    Picture recon;
    boolean idr;
    boolean intra;
    int qp;
    int frameNum;
    int idrPicId;
    int preset;
    boolean deblock = true;
    double adaptiveQuant;
    int minQp = 10;
    int maxQp = 51;
    int globalMvX;
    int globalMvY;

    EncodeContext(int width, int height, int log2MaxFrameNum) {
        this.width = width;
        this.height = height;
        this.mbWidth = width / 16;
        this.mbHeight = height / 16;
        int mbs = this.mbWidth * this.mbHeight;
        this.srcY = new byte[width * height];
        this.srcU = new byte[width * height / 4];
        this.srcV = new byte[width * height / 4];
        this.kind = new byte[mbs];
        this.mvX = new short[mbs];
        this.mvY = new short[mbs];
        this.prevMvX = new short[mbs];
        this.prevMvY = new short[mbs];
        this.nzY = new byte[mbs * 16];
        this.nzU = new byte[mbs * 4];
        this.nzV = new byte[mbs * 4];
        this.intra4x4 = new byte[mbs];
        this.intraModes = new byte[mbs * 16];
        this.mbQp = new byte[mbs];
        this.log2MaxFrameNum = log2MaxFrameNum;
        this.ref = new Picture(width, height);
        this.recon = new Picture(width, height);
    }

    int mbCount() {
        return this.mbWidth * this.mbHeight;
    }
}

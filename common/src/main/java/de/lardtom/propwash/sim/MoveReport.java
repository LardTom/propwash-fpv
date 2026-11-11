package de.lardtom.propwash.sim;

public final class MoveReport {
    public static final int STICKY_FLOOR = stickyBit(1, false);
    public static final int STICKY_ALL = 0x3F;
    public double requestedX;
    public double requestedY;
    public double requestedZ;
    public double actualX;
    public double actualY;
    public double actualZ;
    public boolean hitX;
    public boolean hitY;
    public boolean hitZ;
    public double impactVelocityX = Double.NaN;
    public double impactVelocityY = Double.NaN;
    public double impactVelocityZ = Double.NaN;
    public double surfaceGrip = 1.0;
    public double surfaceRestitution = -1.0;
    public double surfaceImpactScale = 1.0;
    public double supportGap = Double.NaN;
    public double contactNormalX = Double.NaN;
    public double contactNormalY = Double.NaN;
    public double contactNormalZ = Double.NaN;
    public boolean ghost;
    public int stickySides;

    public MoveReport() {
    }

    public void clear() {
        this.requestedX = 0.0;
        this.requestedY = 0.0;
        this.requestedZ = 0.0;
        this.actualX = 0.0;
        this.actualY = 0.0;
        this.actualZ = 0.0;
        this.hitX = false;
        this.hitY = false;
        this.hitZ = false;
        this.impactVelocityX = Double.NaN;
        this.impactVelocityY = Double.NaN;
        this.impactVelocityZ = Double.NaN;
        this.surfaceGrip = 1.0;
        this.surfaceRestitution = -1.0;
        this.surfaceImpactScale = 1.0;
        this.supportGap = Double.NaN;
        this.contactNormalX = Double.NaN;
        this.contactNormalY = Double.NaN;
        this.contactNormalZ = Double.NaN;
        this.ghost = false;
        this.stickySides = 0;
    }

    public static int stickyBit(int axis, boolean positive) {
        return 1 << (axis * 2 + (positive ? 1 : 0));
    }

    public boolean obliqueContact() {
        return Double.isFinite(this.contactNormalX) && Double.isFinite(this.contactNormalY) && Double.isFinite(this.contactNormalZ);
    }
}

package de.lardtom.propwash.sim;

final class HullShape {
    static final double CONTACT_BAND = 0.003;

    final double[] wx = new double[AirframeSpec.HULL_POINTS];
    final double[] wy = new double[AirframeSpec.HULL_POINTS];
    final double[] wz = new double[AirframeSpec.HULL_POINTS];
    final boolean[] inContact = new boolean[AirframeSpec.HULL_POINTS];
    double contactX;
    double contactY;
    double contactZ;

    void update(AirframeSpec p, RigidBody b) {
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            double x = p.hullX(k);
            double y = p.hullY(k);
            double z = p.hullZ(k);
            this.wx[k] = b.r00 * x + b.r01 * y + b.r02 * z;
            this.wy[k] = b.r10 * x + b.r11 * y + b.r12 * z;
            this.wz[k] = b.r20 * x + b.r21 * y + b.r22 * z;
        }
    }

    void contact(double nx, double ny, double nz) {
        double min = Double.POSITIVE_INFINITY;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            double d = this.wx[k] * nx + this.wy[k] * ny + this.wz[k] * nz;
            if (d < min) {
                min = d;
            }
        }
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        int count = 0;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            double d = this.wx[k] * nx + this.wy[k] * ny + this.wz[k] * nz;
            boolean in = d <= min + CONTACT_BAND;
            this.inContact[k] = in;
            if (in) {
                sx += this.wx[k];
                sy += this.wy[k];
                sz += this.wz[k];
                count++;
            }
        }
        this.contactX = sx / count;
        this.contactY = sy / count;
        this.contactZ = sz / count;
    }
}

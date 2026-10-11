package de.lardtom.propwash.sim;

final class BreakSolver {
    final HullShape hull = new HullShape();
    final boolean[] inside = new boolean[AirframeSpec.HULL_POINTS];
    final boolean[] leading = new boolean[AirframeSpec.HULL_POINTS];
    final double[] struckRpm = new double[AirframeSpec.MOTORS];
    int contacts;
    int struck;
    double spin;
    double share;
    double area;
    double plug;
    double lostShards;
    double rateX;
    double rateY;
    double rateZ;

    void clear() {
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            this.inside[k] = false;
            this.leading[k] = false;
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.struckRpm[i] = 0.0;
        }
        this.contacts = 0;
        this.struck = 0;
        this.spin = 0.0;
        this.share = 0.0;
        this.area = 0.0;
        this.plug = 0.0;
        this.lostShards = 0.0;
        this.rateX = 0.0;
        this.rateY = 0.0;
        this.rateZ = 0.0;
    }

    void solve(AirframeSpec p, RigidBody b, PowerSystem power, SimTuning tuning, double spinSign, double massKg, double ux, double uy,
               double uz, double speedAfterFracture, double lostFracture, BreakContact c) {
        this.clear();
        double k = BreakImpulse.strength(c.strength());
        if (k <= 0.0) {
            return;
        }
        b.updateDerived();
        this.hull.update(p, b);
        int count = 0;
        int nearest = 0;
        double nearestGap = Double.POSITIVE_INFINITY;
        for (int i = 0; i < AirframeSpec.HULL_POINTS; i++) {
            double gap = lateralGap(c, ux, uy, uz, b.px + this.hull.wx[i], b.py + this.hull.wy[i], b.pz + this.hull.wz[i]);
            boolean in = gap <= BreakImpulse.EDGE_MARGIN;
            this.inside[i] = in;
            if (in) {
                count++;
            }
            if (gap < nearestGap) {
                nearestGap = gap;
                nearest = i;
            }
        }
        if (count == 0) {
            this.inside[nearest] = true;
            count = 1;
        }
        this.share = (double) count / AirframeSpec.HULL_POINTS;
        double nbx = b.r00 * ux + b.r10 * uy + b.r20 * uz;
        double nby = b.r01 * ux + b.r11 * uy + b.r21 * uz;
        double nbz = b.r02 * ux + b.r12 * uy + b.r22 * uz;
        this.area = BreakImpulse.hitArea(p.areaX, p.areaY, p.areaZ, nbx, nby, nbz, this.share);
        this.plug = BreakImpulse.plugMass(c.shardKgPerM2(), this.area, k);
        this.lostShards = speedAfterFracture - BreakImpulse.afterShards(massKg, speedAfterFracture, this.plug);
        double front = Double.POSITIVE_INFINITY;
        double ax = 0.0;
        double ay = 0.0;
        double az = 0.0;
        for (int i = 0; i < AirframeSpec.HULL_POINTS; i++) {
            if (this.inside[i]) {
                front = Math.min(front, this.hull.wx[i] * ux + this.hull.wy[i] * uy + this.hull.wz[i] * uz);
                ax += this.hull.wx[i];
                ay += this.hull.wy[i];
                az += this.hull.wz[i];
            }
        }
        ax /= count;
        ay /= count;
        az /= count;
        double reach = Math.sqrt(p.hullX(0) * p.hullX(0) + p.hullZ(0) * p.hullZ(0));
        double depth = Math.max(1.0E-3, BreakImpulse.CONTACT_DEPTH * reach);
        double lx = 0.0;
        double ly = 0.0;
        double lz = 0.0;
        double weights = 0.0;
        for (int i = 0; i < AirframeSpec.HULL_POINTS; i++) {
            if (!this.inside[i]) {
                continue;
            }
            double behind = this.hull.wx[i] * ux + this.hull.wy[i] * uy + this.hull.wz[i] * uz - front;
            if (behind <= BreakImpulse.LEAD_BAND) {
                this.leading[i] = true;
                this.contacts |= 1 << i;
            }
            double weight = Math.exp(-behind / depth);
            lx += weight * this.hull.wx[i];
            ly += weight * this.hull.wy[i];
            lz += weight * this.hull.wz[i];
            weights += weight;
        }
        lx /= weights;
        ly /= weights;
        lz /= weights;
        double hx = Math.abs(uy) < 0.9 ? 0.0 : 1.0;
        double hy = Math.abs(uy) < 0.9 ? 1.0 : 0.0;
        double t1x = hy * uz;
        double t1y = -hx * uz;
        double t1z = hx * uy - hy * ux;
        double t1 = Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
        t1x /= t1;
        t1y /= t1;
        t1z /= t1;
        double t2x = uy * t1z - uz * t1y;
        double t2y = uz * t1x - ux * t1z;
        double t2z = ux * t1y - uy * t1x;
        double radius = BreakImpulse.IRREGULARITY * reach;
        double e1 = radius * BreakImpulse.unit(c.seed(), 0);
        double e2 = radius * BreakImpulse.unit(c.seed(), 1);
        lx += e1 * t1x + e2 * t2x;
        ly += e1 * t1y + e2 * t2y;
        lz += e1 * t1z + e2 * t2z;
        double gain = BreakImpulse.tumbleShare(k);
        double jf = gain * massKg * lostFracture;
        double js = gain * massKg * this.lostShards;
        double oldX = b.wbx;
        double oldY = b.wby;
        double oldZ = b.wbz;
        b.lx += jf * (ly * uz - lz * uy) + js * (ay * uz - az * uy);
        b.ly += jf * (lz * ux - lx * uz) + js * (az * ux - ax * uz);
        b.lz += jf * (lx * uy - ly * ux) + js * (ax * uy - ay * ux);
        if (!p.ducted && tuning.propStrikes && nby < CollisionSolver.STRIKE_NORMAL_LIMIT) {
            double loss = BreakImpulse.strikeLoss(k);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                double w = power.omega[i];
                if (this.leading[i] && Math.abs(w) > CollisionSolver.STRIKE_OMEGA_FRACTION * p.hoverOmega) {
                    this.struck |= 1 << i;
                    this.struckRpm[i] = Math.abs(w) * 60.0 / SimMath.TWO_PI;
                    this.spin = Math.max(this.spin, Math.abs(w) / p.hoverOmega);
                    power.omega[i] = (1.0 - loss) * w;
                    double dl = -spinSign * p.spin(i) * p.rotorInertiaEff * loss * w;
                    b.lx += dl * b.r01;
                    b.ly += dl * b.r11;
                    b.lz += dl * b.r21;
                }
            }
        }
        b.updateRates();
        double dx = b.wbx - oldX;
        double dy = b.wby - oldY;
        double dz = b.wbz - oldZ;
        double dw = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dw > BreakImpulse.MAX_RATE_CHANGE) {
            double s = BreakImpulse.MAX_RATE_CHANGE / dw;
            dx *= s;
            dy *= s;
            dz *= s;
            b.setBodyRates(oldX + dx, oldY + dy, oldZ + dz);
        }
        this.rateX = dx;
        this.rateY = dy;
        this.rateZ = dz;
    }

    private static double lateralGap(BreakContact c, double ux, double uy, double uz, double x, double y, double z) {
        double gx = Math.abs(ux) >= 0.5 ? 0.0 : Math.max(0.0, Math.max(c.minX() - x, x - c.maxX()));
        double gy = Math.abs(uy) >= 0.5 ? 0.0 : Math.max(0.0, Math.max(c.minY() - y, y - c.maxY()));
        double gz = Math.abs(uz) >= 0.5 ? 0.0 : Math.max(0.0, Math.max(c.minZ() - z, z - c.maxZ()));
        return Math.sqrt(gx * gx + gy * gy + gz * gz);
    }
}

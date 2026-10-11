package de.lardtom.propwash.sim;

final class CollisionSolver {
    static final double MAX_RATE_CHANGE = 30.0;
    static final double STRIKE_OMEGA_FRACTION = 0.3;
    static final double STRIKE_REMAINING = 0.2;
    static final double STRIKE_YAW_TRANSFER = 0.8;
    static final double STRIKE_NORMAL_LIMIT = 0.7;

    final HullShape hull = new HullShape();
    final boolean[] struck = new boolean[AirframeSpec.MOTORS];
    final double[] struckRpm = new double[AirframeSpec.MOTORS];
    boolean resolved;
    double impactSpeed;
    double tangentialSpeed;
    double normalImpulse;

    private double invInertiaWorldX;
    private double invInertiaWorldY;
    private double invInertiaWorldZ;

    private void applyInverseInertia(RigidBody b, double x, double y, double z) {
        double bx = (b.r00 * x + b.r10 * y + b.r20 * z) / b.ixx;
        double by = (b.r01 * x + b.r11 * y + b.r21 * z) / b.iyy;
        double bz = (b.r02 * x + b.r12 * y + b.r22 * z) / b.izz;
        this.invInertiaWorldX = b.r00 * bx + b.r01 * by + b.r02 * bz;
        this.invInertiaWorldY = b.r10 * bx + b.r11 * by + b.r12 * bz;
        this.invInertiaWorldZ = b.r20 * bx + b.r21 * by + b.r22 * bz;
    }

    private double effectiveMass(RigidBody b, double rx, double ry, double rz, double nx, double ny, double nz) {
        double cx = ry * nz - rz * ny;
        double cy = rz * nx - rx * nz;
        double cz = rx * ny - ry * nx;
        this.applyInverseInertia(b, cx, cy, cz);
        double ix = this.invInertiaWorldX;
        double iy = this.invInertiaWorldY;
        double iz = this.invInertiaWorldZ;
        double kx = iy * rz - iz * ry;
        double ky = iz * rx - ix * rz;
        double kz = ix * ry - iy * rx;
        return 1.0 / b.mass + nx * kx + ny * ky + nz * kz;
    }

    void resolve(AirframeSpec p, RigidBody b, PowerSystem power, SimTuning tuning, double spinSign, double nx, double ny,
                 double nz, double grip, double restitution, double approachLimit) {
        this.resolved = false;
        this.impactSpeed = 0.0;
        this.tangentialSpeed = 0.0;
        this.normalImpulse = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.struck[i] = false;
            this.struckRpm[i] = 0.0;
        }
        b.updateDerived();
        this.hull.update(p, b);
        this.hull.contact(nx, ny, nz);
        double rx = this.hull.contactX;
        double ry = this.hull.contactY;
        double rz = this.hull.contactZ;
        double vcx = b.vx + (b.wwy * rz - b.wwz * ry);
        double vcy = b.vy + (b.wwz * rx - b.wwx * rz);
        double vcz = b.vz + (b.wwx * ry - b.wwy * rx);
        double vn = vcx * nx + vcy * ny + vcz * nz;
        if (vn >= 0.0) {
            return;
        }
        double vDotN = b.vx * nx + b.vy * ny + b.vz * nz;
        this.impactSpeed = -vDotN;
        double tx0 = b.vx - vDotN * nx;
        double ty0 = b.vy - vDotN * ny;
        double tz0 = b.vz - vDotN * nz;
        this.tangentialSpeed = Math.sqrt(tx0 * tx0 + ty0 * ty0 + tz0 * tz0);
        double e0 = restitution >= 0.0 ? restitution : p.restitution;
        double vcr = p.impactTolerance * tuning.crashSpeedScale;
        double speed = Math.min(-vn, approachLimit);
        double e = speed < ModelConstants.BOUNCE_MIN ? 0.0
                : SimMath.clamp(e0 * tuning.restitutionScale * (1.0 - 0.6 * SimMath.smoothstep(0.3 * vcr, vcr, speed)), 0.0,
                ModelConstants.RESTITUTION_MAX);
        double oldWx = b.wbx;
        double oldWy = b.wby;
        double oldWz = b.wbz;
        double kn = this.effectiveMass(b, rx, ry, rz, nx, ny, nz);
        double jn = -(1.0 + e) * vn / kn;
        this.applyImpulse(b, rx, ry, rz, jn * nx, jn * ny, jn * nz);
        this.normalImpulse = jn;
        double mu = p.friction * Math.max(0.0, grip);
        vcx = b.vx + (b.wwy * rz - b.wwz * ry);
        vcy = b.vy + (b.wwz * rx - b.wwx * rz);
        vcz = b.vz + (b.wwx * ry - b.wwy * rx);
        double vn2 = vcx * nx + vcy * ny + vcz * nz;
        double vtx = vcx - vn2 * nx;
        double vty = vcy - vn2 * ny;
        double vtz = vcz - vn2 * nz;
        double vt = Math.sqrt(vtx * vtx + vty * vty + vtz * vtz);
        if (vt > 1e-6 && mu > 0.0) {
            double tx = vtx / vt;
            double ty = vty / vt;
            double tz = vtz / vt;
            double kt = this.effectiveMass(b, rx, ry, rz, tx, ty, tz);
            double jt = Math.min(mu * jn, vt / kt);
            this.applyImpulse(b, rx, ry, rz, -jt * tx, -jt * ty, -jt * tz);
        }
        if (!p.ducted && tuning.propStrikes) {
            double normalBodyY = b.r01 * nx + b.r11 * ny + b.r21 * nz;
            if (normalBodyY < STRIKE_NORMAL_LIMIT) {
                for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                    double w = power.omega[i];
                    if (this.hull.inContact[i] && Math.abs(w) > STRIKE_OMEGA_FRACTION * p.hoverOmega) {
                        this.struck[i] = true;
                        this.struckRpm[i] = Math.abs(w) * 60.0 / SimMath.TWO_PI;
                        power.omega[i] = STRIKE_REMAINING * w;
                        double dl = -spinSign * p.spin(i) * p.rotorInertiaEff * STRIKE_YAW_TRANSFER * w;
                        b.lx += dl * b.r01;
                        b.ly += dl * b.r11;
                        b.lz += dl * b.r21;
                    }
                }
                b.updateRates();
            }
        }
        double dx = b.wbx - oldWx;
        double dy = b.wby - oldWy;
        double dz = b.wbz - oldWz;
        double dw = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dw > MAX_RATE_CHANGE) {
            double s = MAX_RATE_CHANGE / dw;
            b.setBodyRates(oldWx + dx * s, oldWy + dy * s, oldWz + dz * s);
        }
        this.resolved = true;
    }

    private void applyImpulse(RigidBody b, double rx, double ry, double rz, double jx, double jy, double jz) {
        double m = b.mass;
        b.vx += jx / m;
        b.vy += jy / m;
        b.vz += jz / m;
        b.lx += ry * jz - rz * jy;
        b.ly += rz * jx - rx * jz;
        b.lz += rx * jy - ry * jx;
        b.updateRates();
    }
}

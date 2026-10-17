package de.lardtom.propwash.sim;

final class GroundSolver {
    static final int POINTS = AirframeSpec.GROUND_POINTS;
    static final int ITERATIONS = 64;
    static final double TOLERANCE = 1.0E-7;
    static final double IMPACT_TIME = 0.002;
    static final double LOSS_ONSET = 0.3;
    static final double LOSS_EXPONENT = 0.25;
    static final double DIG_GAIN = 4.0;
    static final double REACH = 0.02;
    static final double SLOP = 0.0005;
    static final double PUSH_RATE = 20.0;
    static final double PUSH_MAX = 0.3;

    private final double[] rx = new double[POINTS];
    private final double[] ry = new double[POINTS];
    private final double[] rz = new double[POINTS];
    private final double[] target = new double[POINTS];
    private final double[] lambda = new double[POINTS];
    private final double[] frictionX = new double[POINTS];
    private final double[] frictionZ = new double[POINTS];
    private final double[] massN = new double[POINTS];
    private final double[] grip = new double[POINTS];
    private final double[] massX = new double[POINTS];
    private final double[] massZ = new double[POINTS];
    private final boolean[] active = new boolean[POINTS];
    final boolean[] touching = new boolean[POINTS];
    private final boolean[] wasTouching = new boolean[POINTS];
    private final double[] warmN = new double[POINTS];
    private final double[] warmX = new double[POINTS];
    private final double[] warmZ = new double[POINTS];

    double vx;
    double vy;
    double vz;
    double lx;
    double ly;
    double lz;
    private double wx;
    private double wy;
    private double wz;
    private double ix;
    private double iy;
    private double iz;

    double impulseX;
    double impulseY;
    double impulseZ;
    double angularX;
    double angularY;
    double angularZ;
    double normalImpulse;
    int newContacts;
    int touchMask;
    double approachSpeed;
    double tangentialSpeed;

    void forget() {
        for (int k = 0; k < POINTS; k++) {
            this.wasTouching[k] = false;
            this.touching[k] = false;
            this.warmN[k] = 0.0;
            this.warmX[k] = 0.0;
            this.warmZ[k] = 0.0;
        }
        this.touchMask = 0;
    }

    double lowest(AirframeSpec p, RigidBody b) {
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k < p.groundCount(); k++) {
            low = Math.min(low, b.r10 * p.groundX(k) + b.r11 * p.groundY(k) + b.r12 * p.groundZ(k));
        }
        return low;
    }

    private void inverseInertia(RigidBody b, double x, double y, double z) {
        double bx = (b.r00 * x + b.r10 * y + b.r20 * z) / b.ixx;
        double by = (b.r01 * x + b.r11 * y + b.r21 * z) / b.iyy;
        double bz = (b.r02 * x + b.r12 * y + b.r22 * z) / b.izz;
        this.ix = b.r00 * bx + b.r01 * by + b.r02 * bz;
        this.iy = b.r10 * bx + b.r11 * by + b.r12 * bz;
        this.iz = b.r20 * bx + b.r21 * by + b.r22 * bz;
    }

    private void rates(RigidBody b) {
        this.inverseInertia(b, this.lx, this.ly, this.lz);
        this.wx = this.ix;
        this.wy = this.iy;
        this.wz = this.iz;
    }

    private void push(RigidBody b, int k, double jx, double jy, double jz) {
        double m = b.mass;
        this.vx += jx / m;
        this.vy += jy / m;
        this.vz += jz / m;
        double x = this.rx[k];
        double y = this.ry[k];
        double z = this.rz[k];
        this.lx += y * jz - z * jy;
        this.ly += z * jx - x * jz;
        this.lz += x * jy - y * jx;
        this.rates(b);
    }

    static double restitution(double base, double speed, double crashSpeed) {
        if (!(base > 0.0)) {
            return 0.0;
        }
        double onset = Math.max(LOSS_ONSET * crashSpeed, ModelConstants.BOUNCE_MIN);
        double loss = speed > onset ? StrictMath.pow(speed / onset, -LOSS_EXPONENT) : 1.0;
        return SimMath.clamp(base * loss, 0.0, ModelConstants.RESTITUTION_MAX);
    }

    static boolean digs(int k) {
        return k < AirframeSpec.HULL_POINTS && k != 8 && k != 9 && k != 11;
    }

    static boolean soft(int k) {
        return k == 8 || k == 9 || (k >= AirframeSpec.HULL_POINTS && k < AirframeSpec.HULL_POINTS + 4);
    }

    boolean solve(AirframeSpec p, RigidBody b, double plane, double h, double mu, double bounce, double softBounce, double crashSpeed,
                  boolean substep, double vx0, double vy0, double vz0, double lx0, double ly0, double lz0) {
        this.vx = vx0;
        this.vy = vy0;
        this.vz = vz0;
        this.lx = lx0;
        this.ly = ly0;
        this.lz = lz0;
        this.normalImpulse = 0.0;
        this.newContacts = 0;
        this.touchMask = 0;
        this.approachSpeed = 0.0;
        this.tangentialSpeed = 0.0;
        double m = b.mass;
        boolean any = false;
        int count = p.groundCount();
        this.rates(b);
        for (int k = 0; k < POINTS; k++) {
            this.active[k] = false;
            this.lambda[k] = 0.0;
            this.frictionX[k] = 0.0;
            this.frictionZ[k] = 0.0;
        }
        for (int k = 0; k < count; k++) {
            if (!p.groundUsed(k)) {
                continue;
            }
            double x = b.r00 * p.groundX(k) + b.r01 * p.groundY(k) + b.r02 * p.groundZ(k);
            double y = b.r10 * p.groundX(k) + b.r11 * p.groundY(k) + b.r12 * p.groundZ(k);
            double z = b.r20 * p.groundX(k) + b.r21 * p.groundY(k) + b.r22 * p.groundZ(k);
            double depth = plane - (b.py + y);
            if (!(depth > -REACH)) {
                continue;
            }
            this.active[k] = true;
            any = true;
            this.rx[k] = x;
            this.ry[k] = y;
            this.rz[k] = z;
            this.inverseInertia(b, -z, 0.0, x);
            this.massN[k] = 1.0 / (1.0 / m + this.iz * x - this.ix * z);
            this.inverseInertia(b, 0.0, z, -y);
            this.massX[k] = 1.0 / (1.0 / m + this.iy * z - this.iz * y);
            this.inverseInertia(b, y, -x, 0.0);
            this.massZ[k] = 1.0 / (1.0 / m + this.ix * y - this.iy * x);
            double vn = this.vy + (this.wz * x - this.wx * z);
            double goal;
            if (depth < 0.0) {
                goal = depth / h;
            } else if (substep && depth > SLOP) {
                goal = Math.min(PUSH_MAX, PUSH_RATE * (depth - SLOP));
            } else {
                goal = 0.0;
            }
            double base = soft(k) ? softBounce : bounce;
            if (!this.wasTouching[k]) {
                this.grip[k] = 1.0;
            }
            boolean arriving = depth >= 0.0 || vn * Math.max(h, IMPACT_TIME) <= depth;
            if (arriving && !this.wasTouching[k] && vn < -ModelConstants.BOUNCE_MIN) {
                double speed = -vn;
                double e = restitution(base, speed, crashSpeed);
                goal = Math.max(goal, e * speed);
                if (digs(k)) {
                    this.grip[k] = Math.max(this.grip[k], 1.0 + DIG_GAIN * SimMath.smoothstep(LOSS_ONSET * crashSpeed, crashSpeed, speed));
                }
            }
            this.target[k] = goal;
        }
        if (!any) {
            this.forget();
            return false;
        }
        double startVy = this.vy;
        double startT = Math.sqrt(this.vx * this.vx + this.vz * this.vz);
        if (substep) {
            for (int k = 0; k < count; k++) {
                if (this.active[k] && this.wasTouching[k] && this.warmN[k] > 0.0) {
                    this.lambda[k] = this.warmN[k];
                    this.frictionX[k] = this.warmX[k];
                    this.frictionZ[k] = this.warmZ[k];
                    this.push(b, k, this.warmX[k], this.warmN[k], this.warmZ[k]);
                }
            }
        }
        double tolerance = TOLERANCE * m;
        for (int it = 0; it < ITERATIONS; it++) {
            double change = 0.0;
            for (int n = 0; n < count; n++) {
                int k = (it & 1) == 0 ? n : count - 1 - n;
                if (!this.active[k]) {
                    continue;
                }
                double x = this.rx[k];
                double y = this.ry[k];
                double z = this.rz[k];
                double vn = this.vy + (this.wz * x - this.wx * z);
                double next = Math.max(0.0, this.lambda[k] + (this.target[k] - vn) * this.massN[k]);
                double dn = next - this.lambda[k];
                this.lambda[k] = next;
                if (dn != 0.0) {
                    this.push(b, k, 0.0, dn, 0.0);
                    change = Math.max(change, Math.abs(dn));
                }
                double limit = this.grip[k] * mu * next;
                double fx = this.frictionX[k];
                double fz = this.frictionZ[k];
                double nx = 0.0;
                double nz = 0.0;
                if (limit > 0.0) {
                    double ux = this.vx + (this.wy * z - this.wz * y);
                    double uz = this.vz + (this.wx * y - this.wy * x);
                    nx = fx - ux * this.massX[k];
                    nz = fz - uz * this.massZ[k];
                    double size = Math.sqrt(nx * nx + nz * nz);
                    if (size > limit) {
                        nx *= limit / size;
                        nz *= limit / size;
                    }
                }
                if (nx != fx || nz != fz) {
                    this.frictionX[k] = nx;
                    this.frictionZ[k] = nz;
                    this.push(b, k, nx - fx, 0.0, nz - fz);
                    change = Math.max(change, Math.max(Math.abs(nx - fx), Math.abs(nz - fz)));
                }
            }
            if (change < tolerance) {
                break;
            }
        }
        double total = 0.0;
        for (int k = 0; k < POINTS; k++) {
            boolean touch = this.active[k] && this.lambda[k] > 0.0;
            if (touch) {
                total += this.lambda[k];
                this.touchMask |= 1 << k;
                if (!this.wasTouching[k]) {
                    this.newContacts |= 1 << k;
                }
            }
            this.touching[k] = touch;
            this.wasTouching[k] = touch;
            if (substep) {
                this.warmN[k] = touch ? this.lambda[k] : 0.0;
                this.warmX[k] = touch ? this.frictionX[k] : 0.0;
                this.warmZ[k] = touch ? this.frictionZ[k] : 0.0;
            }
        }
        this.normalImpulse = total;
        if (this.newContacts != 0) {
            this.approachSpeed = Math.max(0.0, -startVy);
            this.tangentialSpeed = startT;
        }
        this.impulseX = (this.vx - vx0) * m;
        this.impulseY = (this.vy - vy0) * m;
        this.impulseZ = (this.vz - vz0) * m;
        this.angularX = this.lx - lx0;
        this.angularY = this.ly - ly0;
        this.angularZ = this.lz - lz0;
        return total > 0.0;
    }
}

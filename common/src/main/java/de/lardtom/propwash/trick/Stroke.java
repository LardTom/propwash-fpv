package de.lardtom.propwash.trick;

final class Stroke {
    static final double DIAGONAL_RATIO = 0.6;

    enum Kind {
        PITCH,
        ROLL,
        DIAGONAL,
        YAW
    }

    final boolean yawStroke;
    int first;
    int last;
    double start;
    double activeStart;
    double end;
    double pitch;
    double roll;
    double yaw;
    double active;
    double path;
    double idle;

    Stroke(boolean yawStroke, int first, double start) {
        this.yawStroke = yawStroke;
        this.first = first;
        this.last = first;
        this.start = start;
        this.activeStart = start;
        this.end = start;
    }

    double tumble() {
        return Math.hypot(this.pitch, this.roll);
    }

    double angle() {
        return this.yawStroke ? Math.abs(this.yaw) : this.tumble();
    }

    double rate() {
        return this.active > 1.0E-6 ? this.path / this.active : 0.0;
    }

    Kind kind() {
        if (this.yawStroke) {
            return Kind.YAW;
        }
        double p = Math.abs(this.pitch);
        double r = Math.abs(this.roll);
        if (Math.min(p, r) >= DIAGONAL_RATIO * Math.max(p, r)) {
            return Kind.DIAGONAL;
        }
        return p >= r ? Kind.PITCH : Kind.ROLL;
    }

    boolean pitchDominant() {
        return Math.abs(this.pitch) >= Math.abs(this.roll);
    }

    boolean partial() {
        double a = this.angle();
        return Math.abs(a - 360.0 * Math.rint(a / 360.0)) >= 90.0;
    }

    double cosine(double dPitch, double dRoll) {
        double a = this.tumble();
        double b = Math.hypot(dPitch, dRoll);
        if (a < 1.0E-9 || b < 1.0E-9) {
            return 0.0;
        }
        return (this.pitch * dPitch + this.roll * dRoll) / (a * b);
    }

    boolean sameDirection(Stroke other, double minCosine) {
        if (this.yawStroke != other.yawStroke) {
            return false;
        }
        if (this.yawStroke) {
            return Math.signum(this.yaw) == Math.signum(other.yaw);
        }
        return this.cosine(other.pitch, other.roll) >= minCosine;
    }

    boolean opposite(Stroke other, double maxCosine) {
        if (this.yawStroke != other.yawStroke) {
            return false;
        }
        if (this.yawStroke) {
            return Math.signum(this.yaw) == -Math.signum(other.yaw);
        }
        return this.cosine(other.pitch, other.roll) <= maxCosine;
    }

    void absorb(Stroke other) {
        this.last = Math.max(this.last, other.last);
        this.first = Math.min(this.first, other.first);
        this.start = Math.min(this.start, other.start);
        this.activeStart = Math.min(this.activeStart, other.activeStart);
        this.end = Math.max(this.end, other.end);
        this.pitch += other.pitch;
        this.roll += other.roll;
        this.yaw += other.yaw;
        this.active += other.active;
        this.path += other.path;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%s[p=%.0f r=%.0f y=%.0f %.2f-%.2fs rate=%.0f]",
                this.kind(), this.pitch, this.roll, this.yaw, this.start, this.end, this.rate());
    }
}

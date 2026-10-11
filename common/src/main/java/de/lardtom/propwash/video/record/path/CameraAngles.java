package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;

public record CameraAngles(double yaw, double pitch, double roll) {
    private static final double GIMBAL = 1.0E-9;

    public static CameraAngles of(Quaterniondc rotation) {
        Quaterniond q = new Quaterniond(rotation).normalize();
        double x = q.x();
        double y = q.y();
        double z = q.z();
        double w = q.w();
        double m00 = 1.0 - 2.0 * (y * y + z * z);
        double m02 = 2.0 * (x * z + y * w);
        double m10 = 2.0 * (x * y + z * w);
        double m11 = 1.0 - 2.0 * (x * x + z * z);
        double m12 = 2.0 * (y * z - x * w);
        double m20 = 2.0 * (x * z - y * w);
        double m22 = 1.0 - 2.0 * (x * x + y * y);
        double b = Math.asin(Math.max(-1.0, Math.min(1.0, -m12)));
        double a;
        double c;
        if (Math.abs(Math.cos(b)) < GIMBAL) {
            a = Math.atan2(-m20, m00);
            c = 0.0;
        } else {
            a = Math.atan2(m02, m22);
            c = Math.atan2(m10, m11);
        }
        return new CameraAngles(wrap(180.0 - Math.toDegrees(a)), -Math.toDegrees(b), wrap(Math.toDegrees(c)));
    }

    public Quaterniond rotation() {
        return new Quaterniond().rotationYXZ(Math.PI - Math.toRadians(this.yaw), -Math.toRadians(this.pitch), Math.toRadians(this.roll));
    }

    public CameraAngles shifted(double yawDelta, double rollDelta) {
        return new CameraAngles(wrap(this.yaw + yawDelta), this.pitch, wrap(this.roll + rollDelta));
    }

    public CameraAngles lerpShortest(CameraAngles to, double t) {
        return new CameraAngles(this.yaw + wrap(to.yaw - this.yaw) * t, this.pitch + (to.pitch - this.pitch) * t,
                this.roll + wrap(to.roll - this.roll) * t);
    }

    public static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    public static double degreesBetween(Quaterniondc a, Quaterniondc b) {
        double dot = Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        double na = Math.sqrt(a.x() * a.x() + a.y() * a.y() + a.z() * a.z() + a.w() * a.w());
        double nb = Math.sqrt(b.x() * b.x() + b.y() * b.y() + b.z() * b.z() + b.w() * b.w());
        double cos = na > 0.0 && nb > 0.0 ? Math.min(1.0, dot / (na * nb)) : 1.0;
        return Math.toDegrees(2.0 * Math.acos(cos));
    }
}

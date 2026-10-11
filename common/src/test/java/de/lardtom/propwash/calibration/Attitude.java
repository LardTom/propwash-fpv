package de.lardtom.propwash.calibration;

public final class Attitude {
    static final double QUATERNION_SCALE = 0x7FFF;
    static final double ACC_TIME_CONSTANT_S = 0.6;
    static final double ACC_TRUST_LOW = 3.0 / 4.0;
    static final double ACC_TRUST_HIGH = 4.0 / 3.0;
    static final double MAX_DT = 0.01;

    private Attitude() {
    }

    public static double[] tiltDegrees(LogHeader h, LogData d) {
        if (d.has("imuQuaternion[0]") && d.has("imuQuaternion[1]")) {
            double[] qx = d.column("imuQuaternion[0]");
            double[] qy = d.column("imuQuaternion[1]");
            double[] out = new double[d.rows()];
            for (int r = 0; r < out.length; r++) {
                double x = qx[r] / QUATERNION_SCALE;
                double y = qy[r] / QUATERNION_SCALE;
                out[r] = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, 1.0 - 2.0 * (x * x + y * y)))));
            }
            return out;
        }
        if (!d.has("gyroADC[2]") || !d.has("accSmooth[2]") || Double.isNaN(h.acc1G()) || !d.has("time")) {
            return null;
        }
        return complementary(d.column("time"), d.column("gyroADC[0]"), d.column("gyroADC[1]"), d.column("gyroADC[2]"),
                d.column("accSmooth[0]"), d.column("accSmooth[1]"), d.column("accSmooth[2]"), h.acc1G(), h.gyroScale());
    }

    static double[] complementary(double[] time, double[] gx, double[] gy, double[] gz, double[] ax, double[] ay, double[] az, double acc1g,
                                  double gyroScale) {
        double[] out = new double[time.length];
        double[] down = {0.0, 0.0, acc1g};
        double radPerUnit = Math.toRadians(gyroScale);
        for (int r = 0; r < time.length; r++) {
            double dt = r == 0 ? 0.0 : Math.max(0.0, Math.min(MAX_DT, (time[r] - time[r - 1]) * 1e-6));
            turnAgainst(down, gx[r] * radPerUnit * dt, gy[r] * radPerUnit * dt, gz[r] * radPerUnit * dt);
            double load = Math.sqrt(ax[r] * ax[r] + ay[r] * ay[r] + az[r] * az[r]) / acc1g;
            if (load >= ACC_TRUST_LOW && load <= ACC_TRUST_HIGH && dt > 0.0) {
                double blend = 1.0 - Math.exp(-dt / ACC_TIME_CONSTANT_S);
                down[0] += blend * (ax[r] - down[0]);
                down[1] += blend * (ay[r] - down[1]);
                down[2] += blend * (az[r] - down[2]);
            }
            double length = Math.sqrt(down[0] * down[0] + down[1] * down[1] + down[2] * down[2]);
            out[r] = length > 0.0 ? Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, down[2] / length)))) : Double.NaN;
        }
        return out;
    }

    static void turnAgainst(double[] v, double rx, double ry, double rz) {
        double angle = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (angle == 0.0) {
            return;
        }
        double kx = rx / angle;
        double ky = ry / angle;
        double kz = rz / angle;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double along = (kx * v[0] + ky * v[1] + kz * v[2]) * (1.0 - cos);
        double crossX = ky * v[2] - kz * v[1];
        double crossY = kz * v[0] - kx * v[2];
        double crossZ = kx * v[1] - ky * v[0];
        double x = v[0] * cos - crossX * sin + kx * along;
        double y = v[1] * cos - crossY * sin + ky * along;
        double z = v[2] * cos - crossZ * sin + kz * along;
        v[0] = x;
        v[1] = y;
        v[2] = z;
    }
}

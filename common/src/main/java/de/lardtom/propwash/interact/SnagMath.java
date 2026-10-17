package de.lardtom.propwash.interact;

import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.SnagField;

public final class SnagMath {
    public static final float WEAR_SLACK = 1.5F;
    public static final float MAX_WEAR_PER_PACKET = 0.1F;
    public static final double CAUGHT_SPEED = 1.0;
    public static final double CAUGHT_HOLD = 1.0;

    private SnagMath() {
    }

    public static void fill(double[][] exposure, SnagField out) {
        out.clear();
        double hold = 0.0;
        for (int i = 0; i < SnagField.POINTS; i++) {
            for (SnagMaterial m : SnagMaterial.VALUES) {
                double e = exposure[i][m.ordinal()];
                if (!(e > 0.0)) {
                    continue;
                }
                out.linear[i] += e * m.linear() / SnagField.POINTS;
                out.quadratic[i] += e * m.quadratic() / SnagField.POINTS;
                hold += e * m.hold() / SnagField.POINTS;
                if (i < AirframeSpec.MOTORS) {
                    out.propLoad[i] += e * m.propLoad();
                }
            }
        }
        out.hold = hold;
    }

    public static double segmentFraction(double x0, double y0, double z0, double x1, double y1, double z1,
                                         double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double[] range = new double[2];
        return clip(x0, y0, z0, x1, y1, z1, minX, minY, minZ, maxX, maxY, maxZ, range) ? range[1] - range[0] : 0.0;
    }

    public static boolean clip(double x0, double y0, double z0, double x1, double y1, double z1,
                               double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double[] range) {
        double lo = 0.0;
        double hi = 1.0;
        double dx = x1 - x0;
        if (Math.abs(dx) < 1.0E-12) {
            if (x0 < minX || x0 > maxX) {
                return false;
            }
        } else {
            double a = (minX - x0) / dx;
            double b = (maxX - x0) / dx;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        double dy = y1 - y0;
        if (Math.abs(dy) < 1.0E-12) {
            if (y0 < minY || y0 > maxY) {
                return false;
            }
        } else {
            double a = (minY - y0) / dy;
            double b = (maxY - y0) / dy;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        double dz = z1 - z0;
        if (Math.abs(dz) < 1.0E-12) {
            if (z0 < minZ || z0 > maxZ) {
                return false;
            }
        } else {
            double a = (minZ - z0) / dz;
            double b = (maxZ - z0) / dz;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        if (!(hi > lo)) {
            return false;
        }
        range[0] = lo;
        range[1] = hi;
        return true;
    }

    public static double unionLength(double[] lo, double[] hi, int count) {
        for (int i = 1; i < count; i++) {
            double l = lo[i];
            double h = hi[i];
            int j = i - 1;
            while (j >= 0 && lo[j] > l) {
                lo[j + 1] = lo[j];
                hi[j + 1] = hi[j];
                j--;
            }
            lo[j + 1] = l;
            hi[j + 1] = h;
        }
        double total = 0.0;
        double start = Double.NaN;
        double end = Double.NaN;
        for (int i = 0; i < count; i++) {
            if (Double.isNaN(start) || lo[i] > end) {
                if (!Double.isNaN(start)) {
                    total += end - start;
                }
                start = lo[i];
                end = hi[i];
            } else {
                end = Math.max(end, hi[i]);
            }
        }
        if (!Double.isNaN(start)) {
            total += end - start;
        }
        return Math.min(1.0, total);
    }

    public static void addWear(double[][] exposure, double[] rpmFraction, double seconds, double[] wear) {
        if (!(seconds > 0.0)) {
            return;
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double rpm = rpmFraction[i];
            if (!(rpm > 0.0)) {
                continue;
            }
            double rate = 0.0;
            for (SnagMaterial m : SnagMaterial.VALUES) {
                double e = exposure[i][m.ordinal()];
                if (e > 0.0) {
                    rate += e * m.propWear();
                }
            }
            wear[i] += rate * Math.min(1.0, rpm) * seconds;
        }
    }

    public static float wearAllowance(double seconds) {
        double allowed = SnagMaterial.maxPropWear() * Math.max(1.0, seconds) * WEAR_SLACK;
        return (float) Math.min(MAX_WEAR_PER_PACKET, Math.max(0.0, allowed));
    }

    public static boolean caught(double speed, double hold) {
        return hold >= CAUGHT_HOLD && speed < CAUGHT_SPEED;
    }
}

package de.lardtom.propwash.build;

import java.util.Optional;

public record AccessoryAnchor(double x, double y, double z, double tiltDegrees, double lengthMm) {
    public static final double MIN_TILT = -45.0;
    public static final double MAX_TILT = 90.0;

    public static AccessoryAnchor of(FrameType frame, AccessoryMount mount) {
        return table(frame, mount).orElseGet(() -> fallback(frame, mount));
    }

    public static Optional<AccessoryAnchor> table(FrameType frame, AccessoryMount mount) {
        return frame.accessoryAnchor(mount);
    }

    public static AccessoryAnchor fallback(FrameType frame, AccessoryMount mount) {
        double wheelbase = frame.wheelbaseMm;
        return switch (mount) {
            case TOP -> new AccessoryAnchor(0.0, 0.16 * wheelbase, -0.23 * wheelbase, 15.0, 0.0);
            case BOTTOM -> new AccessoryAnchor(0.0, -3.0, -0.25 * wheelbase, 30.0, 0.15 * wheelbase);
        };
    }

    public double[] rotate(double lx, double ly, double lz) {
        return rotate(this.tiltDegrees, lx, ly, lz);
    }

    private static double[] rotate(double tiltDegrees, double lx, double ly, double lz) {
        double tilt = Math.toRadians(tiltDegrees);
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        return new double[]{lx, ly * cos - lz * sin, ly * sin + lz * cos};
    }

    public double[] toFrame(double lx, double ly, double lz) {
        return this.toFrame(this.tiltDegrees, lx, ly, lz);
    }

    private double[] toFrame(double tiltDegrees, double lx, double ly, double lz) {
        double[] turned = rotate(tiltDegrees, lx, ly, lz);
        return new double[]{this.x + turned[0], this.y + turned[1], this.z + turned[2]};
    }

    public double[] toFrame(double[] local) {
        return this.toFrame(local[0], local[1], local[2]);
    }

    public double tiltFor(AccessoryType type) {
        return type.hangsStraight() ? 0.0 : this.tiltDegrees;
    }

    public double[] centerOf(AccessoryType type) {
        double[] local = type.centerMm();
        return this.toFrame(this.tiltFor(type), local[0], local[1], local[2]);
    }

    public double stretch(AccessoryType type) {
        if (type.mount == AccessoryMount.BOTTOM && !type.hangsStraight() && this.lengthMm > 0.0 && type.widthMm > 0.0) {
            return this.lengthMm / type.widthMm;
        }
        return 1.0;
    }

    public double[][] corners(AccessoryType type) {
        double[] min = type.boxMinMm();
        double[] max = type.boxMaxMm();
        double stretch = this.stretch(type);
        double tilt = this.tiltFor(type);
        double[][] out = new double[8][];
        for (int c = 0; c < 8; c++) {
            double lx = ((c & 1) == 0 ? min[0] : max[0]) * stretch;
            double ly = (c & 2) == 0 ? min[1] : max[1];
            double lz = (c & 4) == 0 ? min[2] : max[2];
            out[c] = this.toFrame(tilt, lx, ly, lz);
        }
        return out;
    }

    public double[] ledMm(AccessoryType type, int index) {
        return this.toFrame(type.ledMm(index, this.lengthMm > 0.0 ? this.lengthMm : type.widthMm));
    }

    public double[] ledDirection() {
        return this.rotate(0.0, -1.0, 0.0);
    }

    public double[] lensMm(AccessoryType type) {
        return this.toFrame(type.lensMm());
    }

    public double[] viewDirection() {
        return this.rotate(0.0, 0.0, -1.0);
    }
}

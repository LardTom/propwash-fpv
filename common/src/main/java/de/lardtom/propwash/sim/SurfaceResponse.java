package de.lardtom.propwash.sim;

public record SurfaceResponse(double restitution, double grip, double impactScale) {
    public static final double DRONE_RESTITUTION = -1.0;
    public static final SurfaceResponse NORMAL = new SurfaceResponse(DRONE_RESTITUTION, 1.0, 1.0);
    public static final SurfaceResponse BOUNCY = new SurfaceResponse(0.8, 1.0, 0.0);
    public static final SurfaceResponse CUSHION = new SurfaceResponse(0.05, 1.5, 0.0);
    public static final SurfaceResponse STICKY = new SurfaceResponse(0.0, 1.5, 0.0);
    static final double MIN_GRIP = 0.05;
    static final double MAX_GRIP = 1.5;

    public static SurfaceResponse normal(double friction) {
        return new SurfaceResponse(DRONE_RESTITUTION, gripOf(friction), 1.0);
    }

    public static double gripOf(double friction) {
        return Math.max(MIN_GRIP, Math.min(MAX_GRIP, (1.0 - friction) / 0.4));
    }

    public static double impactScale(double value) {
        return SimMath.sanitize(value, 0.0, 1.0, 1.0);
    }

    public void writeTo(MoveReport report) {
        report.surfaceRestitution = this.restitution;
        report.surfaceGrip = this.grip;
        report.surfaceImpactScale = this.impactScale;
    }

    public boolean softerThan(SurfaceResponse other) {
        double a = impactScale(this.impactScale);
        double b = impactScale(other.impactScale);
        if (a != b) {
            return a < b;
        }
        return bounceRank(this.restitution) < bounceRank(other.restitution);
    }

    private static double bounceRank(double restitution) {
        return Double.isNaN(restitution) || restitution < 0.0 ? Double.POSITIVE_INFINITY : restitution;
    }

    public static final class Mix {
        static final int KINDS = 8;
        static final double TIE = 1.0E-9;
        private final SurfaceResponse[] kinds = new SurfaceResponse[KINDS];
        private final double[] areas = new double[KINDS];
        private int count;

        public void clear() {
            for (int i = 0; i < this.count; i++) {
                this.kinds[i] = null;
            }
            this.count = 0;
        }

        public boolean isEmpty() {
            return this.count == 0;
        }

        public void add(SurfaceResponse response, double area) {
            if (response == null || !(area > 0.0) || Double.isInfinite(area)) {
                return;
            }
            for (int i = 0; i < this.count; i++) {
                if (this.kinds[i].equals(response)) {
                    this.areas[i] += area;
                    return;
                }
            }
            if (this.count < KINDS) {
                this.kinds[this.count] = response;
                this.areas[this.count] = area;
                this.count++;
            }
        }

        public SurfaceResponse result() {
            SurfaceResponse best = null;
            double bestArea = 0.0;
            for (int i = 0; i < this.count; i++) {
                double area = this.areas[i];
                SurfaceResponse kind = this.kinds[i];
                if (best == null || area > bestArea + TIE || area >= bestArea - TIE && kind.softerThan(best)) {
                    best = kind;
                    bestArea = Math.max(bestArea, area);
                }
            }
            return best;
        }
    }
}

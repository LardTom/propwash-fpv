package de.lardtom.propwash.video;

public record FisheyeLens(double horizontalDeg, double strength, double bend, double imageWidth, double imageHeight, double windowWidth,
                          double windowHeight, double focal, double cornerRad, double circleRad, double tanX, double tanY) {
    public static final double MAX_FIELD_DEG = 80.0;
    public static final double EDGE_PX = 1.5;
    public static final int DEFAULT_PERCENT = 80;
    public static final int MAX_PERCENT = 100;
    public static final int PERCENT_STEP = 5;
    public static final double DEFAULT_STRENGTH = DEFAULT_PERCENT / 100.0;
    private static final double MIN_HORIZONTAL_DEG = 1.0;
    private static final double MAX_HORIZONTAL_DEG = 179.0;
    private static final double LINEAR_BEND = 1.0E-12;

    public static FisheyeLens of(double horizontalDeg, ImageArea area) {
        return of(horizontalDeg, DEFAULT_STRENGTH, area);
    }

    public static FisheyeLens of(double horizontalDeg, double strength, ImageArea area) {
        return of(horizontalDeg, strength, area.width(), area.height(), area.screenWidth(), area.screenHeight());
    }

    public static FisheyeLens of(double horizontalDeg, double imageWidth, double imageHeight, double windowWidth, double windowHeight) {
        return of(horizontalDeg, DEFAULT_STRENGTH, imageWidth, imageHeight, windowWidth, windowHeight);
    }

    public static FisheyeLens of(double horizontalDeg, double strength, double imageWidth, double imageHeight, double windowWidth,
                                 double windowHeight) {
        double h = Double.isFinite(horizontalDeg) ? Math.min(Math.max(horizontalDeg, MIN_HORIZONTAL_DEG), MAX_HORIZONTAL_DEG)
                : FovMath.DEFAULT_ANALOG_DEGREES;
        double s = clampStrength(strength);
        double bend = bend(s);
        double w = Math.max(1.0, imageWidth);
        double ih = Math.max(1.0, imageHeight);
        double ww = Math.max(w, windowWidth);
        double wh = Math.max(ih, windowHeight);
        double halfW = w * 0.5;
        double halfH = ih * 0.5;
        double focal = halfW / map(Math.toRadians(h * 0.5), bend);
        double cornerRadius = Math.hypot(halfW, halfH);
        double corner = unmap(cornerRadius / focal, bend);
        double circle = Math.min(corner, Math.toRadians(MAX_FIELD_DEG));
        double circleRadius = focal * map(circle, bend);
        double reach = Math.tan(circle) / circleRadius;
        double needX = reach * Math.min(circleRadius, halfW);
        double needY = reach * Math.min(circleRadius, halfH);
        double aspect = ww / wh;
        double tanY = Math.max(needY, needX / aspect);
        return new FisheyeLens(h, s, bend, w, ih, ww, wh, focal, corner, circle, tanY * aspect, tanY);
    }

    public static double clampStrength(double strength) {
        return Double.isFinite(strength) ? Math.min(Math.max(strength, 0.0), 1.0) : DEFAULT_STRENGTH;
    }

    public static double bend(double strength) {
        return 1.0 - clampStrength(strength) / DEFAULT_STRENGTH;
    }

    public static double map(double theta, double bend) {
        if (Math.abs(bend) < LINEAR_BEND) {
            return theta;
        }
        if (bend > 0.0) {
            double b = Math.sqrt(bend);
            return Math.tan(b * theta) / b;
        }
        double a = Math.sqrt(-bend);
        return Math.tanh(a * theta) / a;
    }

    public static double unmap(double radius, double bend) {
        if (Math.abs(bend) < LINEAR_BEND) {
            return radius;
        }
        if (bend > 0.0) {
            double b = Math.sqrt(bend);
            return Math.atan(b * radius) / b;
        }
        double a = Math.sqrt(-bend);
        double x = a * radius;
        if (!(x < 1.0)) {
            return Double.POSITIVE_INFINITY;
        }
        return 0.5 * Math.log((1.0 + x) / (1.0 - x)) / a;
    }

    public static double derivative(double theta, double bend) {
        if (Math.abs(bend) < LINEAR_BEND) {
            return 1.0;
        }
        if (bend > 0.0) {
            double c = Math.cos(Math.sqrt(bend) * theta);
            return 1.0 / (c * c);
        }
        double t = Math.tanh(Math.sqrt(-bend) * theta);
        return 1.0 - t * t;
    }

    public int percent() {
        return (int) Math.round(this.strength * 100.0);
    }

    public double renderVerticalDeg() {
        return Math.toDegrees(2.0 * Math.atan(this.tanY));
    }

    public double renderHorizontalDeg() {
        return Math.toDegrees(2.0 * Math.atan(this.tanX));
    }

    public double flatTangent() {
        return Math.tan(Math.toRadians(this.horizontalDeg) * 0.5);
    }

    public double flatFocal() {
        return this.imageWidth * 0.5 / this.flatTangent();
    }

    public double centreMagnification() {
        return this.focal / this.flatFocal();
    }

    public double sourceScale() {
        return this.windowWidth * 0.5 / this.tanX;
    }

    public double centreUpscale() {
        return this.focal / this.sourceScale();
    }

    public double radialFootprint(double theta) {
        double t = Math.tan(theta);
        return this.sourceScale() * (1.0 + t * t) / (this.focal * derivative(theta, this.bend));
    }

    public double tangentialFootprint(double theta) {
        if (theta < 1.0E-9) {
            return this.sourceScale() / this.focal;
        }
        return this.sourceScale() * Math.tan(theta) / (map(theta, this.bend) * this.focal);
    }

    public boolean fullImage() {
        return this.circleRad >= this.cornerRad - 1.0E-12;
    }

    public double circleRadius() {
        return this.focal * map(this.circleRad, this.bend);
    }

    public double centreEquivalentVerticalDeg() {
        return Math.toDegrees(2.0 * Math.atan(this.windowHeight * 0.5 / this.focal));
    }

    public boolean covers(double dx, double dy) {
        return Math.hypot(dx, dy) <= this.circleRadius();
    }

    public boolean tangentAt(double dx, double dy, double[] out) {
        double r = Math.hypot(dx, dy);
        double theta = unmap(r / this.focal, this.bend);
        if (!(theta < Math.PI * 0.5)) {
            return false;
        }
        double scale = r < 1.0E-12 ? 1.0 / this.focal : Math.tan(theta) / r;
        out[0] = dx * scale;
        out[1] = dy * scale;
        return true;
    }

    public boolean sourceAt(double dx, double dy, double[] out) {
        if (!this.tangentAt(dx, dy, out)) {
            return false;
        }
        double scale = this.sourceScale();
        out[0] *= scale;
        out[1] *= scale;
        return Math.abs(out[0]) <= this.windowWidth * 0.5 + 1.0E-6 && Math.abs(out[1]) <= this.windowHeight * 0.5 + 1.0E-6;
    }

    public boolean project(double x, double y, double depth, double[] out) {
        double lateral = Math.hypot(x, y);
        if (!(depth > 0.0) && lateral < 1.0E-12) {
            return false;
        }
        double theta = Math.atan2(lateral, depth);
        if (lateral < 1.0E-12) {
            out[0] = 0.0;
            out[1] = 0.0;
            return true;
        }
        double r = this.focal * map(theta, this.bend) / lateral;
        out[0] = x * r;
        out[1] = y * r;
        return theta < Math.PI * 0.5;
    }

    public static double verticalDeg(double horizontalDeg, double aspect) {
        return verticalDeg(horizontalDeg, aspect, DEFAULT_STRENGTH);
    }

    public static double diagonalDeg(double horizontalDeg, double aspect) {
        return diagonalDeg(horizontalDeg, aspect, DEFAULT_STRENGTH);
    }

    public static double verticalDeg(double horizontalDeg, double aspect, double strength) {
        double ratio = aspect > 1.0E-3 && Double.isFinite(aspect) ? aspect : 16.0 / 9.0;
        return spanDeg(horizontalDeg, 1.0 / ratio, strength);
    }

    public static double diagonalDeg(double horizontalDeg, double aspect, double strength) {
        double ratio = aspect > 1.0E-3 && Double.isFinite(aspect) ? aspect : 16.0 / 9.0;
        return spanDeg(horizontalDeg, Math.sqrt(1.0 + 1.0 / (ratio * ratio)), strength);
    }

    private static double spanDeg(double horizontalDeg, double share, double strength) {
        double bend = bend(strength);
        if (Math.abs(bend) < LINEAR_BEND) {
            return Math.min(horizontalDeg * share, 2.0 * MAX_FIELD_DEG);
        }
        double radius = map(Math.toRadians(horizontalDeg * 0.5), bend) * share;
        return Math.min(2.0 * Math.toDegrees(unmap(radius, bend)), 2.0 * MAX_FIELD_DEG);
    }
}

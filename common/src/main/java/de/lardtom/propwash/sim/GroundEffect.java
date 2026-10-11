package de.lardtom.propwash.sim;

final class GroundEffect {
    private GroundEffect() {
    }

    static double factor(double radius, double height, double spacing) {
        if (!(height < Double.POSITIVE_INFINITY)) {
            return 1.0;
        }
        double z = Math.max(height, 0.5 * radius);
        double r2 = radius * radius;
        double a = radius / (4.0 * z);
        double d2 = spacing * spacing;
        double z2 = 4.0 * z * z;
        double t1 = d2 + z2;
        double t2 = 2.0 * d2 + z2;
        double den = 1.0 - a * a - r2 * z / Math.sqrt(t1 * t1 * t1) - 0.5 * r2 * z / Math.sqrt(t2 * t2 * t2);
        if (den <= 1.0 / ModelConstants.GE_CAP) {
            return ModelConstants.GE_CAP;
        }
        return 1.0 / den;
    }
}

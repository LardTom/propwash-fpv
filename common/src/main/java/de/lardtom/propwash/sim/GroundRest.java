package de.lardtom.propwash.sim;

final class GroundRest {
    static final double LEVEL = 0.02;
    static final double EDGE = 0.06;

    private GroundRest() {
    }

    static double gain(double ux, double uz) {
        double lean = Math.sqrt(ux * ux + uz * uz);
        if (!(lean > LEVEL)) {
            return 1.0;
        }
        double blend = SimMath.smoothstep(LEVEL, EDGE, lean);
        return 1.0 + blend * (1.0 / lean - 1.0);
    }
}

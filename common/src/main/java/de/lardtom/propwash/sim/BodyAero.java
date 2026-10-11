package de.lardtom.propwash.sim;

final class BodyAero {
    double fx;
    double fy;
    double fz;

    void drag(AirframeSpec p, double rho, double scale, double ux, double uy, double uz, double h) {
        this.drag(p, rho, scale, ux, uy, uz, h, 1.0);
    }

    void drag(AirframeSpec p, double rho, double scale, double ux, double uy, double uz, double h, double topScale) {
        double speed = SimMath.hypot3(ux, uy, uz);
        double k = 0.5 * rho * p.dragCoefficient * scale * speed;
        double hm = h / p.massKg;
        double kx = k * p.areaX;
        double ky = k * p.areaY * topScale;
        double kz = k * p.areaZ;
        this.fx = -kx * ux / (1.0 + hm * kx);
        this.fy = -ky * uy / (1.0 + hm * ky);
        this.fz = -kz * uz / (1.0 + hm * kz);
    }
}

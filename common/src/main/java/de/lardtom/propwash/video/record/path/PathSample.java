package de.lardtom.propwash.video.record.path;

public record PathSample(double tick, double x, double y, double z, double qx, double qy, double qz, double qw, double fov) {
    public boolean finite() {
        double norm = this.qx * this.qx + this.qy * this.qy + this.qz * this.qz + this.qw * this.qw;
        return Double.isFinite(this.tick) && Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z)
                && Double.isFinite(norm) && norm > 1.0E-12 && Double.isFinite(this.fov) && this.fov > 0.0;
    }
}

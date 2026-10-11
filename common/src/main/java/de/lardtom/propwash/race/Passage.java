package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

public record Passage(double ox, double oy, double oz, double sx, double sy, double sz, double ux, double uy, double uz, double nx, double ny,
                      double nz, double side0, double side1, double up0, double up1, @Nullable FreeformRegion cells) {
    public static final double NONE = -1.0;

    public Passage(double ox, double oy, double oz, double sx, double sy, double sz, double ux, double uy, double uz, double nx, double ny,
                   double nz, double side0, double side1, double up0, double up1) {
        this(ox, oy, oz, sx, sy, sz, ux, uy, uz, nx, ny, nz, side0, side1, up0, up1, null);
    }

    public double distance(double px, double py, double pz) {
        return (px - this.ox) * this.nx + (py - this.oy) * this.ny + (pz - this.oz) * this.nz;
    }

    public double side(double px, double py, double pz) {
        return (px - this.ox) * this.sx + (py - this.oy) * this.sy + (pz - this.oz) * this.sz;
    }

    public double up(double px, double py, double pz) {
        return (px - this.ox) * this.ux + (py - this.oy) * this.uy + (pz - this.oz) * this.uz;
    }

    public boolean inside(double side, double up) {
        FreeformRegion region = this.cells;
        if (region != null) {
            return side >= this.side0 && side < this.side1 && up >= this.up0 && up < this.up1
                    && region.contains((int) Math.floor(side - this.side0), (int) Math.floor(up - this.up0));
        }
        return side > this.side0 && side < this.side1 && up > this.up0 && up < this.up1;
    }

    public double crossing(double x0, double y0, double z0, double x1, double y1, double z1, boolean forward) {
        double d0 = this.distance(x0, y0, z0);
        double d1 = this.distance(x1, y1, z1);
        boolean crosses = forward ? d0 < 0.0 && d1 >= 0.0 : d0 > 0.0 && d1 <= 0.0;
        if (!crosses) {
            return NONE;
        }
        double t = d0 / (d0 - d1);
        double hx = x0 + (x1 - x0) * t;
        double hy = y0 + (y1 - y0) * t;
        double hz = z0 + (z1 - z0) * t;
        return this.inside(this.side(hx, hy, hz), this.up(hx, hy, hz)) ? t : NONE;
    }

    public double centerX() {
        double s = (this.side0 + this.side1) * 0.5;
        double u = (this.up0 + this.up1) * 0.5;
        return this.ox + this.sx * s + this.ux * u;
    }

    public double centerY() {
        double s = (this.side0 + this.side1) * 0.5;
        double u = (this.up0 + this.up1) * 0.5;
        return this.oy + this.sy * s + this.uy * u;
    }

    public double centerZ() {
        double s = (this.side0 + this.side1) * 0.5;
        double u = (this.up0 + this.up1) * 0.5;
        return this.oz + this.sz * s + this.uz * u;
    }
}

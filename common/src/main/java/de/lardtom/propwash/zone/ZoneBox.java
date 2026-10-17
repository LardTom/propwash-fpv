package de.lardtom.propwash.zone;

import com.mojang.serialization.Codec;

import java.util.List;

public record ZoneBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public static final Codec<ZoneBox> CODEC = Codec.DOUBLE.listOf(6, 6).xmap(
            list -> new ZoneBox(list.get(0), list.get(1), list.get(2), list.get(3), list.get(4), list.get(5)),
            box -> List.of(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));

    public ZoneBox {
        double x0 = Math.min(minX, maxX);
        double y0 = Math.min(minY, maxY);
        double z0 = Math.min(minZ, maxZ);
        double x1 = Math.max(minX, maxX);
        double y1 = Math.max(minY, maxY);
        double z1 = Math.max(minZ, maxZ);
        minX = x0;
        minY = y0;
        minZ = z0;
        maxX = x1;
        maxY = y1;
        maxZ = z1;
    }

    public static ZoneBox blocks(int ax, int ay, int az, int bx, int by, int bz) {
        return new ZoneBox(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx) + 1.0, Math.max(ay, by) + 1.0,
                Math.max(az, bz) + 1.0);
    }

    public static ZoneBox cylinder(double centerX, double bottomY, double centerZ, double radius, double height) {
        return new ZoneBox(centerX - radius, bottomY, centerZ - radius, centerX + radius, bottomY + height, centerZ + radius);
    }

    public static ZoneBox around(double centerX, double bottomY, double centerZ, double sizeX, double sizeY, double sizeZ) {
        return new ZoneBox(centerX - sizeX / 2.0, bottomY, centerZ - sizeZ / 2.0, centerX + sizeX / 2.0, bottomY + sizeY, centerZ + sizeZ / 2.0);
    }

    public double sizeX() {
        return this.maxX - this.minX;
    }

    public double sizeY() {
        return this.maxY - this.minY;
    }

    public double sizeZ() {
        return this.maxZ - this.minZ;
    }

    public double centerX() {
        return (this.minX + this.maxX) / 2.0;
    }

    public double centerY() {
        return (this.minY + this.maxY) / 2.0;
    }

    public double centerZ() {
        return (this.minZ + this.maxZ) / 2.0;
    }

    public double radius() {
        return Math.min(this.sizeX(), this.sizeZ()) / 2.0;
    }

    public boolean finite() {
        return Double.isFinite(this.minX) && Double.isFinite(this.minY) && Double.isFinite(this.minZ) && Double.isFinite(this.maxX)
                && Double.isFinite(this.maxY) && Double.isFinite(this.maxZ);
    }

    public boolean contains(ZoneShape shape, double x, double y, double z) {
        if (y < this.minY || y > this.maxY || x < this.minX || x > this.maxX || z < this.minZ || z > this.maxZ) {
            return false;
        }
        if (shape == ZoneShape.BOX) {
            return true;
        }
        double dx = x - this.centerX();
        double dz = z - this.centerZ();
        double r = this.radius();
        return dx * dx + dz * dz <= r * r;
    }

    public double distanceSq(ZoneShape shape, double x, double y, double z) {
        double dy = y < this.minY ? this.minY - y : y > this.maxY ? y - this.maxY : 0.0;
        if (shape == ZoneShape.CYLINDER) {
            double dx = x - this.centerX();
            double dz = z - this.centerZ();
            double out = Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - this.radius());
            return out * out + dy * dy;
        }
        double dx = x < this.minX ? this.minX - x : x > this.maxX ? x - this.maxX : 0.0;
        double dz = z < this.minZ ? this.minZ - z : z > this.maxZ ? z - this.maxZ : 0.0;
        return dx * dx + dy * dy + dz * dz;
    }

    public ZoneBox moved(double dx, double dy, double dz) {
        return new ZoneBox(this.minX + dx, this.minY + dy, this.minZ + dz, this.maxX + dx, this.maxY + dy, this.maxZ + dz);
    }

    public ZoneBox sized(double sizeX, double sizeY, double sizeZ) {
        return new ZoneBox(this.minX, this.minY, this.minZ, this.minX + sizeX, this.minY + sizeY, this.minZ + sizeZ);
    }

    public ZoneBox squared() {
        double size = Math.max(this.sizeX(), this.sizeZ());
        return around(this.centerX(), this.minY, this.centerZ(), size, this.sizeY(), size);
    }
}

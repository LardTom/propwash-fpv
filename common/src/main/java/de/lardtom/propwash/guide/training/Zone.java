package de.lardtom.propwash.guide.training;

public record Zone(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Kind kind) {
    public enum Kind {
        HOVER,
        TRICK
    }

    public Zone {
        if (!(maxX > minX) || !(maxY > minY) || !(maxZ > minZ)) {
            throw new IllegalArgumentException("Leere Zone");
        }
    }

    public static Zone hover(double x, double y, double z, double halfWidth, double halfHeight, double halfDepth) {
        return new Zone(x - halfWidth, y - halfHeight, z - halfDepth, x + halfWidth, y + halfHeight, z + halfDepth, Kind.HOVER);
    }

    public static Zone trick(double x, double z, double half, double bottom, double top) {
        return new Zone(x - half, bottom, z - half, x + half, top, z + half, Kind.TRICK);
    }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    public double centerX() {
        return (this.minX + this.maxX) * 0.5;
    }

    public double centerY() {
        return (this.minY + this.maxY) * 0.5;
    }

    public double centerZ() {
        return (this.minZ + this.maxZ) * 0.5;
    }
}

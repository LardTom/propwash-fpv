package de.lardtom.propwash.bando.plan;

public record BandoTransform(int originX, int originY, int originZ, int turns, int sizeX, int sizeZ) {
    public BandoTransform {
        turns = Math.floorMod(turns, 4);
    }

    public static BandoTransform centered(BandoPlan plan, int turns, int centerX, int originY, int centerZ) {
        int t = Math.floorMod(turns, 4);
        int wx = (t & 1) == 0 ? plan.sizeX() : plan.sizeZ();
        int wz = (t & 1) == 0 ? plan.sizeZ() : plan.sizeX();
        return new BandoTransform(centerX - wx / 2, originY, centerZ - wz / 2, t, plan.sizeX(), plan.sizeZ());
    }

    public int widthX() {
        return (this.turns & 1) == 0 ? this.sizeX : this.sizeZ;
    }

    public int widthZ() {
        return (this.turns & 1) == 0 ? this.sizeZ : this.sizeX;
    }

    public int worldX(int x, int z) {
        return this.originX + switch (this.turns) {
            case 1 -> this.sizeZ - 1 - z;
            case 2 -> this.sizeX - 1 - x;
            case 3 -> z;
            default -> x;
        };
    }

    public int worldZ(int x, int z) {
        return this.originZ + switch (this.turns) {
            case 1 -> x;
            case 2 -> this.sizeZ - 1 - z;
            case 3 -> this.sizeX - 1 - x;
            default -> z;
        };
    }

    public int worldY(int y) {
        return this.originY + y;
    }

    public int localX(int worldX, int worldZ) {
        int rx = worldX - this.originX;
        int rz = worldZ - this.originZ;
        return switch (this.turns) {
            case 1 -> rz;
            case 2 -> this.sizeX - 1 - rx;
            case 3 -> this.sizeX - 1 - rz;
            default -> rx;
        };
    }

    public int localZ(int worldX, int worldZ) {
        int rx = worldX - this.originX;
        int rz = worldZ - this.originZ;
        return switch (this.turns) {
            case 1 -> this.sizeZ - 1 - rx;
            case 2 -> this.sizeZ - 1 - rz;
            case 3 -> rx;
            default -> rz;
        };
    }
}

package de.lardtom.propwash.race.world;

public record WorldCourseSite(int originX, int originY, int originZ, int quarterTurns) {
    public WorldCourseSite {
        quarterTurns = Math.floorMod(quarterTurns, 4);
    }

    public static int quarterTurns(float localYaw, float worldYaw) {
        return Math.floorMod(Math.round((worldYaw - localYaw) / 90.0F), 4);
    }

    public static double[] rotateEntity(double x, double y, double z, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new double[]{1.0 - z, y, x};
            case 2 -> new double[]{1.0 - x, y, 1.0 - z};
            case 3 -> new double[]{z, y, 1.0 - x};
            default -> new double[]{x, y, z};
        };
    }

    public static int[] rotateBlock(int x, int y, int z, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new int[]{-z, y, x};
            case 2 -> new int[]{-x, y, -z};
            case 3 -> new int[]{z, y, -x};
            default -> new int[]{x, y, z};
        };
    }

    public static WorldCourseSite locate(double localX, double localY, double localZ, float localYaw,
                                         double worldX, double worldY, double worldZ, float worldYaw) {
        int turns = quarterTurns(localYaw, worldYaw);
        double[] rotated = rotateEntity(localX, localY, localZ, turns);
        return new WorldCourseSite((int) Math.round(worldX - rotated[0]), (int) Math.round(worldY - rotated[1]),
                (int) Math.round(worldZ - rotated[2]), turns);
    }

    public int[] blockToWorld(int x, int y, int z) {
        int[] rotated = rotateBlock(x, y, z, this.quarterTurns);
        return new int[]{this.originX + rotated[0], this.originY + rotated[1], this.originZ + rotated[2]};
    }

    public double[] entityToWorld(double x, double y, double z) {
        double[] rotated = rotateEntity(x, y, z, this.quarterTurns);
        return new double[]{this.originX + rotated[0], this.originY + rotated[1], this.originZ + rotated[2]};
    }

    public static boolean acceptable(int[] sortedHeights, int maxSlope, int minY, int maxY) {
        if (sortedHeights.length == 0) {
            return false;
        }
        int low = sortedHeights[0];
        int high = sortedHeights[sortedHeights.length - 1];
        return high - low <= maxSlope && low > minY && high < maxY;
    }

    public String key() {
        return this.originX + "," + this.originY + "," + this.originZ;
    }
}

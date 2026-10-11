package de.lardtom.propwash.rescue;

public interface RescueWorld {
    int UNKNOWN_SURFACE = Integer.MIN_VALUE;

    boolean solid(int x, int y, int z);

    int surfaceY(int x, int z);

    int minY();

    int maxY();

    default Ground ground(int x, int y, int z) {
        return this.solid(x, y, z) ? Ground.SOLID : Ground.NONE;
    }
}

package de.lardtom.propwash.sim;

final class FcAxes {
    static final int ROLL = 0;
    static final int PITCH = 1;
    static final int YAW = 2;

    private FcAxes() {
    }

    static double rollDps(double bodyZ) {
        return bodyZ * SimMath.DEG;
    }

    static double pitchDps(double bodyX) {
        return bodyX * SimMath.DEG;
    }

    static double yawDps(double bodyY) {
        return -bodyY * SimMath.DEG;
    }
}

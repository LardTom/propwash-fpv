package de.lardtom.propwash.bando.plan;

public final class Meta {
    public static final int AXIS_Y = 0;
    public static final int AXIS_X = 1;
    public static final int AXIS_Z = 2;

    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;
    public static final int TOP = 4;
    public static final int SOLID = 32;

    public static final int FACE_NORTH = 1;
    public static final int FACE_EAST = 2;
    public static final int FACE_SOUTH = 4;
    public static final int FACE_WEST = 8;
    public static final int FACE_UP = 16;

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    private Meta() {
    }

    public static int dx(int facing) {
        return DX[facing & 3];
    }

    public static int dz(int facing) {
        return DZ[facing & 3];
    }

    public static int opposite(int facing) {
        return (facing + 2) & 3;
    }

    public static int face(int facing) {
        return 1 << (facing & 3);
    }

    public static int rotateFacing(int facing, int quarterTurns) {
        return (facing + quarterTurns) & 3;
    }

    public static int rotateAxis(int axis, int quarterTurns) {
        if (axis == AXIS_Y || (quarterTurns & 1) == 0) {
            return axis;
        }
        return axis == AXIS_X ? AXIS_Z : AXIS_X;
    }

    public static int rotateFaces(int faces, int quarterTurns) {
        int turns = quarterTurns & 3;
        int horizontal = faces & 15;
        int rotated = ((horizontal << turns) | (horizontal >> (4 - turns))) & 15;
        return rotated | (faces & FACE_UP);
    }
}

package de.lardtom.propwash.drone.client.render;

public final class DroneVisualScale {
    public static final float MILLIMETRES_PER_MODEL_UNIT = 8.0F;
    public static final float MODEL_UNITS_PER_BLOCK = 16.0F;
    public static final float MILLIMETRES_PER_BLOCK = 1000.0F;
    public static final float MILLIMETRES_PER_MODEL_BLOCK = MILLIMETRES_PER_MODEL_UNIT * MODEL_UNITS_PER_BLOCK;
    public static final float MODEL_UNIT_SCALE = MILLIMETRES_PER_MODEL_BLOCK / MILLIMETRES_PER_BLOCK;
    public static final float MM_TO_UNITS = 1.0F / MILLIMETRES_PER_MODEL_BLOCK;

    private DroneVisualScale() {
    }

    public static float blocksPerMillimetre(float visualScale) {
        return visualScale / MILLIMETRES_PER_BLOCK;
    }

    public static float poseScale(float visualScale) {
        return MODEL_UNIT_SCALE * visualScale;
    }
}

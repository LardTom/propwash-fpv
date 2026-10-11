package de.lardtom.propwash.sim;

public record ImpactSample(float speed, float tangential, float normalX, float normalY, float normalZ, int contacts, int struck,
                           float strikeSpin) {
    public static final float MAX_SPEED = 150.0F;
    public static final float MAX_SPIN = 3.0F;
    public static final int CONTACT_MASK = (1 << AirframeSpec.HULL_POINTS) - 1;
    public static final int STRUCK_MASK = (1 << AirframeSpec.MOTORS) - 1;
    public static final int HULL_TIP = 0;
    public static final int HULL_MOTOR = 4;
    public static final int HULL_BATTERY_FRONT = 8;
    public static final int HULL_BATTERY_BACK = 9;
    public static final int HULL_ELECTRONICS = 10;
    public static final int HULL_BOTTOM = 11;

    public ImpactSample sanitized() {
        float nx = finite(this.normalX);
        float ny = finite(this.normalY);
        float nz = finite(this.normalZ);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1.0E-4F) {
            nx /= length;
            ny /= length;
            nz /= length;
        } else {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        }
        return new ImpactSample(clamp(this.speed, MAX_SPEED), clamp(this.tangential, MAX_SPEED), nx, ny, nz, this.contacts & CONTACT_MASK,
                this.struck & STRUCK_MASK, clamp(this.strikeSpin, MAX_SPIN));
    }

    public boolean touches(int hullPoint) {
        return (this.contacts & (1 << hullPoint)) != 0;
    }

    public boolean struck(int motor) {
        return (this.struck & (1 << motor)) != 0;
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float clamp(float value, float max) {
        if (!Float.isFinite(value) || value < 0.0F) {
            return 0.0F;
        }
        return Math.min(value, max);
    }
}

package de.lardtom.propwash.input.client.screen.remote;

public record RemoteProjection(float tiltDegrees, float pixelScale, float centerOffset, float centerX, float centerY, float pivotX) {
    public static final float PIVOT_Y = 7.75F;
    public static final float PIVOT_Z = 10.0F;

    public static final float STICK_DEFLECTION_DEGREES = 25.0F;

    public enum Region {
        WHOLE(15.0F, 1.5F, 14.5F, 0.5F, 15.0F, 7.0F, 13.25F),
        SWITCHES(30.0F, 1.5F, 14.5F, 7.5F, 12.25F, 7.0F, 10.5F),
        BUTTONS(30.0F, 3.5F, 12.5F, 2.5F, 9.75F, 9.0F, 10.5F);

        final float tiltDegrees;
        final float minX;
        final float maxX;
        final float minY;
        final float maxY;
        final float minZ;
        final float maxZ;

        Region(float tiltDegrees, float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
            this.tiltDegrees = tiltDegrees;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public float width() {
            return this.maxX - this.minX;
        }

        public float tiltDegrees() {
            return this.tiltDegrees;
        }
    }

    public static RemoteProjection fit(Region region, float x0, float y0, float x1, float y1) {
        return fit(region.tiltDegrees, region, x0, y0, x1, y1);
    }

    public static RemoteProjection fit(float tiltDegrees, Region region, float x0, float y0, float x1, float y1) {
        double tilt = Math.toRadians(tiltDegrees);
        float cos = (float) Math.cos(tilt);
        float sin = (float) Math.sin(tilt);
        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        for (float y : new float[]{region.minY, region.maxY}) {
            for (float z : new float[]{region.minZ, region.maxZ}) {
                float projected = (y - PIVOT_Y) * cos - (z - PIVOT_Z) * sin;
                low = Math.min(low, projected);
                high = Math.max(high, projected);
            }
        }
        float height = high - low;
        float scale = Math.max(0.1F, Math.min((x1 - x0) / region.width(), (y1 - y0) / height));
        return new RemoteProjection(tiltDegrees, scale, (low + high) * 0.5F, (x0 + x1) * 0.5F, (y0 + y1) * 0.5F,
                (region.minX + region.maxX) * 0.5F);
    }

    public RemoteProjection centeredAt(float x, float y) {
        return new RemoteProjection(this.tiltDegrees, this.pixelScale, this.centerOffset, x, y, this.pivotX);
    }

    public float screenX(float x, float y, float z) {
        return this.centerX + (x - this.pivotX) * this.pixelScale;
    }

    public float[] stickTip(float pivotX, float pivotY, float pivotZ, float length, float stickX, float stickY) {
        double pitch = Math.toRadians(stickY * STICK_DEFLECTION_DEGREES);
        double yaw = Math.toRadians(stickX * STICK_DEFLECTION_DEGREES);
        float tx = pivotX + (float) (length * Math.cos(pitch) * Math.sin(yaw));
        float ty = pivotY + (float) (length * Math.sin(pitch));
        float tz = pivotZ + (float) (length * Math.cos(pitch) * Math.cos(yaw));
        return new float[]{this.screenX(tx, ty, tz), this.screenY(tx, ty, tz)};
    }

    public float screenY(float x, float y, float z) {
        double tilt = Math.toRadians(this.tiltDegrees);
        float projected = (float) ((y - PIVOT_Y) * Math.cos(tilt) - (z - PIVOT_Z) * Math.sin(tilt));
        return this.centerY - (projected - this.centerOffset) * this.pixelScale;
    }
}

package de.lardtom.propwash.osd;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector3f;

public final class OsdProjection {
    private static final float MIN_DEPTH = 1.0E-4F;
    private static final float MIN_FOV_DEG = 1.0F;
    private static final float MAX_FOV_DEG = 179.0F;

    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf inverse = new Quaternionf();
    private final Vector3f scratch = new Vector3f();
    private float centerX;
    private float centerY;
    private float focal = 1.0F;
    private boolean valid;

    public OsdProjection setup(Quaternionfc cameraRotation, float verticalFovDeg, float centerX, float centerY, float viewHeight) {
        this.rotation.set(cameraRotation);
        float length = this.rotation.lengthSquared();
        this.valid = Float.isFinite(length) && length > 1.0E-6F && Float.isFinite(verticalFovDeg) && viewHeight > 0.0F;
        if (!this.valid) {
            this.rotation.identity();
        } else {
            this.rotation.normalize();
        }
        this.rotation.conjugate(this.inverse);
        float fov = Math.clamp(Float.isFinite(verticalFovDeg) ? verticalFovDeg : 70.0F, MIN_FOV_DEG, MAX_FOV_DEG);
        this.focal = (float) (viewHeight * 0.5 / Math.tan(Math.toRadians(fov) * 0.5));
        this.centerX = centerX;
        this.centerY = centerY;
        return this;
    }

    public static Quaternionf cameraRotation(double yRotDeg, double xRotDeg, double rollDeg, Quaternionf dest) {
        return dest.rotationYXZ((float) (Math.PI - Math.toRadians(yRotDeg)), (float) -Math.toRadians(xRotDeg), (float) -Math.toRadians(rollDeg));
    }

    public boolean valid() {
        return this.valid;
    }

    public float focal() {
        return this.focal;
    }

    public float centerX() {
        return this.centerX;
    }

    public float centerY() {
        return this.centerY;
    }

    public Vector3f toCamera(double dx, double dy, double dz, Vector3f dest) {
        return this.inverse.transform(dest.set((float) dx, (float) dy, (float) dz));
    }

    public boolean project(double dx, double dy, double dz, Vector2f out) {
        Vector3f c = this.toCamera(dx, dy, dz, this.scratch);
        float depth = -c.z;
        if (!(depth > MIN_DEPTH)) {
            return false;
        }
        out.set(this.centerX + this.focal * c.x / depth, this.centerY - this.focal * c.y / depth);
        return Float.isFinite(out.x) && Float.isFinite(out.y);
    }

    public float screenAngle(double dx, double dy, double dz) {
        Vector3f c = this.toCamera(dx, dy, dz, this.scratch);
        if (Math.abs(c.x) < 1.0E-6F && Math.abs(c.y) < 1.0E-6F) {
            return c.z > 0.0F ? (float) (Math.PI * 0.5) : 0.0F;
        }
        return (float) Math.atan2(-c.y, c.x);
    }

    public boolean horizon(Horizon out) {
        Vector3f n = this.toCamera(0.0, 1.0, 0.0, this.scratch);
        float planar = n.x * n.x + n.y * n.y;
        if (planar < 1.0E-8F) {
            return false;
        }
        float u0 = n.z * n.x / planar;
        float v0 = n.z * n.y / planar;
        out.x = this.centerX + this.focal * u0;
        out.y = this.centerY - this.focal * v0;
        out.angle = (float) Math.atan2(n.x, n.y);
        float inverseLength = (float) (1.0 / Math.sqrt(planar));
        out.skyX = n.x * inverseLength;
        out.skyY = -n.y * inverseLength;
        out.upsideDown = n.y < 0.0F;
        return Float.isFinite(out.x) && Float.isFinite(out.y);
    }

    public boolean horizonAzimuth(Horizon horizon, Vector3f dest) {
        float u = (horizon.x - this.centerX) / this.focal;
        float v = (this.centerY - horizon.y) / this.focal;
        this.rotation.transform(dest.set(u, v, -1.0F));
        dest.y = 0.0F;
        float length = dest.length();
        if (length < 1.0E-6F) {
            return false;
        }
        dest.div(length);
        return true;
    }

    public boolean projectElevation(Vector3f azimuth, double elevationDeg, Vector2f out) {
        double rad = Math.toRadians(elevationDeg);
        double horizontal = Math.cos(rad);
        return this.project(azimuth.x * horizontal, Math.sin(rad), azimuth.z * horizontal, out);
    }

    public static final class Horizon {
        public float x;
        public float y;
        public float angle;
        public float skyX;
        public float skyY;
        public boolean upsideDown;
    }
}

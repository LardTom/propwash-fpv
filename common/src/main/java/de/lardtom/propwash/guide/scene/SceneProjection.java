package de.lardtom.propwash.guide.scene;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class SceneProjection {
    private static final Quaternionfc FLIP = new Quaternionf().rotationX((float) Math.PI);

    private final Quaternionf view;
    private final ScenePos target;
    private final float pixelsPerBlock;
    private final float centerX;
    private final float centerY;
    private final Vector3f scratch = new Vector3f();

    public SceneProjection(CameraSpec camera, float basePixelsPerBlock, float centerX, float centerY) {
        this.view = new Quaternionf().rotationX((float) Math.toRadians(camera.pitch())).rotateY((float) Math.toRadians(-camera.yaw()));
        this.target = camera.target();
        this.pixelsPerBlock = basePixelsPerBlock * camera.zoom();
        this.centerX = centerX;
        this.centerY = centerY;
    }

    public static float basePixelsPerBlock(float width, float height, float viewRadius) {
        float radius = viewRadius > 0.0F ? viewRadius : 1.0F;
        return Math.max(1.0F, Math.min(width, height) * 0.5F / radius);
    }

    public Quaternionfc view() {
        return this.view;
    }

    public ScenePos target() {
        return this.target;
    }

    public float pixelsPerBlock() {
        return this.pixelsPerBlock;
    }

    public float centerX() {
        return this.centerX;
    }

    public float centerY() {
        return this.centerY;
    }

    public Vector3f eye(float x, float y, float z, Vector3f dest) {
        dest.set(x - this.target.x(), y - this.target.y(), z - this.target.z());
        return this.view.transform(dest);
    }

    public float[] project(ScenePos pos) {
        return this.project(pos.x(), pos.y(), pos.z(), new float[3]);
    }

    public float[] project(float x, float y, float z, float[] out) {
        Vector3f eye = this.eye(x, y, z, this.scratch);
        out[0] = this.centerX + eye.x * this.pixelsPerBlock;
        out[1] = this.centerY - eye.y * this.pixelsPerBlock;
        out[2] = eye.z;
        return out;
    }

    public void applyRaster(float left, float top, float pixelScale, Transform out) {
        float k = this.pixelsPerBlock * pixelScale;
        out.translate((this.centerX - left) * pixelScale, (this.centerY - top) * pixelScale, 0.0F);
        out.scale(k, k, -k);
        out.rotate(new Quaternionf(FLIP).mul(this.view));
        out.translate(-this.target.x(), -this.target.y(), -this.target.z());
    }

    public Matrix4f rasterMatrix(float left, float top, float pixelScale, Matrix4f dest) {
        dest.identity();
        this.applyRaster(left, top, pixelScale, new Transform() {
            @Override
            public void translate(float x, float y, float z) {
                dest.translate(x, y, z);
            }

            @Override
            public void scale(float x, float y, float z) {
                dest.scale(x, y, z);
            }

            @Override
            public void rotate(Quaternionfc rotation) {
                dest.rotate(rotation);
            }
        });
        return dest;
    }

    public static float[] rasterToGui(Matrix4f raster, float left, float top, float pixelScale, float x, float y, float z, float[] out) {
        Vector3f p = raster.transformPosition(x, y, z, new Vector3f());
        out[0] = left + p.x / pixelScale;
        out[1] = top + p.y / pixelScale;
        out[2] = p.z;
        return out;
    }

    public Vector3f viewDirection(Vector3f dest) {
        dest.set(0.0F, 0.0F, -1.0F);
        return this.view.transformInverse(dest);
    }

    public interface Transform {
        void translate(float x, float y, float z);

        void scale(float x, float y, float z);

        void rotate(Quaternionfc rotation);
    }
}

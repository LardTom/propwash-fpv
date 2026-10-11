package de.lardtom.propwash.drone.client.render;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Arrays;

public final class ShapeBuilder {
    public static final float GLOW_CORE_RATIO = 0.35F;
    public static final int GLOW_SEGMENTS = 12;
    private static final float[][] CUBE_FACES = {
            {0, 1, 0, -0.5F, 0.5F, -0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F},
            {0, -1, 0, -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, -0.5F, -0.5F, 0.5F},
            {0, 0, -1, -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, -0.5F, -0.5F},
            {0, 0, 1, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F},
            {-1, 0, 0, -0.5F, -0.5F, -0.5F, -0.5F, -0.5F, 0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, -0.5F},
            {1, 0, 0, 0.5F, -0.5F, -0.5F, 0.5F, 0.5F, -0.5F, 0.5F, 0.5F, 0.5F, 0.5F, -0.5F, 0.5F}
    };

    private final float unitScale;
    private float[] positions = new float[96];
    private float[] normals = new float[96];
    private int[] colors = new int[32];
    private int vertices;
    private final Vector3f scratch = new Vector3f();
    private final Vector3f scratchNormal = new Vector3f();
    private final Matrix3f normalMatrix = new Matrix3f();

    public ShapeBuilder(float unitScale) {
        this.unitScale = unitScale;
    }

    public static Matrix4f at(float x, float y, float z) {
        return new Matrix4f().translation(x, y, z);
    }

    public ShapeBuilder box(float cx, float cy, float cz, float sx, float sy, float sz, int color) {
        return this.cube(at(cx, cy, cz).scale(sx, sy, sz), color);
    }

    public ShapeBuilder boxBetween(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        return this.box((x0 + x1) * 0.5F, (y0 + y1) * 0.5F, (z0 + z1) * 0.5F, Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), color);
    }

    public ShapeBuilder cube(Matrix4fc transform, int color) {
        transform.normal(this.normalMatrix);
        for (float[] face : CUBE_FACES) {
            this.scratchNormal.set(face[0], face[1], face[2]);
            this.normalMatrix.transform(this.scratchNormal).normalize();
            for (int v = 0; v < 4; v++) {
                this.scratch.set(face[3 + v * 3], face[4 + v * 3], face[5 + v * 3]);
                transform.transformPosition(this.scratch);
                this.vertex(this.scratch, this.scratchNormal, color);
            }
        }
        return this;
    }

    public ShapeBuilder cylinder(Matrix4fc transform, int segments, int color) {
        transform.normal(this.normalMatrix);
        int n = Math.max(3, segments);
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        Vector3f d = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < n; i++) {
            double t0 = 2.0 * Math.PI * i / n;
            double t1 = 2.0 * Math.PI * (i + 1) / n;
            float x0 = (float) Math.cos(t0) * 0.5F;
            float z0 = (float) -Math.sin(t0) * 0.5F;
            float x1 = (float) Math.cos(t1) * 0.5F;
            float z1 = (float) -Math.sin(t1) * 0.5F;
            double mid = (t0 + t1) * 0.5;
            normal.set((float) Math.cos(mid), 0.0F, (float) -Math.sin(mid));
            this.normalMatrix.transform(normal).normalize();
            transform.transformPosition(a.set(x0, -0.5F, z0));
            transform.transformPosition(b.set(x1, -0.5F, z1));
            transform.transformPosition(c.set(x1, 0.5F, z1));
            transform.transformPosition(d.set(x0, 0.5F, z0));
            this.quad(a, b, c, d, normal, color);
            normal.set(0.0F, 1.0F, 0.0F);
            this.normalMatrix.transform(normal).normalize();
            transform.transformPosition(a.set(0.0F, 0.5F, 0.0F));
            transform.transformPosition(b.set(x0, 0.5F, z0));
            transform.transformPosition(c.set(x1, 0.5F, z1));
            this.quad(a, a, b, c, normal, color);
            normal.set(0.0F, -1.0F, 0.0F);
            this.normalMatrix.transform(normal).normalize();
            transform.transformPosition(a.set(0.0F, -0.5F, 0.0F));
            transform.transformPosition(b.set(x1, -0.5F, z1));
            transform.transformPosition(c.set(x0, -0.5F, z0));
            this.quad(a, a, b, c, normal, color);
        }
        return this;
    }

    public ShapeBuilder annulus(Matrix4fc transform, float innerRatio, int segments, int innerColor, int outerColor) {
        transform.normal(this.normalMatrix);
        int n = Math.max(3, segments);
        float inner = Math.max(0.0F, Math.min(1.0F, innerRatio)) * 0.5F;
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        Vector3f d = new Vector3f();
        Vector3f normal = new Vector3f(0.0F, 1.0F, 0.0F);
        this.normalMatrix.transform(normal).normalize();
        for (int i = 0; i < n; i++) {
            double t0 = 2.0 * Math.PI * i / n;
            double t1 = 2.0 * Math.PI * (i + 1) / n;
            float c0 = (float) Math.cos(t0);
            float s0 = (float) -Math.sin(t0);
            float c1 = (float) Math.cos(t1);
            float s1 = (float) -Math.sin(t1);
            transform.transformPosition(a.set(c0 * inner, 0.0F, s0 * inner));
            transform.transformPosition(b.set(c0 * 0.5F, 0.0F, s0 * 0.5F));
            transform.transformPosition(c.set(c1 * 0.5F, 0.0F, s1 * 0.5F));
            transform.transformPosition(d.set(c1 * inner, 0.0F, s1 * inner));
            this.vertex(a, normal, innerColor);
            this.vertex(b, normal, outerColor);
            this.vertex(c, normal, outerColor);
            this.vertex(d, normal, innerColor);
        }
        return this;
    }

    public ShapeBuilder glow(float cx, float cy, float cz, float radius, int core, int mid, int edge) {
        float diameter = radius * 2.0F;
        float inner = GLOW_CORE_RATIO * diameter;
        for (int plane = 0; plane < 3; plane++) {
            for (int side = -1; side <= 1; side += 2) {
                Matrix4f base = at(cx, cy, cz);
                if (plane == 1) {
                    base.rotateX((float) (Math.PI * 0.5));
                } else if (plane == 2) {
                    base.rotateZ((float) (Math.PI * 0.5));
                }
                this.annulus(new Matrix4f(base).scale(inner, side, inner), 0.0F, GLOW_SEGMENTS, core, mid);
                this.annulus(new Matrix4f(base).scale(diameter, side, diameter), GLOW_CORE_RATIO, GLOW_SEGMENTS, mid, edge);
            }
        }
        return this;
    }

    public boolean isEmpty() {
        return this.vertices == 0;
    }

    public Shape build() {
        if (this.vertices == 0) {
            return Shape.EMPTY;
        }
        float[] p = Arrays.copyOf(this.positions, this.vertices * 3);
        for (int i = 0; i < p.length; i++) {
            p[i] *= this.unitScale;
        }
        return new Shape(p, Arrays.copyOf(this.normals, this.vertices * 3), Arrays.copyOf(this.colors, this.vertices));
    }

    private void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f normal, int color) {
        this.vertex(a, normal, color);
        this.vertex(b, normal, color);
        this.vertex(c, normal, color);
        this.vertex(d, normal, color);
    }

    private void vertex(Vector3f position, Vector3f normal, int color) {
        if (this.vertices == this.colors.length) {
            int capacity = this.colors.length * 2;
            this.positions = Arrays.copyOf(this.positions, capacity * 3);
            this.normals = Arrays.copyOf(this.normals, capacity * 3);
            this.colors = Arrays.copyOf(this.colors, capacity);
        }
        int p = this.vertices * 3;
        this.positions[p] = position.x;
        this.positions[p + 1] = position.y;
        this.positions[p + 2] = position.z;
        this.normals[p] = normal.x;
        this.normals[p + 1] = normal.y;
        this.normals[p + 2] = normal.z;
        this.colors[this.vertices] = color;
        this.vertices++;
    }
}

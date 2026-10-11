package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Shape {
    public static final Shape EMPTY = new Shape(new float[0], new float[0], new int[0]);

    private final float[] positions;
    private final float[] normals;
    private final int[] colors;
    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();

    Shape(float[] positions, float[] normals, int[] colors) {
        this.positions = positions;
        this.normals = normals;
        this.colors = colors;
        if (positions.length == 0) {
            return;
        }
        this.min.set(Float.POSITIVE_INFINITY);
        this.max.set(Float.NEGATIVE_INFINITY);
        for (int i = 0; i < positions.length; i += 3) {
            this.min.min(new Vector3f(positions[i], positions[i + 1], positions[i + 2]));
            this.max.max(new Vector3f(positions[i], positions[i + 1], positions[i + 2]));
        }
    }

    public int vertexCount() {
        return this.colors.length;
    }

    public boolean isEmpty() {
        return this.colors.length == 0;
    }

    int color(int vertex) {
        return this.colors[vertex];
    }

    Vector3fc position(int vertex, Vector3f dest) {
        int p = vertex * 3;
        return dest.set(this.positions[p], this.positions[p + 1], this.positions[p + 2]);
    }

    Vector3fc normal(int vertex, Vector3f dest) {
        int p = vertex * 3;
        return dest.set(this.normals[p], this.normals[p + 1], this.normals[p + 2]);
    }

    public Vector3fc min() {
        return this.min;
    }

    public Vector3fc max() {
        return this.max;
    }

    public Shape recolored(int baseRgb, int rgb) {
        int base = baseRgb & 0xFFFFFF;
        int[] recolored = null;
        for (int v = 0; v < this.colors.length; v++) {
            if ((this.colors[v] & 0xFFFFFF) == base) {
                if (recolored == null) {
                    recolored = this.colors.clone();
                }
                recolored[v] = (this.colors[v] & 0xFF000000) | (rgb & 0xFFFFFF);
            }
        }
        return recolored == null ? this : new Shape(this.positions, this.normals, recolored);
    }

    public void emit(PoseStack.Pose pose, VertexConsumer buffer, int lightCoords, int overlayCoords, int tint) {
        for (int v = 0; v < this.colors.length; v++) {
            int p = v * 3;
            int color = tint == -1 ? this.colors[v] : multiply(this.colors[v], tint);
            buffer.addVertex(pose, this.positions[p], this.positions[p + 1], this.positions[p + 2])
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(overlayCoords)
                    .setLight(lightCoords)
                    .setNormal(pose, this.normals[p], this.normals[p + 1], this.normals[p + 2]);
        }
    }

    public static int multiply(int a, int b) {
        int alpha = ((a >>> 24) * (b >>> 24) + 127) / 255;
        int red = (((a >>> 16) & 0xFF) * ((b >>> 16) & 0xFF) + 127) / 255;
        int green = (((a >>> 8) & 0xFF) * ((b >>> 8) & 0xFF) + 127) / 255;
        int blue = ((a & 0xFF) * (b & 0xFF) + 127) / 255;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}

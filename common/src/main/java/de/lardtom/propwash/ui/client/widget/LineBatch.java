package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.client.theme.GuiLayerAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.Arrays;

public final class LineBatch {
    private float[] corners = new float[8 * 256];
    private int[] colors = new int[256];
    private int count;
    private float minX = Float.POSITIVE_INFINITY;
    private float minY = Float.POSITIVE_INFINITY;
    private float maxX = Float.NEGATIVE_INFINITY;
    private float maxY = Float.NEGATIVE_INFINITY;

    public int count() {
        return this.count;
    }

    public void clear() {
        this.count = 0;
        this.minX = Float.POSITIVE_INFINITY;
        this.minY = Float.POSITIVE_INFINITY;
        this.maxX = Float.NEGATIVE_INFINITY;
        this.maxY = Float.NEGATIVE_INFINITY;
    }

    public void line(float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float half = width * 0.5F;
        if (length < 1.0E-4F) {
            this.rect(x0 - half, y0 - half, x0 + half, y0 + half, color);
            return;
        }
        float nx = -dy / length * half;
        float ny = dx / length * half;
        this.quad(x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, color);
    }

    public void rect(float x0, float y0, float x1, float y1, int color) {
        this.quad(x0, y0, x0, y1, x1, y1, x1, y0, color);
    }

    public void dot(float cx, float cy, float radius, int color) {
        this.quad(cx, cy - radius, cx - radius, cy, cx, cy + radius, cx + radius, cy, color);
    }

    public void ring(float cx, float cy, float radius, float width, int color) {
        int segments = 16;
        float px = cx + radius;
        float py = cy;
        for (int i = 1; i <= segments; i++) {
            double a = Math.PI * 2.0 * i / segments;
            float x = cx + (float) (Math.cos(a) * radius);
            float y = cy + (float) (Math.sin(a) * radius);
            this.line(px, py, x, y, width, color);
            px = x;
            py = y;
        }
    }

    private void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int color) {
        if (this.count == this.colors.length) {
            this.colors = Arrays.copyOf(this.colors, this.count * 2);
            this.corners = Arrays.copyOf(this.corners, this.count * 16);
        }
        float area = (ax * by - bx * ay) + (bx * cy - cx * by) + (cx * dy - dx * cy) + (dx * ay - ax * dy);
        int o = this.count * 8;
        this.corners[o] = ax;
        this.corners[o + 1] = ay;
        this.corners[o + 4] = cx;
        this.corners[o + 5] = cy;
        if (area <= 0.0F) {
            this.corners[o + 2] = bx;
            this.corners[o + 3] = by;
            this.corners[o + 6] = dx;
            this.corners[o + 7] = dy;
        } else {
            this.corners[o + 2] = dx;
            this.corners[o + 3] = dy;
            this.corners[o + 6] = bx;
            this.corners[o + 7] = by;
        }
        this.colors[this.count] = color;
        this.count++;
        this.minX = Math.min(this.minX, Math.min(Math.min(ax, bx), Math.min(cx, dx)));
        this.minY = Math.min(this.minY, Math.min(Math.min(ay, by), Math.min(cy, dy)));
        this.maxX = Math.max(this.maxX, Math.max(Math.max(ax, bx), Math.max(cx, dx)));
        this.maxY = Math.max(this.maxY, Math.max(Math.max(ay, by), Math.max(cy, dy)));
    }

    public void submit(GuiGraphicsExtractor g) {
        if (this.count == 0 || !(g instanceof GuiLayerAccess access)) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        Vector2f a = pose.transformPosition(this.minX, this.minY, new Vector2f());
        Vector2f b = pose.transformPosition(this.maxX, this.maxY, new Vector2f());
        int x0 = (int) Math.floor(Math.min(a.x, b.x)) - 1;
        int y0 = (int) Math.floor(Math.min(a.y, b.y)) - 1;
        int x1 = (int) Math.ceil(Math.max(a.x, b.x)) + 1;
        int y1 = (int) Math.ceil(Math.max(a.y, b.y)) + 1;
        ScreenRectangle bounds = new ScreenRectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        ScreenRectangle scissor = access.propwash$scissor();
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) {
                return;
            }
        }
        access.propwash$renderState().addGuiElement(new LineQuads(pose, Arrays.copyOf(this.corners, this.count * 8),
                Arrays.copyOf(this.colors, this.count), this.count, scissor, bounds));
    }
}

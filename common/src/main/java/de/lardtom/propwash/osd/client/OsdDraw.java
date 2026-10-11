package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.osd.OsdColors;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdOutline;
import de.lardtom.propwash.ui.client.theme.GuiLayerAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.function.ToIntFunction;

final class OsdDraw {
    static final int GLYPH_HEIGHT = 7;
    static final int CELL_HEIGHT = 8;

    private final Vector2f corner = new Vector2f();
    private @Nullable Matrix3x2f lastPose;
    private GuiGraphicsExtractor graphics;
    private Font font;
    private ToIntFunction<String> widths;
    private OsdConfig.Outline outline = OsdConfig.Outline.THIN;
    private OsdConfig.Background background = OsdConfig.Background.OFF;
    private double pixelScale;

    void begin(GuiGraphicsExtractor graphics, Font font, OsdConfig.Outline outline, OsdConfig.Background background, double pixelScale) {
        this.graphics = graphics;
        if (font != this.font || this.widths == null) {
            this.font = font;
            this.widths = font::width;
        }
        this.outline = outline == null ? OsdConfig.Outline.THIN : outline;
        this.background = background == null ? OsdConfig.Background.OFF : background;
        this.pixelScale = pixelScale;
        OsdFonts.begin(graphics);
    }

    void end() {
        this.graphics = null;
    }

    GuiGraphicsExtractor graphics() {
        return this.graphics;
    }

    static float width(Font font, String text, float scale) {
        return (font.width(text) + 1) * scale;
    }

    float width(String text, float scale) {
        return width(this.font, text, scale);
    }

    boolean hasBackground() {
        return this.background != OsdConfig.Background.OFF;
    }

    void text(String text, float x, float y, float scale, int color) {
        if (text.isEmpty() || OsdColors.alpha(color) == 0) {
            return;
        }
        Matrix3x2fStack pose = this.graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(scale, scale);
            float alpha = OsdColors.alpha(color) / 255.0F;
            if (this.layeredText(text, color, alpha)) {
                return;
            }
            float stack = OsdOutline.stackAlpha(alpha, this.outline);
            int shade = OsdColors.withAlpha(OsdColors.SHADE_RGB, OsdOutline.copyAlpha(stack));
            if (OsdColors.alpha(shade) > 0) {
                switch (this.outline) {
                    case NONE -> {
                    }
                    case SHADOW -> this.textCopy(text, 1.0F, 1.0F, shade);
                    case THIN -> this.textCross(text, this.physicalPixel(), shade);
                    case THICK -> this.textRing(text, 1.0F, shade);
                }
            }
            int fill = OsdColors.withAlpha(color, OsdOutline.fillAlpha(alpha, OsdColors.alpha(shade) > 0 ? stack : 0.0F));
            if (OsdColors.alpha(fill) > 0) {
                this.graphics.text(this.font, text, 0, 0, fill, false);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private boolean layeredText(String text, int color, float alpha) {
        if (!(this.graphics instanceof GuiLayerAccess)) {
            return false;
        }
        OsdTextCache.Shape shape = OsdFonts.CACHE.text(text, this.outline, this.outlineEdge(), this.widths);
        if (shape == null) {
            return false;
        }
        if (shape.isEmpty()) {
            return true;
        }
        Matrix3x2fStack pose = this.graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(0.0F, shape.top());
            return this.submit(shape, color, this.layerShade(alpha));
        } finally {
            pose.popMatrix();
        }
    }

    private int layerShade(float alpha) {
        return OsdColors.withAlpha(OsdColors.SHADE_RGB, alpha * this.outline.alphaFactor());
    }

    private float outlineEdge() {
        return switch (this.outline) {
            case THIN -> OsdTextCache.quantizeEdge(this.physicalPixel());
            default -> 1.0F;
        };
    }

    private boolean submit(OsdTextCache.Shape shape, int fillColor, int outlineColor) {
        if (!(this.graphics instanceof GuiLayerAccess access)) {
            return false;
        }
        boolean outlined = OsdColors.alpha(outlineColor) > 0;
        int from = outlined ? 0 : shape.outlineCount();
        int count = shape.fillCount() + (outlined ? shape.outlineCount() : 0);
        if (count == 0) {
            return true;
        }
        Matrix3x2f pose = this.poseSnapshot();
        ScreenRectangle bounds = this.transformedBounds(pose, outlined ? shape.allBounds() : shape.fillBounds());
        @Nullable ScreenRectangle scissor = access.propwash$scissor();
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
            if (bounds == null) {
                return true;
            }
        }
        access.propwash$renderState().addGuiElement(
                new OsdQuads(pose, shape.quads(), from, count, shape.outlineCount(), outlineColor, fillColor, scissor, bounds));
        return true;
    }

    private Matrix3x2f poseSnapshot() {
        Matrix3x2fStack pose = this.graphics.pose();
        Matrix3x2f last = this.lastPose;
        if (last != null && last.m00() == pose.m00() && last.m01() == pose.m01() && last.m10() == pose.m10()
                && last.m11() == pose.m11() && last.m20() == pose.m20() && last.m21() == pose.m21()) {
            return last;
        }
        last = new Matrix3x2f(pose);
        this.lastPose = last;
        return last;
    }

    private ScreenRectangle transformedBounds(Matrix3x2fc pose, int[] local) {
        float left = local[0];
        float top = local[1];
        float right = local[0] + local[2];
        float bottom = local[1] + local[3];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            pose.transformPosition((i & 1) == 0 ? left : right, (i & 2) == 0 ? top : bottom, this.corner);
            minX = Math.min(minX, this.corner.x);
            minY = Math.min(minY, this.corner.y);
            maxX = Math.max(maxX, this.corner.x);
            maxY = Math.max(maxY, this.corner.y);
        }
        return new ScreenRectangle(Mth.floor(minX), Mth.floor(minY), Mth.ceil(maxX - minX), Mth.ceil(maxY - minY));
    }

    private void textCross(String text, float offset, int shade) {
        this.textCopy(text, -offset, 0.0F, shade);
        this.textCopy(text, offset, 0.0F, shade);
        this.textCopy(text, 0.0F, -offset, shade);
        this.textCopy(text, 0.0F, offset, shade);
    }

    private void textRing(String text, float offset, int shade) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dy != 0) {
                    this.textCopy(text, dx * offset, dy * offset, shade);
                }
            }
        }
    }

    private void textCopy(String text, float dx, float dy, int shade) {
        Matrix3x2fStack pose = this.graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(dx, dy);
            this.graphics.text(this.font, text, 0, 0, shade, false);
        } finally {
            pose.popMatrix();
        }
    }

    void box(float x0, float y0, float x1, float y1, int color) {
        if (!this.hasBackground()) {
            return;
        }
        int shade = OsdColors.shade(color, this.background.alphaFactor());
        if (OsdColors.alpha(shade) > 0 && x1 > x0 && y1 > y0) {
            this.fillF(x0, y0, x1, y1, shade);
        }
    }

    void fill(int x0, int y0, int x1, int y1, int color) {
        if (OsdColors.alpha(color) > 0) {
            this.graphics.fill(x0, y0, x1, y1, color);
        }
    }

    void rects(int color, int... rects) {
        this.rects(color, rects, rects.length / 4);
    }

    void rects(int color, int[] rects, int count) {
        if (OsdColors.alpha(color) == 0) {
            return;
        }
        int length = Math.min(rects.length / 4, count) * 4;
        if (this.graphics instanceof GuiLayerAccess) {
            float alpha = OsdColors.alpha(color) / 255.0F;
            OsdTextCache.Shape shape = OsdFonts.CACHE.rects(rects, length / 4, this.outline, this.outlineEdge());
            if (this.submit(shape, color, this.layerShade(alpha))) {
                return;
            }
        }
        float stack = OsdOutline.stackAlpha(OsdColors.alpha(color) / 255.0F, this.outline);
        int shade = OsdColors.withAlpha(OsdColors.SHADE_RGB, stack);
        if (OsdColors.alpha(shade) > 0) {
            float e = switch (this.outline) {
                case THIN -> this.physicalPixel();
                default -> 1.0F;
            };
            for (int i = 0; i < length; i += 4) {
                float[] strips = this.outline == OsdConfig.Outline.SHADOW
                        ? OsdOutline.shadow(rects[i], rects[i + 1], rects[i + 2], rects[i + 3], e)
                        : OsdOutline.ring(rects[i], rects[i + 1], rects[i + 2], rects[i + 3], e);
                for (int j = 0; j < strips.length; j += 4) {
                    if (strips[j + 2] > strips[j] && strips[j + 3] > strips[j + 1]) {
                        this.fillF(strips[j], strips[j + 1], strips[j + 2], strips[j + 3], shade);
                    }
                }
            }
        }
        for (int i = 0; i < length; i += 4) {
            this.graphics.fill(rects[i], rects[i + 1], rects[i + 2], rects[i + 3], color);
        }
    }

    void frame(int x0, int y0, int x1, int y1, int color) {
        this.rects(color,
                x0, y0, x1, y0 + 1,
                x0, y1 - 1, x1, y1,
                x0, y0 + 1, x0 + 1, y1 - 1,
                x1 - 1, y0 + 1, x1, y1 - 1);
    }

    void push(float x, float y, float rotation, float scale) {
        Matrix3x2fStack pose = this.graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        if (rotation != 0.0F) {
            pose.rotate(rotation);
        }
        pose.scale(scale, scale);
    }

    void pop() {
        this.graphics.pose().popMatrix();
    }

    private void fillF(float x0, float y0, float x1, float y1, int color) {
        Matrix3x2fStack pose = this.graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x0, y0);
            pose.scale(x1 - x0, y1 - y0);
            this.graphics.fill(0, 0, 1, 1, color);
        } finally {
            pose.popMatrix();
        }
    }

    private float physicalPixel() {
        Matrix3x2fStack pose = this.graphics.pose();
        float units = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
        if (!(units > 0.0F) || !(this.pixelScale > 0.0)) {
            return 1.0F;
        }
        return Math.min(1.0F, (float) (1.0 / (units * this.pixelScale)));
    }
}

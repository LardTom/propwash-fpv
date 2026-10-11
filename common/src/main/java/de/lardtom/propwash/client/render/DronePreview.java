package de.lardtom.propwash.client.render;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.client.render.DroneVisuals;
import de.lardtom.propwash.drone.client.render.QuadcopterRenderer;
import de.lardtom.propwash.drone.led.LedSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class DronePreview {
    private static boolean warned;

    private DronePreview() {
    }

    public static void render(GuiGraphicsExtractor graphics, DroneBuild build, int x0, int y0, int x1, int y1, float yawDegrees) {
        render(graphics, build, PartWear.PRISTINE, x0, y0, x1, y1, yawDegrees, null, false, 0.0);
    }

    public static void render(GuiGraphicsExtractor graphics, DroneBuild build, PartWear wear, int x0, int y0, int x1, int y1,
                              float yawDegrees) {
        render(graphics, build, wear, x0, y0, x1, y1, yawDegrees, null, false, 0.0);
    }

    public static void render(GuiGraphicsExtractor graphics, DroneBuild build, int x0, int y0, int x1, int y1, float yawDegrees,
                              @Nullable LedSettings leds, boolean armed, double seconds) {
        render(graphics, build, PartWear.PRISTINE, x0, y0, x1, y1, yawDegrees, leds, armed, seconds);
    }

    public static void render(GuiGraphicsExtractor graphics, DroneBuild build, PartWear wear, DronePaint paint, int x0, int y0, int x1,
                              int y1, float yawDegrees) {
        render(graphics, build, wear, paint, x0, y0, x1, y1, yawDegrees, null, false, 0.0);
    }

    public static void render(GuiGraphicsExtractor graphics, DroneBuild build, PartWear wear, int x0, int y0, int x1, int y1,
                              float yawDegrees, @Nullable LedSettings leds, boolean armed, double seconds) {
        render(graphics, build, wear, DronePaint.DEFAULT, x0, y0, x1, y1, yawDegrees, leds, armed, seconds);
    }

    public static void renderAtScale(GuiGraphicsExtractor graphics, DroneBuild build, PartWear wear, DronePaint paint, int x0, int y0, int x1,
                                     int y1, float yawDegrees, float pixelsPerMillimetre) {
        if (graphics == null || build == null || x1 - x0 < 8 || y1 - y0 < 8 || !(pixelsPerMillimetre > 0.0F)) {
            return;
        }
        try {
            DroneRenderState state = DroneVisuals.previewState(build, wear, paint);
            if (state == null || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state) instanceof QuadcopterRenderer)) {
                return;
            }
            float scale = DroneVisuals.scaleForPixelsPerMillimetre(state, pixelsPerMillimetre);
            ScreenRectangle placed = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformAxisAligned(graphics.pose());
            graphics.entity(state, scale, new Vector3f(), rotation(yawDegrees), null, placed.left(), placed.top(), placed.right(), placed.bottom());
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                Constants.LOG.error("Drohnen-Vorschau konnte nicht gezeichnet werden", e);
            }
        }
    }

    public static Quaternionf rotation(float yawDegrees) {
        float yaw = Float.isFinite(yawDegrees) ? (float) Math.toRadians(yawDegrees) : 0.0F;
        return new Quaternionf().rotationXYZ(DroneVisuals.PREVIEW_TILT_RADIANS, 0.0F, (float) Math.PI).rotateY(yaw);
    }

    public static void render(GuiGraphicsExtractor graphics, DroneBuild build, PartWear wear, DronePaint paint, int x0, int y0, int x1,
                              int y1, float yawDegrees, @Nullable LedSettings leds, boolean armed, double seconds) {
        render(graphics, build, wear, paint, Float.NaN, x0, y0, x1, y1, yawDegrees, leds, armed, seconds);
    }

    public static void render(GuiGraphicsExtractor graphics, DroneBuild build, PartWear wear, DronePaint paint, float cameraTiltDegrees, int x0,
                              int y0, int x1, int y1, float yawDegrees) {
        render(graphics, build, wear, paint, cameraTiltDegrees, x0, y0, x1, y1, yawDegrees, null, false, 0.0);
    }

    private static void render(GuiGraphicsExtractor graphics, DroneBuild build, PartWear wear, DronePaint paint, float cameraTiltDegrees, int x0,
                               int y0, int x1, int y1, float yawDegrees, @Nullable LedSettings leds, boolean armed, double seconds) {
        if (graphics == null || build == null || x1 - x0 < 8 || y1 - y0 < 8) {
            return;
        }
        try {
            DroneRenderState state = leds == null ? DroneVisuals.previewState(build, wear, paint, cameraTiltDegrees)
                    : DroneVisuals.ledPreviewState(build, leds, armed, seconds);
            if (state == null || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state) instanceof QuadcopterRenderer)) {
                return;
            }
            float scale = DroneVisuals.previewScale(state, x1 - x0, y1 - y0);
            ScreenRectangle placed = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformAxisAligned(graphics.pose());
            graphics.entity(state, scale, new Vector3f(), rotation(yawDegrees), null, placed.left(), placed.top(), placed.right(), placed.bottom());
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                Constants.LOG.error("Drohnen-Vorschau konnte nicht gezeichnet werden", e);
            }
        }
    }
}

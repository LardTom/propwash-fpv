package de.lardtom.propwash.guide.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.guide.scene.ScenePos;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.jspecify.annotations.Nullable;

final class HighlightGeometry {
    private static final float TEXEL = 0.5F;
    private static @Nullable RenderType type;

    private HighlightGeometry() {
    }

    static void submit(PoseStack ps, SubmitNodeCollector collector, ScenePos min, ScenePos max, float thickness, int color) {
        float t = thickness * 0.5F;
        float x0 = Math.min(min.x(), max.x()) - t;
        float y0 = Math.min(min.y(), max.y()) - t;
        float z0 = Math.min(min.z(), max.z()) - t;
        float x1 = Math.max(min.x(), max.x()) + t;
        float y1 = Math.max(min.y(), max.y()) + t;
        float z1 = Math.max(min.z(), max.z()) + t;
        float w = thickness;
        collector.submitCustomGeometry(ps, type(), (pose, buffer) -> {
            for (float y : new float[]{y0, y1 - w}) {
                box(pose, buffer, x0, y, z0, x1, y + w, z0 + w, color);
                box(pose, buffer, x0, y, z1 - w, x1, y + w, z1, color);
                box(pose, buffer, x0, y, z0 + w, x0 + w, y + w, z1 - w, color);
                box(pose, buffer, x1 - w, y, z0 + w, x1, y + w, z1 - w, color);
            }
            for (float x : new float[]{x0, x1 - w}) {
                for (float z : new float[]{z0, z1 - w}) {
                    box(pose, buffer, x, y0 + w, z, x + w, y1 - w, z + w, color);
                }
            }
        });
    }

    private static RenderType type() {
        if (type == null) {
            type = RenderTypes.entityTranslucent(DroneRenderResources.WHITE_TEXTURE);
        }
        return type;
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        quad(pose, buffer, color, 0.0F, 1.0F, 0.0F, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, buffer, color, 0.0F, -1.0F, 0.0F, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, buffer, color, 0.0F, 0.0F, -1.0F, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, buffer, color, 0.0F, 0.0F, 1.0F, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, buffer, color, -1.0F, 0.0F, 0.0F, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, buffer, color, 1.0F, 0.0F, 0.0F, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, buffer, color, nx, ny, nz, ax, ay, az);
        vertex(pose, buffer, color, nx, ny, nz, bx, by, bz);
        vertex(pose, buffer, color, nx, ny, nz, cx, cy, cz);
        vertex(pose, buffer, color, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float nx, float ny, float nz, float x, float y, float z) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(TEXEL, TEXEL).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }
}

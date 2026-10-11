package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.Constants;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

final class BoxDraw {
    static final Identifier WHITE_TEXTURE = Constants.id("textures/entity/race/white.png");
    private static final float TEXEL = 0.5F;

    private BoxDraw() {
    }

    static void box(PoseStack.Pose pose, VertexConsumer buffer, double minX, double minY, double minZ, double maxX, double maxY,
                    double maxZ, int color, int light) {
        float x0 = (float) minX;
        float y0 = (float) minY;
        float z0 = (float) minZ;
        float x1 = (float) maxX;
        float y1 = (float) maxY;
        float z1 = (float) maxZ;
        quad(pose, buffer, color, light, 0.0F, 1.0F, 0.0F, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, buffer, color, light, 0.0F, -1.0F, 0.0F, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, buffer, color, light, 0.0F, 0.0F, -1.0F, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(pose, buffer, color, light, 0.0F, 0.0F, 1.0F, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, buffer, color, light, -1.0F, 0.0F, 0.0F, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, buffer, color, light, 1.0F, 0.0F, 0.0F, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    static void centered(PoseStack.Pose pose, VertexConsumer buffer, double cx, double cy, double cz, double hx, double hy, double hz,
                         int color, int light) {
        box(pose, buffer, cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, color, light);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, buffer, color, light, nx, ny, nz, ax, ay, az);
        vertex(pose, buffer, color, light, nx, ny, nz, bx, by, bz);
        vertex(pose, buffer, color, light, nx, ny, nz, cx, cy, cz);
        vertex(pose, buffer, color, light, nx, ny, nz, dx, dy, dz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, int light, float nx, float ny, float nz,
                               float x, float y, float z) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(TEXEL, TEXEL).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}

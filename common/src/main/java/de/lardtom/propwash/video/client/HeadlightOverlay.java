package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.HeadlightMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class HeadlightOverlay {
    static final Identifier CONE_TEXTURE = Constants.id("video/headlight_cone");
    static final int CONE_SIZE = 256;

    public static final RenderPipeline BOOST_PIPELINE = pipeline("pipeline/fpv_headlight_boost",
            new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE));
    public static final RenderPipeline LIFT_PIPELINE = pipeline("pipeline/fpv_headlight_lift",
            new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE));

    private static boolean textureReady;
    private static boolean textureFailed;
    private static boolean pipelineFailed;
    private static long framesDrawn;

    private HeadlightOverlay() {
    }

    private static RenderPipeline pipeline(String location, BlendFunction blend) {
        return RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withVertexShader(Identifier.withDefaultNamespace("core/position_tex_color"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/position_tex_color"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(blend))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withLocation(Constants.id(location))
                .build();
    }

    public static long framesDrawn() {
        return framesDrawn;
    }

    static boolean available() {
        if (pipelineFailed) {
            return false;
        }
        if (RenderSystem.getCompiledPipelineNullable(BOOST_PIPELINE) == null || RenderSystem.getCompiledPipelineNullable(LIFT_PIPELINE) == null) {
            pipelineFailed = true;
            Constants.LOG.warn("FPV-Scheinwerfer: Overlay-Pipelines nicht verfügbar, keine Aufhellung im Overlay");
            return false;
        }
        return ensureTexture();
    }

    static void extract(GuiGraphicsExtractor graphics, int width, int height) {
        Headlight light = Headlight.INSTANCE;
        if (!light.usesOverlay() || !available()) {
            return;
        }
        double radius = HeadlightMath.overlayRadius(VideoState.INSTANCE.displayFov(), height * 0.5);
        int size = (int) Math.round(Math.max(8.0, radius * 2.0 / 0.82));
        int x = Math.round(width * 0.5F - size * 0.5F);
        int y = Math.round(height * 0.5F - size * 0.5F);
        float layer = (float) HeadlightMath.overlayLayerGain(light.gain());
        if (layer > 0.0F) {
            int boost = tint(light, layer);
            for (int i = 0; i < HeadlightMath.OVERLAY_LAYERS; i++) {
                graphics.blit(BOOST_PIPELINE, CONE_TEXTURE, x, y, 0.0F, 0.0F, size, size, CONE_SIZE, CONE_SIZE, CONE_SIZE, CONE_SIZE, boost);
            }
        }
        float lift = (float) (HeadlightMath.OVERLAY_LIFT * light.gain() / HeadlightMath.GAIN);
        if (lift > 0.002F) {
            graphics.blit(LIFT_PIPELINE, CONE_TEXTURE, x, y, 0.0F, 0.0F, size, size, CONE_SIZE, CONE_SIZE, CONE_SIZE, CONE_SIZE,
                    tint(light, lift));
        }
        framesDrawn++;
    }

    private static int tint(Headlight light, float amount) {
        float a = Math.max(0.0F, Math.min(1.0F, amount));
        double[] warm = HeadlightMath.warmTint(1.0);
        return ARGB.color(255, channel((float) (light.red() * warm[0] * a)), channel((float) (light.green() * warm[1] * a)),
                channel((float) (light.blue() * warm[2] * a)));
    }

    private static int channel(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }

    static boolean ensureTexture() {
        if (textureReady) {
            return true;
        }
        if (textureFailed) {
            return false;
        }
        try {
            DynamicTexture texture = new DynamicTexture(() -> "propwash headlight cone", CONE_SIZE, CONE_SIZE, false);
            NativeImage pixels = texture.getPixels();
            double center = (CONE_SIZE - 1) * 0.5;
            double inner = Math.tan(Math.toRadians(HeadlightMath.CONE_INNER_DEG)) / Math.tan(Math.toRadians(HeadlightMath.CONE_OUTER_DEG));
            for (int py = 0; py < CONE_SIZE; py++) {
                for (int px = 0; px < CONE_SIZE; px++) {
                    double dx = (px - center) / (CONE_SIZE * 0.5 * 0.82);
                    double dy = (py - center) / (CONE_SIZE * 0.5 * 0.82);
                    double r = Math.sqrt(dx * dx + dy * dy);
                    double core = Math.pow(1.0 - HeadlightMath.smoothstep(inner, 1.0, r), HeadlightMath.CONE_EDGE_POWER);
                    double hot = 1.0 - HeadlightMath.smoothstep(0.0, inner, r);
                    core = core * (1.0 + HeadlightMath.HOTSPOT_BOOST * hot) / (1.0 + HeadlightMath.HOTSPOT_BOOST);
                    double spill = HeadlightMath.SPILL * (1.0 - HeadlightMath.smoothstep(1.0, 1.22, r));
                    double value = Math.max(0.0, Math.min(1.0, HeadlightMath.SPILL + (1.0 - HeadlightMath.SPILL) * core));
                    value = r > 1.0 ? spill : value;
                    int level = (int) Math.round(value * 255.0);
                    pixels.setPixel(px, py, ARGB.color(level > 0 ? 255 : 0, level, level, level));
                }
            }
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(CONE_TEXTURE, texture);
            textureReady = true;
        } catch (RuntimeException exception) {
            textureFailed = true;
            Constants.LOG.warn("FPV-Scheinwerfer: Kegeltextur für das Overlay nicht anlegbar ({})", exception.toString());
        }
        return textureReady;
    }
}

package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.compat.iris.client.IrisCompat;
import de.lardtom.propwash.mixin.drone.client.RenderTypeAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AfterWaterRenderTypes {
    private static final String IRIS_PROGRAM = "ENTITIES_TRANSLUCENT";
    private static final String IRIS_SHADOW_PROGRAM = "SHADOW_ENTITIES";
    private static final DepthStencilState NO_DEPTH_WRITE = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    static final RenderPipeline BLUR_PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.id("pipeline/prop_blur"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(NO_DEPTH_WRITE)
            .withCull(false)
            .build();
    static final RenderPipeline BLUR_ITEM_PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.id("pipeline/prop_blur_item"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
            .withVertexShader("core/item")
            .withFragmentShader("core/item")
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(NO_DEPTH_WRITE)
            .build();
    private static final Set<RenderType> AFTER_WATER = ConcurrentHashMap.newKeySet();
    private static final Map<Identifier, RenderType> BLUR_ITEMS = new HashMap<>();
    private static final Map<Identifier, RenderType> GHOSTS = new HashMap<>();
    private static @Nullable RenderType blur;
    private static @Nullable RenderType fpvDisc;
    private static @Nullable RenderType halo;
    private static boolean irisAssigned;

    private AfterWaterRenderTypes() {
    }

    public static boolean drawsAfterWater(RenderType type) {
        return AFTER_WATER.contains(type);
    }

    public static boolean isHalo(RenderType type) {
        return type != null && type == halo;
    }

    public static synchronized RenderType blur() {
        if (blur == null) {
            blur = create("propwash_prop_blur", RenderSetup.builder(BLUR_PIPELINE)
                    .setOitPipelines(RenderPipelines.OIT_ENTITY)
                    .withTexture("Sampler0", DroneRenderResources.WHITE_TEXTURE)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup());
        }
        return blur;
    }

    public static synchronized RenderType fpvDisc() {
        if (fpvDisc == null) {
            fpvDisc = create("propwash_fpv_disc", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                    .setOitPipelines(RenderPipelines.OIT_ENTITY)
                    .withTexture("Sampler0", DroneRenderResources.WHITE_TEXTURE)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .createRenderSetup());
        }
        return fpvDisc;
    }

    public static synchronized RenderType blurItems(Identifier atlas) {
        return BLUR_ITEMS.computeIfAbsent(atlas, texture -> create("propwash_prop_blur_item", RenderSetup.builder(BLUR_ITEM_PIPELINE)
                .setOitPipelines(RenderPipelines.OIT_ITEM)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .sortOnUpload()
                .createRenderSetup()));
    }

    public static synchronized RenderType ghost(Identifier texture) {
        return GHOSTS.computeIfAbsent(texture, id -> create("propwash_ghost", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
                .setOitPipelines(RenderPipelines.OIT_ENTITY)
                .withTexture("Sampler0", id)
                .useLightmap()
                .useOverlay()
                .sortOnUpload()
                .createRenderSetup()));
    }

    public static synchronized RenderType halo() {
        if (halo == null) {
            halo = create("propwash_halo", RenderSetup.builder(RenderPipelines.EYES)
                    .setOitPipelines(RenderPipelines.OIT_EYES)
                    .withTexture("Sampler0", DroneRenderResources.WHITE_TEXTURE)
                    .sortOnUpload()
                    .createRenderSetup());
        }
        return halo;
    }

    private static RenderType create(String name, RenderSetup setup) {
        if (!irisAssigned) {
            irisAssigned = true;
            IrisCompat.assignPipeline(BLUR_PIPELINE, IRIS_PROGRAM, IRIS_SHADOW_PROGRAM);
            IrisCompat.assignPipeline(BLUR_ITEM_PIPELINE, IRIS_PROGRAM, IRIS_SHADOW_PROGRAM);
        }
        RenderType type = RenderTypeAccessor.propwash$create(name, setup);
        AFTER_WATER.add(type);
        return type;
    }
}

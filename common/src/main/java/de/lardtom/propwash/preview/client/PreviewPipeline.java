package de.lardtom.propwash.preview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import de.lardtom.propwash.Constants;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class PreviewPipeline {
    private static final String VERTEX_SOURCE = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                mat4 TextureMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
            };
            layout(std140) uniform Projection {
                mat4 ProjMat;
            };
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec4 Color;
            layout(location = 0) out vec4 vertexColor;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                vertexColor = Color;
            }
            """;
    private static final String FRAGMENT_SOURCE = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(location = 0) in vec4 vertexColor;
            layout(location = 0) out vec4 fragColor;
            void main() {
                fragColor = vertexColor;
            }
            """;
    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.id("pipeline/preview_mark"))
            .withVertexShader(Constants.id("core/preview_mark"))
            .withFragmentShader(Constants.id("core/preview_mark"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();
    public static final ShaderSource SOURCE = new ShaderSource() {
        @Override
        public @Nullable String getShader(Identifier id, ShaderType type) {
            if (!Objects.equals(PIPELINE.getShaders().get(type), id)) {
                return null;
            }
            return type == ShaderType.VERTEX ? VERTEX_SOURCE : type == ShaderType.FRAGMENT ? FRAGMENT_SOURCE : null;
        }

        @Override
        public ShaderSource.@Nullable CachedIncludeSource getInclude(Identifier id) {
            return null;
        }

        @Override
        public void close() {
        }
    };
    private static @Nullable CompiledRenderPipeline compiled;
    private static long compiles;
    private static long lookups;

    private PreviewPipeline() {
    }

    public static CompiledRenderPipeline compiled() {
        CompiledRenderPipeline known = compiled;
        if (known != null && !known.isClosed()) {
            return known;
        }
        CompiledRenderPipeline fresh = RenderSystem.getDevice().compilePipeline(PIPELINE, SOURCE, Util.backgroundExecutor()).join().finishCompile();
        if (fresh == null) {
            throw new IllegalStateException("Vorschau-Wasserzeichen: eigener Shader " + PIPELINE + " ließ sich nicht kompilieren");
        }
        compiled = fresh;
        compiles++;
        return fresh;
    }

    public static @Nullable CompiledRenderPipeline lookup(RenderPipeline pipeline) {
        if (pipeline != PIPELINE) {
            return null;
        }
        lookups++;
        return compiled();
    }

    public static long compiles() {
        return compiles;
    }

    public static long lookups() {
        return lookups;
    }
}

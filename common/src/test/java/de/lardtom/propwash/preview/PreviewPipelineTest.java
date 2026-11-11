package de.lardtom.propwash.preview;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PreviewPipelineTest {
    private static final String PIPELINE_CLASS = "de.lardtom.propwash.preview.client.PreviewPipeline";
    private static final Identifier VANILLA_GUI = Identifier.withDefaultNamespace("core/gui");

    private static Object field(String name) {
        try {
            return Class.forName(PIPELINE_CLASS).getField(name).get(null);
        } catch (ReflectiveOperationException e) {
            return fail("Eigene Pipeline des Wasserzeichens fehlt (" + PIPELINE_CLASS + "." + name + "): " + e);
        }
    }

    private static RenderPipeline pipeline() {
        return (RenderPipeline) field("PIPELINE");
    }

    private static ShaderSource source() {
        return (ShaderSource) field("SOURCE");
    }

    @Test
    void everyShaderOfThePipelineComesFromTheCode() {
        RenderPipeline pipeline = pipeline();
        ShaderSource source = source();
        Map<ShaderType, Identifier> shaders = pipeline.getShaders();
        assertEquals(Set.of(ShaderType.VERTEX, ShaderType.FRAGMENT), shaders.keySet(), "Shader der Pipeline: " + shaders);
        for (Map.Entry<ShaderType, Identifier> shader : shaders.entrySet()) {
            Identifier id = shader.getValue();
            assertEquals("propwash", id.getNamespace(), "Shader " + id + " nicht im eigenen Namensraum");
            String glsl = source.getShader(id, shader.getKey());
            assertNotNull(glsl, "Kein Quelltext im Code für " + shader.getKey() + " " + id);
            assertTrue(glsl.startsWith("#version "), shader.getKey() + " " + id + " beginnt nicht mit #version");
            assertFalse(glsl.contains("#include") || glsl.contains("moj_import"), shader.getKey() + " " + id + " lädt Teile aus Ressourcen nach");
        }
        String fragment = source.getShader(shaders.get(ShaderType.FRAGMENT), ShaderType.FRAGMENT);
        assertFalse(fragment.contains("discard"), "Fragment-Shader des Wasserzeichens verwirft Bildpunkte");
        assertFalse(fragment.contains("uniform"), "Fragment-Shader des Wasserzeichens hängt von Uniforms ab");
        assertTrue(fragment.contains("fragColor = vertexColor;"), "Fragment-Shader übernimmt die Farbe aus dem Code nicht unverändert");
        String vertex = source.getShader(shaders.get(ShaderType.VERTEX), ShaderType.VERTEX);
        assertTrue(vertex.contains("ProjMat * ModelViewMat * vec4(Position, 1.0)"), "Vertex-Shader rechnet nicht wie die GUI");
    }

    @Test
    void shaderSourceAnswersOnlyForTheWatermark() {
        ShaderSource source = source();
        assertNull(source.getShader(VANILLA_GUI, ShaderType.FRAGMENT), "Quelle liefert auch den Vanilla-GUI-Shader");
        assertNull(source.getShader(VANILLA_GUI, ShaderType.VERTEX), "Quelle liefert auch den Vanilla-GUI-Shader");
        Identifier vertex = pipeline().getShaders().get(ShaderType.VERTEX);
        assertNull(source.getShader(Identifier.fromNamespaceAndPath("propwash", "core/other"), ShaderType.VERTEX), "Quelle liefert fremde Shader");
        assertNotNull(source.getShader(vertex, ShaderType.VERTEX));
        assertNull(source.getInclude(Identifier.withDefaultNamespace("dynamictransforms.glsl")), "Quelle liefert Include-Dateien");
        assertNull(source.getInclude(Identifier.withDefaultNamespace("projection.glsl")), "Quelle liefert Include-Dateien");
    }

    @Test
    void pipelineFitsTheGuiRendererLikeTheVanillaGuiPipeline() {
        RenderPipeline pipeline = pipeline();
        assertEquals("propwash", pipeline.getLocation().getNamespace(), "Pipeline " + pipeline.getLocation() + " nicht im eigenen Namensraum");
        assertEquals(DefaultVertexFormat.POSITION_COLOR, pipeline.getVertexFormatBinding(0));
        assertEquals(PrimitiveTopology.QUADS, pipeline.getPrimitiveTopology());
        assertNull(pipeline.getDepthStencilState(), "Wasserzeichen darf nicht hinter Tiefenwerten anderer GUI-Flächen verschwinden");
        ColorTargetState target = pipeline.getColorTargetStates().getFirst();
        assertNotNull(target);
        assertEquals(new ColorTargetState(BlendFunction.TRANSLUCENT), target);
        assertEquals(Set.of("Globals", "Projection", "DynamicTransforms"),
                Set.copyOf(pipeline.getBindGroupLayouts().stream().flatMap(layout -> layout.uniforms().stream()).map(u -> u.name()).toList()));
    }
}

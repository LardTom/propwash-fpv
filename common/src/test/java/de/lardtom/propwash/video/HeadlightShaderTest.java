package de.lardtom.propwash.video;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlightShaderTest {
    private static final int WIDTH = 96;
    private static final int HEIGHT = 54;
    private static final float NEAR = 0.05F;
    private static final float FAR = 768.0F;
    private static final float EYE = 1.6F;
    private static final double PITCH_DEG = 12.0;
    private static final float WALL_DISTANCE = 6.0F;
    private static final float GAIN = (float) HeadlightMath.GAIN;

    @Test
    void guardedShaderNeverDarkensAndLeavesTheSkyAlone() {
        for (boolean zeroToOne : new boolean[]{true, false}) {
            Scene scene = Scene.night(zeroToOne);
            float[] out = HeadlightShaderModel.render(scene.uniforms(GAIN), scene.colors, scene.depth, WIDTH, HEIGHT, true);
            int sky = 0;
            for (int i = 0; i < WIDTH * HEIGHT; i++) {
                for (int c = 0; c < 3; c++) {
                    float in = scene.colors[i * 3 + c];
                    float value = out[i * 3 + c];
                    assertTrue(Float.isFinite(value) && value >= 0.0F && value <= 1.0F, "Bildpunkt " + i + " ungültig: " + value);
                    assertTrue(value >= in, "Bildpunkt " + i + " dunkler als ohne Licht (" + zeroToOne + "): " + in + " → " + value);
                    if (scene.depth[i] <= 0.0F) {
                        assertEquals(in, value, "Himmel bleibt unverändert");
                    }
                }
                if (scene.depth[i] <= 0.0F) {
                    sky++;
                }
            }
            assertTrue(sky > WIDTH * HEIGHT / 10 && sky < WIDTH * HEIGHT * 9 / 10, "Szene mit Himmel und Boden: " + sky);
            int centre = (HEIGHT / 2) * WIDTH + WIDTH / 2;
            assertTrue(scene.depth[centre] > 0.0F, "Mitte trifft die Wand");
            assertTrue(luma(out, centre) > luma(scene.colors, centre) * 4.0F, "Wand in der Kegelmitte wird hell: " + luma(scene.colors, centre)
                    + " → " + luma(out, centre));
        }
    }

    @Test
    void guardedShaderLooksLikeTheOldShaderWhereTheOldOneWorked() {
        for (boolean zeroToOne : new boolean[]{true, false}) {
            Scene scene = Scene.night(zeroToOne);
            HeadlightShaderModel.Uniforms uniforms = scene.uniforms(GAIN);
            float[] guarded = HeadlightShaderModel.render(uniforms, scene.colors, scene.depth, WIDTH, HEIGHT, true);
            float[] legacy = HeadlightShaderModel.render(uniforms, scene.colors, scene.depth, WIDTH, HEIGHT, false);
            int compared = 0;
            for (int y = 0; y < HEIGHT; y += 2) {
                for (int x = 0; x < WIDTH; x += 2) {
                    if (!solidQuad(scene.depth, x, y)) {
                        continue;
                    }
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            int i = (y + dy) * WIDTH + x + dx;
                            for (int c = 0; c < 3; c++) {
                                assertEquals(legacy[i * 3 + c], guarded[i * 3 + c], 1.0E-6F, "Bildpunkt " + i + " sieht anders aus als vorher");
                            }
                            compared++;
                        }
                    }
                }
            }
            assertTrue(compared > WIDTH * HEIGHT / 4, "genug Bildpunkte verglichen: " + compared);
        }
    }

    @Test
    void nanProjectionPassesThePictureThrough() {
        Scene scene = Scene.night(true);
        float[] nan = new float[16];
        java.util.Arrays.fill(nan, Float.NaN);
        HeadlightShaderModel.Uniforms uniforms = HeadlightShaderModel.Uniforms.of(nan, 1.0F, 0.9F, 0.74F, GAIN, true);
        float[] guarded = HeadlightShaderModel.render(uniforms, scene.colors, scene.depth, WIDTH, HEIGHT, true);
        float[] legacy = HeadlightShaderModel.render(uniforms, scene.colors, scene.depth, WIDTH, HEIGHT, false);
        int broken = 0;
        for (int i = 0; i < scene.colors.length; i++) {
            assertEquals(scene.colors[i], guarded[i], "NaN in der Projektion lässt das Bild unverändert");
            if (Float.isNaN(legacy[i])) {
                broken++;
            }
        }
        assertTrue(broken > scene.colors.length / 4, "ohne Schutz wird das Bild ungültig (auf manchen Treibern schwarz): " + broken);
    }

    @Test
    void projectionWithoutWPassesThePictureThrough() {
        Scene scene = Scene.night(true);
        float[] flat = scene.inverse.clone();
        flat[3] = 0.0F;
        flat[7] = 0.0F;
        flat[11] = 0.0F;
        flat[15] = 0.0F;
        HeadlightShaderModel.Uniforms uniforms = HeadlightShaderModel.Uniforms.of(flat, 1.0F, 1.0F, 1.0F, GAIN, true);
        float[] guarded = HeadlightShaderModel.render(uniforms, scene.colors, scene.depth, WIDTH, HEIGHT, true);
        float[] legacy = HeadlightShaderModel.render(uniforms, scene.colors, scene.depth, WIDTH, HEIGHT, false);
        boolean legacyBroken = false;
        for (int i = 0; i < scene.colors.length; i++) {
            assertEquals(scene.colors[i], guarded[i], "w = 0 lässt das Bild unverändert");
            legacyBroken |= !Float.isFinite(legacy[i]);
        }
        assertTrue(legacyBroken, "ohne Schutz teilt der alte Shader durch 0");
    }

    @Test
    void invalidDepthPassesThrough() {
        Scene scene = Scene.night(true);
        HeadlightShaderModel.Uniforms uniforms = scene.uniforms(GAIN);
        float[] col = {0.05F, 0.06F, 0.04F};
        for (float depth : new float[]{0.0F, -0.1F, 1.0F, 1.5F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float[] colors = new float[12];
            float[] depths = new float[4];
            for (int i = 0; i < 4; i++) {
                System.arraycopy(col, 0, colors, i * 3, 3);
                depths[i] = depth;
            }
            float[] out = HeadlightShaderModel.render(uniforms, colors, depths, 2, 2, true);
            for (int i = 0; i < colors.length; i++) {
                assertEquals(colors[i], out[i], "Tiefe " + depth + " lässt den Bildpunkt unverändert");
            }
        }
    }

    @Test
    void brokenLightValuesPassThrough() {
        Scene scene = Scene.night(true);
        float[][] lights = {
                {1.0F, 1.0F, 1.0F, Float.NaN},
                {1.0F, 1.0F, 1.0F, -1.0F},
                {1.0F, 1.0F, 1.0F, 0.0F},
                {1.0F, 1.0F, 1.0F, 1.0E6F},
                {1.0F, 1.0F, 1.0F, Float.POSITIVE_INFINITY},
                {-1.0F, 1.0F, 1.0F, GAIN},
                {Float.NaN, 1.0F, 1.0F, GAIN}
        };
        for (float[] light : lights) {
            HeadlightShaderModel.Uniforms uniforms = HeadlightShaderModel.Uniforms.of(scene.inverse, light[0], light[1], light[2], light[3], true);
            float[] out = HeadlightShaderModel.render(uniforms, scene.colors, scene.depth, WIDTH, HEIGHT, true);
            for (int i = 0; i < out.length; i++) {
                assertTrue(Float.isFinite(out[i]), "Licht " + java.util.Arrays.toString(light) + " gibt ungültige Farbe");
                assertTrue(out[i] >= scene.colors[i], "Licht " + java.util.Arrays.toString(light) + " dunkelt ab");
            }
        }
    }

    @Test
    void normalFromBrokenNeighboursStaysUsable() {
        Scene scene = Scene.night(true);
        HeadlightShaderModel.Uniforms uniforms = scene.uniforms(GAIN);
        float[] col = {0.05F, 0.05F, 0.05F};
        float[] pos = {0.0F, 0.0F, -3.0F};
        for (float[] normal : new float[][]{{Float.NaN, 0.0F, 0.0F}, {Float.POSITIVE_INFINITY, 1.0F, 0.0F}, {0.0F, 0.0F, 0.0F}}) {
            float[] out = HeadlightShaderModel.shadeGuarded(uniforms, col, 0.5F, pos, true, normal);
            for (int c = 0; c < 3; c++) {
                assertTrue(Float.isFinite(out[c]) && out[c] > col[c], "Normale " + java.util.Arrays.toString(normal) + ": " + out[c]);
            }
        }
    }

    @Test
    void shaderFollowsTheDocumentedLightMath() {
        Scene scene = Scene.night(true);
        HeadlightShaderModel.Uniforms uniforms = scene.uniforms(GAIN);
        double angle = Math.toRadians(20.0);
        for (double distance : new double[]{2.0, 5.0, 9.0, 15.0}) {
            float[] dir = {(float) Math.sin(angle), 0.0F, (float) -Math.cos(angle)};
            float[] pos = {(float) (dir[0] * distance), 0.0F, (float) (dir[2] * distance)};
            float[] normal = {-dir[0] * 4.0F, 0.0F, -dir[2] * 4.0F};
            float[] col = {0.2F, 0.2F, 0.2F};
            float[] out = HeadlightShaderModel.shadeGuarded(uniforms, col, 0.5F, pos, true, normal);
            double cos = Math.cos(angle);
            double cone = HeadlightMath.cone(cos) * HeadlightMath.hotspot(cos);
            double attenuation = HeadlightMath.attenuation(distance);
            double expected = 0.2 * HeadlightMath.boost(0.2, GAIN, cone, attenuation) + HeadlightMath.lift(GAIN, cone, attenuation);
            assertEquals(Math.min(1.0, expected), out[0], 2.0E-4, "Abstand " + distance);
        }
    }

    @Test
    void shaderConstantsMatchTheLightMath() throws IOException {
        Map<String, String> constants = shaderConstants();
        assertEquals(Math.cos(Math.toRadians(HeadlightMath.HOTSPOT_DEG)), number(constants, "HOTSPOT_COS"), 1.0E-6);
        assertEquals(HeadlightMath.HOTSPOT_BOOST, number(constants, "HOTSPOT_BOOST"), 1.0E-9);
        assertEquals(HeadlightMath.HUE_TRUST_FROM, number(constants, "HUE_TRUST_FROM"), 1.0E-9);
        assertEquals(HeadlightMath.HUE_TRUST_TO, number(constants, "HUE_TRUST_TO"), 1.0E-9);
        assertEquals(HeadlightMath.WARM_EDGE, number(constants, "WARM_EDGE"), 1.0E-9);
        assertEquals(HeadlightMath.WARM_CORE, number(constants, "WARM_CORE"), 1.0E-9);
        assertEquals("vec3(" + HeadlightMath.WARM_RED + ", " + HeadlightMath.WARM_GREEN + ", " + HeadlightMath.WARM_BLUE + ")", constants.get("WARM"));
        assertEquals(HeadlightShaderModel.HOTSPOT_COS, (float) number(constants, "HOTSPOT_COS"));
        assertEquals(HeadlightShaderModel.MIN_DEPTH, (float) number(constants, "MIN_DEPTH"));
        assertEquals(HeadlightShaderModel.MIN_W, (float) number(constants, "MIN_W"));
        assertEquals(HeadlightShaderModel.MAX_COORD, (float) number(constants, "MAX_COORD"));
        assertEquals(HeadlightShaderModel.MAX_GAIN, (float) number(constants, "MAX_GAIN"));
        assertTrue(HeadlightMath.GAIN < HeadlightShaderModel.MAX_GAIN, "volle Stärke liegt unter der Schutzgrenze");
    }

    private static double number(Map<String, String> constants, String name) {
        String value = constants.get(name);
        assertNotNull(value, "Konstante " + name + " fehlt im Shader");
        return Double.parseDouble(value);
    }

    private static Map<String, String> shaderConstants() throws IOException {
        try (InputStream in = HeadlightShaderTest.class.getResourceAsStream("/assets/propwash/shaders/post/fpv_headlight.fsh")) {
            assertNotNull(in, "fpv_headlight.fsh fehlt");
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(source.contains("//") || source.contains("/*"), "Shader ohne Kommentare");
            Matcher m = Pattern.compile("const\\s+(?:float|vec3)\\s+(\\w+)\\s*=\\s*([^;]+);").matcher(source);
            Map<String, String> constants = new HashMap<>();
            while (m.find()) {
                constants.put(m.group(1), m.group(2).trim());
            }
            return constants;
        }
    }

    private static boolean solidQuad(float[] depth, int x, int y) {
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                float d = depth[(y + dy) * WIDTH + x + dx];
                if (!(d > 0.0F && d < 1.0F)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static float luma(float[] colors, int i) {
        return colors[i * 3] * 0.299F + colors[i * 3 + 1] * 0.587F + colors[i * 3 + 2] * 0.114F;
    }

    private static final class Scene {
        private final float[] inverse = new float[16];
        private final float[] colors = new float[WIDTH * HEIGHT * 3];
        private final float[] depth = new float[WIDTH * HEIGHT];
        private final boolean zeroToOne;

        private Scene(boolean zeroToOne) {
            this.zeroToOne = zeroToOne;
        }

        private HeadlightShaderModel.Uniforms uniforms(float gain) {
            float[] tint = HeadlightMath.tint(1.0F, 1.0F, 1.0F);
            return HeadlightShaderModel.Uniforms.of(this.inverse, tint[0], tint[1], tint[2], gain, this.zeroToOne);
        }

        private static Scene night(boolean zeroToOne) {
            Scene scene = new Scene(zeroToOne);
            Matrix4f projection = new Matrix4f().setPerspective((float) Math.toRadians(70.0), WIDTH / (float) HEIGHT, FAR, NEAR, zeroToOne);
            Matrix4f inverse = new Matrix4f(projection).invert();
            inverse.get(scene.inverse);
            double pitch = Math.toRadians(PITCH_DEG);
            float cos = (float) Math.cos(pitch);
            float sin = (float) Math.sin(pitch);
            float[] ground = {0.0F, cos, sin};
            float[] wall = {0.0F, -sin, cos};
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int i = y * WIDTH + x;
                    float ndcX = (x + 0.5F) / WIDTH * 2.0F - 1.0F;
                    float ndcY = (y + 0.5F) / HEIGHT * 2.0F - 1.0F;
                    Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, 1.0F, 1.0F));
                    float[] ray = normalize(new float[]{near.x / near.w, near.y / near.w, near.z / near.w});
                    float best = Float.POSITIVE_INFINITY;
                    float[] colour = {0.01F, 0.015F, 0.05F};
                    float t = hit(ground, -EYE, ray);
                    if (t > 0.0F && t < best) {
                        best = t;
                        boolean checker = ((int) Math.floor(ray[0] * t) + (int) Math.floor(ray[2] * t)) % 2 == 0;
                        colour = checker ? new float[]{0.05F, 0.07F, 0.035F} : new float[]{0.04F, 0.06F, 0.03F};
                    }
                    float tw = hit(wall, -WALL_DISTANCE, ray);
                    if (tw > 0.0F && tw < best) {
                        float[] p = {ray[0] * tw, ray[1] * tw, ray[2] * tw};
                        float worldY = p[1] * cos + p[2] * sin;
                        if (Math.abs(p[0]) < 2.5F && worldY > -EYE && worldY < -EYE + 3.0F) {
                            best = tw;
                            colour = new float[]{0.07F, 0.07F, 0.08F};
                        }
                    }
                    System.arraycopy(colour, 0, scene.colors, i * 3, 3);
                    if (Float.isFinite(best) && best < FAR * 0.9F) {
                        Vector4f clip = projection.transform(new Vector4f(ray[0] * best, ray[1] * best, ray[2] * best, 1.0F));
                        float ndcZ = clip.z / clip.w;
                        scene.depth[i] = zeroToOne ? ndcZ : ndcZ * 0.5F + 0.5F;
                    } else {
                        scene.depth[i] = 0.0F;
                    }
                }
            }
            return scene;
        }

        private static float hit(float[] normal, float offset, float[] ray) {
            float facing = normal[0] * ray[0] + normal[1] * ray[1] + normal[2] * ray[2];
            if (Math.abs(facing) < 1.0E-6F) {
                return -1.0F;
            }
            return offset / facing;
        }

        private static float[] normalize(float[] v) {
            float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            return new float[]{v[0] / length, v[1] / length, v[2] / length};
        }
    }
}

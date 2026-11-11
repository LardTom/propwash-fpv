package de.lardtom.propwash.preview.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.preview.PreviewBuild;
import de.lardtom.propwash.preview.PreviewFont;
import de.lardtom.propwash.preview.PreviewStamp;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLVideo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class PreviewSelfTests {
    public static final String EXPECTED_PROPERTY = "propwash.selftest.preview";
    public static final String PACK = "propwash-selftest-preview";
    private static final String PACK_ID = "file/" + PACK;
    private static final String FLAG_FILE = "/META-INF/propwash-build.properties";
    private static final List<String> BLANK_FONTS = List.of("textures/font/ascii.png", "textures/font/accented.png",
            "textures/font/nonlatin_european.png");
    private static final String FONT_PROBE = "Propwash Preview";
    private static final String PASSES = "passes";
    private static final String DRAWN = "drawn";
    private static final String LOOKUPS = "lookups";
    private static final String READY = "ready";
    private static final String TOGGLED = "toggled";
    private static final String SHOT = "shot";
    private static final String PIXELS = "pixels";
    private static final String RELOAD = "reload";
    private static final int LIMIT_TICKS = 180;
    private static final int RELOAD_TICKS = 1200;
    private static final int INK_MIN = 170;
    private static final int SHADOW_MAX = 90;
    private static final int FRAME_WIDTH = 1280;
    private static final int FRAME_HEIGHT = 720;
    private static final int PHOTO_WIDTH = 640;
    private static final int PHOTO_HEIGHT = 360;
    private static final int GREY_LUMA = 100;
    private static final int GREY_CHROMA = 128;
    private static final int GREY_ARGB = 0xFF404040;
    private static final String TITLE_PREFIX = "Propwash Preview ";
    private static final String GUI_SHADER = "shaders/core/gui.fsh";
    private static final String GUI_SHADER_DISCARD = "abs(color.a - 180.0 / 255.0) < 0.003";
    private static final String GUI_SHADER_SOURCE = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                mat4 TextureMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
            };
            layout(location = 0) in vec4 vertexColor;
            layout(location = 0) out vec4 fragColor;
            void main() {
                vec4 color = vertexColor;
                if (color.a == 0.0) {
                    discard;
                }
                if (abs(color.a - 180.0 / 255.0) < 0.003 && (all(greaterThan(color.rgb, vec3(0.99))) || all(lessThan(color.rgb, vec3(0.01))))) {
                    discard;
                }
                fragColor = color * ColorModulator;
            }
            """;
    private static final List<String> OWN_SHADERS = List.of("shaders/core/preview_mark.vsh", "shaders/core/preview_mark.fsh");
    private static final String OWN_VERTEX_SOURCE = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec4 Color;
            layout(location = 0) out vec4 vertexColor;
            void main() {
                gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                vertexColor = vec4(0.0);
            }
            """;
    private static final String OWN_FRAGMENT_SOURCE = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(location = 0) in vec4 vertexColor;
            layout(location = 0) out vec4 fragColor;
            void main() {
                discard;
            }
            """;
    private static final int PROBE_LEFT = 8;
    private static final int PROBE_TOP = 24;
    private static final int PROBE_SIZE = 24;
    private static final int PROBE_INSET = 6;
    private static final int PROBE_BACK_ARGB = 0xFF808080;
    private static final int PROBE_DISCARDED_MIN = 100;
    private static final int PROBE_DISCARDED_MAX = 150;
    private static final int NO_PROBE = 0x00FFFFFF;
    private static @Nullable String probeKey;
    private static int normalFontWidth;
    private static volatile boolean probe;

    private PreviewSelfTests() {
    }

    public static void register() {
        ClientRegistrar.hudLayer(Constants.id("selftest_preview_probe"), (graphics, deltaTracker) -> {
            if (probe) {
                graphics.fill(PROBE_LEFT, PROBE_TOP, PROBE_LEFT + PROBE_SIZE, PROBE_TOP + PROBE_SIZE, PROBE_BACK_ARGB);
                graphics.fill(PROBE_LEFT + PROBE_INSET, PROBE_TOP + PROBE_INSET, PROBE_LEFT + PROBE_SIZE - PROBE_INSET,
                        PROBE_TOP + PROBE_SIZE - PROBE_INSET, PreviewStamp.TEXT_ARGB);
            }
        });
        SelfTest.clientStep("preview: Build-Schalter, erzeugte Konstante und Kennzeichen im Jar stimmen überein", 5, PreviewSelfTests::switchStep);
        SelfTest.clientStep("preview: Fenstertitel nennt den Vorschau-Build", 5, PreviewSelfTests::titleStep);
        SelfTest.clientStep("preview: Wasserzeichen im Spiel über dem HUD", 200, ctx -> sceneStep(ctx, Scene.HUD));
        SelfTest.clientStep("preview: Wasserzeichen im Spiel bei ausgeblendetem HUD (F1)", 200, ctx -> sceneStep(ctx, Scene.HIDDEN_HUD));
        SelfTest.clientStep("preview: Wasserzeichen im Pausenmenü", 200, ctx -> sceneStep(ctx, Scene.MENU));
        SelfTest.clientStep("preview: Wasserzeichen in Action-Cam-Bild und Foto", 20, PreviewSelfTests::stampStep);
        SelfTest.clientStep("preview: Ressourcenpaket überschreibt alle Propwash-Texte, leert die Standardschrift und ersetzt den GUI-Shader",
                RELOAD_TICKS, PreviewSelfTests::packOnStep);
        SelfTest.clientStep("preview: Wasserzeichen bleibt mit diesem Ressourcenpaket unverändert", 200, ctx -> sceneStep(ctx, Scene.PACK));
        SelfTest.clientStep("preview: Ressourcenpaket wieder entfernt", RELOAD_TICKS, PreviewSelfTests::packOffStep);
        SelfTest.afterLeaveStep("preview: Wasserzeichen im Titelbildschirm", 400, ctx -> sceneStep(ctx, Scene.TITLE));
    }

    private static boolean expected() {
        return Boolean.parseBoolean(System.getProperty(EXPECTED_PROPERTY, "false"));
    }

    private static StepResult switchStep(SelfTest.StepContext ctx) {
        boolean expected = expected();
        boolean actual = PreviewBuild.enabled();
        if (actual != expected) {
            return StepResult.fail("Build-Schalter propwash_preview=" + expected + ", im Code steht " + actual);
        }
        String flag = jarFlag();
        if (flag != null && !flag.equals(Boolean.toString(actual))) {
            return StepResult.fail("Kennzeichen " + FLAG_FILE + " im Jar sagt preview=" + flag + ", der Code " + actual);
        }
        String text = PreviewMark.text();
        String version = Services.PLATFORM.getModVersion();
        if (!text.equals("Propwash Preview " + version + " · not final")) {
            return StepResult.fail("Wasserzeichen „" + text + "“ nennt nicht die Version " + version);
        }
        ctx.log(actual ? "Vorschau-Build: „" + text + "“" : "Release-Build: kein Wasserzeichen");
        return StepResult.pass();
    }

    private static @Nullable String jarFlag() {
        try (InputStream in = PreviewSelfTests.class.getResourceAsStream(FLAG_FILE)) {
            if (in == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties.getProperty("preview", "").trim();
        } catch (IOException e) {
            return "unlesbar: " + e;
        }
    }

    private static StepResult titleStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        String shown = SDLVideo.SDL_GetWindowTitle(mc.getWindow().handle());
        mc.updateTitle();
        String updated = SDLVideo.SDL_GetWindowTitle(mc.getWindow().handle());
        String problem = titleProblem(shown);
        if (problem == null) {
            problem = titleProblem(updated);
        }
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log(PreviewBuild.enabled() ? "Fenstertitel „" + shown + "“" : "Release-Build: Fenstertitel „" + shown + "“ ohne Vorschau");
        return StepResult.pass();
    }

    private static @Nullable String titleProblem(@Nullable String title) {
        if (!PreviewBuild.enabled()) {
            return title != null && !title.contains(TITLE_PREFIX.trim()) ? null : "Release-Build mit Fenstertitel „" + title + "“";
        }
        String expected = TITLE_PREFIX + Services.PLATFORM.getModVersion();
        return expected.equals(title) ? null : "Fenstertitel „" + title + "“ statt „" + expected + "“";
    }

    private static StepResult sceneStep(SelfTest.StepContext ctx, Scene scene) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(READY)) {
            StepResult prepared = prepare(mc, scene, state);
            if (prepared.status() != StepResult.Status.PASS) {
                return ctx.tick() > LIMIT_TICKS ? fail(mc, scene, state, "Szene " + scene.label + " nicht erreicht: " + mc.gui.screen()) : prepared;
            }
            state.put(READY, ctx.tick());
            state.put(PASSES, PreviewMark.passes());
            state.put(DRAWN, scene.count());
            state.put(LOOKUPS, PreviewPipeline.lookups());
            return StepResult.pending();
        }
        if (!scene.holds(mc)) {
            return fail(mc, scene, state, "Szene " + scene.label + " verlassen: " + mc.gui.screen());
        }
        if (ctx.tick() - (int) state.get(READY) < scene.settleTicks) {
            return StepResult.pending();
        }
        long passes = PreviewMark.passes() - (long) state.get(PASSES);
        long drawn = scene.count() - (long) state.get(DRAWN);
        if (!PreviewBuild.enabled()) {
            if (PreviewMark.passes() != 0L || PreviewMark.drawn() != 0L || PreviewPipeline.lookups() != 0L || PreviewPipeline.compiles() != 0L) {
                return fail(mc, scene, state, "Release-Build ruft das Wasserzeichen auf (" + PreviewMark.passes() + " Aufrufe, " + PreviewMark.drawn()
                        + " gezeichnet, eigene Pipeline " + PreviewPipeline.lookups() + "-mal abgefragt)");
            }
            ctx.log(scene.label + ": Release-Build ohne Wasserzeichen-Aufruf");
            return finish(mc, scene, state);
        }
        if (passes <= 0L) {
            return fail(mc, scene, state, "Gui#extractRenderState ohne Aufruf des Wasserzeichens (Mixin nicht aktiv?)");
        }
        if (drawn <= 0L) {
            return fail(mc, scene, state, scene.label + ": Wasserzeichen in " + passes + " Bildern nicht gezeichnet");
        }
        long lookups = PreviewPipeline.lookups() - (long) state.get(LOOKUPS);
        if (lookups <= 0L) {
            return fail(mc, scene, state, scene.label + ": GUI-Renderer hat die eigene Pipeline des Wasserzeichens nicht abgefragt");
        }
        if (RenderSystem.getCompiledPipeline(PreviewPipeline.PIPELINE) != PreviewPipeline.compiled()) {
            return fail(mc, scene, state, scene.label + ": RenderSystem liefert für " + PreviewPipeline.PIPELINE
                    + " nicht die mit dem Shader aus dem Code übersetzte Pipeline");
        }
        if (!state.containsKey(PIXELS)) {
            state.put(PIXELS, checkPixels(mc, scene == Scene.PACK));
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<PixelCheck> pixels = (CompletableFuture<PixelCheck>) state.get(PIXELS);
        if (!pixels.isDone()) {
            return ctx.tick() > LIMIT_TICKS ? fail(mc, scene, state, "Bild für die Pixelprüfung nicht fertig") : StepResult.pending();
        }
        if (pixels.isCompletedExceptionally()) {
            return fail(mc, scene, state, "Pixelprüfung: " + pixels.exceptionNow());
        }
        PixelCheck check = pixels.join();
        if (!check.matches()) {
            return fail(mc, scene, state, scene.label + ": Wasserzeichen im Bild weicht vom Text „" + PreviewMark.text() + "“ ab: " + check);
        }
        if (scene == Scene.PACK && !check.probeDiscarded()) {
            return fail(mc, scene, state, scene.label + ": GUI-Shader des Ressourcenpakets wirkt nicht, die Probe-Fläche in den Farben des "
                    + "Wasserzeichens ist noch zu sehen: " + check);
        }
        if (scene == Scene.TITLE) {
            String title = titleProblem(SDLVideo.SDL_GetWindowTitle(mc.getWindow().handle()));
            if (title != null) {
                return fail(mc, scene, state, scene.label + ": " + title);
            }
        }
        if (Screenshots.enabled() && scene.shot != null) {
            if (!state.containsKey(SHOT)) {
                Screenshots.parkMouse(mc);
                state.put(SHOT, Screenshots.capture(scene.shot));
                return StepResult.pending();
            }
            CompletableFuture<?> shot = (CompletableFuture<?>) state.get(SHOT);
            if (!shot.isDone()) {
                return ctx.tick() > LIMIT_TICKS ? fail(mc, scene, state, "Bildschirmfoto " + scene.shot + " nicht fertig") : StepResult.pending();
            }
            if (shot.isCompletedExceptionally()) {
                return fail(mc, scene, state, "Bildschirmfoto " + scene.shot + ": " + shot.exceptionNow());
            }
            ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(scene.shot + ".png"));
        }
        ctx.log(scene.label + ": Wasserzeichen in " + drawn + " von " + passes + " Bildern, „" + PreviewMark.text() + "“, " + check
                + ", eigener Shader " + lookups + "-mal abgefragt, " + PreviewPipeline.compiles() + "-mal übersetzt");
        return finish(mc, scene, state);
    }

    private static CompletableFuture<PixelCheck> checkPixels(Minecraft mc, boolean withProbe) {
        CompletableFuture<PixelCheck> done = new CompletableFuture<>();
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        int guiScale = Math.max(1, mc.getWindow().getGuiScale());
        int lift = mc.gui.screen() instanceof TitleScreen ? PreviewMark.TITLE_COPYRIGHT_LINE * guiScale : 0;
        PreviewFont.Raster raster = PreviewFont.raster(PreviewMark.text());
        int probeX = (PROBE_LEFT + PROBE_SIZE / 2) * guiScale;
        int probeY = (PROBE_TOP + PROBE_SIZE / 2) * guiScale;
        Screenshot.takeScreenshot(target, 1, image -> {
            try (image) {
                int probeArgb = !withProbe ? NO_PROBE : probeX < image.getWidth() && probeY < image.getHeight() ? image.getPixel(probeX, probeY) : 0;
                done.complete(compare(image, raster, PreviewStamp.place(raster, image.getWidth(), image.getHeight(), lift), probeArgb));
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        });
        return done;
    }

    private static PixelCheck compare(NativeImage image, PreviewFont.Raster raster, PreviewStamp.Placement at, int probeArgb) {
        int ink = 0;
        int inkOk = 0;
        int shadow = 0;
        int shadowOk = 0;
        int scale = at.scale();
        for (int y = 0; y <= raster.height(); y++) {
            for (int x = 0; x <= raster.width(); x++) {
                boolean text = raster.ink(x, y);
                boolean shade = !text && raster.ink(x - 1, y - 1);
                if (!text && !shade) {
                    continue;
                }
                int px = at.left() + x * scale + scale / 2;
                int py = at.top() + y * scale + scale / 2;
                if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
                    if (text) {
                        ink++;
                    } else {
                        shadow++;
                    }
                    continue;
                }
                int argb = image.getPixel(px, py);
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                if (text) {
                    ink++;
                    if (Math.min(r, Math.min(g, b)) >= INK_MIN) {
                        inkOk++;
                    }
                } else {
                    shadow++;
                    if (Math.max(r, Math.max(g, b)) <= SHADOW_MAX) {
                        shadowOk++;
                    }
                }
            }
        }
        return new PixelCheck(ink, inkOk, shadow, shadowOk, image.getWidth(), image.getHeight(), at, probeArgb);
    }

    private static StepResult prepare(Minecraft mc, Scene scene, Map<String, Object> state) {
        Screen screen = mc.gui.screen();
        switch (scene) {
            case HUD, HIDDEN_HUD, PACK -> {
                if (screen != null) {
                    mc.gui.setScreen(null);
                    return StepResult.pending();
                }
                probe = scene == Scene.PACK;
                boolean wantHidden = scene == Scene.HIDDEN_HUD;
                if (mc.gui.hud.isHidden() != wantHidden) {
                    mc.gui.hud.toggle();
                    state.put(TOGGLED, Boolean.TRUE);
                }
            }
            case MENU -> {
                if (!(screen instanceof PauseScreen)) {
                    mc.gui.setScreen(new PauseScreen(true));
                    return StepResult.pending();
                }
            }
            case TITLE -> {
                if (!(screen instanceof TitleScreen)) {
                    return StepResult.pending();
                }
            }
        }
        if (mc.gui.overlay() != null) {
            return StepResult.pending();
        }
        Screenshots.clearOverlays(mc);
        return StepResult.pass();
    }

    private static StepResult finish(Minecraft mc, Scene scene, Map<String, Object> state) {
        restore(mc, scene, state);
        return StepResult.pass();
    }

    private static StepResult fail(Minecraft mc, Scene scene, Map<String, Object> state, String reason) {
        restore(mc, scene, state);
        return StepResult.fail(reason);
    }

    private static void restore(Minecraft mc, Scene scene, Map<String, Object> state) {
        probe = false;
        if (state.remove(TOGGLED) != null) {
            mc.gui.hud.toggle();
        }
        if (scene == Scene.MENU && mc.gui.screen() instanceof PauseScreen) {
            mc.gui.setScreen(null);
        }
    }

    private static Path packDir(Minecraft mc) {
        return mc.getResourcePackDirectory().resolve(PACK);
    }

    private static void writePack(Minecraft mc) throws IOException {
        Path root = packDir(mc);
        deleteTree(root);
        Files.createDirectories(root);
        String format = "[" + SharedConstants.RESOURCE_PACK_FORMAT_MAJOR + ", " + SharedConstants.RESOURCE_PACK_FORMAT_MINOR + "]";
        Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\": {\"description\": \"Propwash Selbsttest: Texte und Schrift leer, GUI-Shader ohne "
                + "Wasserzeichen-Farben\", \"min_format\": " + format + ", \"max_format\": " + format + "}}\n", StandardCharsets.UTF_8);
        Path guiShader = root.resolve("assets/minecraft").resolve(GUI_SHADER);
        Files.createDirectories(guiShader.getParent());
        Files.writeString(guiShader, GUI_SHADER_SOURCE, StandardCharsets.UTF_8);
        for (String own : OWN_SHADERS) {
            Path file = root.resolve("assets/propwash").resolve(own);
            Files.createDirectories(file.getParent());
            Files.writeString(file, own.endsWith(".vsh") ? OWN_VERTEX_SOURCE : OWN_FRAGMENT_SOURCE, StandardCharsets.UTF_8);
        }
        Map<Identifier, Resource> languages = mc.getResourceManager().listResources("lang",
                id -> id.getNamespace().equals("propwash") && id.getPath().endsWith(".json"));
        if (languages.isEmpty()) {
            throw new IOException("keine Propwash-Sprachdateien gefunden");
        }
        for (Map.Entry<Identifier, Resource> language : languages.entrySet()) {
            JsonObject original;
            try (InputStream in = language.getValue().open()) {
                original = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            JsonObject blank = new JsonObject();
            for (String key : original.keySet()) {
                blank.addProperty(key, "");
                if (probeKey == null) {
                    probeKey = key;
                }
            }
            Path file = root.resolve("assets/propwash").resolve(language.getKey().getPath());
            Files.createDirectories(file.getParent());
            Files.writeString(file, blank.toString(), StandardCharsets.UTF_8);
        }
        for (String font : BLANK_FONTS) {
            Resource resource = mc.getResourceManager().getResource(Identifier.withDefaultNamespace(font)).orElseThrow(() -> new IOException(font + " fehlt"));
            int width;
            int height;
            try (InputStream in = resource.open(); NativeImage image = NativeImage.read(in)) {
                width = image.getWidth();
                height = image.getHeight();
            }
            Path file = root.resolve("assets/minecraft").resolve(font);
            Files.createDirectories(file.getParent());
            try (NativeImage empty = new NativeImage(width, height, true)) {
                empty.writeToFile(file);
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static StepResult packOnStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RELOAD)) {
            if (mc.gui.overlay() != null) {
                return StepResult.pending();
            }
            normalFontWidth = mc.font.width(FONT_PROBE);
            try {
                writePack(mc);
            } catch (IOException | RuntimeException e) {
                return StepResult.fail("Ressourcenpaket nicht geschrieben: " + e);
            }
            PackRepository packs = mc.getResourcePackRepository();
            packs.reload();
            if (!packs.addPack(PACK_ID)) {
                return StepResult.fail("Ressourcenpaket " + PACK_ID + " nicht auswählbar: " + packs.getAvailableIds());
            }
            state.put(RELOAD, mc.reloadResourcePacks());
            return StepResult.pending();
        }
        StepResult reloaded = waitForReload(mc, state);
        if (reloaded.status() != StepResult.Status.PASS) {
            return reloaded;
        }
        String translated = probeKey == null ? null : Language.getInstance().getOrDefault(probeKey);
        int blankWidth = mc.font.width(FONT_PROBE);
        List<String> problems = new ArrayList<>();
        if (!mc.getResourcePackRepository().getSelectedIds().contains(PACK_ID)) {
            problems.add("Paket nicht aktiv");
        }
        if (translated == null || !translated.isEmpty()) {
            problems.add("Propwash-Text " + probeKey + " nicht überschrieben: „" + translated + "“");
        }
        if (blankWidth * 3 > normalFontWidth) {
            problems.add("Standardschrift nicht geleert: „" + FONT_PROBE + "“ " + blankWidth + " statt " + normalFontWidth + " breit");
        }
        String shaderPack = packShader(mc, Identifier.withDefaultNamespace(GUI_SHADER), GUI_SHADER_DISCARD);
        if (shaderPack != null) {
            problems.add(shaderPack);
        }
        for (String own : OWN_SHADERS) {
            String ownPack = packShader(mc, Constants.id(own), own.endsWith(".vsh") ? "gl_Position = vec4(2.0" : "discard;");
            if (ownPack != null) {
                problems.add(ownPack);
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        ctx.log("Ressourcenpaket aktiv: alle Propwash-Texte leer (" + probeKey + " = „“), Standardschrift leer („" + FONT_PROBE + "“ " + blankWidth
                + " statt " + normalFontWidth + " Bildpunkte breit), " + GUI_SHADER + " verwirft Weiß und Schwarz mit Alpha 180/255, "
                + String.join(" und ", OWN_SHADERS) + " unter propwash verwerfen alles");
        return StepResult.pass();
    }

    private static @Nullable String packShader(Minecraft mc, Identifier id, String marker) {
        Resource resource = mc.getResourceManager().getResource(id).orElse(null);
        if (resource == null) {
            return id + " fehlt";
        }
        if (!resource.sourcePackId().equals(PACK_ID)) {
            return id + " stammt aus " + resource.sourcePackId() + " statt aus " + PACK_ID;
        }
        try {
            return resource.readAllAsString().contains(marker) ? null : id + " ist nicht der Shader des Pakets";
        } catch (IOException e) {
            return id + " unlesbar: " + e;
        }
    }

    private static StepResult packOffStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RELOAD)) {
            if (mc.gui.overlay() != null) {
                return StepResult.pending();
            }
            mc.getResourcePackRepository().removePack(PACK_ID);
            state.put(RELOAD, mc.reloadResourcePacks());
            return StepResult.pending();
        }
        StepResult reloaded = waitForReload(mc, state);
        if (reloaded.status() != StepResult.Status.PASS) {
            return reloaded;
        }
        try {
            deleteTree(packDir(mc));
        } catch (IOException e) {
            return StepResult.fail("Ressourcenpaket nicht gelöscht: " + e);
        }
        mc.getResourcePackRepository().reload();
        String translated = probeKey == null ? "" : Language.getInstance().getOrDefault(probeKey);
        int width = mc.font.width(FONT_PROBE);
        if (mc.getResourcePackRepository().getSelectedIds().contains(PACK_ID) || translated.isEmpty() || width != normalFontWidth) {
            return StepResult.fail("Ressourcenpaket wirkt noch: " + probeKey + " = „" + translated + "“, „" + FONT_PROBE + "“ " + width + " breit");
        }
        ctx.log("Ressourcenpaket entfernt, Texte und Schrift wieder normal");
        return StepResult.pass();
    }

    private static StepResult waitForReload(Minecraft mc, Map<String, Object> state) {
        CompletableFuture<?> reload = (CompletableFuture<?>) state.get(RELOAD);
        if (!reload.isDone() || mc.gui.overlay() != null) {
            return StepResult.pending();
        }
        if (reload.isCompletedExceptionally()) {
            return StepResult.fail("Neuladen der Ressourcen fehlgeschlagen: " + reload.exceptionNow());
        }
        return StepResult.pass();
    }

    private static StepResult stampStep(SelfTest.StepContext ctx) {
        boolean enabled = PreviewBuild.enabled();
        int luma = FRAME_WIDTH * FRAME_HEIGHT;
        ByteBuffer frame = ByteBuffer.allocateDirect(luma * 3 / 2);
        for (int i = 0; i < frame.capacity(); i++) {
            frame.put(i, (byte) (i < luma ? GREY_LUMA : GREY_CHROMA));
        }
        long framesBefore = PreviewMark.stampedFrames();
        boolean stampedFrame = PreviewMark.stampFrame(frame, FRAME_WIDTH, FRAME_HEIGHT);
        int frameChanged = 0;
        for (int i = 0; i < luma; i++) {
            if ((frame.get(i) & 0xFF) != GREY_LUMA) {
                frameChanged++;
            }
        }
        boolean stampedPhoto;
        int photoChanged = 0;
        try (NativeImage photo = new NativeImage(PHOTO_WIDTH, PHOTO_HEIGHT, false)) {
            photo.fillRect(0, 0, PHOTO_WIDTH, PHOTO_HEIGHT, GREY_ARGB);
            stampedPhoto = PreviewMark.stampImage(photo);
            for (int y = 0; y < PHOTO_HEIGHT; y++) {
                for (int x = 0; x < PHOTO_WIDTH; x++) {
                    if (photo.getPixel(x, y) != GREY_ARGB) {
                        photoChanged++;
                    }
                }
            }
        }
        if (!enabled) {
            return StepResult.check(!stampedFrame && frameChanged == 0 && !stampedPhoto && photoChanged == 0,
                    "Release-Build stempelt: Bild " + frameChanged + ", Foto " + photoChanged + " Bildpunkte");
        }
        if (!stampedFrame || frameChanged < 100 || PreviewMark.stampedFrames() != framesBefore + 1L) {
            return StepResult.fail("Action-Cam-Bild " + FRAME_WIDTH + "x" + FRAME_HEIGHT + " ohne Wasserzeichen: gestempelt " + stampedFrame + ", "
                    + frameChanged + " Bildpunkte");
        }
        if (!stampedPhoto || photoChanged < 50) {
            return StepResult.fail("Foto " + PHOTO_WIDTH + "x" + PHOTO_HEIGHT + " ohne Wasserzeichen: gestempelt " + stampedPhoto + ", " + photoChanged
                    + " Bildpunkte");
        }
        ctx.log("Wasserzeichen im Action-Cam-Bild (" + frameChanged + " Bildpunkte) und im Foto (" + photoChanged + "), bisher "
                + PreviewMark.stampedFrames() + " Bilder und " + PreviewMark.stampedImages() + " Fotos gestempelt");
        return StepResult.pass();
    }

    private record PixelCheck(int ink, int inkOk, int shadow, int shadowOk, int width, int height, PreviewStamp.Placement at, int probeArgb) {
        private boolean matches() {
            return this.ink > 0 && this.inkOk == this.ink && this.shadowOk == this.shadow;
        }

        private boolean probeDiscarded() {
            int r = this.probeArgb >> 16 & 0xFF;
            int g = this.probeArgb >> 8 & 0xFF;
            int b = this.probeArgb & 0xFF;
            return Math.min(r, Math.min(g, b)) >= PROBE_DISCARDED_MIN && Math.max(r, Math.max(g, b)) <= PROBE_DISCARDED_MAX;
        }

        @Override
        public String toString() {
            return "Schrift " + this.inkOk + "/" + this.ink + " hell, Schatten " + this.shadowOk + "/" + this.shadow + " dunkel, Bild " + this.width
                    + "x" + this.height + ", ab " + this.at.left() + "," + this.at.top() + " mit " + this.at.scale() + " Bildpunkten je Schriftpunkt"
                    + (this.probeArgb == NO_PROBE ? "" : ", Probe " + String.format("%06X", this.probeArgb & 0xFFFFFF)
                    + (this.probeDiscarded() ? " (Paket-Shader verwirft sie)" : " (Paket-Shader wirkt nicht)"));
        }
    }

    private enum Scene {
        HUD("im Spiel", "preview_ingame", 20),
        HIDDEN_HUD("im Spiel mit F1", "preview_ingame_f1", 20),
        MENU("im Pausenmenü", "preview_menu", 60),
        PACK("im Spiel mit Ressourcenpaket ohne Texte und Schrift, GUI-Shader verwirft Wasserzeichen-Farben", "preview_resourcepack", 20),
        TITLE("im Titelbildschirm", "preview_title", 20);

        private final String label;
        private final @Nullable String shot;
        private final int settleTicks;

        Scene(String label, @Nullable String shot, int settleTicks) {
            this.label = label;
            this.shot = shot;
            this.settleTicks = settleTicks;
        }

        private long count() {
            return switch (this) {
                case HUD, PACK -> PreviewMark.drawn();
                case HIDDEN_HUD -> PreviewMark.drawnHudHidden();
                case MENU -> PreviewMark.drawnOnScreen();
                case TITLE -> PreviewMark.drawnOnTitle();
            };
        }

        private boolean holds(Minecraft mc) {
            Screen screen = mc.gui.screen();
            return switch (this) {
                case HUD, PACK -> screen == null && !mc.gui.hud.isHidden();
                case HIDDEN_HUD -> screen == null && mc.gui.hud.isHidden();
                case MENU -> screen instanceof PauseScreen;
                case TITLE -> screen instanceof TitleScreen;
            };
        }
    }
}

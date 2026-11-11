package de.lardtom.propwash.osd.addon.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdTone;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleOsd;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.addon.AccessoryActionHints;
import de.lardtom.propwash.input.addon.client.AccessoryHintClient;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.osd.client.OsdRenderer;
import de.lardtom.propwash.ui.client.theme.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovAddonOsdSelfTests {
    public static final String NAME = AddonOsdSelfTests.GROUP + "Abdeckung: Farbe, Größe und Warnfarben des Beispiel-Elements im Flug aus dem Bildpuffer";
    public static final String CHAT_NAME = AddonOsdSelfTests.GROUP
            + "Abdeckung: Zubehör-Hinweise früherer Schritte stehen beim Start der Bildprüfung noch im Chat";
    private static final int CHAT_LINES = 12;
    private static final String ELEMENT_COLOR = "#00FFFF";
    private static final int CYAN = 0x00FFFF;
    private static final int ROUNDS = 12;
    private static final int LOW_ROUNDS = 2;
    private static final int MIN_PIXELS = 12;
    private static final float BIG = 2.0F;
    private static final String PHASE = "cov_a2_phase";
    private static final String SINCE = "cov_a2_since";
    private static final String FUTURE = "cov_a2_future";
    private static final String RESULT = "cov_a2_result";
    private static final String SAMPLE = "cov_a2_sample";
    private static final String QUIET = "cov_a2_quiet";
    private static final int QUIET_TICKS = 10;
    private static final int[] PLACE = {ExampleOsd.COLUMN + 3, ExampleOsd.ROW + 2};

    private CovAddonOsdSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(CHAT_NAME, 100, CovAddonOsdSelfTests::chatStep);
        SelfTest.clientStep(NAME, 2400, CovAddonOsdSelfTests::step);
    }

    private static int chatLines() {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            return ((List<?>) field.get(Minecraft.getInstance().gui.hud.getChat())).size();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Chat nicht lesbar: " + e, e);
        }
    }

    private static int clearChat(SelfTest.StepContext ctx) {
        int lines = chatLines();
        if (lines > 0) {
            Minecraft.getInstance().gui.hud.getChat().clearMessages(false);
        }
        ctx.state().put(QUIET, ctx.tick());
        return lines;
    }

    private static boolean chatQuiet(SelfTest.StepContext ctx) {
        int lines = chatLines();
        if (lines > 0) {
            clearChat(ctx);
            ctx.log("Chat vor dem Bild in Phase " + ctx.state().get(PHASE) + " geleert: " + lines + " Zeilen");
            return false;
        }
        Object quiet = ctx.state().get(QUIET);
        return !(quiet instanceof Integer tick) || ctx.tick() - tick >= QUIET_TICKS;
    }

    private static StepResult chatStep(SelfTest.StepContext ctx) {
        if (!JoinTestEnv.exampleAddon() || AddonOsdSelfTests.ammoSpec() == null) {
            ctx.log("Beispiel-Add-on aus, nichts vorzubereiten");
            return StepResult.pass();
        }
        Component title = AccessoryHintClient.message(AccessoryType.byId(ExampleSwitches.SIGNAL_LIGHT).orElse(null),
                new AccessoryActionHints.Named(ExampleSwitches.SIGNAL_LIGHT, Map.of())).getFirst();
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        int before = chatLines();
        for (int i = 0; i < CHAT_LINES; i++) {
            chat.addClientSystemMessage(title);
        }
        int lines = chatLines();
        ctx.log(String.format(Locale.ROOT, "%d Zeilen „%s“ in der Farbe der Hinweis-Titel #%06X dazu, Chat %d → %d Zeilen", CHAT_LINES,
                title.getString(), Palette.ACCENT & 0xFFFFFF, before, lines));
        return lines >= CHAT_LINES ? StepResult.pass() : StepResult.fail("nur " + lines + " Zeilen im Chat statt mindestens " + CHAT_LINES);
    }

    private record Sample(int cyan, int amber, int cyanRows, int amberRows, int width, int height, String box, String top) {
        String describe() {
            return String.format(Locale.ROOT, "Türkis %d Pixel in %d Zeilen, Gelb %d Pixel in %d Zeilen (Ausschnitt %s, Bild %dx%d, häufigste Farben %s)",
                    this.cyan, this.cyanRows, this.amber, this.amberRows, this.box, this.width, this.height, this.top);
        }
    }

    private static boolean cyanLike(int pixel) {
        int r = ARGB.red(pixel);
        int g = ARGB.green(pixel);
        int b = ARGB.blue(pixel);
        return g > r + 70 && b > r + 70 && Math.abs(g - b) < 50;
    }

    private static boolean amberLike(int pixel) {
        int r = ARGB.red(pixel);
        int g = ARGB.green(pixel);
        int b = ARGB.blue(pixel);
        return r > 120 && r > g + 25 && g > b + 50;
    }

    private static Sample measure(NativeImage image, float[] bounds, int guiWidth, int guiHeight, String name) {
        double sx = image.getWidth() / (double) guiWidth;
        double sy = image.getHeight() / (double) guiHeight;
        int x0 = Math.max(0, (int) Math.floor(bounds[0] * sx));
        int y0 = Math.max(0, (int) Math.floor(bounds[1] * sy));
        int x1 = Math.min(image.getWidth(), (int) Math.ceil(bounds[2] * sx));
        int y1 = Math.min(image.getHeight(), (int) Math.ceil(bounds[3] * sy));
        int cyan = 0;
        int amber = 0;
        int cyanRows = 0;
        int amberRows = 0;
        Map<Integer, Integer> colors = new java.util.HashMap<>();
        for (int y = y0; y < y1; y++) {
            boolean cyanRow = false;
            boolean amberRow = false;
            for (int x = x0; x < x1; x++) {
                int pixel = image.getPixel(x, y);
                colors.merge(pixel & 0xE0E0E0, 1, Integer::sum);
                if (cyanLike(pixel)) {
                    cyan++;
                    cyanRow = true;
                } else if (amberLike(pixel)) {
                    amber++;
                    amberRow = true;
                }
            }
            cyanRows += cyanRow ? 1 : 0;
            amberRows += amberRow ? 1 : 0;
        }
        save(image, x0, y0, x1, y1, name);
        String top = colors.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()).limit(5)
                .map(entry -> String.format(Locale.ROOT, "#%06X×%d", entry.getKey(), entry.getValue())).reduce((a, b) -> a + " " + b).orElse("-");
        return new Sample(cyan, amber, cyanRows, amberRows, image.getWidth(), image.getHeight(), x0 + "," + y0 + "–" + x1 + "," + y1, top);
    }

    private static void save(NativeImage image, int x0, int y0, int x1, int y1, String name) {
        int width = Math.max(1, x1 - x0);
        int height = Math.max(1, y1 - y0);
        try (NativeImage crop = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    crop.setPixel(x, y, image.getPixel(Math.min(image.getWidth() - 1, x0 + x), Math.min(image.getHeight() - 1, y0 + y)));
                }
            }
            java.nio.file.Path file = de.lardtom.propwash.platform.Services.PLATFORM.getGameDir().resolve("cov-api").resolve(name + ".png");
            java.nio.file.Files.createDirectories(file.getParent());
            crop.writeToFile(file);
        } catch (java.io.IOException | RuntimeException e) {
            de.lardtom.propwash.Constants.LOG.warn("[Selbsttest] Ausschnitt {} nicht gespeichert: {}", name, e.toString());
        }
    }

    private static void configure(float scale, boolean coloredAlerts) {
        OsdElementSpec spec = AddonOsdSelfTests.ammoSpec();
        if (spec == null) {
            throw new IllegalStateException("Element " + ExampleOsd.AMMO + " ist nicht angemeldet");
        }
        OsdConfig config = OsdClient.CONFIG.get().copy();
        config.opacity = 100;
        config.background = OsdConfig.Background.OFF;
        config.coloredAlerts = coloredAlerts;
        OsdConfig.Slot slot = AddonOsdKeys.slot(config, spec);
        slot.enabled = true;
        slot.col = PLACE[0];
        slot.row = PLACE[1];
        slot.color = ELEMENT_COLOR;
        slot.scale = scale;
        OsdClient.CONFIG.set(config);
    }

    private static String phase(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            state.put(PHASE, "spawn");
            state.put(SINCE, ctx.tick());
        }
        return (String) state.get(PHASE);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().get(SINCE);
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(SAMPLE);
        return StepResult.pending();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RESULT)) {
            state.put(RESULT, result);
            PilotSession.INSTANCE.clearTestInput();
            AddonOsdSelfTests.restoreOsd();
            state.put(FUTURE, ctx.onServer(AddonOsdSelfTests::release));
            ctx.state().put(PHASE, "cleanup");
            ctx.state().put(SINCE, ctx.tick());
        }
        return StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Sample sample(SelfTest.StepContext ctx) {
        String name = "osd_" + ctx.state().get(PHASE);
        Map<String, Object> state = ctx.state();
        AtomicReference<Sample> taken = (AtomicReference<Sample>) state.get(SAMPLE);
        if (taken == null) {
            AddonOsdLayer live = AddonOsdLayer.live();
            float[] bounds = live == null ? null : live.bounds(ExampleOsd.AMMO);
            if (bounds == null || !chatQuiet(ctx)) {
                return null;
            }
            AtomicReference<Sample> out = new AtomicReference<>();
            state.put(SAMPLE, out);
            Minecraft mc = Minecraft.getInstance();
            int guiWidth = mc.getWindow().getGuiScaledWidth();
            int guiHeight = mc.getWindow().getGuiScaledHeight();
            float[] copy = bounds.clone();
            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            Screenshot.takeScreenshot(target, 1, image -> {
                try (NativeImage owned = image) {
                    out.set(measure(owned, copy, guiWidth, guiHeight, name));
                }
            });
            return null;
        }
        return taken.get();
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        if (!JoinTestEnv.exampleAddon() || AddonOsdSelfTests.ammoSpec() == null) {
            ctx.log("Beispiel-Add-on aus, nichts zu prüfen");
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        String phase = phase(ctx);
        int since = since(ctx);
        if (!phase.equals("spawn") && !phase.equals("fpv") && !phase.equals("cleanup") && !AddonOsdSelfTests.flying()) {
            return cleanup(ctx, StepResult.fail("FPV-Sitzung endete in Phase " + phase));
        }
        try {
            switch (phase) {
                case "spawn" -> {
                    Minecraft.getInstance().gui.setScreen(null);
                    ctx.log("Chat geleert, damit keine Zeile früherer Schritte über dem Element liegt: " + clearChat(ctx) + " Zeilen");
                    AddonOsdSelfTests.showAmmo(PLACE);
                    ExampleOsd.ghost(false);
                    configure(1.0F, true);
                    state.put(FUTURE, ctx.onServer(server -> AddonOsdSelfTests.spawnAndFly(server, ROUNDS)));
                    return next(ctx, "fpv");
                }
                case "fpv" -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (future.isCompletedExceptionally()) {
                        return cleanup(ctx, StepResult.fail("Start: " + future.exceptionNow().getMessage()));
                    }
                    if (!future.isDone() || !AddonOsdSelfTests.flying()) {
                        return since > 400 ? cleanup(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
                    }
                    return next(ctx, "arm");
                }
                case "arm" -> {
                    AddonOsdSelfTests.arm(since);
                    return since >= 20 ? next(ctx, "climb") : StepResult.pending();
                }
                case "climb" -> {
                    AddonOsdSelfTests.hover();
                    if (AddonOsdSelfTests.height() > 2.2) {
                        return next(ctx, "normal");
                    }
                    return since > 400 ? cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Drohne steigt nicht: %.2f m", AddonOsdSelfTests.height())))
                            : StepResult.pending();
                }
                case "normal" -> {
                    AddonOsdSelfTests.hover();
                    if (since < 10) {
                        return StepResult.pending();
                    }
                    Sample sample = sample(ctx);
                    if (sample == null) {
                        return since > 200 ? cleanup(ctx, StepResult.fail("kein Bild vom Element")) : StepResult.pending();
                    }
                    ctx.log("Elementfarbe " + ELEMENT_COLOR + ", Größe 1,0, Ton NORMAL: " + sample.describe());
                    if (sample.cyan() < MIN_PIXELS || sample.amber() > sample.cyan() / 4) {
                        return cleanup(ctx, StepResult.fail("Element nicht in der Elementfarbe " + ELEMENT_COLOR + ": " + sample.describe()));
                    }
                    state.put("rows", sample.cyanRows());
                    configure(BIG, true);
                    return next(ctx, "big");
                }
                case "big" -> {
                    AddonOsdSelfTests.hover();
                    if (since < 10) {
                        return StepResult.pending();
                    }
                    Sample sample = sample(ctx);
                    if (sample == null) {
                        return since > 200 ? cleanup(ctx, StepResult.fail("kein Bild vom großen Element")) : StepResult.pending();
                    }
                    int small = (int) state.get("rows");
                    double ratio = sample.cyanRows() / (double) Math.max(1, small);
                    ctx.log(String.format(Locale.ROOT, "Größe %.1f: %s, Glyphenhöhe %d → %d Zeilen (Faktor %.2f)", BIG, sample.describe(), small,
                            sample.cyanRows(), ratio));
                    if (sample.cyan() < MIN_PIXELS || ratio < 1.6 || ratio > 2.5) {
                        return cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Glyphenhöhe bei Größe %.1f nur Faktor %.2f (%d → %d Zeilen)", BIG,
                                ratio, small, sample.cyanRows())));
                    }
                    configure(1.0F, true);
                    state.put(FUTURE, ctx.onServer(server -> AddonOsdSelfTests.load(server, LOW_ROUNDS)));
                    return next(ctx, "caution");
                }
                case "caution" -> {
                    AddonOsdSelfTests.hover();
                    AddonOsdLayer live = AddonOsdLayer.live();
                    boolean low = live != null && (ExampleOsd.LABEL + LOW_ROUNDS).equals(live.text(ExampleOsd.AMMO));
                    if (!low || since < 10) {
                        return since > 200 ? cleanup(ctx, StepResult.fail("Munition sinkt nicht auf " + LOW_ROUNDS)) : StepResult.pending();
                    }
                    Sample sample = sample(ctx);
                    if (sample == null) {
                        return since > 300 ? cleanup(ctx, StepResult.fail("kein Bild vom Element mit Vorsicht")) : StepResult.pending();
                    }
                    ctx.log("Warnfarben an, Ton CAUTION: " + sample.describe());
                    if (sample.amber() < MIN_PIXELS || sample.cyan() > sample.amber() / 4) {
                        return cleanup(ctx, StepResult.fail("CAUTION mit Warnfarben nicht in Gelb (" + Integer.toHexString(OsdRenderer.AMBER) + "): "
                                + sample.describe()));
                    }
                    configure(1.0F, false);
                    return next(ctx, "plain");
                }
                case "plain" -> {
                    AddonOsdSelfTests.hover();
                    if (since < 10) {
                        return StepResult.pending();
                    }
                    Sample sample = sample(ctx);
                    if (sample == null) {
                        return since > 200 ? cleanup(ctx, StepResult.fail("kein Bild vom Element ohne Warnfarben")) : StepResult.pending();
                    }
                    AddonOsdLayer live = AddonOsdLayer.live();
                    OsdTone tone = null;
                    if (live != null) {
                        for (AddonOsdLayer.Shown shown : live.shown()) {
                            if (shown.spec().id().equals(ExampleOsd.AMMO)) {
                                tone = shown.tone();
                            }
                        }
                    }
                    ctx.log("Warnfarben aus, Ton " + tone + ": " + sample.describe());
                    if (tone != OsdTone.CAUTION) {
                        return cleanup(ctx, StepResult.fail("Ton ohne Warnfarben " + tone + " statt CAUTION"));
                    }
                    if (sample.cyan() < MIN_PIXELS || sample.amber() > sample.cyan() / 4) {
                        return cleanup(ctx, StepResult.fail("CAUTION mit Warnfarben aus nicht in der Elementfarbe " + ELEMENT_COLOR + ": "
                                + sample.describe()));
                    }
                    return cleanup(ctx, StepResult.pass());
                }
                case "cleanup" -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (future != null && !future.isDone() || PilotSession.INSTANCE.isActive()) {
                        return since > 200 ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
                    }
                    return (StepResult) state.get(RESULT);
                }
                default -> {
                    return cleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
                }
            }
        } catch (RuntimeException e) {
            return cleanup(ctx, StepResult.fail(e.toString()));
        }
    }
}

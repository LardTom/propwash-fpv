package de.lardtom.propwash.gpucompat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.DeviceInfo;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.gpucompat.GpuCompatDecision;
import de.lardtom.propwash.gpucompat.GpuCompatMode;
import de.lardtom.propwash.gpucompat.GpuPaths;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.SettingsTab;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.video.client.CompatVideoSelfTests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class GpuCompatSelfTests {
    public static final String DETECT_STEP = "compat: Erkennung – Entscheidung beim Start, Mixins gegriffen, OpenGL-Weg und Erweiterungen passen";
    public static final String PATHS_STEP = "compat: Grafikwege – Testbild erkennt Blit ohne Wirkung und Rücklesen mit Nullen, wählt den Ersatzweg";
    public static final String SETTING_STEP = "compat: Einstellung Grafik-Kompatibilität im Tab Konfiguration – Automatisch/An/Aus, Hinweis auf Neustart";
    private static final String DSA = "GL_ARB_direct_state_access";
    private static final String STORAGE = "GL_ARB_buffer_storage";

    private GpuCompatSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(DETECT_STEP, 20, GpuCompatSelfTests::detect);
        SelfTest.clientStep(PATHS_STEP, 20, GpuCompatSelfTests::paths);
        SelfTest.clientStep(SETTING_STEP, 200, GpuCompatSelfTests::setting);
        CompatVideoSelfTests.register();
    }

    private static StepResult detect(SelfTest.StepContext ctx) {
        GpuCompatHooks.Status status = GpuCompatHooks.status();
        DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
        ctx.log("Grafik-Kompatibilität: " + status.describe());
        ctx.log("Gerät: " + info.vendorName() + " / " + info.name() + " / " + info.driverInfo() + " (" + info.backendName() + "), Erweiterungen "
                + info.underlyingExtensions() + ", Persistent Mapping " + info.features().persistentMapping());
        if (!"OpenGL".equals(info.backendName())) {
            return StepResult.check(!status.hooked(), "kein OpenGL, die Mixins melden trotzdem einen Weg: " + status.describe());
        }
        List<String> failures = new ArrayList<>();
        if (!status.hooked() || status.devices() < 1) {
            failures.add("Mixins nicht gegriffen: " + status.describe());
        }
        GpuCompatDecision expected = expected(info);
        GpuCompatDecision decision = status.decision();
        ctx.log("erwartet: " + expected.describe());
        if (decision.active() != expected.active() || decision.reason() != expected.reason() || decision.source() != expected.source()
                || decision.simulated() != expected.simulated()) {
            failures.add("Entscheidung „" + decision.describe() + "“ statt „" + expected.describe() + "“");
        }
        boolean dsaUsed = info.underlyingExtensions().contains(DSA);
        boolean storageUsed = info.underlyingExtensions().contains(STORAGE);
        if ("Core".equals(status.dsaChosen()) != dsaUsed) {
            failures.add("DSA-Weg " + status.dsaChosen() + " passt nicht zu den Erweiterungen (" + DSA + " " + dsaUsed + ")");
        }
        if ("Immutable".equals(status.storageChosen()) != storageUsed || info.features().persistentMapping() != storageUsed) {
            failures.add("Puffer-Weg " + status.storageChosen() + " passt nicht zu den Erweiterungen (" + STORAGE + " " + storageUsed + ")");
        }
        if (decision.active()) {
            if (!status.emulated() || !status.mutableBuffers() || dsaUsed || storageUsed) {
                failures.add("Kompatibilität an, aber nicht der Weg wie auf macOS: " + status.describe());
            }
        } else {
            if (Boolean.FALSE.equals(status.dsaOffered()) && !status.emulated()) {
                failures.add("Treiber ohne DSA, trotzdem Weg " + status.dsaChosen());
            }
            if (Boolean.FALSE.equals(status.storageOffered()) && !status.mutableBuffers()) {
                failures.add("Treiber ohne Buffer Storage, trotzdem Weg " + status.storageChosen());
            }
        }
        String log = log();
        if (log != null) {
            long lines = log.lines().filter(line -> line.contains("Grafik-Kompatibilität: ") && !line.contains("Grafikwege")
                    && !line.contains("[Selbsttest]")).count();
            ctx.log("Logzeilen „Grafik-Kompatibilität:“ im latest.log: " + lines);
            if (lines != 1L) {
                failures.add(lines + " Logzeilen „Grafik-Kompatibilität:“ statt genau einer");
            }
        }
        return failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures));
    }

    private static GpuCompatDecision expected(DeviceInfo info) {
        GpuCompatMode override = GpuCompatDecision.override(System.getProperty(GpuCompatDecision.MODE_PROPERTY), System.getenv(GpuCompatDecision.MODE_ENV));
        GpuCompatMode mode = override != null ? override : GpuCompatSettings.stored();
        boolean simulated = Services.PLATFORM.isDevelopmentEnvironment()
                && GpuCompatDecision.simulateIntelWindows(System.getenv(GpuCompatDecision.SIMULATE_ENV));
        return GpuCompatDecision.decide(mode, override != null ? GpuCompatDecision.Source.OVERRIDE : GpuCompatDecision.Source.SETTING,
                simulated || Util.getPlatform() == Util.OS.WINDOWS, simulated ? GpuCompatDecision.SIMULATED_VENDOR : info.vendorName(),
                simulated ? GpuCompatDecision.SIMULATED_RENDERER : info.name(), simulated);
    }

    private static @Nullable String log() {
        try {
            return Files.readString(Services.PLATFORM.getGameDir().resolve("logs").resolve("latest.log"), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static StepResult paths(SelfTest.StepContext ctx) {
        List<String> failures = new ArrayList<>();
        try {
            check(ctx, failures, "ohne Fehler", false, false, false, GpuPaths.Result.OK, GpuPaths.Result.OK, GpuPaths.Result.OK, GpuPaths.Result.OK,
                    GpuPaths.ReadPath.GPU);
            check(ctx, failures, "Blit ohne Wirkung", true, false, false, GpuPaths.Result.OK, GpuPaths.Result.OK, GpuPaths.Result.ZERO,
                    GpuPaths.Result.OK, GpuPaths.ReadPath.GPU);
            check(ctx, failures, "GPU-Rücklesen liefert Nullen", false, true, false, GpuPaths.Result.ZERO, GpuPaths.Result.OK, GpuPaths.Result.OK,
                    GpuPaths.Result.OK, GpuPaths.ReadPath.CLASSIC);
            check(ctx, failures, "Blit und GPU-Rücklesen wie vermutet auf Intel", true, true, false, GpuPaths.Result.ZERO, GpuPaths.Result.OK,
                    GpuPaths.Result.ZERO, GpuPaths.Result.OK, GpuPaths.ReadPath.CLASSIC);
            check(ctx, failures, "auch klassisches Rücklesen liefert Nullen", false, true, true, GpuPaths.Result.ZERO, GpuPaths.Result.ZERO,
                    GpuPaths.Result.UNTESTED, GpuPaths.Result.UNTESTED, GpuPaths.ReadPath.NONE);
            check(ctx, failures, "alles ohne Wirkung", true, true, true, GpuPaths.Result.ZERO, GpuPaths.Result.ZERO, GpuPaths.Result.UNTESTED,
                    GpuPaths.Result.UNTESTED, GpuPaths.ReadPath.NONE);
        } finally {
            GpuFaults.clear();
        }
        GpuPaths.Health clean = GpuPathCheck.recheck();
        ctx.log("danach: " + clean.describe() + " (" + GpuPathCheck.runs() + " Prüfungen)");
        if (clean.anyBroken()) {
            failures.add("nach der Fehler-Injektion nicht wieder alles ok: " + clean.describe());
        }
        return failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures));
    }

    private static void check(SelfTest.StepContext ctx, List<String> failures, String name, boolean copy, boolean read, boolean classic,
                              GpuPaths.Result gpu, GpuPaths.Result classicRead, GpuPaths.Result blit, GpuPaths.Result shader, GpuPaths.ReadPath path) {
        GpuFaults.set(copy, read, classic);
        long start = Util.getNanos();
        GpuPaths.Health health = GpuPathCheck.recheck();
        double millis = (Util.getNanos() - start) / 1.0E6;
        ctx.log(String.format("%s (%.2f ms): %s", name, millis, health.describe()));
        if (health.gpuRead() != gpu || health.classicRead() != classicRead || health.blit() != blit || health.shaderCopy() != shader
                || health.readPath() != path) {
            failures.add(name + ": " + health.describe() + " statt GPU " + gpu.text() + ", klassisch " + classicRead.text() + ", Blit " + blit.text()
                    + ", Shader " + shader.text() + ", Weg " + path.text());
        }
    }

    private static StepResult setting(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("saved")) {
            state.put("saved", GpuCompatSettings.stored());
            GpuCompatSettings.store(GpuCompatMode.AUTO);
            ConfigTabs.open(null, SettingsTab.ID);
            return StepResult.pending();
        }
        GpuCompatMode saved = (GpuCompatMode) state.get("saved");
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return ctx.tick() > 100 ? done(saved, StepResult.fail("Konfigurator öffnet nicht")) : StepResult.pending();
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !SettingsTab.ID.equals(active.id())) {
            screen.selectTab(SettingsTab.ID);
            return StepResult.pending();
        }
        String label = Component.translatable(GpuCompatClient.SETTING).getString();
        ChoiceButton<?> button = null;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ChoiceButton<?> choice && choice.getMessage().getString().equals(label)) {
                button = choice;
            }
        }
        if (button == null) {
            return ctx.tick() > 100 ? done(saved, StepResult.fail("kein Knopf „" + label + "“ im Tab Konfiguration")) : StepResult.pending();
        }
        Supplier<?> getter = getter(button);
        if (getter == null) {
            return done(saved, StepResult.fail("Knopf ohne lesbaren Wert"));
        }
        List<String> failures = new ArrayList<>();
        List<Object> seen = new ArrayList<>();
        button.setFocused(true);
        for (int i = 0; i < 3; i++) {
            seen.add(getter.get());
            button.mouseScrolled(0.0, 0.0, 0.0, -1.0);
        }
        seen.add(getter.get());
        ctx.log("Knopf „" + label + "“: " + seen);
        if (!seen.equals(List.of(GpuCompatMode.AUTO, GpuCompatMode.ON, GpuCompatMode.OFF, GpuCompatMode.AUTO))) {
            failures.add("Reihenfolge " + seen + " statt Automatisch → An → Aus → Automatisch");
        }
        boolean active0 = GpuCompatHooks.status().decision().active();
        GpuCompatMode flip = active0 ? GpuCompatMode.OFF : GpuCompatMode.ON;
        String restart = Component.translatable("propwash.gpucompat.state.restart").getString();
        String flipped = GpuCompatClient.state(flip).getString();
        String unchanged = GpuCompatClient.state(GpuCompatHooks.status().decision().active() ? GpuCompatMode.ON : GpuCompatMode.OFF).getString();
        ctx.log("Hinweis bei " + flip + ": " + flipped);
        boolean overridden = GpuCompatHooks.status().decision().source() == GpuCompatDecision.Source.OVERRIDE;
        if (!overridden && !flipped.contains(restart)) {
            failures.add("kein Hinweis auf den Neustart, wenn " + flip + " den Weg ändert: " + flipped);
        }
        if (unchanged.contains(restart)) {
            failures.add("Hinweis auf den Neustart, obwohl sich nichts ändert: " + unchanged);
        }
        for (int i = 0; i < GpuCompatClient.CHOICES.size() && getter.get() != flip; i++) {
            button.mouseScrolled(0.0, 0.0, 0.0, -1.0);
        }
        if (getter.get() != flip) {
            return done(saved, StepResult.fail("Knopf lässt sich nicht auf " + flip + " stellen"));
        }
        screen.saveAndClose();
        GpuCompatMode stored = GpuCompatSettings.CONFIG.get().mode;
        String file;
        try {
            file = Files.readString(GpuCompatSettings.CONFIG.path(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            file = "? " + exception;
        }
        ctx.log("gespeichert: " + stored + " in " + GpuCompatSettings.CONFIG.path() + ": " + file.strip().replaceAll("\\s+", " "));
        if (stored != flip || !file.contains("\"" + flip.name() + "\"")) {
            failures.add("Speichern ergibt " + stored + " statt " + flip);
        }
        if (GpuCompatHooks.status().decision().active() != active0) {
            failures.add("Entscheidung hat sich ohne Neustart geändert");
        }
        return done(saved, failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures)));
    }

    private static @Nullable Supplier<?> getter(ChoiceButton<?> button) {
        try {
            Field field = ChoiceButton.class.getDeclaredField("getter");
            field.setAccessible(true);
            return (Supplier<?>) field.get(button);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static StepResult done(GpuCompatMode saved, StepResult result) {
        if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        GpuCompatSettings.store(saved);
        return result;
    }
}

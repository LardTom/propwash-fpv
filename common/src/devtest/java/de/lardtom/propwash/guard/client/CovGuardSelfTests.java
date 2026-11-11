package de.lardtom.propwash.guard.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.SelfTestRunner;
import de.lardtom.propwash.guard.CovGuardDatapack;
import de.lardtom.propwash.guard.CovGuardServerChecks;
import de.lardtom.propwash.guard.GuardCrash;
import de.lardtom.propwash.guard.MixinScan;
import de.lardtom.propwash.guard.NamespaceGuard;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.CovLogCapture;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class CovGuardSelfTests {
    public static final String GROUP = "guard: Abdeckung – ";
    public static final String NOCLIP = "pwnoclip";
    public static final int NOCLIP_MIXIN_CLASSES = 9;
    private static final String STAGE = "cov_guard_stage";
    private static final String SINCE = "cov_guard_since";
    private static final String BEFORE = "cov_guard_before";
    private static final String FUTURE = "cov_guard_future";
    private static @Nullable CovLogCapture capture;

    private CovGuardSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Details-Fenster: „Regeln für Add-ons“ fragt nach dem Link " + NamespaceReportScreen.RULES_URL
                + ", Abbrechen führt zurück, „Schließen“ schließt", 300, CovGuardSelfTests::rulesStep);
        SelfTest.clientStep(GROUP + "Absturzbericht: Add-ons mit Version, Mixins und Ergebnis der Namensraum-Prüfung, Datei unter crash-reports/", 40,
                CovGuardSelfTests::crashStep);
        SelfTest.clientStep(GROUP + "Datenpaket ändert und ergänzt data/propwash/: /reload ohne Fund und Hinweis, nur eine Info-Zeile", 1600,
                CovGuardSelfTests::datapackStep);
        SelfTest.afterLeaveStep(GROUP + "Datenpaket bleibt beim erneuten Betreten ohne Fund und Hinweis", 2400, CovGuardSelfTests::rejoinStep);
    }

    private static int stage(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(STAGE, 0);
    }

    private static StepResult next(SelfTest.StepContext ctx, int stage) {
        ctx.state().put(STAGE, stage);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static @Nullable Button labelled(List<? extends GuiEventListener> children, Component label) {
        String text = label.getString();
        for (GuiEventListener child : children) {
            if (child instanceof Button button && button.visible && text.equals(button.getMessage().getString())) {
                return button;
            }
        }
        return null;
    }

    private static @Nullable FlatButton rulesButton(NamespaceReportScreen screen) {
        String rules = Component.translatableWithFallback(NamespaceReportScreen.KEY + "rules", "Add-on rules").getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof FlatButton button && rules.equals(button.getMessage().getString())) {
                return button;
            }
        }
        return null;
    }

    private static @Nullable URI linkOf(ConfirmLinkScreen screen) {
        try {
            Field field = ConfirmLinkScreen.class.getDeclaredField("url");
            field.setAccessible(true);
            return (URI) field.get(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static StepResult rulesStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        switch (stage(ctx)) {
            case 0 -> {
                mc.gui.setScreen(null);
                if (!GuardClient.handleClick(new ClickEvent.Custom(GuardClient.DETAILS_ID, Optional.empty()), mc, null)) {
                    return StepResult.fail("Knopf Details öffnet nichts");
                }
                return next(ctx, 1);
            }
            case 1 -> {
                if (!(mc.gui.screen() instanceof NamespaceReportScreen screen)) {
                    return since(ctx) > 40 ? StepResult.fail("Details-Fenster öffnet nicht: " + mc.gui.screen()) : StepResult.pending();
                }
                if (since(ctx) < 3) {
                    return StepResult.pending();
                }
                FlatButton rules = rulesButton(screen);
                if (rules == null || !rules.active || rules.getX() < 0 || rules.getX() + rules.getWidth() > screen.width
                        || rules.getY() + rules.getHeight() > screen.height) {
                    return StepResult.fail("Knopf „Regeln für Add-ons“ fehlt oder liegt außerhalb");
                }
                ctx.state().put("report", screen);
                rules.press();
                return next(ctx, 2);
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof ConfirmLinkScreen confirm)) {
                    return since(ctx) > 40 ? StepResult.fail("keine Link-Abfrage nach „Regeln für Add-ons“: " + mc.gui.screen()) : StepResult.pending();
                }
                URI link = linkOf(confirm);
                if (link == null || !NamespaceReportScreen.RULES_URL.equals(link.toString())) {
                    return StepResult.fail("Link-Abfrage für „" + link + "“ statt " + NamespaceReportScreen.RULES_URL);
                }
                if (!"https://propwashfpv.com/docs/api/#rules".equals(NamespaceReportScreen.RULES_URL)) {
                    return StepResult.fail("RULES_URL " + NamespaceReportScreen.RULES_URL);
                }
                if (since(ctx) < 3) {
                    return StepResult.pending();
                }
                Button cancel = labelled(confirm.children(), CommonComponents.GUI_NO);
                if (cancel == null) {
                    cancel = labelled(confirm.children(), CommonComponents.GUI_CANCEL);
                }
                if (cancel == null) {
                    return StepResult.fail("Abbrechen fehlt in der Link-Abfrage");
                }
                ctx.log("Link-Abfrage für " + link + ", Abbrechen mit „" + cancel.getMessage().getString() + "“");
                cancel.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                return next(ctx, 3);
            }
            case 3 -> {
                if (!(mc.gui.screen() instanceof NamespaceReportScreen screen)) {
                    return since(ctx) > 40 ? StepResult.fail("Abbrechen führt nicht zum Details-Fenster zurück: " + mc.gui.screen()) : StepResult.pending();
                }
                if (screen != ctx.state().get("report")) {
                    ctx.log("Abbrechen öffnet ein neues Details-Fenster (andere Instanz)");
                }
                FlatButton close = screen.closeButton();
                if (close == null) {
                    return StepResult.fail("Knopf „Schließen“ fehlt");
                }
                close.press();
                return next(ctx, 4);
            }
            default -> {
                if (mc.gui.screen() != null) {
                    return since(ctx) > 20 ? StepResult.fail("„Schließen“ lässt " + mc.gui.screen() + " offen") : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult crashStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        CrashReport report = CrashReport.forThrowable(new IllegalStateException("Propwash Abdeckung cov-api: Absturzbericht"), "Propwash coverage crash report");
        Path dir = mc.gameDirectory.toPath().resolve("crash-reports");
        Path file = dir.resolve("crash-cov-api-" + System.currentTimeMillis() + "-client.txt");
        if (!report.saveToFile(file, ReportType.CRASH)) {
            return StepResult.fail("Absturzbericht nicht gespeichert: " + file);
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            return StepResult.fail("Absturzbericht nicht lesbar: " + e);
        }
        List<String> lines = CovGuardServerChecks.section(text);
        ctx.log("Datei " + dir.getFileName() + "/" + file.getFileName() + " (über " + (GuardCrash.viaCallable() ? "Crash-Callable" : "Systembericht")
                + "): " + String.join(" | ", lines));
        Map<String, Set<String>> mixins = MixinScan.foreignTargets();
        List<ModMeta> addons = Services.PLATFORM.dependentMods(Constants.MOD_ID);
        String problem = CovGuardServerChecks.sectionProblem(lines, NamespaceGuard.report(), mixins, addons);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        boolean noclip = Services.PLATFORM.isModLoaded(NOCLIP);
        boolean probe = GuardSelfTests.probe();
        if (!noclip && !probe && addons.stream().noneMatch(addon -> addon.id().equals("justmoreparts"))) {
            if (!lines.contains("Mixins into Propwash internals: none") || !lines.contains("Namespace check: no findings")) {
                return StepResult.fail("sauberer Lauf ohne „none“ und „no findings“: " + lines);
            }
        }
        if (noclip) {
            Set<String> classes = mixins.get(NOCLIP);
            if (classes == null || classes.size() != NOCLIP_MIXIN_CLASSES || !lines.contains(NOCLIP + ": " + NOCLIP_MIXIN_CLASSES + " classes")) {
                return StepResult.fail("Noclip mit " + (classes == null ? 0 : classes.size()) + " Mixin-Klassen statt " + NOCLIP_MIXIN_CLASSES + ": " + lines);
            }
            if (lines.stream().noneMatch(line -> line.startsWith(NOCLIP + ": ") && line.endsWith(" findings"))) {
                return StepResult.fail("Noclip ohne Funde im Absturzbericht: " + lines);
            }
            ctx.log("Noclip: Mixins in " + classes.size() + " Klassen " + classes + ", Funde " + NamespaceGuard.report().categories(NOCLIP));
        }
        return StepResult.pass();
    }

    private static List<String> infoLines() {
        CovLogCapture log = capture;
        if (log == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : log.lines()) {
            if (line.startsWith("Namespace check: pack '" + CovGuardDatapack.packId() + "'")) {
                out.add(line);
            }
        }
        return out;
    }

    private static void stopCapture() {
        CovLogCapture log = capture;
        capture = null;
        if (log != null) {
            log.close();
        }
    }

    private static StepResult datapackStep(SelfTest.StepContext ctx) {
        switch (stage(ctx)) {
            case 0 -> {
                stopCapture();
                capture = CovLogCapture.propwash();
                ctx.state().put(BEFORE, new int[]{NamespaceGuard.runs(), GuardClient.hints(), NamespaceGuard.report().count()});
                ctx.state().put(FUTURE, ctx.onServer(CovGuardDatapack::install));
                return next(ctx, 1);
            }
            case 1, 3 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
                int[] before = (int[]) ctx.state().get(BEFORE);
                if (future.isCompletedExceptionally()) {
                    stopCapture();
                    return StepResult.fail("Datenpaket: " + future.exceptionNow().getMessage());
                }
                if (!future.isDone() || NamespaceGuard.runs() <= before[0] || since(ctx) < 20) {
                    return since(ctx) > 1200 ? StepResult.fail("keine Namensraum-Prüfung nach dem Laden des Datenpakets") : StepResult.pending();
                }
                List<String> info = infoLines();
                ctx.log((stage(ctx) == 1 ? "Datenpaket aktiviert" : "/reload") + ": Läufe " + before[0] + " -> " + NamespaceGuard.runs() + ", Funde "
                        + before[2] + " -> " + NamespaceGuard.report().count() + ", Hinweise " + before[1] + " -> " + GuardClient.hints()
                        + ", Info-Zeilen " + info);
                List<String> problems = new ArrayList<>();
                if (NamespaceGuard.report().count() != before[2]) {
                    problems.add("Funde ändern sich durch das Datenpaket");
                }
                if (!GuardSelfTests.probe() && !Services.PLATFORM.isModLoaded(NOCLIP) && NamespaceGuard.report().count() != 0) {
                    problems.add(NamespaceGuard.report().count() + " Funde statt 0");
                }
                if (GuardClient.hints() != before[1]) {
                    problems.add("Hinweis im Chat durch das Datenpaket");
                }
                if (info.size() != 1 || !info.getFirst().contains("adds 1 new files") || !info.getFirst().contains(CovGuardDatapack.NEW_FILE)) {
                    problems.add("Info-Zeilen " + info + " statt genau einer zu " + CovGuardDatapack.NEW_FILE);
                }
                if (!problems.isEmpty()) {
                    stopCapture();
                    ctx.onServer(CovGuardDatapack::remove);
                    return StepResult.fail(String.join("; ", problems));
                }
                if (stage(ctx) == 3) {
                    return StepResult.pass();
                }
                ctx.state().put(BEFORE, new int[]{NamespaceGuard.runs(), GuardClient.hints(), NamespaceGuard.report().count()});
                CompletableFuture<Void> reload = new CompletableFuture<>();
                ctx.state().put(FUTURE, reload);
                ctx.onServer(server -> server.reloadResources(server.getPackRepository().getSelectedIds())
                        .whenComplete((done, error) -> reload.complete(null)));
                return next(ctx, 3);
            }
            default -> {
                return StepResult.fail("Stufe " + stage(ctx));
            }
        }
    }

    private static @Nullable String worldFolder(Minecraft mc) {
        Path saves = mc.getLevelSource().getBaseDir();
        try (Stream<Path> worlds = Files.list(saves)) {
            return worlds.map(path -> path.getFileName().toString()).filter(name -> name.startsWith(SelfTestRunner.WORLD_NAME)).sorted().findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static StepResult rejoinStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        switch (stage(ctx)) {
            case 0 -> {
                if (capture == null) {
                    ctx.log("Datenpaket-Schritt lief nicht – nichts zu prüfen");
                    return StepResult.pass();
                }
                if (!(mc.gui.screen() instanceof TitleScreen) && since(ctx) < 200) {
                    return StepResult.pending();
                }
                String folder = worldFolder(mc);
                if (folder == null) {
                    stopCapture();
                    return StepResult.fail("Welt des Selbsttests nicht gefunden");
                }
                ctx.state().put(BEFORE, new int[]{GuardClient.hints(), GuardClient.logins(), NamespaceGuard.report().count(), infoLines().size()});
                mc.createWorldOpenFlows().openWorld(folder, () -> mc.gui.setScreen(new TitleScreen()));
                return next(ctx, 1);
            }
            case 1 -> {
                if (mc.gui.screen() instanceof ConfirmScreen confirm && ctx.tick() % 20 == 0) {
                    Button yes = labelled(confirm.children(), CommonComponents.GUI_YES);
                    if (yes != null) {
                        yes.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                    }
                }
                if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.getSingleplayerServer() == null) {
                    return since(ctx) > 1800 ? StepResult.fail("erneutes Betreten gelingt nicht: " + mc.gui.screen()) : StepResult.pending();
                }
                return next(ctx, 2);
            }
            case 2 -> {
                if (since(ctx) < 60) {
                    return StepResult.pending();
                }
                int[] before = (int[]) ctx.state().get(BEFORE);
                boolean active = mc.getSingleplayerServer().getPackRepository().getSelectedIds().contains(CovGuardDatapack.packId());
                ctx.log("erneut betreten: Datenpaket aktiv " + active + ", Beitritte " + before[1] + " -> " + GuardClient.logins() + ", Hinweise " + before[0]
                        + " -> " + GuardClient.hints() + ", Funde " + before[2] + " -> " + NamespaceGuard.report().count() + ", Info-Zeilen " + infoLines());
                List<String> problems = new ArrayList<>();
                if (!active) {
                    problems.add("Datenpaket nach dem erneuten Betreten nicht aktiv");
                }
                if (GuardClient.logins() != before[1] + 1) {
                    problems.add("Beitritte " + GuardClient.logins() + " statt " + (before[1] + 1));
                }
                if (GuardClient.hints() != before[0]) {
                    problems.add("Hinweis beim erneuten Betreten");
                }
                if (NamespaceGuard.report().count() != before[2]) {
                    problems.add("Funde ändern sich beim erneuten Betreten");
                }
                if (infoLines().size() != 1) {
                    problems.add("Info-Zeilen " + infoLines() + " statt genau einer");
                }
                ctx.state().put("problems", problems);
                ctx.state().put(FUTURE, ctx.onServer(CovGuardDatapack::remove));
                return next(ctx, 3);
            }
            case 3 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                mc.disconnectFromWorld(Component.literal("Propwash-Selbsttest Abdeckung Namensraum beendet"));
                return next(ctx, 4);
            }
            default -> {
                if (mc.level != null || mc.player != null || mc.getSingleplayerServer() != null) {
                    return StepResult.pending();
                }
                stopCapture();
                @SuppressWarnings("unchecked")
                List<String> problems = (List<String>) ctx.state().get("problems");
                return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
            }
        }
    }
}

package de.lardtom.propwash.guard.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.SelfTestRunner;
import de.lardtom.propwash.guard.GuardCategory;
import de.lardtom.propwash.guard.GuardReport;
import de.lardtom.propwash.guard.NamespaceGuard;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class GuardSelfTests {
    public static final String PROBE = "nsprobe";
    public static final String PROBE_NAME = "Namespace Probe";
    private static final String GROUP = "guard: ";
    private static final String JMP = "justmoreparts";
    private static final String GERMAN = "de_de";
    private static final String RELOAD = "reload";
    private static final String BEFORE = "before";
    private static final String STAGE = "stage";
    private static final String SINCE = "since";
    private static final int WINDOW_TICKS = 80;
    private static final int[][] REPORT_SIZES = {{854, 480, 2}, {320, 240, 3}};
    private static final Map<GuardCategory, List<String>> EXPECTED = expected();
    private static int @Nullable [] savedWindow;

    private GuardSelfTests() {
    }

    private static Map<GuardCategory, List<String>> expected() {
        Map<GuardCategory, List<String>> out = new LinkedHashMap<>();
        out.put(GuardCategory.REGISTRY, List.of("propwash:nsprobe_item"));
        out.put(GuardCategory.TRANSLATION, List.of("key.propwash.nsprobe", "propwash.nsprobe.text"));
        out.put(GuardCategory.KEY_MAPPING, List.of("key.propwash.nsprobe"));
        out.put(GuardCategory.NETWORK, List.of("propwash:nsprobe_ping"));
        out.put(GuardCategory.ENUM, List.of("input.SwitchFunction.NSPROBE", "osd.OsdElement.NSPROBE"));
        out.put(GuardCategory.DATA, List.of("data/propwash/recipe/nsprobe.json"));
        out.put(GuardCategory.ASSET, List.of("assets/propwash/textures/item/nsprobe.png"));
        out.put(GuardCategory.COMMAND, List.of("/propwash nsprobe"));
        out.put(GuardCategory.REFUSED, List.of("propwash:nsprobe (config tab)", "propwash:probe (hud element)"));
        return out;
    }

    public static boolean probe() {
        return Services.PLATFORM.isModLoaded(PROBE);
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Namensraum-Prüfung beim Start (ohne Probe 0 Funde, mit Probe genau die erwarteten Ids)", 400,
                GuardSelfTests::findingsStep);
        SelfTest.clientStep(GROUP + "Hinweise im Chat genau einmal beim ersten Betreten", 200,
                GuardSelfTests::hintStep);
        SelfTest.clientStep(GROUP + "Neuladen der Ressourcen (F3+T) ohne zweiten Hinweis", 1200, GuardSelfTests::reloadStep);
        SelfTest.clientStep(GROUP + "Details-Fenster über den Knopf im Chat, Kopieren und Schließen sichtbar", 200, GuardSelfTests::detailsStep);
        SelfTest.clientStep(GROUP + "Absturzbericht mit Abschnitt Propwash add-ons", 20, GuardSelfTests::crashStep);
        SelfTest.clientStep(GROUP + "/reload prüft Befehle und Datenpakete erneut ohne neuen Hinweis", 1200, GuardSelfTests::serverReloadStep);
        if (Screenshots.enabled()) {
            registerScreenshots();
        }
        SelfTest.afterLeaveStep(GROUP + "zweiter Weltbeitritt ohne zweiten Hinweis", 2400, GuardSelfTests::rejoinStep);
    }

    private static void registerScreenshots() {
        Screenshots.step(GROUP + "Bildschirmfoto Hinweis im Chat", "guard_hint_en", 10, new HintScene());
        for (int[] size : REPORT_SIZES) {
            Screenshots.step(GROUP + "Bildschirmfoto Details-Fenster " + size[0] + "x" + size[1], "guard_report_en_" + size[0] + "x" + size[1], 6,
                    new ReportScene(size[0], size[1], size[2]));
        }
        String back = GROUP + "Bildschirmfotos Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "Bildschirmfotos Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        Screenshots.step(GROUP + "Bildschirmfoto Hinweis im Chat deutsch", "guard_hint_de", 10, new HintScene());
        for (int[] size : REPORT_SIZES) {
            Screenshots.step(GROUP + "Bildschirmfoto Details-Fenster deutsch " + size[0] + "x" + size[1], "guard_report_de_" + size[0] + "x" + size[1],
                    6, new ReportScene(size[0], size[1], size[2]));
        }
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
    }

    private static StepResult findingsStep(SelfTest.StepContext ctx) {
        if (NamespaceGuard.runs() < 2 || GuardClient.reloads() < 1) {
            return ctx.tick() >= 399 ? StepResult.fail("Prüfung lief nicht: " + NamespaceGuard.runs() + " Läufe, " + GuardClient.reloads()
                    + " Ressourcen-Läufe") : StepResult.pending();
        }
        ConfigTabs.create();
        Minecraft mc = Minecraft.getInstance();
        long start = System.nanoTime();
        GuardClient.reload(mc.getResourceManager());
        double millis = (System.nanoTime() - start) / 1_000_000.0;
        GuardReport report = NamespaceGuard.report();
        ctx.log(String.format(Locale.ROOT, "Prüfung der Client-Ressourcen in %.1f ms, %d Läufe, %d Funde, Just More Parts %s", millis,
                NamespaceGuard.runs(), report.count(), Services.PLATFORM.modVersion(JMP).orElse("nicht geladen")));
        for (Map.Entry<String, List<String>> entry : report.mixins().entrySet()) {
            ctx.log("Mixins in Interna (Info): " + entry.getKey() + " mit " + entry.getValue().size() + " Klassen");
        }
        if (!probe()) {
            return report.count() == 0 ? StepResult.pass() : StepResult.fail("Funde ohne Probe-Mod: " + describe(report));
        }
        List<String> problems = new ArrayList<>();
        if (!report.mods().equals(List.of(PROBE))) {
            problems.add("Mods mit Funden " + report.mods() + " statt [" + PROBE + "]");
        }
        SortedMap<GuardCategory, List<String>> actual = new TreeMap<>(report.categories(PROBE));
        for (GuardCategory category : GuardCategory.values()) {
            List<String> want = EXPECTED.getOrDefault(category, List.of());
            List<String> got = actual.getOrDefault(category, List.of());
            if (!want.equals(got)) {
                problems.add(category.logName() + ": " + got + " statt " + want);
            }
        }
        List<String> mixins = report.mixins().getOrDefault(PROBE, List.of());
        if (mixins.size() != 2) {
            problems.add("Mixins der Probe in Interna: " + mixins);
        }
        ctx.log("Funde der Probe: " + describe(report));
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static String describe(GuardReport report) {
        List<String> parts = new ArrayList<>();
        for (String mod : report.mods()) {
            parts.add(mod + " " + report.categories(mod));
        }
        return String.join(", ", parts);
    }

    private static StepResult hintStep(SelfTest.StepContext ctx) {
        if (GuardClient.logins() < 1 && ctx.tick() < 199) {
            return StepResult.pending();
        }
        int expectedHints = NamespaceGuard.report().count() > 0 ? 1 : 0;
        List<String> problems = new ArrayList<>();
        if (GuardClient.logins() != 1) {
            problems.add("Weltbeitritte " + GuardClient.logins() + " statt 1");
        }
        if (GuardClient.hints() != expectedHints) {
            problems.add("Namensraum-Hinweise " + GuardClient.hints() + " statt " + expectedHints);
        }
        int share = shareButtons();
        if (share > 1) {
            problems.add(share + " Sharecode-Knöpfe in der Werkbank");
        }
        ctx.log("Namensraum-Hinweise " + GuardClient.hints() + ", Sharecode-Knöpfe " + share);
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    static int shareButtons() {
        int count = 0;
        for (WorkbenchExtension extension : PropwashClientApi.workbenchExtensions()) {
            if (extension.id().getPath().contains("share") || extension.getClass().getSimpleName().contains("Share")) {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static StepResult reloadStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey(RELOAD)) {
            ctx.state().put(BEFORE, new int[]{GuardClient.reloads(), GuardClient.hints(), NamespaceGuard.report().count()});
            ctx.state().put(RELOAD, mc.reloadResourcePacks());
            return StepResult.pending();
        }
        CompletableFuture<Void> reload = (CompletableFuture<Void>) ctx.state().get(RELOAD);
        if (!reload.isDone() || mc.gui.overlay() != null || ctx.tick() < 5) {
            return StepResult.pending();
        }
        if (reload.isCompletedExceptionally()) {
            return StepResult.fail("Neuladen: " + reload.exceptionNow());
        }
        int[] before = (int[]) ctx.state().get(BEFORE);
        ctx.log("Ressourcen-Läufe " + before[0] + " -> " + GuardClient.reloads() + ", Funde " + before[2] + " -> " + NamespaceGuard.report().count());
        if (GuardClient.reloads() <= before[0]) {
            return StepResult.fail("Prüfung lief beim Neuladen nicht");
        }
        if (GuardClient.hints() != before[1]) {
            return StepResult.fail("zweiter Hinweis nach dem Neuladen: " + GuardClient.hints());
        }
        return StepResult.check(NamespaceGuard.report().count() == before[2], "Funde ändern sich beim Neuladen");
    }

    private static StepResult detailsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (NamespaceGuard.report().count() > 0) {
                Component hint = GuardClient.hint();
                Optional<ClickEvent> click = hint == null ? Optional.empty() : clickOf(hint);
                if (click.isEmpty() || !(click.get() instanceof ClickEvent.Custom custom) || !custom.id().equals(GuardClient.DETAILS_ID)) {
                    return StepResult.fail("Hinweis ohne Knopf Details: " + (hint == null ? "kein Hinweis" : hint.getString()));
                }
            }
            if (!GuardClient.handleClick(new ClickEvent.Custom(GuardClient.DETAILS_ID, Optional.empty()), mc, null)) {
                return StepResult.fail("Knopf Details öffnet nichts");
            }
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof NamespaceReportScreen screen)) {
            return StepResult.fail("Details-Fenster nicht offen: " + mc.gui.screen());
        }
        if (ctx.tick() < 3) {
            return StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        for (FlatButton button : new FlatButton[]{screen.copyButton(), screen.closeButton()}) {
            if (button == null || button.getY() < 0 || button.getY() + button.getHeight() > screen.height || button.getX() < 0
                    || button.getX() + button.getWidth() > screen.width) {
                problems.add("Knopf außerhalb des Fensters: " + (button == null ? "fehlt" : button.getMessage().getString()));
            }
        }
        String text = screen.detailsText();
        for (String mod : NamespaceGuard.report().mods()) {
            if (!text.contains(mod)) {
                problems.add("Text zum Kopieren ohne " + mod);
            }
        }
        screen.onClose();
        if (mc.gui.screen() != null) {
            problems.add("Schließen lässt ein Fenster offen: " + mc.gui.screen());
        }
        ctx.log("Details-Fenster " + screen.width + "x" + screen.height + ", Scrollweite " + Math.round(screen.maxScroll()));
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static Optional<ClickEvent> clickOf(Component component) {
        AtomicReference<ClickEvent> found = new AtomicReference<>();
        component.visit((Style style, String text) -> {
            if (style.getClickEvent() != null && found.get() == null) {
                found.set(style.getClickEvent());
            }
            return Optional.empty();
        }, Style.EMPTY);
        return Optional.ofNullable(found.get());
    }

    private static StepResult crashStep(SelfTest.StepContext ctx) {
        String text = new CrashReport("Propwash guard selftest", new IllegalStateException("guard selftest")).getFriendlyReport(ReportType.CRASH);
        int start = text.indexOf("Propwash add-ons");
        if (start >= 0) {
            ctx.log(text.substring(start).lines().takeWhile(line -> line.startsWith("Propwash add-ons") || line.startsWith("\t\t"))
                    .map(String::trim).reduce((a, b) -> a + " | " + b).orElse(""));
        }
        if (!text.contains("Propwash add-ons")) {
            return StepResult.fail("Abschnitt Propwash add-ons fehlt im Systembericht");
        }
        return StepResult.check(Services.PLATFORM.modVersion(JMP).isEmpty() || text.contains(JMP), "Just More Parts fehlt im Abschnitt");
    }

    @SuppressWarnings("unchecked")
    private static StepResult serverReloadStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(RELOAD)) {
            int[] before = {NamespaceGuard.runs(), GuardClient.hints(), NamespaceGuard.report().count()};
            ctx.state().put(BEFORE, before);
            CompletableFuture<Void> started = new CompletableFuture<>();
            ctx.state().put(RELOAD, started);
            ctx.onServer(server -> server.reloadResources(server.getPackRepository().getSelectedIds())
                    .whenComplete((done, error) -> started.complete(null)));
            return StepResult.pending();
        }
        int[] before = (int[]) ctx.state().get(BEFORE);
        CompletableFuture<Void> reload = (CompletableFuture<Void>) ctx.state().get(RELOAD);
        if (!reload.isDone() || NamespaceGuard.runs() <= before[0]) {
            return ctx.tick() >= 1199 ? StepResult.fail("keine Prüfung nach /reload (" + NamespaceGuard.runs() + " Läufe)") : StepResult.pending();
        }
        ctx.log("Läufe " + before[0] + " -> " + NamespaceGuard.runs() + ", Funde " + before[2] + " -> " + NamespaceGuard.report().count());
        if (GuardClient.hints() != before[1]) {
            return StepResult.fail("neuer Hinweis nach /reload");
        }
        return StepResult.check(NamespaceGuard.report().count() == before[2], "Funde ändern sich durch /reload");
    }

    private static StepResult rejoinStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int stage = (int) ctx.state().getOrDefault(STAGE, 0);
        int[] before = (int[]) ctx.state().computeIfAbsent(BEFORE, key -> new int[]{GuardClient.hints(), GuardClient.logins()});
        switch (stage) {
            case 0 -> {
                if (!(mc.gui.screen() instanceof TitleScreen) && ctx.tick() < 200) {
                    return StepResult.pending();
                }
                String folder = worldFolder(mc);
                if (folder == null) {
                    return StepResult.fail("Welt des Selbsttests nicht gefunden");
                }
                mc.createWorldOpenFlows().openWorld(folder, () -> mc.gui.setScreen(new TitleScreen()));
                ctx.state().put(STAGE, 1);
                ctx.state().put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                if (mc.gui.screen() instanceof ConfirmScreen confirm && ctx.tick() % 20 == 0) {
                    for (GuiEventListener child : confirm.children()) {
                        if (child instanceof Button button && button.getMessage().getString().equals(CommonComponents.GUI_YES.getString())) {
                            button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                            break;
                        }
                    }
                }
                if (mc.player == null || mc.level == null || mc.gui.screen() != null) {
                    return ctx.tick() - (int) ctx.state().get(SINCE) > 1800 ? StepResult.fail("zweiter Weltbeitritt gelingt nicht: " + mc.gui.screen())
                            : StepResult.pending();
                }
                ctx.state().put(STAGE, 2);
                ctx.state().put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                if (ctx.tick() - (int) ctx.state().get(SINCE) < 60) {
                    return StepResult.pending();
                }
                ctx.log("Weltbeitritte " + before[1] + " -> " + GuardClient.logins() + ", Hinweise " + before[0] + " -> " + GuardClient.hints());
                ctx.state().put("result", GuardClient.logins() == before[1] + 1 && GuardClient.hints() == before[0]);
                mc.disconnectFromWorld(Component.literal("Propwash-Selbsttest Namensraum beendet"));
                ctx.state().put(STAGE, 3);
                return StepResult.pending();
            }
            default -> {
                if (mc.level != null || mc.player != null || mc.getSingleplayerServer() != null) {
                    return StepResult.pending();
                }
                return StepResult.check((boolean) ctx.state().get("result"), "zweiter Hinweis beim zweiten Weltbeitritt oder kein zweiter Beitritt: "
                        + GuardClient.logins() + " Beitritte, " + GuardClient.hints() + " Hinweise");
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

    public static GuardReport sampleReport() {
        GuardReport report = new GuardReport();
        for (Map.Entry<GuardCategory, List<String>> entry : EXPECTED.entrySet()) {
            for (String id : entry.getValue()) {
                report.add(PROBE, entry.getKey(), id);
            }
        }
        report.mixins(PROBE, List.of("de.lardtom.propwash.input.SwitchFunction", "de.lardtom.propwash.osd.OsdElement"));
        report.name(PROBE, PROBE_NAME);
        return report;
    }

    private static GuardReport shownReport() {
        return NamespaceGuard.report().count() > 0 ? NamespaceGuard.report() : sampleReport();
    }

    private static final class HintScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            Screenshots.parkMouse(mc);
            Screenshots.hideMarker(true);
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Screenshots.hideMarker(false);
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey("hint")) {
                ctx.state().put("hint", true);
                Component hint = NamespaceGuard.report().count() > 0 ? GuardClient.hint()
                        : Component.translatable(GuardClient.HINT_KEY, PROBE_NAME).withStyle(ChatFormatting.YELLOW).append(" ")
                        .append(GuardClient.detailsButton());
                mc.gui.hud.getChat().addClientSystemMessage(hint);
            }
        }
    }

    private static final class ReportScene implements Screenshots.Scene {
        private final int width;
        private final int height;
        private final int scale;
        private int stage;
        private int since;

        ReportScene(int width, int height, int scale) {
            this.width = width;
            this.height = height;
            this.scale = scale;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            int frameWidth = this.width * this.scale;
            int frameHeight = this.height * this.scale;
            if (this.stage == 0) {
                if (savedWindow == null) {
                    savedWindow = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                }
                mc.gui.setScreen(null);
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(frameWidth / density), Math.round(frameHeight / density));
                this.stage = 1;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (this.stage == 1) {
                boolean sized = window.getWidth() == frameWidth && window.getHeight() == frameHeight;
                if (!sized && ctx.tick() - this.since < WINDOW_TICKS) {
                    return StepResult.pending();
                }
                mc.options.guiScale().set(this.scale);
                mc.resizeGui();
                ctx.log("Fenster " + window.getWidth() + "x" + window.getHeight() + ", GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight());
                if (window.getGuiScaledWidth() != this.width || window.getGuiScaledHeight() != this.height) {
                    return StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " statt " + this.width + "x" + this.height);
                }
                NamespaceReportScreen screen = new NamespaceReportScreen(null, shownReport());
                String header = PROBE_NAME + " (" + PROBE + ")";
                if (screen.report().mods().contains(PROBE) && !screen.detailsText().contains(header)) {
                    return StepResult.fail("Details-Fenster ohne Namen der Probe, erwartet „" + header + "“: "
                            + screen.detailsText().lines().filter(line -> line.contains(PROBE)).findFirst().orElse("-"));
                }
                mc.gui.setScreen(screen);
                Screenshots.parkMouse(mc);
                Screenshots.hideMarker(true);
                this.stage = 2;
                return StepResult.pass();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            Screenshots.hideMarker(false);
            int[] saved = savedWindow;
            if (saved != null) {
                mc.options.guiScale().set(saved[2]);
                mc.getWindow().setWindowed(saved[0], saved[1]);
                mc.resizeGui();
                savedWindow = null;
            }
            this.stage = 0;
        }
    }
}

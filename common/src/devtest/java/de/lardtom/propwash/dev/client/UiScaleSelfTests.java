package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import de.lardtom.propwash.zone.ZoneText;
import de.lardtom.propwash.zone.ZonesS2C;
import de.lardtom.propwash.zone.client.ClientZones;
import de.lardtom.propwash.zone.client.ZoneSection;
import de.lardtom.propwash.zone.client.ZoneSelfTests;
import de.lardtom.propwash.zone.client.ZonesTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UiScaleSelfTests {
    public static final String REPORT_FILE = "propwash-ui-scale.txt";
    private static final String GROUP = "ui-scale: ";
    private static final int STEP_TICKS = 24000;
    private static final int OPEN_TICKS = 300;
    private static final int CLOSE_TICKS = 40;
    private static final int SETTLE_TICKS = 3;
    private static final int FRAME_TICKS = 2;
    private static final int WINDOW_TICKS = 80;
    private static final int MAX_SHOTS = 80;
    private static final int TOOLTIP_TICKS = 4;
    private static final int TOOLTIP_MAX_WIDTH = 480;
    private static final int TOOLTIP_MAX_HEIGHT = 270;
    private static final String RUN = "run";
    private static int @Nullable [] savedWindow;
    private static final String GERMAN = "de_de";
    private static final String WINDOWS_PROPERTY = "propwash.selftest.uiScaleWindows";
    private static final String CASES_PROPERTY = "propwash.selftest.uiScaleCases";
    private static final int[][] BETWEEN_WINDOWS = {{768, 480}, {912, 512}};
    private static final Pattern WINDOW_PATTERN = Pattern.compile("(\\d+)x(\\d+)(?:@(\\d+))?");
    private static boolean reportStarted;
    private static final Map<String, String> AUDITED = new LinkedHashMap<>();

    private UiScaleSelfTests() {
    }

    public record Combo(int width, int height, int scale, @Nullable String language, boolean between) {
        String label() {
            return this.width + "x" + this.height + " mit GUI-Skala " + (this.scale == 0 ? "automatisch" : String.valueOf(this.scale))
                    + (this.language == null ? "" : " auf " + this.language);
        }

        String slug() {
            return this.width + "x" + this.height + "_" + (this.scale == 0 ? "auto" : "gui" + this.scale)
                    + (this.language == null ? "" : "_" + this.language.substring(0, 2));
        }
    }

    public interface Case {
        String name();

        StepResult open(SelfTest.StepContext ctx);

        default int pages(Screen screen) {
            return 1;
        }

        default String page(Screen screen, int index) {
            return "";
        }

        default int settleTicks() {
            return SETTLE_TICKS;
        }

        default void frame(Screen screen, String page) {
        }

        default List<Finding> audit(Minecraft mc, Screen screen) {
            return LayoutProbe.audit(mc, screen);
        }

        default boolean closed() {
            return true;
        }

        default boolean between() {
            return false;
        }

        void close(SelfTest.StepContext ctx);
    }

    public static void register() {
        SelfTest.setupStep(GROUP + "Fenster und GUI-Skala merken", 40, UiScaleSelfTests::remember);
        List<int[]> sizes = sizes(System.getProperty(WINDOWS_PROPERTY, ""));
        for (int[] size : sizes) {
            Combo combo = new Combo(size[0], size[1], size[2], null, size[3] != 0);
            SelfTest.clientStep(GROUP + "Fenster " + combo.label() + " – kein Text und kein Widget abgeschnitten oder überlappt", STEP_TICKS,
                    ctx -> run(ctx, combo));
        }
        String back = GROUP + "Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "Sprache Deutsch (längere Texte)", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        for (int[] size : sizes) {
            Combo combo = new Combo(size[0], size[1], size[2], GERMAN, size[3] != 0);
            SelfTest.clientStep(GROUP + "Deutsch: Fenster " + combo.label() + " – kein Text und kein Widget abgeschnitten oder überlappt",
                    STEP_TICKS, ctx -> run(ctx, combo));
        }
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        SelfTest.setupStep(GROUP + "Fenster und GUI-Skala zurückstellen", 40, UiScaleSelfTests::restore);
        String zoneBack = GROUP + "Zonenname ganz – Sprache zurückstellen";
        SelfTest.clientStep(GROUP + ZoneNames.TITLE, ZoneNames.STEP_TICKS, ctx -> ZoneNames.run(ctx, ZoneNames.ENGLISH));
        SelfTest.switchStep(GROUP + "Zonenname ganz – Sprache Deutsch", zoneBack, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        SelfTest.clientStep(GROUP + "Deutsch: " + ZoneNames.TITLE, ZoneNames.STEP_TICKS, ctx -> ZoneNames.run(ctx, GERMAN));
        SelfTest.clientStep(zoneBack, 2400, Screenshots::restoreLanguage);
    }

    static List<Case> selected(List<Case> cases, String custom) {
        List<String> prefixes = new ArrayList<>();
        for (String part : custom.split(",")) {
            if (!part.isBlank()) {
                prefixes.add(part.trim());
            }
        }
        if (prefixes.isEmpty()) {
            return cases;
        }
        List<Case> out = new ArrayList<>();
        for (Case candidate : cases) {
            if (prefixes.stream().anyMatch(candidate.name()::startsWith)) {
                out.add(candidate);
            }
        }
        return out;
    }

    static List<int[]> sizes(String custom) {
        List<int[]> out = new ArrayList<>();
        for (String part : custom.split(",")) {
            Matcher matcher = WINDOW_PATTERN.matcher(part.trim());
            if (matcher.matches()) {
                out.add(new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        matcher.group(3) == null ? 2 : Integer.parseInt(matcher.group(3)), 0});
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        int[][] windows = {{854, 480}, {1920, 1080}, {640, 480}, {800, 600}};
        int[] scales = {2, 3, 4, 0};
        for (int[] window : windows) {
            for (int scale : scales) {
                out.add(new int[]{window[0], window[1], scale, 0});
            }
        }
        for (int[] window : BETWEEN_WINDOWS) {
            out.add(new int[]{window[0], window[1], 2, 1});
        }
        return out;
    }

    private static StepResult remember(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (savedWindow == null) {
            savedWindow = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
        }
        return StepResult.pass();
    }

    private static StepResult restore(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int[] saved = savedWindow;
        if (saved != null) {
            mc.options.guiScale().set(saved[2]);
            mc.getWindow().setWindowed(saved[0], saved[1]);
            mc.resizeGui();
            savedWindow = null;
        }
        return StepResult.pass();
    }

    private static StepResult run(SelfTest.StepContext ctx, Combo combo) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run(combo));
        try {
            StepResult result = run.advance(ctx, Minecraft.getInstance());
            if (result.status() != StepResult.Status.PENDING) {
                run.cleanup(ctx);
            }
            return result;
        } catch (RuntimeException e) {
            run.cleanup(ctx);
            throw e;
        }
    }

    private static final class Run {
        private final Combo combo;
        private final List<Case> cases;
        private final List<String> lines = new ArrayList<>();
        private final Map<LayoutAudit.Kind, Integer> kinds = new EnumMap<>(LayoutAudit.Kind.class);
        private int stage;
        private int since;
        private int index;
        private int page;
        private int pages;
        private int audited;
        private int failedCases;
        private int shots;
        private boolean pageShown;
        private long tooltipNanos;
        private int tooltipViews;
        private int tooltipsSeen;
        private boolean tipWanted;
        private String tipName = "";
        private @Nullable Case current;
        private @Nullable SubContext sub;
        private @Nullable CompletableFuture<String> shot;
        private String shotName = "";
        private String effective = "";

        private Run(Combo combo) {
            this.combo = combo;
            List<Case> chosen = selected(UiScaleCases.all(), System.getProperty(CASES_PROPERTY, ""));
            this.cases = combo.between() ? chosen.stream().filter(Case::between).toList() : chosen;
        }

        private StepResult advance(SelfTest.StepContext ctx, Minecraft mc) {
            Window window = mc.getWindow();
            if (this.stage == 0) {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(this.combo.width() / density), Math.round(this.combo.height() / density));
                this.stage = 1;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (this.stage == 1) {
                boolean sized = window.getWidth() == this.combo.width() && window.getHeight() == this.combo.height();
                if (!sized && ctx.tick() - this.since < WINDOW_TICKS) {
                    return StepResult.pending();
                }
                if (!sized) {
                    return StepResult.fail("Fenster wird nicht " + this.combo.width() + "x" + this.combo.height() + ": " + window.getWidth() + "x"
                            + window.getHeight());
                }
                mc.options.guiScale().set(this.combo.scale());
                mc.resizeGui();
                this.effective = window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " bei Skala " + window.getGuiScale()
                        + (this.combo.language() == null ? "" : " auf " + this.combo.language());
                if (this.combo.language() != null && !this.combo.language().equals(mc.getLanguageManager().getSelected())) {
                    return StepResult.fail("Sprache ist " + mc.getLanguageManager().getSelected() + " statt " + this.combo.language());
                }
                String earlier = AUDITED.get(this.effective);
                if (earlier != null) {
                    ctx.log("GUI " + this.effective + " ist dieselbe wie bei Fenster " + earlier + " (Minecraft begrenzt die Skala auf mindestens "
                            + "320x240 GUI-Punkte) – dort geprüft");
                    return StepResult.pass();
                }
                this.stage = 2;
                return StepResult.pending();
            }
            if (this.stage == 2) {
                if (this.index >= this.cases.size()) {
                    return this.finish(ctx);
                }
                this.current = this.cases.get(this.index);
                this.sub = new SubContext(ctx);
                this.stage = 3;
            }
            Case active = this.current;
            SubContext sub = this.sub;
            if (active == null || sub == null) {
                return StepResult.fail("kein Fall");
            }
            sub.parent = ctx;
            if (this.stage == 3) {
                StepResult opened;
                try {
                    opened = active.open(sub);
                } catch (RuntimeException e) {
                    opened = StepResult.fail(String.valueOf(e));
                }
                if (opened.status() == StepResult.Status.PENDING && sub.tick() < OPEN_TICKS) {
                    return StepResult.pending();
                }
                if (opened.status() != StepResult.Status.PASS || mc.gui.screen() == null) {
                    this.record(active.name(), List.of(new Finding(LayoutAudit.Kind.HIDDEN, active.name(), "öffnet nicht: " + opened.message())));
                    return this.closeCase(ctx);
                }
                this.pages = Math.max(1, active.pages(mc.gui.screen()));
                this.page = 0;
                this.pageShown = false;
                this.stage = 4;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            Screen screen = mc.gui.screen();
            if (this.stage == 4) {
                if (screen == null) {
                    this.record(active.name(), List.of(new Finding(LayoutAudit.Kind.HIDDEN, active.name(), "Screen vor der Prüfung geschlossen")));
                    return this.closeCase(ctx);
                }
                if (!this.pageShown) {
                    this.pageShown = true;
                    Screenshots.parkMouse(mc);
                    this.since = ctx.tick();
                    sub.state().put("page", active.page(screen, this.page));
                    return StepResult.pending();
                }
                if (ctx.tick() - this.since < active.settleTicks()) {
                    return StepResult.pending();
                }
                String label = String.valueOf(sub.state().getOrDefault("page", ""));
                String name = label.isEmpty() ? active.name() : active.name() + " / " + label;
                List<Finding> findings;
                try {
                    List<Finding> hover = new ArrayList<>();
                    Screen shown = mc.gui.screen();
                    if (shown != null) {
                        hover.addAll(LayoutProbe.scrollbars(mc, shown));
                    }
                    shown = mc.gui.screen();
                    this.tipWanted = false;
                    if (shown != null && tooltipsMatter(shown)) {
                        long started = System.nanoTime();
                        LayoutProbe.TooltipSweep sweep = LayoutProbe.tooltips(mc, shown);
                        hover.addAll(sweep.findings());
                        this.tooltipNanos += System.nanoTime() - started;
                        this.tooltipViews++;
                        this.tooltipsSeen += sweep.seen();
                        this.tipWanted = UiScaleCases.tooltipShowcase(this.combo, name) && Screenshots.enabled();
                        this.tipName = "ui-scale/" + this.combo.slug() + "_" + slug(name) + "_tooltip";
                    }
                    findings = new ArrayList<>(active.audit(mc, mc.gui.screen() == null ? screen : mc.gui.screen()));
                    findings.addAll(hover);
                } catch (RuntimeException e) {
                    findings = List.of(new Finding(LayoutAudit.Kind.HIDDEN, name, "Ausnahme beim Prüfen: " + e));
                }
                this.audited++;
                this.record(name, findings);
                if ((!findings.isEmpty() || UiScaleCases.showcase(this.combo, name)) && Screenshots.enabled() && this.shots < MAX_SHOTS) {
                    this.shots++;
                    active.frame(mc.gui.screen() == null ? screen : mc.gui.screen(), label);
                    this.shotName = "ui-scale/" + this.combo.slug() + "_" + slug(name);
                    this.since = ctx.tick();
                    this.stage = 7;
                    return StepResult.pending();
                }
                return this.tooltipShot(ctx, mc);
            }
            if (this.stage == 7) {
                if (ctx.tick() - this.since < FRAME_TICKS) {
                    return StepResult.pending();
                }
                this.shot = Screenshots.capture(this.shotName);
                this.stage = 5;
                return StepResult.pending();
            }
            if (this.stage == 5) {
                if (this.shot != null && !this.shot.isDone()) {
                    return StepResult.pending();
                }
                this.shot = null;
                return this.tooltipShot(ctx, mc);
            }
            if (this.stage == 8) {
                if (ctx.tick() - this.since < TOOLTIP_TICKS) {
                    return StepResult.pending();
                }
                this.shot = Screenshots.capture(this.tipName);
                this.stage = 9;
                return StepResult.pending();
            }
            if (this.stage == 9) {
                if (this.shot != null && !this.shot.isDone()) {
                    return StepResult.pending();
                }
                this.shot = null;
                Screenshots.parkMouse(mc);
                return this.nextPage(ctx);
            }
            if (this.stage == 6) {
                if ((mc.gui.screen() != null || !active.closed()) && ctx.tick() - this.since < CLOSE_TICKS) {
                    return StepResult.pending();
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                this.current = null;
                this.sub = null;
                this.index++;
                this.stage = 2;
                return StepResult.pending();
            }
            return StepResult.fail("unbekannter Zustand " + this.stage);
        }

        private StepResult tooltipShot(SelfTest.StepContext ctx, Minecraft mc) {
            boolean wanted = this.tipWanted;
            this.tipWanted = false;
            Screen shown = mc.gui.screen();
            LayoutProbe.TooltipSpot spot = wanted && shown != null ? LayoutProbe.largestTooltip(mc, shown) : null;
            if (spot == null) {
                return this.nextPage(ctx);
            }
            LayoutProbe.pointMouse(mc, spot.x(), spot.y());
            this.since = ctx.tick();
            this.stage = 8;
            return StepResult.pending();
        }

        private StepResult nextPage(SelfTest.StepContext ctx) {
            this.page++;
            if (this.page < this.pages) {
                this.pageShown = false;
                this.stage = 4;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            return this.closeCase(ctx);
        }

        private StepResult closeCase(SelfTest.StepContext ctx) {
            Case active = this.current;
            SubContext sub = this.sub;
            if (active != null && sub != null) {
                sub.parent = ctx;
                try {
                    active.close(sub);
                } catch (RuntimeException e) {
                    ctx.log("Schließen von " + active.name() + ": " + e);
                }
            }
            this.stage = 6;
            this.since = ctx.tick();
            return StepResult.pending();
        }

        private void record(String name, List<Finding> findings) {
            if (findings.isEmpty()) {
                return;
            }
            this.failedCases++;
            this.lines.add("## " + name + " (" + findings.size() + ")");
            for (Finding finding : findings) {
                this.kinds.merge(finding.kind(), 1, Integer::sum);
                this.lines.add("- " + finding.describe());
            }
        }

        private StepResult finish(SelfTest.StepContext ctx) {
            if (!this.combo.between()) {
                AUDITED.put(this.effective, this.combo.label());
            }
            int total = 0;
            for (int count : this.kinds.values()) {
                total += count;
            }
            List<String> out = new ArrayList<>();
            out.add("# Fenster " + this.combo.label() + " – GUI " + this.effective + ": " + total + " Befunde in " + this.failedCases + " von "
                    + this.audited + " Ansichten " + this.kinds);
            out.addAll(this.lines);
            out.add("");
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(REPORT_FILE);
            try {
                if (!reportStarted) {
                    reportStarted = true;
                    Files.writeString(file, "Propwash " + Services.PLATFORM.getModVersion() + " (" + Services.PLATFORM.getPlatformName() + ")\n\n",
                            StandardCharsets.UTF_8);
                }
                Files.write(file, out, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } catch (IOException e) {
                return StepResult.fail("Bericht " + file + ": " + e);
            }
            String summary = "GUI " + this.effective + ": " + this.audited + " Ansichten geprüft, " + total + " Befunde in " + this.failedCases
                    + " Ansichten " + this.kinds + (this.tooltipViews == 0 ? ""
                    : ", " + this.tooltipsSeen + " Tooltips in " + this.tooltipViews + " Ansichten abgefahren (" + this.tooltipNanos / 1_000_000L + " ms)");
            ctx.log(summary + " (Bericht " + REPORT_FILE + ")");
            if (this.audited == 0) {
                return StepResult.fail("keine Ansicht geprüft – passt die Auswahl " + CASES_PROPERTY + "?");
            }
            return total == 0 ? StepResult.pass() : StepResult.fail(summary + ", Einzelheiten in " + REPORT_FILE);
        }

        private void cleanup(SelfTest.StepContext ctx) {
            Case active = this.current;
            SubContext sub = this.sub;
            if (active != null && sub != null) {
                sub.parent = ctx;
                try {
                    active.close(sub);
                } catch (RuntimeException e) {
                    ctx.log("Aufräumen von " + active.name() + ": " + e);
                }
            }
            this.current = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
        }
    }

    static boolean tooltipsMatter(Screen screen) {
        return screen.width <= TOOLTIP_MAX_WIDTH || screen.height <= TOOLTIP_MAX_HEIGHT;
    }

    static String slug(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        return lower.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private static final class SubContext implements SelfTest.StepContext {
        private SelfTest.StepContext parent;
        private final int start;
        private final Map<String, Object> state = SelfTest.newState();

        private SubContext(SelfTest.StepContext parent) {
            this.parent = parent;
            this.start = parent.tick();
        }

        @Override
        public int tick() {
            return this.parent.tick() - this.start;
        }

        @Override
        public @Nullable MinecraftServer server() {
            return this.parent.server();
        }

        @Override
        public CompletableFuture<Void> onServer(Consumer<MinecraftServer> action) {
            return this.parent.onServer(action);
        }

        @Override
        public Map<String, Object> state() {
            return this.state;
        }

        @Override
        public void log(String message) {
            this.parent.log(message);
        }
    }

    static final class ZoneNames {
        static final String ENGLISH = "en_us";
        static final String ZONE = ZoneSelfTests.PREFIX + "Suche";
        static final String TITLE = "Zonenname ganz – Zone „" + ZONE + "“ im Tab Zonen bei 640x480 (GUI-Skala 2 und automatisch), "
                + "854x480 (2) und 1280x720 (3), als Operator und als Besitzer; Knöpfe der Zone ungekürzt";
        static final int STEP_TICKS = 3600;
        private static final int[][] WINDOWS = {{640, 480, 2}, {640, 480, 0}, {854, 480, 2}, {1280, 720, 3}};
        private static final String STATE = "zone_names";
        private static final String SHOT = "configurator_zones_narrow_";
        private static final int WAIT_TICKS = 200;
        private static final int MAX_SCROLLS = 24;
        private static final int CLEANUP = 10;
        private static final int SHOT_TICKS = 5;
        private final String language;
        private final AtomicBoolean created = new AtomicBoolean();
        private final List<String> problems = new ArrayList<>();
        private final List<String> checked = new ArrayList<>();
        private int stage;
        private int since;
        private int index;
        private int scrolls;
        private int @Nullable [] saved;
        private @Nullable String failure;
        private @Nullable CompletableFuture<?> server;
        private @Nullable CompletableFuture<String> shot;

        private ZoneNames(String language) {
            this.language = language;
        }

        static StepResult run(SelfTest.StepContext ctx, String language) {
            ZoneNames run = (ZoneNames) ctx.state().computeIfAbsent(STATE, key -> new ZoneNames(language));
            try {
                return run.advance(ctx, Minecraft.getInstance());
            } catch (RuntimeException e) {
                run.restoreWindow(Minecraft.getInstance());
                throw e;
            }
        }

        private boolean right() {
            return this.index % 2 == 0;
        }

        private int[] window() {
            return WINDOWS[this.index / 2];
        }

        private String where(Minecraft mc) {
            Window window = mc.getWindow();
            int[] size = this.window();
            return "GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " (Fenster " + size[0] + "x" + size[1] + ", Skala "
                    + (size[2] == 0 ? "automatisch" : String.valueOf(size[2])) + ") auf " + this.language + (this.right() ? " als Operator" : " als Besitzer");
        }

        private StepResult abort(SelfTest.StepContext ctx, String message) {
            this.failure = message;
            this.stage = CLEANUP;
            this.since = ctx.tick();
            return StepResult.pending();
        }

        private static void close(Minecraft mc) {
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            } else if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
        }

        private static @Nullable ZonesTab tab(Minecraft mc) {
            return mc.gui.screen() instanceof ConfiguratorScreen screen && screen.activeTab() instanceof ZonesTab tab ? tab : null;
        }

        private static ZoneSection.@Nullable Entry entry(ZonesTab tab) {
            for (ZoneSection.Entry entry : tab.zones().entries()) {
                if (ZONE.equals(entry.zone().name())) {
                    return entry;
                }
            }
            return null;
        }

        private static boolean synced() {
            for (Zone zone : ClientZones.INSTANCE.zones()) {
                if (ZONE.equals(zone.name())) {
                    return true;
                }
            }
            return false;
        }

        private static void command(MinecraftServer server, String command) {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }

        private static void resync(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                PropwashNet.sendToPlayer(player, WorldRules.snapshotFor(player));
                PropwashNet.sendToPlayer(player, ZoneServer.snapshotFor(player));
            });
        }

        private void restoreWindow(Minecraft mc) {
            int[] window = this.saved;
            if (window != null) {
                mc.options.guiScale().set(window[2]);
                mc.getWindow().setWindowed(window[0], window[1]);
                mc.resizeGui();
                this.saved = null;
            }
        }

        private StepResult advance(SelfTest.StepContext ctx, Minecraft mc) {
            Window window = mc.getWindow();
            switch (this.stage) {
                case 0 -> {
                    if (!this.language.equals(mc.getLanguageManager().getSelected())) {
                        return StepResult.fail("Sprache ist " + mc.getLanguageManager().getSelected() + " statt " + this.language);
                    }
                    this.saved = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                    close(mc);
                    String quoted = "\"" + ZONE + "\"";
                    this.server = ctx.onServer(server -> {
                        command(server, "propwash zone delete " + quoted);
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        int x = player.getBlockX();
                        int y = player.getBlockY();
                        int z = player.getBlockZ();
                        Zone zone = Zone.create(ZoneServer.dimension(player.level()), ZONE, ZoneShape.BOX, ZoneBox.blocks(x - 70, y - 1, z - 70, x - 61, y + 4, z - 61))
                                .withOwner(ZoneServer.owner(player));
                        this.created.set(ZoneServer.save(server, zone, Component.literal("Selbsttest"), "Selbsttest").ok());
                    });
                    this.stage = 1;
                    this.since = ctx.tick();
                    return StepResult.pending();
                }
                case 1 -> {
                    CompletableFuture<?> future = this.server;
                    if (future != null && !future.isDone()) {
                        return ctx.tick() - this.since > WAIT_TICKS ? this.abort(ctx, "Server legt die Testzone nicht an") : StepResult.pending();
                    }
                    if (!this.created.get()) {
                        return this.abort(ctx, "Server lehnt die Testzone ab");
                    }
                    if (!synced()) {
                        return ctx.tick() - this.since > WAIT_TICKS ? this.abort(ctx, "Testzone kommt am Client nicht an") : StepResult.pending();
                    }
                    this.stage = 2;
                    return StepResult.pending();
                }
                case 2 -> {
                    if (this.index >= WINDOWS.length * 2) {
                        this.stage = CLEANUP;
                        this.since = ctx.tick();
                        return StepResult.pending();
                    }
                    close(mc);
                    int[] size = this.window();
                    float density = Math.max(1.0F, window.getPixelDensity());
                    window.setWindowed(Math.round(size[0] / density), Math.round(size[1] / density));
                    this.stage = 3;
                    this.since = ctx.tick();
                    return StepResult.pending();
                }
                case 3 -> {
                    int[] size = this.window();
                    boolean sized = window.getWidth() == size[0] && window.getHeight() == size[1];
                    if (!sized && ctx.tick() - this.since < WINDOW_TICKS) {
                        return StepResult.pending();
                    }
                    if (!sized) {
                        return this.abort(ctx, "Fenster wird nicht " + size[0] + "x" + size[1] + ": " + window.getWidth() + "x" + window.getHeight());
                    }
                    mc.options.guiScale().set(size[2]);
                    mc.resizeGui();
                    if (this.right() && (!ClientWorldRules.INSTANCE.editable() || !ClientZones.INSTANCE.editable())) {
                        resync(ctx);
                    } else if (!this.right() && (ClientWorldRules.INSTANCE.editable() || ClientZones.INSTANCE.editable())) {
                        List<WorldRulesS2C.Entry> entries = new ArrayList<>();
                        ClientWorldRules.INSTANCE.raw().forEach((id, value) -> entries.add(new WorldRulesS2C.Entry(id, value)));
                        ClientWorldRules.INSTANCE.accept(new WorldRulesS2C(false, entries));
                        ClientZones.INSTANCE.accept(new ZonesS2C(false, true, ClientZones.INSTANCE.syncedDimension(), ClientZones.INSTANCE.stored(),
                                ClientZones.INSTANCE.release()));
                    }
                    this.stage = 4;
                    this.since = ctx.tick();
                    return StepResult.pending();
                }
                case 4 -> {
                    if (ClientWorldRules.INSTANCE.editable() != this.right() || ClientZones.INSTANCE.editable() != this.right()) {
                        return ctx.tick() - this.since > WAIT_TICKS ? this.abort(ctx, "Operator-Rechte kommen nicht " + (this.right() ? "zurück"
                                : "weg")) : StepResult.pending();
                    }
                    ConfigTabs.open(null, ZonesTab.ID);
                    this.scrolls = 0;
                    this.stage = 5;
                    this.since = ctx.tick();
                    return StepResult.pending();
                }
                case 5 -> {
                    ZonesTab tab = tab(mc);
                    ZoneSection.Entry entry = tab == null ? null : entry(tab);
                    if (tab == null || entry == null || entry.owner() != this.right() || entry.edit() == null || ctx.tick() - this.since < 3) {
                        return ctx.tick() - this.since > WAIT_TICKS ? this.abort(ctx, "Tab Zonen zeigt die Testzone nicht " + this.where(mc))
                                : StepResult.pending();
                    }
                    int top = tab.screenY(entry);
                    if (top >= tab.viewTop() && top + entry.height() <= tab.clipBottom()) {
                        this.stage = 6;
                        this.since = ctx.tick();
                        return StepResult.pending();
                    }
                    Screen screen = mc.gui.screen();
                    if (screen == null || this.scrolls++ >= MAX_SCROLLS) {
                        this.problems.add(this.where(mc) + ": Zone lässt sich nicht ins Bild scrollen (y " + top + ", sichtbar " + tab.viewTop() + "–"
                                + tab.clipBottom() + ")");
                        return this.next(ctx, mc);
                    }
                    screen.mouseScrolled(tab.left() + 4, tab.viewTop() + 4, 0.0, top < tab.viewTop() ? 1.0 : -1.0);
                    return StepResult.pending();
                }
                case 6 -> {
                    if (ctx.tick() - this.since < SETTLE_TICKS) {
                        return StepResult.pending();
                    }
                    ZonesTab tab = tab(mc);
                    ZoneSection.Entry entry = tab == null ? null : entry(tab);
                    Screen screen = mc.gui.screen();
                    if (tab == null || entry == null || screen == null) {
                        return this.abort(ctx, "Tab Zonen vor der Prüfung geschlossen " + this.where(mc));
                    }
                    this.check(mc, screen, tab, entry);
                    int[] size = this.window();
                    if (Screenshots.enabled() && this.right() && size[0] == 640 && size[2] == 2) {
                        Screenshots.clearOverlays(mc);
                        Screenshots.parkMouse(mc);
                        this.stage = 7;
                        this.since = ctx.tick();
                        return StepResult.pending();
                    }
                    return this.next(ctx, mc);
                }
                case 7 -> {
                    if (ctx.tick() - this.since < SHOT_TICKS) {
                        return StepResult.pending();
                    }
                    this.shot = Screenshots.capture(SHOT + this.language.substring(0, 2));
                    this.stage = 8;
                    return StepResult.pending();
                }
                case 8 -> {
                    CompletableFuture<String> pending = this.shot;
                    if (pending != null && !pending.isDone()) {
                        return StepResult.pending();
                    }
                    if (pending != null && pending.isCompletedExceptionally()) {
                        this.problems.add("Bildschirmfoto " + SHOT + this.language.substring(0, 2) + ": " + pending.exceptionNow());
                    } else if (pending != null) {
                        ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(SHOT + this.language.substring(0, 2) + ".png") + " (" + pending.join() + ")");
                    }
                    this.shot = null;
                    return this.next(ctx, mc);
                }
                case CLEANUP -> {
                    close(mc);
                    this.restoreWindow(mc);
                    String quoted = "\"" + ZONE + "\"";
                    this.server = ctx.onServer(server -> {
                        command(server, "propwash zone delete " + quoted);
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        PropwashNet.sendToPlayer(player, WorldRules.snapshotFor(player));
                        PropwashNet.sendToPlayer(player, ZoneServer.snapshotFor(player));
                    });
                    this.stage = CLEANUP + 1;
                    this.since = ctx.tick();
                    return StepResult.pending();
                }
                default -> {
                    CompletableFuture<?> future = this.server;
                    boolean done = (future == null || future.isDone()) && ClientWorldRules.INSTANCE.editable() && ClientZones.INSTANCE.editable() && !synced();
                    if (!done && ctx.tick() - this.since < WAIT_TICKS) {
                        return StepResult.pending();
                    }
                    if (!done) {
                        ctx.log("Aufräumen unvollständig: Rechte " + ClientWorldRules.INSTANCE.editable() + ", Testzone noch da " + synced());
                    }
                    if (this.failure != null) {
                        return StepResult.fail(this.failure);
                    }
                    if (!this.problems.isEmpty()) {
                        return StepResult.fail(String.join("; ", this.problems));
                    }
                    ctx.log(this.checked.size() + " Ansichten geprüft, Zonenname ganz: " + String.join(" | ", this.checked));
                    return StepResult.pass();
                }
            }
        }

        private StepResult next(SelfTest.StepContext ctx, Minecraft mc) {
            close(mc);
            this.index++;
            this.stage = 2;
            this.since = ctx.tick();
            return StepResult.pending();
        }

        private void check(Minecraft mc, Screen screen, ZonesTab tab, ZoneSection.Entry entry) {
            String where = this.where(mc);
            Zone zone = entry.zone();
            boolean button = false;
            int width = tab.rowWidth();
            boolean compact = ZoneSection.compact(mc.font, zone, width, button);
            int room = ZoneSection.nameRoom(mc.font, zone, width, button);
            int needed = mc.font.width(zone.name());
            String kind = ZoneText.kind(zone).getString();
            LayoutProbe.Frame frame = LayoutProbe.capture(mc, screen);
            LayoutAudit.Text drawn = null;
            for (LayoutAudit.Text text : frame.texts()) {
                if (text.value().equals(zone.name())) {
                    drawn = text;
                    break;
                }
            }
            String cut = frame.cuts().get(zone.name());
            String found = null;
            if (cut != null) {
                found = "gekürzt zu „" + cut + "“";
            } else if (drawn == null) {
                found = "nicht gezeichnet";
            } else if (drawn.full().width() < needed - 1) {
                found = "gestaucht auf " + drawn.full().width() + " von " + needed + " Punkten";
            } else if (!drawn.full().equals(drawn.visible())) {
                found = "nur teilweise sichtbar";
            }
            String layout = "Zeilenbreite " + width + ", Platz für den Namen " + room + ", nötig " + needed + (compact ? ", kompakt" : ", einzeilig");
            if (found != null) {
                this.problems.add("TRUNCATED „" + zone.name() + "“ " + where + ": " + found + " (" + layout + ")");
            }
            if (frame.cuts().containsKey(kind)) {
                this.problems.add("TRUNCATED Art „" + kind + "“ " + where + ": gekürzt zu „" + frame.cuts().get(kind) + "“ (" + layout + ")");
            }
            for (FlatButton action : entry.buttons()) {
                String label = action.getMessage().getString();
                if (frame.cuts().containsKey(label) || mc.font.width(label) > action.getWidth() - 2 || action.getX() + action.getWidth() > tab.left() + width) {
                    this.problems.add("TRUNCATED Knopf „" + label + "“ " + where + ": Breite " + action.getWidth() + ", nötig " + mc.font.width(label)
                            + ", x " + action.getX() + "–" + (action.getX() + action.getWidth()) + " bei Zeile bis " + (tab.left() + width));
                }
            }
            LayoutAudit.Box bounds = LayoutAudit.Box.of(0, 0, screen.width, screen.height);
            for (Finding finding : LayoutAudit.statics(bounds, frame.texts(), frame.widgets())) {
                if (finding.subject().equals(zone.name()) || finding.subject().equals(kind)) {
                    this.problems.add(where + ": " + finding.describe());
                }
            }
            this.checked.add(where + ": " + layout);
        }
    }
}

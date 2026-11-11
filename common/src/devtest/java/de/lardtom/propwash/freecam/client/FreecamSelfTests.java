package de.lardtom.propwash.freecam.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.UiScaleSelfTests;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.CameraClip;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotView;
import de.lardtom.propwash.freecam.FreecamBounds;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.screen.KeysTab;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.UiClient;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.search.SettingsIndex;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.tab.RatesTab;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FreecamSelfTests {
    public static final String GROUP = "freecam: ";
    static final int SITE_X = 40;
    static final int SITE_Z = -36;
    static final int WALL_DX = 6;
    static final int WALL_THICK = 3;
    static final int WALL_HALF = 2;
    static final int WALL_HEIGHT = 5;
    private static final String RUN = "freecam_run";
    private static final int PARK_TICKS = 40;
    private static final int DRIFT_TICKS = 100;
    private static final int SETTLE_TICKS = 80;
    private static final long HITCH_MILLIS = 400L;
    private static final int ARM_SWITCH_OFF = 1;
    private static final int ARM_SWITCH_ON = 2;
    private static final int ARM_DONE = 3;
    static final AtomicReference<BlockPos> SITE = new AtomicReference<>();
    static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    static final AtomicReference<String> SERVER = new AtomicReference<>();
    static final AtomicInteger SERVER_DRONES = new AtomicInteger(-1);
    static final AtomicReference<FreecamConfig> CONFIG = new AtomicReference<>();

    private FreecamSelfTests() {
    }

    record Saved(Vec3 position, float yaw, float pitch, GameType mode, float health, int wind) {
    }

    interface Body {
        StepResult run(SelfTest.StepContext ctx, Run run);
    }

    record Phase(String name, int timeout, Body body) {
    }

    static final class Run {
        int index;
        int since;
        @Nullable CompletableFuture<Void> future;
        final List<String> notes = new ArrayList<>();
        final Vector3d mark = new Vector3d();
        final Vector3d other = new Vector3d();
        Vec3 player = Vec3.ZERO;
        int count;
        int sent;
        int clientDrones;
        int tick = -1;
        @Nullable Object ref;
        boolean flag;
        double value;
        int arming;
        long armFrame;

        int waited(SelfTest.StepContext ctx) {
            return ctx.tick() - this.since;
        }
    }

    public static void register() {
        String cleanup = GROUP + "aufräumen";
        SelfTest.switchStep(GROUP + "Testplatz mit Wand, Regel im Standard (aus)", cleanup, 900, flow(
                phase("Freecam-Einstellungen auf Standard", 20, (ctx, run) -> {
                    resetConfig();
                    return StepResult.pass();
                }),
                phase("Testplatz bauen und Regel auf Standard", 600, (ctx, run) -> server(ctx, run, FreecamSelfTests::prepare)),
                phase("Spieler am Testplatz, Regel synchronisiert aus", 400, (ctx, run) -> until(atSite() && ruleSynced(false), "Spieler oder Regel"))));
        SelfTest.clientStep(GROUP + "Regel im Standard (aus) → Start verweigert, Knopf ausgegraut mit Grund und Sprung zur Regel", 1200, flow(
                phase("Taste F6 bei Regel aus", 100, FreecamSelfTests::deniedByKey),
                phase("K-Menü Übersicht öffnen", 200, (ctx, run) -> openConfigurator(ctx, run, OverviewTab.ID)),
                phase("Knopf ausgegraut mit Grund, Sprung zur Regel sichtbar", 100, FreecamSelfTests::deniedButton),
                phase("Sprung zur Regel im Tab Welt-Regeln", 100, FreecamSelfTests::ruleJump),
                phase("K-Menü schließen", 100, FreecamSelfTests::closeScreen)));
        SelfTest.clientStep(GROUP + "Regel an → Start ohne neues Drohnen-Entity, Spieler steht still, kein C2S-Paket", 1600, flow(
                phase("Regel an", 300, (ctx, run) -> setRule(ctx, run, true)),
                phase("Drohnen zählen", 200, FreecamSelfTests::countDrones),
                phase("Start per F6 an der Augenposition", 100, FreecamSelfTests::startByKey),
                phase("unscharf geparkt, Spieler steht still, Tastatur steuert die Freecam", PARK_TICKS + 60, FreecamSelfTests::parkedAndStill),
                phase("kein neues Drohnen-Entity am Server", 200, FreecamSelfTests::noNewDrone),
                phase("Ende per F6, Kamera zurück am Spieler", 100, FreecamSelfTests::endByKey)));
        SelfTest.clientStep(GROUP + "Start über den Knopf im K-Menü", 800, flow(
                phase("K-Menü Übersicht öffnen", 200, (ctx, run) -> openConfigurator(ctx, run, OverviewTab.ID)),
                phase("Knopf aktiv, Klick startet die Freecam", 200, FreecamSelfTests::startByButton),
                phase("Ende per Y", 100, FreecamSelfTests::endByExitKey)));
        SelfTest.clientStep(GROUP + "durch eine Wand → kein Crash, Kamera im Block wie im Zuschauermodus", 1600, flow(
                phase("Start", 100, FreecamSelfTests::start),
                phase("vor der Wand scharf schalten", 300, FreecamSelfTests::armBeforeWall),
                phase("durch die Wand", 300, FreecamSelfTests::throughWall),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "an der Chunk-Grenze → Stopp, Warnung SICHTWEITE ENDE und Gleiten entlang der Grenze", 1600, flow(
                phase("Start", 100, FreecamSelfTests::start),
                phase("an die Grenze der geladenen Chunks", 400, FreecamSelfTests::toEdge),
                phase("entlang der Grenze gleiten", 200, FreecamSelfTests::alongEdge),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Wind 3 → Drift im Angle-Modus über 0,5 m in 5 s, Wind 0 → unter 0,1 m", 2400, flow(
                phase("Wind 3", 300, (ctx, run) -> setWind(ctx, run, 3)),
                phase("Start", 100, FreecamSelfTests::start),
                phase("schweben im Angle-Modus", 400, FreecamSelfTests::hoverSettled),
                phase("Drift mit Wind 3", DRIFT_TICKS + 40, (ctx, run) -> drift(ctx, run, true)),
                phase("Wind 0", 300, (ctx, run) -> setWind(ctx, run, 0)),
                phase("schweben ohne Wind", 400, FreecamSelfTests::hoverSettled),
                phase("Drift ohne Wind", DRIFT_TICKS + 40, (ctx, run) -> drift(ctx, run, false)),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Ruckler beim Scharfschalten – mehrere Ticks ohne Bild dazwischen, die Freecam wird trotzdem scharf und fliegt", 1200, flow(
                phase("Regel an", 300, (ctx, run) -> setRule(ctx, run, true)),
                phase("Ruckler vor dem Start", 20, (ctx, run) -> hitch()),
                phase("Start", 100, FreecamSelfTests::start),
                phase("Start und Schalter ohne Bild dazwischen, scharf in der Luft mit 4 m/s", 300, FreecamSelfTests::cruise),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "K → genau vier Tabs, Kopfzeile mit Größe, Suche nur in sichtbaren Tabs, Preset 7″ im Flug", 1600, flow(
                phase("Start", 100, FreecamSelfTests::start),
                phase("scharf in der Luft mit 4 m/s", 300, FreecamSelfTests::cruise),
                phase("K drücken", 200, FreecamSelfTests::pressK),
                phase("vier Tabs und Kopfzeile", 100, FreecamSelfTests::fourTabs),
                phase("Suche", 200, FreecamSelfTests::searchVisibleOnly),
                phase("Preset 7″: Position, Lage und Tempo übernommen", 200, FreecamSelfTests::presetSeven),
                phase("K-Menü schließen", 100, FreecamSelfTests::closeScreen),
                phase("7″ fliegt scharf mit der Masse der 7″-Sim weiter", 200, FreecamSelfTests::sevenFlies),
                phase("zurück auf 5″", 200, FreecamSelfTests::presetFive),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "OSD ohne Akku- und Link-Elemente, Name FREECAM · 5″, OSD aus zeichnet nichts", 1200, flow(
                phase("Start", 100, FreecamSelfTests::start),
                phase("OSD gezeichnet ohne Akku und Link", 200, FreecamSelfTests::osdWithoutBattery),
                phase("OSD aus", 200, FreecamSelfTests::osdOff),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Schaden → Ende", 1200, flow(
                phase("Start", 100, FreecamSelfTests::start),
                phase("Schaden am Server", 300, (ctx, run) -> server(ctx, run, FreecamSelfTests::hurt)),
                phase("Freecam endet mit Grund Schaden", 200, (ctx, run) -> ended(run, FreecamRules.End.HURT)),
                phase("Spieler heilen", 300, (ctx, run) -> server(ctx, run, FreecamSelfTests::heal))));
        SelfTest.clientStep(GROUP + "Regel aus im Flug → Ende innerhalb eines Ticks, danach Start verweigert", 1200, flow(
                phase("Start", 100, FreecamSelfTests::start),
                phase("scharf in der Luft", 200, FreecamSelfTests::armInAir),
                phase("Regel aus am Server", 300, FreecamSelfTests::ruleOffInFlight),
                phase("Start verweigert", 100, FreecamSelfTests::deniedByKey)));
        SelfTest.clientStep(cleanup, 900, flow(phase("Testplatz abbauen, Regel und Wind zurück", 600, (ctx, run) -> {
            FreecamSession.INSTANCE.clearTestInput();
            if (FreecamSession.active()) {
                FreecamSession.INSTANCE.end(Minecraft.getInstance(), FreecamRules.End.BUTTON);
            }
            restoreConfig();
            return server(ctx, run, FreecamSelfTests::tearDown);
        })));
        SelfTest.clientStep(GROUP + "Sprung aus der Suche ab der Übersicht → Fußzeile ohne Freecam-Knöpfe, Treffer mit Fokus und Markierung, auch nach dem Weg zurück", 900, flow(
                phase("K-Menü Übersicht öffnen", 200, (ctx, run) -> openConfigurator(ctx, run, OverviewTab.ID)),
                phase("Übersicht → Totzone im Tab Rates per Enter", 100, (ctx, run) -> searchJump(ctx, run, RatesTab.ID,
                        SettingsSearch.Kind.SETTING, UiTab.label(TuneKeys.RC_DEADBAND).getString())),
                phase("zurück in die Übersicht", 100, FreecamSelfTests::backToOverview),
                phase("Übersicht → Regel „Freecam-Drohne erlauben“ per Enter", 100, (ctx, run) -> searchJump(ctx, run, WorldRulesTab.ID,
                        SettingsSearch.Kind.WORLD_RULE, Component.translatable("gamerule.propwash." + FreecamSession.RULE).getString())),
                phase("K-Menü schließen", 100, FreecamSelfTests::closeScreen)));
        SelfTest.clientStep(GROUP + "Sprung aus der Suche über die Grenze Übersicht ↔ Tab → Taste ab der Übersicht mit Fokus und Markierung, Tab-Treffer Übersicht mit Freecam-Knöpfen", 900, flow(
                phase("K-Menü Übersicht öffnen", 200, (ctx, run) -> openConfigurator(ctx, run, OverviewTab.ID)),
                phase("Übersicht → Taste im Tab Tasten per Enter", 100, (ctx, run) -> searchJump(ctx, run, KeysTab.ID,
                        SettingsSearch.Kind.KEY, Component.translatable(KeysTab.mappings().getFirst().getName()).getString())),
                phase("Tasten → Tab-Treffer Übersicht per Enter", 100, FreecamSelfTests::tabJumpToOverview),
                phase("K-Menü schließen", 100, FreecamSelfTests::closeScreen)));
        FreecamShowcase.register();
    }

    private static StepResult searchJump(SelfTest.StepContext ctx, Run run, String tabId, SettingsSearch.Kind kind, String label) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.fail("K-Menü zu");
        }
        if (run.waited(ctx) == 0) {
            ConfigTab active = screen.activeTab();
            if (active == null || !OverviewTab.ID.equals(active.id())) {
                return StepResult.fail("Ausgangslage nicht die Übersicht: " + (active == null ? "kein Tab" : active.id()));
            }
            FlatButton start = screen.freecamStartButton();
            FlatButton rule = screen.freecamRuleButton();
            if (start == null || rule == null || !(start.visible || rule.visible)) {
                return StepResult.fail("Fußzeile der Übersicht ohne Freecam-Knöpfe");
            }
            SearchBar search = screen.search();
            if (search == null) {
                return StepResult.fail("Konfigurator ohne Suchfeld");
            }
            search.focus();
            search.type(label);
            List<SettingsSearch.Hit> hits = search.hits();
            int pick = -1;
            for (int i = 0; i < hits.size() && pick < 0; i++) {
                SettingsSearch.Entry entry = hits.get(i).entry();
                if (entry.kind() == kind && tabId.equals(entry.tabId()) && entry.label().contains(label)) {
                    pick = i;
                }
            }
            if (pick < 0) {
                return StepResult.fail("Suche „" + label + "“ findet nichts im Tab " + tabId + ": "
                        + hits.stream().map(h -> h.entry().label() + " (" + h.entry().tabId() + ")").toList());
            }
            search.select(pick);
            KeyEvent enter = new KeyEvent(InputConstants.KEY_RETURN, 13, 0);
            mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.PRESS, enter);
            mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.RELEASE, enter);
            return StepResult.pending();
        }
        if (run.waited(ctx) < 2) {
            return StepResult.pending();
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !tabId.equals(active.id())) {
            return StepResult.fail("Enter springt nicht in den Tab " + tabId + ": " + (active == null ? "kein Tab" : active.id()));
        }
        AbstractWidget target = screen.highlighted();
        if (target == null || screen.getFocused() != target) {
            return StepResult.fail("Treffer „" + label + "“ nach dem Sprung ohne Fokus oder Markierung: Fokus " + screen.getFocused()
                    + ", markiert " + target);
        }
        if (active instanceof WorldRulesTab rules) {
            var row = rules.rows().stream().filter(r -> r.id().getPath().equals(FreecamSession.RULE)).findFirst().orElse(null);
            if (row == null || row.widget() != target) {
                return StepResult.fail("Sprung markiert nicht die Zeile " + FreecamSession.RULE + ": " + target);
            }
        }
        if (!target.visible || target.getY() < screen.contentView().top() || target.getY() + target.getHeight() > screen.contentView().bottom()) {
            return StepResult.fail("Treffer „" + label + "“ nicht im Bild: y " + target.getY() + " außerhalb " + screen.contentView());
        }
        FlatButton start = screen.freecamStartButton();
        FlatButton rule = screen.freecamRuleButton();
        if ((start != null && start.visible) || (rule != null && rule.visible)) {
            return StepResult.fail("Fußzeile im Tab " + tabId + " zeigt noch die Freecam-Knöpfe");
        }
        SearchBar search = screen.search();
        if (search != null && search.focused()) {
            return StepResult.fail("Suchfeld behält nach dem Sprung den Fokus");
        }
        run.notes.add("Sprung Übersicht → " + tabId + " fokussiert „" + label + "“");
        return StepResult.pass();
    }

    private static StepResult tabJumpToOverview(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.fail("K-Menü zu");
        }
        int overview = -1;
        List<ConfigTab> tabs = screen.tabs();
        for (int i = 0; i < tabs.size() && overview < 0; i++) {
            if (OverviewTab.ID.equals(tabs.get(i).id())) {
                overview = i;
            }
        }
        if (overview < 0) {
            return StepResult.fail("K-Menü ohne Tab Übersicht");
        }
        if (run.waited(ctx) == 0) {
            ConfigTab active = screen.activeTab();
            if (active == null || OverviewTab.ID.equals(active.id())) {
                return StepResult.fail("Ausgangslage ist schon die Übersicht");
            }
            SearchBar search = screen.search();
            if (search == null) {
                return StepResult.fail("Konfigurator ohne Suchfeld");
            }
            String title = tabs.get(overview).title().getString();
            search.focus();
            search.type(title);
            List<SettingsSearch.Hit> hits = search.hits();
            int pick = -1;
            for (int i = 0; i < hits.size() && pick < 0; i++) {
                SettingsSearch.Entry entry = hits.get(i).entry();
                if (entry.kind() == SettingsSearch.Kind.TAB && OverviewTab.ID.equals(entry.tabId())) {
                    pick = i;
                }
            }
            if (pick < 0) {
                return StepResult.fail("Suche „" + title + "“ findet den Tab Übersicht nicht: "
                        + hits.stream().map(h -> h.entry().label() + " (" + h.entry().tabId() + ")").toList());
            }
            search.select(pick);
            KeyEvent enter = new KeyEvent(InputConstants.KEY_RETURN, 13, 0);
            mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.PRESS, enter);
            mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.RELEASE, enter);
            return StepResult.pending();
        }
        if (run.waited(ctx) < 2) {
            return StepResult.pending();
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !OverviewTab.ID.equals(active.id())) {
            return StepResult.fail("Enter springt nicht in die Übersicht: " + (active == null ? "kein Tab" : active.id()));
        }
        List<FlatButton> sidebar = screen.sidebarButtons();
        FlatButton tab = overview < sidebar.size() ? sidebar.get(overview) : null;
        if (tab == null || screen.getFocused() != tab || !tab.visible) {
            return StepResult.fail("Tab Übersicht nach dem Sprung ohne Fokus in der Tab-Leiste: Fokus " + screen.getFocused());
        }
        FlatButton start = screen.freecamStartButton();
        FlatButton rule = screen.freecamRuleButton();
        if (start == null || rule == null || !(start.visible || rule.visible)) {
            return StepResult.fail("Fußzeile der Übersicht nach dem Sprung ohne Freecam-Knöpfe");
        }
        SearchBar search = screen.search();
        if (search == null || search.focused()) {
            return StepResult.fail("Suchfeld fehlt oder behält nach dem Sprung den Fokus");
        }
        run.notes.add("Tab-Treffer " + active.id() + " fokussiert die Tab-Leiste, Fußzeile mit Freecam-Knöpfen");
        return StepResult.pass();
    }

    private static StepResult backToOverview(SelfTest.StepContext ctx, Run run) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.fail("K-Menü zu");
        }
        if (run.waited(ctx) == 0) {
            screen.selectTab(OverviewTab.ID);
            return StepResult.pending();
        }
        ConfigTab active = screen.activeTab();
        return until(run.waited(ctx) > 2 && active != null && OverviewTab.ID.equals(active.id()), "Übersicht öffnet nicht");
    }

    public static List<UiScaleSelfTests.Case> uiScaleCases() {
        return List.of(new MenuCase());
    }

    private static final class MenuCase implements UiScaleSelfTests.Case {
        private static final String FUTURE = "freecam_ui_future";

        @Override
        public String name() {
            return "Freecam-K-Menü";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ruleSynced(true)) {
                if (!ctx.state().containsKey(FUTURE)) {
                    resetConfig();
                    ctx.state().put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 1)));
                }
                return StepResult.pending();
            }
            if (!FreecamSession.active()) {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                FreecamSession.INSTANCE.tryStart(mc);
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, FreecamTab.ID);
                ctx.state().put("freecam_ui_opened", ctx.tick());
                return StepResult.pending();
            }
            return screen.freecam() && ctx.tick() - (int) ctx.state().getOrDefault("freecam_ui_opened", 0) > 3 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public int pages(Screen screen) {
            return screen instanceof ConfiguratorScreen configurator ? configurator.tabs().size() : 1;
        }

        @Override
        public String page(Screen screen, int index) {
            if (!(screen instanceof ConfiguratorScreen configurator)) {
                return "";
            }
            if (configurator.activeTab() instanceof ModesTab modes) {
                modes.cancelAction();
            }
            configurator.selectTab(index);
            configurator.setFocused(null);
            ConfigTab tab = configurator.activeTab();
            return tab == null ? "?" : tab.id();
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen configurator) {
                if (configurator.activeTab() instanceof ModesTab modes) {
                    modes.cancelAction();
                }
                configurator.discardAndClose();
            }
            if (FreecamSession.active()) {
                FreecamSession.INSTANCE.end(mc, FreecamRules.End.BUTTON);
            }
            restoreConfig();
            ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, WorldRuleSpecs.ALLOW_FREECAM.defaultValue()));
        }
    }

    static void resetConfig() {
        FreecamConfig current = FreecamConfig.get();
        FreecamConfig copy = new FreecamConfig();
        copy.preset = current.preset;
        copy.osd = current.osd;
        copy.fovDeg = current.fovDeg;
        copy.tunes = new TreeMap<>(current.tunes);
        copy.uptilt = new TreeMap<>(current.uptilt);
        CONFIG.compareAndSet(null, copy);
        current.preset = FreecamPreset.DEFAULT.key();
        current.osd = true;
        current.fovDeg = FreecamConfig.FOV_DEFAULT;
        current.tunes = new TreeMap<>();
        current.uptilt = new TreeMap<>();
        FreecamConfig.save();
    }

    static void restoreConfig() {
        FreecamConfig saved = CONFIG.getAndSet(null);
        if (saved != null) {
            FreecamConfig.CONFIG.set(saved);
        }
    }

    static Phase phase(String name, int timeout, Body body) {
        return new Phase(name, timeout, body);
    }

    static SelfTest.ClientStep flow(Phase... phases) {
        return ctx -> {
            Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> {
                Run fresh = new Run();
                fresh.since = ctx.tick();
                return fresh;
            });
            if (run.index >= phases.length) {
                if (!run.notes.isEmpty()) {
                    ctx.log(String.join("; ", run.notes));
                }
                return StepResult.pass();
            }
            Phase phase = phases[run.index];
            StepResult result;
            try {
                result = phase.body().run(ctx, run);
            } catch (RuntimeException e) {
                return StepResult.fail(phase.name() + ": " + e);
            }
            if (result.status() == StepResult.Status.FAIL) {
                FreecamSession.INSTANCE.clearTestInput();
                return StepResult.fail(phase.name() + ": " + result.message());
            }
            if (result.status() == StepResult.Status.PENDING) {
                if (run.waited(ctx) > phase.timeout()) {
                    FreecamSession.INSTANCE.clearTestInput();
                    return StepResult.fail(phase.name() + ": nach " + phase.timeout() + " Ticks nicht erreicht"
                            + (result.message().isEmpty() ? "" : " (" + result.message() + ")"));
                }
                return StepResult.pending();
            }
            run.index++;
            run.since = ctx.tick() + 1;
            run.future = null;
            run.tick = -1;
            run.count = 0;
            run.flag = false;
            run.arming = 0;
            return StepResult.pending();
        };
    }

    static StepResult server(SelfTest.StepContext ctx, Run run, Consumer<MinecraftServer> action) {
        if (run.future == null) {
            SERVER.set(null);
            run.future = ctx.onServer(action);
            return StepResult.pending();
        }
        if (!run.future.isDone()) {
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail(String.valueOf(run.future.exceptionNow()));
        }
        String problem = SERVER.getAndSet(null);
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    static StepResult check(SelfTest.StepContext ctx, Run run, Function<MinecraftServer, @Nullable String> check) {
        return server(ctx, run, server -> SERVER.set(check.apply(server)));
    }

    static StepResult until(boolean condition, String what) {
        return condition ? StepResult.pass() : new StepResult(StepResult.Status.PENDING, what);
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler");
        }
        return players.getFirst();
    }

    static BlockPos site() {
        BlockPos site = SITE.get();
        if (site == null) {
            throw new IllegalStateException("Testplatz fehlt");
        }
        return site;
    }

    static Vec3 standPoint() {
        return Vec3.atBottomCenterOf(site());
    }

    static void prepare(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.gameMode(), player.getHealth(),
                WorldRules.get(server, WorldRuleSpecs.WIND)));
        player.setGameMode(GameType.CREATIVE);
        BlockPos spawn = level.getRespawnData().pos();
        int x = spawn.getX() + SITE_X;
        int z = spawn.getZ() + SITE_Z;
        level.getChunk(x >> 4, z >> 4);
        BlockPos site = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        SITE.set(site);
        for (int dx = -4; dx <= 14; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                level.setBlock(site.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                for (int dy = 0; dy <= 14; dy++) {
                    boolean wall = dx >= WALL_DX && dx < WALL_DX + WALL_THICK && Math.abs(dz) <= WALL_HALF && dy < WALL_HEIGHT;
                    level.setBlock(site.offset(dx, dy, dz), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, WorldRuleSpecs.ALLOW_FREECAM.defaultValue());
        teleport(player, standPoint(), -90.0F, 0.0F);
    }

    static void tearDown(MinecraftServer server) {
        ServerPlayer player = player(server);
        WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, WorldRuleSpecs.ALLOW_FREECAM.defaultValue());
        Saved saved = SAVED.getAndSet(null);
        BlockPos site = SITE.getAndSet(null);
        if (site != null) {
            ServerLevel level = player.level();
            for (int dx = -4; dx <= 14; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                        level.setBlock(site.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        if (saved != null) {
            WorldRules.set(server, WorldRuleSpecs.WIND, saved.wind());
            player.setGameMode(saved.mode());
            player.setHealth(Math.max(saved.health(), 1.0F));
            teleport(player, saved.position(), saved.yaw(), saved.pitch());
        }
    }

    static void teleport(ServerPlayer player, Vec3 feet, float yaw, float pitch) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true);
    }

    static boolean atSite() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && SITE.get() != null && player.position().distanceTo(standPoint()) < 0.2;
    }

    static boolean ruleSynced(boolean on) {
        ClientWorldRules rules = ClientWorldRules.INSTANCE;
        return rules.known() && rules.on(WorldRuleSpecs.ALLOW_FREECAM) == on;
    }

    static StepResult setRule(SelfTest.StepContext ctx, Run run, boolean on) {
        if (run.future == null) {
            run.future = ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, on ? 1 : 0));
            return StepResult.pending();
        }
        return until(run.future.isDone() && ruleSynced(on), "Regel kommt nicht beim Client an");
    }

    static StepResult setWind(SelfTest.StepContext ctx, Run run, int wind) {
        RuleSpec spec = WorldRuleSpecs.WIND;
        if (run.future == null) {
            run.future = ctx.onServer(server -> WorldRules.set(server, spec, wind));
            return StepResult.pending();
        }
        return until(run.future.isDone() && ClientWorldRules.INSTANCE.get(spec) == wind, "Wind " + wind + " kommt nicht an");
    }

    static void clickKey(@Nullable KeyMapping mapping) {
        if (mapping != null) {
            KeyMapping.click(InputConstants.getKey(mapping.saveString()));
        }
    }

    private static StepResult deniedByKey(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            if (FreecamSession.active() || mc.gui.screen() != null) {
                return StepResult.fail("Ausgangslage falsch: Freecam " + FreecamSession.active() + ", Fenster " + mc.gui.screen());
            }
            if (!ruleSynced(false)) {
                return new StepResult(StepResult.Status.PENDING, "Regel noch an");
            }
            run.count = FreecamSession.INSTANCE.starts();
            clickKey(FreecamClient.key());
            return StepResult.pending();
        }
        if (run.waited(ctx) < 3) {
            return StepResult.pending();
        }
        if (FreecamSession.active() || FreecamSession.INSTANCE.starts() != run.count) {
            return StepResult.fail("Freecam startet trotz Regel aus");
        }
        if (FreecamSession.INSTANCE.lastDenial() != FreecamRules.Denial.RULE_OFF) {
            return StepResult.fail("Grund " + FreecamSession.INSTANCE.lastDenial() + " statt RULE_OFF");
        }
        run.notes.add("F6 bei Regel aus verweigert (" + FreecamSession.INSTANCE.lastDenial() + ")");
        return StepResult.pass();
    }

    static StepResult openConfigurator(SelfTest.StepContext ctx, Run run, String tab) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ConfigTabs.open(null, tab);
            return StepResult.pending();
        }
        if (mc.gui.screen() instanceof ConfiguratorScreen screen && screen.activeTab() != null && tab.equals(screen.activeTab().id())) {
            return run.waited(ctx) > 4 ? StepResult.pass() : StepResult.pending();
        }
        return new StepResult(StepResult.Status.PENDING, "K-Menü offen: " + mc.gui.screen());
    }

    private static StepResult deniedButton(SelfTest.StepContext ctx, Run run) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.fail("K-Menü zu");
        }
        FlatButton start = screen.freecamStartButton();
        FlatButton rule = screen.freecamRuleButton();
        if (start == null || rule == null) {
            return StepResult.fail("Knöpfe fehlen in der Übersicht");
        }
        if (start.active || start.visible == screen.freecamSingleButton()) {
            return StepResult.fail("„Freecam starten“ nicht ausgegraut sichtbar: sichtbar " + start.visible + ", aktiv " + start.active
                    + ", ein Knopf " + screen.freecamSingleButton());
        }
        if (!rule.visible || !rule.active) {
            return StepResult.fail("„Zur Welt-Regel“ fehlt im Einzelspieler");
        }
        String status = String.join(" ", screen.statusLines());
        String reason = net.minecraft.network.chat.Component.translatable("propwash.freecam.denied").getString();
        if (!status.replace(" ", "").contains(reason.replace(" ", ""))) {
            return StepResult.fail("Grund nicht in der Fußzeile: „" + status + "“");
        }
        start.press();
        if (FreecamSession.active()) {
            return StepResult.fail("ausgegrauter Knopf startet die Freecam");
        }
        run.notes.add("Knopf ausgegraut, Grund „" + reason + "“");
        return StepResult.pass();
    }

    private static StepResult ruleJump(SelfTest.StepContext ctx, Run run) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.fail("K-Menü zu");
        }
        if (run.waited(ctx) == 0) {
            FlatButton rule = screen.freecamRuleButton();
            if (rule == null) {
                return StepResult.fail("Sprung fehlt");
            }
            rule.press();
            return StepResult.pending();
        }
        if (!(screen.activeTab() instanceof WorldRulesTab rules)) {
            return StepResult.fail("Sprung landet nicht im Tab Welt-Regeln: " + (screen.activeTab() == null ? "kein Tab" : screen.activeTab().id()));
        }
        var row = rules.rows().stream().filter(r -> r.id().getPath().equals(FreecamSession.RULE)).findFirst().orElse(null);
        if (row == null || row.widget() == null) {
            return StepResult.fail("Zeile allow_freecam fehlt");
        }
        if (screen.highlighted() != row.widget()) {
            return StepResult.fail("Regel nicht hervorgehoben");
        }
        int y = rules.screenY(row);
        if (y < screen.contentView().top() || y > screen.contentView().bottom()) {
            return StepResult.fail("Zeile nicht sichtbar: y " + y + " außerhalb " + screen.contentView());
        }
        run.notes.add("Sprung zur Regel, Zeile bei y " + y);
        return StepResult.pass();
    }

    static StepResult closeScreen(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        } else if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        return until(mc.gui.screen() == null, "Fenster bleibt offen");
    }

    private static StepResult countDrones(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            run.clientDrones = clientDrones();
            run.future = ctx.onServer(server -> SERVER_DRONES.set(serverDrones(server)));
            return StepResult.pending();
        }
        return until(run.future.isDone() && SERVER_DRONES.get() >= 0, "Server zählt nicht");
    }

    static int clientDrones() {
        Minecraft mc = Minecraft.getInstance();
        int count = 0;
        if (mc.level != null) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof QuadcopterEntity) {
                    count++;
                }
            }
        }
        return count;
    }

    static int serverDrones(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(DroneModule.DRONE.get(), entity -> true).size();
        }
        return count;
    }

    private static StepResult startByKey(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        if (run.waited(ctx) == 0) {
            run.sent = ClientNet.sentCount();
            run.player = player.position();
            clickKey(FreecamClient.key());
            return StepResult.pending();
        }
        if (!FreecamSession.active()) {
            return new StepResult(StepResult.Status.PENDING, "Freecam startet nicht: " + FreecamSession.INSTANCE.lastDenial());
        }
        Vector3d camera = new Vector3d();
        Quaternionf rotation = new Quaternionf();
        FreecamSession.INSTANCE.cameraPose(camera, rotation);
        Vec3 eye = player.getEyePosition();
        double offset = eye.distanceTo(new Vec3(camera.x, camera.y, camera.z));
        if (offset > 0.02) {
            return StepResult.fail(String.format(Locale.ROOT, "Kamera %.3f Blöcke neben dem Auge", offset));
        }
        Quaternionf body = FreecamSession.INSTANCE.orientation(new Quaternionf());
        Vector3f forward = body.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f up = body.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        if (Math.abs(forward.y) > 0.01F || up.y < 0.999F) {
            return StepResult.fail("nicht waagerecht: vorn y " + forward.y + ", oben y " + up.y);
        }
        if (forward.x * look.x + forward.z * look.z < 0.999) {
            return StepResult.fail(String.format(Locale.ROOT, "Kurs nicht in Blickrichtung: %.3f/%.3f gegen %.3f/%.3f", forward.x, forward.z, look.x, look.z));
        }
        if (FreecamSession.INSTANCE.armed() || !FreecamSession.INSTANCE.parked()) {
            return StepResult.fail("nicht unscharf geparkt");
        }
        if (!FpvView.active() || !PilotView.active() || !CameraClip.active() || !FpvView.isPilotBody(player)) {
            return StepResult.fail("Haken fehlen: FpvView " + FpvView.active() + ", PilotView " + PilotView.active() + ", CameraClip " + CameraClip.active());
        }
        DroneContext context = DroneContext.resolve(mc);
        if (!context.freecam() || !context.hasDrone() || context.piloting()) {
            return StepResult.fail("DroneContext nicht FREECAM: " + context.source());
        }
        run.mark.set(camera);
        run.notes.add(String.format(Locale.ROOT, "Start an der Augenposition (Abstand %.4f), Kurs %.1f°", offset, player.getYRot()));
        return StepResult.pass();
    }

    private static StepResult parkedAndStill(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !FreecamSession.active()) {
            return StepResult.fail("Freecam endet von selbst: " + FreecamSession.INSTANCE.lastEnd());
        }
        int waited = run.waited(ctx);
        Vector3d camera = new Vector3d();
        FreecamSession.INSTANCE.cameraPose(camera, new Quaternionf());
        if (waited < PARK_TICKS) {
            if (camera.distance(run.mark) > 0.01) {
                return StepResult.fail(String.format(Locale.ROOT, "unscharf nicht geparkt: %.3f Blöcke bewegt", camera.distance(run.mark)));
            }
            Camera main = mc.gameRenderer.mainCamera();
            if (waited > 2 && main.position().distanceTo(new Vec3(camera.x, camera.y, camera.z)) > 0.02) {
                return StepResult.fail("Spielkamera folgt der Freecam nicht: " + main.position());
            }
            return StepResult.pending();
        }
        if (waited == PARK_TICKS) {
            mc.options.keyUp.setDown(true);
            run.count = 0;
            return StepResult.pending();
        }
        if (waited < PARK_TICKS + 20) {
            if (FreecamSession.INSTANCE.sticks().throttle() > 0.01F) {
                run.count++;
            }
            return StepResult.pending();
        }
        mc.options.keyUp.setDown(false);
        double moved = player.position().distanceTo(run.player);
        if (moved > 0.001) {
            return StepResult.fail(String.format(Locale.ROOT, "Spieler bewegt sich %.4f Blöcke", moved));
        }
        if (run.count == 0) {
            return StepResult.fail("Bewegungstaste W gibt der Freecam kein Gas (Tastatur nicht als Flug-Sitzung)");
        }
        int sent = ClientNet.sentCount() - run.sent;
        if (sent != 0) {
            return StepResult.fail(sent + " Propwash-Pakete an den Server während der Freecam");
        }
        int drones = clientDrones();
        if (drones != run.clientDrones) {
            return StepResult.fail("Drohnen-Entities am Client " + run.clientDrones + " → " + drones);
        }
        run.notes.add("geparkt " + PARK_TICKS + " Ticks, Spieler unbewegt, W gab " + run.count + " Ticks Gas, 0 Pakete");
        return StepResult.pass();
    }

    private static StepResult noNewDrone(SelfTest.StepContext ctx, Run run) {
        int before = SERVER_DRONES.get();
        return check(ctx, run, server -> {
            int now = serverDrones(server);
            return now == before ? null : "Drohnen am Server " + before + " → " + now;
        });
    }

    private static StepResult endByKey(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            clickKey(FreecamClient.key());
            return StepResult.pending();
        }
        if (FreecamSession.active()) {
            return StepResult.pending();
        }
        return afterEnd(mc, run, FreecamRules.End.KEY);
    }

    static StepResult afterEnd(Minecraft mc, Run run, FreecamRules.End expected) {
        if (FreecamSession.INSTANCE.lastEnd() != expected) {
            return StepResult.fail("Ende mit Grund " + FreecamSession.INSTANCE.lastEnd() + " statt " + expected);
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.getCameraEntity() != player || FpvView.active() || PilotView.active() || FpvView.isPilotBody(player)) {
            return StepResult.fail("Kamera nicht zurück am Spieler");
        }
        if (mc.gameRenderer.mainCamera().position().distanceTo(player.getEyePosition()) > 0.05) {
            return new StepResult(StepResult.Status.PENDING, "Spielkamera noch nicht am Auge");
        }
        return StepResult.pass();
    }

    private static StepResult startByButton(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return StepResult.fail("K-Menü zu");
            }
            FlatButton start = screen.freecamStartButton();
            FlatButton rule = screen.freecamRuleButton();
            if (start == null || !start.visible || !start.active) {
                return StepResult.fail("„Freecam starten“ bei Regel an nicht aktiv");
            }
            if (rule != null && rule.visible) {
                return StepResult.fail("Sprung zur Regel bei Regel an noch sichtbar");
            }
            start.press();
            return StepResult.pending();
        }
        return until(FreecamSession.active() && mc.gui.screen() == null, "Knopf startet nicht");
    }

    private static StepResult endByExitKey(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        int waited = run.waited(ctx);
        if (waited == 0) {
            InputKeys.EXIT.setDown(true);
            return StepResult.pending();
        }
        if (waited == 3) {
            InputKeys.EXIT.setDown(false);
        }
        if (FreecamSession.active()) {
            return StepResult.pending();
        }
        InputKeys.EXIT.setDown(false);
        return afterEnd(mc, run, FreecamRules.End.KEY);
    }

    static StepResult start(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            if (!ruleSynced(true)) {
                run.future = ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 1));
            }
            return StepResult.pending();
        }
        if (!ruleSynced(true)) {
            return new StepResult(StepResult.Status.PENDING, "Regel an kommt nicht an");
        }
        if (!FreecamSession.active()) {
            FreecamSession.INSTANCE.tryStart(mc);
        }
        return until(FreecamSession.active(), "Start: " + FreecamSession.INSTANCE.lastDenial());
    }

    private static StepResult hitch() {
        try {
            Thread.sleep(HITCH_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.fail("Ruckler unterbrochen");
        }
        return StepResult.pass();
    }

    static StepResult endQuietly(SelfTest.StepContext ctx, Run run) {
        FreecamSession.INSTANCE.clearTestInput();
        Minecraft mc = Minecraft.getInstance();
        if (FreecamSession.active()) {
            FreecamSession.INSTANCE.end(mc, FreecamRules.End.BUTTON);
        }
        return until(!FreecamSession.active() && !FpvView.active(), "Freecam endet nicht");
    }

    static void arm(float throttle, StabMode mode) {
        FreecamSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, mode);
    }

    static boolean armAt(Run run, double x, double y, double z, float yawDeg) {
        FreecamSession session = FreecamSession.INSTANCE;
        if (run.arming == 0) {
            session.testPlace(x, y, z, yawDeg);
            if (session.armed()) {
                run.arming = ARM_DONE;
                return true;
            }
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
            run.armFrame = session.frames();
            run.arming = ARM_SWITCH_OFF;
            return false;
        }
        if (run.arming == ARM_SWITCH_OFF && session.frames() > run.armFrame) {
            arm(0.0F, StabMode.ANGLE);
            run.armFrame = session.frames();
            run.arming = ARM_SWITCH_ON;
            return false;
        }
        if (run.arming == ARM_SWITCH_ON && session.frames() > run.armFrame && session.armed()) {
            run.arming = ARM_DONE;
        }
        return run.arming == ARM_DONE && session.armed();
    }

    static StepResult notArmed(Run run) {
        boolean seen = FreecamSession.INSTANCE.frames() > run.armFrame;
        String stage = switch (run.arming) {
            case ARM_SWITCH_OFF -> seen ? "" : ", noch kein Bild mit Schalter aus";
            case ARM_SWITCH_ON -> seen ? ", Schalter an mit Gas unten im Bild" : ", noch kein Bild mit Schalter an und Gas unten";
            case ARM_DONE -> ", entschärft";
            default -> "";
        };
        return new StepResult(StepResult.Status.PENDING, "scharf: Blocker " + armBlockers() + stage);
    }

    static float hover() {
        DroneSim sim = FreecamSession.INSTANCE.sim();
        if (sim == null) {
            return 0.0F;
        }
        float hover = (float) sim.telemetry().hoverThrottleStick;
        return Float.isFinite(hover) && hover > 0.05F ? hover : 0.3F;
    }

    private static StepResult armBeforeWall(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = site();
        if (!armAt(run, site.getX() + 3.5, site.getY() + 2.5, site.getZ() + 0.5, -90.0F)) {
            return notArmed(run);
        }
        arm(hover(), StabMode.ANGLE);
        return StepResult.pass();
    }

    static int armBlockers() {
        DroneSim sim = FreecamSession.INSTANCE.sim();
        return sim == null ? -1 : sim.telemetry().armBlockers;
    }

    private static StepResult throughWall(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam endet in der Wand: " + FreecamSession.INSTANCE.lastEnd());
        }
        BlockPos site = site();
        double far = site.getX() + WALL_DX + WALL_THICK + 0.6;
        Vector3d position = FreecamSession.INSTANCE.position(new Vector3d());
        arm(hover(), StabMode.ANGLE);
        if (run.waited(ctx) % 4 == 0) {
            FreecamSession.INSTANCE.testPush(6.0, 0.0, 0.0);
        }
        Camera main = mc.gameRenderer.mainCamera();
        BlockPos eye = main.blockPosition();
        if (mc.level != null && !mc.level.getBlockState(eye).isAir() && mc.level.getBlockState(eye).isSolidRender()) {
            run.count++;
            Vector3d pose = new Vector3d();
            FreecamSession.INSTANCE.cameraPose(pose, new Quaternionf());
            if (main.position().distanceTo(new Vec3(pose.x, pose.y, pose.z)) > 0.05) {
                return StepResult.fail("Kamera im Block verschoben (Kamera-Schutz aktiv): " + main.position() + " statt " + pose);
            }
            if (!CameraClip.active()) {
                return StepResult.fail("CameraClip nicht aktiv");
            }
        }
        DroneSim sim = FreecamSession.INSTANCE.sim();
        if (sim == null || !sim.telemetry().armed) {
            return StepResult.fail("in der Wand entschärft (Crash)");
        }
        if (sim.telemetry().crashCount != 0) {
            return StepResult.fail("Crash-Zähler " + sim.telemetry().crashCount);
        }
        if (position.x > far) {
            if (run.count == 0) {
                return StepResult.fail("Kamera war nie im Block");
            }
            run.notes.add(String.format(Locale.ROOT, "durch %d Blöcke Wand, %d Ticks Kamera im Block, x %.2f", WALL_THICK, run.count, position.x));
            FreecamSession.INSTANCE.clearTestInput();
            return StepResult.pass();
        }
        return new StepResult(StepResult.Status.PENDING, String.format(Locale.ROOT, "x %.2f vor %.2f", position.x, far));
    }

    static int loadedEdgeX(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return Integer.MIN_VALUE;
        }
        return FreecamEdgeCheck.edgeX(columns(level), player.getBlockX() >> 4, player.getBlockZ() >> 4);
    }

    static FreecamBounds.Columns columns(ClientLevel level) {
        return (cx, cz) -> level.getChunkSource().hasChunk(cx, cz);
    }

    private static StepResult toEdge(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus: " + FreecamSession.INSTANCE.lastEnd());
        }
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return StepResult.fail("kein Spieler");
        }
        int edge = loadedEdgeX(mc);
        if (run.tick < 0) {
            if (edge != (int) run.value) {
                run.value = edge;
                run.count = 0;
                return new StepResult(StepResult.Status.PENDING, "Chunks laden noch");
            }
            if (++run.count < 40) {
                return StepResult.pending();
            }
            run.tick = ctx.tick();
            run.count = FreecamSession.INSTANCE.edgeHits();
            BlockPos site = site();
            FreecamSession.INSTANCE.testPlace(edge - 3.0, site.getY() + 12.0, site.getZ() + 0.5, -90.0F);
            arm(0.0F, StabMode.ANGLE);
            return StepResult.pending();
        }
        run.value = edge;
        if (ctx.tick() - run.tick < 5) {
            return StepResult.pending();
        }
        arm(hover(), StabMode.ANGLE);
        if (!FreecamSession.INSTANCE.armed()) {
            return new StepResult(StepResult.Status.PENDING, "scharf: Blocker " + armBlockers());
        }
        FreecamSession.INSTANCE.testPush(8.0, 0.0, 0.0);
        Vector3d position = FreecamSession.INSTANCE.position(new Vector3d());
        String outside = FreecamEdgeCheck.outside(columns(level), player.getBlockX() >> 4, player.getBlockZ() >> 4, position.x, position.z);
        if (outside != null) {
            return StepResult.fail("über die Grenze geflogen: " + outside + ", Grenzkontakte " + FreecamSession.INSTANCE.edgeHits());
        }
        if (FreecamSession.INSTANCE.edgeHits() == run.count || !FreecamSession.INSTANCE.edge()) {
            return new StepResult(StepResult.Status.PENDING, String.format(Locale.ROOT, "x %.2f, Grenze %.0f", position.x, run.value));
        }
        OsdData data = OsdHud.sampler().data();
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        OsdWarnings.collect(data, warnings);
        if (!data.freecamEdge || !warnings.contains(OsdWarnings.Warning.FREECAM_EDGE)) {
            return new StepResult(StepResult.Status.PENDING, "OSD-Warnung fehlt");
        }
        String text = net.minecraft.network.chat.Component.translatable(OsdWarnings.Warning.FREECAM_EDGE.translationKey()).getString();
        run.notes.add(String.format(Locale.ROOT, "an der Grenze x %.0f gestoppt bei x %.2f, Warnung „%s“", run.value, position.x, text));
        run.other.set(position);
        return StepResult.pass();
    }

    private static StepResult alongEdge(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return StepResult.fail("kein Spieler");
        }
        if (run.waited(ctx) == 0) {
            run.count = FreecamSession.INSTANCE.edgeHits();
        }
        arm(hover(), StabMode.ANGLE);
        FreecamSession.INSTANCE.testPush(6.0, 0.0, 3.0);
        Vector3d position = FreecamSession.INSTANCE.position(new Vector3d());
        FreecamBounds.Columns columns = columns(level);
        int playerChunkX = player.getBlockX() >> 4;
        String outside = FreecamEdgeCheck.outside(columns, playerChunkX, player.getBlockZ() >> 4, position.x, position.z);
        if (outside != null) {
            return StepResult.fail("beim Gleiten über die Grenze: " + outside);
        }
        if (run.waited(ctx) < 30) {
            return StepResult.pending();
        }
        double slid = position.z - run.other.z;
        if (slid < 1.0) {
            return StepResult.fail(String.format(Locale.ROOT, "gleitet nicht entlang der Grenze: z %.2f", slid));
        }
        if (FreecamSession.INSTANCE.edgeHits() == run.count) {
            return StepResult.fail(String.format(Locale.ROOT, "beim Gleiten nie an der Grenze gehalten: x %.2f", position.x));
        }
        run.notes.add(String.format(Locale.ROOT, "entlang der Grenze %.1f Blöcke geglitten bis x %.2f, %s", slid, position.x,
                FreecamEdgeCheck.rows(columns, playerChunkX, run.other.z, position.z)));
        FreecamSession.INSTANCE.clearTestInput();
        return StepResult.pass();
    }

    private static StepResult hoverSettled(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = site();
        if (!armAt(run, site.getX() + 2.5, site.getY() + 22.0, site.getZ() + 0.5, -90.0F)) {
            return notArmed(run);
        }
        arm(hover(), StabMode.ANGLE);
        run.count++;
        return until(run.count > SETTLE_TICKS, "einschwingen");
    }

    private static StepResult drift(SelfTest.StepContext ctx, Run run, boolean windy) {
        if (!FreecamSession.active() || !FreecamSession.INSTANCE.armed()) {
            return StepResult.fail("Freecam aus oder entschärft");
        }
        arm(hover(), StabMode.ANGLE);
        Vector3d position = FreecamSession.INSTANCE.position(new Vector3d());
        if (run.tick < 0) {
            Vector3d velocity = FreecamSession.INSTANCE.velocity(new Vector3d());
            FreecamSession.INSTANCE.testPush(0.0, velocity.y, 0.0);
            run.tick = ctx.tick();
            run.mark.set(position);
            return StepResult.pending();
        }
        if (ctx.tick() - run.tick < DRIFT_TICKS) {
            return StepResult.pending();
        }
        double drift = Math.hypot(position.x - run.mark.x, position.z - run.mark.z);
        run.notes.add(String.format(Locale.ROOT, "%s: Drift %.3f m in 5 s", windy ? "Wind 3" : "Wind 0", drift));
        if (windy ? drift <= 0.5 : drift >= 0.1) {
            return StepResult.fail(String.format(Locale.ROOT, "Drift %.3f m in 5 s (%s)", drift, windy ? "erwartet > 0,5" : "erwartet < 0,1"));
        }
        return StepResult.pass();
    }

    private static StepResult pressK(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            clickKey(UiClient.configuratorKey());
            return StepResult.pending();
        }
        return until(mc.gui.screen() instanceof ConfiguratorScreen screen && run.waited(ctx) > 3 && screen.activeTab() != null, "K öffnet nichts");
    }

    private static StepResult fourTabs(SelfTest.StepContext ctx, Run run) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.fail("K-Menü zu");
        }
        Set<String> ids = new TreeSet<>();
        for (ConfigTab tab : screen.tabs()) {
            ids.add(tab.id());
        }
        Set<String> expected = new TreeSet<>(List.of(FreecamTab.ID, RatesTab.ID, ReceiverTab.ID, ModesTab.ID));
        if (!ids.equals(expected)) {
            return StepResult.fail("Tabs " + ids + " statt " + expected);
        }
        if (!screen.freecam() || screen.activeTab() == null || !FreecamTab.ID.equals(screen.activeTab().id())) {
            return StepResult.fail("öffnet nicht auf dem Tab Freecam");
        }
        String name = screen.context().name().getString();
        String expectedName = FreecamClient.title(FreecamPreset.FIVE).getString();
        if (!name.equals(expectedName)) {
            return StepResult.fail("Kopfzeile „" + name + "“ statt „" + expectedName + "“");
        }
        if (screen.freecamStartButton() != null) {
            return StepResult.fail("Start-Knopf im Freecam-Menü");
        }
        run.notes.add("Tabs " + ids + ", Kopfzeile „" + name + "“");
        return StepResult.pass();
    }

    private static StepResult searchVisibleOnly(SelfTest.StepContext ctx, Run run) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.fail("K-Menü zu");
        }
        SettingsIndex index = SettingsIndex.build(screen);
        if (!index.failedTabs().isEmpty()) {
            return StepResult.fail("Suche kann Tabs nicht durchsuchen: " + index.failedTabs());
        }
        for (String query : List.of("Action-Cam", "Blackbox", "Videokanal", "Welt-Regel")) {
            List<SettingsSearch.Hit> hits = index.search(query, 8);
            for (SettingsSearch.Hit hit : hits) {
                String tab = hit.entry().tabId();
                if (!Set.of(FreecamTab.ID, RatesTab.ID, ReceiverTab.ID, ModesTab.ID).contains(tab)) {
                    return StepResult.fail("Suche „" + query + "“ findet unsichtbaren Tab " + tab);
                }
            }
        }
        List<SettingsSearch.Hit> rates = index.search("Rates", 8);
        if (rates.isEmpty()) {
            return StepResult.fail("Suche findet nicht einmal Rates");
        }
        run.notes.add("Suche nur in vier Tabs");
        return StepResult.pass();
    }

    private static StepResult cruise(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        if (!armAt(run, site().getX() + 2.5, site().getY() + 20.0, site().getZ() + 0.5, -90.0F)) {
            return notArmed(run);
        }
        arm(hover(), StabMode.ANGLE);
        FreecamSession.INSTANCE.testPush(4.0, 0.0, 0.0);
        return until(++run.count > 10, "fliegen");
    }

    private static StepResult presetSeven(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof FreecamTab tab)) {
            return StepResult.fail("Tab Freecam nicht offen");
        }
        DroneSim current = FreecamSession.INSTANCE.sim();
        if (current == null) {
            return StepResult.fail("Freecam aus");
        }
        if (run.tick < 0) {
            DroneSim before = current;
            run.ref = before;
            run.mark.set(FreecamSession.INSTANCE.position(new Vector3d()));
            before.getVelocity(run.other);
            run.value = run.other.length();
            if (run.value < 1.0) {
                return StepResult.fail(String.format(Locale.ROOT, "vor dem Wechsel nur %.2f m/s", run.value));
            }
            FlatButton chip = tab.chip(FreecamPreset.SEVEN);
            if (chip == null) {
                return StepResult.fail("Chip 7″ fehlt");
            }
            chip.press();
            run.tick = ctx.tick();
            return StepResult.pending();
        }
        DroneSim after = current;
        if (after == run.ref || FreecamSession.INSTANCE.preset() != FreecamPreset.SEVEN) {
            return new StepResult(StepResult.Status.PENDING, "Preset wechselt nicht");
        }
        double expected = AirframeSpec.of(FreecamPreset.SEVEN.build()).massKg;
        if (Math.abs(after.params().massKg - expected) > 1.0E-9) {
            return StepResult.fail("Masse " + after.params().massKg + " statt " + expected);
        }
        Vector3d position = new Vector3d();
        after.getPosition(position);
        Vector3d velocity = new Vector3d();
        after.getVelocity(velocity);
        if (position.distance(run.mark) > 1.0E-6 || velocity.distance(run.other) > 1.0E-6) {
            return StepResult.fail(String.format(Locale.ROOT, "Wechsel verschiebt Position %.4f oder Tempo %.4f", position.distance(run.mark),
                    velocity.distance(run.other)));
        }
        if (!FreecamPreset.SEVEN.key().equals(FreecamConfig.get().preset)) {
            return StepResult.fail("Preset nicht gespeichert");
        }
        if (!screen.context().build().equals(FreecamPreset.SEVEN.build())) {
            return new StepResult(StepResult.Status.PENDING, "K-Menü übernimmt das neue Preset nicht");
        }
        run.notes.add(String.format(Locale.ROOT, "7″ bei %.1f m/s: Masse %.3f kg, Position und Tempo gleich", run.value, expected));
        return StepResult.pass();
    }

    private static StepResult sevenFlies(SelfTest.StepContext ctx, Run run) {
        DroneSim sim = FreecamSession.INSTANCE.sim();
        if (sim == null || FreecamSession.INSTANCE.preset() != FreecamPreset.SEVEN) {
            return StepResult.fail("7″ nicht mehr aktiv");
        }
        if (run.waited(ctx) < 5) {
            return StepResult.pending();
        }
        if (!sim.telemetry().armed) {
            return StepResult.fail("7″ nach dem Wechsel unscharf");
        }
        return StepResult.pass();
    }

    private static StepResult presetFive(SelfTest.StepContext ctx, Run run) {
        FreecamSession.INSTANCE.clearTestInput();
        FreecamSession.INSTANCE.setPreset(FreecamPreset.FIVE);
        return until(FreecamSession.INSTANCE.preset() == FreecamPreset.FIVE && FreecamPreset.FIVE.key().equals(FreecamConfig.get().preset), "zurück auf 5″");
    }

    private static StepResult osdWithoutBattery(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        if (run.waited(ctx) < 10) {
            run.count = OsdHud.renderedFrames();
            return StepResult.pending();
        }
        if (OsdHud.renderedFrames() == run.count) {
            return new StepResult(StepResult.Status.PENDING, "OSD zeichnet nicht");
        }
        OsdData data = OsdHud.sampler().data();
        if (!data.active || !data.freecam) {
            return StepResult.fail("OSD-Daten nicht aus der Freecam");
        }
        String name = data.craftName;
        String expected = FreecamClient.osdName(FreecamPreset.FIVE).getString();
        if (!name.equals(expected)) {
            return StepResult.fail("Name „" + name + "“ statt „" + expected + "“");
        }
        List<String> drawn = new ArrayList<>();
        for (OsdElement element : OsdElement.values()) {
            if ((element.group() == OsdElement.Group.BATTERY || element.group() == OsdElement.Group.LINK) && OsdHud.drewLastFrame(element)) {
                drawn.add(element.key());
            }
        }
        if (!drawn.isEmpty()) {
            return StepResult.fail("Akku- oder Link-Elemente gezeichnet: " + drawn);
        }
        if (OsdHud.config().enabled(OsdElement.CRAFT_NAME) && !OsdHud.drewLastFrame(OsdElement.CRAFT_NAME)) {
            return StepResult.fail("Name nicht gezeichnet");
        }
        if (!OsdHud.hideVanilla()) {
            return StepResult.fail("Vanilla-HUD nicht ausgeblendet");
        }
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        OsdWarnings.collect(data, warnings);
        for (OsdWarnings.Warning warning : warnings) {
            if (warning == OsdWarnings.Warning.BATTERY_LOW || warning == OsdWarnings.Warning.BATTERY_CRITICAL
                    || warning == OsdWarnings.Warning.PACK_DEPLETED || warning == OsdWarnings.Warning.WEAK_SIGNAL) {
                return StepResult.fail("Akku- oder Link-Warnung in der Freecam: " + warning);
            }
        }
        run.notes.add("OSD-Name „" + name.toUpperCase(Locale.ROOT) + "“, keine Akku- und Link-Elemente");
        return StepResult.pass();
    }

    private static StepResult osdOff(SelfTest.StepContext ctx, Run run) {
        FreecamConfig config = FreecamConfig.get();
        if (run.waited(ctx) == 0) {
            config.osd = false;
            return StepResult.pending();
        }
        if (run.waited(ctx) == 3) {
            run.count = OsdHud.renderedFrames();
            return StepResult.pending();
        }
        if (run.waited(ctx) < 10) {
            return StepResult.pending();
        }
        boolean drawn = OsdHud.renderedFrames() != run.count || OsdHud.sampler().data().active;
        config.osd = true;
        FreecamConfig.save();
        if (drawn) {
            return StepResult.fail("OSD aus zeichnet weiter");
        }
        run.notes.add("OSD aus: nichts gezeichnet");
        return StepResult.pass();
    }

    static void hurt(MinecraftServer server) {
        ServerPlayer player = player(server);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        ServerLevel level = player.level();
        if (!player.hurtServer(level, level.damageSources().generic(), 1.0F)) {
            SERVER.set("Schaden am Server nicht angenommen");
        }
    }

    static void heal(MinecraftServer server) {
        ServerPlayer player = player(server);
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameType.CREATIVE);
    }

    static StepResult ended(Run run, FreecamRules.End reason) {
        if (FreecamSession.active()) {
            return new StepResult(StepResult.Status.PENDING, "Freecam läuft weiter");
        }
        if (FreecamSession.INSTANCE.lastEnd() != reason) {
            return StepResult.fail("Ende mit " + FreecamSession.INSTANCE.lastEnd() + " statt " + reason);
        }
        run.notes.add("Ende mit Grund " + reason);
        return StepResult.pass();
    }

    private static StepResult armInAir(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        if (!armAt(run, site().getX() + 2.5, site().getY() + 10.0, site().getZ() + 0.5, -90.0F)) {
            return notArmed(run);
        }
        arm(hover(), StabMode.ANGLE);
        return StepResult.pass();
    }

    private static StepResult ruleOffInFlight(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            run.future = ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 0));
            return StepResult.pending();
        }
        boolean off = !ClientWorldRules.INSTANCE.on(WorldRuleSpecs.ALLOW_FREECAM);
        if (off && run.tick < 0) {
            run.tick = ctx.tick();
        }
        if (FreecamSession.active()) {
            if (run.tick >= 0 && ctx.tick() - run.tick > 1) {
                return StepResult.fail("Freecam läuft " + (ctx.tick() - run.tick) + " Ticks nach Regel aus weiter");
            }
            arm(hover(), StabMode.ANGLE);
            return new StepResult(StepResult.Status.PENDING, "Regel aus kommt nicht an");
        }
        FreecamSession.INSTANCE.clearTestInput();
        if (FreecamSession.INSTANCE.lastEnd() != FreecamRules.End.RULE_OFF) {
            return StepResult.fail("Ende mit " + FreecamSession.INSTANCE.lastEnd() + " statt RULE_OFF");
        }
        int ticks = run.tick < 0 ? 0 : ctx.tick() - run.tick;
        run.notes.add("Regel aus im Flug: Ende nach " + ticks + " Tick(s)");
        return StepResult.pass();
    }
}

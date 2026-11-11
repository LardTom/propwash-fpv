package de.lardtom.propwash.osd.addon.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAccessories;
import de.lardtom.propwash.dev.example.ExampleOsd;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.osd.client.OsdConfigTab;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.osd.client.RadioHud;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.ChromeLayout;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AddonOsdSelfTests {
    public static final String GROUP = "addon-api-17: A2 – ";
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
    private static final int DRAG_COLS = 3;
    private static final int DRAG_ROWS = 2;
    private static final int ROUNDS = 12;
    private static final int LOW_ROUNDS = 2;
    private static final double FLIGHT_HEIGHT = 3.0;
    private static final int ARM_TICKS = 20;
    private static final int TIMEOUT_TICKS = 400;
    private static final int WINDOW_TICKS = 80;
    private static final String GERMAN = "de_de";
    private static final String PHASE = "a2_phase";
    private static final String SINCE = "a2_since";
    private static final String FUTURE = "a2_future";
    private static final String RESULT = "a2_result";
    private static final String TAB = "a2_tab";
    private static final String EXTRACTED = "a2_extracted";
    private static final String FRAMES = "a2_frames";
    private static final int FRESH_TICKS = 5;
    private static final int[][] EDITOR_SIZES = {{854, 480, 2}, {320, 240, 3}};
    private static final String NARROW = "a2_narrow";
    private static final String NARROW_WIDGETS = "a2_narrow_widgets";
    private static final int SMALLEST_GUI = 320;
    private static final int LONGEST_TITLE = 1000;
    private static final int NARROW_HEIGHT = 172;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static volatile double groundY;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;
    private static volatile int @Nullable [] edited;
    private static @Nullable OsdConfig savedConfig;
    private static int @Nullable [] savedWindow;

    private AddonOsdSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Element „Munition“ in der Gruppe Add-ons des OSD-Editors: Vorschau, Ziehen, Pfeiltasten, Leertaste, Zurücksetzen",
                200, AddonOsdSelfTests::editorStep);
        SelfTest.clientStep(GROUP + "im Flug an der verschobenen Stelle sichtbar, Munition aus dem Werfer, Warnung GHOST im Warnfeld", 1500,
                AddonOsdSelfTests::flightStep);
        SelfTest.clientStep(GROUP + "im Sichtflug ohne Brille: Munition an ihrer Stelle im ganzen Bild, OsdContext meldet Sichtflug, Funk-HUD bleibt", 1500,
                AddonOsdSelfTests::sightStep);
        SelfTest.clientStep(GROUP + "OSD-Editor in der schmalsten Tab-Breite bei GUI 320×240 (Seitenleiste mit langem Add-on-Tab): eingebaute Namen, "
                + "Scroll-Hinweis und Bedienelemente nicht gekürzt", 200, AddonOsdSelfTests::narrowStep);
        if (Screenshots.enabled()) {
            registerScreenshots();
        }
        AddonOsdBannerSelfTests.register();
    }

    private static void registerScreenshots() {
        shots("en", "");
        String back = GROUP + "Bildschirmfotos Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "Bildschirmfotos Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        shots("de", " deutsch");
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
    }

    private static void shots(String language, String label) {
        for (int[] size : EDITOR_SIZES) {
            String dims = size[0] + "x" + size[1];
            Screenshots.step(GROUP + "Bildschirmfoto OSD-Editor mit Gruppe Add-ons" + label + " " + dims, "osd_addon_editor_" + language + "_" + dims, 6,
                    new EditorScene(size[0], size[1], size[2]));
        }
        Screenshots.step(GROUP + "Bildschirmfoto im Flug mit Munition und GHOST" + label, "osd_addon_flight_" + language, 8, new FlightScene());
        Screenshots.step(GROUP + "Bildschirmfoto im Sichtflug mit Munition" + label, "osd_addon_line_of_sight_" + language, 8, new SightScene());
    }

    static @Nullable OsdElementSpec ammoSpec() {
        for (OsdElementSpec spec : Api17Registries.Client.osdElements()) {
            if (spec.id().equals(ExampleOsd.AMMO)) {
                return spec;
            }
        }
        return null;
    }

    private static StepResult editorStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            if (!JoinTestEnv.exampleAddon()) {
                ctx.log("Beispiel-Add-on aus, nichts zu prüfen");
                return StepResult.pass();
            }
            OsdElementSpec spec = ammoSpec();
            if (spec == null) {
                return StepResult.fail("Element " + ExampleOsd.AMMO + " ist nicht angemeldet");
            }
            List<String> errors = new ArrayList<>();
            if (Api17Registries.Client.osdWarnings().stream().noneMatch(warning -> warning.id().equals(ExampleOsd.GHOST))) {
                errors.add("Warnung " + ExampleOsd.GHOST + " ist nicht angemeldet");
            }
            for (String key : List.of(OsdConfigTab.ADDON_GROUP_KEY, "propwash.osd.editor.addon_source", "propwash.osd.editor.addon_failed",
                    spec.nameKey())) {
                if (!Language.getInstance().has(key)) {
                    errors.add("Text fehlt: " + key);
                }
            }
            if (!errors.isEmpty()) {
                return StepResult.fail(String.join("; ", errors));
            }
            OsdHud.sampler().setOverride(null);
            FakeHost host = new FakeHost(8, 28, 510, 302);
            OsdConfigTab tab = host.bind(new OsdConfigTab());
            tab.resetDefaults();
            OsdConfig.Slot slot = AddonOsdKeys.slot(tab.working(), spec);
            if (slot.enabled || slot.col != ExampleOsd.COLUMN || slot.row != ExampleOsd.ROW) {
                return StepResult.fail("Standardplatz " + slot.col + "/" + slot.row + (slot.enabled ? " an" : " aus") + " statt " + ExampleOsd.COLUMN
                        + "/" + ExampleOsd.ROW + " aus");
            }
            tab.toggleAddon(ExampleOsd.AMMO);
            if (!slot.enabled) {
                return StepResult.fail("Haken setzt das Element nicht an");
            }
            AtomicBoolean extracted = new AtomicBoolean();
            OsdHud.probeNextFrame(graphics -> {
                tab.extract(graphics, 0, 0, 0.0F);
                extracted.set(true);
            });
            state.put(TAB, tab);
            state.put(EXTRACTED, extracted);
            return StepResult.pending();
        }
        if (!state.containsKey(TAB)) {
            return StepResult.pass();
        }
        OsdConfigTab tab = (OsdConfigTab) state.get(TAB);
        if (!((AtomicBoolean) state.get(EXTRACTED)).get()) {
            return ctx.tick() > 150 ? StepResult.fail("Editor-Vorschau wurde nicht gezeichnet") : StepResult.pending();
        }
        OsdElementSpec spec = ammoSpec();
        AddonOsdLayer layer = tab.addons();
        float[] b = layer.bounds(ExampleOsd.AMMO);
        if (spec == null || b == null || !ExampleOsd.PREVIEW.equals(layer.text(ExampleOsd.AMMO))) {
            return StepResult.fail("Vorschau zeigt „" + layer.text(ExampleOsd.AMMO) + "“ statt „" + ExampleOsd.PREVIEW + "“");
        }
        OsdGrid grid = tab.previewGrid();
        if (grid == null) {
            return StepResult.fail("Editor ohne Vorschau");
        }
        double startX = (b[0] + b[2]) * 0.5;
        double startY = (b[1] + b[3]) * 0.5;
        if (!tab.mouseClicked(new MouseButtonEvent(startX, startY, LEFT), false) || !ExampleOsd.AMMO.equals(tab.selectedAddon()) || !tab.isDragging()
                || tab.selected() != null) {
            return StepResult.fail("Klick auf die Munition wählt sie nicht aus oder startet kein Ziehen");
        }
        double endX = startX + grid.cellWidth() * DRAG_COLS;
        double endY = startY + grid.cellHeight() * DRAG_ROWS;
        tab.mouseDragged(new MouseButtonEvent(endX, endY, LEFT), endX - startX, endY - startY);
        tab.mouseReleased(new MouseButtonEvent(endX, endY, LEFT));
        OsdConfig.Slot slot = AddonOsdKeys.slot(tab.working(), spec);
        int col = ExampleOsd.COLUMN + DRAG_COLS;
        int row = ExampleOsd.ROW + DRAG_ROWS;
        if (slot.col != col || slot.row != row || tab.isDragging()) {
            return StepResult.fail("Ziehen verschiebt nach " + slot.col + "/" + slot.row + " statt " + col + "/" + row);
        }
        tab.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
        if (slot.col != col + 1) {
            return StepResult.fail("Pfeil rechts verschiebt nicht um eine Spalte: " + slot.col);
        }
        tab.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, 0, 0));
        tab.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 32, 0));
        if (slot.enabled) {
            return StepResult.fail("Leertaste schaltet die Munition nicht aus");
        }
        tab.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 32, 0));
        OsdConfig saved = tab.working().copy();
        OsdConfig.Slot kept = saved.elements.get(ExampleOsd.AMMO.toString());
        if (kept == null || kept.col != col || kept.row != row || !kept.enabled) {
            return StepResult.fail("gespeicherte Kopie verliert die Munition: " + (kept == null ? "fehlt" : kept.col + "/" + kept.row));
        }
        edited = new int[]{col, row};
        tab.resetLayout();
        if (slot.enabled || slot.col != ExampleOsd.COLUMN || slot.row != ExampleOsd.ROW) {
            return StepResult.fail("Zurücksetzen stellt den Standardplatz nicht her: " + slot.col + "/" + slot.row);
        }
        ctx.log("Munition in der Gruppe Add-ons: Vorschau „" + ExampleOsd.PREVIEW + "“, nach " + col + "/" + row + " gezogen, Zurücksetzen auf "
                + ExampleOsd.COLUMN + "/" + ExampleOsd.ROW);
        return StepResult.pass();
    }

    static int narrowestTab() {
        int sidebar = ChromeLayout.sidebarWidth(SMALLEST_GUI, LONGEST_TITLE);
        return SMALLEST_GUI - 6 - (sidebar + 8) - Metrics.CONTENT_INSET;
    }

    @SuppressWarnings("unchecked")
    private static StepResult narrowStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        int width = narrowestTab();
        if (ctx.tick() == 0) {
            OsdHud.sampler().setOverride(null);
            FakeHost host = new FakeHost(8, 28, width, NARROW_HEIGHT);
            OsdConfigTab tab = host.bind(new OsdConfigTab());
            AtomicReference<Map<String, String>> cuts = new AtomicReference<>();
            int[] drawn = new int[2];
            OsdHud.probeNextFrame(graphics -> {
                Map<String, String> found = new LinkedHashMap<>();
                ThemeStats.trackCuts(true);
                try {
                    double listX = 8 + width - 6;
                    double listY = 28 + 6;
                    for (int page = 0; page < 2; page++) {
                        scrollList(tab, listX, listY, 1.0);
                        do {
                            tab.extract(graphics, -1, -1, 0.0F);
                            drawn[0]++;
                        } while (tab.mouseScrolled(listX, listY, 0.0, -1.0));
                        scrollList(tab, listX, listY, 1.0);
                        for (AbstractWidget widget : List.copyOf(host.widgets)) {
                            widget.extractRenderState(graphics, -1, -1, 0.0F);
                            drawn[1]++;
                        }
                        found.putAll(ThemeStats.takeCuts());
                        tab.switchPage();
                    }
                } catch (RuntimeException e) {
                    found.put("Ausnahme", String.valueOf(e));
                } finally {
                    ThemeStats.trackCuts(false);
                    cuts.set(found);
                }
            });
            state.put(NARROW, cuts);
            state.put(NARROW_WIDGETS, drawn);
            return StepResult.pending();
        }
        Map<String, String> cuts = ((AtomicReference<Map<String, String>>) state.get(NARROW)).get();
        if (cuts == null) {
            return ctx.tick() > 150 ? StepResult.fail("Editor wurde nicht gezeichnet") : StepResult.pending();
        }
        if (!cuts.isEmpty()) {
            return StepResult.fail("bei " + width + " px Tab-Breite gekürzt: " + cuts);
        }
        int[] drawn = (int[]) state.get(NARROW_WIDGETS);
        ctx.log("OSD-Editor bei " + width + " px Tab-Breite: Liste in " + drawn[0] + " Scrollstellen und " + drawn[1]
                + " Widgets auf beiden Seiten ohne Kürzung");
        return StepResult.pass();
    }

    private static void scrollList(OsdConfigTab tab, double x, double y, double direction) {
        int steps = 0;
        while (steps < 200 && tab.mouseScrolled(x, y, 0.0, direction)) {
            steps++;
        }
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        if (!JoinTestEnv.exampleAddon()) {
            ctx.log("Beispiel-Add-on aus, nichts zu prüfen");
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        String phase = phase(ctx, "spawn");
        int since = since(ctx);
        int[] place = edited != null ? edited : new int[]{ExampleOsd.COLUMN + DRAG_COLS, ExampleOsd.ROW + DRAG_ROWS};
        if (!phase.equals("spawn") && !phase.equals("fpv") && !phase.equals("cleanup") && !flying()) {
            return cleanup(ctx, StepResult.fail("FPV-Sitzung endete in Phase " + phase));
        }
        switch (phase) {
            case "spawn" -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                showAmmo(place);
                ExampleOsd.ghost(false);
                state.put(FUTURE, ctx.onServer(server -> spawnAndFly(server, ROUNDS)));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                StepResult started = started(ctx, since);
                return started.status() == StepResult.Status.PASS ? next(ctx, "arm") : started;
            }
            case "arm" -> {
                arm(since);
                return since >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
            }
            case "climb" -> {
                hover();
                if (height() > FLIGHT_HEIGHT - 0.8) {
                    return next(ctx, "ammo");
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Drohne steigt nicht: Höhe %.2f m", height())))
                        : StepResult.pending();
            }
            case "ammo" -> {
                hover();
                String problem = ammoProblem(mc, ExampleOsd.LABEL + ROUNDS, OsdTone.NORMAL, place);
                if (problem == null) {
                    problem = contextProblem(mc, true);
                }
                if (problem == null) {
                    ctx.log(String.format(Locale.ROOT, "Munition im Flug in %.1f m Höhe bei Spalte %d, Zeile %d: „%s“", height(), place[0], place[1],
                            ExampleOsd.LABEL + ROUNDS));
                    ExampleOsd.ghost(true);
                    return next(ctx, "ghost");
                }
                return since > 200 ? cleanup(ctx, StepResult.fail(problem)) : StepResult.pending();
            }
            case "ghost" -> {
                hover();
                AddonOsdLayer live = AddonOsdLayer.live();
                OsdText shown = live == null ? null : live.shownWarning();
                if (shown != null && ExampleOsd.GHOST_TEXT.equals(shown.text()) && shown.tone() == OsdTone.CAUTION
                        && OsdHud.drewLastFrame(OsdElement.WARNINGS)) {
                    ctx.log("Warnung GHOST nach " + since + " Ticks im Warnfeld");
                    ExampleOsd.ghost(false);
                    return next(ctx, "unghost");
                }
                return since > 300 ? cleanup(ctx, StepResult.fail("GHOST erscheint nicht im Warnfeld: " + (live == null ? "kein OSD" : live.warnings())))
                        : StepResult.pending();
            }
            case "unghost" -> {
                hover();
                AddonOsdLayer live = AddonOsdLayer.live();
                if (since > 2 && live != null && live.warnings().stream().noneMatch(warning -> ExampleOsd.GHOST_TEXT.equals(warning.text()))) {
                    state.put(FUTURE, ctx.onServer(server -> load(server, LOW_ROUNDS)));
                    return next(ctx, "low");
                }
                return since > 100 ? cleanup(ctx, StepResult.fail("GHOST bleibt nach dem Ausschalten")) : StepResult.pending();
            }
            case "low" -> {
                hover();
                String problem = ammoProblem(mc, ExampleOsd.LABEL + LOW_ROUNDS, OsdTone.CAUTION, place);
                if (problem == null) {
                    ctx.log("nach dem Entladen „" + ExampleOsd.LABEL + LOW_ROUNDS + "“ in Gelb");
                    return cleanup(ctx, StepResult.pass());
                }
                return since > 200 ? cleanup(ctx, StepResult.fail(problem)) : StepResult.pending();
            }
            case "cleanup" -> {
                return finishCleanup(ctx);
            }
            default -> {
                return cleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static StepResult sightStep(SelfTest.StepContext ctx) {
        if (!JoinTestEnv.exampleAddon()) {
            ctx.log("Beispiel-Add-on aus, nichts zu prüfen");
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        String phase = phase(ctx, "spawn");
        int since = since(ctx);
        int[] place = {ExampleOsd.COLUMN + DRAG_COLS, ExampleOsd.ROW + DRAG_ROWS};
        if (!phase.equals("spawn") && !phase.equals("sight") && !phase.equals("cleanup") && !inSight()) {
            return cleanup(ctx, StepResult.fail("Sichtflug endete in Phase " + phase));
        }
        switch (phase) {
            case "spawn" -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                showAmmo(place);
                ExampleOsd.ghost(false);
                state.put(FUTURE, ctx.onServer(server -> spawnAndFly(server, ROUNDS, PilotMode.LINE_OF_SIGHT)));
                return next(ctx, "sight");
            }
            case "sight" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return cleanup(ctx, StepResult.fail("Start: " + future.exceptionNow().getMessage()));
                }
                if (!future.isDone() || !inSight()) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("keine Sichtflug-Sitzung")) : StepResult.pending();
                }
                return next(ctx, "arm");
            }
            case "arm" -> {
                arm(since);
                return since >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
            }
            case "climb" -> {
                hover();
                if (height() > FLIGHT_HEIGHT - 0.8) {
                    state.put(FRAMES, RadioHud.renderedFrames());
                    return next(ctx, "ammo");
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Drohne steigt nicht: Höhe %.2f m", height())))
                        : StepResult.pending();
            }
            case "ammo" -> {
                hover();
                String problem = sightProblem(mc, place, RadioHud.renderedFrames() - (int) state.get(FRAMES));
                if (problem == null) {
                    AddonOsdLayer live = AddonOsdLayer.live();
                    float[] b = live == null ? null : live.bounds(ExampleOsd.AMMO);
                    ctx.log(String.format(Locale.ROOT, "Sichtflug in %.1f m Höhe: „%s“ bei Spalte %d, Zeile %d im ganzen Bild (x %.1f, y %.1f), "
                                    + "OsdContext fpv=false, lineOfSight=true, Funk-HUD %d Bilder", height(), ExampleOsd.LABEL + ROUNDS, place[0], place[1],
                            b == null ? Float.NaN : b[0], b == null ? Float.NaN : b[1], RadioHud.renderedFrames() - (int) state.get(FRAMES)));
                    return cleanup(ctx, StepResult.pass());
                }
                return since > 200 ? cleanup(ctx, StepResult.fail(problem)) : StepResult.pending();
            }
            case "cleanup" -> {
                return finishCleanup(ctx);
            }
            default -> {
                return cleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static @Nullable String sightProblem(Minecraft mc, int[] place, int radioFrames) {
        if (!RadioHud.active() || radioFrames < 3) {
            return "Funk-HUD im Sichtflug nicht gezeichnet (" + radioFrames + " Bilder)";
        }
        if (OsdHud.sampler().data().active) {
            return "Brillen-OSD im Sichtflug aktiv";
        }
        String context = contextProblem(mc, false);
        if (context != null) {
            return context;
        }
        AddonOsdLayer live = AddonOsdLayer.live();
        if (live == null || !live.drawn(ExampleOsd.AMMO)) {
            return "Munition wird im Sichtflug nicht gezeichnet";
        }
        if (!(ExampleOsd.LABEL + ROUNDS).equals(live.text(ExampleOsd.AMMO))) {
            return "Munition zeigt im Sichtflug „" + live.text(ExampleOsd.AMMO) + "“ statt „" + ExampleOsd.LABEL + ROUNDS + "“";
        }
        float[] b = live.bounds(ExampleOsd.AMMO);
        OsdGrid grid = OsdGrid.screen(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        float anchor = grid.anchorX(place[0], OsdElement.Align.LEFT);
        float middle = (b[1] + b[3]) * 0.5F;
        if (Math.abs(b[0] - anchor) > grid.cellWidth() * 0.5F || middle < grid.cellTop(place[1]) || middle > grid.cellTop(place[1] + 1)) {
            return String.format(Locale.ROOT, "Munition im Sichtflug bei x %.1f, Mitte y %.1f statt Spalte %d, Zeile %d im ganzen Bild (x %.1f, y %.1f bis %.1f)",
                    b[0], middle, place[0], place[1], anchor, grid.cellTop(place[1]), grid.cellTop(place[1] + 1));
        }
        return null;
    }

    private static @Nullable String contextProblem(Minecraft mc, boolean fpv) {
        AddonOsdLayer live = AddonOsdLayer.live();
        OsdContext context = live == null ? null : live.context();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        if (context == null || now - context.gameTime() >= FRESH_TICKS) {
            return "Propwash ruft die Add-on-Elemente im " + (fpv ? "FPV" : "Sichtflug") + " nicht auf (letzter Kontext "
                    + (context == null ? "nie" : (now - context.gameTime()) + " Ticks her mit fpv=" + context.fpv() + ", lineOfSight=" + context.lineOfSight())
                    + ")";
        }
        Entity drone = context.drone().orElse(null);
        if (context.fpv() != fpv || context.lineOfSight() == fpv || context.preview() || drone == null || drone.getId() != DRONE.get()) {
            return "OsdContext im " + (fpv ? "FPV" : "Sichtflug") + ": fpv=" + context.fpv() + ", lineOfSight=" + context.lineOfSight() + ", Vorschau="
                    + context.preview() + ", Drohne " + (drone == null ? "keine" : drone.getId()) + " statt fpv=" + fpv + ", lineOfSight=" + !fpv
                    + ", Drohne " + DRONE.get();
        }
        return null;
    }

    private static StepResult started(SelfTest.StepContext ctx, int since) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return cleanup(ctx, StepResult.fail("Start: " + future.exceptionNow().getMessage()));
        }
        if (!future.isDone() || !flying() || !VideoState.INSTANCE.active()) {
            return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static @Nullable String ammoProblem(Minecraft mc, String text, OsdTone tone, int[] place) {
        AddonOsdLayer live = AddonOsdLayer.live();
        if (live == null || !live.drawn(ExampleOsd.AMMO)) {
            return "Munition wird im Flug nicht gezeichnet";
        }
        if (!text.equals(live.text(ExampleOsd.AMMO))) {
            return "Munition zeigt „" + live.text(ExampleOsd.AMMO) + "“ statt „" + text + "“";
        }
        AddonOsdLayer.Shown entry = null;
        for (AddonOsdLayer.Shown shown : live.shown()) {
            if (shown.spec().id().equals(ExampleOsd.AMMO)) {
                entry = shown;
            }
        }
        if (entry == null || entry.tone() != tone) {
            return "Munition im Ton " + (entry == null ? "?" : entry.tone()) + " statt " + tone;
        }
        float[] b = live.bounds(ExampleOsd.AMMO);
        OsdGrid grid = OsdHud.grid(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        float anchor = grid.anchorX(place[0], OsdElement.Align.LEFT);
        float middle = (b[1] + b[3]) * 0.5F;
        if (Math.abs(b[0] - anchor) > grid.cellWidth() * 0.5F || middle < grid.cellTop(place[1]) || middle > grid.cellTop(place[1] + 1)) {
            return String.format(Locale.ROOT, "Munition bei x %.1f, Mitte y %.1f statt Spalte %d, Zeile %d (x %.1f, y %.1f bis %.1f)", b[0], middle,
                    place[0], place[1], anchor, grid.cellTop(place[1]), grid.cellTop(place[1] + 1));
        }
        return null;
    }

    static void showAmmo(int[] place) {
        OsdElementSpec spec = ammoSpec();
        if (spec == null) {
            return;
        }
        OsdConfig current = OsdClient.CONFIG.get();
        if (savedConfig == null) {
            savedConfig = current.copy();
        }
        OsdConfig config = current.copy();
        OsdConfig.Slot slot = AddonOsdKeys.slot(config, spec);
        slot.enabled = true;
        slot.col = place[0];
        slot.row = place[1];
        OsdClient.CONFIG.set(config);
    }

    static void restoreOsd() {
        OsdConfig saved = savedConfig;
        if (saved != null) {
            OsdClient.CONFIG.set(saved);
            savedConfig = null;
        }
        ExampleOsd.ghost(false);
    }

    private static String phase(SelfTest.StepContext ctx, String initial) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            state.put(PHASE, initial);
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
        return StepResult.pending();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RESULT)) {
            state.put(RESULT, result);
            PilotSession.INSTANCE.clearTestInput();
            restoreOsd();
            state.put(FUTURE, ctx.onServer(AddonOsdSelfTests::release));
            return next(ctx, "cleanup");
        }
        return StepResult.pending();
    }

    private static StepResult finishCleanup(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future != null && !future.isDone() || PilotSession.INSTANCE.isActive()) {
            return since(ctx) > 200 ? StepResult.fail("Aufräumen hängt: FPV " + PilotSession.INSTANCE.isActive()) : StepResult.pending();
        }
        return (StepResult) state.get(RESULT);
    }

    static boolean flying() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isFpv() && session.sim() != null && drone != null && drone.getId() == DRONE.get();
    }

    static boolean inSight() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isLineOfSight() && session.sim() != null && drone != null && drone.getId() == DRONE.get();
    }

    static double height() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null ? 0.0 : drone.getY() - groundY;
    }

    static void arm(int since) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= ARM_TICKS / 2, StabMode.ANGLE);
    }

    static void hover() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        Telemetry t = sim == null ? null : sim.telemetry();
        double hover = t == null ? 0.3 : Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.clamp(1.2 * (FLIGHT_HEIGHT - height()), -3.0, 3.0);
        float throttle = (float) Math.clamp(hover + 0.06 * (wanted - vs), 0.05, 0.9);
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
    }

    static void spawnAndFly(MinecraftServer server, int rounds) {
        spawnAndFly(server, rounds, PilotMode.FPV);
    }

    static void spawnAndFly(MinecraftServer server, int rounds, PilotMode mode) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerPlayer player = players.getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discard(server);
        ServerLevel level = player.level();
        AccessoryType launcher = AccessoryType.byId(ExampleAccessories.LAUNCHER)
                .orElseThrow(() -> new IllegalStateException("Werfer des Beispiel-Add-ons fehlt"));
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE.withAccessory(launcher));
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        Vec3 feet = player.position();
        groundY = feet.y;
        Vec3 pos = new Vec3(feet.x + 2.0, feet.y + 0.1, feet.z - 4.0);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        drone.setCustomName(Component.literal("Munition"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        drone.resetTo(pos, new Quaternionf());
        DRONE.set(drone.getId());
        load(drone, rounds);
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        if (previousHand == null) {
            previousHand = player.getMainHandItem().copy();
        }
        if (mode == PilotMode.FPV) {
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new)
                    .orElseThrow(() -> new IllegalStateException("Kreativ-Brille nicht registriert"));
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
        } else {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, mode);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException((mode == PilotMode.FPV ? "FPV" : "Sichtflug") + "-Start verweigert: " + result);
        }
    }

    static void load(MinecraftServer server, int rounds) {
        QuadcopterEntity drone = find(server);
        if (drone == null) {
            throw new IllegalStateException("Test-Drohne fehlt");
        }
        load(drone, rounds);
    }

    private static void load(QuadcopterEntity drone, int rounds) {
        AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                .orElseThrow(() -> new IllegalStateException("kein Inventar am Werfer"));
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.set(slot, slot == 0 ? new ItemStack(Items.ARROW, rounds) : ItemStack.EMPTY);
        }
    }

    private static @Nullable QuadcopterEntity find(MinecraftServer server) {
        int id = DRONE.get();
        if (id < 0) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static void discard(MinecraftServer server) {
        QuadcopterEntity drone = find(server);
        if (drone != null) {
            drone.discard();
        }
        DRONE.set(-1);
    }

    static void release(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ItemStack head = previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        discard(server);
        previousHead = null;
        previousHand = null;
    }

    private static boolean resize(SelfTest.StepContext ctx, int width, int height, int scale, int[] since) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int frameWidth = width * scale;
        int frameHeight = height * scale;
        if (since[0] < 0) {
            if (savedWindow == null) {
                savedWindow = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
            }
            float density = Math.max(1.0F, window.getPixelDensity());
            window.setWindowed(Math.round(frameWidth / density), Math.round(frameHeight / density));
            since[0] = ctx.tick();
            return false;
        }
        boolean sized = window.getWidth() == frameWidth && window.getHeight() == frameHeight;
        if (!sized && ctx.tick() - since[0] < WINDOW_TICKS) {
            return false;
        }
        mc.options.guiScale().set(scale);
        mc.resizeGui();
        return true;
    }

    private static void restoreWindow() {
        int[] saved = savedWindow;
        if (saved != null) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(saved[2]);
            mc.getWindow().setWindowed(saved[0], saved[1]);
            mc.resizeGui();
            savedWindow = null;
        }
    }

    private static final class EditorScene implements Screenshots.Scene {
        private final int width;
        private final int height;
        private final int scale;
        private final int[] since = {-1};
        private int stage;

        EditorScene(int width, int height, int scale) {
            this.width = width;
            this.height = height;
            this.scale = scale;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!JoinTestEnv.exampleAddon() || ammoSpec() == null) {
                return StepResult.fail("Beispiel-Add-on ohne Element Munition");
            }
            if (this.stage == 0) {
                mc.gui.setScreen(null);
                if (!resize(ctx, this.width, this.height, this.scale, this.since)) {
                    return StepResult.pending();
                }
                Window window = mc.getWindow();
                if (window.getGuiScaledWidth() != this.width || window.getGuiScaledHeight() != this.height) {
                    return StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " statt " + this.width + "x" + this.height);
                }
                if (savedConfig == null) {
                    savedConfig = OsdClient.CONFIG.get().copy();
                }
                ConfiguratorScreen.open(null, OsdConfigTab.ID);
                this.stage = 1;
                return StepResult.pending();
            }
            ConfiguratorScreen screen = ConfiguratorScreen.active();
            if (screen == null || !(screen.activeTab() instanceof OsdConfigTab osd)) {
                return ctx.tick() - this.since[0] > 200 ? StepResult.fail("Konfigurator mit Tab OSD öffnet nicht") : StepResult.pending();
            }
            OsdElementSpec spec = ammoSpec();
            if (spec != null && !AddonOsdKeys.slot(osd.working(), spec).enabled) {
                osd.toggleAddon(ExampleOsd.AMMO);
            }
            osd.selectAddon(ExampleOsd.AMMO);
            Screenshots.parkMouse(mc);
            Screenshots.hideMarker(true);
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            Screenshots.hideMarker(false);
            restoreOsd();
            restoreWindow();
            this.stage = 0;
            this.since[0] = -1;
        }
    }

    private static final class FlightScene implements Screenshots.Scene {
        private final int[] since = {-1};

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            String phase = phase(ctx, "window");
            int elapsed = since(ctx);
            switch (phase) {
                case "window" -> {
                    if (!resize(ctx, Screenshots.TARGET_WIDTH / Screenshots.GUI_SCALE, Screenshots.TARGET_HEIGHT / Screenshots.GUI_SCALE,
                            Screenshots.GUI_SCALE, this.since)) {
                        return StepResult.pending();
                    }
                    showAmmo(new int[]{ExampleOsd.COLUMN, ExampleOsd.ROW});
                    ExampleOsd.ghost(true);
                    state.put(FUTURE, ctx.onServer(server -> spawnAndFly(server, ROUNDS)));
                    return next(ctx, "fpv");
                }
                case "fpv" -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (future.isCompletedExceptionally()) {
                        return StepResult.fail("Start: " + future.exceptionNow().getMessage());
                    }
                    if (!future.isDone() || !flying() || !VideoState.INSTANCE.active()) {
                        return elapsed > TIMEOUT_TICKS ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                    }
                    return next(ctx, "arm");
                }
                case "arm" -> {
                    arm(elapsed);
                    return elapsed >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
                }
                case "climb" -> {
                    hover();
                    AddonOsdLayer live = AddonOsdLayer.live();
                    OsdText warning = live == null ? null : live.shownWarning();
                    if (height() > FLIGHT_HEIGHT - 0.5 && live != null && live.drawn(ExampleOsd.AMMO) && warning != null
                            && ExampleOsd.GHOST_TEXT.equals(warning.text())) {
                        Screenshots.hideMarker(true);
                        return StepResult.pass();
                    }
                    return elapsed > TIMEOUT_TICKS ? StepResult.fail("Munition oder GHOST fehlen im Flug") : StepResult.pending();
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + phase);
                }
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            hover();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            Screenshots.hideMarker(false);
            restoreOsd();
            ctx.onServer(AddonOsdSelfTests::release);
            restoreWindow();
            this.since[0] = -1;
        }
    }

    private static final class SightScene implements Screenshots.Scene {
        private final int[] since = {-1};

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            Minecraft mc = Minecraft.getInstance();
            String phase = phase(ctx, "window");
            int elapsed = since(ctx);
            switch (phase) {
                case "window" -> {
                    if (!resize(ctx, Screenshots.TARGET_WIDTH / Screenshots.GUI_SCALE, Screenshots.TARGET_HEIGHT / Screenshots.GUI_SCALE,
                            Screenshots.GUI_SCALE, this.since)) {
                        return StepResult.pending();
                    }
                    showAmmo(new int[]{ExampleOsd.COLUMN, ExampleOsd.ROW});
                    ExampleOsd.ghost(false);
                    state.put(FUTURE, ctx.onServer(server -> spawnAndFly(server, ROUNDS, PilotMode.LINE_OF_SIGHT)));
                    return next(ctx, "sight");
                }
                case "sight" -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (future.isCompletedExceptionally()) {
                        return StepResult.fail("Start: " + future.exceptionNow().getMessage());
                    }
                    if (!future.isDone() || !inSight()) {
                        return elapsed > TIMEOUT_TICKS ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
                    }
                    return next(ctx, "arm");
                }
                case "arm" -> {
                    arm(elapsed);
                    return elapsed >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
                }
                case "climb" -> {
                    hover();
                    AddonOsdLayer live = AddonOsdLayer.live();
                    if (height() > FLIGHT_HEIGHT - 0.5 && live != null && live.drawn(ExampleOsd.AMMO) && contextProblem(mc, false) == null) {
                        Screenshots.hideMarker(true);
                        return StepResult.pass();
                    }
                    return elapsed > TIMEOUT_TICKS ? StepResult.fail("Munition fehlt im Sichtflug") : StepResult.pending();
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + phase);
                }
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            hover();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            Screenshots.hideMarker(false);
            restoreOsd();
            ctx.onServer(AddonOsdSelfTests::release);
            restoreWindow();
            this.since[0] = -1;
        }
    }

    private static final class FakeHost implements ConfigTabHost {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private @Nullable OsdConfigTab tab;
        private @Nullable Screen screen;

        FakeHost(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        OsdConfigTab bind(OsdConfigTab tab) {
            this.tab = tab;
            this.rebuild();
            return tab;
        }

        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
            this.widgets.add(widget);
            return widget;
        }

        @Override
        public Font font() {
            return Minecraft.getInstance().font;
        }

        @Override
        public Minecraft minecraft() {
            return Minecraft.getInstance();
        }

        @Override
        public Screen screen() {
            if (this.screen == null) {
                this.screen = new Screen(Component.empty()) {
                };
            }
            return this.screen;
        }

        @Override
        public void rebuild() {
            this.widgets.clear();
            if (this.tab != null) {
                this.tab.attach(this, this.x, this.y, this.width, this.height);
            }
        }

        @Override
        public void changed() {
        }
    }
}

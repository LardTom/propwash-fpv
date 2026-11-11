package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.client.GuideSelfTests;
import de.lardtom.propwash.guide.scenes.GateMenuScene;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class GateNudgeTabSelfTests {
    private static final String GROUP = "race-topo: Position-Tab – ";
    private static final String SHOTS = "bildschirmfotos: Position-Tab – ";
    private static final AtomicReference<CovRaceSite.Site> SITE = new AtomicReference<>();
    private static final int[] BOUNDS = {-4, 10, -4, 8, 8};
    private static final Vec3 STAND = new Vec3(2.5, 0.0, 6.5);
    private static final String GATE = "gate";
    private static final String POLE = "pole";
    private static final double POLE_X = 6.5;

    private GateNudgeTabSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Aufbau: Gate und Slalomstange", 900, GateNudgeTabSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Gate: Tabs Gate, Redstone und Position in der Anzeige, Tab und Umschalt+Tab wechseln die Seite", 300,
                GateNudgeTabSelfTests::hudStep);
        SelfTest.clientStep(GROUP + "Gate und Slalomstange: Seite Position erklärt Schritt, Achsen und Drehen, alles im Fenster", 300,
                GateNudgeTabSelfTests::infoStep);
        SelfTest.clientStep(GROUP + "Gate und Slalomstange: mit freier Maus jeden Tab anklicken", 300, GateNudgeTabSelfTests::cursorStep);
        if (Screenshots.enabled()) {
            registerShots("");
            SelfTest.switchStep(SHOTS + "Sprache Deutsch", SHOTS + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            registerShots("_de");
            SelfTest.clientStep(SHOTS + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(GROUP + "Aufräumen", 300, GateNudgeTabSelfTests::cleanupStep);
    }

    private static void registerShots(String suffix) {
        String german = suffix.isEmpty() ? "" : " deutsch";
        Screenshots.step(SHOTS + "Gate-Menü Seite Position in der Anzeige" + german, "race_nudge_tab_hud" + suffix, 30,
                new MenuShot(GATE, false));
        Screenshots.step(SHOTS + "Slalomstange Seite Position mit freier Maus" + german, "race_nudge_tab_cursor" + suffix, 30,
                new MenuShot(POLE, true));
        Screenshots.step(SHOTS + "Flugschule Kapitel Gate-Menü, Schritt Position" + german, "flight_school_gate_menu_position" + suffix, 30,
                GuideSelfTests.sceneShot(GateMenuScene.ID, 3, 56.0F));
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = CovRaceSite.prepare(ctx, SITE, 150, -150, BOUNDS, STAND);
        if (wait.status() != StepResult.Status.PASS) {
            return wait;
        }
        wait = CovRaceSite.once(ctx, "Aufbau", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            CovRaceSite.Site site = SITE.get();
            Vec3 g = site.raw(2.5, 0.0, 0.5);
            GateEntity gate = CovRaceSite.spawn(level, GateGeometry.of(g.x, g.y, g.z, 0.0F, GateSize.MEDIUM), GateSize.MEDIUM);
            Vec3 p = site.raw(POLE_X, 0.0, 1.5);
            GateEntity pole = CovRaceSite.spawn(level,
                    GateGeometry.element(p.x, p.y, p.z, 0.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0, GateTilt.NONE), GateSize.MEDIUM);
            site.ids().put(GATE, gate.getId());
            site.ids().put(POLE, pole.getId());
            Vec3 feet = site.raw(STAND.x, 0.0, STAND.z);
            player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 180.0F, 18.0F, true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !GateMounting.holdsTool(mc.player) || element(GATE) == null || element(POLE) == null) {
            return ctx.tick() > 800 ? StepResult.fail("Gate, Slalomstange oder Kurs-Werkzeug kommen beim Client nicht an") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static @Nullable GateEntity element(String key) {
        return CovRaceSite.clientEntity(SITE.get(), key);
    }

    private static @Nullable GateEditor open(String key) {
        Minecraft mc = Minecraft.getInstance();
        GateEntity entity = element(key);
        if (entity == null) {
            return null;
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        GateEditor.muteStartNotice(true);
        GateEditor.start(entity);
        GateEditor editor = GateEditor.current();
        return editor != null && editor.gate() == entity ? editor : null;
    }

    private static List<String> tabs(CourseElement kind) {
        String first = kind.gate() ? I18n.get("propwash.race.edit.tab.gate") : GateMounting.elementName(kind).getString();
        String second = kind == CourseElement.GATE ? I18n.get("propwash.race.edit.tab.signal") : I18n.get("propwash.race.edit.tab.score");
        return List.of(first, second, I18n.get("propwash.race.edit.tab.position"));
    }

    private static String info(CourseElement kind) {
        return Component.translatable("propwash.race.nudge.info", tabs(kind).getFirst(), Component.keybind(KeyDefaults.GATE_ROTATE.mappingName()))
                .getString();
    }

    private static LayoutProbe.Frame hud(Minecraft mc) {
        return LayoutProbe.capture(mc, g -> GateEditor.extractHud(g, DeltaTracker.ZERO), List.of());
    }

    private static LayoutAudit.@Nullable Text drawn(LayoutProbe.Frame frame, String text) {
        for (LayoutAudit.Text drawn : frame.texts()) {
            if (drawn.value().strip().equals(text)) {
                return drawn;
            }
        }
        return null;
    }

    private static String squeeze(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static String joined(LayoutProbe.Frame frame) {
        StringBuilder all = new StringBuilder();
        for (LayoutAudit.Text drawn : frame.texts()) {
            all.append(drawn.value());
        }
        return squeeze(all.toString());
    }

    private static @Nullable String tabRow(LayoutProbe.Frame frame, List<String> labels, String where) {
        int lastLeft = Integer.MIN_VALUE;
        int top = Integer.MIN_VALUE;
        for (String label : labels) {
            LayoutAudit.Text text = drawn(frame, label);
            if (text == null) {
                return where + ": Tab „" + label + "“ fehlt (gezeichnet: " + frame.texts().stream().map(LayoutAudit.Text::value).toList() + ")";
            }
            LayoutAudit.Box box = text.full();
            if (box.left() <= lastLeft || top != Integer.MIN_VALUE && Math.abs(box.top() - top) > 1) {
                return where + ": Tab „" + label + "“ steht nicht rechts neben dem vorigen in derselben Zeile (" + box + ")";
            }
            lastLeft = box.left();
            top = box.top();
        }
        return null;
    }

    private static void key(Minecraft mc, int key, boolean shift) {
        KeyEvent event = new KeyEvent(key, 0, shift ? InputConstants.MOD_SHIFT : 0);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.PRESS, event);
        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.RELEASE, event);
    }

    private static StepResult hudStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        GateEditor editor = open(GATE);
        if (editor == null) {
            return StepResult.fail("Gate-Menü startet nicht");
        }
        try {
            GateEditModel model = editor.model();
            if (model.pages() != 3 || model.pageKind(2) != GateEditModel.Page.POSITION) {
                return StepResult.fail("Gate ohne Seite Position: " + model.pages() + " Seiten");
            }
            List<String> labels = tabs(CourseElement.GATE);
            for (int page = 0; page < model.pages(); page++) {
                editor.showPage(page);
                String problem = tabRow(hud(mc), labels, "Anzeige Seite " + (page + 1));
                if (problem != null) {
                    return StepResult.fail(problem);
                }
            }
            editor.showPage(0);
            int[] expected = {1, 2, 0};
            for (int want : expected) {
                key(mc, InputConstants.KEY_TAB, false);
                if (model.page() != want) {
                    return StepResult.fail("Tab wechselt nicht auf Seite " + (want + 1) + ", sondern zeigt Seite " + (model.page() + 1));
                }
            }
            key(mc, InputConstants.KEY_TAB, true);
            if (model.pageKind(model.page()) != GateEditModel.Page.POSITION) {
                return StepResult.fail("Umschalt+Tab von Seite Gate zeigt nicht Position, sondern " + model.pageKind(model.page()));
            }
            String hint = I18n.get("propwash.race.edit.hint.cancel_tab");
            if (!joined(hud(mc)).contains(squeeze(hint))) {
                return StepResult.fail("Anzeige nennt die Tab-Taste nicht („" + hint + "“)");
            }
            if (editor.status() != GateEditor.Status.SAME || model.moved()) {
                return StepResult.fail("Tab-Taste ändert das Gate: " + editor.status());
            }
            ctx.log("Tabs " + labels + ", Tab-Taste 1 → 2 → 3 → 1, Umschalt+Tab 1 → 3");
            return StepResult.pass();
        } finally {
            GateEditor.stop();
        }
    }

    private static StepResult infoStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        List<String> seen = new ArrayList<>();
        for (String key : List.of(GATE, POLE)) {
            GateEditor editor = open(key);
            if (editor == null) {
                return StepResult.fail("Menü startet nicht: " + key);
            }
            try {
                GateEditModel model = editor.model();
                CourseElement kind = model.kind();
                String text = info(kind);
                String rotate = Component.keybind(KeyDefaults.GATE_ROTATE.mappingName()).getString();
                for (String part : List.of("1/16", "1/4", rotate, tabs(kind).getFirst())) {
                    if (!text.contains(part)) {
                        return StepResult.fail(kind + ": Erklärung „" + text + "“ nennt „" + part + "“ nicht");
                    }
                }
                int height = mc.getWindow().getGuiScaledHeight();
                for (int page = 0; page < model.pages(); page++) {
                    editor.showPage(page);
                    LayoutProbe.Frame frame = hud(mc);
                    boolean position = model.pageKind(page) == GateEditModel.Page.POSITION;
                    if (joined(frame).contains(squeeze(text)) != position) {
                        return StepResult.fail(kind + " Seite " + (page + 1) + ": Erklärung zum Verschieben " + (position ? "fehlt" : "steht auf der falschen Seite")
                                + " („" + text + "“)");
                    }
                    for (LayoutAudit.Text drawn : frame.texts()) {
                        if (drawn.full().top() < 0 || drawn.full().bottom() > height) {
                            return StepResult.fail(kind + " Seite " + (page + 1) + ": „" + drawn.value() + "“ liegt außerhalb des Fensters (" + drawn.full()
                                    + ", Höhe " + height + ")");
                        }
                    }
                    for (boolean interactive : new boolean[]{false, true}) {
                        GateEditPanel probe = new GateEditPanel(editor, interactive);
                        for (int[] size : new int[][]{{480, 270}, {320, 240}}) {
                            probe.layout(size[0], size[1]);
                            if (probe.top() < 0 || probe.bottom() > size[1]) {
                                return StepResult.fail(kind + " Seite " + (page + 1) + " passt nicht in " + size[0] + " × " + size[1] + ": " + probe.top() + "–"
                                        + probe.bottom());
                            }
                            if (kind == CourseElement.GATE && size[1] == 270 && probe.compact()) {
                                return StepResult.fail("Gate-Menü braucht bei 480 × 270 die gedrängte Darstellung (Seite " + (page + 1) + ")");
                            }
                        }
                    }
                }
                seen.add(kind + ": " + text);
            } finally {
                GateEditor.stop();
            }
        }
        ctx.log("Erklärung " + seen);
        return StepResult.pass();
    }

    private static @Nullable AbstractWidget tab(GateEditPanel panel, String label) {
        for (AbstractWidget widget : panel.widgets()) {
            if (widget.getMessage().getString().strip().equals(label)) {
                return widget;
            }
        }
        return null;
    }

    private static StepResult cursorStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = (int) ctx.state().getOrDefault("phase", 0);
        List<String> keys = List.of(GATE, POLE);
        if (phase >= keys.size() * 4) {
            GateEditor.stop();
            mc.gui.setScreen(null);
            return StepResult.pass();
        }
        String key = keys.get(phase / 4);
        int click = phase % 4;
        if (click == 0) {
            GateEditor editor = open(key);
            if (editor == null || !GateEditCursorScreen.open()) {
                return StepResult.fail("Menü mit freier Maus öffnet nicht: " + key);
            }
            ctx.state().put("phase", phase + 1);
            return StepResult.pending();
        }
        GateEditor editor = GateEditor.current();
        GateEditPanel panel = GateEditDriver.panel(mc);
        if (editor == null || panel == null) {
            return StepResult.fail("Menü mit freier Maus ging zu: " + key);
        }
        if (panel.stale(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight())) {
            return StepResult.pending();
        }
        GateEditModel model = editor.model();
        List<String> labels = tabs(model.kind());
        int target = new int[]{2, 0, 1}[click - 1];
        String label = labels.get(target);
        AbstractWidget widget = tab(panel, label);
        if (widget == null) {
            List<String> shown = new ArrayList<>();
            for (AbstractWidget w : panel.widgets()) {
                shown.add(w.getMessage().getString());
            }
            return StepResult.fail(model.kind() + ": kein Tab „" + label + "“ zum Anklicken auf Seite " + (model.page() + 1) + " " + shown);
        }
        if (widget.getX() < panel.left() || widget.getX() + widget.getWidth() > panel.left() + panel.width() || widget.getY() < panel.top()
                || widget.getY() + widget.getHeight() > panel.bottom()) {
            return StepResult.fail(model.kind() + ": Tab „" + label + "“ ragt aus dem Panel");
        }
        GateEditDriver.click(mc, widget);
        GateEditModel.Page want = new GateEditModel.Page[]{GateEditModel.Page.POSE, model.kind() == CourseElement.GATE ? GateEditModel.Page.SIGNAL
                : GateEditModel.Page.SCORE, GateEditModel.Page.POSITION}[target];
        if (model.pageKind(model.page()) != want) {
            return StepResult.fail(model.kind() + ": Klick auf „" + label + "“ zeigt " + model.pageKind(model.page()) + " statt " + want);
        }
        ctx.state().put("phase", phase + 1);
        if (click == 3) {
            GateEditor.stop();
            mc.gui.setScreen(null);
        }
        return StepResult.pending();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        GateEditor.stop();
        Minecraft.getInstance().gui.setScreen(null);
        CovRaceSite.Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Abbau", server -> CovRaceSite.teardown(server, site));
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static final class MenuShot implements Screenshots.Scene {
        private final String key;
        private final boolean cursor;

        private MenuShot(String key, boolean cursor) {
            this.key = key;
            this.cursor = cursor;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            GateEntity entity = element(this.key);
            if (mc.player == null || entity == null) {
                return StepResult.fail("Aufbau für das Bildschirmfoto fehlt");
            }
            mc.getTutorial().setStep(TutorialSteps.NONE);
            CovRaceSite.Site site = SITE.get();
            Vec3 eye = site.raw(STAND.x, mc.player.getEyeHeight(), STAND.z);
            Vec3 look = entity.position().add(this.cursor ? 1.6 : 2.2, 1.2, 0.0).subtract(eye);
            mc.player.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
            mc.player.setXRot((float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != entity) {
                editor = open(this.key);
                if (editor == null) {
                    return StepResult.fail("Menü für das Bildschirmfoto startet nicht");
                }
                editor.showPage(2);
                editor.nudge(GateEditModel.AXIS_X, 2);
                editor.nudge(GateEditModel.AXIS_Y, 1);
            }
            if (editor.status() != GateEditor.Status.FREE) {
                return ctx.tick() > 300 ? StepResult.fail("Vorschau nicht frei: " + editor.status()) : StepResult.pending();
            }
            if (this.cursor && !this.cursorOpen(mc)) {
                return ctx.tick() > 300 ? StepResult.fail("Freie Maus öffnet nicht") : StepResult.pending();
            }
            return StepResult.pass();
        }

        private boolean cursorOpen(Minecraft mc) {
            if (!(mc.gui.screen() instanceof GateEditCursorScreen)) {
                GateEditCursorScreen.open();
            }
            Screenshots.parkMouse(mc);
            return GateEditDriver.panel(mc) != null;
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.toastManager().clear();
            if (this.cursor) {
                this.cursorOpen(mc);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            GateEditor.stop();
            mc.gui.setScreen(null);
            Screenshots.parkMouse(mc);
        }
    }
}

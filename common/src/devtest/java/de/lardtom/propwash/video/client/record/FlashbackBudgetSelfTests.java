package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.compat.flashback.client.FlashbackBridge;
import de.lardtom.propwash.compat.flashback.client.FlashbackProbe;
import de.lardtom.propwash.compat.flashback.client.FlashbackReplays;
import de.lardtom.propwash.compat.flashback.client.FlashbackScene;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.video.record.path.CameraAngles;
import de.lardtom.propwash.video.record.path.CameraTrack;
import de.lardtom.propwash.video.record.path.FlashbackBudget;
import de.lardtom.propwash.video.record.path.FlashbackSpline;
import de.lardtom.propwash.video.record.path.PathKeyframe;
import de.lardtom.propwash.video.record.path.PathPreview;
import de.lardtom.propwash.video.record.path.Simplification;
import de.lardtom.propwash.video.record.path.TickPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FlashbackBudgetSelfTests {
    public static final String GROUP = "video: Flashback-Budget – ";
    public static final String GOLDEN_FILE = "propwash-flashback-spline-golden.txt";
    private static final String BIG_SCENE = "Propwash · Budget-Test";
    public static final String CORNER_STEP = GROUP + "Pfad-Vorschau: Ring an der sichtbaren Abweichung einer Haarnadelkurve";
    private static final int CORNER_TICK = 60;
    private static final String PHASE = "fb.phase";
    private static final String MARK = "fb.mark";
    private static final String SHOT = "fb.shot";
    private static final String LIVE = "fb.live";
    private static final int SETTLE = 12;
    private static final int GOLDEN_CASES = 400;
    private static final int BIG_TICKS = 3000;

    private record OsdState(String name, double fill, boolean limit) {
    }

    private static final OsdState[] OSD_STATES = {
            new OsdState("normal", 0.43, true),
            new OsdState("caution", 0.91, true),
            new OsdState("thinning", 1.3, true),
            new OsdState("full", 1.3, false)};

    private FlashbackBudgetSelfTests() {
    }

    public static void registerInFlight() {
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Fenster für Bildschirmfotos im Flug", 200, Screenshots::resizeWindow);
        }
        SelfTest.clientStep(GROUP + "OSD-Element in vier Zuständen", 600, ctx -> osdStep(ctx, ""));
        if (!FlashbackCamSelfTests.flashbackPresent()) {
            SelfTest.clientStep(GROUP + "ohne Flashback kein Budget", 40, ctx -> absentStep());
            if (Screenshots.enabled()) {
                SelfTest.clientStep(GROUP + "Fenster nach den Bildschirmfotos im Flug zurück", 40, Screenshots::restoreWindow);
            }
            return;
        }
        SelfTest.clientStep(GROUP + "Spline-Nachbildung gleich Flashbacks CatmullRom", 40, FlashbackBudgetSelfTests::splineStep);
        SelfTest.clientStep(GROUP + "zu große Szene für Flashback ausdünnen", 2400, ctx -> rescueStep(ctx, ""));
        if (Screenshots.enabled()) {
            SelfTest.switchStep(GROUP + "Sprache Deutsch im Flug", GROUP + "Sprache im Flug zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            SelfTest.clientStep(GROUP + "Deutsch: OSD-Element voll", 600, ctx -> osdStep(ctx, "_de"));
            SelfTest.clientStep(GROUP + "Deutsch: Aufnahmen prüfen", 1200, ctx -> rescueListStep(ctx, "_de"));
            SelfTest.clientStep(GROUP + "Sprache im Flug zurücksetzen", 1200, Screenshots::restoreLanguage);
            SelfTest.clientStep(GROUP + "Fenster nach den Bildschirmfotos im Flug zurück", 40, Screenshots::restoreWindow);
        }
    }

    public static void registerAfterFlight() {
        if (!FlashbackCamSelfTests.flashbackPresent()) {
            return;
        }
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Fenster für Bildschirmfotos", 200, Screenshots::resizeWindow);
        }
        SelfTest.clientStep(GROUP + "Tab mit Regler, Obergrenze und Pfad-Vorschau", 1200, ctx -> tabStep(ctx, ""));
        SelfTest.clientStep(GROUP + "große Pfad-Vorschau mit Lupe und Graph", 1200, ctx -> largeStep(ctx, ""));
        SelfTest.clientStep(CORNER_STEP, 1200, FlashbackBudgetSelfTests::cornerStep);
        if (Screenshots.enabled()) {
            SelfTest.switchStep(GROUP + "Sprache Deutsch", GROUP + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
            SelfTest.clientStep(GROUP + "Deutsch: Tab mit Pfad-Vorschau", 1200, ctx -> tabStep(ctx, "_de"));
            SelfTest.clientStep(GROUP + "Deutsch: große Pfad-Vorschau", 1200, ctx -> largeStep(ctx, "_de"));
            SelfTest.clientStep(GROUP + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
            SelfTest.clientStep(GROUP + "Fenster nach den Bildschirmfotos zurück", 40, Screenshots::restoreWindow);
        }
    }

    static StepResult liveBefore(SelfTest.StepContext ctx) {
        FlashbackCam.Budget budget = FlashbackCam.INSTANCE.budget();
        if (budget == null) {
            return StepResult.fail("Flashback-Budget fehlt während der Aufnahme");
        }
        if (!(budget.fill() >= 0.0) || budget.fill() > 1.0 || budget.keyframes() < 2) {
            return StepResult.fail("Flashback-Budget unplausibel: " + budget);
        }
        if (OsdHud.config().slot(OsdElement.FLASHBACK_BUDGET).enabled && OsdHud.renderedFrames() > 0
                && !OsdHud.drewLastFrame(OsdElement.FLASHBACK_BUDGET)) {
            return StepResult.fail("OSD-Element „Flashback-Zeitleiste“ fehlt während der Aufnahme");
        }
        ctx.state().put(LIVE, budget.keyframes());
        ctx.log(String.format(Locale.ROOT, "Live-Zähler vor dem Speichern: %d Keyframes, Budget %.1f %% (höchstens %d bei UI-Skalierung %.2f)",
                budget.keyframes(), budget.fill() * 100.0, budget.maxKeyframes(), budget.scale()));
        return StepResult.pass();
    }

    static StepResult liveAfter(SelfTest.StepContext ctx, FlashbackCam.Handover handover) {
        Object live = ctx.state().get(LIVE);
        if (!(live instanceof Integer before)) {
            return StepResult.fail("Live-Zähler nicht gemessen");
        }
        int saved = handover.wantedKeyframes();
        int allowed = Math.max(4, saved / 10);
        ctx.log("Live-Zähler " + before + " Keyframes, gespeichert " + saved + " (Obergrenze " + handover.maxKeyframes() + ")");
        if (Math.abs(saved - before) > allowed) {
            return StepResult.fail("Live-Zähler " + before + " weicht vom gespeicherten Pfad ab: " + saved);
        }
        if (handover.capped() || handover.fill() > 1.0) {
            return StepResult.fail("kurzer Flug sprengt das Budget: " + handover);
        }
        return StepResult.pass();
    }

    private static StepResult absentStep() {
        FlashbackCam.Budget budget = FlashbackCam.INSTANCE.budget();
        return StepResult.check(budget == null, "Flashback-Budget ohne Flashback-Aufnahme: " + budget);
    }

    private static StepResult osdStep(SelfTest.StepContext ctx, String suffix) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Screenshots.enabled()) {
            Screenshots.clearOverlays(minecraft);
            if (minecraft.gui.screen() != null) {
                Screenshots.parkMouse(minecraft);
            }
        }
        Map<String, Object> state = ctx.state();
        FlashbackCamSelfTests.hover(ctx);
        int phase = (int) state.getOrDefault(PHASE, suffix.isEmpty() ? 0 : OSD_STATES.length - 1);
        FlashbackCam cam = FlashbackCam.INSTANCE;
        if (phase >= OSD_STATES.length) {
            cam.overrideBudget(null);
            if (ctx.tick() - (int) state.get(MARK) < SETTLE) {
                return StepResult.pending();
            }
            Screenshots.hideMarker(false);
            return StepResult.check(cam.recording() || !OsdHud.drewLastFrame(OsdElement.FLASHBACK_BUDGET),
                    "OSD-Element „Flashback-Zeitleiste“ ohne Flashback-Aufnahme sichtbar");
        }
        OsdState wanted = OSD_STATES[phase];
        if (!state.containsKey(MARK)) {
            if (!de.lardtom.propwash.drone.client.PilotSession.INSTANCE.isActive()) {
                return ctx.tick() > 200 ? StepResult.fail("keine Flugsitzung für das OSD") : StepResult.pending();
            }
            Screenshots.hideMarker(true);
            Screenshots.parkMouse(minecraft);
            cam.overrideBudget(new FlashbackCam.Budget(wanted.fill(), (int) Math.round(wanted.fill() * 1239), 1239, wanted.limit(), 2.0F));
            state.put(MARK, ctx.tick());
            return StepResult.pending();
        }
        int since = ctx.tick() - (int) state.get(MARK);
        if (since < SETTLE) {
            return StepResult.pending();
        }
        if (!state.containsKey(SHOT)) {
            if (OsdHud.config().slot(OsdElement.FLASHBACK_BUDGET).enabled && !OsdHud.drewLastFrame(OsdElement.FLASHBACK_BUDGET)) {
                cam.overrideBudget(null);
                return StepResult.fail("OSD-Element „Flashback-Zeitleiste“ fehlt im Zustand " + wanted.name());
            }
            List<OsdWarnings.Warning> warnings = new ArrayList<>();
            OsdWarnings.collect(OsdHud.sampler().data(), warnings);
            boolean full = warnings.contains(OsdWarnings.Warning.FLASHBACK_FULL);
            if (full != (!wanted.limit() && wanted.fill() > 1.0)) {
                cam.overrideBudget(null);
                return StepResult.fail("Warnung „FLASHBACK VOLL“ im Zustand " + wanted.name() + ": " + full);
            }
            double blink = OsdHud.sampler().data().blinkSeconds % 0.8;
            if (Screenshots.enabled() && wanted.fill() > 1.0 && !wanted.limit() && (blink < 0.05 || blink > 0.35) && since < SETTLE + 60) {
                return StepResult.pending();
            }
            state.put(SHOT, Screenshots.enabled() ? Screenshots.capture("osd_flashback_budget_" + wanted.name() + suffix) : CompletableFuture.completedFuture(""));
            return StepResult.pending();
        }
        if (!((CompletableFuture<?>) state.get(SHOT)).isDone()) {
            return StepResult.pending();
        }
        ctx.log("OSD-Zustand " + wanted.name() + " geprüft");
        state.remove(SHOT);
        state.remove(MARK);
        state.put(PHASE, phase + 1);
        if (phase + 1 >= OSD_STATES.length) {
            state.put(MARK, ctx.tick());
        }
        return StepResult.pending();
    }

    private static StepResult splineStep(SelfTest.StepContext ctx) {
        Random random = new Random(20261014L);
        StringBuilder golden = new StringBuilder();
        double worstPosition = 0.0;
        float worstAngle = 0.0F;
        try {
            for (int i = 0; i < GOLDEN_CASES; i++) {
                Vector3d[] p = new Vector3d[4];
                for (int k = 0; k < 4; k++) {
                    p[k] = new Vector3d(random.nextGaussian() * 20.0, 64.0 + random.nextGaussian() * 5.0, random.nextGaussian() * 20.0);
                }
                if (i % 7 == 0) {
                    p[0] = new Vector3d(p[1]);
                }
                if (i % 11 == 0) {
                    p[3] = new Vector3d(p[2]);
                }
                float t1 = i % 7 == 0 ? 0.0F : 1 + random.nextInt(30);
                float t2 = t1 + 1 + random.nextInt(40);
                float t3 = i % 11 == 0 ? t2 : t2 + 1 + random.nextInt(30);
                float amount = random.nextFloat();
                float[] a = new float[4];
                for (int k = 0; k < 4; k++) {
                    a[k] = (float) (random.nextGaussian() * 200.0);
                }
                Vector3d flashback = FlashbackProbe.splinePosition(new Vector3d(p[0]), new Vector3d(p[1]), new Vector3d(p[2]), new Vector3d(p[3]), t1, t2,
                        t3, amount);
                Vector3d ours = FlashbackSpline.position(new Vector3d(p[0]), new Vector3d(p[1]), new Vector3d(p[2]), new Vector3d(p[3]), t1, t2, t3, amount);
                float flashbackAngle = FlashbackProbe.splineDegrees(a[0], a[1], a[2], a[3], t1, t2, t3, amount);
                float ourAngle = FlashbackSpline.degrees(a[0], a[1], a[2], a[3], t1, t2, t3, amount);
                worstPosition = Math.max(worstPosition, flashback.distance(ours));
                worstAngle = Math.max(worstAngle, Math.abs(flashbackAngle - ourAngle));
                golden.append(String.format(Locale.ROOT, "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s%n",
                        p[0].x, p[0].y, p[0].z, p[1].x, p[1].y, p[1].z, p[2].x, p[2].y, p[2].z, p[3].x, p[3].y, p[3].z, t1, t2, t3, amount,
                        flashback.x, flashback.y, flashback.z, a[0], a[1], a[2], a[3], flashbackAngle));
            }
            Files.writeString(Services.PLATFORM.getGameDir().resolve(GOLDEN_FILE), golden.toString());
        } catch (ReflectiveOperationException | IOException e) {
            return StepResult.fail("Flashbacks CatmullRom nicht aufrufbar: " + e);
        }
        ctx.log(String.format(Locale.ROOT, "Spline-Nachbildung: %d Fälle, höchstens %.2e Blöcke und %.2e° neben Flashback (Tabelle %s)", GOLDEN_CASES,
                worstPosition, worstAngle, GOLDEN_FILE));
        return StepResult.check(worstPosition <= 1.0E-12 && worstAngle == 0.0F,
                "Nachbildung weicht von Flashbacks CatmullRom ab: " + worstPosition + " Blöcke, " + worstAngle + "°");
    }

    private static @Nullable ActionCamTab openTab(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, "action_cam");
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (!(active instanceof ActionCamTab tab)) {
            screen.selectTab("action_cam");
            return null;
        }
        return tab;
    }

    private static @Nullable StepSlider simplifySlider(Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
            return null;
        }
        String label = Component.translatable("propwash.video.record.tab.flashback_simplify").getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof StepSlider slider && slider.getMessage().getString().startsWith(label)) {
                return slider;
            }
        }
        return null;
    }

    private static StepResult tabStep(SelfTest.StepContext ctx, String suffix) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Screenshots.enabled()) {
            Screenshots.clearOverlays(minecraft);
            if (minecraft.gui.screen() != null) {
                Screenshots.parkMouse(minecraft);
            }
        }
        Map<String, Object> state = ctx.state();
        ActionCamTab tab = openTab(ctx);
        if (tab == null) {
            return ctx.tick() > 200 ? StepResult.fail("Tab Action-Cam öffnet nicht") : StepResult.pending();
        }
        PathPreviewWidget widget = tab.previewWidget();
        StepSlider slider = simplifySlider(minecraft);
        if (widget == null || slider == null) {
            return StepResult.fail("Regler oder Pfad-Vorschau fehlt im Tab Action-Cam");
        }
        double[] values = suffix.isEmpty() ? new double[]{0.0, 0.5, 1.0} : new double[]{0.5};
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase >= values.length) {
            if (minecraft.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            Screenshots.hideMarker(false);
            return StepResult.pass();
        }
        PathPreviewModel model = tab.previewModel();
        if (!state.containsKey(MARK)) {
            Screenshots.hideMarker(true);
            Screenshots.parkMouse(minecraft);
            tab.reveal(widget);
            int visibleBottom = minecraft.getWindow().getGuiScaledHeight() - 46;
            for (int i = 0; i < 40 && widget.getY() + widget.getHeight() > visibleBottom && widget.getY() > 60; i++) {
                tab.mouseScrolled(widget.getX() + 4, widget.getY() + 4, 0.0, -1.0);
            }
            slider.select(Math.round(values[phase] * 100.0));
            state.put(MARK, ctx.tick());
            return StepResult.pending();
        }
        model.tick();
        if (!model.ready() || Math.abs(model.slider() - values[phase]) > 1.0E-9 || ctx.tick() - (int) state.get(MARK) < SETTLE) {
            return ctx.tick() - (int) state.get(MARK) > 800 ? StepResult.fail("Pfad-Vorschau rechnet nicht fertig") : StepResult.pending();
        }
        PathPreview preview = model.result();
        if (preview == null) {
            return StepResult.fail("Pfad-Vorschau ohne Ergebnis");
        }
        if (!state.containsKey(SHOT)) {
            StepResult checked = checkPreview(ctx, model, preview, values[phase]);
            if (checked.status() == StepResult.Status.FAIL) {
                return checked;
            }
            String name = "action_cam_flashback_simplify_" + Math.round(values[phase] * 100.0) + suffix;
            state.put(SHOT, Screenshots.enabled() ? Screenshots.capture(name) : CompletableFuture.completedFuture(""));
            return StepResult.pending();
        }
        if (!((CompletableFuture<?>) state.get(SHOT)).isDone()) {
            return StepResult.pending();
        }
        state.remove(SHOT);
        state.remove(MARK);
        state.put(PHASE, phase + 1);
        return StepResult.pending();
    }

    private static StepResult checkPreview(SelfTest.StepContext ctx, PathPreviewModel model, PathPreview preview, double slider) {
        Simplification wanted = Simplification.ofSlider(slider);
        ctx.log(String.format(Locale.ROOT, "Vorschau (%s) bei Regler %.2f: %d von %d Ticks als Keyframes, Budget %.0f %%, neben der Bahn %.3f, "
                        + "zur gleichen Zeit %.3f, Winkel %.2f°, passt %.0f s", model.source(), slider, preview.keyframes(), preview.ticks(), preview.fill() * 100.0,
                preview.maxOffPath(), preview.maxSameTime(), preview.maxAngle(), preview.secondsThatFit()));
        if (preview.ticks() < 40) {
            return StepResult.fail("Vorschauflug zu kurz: " + preview.ticks() + " Ticks");
        }
        if (wanted.off()) {
            if (preview.built().capped()) {
                return StepResult.check(preview.keyframes() <= preview.maxKeyframes(), "Obergrenze greift nicht");
            }
            return StepResult.check(preview.keyframes() == preview.ticks() && preview.maxSameTime() < 1.0E-9,
                    "Regler „Aus“ speichert nicht jeden Tick: " + preview.keyframes() + " von " + preview.ticks());
        }
        Simplification used = preview.built().used();
        if (preview.maxSameTime() > used.position() * Simplification.REFINE_FACTOR + 1.0E-9) {
            return StepResult.fail("Vorschau über der Toleranz: " + preview.maxSameTime() + " bei " + used.position());
        }
        return StepResult.check(preview.keyframes() < preview.ticks(), "Vereinfachung spart keine Keyframes");
    }

    private static StepResult largeStep(SelfTest.StepContext ctx, String suffix) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Screenshots.enabled()) {
            Screenshots.clearOverlays(minecraft);
            if (minecraft.gui.screen() != null) {
                Screenshots.parkMouse(minecraft);
            }
        }
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            ActionCamTab tab = openTab(ctx);
            if (tab == null) {
                return ctx.tick() > 200 ? StepResult.fail("Tab Action-Cam öffnet nicht") : StepResult.pending();
            }
            Screenshots.hideMarker(true);
            Screenshots.parkMouse(minecraft);
            tab.openPreview();
            state.put(PHASE, 1);
            state.put(MARK, ctx.tick());
            return StepResult.pending();
        }
        if (!(minecraft.gui.screen() instanceof PathPreviewScreen screen)) {
            return StepResult.fail("große Pfad-Vorschau öffnet nicht");
        }
        PathPreviewModel model = screen.model();
        if (!state.containsKey("fb.layout")) {
            state.put("fb.layout", Boolean.TRUE);
            ctx.log("Layout der großen Vorschau: " + screen.layout());
        }
        if (!model.ready() || ctx.tick() - (int) state.get(MARK) < SETTLE) {
            return ctx.tick() - (int) state.get(MARK) > 800 ? StepResult.fail("große Pfad-Vorschau rechnet nicht fertig") : StepResult.pending();
        }
        String[] shots = suffix.isEmpty() ? new String[]{"flashback_preview_large", "flashback_preview_large_worst", "flashback_preview_large_side"}
                : new String[]{"flashback_preview_large", "flashback_preview_large_worst"};
        if (phase - 1 >= shots.length) {
            screen.onClose();
            if (minecraft.gui.screen() instanceof ConfiguratorScreen configurator) {
                configurator.discardAndClose();
            }
            Screenshots.hideMarker(false);
            return StepResult.pass();
        }
        if (!state.containsKey(SHOT)) {
            if (phase == 3) {
                if (!state.containsKey("fb.side")) {
                    screen.view(PathPreviewDraw.View.SIDE);
                    state.put("fb.side", ctx.tick());
                    return StepResult.pending();
                }
                if (ctx.tick() - (int) state.get("fb.side") < SETTLE) {
                    return StepResult.pending();
                }
            }
            if (phase == 2) {
                screen.zoomOnWorst();
                if (!state.containsKey("fb.zoomed")) {
                    state.put("fb.zoomed", ctx.tick());
                    return StepResult.pending();
                }
                if (ctx.tick() - (int) state.get("fb.zoomed") < SETTLE) {
                    return StepResult.pending();
                }
            }
            state.put(SHOT, Screenshots.enabled() ? Screenshots.capture(shots[phase - 1] + suffix) : CompletableFuture.completedFuture(""));
            return StepResult.pending();
        }
        if (!((CompletableFuture<?>) state.get(SHOT)).isDone()) {
            return StepResult.pending();
        }
        state.remove(SHOT);
        state.put(PHASE, phase + 1);
        return StepResult.pending();
    }

    private static StepResult cornerStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            Screenshots.hideMarker(true);
            PathPreviewModel model = PathPreviewModel.forScene("Propwash · Haarnadel", hairpin(), 1.0, 2.0F);
            minecraft.gui.setScreen(new PathPreviewScreen(null, model, (slider, limit) -> {
            }, null));
            Screenshots.parkMouse(minecraft);
            state.put(PHASE, 1);
            state.put(MARK, ctx.tick());
            return StepResult.pending();
        }
        if (!(minecraft.gui.screen() instanceof PathPreviewScreen screen)) {
            return cornerDone(StepResult.fail("Pfad-Vorschau der Haarnadelkurve öffnet nicht"));
        }
        PathPreviewModel model = screen.model();
        int since = ctx.tick() - (int) state.get(MARK);
        if (!model.ready() || since < SETTLE) {
            return since > 800 ? cornerDone(StepResult.fail("Pfad-Vorschau rechnet nicht fertig")) : StepResult.pending();
        }
        PathPreview preview = model.result();
        if (preview == null) {
            return cornerDone(StepResult.fail("keine Vorschau"));
        }
        PathPreview.Gap gap = preview.topGap();
        if (phase == 1) {
            ctx.log(String.format(Locale.ROOT, "Haarnadel: %d Keyframes, Ring bei Tick %.2f (%.3f Blöcke neben der Bahn), gleiche Zeit am stärksten bei Tick %d (%.3f), Höhe %.3f",
                    preview.keyframes(), gap.time(), gap.distance(), preview.worstIndex(), preview.maxSameTime(), preview.sideGap().distance()));
            if (!gap.visible() || gap.time() < CORNER_TICK - 16.0 || gap.time() > CORNER_TICK + 76.0) {
                return cornerDone(StepResult.fail("Ring nicht an einer Kurve: Tick " + gap.time() + ", Abstand " + gap.distance()));
            }
            screen.zoomOnWorst();
            state.put(PHASE, 2);
            state.put(MARK, ctx.tick());
            return StepResult.pending();
        }
        if (!state.containsKey(SHOT)) {
            double ringTime = screen.graphRingTime();
            ctx.log(String.format(Locale.ROOT, "Haarnadel: Graph-Ring bei Tick %.2f, Pfad-Ring bei Tick %.2f, Maximum gleicher Zeit bei Tick %d",
                    ringTime, gap.time(), preview.worstIndex()));
            if (screen.view() != PathPreviewDraw.View.TOP || Math.abs(ringTime - gap.time()) > 1.0E-9) {
                return cornerDone(StepResult.fail("Graph-Ring zeigt einen anderen Zeitpunkt als der Pfad-Ring: " + ringTime + " statt " + gap.time()));
            }
            PathPreview.Gap side = preview.sideGap();
            double sideRing = PathPreviewDraw.gapTime(PathPreviewDraw.Projection.of(preview, PathPreviewDraw.View.SIDE));
            if (side.visible() ? Math.abs(sideRing - side.time()) > 1.0E-9 : sideRing >= 0.0) {
                return cornerDone(StepResult.fail("Graph-Ring in der Seitenansicht nicht an der Höhenabweichung: " + sideRing + " statt " + side.time()));
            }
            state.put(SHOT, Screenshots.enabled() ? Screenshots.capture("flashback_preview_corner_gap") : CompletableFuture.completedFuture(""));
            return StepResult.pending();
        }
        if (!((CompletableFuture<?>) state.get(SHOT)).isDone()) {
            return StepResult.pending();
        }
        return cornerDone(StepResult.pass());
    }

    private static StepResult cornerDone(StepResult result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof PathPreviewScreen screen) {
            screen.onClose();
        }
        minecraft.gui.setScreen(null);
        Screenshots.hideMarker(false);
        return result;
    }

    static List<TickPose> hairpin() {
        List<TickPose> poses = new ArrayList<>();
        double x = 0.0;
        double z = 0.0;
        double first = Math.toRadians(-65.0);
        double down = Math.toRadians(2.0);
        double back = Math.toRadians(-62.0);
        Quaterniond rotation = new CameraAngles(-30.0, 20.0, 0.0).rotation();
        for (int t = 0; t < 200; t++) {
            double heading;
            double speed;
            if (t < CORNER_TICK) {
                heading = first;
                speed = 0.4;
            } else if (t < CORNER_TICK + 2) {
                double a = (t - CORNER_TICK) / 2.0;
                heading = first + (down - first) * a;
                speed = 0.4 - 0.275 * a;
            } else if (t < 110) {
                heading = down;
                speed = 0.125;
            } else if (t < 120) {
                double a = (t - 110) / 10.0;
                heading = down + (back - down) * a;
                speed = 0.125 + 0.275 * a;
            } else {
                heading = back;
                speed = 0.4;
            }
            x += -Math.sin(heading) * speed;
            z += Math.cos(heading) * speed;
            poses.add(new TickPose(1000 + t, x, 70.0, z, new Quaterniond(rotation), 100.0));
        }
        return poses;
    }

    private static List<TickPose> bigFlight() {
        List<TickPose> poses = new ArrayList<>();
        for (int i = 0; i < BIG_TICKS; i++) {
            double t = i / 20.0;
            double yaw = t * 23.0 + 30.0 * Math.sin(t * 0.7);
            double pitch = 15.0 + 25.0 * Math.sin(t * 0.37);
            double roll = 20.0 * Math.sin(t * 1.3);
            poses.add(new TickPose(i, 40.0 * Math.sin(t * 0.2) + 6.0 * Math.sin(t * 1.1), 80.0 + 8.0 * Math.sin(t * 0.5), 40.0 * Math.cos(t * 0.2),
                    new CameraAngles(yaw, pitch, roll).rotation(), 70.0));
        }
        return poses;
    }

    private static StepResult rescueStep(SelfTest.StepContext ctx, String suffix) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Screenshots.enabled()) {
            Screenshots.clearOverlays(minecraft);
            if (minecraft.gui.screen() != null) {
                Screenshots.parkMouse(minecraft);
            }
        }
        Map<String, Object> state = ctx.state();
        FlashbackCamSelfTests.hover(ctx);
        int phase = (int) state.getOrDefault(PHASE, 0);
        UUID replay = FlashbackCamSelfTests.recordedReplay();
        if (replay == null) {
            return StepResult.fail("keine Flashback-Aufnahme aus dem Aufnahme-Schritt");
        }
        try {
            switch (phase) {
                case 0 -> {
                    if (FlashbackBridge.recording() || FlashbackCam.INSTANCE.recording() || FlashbackCam.INSTANCE.saving()) {
                        return ctx.tick() > 600 ? StepResult.fail("Flashback nimmt noch auf") : StepResult.pending();
                    }
                    CameraTrack track = CameraTrack.build(bigFlight(), Simplification.OFF, Integer.MAX_VALUE).track();
                    FlashbackScene scene = new FlashbackScene(BIG_SCENE, Component.translatable("propwash.video.flashback.track.camera").getString(),
                            Component.translatable("propwash.video.flashback.track.fov").getString(), FlashbackCam.TRACK_COLOUR, Avatar.DEFAULT_EYE_HEIGHT,
                            track);
                    int index = FlashbackBridge.writeScene(replay, scene);
                    ctx.log("Szene „" + BIG_SCENE + "“ mit " + track.camera().size() + " Keyframes als Szene " + index + " geschrieben");
                    Screenshots.hideMarker(true);
                    Screenshots.parkMouse(minecraft);
                    minecraft.gui.setScreen(new FlashbackRescueScreen(null));
                    state.put(PHASE, 1);
                    state.put(MARK, ctx.tick());
                    return StepResult.pending();
                }
                case 1 -> {
                    if (!(minecraft.gui.screen() instanceof FlashbackRescueScreen screen) || screen.scanning()) {
                        return ctx.tick() - (int) state.get(MARK) > 400 ? StepResult.fail("Liste der Flashback-Aufnahmen lädt nicht") : StepResult.pending();
                    }
                    int row = row(screen, replay);
                    if (row < 0) {
                        return StepResult.fail("Szene „" + BIG_SCENE + "“ fehlt in der Liste: " + screen.scenes().size() + " Szenen");
                    }
                    FlashbackReplays.Scene found = screen.scenes().get(row);
                    if (!found.propwash() || found.fits(screen.scale())) {
                        return StepResult.fail("Szene „" + BIG_SCENE + "“ nicht als zu groß erkannt: " + found);
                    }
                    screen.select(row);
                    if (!state.containsKey(SHOT)) {
                        state.put(SHOT, Screenshots.enabled() ? Screenshots.capture("flashback_rescue_list" + suffix) : CompletableFuture.completedFuture(""));
                        return StepResult.pending();
                    }
                    if (!((CompletableFuture<?>) state.get(SHOT)).isDone()) {
                        return StepResult.pending();
                    }
                    state.remove(SHOT);
                    screen.openSelected();
                    state.put(PHASE, 2);
                    state.put(MARK, ctx.tick());
                    return StepResult.pending();
                }
                case 2 -> {
                    if (minecraft.gui.screen() instanceof FlashbackRescueScreen screen) {
                        screen.tick();
                    }
                    if (!(minecraft.gui.screen() instanceof PathPreviewScreen screen) || !screen.model().ready()) {
                        return ctx.tick() - (int) state.get(MARK) > 1200 ? StepResult.fail("Vorschau der Reparatur öffnet nicht") : StepResult.pending();
                    }
                    PathPreview preview = screen.model().result();
                    if (preview == null || !preview.fits()) {
                        return StepResult.fail("Vorschau der Reparatur passt nicht ins Budget: " + (preview == null ? "-" : preview.keyframes()));
                    }
                    if (!state.containsKey(SHOT)) {
                        state.put(SHOT, Screenshots.enabled() ? Screenshots.capture("flashback_rescue_preview" + suffix) : CompletableFuture.completedFuture(""));
                        return StepResult.pending();
                    }
                    if (!((CompletableFuture<?>) state.get(SHOT)).isDone()) {
                        return StepResult.pending();
                    }
                    state.remove(SHOT);
                    ctx.log(String.format(Locale.ROOT, "Reparatur startet bei Regler %.2f: %d Keyframes, Budget %.0f %%", screen.model().slider(),
                            preview.keyframes(), preview.fill() * 100.0));
                    screen.applyNow();
                    Component message = screen.message();
                    ctx.log("Reparatur: " + (message == null ? "-" : message.getString()));
                    screen.onClose();
                    state.put(PHASE, 3);
                    state.put(MARK, ctx.tick());
                    return StepResult.pending();
                }
                default -> {
                    if (!(minecraft.gui.screen() instanceof FlashbackRescueScreen screen)) {
                        return StepResult.fail("Liste nach der Reparatur fehlt");
                    }
                    screen.tick();
                    if (screen.scanning()) {
                        return ctx.tick() - (int) state.get(MARK) > 400 ? StepResult.fail("Liste lädt nach der Reparatur nicht") : StepResult.pending();
                    }
                    int row = row(screen, replay);
                    if (row < 0) {
                        return StepResult.fail("Szene „" + BIG_SCENE + "“ nach der Reparatur verschwunden");
                    }
                    FlashbackReplays.Scene found = screen.scenes().get(row);
                    Path backup = found.state().resolveSibling(found.state().getFileName() + ".bak");
                    if (!found.fits(screen.scale()) || !Files.isRegularFile(backup)) {
                        return StepResult.fail("Szene nach der Reparatur: " + found.cameraKeyframes() + " Keyframes, Sicherung " + Files.isRegularFile(backup));
                    }
                    ctx.log("nach der Reparatur: " + found.cameraKeyframes() + " Keyframes, Budget "
                            + Math.round(found.fill(screen.scale()) * 100.0) + " %, Sicherung " + backup.getFileName());
                    screen.onClose();
                    Screenshots.hideMarker(false);
                    return StepResult.pass();
                }
            }
        } catch (FlashbackBridge.FlashbackException e) {
            return StepResult.fail("Szene nicht schreibbar: " + e.getMessage());
        }
    }

    private static int row(FlashbackRescueScreen screen, UUID replay) {
        List<FlashbackReplays.Scene> scenes = screen.scenes();
        for (int i = 0; i < scenes.size(); i++) {
            FlashbackReplays.Scene scene = scenes.get(i);
            if (scene.replay().equals(replay) && BIG_SCENE.equals(scene.sceneName())) {
                return i;
            }
        }
        return -1;
    }

    private static StepResult rescueListStep(SelfTest.StepContext ctx, String suffix) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Screenshots.enabled()) {
            Screenshots.clearOverlays(minecraft);
            if (minecraft.gui.screen() != null) {
                Screenshots.parkMouse(minecraft);
            }
        }
        Map<String, Object> state = ctx.state();
        FlashbackCamSelfTests.hover(ctx);
        if (!state.containsKey(MARK)) {
            Screenshots.hideMarker(true);
            Screenshots.parkMouse(minecraft);
            minecraft.gui.setScreen(new FlashbackRescueScreen(null));
            state.put(MARK, ctx.tick());
            return StepResult.pending();
        }
        if (!(minecraft.gui.screen() instanceof FlashbackRescueScreen screen) || screen.scanning() || ctx.tick() - (int) state.get(MARK) < SETTLE) {
            return ctx.tick() - (int) state.get(MARK) > 400 ? StepResult.fail("Liste lädt nicht") : StepResult.pending();
        }
        if (!state.containsKey(SHOT)) {
            state.put(SHOT, Screenshots.capture("flashback_rescue_list" + suffix));
            return StepResult.pending();
        }
        if (!((CompletableFuture<?>) state.get(SHOT)).isDone()) {
            return StepResult.pending();
        }
        screen.onClose();
        Screenshots.hideMarker(false);
        return StepResult.pass();
    }

    static StepResult measureInReplay(SelfTest.StepContext ctx, CameraTrack recorded) {
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 99) {
            return StepResult.pass();
        }
        try {
            if (phase == 0) {
                if (!FlashbackProbe.editorVisible()) {
                    return ctx.tick() > 2000 ? StepResult.fail("Flashbacks Editor ist nicht sichtbar") : StepResult.pending();
                }
                int start = recorded.startTick();
                int span = Math.max(1, recorded.endTick() - recorded.startTick() + 1);
                int total = FlashbackBudget.maxCameraKeyframes(FlashbackBudget.MIN_ASSUMED_SCALE, 2);
                int tracks = (total + span - 1) / span;
                List<List<PathKeyframe>> full = new ArrayList<>();
                List<List<PathKeyframe>> single = new ArrayList<>();
                PathKeyframe anchor = recorded.camera().getFirst();
                int left = total;
                for (int t = 0; t < tracks; t++) {
                    List<PathKeyframe> keys = new ArrayList<>();
                    for (int i = 0; i < span && left > 0; i++, left--) {
                        keys.add(new PathKeyframe(start + i, anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch(), anchor.roll()));
                    }
                    full.add(keys);
                    single.add(List.of(keys.getFirst()));
                }
                int colour = FlashbackRescueScreen.imguiColour(FlashbackCam.TRACK_COLOUR);
                state.put("fb.original", FlashbackProbe.activeScene());
                state.put("fb.full", FlashbackProbe.addScene("Budget A", full, 2, colour, Avatar.DEFAULT_EYE_HEIGHT));
                state.put("fb.single", FlashbackProbe.addScene("Budget B", single, 2, colour, Avatar.DEFAULT_EYE_HEIGHT));
                state.put("fb.total", total);
                state.put("fb.tracks", tracks);
                ctx.log("Budget-Szenen: " + total + " Kamera-Keyframes auf " + tracks + " Spuren (" + span + " Ticks je Spur)");
                FlashbackProbe.uiZoom(1.0F);
                return next(ctx, 1);
            }
            if (ctx.tick() - (int) state.get(MARK) < (phase == 5 ? 40 : SETTLE)) {
                return StepResult.pending();
            }
            int total = (int) state.get("fb.total");
            int tracks = (int) state.get("fb.tracks");
            int measured = max(FlashbackProbe.drawListVertices());
            float scale = FlashbackProbe.uiScale();
            switch (phase) {
                case 1 -> {
                    state.put("fb.real1", measured);
                    log(ctx, "echte Szene", measured, scale);
                    FlashbackProbe.activeScene((int) state.get("fb.single"));
                    return next(ctx, 2);
                }
                case 2 -> {
                    state.put("fb.single1", measured);
                    log(ctx, "Spuren mit je einem Keyframe", measured, scale);
                    FlashbackProbe.activeScene((int) state.get("fb.full"));
                    return next(ctx, 3);
                }
                case 3 -> {
                    int single = (int) state.get("fb.single1");
                    log(ctx, total + " Keyframes", measured, scale);
                    double cost = (measured - single) / (double) (total - tracks);
                    ctx.log(String.format(Locale.ROOT, "Eckpunkte je Keyframe bei UI-Skalierung %.2f: gemessen %.2f, gerechnet %d", scale, cost,
                            FlashbackBudget.keyframeVertices(scale)));
                    if (Math.abs(cost - FlashbackBudget.keyframeVertices(scale)) > 0.5) {
                        return restore(ctx, StepResult.fail("Eckpunkte je Keyframe stimmen nicht: " + cost));
                    }
                    FlashbackProbe.activeScene((int) state.get("fb.single"));
                    FlashbackProbe.uiZoom(FlashbackBudget.MIN_ASSUMED_SCALE / Math.max(0.125F, scale));
                    return next(ctx, 5);
                }
                case 5 -> {
                    if (Math.abs(scale - FlashbackBudget.MIN_ASSUMED_SCALE) > 0.01F) {
                        return ctx.tick() - (int) state.get(MARK) > 400 ? restore(ctx, StepResult.fail("UI-Skalierung 2 greift nicht: " + scale))
                                : StepResult.pending();
                    }
                    state.put("fb.single2", measured);
                    log(ctx, "Spuren mit je einem Keyframe", measured, scale);
                    long predicted = measured + (long) (total - tracks) * FlashbackBudget.keyframeVertices(scale);
                    if (predicted >= FlashbackBudget.VERTEX_LIMIT) {
                        return restore(ctx, StepResult.fail("die Szene mit " + total + " Keyframes käme auf " + predicted + " Eckpunkte – Reserve zu klein"));
                    }
                    FlashbackProbe.activeScene((int) state.get("fb.full"));
                    return next(ctx, 6);
                }
                case 6 -> {
                    log(ctx, total + " Keyframes", measured, scale);
                    if (measured >= FlashbackBudget.VERTEX_LIMIT) {
                        return restore(ctx, StepResult.fail("Zeitleiste über der Grenze: " + measured));
                    }
                    state.put("fb.full2", measured);
                    FlashbackProbe.activeScene((int) state.get("fb.original"));
                    return next(ctx, 7);
                }
                case 7 -> {
                    log(ctx, "echte Szene", measured, scale);
                    int keys = recorded.camera().size();
                    int holds = recorded.fov().size();
                    int reserve = measured - keys * FlashbackBudget.keyframeVertices(scale) - holds * FlashbackBudget.HOLD_KEYFRAME_VERTICES;
                    int full = (int) state.get("fb.full2");
                    ctx.log(String.format(Locale.ROOT, "Reserve der Zeitleiste bei UI-Skalierung %.2f: %d Eckpunkte für eine Propwash-Szene (angenommen %d); "
                                    + "volle Budget-Szene %d von %d Eckpunkten", scale, reserve, FlashbackBudget.RESERVE, full, FlashbackBudget.VERTEX_LIMIT));
                    if (reserve > FlashbackBudget.RESERVE) {
                        return restore(ctx, StepResult.fail("Reserve " + reserve + " größer als angenommen"));
                    }
                    return restore(ctx, StepResult.pass());
                }
                default -> {
                    return restore(ctx, StepResult.fail("unbekannte Phase " + phase));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return restore(ctx, StepResult.fail("Zeitleiste nicht messbar: " + e));
        }
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(MARK, ctx.tick());
        return StepResult.pending();
    }

    private static int max(int[] values) {
        int max = 0;
        for (int v : values) {
            max = Math.max(max, v);
        }
        return max;
    }

    private static void log(SelfTest.StepContext ctx, String what, int vertices, float scale) {
        ctx.log(String.format(Locale.ROOT, "Zeitleiste (%s) bei UI-Skalierung %.2f: %d Eckpunkte in der größten Zeichenliste", what, scale, vertices));
    }

    private static StepResult restore(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        try {
            FlashbackProbe.uiZoom(1.0F);
            if (state.get("fb.original") instanceof Integer original) {
                FlashbackProbe.activeScene(original);
            }
            int full = (int) state.getOrDefault("fb.full", -1);
            int single = (int) state.getOrDefault("fb.single", -1);
            FlashbackProbe.removeScene(Math.max(full, single));
            FlashbackProbe.removeScene(Math.min(full, single));
        } catch (ReflectiveOperationException | RuntimeException e) {
            ctx.log("Budget-Szenen nicht entfernt: " + e);
        }
        state.put(PHASE, 99);
        return result;
    }
}

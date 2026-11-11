package de.lardtom.propwash.osd.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.FlightPathMarker;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.client.FpvLens;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class CovFlightPathMarkerSelfTests {
    public static final String PREFIX = FlightPathMarkerSelfTests.PREFIX;
    public static final String EDITOR_STEP = PREFIX + " im Editor – Liste mit ⌖, in der Vorschau gezeichnet, nicht verschiebbar, Farbe, Größe und Linien-Deckkraft";
    public static final String MOTION_STEP = PREFIX + " – Drift, Kurve und Powerloop ohne Sprünge";
    public static final String LENS_STEP = PREFIX + " – Fisheye und Bildformat";
    public static final String LOS_STEP = PREFIX + " – nicht im Sichtflug-HUD";
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
    private static final int TIMEOUT_TICKS = 400;
    private static final int ARM_TICKS = 20;
    private static final int SEGMENT_TICKS = 40;
    private static final double MIN_SPEED = 2.5;
    private static final String PHASE = "cov_fpm_phase";
    private static final String SINCE = "cov_fpm_since";
    private static final String FUTURE = "cov_fpm_future";
    private static final String RESULT = "cov_fpm_result";
    private static final AtomicInteger LOS_DRONE = new AtomicInteger(-1);
    private static volatile @Nullable ItemStack losHand;

    private CovFlightPathMarkerSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(EDITOR_STEP, 300, CovFlightPathMarkerSelfTests::editorStep);
        SelfTest.clientStep(MOTION_STEP, 4000, CovFlightPathMarkerSelfTests::motionStep);
        SelfTest.clientStep(LENS_STEP, 3000, CovFlightPathMarkerSelfTests::lensStep);
        SelfTest.clientStep(LOS_STEP, 1500, CovFlightPathMarkerSelfTests::losStep);
        SelfTest.clientStep(PREVIEW_STEP, 400, CovFlightPathMarkerSelfTests::previewStep);
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Abdeckung Flugbahn-Marker – rückwärts am Rand mit Home-Pfeil", "cov_osd_flight_path_marker_edge_home",
                    6, new EdgeScene(FlightPathMarkerSelfTests.BACKWARD_PITCH, true));
            Screenshots.step("bildschirmfotos: Abdeckung Flugbahn-Marker – Sturzflug unten im Bild", "cov_osd_flight_path_marker_dive", 2,
                    new EdgeScene(0.0F, false));
        }
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
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

    private static final class Host implements ConfigTabHost {
        final List<AbstractWidget> widgets = new ArrayList<>();
        final int x;
        final int y;
        final int width;
        final int height;
        private @Nullable OsdConfigTab tab;
        private @Nullable Screen screen;

        Host(int x, int y, int width, int height) {
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

        @Nullable OsdSlider slider(String nameKey) {
            String name = Component.translatable(nameKey).getString();
            for (int i = this.widgets.size() - 1; i >= 0; i--) {
                if (this.widgets.get(i) instanceof OsdSlider slider && slider.themeLabel().getString().equals(name)) {
                    return slider;
                }
            }
            return null;
        }
    }

    private static boolean clickWidget(@Nullable AbstractWidget widget) {
        if (widget == null || !widget.visible || !widget.active) {
            return false;
        }
        return widget.mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() * 0.5, widget.getY() + widget.getHeight() * 0.5, LEFT), false);
    }

    private record Row(LayoutAudit.Text name, LayoutAudit.@Nullable Text icon) {
    }

    private static @Nullable Row row(OsdConfigTab tab, OsdElement element) {
        Minecraft mc = Minecraft.getInstance();
        String name = Component.translatable(element.translationKey()).getString();
        LayoutProbe.Frame frame = LayoutProbe.capture(mc, graphics -> tab.extract(graphics, -1, -1, 0.0F), List.of());
        LayoutAudit.Text found = null;
        for (LayoutAudit.Text text : frame.texts()) {
            if (text.value().equals(name) && text.visible() != null) {
                found = text;
            }
        }
        if (found == null) {
            return null;
        }
        int middle = (found.full().top() + found.full().bottom()) / 2;
        for (LayoutAudit.Text text : frame.texts()) {
            int y = (text.full().top() + text.full().bottom()) / 2;
            if (text.value().equals("⌖") && Math.abs(y - middle) <= 3 && text.full().left() > found.full().left()) {
                return new Row(found, text);
            }
        }
        return new Row(found, null);
    }

    private static @Nullable Row selectInList(OsdConfigTab tab, Host host, OsdElement element) {
        for (int scroll = 0; scroll < 40; scroll++) {
            Row row = row(tab, element);
            if (row != null) {
                LayoutAudit.Box box = row.name().full();
                tab.mouseClicked(new MouseButtonEvent(box.left() + 3, (box.top() + box.bottom()) * 0.5, LEFT), false);
                return tab.selected() == element ? row : null;
            }
            List<String> names = new ArrayList<>();
            for (OsdElement listed : OsdElement.values()) {
                names.add(Component.translatable(listed.translationKey()).getString());
            }
            LayoutProbe.Frame frame = LayoutProbe.capture(Minecraft.getInstance(), graphics -> tab.extract(graphics, -1, -1, 0.0F), List.of());
            LayoutAudit.Text anchor = null;
            for (LayoutAudit.Text text : frame.texts()) {
                if (text.visible() != null && names.contains(text.value())) {
                    anchor = text;
                    break;
                }
            }
            if (anchor == null) {
                return null;
            }
            tab.mouseScrolled(anchor.full().left() + 3, (anchor.full().top() + anchor.full().bottom()) * 0.5, 0.0, -1.0);
        }
        return null;
    }

    private static StepResult editorStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            OsdHud.sampler().setOverride(null);
            Host host = new Host(8, 28, 510, 302);
            OsdConfigTab tab = host.bind(new OsdConfigTab());
            tab.resetDefaults();
            OsdElement marker = OsdElement.FLIGHT_PATH_MARKER;
            if (tab.working().enabled(marker)) {
                return StepResult.fail("Flugbahn-Marker ist nach den Standardwerten schon an");
            }
            Row row = selectInList(tab, host, marker);
            if (row == null) {
                List<String> seen = new ArrayList<>();
                for (LayoutAudit.Text text : LayoutProbe.capture(Minecraft.getInstance(), graphics -> tab.extract(graphics, -1, -1, 0.0F), List.of()).texts()) {
                    seen.add(text.value() + "@" + text.full().left() + "/" + text.full().top() + (text.visible() == null ? "(verdeckt)" : ""));
                }
                return StepResult.fail("Flugbahn-Marker in der Elementliste nicht gefunden (Auswahl " + tab.selected() + "), Texte: "
                        + String.join(", ", seen.subList(0, Math.min(seen.size(), 60))));
            }
            if (row.icon() == null) {
                return StepResult.fail("Listeneintrag „" + row.name().value() + "“ ohne ⌖");
            }
            state.put("row", "„" + row.name().value() + "“ mit ⌖" + (row.icon().visible() == null ? " (⌖ am Rand der Liste abgeschnitten)" : ""));
            tab.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 32, 0));
            if (!tab.working().enabled(marker)) {
                return StepResult.fail("Anhaken (Leertaste) schaltet den Flugbahn-Marker nicht ein");
            }
            if (marker.movable()) {
                return StepResult.fail("Flugbahn-Marker gilt als verschiebbar, die Liste zeigt dann kein ⌖");
            }
            AtomicBoolean extracted = new AtomicBoolean();
            OsdHud.probeNextFrame(graphics -> {
                tab.extract(graphics, 0, 0, 0.0F);
                extracted.set(true);
            });
            state.put("tab", tab);
            state.put("host", host);
            state.put("extracted", extracted);
            return StepResult.pending();
        }
        OsdConfigTab tab = (OsdConfigTab) state.get("tab");
        Host host = (Host) state.get("host");
        AtomicBoolean extracted = (AtomicBoolean) state.get("extracted");
        if (!extracted.get()) {
            return ctx.tick() > 100 ? StepResult.fail("Vorschau nicht gezeichnet") : StepResult.pending();
        }
        OsdElement marker = OsdElement.FLIGHT_PATH_MARKER;
        if (!tab.renderer().wasDrawn(marker)) {
            return StepResult.fail("Vorschau zeichnet den eingeschalteten Flugbahn-Marker nicht");
        }
        OsdGrid grid = tab.previewGrid();
        float[] b = tab.renderer().bounds(marker);
        double cx = (b[0] + b[2]) * 0.5;
        double cy = (b[1] + b[3]) * 0.5;
        OsdConfig config = tab.working();
        OsdConfig.Slot before = config.slot(marker).copy();
        tab.mouseClicked(new MouseButtonEvent(cx, cy, LEFT), false);
        boolean grabbed = tab.isDragging();
        tab.mouseDragged(new MouseButtonEvent(cx + grid.cellWidth() * 3.0, cy + grid.cellHeight() * 2.0, LEFT), grid.cellWidth() * 3.0,
                grid.cellHeight() * 2.0);
        tab.mouseReleased(new MouseButtonEvent(cx + grid.cellWidth() * 3.0, cy + grid.cellHeight() * 2.0, LEFT));
        tab.beginDrag(marker, cx, cy);
        boolean begun = tab.isDragging();
        tab.dragTo(cx + grid.cellWidth() * 4.0, cy);
        tab.endDrag();
        if (selectInList(tab, host, marker) == null) {
            return StepResult.fail("Flugbahn-Marker nach dem Ziehen nicht mehr auswählbar");
        }
        tab.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
        tab.keyPressed(new KeyEvent(InputConstants.KEY_DOWN, 0, 0));
        OsdConfig.Slot after = config.slot(marker);
        if (grabbed || begun || after.col != before.col || after.row != before.row) {
            return StepResult.fail(String.format(Locale.ROOT, "Flugbahn-Marker lässt sich verschieben: gegriffen %b, Ziehen %b, col/row %d/%d → %d/%d", grabbed,
                    begun, before.col, before.row, after.col, after.row));
        }
        if (!clickWidget(tab.elementColorButton()) || !config.slot(marker).hasColor()) {
            return StepResult.fail("Elementfarbe landet nicht im Slot des Flugbahn-Markers");
        }
        OsdSlider size = host.slider("propwash.osd.editor.element_scale.name");
        if (size == null) {
            return StepResult.fail("Regler für die Elementgröße fehlt");
        }
        float scaleBefore = config.slot(marker).scale;
        size.select(OsdConfig.SCALE_STEPS);
        if (!config.slot(marker).hasScale()) {
            return StepResult.fail("Elementgröße landet nicht im Slot des Flugbahn-Markers: Regler „" + size.getMessage().getString() + "“, aktiv "
                    + size.active + ", Auswahl " + tab.selected() + ", Größe vorher " + scaleBefore + ", nachher " + config.slot(marker).scale);
        }
        OsdSlider lines = host.slider("propwash.osd.editor.lines.name");
        if (lines == null) {
            return StepResult.fail("Regler Linien-Deckkraft fehlt");
        }
        lines.select(10);
        float expected = config.opacityFraction() * 0.5F;
        float alpha = config.elementAlpha(marker);
        float crosshair = config.elementAlpha(OsdElement.CROSSHAIR);
        float text = config.elementAlpha(OsdElement.BATTERY_VOLTAGE);
        if (config.lineOpacity != 50 || Math.abs(alpha - expected) > 1.0E-4F || Math.abs(alpha - crosshair) > 1.0E-4F
                || Math.abs(text - config.opacityFraction()) > 1.0E-4F) {
            return StepResult.fail(String.format(Locale.ROOT, "Deckkraft folgt nicht der Linien-Deckkraft: Linien %d %%, Marker %.3f, Fadenkreuz %.3f, Text %.3f, "
                    + "erwartet %.3f", config.lineOpacity, alpha, crosshair, text, expected));
        }
        OsdConfig.Slot slot = config.slot(marker);
        ctx.log(String.format(Locale.ROOT, "Flugbahn-Marker im Editor: Listeneintrag %s, angehakt, in der Vorschau gezeichnet bei %.1f/%.1f (Mitte %.1f/%.1f), nicht verschiebbar (col/row %d/%d), "
                        + "Farbe %s und Größe %.2f im Slot, Deckkraft %.3f bei Linien-Deckkraft 50 %% wie das Fadenkreuz", state.get("row"), cx, cy, grid.centerX(),
                grid.centerY(), slot.col, slot.row, slot.color, slot.scale, alpha));
        return StepResult.pass();
    }

    public static final String PREVIEW_STEP = PREFIX + " – Vorschau im Editor leicht unter der Mitte";

    public static StepResult previewStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            OsdHud.sampler().setOverride(null);
            Host host = new Host(8, 28, 510, 302);
            OsdConfigTab tab = host.bind(new OsdConfigTab());
            tab.resetDefaults();
            tab.toggle(OsdElement.FLIGHT_PATH_MARKER);
            state.put("tab", tab);
            state.put("frames", new ArrayList<String>());
            state.put("off", 0);
        }
        OsdConfigTab tab = (OsdConfigTab) state.get("tab");
        AtomicBoolean extracted = new AtomicBoolean();
        LayoutProbe.capture(Minecraft.getInstance(), graphics -> {
            tab.extract(graphics, -1, -1, 0.0F);
            extracted.set(true);
        }, List.of());
        OsdGrid grid = tab.previewGrid();
        @SuppressWarnings("unchecked")
        List<String> frames = (List<String>) state.get("frames");
        boolean drawn = tab.renderer().wasDrawn(OsdElement.FLIGHT_PATH_MARKER);
        float[] b = tab.renderer().bounds(OsdElement.FLIGHT_PATH_MARKER);
        double cx = (b[0] + b[2]) * 0.5;
        double cy = (b[1] + b[3]) * 0.5;
        boolean below = drawn && cy > grid.centerY() + 2.0 && cy < grid.centerY() + grid.height() * 0.3 && Math.abs(cx - grid.centerX()) < grid.width() * 0.05;
        if (!below) {
            state.put("off", (int) state.get("off") + 1);
            if (frames.size() < 8) {
                frames.add(String.format(Locale.ROOT, "Tick %d: %s %.1f/%.1f", ctx.tick(), drawn ? "bei" : "nicht gezeichnet", cx, cy));
            }
        }
        if (ctx.tick() < PREVIEW_TICKS) {
            return StepResult.pending();
        }
        int off = (int) state.get("off");
        String text = String.format(Locale.ROOT, "Vorschau-Marker in %d von %d Ticks nicht leicht unter der Mitte (Mitte %.1f/%.1f): %s", off, PREVIEW_TICKS + 1,
                grid.centerX(), grid.centerY(), String.join(", ", frames));
        return off == 0 ? StepResult.pass() : StepResult.fail(text);
    }

    private static final int PREVIEW_TICKS = 200;

    private static final class Track {
        int frames;
        int drawnFrames;
        int fastFrames;
        int fastHidden;
        int toggles;
        int jumps;
        int edgeFrames;
        int compared;
        double maxError;
        double maxStep;
        double simError;
        @Nullable Boolean lastDrawn;
        FlightPathMarker.@Nullable State last;

        void sample(Minecraft mc, double speed) {
            this.frames++;
            boolean drawn = FlightPathMarkerSelfTests.drawn();
            if (speed >= MIN_SPEED) {
                this.fastFrames++;
                if (!drawn) {
                    this.fastHidden++;
                }
                if (this.lastDrawn != null && this.lastDrawn != drawn) {
                    this.toggles++;
                }
            }
            this.lastDrawn = drawn;
            if (!drawn) {
                this.last = null;
                return;
            }
            this.drawnFrames++;
            FlightPathMarker.State shown = FlightPathMarkerSelfTests.shown();
            FlightPathMarker.State copy = new FlightPathMarker.State();
            copy.visible = shown.visible;
            copy.inView = shown.inView;
            copy.x = shown.x;
            copy.y = shown.y;
            if (!shown.inView) {
                this.edgeFrames++;
            }
            OsdData data = OsdHud.sampler().data();
            if (data.hasVelocity()) {
                FlightPathMarker.State expected = FlightPathMarkerSelfTests.predict(mc, data.velocityX, data.velocityY, data.velocityZ);
                if (expected != null && expected.visible && expected.inView == shown.inView) {
                    this.compared++;
                    this.maxError = Math.max(this.maxError, Math.hypot(expected.x - shown.x, expected.y - shown.y));
                }
            }
            Vector3d sim = FlightPathMarkerSelfTests.simVelocity();
            if (shown.inView && sim.isFinite()) {
                FlightPathMarker.State truth = FlightPathMarkerSelfTests.predict(mc, sim.x, sim.y, sim.z);
                if (truth != null && truth.visible && truth.inView) {
                    this.simError = Math.max(this.simError, Math.hypot(truth.x - shown.x, truth.y - shown.y));
                }
            }
            FlightPathMarker.State previous = this.last;
            if (previous != null && previous.inView && copy.inView) {
                double step = Math.hypot(copy.x - previous.x, copy.y - previous.y);
                this.maxStep = Math.max(this.maxStep, step);
                OsdGrid grid = FlightPathMarkerSelfTests.grid(mc);
                if (step > grid.height() * JUMP_SHARE) {
                    this.jumps++;
                }
            }
            this.last = copy;
        }

        String text(String what) {
            return String.format(Locale.ROOT, "%s: %d Ticks, gezeichnet %d (am Rand %d), ab %.1f m/s %d Ticks davon %d ohne Marker, %d Wechsel an/aus, "
                            + "größte Abweichung zur Projektion des OSD-Tempos %.1f Punkte (%d verglichen), zur Sim-Geschwindigkeit %.1f Punkte, "
                            + "größter Schritt im Bild %.1f Punkte, %d Sprünge", what, this.frames, this.drawnFrames, this.edgeFrames, MIN_SPEED,
                    this.fastFrames, this.fastHidden, this.toggles, this.maxError, this.compared, this.simError, this.maxStep, this.jumps);
        }

        @Nullable String problem(Minecraft mc, String what, boolean edgeAllowed) {
            OsdGrid grid = FlightPathMarkerSelfTests.grid(mc);
            if (this.fastFrames < 10) {
                return what + ": zu wenig Fahrt";
            }
            if (this.fastHidden > 0 || this.toggles > 0) {
                return what + ": Marker flackert";
            }
            if (this.compared < this.drawnFrames / 2 || !(this.maxError < Math.max(8.0, grid.height() * 0.05))) {
                return what + ": Marker weicht von der Projektion ab";
            }
            if (this.jumps > 0) {
                return what + ": Marker springt";
            }
            if (!edgeAllowed && this.edgeFrames > 0) {
                return what + ": Marker unerwartet am Rand";
            }
            if (!edgeAllowed && !(this.simError < grid.height() * 0.15)) {
                return what + ": Marker weicht von der Projektion der Sim-Geschwindigkeit ab";
            }
            return null;
        }
    }

    private static final double JUMP_SHARE = 0.2;

    private static double speed() {
        Vector3d v = FlightPathMarkerSelfTests.simVelocity();
        return v.isFinite() ? v.length() : 0.0;
    }

    private static void input(float roll, float pitch, float yaw, float throttle, StabMode mode) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(roll, pitch, yaw, throttle), true, mode);
    }

    private static float holdThrottle(double target) {
        Telemetry t = FlightPathMarkerSelfTests.telemetry();
        double hover = t == null ? 0.3 : Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.clamp(1.2 * (target - FlightPathMarkerSelfTests.height()), -3.0, 3.0);
        return (float) Math.clamp(hover + 0.06 * (wanted - vs) + 0.04, 0.05, 0.95);
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RESULT)) {
            state.put(RESULT, result);
            PilotSession.INSTANCE.clearTestInput();
            OsdHud.sampler().setOverride(null);
            FlightPathMarkerSelfTests.restoreOsd();
            Object saved = state.get("video");
            if (saved instanceof VideoConfig video) {
                VideoSettings.CONFIG.set(video);
            }
            FpvLens.INSTANCE.testStrength(0);
            state.put(FUTURE, ctx.onServer(FlightPathMarkerSelfTests::release));
            return next(ctx, "cleanup");
        }
        return StepResult.pending();
    }

    private static StepResult finishCleanup(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future != null && !future.isDone() || PilotSession.INSTANCE.isActive()) {
            return since(ctx) > 200 ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
        }
        return (StepResult) ctx.state().get(RESULT);
    }

    private static @Nullable StepResult startFpv(SelfTest.StepContext ctx, String phase, String then) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        int since = since(ctx);
        switch (phase) {
            case "spawn" -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                FlightPathMarkerSelfTests.showMarker();
                state.put(FUTURE, ctx.onServer(FlightPathMarkerSelfTests::spawnAndFly));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return cleanup(ctx, StepResult.fail("Start: " + future.exceptionNow().getMessage()));
                }
                if (!future.isDone() || !FlightPathMarkerSelfTests.flying() || !VideoState.INSTANCE.active()) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
                }
                return next(ctx, "arm");
            }
            case "arm" -> {
                FlightPathMarkerSelfTests.arm(since);
                return since >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
            }
            case "climb" -> {
                FlightPathMarkerSelfTests.hover();
                boolean high = Math.abs(FlightPathMarkerSelfTests.height() - FlightPathMarkerSelfTests.FLIGHT_HEIGHT) < 0.5;
                if (high && since >= 60) {
                    return next(ctx, then);
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Höhe %.1f nicht erreicht",
                        FlightPathMarkerSelfTests.height()))) : StepResult.pending();
            }
            default -> {
                return null;
            }
        }
    }

    private static StepResult motionStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        String phase = phase(ctx, "spawn");
        int since = since(ctx);
        if (!phase.equals("spawn") && !phase.equals("fpv") && !phase.equals("cleanup") && !FlightPathMarkerSelfTests.flying()) {
            return cleanup(ctx, StepResult.fail("FPV-Sitzung endete in Phase " + phase));
        }
        StepResult started = startFpv(ctx, phase, "accelerate");
        if (started != null) {
            return started;
        }
        switch (phase) {
            case "accelerate" -> {
                FlightPathMarkerSelfTests.fly(FlightPathMarkerSelfTests.FORWARD_PITCH);
                if (since >= 40 && FlightPathMarkerSelfTests.forwardSpeed() >= FlightPathMarkerSelfTests.CRUISE_SPEED) {
                    state.put("drift", new Track());
                    return next(ctx, "drift");
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("Vorwärtsflug erreicht nicht 6 m/s")) : StepResult.pending();
            }
            case "drift" -> {
                FlightPathMarkerSelfTests.fly(0.05F, since < SEGMENT_TICKS / 2 ? 0.35F : -0.35F);
                Track track = (Track) state.get("drift");
                if (since >= 4) {
                    track.sample(mc, speed());
                }
                if (since < SEGMENT_TICKS + 4) {
                    return StepResult.pending();
                }
                state.put("turn", new Track());
                return next(ctx, "turn");
            }
            case "turn" -> {
                input(0.45F, 0.15F, 0.25F, holdThrottle(FlightPathMarkerSelfTests.FLIGHT_HEIGHT), StabMode.ANGLE);
                Track track = (Track) state.get("turn");
                if (since >= 4) {
                    track.sample(mc, speed());
                }
                if (since < SEGMENT_TICKS * 3 / 2 + 4) {
                    return StepResult.pending();
                }
                return next(ctx, "loop_climb");
            }
            case "loop_climb" -> {
                FlightPathMarkerSelfTests.fly(FlightPathMarkerSelfTests.FORWARD_PITCH);
                double h = FlightPathMarkerSelfTests.height();
                if (since >= 30 && FlightPathMarkerSelfTests.forwardSpeed() >= FlightPathMarkerSelfTests.CRUISE_SPEED && h > 3.5) {
                    state.put("loop", new Track());
                    state.put("inverted", false);
                    return next(ctx, "loop");
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("kein Anlauf für den Powerloop")) : StepResult.pending();
            }
            case "loop" -> {
                QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                Vector3f up = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 1.0F, 0.0F));
                boolean inverted = (boolean) state.get("inverted");
                if (up.y < -0.5F) {
                    state.put("inverted", true);
                    inverted = true;
                }
                Track track = (Track) state.get("loop");
                track.sample(mc, speed());
                if (inverted && up.y > 0.6F) {
                    state.put("loop_ticks", since);
                    return next(ctx, "recover");
                }
                input(0.0F, -1.0F, 0.0F, 0.85F, StabMode.ACRO);
                return since > 120 ? cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Powerloop nicht geschlossen (oben %.2f, kopfüber %b)", up.y,
                        inverted))) : StepResult.pending();
            }
            case "recover" -> {
                FlightPathMarkerSelfTests.hover();
                Track loop = (Track) state.get("loop");
                if (since < 5) {
                    loop.sample(mc, speed());
                    return StepResult.pending();
                }
                Track drift = (Track) state.get("drift");
                Track turn = (Track) state.get("turn");
                String problem = drift.problem(mc, "Drift", false);
                if (problem == null) {
                    problem = turn.problem(mc, "enge Kurve", true);
                }
                if (problem == null) {
                    problem = loop.problem(mc, "Powerloop", true);
                }
                String log = drift.text("Drift (Rollen ohne Gieren)") + "; " + turn.text("enge Kurve") + "; " + loop.text("Powerloop in "
                        + state.get("loop_ticks") + " Ticks");
                if (problem != null) {
                    return cleanup(ctx, StepResult.fail(problem + " – " + log));
                }
                ctx.log("osd: Flugbahn-Marker ohne Sprünge – " + log);
                return cleanup(ctx, StepResult.pass());
            }
            case "cleanup" -> {
                return finishCleanup(ctx);
            }
            default -> {
                return cleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private record LensCase(int percent, ImageFormat format) {
        String label() {
            return this.percent + " % " + (this.format == ImageFormat.FOUR_THREE ? "4:3" : "16:9");
        }
    }

    private static final List<LensCase> LENSES = List.of(new LensCase(0, ImageFormat.FOUR_THREE), new LensCase(0, ImageFormat.WIDE),
            new LensCase(80, ImageFormat.FOUR_THREE), new LensCase(80, ImageFormat.WIDE), new LensCase(100, ImageFormat.FOUR_THREE),
            new LensCase(100, ImageFormat.WIDE));
    private static final double[][] TARGETS = {{0.0, 0.0}, {0.85, 0.0}, {-0.85, 0.0}, {0.0, 0.85}, {0.0, -0.85}, {0.7, 0.7}, {-0.7, -0.6}};

    private static final class AimSource implements OsdSampler.Source {
        volatile double sx;
        volatile double sy;

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            d.videoSystem = VideoSystem.DIGITAL;
            d.videoSignal = 1.0;
            d.videoLost = false;
            Vector3f world = direction(Minecraft.getInstance(), this.sx, this.sy);
            if (world == null) {
                d.clearVelocity();
                return;
            }
            d.velocityX = world.x * 12.0;
            d.velocityY = world.y * 12.0;
            d.velocityZ = world.z * 12.0;
        }
    }

    private static float[] target(Minecraft mc, double sx, double sy) {
        OsdGrid grid = FlightPathMarkerSelfTests.grid(mc);
        float margin = grid.cellHeight() * 1.5F;
        float halfW = grid.width() * 0.5F - margin;
        float halfH = grid.height() * 0.5F - margin;
        return new float[]{grid.centerX() + (float) sx * halfW, grid.centerY() - (float) sy * halfH};
    }

    private static @Nullable Vector3f direction(Minecraft mc, double sx, double sy) {
        OsdRenderer.View view = OsdHud.liveView(mc);
        if (view == null) {
            return null;
        }
        OsdGrid grid = FlightPathMarkerSelfTests.grid(mc);
        float[] t = target(mc, sx, sy);
        double dx = t[0] - grid.centerX();
        double dy = grid.centerY() - t[1];
        double r = Math.hypot(dx, dy);
        FisheyeLens lens = FpvLens.INSTANCE.rendered();
        Vector3f cam;
        if (lens != null) {
            double focalGui = lens.focal() * grid.width() / lens.imageWidth();
            double theta = FisheyeLens.unmap(r / focalGui, lens.bend());
            double phi = Math.atan2(dy, dx);
            cam = new Vector3f((float) (Math.sin(theta) * Math.cos(phi)), (float) (Math.sin(theta) * Math.sin(phi)), (float) -Math.cos(theta));
        } else {
            double focal = grid.height() * 0.5 / Math.tan(Math.toRadians(view.fovDeg()) * 0.5);
            cam = new Vector3f((float) dx, (float) dy, (float) -focal).normalize();
        }
        return view.rotation().transform(cam, new Vector3f());
    }

    private static StepResult lensStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        String phase = phase(ctx, "spawn");
        int since = since(ctx);
        if (!phase.equals("spawn") && !phase.equals("fpv") && !phase.equals("cleanup") && !FlightPathMarkerSelfTests.flying()) {
            return cleanup(ctx, StepResult.fail("FPV-Sitzung endete in Phase " + phase));
        }
        if (!state.containsKey("video")) {
            state.put("video", VideoSettings.get().copy());
            state.put("lens_index", 0);
            state.put("lens_log", new ArrayList<String>());
        }
        StepResult started = startFpv(ctx, phase, "setup");
        if (started != null) {
            return started;
        }
        @SuppressWarnings("unchecked")
        List<String> log = (List<String>) state.get("lens_log");
        int index = (int) state.get("lens_index");
        switch (phase) {
            case "setup" -> {
                FlightPathMarkerSelfTests.hover();
                if (index >= LENSES.size()) {
                    ctx.log("osd: Flugbahn-Marker – Fisheye und Bildformat: " + String.join("; ", log));
                    return cleanup(ctx, StepResult.pass());
                }
                LensCase lens = LENSES.get(index);
                VideoConfig config = ((VideoConfig) state.get("video")).copy();
                config.imageFormat = lens.format();
                VideoSettings.CONFIG.set(config);
                FpvLens.INSTANCE.testStrength(lens.percent());
                AimSource aim = new AimSource();
                state.put("aim", aim);
                state.put("target", 0);
                state.put("worst", 0.0);
                OsdHud.sampler().setOverride(aim);
                return next(ctx, "settle");
            }
            case "settle" -> {
                FlightPathMarkerSelfTests.hover();
                LensCase lens = LENSES.get(index);
                boolean ready = lens.percent() == 0 ? FpvLens.INSTANCE.rendered() == null : FpvLens.INSTANCE.rendered() != null
                        && FpvLens.INSTANCE.rendered().percent() == lens.percent();
                if (since >= 20 && ready) {
                    return next(ctx, "aim");
                }
                return since > 200 ? cleanup(ctx, StepResult.fail("Objektiv " + lens.label() + " wird nicht gerendert")) : StepResult.pending();
            }
            case "aim" -> {
                FlightPathMarkerSelfTests.hover();
                AimSource aim = (AimSource) state.get("aim");
                int target = (int) state.get("target");
                aim.sx = TARGETS[target][0];
                aim.sy = TARGETS[target][1];
                if (since < 3) {
                    return StepResult.pending();
                }
                LensCase lens = LENSES.get(index);
                if (!FlightPathMarkerSelfTests.drawn()) {
                    return since > 40 ? cleanup(ctx, StepResult.fail("Marker bei " + lens.label() + " zum Ziel " + target + " nicht gezeichnet"))
                            : StepResult.pending();
                }
                FlightPathMarker.State shown = FlightPathMarkerSelfTests.shown();
                float[] expected = target(mc, aim.sx, aim.sy);
                double error = Math.hypot(shown.x - expected[0], shown.y - expected[1]);
                if (!shown.inView || error > 2.0) {
                    return cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Objektiv %s, Ziel %.2f/%.2f: Marker bei %.1f/%.1f (%s) statt %.1f/%.1f "
                                    + "(Abweichung %.2f Punkte)", lens.label(), aim.sx, aim.sy, shown.x, shown.y, shown.inView ? "im Bild" : "am Rand",
                            expected[0], expected[1], error)));
                }
                state.put("worst", Math.max((double) state.get("worst"), error));
                if (target + 1 < TARGETS.length) {
                    state.put("target", target + 1);
                    return next(ctx, "aim");
                }
                FisheyeLens rendered = FpvLens.INSTANCE.rendered();
                log.add(String.format(Locale.ROOT, "%s (%s): %d Ziele bis 85 %% zum Rand, größte Abweichung %.2f Punkte", lens.label(),
                        rendered == null ? "flach" : String.format(Locale.ROOT, "%.0f° gerendert, Biegung %.2f", rendered.horizontalDeg(), rendered.bend()),
                        TARGETS.length, (double) state.get("worst")));
                state.put("lens_index", index + 1);
                OsdHud.sampler().setOverride(null);
                return next(ctx, "setup");
            }
            case "cleanup" -> {
                return finishCleanup(ctx);
            }
            default -> {
                return cleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static void spawnLos(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        releaseLos(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x + 2.0, feet.y + 0.1, feet.z - 4.0, 180.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        LOS_DRONE.set(drone.getId());
        if (losHand == null) {
            losHand = player.getMainHandItem().copy();
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.isEmpty()) {
            throw new IllegalStateException("Spieler trägt eine Brille, Sichtflug nicht möglich");
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Sichtflug verweigert: " + result);
        }
    }

    private static void releaseLos(MinecraftServer server) {
        int id = LOS_DRONE.getAndSet(-1);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ItemStack hand = losHand;
            if (hand != null && id >= 0) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
                losHand = null;
            }
        }
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
    }

    private static double losHeight(Minecraft mc) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null || mc.player == null ? 0.0 : drone.getY() - mc.player.getY();
    }

    private static StepResult losStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        String phase = phase(ctx, "spawn");
        int since = since(ctx);
        switch (phase) {
            case "spawn" -> {
                if (session.isActive()) {
                    session.requestStop();
                    return since > 200 ? StepResult.fail("alte Sitzung endet nicht") : StepResult.pending();
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                FlightPathMarkerSelfTests.showMarker();
                state.put(FUTURE, ctx.onServer(CovFlightPathMarkerSelfTests::spawnLos));
                return next(ctx, "los");
            }
            case "los" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return losCleanup(ctx, StepResult.fail("Start: " + future.exceptionNow().getMessage()));
                }
                if (!future.isDone() || !session.isLineOfSight() || session.sim() == null) {
                    return since > TIMEOUT_TICKS ? losCleanup(ctx, StepResult.fail("kein Sichtflug")) : StepResult.pending();
                }
                return next(ctx, "arm");
            }
            case "arm" -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= ARM_TICKS / 2, StabMode.ANGLE);
                return since >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
            }
            case "climb" -> {
                Telemetry t = FlightPathMarkerSelfTests.telemetry();
                double hover = t == null ? 0.3 : t.hoverThrottleStick;
                double vs = t == null ? 0.0 : t.verticalSpeed;
                float throttle = (float) Math.clamp(hover + 0.06 * (Math.clamp(1.2 * (4.0 - losHeight(mc)), -3.0, 3.0) - vs), 0.05, 0.9);
                session.setTestInput(new StickPositions(0.0F, losHeight(mc) > 3.0 ? FlightPathMarkerSelfTests.FORWARD_PITCH : 0.0F, 0.0F, throttle), true,
                        StabMode.ANGLE);
                if (losHeight(mc) > 3.0 && FlightPathMarkerSelfTests.horizontalSpeed() >= FlightPathMarkerSelfTests.CRUISE_SPEED) {
                    state.put("frames", RadioHud.renderedFrames());
                    state.put("drawn", 0);
                    state.put("speed", Double.POSITIVE_INFINITY);
                    return next(ctx, "measure");
                }
                return since > TIMEOUT_TICKS ? losCleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Sichtflug erreicht 6 m/s nicht: %.1f m/s, Höhe %.1f",
                        FlightPathMarkerSelfTests.horizontalSpeed(), losHeight(mc)))) : StepResult.pending();
            }
            case "measure" -> {
                Telemetry t = FlightPathMarkerSelfTests.telemetry();
                double hover = t == null ? 0.3 : t.hoverThrottleStick;
                double vs = t == null ? 0.0 : t.verticalSpeed;
                float throttle = (float) Math.clamp(hover + 0.06 * (Math.clamp(1.2 * (4.0 - losHeight(mc)), -3.0, 3.0) - vs) + 0.02, 0.05, 0.9);
                session.setTestInput(new StickPositions(0.0F, FlightPathMarkerSelfTests.FORWARD_PITCH, 0.0F, throttle), true, StabMode.ANGLE);
                if (FlightPathMarkerSelfTests.drawn()) {
                    state.put("drawn", (int) state.get("drawn") + 1);
                }
                state.put("speed", Math.min((double) state.get("speed"), FlightPathMarkerSelfTests.horizontalSpeed()));
                if (since < 40) {
                    return StepResult.pending();
                }
                int hudFrames = RadioHud.renderedFrames() - (int) state.get("frames");
                int drawn = (int) state.get("drawn");
                String numbers = String.format(Locale.ROOT, "40 Ticks Sichtflug mit mindestens %.1f m/s, Funk-HUD %d Bilder, Marker in %d Ticks gezeichnet",
                        (double) state.get("speed"), hudFrames, drawn);
                if (!session.isLineOfSight()) {
                    return losCleanup(ctx, StepResult.fail("Sichtflug endete: " + numbers));
                }
                if (hudFrames <= 0) {
                    return losCleanup(ctx, StepResult.fail("Sichtflug-HUD nicht gezeichnet: " + numbers));
                }
                if (drawn > 0) {
                    return losCleanup(ctx, StepResult.fail("Flugbahn-Marker im Sichtflug gezeichnet: " + numbers));
                }
                ctx.log("osd: Flugbahn-Marker nicht im Sichtflug-HUD – " + numbers);
                return losCleanup(ctx, StepResult.pass());
            }
            case "cleanup" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future != null && !future.isDone() || session.isActive()) {
                    return since > 200 ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
                }
                return (StepResult) state.get(RESULT);
            }
            default -> {
                return losCleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static StepResult losCleanup(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RESULT)) {
            state.put(RESULT, result);
            PilotSession.INSTANCE.clearTestInput();
            FlightPathMarkerSelfTests.restoreOsd();
            state.put(FUTURE, ctx.onServer(CovFlightPathMarkerSelfTests::releaseLos));
            return next(ctx, "cleanup");
        }
        return StepResult.pending();
    }

    private static final class EdgeScene implements Screenshots.Scene {
        private final float pitch;
        private final boolean backwards;

        EdgeScene(float pitch, boolean backwards) {
            this.pitch = pitch;
            this.backwards = backwards;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            Minecraft mc = Minecraft.getInstance();
            String phase = phase(ctx, "spawn");
            int since = since(ctx);
            if (!phase.equals("spawn") && !phase.equals("fpv") && !FlightPathMarkerSelfTests.flying()) {
                return StepResult.fail("FPV-Sitzung endete in Phase " + phase);
            }
            switch (phase) {
                case "spawn" -> {
                    if (mc.gui.screen() != null) {
                        mc.gui.setScreen(null);
                    }
                    OsdConfig config = OsdClient.CONFIG.get().copy();
                    config.slot(OsdElement.FLIGHT_PATH_MARKER).enabled = true;
                    config.slot(OsdElement.HOME_DIRECTION).enabled = true;
                    config.slot(OsdElement.HOME_MARKER).enabled = true;
                    OsdHud.overrideConfig(config);
                    state.put(FUTURE, ctx.onServer(FlightPathMarkerSelfTests::spawnAndFly));
                    return next(ctx, "fpv");
                }
                case "fpv" -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (future.isCompletedExceptionally()) {
                        return StepResult.fail("Start: " + future.exceptionNow().getMessage());
                    }
                    if (!future.isDone() || !FlightPathMarkerSelfTests.flying() || !VideoState.INSTANCE.active()) {
                        return since > TIMEOUT_TICKS ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                    }
                    return next(ctx, "arm");
                }
                case "arm" -> {
                    FlightPathMarkerSelfTests.arm(since);
                    return since >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
                }
                case "climb" -> {
                    FlightPathMarkerSelfTests.hover();
                    if (Math.abs(FlightPathMarkerSelfTests.height() - FlightPathMarkerSelfTests.FLIGHT_HEIGHT) < 0.5 && since >= 60) {
                        return next(ctx, "go");
                    }
                    return since > TIMEOUT_TICKS ? StepResult.fail("Höhe nicht erreicht") : StepResult.pending();
                }
                default -> {
                }
            }
            if (this.backwards) {
                FlightPathMarkerSelfTests.fly(this.pitch);
                FlightPathMarker.State shown = FlightPathMarkerSelfTests.shown();
                if (since >= 40 && FlightPathMarkerSelfTests.forwardSpeed() <= -4.0 && FlightPathMarkerSelfTests.drawn() && !shown.inView
                        && shown.alpha >= 1.0F) {
                    ctx.log(String.format(Locale.ROOT, "rückwärts %.1f m/s, Marker am Rand bei %.0f/%.0f, Home-Pfeil gezeichnet %b, Home-Marker %b",
                            FlightPathMarkerSelfTests.forwardSpeed(), shown.x, shown.y, OsdHud.drewLastFrame(OsdElement.HOME_DIRECTION),
                            OsdHud.drewLastFrame(OsdElement.HOME_MARKER)));
                    return StepResult.pass();
                }
                return since > TIMEOUT_TICKS ? StepResult.fail("Szene rückwärts nicht erreicht") : StepResult.pending();
            }
            input(0.0F, 0.0F, 0.0F, 0.0F, StabMode.ANGLE);
            FlightPathMarker.State shown = FlightPathMarkerSelfTests.shown();
            Vector3d v = FlightPathMarkerSelfTests.simVelocity();
            if (since >= 6 && v.isFinite() && v.y < -4.0 && FlightPathMarkerSelfTests.drawn()) {
                ctx.log(String.format(Locale.ROOT, "Sturzflug %.1f m/s abwärts, Marker %s bei %.0f/%.0f", -v.y, shown.inView ? "im Bild" : "am Rand", shown.x,
                        shown.y));
                return StepResult.pass();
            }
            return since > 120 ? StepResult.fail("Sturzflug nicht erreicht") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (FlightPathMarkerSelfTests.flying()) {
                if (this.backwards) {
                    FlightPathMarkerSelfTests.fly(this.pitch);
                } else {
                    input(0.0F, 0.0F, 0.0F, 0.0F, StabMode.ANGLE);
                }
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            FlightPathMarkerSelfTests.restoreOsd();
            ctx.onServer(FlightPathMarkerSelfTests::release);
        }
    }
}

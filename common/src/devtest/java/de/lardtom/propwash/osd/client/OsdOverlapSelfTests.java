package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.ImageFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OsdOverlapSelfTests {
    public static final String STEP = "osd: Vorlagen ohne Überlappung";
    public static final String STEP_GERMAN = STEP + " auf Deutsch";
    private static final String LANGUAGE = "osd: Sprache Deutsch für die Vorlagen";
    private static final String LANGUAGE_RESET = "osd: Sprache nach den Vorlagen zurücksetzen";
    private static final float EPSILON = 0.01F;
    private static final float OFFSCREEN = -100_000.0F;
    private static final int LOG_LIMIT = 40;
    private static final List<Window> WINDOWS = List.of(
            new Window(1280, 720, 1, 3), new Window(1920, 1080, 1, 4), new Window(2560, 1440, 2, 6), new Window(3840, 2160, 3, 9),
            new Window(1440, 900, 1, 3), new Window(3024, 1964, 2, 8), new Window(1024, 768, 1, 3), new Window(1600, 1200, 1, 5),
            new Window(1280, 1024, 1, 4), new Window(2560, 2048, 2, 8));

    private OsdOverlapSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 1200, OsdOverlapSelfTests::overlapStep);
        SelfTest.switchStep(LANGUAGE, LANGUAGE_RESET, 1200, ctx -> Screenshots.language(ctx, "de_de"));
        SelfTest.clientStep(STEP_GERMAN, 1200, OsdOverlapSelfTests::overlapStep);
        SelfTest.clientStep(LANGUAGE_RESET, 1200, Screenshots::restoreLanguage);
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step("bildschirmfotos: OSD-Editor Minimal mit eingeschalteter Geschwindigkeit", "configurator_osd_minimal_speed", 30,
                new SpeedScene());
    }

    public static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        for (Window window : WINDOWS) {
            for (int scale = window.minScale(); scale <= window.maxScale(); scale++) {
                out.add(new Case(window, scale, false));
                if ((double) window.width() / window.height() > ImageFormat.FOUR_THREE_ASPECT + 1.0E-3) {
                    out.add(new Case(window, scale, true));
                }
            }
        }
        return out;
    }

    public static List<OsdElement> checked() {
        List<OsdElement> out = new ArrayList<>();
        for (OsdElement element : OsdElement.values()) {
            if (element == OsdElement.CROSSHAIR || (element.movable() && element != OsdElement.POST_FLIGHT_STATS)) {
                out.add(element);
            }
        }
        return out;
    }

    public static OsdConfig allAtDefaults(OsdPreset preset) {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(preset);
        for (OsdElement element : checked()) {
            config.slot(element).enabled = true;
        }
        return config;
    }

    public static List<OsdData> variants() {
        OsdData armed = OsdData.sample();
        armed.armedSeconds = 0.5;
        armed.ledsOff = true;
        armed.vtxPowerMw = 1200;
        armed.vtxPowerAuto = true;
        armed.vtxChannel = "R8";
        armed.relayHops = OsdData.MAX_RELAY_HOPS - 1;
        for (int i = 0; i < OsdData.MAX_RELAY_HOPS; i++) {
            armed.relayHopQuality[i] = 0.88;
        }
        armed.trickType = TrickType.BACKWARDS_DIVE;
        OsdData wide = OsdData.sample();
        wide.armed = false;
        wide.ledsOff = true;
        wide.videoSystem = VideoSystem.DIGITAL;
        wide.vtxPowerMw = 1200;
        wide.vtxChannel = "R8";
        wide.relayHops = 1;
        wide.trickPopup = OsdData.TRICK_LOST;
        wide.recording = OsdData.REC_SAVING;
        wide.recordingPercent = 100;
        wide.flashbackFill = 9.99;
        wide.flashbackLimit = false;
        wide.speed = 50.0;
        wide.altitude = 320.0;
        wide.verticalSpeed = -12.5;
        wide.gLoad = 12.5;
        wide.currentA = 142.6;
        wide.powerW = 2480.0;
        wide.mAhUsed = 1875.0;
        wide.packVoltage = 25.2;
        wide.cellVoltage = 4.2;
        wide.linkStrength = 0.1;
        wide.videoSignal = 1.0;
        wide.x = -12345.6;
        wide.y = -64.0;
        wide.z = -23456.7;
        wide.homeX = -10900.0;
        wide.homeZ = -22100.0;
        wide.flightSeconds = 754.0;
        wide.onSeconds = 3754.0;
        wide.lap = 12;
        wide.currentLapNanos = 75_320_000_000L;
        wide.lastLapNanos = 75_870_000_000L;
        wide.bestLapNanos = 74_950_000_000L;
        wide.nextGate = 10;
        wide.gateCount = 12;
        wide.splitDeltaNanos = 12_340_000_000L;
        wide.throttle = 1.0;
        wide.clockHour = 23;
        wide.clockMinute = 59;
        wide.gpsSatellites = 24;
        return List.of(armed, wide);
    }

    public static Map<OsdElement, float[]> footprints(GuiGraphicsExtractor graphics, OsdConfig config, Case c) {
        Minecraft minecraft = Minecraft.getInstance();
        OsdGrid grid = c.grid();
        float textScale = OsdGrid.snapToPixels(grid.textScale(config.scale), c.scale());
        Map<OsdElement, float[]> out = new EnumMap<>(OsdElement.class);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(OFFSCREEN, OFFSCREEN);
            for (OsdData data : variants()) {
                OsdRenderer renderer = new OsdRenderer();
                renderer.setPixelScale(c.scale());
                renderer.render(graphics, minecraft.font, data, config, grid, textScale, null, true);
                for (OsdElement element : checked()) {
                    if (!renderer.wasDrawn(element)) {
                        continue;
                    }
                    float[] b = renderer.bounds(element);
                    float[] u = out.get(element);
                    if (u == null) {
                        out.put(element, b.clone());
                    } else {
                        u[0] = Math.min(u[0], b[0]);
                        u[1] = Math.min(u[1], b[1]);
                        u[2] = Math.max(u[2], b[2]);
                        u[3] = Math.max(u[3], b[3]);
                    }
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
        return out;
    }

    public static List<String> overlaps(Map<OsdElement, float[]> boxes) {
        List<String> out = new ArrayList<>();
        List<OsdElement> elements = new ArrayList<>(boxes.keySet());
        for (int i = 0; i < elements.size(); i++) {
            float[] a = boxes.get(elements.get(i));
            for (int j = i + 1; j < elements.size(); j++) {
                float[] b = boxes.get(elements.get(j));
                float w = Math.min(a[2], b[2]) - Math.max(a[0], b[0]);
                float h = Math.min(a[3], b[3]) - Math.max(a[1], b[1]);
                if (w > EPSILON && h > EPSILON) {
                    out.add(elements.get(i).key() + "/" + elements.get(j).key());
                }
            }
        }
        return out;
    }

    private static StepResult overlapStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            state.put("cases", cases());
            state.put("index", 0);
            state.put("found", new LinkedHashMap<String, String>());
            state.put("missing", new ArrayList<String>());
            state.put("widths", new EnumMap<OsdElement, Float>(OsdElement.class));
            state.put("heights", new EnumMap<OsdElement, Float>(OsdElement.class));
        }
        @SuppressWarnings("unchecked")
        List<Case> all = (List<Case>) state.get("cases");
        @SuppressWarnings("unchecked")
        Map<String, String> found = (Map<String, String>) state.get("found");
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) state.get("missing");
        @SuppressWarnings("unchecked")
        Map<OsdElement, Float> widths = (Map<OsdElement, Float>) state.get("widths");
        @SuppressWarnings("unchecked")
        Map<OsdElement, Float> heights = (Map<OsdElement, Float>) state.get("heights");
        AtomicBoolean busy = (AtomicBoolean) state.get("busy");
        if (busy != null && busy.get()) {
            return StepResult.pending();
        }
        int index = (Integer) state.get("index");
        if (index < all.size()) {
            Case c = all.get(index);
            AtomicBoolean running = new AtomicBoolean(true);
            state.put("busy", running);
            state.put("index", index + 1);
            OsdHud.probeNextFrame(graphics -> {
                try {
                    for (OsdPreset preset : OsdPreset.values()) {
                        Map<OsdElement, float[]> boxes = footprints(graphics, allAtDefaults(preset), c);
                        for (OsdElement element : checked()) {
                            float[] b = boxes.get(element);
                            if (b == null) {
                                missing.add(preset.key() + ":" + element.key());
                                continue;
                            }
                            float cells = (b[2] - b[0]) / c.grid().cellWidth();
                            widths.merge(element, cells, Math::max);
                            heights.merge(element, (b[3] - b[1]) / c.grid().cellHeight(), Math::max);
                        }
                        for (String pair : overlaps(boxes)) {
                            found.putIfAbsent(preset.key() + " " + pair, c.toString());
                        }
                    }
                } finally {
                    running.set(false);
                }
            });
            return StepResult.pending();
        }
        StringBuilder table = new StringBuilder("osd: größte Breite in Rasterzellen:");
        for (Map.Entry<OsdElement, Float> entry : widths.entrySet()) {
            table.append(' ').append(entry.getKey().key()).append('=').append(String.format(Locale.ROOT, "%.1f", entry.getValue()));
        }
        ctx.log(table.toString());
        StringBuilder rows = new StringBuilder("osd: größte Höhe in Rasterzeilen:");
        for (Map.Entry<OsdElement, Float> entry : heights.entrySet()) {
            rows.append(' ').append(entry.getKey().key()).append('=').append(String.format(Locale.ROOT, "%.2f", entry.getValue()));
        }
        ctx.log(rows.toString());
        if (!missing.isEmpty()) {
            return StepResult.fail("nicht gezeichnet: " + missing.subList(0, Math.min(10, missing.size())));
        }
        if (!found.isEmpty()) {
            int shown = 0;
            for (Map.Entry<String, String> entry : found.entrySet()) {
                if (shown++ >= LOG_LIMIT) {
                    break;
                }
                ctx.log("osd: Überlappung " + entry.getKey() + " bei " + entry.getValue());
            }
            return StepResult.fail(found.size() + " Überlappungen in den Standardplätzen der Vorlagen, z. B. " + found.keySet().iterator().next());
        }
        ctx.log("osd: " + OsdPreset.values().length + " Vorlagen mit allen " + checked().size() + " Elementen auf ihren Standardplätzen, "
                + all.size() + " Bildformate und GUI-Skalen ohne Überlappung, Sprache " + Minecraft.getInstance().getLanguageManager().getSelected());
        return StepResult.pass();
    }

    public record Window(int width, int height, int minScale, int maxScale) {
    }

    public record Case(Window window, int scale, boolean fourThree) {
        public OsdGrid grid() {
            int screenWidth = Math.max(1, (int) Math.ceil((double) this.window.width() / this.scale));
            int screenHeight = Math.max(1, (int) Math.ceil((double) this.window.height() / this.scale));
            if (!this.fourThree) {
                return OsdGrid.screen(screenWidth, screenHeight);
            }
            ImageArea area = ImageArea.of(ImageFormat.FOUR_THREE, this.window.width(), this.window.height());
            float s = this.scale;
            return new OsdGrid(area.x() / s, area.y() / s, area.width() / s, area.height() / s);
        }

        @Override
        public String toString() {
            return this.window.width() + "x" + this.window.height() + " GUI " + this.scale + (this.fourThree ? " 4:3" : "");
        }
    }

    private static final class SpeedScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Screenshots.parkMouse(mc);
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, OsdConfigTab.ID);
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            if (!(screen.activeTab() instanceof OsdConfigTab tab)) {
                screen.selectTab(OsdConfigTab.ID);
                return StepResult.pending();
            }
            OsdConfig working = tab.working();
            if (working.preset != OsdPreset.MINIMAL || !working.enabled(OsdElement.SPEED)) {
                working.applyPreset(OsdPreset.MINIMAL);
                working.slot(OsdElement.SPEED).enabled = true;
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
        }
    }
}

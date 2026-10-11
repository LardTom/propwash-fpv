package de.lardtom.propwash.osd.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdPalette;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class OsdSelfTests {
    private static final long FRAME_NANOS = 16_666_667L;
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
    private static final long GLYPH_FRAME_LIMIT_NANOS = 6_000_000L;
    private static final int GLYPH_WARMUP_TICKS = 20;
    private static final int GLYPH_MEASURE_TICKS = 130;
    private static final int GLYPH_UNICODE_TICKS = 40;
    private static final int GLYPH_SKIPPED_FRAMES = 3;
    private static final int GLYPH_STEP_TICKS = 500;
    private static final List<StyleVariant> VARIANTS = List.of(
            new StyleVariant(0, 1.0F, OsdConfig.Outline.THIN, OsdConfig.Background.OFF),
            new StyleVariant(50, 1.0F, OsdConfig.Outline.SHADOW, OsdConfig.Background.SUBTLE),
            new StyleVariant(100, 1.0F, OsdConfig.Outline.THICK, OsdConfig.Background.SOLID),
            new StyleVariant(75, 0.5F, OsdConfig.Outline.NONE, OsdConfig.Background.SOLID),
            new StyleVariant(75, 2.0F, OsdConfig.Outline.THIN, OsdConfig.Background.SUBTLE));

    private OsdSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("osd: HUD mit simulierter Sitzung", 300, OsdSelfTests::hudStep);
        SelfTest.clientStep("osd: HUD mit Deckkraft 0/50/100 % und Größe 0,5/2,0", 400, OsdSelfTests::styleStep);
        SelfTest.clientStep("osd: Breiten-Abweichung der Schrift ohne Nachladen und Ruckler", GLYPH_STEP_TICKS, OsdSelfTests::glyphMismatchStep);
        SelfTest.clientStep("osd: Editor-Tab", 200, OsdSelfTests::editorStep);
        SelfTest.clientStep("osd: Editor-Tab wendet Vorlagen an", 200, OsdSelfTests::presetStep);
        SelfTest.clientStep("osd: Funk-HUD im Sichtflug", 300, OsdSelfTests::radioHudStep);
        SelfTest.afterLeaveStep("osd: Zustand nach dem Verlassen", 40, ctx -> {
            OsdSampler sampler = OsdHud.sampler();
            if (sampler.hasOverride()) {
                return StepResult.fail("simulierte OSD-Quelle ist noch aktiv");
            }
            if (OsdHud.config() != OsdClient.CONFIG.get()) {
                return StepResult.fail("Test-Config des OSD ist noch aktiv");
            }
            return StepResult.check(!sampler.data().active, "OSD-Daten nach dem Verlassen noch aktiv");
        });
    }

    private static final AtomicReference<QuadcopterEntity> RADIO_DRONE = new AtomicReference<>();

    private static StepResult radioHudStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0 && mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        if (ctx.tick() == 0) {
            AtomicInteger droneId = new AtomicInteger(-1);
            state.put("drone", droneId);
            state.put("start", ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(player.level(), EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("Drohne nicht erzeugt");
                }
                drone.setBuild(DroneBuild.FREESTYLE);
                Vec3 pos = player.position().add(2.0, 0.1, 0.0);
                drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
                if (!player.level().addFreshEntity(drone)) {
                    throw new IllegalStateException("addFreshEntity lehnt die Drohne ab: " + drone.getUUID() + " bei " + pos
                            + ", gleiche UUID vorhanden: " + (player.level().getEntity(drone.getUUID()) != null));
                }
                droneId.set(drone.getId());
                RADIO_DRONE.set(drone);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                player.setItemInHand(InteractionHand.MAIN_HAND, remote);
                DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                if (result != DronePiloting.StartResult.STARTED) {
                    throw new IllegalStateException("Sichtflug nicht gestartet: " + result);
                }
            }));
            state.put("frames", RadioHud.renderedFrames());
            return StepResult.pending();
        }
        CompletableFuture<?> start = (CompletableFuture<?>) state.get("start");
        if (start.isCompletedExceptionally()) {
            return StepResult.fail(start.exceptionNow().getMessage());
        }
        if (state.containsKey("cleanup")) {
            CompletableFuture<?> cleanup = (CompletableFuture<?>) state.get("cleanup");
            if (!cleanup.isDone() || session.isActive()) {
                return StepResult.pending();
            }
            String problem = (String) state.get("checked");
            return problem.isEmpty() ? StepResult.pass() : StepResult.fail(problem);
        }
        if (!start.isDone() || !session.isLineOfSight()) {
            if (ctx.tick() <= 150) {
                return StepResult.pending();
            }
            int id = ((AtomicInteger) state.get("drone")).get();
            if (!state.containsKey("diag")) {
                AtomicReference<String> diag = new AtomicReference<>("");
                state.put("diag", diag);
                state.put("diagFuture", ctx.onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    Entity entity = player.level().getEntity(id);
                    QuadcopterEntity kept = RADIO_DRONE.get();
                    String removed = kept == null ? "?" : kept.getRemovalReason() + " bei " + kept.position() + ", Pilot " + kept.pilot()
                            + ", Akku " + kept.hasBattery();
                    diag.set(entity == null ? "Server: keine Entity (" + removed + "), Spieler bei " + player.position()
                            : "Server: " + entity + " entfernt=" + entity.getRemovalReason() + ", Spieler bei " + player.position());
                }));
                return StepResult.pending();
            }
            if (!((CompletableFuture<?>) state.get("diagFuture")).isDone()) {
                return StepResult.pending();
            }
            Entity seen = mc.level == null ? null : mc.level.getEntity(id);
            String pilot = seen instanceof QuadcopterEntity drone ? drone.pilot() + "/" + drone.pilotMode() : "keine Drohne";
            return StepResult.fail("keine Sichtflug-Sitzung (Server fertig " + start.isDone() + ", Client-Pilot " + pilot + ", Sitzung "
                    + session.mode() + ", Pause " + mc.isPaused() + ", Screen " + mc.gui.screen() + ", Client-Spieler bei "
                    + (mc.player == null ? "-" : mc.player.position()) + ", "
                    + ((AtomicReference<?>) state.get("diag")).get() + ")");
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
        if (!state.containsKey("checked")) {
            if (RadioHud.renderedFrames() - (int) state.get("frames") < 5) {
                return ctx.tick() > 200 ? StepResult.fail("Funk-HUD wird nicht gezeichnet") : StepResult.pending();
            }
            String problem = null;
            if (!RadioHud.active()) {
                problem = "Funk-HUD inaktiv";
            } else if (OsdHud.sampler().data().active) {
                problem = "Brillen-OSD im Sichtflug aktiv";
            } else if (OsdHud.hideVanilla()) {
                problem = "Vanilla-HUD im Sichtflug ausgeblendet";
            }
            state.put("checked", problem == null ? "" : problem);
            session.clearTestInput();
            session.requestStop();
            int id = ((AtomicInteger) state.get("drone")).get();
            state.put("cleanup", ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        entity.discard();
                    }
                }
                server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> cleanup = (CompletableFuture<?>) state.get("cleanup");
        if (!cleanup.isDone() || session.isActive()) {
            return StepResult.pending();
        }
        String problem = (String) state.get("checked");
        return problem.isEmpty() ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult hudStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            Player player = minecraft.player;
            if (player == null) {
                return StepResult.fail("kein Spieler");
            }
            SimulatedSource source = new SimulatedSource(player.getX(), player.getY(), player.getZ());
            state.put("source", source);
            state.put("frames", OsdHud.renderedFrames());
            state.put("errors", OsdHud.errors());
            OsdHud.sampler().setOverride(source);
            return StepResult.pending();
        }
        SimulatedSource source = (SimulatedSource) state.get("source");
        int errors = OsdHud.errors() - (Integer) state.get("errors");
        if (errors > 0) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail(errors + " OSD-Fehler beim Zeichnen (siehe Log)");
        }
        if (!OsdHud.hideVanilla() && OsdHud.config().hideVanillaHud) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail("Vanilla-HUD wird während der Sitzung nicht ausgeblendet");
        }
        int frames = OsdHud.renderedFrames() - (Integer) state.get("frames");
        OsdData data = OsdHud.sampler().data();
        if (frames >= 5 && source.steps >= 5 && data.active && data.hasSim) {
            if (!Double.isFinite(data.packVoltage) || !Double.isFinite(data.compassDeg)) {
                OsdHud.sampler().setOverride(null);
                return StepResult.fail("Telemetrie kam nicht im OSD an (Spannung " + data.packVoltage + ", Kurs " + data.compassDeg + ")");
            }
            ctx.log("osd: " + frames + " Frames, " + source.steps + " Sim-Schritte, scharf=" + data.armed + ", Akku " + data.packVoltage + " V");
            OsdHud.sampler().setOverride(null);
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    private static StepResult styleStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            Player player = Minecraft.getInstance().player;
            if (player == null) {
                return StepResult.fail("kein Spieler");
            }
            OsdHud.sampler().setOverride(new SimulatedSource(player.getX(), player.getY(), player.getZ()));
            state.put("errors", OsdHud.errors());
            state.put("variant", 0);
            state.put("frames", OsdHud.renderedFrames());
            OsdHud.overrideConfig(VARIANTS.getFirst().config());
            return StepResult.pending();
        }
        int variant = (Integer) state.get("variant");
        StyleVariant current = VARIANTS.get(variant);
        int errors = OsdHud.errors() - (Integer) state.get("errors");
        if (errors > 0) {
            endStyleStep();
            return StepResult.fail(errors + " OSD-Fehler bei " + current + " (siehe Log)");
        }
        OsdData data = OsdHud.sampler().data();
        if (!data.armed || data.showStats) {
            state.put("frames", OsdHud.renderedFrames());
            return StepResult.pending();
        }
        if (OsdHud.renderedFrames() - (Integer) state.get("frames") < 3) {
            return StepResult.pending();
        }
        OsdRenderer renderer = OsdHud.renderer();
        for (OsdElement element : List.of(OsdElement.LINK_QUALITY, OsdElement.CROSSHAIR, OsdElement.BATTERY_BAR, OsdElement.FLIGHT_TIME)) {
            if (!renderer.wasDrawn(element)) {
                endStyleStep();
                return StepResult.fail(element.key() + " wurde bei " + current + " nicht gezeichnet");
            }
        }
        float[] bounds = renderer.bounds(OsdElement.LINK_QUALITY);
        state.put("width" + variant, bounds[2] - bounds[0]);
        if (variant + 1 < VARIANTS.size()) {
            state.put("variant", variant + 1);
            state.put("frames", OsdHud.renderedFrames());
            OsdHud.overrideConfig(VARIANTS.get(variant + 1).config());
            return StepResult.pending();
        }
        endStyleStep();
        float small = (Float) state.get("width" + indexOfScale(0.5F));
        float large = (Float) state.get("width" + indexOfScale(2.0F));
        if (!(large > small * 2.5F)) {
            return StepResult.fail("Größe 2,0 zeichnet LQ nicht deutlich größer als 0,5 (" + small + " → " + large + ")");
        }
        ctx.log("osd: " + VARIANTS.size() + " Darstellungen gezeichnet, LQ-Breite " + small + " bei 0,5 und " + large + " bei 2,0");
        return StepResult.pass();
    }

    private static StepResult glyphMismatchStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft minecraft = Minecraft.getInstance();
        OsdGlyphs glyphs = OsdFonts.GLYPHS;
        OsdTextCache cache = OsdFonts.CACHE;
        if (ctx.tick() == 0) {
            Player player = minecraft.player;
            if (player == null) {
                return StepResult.fail("kein Spieler");
            }
            OsdConfig config = OsdConfig.defaults();
            config.applyPreset(OsdPreset.FULL);
            config.outline = OsdConfig.Outline.THIN;
            config.analogBreakup = false;
            OsdHud.overrideConfig(config);
            OsdHud.sampler().setOverride(new SimulatedSource(player.getX(), player.getY(), player.getZ()));
            FrameStats stats = new FrameStats();
            ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            boolean cpu = threads.isCurrentThreadCpuTimeSupported();
            OsdHud.timeFrames(new OsdHud.FrameTiming(cpu ? threads::getCurrentThreadCpuTime : System::nanoTime, stats::add));
            state.put("stats", stats);
            state.put("clock", cpu ? "CPU-Zeit" : "Wanduhr");
            state.put("errors", OsdHud.errors());
            state.put("unicode", minecraft.options.forceUnicodeFont().get());
            state.put("frames", OsdHud.renderedFrames());
            state.put("phase", "wait");
            return StepResult.pending();
        }
        FrameStats stats = (FrameStats) state.get("stats");
        boolean unicode = (Boolean) state.get("unicode");
        int errors = OsdHud.errors() - (Integer) state.get("errors");
        if (errors > 0) {
            return endGlyphStep(state, StepResult.fail(errors + " OSD-Fehler bei abweichender Schriftbreite (siehe Log)"));
        }
        String phase = (String) state.get("phase");
        if (ctx.tick() > GLYPH_STEP_TICKS - 20) {
            return endGlyphStep(state, StepResult.fail("Phase " + phase + " wurde nicht rechtzeitig fertig"));
        }
        switch (phase) {
            case "wait" -> {
                OsdGlyphs.Table table = glyphs.table();
                if (table == null || !table.options().equals(OsdFonts.options()) || OsdHud.renderedFrames() - (Integer) state.get("frames") < 3) {
                    return StepResult.pending();
                }
                if (OsdFonts.reloads() == 0) {
                    return endGlyphStep(state, StepResult.fail("Reload-Listener " + OsdFonts.RELOAD_LISTENER_ID + " lief beim Laden der Ressourcen nicht"));
                }
                if (glyphs.lastBuildThread() == Thread.currentThread()) {
                    return endGlyphStep(state, StepResult.fail("Glyphentabelle wurde im Zeichen-Thread aufgebaut"));
                }
                state.put("original", table);
                OsdGlyphs.Table skewed = new OsdGlyphs.Table(table.options(), skew(table.glyphs()));
                glyphs.install(skewed);
                state.put("skewed", skewed);
                state.put("loads", glyphs.loads());
                state.put("generation", glyphs.generation());
                state.put("unsupported", cache.unsupported());
                state.put("start", ctx.tick());
                state.put("phase", "warm");
                return StepResult.pending();
            }
            case "warm" -> {
                if (ctx.tick() - (Integer) state.get("start") < GLYPH_WARMUP_TICKS) {
                    return StepResult.pending();
                }
                state.put("clears", cache.clears());
                state.put("computed", cache.computed());
                state.put("frames", OsdHud.renderedFrames());
                state.put("start", ctx.tick());
                stats.reset(0);
                state.put("phase", "measure");
                return StepResult.pending();
            }
            case "measure" -> {
                if (ctx.tick() - (Integer) state.get("start") < GLYPH_MEASURE_TICKS) {
                    return StepResult.pending();
                }
                int frames = OsdHud.renderedFrames() - (Integer) state.get("frames");
                String problem = "";
                if (glyphs.loads() != (Integer) state.get("loads")) {
                    problem = "Glyphentabelle wurde " + (glyphs.loads() - (Integer) state.get("loads")) + "× neu geladen";
                } else if (glyphs.generation() != (Integer) state.get("generation")) {
                    problem = "Glyphen-Generation hat sich geändert (" + state.get("generation") + " → " + glyphs.generation() + ")";
                } else if (cache.clears() != (Integer) state.get("clears")) {
                    problem = "Text-Cache wurde " + (cache.clears() - (Integer) state.get("clears")) + "× geleert";
                } else if (cache.unsupported() <= (Integer) state.get("unsupported")) {
                    problem = "kein Text lief über die Spielschrift, die Abweichung wurde nicht erzwungen";
                } else if (cache.computed() != (Integer) state.get("computed")) {
                    problem = "abweichende Texte wurden trotzdem als Kontur berechnet";
                } else if (frames < 40 || stats.count < 40) {
                    problem = "zu wenige OSD-Frames gemessen (" + frames + ")";
                } else if (stats.max > GLYPH_FRAME_LIMIT_NANOS) {
                    problem = "OSD-Frame braucht " + stats.millis(stats.max) + " ms (" + state.get("clock") + ", Grenze "
                            + stats.millis(GLYPH_FRAME_LIMIT_NANOS) + " ms)";
                }
                if (!problem.isEmpty()) {
                    return endGlyphStep(state, StepResult.fail(problem));
                }
                ctx.log("osd: Breiten-Abweichung " + stats.count + " Frames über " + GLYPH_MEASURE_TICKS / 20.0 + " s, "
                        + (cache.unsupported() - (Integer) state.get("unsupported")) + " Texte über die Spielschrift, kein Nachladen, "
                        + state.get("clock") + " je Frame Mittel " + stats.millis(stats.total / Math.max(1, stats.count)) + " ms, Maximum "
                        + stats.millis(stats.max) + " ms");
                state.put("loads", glyphs.loads());
                state.put("clears", cache.clears());
                state.put("start", ctx.tick());
                stats.reset(GLYPH_SKIPPED_FRAMES);
                minecraft.options.forceUnicodeFont().set(!unicode);
                state.put("phase", "unicode");
                return StepResult.pending();
            }
            case "unicode" -> {
                OsdGlyphs.Table table = glyphs.table();
                if (table == null || table.options().uniform() == unicode || ctx.tick() - (Integer) state.get("start") < GLYPH_UNICODE_TICKS) {
                    return StepResult.pending();
                }
                String problem = "";
                int loads = glyphs.loads() - (Integer) state.get("loads");
                int clears = cache.clears() - (Integer) state.get("clears");
                if (loads != 1) {
                    problem = "Unicode-Umschaltung lud die Glyphentabelle " + loads + "× statt einmal";
                } else if (glyphs.lastBuildThread() == Thread.currentThread()) {
                    problem = "Glyphentabelle wurde im Zeichen-Thread aufgebaut";
                } else if (clears > 1) {
                    problem = "Text-Cache wurde nach der Umschaltung " + clears + "× geleert";
                } else if (stats.max > GLYPH_FRAME_LIMIT_NANOS) {
                    problem = "OSD-Frame nach der Unicode-Umschaltung braucht " + stats.millis(stats.max) + " ms";
                }
                if (!problem.isEmpty()) {
                    return endGlyphStep(state, StepResult.fail(problem));
                }
                ctx.log("osd: Unicode-Schrift umgeschaltet, Glyphentabelle einmal im Hintergrund (" + glyphs.lastBuildThread().getName()
                        + ") neu aufgebaut, " + stats.count + " Frames, Maximum " + stats.millis(stats.max) + " ms");
                minecraft.options.forceUnicodeFont().set(unicode);
                state.put("phase", "restore");
                return StepResult.pending();
            }
            default -> {
                OsdGlyphs.Table table = glyphs.table();
                if (table == null || table.options().uniform() != unicode || table == state.get("skewed")) {
                    return StepResult.pending();
                }
                return endGlyphStep(state, StepResult.pass());
            }
        }
    }

    private static StepResult endGlyphStep(Map<String, Object> state, StepResult result) {
        OsdHud.timeFrames(null);
        OsdHud.overrideConfig(null);
        OsdHud.sampler().setOverride(null);
        Minecraft minecraft = Minecraft.getInstance();
        Object unicode = state.get("unicode");
        if (unicode instanceof Boolean original && minecraft.options.forceUnicodeFont().get() != original) {
            minecraft.options.forceUnicodeFont().set(original);
        }
        Object skewed = state.get("skewed");
        if (skewed != null && OsdFonts.GLYPHS.table() == skewed && state.get("original") instanceof OsdGlyphs.Table original) {
            OsdFonts.GLYPHS.install(original);
        }
        return result;
    }

    private static Map<Integer, OsdGlyphs.Glyph> skew(Map<Integer, OsdGlyphs.Glyph> glyphs) {
        Map<Integer, OsdGlyphs.Glyph> skewed = new HashMap<>();
        for (Map.Entry<Integer, OsdGlyphs.Glyph> entry : glyphs.entrySet()) {
            OsdGlyphs.Glyph glyph = entry.getValue();
            int advance = glyph.width() > 0 ? glyph.advance() + 1 : glyph.advance();
            skewed.put(entry.getKey(), new OsdGlyphs.Glyph(glyph.width(), glyph.height(), glyph.top(), glyph.mask(), advance));
        }
        return skewed;
    }

    private static final class FrameStats {
        private int skip;
        private int count;
        private long total;
        private long max;

        void reset(int skipFrames) {
            this.skip = skipFrames;
            this.count = 0;
            this.total = 0L;
            this.max = 0L;
        }

        void add(long nanos) {
            if (this.skip > 0) {
                this.skip--;
                return;
            }
            this.count++;
            this.total += nanos;
            this.max = Math.max(this.max, nanos);
        }

        String millis(long nanos) {
            return String.format(Locale.ROOT, "%.2f", nanos / 1.0e6);
        }
    }

    private static int indexOfScale(float scale) {
        for (int i = 0; i < VARIANTS.size(); i++) {
            if (VARIANTS.get(i).scale() == scale) {
                return i;
            }
        }
        return 0;
    }

    private static void endStyleStep() {
        OsdHud.overrideConfig(null);
        OsdHud.sampler().setOverride(null);
    }

    private static StepResult editorStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            OsdHud.sampler().setOverride(null);
            FakeHost host = new FakeHost(8, 28, 510, 302);
            OsdConfigTab tab = host.bind(new OsdConfigTab());
            tab.resetDefaults();
            int expected = tab.page() == OsdConfigTab.Page.STYLE ? OsdConfigTab.STYLE_PAGE_WIDGETS : OsdConfigTab.OPTIONS_PAGE_WIDGETS;
            if (tab.page() == OsdConfigTab.Page.STYLE && tab.colorBox() == null) {
                return StepResult.fail("Editor-Tab ohne Hex-Feld für die OSD-Farbe");
            }
            String narrow = narrowHexFields();
            if (narrow != null) {
                return StepResult.fail(narrow);
            }
            if (host.widgets.size() != expected) {
                return StepResult.fail("Editor-Tab hat " + host.widgets.size() + " statt " + expected + " Widgets");
            }
            if (tab.previewGrid() == null) {
                return StepResult.fail("Editor-Tab ohne Vorschau");
            }
            String cramped = crampedLabel();
            if (cramped != null) {
                return StepResult.fail("Beschriftung passt bei 427×240 nicht: " + cramped);
            }
            AtomicBoolean extracted = new AtomicBoolean();
            OsdHud.probeNextFrame(graphics -> {
                tab.extract(graphics, 0, 0, 0.0F);
                extracted.set(true);
            });
            state.put("tab", tab);
            state.put("extracted", extracted);
            return StepResult.pending();
        }
        OsdConfigTab tab = (OsdConfigTab) state.get("tab");
        AtomicBoolean extracted = (AtomicBoolean) state.get("extracted");
        if (!extracted.get()) {
            return StepResult.pending();
        }
        OsdElement element = OsdElement.LINK_QUALITY;
        if (!tab.renderer().wasDrawn(element)) {
            return StepResult.fail("Vorschau hat " + element.key() + " nicht gezeichnet");
        }
        OsdGrid grid = tab.previewGrid();
        OsdConfig config = tab.working();
        OsdConfig.Slot before = config.slot(element).copy();
        float[] b = tab.renderer().bounds(element);
        double startX = (b[0] + b[2]) * 0.5;
        double startY = (b[1] + b[3]) * 0.5;
        if (!tab.mouseClicked(new MouseButtonEvent(startX, startY, LEFT), false) || tab.selected() != element || !tab.isDragging()) {
            return StepResult.fail("Klick auf " + element.key() + " startet kein Ziehen");
        }
        double endX = startX + grid.cellWidth() * 3.0;
        double endY = startY + grid.cellHeight() * 2.0;
        tab.mouseDragged(new MouseButtonEvent(endX, endY, LEFT), endX - startX, endY - startY);
        tab.mouseReleased(new MouseButtonEvent(endX, endY, LEFT));
        OsdConfig.Slot after = config.slot(element);
        if (after.col != before.col + 3 || after.row != before.row + 2) {
            return StepResult.fail("Ziehen verschiebt nach " + after.col + "/" + after.row + " statt " + (before.col + 3) + "/" + (before.row + 2));
        }
        if (!config.customized()) {
            return StepResult.fail("verschobenes Element gilt nicht als angepasst");
        }
        boolean wasEnabled = after.enabled;
        tab.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 32, 0));
        if (config.slot(element).enabled == wasEnabled) {
            return StepResult.fail("Leertaste schaltet das Element nicht um");
        }
        if (!clickWidget(tab.elementColorButton()) || !config.slot(element).hasColor()) {
            return StepResult.fail("Elementfarbe lässt sich für " + element.key() + " nicht setzen");
        }
        tab.resetLayout();
        OsdConfig.Slot reset = tab.working().slot(element);
        OsdPreset preset = tab.working().preset;
        if (reset.col != preset.col(element) || reset.row != preset.row(element) || reset.enabled != preset.enabled(element) || reset.hasOverrides()) {
            return StepResult.fail("Zurücksetzen stellt " + element.key() + " nicht wie in der Vorlage " + preset.key() + " wieder her");
        }
        EditBox box = tab.colorBox();
        if (box == null && tab.page() == OsdConfigTab.Page.STYLE) {
            return StepResult.fail("Hex-Feld für die OSD-Farbe fehlt");
        }
        if (box != null) {
            box.setValue("#12ab34");
            if (!"#12AB34".equals(tab.working().color)) {
                return StepResult.fail("gültige Hex-Farbe wird nicht übernommen: " + tab.working().color);
            }
            box.setValue("#12zz");
            if (!"#12AB34".equals(tab.working().color)) {
                return StepResult.fail("ungültige Hex-Farbe ändert die Farbe: " + tab.working().color);
            }
        }
        OsdSlider opacity = tab.opacitySlider();
        if (opacity != null) {
            if (!opacity.labelColumn() || opacity.themeLabel().getString().isEmpty() || opacity.themeValue().getString().contains(":")) {
                return StepResult.fail("Deckkraft-Regler ohne eigene Beschriftungsspalte: " + opacity.getMessage().getString());
            }
            opacity.select(10);
            if (tab.working().opacity != 10 * OsdConfigTab.OPACITY_STEP) {
                return StepResult.fail("Deckkraft-Regler setzt " + tab.working().opacity + " statt 50");
            }
        }
        return StepResult.pass();
    }

    private static StepResult presetStep(SelfTest.StepContext ctx) {
        FakeHost host = new FakeHost(8, 28, 510, 302);
        OsdConfigTab tab = host.bind(new OsdConfigTab());
        tab.resetDefaults();
        OsdConfig config = tab.working();
        if (config.preset != OsdConfig.DEFAULT_PRESET || !OsdConfig.DEFAULT_PRESET.matches(config)) {
            return StepResult.fail("Standard ist nicht die Vorlage " + OsdConfig.DEFAULT_PRESET.key());
        }
        if (tab.resetButton() == null || tab.resetButton().active) {
            return StepResult.fail("Zurücksetzen ist ohne Änderung an der Vorlage aktiv");
        }
        if (!clickWidget(tab.presetButton()) || tab.working().preset != OsdPreset.STANDARD || tab.pendingPreset() != null) {
            return StepResult.fail("unveränderte Vorlage wird nicht direkt gewechselt (" + tab.working().preset.key() + ")");
        }
        if (!OsdPreset.STANDARD.matches(tab.working())) {
            return StepResult.fail("Vorlage Standard wurde nicht angewendet");
        }
        tab.working().move(OsdElement.SPEED, 10, 10);
        if (!clickWidget(tab.presetButton()) || tab.pendingPreset() != OsdPreset.FULL || tab.working().preset != OsdPreset.STANDARD) {
            return StepResult.fail("angepasstes Layout fragt vor dem Wechsel nicht nach");
        }
        if (tab.working().slot(OsdElement.SPEED).col != 10) {
            return StepResult.fail("Vorlage wurde ohne Bestätigung angewendet");
        }
        if (!clickWidget(tab.confirmButton())) {
            return StepResult.fail("Bestätigen-Knopf ist nicht sichtbar");
        }
        if (tab.working().preset != OsdPreset.FULL || tab.pendingPreset() != null || !OsdPreset.FULL.matches(tab.working())) {
            return StepResult.fail("Vorlage Voll wurde nach Bestätigung nicht angewendet");
        }
        tab.working().move(OsdElement.SPEED, 3, 3);
        clickWidget(tab.presetButton());
        if (tab.pendingPreset() != OsdPreset.RACE) {
            return StepResult.fail("zweite Nachfrage fehlt");
        }
        tab.cancelAction();
        if (tab.pendingPreset() != null || tab.working().preset != OsdPreset.FULL || tab.working().slot(OsdElement.SPEED).col != 3) {
            return StepResult.fail("Abbrechen verwirft die Nachfrage nicht sauber");
        }
        if (tab.resetButton() == null || !tab.resetButton().active) {
            return StepResult.fail("Zurücksetzen ist trotz angepasstem Layout gesperrt");
        }
        OsdConfigTab.Page page = tab.page();
        if (!clickWidget(tab.pageButton()) || tab.page() == page) {
            return StepResult.fail("Seitenwechsel im Editor-Tab funktioniert nicht");
        }
        int expected = tab.page() == OsdConfigTab.Page.STYLE ? OsdConfigTab.STYLE_PAGE_WIDGETS : OsdConfigTab.OPTIONS_PAGE_WIDGETS;
        if (host.widgets.size() != expected) {
            return StepResult.fail("Seite " + tab.page() + " hat " + host.widgets.size() + " statt " + expected + " Widgets");
        }
        clickWidget(tab.pageButton());
        if (tab.page() != page) {
            return StepResult.fail("Seitenwechsel zurück funktioniert nicht");
        }
        ctx.log("osd: Vorlagen Minimal → Standard → Voll (mit Nachfrage) angewendet, Abbrechen und Seitenwechsel geprüft");
        return StepResult.pass();
    }

    private record StyleVariant(int opacity, float scale, OsdConfig.Outline outline, OsdConfig.Background background) {
        OsdConfig config() {
            OsdConfig config = OsdConfig.defaults();
            config.applyPreset(OsdPreset.FULL);
            config.opacity = this.opacity;
            config.scale = this.scale;
            config.outline = this.outline;
            config.background = this.background;
            config.lineOpacity = 40;
            config.analogBreakup = false;
            config.slot(OsdElement.BATTERY_VOLTAGE).color = OsdPalette.CYAN.hex();
            config.slot(OsdElement.FLIGHT_TIME).scale = 1.5F;
            return config;
        }

        @Override
        public String toString() {
            return "Deckkraft " + this.opacity + " %, Größe " + this.scale + ", " + this.outline + "/" + this.background;
        }
    }

    private static @Nullable String narrowHexFields() {
        for (int[] size : new int[][] {{214, 196}, {300, 196}, {420, 250}, {510, 302}}) {
            FakeHost host = new FakeHost(8, 28, size[0], size[1]);
            OsdConfigTab tab = host.bind(new OsdConfigTab());
            boolean switched = tab.page() != OsdConfigTab.Page.STYLE;
            if (switched) {
                tab.switchPage();
            }
            String problem = hexProblem(host, tab.colorBox(), size[0]);
            if (problem == null) {
                problem = hexProblem(host, tab.warningBox(), size[0]);
            }
            if (switched) {
                tab.switchPage();
            }
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static @Nullable String hexProblem(FakeHost host, @Nullable EditBox box, int width) {
        if (box == null) {
            return "Hex-Feld fehlt bei " + width + " Punkten Tab-Breite";
        }
        for (AbstractWidget other : host.widgets) {
            if (other != box && other.visible && other.getY() < box.getY() + box.getHeight() && box.getY() < other.getY() + other.getHeight()
                    && other.getX() < box.getX() + box.getWidth() && box.getX() < other.getX() + other.getWidth()) {
                return "Hex-Feld liegt bei " + width + " Punkten Tab-Breite über " + other.getMessage().getString();
            }
        }
        if (box.getX() < host.x || box.getX() + box.getWidth() > host.x + host.width) {
            return "Hex-Feld ragt bei " + width + " Punkten Tab-Breite aus dem Tab";
        }
        return null;
    }

    private static @Nullable String crampedLabel() {
        FakeHost host = new FakeHost(49, 29, 328, 182);
        OsdConfigTab tab = host.bind(new OsdConfigTab());
        tab.resetDefaults();
        OsdConfigTab.Page start = tab.page();
        try {
            for (int i = 0; i < 2; i++) {
                for (AbstractWidget widget : host.widgets) {
                    if (!(widget instanceof EditBox) && !tab.fits(widget.getMessage(), widget.getWidth())) {
                        return widget.getMessage().getString() + " (" + widget.getWidth() + " px)";
                    }
                }
                tab.switchPage();
            }
        } finally {
            if (tab.page() != start) {
                tab.switchPage();
            }
        }
        return null;
    }

    private static boolean clickWidget(@Nullable AbstractWidget widget) {
        if (widget == null || !widget.visible || !widget.active) {
            return false;
        }
        double cx = widget.getX() + widget.getWidth() * 0.5;
        double cy = widget.getY() + widget.getHeight() * 0.5;
        return widget.mouseClicked(new MouseButtonEvent(cx, cy, LEFT), false);
    }

    private static final class SimulatedSource implements OsdSampler.Source {
        private final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 7L);
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private final Vector3d position = new Vector3d();
        private final double homeX;
        private final double homeY;
        private final double homeZ;
        private int steps;

        SimulatedSource(double homeX, double homeY, double homeZ) {
            this.homeX = homeX;
            this.homeY = homeY;
            this.homeZ = homeZ;
            this.sim.reset(homeX + 6.0, homeY + 4.0, homeZ - 10.0, 0.0);
            this.environment.groundDistance = 4.0;
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            this.input.armSwitch = this.steps >= 3;
            this.input.throttle = this.steps >= 6 ? 0.45 : 0.0;
            this.input.roll = 0.1;
            this.sim.step(FRAME_NANOS, this.input, this.environment, this.result);
            this.steps++;
            d.applySim(this.sim);
            this.sim.getPosition(this.position);
            d.hasPosition = true;
            d.x = this.position.x;
            d.y = this.position.y;
            d.z = this.position.z;
            d.hasHome = true;
            d.homeX = this.homeX;
            d.homeY = this.homeY;
            d.homeZ = this.homeZ;
            d.craftName = "Selftest";
            d.radioConnected = true;
            d.linkStrength = 0.9;
            d.videoSystem = VideoSystem.ANALOG;
            d.videoSignal = 0.8;
            d.stickThrottle = (float) this.input.throttle;
            d.stickRoll = (float) this.input.roll;
            d.racing = true;
            d.lap = 1;
            d.currentLapNanos = this.steps * FRAME_NANOS;
            d.gateCount = 5;
            d.nextGate = 1;
            d.splitDeltaNanos = -250_000_000L;
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

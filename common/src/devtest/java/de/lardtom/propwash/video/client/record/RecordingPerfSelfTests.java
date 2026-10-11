package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
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
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.video.record.EncoderChoice;
import de.lardtom.propwash.video.record.PipelineStats;
import de.lardtom.propwash.video.record.RecordingDiagnosis;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import de.lardtom.propwash.video.record.mp4.Mp4Info;
import de.lardtom.propwash.video.record.mp4.Mp4Reader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class RecordingPerfSelfTests {
    public static final String PREFIX = "video: Aufnahme-Leistung";
    private static final int FIELD_OFFSET = 16;
    private static final int FIELD_SIZE = 64;
    private static final int FIELD_HEIGHT = 8;
    private static final double RADIUS = 22.0;
    private static final double ALTITUDE = 12.0;
    private static final double ANGULAR_SPEED = 0.5;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String START = "start";
    private static final String FPS = "fps";
    private static final String STATS = "stats";
    private static final String STOPPED = "stopped";
    private static final String SEEN = "seen";
    private static final String SEEN_LIMIT = "limit";
    private static final String SINCE = "since";
    private static final String WRONG_TIP = "wrongTip";
    private static final String SHOT = "shot";
    private static final String ENCODER_LINE = "encoderLine";
    private static final String ENCODER_SHOWN = "encoderShown";
    private static final double MAX_RENDER_THREAD_MS = 2.0;
    private static final double MAX_RENDER_THREAD_STALL_MS = 25.0;
    private static volatile @Nullable RecordingConfig savedConfig;
    private static final SimSnapshot SNAPSHOT = new SimSnapshot();

    private static volatile int droneId = -1;
    private static volatile @Nullable BlockPos fieldOrigin;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;

    private RecordingPerfSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + " – Testgelände und FPV-Drohne", 600, RecordingPerfSelfTests::setupStep);
        SelfTest.clientStep(PREFIX + " 720p30 im Testflug (Propwash-Encoder)", 4000,
                ctx -> recordStep(ctx, RecordingFormat.of(720, 30), 8, Run.BUILTIN));
        SelfTest.clientStep(PREFIX + " 1080p60 im Testflug (Propwash-Encoder)", 6000,
                ctx -> recordStep(ctx, RecordingFormat.of(1080, 60), 10, Run.BUILTIN));
        SelfTest.clientStep(PREFIX + " 1080p60 im Testflug (Hardware-Encoder)", 4000,
                ctx -> recordStep(ctx, RecordingFormat.of(1080, 60), 10, Run.HARDWARE));
        SelfTest.clientStep(PREFIX + " 1080p60 mit knappem Encoder: Engpass-Hinweis, nichts verloren", 9000,
                ctx -> recordStep(ctx, RecordingFormat.of(1080, 60), 8, Run.STARVED));
        if (Screenshots.enabled()) {
            SelfTest.clientStep(PREFIX + " – Bildschirmfoto Engpass-Hinweis", 3000, RecordingPerfSelfTests::hudShotStep);
            SelfTest.clientStep(PREFIX + " – Bildschirmfoto Encoder-Anzeige", 3000, RecordingPerfSelfTests::encoderShotStep);
        }
        SelfTest.clientStep(PREFIX + " – Tab Action-Cam mit Encoder und Stärke", 600, RecordingPerfSelfTests::tabStep);
        SelfTest.clientStep(PREFIX + " – aufräumen", 400, RecordingPerfSelfTests::cleanupStep);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ActionCamRecorder.INSTANCE.cancelAll();
            ctx.state().put(FUTURE, ctx.onServer(RecordingPerfSelfTests::buildAndFly));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufbau fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (drone == null || drone.getId() != droneId || !session.isFpv() || minecraft.getCameraEntity() != drone || session.sim() == null) {
            return ctx.tick() > 400 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        fly(ctx.tick() * 0.05);
        return ctx.tick() > 80 ? StepResult.pass() : StepResult.pending();
    }

    private static void configure(Run run) {
        RecordingConfig base = savedConfig;
        if (base == null) {
            base = RecordingConfig.get().copy();
            savedConfig = base;
        }
        RecordingConfig config = base.copy();
        config.encoder = run == Run.HARDWARE ? EncoderChoice.AUTO : EncoderChoice.BUILTIN;
        config.encoderThreads = run == Run.STARVED ? 1 : 0;
        config.stabilization = de.lardtom.propwash.video.record.Stabilizer.Mode.SMOOTH;
        RecordingConfig.CONFIG.set(config);
    }

    private static void restoreConfig() {
        RecordingConfig base = savedConfig;
        if (base != null) {
            RecordingConfig.CONFIG.set(base);
            savedConfig = null;
        }
    }

    private static StepResult recordStep(SelfTest.StepContext ctx, RecordingFormat format, int seconds, Run run) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        if (session.drone() == null || session.drone().getId() != droneId || session.sim() == null) {
            return StepResult.fail("Testdrohne nicht mehr im FPV");
        }
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        double time = ctx.tick() * 0.05;
        if (phase < 3) {
            fly(time);
        }
        switch (phase) {
            case 0 -> {
                if (ctx.tick() == 0) {
                    configure(run);
                }
                if (run == Run.HARDWARE) {
                    HardwareEncoders.Status status = ActionCamRecorder.hardwareStatus(minecraft, format);
                    if (status.state() == HardwareEncoders.State.CHECKING) {
                        return ctx.tick() > 600 ? StepResult.fail("Prüfung des Hardware-Encoders endet nicht") : StepResult.pending();
                    }
                    if (status.state() != HardwareEncoders.State.READY) {
                        ctx.log("kein Hardware-Encoder auf diesem Rechner (" + status.state() + " " + status.detail() + "), Messung entfällt");
                        restoreConfig();
                        return StepResult.pass();
                    }
                    if (!ctx.state().containsKey(SEEN)) {
                        ctx.state().put(SEEN, status);
                        ctx.log("Hardware-Encoder: " + status.codec() + " (Variante " + status.variant() + ", Probe " + Math.round(status.fps())
                                + " B/s) über " + status.ffmpeg());
                    }
                }
                if (ctx.tick() < 60) {
                    return StepResult.pending();
                }
                ctx.state().put(FPS, minecraft.getFps());
                if (run == Run.BUILTIN && format.height() == 720) {
                    ctx.log("Hauptbild " + minecraft.gameRenderer.mainRenderTarget().width + "x" + minecraft.gameRenderer.mainRenderTarget().height
                            + ", Encoder-Threads " + RecordingConfig.get().threads() + ", Puffer " + RecordingConfig.get().memoryCapBytes() / (1024L * 1024L)
                            + " MB");
                }
                recorder.overrideFormat(format);
                ActionCamRecorder.Result result = recorder.start(minecraft, false);
                if (result != ActionCamRecorder.Result.STARTED) {
                    recorder.overrideFormat(null);
                    restoreConfig();
                    return StepResult.fail("Aufnahme startet nicht: " + result);
                }
                PipelineStats stats = recorder.pipelineStats();
                if (stats == null) {
                    return StepResult.fail("keine Messwerte für die laufende Aufnahme");
                }
                ctx.state().put(STATS, stats);
                ctx.state().put(PHASE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (recorder.phase() != ActionCamRecorder.Phase.RECORDING) {
                    recorder.overrideFormat(null);
                    restoreConfig();
                    return StepResult.fail("Aufnahme endete vorzeitig: " + recorder.lastError());
                }
                RecordingDiagnosis diagnosis = recorder.diagnosis();
                if (diagnosis.limit() == RecordingDiagnosis.Limit.ENCODER && !ctx.state().containsKey(SEEN_LIMIT)) {
                    ctx.state().put(SEEN_LIMIT, diagnosis);
                    ctx.log("Engpass erkannt: " + diagnosis.describe() + " | HUD: " + RecordingHud.notices(recorder).stream()
                            .map(component -> component.getString()).toList());
                }
                String ownFormat = ownFormatTip(diagnosis, format);
                if (ownFormat != null) {
                    ctx.state().putIfAbsent(WRONG_TIP, diagnosis.tip() + ": " + ownFormat);
                }
                String encoderLine = RecordingHud.encoderLine(recorder.hardwareRecording(), recorder.startStatus()).getString();
                long recorded = recorder.recordedNanos();
                if (!ctx.state().containsKey(ENCODER_LINE) && recorded > 500_000_000L && recorded < RecordingHud.ENCODER_NOTICE_NANOS - 500_000_000L) {
                    ctx.state().put(ENCODER_LINE, encoderLine);
                    ctx.state().put(ENCODER_SHOWN, RecordingHud.notices(recorder).stream().anyMatch(line -> line.getString().equals(encoderLine)));
                    ctx.log("Encoder-Anzeige im HUD: " + encoderLine + " | Kopfzeile: " + format.label() + " · "
                            + RecordingHud.encoderShort(recorder.hardwareRecording(), recorder.startStatus()).getString());
                }
                if (recorded < seconds * 1_000_000_000L) {
                    return StepResult.pending();
                }
                List<String> notices = RecordingHud.notices(recorder).stream().map(component -> component.getString()).toList();
                ctx.log("Hinweise vor dem Stopp: " + (notices.isEmpty() ? "keine" : String.join(" | ", notices)));
                if (!(ctx.state().get(ENCODER_LINE) instanceof String shownLine) || !Boolean.TRUE.equals(ctx.state().get(ENCODER_SHOWN))) {
                    ctx.state().put(WRONG_TIP, "Encoder-Anzeige fehlte in den ersten Sekunden im HUD");
                } else if (run == Run.HARDWARE ? !shownLine.contains("ffmpeg") || recorder.startStatus().codec() == null
                        || !shownLine.contains(recorder.startStatus().codec().label()) : !shownLine.contains("Propwash (CPU)")) {
                    ctx.state().put(WRONG_TIP, "Encoder-Anzeige passt nicht zum Encoder (" + run + "): " + shownLine);
                } else if (seconds * 1_000_000_000L > RecordingHud.ENCODER_NOTICE_NANOS && notices.contains(encoderLine)) {
                    ctx.state().put(WRONG_TIP, "Encoder-Anzeige bleibt nach " + seconds + " s stehen");
                }
                recorder.stop(minecraft, ActionCamRecorder.StopReason.USER);
                ctx.state().put(STOPPED, System.nanoTime());
                ctx.state().put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (recorder.phase() != ActionCamRecorder.Phase.IDLE || !recorder.saving().isEmpty()) {
                    return StepResult.pending();
                }
                recorder.overrideFormat(null);
                restoreConfig();
                Path file = recorder.lastSaved();
                if (file == null) {
                    return StepResult.fail("kein Video gespeichert: " + recorder.lastError());
                }
                double saveSeconds = (System.nanoTime() - (long) ctx.state().get(STOPPED)) / 1.0E9;
                PipelineStats.Snapshot snapshot = ((PipelineStats) ctx.state().get(STATS)).snapshot();
                Mp4Info info;
                try {
                    info = Mp4Reader.read(file);
                } catch (IOException e) {
                    return StepResult.fail("MP4 lässt sich nicht lesen: " + e.getMessage());
                }
                ctx.log(String.format(Locale.ROOT, "%s: Spiel vorher %d FPS, Speichern nach dem Stopp %.1f s, Video %d Bilder %.2f s",
                        format.label(), (int) ctx.state().get(FPS), saveSeconds, info.sampleCount(), info.durationSeconds()));
                ctx.log(format.label() + " Messwerte: " + snapshot.summary());
                if (info.width() != format.width() || info.height() != format.height()) {
                    return StepResult.fail("falsche Größe: " + info.width() + "x" + info.height());
                }
                if (Math.abs(info.durationSeconds() - seconds) > 0.25) {
                    return StepResult.fail("Dauer " + info.durationSeconds() + " s statt " + seconds + " s");
                }
                if (ctx.state().get(WRONG_TIP) instanceof String wrong) {
                    return StepResult.fail("Hinweis im HUD falsch (" + format.label() + "): " + wrong);
                }
                if (snapshot.captures() == 0L || snapshot.encoded() == 0L || snapshot.renderFrames() == 0L) {
                    return StepResult.fail("Messwerte leer: " + snapshot.summary());
                }
                if (snapshot.lostFrames() > 0L) {
                    return StepResult.fail(snapshot.lostFrames() + " Bilder verloren: " + snapshot.summary());
                }
                if (snapshot.renderThreadMs() > MAX_RENDER_THREAD_MS) {
                    return StepResult.fail(String.format(Locale.ROOT, "Renderthread %.3f ms je Bild (Grenze %.1f ms)", snapshot.renderThreadMs(),
                            MAX_RENDER_THREAD_MS));
                }
                double worst = Math.max(snapshot.captureNanosMax(), snapshot.readbackNanosMax()) / 1.0E6;
                if (worst > MAX_RENDER_THREAD_STALL_MS) {
                    return StepResult.fail(String.format(Locale.ROOT, "Renderthread hängt: schlimmstes Bild %.1f ms (Auftrag max %.2f, Auslesen max %.2f; Grenze %.0f ms)",
                            worst, snapshot.captureNanosMax() / 1.0E6, snapshot.readbackNanosMax() / 1.0E6, MAX_RENDER_THREAD_STALL_MS));
                }
                if ((run == Run.HARDWARE) == (info.profile() == 66)) {
                    return StepResult.fail("Profil " + info.profile() + " passt nicht zum Encoder (" + run + ")");
                }
                if (run == Run.STARVED && !ctx.state().containsKey(SEEN_LIMIT)) {
                    return StepResult.fail("Encoder-Engpass nicht gemeldet: " + recorder.diagnosis().describe());
                }
                ctx.state().put(PHASE, 3);
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static @Nullable String ownFormatTip(RecordingDiagnosis diagnosis, RecordingFormat format) {
        Component tip = RecordingHud.tipLine(diagnosis, format);
        if (tip == null) {
            return null;
        }
        String text = tip.getString();
        return text.contains(format.height() + "p") || text.contains(format.fps() + " fps") ? text : null;
    }

    private static StepResult hudShotStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        if (phase > 0 && phase < 4) {
            fly(ctx.tick() * 0.05);
        }
        switch (phase) {
            case 0 -> {
                StepResult resized = Screenshots.resizeWindow(ctx);
                if (resized.status() == StepResult.Status.PASS) {
                    ctx.state().put(PHASE, 1);
                }
                return resized.status() == StepResult.Status.FAIL ? resized : StepResult.pending();
            }
            case 1 -> {
                configure(Run.STARVED);
                recorder.overrideFormat(RecordingFormat.of(1080, 60));
                ActionCamRecorder.Result result = recorder.start(minecraft, false);
                recorder.overrideFormat(null);
                if (result != ActionCamRecorder.Result.STARTED) {
                    restoreConfig();
                    return StepResult.fail("Aufnahme startet nicht: " + result);
                }
                ctx.state().put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (recorder.diagnosis().limit() != RecordingDiagnosis.Limit.ENCODER) {
                    return ctx.tick() > 1200 ? StepResult.fail("kein Encoder-Engpass: " + recorder.diagnosis().describe()) : StepResult.pending();
                }
                Screenshots.clearOverlays(minecraft);
                Screenshots.hideMarker(true);
                ctx.state().put(SINCE, ctx.tick());
                ctx.state().put(PHASE, 3);
                return StepResult.pending();
            }
            case 3 -> {
                if (ctx.tick() - (int) ctx.state().get(SINCE) < 30) {
                    return StepResult.pending();
                }
                if (recorder.diagnosis().limit() != RecordingDiagnosis.Limit.ENCODER || RecordingHud.notices(recorder).isEmpty()) {
                    ctx.state().put(PHASE, 2);
                    return StepResult.pending();
                }
                ctx.log("Hinweis im Bild: " + RecordingHud.notices(recorder).stream().map(component -> component.getString()).toList());
                ctx.state().put(SHOT, Screenshots.capture("action_cam_bottleneck_hud"));
                ctx.state().put(PHASE, 4);
                return StepResult.pending();
            }
            default -> {
                CompletableFuture<?> shot = (CompletableFuture<?>) ctx.state().get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                Screenshots.hideMarker(false);
                recorder.cancelAll();
                restoreConfig();
                return shot.isCompletedExceptionally() ? StepResult.fail("Bildschirmfoto: " + shot.exceptionNow()) : StepResult.pass();
            }
        }
    }

    private static StepResult encoderShotStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        if (session.drone() == null || session.drone().getId() != droneId || session.sim() == null) {
            return StepResult.fail("Testdrohne nicht mehr im FPV");
        }
        RecordingFormat format = RecordingFormat.of(1080, 60);
        int phase = (int) ctx.state().getOrDefault(PHASE, 0);
        if (phase < 4) {
            fly(ctx.tick() * 0.05);
        }
        switch (phase) {
            case 0 -> {
                StepResult resized = Screenshots.resizeWindow(ctx);
                if (resized.status() == StepResult.Status.PASS) {
                    configure(Run.HARDWARE);
                    ctx.state().put(PHASE, 1);
                }
                return resized.status() == StepResult.Status.FAIL ? resized : StepResult.pending();
            }
            case 1 -> {
                HardwareEncoders.Status status = ActionCamRecorder.hardwareStatus(minecraft, format);
                if (status.state() == HardwareEncoders.State.CHECKING) {
                    return ctx.tick() > 900 ? StepResult.fail("Prüfung des Hardware-Encoders endet nicht") : StepResult.pending();
                }
                recorder.overrideFormat(format);
                ActionCamRecorder.Result result = recorder.start(minecraft, false);
                recorder.overrideFormat(null);
                if (result != ActionCamRecorder.Result.STARTED) {
                    restoreConfig();
                    return StepResult.fail("Aufnahme startet nicht: " + result);
                }
                ctx.state().put(PHASE, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (recorder.recordedNanos() < 1_000_000_000L) {
                    return StepResult.pending();
                }
                Screenshots.clearOverlays(minecraft);
                Screenshots.hideMarker(true);
                ctx.state().put(PHASE, 3);
                return StepResult.pending();
            }
            case 3 -> {
                long recorded = recorder.recordedNanos();
                if (recorded < 2_500_000_000L) {
                    return StepResult.pending();
                }
                String line = RecordingHud.encoderLine(recorder.hardwareRecording(), recorder.startStatus()).getString();
                List<String> notices = RecordingHud.notices(recorder).stream().map(component -> component.getString()).toList();
                if (recorded > RecordingHud.ENCODER_NOTICE_NANOS - 1_000_000_000L || !notices.contains(line)) {
                    Screenshots.hideMarker(false);
                    recorder.cancelAll();
                    restoreConfig();
                    return StepResult.fail("Encoder-Anzeige nicht im Bild (" + recorded / 1_000_000L + " ms): " + notices);
                }
                ctx.log("Encoder-Anzeige im Bild: " + notices);
                ctx.state().put(SHOT, Screenshots.capture("action_cam_encoder_hud"));
                ctx.state().put(PHASE, 4);
                return StepResult.pending();
            }
            default -> {
                CompletableFuture<?> shot = (CompletableFuture<?>) ctx.state().get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                Screenshots.hideMarker(false);
                recorder.cancelAll();
                restoreConfig();
                return shot.isCompletedExceptionally() ? StepResult.fail("Bildschirmfoto: " + shot.exceptionNow()) : StepResult.pass();
            }
        }
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (ctx.tick() > 200) {
                return StepResult.fail("Konfigurator öffnet nicht");
            }
            ConfigTabs.open(null, "action_cam");
            return StepResult.pending();
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !"action_cam".equals(active.id())) {
            screen.selectTab("action_cam");
            return StepResult.pending();
        }
        if (!(active instanceof ActionCamTab tab)) {
            return StepResult.fail("Tab action_cam ist kein ActionCamTab");
        }
        List<String> lines = tab.statusLines().stream().map(component -> component.getString()).toList();
        boolean checking = lines.stream().anyMatch(line -> line.contains("ffmpeg") && line.contains("…"))
                || tab.ffmpegStatus().state() == HardwareEncoders.State.CHECKING;
        if (checking && ctx.tick() < 400) {
            return StepResult.pending();
        }
        if (lines.stream().noneMatch(line -> line.startsWith("Encoder") || line.contains("ffmpeg") || line.contains("Hardware"))) {
            screen.discardAndClose();
            return StepResult.fail("Status ohne Encoder-Zeile: " + lines);
        }
        List<String> ffmpeg = FfmpegSelfTests.texts(tab);
        if (ffmpeg.isEmpty() || !ffmpeg.getFirst().contains("ffmpeg")) {
            screen.discardAndClose();
            return StepResult.fail("Abschnitt „ffmpeg“ ohne Status: " + ffmpeg);
        }
        if (!ctx.state().containsKey(SEEN)) {
            ctx.state().put(SEEN, ctx.tick());
            ctx.log("Status im Tab: " + lines + " | ffmpeg: " + ffmpeg);
            Screenshots.parkMouse(minecraft);
            Screenshots.clearOverlays(minecraft);
            return StepResult.pending();
        }
        if (ctx.tick() - (int) ctx.state().get(SEEN) < 30) {
            return StepResult.pending();
        }
        if (Screenshots.enabled()) {
            if (!ctx.state().containsKey(SHOT)) {
                ctx.state().put(SHOT, Screenshots.capture("configurator_action_cam"));
                return StepResult.pending();
            }
            CompletableFuture<?> shot = (CompletableFuture<?>) ctx.state().get(SHOT);
            if (!shot.isDone()) {
                return StepResult.pending();
            }
            if (shot.isCompletedExceptionally()) {
                screen.discardAndClose();
                return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
            }
        }
        screen.discardAndClose();
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ActionCamRecorder.INSTANCE.overrideFormat(null);
            restoreConfig();
            Screenshots.restoreWindow(ctx);
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            ctx.state().put(FUTURE, ctx.onServer(RecordingPerfSelfTests::tearDown));
            return StepResult.pending();
        }
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone() || PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 300 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static void fly(double time) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        BlockPos origin = fieldOrigin;
        if (sim == null || drone == null || origin == null) {
            return;
        }
        double centerX = origin.getX() + FIELD_SIZE * 0.5;
        double centerZ = origin.getZ() + FIELD_SIZE * 0.5;
        double angle = time * ANGULAR_SPEED;
        double x = centerX + RADIUS * Math.cos(angle);
        double z = centerZ + RADIUS * Math.sin(angle);
        double footY = origin.getY() + ALTITUDE + 1.5 * Math.sin(time * 0.7);
        double dirX = -Math.sin(angle);
        double dirZ = Math.cos(angle);
        double speed = RADIUS * ANGULAR_SPEED;
        double yaw = Math.toDegrees(Math.atan2(-dirX, dirZ));
        double half = -Math.toRadians(yaw) * 0.5;
        Quaternionf attitude = new Quaternionf(0.0F, (float) Math.sin(half), 0.0F, (float) Math.cos(half))
                .rotateX((float) Math.toRadians(28.0 + 8.0 * Math.sin(time * 1.1)))
                .rotateZ((float) Math.toRadians(22.0 * Math.sin(time * 1.7)));
        double com = -sim.params().hitboxFootOffset;
        sim.copyStateTo(SNAPSHOT);
        SNAPSHOT.px = x;
        SNAPSHOT.py = footY + com;
        SNAPSHOT.pz = z;
        SNAPSHOT.vx = dirX * speed;
        SNAPSHOT.vy = 0.0;
        SNAPSHOT.vz = dirZ * speed;
        SNAPSHOT.lx = 0.0;
        SNAPSHOT.ly = 0.0;
        SNAPSHOT.lz = 0.0;
        SNAPSHOT.qw = attitude.w;
        SNAPSHOT.qx = attitude.x;
        SNAPSHOT.qy = attitude.y;
        SNAPSHOT.qz = attitude.z;
        SNAPSHOT.sleeping = false;
        sim.copyStateFrom(SNAPSHOT);
        Quaternionf body = new Quaternionf(attitude).rotateY((float) -Math.PI);
        drone.snapClientPose(new Vec3(x, footY, z), body);
    }

    private static void buildAndFly(MinecraftServer server) {
        ServerPlayer player = firstPlayer(server);
        if (player == null) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        BlockPos origin = new BlockPos(feet.getX() + FIELD_OFFSET, feet.getY(), feet.getZ() - FIELD_SIZE / 2);
        BlockState[] palette = palette();
        Random random = new Random(42L);
        for (int dx = 0; dx < FIELD_SIZE; dx++) {
            for (int dz = 0; dz < FIELD_SIZE; dz++) {
                int height = 1 + random.nextInt(FIELD_HEIGHT - 1);
                BlockState state = palette[random.nextInt(palette.length)];
                for (int dy = 0; dy < height; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), dy == height - 1 ? state : palette[(dx * 7 + dz * 3 + dy) % palette.length], 2);
                }
            }
        }
        fieldOrigin = origin;
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        drone.snapTo(origin.getX() + FIELD_SIZE * 0.5 + RADIUS, origin.getY() + ALTITUDE, origin.getZ() + FIELD_SIZE * 0.5, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY));
        drone.setCameraCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        previousHand = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void tearDown(MinecraftServer server) {
        ServerPlayer player = firstPlayer(server);
        if (player != null) {
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ItemStack head = previousHead;
            ItemStack hand = previousHand;
            player.setItemSlot(EquipmentSlot.HEAD, head == null ? ItemStack.EMPTY : head);
            player.setItemSlot(EquipmentSlot.MAINHAND, hand == null ? ItemStack.EMPTY : hand);
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(droneId);
            if (entity != null) {
                entity.discard();
            }
            BlockPos origin = fieldOrigin;
            if (origin != null && level == (player == null ? server.overworld() : player.level())) {
                for (int dx = 0; dx < FIELD_SIZE; dx++) {
                    for (int dz = 0; dz < FIELD_SIZE; dz++) {
                        for (int dy = 0; dy < FIELD_HEIGHT; dy++) {
                            level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
        fieldOrigin = null;
        droneId = -1;
    }

    private static BlockState[] palette() {
        return new BlockState[]{
                Blocks.OAK_LOG.defaultBlockState(), Blocks.BIRCH_LOG.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.SAND.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                Blocks.BOOKSHELF.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.SPRUCE_PLANKS.defaultBlockState()
        };
    }

    private static @Nullable ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.getFirst();
    }

    private static CompletableFuture<?> future(SelfTest.StepContext ctx) {
        Object value = ctx.state().get(FUTURE);
        return value instanceof CompletableFuture<?> future ? future : CompletableFuture.failedFuture(new IllegalStateException("kein Auftrag"));
    }

    private enum Run {
        BUILTIN,
        HARDWARE,
        STARVED
    }
}

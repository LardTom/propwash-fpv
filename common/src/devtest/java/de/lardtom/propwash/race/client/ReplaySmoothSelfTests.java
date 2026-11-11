package de.lardtom.propwash.race.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.CameraPathMeter;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostLook;
import de.lardtom.propwash.race.GhostRecorder;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.RecordingConfig;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import de.lardtom.propwash.race.ReplayExport;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ReplaySmoothSelfTests {
    public static final String STEP = "race: Replay glatt bei 60 fps (Kamerabahn je Bild aus eigenem Flug)";
    private static final long MS = 1_000_000L;
    private static final UUID PILOT = new UUID(0x5A00L, 0x60F5L);
    private static final double FLIGHT_HEIGHT = 5.0;
    private static final float TURN_PITCH = 0.2F;
    private static final float TURN_YAW = 0.35F;
    private static final int ARM_TICKS = 20;
    private static final int CLIMB_TICKS = 60;
    private static final int TURN_TICKS = 30;
    private static final int RECORD_TICKS = 90;
    private static final int TIMEOUT_TICKS = 400;
    private static final double MIN_TURN_DEGREES = 0.3;
    private static final double MIN_MOVE_BLOCKS = 0.02;
    private static final String PHASE = "smooth_phase";
    private static final String SINCE = "smooth_since";
    private static final String FUTURE = "smooth_future";
    private static final String RESULT = "smooth_result";
    private static volatile int droneId = -1;
    private static volatile double groundY;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;

    private ReplaySmoothSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 2400, ReplaySmoothSelfTests::step);
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().get(SINCE);
    }

    private static @Nullable Telemetry telemetry() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? null : sim.telemetry();
    }

    private static double height() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null ? 0.0 : drone.getY() - groundY;
    }

    private static boolean flying() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isFpv() && session.sim() != null && drone != null && drone.getId() == droneId;
    }

    private static void fly(float pitch, float yaw) {
        Telemetry t = telemetry();
        double hover = t == null ? 0.3 : Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.clamp(1.2 * (FLIGHT_HEIGHT - height()), -3.0, 3.0);
        float throttle = (float) Math.clamp(hover + 0.06 * (wanted - vs) + (Math.abs(pitch) > 0.1F ? 0.03 : 0.0), 0.05, 0.9);
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, pitch, yaw, throttle), true, StabMode.ANGLE);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        if (!state.containsKey(PHASE)) {
            next(ctx, "spawn");
        }
        String phase = (String) state.get(PHASE);
        int since = since(ctx);
        boolean airborne = phase.equals("arm") || phase.equals("climb") || phase.equals("turn") || phase.equals("record");
        if (airborne && !flying()) {
            return finish(ctx, StepResult.fail("FPV-Sitzung endete in Phase " + phase));
        }
        switch (phase) {
            case "spawn" -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                ClientRaceTracker.INSTANCE.recorder().reset();
                state.put(FUTURE, ctx.onServer(ReplaySmoothSelfTests::spawn));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return finish(ctx, StepResult.fail("Start: " + future.exceptionNow().getMessage()));
                }
                if (!future.isDone() || !flying()) {
                    return since > TIMEOUT_TICKS ? finish(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
                }
                return next(ctx, "arm");
            }
            case "arm" -> {
                PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, since >= ARM_TICKS / 2, StabMode.ANGLE);
                return since >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
            }
            case "climb" -> {
                fly(0.0F, 0.0F);
                if (since >= CLIMB_TICKS && height() > FLIGHT_HEIGHT - 1.5) {
                    return next(ctx, "turn");
                }
                return since > TIMEOUT_TICKS ? finish(ctx, StepResult.fail(String.format(Locale.ROOT, "Steigflug erreicht %.1f Blöcke nicht: %.2f",
                        FLIGHT_HEIGHT, height()))) : StepResult.pending();
            }
            case "turn" -> {
                fly(TURN_PITCH, TURN_YAW);
                if (since < TURN_TICKS) {
                    return StepResult.pending();
                }
                long start = ClientRaceTracker.INSTANCE.now();
                ClientRaceTracker.INSTANCE.recorder().onLapStart(LapTracker.NO_COURSE, start);
                state.put("lap_start", start);
                return next(ctx, "record");
            }
            case "record" -> {
                fly(TURN_PITCH, TURN_YAW);
                if (since < RECORD_TICKS) {
                    return StepResult.pending();
                }
                return recorded(ctx);
            }
            case "release" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (!future.isDone() || PilotSession.INSTANCE.isActive()) {
                    return since > 200 ? finish(ctx, StepResult.fail("FPV endet nicht")) : StepResult.pending();
                }
                return replay(ctx, mc);
            }
            case "export" -> {
                return export(ctx, mc);
            }
            case "video" -> {
                return video(ctx);
            }
            case "analyse" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (!future.isDone()) {
                    return since > 1200 ? finish(ctx, (StepResult) state.get(RESULT)) : StepResult.pending();
                }
                if (future.isCompletedExceptionally()) {
                    ctx.log("Video nicht gemessen: " + future.exceptionNow().getMessage());
                } else {
                    ctx.log("Video " + state.get("video_file") + ": " + future.join());
                }
                return finish(ctx, (StepResult) state.get(RESULT));
            }
            case "cleanup" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future != null && !future.isDone() || PilotSession.INSTANCE.isActive()) {
                    return since > 200 ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
                }
                return (StepResult) state.get(RESULT);
            }
            default -> {
                return finish(ctx, StepResult.fail("Unbekannte Phase " + phase));
            }
        }
    }

    private static StepResult recorded(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        GhostRecorder recorder = ClientRaceTracker.INSTANCE.recorder();
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null) {
            return finish(ctx, StepResult.fail("Drohne fehlt"));
        }
        long start = (long) state.get("lap_start");
        long end = ClientRaceTracker.INSTANCE.now();
        long lap = end - start;
        String frame = drone.build().frame().key();
        GhostTrack ghost = recorder.onLapComplete(lap, end, frame, 0);
        GhostTrack upload = recorder.keptLap(LapTracker.NO_COURSE, lap);
        recorder.stop();
        if (ghost == null || upload == null) {
            return finish(ctx, StepResult.fail("Runde nicht aufgenommen: Geist " + ghost + ", Upload " + upload));
        }
        GhostTrack shown;
        try {
            shown = GhostCodec.decode(GhostCodec.encode(upload.withLook(GhostLook.of(drone.build(), drone.paint()))));
        } catch (IOException e) {
            return finish(ctx, StepResult.fail("Spur nicht lesbar: " + e.getMessage()));
        }
        CameraPathMeter.Result raced = play(ghost);
        state.put("ghost", raced);
        state.put("shown", shown);
        state.put("frame", frame);
        state.put("lap", lap);
        ctx.log(String.format(Locale.ROOT, "Eigener Flug: %d ms, %d Proben zu %d ms (%d Bytes), Lage in %.0f %% der Folgeproben unverändert", lap / MS,
                upload.samples(), upload.intervalNanos() / MS, GhostCodec.encode(upload).length, 100.0 * held(upload)));
        PilotSession.INSTANCE.clearTestInput();
        state.put(FUTURE, ctx.onServer(ReplaySmoothSelfTests::release));
        return next(ctx, "release");
    }

    private static double held(GhostTrack track) {
        int same = 0;
        for (int i = 1; i < track.samples(); i++) {
            boolean equal = true;
            for (int c = 0; c < 4; c++) {
                equal &= track.rotation(i, c) == track.rotation(i - 1, c);
            }
            if (equal) {
                same++;
            }
        }
        return same / (double) Math.max(1, track.samples() - 1);
    }

    private static CameraPathMeter.Result play(GhostTrack track) {
        CameraPathMeter meter = new CameraPathMeter();
        double[] at = new double[3];
        Quaternionf rotation = new Quaternionf();
        for (long t = 0L; t <= track.durationNanos(); t += CameraPathMeter.FRAME_NANOS) {
            if (GhostRenderer.pose(track, t, at, rotation)) {
                meter.frame(at[0], at[1], at[2], rotation);
            }
        }
        return meter.result();
    }

    private static StepResult replay(SelfTest.StepContext ctx, Minecraft mc) {
        Map<String, Object> state = ctx.state();
        GhostTrack shown = (GhostTrack) state.get("shown");
        String frame = (String) state.get("frame");
        long lap = (long) state.get("lap");
        long date = System.currentTimeMillis();
        RacePayloads.BoardRow row = new RacePayloads.BoardRow("Glattflug", frame, lap, date, true, 1, 1, LapAssist.NONE, List.of(), PILOT, "", true,
                null);
        EntryGhost ghost = new EntryGhost(new EntryGhost.Info(PILOT, "Glattflug", frame, lap, date, LapAssist.NONE, 0, Float.NaN, "",
                EntryGhost.Source.PILOT), GhostCodec.encode(shown));
        ReplaySession session = ReplaySession.INSTANCE;
        session.start(mc, LapTracker.NO_COURSE, row, ghost, shown);
        session.setView(ReplaySession.View.FPV);
        CameraPathMeter meter = new CameraPathMeter();
        Vector3d position = new Vector3d();
        Quaternionf rotation = new Quaternionf();
        for (long t = session.begin(); t <= session.end(); t += CameraPathMeter.FRAME_NANOS) {
            session.seek(t);
            if (session.cameraPose(position, rotation)) {
                meter.frame(position.x, position.y, position.z, rotation);
            }
        }
        session.seek(session.begin());
        CameraPathMeter.Result fpv = meter.result();
        CameraPathMeter.Result raced = (CameraPathMeter.Result) state.get("ghost");
        ctx.log("Replay FPV, " + meter.frames() + " Bilder zu 60 fps: " + fpv.text());
        ctx.log("Geist im Rennen, 60 fps: " + raced.text());
        StepResult result;
        if (fpv.rotation().mean() < MIN_TURN_DEGREES || fpv.position().mean() < MIN_MOVE_BLOCKS) {
            result = StepResult.fail("Flug zu ruhig für die Messung: " + fpv.text());
        } else if (!fpv.smooth()) {
            result = StepResult.fail("Replay ruckelt im 20-Hz-Takt: " + fpv.text());
        } else if (!raced.smooth()) {
            result = StepResult.fail("Geist im Rennen ruckelt im 20-Hz-Takt: " + raced.text());
        } else {
            result = StepResult.pass();
        }
        state.put(RESULT, result);
        return next(ctx, "export");
    }

    private static StepResult export(SelfTest.StepContext ctx, Minecraft mc) {
        Map<String, Object> state = ctx.state();
        ReplayExport.Block block = ReplayVideo.INSTANCE.block(mc);
        if (block == ReplayExport.Block.CHECKING || block == ReplayExport.Block.SAVING) {
            return since(ctx) > TIMEOUT_TICKS ? finish(ctx, (StepResult) state.get(RESULT)) : StepResult.pending();
        }
        state.put("video_saved", ActionCamRecorder.INSTANCE.lastSaved() == null ? "" : String.valueOf(ActionCamRecorder.INSTANCE.lastSaved()));
        if (block != ReplayExport.Block.NONE || !ReplayVideo.INSTANCE.start(mc)) {
            ctx.log("Video nicht aufgenommen: " + block + " " + ReplayVideo.INSTANCE.error());
            return finish(ctx, (StepResult) state.get(RESULT));
        }
        return next(ctx, "video");
    }

    private static StepResult video(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        ReplayVideo video = ReplayVideo.INSTANCE;
        if (video.recording() || video.stage() == ReplayVideo.Stage.SAVING) {
            return since(ctx) > 1600 ? finish(ctx, (StepResult) state.get(RESULT)) : StepResult.pending();
        }
        Path file = ActionCamRecorder.INSTANCE.lastSaved();
        if (video.stage() != ReplayVideo.Stage.SAVED || file == null || file.toString().equals(state.get("video_saved"))) {
            ctx.log("Video nicht gespeichert: " + video.stage() + " " + video.error());
            return finish(ctx, (StepResult) state.get(RESULT));
        }
        state.put("video_file", file);
        state.put(FUTURE, measure(file));
        return next(ctx, "analyse");
    }

    private static CompletableFuture<String> measure(Path file) {
        Minecraft mc = Minecraft.getInstance();
        HardwareEncoders.Status status = HardwareEncoders.status(
                new HardwareEncoders.Settings(true, RecordingConfig.get().ffmpegPath, ActionCamRecorder.ffmpegFolders(mc)),
                ActionCamRecorder.INSTANCE.replayFormat());
        Path ffmpeg = status.ffmpeg();
        return CompletableFuture.supplyAsync(() -> {
            if (ffmpeg == null) {
                throw new IllegalStateException("ffmpeg fehlt: " + status.state());
            }
            try {
                Path out = Files.createTempFile("propwash-replay-yavg", ".txt");
                Process process = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-i", file.toString(), "-vf",
                        "tblend=all_mode=difference,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=" + out.toString().replace(":", "\\:"),
                        "-f", "null", "-").redirectErrorStream(true).start();
                process.getOutputStream().close();
                String log = new String(process.getInputStream().readAllBytes());
                if (!process.waitFor(120L, TimeUnit.SECONDS) || process.exitValue() != 0) {
                    throw new IllegalStateException("ffmpeg: " + log);
                }
                List<Double> values = new ArrayList<>();
                for (String line : Files.readAllLines(out)) {
                    int at = line.indexOf("lavfi.signalstats.YAVG=");
                    if (at >= 0) {
                        values.add(Double.parseDouble(line.substring(at + "lavfi.signalstats.YAVG=".length()).trim()));
                    }
                }
                Files.deleteIfExists(out);
                double[] steps = new double[values.size()];
                for (int i = 0; i < steps.length; i++) {
                    steps[i] = values.get(i);
                }
                return CameraPathMeter.series("Bilddifferenz YAVG", steps).text() + " (" + steps.length + " Bildpaare)";
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
    }

    private static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        state.put(RESULT, result);
        ReplayVideo.INSTANCE.cancel();
        ReplaySession.INSTANCE.stop();
        PilotSession.INSTANCE.clearTestInput();
        ClientRaceTracker.INSTANCE.recorder().reset();
        state.put(FUTURE, ctx.onServer(ReplaySmoothSelfTests::release));
        return next(ctx, "cleanup");
    }

    private static void spawn(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discard(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 feet = player.position();
        groundY = feet.y;
        drone.snapTo(feet.x + 2.0, feet.y + 0.1, feet.z - 6.0, 180.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        drone.setCustomName(Component.literal("Glattflug"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            previousHand = player.getMainHandItem().copy();
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void release(MinecraftServer server) {
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
        previousHead = null;
        previousHand = null;
        discard(server);
    }

    private static void discard(MinecraftServer server) {
        int id = droneId;
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
        droneId = -1;
    }
}

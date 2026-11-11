package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.ReplayExport;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.RecordingConfig;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingLibrary;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import de.lardtom.propwash.video.record.mp4.Mp4Info;
import de.lardtom.propwash.video.record.mp4.Mp4Reader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class ReplayVideoSelfTests {
    private static final String GROUP = "race: Replay als Video: ";
    private static final UUID PILOT = new UUID(0x7E057L, 0x5A11L);
    private static final String PILOT_NAME = "Videopilot";
    private static final String FRAME = "race_5";
    private static final long MS = 1_000_000L;
    private static final long LAP_NANOS = 20_000L * MS;
    private static final double LENGTH_TOLERANCE = 0.4;
    private static final int[][] SMALL_WINDOWS = {{320, 240}, {427, 240}, {640, 360}};
    private static final int MIN_START_GATE_PIXELS = 300;
    private static final double GROUND_FROM = 0.6;

    private record Shot(String name, double seconds, double spread, int course, int middle, int side) {
        boolean drone() {
            return this.middle > 2 * this.side + 300;
        }
    }

    private ReplayVideoSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Knopf aus ohne ffmpeg, mit Hinweis", 600, ReplayVideoSelfTests::buttonStep);
        SelfTest.clientStep(GROUP + "Abbruch mit Esc und beim Schließen speichert nichts", 900, ReplayVideoSelfTests::cancelStep);
        SelfTest.clientStep(GROUP + "20-s-Geist hinter der Drohne, Länge mit Vorlauf, Bilder aus dem Video", 2400, ReplayVideoSelfTests::chaseStep);
        SelfTest.clientStep(GROUP + "FPV nimmt das Bild der Drohnenkamera auf", 1800, ReplayVideoSelfTests::fpvStep);
        SelfTest.clientStep(GROUP + "Aufräumen", 200, ReplayVideoSelfTests::cleanupStep);
    }

    private static StepResult failing(String reason) {
        ReplayVideo.INSTANCE.forceFfmpegState(null);
        return StepResult.fail(reason);
    }

    private static boolean noFfmpeg() {
        ReplayExport.Block block = ReplayVideo.INSTANCE.block(Minecraft.getInstance());
        return block == ReplayExport.Block.NO_FFMPEG || block == ReplayExport.Block.FFMPEG_BROKEN;
    }

    private static @Nullable StepResult loaded(SelfTest.StepContext ctx, UUID pilot) {
        if (ctx.tick() == 0) {
            LeaderboardScreen screen = LeaderboardScreen.active();
            RacePayloads.BoardRow row = RaceGhostsSelfTests.row(pilot);
            if (screen == null || row == null) {
                return StepResult.fail("Bestenliste oder Zeile fehlt: " + screen + " " + row);
            }
            screen.openReplay(row);
            return StepResult.pending();
        }
        ReplayScreen replay = ReplayScreen.active();
        if (replay == null || !replay.loaded() || replay.frames() < 3) {
            if (replay != null && replay.failure() != null) {
                return StepResult.fail("Replay nicht geladen: " + replay.failure());
            }
            return ctx.tick() > 200 ? StepResult.fail("Replay lädt nicht") : StepResult.pending();
        }
        return null;
    }

    private static @Nullable StepResult settle(SelfTest.StepContext ctx, String key, int ticks) {
        if (!ctx.state().containsKey(key)) {
            ctx.state().put(key, ctx.tick());
            return StepResult.pending();
        }
        return ctx.tick() - (int) ctx.state().get(key) < ticks ? StepResult.pending() : null;
    }

    private static @Nullable StepResult shot(SelfTest.StepContext ctx, String name) {
        if (!Screenshots.enabled()) {
            return null;
        }
        if (!ctx.state().containsKey(name)) {
            ctx.state().put(name, Screenshots.capture(name));
            return StepResult.pending();
        }
        return ctx.state().get(name) instanceof CompletableFuture<?> shot && !shot.isDone() ? StepResult.pending() : null;
    }

    private static boolean overlaps(AbstractWidget a, AbstractWidget b) {
        return a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
    }

    private static @Nullable String fits(ReplayScreen replay, int width, int height) {
        replay.resize(width, height);
        FlatButton button = replay.videoButton();
        if (button == null) {
            return width + "x" + height + ": kein Knopf";
        }
        if (button.getX() < 0 || button.getY() < 0 || button.getX() + button.getWidth() > width || button.getY() + button.getHeight() > height) {
            return width + "x" + height + ": Knopf ragt aus dem Fenster";
        }
        for (GuiEventListener child : replay.children()) {
            if (child instanceof AbstractWidget other && other != button && overlaps(button, other)) {
                return width + "x" + height + ": Knopf überdeckt „" + other.getMessage().getString() + "“";
            }
        }
        if (Minecraft.getInstance().font.width(button.getMessage()) + 8 > button.getWidth()) {
            return width + "x" + height + ": Text passt nicht in den Knopf";
        }
        List<LayoutAudit.Finding> findings = LayoutProbe.audit(Minecraft.getInstance(), replay);
        return findings.isEmpty() ? null : width + "x" + height + ": " + findings.getFirst().describe();
    }

    private static String tooltip(ReplayScreen replay, FlatButton button) {
        int x = button.getX() + button.getWidth() / 2;
        int y = button.getY() + button.getHeight() / 2;
        LayoutProbe.capture(Minecraft.getInstance(), replay, x, y);
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LayoutProbe.Frame frame = LayoutProbe.capture(Minecraft.getInstance(), replay, x, y);
        StringBuilder text = new StringBuilder();
        for (LayoutAudit.Text line : frame.tooltip()) {
            text.append(line.value()).append(' ');
        }
        return text.toString();
    }

    private static StepResult buttonStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if ("back".equals(ctx.state().get("stage"))) {
            if (LeaderboardScreen.active() == null) {
                return ctx.tick() > 550 ? StepResult.fail("Verlassen führt nicht zur Bestenliste") : StepResult.pending();
            }
            return ReplaySession.INSTANCE.active() ? StepResult.fail("Replay läuft nach dem Verlassen weiter") : StepResult.pass();
        }
        StepResult open = loaded(ctx, RaceGhostsSelfTests.RIVAL);
        if (open != null) {
            return open;
        }
        ReplayVideo video = ReplayVideo.INSTANCE;
        ReplayScreen replay = ReplayScreen.active();
        FlatButton button = replay == null ? null : replay.videoButton();
        if (replay == null || button == null) {
            return failing("Knopf „Als Video aufnehmen“ fehlt");
        }
        String stage = (String) ctx.state().getOrDefault("stage", "missing");
        switch (stage) {
            case "missing" -> {
                ReplaySession.INSTANCE.setPlaying(false);
                video.forceFfmpegState(HardwareEncoders.State.NO_FFMPEG);
                ctx.state().put("stage", "off");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "off" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 3) {
                    return StepResult.pending();
                }
                if (button.active || replay.videoBlock() != ReplayExport.Block.NO_FFMPEG) {
                    return failing("Knopf ohne ffmpeg nicht aus: aktiv=" + button.active + " " + replay.videoBlock());
                }
                String hint = Component.translatable(ReplayExport.Block.NO_FFMPEG.translationKey()).getString();
                if (!hint.contains("ffmpeg") || hint.equals(ReplayExport.Block.NO_FFMPEG.translationKey())) {
                    return failing("Hinweis ohne ffmpeg fehlt: " + hint);
                }
                String shown = tooltip(replay, button);
                if (!shown.contains("ffmpeg")) {
                    return failing("Tooltip am Knopf ohne Hinweis auf ffmpeg: „" + shown + "“");
                }
                button.press();
                if (video.stage() != ReplayVideo.Stage.IDLE || ActionCamRecorder.INSTANCE.phase() != ActionCamRecorder.Phase.IDLE) {
                    return failing("Aufnahme startet ohne ffmpeg: " + video.stage());
                }
                LayoutProbe.pointMouse(mc, button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2);
                StepResult picture = settle(ctx, "hover", 6);
                if (picture != null) {
                    return picture;
                }
                picture = shot(ctx, "race_replay_video_no_ffmpeg");
                if (picture != null) {
                    return picture;
                }
                Screenshots.parkMouse(mc);
                video.forceFfmpegState(HardwareEncoders.State.UNUSABLE);
                ctx.state().put("stage", "broken");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "broken" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 3) {
                    return StepResult.pending();
                }
                if (button.active || replay.videoBlock() != ReplayExport.Block.FFMPEG_BROKEN) {
                    return failing("Knopf mit kaputtem ffmpeg nicht aus: " + replay.videoBlock());
                }
                video.forceFfmpegState(HardwareEncoders.State.READY);
                ctx.state().put("stage", "ready");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "ready" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 3) {
                    return StepResult.pending();
                }
                if (!button.active || replay.videoBlock() != ReplayExport.Block.NONE) {
                    return failing("Knopf mit ffmpeg nicht an: " + replay.videoBlock());
                }
                video.forceFfmpegState(null);
                List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, replay);
                if (!findings.isEmpty()) {
                    return StepResult.fail(findings.size() + " Befunde im Replay: " + findings.getFirst().describe());
                }
                LayoutProbe.TooltipSweep tips = LayoutProbe.tooltips(mc, replay);
                if (!tips.findings().isEmpty()) {
                    return StepResult.fail("Tooltips im Replay: " + tips.findings().getFirst().describe());
                }
                int width = replay.width;
                int height = replay.height;
                for (int[] size : SMALL_WINDOWS) {
                    String problem = fits(replay, size[0], size[1]);
                    if (problem != null) {
                        replay.resize(width, height);
                        return StepResult.fail(problem);
                    }
                }
                replay.resize(width, height);
                ctx.state().put("stage", "real");
                return StepResult.pending();
            }
            case "real" -> {
                if (replay.videoBlock() == ReplayExport.Block.CHECKING) {
                    return StepResult.pending();
                }
                if (ctx.state().put("logged", true) == null) {
                    ctx.log("ffmpeg auf diesem Rechner: " + replay.videoBlock());
                }
                FlatButton current = replay.videoButton();
                LayoutProbe.pointMouse(mc, current.getX() + current.getWidth() / 2, current.getY() + current.getHeight() / 2);
                StepResult picture = settle(ctx, "hover2", 6);
                if (picture != null) {
                    return picture;
                }
                picture = shot(ctx, "race_replay_video_button");
                if (picture != null) {
                    return picture;
                }
                Screenshots.parkMouse(mc);
                replay.onClose();
                ctx.state().put("stage", "back");
                return StepResult.pending();
            }
            default -> {
                return StepResult.fail("Bildschirm nicht geschlossen");
            }
        }
    }

    private static Set<String> files(Path dir) {
        Set<String> names = new TreeSet<>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).map(file -> file.getFileName().toString()).forEach(names::add);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return names;
    }

    private static StepResult cancelStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        String stage = (String) ctx.state().getOrDefault("stage", "open");
        ReplayVideo video = ReplayVideo.INSTANCE;
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        ReplaySession session = ReplaySession.INSTANCE;
        Path dir = ActionCamRecorder.directory(mc);
        switch (stage) {
            case "open" -> {
                StepResult open = loaded(ctx, RaceGhostsSelfTests.RIVAL);
                if (open != null) {
                    return open;
                }
                ReplayScreen replay = ReplayScreen.active();
                if (replay.videoBlock() == ReplayExport.Block.CHECKING) {
                    return StepResult.pending();
                }
                if (noFfmpeg()) {
                    ctx.log("kein ffmpeg auf diesem Rechner, Aufnahme nicht geprüft");
                    replay.onClose();
                    return StepResult.pass();
                }
                ctx.state().put("before", files(dir));
                replay.videoButton().press();
                if (video.stage() != ReplayVideo.Stage.RECORDING || !recorder.replayRecording() || !session.exporting()) {
                    return StepResult.fail("Aufnahme läuft nicht: " + video.stage() + " " + recorder.phase() + " " + recorder.lastError());
                }
                if (replay.cancelButton() == null || replay.playButton() != null || replay.videoButton() != null) {
                    return StepResult.fail("Knöpfe während der Aufnahme falsch");
                }
                if (session.speed() != 1.0) {
                    return StepResult.fail("Aufnahme nicht mit 1×: " + session.speed());
                }
                ctx.state().put("stage", "running");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "running" -> {
                ReplayScreen replay = ReplayScreen.active();
                if (replay == null || !video.recording()) {
                    return StepResult.fail("Aufnahme endete von selbst: " + video.stage());
                }
                if (recorder.capturedFrames() < 5 || video.progress() <= 0.0F) {
                    return ctx.tick() - (int) ctx.state().get("at") > 300
                            ? StepResult.fail("Aufnahme liefert keine Bilder: " + recorder.capturedFrames() + ", " + recorder.lastError()) : StepResult.pending();
                }
                ReplaySession.View view = session.view();
                replay.keyPressed(new KeyEvent(InputConstants.KEY_V, 0, 0));
                long before = session.time();
                replay.skip(-ReplaySession.SKIP_NANOS);
                if (session.view() != view || session.time() != before) {
                    return StepResult.fail("Bedienung greift in die laufende Aufnahme ein");
                }
                if (!replay.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0))) {
                    return StepResult.fail("Esc nicht angenommen");
                }
                if (video.stage() != ReplayVideo.Stage.CANCELLED || recorder.phase() != ActionCamRecorder.Phase.IDLE || session.exporting()) {
                    return StepResult.fail("Esc bricht nicht ab: " + video.stage() + " " + recorder.phase());
                }
                if (mc.gui.screen() != replay || replay.playButton() == null || replay.cancelButton() != null) {
                    return StepResult.fail("Esc verlässt das Replay oder stellt die Knöpfe nicht wieder her");
                }
                if (!session.active()) {
                    return StepResult.fail("Replay nach dem Abbruch beendet");
                }
                ctx.state().put("stage", "clean");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "clean" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 30) {
                    return StepResult.pending();
                }
                @SuppressWarnings("unchecked")
                Set<String> before = (Set<String>) ctx.state().get("before");
                Set<String> after = files(dir);
                if (!after.equals(before)) {
                    after.removeAll(before);
                    return StepResult.fail("Abbruch hinterlässt Dateien: " + after);
                }
                ReplayScreen replay = ReplayScreen.active();
                if (replay == null) {
                    return StepResult.fail("Replay zu");
                }
                FlatButton button = replay.videoButton();
                if (button == null || !button.active) {
                    return ctx.tick() - (int) ctx.state().get("at") > 200 ? StepResult.fail("Knopf nach dem Abbruch nicht wieder an") : StepResult.pending();
                }
                button.press();
                if (!video.recording()) {
                    return StepResult.fail("Zweite Aufnahme startet nicht: " + video.stage());
                }
                ctx.state().put("stage", "closing");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "closing" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 20) {
                    return StepResult.pending();
                }
                mc.gui.setScreen(new LeaderboardScreen(null, RaceSelfTests.courseId()));
                if (video.stage() != ReplayVideo.Stage.IDLE || recorder.phase() != ActionCamRecorder.Phase.IDLE || session.active()) {
                    return StepResult.fail("Schließen bricht die Aufnahme nicht ab: " + video.stage() + " " + recorder.phase());
                }
                ctx.state().put("stage", "closed");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            default -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 30) {
                    return StepResult.pending();
                }
                @SuppressWarnings("unchecked")
                Set<String> before = (Set<String>) ctx.state().get("before");
                Set<String> after = files(dir);
                if (!after.equals(before)) {
                    after.removeAll(before);
                    return StepResult.fail("Schließen hinterlässt Dateien: " + after);
                }
                return LeaderboardScreen.active() == null ? StepResult.fail("Bestenliste nicht offen") : StepResult.pass();
            }
        }
    }

    private static @Nullable String addPilot(MinecraftServer server) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(RaceSelfTests.courseId());
        if (course == null) {
            return "Kurs fehlt auf dem Server";
        }
        long date = System.currentTimeMillis();
        Leaderboard.Result result = registry.submitTime(course, PILOT, PILOT_NAME, FRAME, LAP_NANOS, date, AssistReasons.NONE, RaceRuleSet.DEFAULTS, true);
        if (!result.stored()) {
            return "Zeit des Videopiloten nicht gespeichert";
        }
        int layout = course.toData().layoutHash();
        GhostTrack track = RaceGhostsSelfTests.rivalTrack(course.toData().geometry(), LAP_NANOS, layout);
        if (track == null) {
            return "Spur des Videopiloten nicht geschnitten";
        }
        Leaderboard.Entry own = registry.board(course).best(server.getPlayerList().getPlayers().getFirst().getUUID(), null);
        EntryGhost.Info ownInfo = own == null ? null : registry.entryGhosts().info(course.key(), GhostKey.of(own));
        String code = ownInfo == null ? "" : ownInfo.code();
        EntryGhost.Info info = new EntryGhost.Info(PILOT, PILOT_NAME, FRAME, LAP_NANOS, date, LapAssist.NONE, layout, 30.0F, code, EntryGhost.Source.SERVER);
        if (!registry.entryGhosts().put(course.key(), new EntryGhost(info, GhostCodec.encode(track)))) {
            return "Geist des Videopiloten nicht abgelegt";
        }
        return RaceServer.sendLeaderboard(server.getPlayerList().getPlayers().getFirst(), course.id, false) ? null : "Bestenliste nicht geschickt";
    }

    private static CompletableFuture<List<Shot>> frames(Path file, String prefix, double[] seconds, double still) {
        Minecraft mc = Minecraft.getInstance();
        RecordingFormat format = ActionCamRecorder.INSTANCE.replayFormat();
        HardwareEncoders.Status status = HardwareEncoders.status(
                new HardwareEncoders.Settings(true, RecordingConfig.get().ffmpegPath, ActionCamRecorder.ffmpegFolders(mc)), format);
        Path ffmpeg = status.ffmpeg();
        return CompletableFuture.supplyAsync(() -> {
            List<Shot> shots = new ArrayList<>();
            if (ffmpeg == null) {
                throw new IllegalStateException("ffmpeg fehlt: " + status.state());
            }
            try {
                Path dir = Files.createTempDirectory("propwash-replay-video");
                for (double at : seconds) {
                    Path out = dir.resolve(prefix + "_" + String.format(Locale.ROOT, "%04.1f", at).replace('.', '_') + "s.png");
                    Process process = new ProcessBuilder(ffmpeg.toString(), "-y", "-hide_banner", "-loglevel", "error", "-ss",
                            String.format(Locale.ROOT, "%.2f", at), "-i", file.toString(), "-frames:v", "1", out.toString())
                            .redirectErrorStream(true).start();
                    process.getOutputStream().close();
                    String log = new String(process.getInputStream().readAllBytes());
                    if (!process.waitFor(30L, TimeUnit.SECONDS) || process.exitValue() != 0 || !Files.isRegularFile(out)) {
                        throw new IllegalStateException("Bild bei " + at + " s nicht erzeugt: " + log);
                    }
                    shots.add(analyse(out, at));
                    if (Screenshots.enabled() && at == still) {
                        Files.copy(out, Screenshots.dir().resolve(prefix + ".png"), StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.deleteIfExists(out);
                }
                Files.deleteIfExists(dir);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return shots;
        });
    }

    private static Shot analyse(Path file, double at) throws IOException {
        try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            int width = image.getWidth();
            int height = image.getHeight();
            double sum = 0.0;
            double squares = 0.0;
            int count = 0;
            int course = 0;
            for (int y = 0; y < height; y += 2) {
                for (int x = 0; x < width; x += 2) {
                    int argb = image.getPixel(x, y);
                    int r = argb >> 16 & 0xFF;
                    int g = argb >> 8 & 0xFF;
                    int b = argb & 0xFF;
                    double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                    sum += luma;
                    squares += luma * luma;
                    count++;
                    boolean light = r > 190 && g > 190 && b > 190 || g > r + 60 && g > b + 40 && g > 130;
                    if (light && y >= height * GROUND_FROM) {
                        course++;
                    }
                }
            }
            double mean = sum / count;
            int offset = width / 10;
            int middle = notGrass(image, width / 2);
            int side = Math.max(notGrass(image, width / 2 - offset), notGrass(image, width / 2 + offset));
            return new Shot(file.getFileName().toString(), at, Math.sqrt(Math.max(0.0, squares / count - mean * mean)), course, middle, side);
        }
    }

    private static int notGrass(NativeImage image, int centerX) {
        int halfWidth = image.getWidth() / 32;
        int halfHeight = image.getHeight() / 27;
        int centerY = image.getHeight() / 2;
        int found = 0;
        for (int y = centerY - halfHeight; y < centerY + halfHeight; y++) {
            for (int x = centerX - halfWidth; x < centerX + halfWidth; x++) {
                int argb = image.getPixel(x, y);
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                if (g < r || g < b) {
                    found++;
                }
            }
        }
        return found;
    }

    private static @Nullable String checkFile(SelfTest.StepContext ctx, Path file, double seconds) {
        Minecraft mc = Minecraft.getInstance();
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (!Files.isRegularFile(file) || !file.getFileName().toString().matches("propwash_replay_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}(_\\d+)?\\.mp4")) {
            return "Videodatei fehlt oder falscher Name: " + file;
        }
        if (!file.toAbsolutePath().getParent().equals(ActionCamRecorder.directory(mc).toAbsolutePath())) {
            return "Video nicht im Action-Cam-Ordner: " + file;
        }
        Mp4Info info;
        try {
            info = Mp4Reader.read(file);
        } catch (IOException e) {
            return "MP4 lässt sich nicht lesen: " + e.getMessage();
        }
        RecordingFormat wanted = recorder.replayFormat();
        ctx.log("MP4: " + info.width() + "x" + info.height() + ", " + info.sampleCount() + " Bilder, " + info.durationSeconds() + " s (Soll "
                + seconds + " s), " + info.frameRate() + " Bilder/s, " + recorder.capturedFrames() + " gelesen, Qualität " + recorder.quality().key());
        if (!info.codec().equals("avc1") || !info.handler().equals("vide") || !info.startsWithSync()) {
            return "kein H.264-Video: " + info.codec() + "/" + info.handler();
        }
        if (info.width() != wanted.width() || info.height() != wanted.height() || Math.abs(info.frameRate() - wanted.fps()) > 0.01) {
            return "Format " + info.width() + "x" + info.height() + "@" + info.frameRate() + " statt " + wanted.label();
        }
        RecordingConfig config = RecordingConfig.get();
        RecordingFormat configured = RecordingFormat.of(config.height, config.fps);
        if (wanted.height() != configured.height() || wanted.fps() != configured.fps()) {
            return "Format " + wanted.label() + " nicht wie im Tab Action-Cam: " + configured.label();
        }
        if (recorder.quality() != config.quality) {
            return "Qualität " + recorder.quality().key() + " statt " + config.quality.key();
        }
        if (Math.abs(info.durationSeconds() - seconds) > LENGTH_TOLERANCE) {
            return "Länge " + info.durationSeconds() + " s statt " + seconds + " s";
        }
        if (info.hasAudio()) {
            return "Tonspur im Video eines Geists";
        }
        if (recorder.capturedFrames() < seconds * wanted.fps() / 4.0) {
            return "nur " + recorder.capturedFrames() + " Bilder von der GPU";
        }
        return null;
    }

    private static @Nullable String checkShots(SelfTest.StepContext ctx, List<Shot> shots, boolean chase) {
        boolean drone = false;
        for (Shot shot : shots) {
            ctx.log(String.format(Locale.ROOT, "Bild %s: Streuung %.1f, Kurs-Pixel %d, Mitte %d, daneben %d", shot.name(), shot.spread(), shot.course(),
                    shot.middle(), shot.side()));
            if (shot.spread() < 5.0) {
                return "Bild " + shot.name() + " fast einfarbig: Streuung " + shot.spread();
            }
            drone |= shot.drone();
        }
        if (shots.getFirst().course() < MIN_START_GATE_PIXELS) {
            return "Startgate im ersten Bild nicht zu sehen: " + shots.getFirst().course() + " helle Pixel über dem Boden";
        }
        if (drone != chase) {
            return chase ? "Drohne in der Ansicht dahinter auf keinem Bild" : "FPV zeigt die eigene Drohne in der Bildmitte";
        }
        return null;
    }

    private static void deleteClip(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(RecordingLibrary.thumbnailOf(file));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static StepResult chaseStep(SelfTest.StepContext ctx) {
        StepResult waiting = RaceGhostsSelfTests.waitServer(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (!ctx.state().containsKey("added")) {
            if (noFfmpeg()) {
                ctx.log("kein ffmpeg auf diesem Rechner, Aufnahme nicht geprüft");
                return StepResult.pass();
            }
            ctx.state().put("added", true);
            RaceGhostsSelfTests.server(ctx, ReplayVideoSelfTests::addPilot);
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("opened")) {
            RacePayloads.BoardRow row = RaceGhostsSelfTests.row(PILOT);
            if (row == null || !row.ghost()) {
                return ctx.tick() > 300 ? StepResult.fail("Zeile des Videopiloten ohne Geist: " + row) : StepResult.pending();
            }
            LeaderboardScreen screen = LeaderboardScreen.active();
            if (screen == null) {
                return StepResult.fail("Bestenliste nicht offen");
            }
            screen.openReplay(row);
            ctx.state().put("opened", ctx.tick());
            return StepResult.pending();
        }
        return record(ctx, ReplaySession.View.CHASE, "race_replay_video_chase", new double[] {0.3, 3.0, 8.0, 12.0, 19.0}, 12.0);
    }

    private static StepResult fpvStep(SelfTest.StepContext ctx) {
        if (noFfmpeg()) {
            ctx.log("kein ffmpeg auf diesem Rechner, Aufnahme nicht geprüft");
            return StepResult.pass();
        }
        if (!ctx.state().containsKey("opened")) {
            LeaderboardScreen screen = LeaderboardScreen.active();
            RacePayloads.BoardRow row = RaceGhostsSelfTests.row(RaceGhostsSelfTests.RIVAL);
            if (screen == null || row == null) {
                return StepResult.fail("Bestenliste oder Zeile fehlt");
            }
            screen.openReplay(row);
            ctx.state().put("opened", ctx.tick());
            return StepResult.pending();
        }
        return record(ctx, ReplaySession.View.FPV, "race_replay_video_fpv", new double[] {0.3, 1.5, 2.5}, 0.3);
    }

    private static StepResult record(SelfTest.StepContext ctx, ReplaySession.View view, String prefix, double[] seconds, double still) {
        Minecraft mc = Minecraft.getInstance();
        ReplayVideo video = ReplayVideo.INSTANCE;
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        ReplaySession session = ReplaySession.INSTANCE;
        ReplayScreen replay = ReplayScreen.active();
        String stage = (String) ctx.state().getOrDefault("stage", "load");
        switch (stage) {
            case "load" -> {
                if (replay == null || !replay.loaded() || replay.frames() < 3) {
                    if (replay != null && replay.failure() != null) {
                        return StepResult.fail("Replay nicht geladen: " + replay.failure());
                    }
                    return ctx.tick() - (int) ctx.state().get("opened") > 200 ? StepResult.fail("Replay lädt nicht") : StepResult.pending();
                }
                if (replay.videoBlock() == ReplayExport.Block.CHECKING || replay.videoBlock() == ReplayExport.Block.SAVING) {
                    return ctx.tick() > 800 ? StepResult.fail("Knopf bleibt aus: " + replay.videoBlock()) : StepResult.pending();
                }
                if (session.view() != view) {
                    replay.toggleView();
                }
                ctx.state().put("length", session.duration() / 1.0E9);
                ctx.state().put("saved", recorder.lastSaved() == null ? "" : recorder.lastSaved().toString());
                replay.videoButton().press();
                if (!video.recording()) {
                    return StepResult.fail("Aufnahme startet nicht: " + video.stage() + " " + recorder.lastError());
                }
                if (session.time() != session.begin() || session.view() != view) {
                    return StepResult.fail("Aufnahme beginnt nicht am Anfang des Vorlaufs: " + session.time() / MS + " ms");
                }
                ctx.state().put("stage", "running");
                ctx.state().put("last", 0.0F);
                ctx.state().put("drawn", GhostRenderer.replayDrawn());
                return StepResult.pending();
            }
            case "running" -> {
                if (video.recording()) {
                    float now = video.progress();
                    if (now < (float) ctx.state().get("last")) {
                        return StepResult.fail("Fortschritt läuft rückwärts: " + now);
                    }
                    ctx.state().put("last", now);
                    if (replay == null || replay.videoStatus() == null) {
                        return StepResult.fail("Fortschrittstext fehlt");
                    }
                    if (now > 0.5F) {
                        if (view == ReplaySession.View.CHASE) {
                            shot(ctx, prefix + "_progress");
                        }
                        if (ctx.state().put("pictured", true) == null) {
                            ctx.log("Fortschritt: " + replay.videoStatus().getString());
                        }
                    }
                    return StepResult.pending();
                }
                if (video.stage() == ReplayVideo.Stage.SAVING) {
                    return StepResult.pending();
                }
                if (video.stage() != ReplayVideo.Stage.SAVED) {
                    return StepResult.fail("Aufnahme endet mit " + video.stage() + ": " + video.error());
                }
                Path file = recorder.lastSaved();
                if (file == null || file.toString().equals(ctx.state().get("saved"))) {
                    return StepResult.fail("kein neues Video gespeichert: " + video.error());
                }
                if (replay == null || replay.videoStatus() == null || !replay.videoStatus().getString().contains(file.getFileName().toString())) {
                    return StepResult.fail("Replay nennt die Datei nicht");
                }
                String problem = checkFile(ctx, file, (double) ctx.state().get("length"));
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                int drawn = GhostRenderer.replayDrawn() - (int) ctx.state().get("drawn");
                if (view == ReplaySession.View.CHASE ? drawn < 60 : drawn != 0) {
                    return StepResult.fail("Drohne während der Aufnahme " + drawn + "-mal gezeichnet");
                }
                ctx.state().put("file", file);
                ctx.state().put("frames", frames(file, prefix, seconds, still));
                ctx.state().put("stage", "frames");
                return StepResult.pending();
            }
            case "frames" -> {
                @SuppressWarnings("unchecked")
                CompletableFuture<List<Shot>> frames = (CompletableFuture<List<Shot>>) ctx.state().get("frames");
                if (!frames.isDone()) {
                    return StepResult.pending();
                }
                if (frames.isCompletedExceptionally()) {
                    return StepResult.fail("Bilder aus dem Video: " + frames.exceptionNow());
                }
                String problem = checkShots(ctx, frames.join(), view == ReplaySession.View.CHASE);
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                deleteClip((Path) ctx.state().get("file"));
                if (replay == null) {
                    return StepResult.fail("Replay zu");
                }
                replay.onClose();
                ctx.state().put("stage", "back");
                return StepResult.pending();
            }
            default -> {
                if (!(mc.gui.screen() instanceof LeaderboardScreen)) {
                    return StepResult.fail("Verlassen führt nicht zur Bestenliste");
                }
                return session.active() ? StepResult.fail("Replay läuft nach dem Verlassen weiter") : StepResult.pass();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult waiting = RaceGhostsSelfTests.waitServer(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (!ctx.state().containsKey("sent")) {
            ctx.state().put("sent", true);
            ReplayVideo.INSTANCE.forceFfmpegState(null);
            int course = RaceSelfTests.courseId();
            RaceGhostsSelfTests.server(ctx, server -> {
                if (RaceGhostsSelfTests.row(PILOT) != null) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "propwash race remove " + course + " " + PILOT_NAME);
                }
                return RaceServer.sendLeaderboard(server.getPlayerList().getPlayers().getFirst(), course, false) ? null : "Bestenliste nicht geschickt";
            });
            return StepResult.pending();
        }
        if (RaceGhostsSelfTests.row(PILOT) != null) {
            return ctx.tick() > 150 ? StepResult.fail("Zeit des Videopiloten noch in der Liste") : StepResult.pending();
        }
        return StepResult.pass();
    }
}

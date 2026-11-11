package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.RaceGhostsJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GhostAccess;
import de.lardtom.propwash.race.GhostChoice;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.GhostRecorder;
import de.lardtom.propwash.race.GhostServer;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.share.client.chat.ShareChat;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

public final class RaceGhostsSelfTests {
    public static final UUID RIVAL = new UUID(0x6E057L, 0x4A11L);
    public static final String RIVAL_NAME = "Rivale";
    private static final String FRAME = "race_5";
    private static final long MS = 1_000_000L;
    private static final String FUTURE = "future";
    private static final double LEAD_DISTANCE = 8.0;
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final AtomicReference<String> RIVAL_CODE = new AtomicReference<>("");
    private static final AtomicReference<Long> RIVAL_NANOS = new AtomicReference<>(0L);
    public static final String LEAD_PILOT = "Anflug";
    private static final AtomicReference<String> LEAD_LOG = new AtomicReference<>("");

    private RaceGhostsSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Geister der Bestenliste von zwei Piloten mit Sharecode", 300, RaceGhostsSelfTests::twoPilotsStep);
        SelfTest.clientStep("race: Sharecode in der Bestenliste kopieren und ansehen", 200, RaceGhostsSelfTests::shareCodeStep);
        SelfTest.clientStep("race: Replay eines fremden Eintrags aus FPV", 600, RaceGhostsSelfTests::replayStep);
        SelfTest.clientStep("race: Replay beginnt vor dem Startgate", 500, RaceGhostsSelfTests::replayLeadStep);
        SelfTest.clientStep("race: Replay zählt dieselben Gates wie Kurs-Einstellungen und Bestenliste", 300, RaceGhostsSelfTests::replayGatesStep);
        ReplayVideoSelfTests.register();
        SelfTest.clientStep("race: Fremden Geist als Gegner wählen", 400, RaceGhostsSelfTests::opponentStep);
        SelfTest.clientStep("race: Welt-Regel Fremde Geister und Replays", 400, RaceGhostsSelfTests::ruleStep);
        SelfTest.clientStep("race: Zeit löschen als Admin", 400, RaceGhostsSelfTests::removeStep);
        SelfTest.clientStep("race: Geister der Bestenliste aus dem Server-Flug beginnen vor dem Startgate", 200, RaceGhostsSelfTests::serverLeadStep);
        RaceGhostsNetSelfTests.register();
        ReplaySmoothSelfTests.register();
    }

    static @Nullable StepResult waitServer(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        String result = RESULT.getAndSet(null);
        return result == null ? null : StepResult.fail(result);
    }

    static void server(SelfTest.StepContext ctx, Function<MinecraftServer, @Nullable String> action) {
        RESULT.set(null);
        ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(action.apply(server))));
    }

    static GhostTrack rivalTrack(List<GateGeometry> gates, long lapNanos, int layout) {
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= gates.size(); i++) {
            GateGeometry gate = gates.get(i % gates.size());
            Vec3 center = new Vec3(gate.x(), gate.centerY(), gate.z());
            Vec3 normal = new Vec3(gate.normalX(), 0.0, gate.normalZ());
            if (i > 0) {
                points.add(center.subtract(normal));
            }
            if (i < gates.size()) {
                points.add(center.add(normal));
            }
        }
        double[] at = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            at[i] = at[i - 1] + points.get(i).distanceTo(points.get(i - 1));
        }
        double total = at[at.length - 1];
        GhostRecorder recorder = new GhostRecorder();
        Quaternionf facing = new Quaternionf();
        GateGeometry start = gates.getFirst();
        Vec3 startNormal = new Vec3(start.normalX(), 0.0, start.normalZ());
        Vec3 approach = new Vec3(start.x(), start.centerY(), start.z()).subtract(startNormal.scale(LEAD_DISTANCE));
        float startYaw = (float) Math.atan2(-startNormal.x, -startNormal.z);
        facing.identity().rotateY(startYaw).rotateX((float) Math.toRadians(-25.0));
        for (long t = -GhostRecorder.LEAD_NANOS; t < 0L; t += 50 * MS) {
            Vec3 here = approach.lerp(points.getFirst(), (double) (t + GhostRecorder.LEAD_NANOS) / GhostRecorder.LEAD_NANOS);
            recorder.sample(t, here.x, here.y - 0.1, here.z, facing);
        }
        recorder.onLapStart(1, 0L);
        int leg = 1;
        for (long t = 0L; t <= lapNanos + 50 * MS; t += 50 * MS) {
            double distance = Math.min(1.0, (double) t / lapNanos) * total;
            while (leg < points.size() - 1 && at[leg] < distance) {
                leg++;
            }
            Vec3 from = points.get(leg - 1);
            Vec3 to = points.get(leg);
            double span = at[leg] - at[leg - 1];
            Vec3 here = span <= 0.0 ? to : from.lerp(to, (distance - at[leg - 1]) / span);
            float yaw = (float) Math.atan2(-(to.x - from.x), -(to.z - from.z));
            facing.identity().rotateY(yaw).rotateX((float) Math.toRadians(-25.0));
            recorder.sample(t, here.x, here.y - 0.1, here.z, facing);
        }
        return recorder.cut(0L, lapNanos, lapNanos, FRAME, layout, GhostServer.FINE_INTERVAL_NANOS, GhostRecorder.LEAD_NANOS);
    }

    private static @Nullable String addRival(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(RaceSelfTests.courseId());
        if (course == null) {
            return "Kurs fehlt auf dem Server";
        }
        if (!registry.entryGhosts().enabled()) {
            return "Ablage der Geister im Weltordner fehlt";
        }
        Leaderboard board = registry.board(course);
        Leaderboard.Entry own = board.best(player.getUUID(), null);
        if (own == null) {
            return "eigene Zeit fehlt";
        }
        EntryGhost.Info ownInfo = registry.entryGhosts().info(course.key(), GhostKey.of(own));
        if (ownInfo == null || ownInfo.code().isEmpty() || !ownInfo.code().startsWith("PW3")) {
            return "eigener Eintrag ohne Geist oder Sharecode: " + ownInfo;
        }
        long nanos = Math.max(2_000L * MS, own.nanos() - 400L * MS);
        long date = System.currentTimeMillis();
        Leaderboard.Result result = registry.submitTime(course, RIVAL, RIVAL_NAME, FRAME, nanos, date, AssistReasons.NONE, RaceRuleSet.DEFAULTS, true);
        if (!result.stored()) {
            return "Zeit des zweiten Piloten nicht gespeichert";
        }
        int layout = course.toData().layoutHash();
        GhostTrack track = rivalTrack(course.toData().geometry(), nanos, layout);
        if (track == null) {
            return "Spur des zweiten Piloten nicht geschnitten";
        }
        EntryGhost.Info info = new EntryGhost.Info(RIVAL, RIVAL_NAME, FRAME, nanos, date, LapAssist.NONE, layout, 30.0F, ownInfo.code(),
                EntryGhost.Source.SERVER);
        if (!registry.entryGhosts().put(course.key(), new EntryGhost(info, GhostCodec.encode(track)))) {
            return "Geist des zweiten Piloten nicht abgelegt";
        }
        Path file = registry.entryGhosts().file(course.key(), info.key());
        if (file == null || !Files.isRegularFile(file) || !file.toString().contains("race-data")) {
            return "Geist liegt nicht unter race-data/ghosts: " + file;
        }
        RIVAL_CODE.set(ownInfo.code());
        RIVAL_NANOS.set(nanos);
        if (!RaceServer.sendLeaderboard(player, course.id, false)) {
            return "Bestenliste nicht geschickt";
        }
        return null;
    }

    static RacePayloads.@Nullable BoardRow row(UUID player) {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(RaceSelfTests.courseId());
        if (board == null) {
            return null;
        }
        for (RacePayloads.BoardRow row : board.rows()) {
            if (!row.assisted() && row.player().equals(player) && row.frame().equals(FRAME) && row.classRank() > 0) {
                return row;
            }
        }
        return null;
    }

    private static RacePayloads.@Nullable BoardRow ownRow() {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(RaceSelfTests.courseId());
        if (board == null) {
            return null;
        }
        for (RacePayloads.BoardRow row : board.rows()) {
            if (row.self() && !row.assisted()) {
                return row;
            }
        }
        return null;
    }

    private static StepResult twoPilotsStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            server(ctx, RaceGhostsSelfTests::addRival);
            return StepResult.pending();
        }
        StepResult waiting = waitServer(ctx);
        if (waiting != null) {
            return waiting;
        }
        RacePayloads.BoardRow rival = row(RIVAL);
        RacePayloads.BoardRow own = ownRow();
        if (rival == null || own == null || !rival.ghost() || !own.ghost()) {
            return ctx.tick() > 250 ? StepResult.fail("Zeilen ohne Geist: rivale=" + rival + " eigen=" + own) : StepResult.pending();
        }
        if (!rival.code().equals(RIVAL_CODE.get()) || own.code().isEmpty()) {
            return StepResult.fail("Sharecode fehlt in der Bestenliste: rivale=" + rival.code().length() + " eigen=" + own.code().length());
        }
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(RaceSelfTests.courseId());
        if (board == null || !board.mayReplayOthers() || !board.mayRaceOthers()) {
            return StepResult.fail("Rechte in der Bestenliste falsch: " + (board == null ? "keine" : board.access()));
        }
        ctx.log("Zwei Piloten mit Geist: eigener Code " + own.code().length() + " Zeichen, Rivale " + RaceFormat.time(rival.nanos()));
        return StepResult.pass();
    }

    private static StepResult shareCodeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int course = RaceSelfTests.courseId();
        if (ctx.tick() == 0) {
            mc.gui.setScreen(new LeaderboardScreen(null, course));
            return StepResult.pending();
        }
        LeaderboardScreen screen = LeaderboardScreen.active();
        RacePayloads.BoardRow rival = row(RIVAL);
        if (screen == null || rival == null || screen.frames() < 2) {
            return ctx.tick() > 150 ? StepResult.fail("Bestenliste nicht offen") : StepResult.pending();
        }
        if (screen.selected() == null) {
            int y = screen.rowY(rival);
            if (y == Integer.MIN_VALUE) {
                return StepResult.fail("Zeile des Rivalen nicht gezeichnet");
            }
            if (screen.rowAt(screen.tableCenterX(), y + 2) == null) {
                return StepResult.fail("Zeile des Rivalen nicht anklickbar");
            }
            screen.select(rival);
            ctx.state().put("selected", ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) ctx.state().get("selected") < 3) {
            return StepResult.pending();
        }
        if (screen.copyButton() == null || !screen.copyButton().active || screen.importButton() == null || !screen.importButton().active) {
            return StepResult.fail("Kopieren/Importieren nicht bereit");
        }
        if (screen.replayButton() == null || !screen.replayButton().active || screen.opponentButton() == null || !screen.opponentButton().active) {
            return StepResult.fail("Replay/Gegen fliegen nicht bereit");
        }
        if (screen.removeButton() == null) {
            return StepResult.fail("Admin sieht kein Zeit löschen");
        }
        double mouseX = screen.codeCenterX() * mc.getWindow().getScreenWidth() / (double) mc.getWindow().getGuiScaledWidth();
        double mouseY = screen.codeCenterY() * mc.getWindow().getScreenHeight() / (double) mc.getWindow().getGuiScaledHeight();
        mc.mouseHandler.onMove(mc.getWindow().handle(), mouseX, mouseY, 0.0, 0.0);
        if (!ctx.state().containsKey("hover")) {
            ctx.state().put("hover", ctx.tick());
            ctx.state().put("tips", screen.codeTooltips());
            return StepResult.pending();
        }
        if (screen.codeTooltips() <= (int) ctx.state().get("tips")) {
            return ctx.tick() - (int) ctx.state().get("hover") > 60 ? StepResult.fail("Keine Hover-Vorschau am Sharecode") : StepResult.pending();
        }
        if (!ctx.state().containsKey("shot") && Screenshots.enabled()) {
            ctx.state().put("shot", Screenshots.capture("leaderboard_share_code"));
            return StepResult.pending();
        }
        if (ctx.state().get("shot") instanceof CompletableFuture<?> shot && !shot.isDone()) {
            return StepResult.pending();
        }
        Style style = Style.EMPTY.withClickEvent(new ClickEvent.Custom(ShareChat.COPY, Optional.of(StringTag.valueOf(rival.code()))));
        if (ShareChat.tooltip(style, mc.font, mc.getWindow().getGuiScaledWidth()) == null) {
            return StepResult.fail("Sharecode ohne gültige Vorschau");
        }
        mc.keyboardHandler.setClipboard("");
        screen.copyButton().press();
        String clip = mc.keyboardHandler.getClipboard();
        Screenshots.parkMouse(mc);
        if (!rival.code().equals(clip)) {
            return StepResult.fail("Kopierter Code falsch: " + clip.length() + " Zeichen");
        }
        return StepResult.pass();
    }

    private static StepResult replayStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ReplaySession session = ReplaySession.INSTANCE;
        if (ctx.tick() == 0) {
            LeaderboardScreen screen = LeaderboardScreen.active();
            RacePayloads.BoardRow rival = row(RIVAL);
            if (screen == null || rival == null) {
                return StepResult.fail("Bestenliste oder Zeile fehlt");
            }
            ctx.state().put("hud", mc.gui.hud.isHidden());
            screen.openReplay(rival);
            return StepResult.pending();
        }
        ReplayScreen replay = ReplayScreen.active();
        String stage = (String) ctx.state().getOrDefault("stage", "load");
        switch (stage) {
            case "load" -> {
                if (replay == null || !replay.loaded()) {
                    if (replay != null && replay.failure() != null) {
                        return StepResult.fail("Replay nicht geladen: " + replay.failure());
                    }
                    return ctx.tick() > 200 ? StepResult.fail("Replay lädt nicht") : StepResult.pending();
                }
                if (session.view() != ReplaySession.View.FPV || !session.playing() || !mc.gui.hud.isHidden()) {
                    return StepResult.fail("Replay startet nicht in FPV mit ausgeblendetem HUD");
                }
                if (Math.abs(session.uptilt() - 30.0F) > 0.01F) {
                    return StepResult.fail("Kamerawinkel des Geists nicht übernommen: " + session.uptilt());
                }
                if (session.marks().length < 2) {
                    return StepResult.fail("Gate-Marken fehlen: " + session.marks().length);
                }
                session.setPlaying(false);
                session.seek(session.end() / 2);
                ctx.state().put("stage", "fpv");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "fpv" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 5) {
                    return StepResult.pending();
                }
                String pose = checkPose(mc, session, false);
                if (pose != null) {
                    return StepResult.fail(pose);
                }
                if (!ctx.state().containsKey("shot") && Screenshots.enabled()) {
                    ctx.state().put("shot", Screenshots.capture("race_replay_fpv"));
                    return StepResult.pending();
                }
                if (ctx.state().get("shot") instanceof CompletableFuture<?> shot && !shot.isDone()) {
                    return StepResult.pending();
                }
                long before = session.time();
                session.setPlaying(true);
                session.setSpeedIndex(ReplaySession.SPEEDS.length - 1);
                ctx.state().put("before", before);
                ctx.state().put("stage", "play");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "play" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 10) {
                    return StepResult.pending();
                }
                if (session.time() <= (long) ctx.state().get("before")) {
                    return StepResult.fail("Replay läuft nicht weiter");
                }
                session.setPlaying(false);
                session.setSpeedIndex(ReplaySession.NORMAL_SPEED);
                long here = session.time();
                if (replay != null) {
                    replay.skip(-ReplaySession.SKIP_NANOS);
                }
                if (session.time() != Math.max(session.begin(), here - ReplaySession.SKIP_NANOS)) {
                    return StepResult.fail("Spulen zurück falsch: " + session.time());
                }
                session.seek(session.end() / 2);
                ctx.state().put("drawn", GhostRenderer.replayDrawn());
                if (replay != null) {
                    replay.toggleView();
                }
                ctx.state().put("stage", "chase");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "chase" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 5) {
                    return StepResult.pending();
                }
                if (session.view() != ReplaySession.View.CHASE) {
                    return StepResult.fail("Ansicht nicht umgeschaltet");
                }
                String pose = checkPose(mc, session, true);
                if (pose != null) {
                    return StepResult.fail(pose);
                }
                if (GhostRenderer.replayDrawn() <= (int) ctx.state().get("drawn")) {
                    return StepResult.fail("Geist hinter der Drohne nicht gezeichnet");
                }
                if (!ctx.state().containsKey("shot2") && Screenshots.enabled()) {
                    ctx.state().put("shot2", Screenshots.capture("race_replay_chase"));
                    return StepResult.pending();
                }
                if (ctx.state().get("shot2") instanceof CompletableFuture<?> shot && !shot.isDone()) {
                    return StepResult.pending();
                }
                if (replay != null) {
                    replay.onClose();
                }
                ctx.state().put("stage", "back");
                return StepResult.pending();
            }
            default -> {
                if (!(mc.gui.screen() instanceof LeaderboardScreen)) {
                    return ctx.tick() > 500 ? StepResult.fail("Verlassen führt nicht zur Bestenliste") : StepResult.pending();
                }
                if (session.active()) {
                    return StepResult.fail("Replay läuft nach dem Verlassen weiter");
                }
                if (mc.gui.hud.isHidden() != (boolean) ctx.state().get("hud")) {
                    return StepResult.fail("HUD nach dem Replay nicht zurückgesetzt");
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult replayLeadStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ReplaySession session = ReplaySession.INSTANCE;
        if (ctx.tick() == 0) {
            LeaderboardScreen screen = LeaderboardScreen.active();
            RacePayloads.BoardRow rival = row(RIVAL);
            if (screen == null || rival == null) {
                return StepResult.fail("Bestenliste oder Zeile fehlt");
            }
            screen.openReplay(rival);
            return StepResult.pending();
        }
        ReplayScreen replay = ReplayScreen.active();
        String zero = RaceFormat.time(0L);
        String stage = (String) ctx.state().getOrDefault("stage", "load");
        switch (stage) {
            case "load" -> {
                if (replay == null || !replay.loaded()) {
                    if (replay != null && replay.failure() != null) {
                        return StepResult.fail("Replay nicht geladen: " + replay.failure());
                    }
                    return ctx.tick() > 200 ? StepResult.fail("Replay lädt nicht") : StepResult.pending();
                }
                GhostTrack track = session.track();
                if (track == null || track.leadNanos() != GhostRecorder.LEAD_NANOS || session.begin() != -GhostRecorder.LEAD_NANOS) {
                    return StepResult.fail("Replay ohne Vorlauf: " + (track == null ? "keine Spur" : track.leadNanos() / MS + " ms"));
                }
                if (session.time() >= 0L) {
                    return StepResult.fail("Replay beginnt nicht vor dem Startgate: " + session.time() / MS + " ms");
                }
                session.setPlaying(false);
                session.seek(Long.MIN_VALUE);
                if (session.time() != session.begin() || session.lapTime() != 0L || !zero.equals(replay.clock())) {
                    return StepResult.fail("Anfang des Vorlaufs falsch: " + session.time() / MS + " ms, Uhr " + replay.clock());
                }
                ctx.state().put("stage", "shot");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "shot" -> {
                if (ctx.tick() - (int) ctx.state().get("at") < 5) {
                    return StepResult.pending();
                }
                String pose = checkPose(mc, session, false);
                if (pose != null) {
                    return StepResult.fail(pose);
                }
                String before = beforeStart(session);
                if (before != null) {
                    return StepResult.fail(before);
                }
                if (replay == null || replay.timelineX(session.begin()) >= replay.timelineX(0L)) {
                    return StepResult.fail("Zeitleiste ohne Vorlauf");
                }
                if (!ctx.state().containsKey("shot") && Screenshots.enabled()) {
                    ctx.state().put("shot", Screenshots.capture("race_replay_lead"));
                    return StepResult.pending();
                }
                if (ctx.state().get("shot") instanceof CompletableFuture<?> shot && !shot.isDone()) {
                    return StepResult.pending();
                }
                session.seek(-300L * MS);
                if (session.lapTime() != 0L || !zero.equals(replay.clock())) {
                    return StepResult.fail("Uhr im Vorlauf nicht 0: " + replay.clock());
                }
                replay.skip(-ReplaySession.SKIP_NANOS);
                if (session.time() != session.begin()) {
                    return StepResult.fail("Spulen zurück endet nicht am Anfang des Vorlaufs: " + session.time() / MS + " ms");
                }
                session.seek(300L * MS);
                if (session.lapTime() != 300L * MS || !RaceFormat.time(300L * MS).equals(replay.clock())) {
                    return StepResult.fail("Uhr nach dem Startgate falsch: " + replay.clock());
                }
                session.seek(session.begin());
                session.setSpeedIndex(ReplaySession.NORMAL_SPEED);
                session.setPlaying(true);
                ctx.state().put("stage", "play");
                ctx.state().put("at", ctx.tick());
                return StepResult.pending();
            }
            case "play" -> {
                if (replay == null) {
                    return StepResult.fail("Replay geschlossen");
                }
                long time = session.time();
                if (time < 0L) {
                    if (!zero.equals(replay.clock())) {
                        return StepResult.fail("Uhr läuft vor dem Startgate: " + replay.clock() + " bei " + time / MS + " ms");
                    }
                    ctx.state().put("lead", true);
                    return ctx.tick() - (int) ctx.state().get("at") > 200 ? StepResult.fail("Wiedergabe erreicht das Startgate nicht")
                            : StepResult.pending();
                }
                if (!ctx.state().containsKey("lead")) {
                    return StepResult.fail("Wiedergabe übersprang den Vorlauf");
                }
                if (time < 200L * MS) {
                    return StepResult.pending();
                }
                if (session.lapTime() <= 0L || session.lapTime() != Math.min(time, track(session).lapNanos())) {
                    return StepResult.fail("Rundenzeit nach dem Startgate falsch: " + session.lapTime() / MS + " ms bei " + time / MS + " ms");
                }
                ctx.log("Vorlauf " + session.begin() / MS + " ms, Uhr bis zum Startgate " + zero);
                replay.onClose();
                ctx.state().put("stage", "back");
                return StepResult.pending();
            }
            default -> {
                if (!(mc.gui.screen() instanceof LeaderboardScreen)) {
                    return ctx.tick() > 450 ? StepResult.fail("Verlassen führt nicht zur Bestenliste") : StepResult.pending();
                }
                return session.active() ? StepResult.fail("Replay läuft nach dem Verlassen weiter") : StepResult.pass();
            }
        }
    }

    static @Nullable String gateCount(ReplayScreen replay, ReplaySession session, CourseData course, long lapNanos, boolean pointToPoint) {
        int expected = course.gates().size();
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course.id());
        if (board == null || board.gates() != expected) {
            return "Bestenliste zählt " + (board == null ? "nichts" : board.gates()) + " statt " + expected + " Gates";
        }
        if (session.gates() != expected) {
            return "Replay zählt " + session.gates() + " statt " + expected + " Gates";
        }
        Component label = replay.gatesLabel();
        String wanted = Component.translatable("propwash.race.replay.marks", expected).getString();
        if (label == null || !label.getString().equals(wanted)) {
            return "Leiste zeigt „" + (label == null ? "nichts" : label.getString()) + "“ statt „" + wanted + "“";
        }
        long[] marks = session.marks();
        if (marks.length != expected || marks[0] != 0L) {
            return "Zeitleiste mit " + marks.length + " Marken statt " + expected + ", erste bei " + (marks.length == 0 ? "-" : marks[0] / MS + " ms");
        }
        if (pointToPoint && marks[marks.length - 1] != lapNanos) {
            return "Punkt-zu-Punkt: letzte Marke " + marks[marks.length - 1] / MS + " ms statt am Ziel " + lapNanos / MS + " ms";
        }
        for (int i = 1; i < marks.length; i++) {
            if (marks[i] < marks[i - 1] || marks[i] > lapNanos) {
                return "Marke " + i + " bei " + marks[i] / MS + " ms liegt falsch";
            }
        }
        return null;
    }

    private static StepResult replayGatesStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        ReplaySession session = ReplaySession.INSTANCE;
        if (ctx.tick() == 0) {
            LeaderboardScreen screen = LeaderboardScreen.active();
            RacePayloads.BoardRow rival = row(RIVAL);
            if (screen == null || rival == null) {
                return StepResult.fail("Bestenliste oder Zeile fehlt");
            }
            screen.openReplay(rival);
            return StepResult.pending();
        }
        ReplayScreen replay = ReplayScreen.active();
        if (!ctx.state().containsKey("checked")) {
            if (replay == null || !replay.loaded() || replay.frames() < 3) {
                if (replay != null && replay.failure() != null) {
                    return StepResult.fail("Replay nicht geladen: " + replay.failure());
                }
                return ctx.tick() > 200 ? StepResult.fail("Replay lädt nicht") : StepResult.pending();
            }
            CourseData course = ClientCourses.get(RaceSelfTests.courseId());
            if (course == null) {
                return StepResult.fail("Kurs fehlt beim Client");
            }
            String problem = gateCount(replay, session, course, track(session).lapNanos(), false);
            if (problem != null) {
                return StepResult.fail(problem);
            }
            ctx.log("Runden-Kurs: " + course.gates().size() + " Gates in Bestenliste, Kurs und Replay, Marken bei " + marksText(session.marks()));
            session.setPlaying(false);
            session.seek(session.end() / 2);
            ctx.state().put("checked", true);
            ctx.state().put("at", ctx.tick());
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("closed")) {
            if (ctx.tick() - (int) ctx.state().get("at") < 5) {
                return StepResult.pending();
            }
            if (!ctx.state().containsKey("shot") && Screenshots.enabled()) {
                ctx.state().put("shot", Screenshots.capture("race_replay_gate_count"));
                return StepResult.pending();
            }
            if (ctx.state().get("shot") instanceof CompletableFuture<?> shot && !shot.isDone()) {
                return StepResult.pending();
            }
            if (replay != null) {
                replay.onClose();
            }
            ctx.state().put("closed", true);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof LeaderboardScreen)) {
            return ctx.tick() > 280 ? StepResult.fail("Verlassen führt nicht zur Bestenliste") : StepResult.pending();
        }
        return session.active() ? StepResult.fail("Replay läuft nach dem Verlassen weiter") : StepResult.pass();
    }

    static String marksText(long[] marks) {
        StringBuilder text = new StringBuilder();
        for (long mark : marks) {
            text.append(text.isEmpty() ? "" : ", ").append(mark / MS).append(" ms");
        }
        return text.toString();
    }

    private static GhostTrack track(ReplaySession session) {
        GhostTrack track = session.track();
        if (track == null) {
            throw new IllegalStateException("Replay ohne Spur");
        }
        return track;
    }

    private static @Nullable String beforeStart(ReplaySession session) {
        CourseData course = ClientCourses.get(RaceSelfTests.courseId());
        if (course == null) {
            return "Kurs fehlt beim Client";
        }
        GateGeometry start = ClientCourses.geometry(course).getFirst();
        Vector3d camera = new Vector3d();
        Quaternionf rotation = new Quaternionf();
        if (!session.cameraPose(camera, rotation)) {
            return "keine Kamerapose am Anfang";
        }
        double along = (camera.x - start.x()) * start.normalX() + (camera.z - start.z()) * start.normalZ();
        if (along > -LEAD_DISTANCE / 2.0) {
            return "Kamera nicht vor dem Startgate: " + along + " Blöcke";
        }
        Vector3f forward = rotation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        double facing = forward.x * start.normalX() + forward.z * start.normalZ();
        if (facing < 0.7) {
            return "Kamera schaut nicht zum Startgate: " + facing;
        }
        return null;
    }

    private static @Nullable String checkPose(Minecraft mc, ReplaySession session, boolean chase) {
        Vector3d expected = new Vector3d();
        Quaternionf rotation = new Quaternionf();
        if (!session.cameraPose(expected, rotation)) {
            return "keine Kamerapose";
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        double off = camera.distanceTo(new Vec3(expected.x, expected.y, expected.z));
        if (off > 0.3) {
            return "Kamera nicht am Geist: Abstand " + off;
        }
        double[] feet = new double[3];
        if (!session.ghostPose(feet, new Quaternionf())) {
            return "Geist ohne Pose";
        }
        double toDrone = camera.distanceTo(new Vec3(feet[0], feet[1], feet[2]));
        if (chase ? toDrone < 1.0 || toDrone > 4.0 : toDrone > 0.8) {
            return (chase ? "Hinter der Drohne" : "FPV") + ": Abstand zur Drohne " + toDrone;
        }
        if (!chase) {
            Quaternionf body = new Quaternionf();
            session.ghostPose(feet, body);
            Quaternionf wanted = new Quaternionf(body).rotateX((float) Math.toRadians(session.uptilt()));
            Quaternionf actual = mc.gameRenderer.mainCamera().rotation();
            if (Math.abs(Math.abs(wanted.dot(actual)) - 1.0F) > 1.0E-3F) {
                return "FPV-Blickrichtung nicht wie die Drohnenkamera";
            }
        }
        return null;
    }

    private static StepResult opponentStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int course = RaceSelfTests.courseId();
        GhostLibrary ghosts = ClientRaceTracker.INSTANCE.ghosts();
        CourseData data = ClientCourses.get(course);
        if (data == null) {
            return StepResult.fail("Kurs fehlt beim Client");
        }
        if (ctx.tick() == 0) {
            LeaderboardScreen screen = LeaderboardScreen.active();
            RacePayloads.BoardRow rival = row(RIVAL);
            if (screen == null || rival == null) {
                return StepResult.fail("Bestenliste oder Zeile fehlt");
            }
            GhostDownloads.INSTANCE.clear();
            screen.toggleOpponent(rival);
            if (!GhostChoices.INSTANCE.chosen(mc, course, rival.key())) {
                return StepResult.fail("Wahl nicht gemerkt");
            }
            ctx.state().put("completed", GhostDownloads.INSTANCE.completed());
            return StepResult.pending();
        }
        ghosts.ensure(mc, data, FRAME);
        if (ghosts.boardStatus() != GhostDownloads.Status.LOADED) {
            return ctx.tick() > 300 ? StepResult.fail("Geist des Rivalen nicht geladen: " + ghosts.boardStatus()) : StepResult.pending();
        }
        if (GhostDownloads.INSTANCE.completed() <= (int) ctx.state().get("completed")) {
            return StepResult.fail("Geist kam nicht über das Netz");
        }
        EntryGhost.Info info = ghosts.boardInfo();
        if (info == null || !info.player().equals(RIVAL) || ghosts.shownKind() != GhostLibrary.Shown.BOARD || ghosts.mode() != GhostChoice.Mode.BOARD) {
            return StepResult.fail("Gewählter Geist fliegt nicht mit: " + ghosts.shownKind() + " " + info);
        }
        long[] reference = ghosts.referenceSplits();
        GhostTrack board = ghosts.board();
        if (board == null || reference.length == 0 || reference[0] != board.lapNanos()) {
            return StepResult.fail("Split-Referenz folgt nicht dem gewählten Geist");
        }
        LeaderboardScreen screen = LeaderboardScreen.active();
        RacePayloads.BoardRow rival = row(RIVAL);
        if (screen == null || rival == null) {
            return StepResult.fail("Bestenliste geschlossen");
        }
        screen.toggleOpponent(rival);
        ghosts.ensure(mc, data, FRAME);
        if (GhostChoices.INSTANCE.chosen(mc, course, rival.key()) || ghosts.mode() != GhostChoice.Mode.OWN || ghosts.shownKind() == GhostLibrary.Shown.BOARD) {
            return StepResult.fail("Zurück zum eigenen Geist klappt nicht: " + ghosts.mode());
        }
        return StepResult.pass();
    }

    private static StepResult ruleStep(SelfTest.StepContext ctx) {
        int course = RaceSelfTests.courseId();
        String stage = (String) ctx.state().getOrDefault("stage", "off");
        if (ctx.tick() == 0) {
            server(ctx, server -> {
                WorldRules.set(server, WorldRuleSpecs.FOREIGN_GHOSTS, GhostAccess.OFF);
                RaceServer.sendLeaderboard(server.getPlayerList().getPlayers().getFirst(), course, false);
                return null;
            });
            ctx.state().put("version", ClientLeaderboards.version());
            return StepResult.pending();
        }
        StepResult waiting = waitServer(ctx);
        if (waiting != null) {
            return waiting;
        }
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course);
        RacePayloads.BoardRow rival = row(RIVAL);
        if (board == null || rival == null || ClientLeaderboards.version() == (int) ctx.state().get("version")) {
            return ctx.tick() > 300 ? StepResult.fail("Bestenliste nach Regelwechsel nicht angekommen") : StepResult.pending();
        }
        switch (stage) {
            case "off" -> {
                if (board.mayRaceOthers() || !board.mayReplayOthers() || !board.mayRemove()) {
                    return StepResult.fail("Regel Aus: Admin-Rechte falsch: " + board.access());
                }
                GhostDownloads.INSTANCE.clear();
                AtomicReference<GhostDownloads.Result> opponent = new AtomicReference<>();
                AtomicReference<GhostDownloads.Result> replay = new AtomicReference<>();
                GhostDownloads.INSTANCE.fetch(course, rival.key(), GhostAccess.Purpose.OPPONENT, opponent::set);
                GhostDownloads.INSTANCE.fetch(course, rival.key(), GhostAccess.Purpose.REPLAY, replay::set);
                ctx.state().put("opponent", opponent);
                ctx.state().put("replay", replay);
                ctx.state().put("stage", "off_wait");
                return StepResult.pending();
            }
            case "off_wait" -> {
                @SuppressWarnings("unchecked")
                GhostDownloads.Result opponent = ((AtomicReference<GhostDownloads.Result>) ctx.state().get("opponent")).get();
                @SuppressWarnings("unchecked")
                GhostDownloads.Result replay = ((AtomicReference<GhostDownloads.Result>) ctx.state().get("replay")).get();
                if (opponent == null || replay == null) {
                    return StepResult.pending();
                }
                if (opponent.status() != GhostDownloads.Status.DENIED) {
                    return StepResult.fail("Regel Aus: fremder Geist als Gegner nicht verweigert: " + opponent.status());
                }
                if (!replay.loaded()) {
                    return StepResult.fail("Regel Aus: Admin darf das Replay nicht öffnen: " + replay.status());
                }
                ctx.state().put("stage", "admins");
                ctx.state().put("version", ClientLeaderboards.version());
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.FOREIGN_GHOSTS, GhostAccess.ADMINS);
                    RaceServer.sendLeaderboard(server.getPlayerList().getPlayers().getFirst(), course, false);
                    return null;
                });
                return StepResult.pending();
            }
            default -> {
                if (!board.mayRaceOthers() || !board.mayReplayOthers()) {
                    return StepResult.fail("Regel Admins: Rechte des Admins falsch: " + board.access());
                }
                server(ctx, server -> {
                    WorldRules.set(server, WorldRuleSpecs.FOREIGN_GHOSTS, WorldRuleSpecs.FOREIGN_GHOSTS.defaultValue());
                    return null;
                });
                ctx.state().put("stage", "done");
                if (WorldRuleSpecs.FOREIGN_GHOSTS.defaultValue() != GhostAccess.ALL) {
                    return StepResult.fail("Standard der Regel ist nicht Alle");
                }
                return StepResult.pass();
            }
        }
    }

    private static @Nullable String checkRemoved(MinecraftServer server, boolean command) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(RaceSelfTests.courseId());
        if (course == null) {
            return "Kurs fehlt";
        }
        for (Leaderboard.Entry entry : registry.board(course).storedTimes()) {
            if (entry.player().equals(RIVAL)) {
                return "Zeit des Rivalen noch in der Liste" + (command ? " (Befehl)" : "");
            }
        }
        Path folder = registry.entryGhosts().folder(course.key());
        try (Stream<Path> files = folder == null || !Files.isDirectory(folder) ? Stream.<Path>empty() : Files.list(folder)) {
            if (files.anyMatch(file -> file.getFileName().toString().startsWith(RIVAL.toString()))) {
                return "Geist des Rivalen liegt noch im Weltordner";
            }
        } catch (IOException e) {
            return "Ordner nicht lesbar: " + e;
        }
        Path log = registry.entryGhosts().logFile();
        try {
            List<String> lines = log == null || !Files.isRegularFile(log) ? List.of() : Files.readAllLines(log);
            long matching = lines.stream().filter(line -> line.contains("pilot=" + RIVAL_NAME + "(" + RIVAL + ")")).count();
            if (matching < (command ? 2 : 1)) {
                return "Protokollzeile fehlt: " + lines;
            }
            if (command && lines.stream().noneMatch(line -> line.contains("via=command"))) {
                return "Befehl nicht protokolliert";
            }
        } catch (IOException e) {
            return "Protokoll nicht lesbar: " + e;
        }
        return null;
    }

    private static StepResult removeStep(SelfTest.StepContext ctx) {
        int course = RaceSelfTests.courseId();
        String stage = (String) ctx.state().getOrDefault("stage", "start");
        StepResult waiting = waitServer(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (stage) {
            case "start" -> {
                LeaderboardScreen screen = LeaderboardScreen.active();
                RacePayloads.BoardRow rival = row(RIVAL);
                if (screen == null || rival == null) {
                    return ctx.tick() > 100 ? StepResult.fail("Bestenliste oder Zeile fehlt") : StepResult.pending();
                }
                screen.select(rival);
                screen.removeTime(rival);
                if (!screen.removeArmed()) {
                    return StepResult.fail("Rückfrage vor dem Löschen fehlt");
                }
                screen.removeTime(rival);
                ctx.state().put("stage", "gone");
                return StepResult.pending();
            }
            case "gone" -> {
                if (row(RIVAL) != null) {
                    return ctx.tick() > 200 ? StepResult.fail("Zeile des Rivalen bleibt in der Liste") : StepResult.pending();
                }
                ctx.state().put("stage", "command");
                server(ctx, server -> {
                    String removed = checkRemoved(server, false);
                    if (removed != null) {
                        return removed;
                    }
                    String again = addRival(server);
                    if (again != null) {
                        return again;
                    }
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "propwash race remove " + course + " " + RIVAL_NAME + " "
                            + FRAME);
                    return checkRemoved(server, true);
                });
                return StepResult.pending();
            }
            default -> {
                Minecraft.getInstance().gui.setScreen(null);
                return StepResult.pass();
            }
        }
    }

    private static @Nullable String flyServerLead(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(RaceSelfTests.courseId());
        if (course == null) {
            return "Kurs fehlt auf dem Server";
        }
        if (!registry.entryGhosts().enabled()) {
            return "Ablage der Geister im Weltordner fehlt";
        }
        ServerPlayer pilot = RaceDataJoinCheck.secondPilot(server, player.level(), LEAD_PILOT);
        List<Leaderboard.Entry> best = registry.board(course).top(null, 1);
        long lap = Math.max(6_000L * MS, best.isEmpty() ? 0L : best.getFirst().nanos() + 1_000L * MS);
        try {
            String flown = RaceGhostsJoinCheck.flyWithRunUp(course, pilot, lap);
            if (flown != null) {
                return "Server-Flug: " + flown;
            }
            String lead = RaceGhostsJoinCheck.leadProblem(registry, course, pilot.getUUID());
            if (lead != null) {
                return "Geist aus dem Server-Flug: " + lead;
            }
            LEAD_LOG.set("Server-Flug von " + LEAD_PILOT + " mit " + RaceGhostsJoinCheck.RUN_UP_NANOS / MS + " ms Anflug, Startmeldung "
                    + RaceGhostsJoinCheck.START_BEFORE_GATE_NANOS / MS + " ms vor dem Gate: Geist der Bestenliste mit " + GhostRecorder.LEAD_NANOS / MS
                    + " ms Vorlauf auf dem Anflug");
            return null;
        } finally {
            registry.removeTimes(course, pilot.getUUID(), null, Leaderboard.Section.ALL);
        }
    }

    private static StepResult serverLeadStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            LEAD_LOG.set("");
            server(ctx, RaceGhostsSelfTests::flyServerLead);
            return StepResult.pending();
        }
        StepResult waiting = waitServer(ctx);
        if (waiting != null) {
            return waiting;
        }
        ctx.log(LEAD_LOG.get());
        return StepResult.pass();
    }
}

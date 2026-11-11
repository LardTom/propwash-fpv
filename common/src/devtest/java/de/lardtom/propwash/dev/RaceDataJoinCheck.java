package de.lardtom.propwash.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.dev.example.ExampleRaceListener;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceDataMirror;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RaceDataJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int AWAY = 24;
    public static final int LIFT = 20;
    public static final String SECOND = "PwDataB";
    public static final String RESULT = "[race-data] ";
    public static final int BOARD_RECORDS = 120;
    public static final int BOARD_ASSISTED = 60;
    public static final double[][] PATH = {{0.0, 1.5, -3.5}, {0.0, 1.5, 15.5}, {6.0, 1.5, 15.5}, {6.0, 1.5, -6.5}, {0.0, 1.5, -6.5},
            {0.0, 1.5, 1.5}};
    private static final long MS = 1_000_000L;
    private static int joined = -1;
    private static boolean done;

    private RaceDataJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        if (done) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            joined = -1;
            return;
        }
        if (joined < 0) {
            joined = tick;
        }
        if (tick - joined < SETTLE_TICKS) {
            return;
        }
        done = true;
        ServerPlayer player = players.getFirst();
        String line;
        try {
            line = run(server, player);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] Renndaten: Ablauf fehlgeschlagen", e);
            line = "fail " + e;
        }
        Constants.LOG.info("[Beitrittstest] Renndaten: {}", line);
        player.sendSystemMessage(Component.literal(RESULT + line).withStyle(ChatFormatting.GRAY));
    }

    public static GateEntity gate(ServerLevel level, Vec3 at) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        gate.setSize(GateSize.MEDIUM);
        gate.applyGeometry(GateGeometry.of(at.x, at.y, at.z, 0.0F, GateSize.MEDIUM));
        if (!level.addFreshEntity(gate)) {
            throw new IllegalStateException("Gate nicht gespawnt");
        }
        return gate;
    }

    public static Vec3 origin(ServerPlayer player) {
        ServerLevel level = player.level();
        int x = player.getBlockX() + AWAY;
        int z = player.getBlockZ();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + LIFT;
        return new Vec3(x + 0.5, ground, z + 0.5);
    }

    public static CourseRegistry.@Nullable Course build(ServerPlayer player, Vec3 origin, List<GateEntity> spawned) {
        ServerLevel level = player.level();
        GateEntity a = gate(level, origin);
        GateEntity b = gate(level, origin.add(0.0, 0.0, 12.0));
        spawned.add(a);
        spawned.add(b);
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setYHeadRot(0.0F);
        CourseToolItem.useOnGate(tool, player, a);
        CourseToolItem.useOnGate(tool, player, b);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(CourseToolItem.courseOf(tool));
        if (course != null) {
            registry.setLaps(course, 1);
        }
        return course;
    }

    public static ServerPlayer secondPilot(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("propwash-data-" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    public static @Nullable String fly(CourseRegistry.Course course, ServerPlayer pilot, Vec3 origin, long minimumLapNanos) {
        ServerLevel level = pilot.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        List<Vec3> path = new ArrayList<>(PATH.length);
        for (double[] point : PATH) {
            path.add(origin.add(point[0], point[1], point[2]));
        }
        drone.snapTo(path.getFirst().x, path.getFirst().y, path.getFirst().z, 0.0F, 0.0F);
        drone.setPilot(pilot);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(course, pilot) * 3L, minimumLapNanos);
            GateGeometry start = course.toData().geometry().getFirst();
            double travelled = 0.0;
            double first = -1.0;
            double last = -1.0;
            for (int i = 1; i < path.size(); i++) {
                Vec3 a = path.get(i - 1);
                Vec3 b = path.get(i);
                double t = start.crossing(a.x, a.y, a.z, b.x, b.y, b.z, true);
                if (t >= 0.0) {
                    first = first < 0.0 ? travelled + a.distanceTo(b) * t : first;
                    last = travelled + a.distanceTo(b) * t;
                }
                travelled += a.distanceTo(b);
            }
            if (first < 0.0 || last <= first) {
                return "Weg kreuzt das Start-Gate nicht zweimal";
            }
            double perBlock = lap / (last - first);
            long t0 = System.nanoTime() + 60_000L * MS;
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L), pilot,
                    t0 - 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            travelled = 0.0;
            long t = t0;
            for (int i = 1; i < path.size(); i++) {
                Vec3 a = path.get(i - 1);
                Vec3 b = path.get(i);
                double length = a.distanceTo(b);
                int steps = Math.max(1, (int) Math.ceil(length / 0.5));
                for (int s = 1; s <= steps; s++) {
                    t = t0 + Math.round((travelled + length * s / steps - first) * perBlock);
                    RaceServer.onDroneMoved(pilot, a.lerp(b, (s - 1) / (double) steps), a.lerp(b, s / (double) steps), t);
                }
                travelled += length;
            }
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course.id, 1, lap, 0L), pilot, t + 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde abgelehnt: " + verdict;
            }
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.FINISH, course.id, 1, lap, lap), pilot, t + 200L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Ziel abgelehnt: " + verdict;
            }
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    public static String verify(MinecraftServer server, CourseRegistry.Course course, List<String> names, int lapsBefore, int racesBefore,
                                boolean listener) throws IOException {
        UUID key = course.key();
        List<String> problems = new ArrayList<>();
        CourseInfo info = PropwashApi.race().course(server, key).orElse(null);
        if (info == null || info.number() != course.id) {
            problems.add("API kennt den Kurs nicht");
        }
        BoardSnapshot board = PropwashApi.race().leaderboard(server, key, BoardQuery.laps()).orElse(null);
        List<String> boardNames = board == null ? List.of() : board.entries().stream().map(BoardEntry::name).toList();
        if (!boardNames.containsAll(names)) {
            problems.add("API-Bestenliste " + boardNames);
        }
        int laps = 0;
        int races = 0;
        int records = 0;
        int bests = 0;
        if (listener) {
            laps = (int) ExampleRaceListener.LAPS.stream().filter(lap -> lap.course().equals(key)).count() - lapsBefore;
            races = (int) ExampleRaceListener.RACES.stream().filter(race -> race.course().equals(key)).count() - racesBefore;
            records = (int) ExampleRaceListener.RECORDS.stream().filter(record -> record.course().equals(key)).count();
            bests = (int) ExampleRaceListener.BESTS.stream().filter(best -> best.course().equals(key)).count();
            if (laps < names.size() || races < names.size() || records < 1 || bests < names.size()) {
                problems.add("Ereignisse laps=" + laps + " races=" + races + " records=" + records + " bests=" + bests);
            }
        }
        RaceDataMirror mirror = RaceDataMirror.get();
        int found = 0;
        long time = -1L;
        if (mirror == null) {
            problems.add("kein JSON-Spiegel");
        } else {
            mirror.flushNow();
            Path file = mirror.root().resolve(RaceDataJson.LEADERBOARDS).resolve(key + ".json");
            Path courses = mirror.root().resolve(RaceDataJson.COURSES);
            if (!Files.isRegularFile(file) || !Files.isRegularFile(courses)) {
                problems.add("Datei fehlt: " + file);
            } else {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray clean = root.getAsJsonArray("layouts").get(0).getAsJsonObject().getAsJsonObject("laps").getAsJsonArray("clean");
                for (JsonElement element : clean) {
                    if (names.contains(element.getAsJsonObject().get("name").getAsString())) {
                        found++;
                    }
                }
                time = clean.isEmpty() ? -1L : clean.get(0).getAsJsonObject().get("timeMs").getAsLong();
                if (!key.toString().equals(root.get("course").getAsString()) || found < names.size()) {
                    problems.add("JSON " + file.getFileName() + " mit " + found + " Piloten");
                }
                if (board != null && !board.entries().isEmpty() && board.entries().getFirst().timeMillis() != time) {
                    problems.add("JSON-Zeit " + time + " statt " + board.entries().getFirst().timeMillis());
                }
                if (!Files.readString(courses, StandardCharsets.UTF_8).contains(key.toString())) {
                    problems.add("courses.json ohne Kurs");
                }
            }
        }
        String summary = "key=" + key + " number=" + course.id + " api=" + boardNames.size() + " events=lap:" + laps + ",race:" + races + ",best:" + bests
                + ",record:" + records + " json=" + found + " timeMs=" + time;
        return (problems.isEmpty() ? "ok " : "fail " + String.join("; ", problems) + " ") + summary;
    }

    private static String run(MinecraftServer server, ServerPlayer player) throws RuntimeException {
        Vec3 origin = origin(player);
        CourseRegistry.Course course = build(player, origin, new ArrayList<>());
        if (course == null) {
            return "fail Kurs nicht angelegt";
        }
        Constants.LOG.info("[Beitrittstest] Renndaten: Kurs {} mit ID {} bei {}", course.id, course.key(), origin);
        boolean listener = JoinTestEnv.exampleAddon();
        int lapsBefore = (int) ExampleRaceListener.LAPS.stream().filter(lap -> lap.course().equals(course.key())).count();
        int racesBefore = (int) ExampleRaceListener.RACES.stream().filter(race -> race.course().equals(course.key())).count();
        ServerPlayer second = secondPilot(server, player.level(), SECOND);
        String problem = fly(course, second, origin, 9_000L * MS);
        if (problem != null) {
            return "fail " + SECOND + ": " + problem;
        }
        problem = fly(course, player, origin, 8_000L * MS);
        if (problem != null) {
            return "fail " + player.getGameProfile().name() + ": " + problem;
        }
        String verdict;
        try {
            verdict = verify(server, course, List.of(SECOND, player.getGameProfile().name()), lapsBefore, racesBefore, listener);
        } catch (IOException e) {
            return "fail JSON nicht lesbar: " + e;
        }
        return verdict.startsWith("ok ") ? verdict + " " + seedBoard(server, course, player) : verdict;
    }

    public static long sum(List<Leaderboard.Entry> entries) {
        long sum = 0L;
        for (Leaderboard.Entry entry : entries) {
            sum += entry.nanos() / MS;
        }
        return sum;
    }

    public static String seedBoard(MinecraftServer server, CourseRegistry.Course course, ServerPlayer player) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "board=fail";
        }
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        Leaderboard board = registry.board(course);
        long now = System.currentTimeMillis();
        int pilot = 0;
        while (board.top(null, Integer.MAX_VALUE).size() < BOARD_RECORDS) {
            board.submit(new UUID(0x1BA11L, pilot), String.format("Pilot_%03d", pilot), pilot % 3 == 0 ? "whoop_65" : "race_5",
                    (9_500L + pilot * 37L) * MS, now - pilot * 60_000L);
            pilot++;
        }
        for (int i = 0; i < BOARD_ASSISTED - 1; i++) {
            board.submitAssisted(new UUID(0x1BA12L, i), String.format("Hilfe_%02d", i), i % 2 == 0 ? "race_5" : "whoop_65", (7_000L + i * 41L) * MS,
                    now - i * 60_000L, LapAssist.HOLD_MODE.bit());
        }
        board.submitAssisted(player.getUUID(), player.getGameProfile().name(), "race_5", (7_000L + 20L * 41L + 13L) * MS, now, LapAssist.HOLD_MODE.bit());
        registry.records().changed();
        List<Leaderboard.Entry> clean = board.top(null, Integer.MAX_VALUE);
        List<Leaderboard.Entry> assisted = board.assistedTop(null, Integer.MAX_VALUE);
        Constants.LOG.info("[Beitrittstest] Renndaten: Bestenliste mit {} Zeiten ohne und {} mit Flughilfe, eigener Platz {} und {}", clean.size(),
                assisted.size(), board.rank(player.getUUID(), null), board.assistedRank(player.getUUID(), null));
        return "board=" + clean.size() + "/" + assisted.size() + " own=" + board.rank(player.getUUID(), null) + "/"
                + board.assistedRank(player.getUUID(), null) + " sum=" + sum(clean) + "/" + sum(assisted);
    }
}

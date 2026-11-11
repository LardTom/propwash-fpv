package de.lardtom.propwash.dev;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.RaceStandards;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.PersonalRuleEdit;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RaceRulesJoinCheck {
    public static final String RESULT = "[race-rules] ";
    public static final int SETTLE_TICKS = 60;
    public static final int RESET_AFTER_TICKS = 20 * 25;
    private static final long S = 1_000_000_000L;
    private static final long MS = 1_000_000L;
    private static long clock;
    private static int joined = -1;
    private static int ranAt = -1;
    private static boolean reset;
    private static final List<GateEntity> SPAWNED = new ArrayList<>();
    private static @Nullable Integer course;
    private static final String SECOND = "PwPersonalB";
    private static final UUID SECOND_ID = UUID.nameUUIDFromBytes(("propwash-personal-" + SECOND).getBytes(StandardCharsets.UTF_8));
    public static final int TWO_CLIENTS = 2;
    public static final String PILOT_A = "PwPersonalA";
    public static final String PILOT_B = SECOND;
    public static final String READY = "two ready ";
    public static final String GO = "two go";
    public static final String DONE = "two result ";
    public static final double[] PARK_A = {-6.0, 1.5, -8.0};
    public static final double[] PARK_B = {-6.0, 1.5, -12.0};
    public static final String EXPECT_A = "assisted[personal_rules:battery_scale=0/100]";
    public static final String EXPECT_B = "assisted[personal_rules:wind=0/1]";
    private static final int TWO_TIMEOUT_TICKS = 20 * 300;
    private static int twoSetupAt = -1;
    private static int twoTurn;
    private static int twoDoneAt = -1;
    private static final List<QuadcopterEntity> TWO_DRONES = new ArrayList<>();

    private RaceRulesJoinCheck() {
    }

    public static synchronized long nextStart(long lapNanos) {
        long t0 = Math.max(System.nanoTime() + 60_000L * MS, clock);
        clock = t0 + lapNanos + 10L * S;
        return t0;
    }

    public static @Nullable String fly(CourseRegistry.Course course, ServerPlayer pilot, Vec3 origin, long lapNanos) {
        return fly(course, pilot, origin, lapNanos, null);
    }

    public static @Nullable String fly(CourseRegistry.Course course, ServerPlayer pilot, Vec3 origin, long lapNanos, @Nullable Runnable halfway) {
        return fly(course, pilot, origin, lapNanos, halfway, null);
    }

    public static @Nullable String fly(CourseRegistry.Course course, ServerPlayer pilot, Vec3 origin, long lapNanos, @Nullable Runnable halfway,
                                       @Nullable DroneBuild build) {
        ServerLevel level = pilot.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        if (build != null) {
            drone.setBuild(build);
        }
        List<Vec3> path = new ArrayList<>(RaceDataJoinCheck.PATH.length);
        for (double[] point : RaceDataJoinCheck.PATH) {
            path.add(origin.add(point[0], point[1], point[2]));
        }
        drone.snapTo(path.getFirst().x, path.getFirst().y, path.getFirst().z, 0.0F, 0.0F);
        drone.setNoGravity(true);
        drone.setPilot(pilot);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(course, pilot) * 3L, lapNanos);
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
            long t0 = nextStart(lap);
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L), pilot,
                    t0 - 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            travelled = 0.0;
            long t = t0;
            double middle = (first + last) / 2.0;
            boolean switched = false;
            for (int i = 1; i < path.size(); i++) {
                Vec3 a = path.get(i - 1);
                Vec3 b = path.get(i);
                double length = a.distanceTo(b);
                int steps = Math.max(1, (int) Math.ceil(length / 0.5));
                for (int s = 1; s <= steps; s++) {
                    double at = travelled + length * s / steps;
                    t = t0 + Math.round((at - first) * perBlock);
                    Vec3 from = a.lerp(b, (s - 1) / (double) steps);
                    Vec3 to = a.lerp(b, s / (double) steps);
                    if (halfway == null) {
                        RaceServer.onDroneMoved(pilot, from, to, t);
                        continue;
                    }
                    if (!switched && at >= middle) {
                        switched = true;
                        halfway.run();
                    }
                    RaceServer.onDroneMoved(drone, pilot, from, to, t);
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

    static void tick(MinecraftServer server, int tick) {
        if (JoinTestEnv.raceRulesMode() == TWO_CLIENTS) {
            tickTwo(server, tick);
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (ranAt >= 0) {
            if (!reset && tick - ranAt >= RESET_AFTER_TICKS) {
                reset = true;
                cleanup(server);
                Constants.LOG.info("[Beitrittstest] race-rules: persönliche Regeln und Freigaben zurückgesetzt");
            }
            return;
        }
        players = players.stream().filter(player -> !player.getUUID().equals(SECOND_ID)).toList();
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
        ranAt = tick;
        ServerPlayer player = players.getFirst();
        String line;
        try {
            line = run(server, player);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] race-rules: Ablauf fehlgeschlagen", e);
            line = "fail " + e;
        }
        Constants.LOG.info("[Beitrittstest] race-rules: {}", line);
        player.sendSystemMessage(Component.literal(RESULT + line).withStyle(ChatFormatting.GRAY));
    }

    private static String verdict(Leaderboard board, UUID player, long nanos) {
        Leaderboard.Entry clean = board.best(player, null);
        if (clean != null && clean.nanos() == nanos) {
            return "clean";
        }
        for (Leaderboard.Entry entry : board.storedTimes()) {
            if (entry.player().equals(player) && entry.nanos() == nanos) {
                if (board.scoring().clean(entry)) {
                    return "clean_hidden";
                }
                List<String> rules = new ArrayList<>();
                for (AssistDetail detail : entry.details()) {
                    rules.add(detail.reason().key() + ":" + detail.subject() + "=" + detail.value() + "/" + detail.reference());
                }
                return "assisted" + (rules.isEmpty() ? "" : "[" + String.join(",", rules) + "]");
            }
        }
        return "missing";
    }

    private static String kept(Leaderboard board, UUID player, long nanos) {
        for (Leaderboard.Entry entry : board.storedTimes()) {
            if (entry.player().equals(player) && entry.nanos() == nanos) {
                return board.scoring().clean(entry) ? "clean" : "assisted";
            }
        }
        return "missing";
    }

    private static ServerPlayer second(MinecraftServer server, ServerPlayer near) {
        ServerPlayer sim = server.getPlayerList().getPlayer(SECOND_ID);
        if (sim == null) {
            GameProfile profile = new GameProfile(SECOND_ID, SECOND);
            sim = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, sim, CommonListenerCookie.createInitial(profile, false));
        }
        sim.teleportTo(near.level(), near.getX() + 2.0, near.getY(), near.getZ(), Set.<Relative>of(), 0.0F, 0.0F, true);
        return sim;
    }

    private static String run(MinecraftServer server, ServerPlayer player) {
        RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Beitrittstest");
        PersonalRulesServer.reset(server);
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        WorldRules.set(server, WorldRuleSpecs.WIND, 1);
        WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, 100);
        WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 0);
        Vec3 origin = RaceDataJoinCheck.origin(player);
        CourseRegistry.Course built = RaceDataJoinCheck.build(player, origin, SPAWNED);
        CourseRegistry registry = CourseRegistry.get();
        if (built == null || registry == null) {
            return "fail Kurs nicht angelegt";
        }
        course = built.id;
        Leaderboard board = registry.board(built);
        UUID id = player.getUUID();
        List<String> out = new ArrayList<>();
        String flown = fly(built, player, origin, 9 * S);
        if (flown != null) {
            return "fail " + flown;
        }
        out.add("disarm_off=" + verdict(board, id, 9 * S));
        WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 1);
        flown = fly(built, player, origin, 8 * S);
        if (flown != null) {
            return "fail " + flown;
        }
        out.add("disarm_on=" + verdict(board, id, 8 * S));
        ServerPlayer other = second(server, player);
        PersonalRulesDev.personal(server, id, Map.of(WorldRuleSpecs.BATTERY_SCALE, 0));
        PersonalRulesDev.personal(server, SECOND_ID, Map.of(WorldRuleSpecs.WIND, 0));
        flown = fly(built, player, origin, 15 * S / 2);
        if (flown != null) {
            return "fail A " + flown;
        }
        out.add("a_battery0=" + verdict(board, id, 15 * S / 2));
        flown = fly(built, other, origin, 7 * S);
        if (flown != null) {
            return "fail B " + flown;
        }
        out.add("b_wind0=" + verdict(board, SECOND_ID, 7 * S));
        WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, 0);
        flown = fly(built, player, origin, 13 * S / 2);
        if (flown != null) {
            return "fail A " + flown;
        }
        out.add("a_server0=" + verdict(board, id, 13 * S / 2));
        WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, 100);
        PersonalRulesServer.edit(server, SECOND_ID, PersonalRuleEdit.Op.RESET, Map.of(WorldRuleSpecs.WIND.name(), 0));
        flown = fly(built, other, origin, 6 * S);
        if (flown != null) {
            return "fail B " + flown;
        }
        out.add("b_reset=" + verdict(board, SECOND_ID, 6 * S));
        out.add("old=" + kept(board, id, 8 * S));
        other.connection.onDisconnect(new DisconnectionDetails(Component.literal("Beitrittstest race-rules beendet")));
        PersonalRulesServer.sync(server);
        boolean ok = out.equals(List.of("disarm_off=clean", "disarm_on=clean", "a_battery0=assisted[personal_rules:battery_scale=0/100]",
                "b_wind0=assisted[personal_rules:wind=0/1]", "a_server0=clean", "b_reset=clean", "old=clean"));
        return (ok ? "ok " : "fail ") + String.join(" ", out);
    }

    private static void tickTwo(MinecraftServer server, int tick) {
        if (twoDoneAt >= 0) {
            if (!reset && tick - twoDoneAt >= RESET_AFTER_TICKS) {
                reset = true;
                for (QuadcopterEntity drone : TWO_DRONES) {
                    drone.setPilot(null);
                    drone.discard();
                }
                TWO_DRONES.clear();
                cleanup(server);
                Constants.LOG.info("[Beitrittstest] race-rules zwei Clients: Drohnen, persönliche Regeln und Freigaben zurückgesetzt");
            }
            return;
        }
        ServerPlayer a = server.getPlayerList().getPlayerByName(PILOT_A);
        ServerPlayer b = server.getPlayerList().getPlayerByName(PILOT_B);
        if (twoSetupAt < 0) {
            if (a == null || b == null) {
                joined = -1;
                return;
            }
            if (joined < 0) {
                joined = tick;
            }
            if (tick - joined < SETTLE_TICKS) {
                return;
            }
            twoSetupAt = tick;
            String failed;
            try {
                failed = setupTwo(server, a, b);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] race-rules zwei Clients: Aufbau fehlgeschlagen", e);
                failed = e.toString();
            }
            if (failed != null) {
                finishTwo(server, tick, "fail " + failed);
            }
            return;
        }
        Integer id = course;
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course built = registry == null || id == null ? null : registry.course(id);
        if (a == null || b == null || built == null) {
            finishTwo(server, tick, "fail Spieler oder Kurs weg: A " + (a != null) + ", B " + (b != null) + ", Kurs " + (built != null));
            return;
        }
        if (tick % 20 != 0) {
            return;
        }
        Leaderboard board = registry.board(built);
        String lapA = lap(board, a.getUUID());
        String lapB = lap(board, b.getUUID());
        if (tick - twoSetupAt > TWO_TIMEOUT_TICKS) {
            finishTwo(server, tick, "fail timeout a=" + lapA + " b=" + lapB);
            return;
        }
        if (twoTurn == 0 && !lapA.equals("missing")) {
            twoTurn = 1;
            Constants.LOG.info("[Beitrittstest] race-rules zwei Clients: Runde von {} gewertet: {}, jetzt {}", PILOT_A, lapA, PILOT_B);
            b.sendSystemMessage(Component.literal(RESULT + GO).withStyle(ChatFormatting.GRAY));
        }
        if (twoTurn == 1 && !lapB.equals("missing")) {
            boolean ok = lapA.equals(EXPECT_A) && lapB.equals(EXPECT_B);
            finishTwo(server, tick, (ok ? "ok " : "fail ") + "a=" + lapA + " b=" + lapB);
        }
    }

    private static String lap(Leaderboard board, UUID player) {
        for (Leaderboard.Entry entry : board.storedTimes()) {
            if (entry.player().equals(player)) {
                return verdict(board, player, entry.nanos());
            }
        }
        return "missing";
    }

    private static void finishTwo(MinecraftServer server, int tick, String line) {
        twoDoneAt = tick;
        twoTurn = 2;
        Constants.LOG.info("[Beitrittstest] race-rules zwei Clients: {}", line);
        for (String name : List.of(PILOT_A, PILOT_B)) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
            if (player != null) {
                player.sendSystemMessage(Component.literal(RESULT + DONE + line).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static @Nullable String setupTwo(MinecraftServer server, ServerPlayer a, ServerPlayer b) {
        RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Beitrittstest");
        PersonalRulesServer.reset(server);
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        WorldRules.set(server, WorldRuleSpecs.WIND, 1);
        WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, 100);
        PersonalRulesServer.setTier(server, WorldRuleSpecs.BATTERY_SCALE, RuleTier.PLAYERS, null, "Beitrittstest");
        PersonalRulesServer.setTier(server, WorldRuleSpecs.WIND, RuleTier.PLAYERS, null, "Beitrittstest");
        PersonalRulesServer.sync(server);
        b.teleportTo(a.level(), a.getX() + 2.0, a.getY(), a.getZ(), Set.<Relative>of(), 0.0F, 0.0F, true);
        Vec3 origin = RaceDataJoinCheck.origin(a);
        CourseRegistry.Course built = RaceDataJoinCheck.build(a, origin, SPAWNED);
        if (built == null) {
            return "Kurs nicht angelegt";
        }
        course = built.id;
        DronePiloting.StartResult startA = fpv(a, origin, PARK_A);
        DronePiloting.StartResult startB = fpv(b, origin, PARK_B);
        Constants.LOG.info("[Beitrittstest] race-rules zwei Clients: Kurs {} bei {}, FPV {} {}, {} {}; Akku und Wind auf Stufe Spieler, Server Wind 1, "
                + "Akku 100 %", built.id, origin, PILOT_A, startA, PILOT_B, startB);
        if (startA != DronePiloting.StartResult.STARTED || startB != DronePiloting.StartResult.STARTED) {
            return "FPV startet nicht: A " + startA + ", B " + startB;
        }
        String ready = RESULT + READY + built.id + " " + String.format(Locale.ROOT, "%.3f %.3f %.3f", origin.x, origin.y, origin.z);
        a.sendSystemMessage(Component.literal(ready).withStyle(ChatFormatting.GRAY));
        b.sendSystemMessage(Component.literal(ready).withStyle(ChatFormatting.GRAY));
        a.sendSystemMessage(Component.literal(RESULT + GO).withStyle(ChatFormatting.GRAY));
        twoTurn = 0;
        return null;
    }

    private static DronePiloting.StartResult fpv(ServerPlayer player, Vec3 origin, double[] park) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(origin.x + park[0], origin.y + park[1], origin.z + park[2], 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.RACER);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        TWO_DRONES.add(drone);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new)
                .orElseThrow(() -> new IllegalStateException("Analog-Brille fehlt"));
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        return DronePiloting.tryStart(player, drone, PilotMode.FPV);
    }

    private static void cleanup(MinecraftServer server) {
        WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, WorldRuleSpecs.CRASH_DISARM.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.WIND, WorldRuleSpecs.WIND.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.BATTERY_SCALE, WorldRuleSpecs.BATTERY_SCALE.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, WorldRuleSpecs.SAVE_ASSISTED_LAPS.defaultValue());
        PersonalRulesServer.reset(server);
        for (GateEntity gate : SPAWNED) {
            gate.discard();
        }
        SPAWNED.clear();
        CourseRegistry registry = CourseRegistry.get();
        Integer id = course;
        if (registry != null && id != null) {
            registry.deleteCourse(id);
        }
        course = null;
    }
}

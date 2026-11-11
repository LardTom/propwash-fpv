package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseAccess;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RaceP2pJoinCheck {
    public static final String RESULT = "[race-p2p] ";
    public static final String OWNER = "PwP2pA";
    public static final String NAME = "P2P Beitritt";
    public static final String STOLEN = "Geklaut";
    public static final double SPACING = 30.0;
    public static final int SETTLE_TICKS = 60;
    public static final int OP_AFTER_TICKS = 20 * 25;
    public static final int CLEANUP_AFTER_TICKS = 20 * 70;
    private static final long MS = 1_000_000L;
    private static final List<GateEntity> SPAWNED = new ArrayList<>();
    private static int joined = -1;
    private static int ranAt = -1;
    private static int switchedAt = -1;
    private static @Nullable NameAndId opped;
    private static boolean deopped;
    private static boolean cleaned;
    private static boolean initialized;
    private static @Nullable Integer course;
    private static @Nullable String guest;

    private RaceP2pJoinCheck() {
    }

    static synchronized void init() {
        if (initialized || JoinTestEnv.holdSeconds() <= 0 || !JoinTestEnv.raceP2p()) {
            return;
        }
        initialized = true;
        ServerBus.STOPPED.register(server -> deop(server, "beim Stopp des Servers"));
    }

    private static void deop(MinecraftServer server, String when) {
        NameAndId id = opped;
        if (id == null || deopped) {
            return;
        }
        deopped = true;
        server.getPlayerList().deop(id);
        Constants.LOG.info("[Beitrittstest] race-p2p: {} ist kein Operator mehr ({})", id.name(), when);
    }

    public static CourseRegistry.@Nullable Course build(ServerPlayer owner, Vec3 origin, List<GateEntity> spawned) {
        ServerLevel level = owner.level();
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        owner.setYRot(0.0F);
        owner.setXRot(0.0F);
        owner.setYHeadRot(0.0F);
        for (int i = 0; i < 3; i++) {
            GateEntity gate = RaceDataJoinCheck.gate(level, origin.add(0.0, 0.0, SPACING * i));
            spawned.add(gate);
            CourseToolItem.useOnGate(tool, owner, gate);
        }
        CourseRegistry registry = CourseRegistry.get();
        return registry == null ? null : registry.course(CourseToolItem.courseOf(tool));
    }

    public static @Nullable String fly(CourseRegistry.Course course, ServerPlayer pilot, Vec3 origin, long runNanos) {
        ServerLevel level = pilot.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        List<GateGeometry> gates = course.toData().geometry();
        GateGeometry start = gates.getFirst();
        GateGeometry finish = gates.getLast();
        Vec3 from = new Vec3(start.centerX(), start.centerY(), start.centerZ() - 4.0);
        Vec3 to = new Vec3(finish.centerX(), finish.centerY(), finish.centerZ() + 4.0);
        drone.snapTo(from.x, from.y, from.z, 0.0F, 0.0F);
        drone.setNoGravity(true);
        drone.setPilot(pilot);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long run = Math.max(RaceServer.minimumLapNanos(course, pilot) * 3L, runNanos);
            double span = finish.centerZ() - start.centerZ();
            double perBlock = run / span;
            long t0 = RaceRulesJoinCheck.nextStart(run);
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L), pilot,
                    t0 - 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            double length = from.distanceTo(to);
            int steps = Math.max(1, (int) Math.ceil(length / 0.5));
            long t = t0;
            for (int s = 1; s <= steps; s++) {
                Vec3 a = from.lerp(to, (s - 1) / (double) steps);
                Vec3 b = from.lerp(to, s / (double) steps);
                t = t0 + Math.round((b.z - start.centerZ()) * perBlock);
                RaceServer.onDroneMoved(pilot, a, b, t);
            }
            drone.snapTo(to.x, to.y, to.z, 0.0F, 0.0F);
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course.id, 1, run, 0L), pilot, t + 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Lauf am Ziel abgelehnt: " + verdict;
            }
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.FINISH, course.id, 1, run, run), pilot, t + 200L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Ziel abgelehnt: " + verdict;
            }
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    public static String rights(CourseRegistry registry, CourseRegistry.Course course, ServerPlayer owner, ServerPlayer other) {
        List<String> out = new ArrayList<>();
        CourseAccess access = CourseEdits.access(other, course, CourseEdits.Action.MODE);
        CourseEdits.Result mode = CourseEdits.apply(other, new RacePayloads.CourseEditC2S(course.id, CourseEdits.Action.MODE, CourseMode.LAPS.ordinal(), 0));
        CourseEdits.Result rename = CourseEdits.apply(other, RacePayloads.CourseEditC2S.rename(course.id, STOLEN));
        CourseEdits.Result remove = CourseEdits.apply(other, new RacePayloads.CourseEditC2S(course.id, CourseEdits.Action.REMOVE, 1, 0));
        boolean untouched = course.mode() == CourseMode.POINT_TO_POINT && NAME.equals(course.name) && course.gates.size() == 3;
        out.add("other=" + access.name().toLowerCase(Locale.ROOT));
        out.add("other_denied=" + (mode == CourseEdits.Result.DENIED && rename == CourseEdits.Result.DENIED && remove == CourseEdits.Result.DENIED
                && untouched ? "yes" : "no:" + mode + "," + rename + "," + remove));
        String previous = course.owner;
        String previousName = course.ownerName;
        course.owner = "";
        course.ownerName = "";
        CourseAccess ownerless = CourseEdits.access(other, course, CourseEdits.Action.MODE);
        CourseAccess formerOwner = CourseEdits.access(owner, course, CourseEdits.Action.MODE);
        course.owner = previous;
        course.ownerName = previousName;
        out.add("ownerless=" + ownerless.name().toLowerCase(Locale.ROOT) + "," + formerOwner.name().toLowerCase(Locale.ROOT));
        out.add("owner=" + CourseEdits.access(owner, course, CourseEdits.Action.MODE).name().toLowerCase(Locale.ROOT));
        return String.join(" ", out);
    }

    static void tick(MinecraftServer server, int tick) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (ranAt >= 0) {
            ServerPlayer joinedPlayer = guest == null ? null : server.getPlayerList().getPlayerByName(guest);
            if (!cleaned && opped == null && tick - ranAt >= OP_AFTER_TICKS && joinedPlayer != null) {
                opped = joinedPlayer.nameAndId();
                server.getPlayerList().op(opped);
                CourseRegistry registry = CourseRegistry.get();
                CourseRegistry.Course built = registry == null || course == null ? null : registry.course(course);
                String access = built == null ? "-" : CourseEdits.access(joinedPlayer, built, CourseEdits.Action.MODE).name();
                Constants.LOG.info("[Beitrittstest] race-p2p: {} ist Operator, Zugriff {}", guest, access);
            }
            CourseRegistry current = CourseRegistry.get();
            CourseRegistry.Course flown = current == null || course == null ? null : current.course(course);
            if (switchedAt < 0 && opped != null && flown != null && flown.mode() == CourseMode.LAPS) {
                switchedAt = tick;
                deop(server, "nach dem Umschalten auf Runden");
            }
            boolean settledBack = switchedAt >= 0 && tick - switchedAt >= SETTLE_TICKS;
            if (!cleaned && (settledBack || joinedPlayer == null || tick - ranAt >= CLEANUP_AFTER_TICKS)) {
                cleaned = true;
                deop(server, joinedPlayer == null ? "nach dem Verlassen" : "beim Aufräumen");
                cleanup();
                Constants.LOG.info("[Beitrittstest] race-p2p: aufgeräumt");
            }
            return;
        }
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
        guest = player.getGameProfile().name();
        String line;
        try {
            line = run(server, player);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] race-p2p: Ablauf fehlgeschlagen", e);
            line = "fail " + e;
        }
        Constants.LOG.info("[Beitrittstest] race-p2p: {}", line);
        player.sendSystemMessage(Component.literal(RESULT + line).withStyle(ChatFormatting.GRAY));
    }

    private static String run(MinecraftServer server, ServerPlayer player) {
        if (CourseRegistry.isOperator(player)) {
            return "fail " + player.getGameProfile().name() + " ist schon Operator";
        }
        ServerPlayer owner = RaceDataJoinCheck.secondPilot(server, player.level(), OWNER);
        Vec3 origin = RaceDataJoinCheck.origin(player);
        CourseRegistry.Course built = build(owner, origin, SPAWNED);
        CourseRegistry registry = CourseRegistry.get();
        if (built == null || registry == null) {
            return "fail Kurs nicht angelegt";
        }
        course = built.id;
        registry.rename(built, NAME);
        CourseEdits.Result switched = CourseEdits.apply(owner, new RacePayloads.CourseEditC2S(built.id, CourseEdits.Action.MODE,
                CourseMode.POINT_TO_POINT.ordinal(), 0));
        if (switched != CourseEdits.Result.DONE || !built.pointToPoint()) {
            return "fail Besitzer kann nicht auf Punkt zu Punkt umschalten: " + switched;
        }
        String flown = fly(built, owner, origin, 9_000L * MS);
        if (flown != null) {
            return "fail " + flown;
        }
        Leaderboard.Entry best = registry.board(built).best(owner.getUUID(), null);
        if (best == null || Math.abs(best.nanos() - 9_000L * MS) > MS) {
            return "fail keine Zeit von Start bis Ziel in der Bestenliste: " + best;
        }
        String rights = rights(registry, built, owner, player);
        boolean ok = rights.equals("other=not_owner other_denied=yes ownerless=ownerless,ownerless owner=allowed");
        return (ok ? "ok " : "fail ") + "course=" + built.id + " key=" + built.key() + " mode=" + built.mode().key() + " owner=" + OWNER + " run="
                + best.nanos() / MS + "ms " + rights;
    }

    private static void cleanup() {
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

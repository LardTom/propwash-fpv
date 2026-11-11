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
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateCollisions;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.ScoreArea;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.HomeResetRules;
import de.lardtom.propwash.rescue.HomeResets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CovRaceJoinCheck {
    public static final String RESULT = "[cov-race] ";
    public static final String COURSE_NAME = "CovRaceNeustart";
    public static final String SECOND = "PwCovRaceB";
    public static final int SETTLE_TICKS = 60;
    public static final int AWAY = 24;
    public static final double HOME_BEFORE_START = 5.0;
    public static final double NUDGE_DX = -0.5;
    public static final double NUDGE_DY = 0.25;
    public static final int SPECTATOR_TICKS = 240;
    public static final int PILOT_TICKS = 700;
    public static final int EDIT_TICKS = 1600;
    private static final long MS = 1_000_000L;
    private static final double EPS = 1.0E-4;
    private static final double LEAD = 3.0;
    private static int joined = -1;
    private static int stage;
    private static int stageTick;
    private static boolean lobbySeen;
    private static boolean wasOp;
    private static int course = GateEntity.NO_COURSE;
    private static @Nullable ServerPlayer second;
    private static @Nullable QuadcopterEntity secondDrone;
    private static @Nullable QuadcopterEntity pilotDrone;
    private static @Nullable GateEntity guestGate;
    private static @Nullable GateEntity nudged;
    private static @Nullable Vec3 nudgedFrom;
    private static @Nullable Vec3 guestFrom;
    private static @Nullable Vec3 home;
    private static String resetOutcome = "";
    private static int movedAt = -1;

    private CovRaceJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream().filter(p -> p != second).toList();
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
        ServerPlayer player = players.getFirst();
        try {
            if (JoinTestEnv.covRace() == 2) {
                restart(server, player, tick);
            } else {
                firstRun(server, player, tick);
            }
        } catch (RuntimeException e) {
            stage = 99;
            Constants.LOG.error("[Beitrittstest] cov-race: Ablauf fehlgeschlagen", e);
            say(player, "fail " + e);
        }
    }

    private static void say(ServerPlayer player, String text) {
        Constants.LOG.info("[Beitrittstest] cov-race: {}", text);
        player.sendSystemMessage(Component.literal(RESULT + text));
    }

    private static void next(int value, int tick) {
        stage = value;
        stageTick = tick;
    }

    private static GateEntity gate(ServerLevel level, Vec3 at) {
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

    private static ServerPlayer fake(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("propwash-cov-race-" + SECOND).getBytes(StandardCharsets.UTF_8)), SECOND);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static QuadcopterEntity drone(ServerLevel level, Vec3 at) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        level.addFreshEntity(drone);
        drone.setHomePoint(new HomePoint(at.x, at.y, at.z, HomePointCodecs.dimension(level), level.getGameTime(), HomePoint.Source.MANUAL));
        return drone;
    }

    private static void firstRun(MinecraftServer server, ServerPlayer player, int tick) {
        ServerLevel level = player.level();
        switch (stage) {
            case 0 -> {
                build(server, player);
                next(1, tick);
            }
            case 1 -> {
                QuadcopterEntity drone = secondDrone;
                if (drone == null || home == null) {
                    return;
                }
                int since = tick - stageTick;
                if (since == 20) {
                    drone.resetTo(home.add(6.0, 5.0, -8.0), drone.orientation());
                } else if (since == 40 && second != null) {
                    drone.setPilot(second);
                    HomeResetRules.Outcome outcome = HomeResets.reset(second, drone);
                    resetOutcome = outcome.name().toLowerCase(Locale.ROOT);
                }
                if (since >= 40 && (RaceServer.lobby(course) != null || RaceServer.heat(course) != null)) {
                    lobbySeen = true;
                }
                if (since >= SPECTATOR_TICKS) {
                    boolean atHome = drone.position().distanceTo(home) < 1.5;
                    say(player, "spectator " + (lobbySeen ? "start" : "clean") + " reset=" + resetOutcome + " home=" + atHome);
                    lobbySeen = false;
                    startPilot(player);
                    next(2, tick);
                }
            }
            case 2 -> {
                if (RaceServer.lobby(course) != null || RaceServer.heat(course) != null) {
                    lobbySeen = true;
                }
                if (tick - stageTick >= PILOT_TICKS) {
                    DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
                    if (pilotDrone != null) {
                        pilotDrone.discard();
                    }
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
                    player.getInventory().setItem(0, tool);
                    player.getInventory().setSelectedSlot(0);
                    player.connection.send(new ClientboundSetHeldSlotPacket(0));
                    Vec3 stand = nudgedFrom.add(3.0, 0.0, -3.5);
                    player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
                    say(player, "pilot " + (lobbySeen ? "start" : "clean") + " second=" + (RaceServer.lobby(course) == null) + " gb=" + guestGate.getId()
                            + " kn=" + nudged.getId());
                    next(3, tick);
                }
            }
            case 3 -> {
                GateEntity mine = nudged;
                GateEntity theirs = guestGate;
                if (mine == null || theirs == null) {
                    return;
                }
                boolean guestMoved = theirs.position().distanceTo(guestFrom) > EPS;
                if (movedAt < 0 && mine.position().distanceTo(nudgedFrom) > EPS) {
                    movedAt = tick;
                }
                if (movedAt >= 0 && tick - movedAt >= 100 || tick - stageTick > EDIT_TICKS) {
                    Vec3 shift = mine.position().subtract(nudgedFrom);
                    boolean expected = Math.abs(shift.x - NUDGE_DX) < EPS && Math.abs(shift.y - NUDGE_DY) < EPS && Math.abs(shift.z) < EPS;
                    CourseRegistry registry = CourseRegistry.get();
                    CourseRegistry.Course data = registry == null ? null : registry.course(course);
                    boolean recorded = data != null && Math.abs(data.gates.get(2).x - mine.getX()) < EPS && Math.abs(data.gates.get(2).y - mine.getY()) < EPS;
                    BlockPos spawn = level.getRespawnData().pos();
                    List<QuadcopterEntity> leftovers = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(spawn).inflate(32.0),
                            drone -> !drone.isRemoved());
                    leftovers.forEach(Entity::discard);
                    Constants.LOG.info("[Beitrittstest] cov-race: {} Drohnen anderer Prüfungen am Spawn entfernt, damit Lauf 2 dieselbe Welt nutzen kann",
                            leftovers.size());
                    server.saveEverything(false, true, true);
                    if (secondDrone != null) {
                        secondDrone.discard();
                    }
                    if (wasOp) {
                        server.getPlayerList().op(player.nameAndId());
                    }
                    say(player, "phase1 saved nudge=" + (expected ? "ok" : "wrong " + shift) + " record=" + recorded + " guest=" + (guestMoved ? "moved"
                            : "unchanged") + String.format(Locale.ROOT, " kn=%.4f,%.4f,%.4f", mine.getX(), mine.getY(), mine.getZ()));
                    next(4, tick);
                }
            }
            default -> {
            }
        }
    }

    private static void build(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = player.level();
        wasOp = server.getPlayerList().isOp(player.nameAndId());
        if (wasOp) {
            server.getPlayerList().deop(player.nameAndId());
        }
        int x = player.getBlockX() + AWAY;
        int z = player.getBlockZ();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Vec3 origin = new Vec3(x + 0.5, ground, z + 0.5);
        GateEntity start = gate(level, origin);
        GateEntity pole = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (pole == null) {
            throw new IllegalStateException("Gate-Stange nicht erzeugt");
        }
        pole.setSize(GateSize.MEDIUM);
        pole.applyGeometry(GateGeometry.element(origin.x, origin.y, origin.z + 12.0, 0.0F, CourseElement.GATE_POLE, 3, GatePoles.THICKNESS, 4.0,
                GateTilt.NONE, GateMount.STANDING));
        level.addFreshEntity(pole);
        GateEntity last = gate(level, origin.add(0.0, 0.0, 24.0));
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        face(player, 0.0F);
        CourseToolItem.useOnGate(tool, player, start);
        CourseToolItem.useOnGate(tool, player, pole);
        CourseToolItem.useOnGate(tool, player, last);
        course = CourseToolItem.courseOf(tool);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course data = registry == null ? null : registry.course(course);
        if (data == null || data.gates.size() != 3 || pole.score().role() != ScoreArea.Role.PASS) {
            throw new IllegalStateException("Kurs mit Gate-Stange nicht angelegt: " + (data == null ? "keiner" : data.gates.size()) + " " + pole.score());
        }
        registry.rename(data, COURSE_NAME);
        registry.setLaps(data, 1);
        nudged = last;
        nudgedFrom = last.position();
        ServerPlayer b = fake(server, level);
        second = b;
        b.snapTo(origin.x - 4.0, origin.y, origin.z - 3.0, 0.0F, 0.0F);
        GateEntity theirs = gate(level, origin.add(6.0, 0.0, 24.0));
        GateEntity theirsSecond = gate(level, origin.add(6.0, 0.0, 36.0));
        ItemStack theirTool = new ItemStack(RaceModule.COURSE_TOOL.get());
        face(b, 0.0F);
        CourseToolItem.useOnGate(theirTool, b, theirs);
        CourseToolItem.useOnGate(theirTool, b, theirsSecond);
        CourseRegistry.Course theirCourse = registry.course(CourseToolItem.courseOf(theirTool));
        if (theirCourse == null || !theirCourse.ownedBy(b.getUUID())) {
            throw new IllegalStateException("Kurs des zweiten Spielers nicht angelegt");
        }
        guestGate = theirs;
        guestFrom = theirs.position();
        home = new Vec3(origin.x, origin.y + 0.05, origin.z - HOME_BEFORE_START);
        QuadcopterEntity drone = drone(level, home);
        drone.setPilot(b);
        secondDrone = drone;
        Constants.LOG.info("[Beitrittstest] cov-race: Kurs {} „{}“ (Start-Gate, Gate-Stange {}, Gate) bei {} {} {}, Kurs {} von {}, Operator vorher {}",
                course, COURSE_NAME, pole.score().role(), x, ground, z, theirCourse.id, SECOND, wasOp);
    }

    private static void face(ServerPlayer player, float yaw) {
        player.setYRot(yaw);
        player.setXRot(0.0F);
        player.setYHeadRot(yaw);
    }

    private static void startPilot(ServerPlayer player) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = drone(level, home);
        pilotDrone = drone;
        player.teleportTo(level, home.x + 3.0, home.y, home.z - 3.0, Set.of(), 0.0F, 0.0F, true);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        Constants.LOG.info("[Beitrittstest] cov-race: FPV für den Piloten am Start-Gate: {}", result);
    }

    private static void restart(MinecraftServer server, ServerPlayer player, int tick) {
        if (stage == 0) {
            next(1, tick);
            return;
        }
        if (stage != 1) {
            return;
        }
        String problem = restartCheck(server, player);
        if (problem != null && problem.startsWith("warten") && tick - stageTick < 400) {
            return;
        }
        say(player, problem == null ? "restart ok course=" + course + " " + RESTART.toString() : "restart fail " + problem);
        next(2, tick);
    }

    private static final StringBuilder RESTART = new StringBuilder();

    private static @Nullable String restartCheck(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "keine Kursdaten";
        }
        CourseRegistry.Course data = null;
        for (CourseRegistry.Course candidate : registry.courses()) {
            if (COURSE_NAME.equals(candidate.name)) {
                data = candidate;
            }
        }
        if (data == null) {
            return "Kurs „" + COURSE_NAME + "“ nach dem Neustart nicht gefunden";
        }
        course = data.id;
        if (data.gates.size() != 3) {
            return "Kurs nach dem Neustart mit " + data.gates.size() + " Gates";
        }
        CourseRegistry.GateRecord pole = data.gates.get(1);
        if (pole.kind() != CourseElement.GATE_POLE || pole.scoreArea().role() != ScoreArea.Role.PASS) {
            return "Gate-Stange nach dem Neustart: " + pole.kind() + " " + pole.scoreArea();
        }
        CourseRegistry.GateRecord moved = data.gates.get(2);
        double fx = moved.x - Math.floor(moved.x);
        double fy = moved.y - Math.floor(moved.y);
        if (Math.abs(fx) > EPS || Math.abs(fy - NUDGE_DY) > EPS) {
            return String.format(Locale.ROOT, "verschobenes Gate in der Kursdatei nicht an der verschobenen Lage: %.4f/%.4f", moved.x, moved.y);
        }
        Entity poleEntity = level.getEntity(UUID.fromString(pole.uuid));
        Entity movedEntity = level.getEntity(UUID.fromString(moved.uuid));
        if (!(poleEntity instanceof GateEntity poleGate) || !(movedEntity instanceof GateEntity movedGate)) {
            return "warten: Gates nach dem Neustart noch nicht geladen";
        }
        if (poleGate.score().role() != ScoreArea.Role.PASS || poleGate.courseId() != data.id) {
            return "Gate-Stange als Entity nach dem Neustart: " + poleGate.score() + " Kurs " + poleGate.courseId();
        }
        if (Math.abs(movedGate.getX() - moved.x) > EPS || Math.abs(movedGate.getY() - moved.y) > EPS || Math.abs(movedGate.getZ() - moved.z) > EPS) {
            return "verschobenes Gate als Entity an anderer Lage: " + movedGate.position();
        }
        GateGeometry g = movedGate.geometry();
        List<AABB> boxes = new ArrayList<>();
        Vec3 post = new Vec3(g.x() - g.sideX() * g.postOffset(), g.y() + 1.5, g.z() - g.sideZ() * g.postOffset());
        GateCollisions.collect(level, new AABB(post, post).inflate(0.05), boxes);
        if (boxes.isEmpty()) {
            return "keine Kollision am Pfosten des verschobenen Gates";
        }
        String lap = lap(player, data);
        if (lap != null) {
            return lap;
        }
        RESTART.setLength(0);
        RESTART.append(String.format(Locale.ROOT, "kn=%.4f,%.4f,%.4f pole=pass lap=ok", movedGate.getX(), movedGate.getY(), movedGate.getZ()));
        return null;
    }

    private static List<Vec3> path(List<GateGeometry> gates) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i <= gates.size(); i++) {
            GateGeometry gate = gates.get(i % gates.size());
            List<GateGeometry.Waypoint> points = i == gates.size() ? List.of(gate.waypoints().getFirst()) : gate.waypoints();
            for (GateGeometry.Waypoint p : points) {
                out.add(new Vec3(p.x() - p.dx() * LEAD, p.y() - p.dy() * LEAD, p.z() - p.dz() * LEAD));
                out.add(new Vec3(p.x() + p.dx() * LEAD, p.y() + p.dy() * LEAD, p.z() + p.dz() * LEAD));
            }
            if (i == gates.size() - 1) {
                Vec3 exit = out.getLast();
                Vec3 first = new Vec3(gates.getFirst().x(), exit.y, gates.getFirst().z() - LEAD);
                out.add(new Vec3(exit.x + 8.0, exit.y, exit.z));
                out.add(new Vec3(first.x + 8.0, first.y, first.z - 1.0));
            }
        }
        return out;
    }

    private static @Nullable String lap(ServerPlayer pilot, CourseRegistry.Course data) {
        ServerLevel level = pilot.level();
        CourseRegistry registry = CourseRegistry.get();
        List<GateGeometry> gates = data.toData().geometry();
        List<Vec3> path = path(gates);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.snapTo(path.getFirst().x, path.getFirst().y, path.getFirst().z, 0.0F, 0.0F);
        drone.setPilot(pilot);
        level.addFreshEntity(drone);
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(data, pilot) * 3L, 8_000L * MS);
            double total = 0.0;
            for (int i = 1; i < path.size(); i++) {
                total += path.get(i - 1).distanceTo(path.get(i));
            }
            double startAt = path.get(0).distanceTo(path.get(1)) * 0.5;
            double endAt = total - path.get(path.size() - 2).distanceTo(path.getLast()) * 0.5;
            double perBlock = lap / (endAt - startAt);
            long t0 = System.nanoTime() + 60_000L * MS;
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, data.id, 0, 0L, 0L), pilot,
                    t0 - 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            double travelled = 0.0;
            long t = t0;
            for (int i = 1; i < path.size(); i++) {
                Vec3 a = path.get(i - 1);
                Vec3 b = path.get(i);
                double length = a.distanceTo(b);
                int steps = Math.max(1, (int) Math.ceil(length / 0.5));
                for (int s = 1; s <= steps; s++) {
                    t = t0 + Math.round((travelled + length * s / steps - startAt) * perBlock);
                    RaceServer.onDroneMoved(pilot, a.lerp(b, (s - 1) / (double) steps), a.lerp(b, s / (double) steps), t);
                }
                travelled += length;
            }
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, data.id, 1, lap, 0L), pilot, t + 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde nach dem Neustart abgelehnt: " + verdict;
            }
            UUID id = pilot.getUUID();
            List<Leaderboard.Entry> entries = new ArrayList<>(registry.board(data).entries());
            entries.addAll(registry.board(data).assistedEntries());
            return entries.stream().anyMatch(entry -> entry.player().equals(id) && entry.nanos() == lap) ? null : "Runde fehlt in der Bestenliste";
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }
}

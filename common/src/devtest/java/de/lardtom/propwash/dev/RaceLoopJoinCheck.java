package de.lardtom.propwash.dev;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.network.Connection;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RaceLoopJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int AWAY = 24;
    public static final int LIFT = 20;
    public static final String SECOND = "PwLoopB";
    public static final double[][] LOOP = {{0.0, 1.5, -3.5}, {0.0, 1.5, 15.5}, {6.0, 1.5, 15.5}, {6.0, 1.5, 3.5}, {0.0, 1.5, 3.5},
            {0.0, 1.5, -4.5}, {-6.0, 1.5, -4.5}, {-6.0, 1.5, -6.5}, {0.0, 1.5, -6.5}, {0.0, 1.5, 1.5}};
    private static final long MS = 1_000_000L;
    private static int joined = -1;
    private static boolean built;

    private RaceLoopJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        if (built) {
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
        built = true;
        ServerPlayer player = players.getFirst();
        try {
            String problem = run(server, player);
            if (problem != null) {
                Constants.LOG.error("[Beitrittstest] Schleife: {}", problem);
            }
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] Schleife: Ablauf fehlgeschlagen", e);
        }
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

    static ServerPlayer secondPilot(MinecraftServer server, ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("propwash-loop-" + SECOND).getBytes(StandardCharsets.UTF_8)), SECOND);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static @Nullable String run(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = player.level();
        int x = player.getBlockX() + AWAY;
        int z = player.getBlockZ();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + LIFT;
        Vec3 origin = new Vec3(x + 0.5, ground, z + 0.5);
        GateEntity a = gate(level, origin);
        GateEntity b = gate(level, origin.add(0.0, 0.0, 12.0));
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        face(player, 0.0F);
        CourseToolItem.useOnGate(tool, player, a);
        CourseToolItem.useOnGate(tool, player, b);
        face(player, 180.0F);
        CourseToolItem.useOnGate(tool, player, a);
        int courseId = CourseToolItem.courseOf(tool);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(courseId);
        if (course == null || course.gates.size() != 3 || !course.gates.get(2).reversed()) {
            return "Kurs nicht angelegt";
        }
        registry.setLaps(course, 1);
        Constants.LOG.info("[Beitrittstest] Schleife: Kurs {} bei {} {} {} mit Gate A, Gate B, Gate A rückwärts; Nummern an A: {}", courseId, x, ground,
                z, a.passText());
        List<ServerPlayer> pilots = List.of(secondPilot(server, level), player);
        for (ServerPlayer pilot : pilots) {
            String problem = lap(level, registry, course, pilot, origin);
            if (problem != null) {
                return pilot.getGameProfile().name() + ": " + problem;
            }
            Constants.LOG.info("[Beitrittstest] Schleife: Runde von {} am Server angenommen", pilot.getGameProfile().name());
        }
        List<Leaderboard.Entry> entries = registry.board(course).entries();
        Constants.LOG.info("[Beitrittstest] Schleife: Bestenliste mit {} Piloten", entries.size());
        RaceServer.sendLeaderboard(player, courseId, false);
        return null;
    }

    private static void face(ServerPlayer player, float yaw) {
        player.setYRot(yaw);
        player.setXRot(0.0F);
        player.setYHeadRot(yaw);
    }

    private static @Nullable String lap(ServerLevel level, CourseRegistry registry, CourseRegistry.Course course, ServerPlayer pilot, Vec3 origin) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        List<Vec3> path = new ArrayList<>(LOOP.length);
        for (double[] point : LOOP) {
            path.add(origin.add(point[0], point[1], point[2]));
        }
        drone.snapTo(path.getFirst().x, path.getFirst().y, path.getFirst().z, 0.0F, 0.0F);
        drone.setPilot(pilot);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(course, pilot) * 3L, 8_000L * MS);
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
            UUID id = pilot.getUUID();
            if (registry.board(course).entries().stream().noneMatch(entry -> entry.player().equals(id))) {
                return "Runde fehlt in der Bestenliste";
            }
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }
}

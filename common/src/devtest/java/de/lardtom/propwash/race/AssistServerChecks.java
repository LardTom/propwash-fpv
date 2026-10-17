package de.lardtom.propwash.race;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import net.minecraft.core.BlockPos;
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
import java.util.Locale;
import java.util.UUID;

public final class AssistServerChecks {
    private static final long MS = 1_000_000L;
    private static final long TICK = 50L * MS;
    private static final int LAPS = 3;
    private static final int PHYSICS_LAP = 2;
    private static final int GRAVITY = 130;
    private static volatile String summary = "";

    private AssistServerChecks() {
    }

    private enum Setup {
        CLEAN("pw_clean", DroneBuild.FREESTYLE),
        CREATIVE("pw_creative", new DroneBuild(FrameType.FREESTYLE_5, StackType.CREATIVE_FC, DroneBuild.FREESTYLE.motor(), DroneBuild.FREESTYLE.prop(),
                DroneBuild.FREESTYLE.video(), DroneBuild.FREESTYLE.battery())),
        PARTS("pw_parts", DroneBuild.FREESTYLE.withProp(PropType.P7_2B)),
        CLIENT_AIDS("pw_aids", DroneBuild.FREESTYLE);

        final String name;
        final DroneBuild build;

        Setup(String name, DroneBuild build) {
            this.name = name;
            this.build = build;
        }

        UUID uuid() {
            return UUID.nameUUIDFromBytes(("propwash-assist-" + this.name).getBytes(StandardCharsets.UTF_8));
        }

        AssistReasons client(int lap) {
            if (this != CLIENT_AIDS) {
                return AssistReasons.NONE;
            }
            AssistReasons aids = AssistReasons.of(List.of(AssistDetail.hold(HoldMode.ALTHOLD), AssistDetail.goggles(10, 25)));
            return lap == 1 ? aids.plus(AssistReasons.of(List.of(AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, 200)))) : aids;
        }

        AssistReasons expected(int lap) {
            AssistReasons own = switch (this) {
                case CLEAN -> AssistReasons.NONE;
                case CREATIVE -> AssistReasons.of(List.of(AssistDetail.creative()));
                case PARTS -> AssistReasons.of(List.of(AssistDetail.issue("prop_too_large")));
                case CLIENT_AIDS -> AssistReasons.of(List.of(AssistDetail.hold(HoldMode.ALTHOLD), AssistDetail.goggles(10, 25)));
            };
            return lap == PHYSICS_LAP ? own.plus(AssistReasons.of(List.of(AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, GRAVITY)))) : own;
        }
    }

    private record Pilot(Setup setup, ServerPlayer player, QuadcopterEntity drone) {
    }

    public static String summary() {
        return summary;
    }

    static ServerPlayer fakePilot(MinecraftServer server, ServerLevel level, Setup setup) {
        GameProfile profile = new GameProfile(setup.uuid(), setup.name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    public static void forceChunks(MinecraftServer server, boolean forced) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        int chunkX = spawn.getX() >> 4;
        int chunkZ = spawn.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -2; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    level.setChunkForced(chunkX + dx, chunkZ + dz, forced);
                }
            }
        }
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "CourseRegistry läuft nicht";
        }
        int savedRule = WorldRules.get(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS);
        int savedGravity = WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE);
        List<GateEntity> gates = new ArrayList<>();
        List<Pilot> pilots = new ArrayList<>();
        int courseId = GateEntity.NO_COURSE;
        forceChunks(server, true);
        try {
            WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
            List<ServerPlayer> players = new ArrayList<>();
            for (Setup setup : Setup.values()) {
                players.add(fakePilot(server, level, setup));
            }
            double x = spawn.getX() + 4.5;
            double z = spawn.getZ() - 4.5;
            gates.add(gate(level, x, z));
            gates.add(gate(level, x, z - 16.0));
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            for (GateEntity gate : gates) {
                CourseToolItem.useOnGate(tool, players.getFirst(), gate);
            }
            courseId = CourseToolItem.courseOf(tool);
            CourseRegistry.Course course = registry.course(courseId);
            if (course == null || course.gates.size() != 2) {
                return "Kurs nicht angelegt";
            }
            registry.setLaps(course, LAPS);
            CourseRegistry.GateRecord start = course.gates.getFirst();
            for (int i = 0; i < players.size(); i++) {
                Setup setup = Setup.values()[i];
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    return "Drohne nicht erzeugt";
                }
                drone.setBuild(setup.build);
                drone.snapTo(start.x + i, start.y + 1.5, start.z + 3.0, 0.0F, 0.0F);
                drone.setNoGravity(true);
                if (!level.addFreshEntity(drone)) {
                    return "Drohne nicht gespawnt";
                }
                drone.setPilot(players.get(i));
                pilots.add(new Pilot(setup, players.get(i), drone));
            }
            for (Pilot pilot : pilots) {
                AssistReasons seen = RaceServer.serverReasons(pilot.drone());
                if (!seen.equals(pilot.setup().expected(1).only(LapAssist.SERVER_REASONS))) {
                    return pilot.setup().name + ": Server sieht " + text(seen) + " statt " + text(pilot.setup().expected(1).only(LapAssist.SERVER_REASONS));
                }
            }
            return fly(server, registry, course, pilots);
        } catch (RuntimeException e) {
            return "Ausnahme: " + e;
        } finally {
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, savedGravity);
            WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, savedRule);
            for (Pilot pilot : pilots) {
                pilot.drone().setPilot(null);
                pilot.drone().discard();
                RaceServer.onLeave(pilot.player());
            }
            for (GateEntity gate : gates) {
                gate.discard();
            }
            if (courseId != GateEntity.NO_COURSE) {
                registry.deleteCourse(courseId);
            }
            forceChunks(server, false);
        }
    }

    private static @Nullable String fly(MinecraftServer server, CourseRegistry registry, CourseRegistry.Course course, List<Pilot> pilots) {
        Leaderboard board = registry.board(course);
        List<GateGeometry> geometry = course.toData().geometry();
        long base = 6_000L * MS;
        for (Pilot pilot : pilots) {
            base = Math.max(base, RaceServer.minimumLapNanos(course, pilot.player()) * 3L);
        }
        long t = System.nanoTime() + 60_000L * MS;
        for (Pilot pilot : pilots) {
            RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.START, course.id, 0, 0L, AssistReasons.NONE), pilot.player(), t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return pilot.setup().name + ": Start abgelehnt: " + verdict;
            }
        }
        long lapStart = t + 100L * MS;
        List<String> lines = new ArrayList<>();
        for (int lap = 1; lap <= LAPS; lap++) {
            long lapNanos = base - (lap - 1) * 300L * MS;
            long end = lapStart + lapNanos;
            if (lap == PHYSICS_LAP) {
                WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, GRAVITY);
            }
            crossAll(pilots, geometry.getFirst(), lapStart);
            boolean reset = false;
            for (long now = lapStart + TICK; now < end - 400L * MS; now += TICK) {
                if (lap == PHYSICS_LAP && !reset && now >= end - 1_000L * MS) {
                    WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
                    reset = true;
                }
                for (int i = 0; i < pilots.size(); i++) {
                    Pilot pilot = pilots.get(i);
                    RaceServer.observe(pilot.player(), RaceServer.serverReasons(pilot.drone()), now + i * MS);
                }
                if (now - lapStart < lapNanos / 2 && now + TICK - lapStart >= lapNanos / 2) {
                    crossAll(pilots, geometry.get(1), now + TICK);
                }
            }
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
            crossAll(pilots, geometry.getFirst(), end);
            for (Pilot pilot : pilots) {
                RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, course.id, lap, lapNanos, pilot.setup().client(lap)),
                        pilot.player(), end + 100L * MS);
                if (verdict != RaceServer.Verdict.ACCEPTED) {
                    return pilot.setup().name + ": Runde " + lap + " abgelehnt: " + verdict;
                }
                AssistReasons expected = pilot.setup().expected(lap);
                UUID uuid = pilot.player().getUUID();
                if (expected.none()) {
                    Leaderboard.Entry best = board.best(uuid, null);
                    if (best == null || best.nanos() != lapNanos || best.assisted()) {
                        return pilot.setup().name + ": Runde " + lap + " ohne Flughilfe nicht normal gewertet: " + best;
                    }
                } else {
                    Leaderboard.Entry best = board.bestAssisted(uuid, null);
                    if (best == null || best.nanos() != lapNanos || !best.reasons().equals(expected)) {
                        return pilot.setup().name + ": Runde " + lap + " mit " + (best == null ? "nichts" : text(best.reasons())) + " statt " + text(expected);
                    }
                    Leaderboard.Entry plain = board.best(uuid, null);
                    if (plain != null && plain.nanos() == lapNanos) {
                        return pilot.setup().name + ": Runde " + lap + " mit Flughilfe in der normalen Wertung";
                    }
                }
                lines.add(pilot.setup().name + " R" + lap + "=" + (expected.none() ? "sauber" : text(expected)));
            }
            lapStart = end;
        }
        summary = String.join(", ", lines);
        return null;
    }

    private static void crossAll(List<Pilot> pilots, GateGeometry gate, long nanos) {
        Vec3 center = new Vec3(gate.x(), gate.centerY(), gate.z());
        Vec3 normal = new Vec3(gate.normalX(), 0.0, gate.normalZ());
        for (int i = 0; i < pilots.size(); i++) {
            Pilot pilot = pilots.get(i);
            RaceServer.onDroneMoved(pilot.drone(), pilot.player(), center.subtract(normal), center.add(normal), nanos);
        }
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos, AssistReasons client) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, 0L, RacePayloads.RaceReportC2S.assistFlags(client.mask()), 0L,
                client.details());
    }

    private static GateEntity gate(ServerLevel level, double x, double z) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        gate.setSize(GateSize.MEDIUM);
        gate.snapTo(x, top, z, 0.0F, 0.0F);
        if (!level.addFreshEntity(gate)) {
            throw new IllegalStateException("Gate nicht gespawnt");
        }
        return gate;
    }

    public static String text(AssistReasons reasons) {
        if (reasons.none()) {
            return "keine";
        }
        List<String> out = new ArrayList<>();
        for (AssistDetail detail : reasons.details()) {
            out.add(detail.reason().key() + ":" + detail.subject() + (detail.value() != 0 || detail.reference() != 0
                    ? String.format(Locale.ROOT, "=%d/%d", detail.value(), detail.reference()) : ""));
        }
        return String.join("+", out);
    }
}

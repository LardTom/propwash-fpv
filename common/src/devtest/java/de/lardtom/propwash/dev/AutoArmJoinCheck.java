package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AutoArmJoinCheck {
    public static final String RESULT = "[auto-arm] ";
    public static final int SETTLE_TICKS = 60;
    public static final int AWAY = 24;
    public static final double HOME_BEFORE_START = 6.0;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int built = -1;
        int course = GateEntity.NO_COURSE;
        int droneId = -1;
        long lastReset = Long.MIN_VALUE;
        int resets;
        int resetTick;
        boolean waiting;
        boolean lobbySeen;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private AutoArmJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (track.built < 0) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    build(player, track, tick);
                }
                continue;
            }
            if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
                continue;
            }
            if (track.course != GateEntity.NO_COURSE && (RaceServer.lobby(track.course) != null || RaceServer.heat(track.course) != null)) {
                track.lobbySeen = true;
            }
            long last = drone.lastHomeResetTick();
            if (last != track.lastReset && last != Long.MIN_VALUE) {
                track.lastReset = last;
                track.resets++;
                track.resetTick = tick;
                track.waiting = true;
                track.lobbySeen = false;
                Constants.LOG.info("[Beitrittstest] Auto-Arm: Reset {} am Server, Drohne bei {}, scharf {}", track.resets, drone.position(), drone.isArmed());
            }
            if (track.waiting && drone.isArmed()) {
                track.waiting = false;
                say(player, "armed after reset " + track.resets + " ticks=" + (tick - track.resetTick) + " lobby=" + track.lobbySeen
                        + " home=" + (drone.homePoint() != null && drone.position().distanceTo(new Vec3(drone.homePoint().x(), drone.homePoint().y(),
                        drone.homePoint().z())) < 1.5));
            }
            if (!track.waiting && track.resets > 0 && track.lobbySeen) {
                track.lobbySeen = false;
                say(player, "lobby after reset " + track.resets);
            }
        }
    }

    private static void say(ServerPlayer player, String text) {
        Constants.LOG.info("[Beitrittstest] Auto-Arm: {}", text);
        player.sendSystemMessage(Component.literal(RESULT + text));
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

    private static void build(ServerPlayer player, Track track, int tick) {
        track.built = tick;
        ServerLevel level = player.level();
        MinecraftServer server = level.getServer();
        int x = player.getBlockX() - AWAY;
        int z = player.getBlockZ();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        try {
            WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 1);
            GateEntity start = gate(level, new Vec3(x + 0.5, ground, z + 0.5));
            int groundNext = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z + 14);
            GateEntity second = gate(level, new Vec3(x + 0.5, groundNext, z + 14.5));
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            CourseToolItem.useOnGate(tool, player, start);
            CourseToolItem.useOnGate(tool, player, second);
            track.course = CourseToolItem.courseOf(tool);
            CourseRegistry registry = CourseRegistry.get();
            CourseRegistry.Course course = registry == null ? null : registry.course(track.course);
            if (course == null || course.gates.size() != 2) {
                Constants.LOG.error("[Beitrittstest] Auto-Arm: Kurs nicht angelegt");
                return;
            }
            int homeGround = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, (int) Math.floor(z + 0.5 - HOME_BEFORE_START));
            Vec3 home = new Vec3(x + 0.5, homeGround + 0.05, z + 0.5 - HOME_BEFORE_START);
            player.teleportTo(level, home.x + 3.0, homeGround, home.z - 3.0, Set.of(), 0.0F, 0.0F, true);
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                Constants.LOG.error("[Beitrittstest] Auto-Arm: Drohne nicht erzeugt");
                return;
            }
            drone.snapTo(home.x, home.y, home.z, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
            drone.setBuild(DroneBuild.FREESTYLE);
            drone.setBatteryCharge(1.0F);
            level.addFreshEntity(drone);
            drone.setHomePoint(new HomePoint(home.x, home.y, home.z, HomePointCodecs.dimension(level), level.getGameTime(), HomePoint.Source.MANUAL));
            track.droneId = drone.getId();
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.getInventory().setItem(0, remote);
            player.getInventory().setSelectedSlot(0);
            player.connection.send(new ClientboundSetHeldSlotPacket(0));
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
            Constants.LOG.info("[Beitrittstest] Auto-Arm: Kurs {} mit Start-Gate bei {} {} {}, Homepoint {} Blöcke davor, FPV {}", track.course, x, ground, z,
                    HOME_BEFORE_START, result);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] Auto-Arm: Bau fehlgeschlagen", e);
        }
    }
}

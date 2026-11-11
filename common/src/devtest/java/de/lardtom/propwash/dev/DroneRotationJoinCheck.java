package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class DroneRotationJoinCheck {
    public static final int BEFORE_TICK = 200;
    public static final int SETTLE_TICKS = 60;
    public static final int STEP_TICKS = 60;
    public static final String NAME = "propwash-rotation";
    public static final String BEFORE_NAME = NAME + " vorher";
    public static final float FIRST_YAW = -90.0F;
    public static final float TURNED_YAW = 45.0F;
    public static final double LIFT = 0.2;
    public static final double AHEAD = 3.0;
    public static final double BEFORE_DX = 3.5;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static boolean beforeDone;

    private static final class Track {
        final int joined;
        int stage;
        int mark;
        int droneId = -1;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private DroneRotationJoinCheck() {
    }

    public static String name(String player) {
        return NAME + " " + player;
    }

    static void tick(MinecraftServer server, int tick) {
        if (!beforeDone && tick >= BEFORE_TICK) {
            beforeDone = true;
            try {
                before(server);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Lage per /tp und /rotate: Drohne vor dem Beitritt fehlgeschlagen", e);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            try {
                step(server, player, track, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Lage per /tp und /rotate: Stufe {} fehlgeschlagen", track.stage, e);
                track.stage = 5;
            }
        }
    }

    private static void before(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        level.setChunkForced(spawn.getX() >> 4, spawn.getZ() >> 4, true);
        double x = spawn.getX() + BEFORE_DX;
        double z = spawn.getZ() + 0.5;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        QuadcopterEntity drone = create(level, BEFORE_NAME, new Vec3(x, y, z));
        String command = "tp " + drone.getStringUUID() + " ~ ~ ~ " + (int) FIRST_YAW + " 0";
        int done = run(server, drone.position(), command);
        Constants.LOG.info("[Beitrittstest] Lage per /tp und /rotate: vor jedem Beitritt (Spieler online: {}) Drohne {} am Spawn mit Gier 0 "
                + "erzeugt, /{} → {}, Server-Lage {}", server.getPlayerList().getPlayerCount(), drone.getId(), command, done, describe(drone));
    }

    private static void step(MinecraftServer server, ServerPlayer player, Track track, int tick) {
        switch (track.stage) {
            case 0 -> {
                if (tick - track.joined < SETTLE_TICKS) {
                    return;
                }
                ServerLevel level = player.level();
                Vec3 look = player.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0.0, look.z);
                Vec3 ahead = flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, -1.0) : flat.normalize();
                double x = player.getX() + ahead.x * AHEAD;
                double z = player.getZ() + ahead.z * AHEAD;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                QuadcopterEntity drone = create(level, name(player.getPlainTextName()), new Vec3(x, y, z));
                track.droneId = drone.getId();
                Constants.LOG.info("[Beitrittstest] Lage per /tp und /rotate: Drohne {} vor {} mit Gier 0 erzeugt", drone.getId(),
                        player.getPlainTextName());
                track.stage = 1;
                track.mark = tick;
            }
            case 1 -> command(server, player, track, tick, "~ ~ ~ " + (int) FIRST_YAW + " 0", false);
            case 2 -> command(server, player, track, tick, (int) TURNED_YAW + " 0", true);
            case 3 -> command(server, player, track, tick, String.format(Locale.ROOT, "~ ~%.1f ~", LIFT), false);
            default -> {
            }
        }
    }

    private static void command(MinecraftServer server, ServerPlayer player, Track track, int tick, String arguments, boolean rotate) {
        if (tick - track.mark < STEP_TICKS) {
            return;
        }
        if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne " + track.droneId + " verschwunden");
        }
        String command = (rotate ? "rotate " : "tp ") + drone.getStringUUID() + " " + arguments;
        int done = run(server, drone.position(), command);
        Constants.LOG.info("[Beitrittstest] Lage per /tp und /rotate: /{} → {}, Server-Lage {}", command, done, describe(drone));
        track.stage++;
        track.mark = tick;
    }

    private static QuadcopterEntity create(ServerLevel level, String name, Vec3 at) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setCustomName(Component.literal(name));
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return drone;
    }

    private static String describe(QuadcopterEntity drone) {
        return String.format(Locale.ROOT, "Gier %.1f, Vanilla-Blick %.1f/%.1f", QuadcopterEntity.yawDegrees(drone.orientation()), drone.getYRot(),
                drone.getXRot());
    }

    private static int run(MinecraftServer server, Vec3 at, String command) {
        AtomicInteger result = new AtomicInteger(-1);
        CommandSourceStack source = server.createCommandSourceStack().withPosition(at).withSuppressedOutput()
                .withCallback((ok, value) -> result.set(ok ? value : 0));
        server.getCommands().performPrefixedCommand(source, command);
        return result.get();
    }
}

package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class DroneScaleJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int CHANGE_TICKS = 100;
    public static final float SUMMONED = 3.0F;
    public static final float CHANGED = 0.25F;
    public static final double AHEAD = 3.0;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int stage;
        int mark;
        int droneId = -1;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private DroneScaleJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            try {
                step(server, player, track, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Skalierung: Stufe {} fehlgeschlagen", track.stage, e);
                track.stage = 3;
            }
        }
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
                String summon = String.format(Locale.ROOT, "summon propwash:drone %.3f %d.02 %.3f {attributes:[{id:\"minecraft:scale\",base:%.1fd}]}", x,
                        y, z, SUMMONED);
                boolean ok = run(server, new Vec3(x, y, z), summon);
                List<QuadcopterEntity> near = level.getEntitiesOfClass(QuadcopterEntity.class, player.getBoundingBox().inflate(AHEAD + 4.0),
                        drone -> drone.scale() != 1.0F);
                near.sort(Comparator.comparingDouble(drone -> drone.distanceToSqr(x, y, z)));
                if (!ok || near.isEmpty()) {
                    Constants.LOG.error("[Beitrittstest] Skalierung: Beschwören mit scale {} scheitert ({})", SUMMONED, summon);
                    track.stage = 3;
                    return;
                }
                QuadcopterEntity drone = near.getFirst();
                track.droneId = drone.getId();
                track.stage = 1;
                track.mark = tick;
                Constants.LOG.info("[Beitrittstest] Skalierung: Drohne {} mit scale {} vor {} beschworen, Hitbox {} × {}", drone.getId(), drone.scale(),
                        player.getPlainTextName(), drone.getBbWidth(), drone.getBbHeight());
            }
            case 1 -> {
                if (tick - track.mark < CHANGE_TICKS) {
                    return;
                }
                if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
                    Constants.LOG.error("[Beitrittstest] Skalierung: Drohne {} verschwunden", track.droneId);
                    track.stage = 3;
                    return;
                }
                String command = String.format(Locale.ROOT, "attribute @e[type=propwash:drone,limit=1,sort=nearest] minecraft:scale base set %s",
                        CHANGED);
                boolean ok = run(server, drone.position(), command);
                track.stage = 2;
                track.mark = tick;
                Constants.LOG.info("[Beitrittstest] Skalierung: {} → {}", command, ok ? "ausgeführt" : "abgelehnt");
            }
            case 2 -> {
                if (tick - track.mark == 5 && player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone) {
                    Constants.LOG.info("[Beitrittstest] Skalierung: Server-Drohne jetzt scale {}, Hitbox {} × {}", drone.scale(), drone.getBbWidth(),
                            drone.getBbHeight());
                }
            }
            default -> {
            }
        }
    }

    private static boolean run(MinecraftServer server, Vec3 at, String command) {
        AtomicBoolean success = new AtomicBoolean();
        AtomicInteger result = new AtomicInteger();
        CommandSourceStack source = server.createCommandSourceStack().withPosition(at).withSuppressedOutput().withCallback((ok, value) -> {
            success.set(ok);
            result.set(value);
        });
        server.getCommands().performPrefixedCommand(source, command);
        return success.get() && result.get() > 0;
    }
}

package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.example.ExampleMission;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AddonDataJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int UPDATE_TICKS = 100;
    public static final String FIRST = "join-1";
    public static final String SECOND = "join-2";
    public static final int SECRET_CHANGES = 42;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int spawned = -1;
        boolean updated;
        @Nullable QuadcopterEntity drone;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private AddonDataJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        if (ExampleMission.mission() == null) {
            if (TRACKS.isEmpty()) {
                TRACKS.put(new UUID(0L, 0L), new Track(tick));
                Constants.LOG.error("[Beitrittstest] Add-on-Daten: Beispiel-Add-on am Server aus, keine Schlüssel");
            }
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (track.spawned < 0 && tick - track.joined >= SETTLE_TICKS) {
                spawn(player, track, tick);
            } else if (track.spawned >= 0 && !track.updated && tick - track.spawned >= UPDATE_TICKS) {
                track.updated = true;
                QuadcopterEntity drone = track.drone;
                if (drone == null || drone.isRemoved()) {
                    Constants.LOG.error("[Beitrittstest] Add-on-Daten: Drohne von {} verschwunden", player.getPlainTextName());
                    continue;
                }
                ExampleMission.Mission next = new ExampleMission.Mission(SECOND, PropwashApi.droneData().get(drone, ExampleMission.mission()).waypoints(), 2);
                PropwashApi.droneData().set(drone, ExampleMission.mission(), next);
                Constants.LOG.info("[Beitrittstest] Add-on-Daten: Mission von {} geändert auf {} ({} Bytes an der Drohne)", player.getPlainTextName(),
                        SECOND, drone.addonData().bytes());
            }
        }
    }

    private static void spawn(ServerPlayer player, Track track, int tick) {
        track.spawned = tick;
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            Constants.LOG.error("[Beitrittstest] Add-on-Daten: Drohne nicht erzeugt");
            return;
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.RACER);
        drone.loadFromStack(stack);
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
        Vec3 pos = player.position().add(look.scale(3.0)).add(0.0, 0.5, 0.0);
        drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(drone);
        track.drone = drone;
        PropwashApi.droneData().set(drone, ExampleMission.mission(), ExampleMission.around(drone.blockPosition(), FIRST));
        PropwashApi.droneData().set(drone, ExampleMission.log(), new ExampleMission.ServiceLog(SECRET_CHANGES, Optional.of(player.getUUID())));
        Constants.LOG.info("[Beitrittstest] Add-on-Daten: Drohne {} bei {} mit Mission {} (TRACKING) und Protokoll {} Wechsel (NONE), Abgleich {}",
                drone.getId(), drone.blockPosition().toShortString(), FIRST, SECRET_CHANGES, drone.addonData().ids());
    }
}

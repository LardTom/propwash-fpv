package de.lardtom.propwash.drone.operator;

import de.lardtom.propwash.drone.LoadedDrones;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.video.SpectateServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class AutonomousFeeds {
    public static final int INTERVAL_TICKS = 10;
    public static final String FALLBACK_NAME = "AUTO";

    private static final Set<QuadcopterEntity> FEEDS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int timer;

    private AutonomousFeeds() {
    }

    public static synchronized boolean feeding(QuadcopterEntity drone) {
        return FEEDS.contains(drone);
    }

    static synchronized void onTick(MinecraftServer server) {
        if (++timer < INTERVAL_TICKS) {
            return;
        }
        timer = 0;
        FEEDS.removeIf(drone -> {
            boolean gone = drone.isRemoved() || !AddonAutonomy.flying(drone) || drone.pilot().isPresent();
            if (gone) {
                SpectateServer.simulateTransmitter(drone, null);
            }
            return gone;
        });
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : LoadedDrones.of(level)) {
                if (entity instanceof QuadcopterEntity drone && !FEEDS.contains(drone) && AddonAutonomy.flying(drone) && drone.pilot().isEmpty()) {
                    FEEDS.add(drone);
                    SpectateServer.simulateTransmitter(drone, AddonAutonomy.label(drone).filter(label -> !label.isBlank()).orElse(FALLBACK_NAME));
                }
            }
        }
    }

    static synchronized void clear() {
        FEEDS.clear();
        timer = 0;
    }
}

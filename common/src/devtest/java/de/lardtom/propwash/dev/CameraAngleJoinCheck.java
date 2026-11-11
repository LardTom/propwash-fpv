package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CameraAngleJoinCheck {
    private static final Map<UUID, Float> SEEN = new HashMap<>();

    private CameraAngleJoinCheck() {
    }

    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuadcopterEntity drone = DronePiloting.pilotedBy(player.getUUID()).orElse(null);
            if (drone == null) {
                continue;
            }
            float angle = CameraMount.uptilt(drone.build(), drone.tune().values());
            Float before = SEEN.put(drone.getUUID(), angle);
            if (before == null || before != angle) {
                CameraMount.Range range = CameraMount.range(drone.build());
                Constants.LOG.info("[Beitrittstest] Kamerawinkel an der Drohne von {}: {}° (gespeichert {}, Halter {}–{}°, Standard {}°)",
                        player.getPlainTextName(), Math.round(angle), drone.tune().values().get(TuneMapping.CAMERA_UPTILT),
                        Math.round(range.minDeg()), Math.round(range.maxDeg()), Math.round(range.defaultDeg()));
            }
        }
    }
}

package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.rescue.net.AutonomyStatusS2C;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class AutonomyLabels {
    private static final Map<Integer, String> LABELS = new HashMap<>();
    private static volatile @Nullable AutonomyStatusS2C last;

    private AutonomyLabels() {
    }

    public static void init() {
        ClientNet.handle(AutonomyStatusS2C.TYPE, (payload, context) -> accept(payload));
        ClientBus.DISCONNECT.register(minecraft -> clear());
    }

    static void accept(AutonomyStatusS2C payload) {
        last = payload;
        if (payload.active()) {
            LABELS.put(payload.entityId(), payload.label());
        } else {
            LABELS.remove(payload.entityId());
        }
    }

    public static @Nullable String label(@Nullable Entity drone) {
        if (drone == null) {
            return null;
        }
        String label = LABELS.get(drone.getId());
        return label == null || label.isBlank() ? null : label;
    }

    public static boolean active(@Nullable Entity drone) {
        return drone != null && LABELS.containsKey(drone.getId());
    }

    public static @Nullable AutonomyEnd lastEnd(int entityId) {
        AutonomyStatusS2C status = last;
        return status == null || status.entityId() != entityId ? null : status.end();
    }

    static void clear() {
        LABELS.clear();
        last = null;
    }
}

package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.SpectatorCount;
import de.lardtom.propwash.osd.SpectatorCountS2C;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class SpectatorCountClient {
    private static volatile int entityId = -1;
    private static volatile int count;
    private static volatile int received;
    private static boolean initialized;

    private SpectatorCountClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientNet.handle(SpectatorCountS2C.TYPE, (payload, context) -> accept(payload));
        ClientBus.DISCONNECT.register(minecraft -> clear());
    }

    public static void accept(SpectatorCountS2C payload) {
        entityId = payload.entityId();
        count = SpectatorCount.clamp(payload.count());
        received++;
    }

    public static int count(@Nullable Entity drone) {
        return drone != null && drone.getId() == entityId ? count : 0;
    }

    public static int received() {
        return received;
    }

    public static void clear() {
        entityId = -1;
        count = 0;
        received = 0;
    }
}

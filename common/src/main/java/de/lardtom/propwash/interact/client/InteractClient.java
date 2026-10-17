package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.interact.net.DroneBreakClearS2C;
import de.lardtom.propwash.interact.net.DroneBreakDeniedS2C;
import de.lardtom.propwash.network.client.ClientNet;
import net.minecraft.client.Minecraft;

public final class InteractClient {
    private static boolean initialized;

    private InteractClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CargoKeys.bootstrap();
        ClientBus.TICK_END.register(InteractClient::onClientTick);
        QuadcopterEntity.CLIENT_TICK.register(SnagEffects::onDroneTick);
        ClientBus.DISCONNECT.register(mc -> {
            PilotInteraction.INSTANCE.reset();
            PilotBreaks.INSTANCE.forget();
        });
        ClientNet.handle(DroneBreakDeniedS2C.TYPE, (payload, context) -> PilotBreaks.INSTANCE.onDenied(payload));
        ClientNet.handle(DroneBreakClearS2C.TYPE, (payload, context) -> PilotBreaks.INSTANCE.onClearance(payload));
    }

    private static void onClientTick(Minecraft mc) {
        boolean active = PilotSession.INSTANCE.isActive();
        while (CargoKeys.DROP.consumeClick()) {
            if (active) {
                PilotInteraction.INSTANCE.requestDrop();
            }
        }
    }
}

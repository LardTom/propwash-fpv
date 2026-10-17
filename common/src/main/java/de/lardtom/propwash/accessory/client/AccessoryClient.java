package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.accessory.AccessoryModule;
import de.lardtom.propwash.accessory.DronePushes;
import de.lardtom.propwash.accessory.net.DronePushS2C;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.sim.DroneSim;

public final class AccessoryClient {
    private static boolean initialized;
    private static int pushesApplied;

    private AccessoryClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AccessoryActionInput.bootstrap();
        ClientRegistrar.menuScreen(AccessoryModule.ACCESSORY_MENU, AccessoryScreen::new);
        ClientRegistrar.hudLayer(AddonHud.ID, AddonHud::extract);
        OsdHud.setReplacement(AddonHud::replacesOsd);
        DronePushes.setLocalApplier(AccessoryClient::applyLocal);
        ClientNet.handle(DronePushS2C.TYPE, (payload, context) -> onPush(payload));
        ClientBus.TICK_END.register(AccessoryActionInput.INSTANCE::tick);
        ClientBus.DISCONNECT.register(minecraft -> {
            AccessoryActionInput.INSTANCE.reset();
            AddonFlightClient.INSTANCE.end();
        });
    }

    public static int pushesApplied() {
        return pushesApplied;
    }

    private static void onPush(DronePushS2C payload) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null || drone.getId() != payload.droneId()) {
            return;
        }
        applyLocal(drone, DronePushes.fromPayload(payload));
    }

    private static boolean applyLocal(QuadcopterEntity drone, DronePushes.Push push) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (!session.isActive() || session.drone() != drone || sim == null) {
            return false;
        }
        boolean applied = DronePushes.applyToSim(sim, push);
        if (applied) {
            pushesApplied++;
        }
        return applied;
    }
}

package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.net.AddonAxisC2S;
import de.lardtom.propwash.input.net.AddonFeaturesS2C;
import de.lardtom.propwash.input.net.AddonSwitchC2S;
import de.lardtom.propwash.network.PropwashNet;

public final class AddonSwitchModule {
    private static boolean initialized;

    private AddonSwitchModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerC2S(AddonSwitchC2S.TYPE, AddonSwitchC2S.CODEC, AddonSwitchServer::onSwitch);
        PropwashNet.registerC2S(AddonAxisC2S.TYPE, AddonAxisC2S.CODEC, AddonSwitchServer::onAxis);
        PropwashNet.registerS2C(AddonFeaturesS2C.TYPE, AddonFeaturesS2C.CODEC);
        ServerBus.PLAYER_JOIN.register(AddonSwitchServer::joined);
        ServerBus.PLAYER_LEAVE.register(AddonSwitchServer::left);
        ServerBus.STARTED.register(server -> AddonSwitchServer.clear());
        ServerBus.STOPPED.register(server -> AddonSwitchServer.clear());
        QuadcopterEntity.PILOT_CHANGED.register((drone, previous, current) -> {
            if (!drone.level().isClientSide()) {
                AddonInputState.clear(drone);
            }
        });
    }
}

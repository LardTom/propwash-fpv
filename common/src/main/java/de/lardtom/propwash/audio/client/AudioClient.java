package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.ui.client.config.ConfigTabs;

public final class AudioClient {
    private static boolean initialized;

    private AudioClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AudioConfig.CONFIG.load();
        QuadcopterEntity.CLIENT_TICK.register(AudioHooks::onDroneClientTick);
        ClientBus.TICK_END.register(MotorVoices::onTickEnd);
        ClientBus.TICK_END.register(LocatorBeeper::onTickEnd);
        ClientBus.FRAME_START.register(MotorVoices::onFrame);
        ClientBus.DISCONNECT.register(minecraft -> MotorVoices.clear());
        ConfigTabs.register(AudioConfigTab::new);
    }
}

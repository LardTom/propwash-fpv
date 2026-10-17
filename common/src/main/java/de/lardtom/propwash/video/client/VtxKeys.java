package de.lardtom.propwash.video.client;

import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;

public final class VtxKeys {
    public static final KeyMapping STEP = ClientRegistrar.keyMapping(KeyDefaults.VTX_POWER.name(), KeyDefaults.VTX_POWER.key());

    private VtxKeys() {
    }

    public static void bootstrap() {
    }
}

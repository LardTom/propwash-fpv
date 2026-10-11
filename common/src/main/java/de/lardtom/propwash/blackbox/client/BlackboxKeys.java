package de.lardtom.propwash.blackbox.client;

import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;

public final class BlackboxKeys {
    public static final KeyMapping TOGGLE = ClientRegistrar.keyMapping(KeyDefaults.BLACKBOX.name(), KeyDefaults.BLACKBOX.key());

    private BlackboxKeys() {
    }

    public static void bootstrap() {
    }
}

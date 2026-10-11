package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;

public final class CargoKeys {
    public static final KeyMapping DROP = ClientRegistrar.keyMapping(KeyDefaults.CARGO_DROP.name(), KeyDefaults.CARGO_DROP.key());

    private CargoKeys() {
    }

    public static void bootstrap() {
    }
}

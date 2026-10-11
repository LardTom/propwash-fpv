package de.lardtom.propwash.rescue.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;

public final class RescueKeys {
    public static final KeyMapping RESCUE = ClientRegistrar.keyMapping("rescue", InputConstants.KEY_J);
    public static final KeyMapping SET_HOME = ClientRegistrar.keyMapping("set_home", InputConstants.KEY_COMMA);

    private RescueKeys() {
    }

    public static void bootstrap() {
    }
}

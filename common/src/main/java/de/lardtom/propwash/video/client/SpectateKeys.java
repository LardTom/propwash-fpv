package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;

public final class SpectateKeys {
    public static final String NAME = "video_spectate";
    public static final int DEFAULT_KEY = InputConstants.KEY_F7;
    public static final KeyMapping CYCLE = ClientRegistrar.keyMapping(NAME, DEFAULT_KEY);

    private SpectateKeys() {
    }

    public static void bootstrap() {
    }
}

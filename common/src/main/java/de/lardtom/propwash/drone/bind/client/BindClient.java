package de.lardtom.propwash.drone.bind.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.bind.BindTooltips;
import de.lardtom.propwash.drone.bind.ReceiverBinds;
import de.lardtom.propwash.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.nio.file.Path;

public final class BindClient {
    public static final String LEGACY_CONFIG_FILE = "binding.json";

    private static boolean initialized;

    private BindClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ReceiverBinds.dropLegacyFile(legacyConfigFile());
        BindTooltips.VIEWER = () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            return player == null ? null : new BindTooltips.Viewer(player.getUUID());
        };
    }

    public static Path legacyConfigFile() {
        return Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID).resolve(LEGACY_CONFIG_FILE);
    }
}

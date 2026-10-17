package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.ui.HintMemory;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public final class FirstUseHints {
    public static final String NAME = "hints";
    public static final JsonConfig<HintMemory.Seen> CONFIG = JsonConfig.create(NAME, HintMemory.Seen.class, HintMemory.Seen::new,
            HintMemory::sanitize);

    private FirstUseHints() {
    }

    private static HintMemory.Seen seen() {
        if (!CONFIG.isLoaded()) {
            CONFIG.load();
        }
        return CONFIG.get();
    }

    public static String playerKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            return minecraft.player.getUUID().toString();
        }
        UUID profile = minecraft.getUser().getProfileId();
        return profile == null ? "local" : profile.toString();
    }

    public static int count(String hint) {
        return HintMemory.count(seen(), playerKey(), hint);
    }

    public static boolean fresh(String hint, int times) {
        return HintMemory.fresh(seen(), playerKey(), hint, times);
    }

    public static void shown(String hint, int times) {
        if (HintMemory.shown(seen(), playerKey(), hint, times)) {
            CONFIG.save();
        }
    }

    public static void learned(String hint, int times) {
        if (HintMemory.learned(seen(), playerKey(), hint, times)) {
            CONFIG.save();
        }
    }

    public static void forget(String hint) {
        if (HintMemory.forget(seen(), playerKey(), hint)) {
            CONFIG.save();
        }
    }
}

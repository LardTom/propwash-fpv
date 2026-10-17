package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMode;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class StickModes {
    private StickModes() {
    }

    public static StickMode current() {
        return InputConfig.get().stickMode();
    }

    public static String variant(String base) {
        return variant(base, current());
    }

    public static String variant(String base, StickMode mode) {
        String key = mode.variantKey(base);
        return key.equals(base) || Language.getInstance().has(key) ? key : base;
    }

    public static MutableComponent translatable(String base, Object... args) {
        return Component.translatable(variant(base), args);
    }

    public static Component name(StickMode mode) {
        return Component.translatable(mode.translationKey());
    }

    public static Component layout(StickMode mode) {
        return Component.translatable(mode.layoutKey());
    }

    public static Component prompt(StickChannel channel, StickMode mode) {
        return Component.translatable(mode.sideKey(channel.promptKey(), channel));
    }
}

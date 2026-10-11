package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class KeyNames {
    private static final String KEYBOARD = "key.keyboard.";
    private static final String NAMES = "propwash.input.keyname.";

    private KeyNames() {
    }

    public static Component of(KeyMapping mapping) {
        Component shown = mapping.getTranslatedKeyMessage();
        String key = spelledOut(shown.getString(), mapping.saveString());
        return key != null && Language.getInstance().has(key) ? Component.translatable(key) : shown;
    }

    public static Component of(InputConstants.Key key) {
        Component shown = key.getDisplayName();
        String name = spelledOut(shown.getString(), key.getName());
        return name != null && Language.getInstance().has(name) ? Component.translatable(name) : shown;
    }

    static @Nullable String spelledOut(String shown, @Nullable String name) {
        if (shown.codePointCount(0, shown.length()) != 1 || Character.isLetterOrDigit(shown.codePointAt(0))) {
            return null;
        }
        if (name == null || !name.startsWith(KEYBOARD)) {
            return null;
        }
        return NAMES + name.substring(KEYBOARD.length());
    }
}

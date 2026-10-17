package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import org.jspecify.annotations.Nullable;

public final class KeyNames {
    private static final String KEYBOARD = "key.keyboard.";
    private static final String NAMES = "propwash.input.keyname.";
    private static final String OPTION = ".option";
    public static final FontDescription KEY_FONT = new FontDescription.Resource(Constants.id("keys"));

    private KeyNames() {
    }

    public static Component of(KeyMapping mapping) {
        return named(mapping.getTranslatedKeyMessage(), mapping.saveString());
    }

    public static Component of(InputConstants.Key key) {
        return named(key.getDisplayName(), key.getName());
    }

    private static Component named(Component shown, @Nullable String name) {
        String symbol = name == null ? null : withSymbol(InputQuirks.keyboardTranslationKey(name));
        if (symbol != null && Language.getInstance().has(symbol)) {
            return Component.translatable(symbol).withStyle(style -> style.withFont(KEY_FONT));
        }
        String key = spelledOut(shown.getString(), name);
        return key != null && Language.getInstance().has(key) ? Component.translatable(key) : shown;
    }

    static @Nullable String withSymbol(@Nullable String translationKey) {
        if (translationKey == null || !translationKey.startsWith(KEYBOARD) || !translationKey.endsWith(OPTION)) {
            return null;
        }
        return NAMES + translationKey.substring(KEYBOARD.length());
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

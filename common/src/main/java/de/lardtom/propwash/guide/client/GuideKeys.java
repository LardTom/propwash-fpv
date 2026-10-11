package de.lardtom.propwash.guide.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class GuideKeys {
    public static final String NAME = "flight_school";
    public static final int DEFAULT_KEY = InputConstants.KEY_M;

    private static @Nullable KeyMapping open;

    private GuideKeys() {
    }

    static void register() {
        if (open == null) {
            open = ClientRegistrar.keyMapping(NAME, DEFAULT_KEY);
        }
    }

    public static @Nullable KeyMapping mapping() {
        return open;
    }

    public static InputConstants.Key boundKey() {
        KeyMapping mapping = open;
        if (mapping == null) {
            return InputConstants.UNKNOWN;
        }
        try {
            return InputConstants.getKey(mapping.saveString());
        } catch (IllegalArgumentException e) {
            return InputConstants.UNKNOWN;
        }
    }

    public static boolean held() {
        InputConstants.Key key = boundKey();
        if (key.equals(InputConstants.UNKNOWN)) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            Minecraft minecraft = Minecraft.getInstance();
            return switch (key.getValue()) {
                case InputConstants.MOUSE_BUTTON_LEFT -> minecraft.mouseHandler.isLeftPressed();
                case InputConstants.MOUSE_BUTTON_MIDDLE -> minecraft.mouseHandler.isMiddlePressed();
                case InputConstants.MOUSE_BUTTON_RIGHT -> minecraft.mouseHandler.isRightPressed();
                default -> false;
            };
        }
        return InputConstants.isKeyDown(key.getValue());
    }

    public static boolean matchesKey(int scancode) {
        InputConstants.Key key = boundKey();
        return key.getType() == InputConstants.Type.KEYBOARD && key.getValue() == scancode;
    }

    public static Component label() {
        KeyMapping mapping = open;
        if (mapping == null || mapping.isUnbound()) {
            return Component.translatable("propwash.guide.key.unbound");
        }
        return mapping.getTranslatedKeyMessage();
    }
}

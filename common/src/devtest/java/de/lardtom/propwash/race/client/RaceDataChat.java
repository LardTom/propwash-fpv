package de.lardtom.propwash.race.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RaceDataChat {
    private RaceDataChat() {
    }

    public static List<Component> messages(Minecraft mc) {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(mc.gui.hud.getChat());
            List<Component> out = new ArrayList<>();
            for (Object message : new ArrayList<>(messages)) {
                if (message instanceof GuiMessage gui) {
                    out.add(gui.content());
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    public static Optional<String> line(Minecraft mc, String prefix) {
        for (Component message : messages(mc)) {
            String text = message.getString();
            if (text.startsWith(prefix)) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    public static int copyLinks(Minecraft mc, String key) {
        int count = 0;
        for (Component message : messages(mc)) {
            if (copyLink(message, key)) {
                count++;
            }
        }
        return count;
    }

    public static boolean copyLink(Component message, String key) {
        Optional<Boolean> found = message.visit((style, text) -> text.contains(key) && style.getClickEvent() instanceof ClickEvent.CopyToClipboard(String value)
                && value.equals(key) ? Optional.of(Boolean.TRUE) : Optional.empty(), Style.EMPTY);
        return found.orElse(Boolean.FALSE);
    }

    public static int containing(Minecraft mc, String needle) {
        int count = 0;
        for (Component message : messages(mc)) {
            if (message.getString().contains(needle)) {
                count++;
            }
        }
        return count;
    }
}

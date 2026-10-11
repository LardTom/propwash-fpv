package de.lardtom.propwash.content.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class ThemedPanel {
    public static final int TEXT = 0xFFE6E8EC;
    public static final int MUTED = 0xFF8C949E;
    public static final int ACCENT = 0xFFFFBB00;
    public static final int BAD = 0xFFFF5555;
    public static final int GOOD = 0xFF55FF55;
    public static final int BACKGROUND = 0xFF2B2F36;
    public static final int DARK = 0xFF1B1E23;
    public static final int SLOT = 0xFF14161A;
    public static final int SLOT_EDGE = 0xFF3A3F47;
    public static final int TEXTURE_SIZE = 256;

    private static final Map<Identifier, Boolean> KNOWN = new HashMap<>();

    private ThemedPanel() {
    }

    public static boolean available(Identifier texture) {
        return KNOWN.computeIfAbsent(texture, id -> {
            try {
                return Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    public static void forget() {
        KNOWN.clear();
    }

    public static boolean blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, int width, int height) {
        if (!available(texture)) {
            return false;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height, TEXTURE_SIZE, TEXTURE_SIZE);
        return true;
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, DARK);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, BACKGROUND);
        g.fill(x + 2, y + 2, x + width - 2, y + 15, DARK);
        g.fill(x + 2, y + 15, x + width - 2, y + 16, ACCENT);
    }

    public static void slot(GuiGraphicsExtractor g, int slotX, int slotY) {
        g.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, SLOT_EDGE);
        g.fill(slotX, slotY, slotX + 16, slotY + 16, SLOT);
    }

    public static void inventory(GuiGraphicsExtractor g, int x, int y, int inventoryX, int inventoryY, int hotbarY) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(g, x + inventoryX + column * 18, y + inventoryY + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(g, x + inventoryX + column * 18, y + hotbarY);
        }
    }
}

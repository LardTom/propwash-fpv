package de.lardtom.propwash.guide.client;

import de.lardtom.propwash.guide.HoldTracker;
import de.lardtom.propwash.guide.client.screen.GuideDraw;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class HoldToOpen {
    private static final HoldTracker TRACKER = new HoldTracker();
    private static final int PILL_HEIGHT = 13;
    private static final int SLOT_GAP = 3;
    private static long frames;
    private static long hintFrames;
    private static @Nullable ItemStack testStack;
    private static @Nullable Boolean testKey;
    private static @Nullable Runnable pending;
    private static @Nullable Component tooltipLine;

    private HoldToOpen() {
    }

    public static long frames() {
        return frames;
    }

    public static long hintFrames() {
        return hintFrames;
    }

    public static void testOverride(@Nullable ItemStack stack, @Nullable Boolean keyDown) {
        testStack = stack;
        testKey = keyDown;
        TRACKER.reset();
    }

    public static void containerFrame(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, @Nullable Slot slot, int leftPos, int topPos,
                                      int imageWidth, int mouseX, int mouseY) {
        frames++;
        ItemStack stack = testStack != null ? testStack : slot != null && slot.hasItem() ? slot.getItem() : ItemStack.EMPTY;
        boolean carrying = !screen.getMenu().getCarried().isEmpty();
        List<Scene> scenes = stack.isEmpty() || carrying ? List.of() : GuideOpener.scenesFor(stack);
        boolean keyDown = testKey != null ? testKey : GuideKeys.held() && !textInputFocused(screen);
        String target = scenes.isEmpty() ? "" : GuideOpener.itemId(stack) + "@" + (slot != null ? slot.index : -1);
        HoldTracker.Result result = TRACKER.update(target, !scenes.isEmpty(), keyDown, Util.getMillis());
        tooltipLine = null;
        if (result.hint()) {
            hintFrames++;
            if (slot != null && testStack == null) {
                tooltipLine = hintLine(result.progress());
            } else {
                drawPill(graphics, screen.width / 2 - 8, screen.height / 2 - 8, screen.width, screen.height, result.progress());
            }
        }
        if (result.fire()) {
            ItemStack opened = stack.copy();
            pending = () -> {
                if (Minecraft.getInstance().gui.screen() == screen) {
                    GuideOpener.openFor(opened, screen);
                }
            };
        }
    }

    public static List<Component> withHint(List<Component> lines) {
        Component line = tooltipLine;
        tooltipLine = null;
        if (line == null || lines.isEmpty()) {
            return lines;
        }
        List<Component> out = new ArrayList<>(lines.size() + 1);
        out.addAll(lines);
        out.add(line);
        return out;
    }

    static Component hintLine(float progress) {
        String key = GuideDraw.shortKeyName(GuideKeys.label().getString());
        String text = "[" + key + "] " + Component.translatable("propwash.guide.hold").getString();
        int filled = progress > 0.0F ? Math.round(text.length() * Math.clamp(progress, 0.0F, 1.0F)) : 0;
        int keyEnd = key.length() + 2;
        MutableComponent line = Component.empty();
        for (int i = 0; i < text.length(); ) {
            int end = i < keyEnd ? keyEnd : text.length();
            if (i < filled && filled < end) {
                end = filled;
            }
            int color = i < filled ? Palette.ACCENT : i < keyEnd ? Palette.ACCENT_HOVER : Palette.TEXT_MUTED;
            line.append(Component.literal(text.substring(i, end)).withColor(color & 0xFFFFFF));
            i = end;
        }
        return line;
    }

    static void runPending() {
        Runnable action = pending;
        pending = null;
        if (action != null) {
            action.run();
        }
    }

    private static boolean textInputFocused(AbstractContainerScreen<?> screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused()) {
                return true;
            }
        }
        return false;
    }

    private static void drawPill(GuiGraphicsExtractor g, int slotX, int slotY, int screenWidth, int screenHeight, float progress) {
        Font font = Minecraft.getInstance().font;
        Component label = Component.translatable("propwash.guide.hold");
        String key = GuideDraw.shortKeyName(GuideKeys.label().getString());
        int capWidth = GuideDraw.keycapWidth(font, key);
        int width = 4 + capWidth + 4 + font.width(label) + 5;
        int x0 = Math.clamp(slotX + 8 - width / 2, 2, Math.max(2, screenWidth - width - 2));
        int y0 = Math.clamp(slotY + 16 + SLOT_GAP, 2, Math.max(2, screenHeight - PILL_HEIGHT - 2));
        int x1 = x0 + width;
        int y1 = y0 + PILL_HEIGHT;
        g.nextStratum();
        ThemeDraw.framedBox(g, x0, y0, x1, y1, Palette.TOOLTIP, progress > 0.0F ? Palette.ACCENT_DIM : Palette.BORDER_STRONG);
        if (progress > 0.0F) {
            int fill = x0 + 1 + Math.round((width - 2) * Math.clamp(progress, 0.0F, 1.0F));
            g.fill(x0 + 1, y0 + 1, fill, y1 - 1, Palette.withAlpha(Palette.ACCENT, 0x55));
            g.fill(x0 + 1, y1 - 2, fill, y1 - 1, Palette.ACCENT);
        }
        GuideDraw.keycap(g, font, key, x0 + 4, y0 + 1, PILL_HEIGHT - 2, 0xFF);
        ThemeText.text(g, font, label, x0 + 4 + capWidth + 4, y0 + 3, progress > 0.0F ? Palette.ACCENT : Palette.TEXT);
    }
}

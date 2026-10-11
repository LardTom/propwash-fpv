package de.lardtom.propwash.ui.client.theme;

import de.lardtom.propwash.ui.TooltipPlacement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TooltipScroll {
    public static final String HINT_KEY = "propwash.ui.hint.tooltip_scroll";
    private static final long ACTIVE_MILLIS = 250L;
    private static final int STEP = 3;
    private static final int MIN_ROWS = 4;
    private static int key;
    private static int offset;
    private static int maxOffset;
    private static long shownAt;
    private static int windows;
    private static @Nullable List<ClientTooltipComponent> last;

    private TooltipScroll() {
    }

    public static List<ClientTooltipComponent> window(Font font, List<ClientTooltipComponent> lines, int screenHeight) {
        if (lines == last || lines.size() < MIN_ROWS) {
            return lines;
        }
        int[] heights = new int[lines.size()];
        int total = 0;
        int id = lines.size();
        for (int i = 0; i < heights.length; i++) {
            ClientTooltipComponent line = lines.get(i);
            heights[i] = line.getHeight(font);
            total += heights[i];
            id = 31 * (31 * id + heights[i]) + line.getWidth(font);
        }
        if (!TooltipPlacement.tooTall(total, screenHeight)) {
            return lines;
        }
        long now = Util.getMillis();
        if (id != key || now - shownAt > ACTIVE_MILLIS) {
            offset = 0;
        }
        key = id;
        shownAt = now;
        windows++;
        int room = TooltipPlacement.room(screenHeight) - TooltipPlacement.ROW;
        maxOffset = TooltipPlacement.lastStart(heights, room);
        offset = Math.max(0, Math.min(maxOffset, offset));
        int end = TooltipPlacement.windowEnd(heights, offset, room);
        List<ClientTooltipComponent> shown = new ArrayList<>(lines.subList(offset, end));
        shown.add(ClientTooltipComponent.create(ThemeText.colored(Component.translatable(HINT_KEY, offset + 1, end, lines.size()), Palette.TEXT_FAINT)
                .getVisualOrderText()));
        last = shown;
        return shown;
    }

    public static boolean scroll(double amount) {
        if (amount == 0.0 || Double.isNaN(amount) || maxOffset <= 0 || Util.getMillis() - shownAt > ACTIVE_MILLIS) {
            return false;
        }
        int next = Math.max(0, Math.min(maxOffset, offset + (amount > 0.0 ? -STEP : STEP)));
        if (next == offset) {
            return false;
        }
        offset = next;
        return true;
    }

    public static int windows() {
        return windows;
    }

    public static int offset() {
        return offset;
    }

    public static int maxOffset() {
        return maxOffset;
    }
}

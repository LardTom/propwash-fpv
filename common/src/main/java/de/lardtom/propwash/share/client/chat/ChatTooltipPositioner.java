package de.lardtom.propwash.share.client.chat;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public final class ChatTooltipPositioner implements ClientTooltipPositioner {
    public static final ChatTooltipPositioner INSTANCE = new ChatTooltipPositioner();
    private static final int OFFSET = 12;
    private static final int MARGIN = 4;

    private ChatTooltipPositioner() {
    }

    @Override
    public Vector2ic positionTooltip(int screenWidth, int screenHeight, int x, int y, int tooltipWidth, int tooltipHeight) {
        int left = x + OFFSET;
        if (left + tooltipWidth + MARGIN > screenWidth) {
            left = Math.max(x - OFFSET * 2 - tooltipWidth, MARGIN);
        }
        int top = Math.max(MARGIN, y - OFFSET - tooltipHeight);
        if (top + tooltipHeight + 3 > screenHeight) {
            top = Math.max(MARGIN, screenHeight - tooltipHeight - 3);
        }
        return new Vector2i(left, top);
    }
}

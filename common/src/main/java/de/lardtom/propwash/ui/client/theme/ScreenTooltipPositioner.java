package de.lardtom.propwash.ui.client.theme;

import de.lardtom.propwash.ui.TooltipPlacement;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public record ScreenTooltipPositioner(ClientTooltipPositioner inner) implements ClientTooltipPositioner {
    public static ClientTooltipPositioner of(ClientTooltipPositioner inner) {
        return inner instanceof ScreenTooltipPositioner ? inner : new ScreenTooltipPositioner(inner);
    }

    @Override
    public Vector2ic positionTooltip(int screenWidth, int screenHeight, int x, int y, int tooltipWidth, int tooltipHeight) {
        Vector2ic placed = this.inner.positionTooltip(screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight);
        return new Vector2i(TooltipPlacement.clamp(placed.x(), tooltipWidth, screenWidth), TooltipPlacement.clamp(placed.y(), tooltipHeight, screenHeight));
    }
}

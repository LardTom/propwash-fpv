package de.lardtom.propwash.dev.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public final class ShowcaseInventoryScreen extends InventoryScreen {
    private final int hoveredMenuSlot;

    public ShowcaseInventoryScreen(Player player, int hoveredMenuSlot) {
        super(player);
        this.hoveredMenuSlot = hoveredMenuSlot;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Slot slot = this.getMenu().getSlot(this.hoveredMenuSlot);
        super.extractRenderState(graphics, this.leftPos + slot.x + 8, this.topPos + slot.y + 8, a);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

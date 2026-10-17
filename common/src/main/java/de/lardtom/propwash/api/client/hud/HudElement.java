package de.lardtom.propwash.api.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface HudElement {
    void render(GuiGraphicsExtractor graphics, HudContext context);

    default boolean active(HudContext context) {
        return context.fpv() || context.lineOfSight();
    }

    default boolean replacesOsd(HudContext context) {
        return false;
    }
}

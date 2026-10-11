package de.lardtom.propwash.registry.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface HudLayer {
    void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
}

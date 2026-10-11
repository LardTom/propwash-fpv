package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.jspecify.annotations.Nullable;

public interface GuiLayerAccess {
    GuiRenderState propwash$renderState();

    @Nullable ScreenRectangle propwash$scissor();
}

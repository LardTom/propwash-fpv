package de.lardtom.propwash.input.client.screen.remote;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

public record RemoteDemoRenderState(ItemStackRenderState item, RemoteProjection projection, int x0, int y0, int x1, int y1, float scale,
                                    @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
    public RemoteDemoRenderState(ItemStackRenderState item, RemoteProjection projection, int x0, int y0, int x1, int y1,
                                 @Nullable ScreenRectangle scissorArea) {
        this(item, projection, x0, y0, x1, y1, projection.pixelScale() * 16.0F, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}

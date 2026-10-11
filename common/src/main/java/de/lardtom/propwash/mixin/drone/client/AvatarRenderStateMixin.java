package de.lardtom.propwash.mixin.drone.client;

import de.lardtom.propwash.drone.client.RemoteHoldAccess;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements RemoteHoldAccess {
    @Unique
    private final ItemStackRenderState propwash$remoteHold = new ItemStackRenderState();

    @Override
    public ItemStackRenderState propwash$remoteHold() {
        return this.propwash$remoteHold;
    }
}

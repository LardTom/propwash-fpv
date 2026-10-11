package de.lardtom.propwash.mixin.join.client;

import de.lardtom.propwash.join.JoinClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerImplMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void propwash$beginJoinCheck(Minecraft minecraft, Connection connection, CommonListenerCookie cookie, CallbackInfo ci) {
        JoinClientState.begin(this);
    }
}

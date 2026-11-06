package de.lardtom.propwash.mixin.fabricsync.client;

import de.lardtom.propwash.neoforge.fabricsync.FabricRegistryBridge;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerImplMixin {
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("HEAD"))
    private void propwash$announceAfterServerSwitch(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ClientConfigurationPacketListenerImpl self = (ClientConfigurationPacketListenerImpl) (Object) this;
        if (packet.payload() instanceof MinecraftRegisterPayload
                && self.getConnectionType().isOther()
                && ClientNetworkRegistry.isConnectionInitialized(self.getConnection())
                && FabricRegistryBridge.enabled()) {
            ClientNetworkRegistry.sendInitialListeningChannels(self);
        }
    }
}

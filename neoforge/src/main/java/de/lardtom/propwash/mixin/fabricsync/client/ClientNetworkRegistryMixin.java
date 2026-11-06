package de.lardtom.propwash.mixin.fabricsync.client;

import de.lardtom.propwash.neoforge.fabricsync.FabricRegistryBridge;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientNetworkRegistry.class)
public abstract class ClientNetworkRegistryMixin {
    @Inject(method = "handleModdedPayload", at = @At("HEAD"))
    private static void propwash$classifyEarlyPayload(ClientCommonPacketListener listener, ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (listener instanceof ClientConfigurationPacketListener configuration
                && configuration.getConnectionType().isOther()
                && ChannelAttributes.getPayloadSetup(listener.getConnection()) == null
                && FabricRegistryBridge.enabled()) {
            ClientNetworkRegistry.initializeOtherConnection(configuration);
        }
    }
}

package de.lardtom.propwash.fabric.client;

import de.lardtom.propwash.platform.client.IClientPlatformHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class FabricClientPlatformHelper implements IClientPlatformHelper {
    @Override
    public KeyMapping.Category createKeyCategory(Identifier id) {
        return KeyMapping.Category.register(id);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public boolean sameKeyModifier(KeyMapping mapping, KeyMapping other) {
        return true;
    }
}

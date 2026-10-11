package de.lardtom.propwash.platform.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public interface IClientPlatformHelper {
    KeyMapping.Category createKeyCategory(Identifier id);

    boolean canSendToServer(CustomPacketPayload.Type<?> type);

    void sendToServer(CustomPacketPayload payload);

    boolean sameKeyModifier(KeyMapping mapping, KeyMapping other);
}

package de.lardtom.propwash.neoforge.client;

import de.lardtom.propwash.platform.client.IClientPlatformHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class NeoForgeClientPlatformHelper implements IClientPlatformHelper {
    @Override
    public KeyMapping.Category createKeyCategory(Identifier id) {
        return new KeyMapping.Category(id);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public boolean sameKeyModifier(KeyMapping mapping, KeyMapping other) {
        return mapping.getKeyModifier() == other.getKeyModifier();
    }
}

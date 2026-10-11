package de.lardtom.propwash.fabric.platform;

import de.lardtom.propwash.platform.INetworkHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNetworkHelper implements INetworkHelper {
    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public Packet<? super ClientGamePacketListener> clientboundPacket(CustomPacketPayload payload) {
        return ServerPlayNetworking.createClientboundPacket(payload);
    }
}

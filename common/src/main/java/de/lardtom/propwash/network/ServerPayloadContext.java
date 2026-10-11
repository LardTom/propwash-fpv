package de.lardtom.propwash.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record ServerPayloadContext(ServerPlayer player) {
    public MinecraftServer server() {
        return this.player.level().getServer();
    }

    public ServerLevel level() {
        return this.player.level();
    }
}

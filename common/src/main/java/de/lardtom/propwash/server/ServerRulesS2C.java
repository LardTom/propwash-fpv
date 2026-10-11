package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerRulesS2C(boolean mayPilot, boolean unlimitedRangeEnabled, boolean unlimitedRange,
                             int unlimitedRangePermissionLevel, double maxPilotDistance, double maxDroneSpeed,
                             int droneViewDistance, int maxStreamedChunks, boolean onlyGeneratedChunks,
                             int generateChunksPermissionLevel, boolean mayGenerateChunks,
                             boolean pilotingInSpawnProtection, boolean spectating) implements CustomPacketPayload {
    public static final Type<ServerRulesS2C> TYPE = new Type<>(Constants.id("server_rules"));
    public static final StreamCodec<ByteBuf, ServerRulesS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ServerRulesS2C::mayPilot,
            ByteBufCodecs.BOOL, ServerRulesS2C::unlimitedRangeEnabled,
            ByteBufCodecs.BOOL, ServerRulesS2C::unlimitedRange,
            ByteBufCodecs.VAR_INT, ServerRulesS2C::unlimitedRangePermissionLevel,
            ByteBufCodecs.DOUBLE, ServerRulesS2C::maxPilotDistance,
            ByteBufCodecs.DOUBLE, ServerRulesS2C::maxDroneSpeed,
            ByteBufCodecs.VAR_INT, ServerRulesS2C::droneViewDistance,
            ByteBufCodecs.VAR_INT, ServerRulesS2C::maxStreamedChunks,
            ByteBufCodecs.BOOL, ServerRulesS2C::onlyGeneratedChunks,
            ByteBufCodecs.VAR_INT, ServerRulesS2C::generateChunksPermissionLevel,
            ByteBufCodecs.BOOL, ServerRulesS2C::mayGenerateChunks,
            ByteBufCodecs.BOOL, ServerRulesS2C::pilotingInSpawnProtection,
            ByteBufCodecs.BOOL, ServerRulesS2C::spectating,
            ServerRulesS2C::new);

    @Override
    public Type<ServerRulesS2C> type() {
        return TYPE;
    }
}

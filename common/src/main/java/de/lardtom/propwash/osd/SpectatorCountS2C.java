package de.lardtom.propwash.osd;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SpectatorCountS2C(int entityId, int count) implements CustomPacketPayload {
    public static final Type<SpectatorCountS2C> TYPE = new Type<>(Constants.id("spectator_count"));
    public static final StreamCodec<ByteBuf, SpectatorCountS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpectatorCountS2C::entityId,
            ByteBufCodecs.VAR_INT, SpectatorCountS2C::count,
            SpectatorCountS2C::new);

    @Override
    public Type<SpectatorCountS2C> type() {
        return TYPE;
    }
}

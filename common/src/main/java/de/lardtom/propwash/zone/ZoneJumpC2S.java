package de.lardtom.propwash.zone;

import de.lardtom.propwash.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ZoneJumpC2S(int id) implements CustomPacketPayload {
    public static final Type<ZoneJumpC2S> TYPE = new Type<>(Constants.id("zone_jump"));
    public static final StreamCodec<FriendlyByteBuf, ZoneJumpC2S> CODEC = StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.id),
            buf -> new ZoneJumpC2S(buf.readVarInt()));

    @Override
    public Type<ZoneJumpC2S> type() {
        return TYPE;
    }
}

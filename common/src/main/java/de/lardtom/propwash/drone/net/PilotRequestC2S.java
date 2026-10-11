package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PilotRequestC2S(int entityId, boolean start) implements CustomPacketPayload {
    public static final Type<PilotRequestC2S> TYPE = new Type<>(Constants.id("drone_pilot_request"));
    public static final StreamCodec<ByteBuf, PilotRequestC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PilotRequestC2S::entityId,
            ByteBufCodecs.BOOL, PilotRequestC2S::start,
            PilotRequestC2S::new);

    @Override
    public Type<PilotRequestC2S> type() {
        return TYPE;
    }
}

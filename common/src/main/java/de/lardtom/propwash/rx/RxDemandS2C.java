package de.lardtom.propwash.rx;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RxDemandS2C(RxMask mask) implements CustomPacketPayload {
    public static final Type<RxDemandS2C> TYPE = new Type<>(Constants.id("rx_demand"));
    public static final StreamCodec<ByteBuf, RxDemandS2C> CODEC = RxMask.STREAM_CODEC.map(RxDemandS2C::new, RxDemandS2C::mask);

    @Override
    public Type<RxDemandS2C> type() {
        return TYPE;
    }
}

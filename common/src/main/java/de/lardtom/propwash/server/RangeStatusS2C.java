package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RangeStatusS2C(boolean piloting, boolean anchored, double maxDistance, int streamRadius) implements CustomPacketPayload {
    public static final RangeStatusS2C IDLE = new RangeStatusS2C(false, false, Double.POSITIVE_INFINITY, 0);
    public static final Type<RangeStatusS2C> TYPE = new Type<>(Constants.id("range_status"));
    public static final StreamCodec<ByteBuf, RangeStatusS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RangeStatusS2C::piloting,
            ByteBufCodecs.BOOL, RangeStatusS2C::anchored,
            ByteBufCodecs.DOUBLE, RangeStatusS2C::maxDistance,
            ByteBufCodecs.VAR_INT, RangeStatusS2C::streamRadius,
            RangeStatusS2C::new);

    @Override
    public Type<RangeStatusS2C> type() {
        return TYPE;
    }
}

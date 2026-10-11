package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.FlightTune;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TuneC2S(int entityId, boolean offHand, FlightTune tune) implements CustomPacketPayload {
    public static final int HELD_ITEM = -1;
    public static final Type<TuneC2S> TYPE = new Type<>(Constants.id("drone_tune"));
    public static final StreamCodec<ByteBuf, TuneC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TuneC2S::entityId,
            ByteBufCodecs.BOOL, TuneC2S::offHand,
            FlightTune.STREAM_CODEC, TuneC2S::tune,
            TuneC2S::new);

    @Override
    public Type<TuneC2S> type() {
        return TYPE;
    }
}

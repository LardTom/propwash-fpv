package de.lardtom.propwash.stats.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.stats.advancement.FeatCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TrickC2S(int droneId, String trick, int combo) implements CustomPacketPayload {
    public static final int MAX_COMBO = 64;
    public static final Type<TrickC2S> TYPE = new Type<>(Constants.id("stats_trick"));
    public static final StreamCodec<ByteBuf, TrickC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TrickC2S::droneId,
            ByteBufCodecs.stringUtf8(FeatCodecs.MAX_TRICK_KEY), TrickC2S::trick,
            ByteBufCodecs.VAR_INT, TrickC2S::combo,
            TrickC2S::new);

    @Override
    public Type<TrickC2S> type() {
        return TYPE;
    }
}

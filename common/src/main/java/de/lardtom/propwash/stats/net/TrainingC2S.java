package de.lardtom.propwash.stats.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TrainingC2S(int chapters, boolean perfect) implements CustomPacketPayload {
    public static final Type<TrainingC2S> TYPE = new Type<>(Constants.id("stats_training"));
    public static final StreamCodec<ByteBuf, TrainingC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TrainingC2S::chapters,
            ByteBufCodecs.BOOL, TrainingC2S::perfect,
            TrainingC2S::new);

    @Override
    public Type<TrainingC2S> type() {
        return TYPE;
    }
}

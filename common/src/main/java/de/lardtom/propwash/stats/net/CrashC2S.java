package de.lardtom.propwash.stats.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CrashC2S(int entityId, int crashes) implements CustomPacketPayload {
    public static final int MAX_CRASHES = 8;
    public static final Type<CrashC2S> TYPE = new Type<>(Constants.id("stats_crash"));
    public static final StreamCodec<ByteBuf, CrashC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CrashC2S::entityId,
            ByteBufCodecs.VAR_INT, CrashC2S::crashes,
            CrashC2S::new);

    @Override
    public Type<CrashC2S> type() {
        return TYPE;
    }
}

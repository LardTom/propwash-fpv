package de.lardtom.propwash.stats.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GhostBeatenC2S(int courseId) implements CustomPacketPayload {
    public static final Type<GhostBeatenC2S> TYPE = new Type<>(Constants.id("stats_ghost_beaten"));
    public static final StreamCodec<ByteBuf, GhostBeatenC2S> CODEC = ByteBufCodecs.VAR_INT.map(GhostBeatenC2S::new, GhostBeatenC2S::courseId);

    @Override
    public Type<GhostBeatenC2S> type() {
        return TYPE;
    }
}

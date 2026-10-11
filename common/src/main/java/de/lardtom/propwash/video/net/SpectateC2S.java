package de.lardtom.propwash.video.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;
import java.util.UUID;

public record SpectateC2S(Optional<UUID> drone) implements CustomPacketPayload {
    public static final SpectateC2S STOP = new SpectateC2S(Optional.empty());
    public static final Type<SpectateC2S> TYPE = new Type<>(Constants.id("video_spectate"));
    public static final StreamCodec<ByteBuf, SpectateC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SpectateC2S::drone,
            SpectateC2S::new);

    public static SpectateC2S watch(UUID drone) {
        return new SpectateC2S(Optional.of(drone));
    }

    @Override
    public Type<SpectateC2S> type() {
        return TYPE;
    }
}

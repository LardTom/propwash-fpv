package de.lardtom.propwash.video.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.PilotFeed;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PilotFeedC2S(int entityId, PilotFeed feed) implements CustomPacketPayload {
    public static final Type<PilotFeedC2S> TYPE = new Type<>(Constants.id("video_pilot_feed"));
    public static final StreamCodec<ByteBuf, PilotFeedC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PilotFeedC2S::entityId,
            PilotFeed.CODEC, PilotFeedC2S::feed,
            PilotFeedC2S::new);

    @Override
    public Type<PilotFeedC2S> type() {
        return TYPE;
    }
}

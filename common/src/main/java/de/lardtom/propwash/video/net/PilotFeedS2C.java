package de.lardtom.propwash.video.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.PilotFeed;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PilotFeedS2C(int entityId, PilotFeed feed) implements CustomPacketPayload {
    public static final Type<PilotFeedS2C> TYPE = new Type<>(Constants.id("video_watched_feed"));
    public static final StreamCodec<ByteBuf, PilotFeedS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PilotFeedS2C::entityId,
            PilotFeed.CODEC, PilotFeedS2C::feed,
            PilotFeedS2C::new);

    @Override
    public Type<PilotFeedS2C> type() {
        return TYPE;
    }
}

package de.lardtom.propwash.video.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.PilotLook;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PilotLookS2C(int entityId, PilotLook look) implements CustomPacketPayload {
    public static final Type<PilotLookS2C> TYPE = new Type<>(Constants.id("video_watched_look"));
    public static final StreamCodec<ByteBuf, PilotLookS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PilotLookS2C::entityId,
            PilotLook.CODEC, PilotLookS2C::look,
            PilotLookS2C::new);

    @Override
    public Type<PilotLookS2C> type() {
        return TYPE;
    }
}

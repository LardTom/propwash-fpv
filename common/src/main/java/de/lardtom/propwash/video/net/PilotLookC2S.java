package de.lardtom.propwash.video.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.PilotLook;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PilotLookC2S(int entityId, PilotLook look) implements CustomPacketPayload {
    public static final Type<PilotLookC2S> TYPE = new Type<>(Constants.id("video_pilot_look"));
    public static final StreamCodec<ByteBuf, PilotLookC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PilotLookC2S::entityId,
            PilotLook.CODEC, PilotLookC2S::look,
            PilotLookC2S::new);

    @Override
    public Type<PilotLookC2S> type() {
        return TYPE;
    }
}

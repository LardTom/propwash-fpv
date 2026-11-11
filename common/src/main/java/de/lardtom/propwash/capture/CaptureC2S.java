package de.lardtom.propwash.capture;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CaptureC2S(int entityId, int radius) implements CustomPacketPayload {
    public static final Type<CaptureC2S> TYPE = new Type<>(Constants.id("camera_chunks_request"));
    public static final StreamCodec<ByteBuf, CaptureC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CaptureC2S::entityId,
            ByteBufCodecs.VAR_INT, CaptureC2S::radius,
            CaptureC2S::new);

    @Override
    public Type<CaptureC2S> type() {
        return TYPE;
    }
}

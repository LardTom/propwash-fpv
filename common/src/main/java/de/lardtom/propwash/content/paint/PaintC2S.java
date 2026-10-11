package de.lardtom.propwash.content.paint;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DronePaint;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PaintC2S(int containerId, DronePaint paint) implements CustomPacketPayload {
    public static final Type<PaintC2S> TYPE = new Type<>(Constants.id("workbench_paint"));
    public static final StreamCodec<ByteBuf, PaintC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PaintC2S::containerId,
            PaintCodecs.STREAM_CODEC, PaintC2S::paint,
            PaintC2S::new);

    @Override
    public Type<PaintC2S> type() {
        return TYPE;
    }
}

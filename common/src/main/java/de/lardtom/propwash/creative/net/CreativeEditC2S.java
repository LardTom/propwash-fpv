package de.lardtom.propwash.creative.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.creative.CreativeCodecs;
import de.lardtom.propwash.creative.CreativeSpec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CreativeEditC2S(int entityId, boolean offHand, CreativeSpec spec, float uptilt) implements CustomPacketPayload {
    public static final int HELD_ITEM = -1;
    public static final Type<CreativeEditC2S> TYPE = new Type<>(Constants.id("creative_edit"));
    public static final StreamCodec<ByteBuf, CreativeEditC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreativeEditC2S::entityId,
            ByteBufCodecs.BOOL, CreativeEditC2S::offHand,
            CreativeCodecs.STREAM_CODEC, CreativeEditC2S::spec,
            ByteBufCodecs.FLOAT, CreativeEditC2S::uptilt,
            CreativeEditC2S::new);

    @Override
    public Type<CreativeEditC2S> type() {
        return TYPE;
    }
}

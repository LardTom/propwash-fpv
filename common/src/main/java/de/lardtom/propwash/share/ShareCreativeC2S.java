package de.lardtom.propwash.share;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.ImportWire;
import de.lardtom.propwash.creative.CreativeCodecs;
import de.lardtom.propwash.creative.CreativeSpec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ShareCreativeC2S(int requestId, ImportWire request, CreativeSpec spec) implements CustomPacketPayload {
    public static final Type<ShareCreativeC2S> TYPE = new Type<>(Constants.id("share_creative_create"));
    public static final StreamCodec<ByteBuf, ShareCreativeC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShareCreativeC2S::requestId,
            ImportWire.CODEC, ShareCreativeC2S::request,
            CreativeCodecs.STREAM_CODEC, ShareCreativeC2S::spec,
            ShareCreativeC2S::new);

    @Override
    public Type<ShareCreativeC2S> type() {
        return TYPE;
    }
}

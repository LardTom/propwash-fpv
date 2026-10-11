package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GenerationEdgeS2C(boolean restricted, int centerX, int centerZ, long low, long mid, long high) implements CustomPacketPayload {
    public static final GenerationEdgeS2C OFF = new GenerationEdgeS2C(false, 0, 0, 0L, 0L, 0L);
    public static final Type<GenerationEdgeS2C> TYPE = new Type<>(Constants.id("generation_edge"));
    public static final StreamCodec<ByteBuf, GenerationEdgeS2C> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GenerationEdgeS2C::restricted,
            ByteBufCodecs.VAR_INT, GenerationEdgeS2C::centerX,
            ByteBufCodecs.VAR_INT, GenerationEdgeS2C::centerZ,
            ByteBufCodecs.LONG, GenerationEdgeS2C::low,
            ByteBufCodecs.LONG, GenerationEdgeS2C::mid,
            ByteBufCodecs.LONG, GenerationEdgeS2C::high,
            GenerationEdgeS2C::new);

    public static GenerationEdgeS2C of(EdgeMask mask) {
        return new GenerationEdgeS2C(true, mask.centerX(), mask.centerZ(), mask.low(), mask.mid(), mask.high());
    }

    public EdgeMask mask() {
        return this.restricted ? new EdgeMask(this.centerX, this.centerZ, this.low, this.mid, this.high) : EdgeMask.NONE;
    }

    @Override
    public Type<GenerationEdgeS2C> type() {
        return TYPE;
    }
}

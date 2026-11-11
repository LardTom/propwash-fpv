package de.lardtom.nsprobe;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NsProbePing(int value) implements CustomPacketPayload {
    public static final Type<NsProbePing> TYPE = new Type<>(Identifier.fromNamespaceAndPath("propwash", "nsprobe_ping"));
    public static final StreamCodec<ByteBuf, NsProbePing> CODEC = ByteBufCodecs.VAR_INT.map(NsProbePing::new, NsProbePing::value);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

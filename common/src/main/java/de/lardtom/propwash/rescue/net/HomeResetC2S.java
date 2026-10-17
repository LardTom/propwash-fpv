package de.lardtom.propwash.rescue.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HomeResetC2S(int entityId) implements CustomPacketPayload {
    public static final Type<HomeResetC2S> TYPE = new Type<>(Constants.id("home_reset"));
    public static final StreamCodec<ByteBuf, HomeResetC2S> CODEC = CustomPacketPayload.codec(HomeResetC2S::write, HomeResetC2S::read);

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
    }

    private static HomeResetC2S read(ByteBuf buf) {
        return new HomeResetC2S(VarInt.read(buf));
    }

    @Override
    public Type<HomeResetC2S> type() {
        return TYPE;
    }
}

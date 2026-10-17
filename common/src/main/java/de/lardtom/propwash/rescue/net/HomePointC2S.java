package de.lardtom.propwash.rescue.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.rescue.HomePoint;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

public record HomePointC2S(int entityId, int seq, double x, double y, double z, HomePoint.@Nullable Source source)
        implements CustomPacketPayload {
    public static final Type<HomePointC2S> TYPE = new Type<>(Constants.id("home_point_set"));
    public static final StreamCodec<ByteBuf, HomePointC2S> CODEC = CustomPacketPayload.codec(HomePointC2S::write, HomePointC2S::read);

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        VarInt.write(buf, this.seq);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeByte(this.source == null ? -1 : this.source.ordinal());
    }

    private static HomePointC2S read(ByteBuf buf) {
        return new HomePointC2S(VarInt.read(buf), VarInt.read(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                HomePoint.Source.byOrdinal(buf.readByte()));
    }

    @Override
    public Type<HomePointC2S> type() {
        return TYPE;
    }
}

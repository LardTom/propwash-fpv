package de.lardtom.propwash.rescue.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.rescue.HomePoint;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

public record HomePointS2C(int entityId, int ack, boolean edited, @Nullable HomePoint home) implements CustomPacketPayload {
    public static final Type<HomePointS2C> TYPE = new Type<>(Constants.id("home_point"));
    public static final StreamCodec<ByteBuf, HomePointS2C> CODEC = CustomPacketPayload.codec(HomePointS2C::write, HomePointS2C::read);
    private static final int MAX_DIMENSION = 32767;

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        VarInt.write(buf, this.ack);
        buf.writeBoolean(this.edited);
        HomePoint h = this.home;
        buf.writeBoolean(h != null);
        if (h != null) {
            buf.writeDouble(h.x());
            buf.writeDouble(h.y());
            buf.writeDouble(h.z());
            Utf8String.write(buf, h.dimension(), MAX_DIMENSION);
            VarLong.write(buf, h.gameTime());
            buf.writeByte(h.source().ordinal());
        }
    }

    private static HomePointS2C read(ByteBuf buf) {
        int entityId = VarInt.read(buf);
        int ack = VarInt.read(buf);
        boolean edited = buf.readBoolean();
        if (!buf.readBoolean()) {
            return new HomePointS2C(entityId, ack, edited, null);
        }
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        String dimension = Utf8String.read(buf, MAX_DIMENSION);
        long time = VarLong.read(buf);
        HomePoint.Source source = HomePoint.Source.byOrdinal(buf.readByte());
        return new HomePointS2C(entityId, ack, edited, new HomePoint(x, y, z, dimension, time,
                source == null ? HomePoint.Source.COMMAND : source));
    }

    @Override
    public Type<HomePointS2C> type() {
        return TYPE;
    }
}

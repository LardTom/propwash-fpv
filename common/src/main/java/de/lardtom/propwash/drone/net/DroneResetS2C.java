package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DroneResetS2C(int entityId, int epoch, double x, double y, double z, float qx, float qy, float qz, float qw)
        implements CustomPacketPayload {
    public static final Type<DroneResetS2C> TYPE = new Type<>(Constants.id("drone_reset"));
    public static final StreamCodec<ByteBuf, DroneResetS2C> CODEC = CustomPacketPayload.codec(DroneResetS2C::write, DroneResetS2C::read);

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        VarInt.write(buf, this.epoch);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.qx);
        buf.writeFloat(this.qy);
        buf.writeFloat(this.qz);
        buf.writeFloat(this.qw);
    }

    private static DroneResetS2C read(ByteBuf buf) {
        return new DroneResetS2C(VarInt.read(buf), VarInt.read(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<DroneResetS2C> type() {
        return TYPE;
    }
}

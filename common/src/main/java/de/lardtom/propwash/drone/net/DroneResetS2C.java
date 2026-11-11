package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DroneResetS2C(int entityId, int epoch, double x, double y, double z, float qx, float qy, float qz, float qw,
                           boolean home, boolean keepMotion, boolean keepPosition)
        implements CustomPacketPayload {
    public static final Type<DroneResetS2C> TYPE = new Type<>(Constants.id("drone_reset"));
    public static final StreamCodec<ByteBuf, DroneResetS2C> CODEC = CustomPacketPayload.codec(DroneResetS2C::write, DroneResetS2C::read);

    private static final int HOME = 1;
    private static final int KEEP_MOTION = 2;
    private static final int KEEP_POSITION = 4;

    public DroneResetS2C(int entityId, int epoch, double x, double y, double z, float qx, float qy, float qz, float qw, boolean home) {
        this(entityId, epoch, x, y, z, qx, qy, qz, qw, home, false, false);
    }

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
        buf.writeByte((this.home ? HOME : 0) | (this.keepMotion ? KEEP_MOTION : 0) | (this.keepPosition ? KEEP_POSITION : 0));
    }

    private static DroneResetS2C read(ByteBuf buf) {
        int entityId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float qx = buf.readFloat();
        float qy = buf.readFloat();
        float qz = buf.readFloat();
        float qw = buf.readFloat();
        int flags = buf.readUnsignedByte();
        return new DroneResetS2C(entityId, epoch, x, y, z, qx, qy, qz, qw, (flags & HOME) != 0, (flags & KEEP_MOTION) != 0,
                (flags & KEEP_POSITION) != 0);
    }

    @Override
    public Type<DroneResetS2C> type() {
        return TYPE;
    }
}

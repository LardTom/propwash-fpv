package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DroneStateC2S(int entityId, int epoch, double x, double y, double z, float qx, float qy, float qz, float qw,
                            float vx, float vy, float vz, boolean armed, float battery, int motors,
                            boolean physicsModified, boolean ledsOff, boolean beeper) implements CustomPacketPayload {
    public static final Type<DroneStateC2S> TYPE = new Type<>(Constants.id("flight_state"));
    public static final StreamCodec<ByteBuf, DroneStateC2S> CODEC = CustomPacketPayload.codec(DroneStateC2S::write, DroneStateC2S::read);

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
        buf.writeFloat(this.vx);
        buf.writeFloat(this.vy);
        buf.writeFloat(this.vz);
        buf.writeBoolean(this.armed);
        buf.writeFloat(this.battery);
        buf.writeInt(this.motors);
        buf.writeBoolean(this.physicsModified);
        buf.writeBoolean(this.ledsOff);
        buf.writeBoolean(this.beeper);
    }

    private static DroneStateC2S read(ByteBuf buf) {
        return new DroneStateC2S(VarInt.read(buf), VarInt.read(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readFloat(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public Type<DroneStateC2S> type() {
        return TYPE;
    }
}

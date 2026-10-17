package de.lardtom.propwash.accessory.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DronePushS2C(int droneId, boolean impulse, boolean frameRelative, float fx, float fy, float fz, float px, float py, float pz,
                           float seconds) implements CustomPacketPayload {
    public static final Type<DronePushS2C> TYPE = new Type<>(Constants.id("drone_push"));
    public static final StreamCodec<ByteBuf, DronePushS2C> CODEC = CustomPacketPayload.codec(DronePushS2C::write, DronePushS2C::read);

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.droneId);
        buf.writeByte((this.impulse ? 1 : 0) | (this.frameRelative ? 2 : 0));
        buf.writeFloat(this.fx);
        buf.writeFloat(this.fy);
        buf.writeFloat(this.fz);
        buf.writeFloat(this.px);
        buf.writeFloat(this.py);
        buf.writeFloat(this.pz);
        buf.writeFloat(this.seconds);
    }

    private static DronePushS2C read(ByteBuf buf) {
        int droneId = VarInt.read(buf);
        int flags = buf.readUnsignedByte();
        if (flags > 3) {
            throw new IllegalArgumentException("Ungültige Stoß-Art: " + flags);
        }
        return new DronePushS2C(droneId, (flags & 1) != 0, (flags & 2) != 0, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<DronePushS2C> type() {
        return TYPE;
    }
}

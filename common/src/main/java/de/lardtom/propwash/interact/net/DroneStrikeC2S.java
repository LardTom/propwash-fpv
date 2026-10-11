package de.lardtom.propwash.interact.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DroneStrikeC2S(int droneId, int epoch, int targetId, float vx, float vy, float vz, int axis, boolean positive)
        implements CustomPacketPayload {
    public static final Type<DroneStrikeC2S> TYPE = new Type<>(Constants.id("drone_strike"));
    public static final StreamCodec<ByteBuf, DroneStrikeC2S> CODEC = CustomPacketPayload.codec(DroneStrikeC2S::write, DroneStrikeC2S::read);

    public DroneStrikeC2S {
        if (axis < 0 || axis > 2) {
            throw new IllegalArgumentException("Achse außerhalb 0..2: " + axis);
        }
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.droneId);
        VarInt.write(buf, this.epoch);
        VarInt.write(buf, this.targetId);
        buf.writeFloat(this.vx);
        buf.writeFloat(this.vy);
        buf.writeFloat(this.vz);
        buf.writeByte(this.axis * 2 + (this.positive ? 1 : 0));
    }

    private static DroneStrikeC2S read(ByteBuf buf) {
        int droneId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        int targetId = VarInt.read(buf);
        float vx = buf.readFloat();
        float vy = buf.readFloat();
        float vz = buf.readFloat();
        int packed = buf.readUnsignedByte();
        if (packed > 5) {
            throw new IllegalArgumentException("Ungültige Trefferseite: " + packed);
        }
        return new DroneStrikeC2S(droneId, epoch, targetId, vx, vy, vz, packed / 2, (packed & 1) != 0);
    }

    @Override
    public Type<DroneStrikeC2S> type() {
        return TYPE;
    }
}

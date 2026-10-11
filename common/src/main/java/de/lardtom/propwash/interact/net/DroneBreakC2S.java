package de.lardtom.propwash.interact.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DroneBreakC2S(int droneId, int epoch, BlockPos pos, float vx, float vy, float vz, int axis, boolean positive)
        implements CustomPacketPayload {
    public static final Type<DroneBreakC2S> TYPE = new Type<>(Constants.id("drone_break"));
    public static final StreamCodec<ByteBuf, DroneBreakC2S> CODEC = CustomPacketPayload.codec(DroneBreakC2S::write, DroneBreakC2S::read);

    public DroneBreakC2S {
        if (axis < 0 || axis > 2) {
            throw new IllegalArgumentException("Achse außerhalb 0..2: " + axis);
        }
        pos = pos.immutable();
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.droneId);
        VarInt.write(buf, this.epoch);
        buf.writeLong(this.pos.asLong());
        buf.writeFloat(this.vx);
        buf.writeFloat(this.vy);
        buf.writeFloat(this.vz);
        buf.writeByte(this.axis * 2 + (this.positive ? 1 : 0));
    }

    private static DroneBreakC2S read(ByteBuf buf) {
        int droneId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        BlockPos pos = BlockPos.of(buf.readLong());
        float vx = buf.readFloat();
        float vy = buf.readFloat();
        float vz = buf.readFloat();
        int packed = buf.readUnsignedByte();
        if (packed > 5) {
            throw new IllegalArgumentException("Ungültige Bruchseite: " + packed);
        }
        return new DroneBreakC2S(droneId, epoch, pos, vx, vy, vz, packed / 2, (packed & 1) != 0);
    }

    @Override
    public Type<DroneBreakC2S> type() {
        return TYPE;
    }
}

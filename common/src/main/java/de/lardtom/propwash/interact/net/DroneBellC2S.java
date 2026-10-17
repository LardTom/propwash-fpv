package de.lardtom.propwash.interact.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DroneBellC2S(int droneId, int epoch, BlockPos pos, Direction face, float hitX, float hitY, float hitZ, float speed)
        implements CustomPacketPayload {
    public static final Type<DroneBellC2S> TYPE = new Type<>(Constants.id("drone_bell"));
    public static final StreamCodec<ByteBuf, DroneBellC2S> CODEC = CustomPacketPayload.codec(DroneBellC2S::write, DroneBellC2S::read);

    public DroneBellC2S {
        if (face == null) {
            throw new IllegalArgumentException("Seite fehlt");
        }
        pos = pos.immutable();
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.droneId);
        VarInt.write(buf, this.epoch);
        buf.writeLong(this.pos.asLong());
        buf.writeByte(this.face.get3DDataValue());
        buf.writeFloat(this.hitX);
        buf.writeFloat(this.hitY);
        buf.writeFloat(this.hitZ);
        buf.writeFloat(this.speed);
    }

    private static DroneBellC2S read(ByteBuf buf) {
        int droneId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        BlockPos pos = BlockPos.of(buf.readLong());
        int face = buf.readUnsignedByte();
        if (face > 5) {
            throw new IllegalArgumentException("Ungültige Glockenseite: " + face);
        }
        float hitX = buf.readFloat();
        float hitY = buf.readFloat();
        float hitZ = buf.readFloat();
        float speed = buf.readFloat();
        return new DroneBellC2S(droneId, epoch, pos, Direction.from3DDataValue(face), hitX, hitY, hitZ, speed);
    }

    @Override
    public Type<DroneBellC2S> type() {
        return TYPE;
    }
}

package de.lardtom.propwash.drone.operator.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.operator.OperatorChannel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OperatorInputC2S(int entityId, short[] axes, int bits) implements CustomPacketPayload {
    public static final Type<OperatorInputC2S> TYPE = new Type<>(Constants.id("operator_input"));
    public static final StreamCodec<ByteBuf, OperatorInputC2S> CODEC = CustomPacketPayload.codec(OperatorInputC2S::write, OperatorInputC2S::read);

    public OperatorInputC2S {
        short[] copy = new short[OperatorChannel.AXES];
        if (axes != null) {
            System.arraycopy(axes, 0, copy, 0, Math.min(axes.length, copy.length));
        }
        axes = copy;
        bits &= OperatorChannel.ACTION_MASK | OperatorChannel.LEAVE;
    }

    public static OperatorInputC2S leave(int entityId) {
        return new OperatorInputC2S(entityId, new short[OperatorChannel.AXES], OperatorChannel.LEAVE);
    }

    public short axis(int index) {
        return index >= 0 && index < this.axes.length ? this.axes[index] : 0;
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        for (short axis : this.axes) {
            buf.writeShort(axis);
        }
        buf.writeByte(this.bits);
    }

    private static OperatorInputC2S read(ByteBuf buf) {
        int entityId = VarInt.read(buf);
        short[] axes = new short[OperatorChannel.AXES];
        for (int i = 0; i < axes.length; i++) {
            axes[i] = buf.readShort();
        }
        return new OperatorInputC2S(entityId, axes, buf.readUnsignedByte());
    }

    @Override
    public Type<OperatorInputC2S> type() {
        return TYPE;
    }
}

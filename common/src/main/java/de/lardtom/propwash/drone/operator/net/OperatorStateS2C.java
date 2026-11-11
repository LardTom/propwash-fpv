package de.lardtom.propwash.drone.operator.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.operator.OperatorChannel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OperatorStateS2C(int entityId, short[] axes, int actions) implements CustomPacketPayload {
    public static final Type<OperatorStateS2C> TYPE = new Type<>(Constants.id("operator_state"));
    public static final StreamCodec<ByteBuf, OperatorStateS2C> CODEC = CustomPacketPayload.codec(OperatorStateS2C::write, OperatorStateS2C::read);

    public OperatorStateS2C {
        short[] copy = new short[OperatorChannel.AXES];
        if (axes != null) {
            System.arraycopy(axes, 0, copy, 0, Math.min(axes.length, copy.length));
        }
        axes = copy;
        actions = OperatorChannel.sanitize(actions);
    }

    public short axis(int index) {
        return index >= 0 && index < this.axes.length ? this.axes[index] : 0;
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        for (short axis : this.axes) {
            buf.writeShort(axis);
        }
        buf.writeByte(this.actions);
    }

    private static OperatorStateS2C read(ByteBuf buf) {
        int entityId = VarInt.read(buf);
        short[] axes = new short[OperatorChannel.AXES];
        for (int i = 0; i < axes.length; i++) {
            axes[i] = buf.readShort();
        }
        return new OperatorStateS2C(entityId, axes, buf.readUnsignedByte());
    }

    @Override
    public Type<OperatorStateS2C> type() {
        return TYPE;
    }
}

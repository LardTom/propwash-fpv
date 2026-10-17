package de.lardtom.propwash.accessory.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AccessoryActionC2S(int droneId, int epoch, int number, boolean pressed) implements CustomPacketPayload {
    public static final int ACTIONS = 4;
    public static final Type<AccessoryActionC2S> TYPE = new Type<>(Constants.id("accessory_action"));
    public static final StreamCodec<ByteBuf, AccessoryActionC2S> CODEC = CustomPacketPayload.codec(AccessoryActionC2S::write,
            AccessoryActionC2S::read);

    public AccessoryActionC2S {
        if (number < 1 || number > ACTIONS) {
            throw new IllegalArgumentException("Zubehör-Aktion außerhalb 1.." + ACTIONS + ": " + number);
        }
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.droneId);
        VarInt.write(buf, this.epoch);
        buf.writeByte((this.number - 1) * 2 + (this.pressed ? 1 : 0));
    }

    private static AccessoryActionC2S read(ByteBuf buf) {
        int droneId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        int packed = buf.readUnsignedByte();
        if (packed >= ACTIONS * 2) {
            throw new IllegalArgumentException("Ungültige Zubehör-Aktion: " + packed);
        }
        return new AccessoryActionC2S(droneId, epoch, packed / 2 + 1, (packed & 1) != 0);
    }

    @Override
    public Type<AccessoryActionC2S> type() {
        return TYPE;
    }
}

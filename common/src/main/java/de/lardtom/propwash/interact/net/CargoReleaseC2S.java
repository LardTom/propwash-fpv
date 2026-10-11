package de.lardtom.propwash.interact.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.interact.CargoReleaseReason;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CargoReleaseC2S(int droneId, int epoch, CargoReleaseReason reason) implements CustomPacketPayload {
    public static final Type<CargoReleaseC2S> TYPE = new Type<>(Constants.id("cargo_release"));
    public static final StreamCodec<ByteBuf, CargoReleaseC2S> CODEC = CustomPacketPayload.codec(CargoReleaseC2S::write, CargoReleaseC2S::read);

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.droneId);
        VarInt.write(buf, this.epoch);
        buf.writeByte(this.reason.ordinal());
    }

    private static CargoReleaseC2S read(ByteBuf buf) {
        int droneId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        int reason = buf.readUnsignedByte();
        if (reason != CargoReleaseReason.COMMAND.ordinal() && reason != CargoReleaseReason.CRASH.ordinal()) {
            throw new IllegalArgumentException("Ungültiger Abwurfgrund: " + reason);
        }
        return new CargoReleaseC2S(droneId, epoch, CargoReleaseReason.byOrdinal(reason));
    }

    @Override
    public Type<CargoReleaseC2S> type() {
        return TYPE;
    }
}

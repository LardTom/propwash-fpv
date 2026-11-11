package de.lardtom.propwash.rescue.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.flight.AssistOutput;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

public record AutonomyStatusS2C(int entityId, boolean active, String label, int reason) implements CustomPacketPayload {
    public static final Type<AutonomyStatusS2C> TYPE = new Type<>(Constants.id("autonomy_status"));
    public static final StreamCodec<ByteBuf, AutonomyStatusS2C> CODEC = CustomPacketPayload.codec(AutonomyStatusS2C::write, AutonomyStatusS2C::read);
    private static final int MAX_LABEL_BYTES = AssistOutput.MAX_LABEL * 4;

    public AutonomyStatusS2C {
        label = label == null ? "" : label.length() > AssistOutput.MAX_LABEL ? label.substring(0, AssistOutput.MAX_LABEL) : label;
        reason = reason >= 0 && reason < AutonomyEnd.values().length ? reason : -1;
    }

    public @Nullable AutonomyEnd end() {
        return this.reason < 0 ? null : AutonomyEnd.values()[this.reason];
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        buf.writeBoolean(this.active);
        Utf8String.write(buf, this.label, MAX_LABEL_BYTES);
        buf.writeByte(this.reason);
    }

    private static AutonomyStatusS2C read(ByteBuf buf) {
        int entityId = VarInt.read(buf);
        boolean active = buf.readBoolean();
        String label = Utf8String.read(buf, MAX_LABEL_BYTES);
        int reason = buf.readByte();
        return new AutonomyStatusS2C(entityId, active, label, reason);
    }

    @Override
    public Type<AutonomyStatusS2C> type() {
        return TYPE;
    }
}

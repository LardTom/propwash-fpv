package de.lardtom.propwash.rescue.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RescueStatusC2S(int entityId, boolean active, double homeX, double homeY, double homeZ, boolean followPilot,
                              float altitude, float speed, float descentRate, boolean obstacleAvoidance, float clearance,
                              float minDistance) implements CustomPacketPayload {
    public static final Type<RescueStatusC2S> TYPE = new Type<>(Constants.id("rescue_status"));
    public static final StreamCodec<ByteBuf, RescueStatusC2S> CODEC = CustomPacketPayload.codec(RescueStatusC2S::write, RescueStatusC2S::read);

    public static RescueStatusC2S inactive(int entityId) {
        return new RescueStatusC2S(entityId, false, 0.0, 0.0, 0.0, false, 0.0F, 0.0F, 0.0F, false, 0.0F, 0.0F);
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        buf.writeBoolean(this.active);
        buf.writeDouble(this.homeX);
        buf.writeDouble(this.homeY);
        buf.writeDouble(this.homeZ);
        buf.writeBoolean(this.followPilot);
        buf.writeFloat(this.altitude);
        buf.writeFloat(this.speed);
        buf.writeFloat(this.descentRate);
        buf.writeBoolean(this.obstacleAvoidance);
        buf.writeFloat(this.clearance);
        buf.writeFloat(this.minDistance);
    }

    private static RescueStatusC2S read(ByteBuf buf) {
        return new RescueStatusC2S(VarInt.read(buf), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
    }

    @Override
    public Type<RescueStatusC2S> type() {
        return TYPE;
    }
}

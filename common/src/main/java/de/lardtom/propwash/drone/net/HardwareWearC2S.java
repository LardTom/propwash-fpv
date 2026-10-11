package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.PartWear;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HardwareWearC2S(int entityId, int epoch, boolean escBurned, float[] motorHeatWear) implements CustomPacketPayload {
    public static final Type<HardwareWearC2S> TYPE = new Type<>(Constants.id("drone_hardware_wear"));
    public static final StreamCodec<ByteBuf, HardwareWearC2S> CODEC = CustomPacketPayload.codec(HardwareWearC2S::write, HardwareWearC2S::read);

    public HardwareWearC2S {
        float[] copy = new float[PartWear.POSITIONS];
        if (motorHeatWear != null) {
            for (int i = 0; i < Math.min(copy.length, motorHeatWear.length); i++) {
                float v = motorHeatWear[i];
                copy[i] = Float.isFinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : 0.0F;
            }
        }
        motorHeatWear = copy;
    }

    public boolean empty() {
        if (this.escBurned) {
            return false;
        }
        for (float v : this.motorHeatWear) {
            if (v > 0.0F) {
                return false;
            }
        }
        return true;
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        VarInt.write(buf, this.epoch);
        buf.writeBoolean(this.escBurned);
        for (float v : this.motorHeatWear) {
            buf.writeFloat(v);
        }
    }

    private static HardwareWearC2S read(ByteBuf buf) {
        int entityId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        boolean escBurned = buf.readBoolean();
        float[] wear = new float[PartWear.POSITIONS];
        for (int i = 0; i < wear.length; i++) {
            wear[i] = buf.readFloat();
        }
        return new HardwareWearC2S(entityId, epoch, escBurned, wear);
    }

    @Override
    public Type<HardwareWearC2S> type() {
        return TYPE;
    }
}

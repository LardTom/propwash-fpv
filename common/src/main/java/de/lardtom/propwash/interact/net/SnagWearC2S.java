package de.lardtom.propwash.interact.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.PartWear;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SnagWearC2S(int droneId, int epoch, float[] propWear) implements CustomPacketPayload {
    public static final Type<SnagWearC2S> TYPE = new Type<>(Constants.id("drone_snag_wear"));
    public static final StreamCodec<ByteBuf, SnagWearC2S> CODEC = CustomPacketPayload.codec(SnagWearC2S::write, SnagWearC2S::read);

    public SnagWearC2S {
        float[] copy = new float[PartWear.POSITIONS];
        if (propWear != null) {
            for (int i = 0; i < Math.min(copy.length, propWear.length); i++) {
                float v = propWear[i];
                copy[i] = Float.isFinite(v) ? Math.max(0.0F, Math.min(1.0F, v)) : 0.0F;
            }
        }
        propWear = copy;
    }

    public boolean empty() {
        for (float v : this.propWear) {
            if (v > 0.0F) {
                return false;
            }
        }
        return true;
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.droneId);
        VarInt.write(buf, this.epoch);
        for (float v : this.propWear) {
            buf.writeFloat(v);
        }
    }

    private static SnagWearC2S read(ByteBuf buf) {
        int droneId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        float[] wear = new float[PartWear.POSITIONS];
        for (int i = 0; i < wear.length; i++) {
            wear[i] = buf.readFloat();
        }
        return new SnagWearC2S(droneId, epoch, wear);
    }

    @Override
    public Type<SnagWearC2S> type() {
        return TYPE;
    }
}

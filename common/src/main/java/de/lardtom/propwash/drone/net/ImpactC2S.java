package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.sim.ImpactSample;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record ImpactC2S(int entityId, int epoch, float strength, List<ImpactSample> impacts) implements CustomPacketPayload {
    public static final int MAX_IMPACTS = 8;
    public static final Type<ImpactC2S> TYPE = new Type<>(Constants.id("drone_impact"));
    public static final StreamCodec<ByteBuf, ImpactC2S> CODEC = CustomPacketPayload.codec(ImpactC2S::write, ImpactC2S::read);

    public ImpactC2S {
        impacts = List.copyOf(impacts.size() > MAX_IMPACTS ? impacts.subList(0, MAX_IMPACTS) : impacts);
    }

    private void write(ByteBuf buf) {
        VarInt.write(buf, this.entityId);
        VarInt.write(buf, this.epoch);
        buf.writeFloat(this.strength);
        VarInt.write(buf, this.impacts.size());
        for (ImpactSample impact : this.impacts) {
            buf.writeFloat(impact.speed());
            buf.writeFloat(impact.tangential());
            buf.writeFloat(impact.normalX());
            buf.writeFloat(impact.normalY());
            buf.writeFloat(impact.normalZ());
            VarInt.write(buf, impact.contacts());
            buf.writeByte(impact.struck());
            buf.writeFloat(impact.strikeSpin());
        }
    }

    private static ImpactC2S read(ByteBuf buf) {
        int entityId = VarInt.read(buf);
        int epoch = VarInt.read(buf);
        float strength = buf.readFloat();
        int count = VarInt.read(buf);
        if (count < 0 || count > MAX_IMPACTS) {
            throw new IllegalArgumentException("Zu viele Aufpralle: " + count);
        }
        List<ImpactSample> impacts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            impacts.add(new ImpactSample(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), VarInt.read(buf),
                    buf.readUnsignedByte(), buf.readFloat()));
        }
        return new ImpactC2S(entityId, epoch, strength, impacts);
    }

    @Override
    public Type<ImpactC2S> type() {
        return TYPE;
    }
}

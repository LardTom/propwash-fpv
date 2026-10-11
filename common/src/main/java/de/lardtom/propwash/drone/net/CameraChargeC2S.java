package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CameraChargeC2S(int entityId, float charge) implements CustomPacketPayload {
    public static final Type<CameraChargeC2S> TYPE = new Type<>(Constants.id("drone_camera_charge"));
    public static final StreamCodec<ByteBuf, CameraChargeC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CameraChargeC2S::entityId,
            ByteBufCodecs.FLOAT, CameraChargeC2S::charge,
            CameraChargeC2S::new);

    @Override
    public Type<CameraChargeC2S> type() {
        return TYPE;
    }
}

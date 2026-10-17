package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BeeperOffC2S(int entityId) implements CustomPacketPayload {
    public static final Type<BeeperOffC2S> TYPE = new Type<>(Constants.id("drone_beeper_off"));
    public static final StreamCodec<ByteBuf, BeeperOffC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BeeperOffC2S::entityId,
            BeeperOffC2S::new);

    @Override
    public Type<BeeperOffC2S> type() {
        return TYPE;
    }
}

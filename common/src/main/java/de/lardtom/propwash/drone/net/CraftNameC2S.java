package de.lardtom.propwash.drone.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.CraftNames;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CraftNameC2S(int entityId, boolean offHand, String name) implements CustomPacketPayload {
    public static final int HELD_ITEM = -1;
    public static final Type<CraftNameC2S> TYPE = new Type<>(Constants.id("drone_craft_name"));
    public static final StreamCodec<ByteBuf, CraftNameC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftNameC2S::entityId,
            ByteBufCodecs.BOOL, CraftNameC2S::offHand,
            ByteBufCodecs.stringUtf8(CraftNames.MAX_LENGTH * 4), CraftNameC2S::name,
            CraftNameC2S::new);

    @Override
    public Type<CraftNameC2S> type() {
        return TYPE;
    }
}

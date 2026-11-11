package de.lardtom.propwash.zone;

import de.lardtom.propwash.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashMap;
import java.util.Map;

public record ZoneReleaseC2S(Map<String, Boolean> changes) implements CustomPacketPayload {
    public static final Type<ZoneReleaseC2S> TYPE = new Type<>(Constants.id("zone_release"));
    public static final StreamCodec<FriendlyByteBuf, ZoneReleaseC2S> CODEC = StreamCodec.of(
            (buf, payload) -> ZoneWire.writeFlags(buf, payload.changes), buf -> new ZoneReleaseC2S(ZoneWire.readFlags(buf)));

    public ZoneReleaseC2S {
        changes = Map.copyOf(new LinkedHashMap<>(changes));
    }

    @Override
    public Type<ZoneReleaseC2S> type() {
        return TYPE;
    }
}

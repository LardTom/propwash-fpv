package de.lardtom.propwash.zone;

import de.lardtom.propwash.Constants;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record ZonesS2C(boolean editable, boolean creatable, String dimension, List<Zone> zones, ZoneRelease release) implements CustomPacketPayload {
    public static final Type<ZonesS2C> TYPE = new Type<>(Constants.id("zones"));
    public static final StreamCodec<FriendlyByteBuf, ZonesS2C> CODEC = StreamCodec.of(ZonesS2C::write, ZonesS2C::read);

    public ZonesS2C {
        zones = List.copyOf(zones);
        release = release == null ? ZoneRelease.DEFAULT : release;
    }

    public ZonesS2C(boolean editable, String dimension, List<Zone> zones) {
        this(editable, editable, dimension, zones, ZoneRelease.DEFAULT);
    }

    private static void write(FriendlyByteBuf buf, ZonesS2C payload) {
        buf.writeBoolean(payload.editable);
        buf.writeBoolean(payload.creatable);
        buf.writeUtf(payload.dimension, ZoneLimits.MAX_DIMENSION);
        buf.writeVarInt(payload.zones.size());
        for (Zone zone : payload.zones) {
            ZoneWire.write(buf, zone);
        }
        ZoneWire.writeRelease(buf, payload.release);
    }

    private static ZonesS2C read(FriendlyByteBuf buf) {
        boolean editable = buf.readBoolean();
        boolean creatable = buf.readBoolean();
        String dimension = buf.readUtf(ZoneLimits.MAX_DIMENSION);
        int count = buf.readVarInt();
        if (count < 0 || count > ZoneLimits.MAX_ZONES) {
            throw new DecoderException("Zu viele Zonen: " + count);
        }
        List<Zone> zones = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            zones.add(ZoneWire.read(buf));
        }
        return new ZonesS2C(editable, creatable, dimension, zones, ZoneWire.readRelease(buf));
    }

    @Override
    public Type<ZonesS2C> type() {
        return TYPE;
    }
}

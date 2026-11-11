package de.lardtom.propwash.zone;

import de.lardtom.propwash.server.rules.PhysicsRules;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZoneWire {
    public static final int MAX_NAME_BYTES = ZoneLimits.MAX_NAME * 4;
    public static final int MAX_SPEC_NAME = 48;
    public static final int MAX_OWNER_BYTES = ZoneOwner.MAX_NAME * 4;

    private ZoneWire() {
    }

    public static void write(FriendlyByteBuf buf, Zone zone) {
        buf.writeVarInt(zone.id());
        buf.writeUtf(zone.dimension(), ZoneLimits.MAX_DIMENSION);
        buf.writeUtf(zone.name(), MAX_NAME_BYTES);
        buf.writeByte(zone.shape().ordinal());
        ZoneBox box = zone.box();
        buf.writeDouble(box.minX());
        buf.writeDouble(box.minY());
        buf.writeDouble(box.minZ());
        buf.writeDouble(box.maxX());
        buf.writeDouble(box.maxY());
        buf.writeDouble(box.maxZ());
        buf.writeInt(zone.priority());
        buf.writeBoolean(zone.raceLegal());
        buf.writeByte(zone.transitionTenths());
        buf.writeVarInt(zone.overrides().size());
        for (Map.Entry<String, Integer> entry : zone.overrides().entrySet()) {
            buf.writeUtf(entry.getKey(), MAX_SPEC_NAME);
            buf.writeVarInt(entry.getValue());
        }
        ZoneOwner owner = zone.owner();
        buf.writeBoolean(owner != null);
        if (owner != null) {
            buf.writeLong(owner.id().getMostSignificantBits());
            buf.writeLong(owner.id().getLeastSignificantBits());
            buf.writeUtf(owner.name(), MAX_OWNER_BYTES);
        }
        buf.writeVarInt(zone.world().size());
        for (String rule : zone.world()) {
            buf.writeUtf(rule, MAX_SPEC_NAME);
        }
    }

    public static void writeRelease(FriendlyByteBuf buf, ZoneRelease release) {
        writeFlags(buf, release.explicit());
    }

    public static ZoneRelease readRelease(FriendlyByteBuf buf) {
        return new ZoneRelease(readFlags(buf));
    }

    public static void writeFlags(FriendlyByteBuf buf, Map<String, Boolean> flags) {
        buf.writeVarInt(flags.size());
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            buf.writeUtf(entry.getKey(), ZoneRelease.MAX_ID);
            buf.writeBoolean(entry.getValue());
        }
    }

    public static Map<String, Boolean> readFlags(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > ZoneRelease.MAX_ENTRIES) {
            throw new DecoderException("Zu viele Freigaben für Zonen: " + count);
        }
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            flags.put(buf.readUtf(ZoneRelease.MAX_ID), buf.readBoolean());
        }
        return flags;
    }

    public static Zone read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String dimension = buf.readUtf(ZoneLimits.MAX_DIMENSION);
        String name = buf.readUtf(MAX_NAME_BYTES);
        int shape = buf.readUnsignedByte();
        ZoneBox box = new ZoneBox(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
        int priority = buf.readInt();
        boolean raceLegal = buf.readBoolean();
        int transition = buf.readUnsignedByte();
        int count = buf.readVarInt();
        if (count < 0 || count > PhysicsRules.SPECS.size()) {
            throw new DecoderException("Zu viele Zonen-Werte: " + count);
        }
        Map<String, Integer> overrides = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            overrides.put(buf.readUtf(MAX_SPEC_NAME), buf.readVarInt());
        }
        ZoneOwner owner = null;
        if (buf.readBoolean()) {
            owner = new ZoneOwner(new UUID(buf.readLong(), buf.readLong()), buf.readUtf(MAX_OWNER_BYTES));
        }
        int forcedCount = buf.readVarInt();
        if (forcedCount < 0 || forcedCount > PhysicsRules.SPECS.size()) {
            throw new DecoderException("Zu viele Zonen-Regeln mit Weltwert: " + forcedCount);
        }
        Set<String> world = new LinkedHashSet<>();
        for (int i = 0; i < forcedCount; i++) {
            world.add(buf.readUtf(MAX_SPEC_NAME));
        }
        ZoneShape[] shapes = ZoneShape.values();
        return new Zone(id, dimension, name, shape < shapes.length ? shapes[shape] : ZoneShape.BOX, box, priority, raceLegal, transition, overrides,
                owner, world);
    }
}

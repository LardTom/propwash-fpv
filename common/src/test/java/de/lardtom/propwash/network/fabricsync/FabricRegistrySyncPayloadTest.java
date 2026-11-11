package de.lardtom.propwash.network.fabricsync;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricRegistrySyncPayloadTest {
    private static final Identifier ITEM = Identifier.fromNamespaceAndPath("minecraft", "item");
    private static final Identifier GADGETS = Identifier.fromNamespaceAndPath("other_mod", "gadgets");
    private static final Identifier WIDGETS = Identifier.fromNamespaceAndPath("other_mod", "widgets");
    private static final Identifier SERIALIZERS = Identifier.fromNamespaceAndPath("fabric-object-builder-api-v1", "tracked_data_handler");

    @Test
    void decodesFabricWireFormat() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1);
        buf.writeUtf("");
        buf.writeVarInt(1);
        buf.writeUtf("item");
        buf.writeByte(0);
        buf.writeVarInt(2);
        buf.writeUtf("");
        buf.writeVarInt(1);
        buf.writeVarInt(0);
        buf.writeVarInt(2);
        buf.writeUtf("air");
        buf.writeUtf("stone");
        buf.writeUtf("propwash");
        buf.writeVarInt(2);
        buf.writeVarInt(3);
        buf.writeVarInt(1);
        buf.writeUtf("remote");
        buf.writeVarInt(2);
        buf.writeVarInt(2);
        buf.writeUtf("goggles");
        buf.writeUtf("charger");

        FabricRegistrySyncPayload payload = FabricRegistrySyncPayload.CODEC.decode(buf);

        Object2IntMap<Identifier> items = payload.registries().get(ITEM);
        assertEquals(5, items.size());
        assertEquals(0, items.getInt(Identifier.fromNamespaceAndPath("minecraft", "air")));
        assertEquals(1, items.getInt(Identifier.fromNamespaceAndPath("minecraft", "stone")));
        assertEquals(4, items.getInt(Identifier.fromNamespaceAndPath("propwash", "remote")));
        assertEquals(6, items.getInt(Identifier.fromNamespaceAndPath("propwash", "goggles")));
        assertEquals(7, items.getInt(Identifier.fromNamespaceAndPath("propwash", "charger")));
        assertEquals(0, buf.readableBytes());
    }

    private static FriendlyByteBuf twoRegistriesOneOptional() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1);
        buf.writeUtf("other_mod");
        buf.writeVarInt(2);
        buf.writeUtf("gadgets");
        buf.writeByte(1);
        buf.writeVarInt(1);
        buf.writeUtf("other_mod");
        buf.writeVarInt(1);
        buf.writeVarInt(0);
        buf.writeVarInt(1);
        buf.writeUtf("gadget");
        buf.writeUtf("widgets");
        buf.writeByte(0);
        buf.writeVarInt(1);
        buf.writeUtf("other_mod");
        buf.writeVarInt(1);
        buf.writeVarInt(0);
        buf.writeVarInt(1);
        buf.writeUtf("widget");
        return buf;
    }

    @Test
    void readsTheOptionalMarkOfARegistry() {
        FabricRegistrySyncPayload payload = FabricRegistrySyncPayload.CODEC.decode(twoRegistriesOneOptional());
        assertEquals(Set.of(GADGETS), payload.optionalRegistries());
        assertEquals(2, payload.registries().size());
    }

    @Test
    void unknownRegistryIsMissingOnlyWithoutTheOptionalMark() {
        FabricRegistrySyncPayload payload = FabricRegistrySyncPayload.CODEC.decode(twoRegistriesOneOptional());
        assertEquals(List.of(WIDGETS), payload.missingRegistries(id -> false));
        assertEquals(List.of(), payload.missingRegistries(id -> true));
        assertEquals(List.of(), payload.missingRegistries(WIDGETS::equals));
    }

    @Test
    void writesTheOptionalMark() {
        Object2IntMap<Identifier> entries = new Object2IntLinkedOpenHashMap<>();
        entries.put(Identifier.fromNamespaceAndPath("other_mod", "gadget"), 0);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FabricRegistrySyncPayload.CODEC.encode(buf, new FabricRegistrySyncPayload(Map.of(GADGETS, entries), Set.of(GADGETS)));
        assertEquals(Set.of(GADGETS), FabricRegistrySyncPayload.CODEC.decode(buf).optionalRegistries());
    }

    @Test
    void roundTripKeepsIdsAcrossGapsAndNamespaces() {
        Map<Identifier, Object2IntMap<Identifier>> registries = new LinkedHashMap<>();
        Object2IntMap<Identifier> items = new Object2IntLinkedOpenHashMap<>();
        items.put(Identifier.fromNamespaceAndPath("minecraft", "air"), 0);
        items.put(Identifier.fromNamespaceAndPath("minecraft", "stone"), 1);
        items.put(Identifier.fromNamespaceAndPath("justmoreparts", "frame_x"), 2);
        items.put(Identifier.fromNamespaceAndPath("minecraft", "dirt"), 3);
        items.put(Identifier.fromNamespaceAndPath("propwash", "remote"), 10);
        items.put(Identifier.fromNamespaceAndPath("propwash", "goggles"), 11);
        registries.put(ITEM, items);
        Object2IntMap<Identifier> serializers = new Object2IntLinkedOpenHashMap<>();
        serializers.put(Identifier.fromNamespaceAndPath("propwash", "drone_state"), 0);
        serializers.put(Identifier.fromNamespaceAndPath("propwash", "tune"), 1);
        registries.put(SERIALIZERS, serializers);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FabricRegistrySyncPayload.CODEC.encode(buf, new FabricRegistrySyncPayload(registries));
        FabricRegistrySyncPayload decoded = FabricRegistrySyncPayload.CODEC.decode(buf);

        assertEquals(registries, decoded.registries());
        assertEquals(0, buf.readableBytes());
    }
}

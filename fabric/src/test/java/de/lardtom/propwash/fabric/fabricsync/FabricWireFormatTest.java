package de.lardtom.propwash.fabric.fabricsync;

import de.lardtom.propwash.accessory.AccessoryMenu;
import de.lardtom.propwash.network.fabricsync.FabricOpenScreenPayload;
import de.lardtom.propwash.network.fabricsync.FabricRegistrySyncPayload;
import de.lardtom.propwash.network.fabricsync.FabricSyncCompletePayload;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.impl.menu.Networking;
import net.fabricmc.fabric.impl.registry.sync.SyncCompletePayload;
import net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricWireFormatTest {
    private static final Identifier ITEM = Identifier.fromNamespaceAndPath("minecraft", "item");
    private static final Identifier SERIALIZERS = Identifier.fromNamespaceAndPath("fabric-object-builder-api-v1", "tracked_data_handler");
    private static final Identifier OPTIONAL = Identifier.fromNamespaceAndPath("other_mod", "gadgets");

    private static final Identifier SERVICE_MENU = Identifier.fromNamespaceAndPath("propwash", "drone_service");
    private static final Identifier ACCESSORY_MENU = Identifier.fromNamespaceAndPath("propwash", "drone_accessories");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RegistryFriendlyByteBuf registryBuf() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @SuppressWarnings("unchecked")
    private static <D> FabricOpenScreenPayload throughFabric(Identifier menu, int containerId, Component title, StreamCodec<? super RegistryFriendlyByteBuf, D> codec, D data) {
        RegistryFriendlyByteBuf buf = registryBuf();
        Networking.OpenScreenPayload.CODEC.encode(buf, new Networking.OpenScreenPayload<>(menu, containerId, title, (StreamCodec<RegistryFriendlyByteBuf, D>) codec, data));
        FabricOpenScreenPayload read = FabricOpenScreenPayload.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        return read;
    }

    private static <D> D data(FabricOpenScreenPayload payload, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()), RegistryAccess.EMPTY);
        D data = codec.decode(buf);
        assertEquals(0, buf.readableBytes());
        return data;
    }

    private static Map<Identifier, Object2IntMap<Identifier>> registries() {
        Map<Identifier, Object2IntMap<Identifier>> registries = new LinkedHashMap<>();
        Object2IntMap<Identifier> items = new Object2IntLinkedOpenHashMap<>();
        items.put(Identifier.fromNamespaceAndPath("minecraft", "air"), 0);
        items.put(Identifier.fromNamespaceAndPath("minecraft", "stone"), 1);
        items.put(Identifier.fromNamespaceAndPath("minecraft", "dirt"), 3);
        items.put(Identifier.fromNamespaceAndPath("propwash", "remote"), 4);
        items.put(Identifier.fromNamespaceAndPath("propwash", "goggles"), 5);
        items.put(Identifier.fromNamespaceAndPath("justmoreparts", "frame_x"), 2);
        items.put(Identifier.fromNamespaceAndPath("justmoreparts", "frame_y"), 900);
        registries.put(ITEM, items);
        Object2IntMap<Identifier> serializers = new Object2IntLinkedOpenHashMap<>();
        serializers.put(Identifier.fromNamespaceAndPath("propwash", "tune"), 0);
        serializers.put(Identifier.fromNamespaceAndPath("propwash", "wear"), 1);
        registries.put(SERIALIZERS, serializers);
        Object2IntMap<Identifier> optional = new Object2IntLinkedOpenHashMap<>();
        optional.put(Identifier.fromNamespaceAndPath("other_mod", "gadget"), 7);
        registries.put(OPTIONAL, optional);
        return registries;
    }

    private static Map<Identifier, EnumSet<RegistryAttribute>> attributes() {
        Map<Identifier, EnumSet<RegistryAttribute>> attributes = new LinkedHashMap<>();
        attributes.put(ITEM, EnumSet.of(RegistryAttribute.SYNCED, RegistryAttribute.MODDED));
        attributes.put(SERIALIZERS, EnumSet.of(RegistryAttribute.SYNCED));
        attributes.put(OPTIONAL, EnumSet.of(RegistryAttribute.SYNCED, RegistryAttribute.OPTIONAL));
        return attributes;
    }

    @Test
    void identifiersMatchFabricApi() {
        assertEquals(RegistrySyncPayload.ID.id(), FabricRegistrySyncPayload.TYPE.id());
        assertEquals(SyncCompletePayload.ID.id(), FabricSyncCompletePayload.TYPE.id());
        assertEquals(Networking.OpenScreenPayload.ID.id(), FabricOpenScreenPayload.TYPE.id());
    }

    @Test
    void readsServiceMenuOpenedByFabricApi() {
        Component title = Component.translatable("container.propwash.drone_service");
        FabricOpenScreenPayload read = throughFabric(SERVICE_MENU, 100, title, ByteBufCodecs.VAR_INT, 300_000);
        assertEquals(SERVICE_MENU, read.menuType());
        assertEquals(100, read.containerId());
        assertEquals(title, read.title());
        assertEquals(300_000, data(read, ByteBufCodecs.VAR_INT));
    }

    @Test
    void readsAccessoryMenuOpenedByFabricApi() {
        int[] sizes = new int[AccessoryMenu.MOUNTS];
        sizes[0] = 4;
        sizes[sizes.length - 1] = 2;
        Component title = Component.literal("Zubehör ü ß");
        FabricOpenScreenPayload read = throughFabric(ACCESSORY_MENU, 1, title, AccessoryMenu.OPENING, new AccessoryMenu.Opening(4711, sizes));
        assertEquals(ACCESSORY_MENU, read.menuType());
        assertEquals(1, read.containerId());
        assertEquals(title, read.title());
        AccessoryMenu.Opening opening = data(read, AccessoryMenu.OPENING);
        assertEquals(4711, opening.droneId());
        assertArrayEquals(sizes, opening.sizes(), Arrays.toString(opening.sizes()));
    }

    @Test
    void fabricApiReadsOpenScreenWeWrite() {
        Networking.CODEC_BY_ID.put(SERVICE_MENU, ByteBufCodecs.VAR_INT);
        RegistryFriendlyByteBuf data = registryBuf();
        ByteBufCodecs.VAR_INT.encode(data, 77);
        byte[] bytes = new byte[data.readableBytes()];
        data.readBytes(bytes);
        RegistryFriendlyByteBuf buf = registryBuf();
        Component title = Component.translatable("container.propwash.drone_service");
        FabricOpenScreenPayload.CODEC.encode(buf, new FabricOpenScreenPayload(SERVICE_MENU, 9, title, bytes));
        Networking.OpenScreenPayload<?> read = Networking.OpenScreenPayload.CODEC.decode(buf);
        assertEquals(SERVICE_MENU, read.identifier());
        assertEquals(9, read.containerId());
        assertEquals(title, read.title());
        assertEquals(77, read.data());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void readsWhatFabricApiWrites() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RegistrySyncPayload.CODEC.encode(buf, new RegistrySyncPayload(registries(), attributes()));
        FabricRegistrySyncPayload read = FabricRegistrySyncPayload.CODEC.decode(buf);
        assertEquals(registries(), read.registries());
        assertEquals(Set.of(OPTIONAL), read.optionalRegistries());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void fabricApiReadsWhatWeWrite() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        FabricRegistrySyncPayload.CODEC.encode(buf, new FabricRegistrySyncPayload(registries(), Set.of(OPTIONAL)));
        RegistrySyncPayload read = RegistrySyncPayload.CODEC.decode(buf);
        assertEquals(registries(), read.registryMap());
        assertEquals(EnumSet.of(RegistryAttribute.OPTIONAL), read.registryAttributes().get(OPTIONAL));
        assertEquals(EnumSet.noneOf(RegistryAttribute.class), read.registryAttributes().get(ITEM));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void completePacketHasNoBody() {
        FriendlyByteBuf ours = new FriendlyByteBuf(Unpooled.buffer());
        FabricSyncCompletePayload.CODEC.encode(ours, FabricSyncCompletePayload.INSTANCE);
        assertEquals(0, ours.readableBytes());
        FriendlyByteBuf real = new FriendlyByteBuf(Unpooled.buffer());
        SyncCompletePayload.CODEC.encode(real, SyncCompletePayload.INSTANCE);
        assertEquals(0, real.readableBytes());
    }
}

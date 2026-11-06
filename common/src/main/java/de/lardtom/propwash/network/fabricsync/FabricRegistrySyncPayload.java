package de.lardtom.propwash.network.fabricsync;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record FabricRegistrySyncPayload(Map<Identifier, Object2IntMap<Identifier>> registries) implements CustomPacketPayload {
    public static final Type<FabricRegistrySyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabric", "registry/sync"));
    public static final StreamCodec<FriendlyByteBuf, FabricRegistrySyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.write(buf), FabricRegistrySyncPayload::read);
    private static final String DEFAULT_NAMESPACE = "minecraft";

    private static FabricRegistrySyncPayload read(FriendlyByteBuf buf) {
        Map<Identifier, Object2IntMap<Identifier>> registries = new LinkedHashMap<>();
        int registryNamespaces = buf.readVarInt();
        for (int i = 0; i < registryNamespaces; i++) {
            String registryNamespace = expand(buf.readUtf());
            int registryCount = buf.readVarInt();
            for (int j = 0; j < registryCount; j++) {
                String registryPath = buf.readUtf();
                buf.readByte();
                Object2IntMap<Identifier> entries = new Object2IntLinkedOpenHashMap<>();
                int namespaceGroups = buf.readVarInt();
                int lastRawId = 0;
                for (int k = 0; k < namespaceGroups; k++) {
                    String entryNamespace = expand(buf.readUtf());
                    int bulks = buf.readVarInt();
                    for (int b = 0; b < bulks; b++) {
                        int rawId = lastRawId + buf.readVarInt() - 1;
                        int size = buf.readVarInt();
                        for (int e = 0; e < size; e++) {
                            rawId++;
                            entries.put(Identifier.fromNamespaceAndPath(entryNamespace, buf.readUtf()), rawId);
                        }
                        lastRawId = rawId;
                    }
                }
                registries.put(Identifier.fromNamespaceAndPath(registryNamespace, registryPath), entries);
            }
        }
        return new FabricRegistrySyncPayload(registries);
    }

    private void write(FriendlyByteBuf buf) {
        Map<String, List<Map.Entry<Identifier, Object2IntMap<Identifier>>>> byNamespace = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Object2IntMap<Identifier>> registry : this.registries.entrySet()) {
            byNamespace.computeIfAbsent(registry.getKey().getNamespace(), key -> new ArrayList<>()).add(registry);
        }
        buf.writeVarInt(byNamespace.size());
        for (Map.Entry<String, List<Map.Entry<Identifier, Object2IntMap<Identifier>>>> namespace : byNamespace.entrySet()) {
            buf.writeUtf(shorten(namespace.getKey()));
            buf.writeVarInt(namespace.getValue().size());
            for (Map.Entry<Identifier, Object2IntMap<Identifier>> registry : namespace.getValue()) {
                buf.writeUtf(registry.getKey().getPath());
                buf.writeByte(0);
                writeEntries(buf, registry.getValue());
            }
        }
    }

    private static void writeEntries(FriendlyByteBuf buf, Object2IntMap<Identifier> entries) {
        List<Object2IntMap.Entry<Identifier>> sorted = new ArrayList<>(entries.object2IntEntrySet());
        sorted.sort(Comparator.comparingInt(Object2IntMap.Entry::getIntValue));
        List<List<Object2IntMap.Entry<Identifier>>> groups = new ArrayList<>();
        for (Object2IntMap.Entry<Identifier> entry : sorted) {
            List<Object2IntMap.Entry<Identifier>> last = groups.isEmpty() ? null : groups.getLast();
            if (last == null || !last.getFirst().getKey().getNamespace().equals(entry.getKey().getNamespace())) {
                last = new ArrayList<>();
                groups.add(last);
            }
            last.add(entry);
        }
        buf.writeVarInt(groups.size());
        int lastRawId = 0;
        for (List<Object2IntMap.Entry<Identifier>> group : groups) {
            buf.writeUtf(shorten(group.getFirst().getKey().getNamespace()));
            List<List<Object2IntMap.Entry<Identifier>>> bulks = new ArrayList<>();
            for (Object2IntMap.Entry<Identifier> entry : group) {
                List<Object2IntMap.Entry<Identifier>> bulk = bulks.isEmpty() ? null : bulks.getLast();
                if (bulk == null || bulk.getLast().getIntValue() + 1 != entry.getIntValue()) {
                    bulk = new ArrayList<>();
                    bulks.add(bulk);
                }
                bulk.add(entry);
            }
            buf.writeVarInt(bulks.size());
            for (List<Object2IntMap.Entry<Identifier>> bulk : bulks) {
                buf.writeVarInt(bulk.getFirst().getIntValue() - lastRawId);
                buf.writeVarInt(bulk.size());
                for (Object2IntMap.Entry<Identifier> entry : bulk) {
                    buf.writeUtf(entry.getKey().getPath());
                }
                lastRawId = bulk.getLast().getIntValue();
            }
        }
    }

    private static String expand(String namespace) {
        return namespace.isEmpty() ? DEFAULT_NAMESPACE : namespace;
    }

    private static String shorten(String namespace) {
        return DEFAULT_NAMESPACE.equals(namespace) ? "" : namespace;
    }

    @Override
    public Type<FabricRegistrySyncPayload> type() {
        return TYPE;
    }
}

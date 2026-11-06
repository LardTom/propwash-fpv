package de.lardtom.propwash.neoforge.fabricsync;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.network.fabricsync.FabricRegistrySyncPayload;
import de.lardtom.propwash.network.fabricsync.FabricSyncCompletePayload;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.neoforge.registries.RegistrySnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FabricRegistryBridge {
    public static final Identifier ENTITY_DATA_REGISTRY = Identifier.fromNamespaceAndPath("fabric-object-builder-api-v1", "tracked_data_handler");
    public static final String UNKNOWN_ENTRIES_KEY = "propwash.network.fabric_sync.unknown_entries";
    public static final String UNKNOWN_REGISTRIES_KEY = "propwash.network.fabric_sync.unknown_registries";
    public static final String FAILED_KEY = "propwash.network.fabric_sync.failed";
    private static final String FABRIC_REGISTRY_SYNC = "fabric_registry_sync_v0";
    private static final AtomicBoolean APPLIED = new AtomicBoolean();
    private static volatile Executor mainThread = Runnable::run;
    private static @Nullable List<EntityDataSerializer<?>> vanillaSerializers;

    private FabricRegistryBridge() {
    }

    public static boolean enabled() {
        return !ModList.get().isLoaded(FABRIC_REGISTRY_SYNC);
    }

    public static void mainThread(Executor executor) {
        mainThread = executor;
    }

    public static void handle(FabricRegistrySyncPayload payload, IPayloadContext context) {
        if (context.listener().getConnectionType().isNeoForge()) {
            return;
        }
        try {
            Component reject = apply(payload);
            if (reject != null) {
                revert();
                context.disconnect(reject);
                return;
            }
            revertOnClose(context.connection());
            context.reply(FabricSyncCompletePayload.INSTANCE);
        } catch (RuntimeException e) {
            Constants.LOG.error("Propwash: Fabric-Registerabgleich fehlgeschlagen", e);
            revert();
            context.disconnect(Component.translatableWithFallback(FAILED_KEY,
                    "Could not match this Fabric server's content: %s", e.toString()));
        }
    }

    public static @Nullable Component apply(FabricRegistrySyncPayload payload) {
        Map<Identifier, RegistrySnapshot> snapshots = new LinkedHashMap<>();
        Object2IntMap<Identifier> serializerIds = null;
        TreeSet<String> unknownRegistries = new TreeSet<>();
        for (Map.Entry<Identifier, Object2IntMap<Identifier>> registry : payload.registries().entrySet()) {
            Identifier id = registry.getKey();
            if (ENTITY_DATA_REGISTRY.equals(id)) {
                serializerIds = registry.getValue();
            } else if (BuiltInRegistries.REGISTRY.containsKey(id)) {
                snapshots.put(id, snapshot(registry.getValue()));
            } else if (!registry.getValue().isEmpty()) {
                unknownRegistries.add(id.toString());
            }
        }
        if (!unknownRegistries.isEmpty()) {
            return Component.translatableWithFallback(UNKNOWN_REGISTRIES_KEY,
                    "This Fabric server uses content types your game doesn't know: %s", String.join(", ", unknownRegistries));
        }
        List<EntityDataSerializer<?>> serializers = serializerIds == null ? List.of() : resolveSerializers(serializerIds);
        if (serializers == null) {
            return unknownEntries(namespaces(missingSerializers(serializerIds)));
        }
        APPLIED.set(true);
        Set<ResourceKey<?>> missing = RegistryManager.applySnapshot(snapshots, false);
        if (!missing.isEmpty()) {
            TreeSet<String> namespaces = new TreeSet<>();
            for (ResourceKey<?> key : missing) {
                namespaces.add(key.identifier().getNamespace());
            }
            Constants.LOG.info("Propwash: Fabric-Server hat {} Registereinträge, die dem Client fehlen: {}", missing.size(), missing);
            return unknownEntries(namespaces);
        }
        remapSerializers(serializers);
        Constants.LOG.info("Propwash: Register an den Fabric-Server angeglichen ({} Register, {} Entity-Data-Serializer)",
                snapshots.size(), serializers.size());
        return null;
    }

    public static void revert() {
        if (!APPLIED.getAndSet(false)) {
            return;
        }
        RegistryManager.revertToFrozen();
        List<EntityDataSerializer<?>> vanilla = vanillaSerializers;
        if (vanilla != null) {
            var map = EntityDataSerializers.SERIALIZERS;
            map.clear();
            vanilla.forEach(map::add);
        }
    }

    private static void revertOnClose(Connection connection) {
        connection.channel().closeFuture().addListener(future -> mainThread.execute(FabricRegistryBridge::revert));
    }

    private static RegistrySnapshot snapshot(Object2IntMap<Identifier> entries) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(entries.size());
            for (Object2IntMap.Entry<Identifier> entry : entries.object2IntEntrySet()) {
                buf.writeVarInt(entry.getIntValue());
                Identifier.STREAM_CODEC.encode(buf, entry.getKey());
            }
            buf.writeVarInt(0);
            return RegistrySnapshot.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static @Nullable List<EntityDataSerializer<?>> resolveSerializers(Object2IntMap<Identifier> ids) {
        List<Object2IntMap.Entry<Identifier>> sorted = new ArrayList<>(ids.object2IntEntrySet());
        sorted.sort(Comparator.comparingInt(Object2IntMap.Entry::getIntValue));
        List<EntityDataSerializer<?>> out = new ArrayList<>(sorted.size());
        for (Object2IntMap.Entry<Identifier> entry : sorted) {
            EntityDataSerializer<?> serializer = NeoForgeRegistries.ENTITY_DATA_SERIALIZERS.getValue(entry.getKey());
            if (serializer == null) {
                return null;
            }
            out.add(serializer);
        }
        return out;
    }

    private static List<Identifier> missingSerializers(Object2IntMap<Identifier> ids) {
        List<Identifier> out = new ArrayList<>();
        for (Identifier id : ids.keySet()) {
            if (!NeoForgeRegistries.ENTITY_DATA_SERIALIZERS.containsKey(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static void remapSerializers(List<EntityDataSerializer<?>> modded) {
        var map = EntityDataSerializers.SERIALIZERS;
        if (vanillaSerializers == null) {
            List<EntityDataSerializer<?>> vanilla = new ArrayList<>(map.size());
            map.forEach(vanilla::add);
            vanillaSerializers = List.copyOf(vanilla);
        }
        map.clear();
        vanillaSerializers.forEach(map::add);
        int base = vanillaSerializers.size();
        for (int i = 0; i < modded.size(); i++) {
            map.addMapping(modded.get(i), base + i);
        }
    }

    private static TreeSet<String> namespaces(List<Identifier> ids) {
        TreeSet<String> out = new TreeSet<>();
        ids.forEach(id -> out.add(id.getNamespace()));
        return out;
    }

    private static Component unknownEntries(TreeSet<String> namespaces) {
        return Component.translatableWithFallback(UNKNOWN_ENTRIES_KEY,
                "This Fabric server has content your game doesn't have (from: %s). Install the same mods as the server.",
                String.join(", ", namespaces));
    }
}

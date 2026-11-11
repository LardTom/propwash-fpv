package de.lardtom.propwash.drone.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneDataTest {
    private static final String NS = "dronedatatest";
    private static final Identifier MISSION_ID = Identifier.fromNamespaceAndPath(NS, "mission");
    private static final Identifier SECRET_ID = Identifier.fromNamespaceAndPath(NS, "secret");
    private static final Identifier FOREIGN_ID = Identifier.fromNamespaceAndPath("absent_addon", "route");
    private static final Mission MISSION = new Mission("Runde 1", List.of(new BlockPos(1, 64, -3), new BlockPos(20, 70, 5)), 3);
    private static final Key<Mission> MISSION_KEY = new Key<>(MISSION_ID, Mission.CODEC, Mission.EMPTY, DroneDataKey.Sync.TRACKING);
    private static final Key<String> SECRET_KEY = new Key<>(SECRET_ID, Codec.STRING, "", DroneDataKey.Sync.NONE);

    record Mission(String name, List<BlockPos> waypoints, int flights) {
        static final Mission EMPTY = new Mission("", List.of(), 0);
        static final Codec<Mission> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(Mission::name),
                BlockPos.CODEC.listOf().optionalFieldOf("waypoints", List.of()).forGetter(Mission::waypoints),
                Codec.INT.optionalFieldOf("flights", 0).forGetter(Mission::flights)).apply(i, Mission::new));
    }

    record Key<T>(Identifier id, Codec<T> codec, T defaultValue, Sync sync) implements DroneDataKey<T> {
    }

    @Test
    void codecRoundTripKeepsEveryValue() {
        AddonData data = AddonData.EMPTY.with(MISSION_KEY, MISSION, NbtOps.INSTANCE).with(SECRET_KEY, "geheim", NbtOps.INSTANCE);
        assertEquals(MISSION, data.value(MISSION_KEY, NbtOps.INSTANCE));
        assertEquals("geheim", data.value(SECRET_KEY, NbtOps.INSTANCE));

        Tag nbt = AddonData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        AddonData fromNbt = AddonData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        assertEquals(data, fromNbt);
        assertEquals(MISSION, fromNbt.value(MISSION_KEY, NbtOps.INSTANCE));

        JsonElement json = AddonData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(MISSION, AddonData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().value(MISSION_KEY, NbtOps.INSTANCE));

        ByteBuf buffer = Unpooled.buffer();
        AddonData.STREAM_CODEC.encode(buffer, data);
        AddonData fromNetwork = AddonData.STREAM_CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(data, fromNetwork);
        assertEquals("geheim", fromNetwork.value(SECRET_KEY, NbtOps.INSTANCE));
    }

    @Test
    void entityNbtHasTheDocumentedShape() {
        AddonData data = AddonData.EMPTY.with(MISSION_KEY, MISSION, NbtOps.INSTANCE);
        CompoundTag root = data.toTag();
        assertEquals(List.of(NS), List.copyOf(root.keySet()));
        CompoundTag entries = root.getCompoundOrEmpty(NS);
        assertEquals(List.of("mission"), List.copyOf(entries.keySet()));
        assertEquals(Mission.CODEC.encodeStart(NbtOps.INSTANCE, MISSION).getOrThrow(), entries.get("mission"));
        assertEquals(List.of(MISSION_ID), data.ids());
        assertEquals("AddonData", AddonData.SAVE_KEY);
    }

    @Test
    void itemComponentCarriesTheSameDataAsTheEntity() {
        AddonData entity = withForeign(AddonData.EMPTY.with(MISSION_KEY, MISSION, NbtOps.INSTANCE).with(SECRET_KEY, "nur am Server", NbtOps.INSTANCE));
        Tag stored = AddonData.CODEC.encodeStart(NbtOps.INSTANCE, entity).getOrThrow();
        assertEquals(entity.toTag(), stored);
        ByteBuf buffer = Unpooled.buffer();
        AddonData.STREAM_CODEC.encode(buffer, entity);
        AddonData item = AddonData.STREAM_CODEC.decode(buffer);
        assertEquals(entity, item);
        assertEquals(entity.hashCode(), item.hashCode());
        assertEquals(entity.toTag(), item.toTag());
        assertEquals("nur am Server", item.value(SECRET_KEY, NbtOps.INSTANCE));
        assertEquals(entity.raw(FOREIGN_ID), item.raw(FOREIGN_ID));

        CompoundTag source = entity.toTag();
        AddonData copy = AddonData.of(source);
        source.getCompoundOrEmpty(NS).putString("mission", "verändert");
        copy.toTag().getCompoundOrEmpty(NS).remove("mission");
        assertEquals(entity, copy);
        assertEquals(MISSION, copy.value(MISSION_KEY, NbtOps.INSTANCE));
    }

    @Test
    void perKeyLimitIsTwoKibibytes() {
        Key<String> blob = new Key<>(Identifier.fromNamespaceAndPath(NS, "blob"), Codec.STRING, "", DroneDataKey.Sync.NONE);
        String fits = "x".repeat(DroneDataAccess.MAX_BYTES_PER_KEY - 3);
        AddonData ok = AddonData.EMPTY.with(blob, fits, NbtOps.INSTANCE);
        assertEquals(DroneDataAccess.MAX_BYTES_PER_KEY, AddonData.bytes(ok.raw(blob.id()).orElseThrow()));
        IllegalArgumentException tooBig = assertThrows(IllegalArgumentException.class, () -> ok.with(blob, fits + "x", NbtOps.INSTANCE));
        assertTrue(tooBig.getMessage().contains(blob.id().toString()), tooBig.getMessage());
        assertTrue(tooBig.getMessage().contains("2049"), tooBig.getMessage());
        assertEquals(fits, ok.value(blob, NbtOps.INSTANCE));
    }

    @Test
    void perDroneLimitIsEightKibibytes() {
        AddonData data = AddonData.EMPTY;
        String chunk = "y".repeat(1900);
        int stored = 0;
        IllegalStateException full = null;
        for (int i = 0; i < 6 && full == null; i++) {
            Key<String> key = new Key<>(Identifier.fromNamespaceAndPath(NS, "part_" + i), Codec.STRING, "", DroneDataKey.Sync.NONE);
            try {
                data = data.with(key, chunk, NbtOps.INSTANCE);
                stored++;
            } catch (IllegalStateException e) {
                full = e;
            }
        }
        assertEquals(4, stored);
        assertTrue(full != null && full.getMessage().contains("8192"), String.valueOf(full));
        assertTrue(data.bytes() <= DroneDataAccess.MAX_BYTES_PER_DRONE, "Drohne " + data.bytes());
        assertEquals(4, data.ids().size());
        assertEquals(AddonData.bytes(data.toTag()), data.bytes());
    }

    @Test
    void foreignDataSurvivesWritesRemovesAndSaves() {
        AddonData before = withForeign(AddonData.EMPTY);
        Tag foreign = before.raw(FOREIGN_ID).orElseThrow();
        Tag broken = before.toTag().get("broken_addon");

        AddonData written = before.with(MISSION_KEY, MISSION, NbtOps.INSTANCE).with(SECRET_KEY, "a", NbtOps.INSTANCE);
        AddonData changed = written.with(MISSION_KEY, new Mission("Runde 2", List.of(), 4), NbtOps.INSTANCE).without(SECRET_ID);
        AddonData removed = changed.without(MISSION_ID);
        for (AddonData data : List.of(written, changed, removed)) {
            assertEquals(foreign, data.raw(FOREIGN_ID).orElseThrow());
            assertEquals(AddonData.bytes(foreign), AddonData.bytes(data.raw(FOREIGN_ID).orElseThrow()));
            assertEquals(broken, data.toTag().get("broken_addon"));
        }
        assertEquals(before, removed);
        assertFalse(removed.toTag().contains(NS));
        assertSame(removed, removed.without(MISSION_ID));
    }

    @Test
    void worldSavedWithoutTheAddOnLoadsUnchangedWithIt() {
        AddonData withAddOn = withForeign(AddonData.EMPTY.with(MISSION_KEY, MISSION, NbtOps.INSTANCE).with(SECRET_KEY, "s", NbtOps.INSTANCE));
        CompoundTag first = save(withAddOn);

        AddonData withoutAddOn = load(first);
        Key<Integer> other = new Key<>(Identifier.fromNamespaceAndPath("other_addon", "count"), Codec.INT, 0, DroneDataKey.Sync.TRACKING);
        CompoundTag second = save(withoutAddOn.with(other, 7, NbtOps.INSTANCE).without(other.id()));

        AddonData again = load(second);
        assertEquals(withAddOn, again);
        assertEquals(first, second);
        assertEquals(MISSION, again.value(MISSION_KEY, NbtOps.INSTANCE));
        assertEquals("s", again.value(SECRET_KEY, NbtOps.INSTANCE));
        assertEquals(AddonData.bytes(withAddOn.toTag()), AddonData.bytes(again.toTag()));
    }

    @Test
    void onlyTrackingKeysAreSynced() {
        Identifier tracked = Identifier.fromNamespaceAndPath(NS, "tracked_" + System.nanoTime());
        Identifier hidden = Identifier.fromNamespaceAndPath(NS, "hidden_" + System.nanoTime());
        DroneDataKey<Integer> trackedKey = PropwashApi.droneData().register(tracked, Codec.INT, 0, DroneDataKey.Sync.TRACKING);
        DroneDataKey<Integer> hiddenKey = PropwashApi.droneData().register(hidden, Codec.INT, 0, DroneDataKey.Sync.NONE);
        AddonData full = withForeign(AddonData.EMPTY.with(trackedKey, 5, NbtOps.INSTANCE).with(hiddenKey, 9, NbtOps.INSTANCE));

        AddonData synced = DroneDataStore.tracked(full);
        assertEquals(List.of(tracked), synced.ids());
        assertEquals(5, synced.value(trackedKey, NbtOps.INSTANCE));
        assertEquals(0, synced.value(hiddenKey, NbtOps.INSTANCE));
        assertFalse(synced.contains(FOREIGN_ID));
        assertFalse(synced.toTag().contains("broken_addon"));
        assertSame(AddonData.EMPTY, DroneDataStore.tracked(withForeign(AddonData.EMPTY)));
        assertEquals(9, full.value(hiddenKey, NbtOps.INSTANCE));
    }

    @Test
    void unreadableValuesFallBackToTheDefault() {
        AddonData data = AddonData.EMPTY.withRaw(MISSION_ID, StringTag.valueOf("keine Mission"));
        assertEquals(Mission.EMPTY, data.value(MISSION_KEY, NbtOps.INSTANCE));
        Key<String> missing = new Key<>(Identifier.fromNamespaceAndPath(NS, "missing"), Codec.STRING, "standard", DroneDataKey.Sync.NONE);
        assertEquals("standard", data.value(missing, NbtOps.INSTANCE));
        IllegalArgumentException unsaveable = assertThrows(IllegalArgumentException.class,
                () -> AddonData.EMPTY.with(new Key<>(MISSION_ID, Codec.intRange(0, 3), 0, DroneDataKey.Sync.NONE), 9, NbtOps.INSTANCE));
        assertTrue(unsaveable.getMessage().contains(MISSION_ID.toString()));
    }

    @Test
    void brokenNamespaceIsNeverOverwritten() {
        AddonData data = withForeign(AddonData.EMPTY);
        Key<Integer> intoBroken = new Key<>(Identifier.fromNamespaceAndPath("broken_addon", "x"), Codec.INT, 0, DroneDataKey.Sync.NONE);
        assertThrows(IllegalStateException.class, () -> data.with(intoBroken, 1, NbtOps.INSTANCE));
        assertEquals(StringTag.valueOf("kein Compound"), data.toTag().get("broken_addon"));
        assertFalse(data.contains(intoBroken.id()));
        assertEquals(Optional.empty(), data.raw(intoBroken.id()));
    }

    @Test
    void equalContentIsEqualAndEmptyIsShared() {
        AddonData a = AddonData.EMPTY.with(MISSION_KEY, MISSION, NbtOps.INSTANCE);
        AddonData b = AddonData.of(a.toTag());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, a.with(MISSION_KEY, Mission.EMPTY, NbtOps.INSTANCE));
        assertSame(AddonData.EMPTY, AddonData.of(new CompoundTag()));
        assertTrue(AddonData.EMPTY.isEmpty());
        assertEquals(2, AddonData.EMPTY.bytes());
    }

    private static AddonData withForeign(AddonData data) {
        CompoundTag route = new CompoundTag();
        route.put("ints", new IntArrayTag(new int[]{1, -2, 3}));
        route.put("longs", new LongArrayTag(new long[]{Long.MAX_VALUE, 0L}));
        route.put("flag", ByteTag.valueOf((byte) 1));
        ListTag mixed = new ListTag();
        mixed.add(IntTag.valueOf(4));
        mixed.add(StringTag.valueOf("fünf"));
        mixed.add(new CompoundTag());
        route.put("mixed", mixed);
        ListTag empty = new ListTag();
        route.put("empty", empty);
        route.put("nested", new CompoundTag());
        CompoundTag tag = data.withRaw(FOREIGN_ID, route).toTag();
        tag.put("broken_addon", StringTag.valueOf("kein Compound"));
        return AddonData.of(tag);
    }

    private static CompoundTag save(AddonData data) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        output.store(AddonData.SAVE_KEY, AddonData.CODEC, data);
        return output.buildResult();
    }

    private static AddonData load(CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag).read(AddonData.SAVE_KEY, AddonData.CODEC)
                .orElseThrow();
    }
}

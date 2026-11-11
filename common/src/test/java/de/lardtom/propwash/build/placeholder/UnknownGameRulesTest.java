package de.lardtom.propwash.build.placeholder;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.server.UnknownGameRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnknownGameRulesTest {
    private static final class Rules {
        private final Map<String, Integer> values;
        private Map<String, Tag> unknown = Map.of();

        private Rules(Map<String, Integer> values) {
            this.values = values;
        }
    }

    private static Codec<Rules> registry(Set<String> known) {
        Codec<String> key = Codec.STRING.validate(id -> known.contains(id) ? DataResult.success(id)
                : DataResult.error(() -> "Unknown registry key: " + id));
        return Codec.unboundedMap(key, Codec.INT).xmap(map -> new Rules(new HashMap<>(map)), rules -> rules.values);
    }

    private static Codec<Rules> carrying(Set<String> known) {
        return UnknownGameRules.carry(registry(known), known::contains, rules -> rules.unknown, (rules, unknown) -> rules.unknown = unknown);
    }

    private static CompoundTag saved() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minecraft:spawn_radius", 10);
        tag.putInt("propwash:wind", 3);
        tag.putInt("gone_addon:allow_tnt", 0);
        tag.putInt("gone_addon:range", 24);
        return tag;
    }

    @Test
    void withoutCarryingTheMissingAddonFailsTheWholeLoad() {
        DataResult<Rules> plain = registry(Set.of("minecraft:spawn_radius", "propwash:wind")).parse(NbtOps.INSTANCE, saved());
        assertTrue(plain.isError());
        assertEquals(2, plain.resultOrPartial().orElseThrow().values.size());
    }

    @Test
    void rulesOfAMissingAddonLoadWithoutErrorAndAreWrittenBack() {
        Codec<Rules> codec = carrying(Set.of("minecraft:spawn_radius", "propwash:wind"));
        DataResult<Rules> loaded = codec.parse(NbtOps.INSTANCE, saved());
        assertFalse(loaded.isError(), () -> loaded.error().orElseThrow().message());
        Rules rules = loaded.getOrThrow();
        assertEquals(Map.of("minecraft:spawn_radius", 10, "propwash:wind", 3), rules.values);
        assertEquals(Map.of("gone_addon:allow_tnt", IntTag.valueOf(0), "gone_addon:range", IntTag.valueOf(24)), rules.unknown);
        rules.values.put("propwash:wind", 5);
        CompoundTag written = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, rules).getOrThrow();
        CompoundTag expected = saved();
        expected.putInt("propwash:wind", 5);
        assertEquals(expected, written);
    }

    @Test
    void theAddonComingBackReadsItsValuesAgain() {
        Codec<Rules> without = carrying(Set.of("minecraft:spawn_radius", "propwash:wind"));
        Rules away = without.parse(NbtOps.INSTANCE, saved()).getOrThrow();
        Tag kept = without.encodeStart(NbtOps.INSTANCE, away).getOrThrow();
        Codec<Rules> back = carrying(Set.of("minecraft:spawn_radius", "propwash:wind", "gone_addon:allow_tnt", "gone_addon:range"));
        Rules again = back.parse(NbtOps.INSTANCE, kept).getOrThrow();
        assertEquals(0, again.values.get("gone_addon:allow_tnt"));
        assertEquals(24, again.values.get("gone_addon:range"));
        assertTrue(again.unknown.isEmpty());
    }

    @Test
    void knownValuesWinAndOtherFormatsWorkToo() {
        Codec<Rules> codec = carrying(Set.of("minecraft:spawn_radius"));
        JsonObject json = new JsonObject();
        json.addProperty("minecraft:spawn_radius", 4);
        json.addProperty("gone_addon:range", 12);
        Rules rules = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(Set.of("gone_addon:range"), rules.unknown.keySet());
        assertEquals(json, codec.encodeStart(JsonOps.INSTANCE, rules).getOrThrow());
        rules.unknown = Map.of("minecraft:spawn_radius", IntTag.valueOf(99), "gone_addon:range", IntTag.valueOf(12));
        assertEquals(json, codec.encodeStart(JsonOps.INSTANCE, rules).getOrThrow());
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createString("kaputt")).isError());
    }

    @Test
    void vanillaKeysAndBrokenKeysAreNeverCarried() {
        assertTrue(UnknownGameRules.known("minecraft:removed_rule"));
        assertTrue(UnknownGameRules.known("Kein Schlüssel"));
    }
}

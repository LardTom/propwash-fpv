package de.lardtom.propwash.server;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRuleMap;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class UnknownGameRules {
    private UnknownGameRules() {
    }

    public interface Carrier {
        Map<String, Tag> propwash$unknownGameRules();

        void propwash$setUnknownGameRules(Map<String, Tag> rules);
    }

    public static Codec<GameRuleMap> wrap(Codec<GameRuleMap> codec) {
        return carry(codec, UnknownGameRules::known, map -> carrier(map).propwash$unknownGameRules(),
                (map, rules) -> carrier(map).propwash$setUnknownGameRules(rules));
    }

    public static void copy(GameRuleMap from, GameRuleMap to) {
        carrier(to).propwash$setUnknownGameRules(carrier(from).propwash$unknownGameRules());
    }

    public static Map<String, Tag> unknown(GameRuleMap map) {
        return carrier(map).propwash$unknownGameRules();
    }

    public static boolean known(String key) {
        Identifier id = Identifier.tryParse(key);
        return id == null || Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) || BuiltInRegistries.GAME_RULE.containsKey(id);
    }

    public static <A> Codec<A> carry(Codec<A> codec, Predicate<String> known, Function<A, Map<String, Tag>> get,
                                     BiConsumer<A, Map<String, Tag>> set) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<MapLike<T>> map = ops.getMap(input);
                if (map.isError()) {
                    return codec.decode(ops, input);
                }
                Map<T, T> kept = new LinkedHashMap<>();
                Map<String, Tag> unknown = new LinkedHashMap<>();
                map.getOrThrow().entries().forEach(entry -> {
                    String key = ops.getStringValue(entry.getFirst()).result().orElse(null);
                    if (key != null && !known.test(key)) {
                        unknown.put(key, ops.convertTo(NbtOps.INSTANCE, entry.getSecond()));
                    } else {
                        kept.put(entry.getFirst(), entry.getSecond());
                    }
                });
                if (unknown.isEmpty()) {
                    return codec.decode(ops, input);
                }
                Map<String, Tag> carried = Collections.unmodifiableMap(unknown);
                return codec.decode(ops, ops.createMap(kept)).map(pair -> {
                    set.accept(pair.getFirst(), carried);
                    return pair;
                });
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                DataResult<T> encoded = codec.encode(input, ops, prefix);
                Map<String, Tag> unknown = get.apply(input);
                if (unknown == null || unknown.isEmpty()) {
                    return encoded;
                }
                return encoded.flatMap(value -> ops.getMap(value).flatMap(written -> {
                    Map<T, T> extra = new LinkedHashMap<>();
                    unknown.forEach((key, tag) -> {
                        if (written.get(key) == null) {
                            extra.put(ops.createString(key), NbtOps.INSTANCE.convertTo(ops, tag));
                        }
                    });
                    return extra.isEmpty() ? DataResult.success(value) : ops.mergeToMap(value, extra);
                }));
            }

            @Override
            public String toString() {
                return "UnknownGameRules[" + codec + "]";
            }
        };
    }

    private static Carrier carrier(GameRuleMap map) {
        return (Carrier) (Object) map;
    }
}

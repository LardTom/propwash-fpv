package de.lardtom.propwash.creative;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.api.part.PartKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CreativeCodecs {
    private static final int KIND_KEY_LENGTH = 16;

    public static final Codec<CreativeSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING).lenientOptionalFieldOf("base", Map.of()).forGetter(CreativeCodecs::baseKeys),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).lenientOptionalFieldOf("values", Map.of()).forGetter(CreativeSpec::values)
    ).apply(i, (base, values) -> new CreativeSpec(kinds(base), values)));

    public static final StreamCodec<ByteBuf, CreativeSpec> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(HashMap::new, ByteBufCodecs.stringUtf8(KIND_KEY_LENGTH),
                    ByteBufCodecs.stringUtf8(CreativeSpec.MAX_KEY_LENGTH), CreativeSpec.BASE_KINDS.size()), CreativeCodecs::baseKeys,
            ByteBufCodecs.<ByteBuf, String, Double, Map<String, Double>>map(HashMap::new, ByteBufCodecs.stringUtf8(CreativeSpec.MAX_KEY_LENGTH),
                    ByteBufCodecs.DOUBLE, CreativeSpec.MAX_VALUES), CreativeSpec::values,
            (base, values) -> new CreativeSpec(kinds(base), values));

    private CreativeCodecs() {
    }

    public static Map<String, String> baseKeys(CreativeSpec spec) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (Map.Entry<PartKind, String> entry : spec.base().entrySet()) {
            keys.put(entry.getKey().key(), entry.getValue());
        }
        return keys;
    }

    public static Map<PartKind, String> kinds(Map<String, String> keys) {
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            PartKind.byKey(entry.getKey()).ifPresent(kind -> base.put(kind, entry.getValue()));
        }
        return base;
    }
}

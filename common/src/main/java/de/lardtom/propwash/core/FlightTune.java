package de.lardtom.propwash.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public record FlightTune(Map<String, Float> values) {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_KEY_LENGTH = 64;
    public static final FlightTune EMPTY = new FlightTune(Map.of());

    public static final Codec<FlightTune> CODEC = Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
            .comapFlatMap(FlightTune::parse, FlightTune::values);

    public static final StreamCodec<ByteBuf, FlightTune> STREAM_CODEC = ByteBufCodecs.<ByteBuf, String, Float, Map<String, Float>>map(
                    HashMap::new, ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH), ByteBufCodecs.FLOAT, MAX_ENTRIES)
            .map(FlightTune::new, FlightTune::values);

    public FlightTune {
        if (values.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("FlightTune mit " + values.size() + " Einträgen (max. " + MAX_ENTRIES + ")");
        }
        TreeMap<String, Float> sorted = new TreeMap<>();
        for (Map.Entry<String, Float> e : values.entrySet()) {
            String key = e.getKey();
            Float value = e.getValue();
            if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("Ungültiger FlightTune-Schlüssel: " + key);
            }
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("Ungültiger FlightTune-Wert für " + key + ": " + value);
            }
            sorted.put(key, value);
        }
        values = Collections.unmodifiableMap(sorted);
    }

    public static DataResult<FlightTune> parse(Map<String, Float> values) {
        try {
            return DataResult.success(new FlightTune(values));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    public boolean has(String key) {
        return this.values.containsKey(key);
    }

    public float get(String key, float fallback) {
        Float value = this.values.get(key);
        return value != null ? value : fallback;
    }

    public FlightTune with(String key, float value) {
        Map<String, Float> copy = new HashMap<>(this.values);
        copy.put(key, value);
        return new FlightTune(copy);
    }

    public FlightTune without(String key) {
        if (!this.values.containsKey(key)) {
            return this;
        }
        Map<String, Float> copy = new HashMap<>(this.values);
        copy.remove(key);
        return new FlightTune(copy);
    }
}

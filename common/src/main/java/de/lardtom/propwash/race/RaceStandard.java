package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public record RaceStandard(Map<String, Integer> values) {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_ID = 128;
    public static final RaceStandard DEFAULTS = new RaceStandard(Map.of());
    public static final Codec<RaceStandard> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(RaceStandard::new, RaceStandard::values);

    public RaceStandard {
        Map<String, Integer> out = new TreeMap<>();
        if (values != null) {
            for (Map.Entry<String, Integer> entry : new TreeMap<>(values).entrySet()) {
                String key = entry.getKey();
                Integer value = entry.getValue();
                if (key == null || value == null || key.isEmpty() || key.length() > MAX_ID || out.size() >= MAX_ENTRIES) {
                    continue;
                }
                Optional<RaceRules.Rule> rule = RaceRules.find(key);
                if (rule.isPresent()) {
                    RaceRules.Rule known = rule.get();
                    if (!known.relevant() || !known.valid(value) || known.defaultValue() == value) {
                        continue;
                    }
                    out.put(known.id(), value);
                } else {
                    out.put(key, value);
                }
            }
        }
        values = Collections.unmodifiableMap(out);
    }

    public int value(RaceRules.Rule rule) {
        Integer value = this.values.get(rule.id());
        return value == null ? rule.defaultValue() : value;
    }

    public boolean isDefault() {
        return this.differences().isEmpty();
    }

    public RaceStandard with(String key, int value) {
        Map<String, Integer> copy = new LinkedHashMap<>(this.values);
        copy.put(RaceRules.id(key), value);
        return new RaceStandard(copy);
    }

    public List<RaceRules.Rule> differences() {
        List<RaceRules.Rule> out = new ArrayList<>();
        for (RaceRules.Rule rule : RaceRules.relevant()) {
            if (this.value(rule) != rule.defaultValue()) {
                out.add(rule);
            }
        }
        return out;
    }

    public List<RaceRules.Rule> differences(Map<String, Integer> current) {
        List<RaceRules.Rule> out = new ArrayList<>();
        for (RaceRules.Rule rule : RaceRules.relevant()) {
            if (this.value(rule) != RaceRules.value(current, rule)) {
                out.add(rule);
            }
        }
        return out;
    }

    public static RaceStandard adopt(Map<String, Integer> current) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (RaceRules.Rule rule : RaceRules.relevant()) {
            out.put(rule.id(), RaceRules.value(current, rule));
        }
        return new RaceStandard(out);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.values.size());
        for (Map.Entry<String, Integer> entry : this.values.entrySet()) {
            buf.writeUtf(entry.getKey(), MAX_ID);
            buf.writeVarInt(entry.getValue());
        }
    }

    public static RaceStandard read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Rennstandard mit " + count + " Regeln");
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            values.put(buf.readUtf(MAX_ID), buf.readVarInt());
        }
        return new RaceStandard(values);
    }
}

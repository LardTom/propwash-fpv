package de.lardtom.propwash.creative;

import de.lardtom.propwash.api.part.PartKind;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;

public record CreativeSpec(Map<PartKind, String> base, Map<String, Double> values) {
    public static final List<PartKind> BASE_KINDS = List.of(PartKind.FRAME, PartKind.STACK, PartKind.MOTOR, PartKind.PROP, PartKind.BATTERY);
    public static final int MAX_VALUES = 96;
    public static final int MAX_KEY_LENGTH = 128;
    public static final CreativeSpec EMPTY = new CreativeSpec(Map.of(), Map.of());

    public CreativeSpec {
        EnumMap<PartKind, String> cleanBase = new EnumMap<>(PartKind.class);
        if (base != null) {
            for (Map.Entry<PartKind, String> entry : base.entrySet()) {
                String key = entry.getValue();
                if (entry.getKey() != null && BASE_KINDS.contains(entry.getKey()) && key != null && !key.isEmpty() && key.length() <= MAX_KEY_LENGTH) {
                    cleanBase.put(entry.getKey(), key);
                }
            }
        }
        base = Collections.unmodifiableMap(cleanBase);
        TreeMap<String, Double> cleanValues = new TreeMap<>();
        if (values != null) {
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                String key = entry.getKey();
                Double value = entry.getValue();
                if (key != null && !key.isEmpty() && key.length() <= MAX_KEY_LENGTH && value != null && Double.isFinite(value)
                        && cleanValues.size() < MAX_VALUES) {
                    cleanValues.put(key, value == 0.0 ? 0.0 : value);
                }
            }
        }
        values = Collections.unmodifiableMap(cleanValues);
    }

    public boolean isEmpty() {
        return this.base.isEmpty() && this.values.isEmpty();
    }

    public Optional<String> base(PartKind kind) {
        return Optional.ofNullable(this.base.get(kind));
    }

    public OptionalDouble value(String key) {
        Double value = this.values.get(key);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    public OptionalDouble value(CreativeParam param) {
        return this.value(param.key());
    }

    public boolean has(CreativeParam param) {
        return this.values.containsKey(param.key());
    }

    public boolean hasAny(List<CreativeParam> params) {
        for (CreativeParam param : params) {
            if (this.has(param)) {
                return true;
            }
        }
        return false;
    }

    public CreativeSpec with(CreativeParam param, double value) {
        TreeMap<String, Double> next = new TreeMap<>(this.values);
        next.put(param.key(), value);
        return new CreativeSpec(this.base, next);
    }

    public CreativeSpec without(CreativeParam param) {
        if (!this.has(param)) {
            return this;
        }
        TreeMap<String, Double> next = new TreeMap<>(this.values);
        next.remove(param.key());
        return new CreativeSpec(this.base, next);
    }

    public CreativeSpec without(List<CreativeParam> params) {
        TreeMap<String, Double> next = new TreeMap<>(this.values);
        for (CreativeParam param : params) {
            next.remove(param.key());
        }
        return next.size() == this.values.size() ? this : new CreativeSpec(this.base, next);
    }

    public CreativeSpec withBase(PartKind kind, String key) {
        EnumMap<PartKind, String> next = new EnumMap<>(PartKind.class);
        next.putAll(this.base);
        next.put(kind, key);
        return new CreativeSpec(next, this.values);
    }

    public CreativeSpec withoutBase(PartKind kind) {
        if (!this.base.containsKey(kind)) {
            return this;
        }
        EnumMap<PartKind, String> next = new EnumMap<>(PartKind.class);
        next.putAll(this.base);
        next.remove(kind);
        return new CreativeSpec(next, this.values);
    }

    public CreativeSpec withValues(Map<String, Double> values) {
        return new CreativeSpec(this.base, values);
    }

    public CreativeSpec withBase(Map<PartKind, String> base) {
        return new CreativeSpec(base, this.values);
    }
}

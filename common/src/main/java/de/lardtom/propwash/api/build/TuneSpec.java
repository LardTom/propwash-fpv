package de.lardtom.propwash.api.build;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record TuneSpec(Map<String, Float> values) {
    public static final int MAX_ENTRIES = 256;
    public static final TuneSpec EMPTY = new TuneSpec(Map.of());
    public static final String CAMERA_UPTILT = "camera.uptiltDeg";

    public TuneSpec {
        TreeMap<String, Float> copy = new TreeMap<>();
        for (Map.Entry<String, Float> entry : Objects.requireNonNullElse(values, Map.<String, Float>of()).entrySet()) {
            String key = entry.getKey();
            Float value = entry.getValue();
            if (key == null || key.isEmpty() || key.length() > 64) {
                throw new IllegalArgumentException("Tuning-Schlüssel muss 1–64 Zeichen haben: " + key);
            }
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("Tuning-Wert muss endlich sein: " + key);
            }
            copy.put(key, value);
        }
        if (copy.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Höchstens " + MAX_ENTRIES + " Tuning-Werte");
        }
        values = Collections.unmodifiableMap(copy);
    }

    public TuneSpec only(Collection<TuneGroup> groups) {
        TreeMap<String, Float> copy = new TreeMap<>();
        for (Map.Entry<String, Float> entry : this.values.entrySet()) {
            if (groups.contains(TuneGroup.of(entry.getKey()))) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return new TuneSpec(copy);
    }

    public TuneSpec without(Collection<TuneGroup> groups) {
        TreeMap<String, Float> copy = new TreeMap<>();
        for (Map.Entry<String, Float> entry : this.values.entrySet()) {
            if (!groups.contains(TuneGroup.of(entry.getKey()))) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return new TuneSpec(copy);
    }

    public TuneSpec overlay(TuneSpec imported, Collection<TuneGroup> groups) {
        TreeMap<String, Float> copy = new TreeMap<>(this.without(groups).values);
        copy.putAll(imported.only(groups).values);
        return new TuneSpec(copy);
    }
}

package de.lardtom.propwash.api.build;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PaintSpec(Map<String, Integer> colors) {
    public static final List<String> SLOTS = List.of("frame", "tpu", "motors", "prop_fl", "prop_fr", "prop_rl", "prop_rr", "stack",
            "battery", "camera", "antenna", "accessories");
    public static final PaintSpec DEFAULT = new PaintSpec(Map.of());

    public PaintSpec {
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>();
        Map<String, Integer> source = Objects.requireNonNullElse(colors, Map.of());
        for (String slot : SLOTS) {
            Integer rgb = source.get(slot);
            if (rgb != null) {
                if ((rgb & 0xFF000000) != 0) {
                    throw new IllegalArgumentException("Lackfarbe ist RGB ohne Alpha: " + slot + "=" + Integer.toHexString(rgb));
                }
                copy.put(slot, rgb);
            }
        }
        for (String key : source.keySet()) {
            if (!SLOTS.contains(key)) {
                throw new IllegalArgumentException("Unbekannter Lack-Platz: " + key);
            }
        }
        colors = Collections.unmodifiableMap(copy);
    }

    public boolean isDefault() {
        return this.colors.isEmpty();
    }

    public Optional<Integer> color(String slot) {
        return Optional.ofNullable(this.colors.get(slot));
    }

    public PaintSpec with(String slot, int rgb) {
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>(this.colors);
        copy.put(slot, rgb);
        return new PaintSpec(copy);
    }

    public PaintSpec without(String slot) {
        if (!this.colors.containsKey(slot)) {
            return this;
        }
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>(this.colors);
        copy.remove(slot);
        return new PaintSpec(copy);
    }
}

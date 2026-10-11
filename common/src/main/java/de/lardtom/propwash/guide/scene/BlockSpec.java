package de.lardtom.propwash.guide.scene;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record BlockSpec(String id, Map<String, String> properties) {
    public BlockSpec {
        Objects.requireNonNull(id, "id");
        properties = Map.copyOf(properties);
    }

    public static BlockSpec of(String id) {
        return new BlockSpec(id, Map.of());
    }

    public BlockSpec with(String property, String value) {
        Map<String, String> copy = new LinkedHashMap<>(this.properties);
        copy.put(property, value);
        return new BlockSpec(this.id, copy);
    }
}

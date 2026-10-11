package de.lardtom.propwash.drone.client.render;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PaintedShapes {
    public static final int MAX_CACHED = 512;
    private static final Map<Key, Shape> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Shape> eldest) {
            return this.size() > MAX_CACHED;
        }
    };

    private PaintedShapes() {
    }

    public static synchronized Shape recolor(Shape shape, int baseRgb, int rgb) {
        if (shape.isEmpty()) {
            return shape;
        }
        return CACHE.computeIfAbsent(new Key(shape, baseRgb & 0xFFFFFF, rgb & 0xFFFFFF), key -> shape.recolored(key.base(), key.rgb()));
    }

    static synchronized int cached() {
        return CACHE.size();
    }

    private record Key(Shape shape, int base, int rgb) {
    }
}

package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdCoverage;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

final class OsdTextCache {
    static final int FRAME_BUDGET = 16;
    private static final int TEXT_CAPACITY = 512;
    private static final int RECT_CAPACITY = 128;
    private static final float EDGE_STEPS = 1024.0F;

    record Shape(float[] quads, int outlineCount, int fillCount, int top, int[] fillBounds, int[] allBounds) {
        static Shape of(OsdCoverage.Shapes shapes, int top) {
            int outline = shapes.outlineCount();
            int fill = shapes.fillCount();
            float[] quads = new float[(outline + fill) * 4];
            System.arraycopy(shapes.outline(), 0, quads, 0, outline * 4);
            System.arraycopy(shapes.fill(), 0, quads, outline * 4, fill * 4);
            return new Shape(quads, outline, fill, top, bounds(quads, outline, outline + fill), bounds(quads, 0, outline + fill));
        }

        private static int[] bounds(float[] quads, int from, int to) {
            if (to <= from) {
                return new int[4];
            }
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int i = from; i < to; i++) {
                minX = Math.min(minX, quads[i * 4]);
                minY = Math.min(minY, quads[i * 4 + 1]);
                maxX = Math.max(maxX, quads[i * 4 + 2]);
                maxY = Math.max(maxY, quads[i * 4 + 3]);
            }
            int bx = (int) Math.floor(minX);
            int by = (int) Math.floor(minY);
            return new int[]{bx, by, Math.max(1, (int) Math.ceil(maxX) - bx), Math.max(1, (int) Math.ceil(maxY) - by)};
        }

        boolean isEmpty() {
            return this.outlineCount == 0 && this.fillCount == 0;
        }
    }

    private static final Shape UNSUPPORTED = Shape.of(OsdCoverage.Shapes.NONE, 0);

    private record TextKey(String text, OsdConfig.Outline outline, float edge) {
    }

    private static final class RectKey {
        private final int[] values;
        private final int length;
        private final OsdConfig.Outline outline;
        private final float edge;
        private final int hash;

        RectKey(int[] values, int length, OsdConfig.Outline outline, float edge) {
            this.values = values;
            this.length = length;
            this.outline = outline;
            this.edge = edge;
            int h = outline.hashCode() * 31 + Float.hashCode(edge);
            for (int i = 0; i < length; i++) {
                h = h * 31 + values[i];
            }
            this.hash = h;
        }

        RectKey copy() {
            return new RectKey(Arrays.copyOf(this.values, this.length), this.length, this.outline, this.edge);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RectKey key && key.hash == this.hash && key.length == this.length && key.outline == this.outline
                    && Float.compare(key.edge, this.edge) == 0 && Arrays.equals(key.values, 0, key.length, this.values, 0, this.length);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    private final OsdGlyphs glyphs;
    private final int budget;
    private final Map<TextKey, Shape> texts = lru(TEXT_CAPACITY);
    private final Map<RectKey, Shape> rects = lru(RECT_CAPACITY);
    private int generation;
    private int spent;
    private int clears;
    private int computed;
    private int unsupported;
    private int deferred;

    OsdTextCache(OsdGlyphs glyphs, int budget) {
        this.glyphs = glyphs;
        this.budget = budget;
        this.generation = glyphs.generation();
    }

    private static <K> Map<K, Shape> lru(int capacity) {
        return new LinkedHashMap<>(capacity, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Shape> eldest) {
                return this.size() > capacity;
            }
        };
    }

    static float quantizeEdge(float edge) {
        return Math.round(edge * EDGE_STEPS) / EDGE_STEPS;
    }

    void nextFrame() {
        this.spent = 0;
    }

    @Nullable Shape text(String text, OsdConfig.Outline outline, float edge, ToIntFunction<String> width) {
        this.sync();
        TextKey key = new TextKey(text, outline, edge);
        Shape cached = this.texts.get(key);
        if (cached != null) {
            return cached == UNSUPPORTED ? null : cached;
        }
        if (!this.glyphs.ready()) {
            return null;
        }
        if (this.spent >= this.budget) {
            this.deferred++;
            return null;
        }
        OsdGlyphs.Bitmap bitmap = this.glyphs.layout(text, width);
        if (bitmap == null) {
            return null;
        }
        this.spent++;
        if (bitmap == OsdGlyphs.UNSUPPORTED) {
            this.unsupported++;
            this.texts.put(key, UNSUPPORTED);
            return null;
        }
        Shape shape = Shape.of(OsdCoverage.mask(bitmap.mask(), bitmap.width(), bitmap.height(), outline, edge), bitmap.top());
        this.computed++;
        this.texts.put(key, shape);
        return shape;
    }

    Shape rects(int[] values, int count, OsdConfig.Outline outline, float edge) {
        RectKey probe = new RectKey(values, Math.min(count, values.length / 4) * 4, outline, edge);
        Shape cached = this.rects.get(probe);
        if (cached != null) {
            return cached;
        }
        Shape shape = Shape.of(OsdCoverage.rects(values, probe.length / 4, outline, edge), 0);
        this.rects.put(probe.copy(), shape);
        return shape;
    }

    private void sync() {
        int current = this.glyphs.generation();
        if (current != this.generation) {
            this.generation = current;
            if (!this.texts.isEmpty()) {
                this.texts.clear();
                this.clears++;
            }
        }
    }

    int clears() {
        return this.clears;
    }

    int computed() {
        return this.computed;
    }

    int unsupported() {
        return this.unsupported;
    }

    int deferred() {
        return this.deferred;
    }

    int size() {
        return this.texts.size();
    }
}

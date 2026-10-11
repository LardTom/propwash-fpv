package de.lardtom.propwash.osd.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;

final class OsdTestFont implements Function<OsdGlyphs.Options, Map<Integer, OsdGlyphs.Glyph>> {
    static final String CHARS = "0123456789.,:-+%/°ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static final int SPACE = 4;

    final AtomicInteger loads = new AtomicInteger();

    @Override
    public Map<Integer, OsdGlyphs.Glyph> apply(OsdGlyphs.Options options) {
        this.loads.incrementAndGet();
        Map<Integer, OsdGlyphs.Glyph> map = new HashMap<>();
        map.put((int) ' ', new OsdGlyphs.Glyph(0, 0, 0, new boolean[0], SPACE));
        if (options.uniform()) {
            return map;
        }
        CHARS.codePoints().forEach(codepoint -> map.put(codepoint, glyph(codepoint)));
        return map;
    }

    static OsdGlyphs.Glyph glyph(int codepoint) {
        int width = 3 + codepoint % 3;
        boolean[] mask = new boolean[width * 7];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = ((codepoint * 31 + i * 7) & 3) != 0;
        }
        return new OsdGlyphs.Glyph(width, 7, 0, mask, width + 1);
    }

    static int advances(String text) {
        return text.codePoints().map(codepoint -> codepoint == ' ' ? SPACE : glyph(codepoint).advance()).sum();
    }

    static ToIntFunction<String> matching() {
        return OsdTestFont::advances;
    }

    static ToIntFunction<String> skewed(AtomicInteger calls) {
        return text -> {
            calls.incrementAndGet();
            return advances(text) + 1;
        };
    }
}

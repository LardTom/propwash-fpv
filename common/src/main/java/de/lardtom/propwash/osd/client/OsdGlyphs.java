package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.Constants;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;

final class OsdGlyphs {
    private static final int REPORT_LIMIT = 256;

    record Options(boolean uniform, boolean japanese) {
        private static final Options[] ALL = {
                new Options(false, false), new Options(false, true), new Options(true, false), new Options(true, true)};

        static Options of(boolean uniform, boolean japanese) {
            return ALL[(uniform ? 2 : 0) + (japanese ? 1 : 0)];
        }
    }

    record Glyph(int width, int height, int top, boolean[] mask, int advance) {
    }

    record Bitmap(boolean[] mask, int width, int height, int top) {
    }

    record Table(Options options, Map<Integer, Glyph> glyphs) {
    }

    static final Bitmap UNSUPPORTED = new Bitmap(new boolean[0], 0, 0, 0);
    private static final Bitmap BLANK = new Bitmap(new boolean[0], 0, 0, 0);

    private final Function<Options, Map<Integer, Glyph>> loader;
    private final Executor executor;
    private final AtomicInteger loads = new AtomicInteger();
    private final Set<String> reported = new HashSet<>();
    private volatile @Nullable Thread lastBuildThread;
    private @Nullable Table table;
    private @Nullable CompletableFuture<Table> pending;
    private @Nullable Options pendingOptions;
    private int generation;

    OsdGlyphs(Function<Options, Map<Integer, Glyph>> loader, Executor executor) {
        this.loader = loader;
        this.executor = executor;
    }

    int generation() {
        return this.generation;
    }

    int loads() {
        return this.loads.get();
    }

    @Nullable Thread lastBuildThread() {
        return this.lastBuildThread;
    }

    boolean ready() {
        return this.table != null;
    }

    @Nullable Table table() {
        return this.table;
    }

    boolean building() {
        return this.pending != null;
    }

    void update(Options options) {
        this.poll();
        Table current = this.table;
        if (current != null && current.options().equals(options)) {
            return;
        }
        if (current != null) {
            this.table = null;
            this.generation++;
        }
        if (this.pending != null && options.equals(this.pendingOptions)) {
            return;
        }
        this.pendingOptions = options;
        this.pending = CompletableFuture.supplyAsync(() -> this.build(options, this.loader), this.executor);
        this.poll();
    }

    Table build(Options options, Function<Options, Map<Integer, Glyph>> source) {
        this.loads.incrementAndGet();
        this.lastBuildThread = Thread.currentThread();
        Map<Integer, Glyph> glyphs;
        try {
            glyphs = Map.copyOf(source.apply(options));
        } catch (RuntimeException e) {
            Constants.LOG.warn("OSD-Schrift konnte nicht gelesen werden, das OSD zeichnet Text mit der Spielschrift", e);
            glyphs = Map.of();
        }
        return new Table(options, glyphs);
    }

    void install(Table table) {
        this.pending = null;
        this.pendingOptions = null;
        this.table = table;
        this.generation++;
        this.reported.clear();
    }

    private void poll() {
        CompletableFuture<Table> future = this.pending;
        Options options = this.pendingOptions;
        if (future == null || options == null || !future.isDone()) {
            return;
        }
        this.install(future.isCompletedExceptionally() ? new Table(options, Map.of()) : future.join());
    }

    @Nullable Bitmap layout(String text, ToIntFunction<String> width) {
        Table current = this.table;
        if (current == null) {
            return null;
        }
        Map<Integer, Glyph> map = current.glyphs();
        int pen = 0;
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        int[] codepoints = text.codePoints().toArray();
        Glyph[] line = new Glyph[codepoints.length];
        for (int i = 0; i < codepoints.length; i++) {
            Glyph glyph = map.get(codepoints[i]);
            if (glyph == null) {
                return UNSUPPORTED;
            }
            line[i] = glyph;
            if (glyph.height() > 0 && glyph.width() > 0) {
                top = Math.min(top, glyph.top());
                bottom = Math.max(bottom, glyph.top() + glyph.height());
            }
            pen += glyph.advance();
        }
        int expected = width.applyAsInt(text);
        if (pen != expected) {
            this.report(text, pen, expected);
            return UNSUPPORTED;
        }
        if (top == Integer.MAX_VALUE) {
            return BLANK;
        }
        int height = bottom - top;
        boolean[] mask = new boolean[pen * height];
        int x = 0;
        for (Glyph glyph : line) {
            for (int gy = 0; gy < glyph.height(); gy++) {
                int row = glyph.top() - top + gy;
                for (int gx = 0; gx < glyph.width(); gx++) {
                    if (glyph.mask()[gy * glyph.width() + gx] && x + gx < pen) {
                        mask[row * pen + x + gx] = true;
                    }
                }
            }
            x += glyph.advance();
        }
        return new Bitmap(mask, pen, height, top);
    }

    private void report(String text, int pen, int expected) {
        if (this.reported.size() < REPORT_LIMIT && this.reported.add(text)) {
            Constants.LOG.debug("OSD-Text \"{}\" ist mit den Glyphen {} px breit, in der Spielschrift {} px; er wird mit der Spielschrift gezeichnet",
                    text, pen, expected);
        }
    }
}

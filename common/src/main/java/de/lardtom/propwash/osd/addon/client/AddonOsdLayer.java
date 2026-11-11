package de.lardtom.propwash.osd.addon.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import de.lardtom.propwash.api.client.osd.OsdWarning;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class AddonOsdLayer {
    private static final Set<Identifier> FAILED = ConcurrentHashMap.newKeySet();
    private static volatile @Nullable AddonOsdLayer live;

    private final Supplier<List<OsdElementSpec>> specs;
    private final Supplier<List<Api17Registries.Registered<OsdWarning>>> warnings;
    private final Contexts contexts;
    private final Map<Identifier, Shown> entries = new HashMap<>();
    private final List<Shown> shown = new ArrayList<>();
    private final List<OsdText> warningTexts = new ArrayList<>();
    private @Nullable OsdText shownWarning;
    private @Nullable OsdContext context;

    @FunctionalInterface
    public interface Contexts {
        OsdContext create(OsdData data, boolean preview);
    }

    public AddonOsdLayer() {
        this(Api17Registries.Client::osdElements, Api17Registries.Client::osdWarnings, AddonOsdContext::create);
    }

    public AddonOsdLayer(Supplier<List<OsdElementSpec>> specs, Supplier<List<Api17Registries.Registered<OsdWarning>>> warnings, Contexts contexts) {
        this.specs = Objects.requireNonNull(specs, "specs");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    public static @Nullable AddonOsdLayer live() {
        return live;
    }

    public static boolean failed(Identifier id) {
        return FAILED.contains(id);
    }

    public List<OsdElementSpec> specs() {
        return this.specs.get();
    }

    public @Nullable OsdElementSpec spec(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        for (OsdElementSpec spec : this.specs.get()) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        return null;
    }

    public void collect(OsdData data, OsdConfig config, boolean editor) {
        this.collect(data, config, editor, !editor);
    }

    public void collectElements(OsdData data, OsdConfig config) {
        this.collect(data, config, false, false);
    }

    private void collect(OsdData data, OsdConfig config, boolean editor, boolean withWarnings) {
        if (!editor) {
            live = this;
        }
        for (Shown entry : this.shown) {
            entry.drawn = false;
        }
        this.shown.clear();
        this.warningTexts.clear();
        this.shownWarning = null;
        List<OsdElementSpec> all = this.specs.get();
        List<Api17Registries.Registered<OsdWarning>> registered = withWarnings ? this.warnings.get() : List.of();
        if (all.isEmpty() && registered.isEmpty()) {
            return;
        }
        OsdContext context = this.contexts.create(data, editor);
        if (!editor) {
            this.context = context;
        }
        for (OsdElementSpec spec : all) {
            OsdConfig.Slot slot = AddonOsdKeys.slot(config, spec);
            if (!slot.enabled) {
                continue;
            }
            OsdText text = editor ? preview(spec, context) : evaluate(spec, context);
            if (text == null) {
                continue;
            }
            Shown entry = this.entries.computeIfAbsent(spec.id(), id -> new Shown(spec));
            if (entry.update(spec, slot, text)) {
                this.shown.add(entry);
            }
        }
        for (Api17Registries.Registered<OsdWarning> warning : registered) {
            if (FAILED.contains(warning.id())) {
                continue;
            }
            OsdText text;
            try {
                text = warning.value().warning(context);
            } catch (RuntimeException | LinkageError e) {
                fail(warning.id(), "OSD-Warnung", e);
                continue;
            }
            if (text == null) {
                continue;
            }
            String clean = AddonOsdKeys.sanitize(text.text(), OsdElementSpec.MAX_CHARS);
            if (!clean.isEmpty()) {
                this.warningTexts.add(clean.equals(text.text()) ? text : OsdText.of(clean, text.tone()));
            }
        }
    }

    private static @Nullable OsdText evaluate(OsdElementSpec spec, OsdContext context) {
        if (FAILED.contains(spec.id())) {
            return null;
        }
        try {
            return spec.content().text(context);
        } catch (RuntimeException | LinkageError e) {
            fail(spec.id(), "OSD-Element", e);
            return null;
        }
    }

    private static OsdText preview(OsdElementSpec spec, OsdContext context) {
        if (!spec.preview().isEmpty()) {
            return OsdText.of(spec.preview());
        }
        if (!FAILED.contains(spec.id())) {
            try {
                OsdText text = spec.content().text(context);
                if (text != null && !text.text().isEmpty()) {
                    return text;
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return OsdText.of(I18n.get(spec.nameKey()).toUpperCase(Locale.ROOT));
    }

    private static void fail(Identifier id, String what, Throwable error) {
        if (FAILED.add(id)) {
            Constants.LOG.error("{} {} eines Add-ons ist fehlgeschlagen und bleibt für diese Sitzung aus", what, id, error);
        }
    }

    public List<Shown> shown() {
        return this.shown;
    }

    public @Nullable OsdContext context() {
        return this.context;
    }

    public boolean drawn(Identifier id) {
        Shown entry = this.entries.get(id);
        return entry != null && entry.drawn;
    }

    public float @Nullable [] bounds(Identifier id) {
        Shown entry = this.entries.get(id);
        return entry != null && entry.drawn ? entry.bounds : null;
    }

    public @Nullable String text(Identifier id) {
        Shown entry = this.entries.get(id);
        return entry != null && entry.drawn ? entry.text : null;
    }

    public @Nullable Identifier at(double x, double y) {
        for (int i = this.shown.size() - 1; i >= 0; i--) {
            Shown entry = this.shown.get(i);
            float[] b = entry.bounds;
            if (entry.drawn && x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) {
                return entry.spec.id();
            }
        }
        return null;
    }

    public int warningCount() {
        return this.warningTexts.size();
    }

    public List<OsdText> warnings() {
        return List.copyOf(this.warningTexts);
    }

    public OsdTone warningTone(int index) {
        return this.warningTexts.get(index).tone();
    }

    public String showWarning(int index) {
        OsdText text = this.warningTexts.get(index);
        this.shownWarning = text;
        return text.text();
    }

    public @Nullable OsdText shownWarning() {
        return this.shownWarning;
    }

    public static int color(OsdTone tone, int normal, int accent, int caution, int warning) {
        return switch (tone) {
            case NORMAL -> normal;
            case ACCENT -> accent;
            case CAUTION -> caution;
            case WARNING -> warning;
        };
    }

    public static final class Shown {
        private final float[] bounds = new float[4];
        private OsdElementSpec spec;
        private OsdElement.Align align;
        private OsdConfig.Slot slot = new OsdConfig.Slot();
        private @Nullable String raw;
        private String text = "";
        private OsdTone tone = OsdTone.NORMAL;
        private boolean drawn;

        private Shown(OsdElementSpec spec) {
            this.spec = spec;
            this.align = AddonOsdKeys.align(spec.align());
        }

        private boolean update(OsdElementSpec current, OsdConfig.Slot slot, OsdText value) {
            if (current != this.spec) {
                this.spec = current;
                this.align = AddonOsdKeys.align(current.align());
                this.raw = null;
            }
            this.slot = slot;
            this.tone = value.tone();
            if (!value.text().equals(this.raw)) {
                this.raw = value.text();
                this.text = AddonOsdKeys.sanitize(this.raw, current.maxChars());
            }
            return !this.text.isEmpty();
        }

        public OsdElementSpec spec() {
            return this.spec;
        }

        public OsdConfig.Slot slot() {
            return this.slot;
        }

        public OsdElement.Align align() {
            return this.align;
        }

        public String text() {
            return this.text;
        }

        public OsdTone tone() {
            return this.tone;
        }

        public void record(float x0, float y0, float x1, float y1) {
            float[] b = this.bounds;
            if (!this.drawn) {
                this.drawn = true;
                b[0] = x0;
                b[1] = y0;
                b[2] = x1;
                b[3] = y1;
            } else {
                b[0] = Math.min(b[0], x0);
                b[1] = Math.min(b[1], y0);
                b[2] = Math.max(b[2], x1);
                b[3] = Math.max(b[3], y1);
            }
        }
    }
}

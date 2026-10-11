package de.lardtom.propwash.osd;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class OsdConfig {
    public static final int VERSION = 3;
    private static final int LAYOUT_VERSION = 2;
    public static final float MIN_SCALE = 0.5F;
    public static final float MAX_SCALE = 2.0F;
    public static final float STEPS_PER_UNIT = 10.0F;
    public static final int SCALE_STEPS = Math.round((MAX_SCALE - MIN_SCALE) * STEPS_PER_UNIT);
    public static final float MIN_LADDER_STEP = 5.0F;
    public static final float MAX_LADDER_STEP = 30.0F;
    private static final int LEGACY_CENTER_COL = 15;
    private static final int LEGACY_ARMED_STATUS_ROW = 10;
    private static final int LEGACY_WARNINGS_ROW = 11;
    public static final int DEFAULT_OPACITY = 60;
    public static final int DEFAULT_LINE_OPACITY = 60;
    public static final String DEFAULT_COLOR = OsdPalette.WHITE.hex();
    public static final String DEFAULT_WARNING_COLOR = OsdPalette.RED.hex();
    public static final OsdPreset DEFAULT_PRESET = OsdPreset.MINIMAL;
    public static final OsdPreset MIGRATED_PRESET = OsdPreset.STANDARD;

    public int version = VERSION;
    public OsdPreset preset = DEFAULT_PRESET;
    public int opacity = DEFAULT_OPACITY;
    public int lineOpacity = DEFAULT_LINE_OPACITY;
    public float scale = 1.0F;
    public String color = DEFAULT_COLOR;
    public String warningColor = DEFAULT_WARNING_COLOR;
    public Outline outline = Outline.THIN;
    public Background background = Background.OFF;
    public boolean coloredAlerts = true;
    public boolean hideVanillaHud = true;
    public boolean pitchLadder = true;
    public boolean analogBreakup = true;
    public float ladderStepDeg = 10.0F;
    public Map<String, Slot> elements = new LinkedHashMap<>();
    private transient @Nullable String argbSource;
    private transient int argb;

    public OsdConfig() {
    }

    public static OsdConfig defaults() {
        OsdConfig config = new OsdConfig();
        sanitize(config);
        return config;
    }

    public static void sanitize(OsdConfig config) {
        boolean legacy = config.version < LAYOUT_VERSION;
        boolean topRow = config.version == LAYOUT_VERSION;
        if (legacy && config.elements != null) {
            migrateLegacyRow(config.elements, OsdElement.ARMED_STATUS, LEGACY_ARMED_STATUS_ROW);
            migrateLegacyRow(config.elements, OsdElement.WARNINGS, LEGACY_WARNINGS_ROW);
        }
        boolean classic = legacy && isClassicLayout(config.elements);
        config.version = VERSION;
        if (legacy) {
            config.preset = classic ? DEFAULT_PRESET : MIGRATED_PRESET;
        } else if (config.preset == null) {
            config.preset = DEFAULT_PRESET;
        }
        config.opacity = Math.clamp(config.opacity, 0, 100);
        config.lineOpacity = Math.clamp(config.lineOpacity, 0, 100);
        config.scale = stepScale(config.scale, 1.0F);
        config.color = OsdColors.normalize(config.color, DEFAULT_COLOR);
        config.warningColor = OsdColors.normalize(config.warningColor, DEFAULT_WARNING_COLOR);
        if (config.outline == null) {
            config.outline = Outline.THIN;
        }
        if (config.background == null) {
            config.background = Background.OFF;
        }
        config.ladderStepDeg = Float.isFinite(config.ladderStepDeg)
                ? Math.round(Math.clamp(config.ladderStepDeg, MIN_LADDER_STEP, MAX_LADDER_STEP))
                : 10.0F;
        Map<String, Slot> old = config.elements == null ? Map.of() : config.elements;
        Map<String, Slot> fresh = new LinkedHashMap<>();
        for (OsdElement element : OsdElement.values()) {
            Slot slot = old.get(element.key());
            if (slot == null) {
                slot = config.preset.slot(element);
            } else {
                slot = slot.copy();
                if (!element.movable()) {
                    slot.col = element.baseCol();
                    slot.row = element.baseRow();
                } else {
                    slot.col = OsdGrid.clampCol(slot.col);
                    slot.row = OsdGrid.clampRow(slot.row);
                }
                slot.color = slot.color == null || slot.color.isBlank() ? "" : OsdColors.normalize(slot.color, "");
                slot.scale = slot.scale > 0.0F ? stepScale(slot.scale, 0.0F) : 0.0F;
            }
            fresh.put(element.key(), slot);
        }
        config.elements = fresh;
        if (classic) {
            config.applyPreset(DEFAULT_PRESET);
        }
        if (topRow) {
            migrateTopRow(config);
        }
    }

    private static void migrateTopRow(OsdConfig config) {
        OsdPreset preset = config.preset;
        int[][] moves = switch (preset) {
            case MINIMAL -> new int[][]{{OsdElement.RECORDING.ordinal(), 28, 0}, {OsdElement.FLIGHT_MODE.ordinal(), 28, 1},
                    {OsdElement.LAP.ordinal(), 28, 2}, {OsdElement.LAP_TIME.ordinal(), 28, 3}};
            case STANDARD -> new int[][]{{OsdElement.RECORDING.ordinal(), 28, 0}};
            case RACE -> new int[][]{{OsdElement.RECORDING.ordinal(), 28, 0}, {OsdElement.LAP.ordinal(), 28, 1},
                    {OsdElement.LAP_TIME.ordinal(), 28, 2}, {OsdElement.LAST_LAP.ordinal(), 28, 3}, {OsdElement.BEST_LAP.ordinal(), 28, 4}};
            case FULL -> new int[0][];
        };
        OsdElement[] all = OsdElement.values();
        for (int[] move : moves) {
            OsdElement element = all[move[0]];
            Slot slot = config.slot(element);
            if (slot.col == move[1] && slot.row == move[2]) {
                slot.col = preset.col(element);
                slot.row = preset.row(element);
            }
        }
    }

    private static void migrateLegacyRow(Map<String, Slot> elements, OsdElement element, int legacyRow) {
        Slot slot = elements.get(element.key());
        if (slot != null && slot.col == LEGACY_CENTER_COL && slot.row == legacyRow) {
            slot.col = element.baseCol();
            slot.row = element.baseRow();
        }
    }

    public static boolean isClassicLayout(@Nullable Map<String, Slot> elements) {
        if (elements == null) {
            return true;
        }
        for (OsdElement element : OsdElement.values()) {
            Slot slot = elements.get(element.key());
            if (slot == null) {
                continue;
            }
            if (slot.enabled != element.classicEnabled()) {
                return false;
            }
            if (element.movable() && (slot.col != element.baseCol() || slot.row != element.baseRow())) {
                return false;
            }
        }
        return true;
    }

    public static float stepScale(float scale, float fallback) {
        if (!Float.isFinite(scale)) {
            return fallback;
        }
        return scaleAt(scaleStep(scale));
    }

    public static float scaleAt(int step) {
        return Math.round(MIN_SCALE * STEPS_PER_UNIT + Math.clamp(step, 0, SCALE_STEPS)) / STEPS_PER_UNIT;
    }

    public static int scaleStep(float scale) {
        return Math.clamp(Math.round((scale - MIN_SCALE) * STEPS_PER_UNIT), 0, SCALE_STEPS);
    }

    public int primaryRgb() {
        return OsdColors.rgbOr(this.color, OsdPalette.WHITE.rgb());
    }

    public int warningRgb() {
        return OsdColors.rgbOr(this.warningColor, OsdPalette.RED.rgb());
    }

    public int elementRgb(OsdElement element) {
        Slot slot = this.slot(element);
        return slot.hasColor() ? OsdColors.rgbOr(slot.color, this.primaryRgb()) : this.primaryRgb();
    }

    public float elementScale(OsdElement element) {
        Slot slot = this.slot(element);
        return slot.hasScale() ? slot.scale : this.scale;
    }

    public float opacityFraction() {
        return OsdColors.fraction(this.opacity);
    }

    public float elementAlpha(OsdElement element) {
        float alpha = this.opacityFraction();
        return element.usesLineOpacity() ? alpha * OsdColors.fraction(this.lineOpacity) : alpha;
    }

    public Slot slot(OsdElement element) {
        Slot slot = this.elements == null ? null : this.elements.get(element.key());
        if (slot == null) {
            slot = (this.preset == null ? DEFAULT_PRESET : this.preset).slot(element);
            if (this.elements == null) {
                this.elements = new LinkedHashMap<>();
            }
            this.elements.put(element.key(), slot);
        }
        return slot;
    }

    public boolean enabled(OsdElement element) {
        return this.slot(element).enabled;
    }

    public void move(OsdElement element, int col, int row) {
        if (!element.movable()) {
            return;
        }
        Slot slot = this.slot(element);
        slot.col = OsdGrid.clampCol(col);
        slot.row = OsdGrid.clampRow(row);
    }

    public void applyPreset(OsdPreset target) {
        this.preset = target;
        for (OsdElement element : OsdElement.values()) {
            Slot slot = this.slot(element);
            slot.enabled = target.enabled(element);
            slot.col = target.col(element);
            slot.row = target.row(element);
        }
    }

    public void resetLayout() {
        this.applyPreset(this.preset == null ? DEFAULT_PRESET : this.preset);
        for (OsdElement element : OsdElement.values()) {
            this.slot(element).clearOverrides();
        }
    }

    public boolean customized() {
        return !(this.preset == null ? DEFAULT_PRESET : this.preset).matches(this);
    }

    public boolean hasElementOverrides() {
        for (OsdElement element : OsdElement.values()) {
            if (this.slot(element).hasOverrides()) {
                return true;
            }
        }
        return false;
    }

    public OsdConfig copy() {
        OsdConfig copy = new OsdConfig();
        copy.version = this.version;
        copy.preset = this.preset;
        copy.opacity = this.opacity;
        copy.lineOpacity = this.lineOpacity;
        copy.scale = this.scale;
        copy.color = this.color;
        copy.warningColor = this.warningColor;
        copy.outline = this.outline;
        copy.background = this.background;
        copy.coloredAlerts = this.coloredAlerts;
        copy.hideVanillaHud = this.hideVanillaHud;
        copy.pitchLadder = this.pitchLadder;
        copy.analogBreakup = this.analogBreakup;
        copy.ladderStepDeg = this.ladderStepDeg;
        copy.elements = new LinkedHashMap<>();
        if (this.elements != null) {
            this.elements.forEach((key, slot) -> {
                if (slot != null) {
                    copy.elements.put(key, slot.copy());
                }
            });
        }
        sanitize(copy);
        return copy;
    }

    public enum Outline {
        NONE(0.0F),
        SHADOW(0.7F),
        THIN(0.95F),
        THICK(1.0F);

        private final float alphaFactor;

        Outline(float alphaFactor) {
            this.alphaFactor = alphaFactor;
        }

        public float alphaFactor() {
            return this.alphaFactor;
        }

        public String translationKey() {
            return "propwash.osd.outline." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Background {
        OFF(0.0F),
        SUBTLE(0.3F),
        SOLID(0.65F);

        private final float alphaFactor;

        Background(float alphaFactor) {
            this.alphaFactor = alphaFactor;
        }

        public float alphaFactor() {
            return this.alphaFactor;
        }

        public String translationKey() {
            return "propwash.osd.background." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static final class Slot {
        public boolean enabled;
        public int col;
        public int row;
        public String color = "";
        public float scale = 0.0F;

        public Slot() {
        }

        public Slot(boolean enabled, int col, int row) {
            this.enabled = enabled;
            this.col = col;
            this.row = row;
        }

        public boolean hasColor() {
            return this.color != null && !this.color.isEmpty();
        }

        public boolean hasScale() {
            return this.scale > 0.0F;
        }

        public boolean hasOverrides() {
            return this.hasColor() || this.hasScale();
        }

        public void clearOverrides() {
            this.color = "";
            this.scale = 0.0F;
        }

        public Slot copy() {
            Slot copy = new Slot(this.enabled, this.col, this.row);
            copy.color = this.color == null ? "" : this.color;
            copy.scale = this.scale;
            return copy;
        }
    }
}

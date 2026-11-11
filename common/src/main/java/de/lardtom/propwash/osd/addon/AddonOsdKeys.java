package de.lardtom.propwash.osd.addon;

import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.apiimpl.NamespaceRules;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdWarnings;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

public final class AddonOsdKeys {
    public static final char FALLBACK_CHAR = '?';
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private AddonOsdKeys() {
    }

    public static boolean isAddonKey(@Nullable String key) {
        return key != null && key.length() <= OsdElementSpec.MAX_ID_LENGTH && KEY.matcher(key).matches() && !NamespaceRules.isReservedId(key);
    }

    public static String key(OsdElementSpec spec) {
        return spec.id().toString();
    }

    public static OsdConfig.Slot defaultSlot(OsdElementSpec spec) {
        return new OsdConfig.Slot(spec.enabledByDefault(), OsdGrid.clampCol(spec.column()), OsdGrid.clampRow(spec.row()));
    }

    public static OsdConfig.Slot slot(OsdConfig config, OsdElementSpec spec) {
        if (config.elements == null) {
            config.elements = new LinkedHashMap<>();
        }
        return config.elements.computeIfAbsent(key(spec), key -> defaultSlot(spec));
    }

    public static OsdConfig.Slot peek(OsdConfig config, OsdElementSpec spec) {
        OsdConfig.Slot slot = config.elements == null ? null : config.elements.get(key(spec));
        return slot != null ? slot : defaultSlot(spec);
    }

    public static void reset(OsdConfig config, OsdElementSpec spec) {
        OsdConfig.Slot slot = slot(config, spec);
        OsdConfig.Slot fresh = defaultSlot(spec);
        slot.enabled = fresh.enabled;
        slot.col = fresh.col;
        slot.row = fresh.row;
        slot.clearOverrides();
    }

    public static boolean customized(OsdConfig config, OsdElementSpec spec) {
        OsdConfig.Slot slot = config.elements == null ? null : config.elements.get(key(spec));
        if (slot == null) {
            return false;
        }
        OsdConfig.Slot fresh = defaultSlot(spec);
        return slot.enabled != fresh.enabled || slot.col != fresh.col || slot.row != fresh.row || slot.hasOverrides();
    }

    public static void move(OsdConfig config, OsdElementSpec spec, int col, int row) {
        OsdConfig.Slot slot = slot(config, spec);
        slot.col = OsdGrid.clampCol(col);
        slot.row = OsdGrid.clampRow(row);
    }

    public static OsdElement.Align align(OsdAlign align) {
        return switch (align) {
            case LEFT -> OsdElement.Align.LEFT;
            case CENTER -> OsdElement.Align.CENTER;
            case RIGHT -> OsdElement.Align.RIGHT;
        };
    }

    public static String sanitize(@Nullable String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int limit = Math.clamp(maxChars, 1, OsdElementSpec.MAX_CHARS);
        StringBuilder out = new StringBuilder(Math.min(text.length(), limit));
        int index = 0;
        while (index < text.length() && out.length() < limit) {
            int codePoint = text.codePointAt(index);
            out.append(codePoint >= 0x20 && codePoint <= 0x7E ? (char) codePoint : FALLBACK_CHAR);
            index += Character.charCount(codePoint);
        }
        return out.toString();
    }

    public static int visibleWarning(List<OsdWarnings.Warning> builtIn, int addonCount, double seconds) {
        for (OsdWarnings.Warning warning : builtIn) {
            if (warning.severity() == OsdWarnings.Severity.CRITICAL) {
                return OsdWarnings.visibleIndex(builtIn, seconds);
            }
        }
        int total = builtIn.size() + Math.max(0, addonCount);
        if (total == 0) {
            return -1;
        }
        int step = Double.isFinite(seconds) && seconds > 0.0 ? (int) Math.floor(seconds / OsdWarnings.CYCLE_SECONDS) : 0;
        return Math.floorMod(step, total);
    }
}

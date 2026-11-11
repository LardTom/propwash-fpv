package de.lardtom.propwash.osd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OsdLayoutCodec {
    public static final int VERSION = 1;
    public static final int MAX_LENGTH = 8192;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private OsdLayoutCodec() {
    }

    public static String encode(OsdConfig config) {
        OsdConfig source = config.copy();
        JsonObject root = new JsonObject();
        root.addProperty("v", VERSION);
        root.addProperty("preset", source.preset.key());
        JsonObject style = new JsonObject();
        style.addProperty("opacity", source.opacity);
        style.addProperty("line_opacity", source.lineOpacity);
        style.addProperty("scale", source.scale);
        style.addProperty("color", color(source.color));
        style.addProperty("warning_color", color(source.warningColor));
        style.addProperty("outline", source.outline.name().toLowerCase(Locale.ROOT));
        style.addProperty("background", source.background.name().toLowerCase(Locale.ROOT));
        style.addProperty("colored_alerts", source.coloredAlerts);
        style.addProperty("pitch_ladder", source.pitchLadder);
        style.addProperty("ladder_step_deg", source.ladderStepDeg);
        root.add("style", style);
        JsonObject elements = new JsonObject();
        for (OsdElement element : OsdElement.values()) {
            elements.add(element.key(), entry(source.slot(element), element.movable()));
        }
        root.add("elements", elements);
        int length = GSON.toJson(root).length();
        for (Map.Entry<String, OsdConfig.Slot> addon : addonSlots(source)) {
            JsonObject entry = entry(addon.getValue(), true);
            int added = GSON.toJson(new JsonPrimitive(addon.getKey())).length() + GSON.toJson(entry).length() + 2;
            if (length + added <= MAX_LENGTH) {
                elements.add(addon.getKey(), entry);
                length += added;
            }
        }
        return GSON.toJson(root);
    }

    private static JsonObject entry(OsdConfig.Slot slot, boolean movable) {
        JsonObject entry = new JsonObject();
        entry.addProperty("on", slot.enabled);
        if (movable) {
            entry.addProperty("col", slot.col);
            entry.addProperty("row", slot.row);
        }
        if (slot.hasColor()) {
            entry.addProperty("color", color(slot.color));
        }
        if (slot.hasScale()) {
            entry.addProperty("scale", slot.scale);
        }
        return entry;
    }

    private static List<Map.Entry<String, OsdConfig.Slot>> addonSlots(OsdConfig config) {
        List<Map.Entry<String, OsdConfig.Slot>> out = new ArrayList<>();
        for (Map.Entry<String, OsdConfig.Slot> entry : config.elements.entrySet()) {
            if (entry.getValue() != null && AddonOsdKeys.isAddonKey(entry.getKey())) {
                out.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        out.sort(Comparator.<Map.Entry<String, OsdConfig.Slot>, Boolean>comparing(entry -> !entry.getValue().enabled).thenComparing(Map.Entry::getKey));
        return out;
    }

    public static OsdConfig decode(String json, OsdConfig base) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("OSD-Layout ist leer");
        }
        if (json.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("OSD-Layout zu groß: " + json.length() + " Zeichen");
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("OSD-Layout ist kein JSON-Objekt");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("OSD-Layout ist kein gültiges JSON: " + e.getMessage(), e);
        }
        int version = integer(root, "v", -1);
        if (version < 1) {
            throw new IllegalArgumentException("OSD-Layout ohne gültige Version (\"v\")");
        }
        OsdConfig defaults = new OsdConfig();
        OsdConfig out = base.copy();
        out.applyPreset(preset(text(root, "preset", null)));
        for (OsdElement element : OsdElement.values()) {
            out.slot(element).clearOverrides();
        }
        JsonObject style = object(root, "style");
        out.opacity = integer(style, "opacity", defaults.opacity);
        out.lineOpacity = integer(style, "line_opacity", defaults.lineOpacity);
        out.scale = decimal(style, "scale", defaults.scale);
        out.color = text(style, "color", defaults.color);
        out.warningColor = text(style, "warning_color", defaults.warningColor);
        out.outline = named(OsdConfig.Outline.class, text(style, "outline", null), defaults.outline);
        out.background = named(OsdConfig.Background.class, text(style, "background", null), defaults.background);
        out.coloredAlerts = bool(style, "colored_alerts", defaults.coloredAlerts);
        out.pitchLadder = bool(style, "pitch_ladder", defaults.pitchLadder);
        out.ladderStepDeg = decimal(style, "ladder_step_deg", defaults.ladderStepDeg);
        JsonObject elements = object(root, "elements");
        for (Map.Entry<String, JsonElement> entry : elements.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            OsdElement element = OsdElement.byKey(entry.getKey());
            OsdConfig.Slot slot;
            if (element != null) {
                slot = out.slot(element);
            } else if (AddonOsdKeys.isAddonKey(entry.getKey())) {
                slot = out.elements.computeIfAbsent(entry.getKey(), key -> new OsdConfig.Slot());
            } else {
                continue;
            }
            JsonObject values = entry.getValue().getAsJsonObject();
            slot.enabled = bool(values, "on", slot.enabled);
            slot.col = integer(values, "col", slot.col);
            slot.row = integer(values, "row", slot.row);
            slot.color = text(values, "color", "");
            slot.scale = decimal(values, "scale", 0.0F);
        }
        OsdConfig.sanitize(out);
        return out;
    }

    private static String color(String value) {
        return OsdColors.normalize(value, value).toLowerCase(Locale.ROOT);
    }

    private static OsdPreset preset(String key) {
        if (key != null) {
            for (OsdPreset preset : OsdPreset.values()) {
                if (preset.key().equals(key.toLowerCase(Locale.ROOT))) {
                    return preset;
                }
            }
        }
        return OsdConfig.DEFAULT_PRESET;
    }

    private static <E extends Enum<E>> E named(Class<E> type, String key, E fallback) {
        if (key != null) {
            for (E value : type.getEnumConstants()) {
                if (value.name().equalsIgnoreCase(key)) {
                    return value;
                }
            }
        }
        return fallback;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonPrimitive primitive(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
    }

    private static int integer(JsonObject parent, String key, int fallback) {
        JsonPrimitive value = primitive(parent, key);
        if (value == null || !value.isNumber()) {
            return fallback;
        }
        double number = value.getAsDouble();
        return Double.isFinite(number) ? (int) Math.round(Math.clamp(number, Integer.MIN_VALUE, Integer.MAX_VALUE)) : fallback;
    }

    private static float decimal(JsonObject parent, String key, float fallback) {
        JsonPrimitive value = primitive(parent, key);
        if (value == null || !value.isNumber()) {
            return fallback;
        }
        float number = value.getAsFloat();
        return Float.isFinite(number) ? number : fallback;
    }

    private static boolean bool(JsonObject parent, String key, boolean fallback) {
        JsonPrimitive value = primitive(parent, key);
        return value != null && value.isBoolean() ? value.getAsBoolean() : fallback;
    }

    private static String text(JsonObject parent, String key, String fallback) {
        JsonPrimitive value = primitive(parent, key);
        return value != null && value.isString() ? value.getAsString() : fallback;
    }
}

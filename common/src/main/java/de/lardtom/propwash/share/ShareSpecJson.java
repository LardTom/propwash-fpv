package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.share.legacy.Pw1Tables;
import de.lardtom.propwash.share.legacy.Pw2Tables;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;

public final class ShareSpecJson {
    public static final int FORMAT = 1;
    public static final List<String> VARIANTS = List.of("parts", "preset");

    private ShareSpecJson() {
    }

    public static JsonObject spec() {
        JsonObject out = new JsonObject();
        out.addProperty("format", FORMAT);
        out.addProperty("code_format", Pw3Params.FORMAT);
        out.addProperty("prefix", Pw3Params.PREFIX);
        out.addProperty("revision", Pw3Params.REVISION);
        out.addProperty("alphabet", Base62.ALPHABET);
        out.addProperty("id_alphabet", Pw3Params.ID_ALPHABET);
        out.addProperty("text_alphabet", Pw3Params.TEXT_ALPHABET);
        out.addProperty("checksum", "crc32-ieee, low 16 bits, big-endian");
        out.addProperty("table_checksum", "crc32-ieee of the utf-8 lines <kind>:<number>:<path>\\n of all used table entries, sorted by kind and number, low 10 bits");
        out.addProperty("unknown_part", Pw3Params.UNKNOWN_NAMESPACE + ":<namespace>/<kind>/<number>");
        out.addProperty("unknown_short_id_key", Pw3Params.SHORT_KEY_PREFIX + "<n>");
        out.addProperty("reserved_prefix", Pw3Params.RESERVED_PREFIX);
        JsonObject params = new JsonObject();
        params.addProperty("namespace_count", Pw3Params.K_NAMESPACE_COUNT);
        params.addProperty("short_id", Pw3Params.K_SHORT_ID);
        params.addProperty("part", Pw3Params.K_PART);
        params.addProperty("id_text", Pw3Params.K_ID_TEXT);
        params.addProperty("version", Pw3Params.K_VERSION);
        params.addProperty("accessory_count", Pw3Params.K_ACCESSORY_COUNT);
        params.addProperty("accessory_slot", Pw3Params.K_ACCESSORY_SLOT);
        params.addProperty("color", Pw3Params.K_COLOR);
        params.addProperty("paint_slots", Pw3Params.K_PAINT_SLOTS);
        params.addProperty("tune_count", Pw3Params.K_TUNE_COUNT);
        params.addProperty("tune_gap", Pw3Params.K_TUNE_GAP);
        params.addProperty("tune_literal_count", Pw3Params.K_TUNE_LITERAL_COUNT);
        params.addProperty("value", Pw3Params.K_VALUE);
        params.addProperty("text", Pw3Params.K_TEXT);
        params.addProperty("osd_version", Pw3Params.K_OSD_VERSION);
        params.addProperty("osd_bytes", Pw3Params.K_OSD_BYTES);
        params.addProperty("extension_count", Pw3Params.K_EXTENSION_COUNT);
        params.addProperty("extension_layer", Pw3Params.K_EXTENSION_LAYER);
        params.addProperty("extension_bits", Pw3Params.K_EXTENSION_BITS);
        params.addProperty("table_checksum_bits", Pw3Params.TABLE_CHECKSUM_BITS);
        params.addProperty("creative_namespace", Pw3Params.K_CREATIVE_NAMESPACE);
        params.addProperty("creative_count", Pw3Params.K_CREATIVE_COUNT);
        params.addProperty("creative_gap", Pw3Params.K_CREATIVE_GAP);
        params.addProperty("creative_literal_count", Pw3Params.K_CREATIVE_LITERAL_COUNT);
        params.addProperty("creative_decimals", Pw3Params.K_CREATIVE_DECIMALS);
        params.addProperty("creative_value", Pw3Params.K_CREATIVE_VALUE);
        out.add("params", params);
        JsonObject limits = new JsonObject();
        limits.addProperty("input_chars", Pw3Params.MAX_INPUT_CHARS);
        limits.addProperty("frame_bytes", Pw3Params.MAX_FRAME_BYTES);
        limits.addProperty("min_frame_bytes", Pw3Params.MIN_FRAME_BYTES);
        limits.addProperty("namespaces", Pw3Params.MAX_NAMESPACES);
        limits.addProperty("namespace_chars", Pw3Params.MAX_NAMESPACE_CHARS);
        limits.addProperty("path_chars", Pw3Params.MAX_PATH_CHARS);
        limits.addProperty("slug_chars", Pw3Params.MAX_SLUG_CHARS);
        limits.addProperty("link_chars", Pw3Params.MAX_LINK_CHARS);
        limits.addProperty("version_suffix_chars", Pw3Params.MAX_VERSION_SUFFIX_CHARS);
        limits.addProperty("addon_name_chars", Pw3Params.MAX_ADDON_NAME_CHARS);
        limits.addProperty("name_chars", Pw3Params.MAX_NAME_CHARS);
        limits.addProperty("osd_chars", Pw3Params.MAX_OSD_CHARS);
        limits.addProperty("osd_inflated_bytes", Pw3Params.MAX_OSD_INFLATED_BYTES);
        limits.addProperty("tune_entries", Pw3Params.MAX_TUNE_ENTRIES);
        limits.addProperty("tune_key_chars", Pw3Params.MAX_TUNE_KEY_CHARS);
        limits.addProperty("paint_slot_bits", Pw3Params.MAX_PAINT_SLOT_BITS);
        limits.addProperty("accessories", Pw3Params.MAX_ACCESSORIES);
        limits.addProperty("extensions", Pw3Params.MAX_EXTENSIONS);
        limits.addProperty("creative_values", Pw3Params.MAX_CREATIVE_VALUES);
        limits.addProperty("creative_key_chars", Pw3Params.MAX_CREATIVE_KEY_CHARS);
        limits.addProperty("creative_decimals", Pw3Params.MAX_CREATIVE_DECIMALS);
        limits.addProperty("creative_mantissa_bits", Pw3Params.MAX_CREATIVE_MANTISSA_BITS);
        out.add("limits", limits);
        JsonArray links = new JsonArray();
        for (String type : List.of("none", "modrinth", "curseforge", "https")) {
            links.add(type);
        }
        out.add("link_types", links);
        JsonArray layers = new JsonArray();
        for (ShareLayer layer : ShareLayer.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("bit", layer.bit());
            entry.addProperty("key", layer.key());
            entry.addProperty("default", ShareLayer.DEFAULT_EXPORT.contains(layer));
            entry.addProperty("translation_key", layer.translationKey());
            layers.add(entry);
        }
        out.add("layers", layers);
        out.add("kinds", strings(Pw3Params.KINDS.stream().map(PartKind::key).toList()));
        out.add("accessory_slots", strings(Pw3Params.ACCESSORY_SLOTS.stream().map(AccessorySlot::key).toList()));
        out.add("paint_slots", strings(Pw3Params.PAINT_SLOTS));
        out.add("tune_keys", strings(Pw3Params.TUNE_KEYS));
        out.add("tune_groups", strings(Pw3Params.TUNE_GROUPS.stream().map(g -> g.name().toLowerCase(Locale.ROOT)).sorted().toList()));
        JsonObject creative = new JsonObject();
        creative.addProperty("extension", Pw3Params.CREATIVE_EXTENSION);
        creative.addProperty("layer_bit", Pw3Params.FIRST_EXTENSION_LAYER + Pw3Params.CREATIVE_EXTENSION);
        creative.add("base_kinds", strings(Pw3Params.CREATIVE_BASE_KINDS.stream().map(PartKind::key).toList()));
        creative.add("keys", strings(Pw3Params.CREATIVE_KEYS));
        creative.addProperty("pwd1_prefix", ShareFormat.DRONE_EDITOR + "-");
        creative.add("pwd1_build", build(CreativeShare.PWD1_BUILD));
        out.add("creative", creative);
        JsonArray colors = new JsonArray();
        for (int rgb : Pw3Params.COLORS) {
            colors.add(String.format(Locale.ROOT, "#%06x", rgb));
        }
        out.add("colors", colors);
        out.addProperty("osd_dictionary", new String(Pw3Params.osdDictionary(), StandardCharsets.UTF_8));
        JsonArray statuses = new JsonArray();
        for (ShareStatus status : ShareStatus.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", status.name());
            entry.addProperty("translation_key", status.translationKey());
            statuses.add(entry);
        }
        out.add("statuses", statuses);
        out.add("namespace_results", strings(EnumSet.allOf(NamespaceReport.Result.class).stream().map(Enum::name).toList()));
        out.add("propwash_table", resource(ShareTables.TABLE_RESOURCE));
        out.add("directory", resource(AddonDirectory.RESOURCE));
        out.add("pw2", pw2());
        out.add("pw1", pw1());
        return out;
    }

    private static JsonObject pw2() {
        JsonObject out = new JsonObject();
        out.addProperty("format", Pw2Tables.FORMAT);
        out.addProperty("prefix", Pw2Tables.PREFIX);
        out.addProperty("revision", Pw2Tables.REVISION);
        out.addProperty("unknown_part", "pw2.unknown:<kind>.<number>");
        out.addProperty("part_param", Pw2Tables.K_PART);
        JsonObject parts = new JsonObject();
        for (PartKind kind : Pw2Tables.KINDS) {
            parts.add(Pw2Tables.kindKey(kind), strings(Pw2Tables.parts(kind)));
        }
        out.add("parts", parts);
        out.add("tune_keys", strings(Pw2Tables.TUNE_KEYS));
        return out;
    }

    private static JsonObject pw1() {
        JsonObject out = new JsonObject();
        out.addProperty("format", Pw1Tables.FORMAT);
        out.addProperty("prefix", Pw1Tables.PREFIX);
        out.addProperty("revision", Pw1Tables.REVISION);
        out.addProperty("alphabet", Pw1Tables.ALPHABET);
        out.addProperty("unknown_namespace", Pw1Tables.UNKNOWN_NAMESPACE_PREFIX + "<n>");
        JsonArray namespaces = new JsonArray();
        for (int i = 0; i < Pw1Tables.NAMESPACES.size(); i++) {
            namespaces.add(i == 0 ? JsonNull.INSTANCE : new JsonPrimitive(Pw1Tables.NAMESPACES.get(i)));
        }
        out.add("namespaces", namespaces);
        out.add("tune_keys", strings(Pw1Tables.TUNE_KEYS));
        return out;
    }

    public static JsonArray vectors(List<DronePreset> presets, ShareTables tables) {
        JsonArray out = new JsonArray();
        for (DronePreset preset : presets) {
            ShareContent full = ShareContent.of(preset.data(), EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.NAME), Optional.empty());
            for (String variant : VARIANTS) {
                ShareContent content = variant.equals("parts") ? ShareContent.of(preset.data().build()) : full;
                String name = variant + ":" + preset.id();
                try {
                    String code = Pw3Codec.encode(content, tables);
                    out.add(vector(name, code, ShareFormat.decode(code, tables)));
                } catch (ShareException e) {
                    JsonObject failed = new JsonObject();
                    failed.addProperty("name", name);
                    failed.addProperty("status", e.status().name());
                    failed.addProperty("error", e.key());
                    out.add(failed);
                }
            }
        }
        return out;
    }

    public static JsonObject vector(String name, String code, ShareDecoded decoded) {
        JsonObject out = new JsonObject();
        out.addProperty("name", name);
        out.addProperty("format", decoded.format());
        out.addProperty("code", code);
        out.addProperty("status", decoded.status().name());
        out.add("layers", strings(decoded.layers().stream().map(ShareLayer::key).toList()));
        out.add("decoded", decoded.ok() ? content(decoded.require()) : JsonNull.INSTANCE);
        JsonArray namespaces = new JsonArray();
        for (NamespaceReport report : decoded.namespaces()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", report.key());
            entry.addProperty("short_id", report.shortId());
            entry.addProperty("table", report.hasTable());
            entry.add("min_version", report.minVersion().<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
            entry.addProperty("result", report.result().name());
            namespaces.add(entry);
        }
        out.add("namespaces", namespaces);
        out.add("skipped", strings(decoded.skipped()));
        out.add("unknown_parts", strings(decoded.unknownParts().stream().map(Identifier::toString).toList()));
        return out;
    }

    public static JsonObject content(ShareContent content) {
        JsonObject out = new JsonObject();
        out.add("build", build(content.build()));
        out.add("paint", content.paint().<JsonElement>map(ShareSpecJson::paint).orElse(JsonNull.INSTANCE));
        out.add("tune", content.tune().<JsonElement>map(tune -> {
            JsonObject values = new JsonObject();
            new TreeMap<>(tune.values()).forEach((key, value) -> values.add(key, new JsonPrimitive(Double.parseDouble(Float.toString(value)))));
            return values;
        }).orElse(JsonNull.INSTANCE));
        out.add("osd", content.osd().<JsonElement>map(osd -> {
            JsonObject value = new JsonObject();
            value.addProperty("version", osd.version());
            value.addProperty("json", osd.json());
            return value;
        }).orElse(JsonNull.INSTANCE));
        out.add("name", content.name().<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
        return out;
    }

    public static JsonObject build(BuildSpec build) {
        JsonObject out = new JsonObject();
        for (int i = 0; i < 6; i++) {
            PartKind kind = Pw3Params.KINDS.get(i);
            out.addProperty(kind.key(), build.part(kind).toString());
        }
        JsonObject accessories = new JsonObject();
        for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
            build.accessory(slot).ifPresent(id -> accessories.addProperty(slot.key(), id.toString()));
        }
        out.add("accessories", accessories);
        return out;
    }

    public static JsonObject paint(PaintSpec paint) {
        JsonObject out = new JsonObject();
        for (String slot : Pw3Params.PAINT_SLOTS) {
            paint.color(slot).ifPresent(rgb -> out.addProperty(slot, String.format(Locale.ROOT, "#%06x", rgb)));
        }
        return out;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray out = new JsonArray();
        values.forEach(out::add);
        return out;
    }

    private static JsonElement resource(String path) {
        try (InputStream in = ShareSpecJson.class.getResourceAsStream(path)) {
            return in == null ? JsonNull.INSTANCE : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return JsonNull.INSTANCE;
        }
    }
}

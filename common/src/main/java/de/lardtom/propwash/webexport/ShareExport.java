package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.share.AddonDirectory;
import de.lardtom.propwash.share.NamespaceReport;
import de.lardtom.propwash.share.Pw3Codec;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareException;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareSpecJson;
import de.lardtom.propwash.share.ShareStatus;
import de.lardtom.propwash.share.ShareTable;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.share.TableChecksum;
import de.lardtom.propwash.share.legacy.Pw1Tables;
import de.lardtom.propwash.share.legacy.Pw2Tables;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;

final class ShareExport {
    static final String SPEC = "sharecode/spec.json";
    static final String VECTORS = "sharecode/vectors.json";
    static final String SOURCE_MOD = "mod";
    static final String SOURCE_DIRECTORY = "directory";
    static final Set<ShareLayer> PRESET_LAYERS = EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE, ShareLayer.NAME);

    record Table(String source, ShareTable table) {
        int entries() {
            return this.table.totalEntries();
        }

        String crc() {
            return ShareExport.crc(this.table);
        }
    }

    private ShareExport() {
    }

    static JsonObject spec() {
        JsonObject out = ShareSpecJson.spec();
        JsonObject pw1 = out.getAsJsonObject("pw1");
        supplement(pw1, "group_length", Pw1Tables.GROUP_LENGTH);
        supplement(pw1, "frame_version", Pw1Tables.FRAME_VERSION);
        supplement(pw1, "flag_deflate", Pw1Tables.FLAG_DEFLATE);
        supplement(pw1, "checksum", "crc32-ieee, big-endian");
        JsonObject tolerant = new JsonObject();
        tolerant.addProperty("O", "0");
        tolerant.addProperty("I", "1");
        tolerant.addProperty("L", "1");
        supplement(pw1, "tolerant", tolerant);
        JsonArray slots = new JsonArray();
        for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("index", Pw3Params.accessoryIndex(slot));
            entry.addProperty("key", slot.key());
            slots.add(entry);
        }
        supplement(pw1, "accessory_slots", slots);
        supplement(pw1, "paint_bits", paintBits());
        JsonObject limits = new JsonObject();
        limits.addProperty("input_chars", Pw3Params.MAX_INPUT_CHARS);
        limits.addProperty("frame_bytes", Pw3Params.MAX_FRAME_BYTES);
        limits.addProperty("min_frame_bytes", Pw1Tables.MIN_FRAME_BYTES);
        limits.addProperty("payload_bytes", Pw1Tables.MAX_PAYLOAD_BYTES);
        limits.addProperty("tune_entries", Pw3Params.MAX_TUNE_ENTRIES);
        limits.addProperty("tune_key_chars", Pw3Params.MAX_TUNE_KEY_CHARS);
        limits.addProperty("osd_chars", Pw3Params.MAX_OSD_CHARS);
        limits.addProperty("name_chars", Pw3Params.MAX_NAME_CHARS);
        limits.addProperty("path_chars", Pw3Params.MAX_PATH_CHARS);
        limits.addProperty("namespace_chars", Pw3Params.MAX_NAMESPACE_CHARS);
        limits.addProperty("id_chars", "[a-z0-9_.-]");
        supplement(pw1, "limits", limits);
        JsonObject pw2 = out.getAsJsonObject("pw2");
        supplement(pw2, "alphabet", Pw2Tables.ALPHABET);
        supplement(pw2, "text_alphabet", Pw2Tables.TEXT_ALPHABET);
        supplement(pw2, "checksum", "crc32-ieee, low 16 bits, big-endian");
        JsonObject params = new JsonObject();
        params.addProperty("part", Pw2Tables.K_PART);
        params.addProperty("color", Pw2Tables.K_COLOR);
        params.addProperty("paint_slots", Pw2Tables.K_PAINT_SLOTS);
        params.addProperty("tune_count", Pw2Tables.K_TUNE_COUNT);
        params.addProperty("tune_gap", Pw2Tables.K_TUNE_GAP);
        params.addProperty("value", Pw2Tables.K_VALUE);
        params.addProperty("text", Pw2Tables.K_TEXT);
        params.addProperty("osd_bytes", Pw2Tables.K_OSD_BYTES);
        params.addProperty("extension_bits", Pw2Tables.K_EXTENSION_BITS);
        supplement(pw2, "params", params);
        JsonArray colors = new JsonArray();
        for (int rgb : Pw2Tables.COLORS) {
            colors.add(ExportJson.hex(rgb));
        }
        supplement(pw2, "colors", colors);
        supplement(pw2, "osd_dictionary", new String(Pw2Tables.OSD_DICTIONARY, StandardCharsets.UTF_8));
        return out;
    }

    private static void supplement(JsonObject target, String key, Object value) {
        if (target.has(key)) {
            return;
        }
        switch (value) {
            case JsonElement element -> target.add(key, element);
            case Number number -> target.addProperty(key, number);
            default -> target.addProperty(key, value.toString());
        }
    }

    static JsonArray paintBits() {
        JsonArray out = new JsonArray();
        for (String slot : Pw3Params.PAINT_SLOTS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("bit", Pw3Params.paintBit(slot));
            entry.addProperty("slot", slot);
            out.add(entry);
        }
        return out;
    }

    static List<String> sharedGroups() {
        List<String> out = new ArrayList<>();
        for (TuneGroup group : TuneGroup.values()) {
            if (Pw3Params.TUNE_GROUPS.contains(group)) {
                out.add(group.name().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    static List<String> neverGroups() {
        List<String> out = new ArrayList<>();
        for (TuneGroup group : TuneGroup.values()) {
            if (!Pw3Params.TUNE_GROUPS.contains(group)) {
                out.add(group.name().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    static JsonArray pw1Keys() {
        JsonArray out = new JsonArray();
        for (String key : Pw1Tables.TUNE_KEYS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("index", Pw1Tables.tuneIndex(key));
            entry.addProperty("key", key);
            entry.addProperty("group", TuneGroup.of(key).name().toLowerCase(Locale.ROOT));
            out.add(entry);
        }
        return out;
    }

    static List<String> translationKeys() {
        List<String> out = new ArrayList<>();
        for (ShareLayer layer : ShareLayer.values()) {
            out.add(layer.translationKey());
        }
        for (ShareStatus status : ShareStatus.values()) {
            out.add(status.translationKey());
        }
        for (NamespaceReport.Result result : NamespaceReport.Result.values()) {
            out.add(result.translationKey());
        }
        return out;
    }

    static JsonArray vectors(List<DronePreset> presets, ShareTables tables, Problems problems) {
        JsonArray out = ShareSpecJson.vectors(presets, tables);
        String where = WebExport.BASE + "/" + VECTORS;
        for (JsonElement element : out) {
            JsonObject vector = element.getAsJsonObject();
            String name = vector.has("name") ? vector.get("name").getAsString() : "?";
            JsonElement code = vector.get("code");
            if (code == null || !code.isJsonPrimitive()) {
                problems.warning(where + "#" + name, "Sharecode nicht schreibbar: " + (vector.has("status") ? vector.get("status").getAsString() : "?")
                        + (vector.has("error") ? " " + vector.get("error").getAsString() : ""));
                continue;
            }
            ShareDecoded decoded = ShareFormat.decode(code.getAsString(), tables);
            if (!decoded.ok()) {
                problems.warning(where + "#" + name, "Sharecode nicht lesbar: " + decoded.status().name());
                vector.add("check", JsonNull.INSTANCE);
                vector.add("analysis", JsonNull.INSTANCE);
                continue;
            }
            BuildCheck check = PropwashApi.builds().check(decoded.require().build());
            if (!check.known()) {
                vector.add("check", JsonNull.INSTANCE);
                vector.add("analysis", JsonNull.INSTANCE);
                continue;
            }
            vector.add("check", WebExport.check(check));
            Optional<BuildStats> stats = PropwashApi.builds().analyze(decoded.require().build());
            vector.add("analysis", stats.<JsonElement>map(WebExport::stats).orElse(JsonNull.INSTANCE));
        }
        return out;
    }

    static Optional<String> presetCode(DronePreset preset, ShareTables tables, Problems problems, String where) {
        try {
            return Optional.of(Pw3Codec.encode(ShareContent.of(preset.data(), PRESET_LAYERS, Optional.empty()), tables));
        } catch (ShareException e) {
            problems.warning(where, "Sharecode nicht schreibbar: " + e.status().name() + " " + e.key());
            return Optional.empty();
        }
    }

    static Optional<Table> table(String namespace, ShareTables tables) {
        Optional<ShareTable> installed = tables.installedTable(namespace);
        if (installed.isPresent()) {
            return Optional.of(new Table(SOURCE_MOD, installed.get()));
        }
        return tables.directory().byNamespace(namespace).flatMap(AddonDirectory.Addon::table).map(table -> new Table(SOURCE_DIRECTORY, table));
    }

    static List<TableChecksum.Line> lines(ShareTable table) {
        List<TableChecksum.Line> out = new ArrayList<>();
        for (PartKind kind : Pw3Params.KINDS) {
            List<ShareTable.Entry> entries = table.entries(kind);
            for (int i = 0; i < entries.size(); i++) {
                out.add(new TableChecksum.Line(kind, i + 1, entries.get(i).id()));
            }
        }
        return out;
    }

    static String crc(ShareTable table) {
        CRC32 crc = new CRC32();
        crc.update(TableChecksum.text(lines(table)).getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "%08x", crc.getValue());
    }
}

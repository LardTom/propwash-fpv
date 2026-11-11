package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class ShareTableJson {
    public static final int FORMAT = 1;
    public static final String FOLDER = "propwash_share";
    public static final String TABLE_FILE = "table.json";
    public static final String DIRECTORY_FILE = "directory.json";

    private ShareTableJson() {
    }

    public record Parsed(Optional<ShareTable> table, List<String> problems, boolean newerFormat) {
        public Parsed {
            problems = List.copyOf(problems);
        }
    }

    public static String tablePath(String namespace) {
        return "data/" + namespace + "/" + FOLDER + "/" + TABLE_FILE;
    }

    public static Parsed parse(String expectedNamespace, byte[] json) {
        return parse(expectedNamespace, new String(json, StandardCharsets.UTF_8));
    }

    public static Parsed parse(String expectedNamespace, String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return new Parsed(Optional.empty(), List.of("kein JSON-Objekt"), false);
            }
            return parse(expectedNamespace, root.getAsJsonObject());
        } catch (JsonParseException | IllegalStateException e) {
            return new Parsed(Optional.empty(), List.of("ungültiges JSON: " + e.getMessage()), false);
        }
    }

    public static Parsed parse(String expectedNamespace, JsonObject root) {
        List<String> problems = new ArrayList<>();
        int format = intValue(root, "format", -1);
        if (format > FORMAT) {
            return new Parsed(Optional.empty(), List.of("Tabellen-Format " + format + " ist neuer als " + FORMAT + ", Teile gehen als Text"), true);
        }
        if (format != FORMAT) {
            return new Parsed(Optional.empty(), List.of("Feld format fehlt oder ist nicht " + FORMAT), false);
        }
        String namespace = string(root, "namespace").orElse("");
        if (!Pw3Params.validNamespace(namespace)) {
            return new Parsed(Optional.empty(), List.of("ungültiger Namensraum \"" + namespace + "\""), false);
        }
        if (expectedNamespace != null && !expectedNamespace.equals(namespace)) {
            return new Parsed(Optional.empty(), List.of("Namensraum \"" + namespace + "\" passt nicht zum Ordner \"" + expectedNamespace + "\""), false);
        }
        OptionalInt shortId = OptionalInt.empty();
        if (root.has("short_id") && !root.get("short_id").isJsonNull()) {
            int value = intValue(root, "short_id", 0);
            if (value >= 1) {
                shortId = OptionalInt.of(value);
            } else {
                problems.add("short_id muss eine ganze Zahl ab 1 sein");
            }
        }
        EnumMap<PartKind, List<ShareTable.Entry>> parts = new EnumMap<>(PartKind.class);
        JsonObject partsJson = root.has("parts") && root.get("parts").isJsonObject() ? root.getAsJsonObject("parts") : new JsonObject();
        for (String kindKey : partsJson.keySet()) {
            Optional<PartKind> kind = PartKind.byKey(kindKey).filter(k -> k.key().equals(kindKey));
            if (kind.isEmpty() || !partsJson.get(kindKey).isJsonArray()) {
                problems.add("unbekannte Art \"" + kindKey + "\"");
                continue;
            }
            List<ShareTable.Entry> entries = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            JsonArray array = partsJson.getAsJsonArray(kindKey);
            for (int i = 0; i < array.size(); i++) {
                String where = kindKey + "[" + i + "]";
                if (!array.get(i).isJsonObject()) {
                    problems.add(where + ": kein Objekt");
                    entries.add(ShareTable.Entry.of("invalid." + i, "0.0.0"));
                    continue;
                }
                JsonObject entry = array.get(i).getAsJsonObject();
                String id = string(entry, "id").orElse("");
                if (!Pw3Params.validPath(id) || id.contains("/")) {
                    problems.add(where + ": ungültige Id \"" + id + "\"");
                    id = "invalid." + i;
                }
                if (!seen.add(id)) {
                    problems.add(where + ": doppelte Id \"" + id + "\"");
                }
                String since = string(entry, "since").orElse("");
                if (ShareVersion.parse(since).isEmpty()) {
                    problems.add(where + ": since fehlt oder ist keine Version");
                    since = "0.0.0";
                }
                Optional<String> deprecated = string(entry, "deprecated");
                Optional<Identifier> replacedBy = identifier(entry, "replaced_by", where, problems);
                Optional<Identifier> fallback = identifier(entry, "fallback", where, problems);
                Map<String, String> names = new LinkedHashMap<>();
                if (entry.has("name") && entry.get("name").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> name : entry.getAsJsonObject("name").entrySet()) {
                        if (name.getValue().isJsonPrimitive()) {
                            names.put(name.getKey(), name.getValue().getAsString());
                        }
                    }
                }
                entries.add(new ShareTable.Entry(id, since, deprecated, replacedBy, fallback, names));
            }
            parts.put(kind.get(), entries);
        }
        return new Parsed(Optional.of(new ShareTable(namespace, shortId, parts)), problems, false);
    }

    private static Optional<Identifier> identifier(JsonObject object, String key, String where, List<String> problems) {
        Optional<String> text = string(object, key);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        Identifier id = Identifier.tryParse(text.get());
        if (id == null || !text.get().contains(":")) {
            problems.add(where + ": " + key + " ist keine volle Id \"" + text.get() + "\"");
            return Optional.empty();
        }
        return Optional.of(id);
    }

    static Optional<String> string(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = object.get(key).getAsString();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    static int intValue(JsonObject object, String key, int fallback) {
        try {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.getAsJsonPrimitive(key).isNumber()) {
                double value = object.get(key).getAsDouble();
                if (value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE) {
                    return (int) value;
                }
            }
        } catch (NumberFormatException e) {
            return fallback;
        }
        return fallback;
    }
}

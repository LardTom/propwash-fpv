package de.lardtom.propwash.api.share;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ShareTableCheck {
    public static final int FORMAT = 1;
    public static final String PROPWASH = "propwash";
    public static final List<String> KINDS = List.of("frame", "stack", "motor", "prop", "video", "battery", "accessory");
    public static final List<Map<String, String>> PROPWASH_PRESETS = List.of(
            preset("whoop_65", "aio_whoop", "m0802_22000", "p31_4b", "analog_nano", "b1s_300_hv"),
            preset("cinewhoop_3", "aio_20", "m1404_4600", "p3_3b", "digital_lite", "b4s_650"),
            preset("freestyle_5", "stack_30", "m2207_1750", "p51_3b", "digital_hd", "b6s_1300"),
            preset("race_5", "stack_30", "m2306_1860", "p51_29_3b", "analog_race", "b6s_1100"),
            preset("long_range_7", "stack_30", "m2807_1300", "p7_2b", "digital_hd", "b6s_4000_lion"));
    private static final String TABLE_RESOURCE = "/data/propwash/propwash_share/table.json";
    private static final String DIRECTORY_RESOURCE = "/data/propwash/propwash_share/directory.json";
    private static final String PARTS_RESOURCE = "/data/propwash/propwash_parts/";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ShareTableCheck() {
    }

    public record Finding(String code, String where, String message) {
        public Finding {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(where, "where");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public String toString() {
            return this.code + " " + this.where + ": " + this.message;
        }
    }

    public record Options(boolean allowPropwash) {
        public static final Options DEFAULT = new Options(false);
    }

    private static Map<String, String> preset(String frame, String stack, String motor, String prop, String video, String battery) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("frame", frame);
        out.put("stack", stack);
        out.put("motor", motor);
        out.put("prop", prop);
        out.put("video", video);
        out.put("battery", battery);
        return Map.copyOf(out);
    }

    public static void main(String[] args) {
        int code = run(args, System.out);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args, PrintStream out) {
        if (args.length == 0) {
            out.println(usage());
            return 2;
        }
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                out.println("Unbekanntes Argument: " + args[i]);
                out.println(usage());
                return 2;
            }
            options.put(args[i].substring(2), args[++i]);
        }
        try {
            switch (args[0]) {
                case "check" -> {
                    Path resources = Path.of(required(options, "resources"));
                    Optional<Path> baseline = Optional.ofNullable(options.get("baseline")).map(Path::of);
                    List<Finding> findings = check(resources, required(options, "version"), baseline.orElse(null));
                    for (Finding finding : findings) {
                        out.println(finding);
                    }
                    out.println(findings.isEmpty() ? "ShareTableCheck: keine Befunde" : "ShareTableCheck: " + findings.size() + " Befunde");
                    return findings.isEmpty() ? 0 : 1;
                }
                case "add" -> {
                    List<String> added = add(Path.of(required(options, "resources")), required(options, "version"));
                    for (String line : added) {
                        out.println("neu: " + line);
                    }
                    out.println("ShareTableCheck: " + added.size() + " Einträge angehängt");
                    return 0;
                }
                case "freeze" -> {
                    freeze(Path.of(required(options, "resources")), Path.of(required(options, "out")));
                    out.println("ShareTableCheck: Vergleichsdatei geschrieben");
                    return 0;
                }
                case "init" -> {
                    OptionalShort shortId = OptionalShort.parse(options.get("short-id"));
                    List<String> added = init(Path.of(required(options, "resources")), required(options, "namespace"), required(options, "version"),
                            shortId.value());
                    out.println("ShareTableCheck: Tabelle angelegt, " + added.size() + " Einträge");
                    return 0;
                }
                default -> {
                    out.println(usage());
                    return 2;
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            out.println("ShareTableCheck: " + e.getMessage());
            return 2;
        }
    }

    private record OptionalShort(Integer value) {
        static OptionalShort parse(String text) {
            if (text == null) {
                return new OptionalShort(null);
            }
            int value = Integer.parseInt(text);
            if (value < 1) {
                throw new IllegalArgumentException("--short-id muss ab 1 sein");
            }
            return new OptionalShort(value);
        }
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + key + " fehlt");
        }
        return value;
    }

    public static String usage() {
        return String.join("\n",
                "Aufruf: ShareTableCheck <befehl> [optionen]",
                "  check  --resources <dir> --version <v> [--baseline <datei oder jar>]",
                "  add    --resources <dir> --version <v>",
                "  freeze --resources <dir> --out <baseline>",
                "  init   --resources <dir> --namespace <ns> --version <v> [--short-id <n>]");
    }

    public static List<Finding> check(Path resources, String version, Path baseline) throws IOException {
        return check(resources, version, baseline, Options.DEFAULT);
    }

    public static List<Finding> check(Path resources, String version, Path baseline, Options options) throws IOException {
        List<Finding> findings = new ArrayList<>();
        Optional<int[]> current = parseVersion(version);
        if (current.isEmpty()) {
            findings.add(new Finding("VERSION_INVALID", "--version", "keine Version der Form major.minor.patch: " + version));
        }
        Builtin builtin = Builtin.load();
        Map<String, Map<String, List<String>>> partFiles = partFiles(resources);
        Map<String, Path> tables = tables(resources);
        for (String namespace : partFiles.keySet()) {
            if (namespace.equals(PROPWASH) && !options.allowPropwash()) {
                findings.add(new Finding("RESERVED_NAMESPACE", "data/propwash/propwash_parts", "Teile im Namensraum propwash gehören nur Propwash"));
            }
            if (!tables.containsKey(namespace)) {
                findings.add(new Finding("NO_TABLE", "data/" + namespace, "keine Teile-Tabelle data/" + namespace + "/propwash_share/table.json (anlegen mit init)"));
            }
        }
        Map<String, Map<String, List<String>>> baselines = baseline == null ? Map.of() : readBaseline(baseline);
        for (Map.Entry<String, Path> entry : tables.entrySet()) {
            checkTable(entry.getKey(), entry.getValue(), partFiles.getOrDefault(entry.getKey(), Map.of()), current.orElse(null),
                    baseline == null ? null : baselines.getOrDefault(entry.getKey(), Map.of()), builtin, options, findings);
        }
        if (tables.isEmpty() && partFiles.isEmpty()) {
            findings.add(new Finding("NO_TABLE", resources.toString(), "weder Teile noch Tabelle gefunden"));
        }
        return findings;
    }

    private static void checkTable(String folder, Path file, Map<String, List<String>> parts, int[] version, Map<String, List<String>> baseline,
                                   Builtin builtin, Options options, List<Finding> findings) throws IOException {
        String where = "data/" + folder + "/propwash_share/table.json";
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                findings.add(new Finding("INVALID_JSON", where, "kein JSON-Objekt"));
                return;
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException e) {
            findings.add(new Finding("INVALID_JSON", where, "ungültiges JSON: " + e.getMessage()));
            return;
        }
        if (intValue(root, "format") != FORMAT) {
            findings.add(new Finding("FORMAT", where, "format muss " + FORMAT + " sein"));
            return;
        }
        String namespace = string(root, "namespace").orElse("");
        if (!namespace.equals(folder)) {
            findings.add(new Finding("NAMESPACE_MISMATCH", where, "namespace \"" + namespace + "\" passt nicht zum Ordner \"" + folder + "\""));
        }
        if (!validNamespace(namespace)) {
            findings.add(new Finding("INVALID_ID", where, "ungültiger Namensraum \"" + namespace + "\""));
        }
        if (namespace.equals(PROPWASH) && !options.allowPropwash()) {
            findings.add(new Finding("RESERVED_NAMESPACE", where, "der Namensraum propwash gehört nur Propwash"));
        }
        if (root.has("short_id") && !root.get("short_id").isJsonNull()) {
            int shortId = intValue(root, "short_id");
            Optional<String> owner = Optional.ofNullable(builtin.shortIds().get(shortId));
            if (shortId < 1) {
                findings.add(new Finding("SHORT_ID_UNKNOWN", where, "short_id muss eine ganze Zahl ab 1 sein"));
            } else if (owner.isEmpty()) {
                findings.add(new Finding("SHORT_ID_UNKNOWN", where, "short_id " + shortId + " steht nicht im Verzeichnis-Abzug (nur vom offiziellen Verzeichnis vergeben)"));
            } else if (!owner.get().equals(namespace)) {
                findings.add(new Finding("SHORT_ID_CONFLICT", where, "short_id " + shortId + " gehört laut Verzeichnis-Abzug zu " + owner.get()));
            }
        } else if (builtin.shortOf().containsKey(namespace) && !namespace.equals(PROPWASH)) {
            findings.add(new Finding("SHORT_ID_CONFLICT", where, "das Verzeichnis führt " + namespace + " mit short_id " + builtin.shortOf().get(namespace)
                    + ", die Tabelle nennt keine"));
        }
        JsonObject partsJson = root.has("parts") && root.get("parts").isJsonObject() ? root.getAsJsonObject("parts") : new JsonObject();
        Map<String, List<JsonObject>> entries = new LinkedHashMap<>();
        for (String kind : partsJson.keySet()) {
            if (!KINDS.contains(kind) || !partsJson.get(kind).isJsonArray()) {
                findings.add(new Finding("UNKNOWN_KIND", where, "unbekannte Art \"" + kind + "\""));
                continue;
            }
            List<JsonObject> list = new ArrayList<>();
            for (JsonElement element : partsJson.getAsJsonArray(kind)) {
                list.add(element.isJsonObject() ? element.getAsJsonObject() : new JsonObject());
            }
            entries.put(kind, list);
        }
        Map<String, String> kindOfId = new HashMap<>();
        for (String kind : KINDS) {
            List<JsonObject> list = entries.getOrDefault(kind, List.of());
            int[] previous = null;
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                JsonObject entry = list.get(i);
                String at = where + "#" + kind + "[" + i + "]";
                String id = string(entry, "id").orElse("");
                if (!validPath(id)) {
                    findings.add(new Finding("INVALID_ID", at, "ungültige Id \"" + id + "\" (nur a–z, 0–9, _, . und -, ohne Namensraum)"));
                }
                if (!seen.add(id)) {
                    findings.add(new Finding("DUPLICATE_ID", at, "Id \"" + id + "\" doppelt in " + kind));
                } else if (kindOfId.containsKey(id)) {
                    findings.add(new Finding("DUPLICATE_ID", at, "Id \"" + id + "\" steht schon unter " + kindOfId.get(id)));
                }
                kindOfId.putIfAbsent(id, kind);
                Optional<int[]> since = string(entry, "since").flatMap(ShareTableCheck::parseVersion);
                if (since.isEmpty()) {
                    findings.add(new Finding("SINCE_INVALID", at, "since fehlt oder ist keine Version"));
                } else {
                    if (previous != null && compare(since.get(), previous) < 0) {
                        findings.add(new Finding("SINCE_DECREASING", at, "since " + string(entry, "since").orElse("") + " ist kleiner als beim Eintrag davor (eingeschoben?)"));
                    }
                    if (version != null && compare(since.get(), version) > 0) {
                        findings.add(new Finding("SINCE_FUTURE", at, "since " + string(entry, "since").orElse("") + " liegt nach der aktuellen Version"));
                    }
                    previous = since.get();
                }
                Optional<String> deprecated = string(entry, "deprecated");
                Optional<String> replacedBy = string(entry, "replaced_by");
                if (deprecated.isPresent() && parseVersion(deprecated.get()).isEmpty()) {
                    findings.add(new Finding("SINCE_INVALID", at, "deprecated ist keine Version"));
                }
                if (deprecated.isPresent() && replacedBy.isEmpty()) {
                    findings.add(new Finding("DEPRECATED_WITHOUT_REPLACEMENT", at, "deprecated ohne replaced_by"));
                }
                if (replacedBy.isPresent() && deprecated.isEmpty()) {
                    findings.add(new Finding("REPLACEMENT_WITHOUT_DEPRECATED", at, "replaced_by ohne deprecated"));
                }
                Optional<String> fallback = string(entry, "fallback");
                if (fallback.isPresent()) {
                    String[] split = fallback.get().split(":", 2);
                    if (split.length != 2 || !split[0].equals(PROPWASH) || !builtin.ids().getOrDefault(kind, List.of()).contains(split[1])) {
                        findings.add(new Finding("FALLBACK_INVALID", at, "fallback " + fallback.get() + " ist kein eingebautes Propwash-Teil der Art " + kind));
                    }
                }
            }
        }
        for (String kind : KINDS) {
            List<JsonObject> list = entries.getOrDefault(kind, List.of());
            for (int i = 0; i < list.size(); i++) {
                JsonObject entry = list.get(i);
                Optional<String> replacedBy = string(entry, "replaced_by");
                if (replacedBy.isEmpty()) {
                    continue;
                }
                String at = where + "#" + kind + "[" + i + "]";
                String[] split = replacedBy.get().split(":", 2);
                if (split.length != 2) {
                    findings.add(new Finding("REPLACEMENT_INVALID", at, "replaced_by muss eine volle Id sein: " + replacedBy.get()));
                    continue;
                }
                if (split[0].equals(namespace)) {
                    String targetKind = kindOfId.get(split[1]);
                    if (targetKind == null) {
                        findings.add(new Finding("REPLACEMENT_INVALID", at, "Ersatz " + replacedBy.get() + " steht nicht in der Tabelle"));
                    } else if (!targetKind.equals(kind)) {
                        findings.add(new Finding("REPLACEMENT_KIND", at, "Ersatz " + replacedBy.get() + " ist " + targetKind + ", nicht " + kind));
                    } else if (cycle(namespace, string(entry, "id").orElse(""), entries.getOrDefault(kind, List.of()))) {
                        findings.add(new Finding("REPLACEMENT_CYCLE", at, "replaced_by bildet einen Kreis"));
                    }
                } else if (split[0].equals(PROPWASH)) {
                    if (!builtin.ids().getOrDefault(kind, List.of()).contains(split[1])) {
                        findings.add(new Finding("REPLACEMENT_INVALID", at, "Ersatz " + replacedBy.get() + " ist kein eingebautes Propwash-Teil der Art " + kind));
                    }
                } else {
                    findings.add(new Finding("REPLACEMENT_INVALID", at, "Ersatz nur im eigenen Namensraum oder in propwash: " + replacedBy.get()));
                }
            }
        }
        for (Map.Entry<String, List<String>> kindParts : parts.entrySet()) {
            Set<String> listed = new HashSet<>();
            for (JsonObject entry : entries.getOrDefault(kindParts.getKey(), List.of())) {
                string(entry, "id").ifPresent(listed::add);
            }
            for (String id : kindParts.getValue()) {
                if (!listed.contains(id)) {
                    findings.add(new Finding("MISSING_ENTRY", "data/" + folder + "/propwash_parts/" + kindParts.getKey() + "/" + id + ".json",
                            "Teil ohne Eintrag in der Tabelle (hinten anhängen mit add)"));
                }
            }
        }
        if (baseline != null) {
            for (String kind : KINDS) {
                List<String> base = baseline.getOrDefault(kind, List.of());
                List<String> now = new ArrayList<>();
                for (JsonObject entry : entries.getOrDefault(kind, List.of())) {
                    now.add(string(entry, "id").orElse(""));
                }
                for (int i = 0; i < base.size(); i++) {
                    String id = base.get(i);
                    int at = now.indexOf(id);
                    if (at < 0) {
                        findings.add(new Finding("ENTRY_REMOVED", where + "#" + kind, "Eintrag " + id + " (Nummer " + (i + 1) + ") gelöscht; nie löschen, "
                                + "sondern deprecated mit replaced_by"));
                    } else if (at != i) {
                        findings.add(new Finding("ENTRY_MOVED", where + "#" + kind, "Eintrag " + id + " von Nummer " + (i + 1) + " auf " + (at + 1) + " verschoben"));
                    }
                }
                Set<String> baseIds = new HashSet<>(base);
                for (int i = 0; i < Math.min(base.size(), now.size()); i++) {
                    if (!baseIds.contains(now.get(i))) {
                        findings.add(new Finding("ENTRY_INSERTED", where + "#" + kind, "Eintrag " + now.get(i) + " auf Nummer " + (i + 1)
                                + " eingeschoben; neue Einträge nur hinten anhängen"));
                    }
                }
            }
        }
    }

    private static boolean cycle(String namespace, String start, List<JsonObject> entries) {
        Map<String, String> next = new HashMap<>();
        for (JsonObject entry : entries) {
            Optional<String> id = string(entry, "id");
            Optional<String> replacedBy = string(entry, "replaced_by");
            if (id.isPresent() && replacedBy.isPresent() && replacedBy.get().startsWith(namespace + ":")) {
                next.put(id.get(), replacedBy.get().substring(namespace.length() + 1));
            }
        }
        Set<String> seen = new HashSet<>();
        String current = start;
        while (current != null) {
            if (!seen.add(current)) {
                return true;
            }
            current = next.get(current);
        }
        return false;
    }

    public static List<String> add(Path resources, String version) throws IOException {
        if (parseVersion(version).isEmpty()) {
            throw new IllegalArgumentException("keine Version der Form major.minor.patch: " + version);
        }
        Map<String, Path> tables = tables(resources);
        if (tables.isEmpty()) {
            throw new IllegalStateException("keine Tabelle unter " + resources + " (zuerst init)");
        }
        Map<String, Map<String, List<String>>> partFiles = partFiles(resources);
        Builtin builtin = Builtin.load();
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, Path> table : tables.entrySet()) {
            JsonObject root = JsonParser.parseString(Files.readString(table.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject partsJson = root.has("parts") && root.get("parts").isJsonObject() ? root.getAsJsonObject("parts") : new JsonObject();
            Map<String, JsonArray> lists = new LinkedHashMap<>();
            for (String kind : KINDS) {
                lists.put(kind, partsJson.has(kind) && partsJson.get(kind).isJsonArray() ? partsJson.getAsJsonArray(kind) : new JsonArray());
            }
            for (Map.Entry<String, List<String>> kindParts : partFiles.getOrDefault(table.getKey(), Map.of()).entrySet()) {
                JsonArray list = lists.get(kindParts.getKey());
                Set<String> listed = new HashSet<>();
                for (JsonElement element : list) {
                    if (element.isJsonObject()) {
                        string(element.getAsJsonObject(), "id").ifPresent(listed::add);
                    }
                }
                for (String id : new TreeSet<>(kindParts.getValue())) {
                    if (listed.contains(id)) {
                        continue;
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", id);
                    entry.addProperty("since", version);
                    Path file = resources.resolve("data").resolve(table.getKey()).resolve("propwash_parts").resolve(kindParts.getKey()).resolve(id + ".json");
                    suggestFallback(kindParts.getKey(), readObject(file), builtin).ifPresent(fallback -> entry.addProperty("fallback", fallback));
                    list.add(entry);
                    added.add(table.getKey() + ":" + id + " (" + kindParts.getKey() + ")");
                }
            }
            JsonObject parts = new JsonObject();
            for (String kind : KINDS) {
                if (!lists.get(kind).isEmpty()) {
                    parts.add(kind, lists.get(kind));
                }
            }
            for (String kind : partsJson.keySet()) {
                if (!KINDS.contains(kind)) {
                    parts.add(kind, partsJson.get(kind));
                }
            }
            root.add("parts", parts);
            Files.writeString(table.getValue(), write(root), StandardCharsets.UTF_8);
        }
        return added;
    }

    public static List<String> init(Path resources, String namespace, String version, Integer shortId) throws IOException {
        if (!validNamespace(namespace)) {
            throw new IllegalArgumentException("ungültiger Namensraum: " + namespace);
        }
        Path file = resources.resolve("data").resolve(namespace).resolve("propwash_share").resolve("table.json");
        if (Files.exists(file)) {
            throw new IllegalStateException("Tabelle gibt es schon: " + file);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("namespace", namespace);
        if (shortId != null) {
            root.addProperty("short_id", shortId);
        }
        root.add("parts", new JsonObject());
        Files.createDirectories(file.getParent());
        Files.writeString(file, write(root), StandardCharsets.UTF_8);
        return add(resources, version);
    }

    public static void freeze(Path resources, Path out) throws IOException {
        Map<String, Path> tables = tables(resources);
        if (tables.isEmpty()) {
            throw new IllegalStateException("keine Tabelle unter " + resources);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("kind", "propwash_share_baseline");
        JsonObject namespaces = new JsonObject();
        for (Map.Entry<String, Path> table : tables.entrySet()) {
            Map<String, List<String>> ids = idsOf(JsonParser.parseString(Files.readString(table.getValue(), StandardCharsets.UTF_8)).getAsJsonObject());
            JsonObject parts = new JsonObject();
            for (String kind : KINDS) {
                if (!ids.getOrDefault(kind, List.of()).isEmpty()) {
                    JsonArray array = new JsonArray();
                    ids.get(kind).forEach(array::add);
                    parts.add(kind, array);
                }
            }
            namespaces.add(table.getKey(), parts);
        }
        root.add("namespaces", namespaces);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, write(root), StandardCharsets.UTF_8);
    }

    public static Map<String, Map<String, List<String>>> readBaseline(Path baseline) throws IOException {
        Map<String, Map<String, List<String>>> out = new TreeMap<>();
        String name = baseline.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(baseline.toFile())) {
                for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                    String[] parts = entry.getName().split("/");
                    if (parts.length == 4 && parts[0].equals("data") && parts[2].equals("propwash_share") && parts[3].equals("table.json")) {
                        try (InputStream in = zip.getInputStream(entry)) {
                            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                            out.put(parts[1], idsOf(root));
                        }
                    }
                }
            }
            return out;
        }
        JsonObject root = JsonParser.parseString(Files.readString(baseline, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("namespaces") && root.get("namespaces").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("namespaces").entrySet()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("parts", entry.getValue());
                out.put(entry.getKey(), idsOf(wrapper));
            }
        } else if (root.has("namespace")) {
            out.put(root.get("namespace").getAsString(), idsOf(root));
        }
        return out;
    }

    private static Map<String, List<String>> idsOf(JsonObject root) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        JsonObject parts = root.has("parts") && root.get("parts").isJsonObject() ? root.getAsJsonObject("parts") : new JsonObject();
        for (String kind : KINDS) {
            List<String> ids = new ArrayList<>();
            if (parts.has(kind) && parts.get(kind).isJsonArray()) {
                for (JsonElement element : parts.getAsJsonArray(kind)) {
                    if (element.isJsonPrimitive()) {
                        ids.add(element.getAsString());
                    } else if (element.isJsonObject()) {
                        ids.add(string(element.getAsJsonObject(), "id").orElse(""));
                    }
                }
            }
            out.put(kind, ids);
        }
        return out;
    }

    public static Optional<String> suggestFallback(String kind, JsonObject part, Builtin builtin) {
        List<String> candidates = new ArrayList<>();
        if (kind.equals("accessory")) {
            candidates.addAll(builtin.ids().getOrDefault(kind, List.of()));
        } else {
            for (Map<String, String> preset : PROPWASH_PRESETS) {
                String id = preset.get(kind);
                if (id != null && !candidates.contains(id)) {
                    candidates.add(id);
                }
            }
        }
        String best = null;
        double bestScore = Double.MAX_VALUE;
        for (String candidate : candidates) {
            JsonObject other = builtin.part(kind, candidate);
            if (other == null) {
                continue;
            }
            double score = distance(kind, part, other);
            if (score < bestScore - 1e-9) {
                bestScore = score;
                best = candidate;
            }
        }
        return Optional.ofNullable(best).map(id -> PROPWASH + ":" + id);
    }

    static double distance(String kind, JsonObject a, JsonObject b) {
        return switch (kind) {
            case "frame" -> (same(a, b, "role") ? 0 : 10) + logDistance(number(a, "wheelbase_mm"), number(b, "wheelbase_mm"));
            case "stack" -> (same(a, b, "mount") ? 0 : 10) + logDistance(maxCells(a), maxCells(b)) + 0.1 * logDistance(number(a, "esc_continuous_a"),
                    number(b, "esc_continuous_a"));
            case "motor" -> (same(a, b, "mount") ? 0 : 10) + logDistance(number(a, "kv"), number(b, "kv"));
            case "prop" -> logDistance(number(a, "diameter_mm"), number(b, "diameter_mm"));
            case "video" -> (same(a, b, "link") ? 0 : 10) + logDistance(number(a, "mass_g"), number(b, "mass_g"));
            case "battery" -> 10 * logDistance(maxCells(a), maxCells(b)) + logDistance(number(a, "capacity_mah"), number(b, "capacity_mah"));
            default -> (same(a, b, "slot") ? 0 : 10) + logDistance(number(a, "mass_g"), number(b, "mass_g"));
        };
    }

    private static boolean same(JsonObject a, JsonObject b, String key) {
        return Objects.equals(string(a, key).orElse(null), string(b, key).orElse(null));
    }

    private static double number(JsonObject object, String key) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : 1.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    private static double maxCells(JsonObject object) {
        if (object.has("cells") && object.get("cells").isJsonArray()) {
            double max = 1.0;
            for (JsonElement element : object.getAsJsonArray("cells")) {
                max = Math.max(max, element.getAsDouble());
            }
            return max;
        }
        return number(object, "cells");
    }

    private static double logDistance(double a, double b) {
        return Math.abs(Math.log(Math.max(a, 1e-6)) - Math.log(Math.max(b, 1e-6)));
    }

    public record Builtin(Map<String, List<String>> ids, Map<Integer, String> shortIds, Map<String, Integer> shortOf, Map<String, JsonObject> parts) {
        public static Builtin load() {
            Map<String, List<String>> ids = new LinkedHashMap<>();
            JsonObject table = resource(TABLE_RESOURCE).orElse(new JsonObject());
            Map<String, List<String>> fromTable = idsOf(table);
            for (String kind : KINDS) {
                ids.put(kind, List.copyOf(fromTable.getOrDefault(kind, List.of())));
            }
            Map<Integer, String> shortIds = new TreeMap<>();
            Map<String, Integer> shortOf = new TreeMap<>();
            shortIds.put(1, PROPWASH);
            shortOf.put(PROPWASH, 1);
            Optional<JsonObject> directory = resource(DIRECTORY_RESOURCE);
            if (directory.isPresent() && directory.get().has("addons") && directory.get().get("addons").isJsonArray()) {
                for (JsonElement element : directory.get().getAsJsonArray("addons")) {
                    if (element.isJsonObject()) {
                        JsonObject addon = element.getAsJsonObject();
                        int shortId = intValue(addon, "short_id");
                        Optional<String> namespace = string(addon, "namespace");
                        if (shortId >= 1 && namespace.isPresent()) {
                            shortIds.putIfAbsent(shortId, namespace.get());
                            shortOf.putIfAbsent(namespace.get(), shortId);
                        }
                    }
                }
            }
            Map<String, JsonObject> parts = new HashMap<>();
            for (String kind : KINDS) {
                for (String id : ids.get(kind)) {
                    resource(PARTS_RESOURCE + kind + "/" + id + ".json").ifPresent(part -> parts.put(kind + "/" + id, part));
                }
            }
            return new Builtin(ids, shortIds, shortOf, parts);
        }

        public JsonObject part(String kind, String id) {
            return this.parts.get(kind + "/" + id);
        }
    }

    private static Optional<JsonObject> resource(String path) {
        try (InputStream in = ShareTableCheck.class.getResourceAsStream(path)) {
            if (in == null) {
                return Optional.empty();
            }
            JsonElement element = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
        } catch (IOException | JsonParseException e) {
            return Optional.empty();
        }
    }

    static Map<String, Map<String, List<String>>> partFiles(Path resources) throws IOException {
        Map<String, Map<String, List<String>>> out = new TreeMap<>();
        Path data = resources.resolve("data");
        if (!Files.isDirectory(data)) {
            return out;
        }
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path namespaceDir : namespaces.sorted().toList()) {
                Path partsDir = namespaceDir.resolve("propwash_parts");
                if (!Files.isDirectory(partsDir)) {
                    continue;
                }
                Map<String, List<String>> kinds = new LinkedHashMap<>();
                for (String kind : KINDS) {
                    Path kindDir = partsDir.resolve(kind);
                    if (!Files.isDirectory(kindDir)) {
                        continue;
                    }
                    List<String> ids = new ArrayList<>();
                    try (Stream<Path> files = Files.list(kindDir)) {
                        for (Path file : files.sorted().toList()) {
                            String name = file.getFileName().toString();
                            if (Files.isRegularFile(file) && name.endsWith(".json")) {
                                ids.add(name.substring(0, name.length() - ".json".length()));
                            }
                        }
                    }
                    kinds.put(kind, ids);
                }
                out.put(namespaceDir.getFileName().toString(), kinds);
            }
        }
        return out;
    }

    static Map<String, Path> tables(Path resources) throws IOException {
        Map<String, Path> out = new TreeMap<>();
        Path data = resources.resolve("data");
        if (!Files.isDirectory(data)) {
            return out;
        }
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path namespaceDir : namespaces.sorted().toList()) {
                Path table = namespaceDir.resolve("propwash_share").resolve("table.json");
                if (Files.isRegularFile(table)) {
                    out.put(namespaceDir.getFileName().toString(), table);
                }
            }
        }
        return out;
    }

    private static JsonObject readObject(Path file) {
        try {
            JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (IOException | JsonParseException e) {
            return new JsonObject();
        }
    }

    public static String write(JsonObject root) {
        StringBuilder out = new StringBuilder("{\n");
        List<String> keys = new ArrayList<>(root.keySet());
        for (int k = 0; k < keys.size(); k++) {
            String key = keys.get(k);
            JsonElement value = root.get(key);
            out.append("  ").append(GSON.toJson(key)).append(": ");
            if (value.isJsonObject() && (key.equals("parts") || key.equals("namespaces"))) {
                writeGroups(out, value.getAsJsonObject(), "  ");
            } else {
                out.append(inline(value));
            }
            out.append(k + 1 < keys.size() ? ",\n" : "\n");
        }
        return out.append("}\n").toString();
    }

    private static void writeGroups(StringBuilder out, JsonObject groups, String indent) {
        if (groups.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        List<String> keys = new ArrayList<>(groups.keySet());
        for (int k = 0; k < keys.size(); k++) {
            String key = keys.get(k);
            JsonElement value = groups.get(key);
            out.append(indent).append("  ").append(GSON.toJson(key)).append(": ");
            if (value.isJsonObject()) {
                writeGroups(out, value.getAsJsonObject(), indent + "  ");
            } else if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) {
                out.append("[\n");
                JsonArray array = value.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    out.append(indent).append("    ").append(inline(array.get(i))).append(i + 1 < array.size() ? ",\n" : "\n");
                }
                out.append(indent).append("  ]");
            } else {
                out.append(inline(value));
            }
            out.append(k + 1 < keys.size() ? ",\n" : "\n");
        }
        out.append(indent).append("}");
    }

    static String inline(JsonElement value) {
        if (value.isJsonObject()) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(GSON.toJson(entry.getKey())).append(": ").append(inline(entry.getValue()));
            }
            return out.append("}").toString();
        }
        if (value.isJsonArray()) {
            StringBuilder out = new StringBuilder("[");
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(inline(array.get(i)));
            }
            return out.append("]").toString();
        }
        return GSON.toJson(value);
    }

    static Optional<String> string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return Optional.empty();
        }
        JsonPrimitive primitive = object.getAsJsonPrimitive(key);
        String value = primitive.getAsString();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    static int intValue(JsonObject object, String key) {
        try {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.getAsJsonPrimitive(key).isNumber()) {
                double value = object.get(key).getAsDouble();
                return value == Math.rint(value) && Math.abs(value) < Integer.MAX_VALUE ? (int) value : -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return -1;
    }

    public static Optional<int[]> parseVersion(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String rest = text.strip();
        int plus = rest.indexOf('+');
        if (plus >= 0) {
            rest = rest.substring(0, plus);
        }
        int dash = rest.indexOf('-');
        if (dash >= 0) {
            rest = rest.substring(0, dash);
        }
        String[] parts = rest.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 9 || !parts[i].chars().allMatch(c -> c >= '0' && c <= '9')) {
                return Optional.empty();
            }
            out[i] = Integer.parseInt(parts[i]);
        }
        return Optional.of(out);
    }

    static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    static boolean validNamespace(String text) {
        return text != null && !text.isEmpty() && text.length() <= 64 && chars(text) && !text.equals(".") && !text.equals("..");
    }

    static boolean validPath(String text) {
        return text != null && !text.isEmpty() && text.length() <= 128 && chars(text);
    }

    private static boolean chars(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    static Set<String> kinds() {
        return new LinkedHashSet<>(KINDS);
    }
}

package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.share.AddonDirectory;
import de.lardtom.propwash.share.ShareConvert;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareFormat;
import de.lardtom.propwash.share.ShareSpecJson;
import de.lardtom.propwash.share.ShareTables;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.CRC32;

final class Format1Compare {
    static final int LIMIT = 200;
    static final String DROPPED_LANG = "justmoreparts.share.";
    static final List<String> KINDS = List.of("motor", "stack", "prop");
    static final List<String> PART_KINDS = List.of("frame", "stack", "motor", "prop", "video", "battery", "accessory");
    static final List<String> NEW_ACCESSORY_FIELDS = List.of("recess_mm", "drop_mm", "hangs_straight");
    static final Set<String> HEAT_VALUES =Set.of("sustained_motor_temp_c", "motor_temp_minute_c", "mixed_motor_temp_c");
    static final String HEAT_WARNING = "motor_thermal";
    static final String HEAT = "Motor-Hitze seit 0.6.0 (heat, Altexport mit Propwash 0.5)";
    static final Set<String> PW1_SPECIAL = Set.of("layers", "namespaces", "tune_keys", "statuses", "aliases", "pw2");
    static final Map<String, String> VECTOR_NAMES = Map.of("preset:", "preset:", "pw2_preset:", "preset:", "preset_parts:", "parts:",
            "pw2_preset_parts:", "parts:");

    record Report(List<String> differences, int differenceCount, List<String> allowed, Map<String, Integer> compared, Set<String> namespaces) {
        boolean pass() {
            return this.differenceCount == 0;
        }

        String text() {
            StringBuilder out = new StringBuilder();
            out.append(this.pass() ? "GRÜN" : "ROT").append(" – Vergleich Format 1 (Altexport) mit Format 2, Namensräume ")
                    .append(String.join(", ", this.namespaces)).append('\n');
            out.append("Verglichen:");
            this.compared.forEach((key, value) -> out.append(' ').append(key).append('=').append(value));
            out.append('\n');
            out.append("Abweichungen: ").append(this.differenceCount).append('\n');
            this.differences.forEach(line -> out.append("  ! ").append(line).append('\n'));
            out.append("Zulässig: ").append(this.allowed.size()).append('\n');
            Map<String, List<String>> kinds = new TreeMap<>();
            this.allowed.forEach(line -> kinds.computeIfAbsent(line.substring(0, line.indexOf(':') < 0 ? line.length() : line.indexOf(':')),
                    k -> new ArrayList<>()).add(line));
            kinds.forEach((key, lines) -> out.append("  ").append(lines.size() == 1 ? lines.getFirst() : key + ": " + lines.size() + " Einträge")
                    .append('\n'));
            return out.toString();
        }
    }

    private final Path old;
    private final Path current;
    private final List<String> differences = new ArrayList<>();
    private int differenceCount;
    private final List<String> allowed = new ArrayList<>();
    private final Map<String, Integer> compared = new LinkedHashMap<>();
    private final Set<String> scope = new TreeSet<>();
    private final Map<String, JsonObject> oldParts = new LinkedHashMap<>();
    private final Map<String, JsonObject> newParts = new LinkedHashMap<>();
    private final Map<String, JsonObject> newPresets = new LinkedHashMap<>();
    private boolean heat;
    private JsonObject newSpec;
    private ShareTables legacyTables;

    private Format1Compare(Path old, Path current) {
        this.old = old;
        this.current = current;
    }

    static Report compare(Path old, Path current) throws IOException {
        return new Format1Compare(old, current).run();
    }

    private static JsonElement read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    private boolean inScope(String id) {
        return this.scope.contains(namespace(id));
    }

    private void count(String what) {
        this.compared.merge(what, 1, Integer::sum);
    }

    private void difference(String text) {
        this.differenceCount++;
        if (this.differences.size() < LIMIT) {
            this.differences.add(text);
        }
    }

    private void deep(String path, JsonElement a, JsonElement b) {
        if (a == null) {
            a = JsonNull.INSTANCE;
        }
        if (b == null) {
            b = JsonNull.INSTANCE;
        }
        if (a.isJsonObject() && b.isJsonObject()) {
            Set<String> keys = new TreeSet<>(a.getAsJsonObject().keySet());
            keys.addAll(b.getAsJsonObject().keySet());
            for (String key : keys) {
                if (!b.getAsJsonObject().has(key)) {
                    this.difference(path + "." + key + " fehlt im neuen Export");
                } else if (!a.getAsJsonObject().has(key)) {
                    this.difference(path + "." + key + " nur im neuen Export");
                } else {
                    this.deep(path + "." + key, a.getAsJsonObject().get(key), b.getAsJsonObject().get(key));
                }
            }
            return;
        }
        if (a.isJsonArray() && b.isJsonArray()) {
            JsonArray x = a.getAsJsonArray();
            JsonArray y = b.getAsJsonArray();
            if (x.size() != y.size()) {
                this.difference(path + ": Länge " + x.size() + " statt " + y.size() + " (alt " + shorten(x) + ", neu " + shorten(y) + ")");
                return;
            }
            for (int i = 0; i < x.size(); i++) {
                this.deep(path + "[" + i + "]", x.get(i), y.get(i));
            }
            return;
        }
        if (!a.equals(b)) {
            this.difference(path + ": alt " + shorten(a) + ", neu " + shorten(b));
        }
    }

    private static String shorten(JsonElement element) {
        String text = element.toString();
        return text.length() > 160 ? text.substring(0, 157) + "…" : text;
    }

    private Report run() throws IOException {
        JsonObject oldManifest = read(this.old.resolve("manifest.json")).getAsJsonObject();
        JsonObject newManifest = read(this.current.resolve("manifest.json")).getAsJsonObject();
        if (oldManifest.get("format").getAsInt() != 1) {
            this.difference("Altexport hat nicht Format 1");
        }
        if (newManifest.get("format").getAsInt() != WebExport.FORMAT) {
            this.difference("neuer Export hat nicht Format " + WebExport.FORMAT);
        }
        newManifest.getAsJsonArray("namespaces").forEach(e -> this.scope.add(e.getAsString()));
        JsonObject oldMods = oldManifest.getAsJsonObject("mods");
        this.heat = oldMods != null && oldMods.has("propwash") && oldMods.get("propwash").getAsString().startsWith("0.5.");
        for (JsonElement element : read(this.old.resolve("parts.json")).getAsJsonArray()) {
            this.oldParts.put(element.getAsJsonObject().get("id").getAsString(), element.getAsJsonObject());
        }
        for (String namespace : this.scope) {
            for (JsonElement element : read(this.current.resolve(namespace).resolve("parts.json")).getAsJsonArray()) {
                this.newParts.put(element.getAsJsonObject().get("id").getAsString(), element.getAsJsonObject());
            }
            for (JsonElement element : read(this.current.resolve(namespace).resolve("presets.json")).getAsJsonArray()) {
                this.newPresets.put(element.getAsJsonObject().get("id").getAsString(), element.getAsJsonObject());
            }
        }
        this.parts();
        this.mounts();
        this.chemistries();
        boolean base = this.scope.contains("propwash");
        if (base) {
            this.count("rules");
            this.deep("rules.json", read(this.old.resolve("rules.json")), this.heatRules(read(this.old.resolve("rules.json")).getAsJsonObject(),
                    read(this.current.resolve("base/rules.json")).getAsJsonObject()));
            this.count("analysis_labels");
            this.deep("analysis_labels.json", read(this.old.resolve("analysis_labels.json")), read(this.current.resolve("base/analysis_labels.json")));
        }
        this.paint(base);
        this.tune(base);
        this.presets();
        this.analysis();
        this.assets();
        this.lang();
        this.sharecodeManifest(oldManifest, newManifest);
        if (base && Files.isDirectory(this.old.resolve("sharecode"))) {
            Path spec = this.current.resolve("base/sharecode/spec.json");
            Path vectors = this.current.resolve("base/sharecode/vectors.json");
            if (!Files.isRegularFile(spec) || !Files.isRegularFile(vectors)) {
                this.difference("base/sharecode/spec.json oder vectors.json fehlt im neuen Export");
            } else {
                this.newSpec = read(spec).getAsJsonObject();
                if (Files.isRegularFile(this.old.resolve("sharecode/spec.json"))) {
                    this.spec(read(this.old.resolve("sharecode/spec.json")).getAsJsonObject());
                }
                if (Files.isRegularFile(this.old.resolve("sharecode/vectors.json"))) {
                    this.vectors(read(this.old.resolve("sharecode/vectors.json")).getAsJsonArray(), read(vectors).getAsJsonArray());
                }
            }
        }
        this.tables();
        return new Report(List.copyOf(this.differences), this.differenceCount, List.copyOf(this.allowed), Map.copyOf(new TreeMap<>(this.compared)),
                Set.copyOf(this.scope));
    }

    private Set<String> oldNamespaces() {
        Set<String> out = new TreeSet<>();
        this.oldParts.keySet().forEach(id -> out.add(namespace(id)));
        return out;
    }

    private void parts() {
        for (Map.Entry<String, JsonObject> entry : this.oldParts.entrySet()) {
            String id = entry.getKey();
            if (!this.inScope(id)) {
                continue;
            }
            JsonObject now = this.newParts.get(id);
            if (now == null) {
                this.difference("parts.json: Teil " + id + " fehlt im neuen Export");
                continue;
            }
            this.count("parts");
            JsonObject copy = now.deepCopy();
            JsonObject data = copy.getAsJsonObject("data");
            if ("accessory".equals(copy.get("category").getAsString()) && !entry.getValue().getAsJsonObject("data").has("led_path")) {
                JsonElement path = data.remove("led_path");
                if (path == null || !path.isJsonArray()) {
                    this.difference("parts.json#" + id + ".data.led_path fehlt oder ist keine Liste");
                } else {
                    this.allowed.add("led_path (neues Feld): " + id + " " + path);
                }
            }
            if ("accessory".equals(copy.get("category").getAsString())) {
                for (String field : NEW_ACCESSORY_FIELDS) {
                    if (data.has(field) && !entry.getValue().getAsJsonObject("data").has(field)) {
                        this.allowed.add(field + " (neues Feld): " + id + " " + data.remove(field));
                    }
                }
            }
            copy.getAsJsonObject("paint").remove("default_colors");
            this.deep("parts.json#" + id, entry.getValue(), copy);
        }
        for (String id : this.newParts.keySet()) {
            if (!this.oldParts.containsKey(id)) {
                this.allowed.add("neues Teil: " + id);
            }
        }
        for (String namespace : this.scope) {
            List<String> before = this.oldParts.keySet().stream().filter(id -> namespace(id).equals(namespace) && this.newParts.containsKey(id)).toList();
            List<String> after = this.newParts.keySet().stream().filter(id -> namespace(id).equals(namespace) && this.oldParts.containsKey(id)).toList();
            if (!before.equals(after)) {
                this.difference("parts.json: Reihenfolge in " + namespace + " weicht ab");
            }
        }
    }

    private record Mount(JsonElement name, Set<String> holders, Set<String> parts) {
    }

    private void mounts() throws IOException {
        JsonObject oldMounts = read(this.old.resolve("mounts.json")).getAsJsonObject();
        Map<String, Map<String, Mount>> merged = new TreeMap<>();
        for (String namespace : this.scope) {
            JsonObject mounts = read(this.current.resolve(namespace).resolve("mounts.json")).getAsJsonObject();
            for (String kind : KINDS) {
                String holderKey = kind.equals("prop") ? "motors" : "frames";
                for (JsonElement element : mounts.getAsJsonArray(kind)) {
                    JsonObject entry = element.getAsJsonObject();
                    String id = entry.get("id").getAsString();
                    Mount mount = merged.computeIfAbsent(kind, k -> new TreeMap<>()).computeIfAbsent(id,
                            k -> new Mount(entry.get("name"), new TreeSet<>(), new TreeSet<>()));
                    if (!mount.name().equals(entry.get("name"))) {
                        this.difference(namespace + "/mounts.json: Name von " + kind + " " + id + " anders als in einem anderen Namensraum");
                    }
                    entry.getAsJsonArray(holderKey).forEach(e -> mount.holders().add(e.getAsString()));
                    entry.getAsJsonArray("parts").forEach(e -> mount.parts().add(e.getAsString()));
                }
            }
        }
        for (String kind : KINDS) {
            String holderKey = kind.equals("prop") ? "motors" : "frames";
            Map<String, Mount> now = merged.getOrDefault(kind, Map.of());
            Set<String> seen = new TreeSet<>();
            for (JsonElement element : oldMounts.getAsJsonArray(kind)) {
                JsonObject entry = element.getAsJsonObject();
                String id = entry.get("id").getAsString();
                Set<String> holders = new TreeSet<>();
                Set<String> parts = new TreeSet<>();
                entry.getAsJsonArray(holderKey).forEach(e -> {
                    if (this.inScope(e.getAsString())) {
                        holders.add(e.getAsString());
                    }
                });
                entry.getAsJsonArray("parts").forEach(e -> {
                    if (this.inScope(e.getAsString())) {
                        parts.add(e.getAsString());
                    }
                });
                if (holders.isEmpty() && parts.isEmpty()) {
                    continue;
                }
                seen.add(id);
                Mount mount = now.get(id);
                if (mount == null) {
                    this.difference("mounts.json: " + kind + " " + id + " fehlt im neuen Export");
                    continue;
                }
                this.count("mounts");
                this.deep("mounts.json#" + kind + ":" + id + ".name", entry.get("name"), mount.name());
                Set<String> newHolders = new TreeSet<>(mount.holders());
                newHolders.retainAll(this.oldParts.keySet());
                Set<String> newParts = new TreeSet<>(mount.parts());
                newParts.retainAll(this.oldParts.keySet());
                if (!holders.equals(newHolders)) {
                    this.difference("mounts.json#" + kind + ":" + id + "." + holderKey + ": alt " + holders + ", neu " + newHolders);
                }
                if (!parts.equals(newParts)) {
                    this.difference("mounts.json#" + kind + ":" + id + ".parts: alt " + parts + ", neu " + newParts);
                }
            }
            for (Map.Entry<String, Mount> entry : now.entrySet()) {
                if (seen.contains(entry.getKey())) {
                    continue;
                }
                Set<String> known = new TreeSet<>(entry.getValue().holders());
                known.addAll(entry.getValue().parts());
                known.retainAll(this.oldParts.keySet());
                if (known.isEmpty()) {
                    this.allowed.add("neue Halterung (nur neue Teile): " + kind + " " + entry.getKey());
                } else {
                    this.difference("mounts.json: " + kind + " " + entry.getKey() + " nur im neuen Export mit " + known);
                }
            }
        }
    }

    private void chemistries() throws IOException {
        Map<String, JsonObject> merged = new TreeMap<>();
        Map<String, Set<String>> users = new TreeMap<>();
        for (String namespace : this.scope) {
            for (JsonElement element : read(this.current.resolve(namespace).resolve("chemistries.json")).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject().deepCopy();
                String id = entry.get("id").getAsString();
                JsonArray usedBy = entry.remove("used_by").getAsJsonArray();
                usedBy.forEach(e -> users.computeIfAbsent(id, k -> new TreeSet<>()).add(e.getAsString()));
                JsonObject before = merged.putIfAbsent(id, entry);
                if (before != null && !before.equals(entry)) {
                    this.difference(namespace + "/chemistries.json: " + id + " anders als in einem anderen Namensraum");
                }
            }
        }
        for (JsonElement element : read(this.old.resolve("chemistries.json")).getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject().deepCopy();
            String id = entry.get("id").getAsString();
            Set<String> usedBy = new TreeSet<>();
            entry.remove("used_by").getAsJsonArray().forEach(e -> {
                if (this.inScope(e.getAsString())) {
                    usedBy.add(e.getAsString());
                }
            });
            if (!this.inScope(id) && usedBy.isEmpty()) {
                continue;
            }
            JsonObject now = merged.get(id);
            if (now == null) {
                this.difference("chemistries.json: " + id + " fehlt im neuen Export");
                continue;
            }
            this.count("chemistries");
            this.deep("chemistries.json#" + id, entry, now);
            Set<String> newUsers = new TreeSet<>(users.getOrDefault(id, Set.of()));
            newUsers.retainAll(this.oldParts.keySet());
            if (!usedBy.equals(newUsers)) {
                this.difference("chemistries.json#" + id + ".used_by: alt " + usedBy + ", neu " + newUsers);
            }
        }
    }

    private void paint(boolean base) throws IOException {
        JsonObject oldPaint = read(this.old.resolve("paint.json")).getAsJsonObject();
        if (base) {
            JsonArray oldSlots = oldPaint.getAsJsonArray("slots");
            JsonArray newSlots = read(this.current.resolve("base/paint.json")).getAsJsonObject().getAsJsonArray("slots");
            if (oldSlots.size() != newSlots.size()) {
                this.difference("paint.json slots: " + oldSlots.size() + " statt " + newSlots.size());
            } else {
                for (int i = 0; i < oldSlots.size(); i++) {
                    this.count("paint_slots");
                    this.deep("paint.json#slots[" + i + "]", oldSlots.get(i), newSlots.get(i));
                }
            }
        }
        JsonObject defaults = oldPaint.getAsJsonObject("default_colors");
        for (String id : this.oldParts.keySet()) {
            if (!this.inScope(id) || !this.newParts.containsKey(id)) {
                continue;
            }
            JsonElement before = defaults.has(id) ? defaults.get(id) : new JsonObject();
            JsonElement after = this.newParts.get(id).getAsJsonObject("paint").get("default_colors");
            this.count("default_colors");
            this.deep("paint.json#default_colors." + id, before, after);
        }
    }

    private void tune(boolean base) throws IOException {
        JsonObject oldTune = read(this.old.resolve("tune.json")).getAsJsonObject();
        if (base) {
            JsonObject newTune = read(this.current.resolve("base/tune.json")).getAsJsonObject();
            JsonObject oldGroups = oldTune.getAsJsonObject("groups");
            JsonObject newGroups = newTune.getAsJsonObject("groups");
            for (String key : oldGroups.keySet()) {
                if (newGroups.has(key)) {
                    this.deep("tune.json#groups." + key, oldGroups.get(key), newGroups.get(key));
                } else {
                    this.difference("tune.json#groups." + key + " fehlt im neuen Export");
                }
            }
            this.count("tune_groups");
            this.deep("tune.json#group_names", oldTune.get("group_names"), newTune.get("group_names"));
            if (oldTune.has("pw1_keys")) {
                this.count("pw1_keys");
                this.deep("tune.json#pw1_keys", oldTune.get("pw1_keys"), newTune.get("pw1_keys"));
            }
        }
        for (Map.Entry<String, JsonElement> entry : oldTune.getAsJsonObject("defaults").entrySet()) {
            if (!this.inScope(entry.getKey())) {
                continue;
            }
            JsonObject preset = this.newPresets.get(entry.getKey());
            if (preset == null) {
                this.difference("tune.json#defaults: Preset " + entry.getKey() + " fehlt im neuen Export");
                continue;
            }
            this.count("tune_defaults");
            this.deep("tune.json#defaults." + entry.getKey(), entry.getValue(), preset.get("default_tune"));
        }
    }

    private void presets() throws IOException {
        List<String> before = new ArrayList<>();
        for (JsonElement element : read(this.old.resolve("presets.json")).getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject().deepCopy();
            String id = entry.get("id").getAsString();
            if (!this.inScope(id)) {
                continue;
            }
            before.add(id);
            JsonObject now = this.newPresets.get(id);
            if (now == null) {
                this.difference("presets.json: " + id + " fehlt im neuen Export");
                continue;
            }
            JsonObject copy = now.deepCopy();
            copy.remove("default_tune");
            if (entry.has("sharecode")) {
                this.converted("presets.json#" + id + ".sharecode", entry.remove("sharecode"), copy.remove("sharecode"));
                this.count("preset_sharecodes");
            }
            if (entry.has("analysis") && copy.has("analysis")) {
                copy.add("analysis", this.heatAnalysis("presets.json#" + id + ".analysis", entry.get("analysis"), copy.get("analysis")));
            }
            this.count("presets");
            this.deep("presets.json#" + id, entry, copy);
        }
        List<String> after = new ArrayList<>();
        for (String id : this.newPresets.keySet()) {
            if (before.contains(id)) {
                after.add(id);
            } else {
                this.allowed.add("neues Preset: " + id);
            }
        }
        for (String namespace : this.scope) {
            if (!before.stream().filter(id -> namespace(id).equals(namespace)).toList()
                    .equals(after.stream().filter(id -> namespace(id).equals(namespace)).toList())) {
                this.difference("presets.json: Reihenfolge in " + namespace + " weicht ab");
            }
        }
    }

    private void analysis() throws IOException {
        Path oldDir = this.old.resolve("analysis");
        for (String file : ExportDiff.files(oldDir)) {
            String namespace = file.substring(0, file.indexOf('/'));
            if (!this.scope.contains(namespace)) {
                continue;
            }
            Path now = this.current.resolve(namespace).resolve("analysis").resolve(file.substring(namespace.length() + 1));
            if (!Files.isRegularFile(now)) {
                this.difference("analysis/" + file + " fehlt im neuen Export");
                continue;
            }
            this.count("analysis_files");
            JsonObject before = read(oldDir.resolve(file)).getAsJsonObject();
            JsonObject after = read(now).getAsJsonObject();
            String where = "analysis/" + file;
            this.deep(where + ".preset", before.get("preset"), after.get("preset"));
            this.deep(where + ".camera_view", before.get("camera_view"), after.get("camera_view"));
            Map<String, JsonElement> oldVariants = new LinkedHashMap<>();
            for (JsonElement variant : before.getAsJsonArray("variants")) {
                String id = variant.getAsJsonObject().get("id").getAsString();
                if (this.inScope(id)) {
                    oldVariants.put(variant.getAsJsonObject().get("category").getAsString() + ":" + id, variant);
                }
            }
            Map<String, JsonElement> newVariants = new LinkedHashMap<>();
            for (JsonElement variant : after.getAsJsonArray("variants")) {
                String id = variant.getAsJsonObject().get("id").getAsString();
                String key = variant.getAsJsonObject().get("category").getAsString() + ":" + id;
                if (this.oldParts.containsKey(id)) {
                    newVariants.put(key, variant);
                } else {
                    this.allowed.add("Variante mit neuem Teil: " + file + " " + key);
                }
            }
            for (Map.Entry<String, JsonElement> entry : oldVariants.entrySet()) {
                JsonElement variant = newVariants.get(entry.getKey());
                if (variant == null) {
                    this.difference(where + ": Variante " + entry.getKey() + " fehlt im neuen Export");
                    continue;
                }
                this.count("analysis_variants");
                JsonObject normalized = variant.getAsJsonObject().deepCopy();
                if (normalized.has("analysis")) {
                    normalized.add("analysis", this.heatAnalysis(where + "#" + entry.getKey() + ".analysis",
                            entry.getValue().getAsJsonObject().get("analysis"), normalized.get("analysis")));
                }
                this.deep(where + "#" + entry.getKey(), entry.getValue(), normalized);
            }
            for (String key : newVariants.keySet()) {
                if (!oldVariants.containsKey(key) && this.inScope(key.substring(key.indexOf(':') + 1))) {
                    this.difference(where + ": Variante " + key + " nur im neuen Export");
                }
            }
            List<String> order = newVariants.keySet().stream().filter(oldVariants::containsKey).toList();
            if (!order.equals(oldVariants.keySet().stream().filter(newVariants::containsKey).toList())) {
                this.difference(where + ": Reihenfolge der Varianten weicht ab");
            }
        }
        for (String namespace : this.scope) {
            Path dir = this.current.resolve(namespace).resolve("analysis");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (String file : ExportDiff.files(dir)) {
                if (!Files.isRegularFile(oldDir.resolve(namespace).resolve(file))) {
                    this.allowed.add("neue Analyse: " + namespace + "/analysis/" + file);
                }
            }
        }
    }

    private void assets() throws IOException {
        Map<String, byte[]> merged = new TreeMap<>();
        for (String namespace : this.scope) {
            Path dir = this.current.resolve(namespace);
            if (!Files.isDirectory(dir.resolve("assets"))) {
                continue;
            }
            for (String file : ExportDiff.files(dir.resolve("assets"))) {
                byte[] data = Files.readAllBytes(dir.resolve("assets").resolve(file));
                byte[] before = merged.putIfAbsent("assets/" + file, data);
                if (before != null && !Arrays.equals(before, data)) {
                    this.difference(namespace + "/assets/" + file + " anders als in einem anderen Namensraum");
                }
            }
        }
        boolean complete = this.scope.containsAll(this.oldNamespaces());
        int missing = 0;
        Path oldAssets = this.old.resolve("assets");
        SortedSet<String> before = ExportDiff.files(oldAssets);
        for (String file : before) {
            byte[] now = merged.get("assets/" + file);
            if (now == null) {
                if (complete) {
                    this.difference("assets/" + file + " fehlt im neuen Export");
                } else {
                    missing++;
                }
                continue;
            }
            this.count("asset_files");
            byte[] then = Files.readAllBytes(oldAssets.resolve(file));
            if (!Arrays.equals(then, now)) {
                int added = file.endsWith(".json") ? added(JsonParser.parseString(new String(then, StandardCharsets.UTF_8)),
                        JsonParser.parseString(new String(now, StandardCharsets.UTF_8))) : -1;
                if (added > 0) {
                    this.allowed.add("Asset nur ergänzt: assets/" + file + " (" + added + " neue Einträge)");
                } else {
                    this.difference("assets/" + file + ": Bytes weichen ab");
                }
            }
        }
        if (missing > 0) {
            this.allowed.add("Assets anderer Namensräume (nicht im Export): " + missing);
        }
        for (String file : merged.keySet()) {
            if (!before.contains(file.substring("assets/".length()))) {
                this.allowed.add("neue Asset-Datei: " + file);
            }
        }
    }

    private void lang() throws IOException {
        boolean complete = this.scope.containsAll(this.oldNamespaces());
        for (String language : LangTable.LANGUAGES) {
            Map<String, JsonElement> merged = new TreeMap<>();
            for (String namespace : this.scope) {
                for (Map.Entry<String, JsonElement> entry : read(this.current.resolve(namespace).resolve("lang/" + language + ".json"))
                        .getAsJsonObject().entrySet()) {
                    JsonElement before = merged.putIfAbsent(entry.getKey(), entry.getValue());
                    if (before != null && !before.equals(entry.getValue())) {
                        this.difference(namespace + "/lang/" + language + ".json: " + entry.getKey() + " anders als in einem anderen Namensraum");
                    }
                }
            }
            JsonObject before = read(this.old.resolve("lang/" + language + ".json")).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : before.entrySet()) {
                JsonElement now = merged.get(entry.getKey());
                if (now == null) {
                    if (entry.getKey().startsWith(DROPPED_LANG)) {
                        this.allowed.add("Text des alten Sharecodes von Just More Parts: " + language + " " + entry.getKey());
                    } else if (complete) {
                        this.difference("lang/" + language + ".json: " + entry.getKey() + " fehlt im neuen Export");
                    }
                    continue;
                }
                this.count("lang_keys");
                if (this.heat && entry.getKey().endsWith(".warning." + HEAT_WARNING) && !entry.getValue().equals(now)) {
                    this.allowed.add(HEAT + ": lang/" + language + ".json " + entry.getKey());
                    continue;
                }
                this.deep("lang/" + language + ".json#" + entry.getKey(), entry.getValue(), now);
            }
            for (String key : merged.keySet()) {
                if (!before.has(key)) {
                    this.allowed.add("neuer Text: " + language + " " + key);
                }
            }
        }
    }

    static int added(JsonElement before, JsonElement after) {
        if (before.isJsonObject() && after.isJsonObject()) {
            int count = 0;
            for (Map.Entry<String, JsonElement> entry : before.getAsJsonObject().entrySet()) {
                JsonElement now = after.getAsJsonObject().get(entry.getKey());
                if (now == null) {
                    return -1;
                }
                int inner = added(entry.getValue(), now);
                if (inner < 0) {
                    return -1;
                }
                count += inner;
            }
            return count + after.getAsJsonObject().size() - before.getAsJsonObject().size();
        }
        return before.equals(after) ? 0 : -1;
    }

    private JsonElement heatAnalysis(String where, JsonElement before, JsonElement after) {
        if (!this.heat || before == null || after == null || !before.isJsonObject() || !after.isJsonObject()) {
            return after;
        }
        JsonObject old = before.getAsJsonObject();
        JsonObject out = after.getAsJsonObject().deepCopy();
        for (String key : HEAT_VALUES) {
            if (old.has(key) && out.has(key) && !old.get(key).equals(out.get(key))) {
                this.allowed.add(HEAT + ": " + where + "." + key);
                out.add(key, old.get(key));
            }
        }
        if (old.has("warnings") && out.has("warnings") && !old.get("warnings").equals(out.get("warnings"))
                && withoutHeat(old.getAsJsonArray("warnings")).equals(withoutHeat(out.getAsJsonArray("warnings")))) {
            this.allowed.add(HEAT + ": " + where + ".warnings " + HEAT_WARNING);
            out.add("warnings", old.get("warnings"));
        }
        return out;
    }

    private static JsonArray withoutHeat(JsonArray warnings) {
        JsonArray out = new JsonArray();
        warnings.forEach(e -> {
            if (!HEAT_WARNING.equals(e.getAsString())) {
                out.add(e);
            }
        });
        return out;
    }

    private JsonObject heatRules(JsonObject before, JsonObject after) {
        if (!this.heat || !before.has("analysis_warnings") || !after.has("analysis_warnings")) {
            return after;
        }
        JsonObject out = after.deepCopy();
        JsonArray oldWarnings = before.getAsJsonArray("analysis_warnings");
        JsonArray newWarnings = out.getAsJsonArray("analysis_warnings");
        for (int i = 0; i < Math.min(oldWarnings.size(), newWarnings.size()); i++) {
            JsonObject a = oldWarnings.get(i).getAsJsonObject();
            JsonObject b = newWarnings.get(i).getAsJsonObject();
            if (HEAT_WARNING.equals(stringOf(a.get("key"))) && HEAT_WARNING.equals(stringOf(b.get("key"))) && !a.get("text").equals(b.get("text"))) {
                this.allowed.add(HEAT + ": rules.json analysis_warnings " + HEAT_WARNING + ".text");
                b.add("text", a.get("text"));
            }
        }
        return out;
    }

    private static String stringOf(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private void converted(String where, JsonElement before, JsonElement after) {
        String oldCode = stringOf(before);
        String newCode = stringOf(after);
        if (newCode == null) {
            this.difference(where + " fehlt im neuen Export (alt " + oldCode + ")");
            return;
        }
        if (oldCode == null) {
            this.difference(where + ": alt " + before + ", neu " + newCode);
            return;
        }
        ShareConvert.Conversion conversion = ShareConvert.toPw3(oldCode);
        if (!conversion.ok() || conversion.code().isEmpty()) {
            this.difference(where + ": alter Code " + oldCode + " nicht umrechenbar (" + conversion.status() + ")");
        } else if (!conversion.code().get().equals(newCode)) {
            this.difference(where + ": " + oldCode + " ergibt umgerechnet " + conversion.code().get() + ", neu " + newCode);
        }
    }

    private void sharecodeManifest(JsonObject oldManifest, JsonObject newManifest) {
        if (!oldManifest.has("sharecode")) {
            return;
        }
        JsonObject before = oldManifest.getAsJsonObject("sharecode");
        JsonObject after = newManifest.has("sharecode") && newManifest.get("sharecode").isJsonObject() ? newManifest.getAsJsonObject("sharecode") : null;
        if (after == null) {
            this.difference("manifest.json sharecode fehlt im neuen Export");
            return;
        }
        this.count("manifest_sharecode");
        this.deep("manifest.json#sharecode.revision", before.get("revision"), after.get("pw1_revision"));
        this.deep("manifest.json#sharecode.pw2_revision", before.get("pw2_revision"), after.get("pw2_revision"));
        if (!"PW3".equals(stringOf(after.get("format")))) {
            this.difference("manifest.json#sharecode.format: " + after.get("format") + " statt PW3");
        }
        Set<String> reads = new TreeSet<>();
        if (after.has("reads")) {
            after.getAsJsonArray("reads").forEach(e -> reads.add(e.getAsString()));
        }
        for (String format : List.of(stringOf(before.get("default_format")), "PW1", "PW2")) {
            if (format != null && !reads.contains(format)) {
                this.difference("manifest.json#sharecode.reads: " + format + " fehlt");
            }
        }
    }

    private void spec(JsonObject before) {
        JsonObject after = this.newSpec;
        JsonObject pw1 = after.has("pw1") && after.get("pw1").isJsonObject() ? after.getAsJsonObject("pw1") : new JsonObject();
        this.count("spec");
        for (String key : before.keySet()) {
            if (PW1_SPECIAL.contains(key)) {
                continue;
            }
            if (!pw1.has(key)) {
                this.difference("sharecode/spec.json#" + key + " fehlt im neuen Export unter pw1");
                continue;
            }
            this.deep("sharecode/spec.json#pw1." + key, before.get(key), pw1.get(key));
        }
        if (before.has("namespaces")) {
            JsonArray oldNamespaces = before.getAsJsonArray("namespaces");
            JsonArray newNamespaces = pw1.has("namespaces") ? pw1.getAsJsonArray("namespaces") : new JsonArray();
            if (oldNamespaces.size() != newNamespaces.size()) {
                this.difference("sharecode/spec.json#pw1.namespaces: Länge " + oldNamespaces.size() + " statt " + newNamespaces.size());
            } else {
                for (int i = 0; i < oldNamespaces.size(); i++) {
                    JsonObject entry = oldNamespaces.get(i).getAsJsonObject();
                    if (entry.get("ns").getAsInt() != i) {
                        this.difference("sharecode/spec.json#namespaces[" + i + "].ns ist " + entry.get("ns"));
                    }
                    this.deep("sharecode/spec.json#pw1.namespaces[" + i + "]", entry.get("namespace"), newNamespaces.get(i));
                }
            }
        }
        if (before.has("tune_keys")) {
            JsonArray keys = new JsonArray();
            JsonArray oldKeys = before.getAsJsonArray("tune_keys");
            for (int i = 0; i < oldKeys.size(); i++) {
                JsonObject entry = oldKeys.get(i).getAsJsonObject();
                if (entry.get("index").getAsInt() != i + 1) {
                    this.difference("sharecode/spec.json#tune_keys[" + i + "].index ist " + entry.get("index"));
                }
                keys.add(entry.get("key"));
            }
            this.deep("sharecode/spec.json#pw1.tune_keys", keys, pw1.get("tune_keys"));
        }
        if (before.has("layers")) {
            JsonArray oldLayers = before.getAsJsonArray("layers");
            JsonArray newLayers = after.has("layers") ? after.getAsJsonArray("layers") : new JsonArray();
            if (oldLayers.size() != newLayers.size()) {
                this.difference("sharecode/spec.json#layers: Länge " + oldLayers.size() + " statt " + newLayers.size());
            } else {
                for (int i = 0; i < oldLayers.size(); i++) {
                    JsonObject a = oldLayers.get(i).getAsJsonObject();
                    JsonObject b = newLayers.get(i).getAsJsonObject();
                    for (String key : List.of("bit", "key", "default")) {
                        this.deep("sharecode/spec.json#layers[" + i + "]." + key, a.get(key), b.get(key));
                    }
                    if (a.has("name")) {
                        this.allowed.add("spec.json Ebenen-Namen als Sprachschlüssel: " + a.get("key").getAsString());
                    }
                }
            }
        }
        if (before.has("statuses")) {
            Set<String> keys = new TreeSet<>();
            if (after.has("statuses")) {
                after.getAsJsonArray("statuses").forEach(e -> keys.add(e.getAsJsonObject().get("key").getAsString()));
            }
            for (JsonElement element : before.getAsJsonArray("statuses")) {
                String key = element.getAsJsonObject().get("key").getAsString();
                if (!keys.contains(key)) {
                    this.difference("sharecode/spec.json#statuses: " + key + " fehlt im neuen Export");
                } else {
                    this.allowed.add("spec.json Status-Texte als Sprachschlüssel: " + key);
                }
            }
        }
        if (before.has("aliases") && !before.getAsJsonArray("aliases").isEmpty()) {
            this.difference("sharecode/spec.json#aliases nicht leer, im neuen Export gibt es keine Alias-Tabelle: " + shorten(before.get("aliases")));
        }
        if (before.has("pw2")) {
            JsonObject oldPw2 = before.getAsJsonObject("pw2");
            JsonObject newPw2 = after.has("pw2") && after.get("pw2").isJsonObject() ? after.getAsJsonObject("pw2") : new JsonObject();
            for (String key : oldPw2.keySet()) {
                if (key.equals("unknown_part")) {
                    this.allowed.add("spec.json pw2.unknown_part: alt " + oldPw2.get(key) + ", neu " + newPw2.get(key) + " (Darstellung unbekannter PW2-Nummern)");
                    continue;
                }
                if (!newPw2.has(key)) {
                    this.difference("sharecode/spec.json#pw2." + key + " fehlt im neuen Export");
                    continue;
                }
                this.deep("sharecode/spec.json#pw2." + key, oldPw2.get(key), newPw2.get(key));
            }
            if (oldPw2.has("params") && newPw2.has("part_param")) {
                this.deep("sharecode/spec.json#pw2.part_param", oldPw2.getAsJsonObject("params").get("part"), newPw2.get("part_param"));
            }
        }
    }

    private static String presetOf(String newName) {
        return newName.substring(newName.indexOf(':') + 1);
    }

    private static String mappedName(String oldName) {
        for (Map.Entry<String, String> entry : VECTOR_NAMES.entrySet()) {
            if (oldName.startsWith(entry.getKey())) {
                return entry.getValue() + oldName.substring(entry.getKey().length());
            }
        }
        return null;
    }

    private void vectors(JsonArray before, JsonArray after) {
        Map<String, JsonObject> now = new LinkedHashMap<>();
        for (JsonElement element : after) {
            JsonObject vector = element.getAsJsonObject();
            String name = vector.get("name").getAsString();
            now.put(name, vector);
            if (!this.inScope(presetOf(name))) {
                continue;
            }
            this.count("vectors_new");
            if (!"PW3".equals(stringOf(vector.get("format"))) || !"OK".equals(stringOf(vector.get("status")))) {
                this.difference("sharecode/vectors.json#" + name + ": " + vector.get("format") + " " + vector.get("status") + " statt PW3 OK");
                continue;
            }
            for (JsonElement report : vector.getAsJsonArray("namespaces")) {
                if (!"MATCH".equals(stringOf(report.getAsJsonObject().get("result")))) {
                    this.difference("sharecode/vectors.json#" + name + ".namespaces: " + report);
                }
            }
        }
        Set<String> matched = new TreeSet<>();
        Set<String> oldPresets = new TreeSet<>();
        for (JsonElement element : before) {
            JsonObject vector = element.getAsJsonObject();
            String name = vector.get("name").getAsString();
            String mapped = mappedName(name);
            if (mapped == null) {
                this.legacyVector(name, vector);
                continue;
            }
            String preset = presetOf(mapped);
            oldPresets.add(preset);
            if (!this.inScope(preset)) {
                continue;
            }
            JsonObject vectorNow = now.get(mapped);
            String where = "sharecode/vectors.json#" + name;
            if (vectorNow == null) {
                this.difference(where + ": Gegenstück " + mapped + " fehlt im neuen Export");
                continue;
            }
            matched.add(mapped);
            this.count("vectors");
            this.converted(where + ".code", vector.get("code"), vectorNow.get("code"));
            for (String key : List.of("status", "layers", "decoded", "skipped", "unknown_parts", "check")) {
                this.deep(where + "." + key, vector.get(key), vectorNow.get(key));
            }
            this.deep(where + ".analysis", vector.get("analysis"), this.heatAnalysis(where + ".analysis", vector.get("analysis"), vectorNow.get("analysis")));
            for (String key : List.of("wrong_kind", "renamed")) {
                if (vector.has(key) && !vector.getAsJsonArray(key).isEmpty()) {
                    this.difference(where + "." + key + " nicht leer: " + shorten(vector.get(key)));
                }
            }
            if (vector.has("compression")) {
                this.allowed.add("vectors.json compression (nur PW1/PW2): " + name);
            }
        }
        for (String name : now.keySet()) {
            String preset = presetOf(name);
            if (!this.inScope(preset) || matched.contains(name)) {
                continue;
            }
            if (oldPresets.contains(preset)) {
                this.difference("sharecode/vectors.json#" + name + " ohne Gegenstück im Altexport");
            } else {
                this.allowed.add("neuer Vektor: " + name);
            }
        }
        for (Map.Entry<String, JsonObject> entry : this.newPresets.entrySet()) {
            JsonObject vector = now.get("preset:" + entry.getKey());
            if (vector != null) {
                this.deep("presets.json#" + entry.getKey() + ".sharecode (gleich vectors.json preset:)", vector.get("code"), entry.getValue().get("sharecode"));
            }
        }
    }

    private static JsonElement legacyIds(JsonElement element) {
        if (element == null) {
            return JsonNull.INSTANCE;
        }
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            element.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(), legacyIds(e.getValue())));
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            element.getAsJsonArray().forEach(e -> out.add(legacyIds(e)));
            return out;
        }
        String text = stringOf(element);
        if (text != null && element.getAsJsonPrimitive().isString() && text.startsWith("pw2.unknown:")) {
            return new JsonPrimitive("?" + text.substring("pw2.unknown:".length()));
        }
        return element;
    }

    private ShareTables legacyTables() {
        if (this.legacyTables == null) {
            Map<String, PartKind> kinds = new TreeMap<>();
            for (Map.Entry<String, JsonObject> entry : this.oldParts.entrySet()) {
                String category = entry.getValue().get("category").getAsString();
                for (PartKind kind : PartKind.values()) {
                    if (kind.key().equals(category)) {
                        kinds.put(entry.getKey(), kind);
                    }
                }
            }
            ShareTables.Builder builder = ShareTables.builder().installed(ShareTables.propwashTable()).directory(AddonDirectory.builtin())
                    .parts(id -> kinds.containsKey(id.toString()) ? Optional.of(kinds.get(id.toString())) : ShareTables.REGISTERED.kind(id)).frozen(true);
            for (AddonDirectory.Addon addon : AddonDirectory.builtin().addons()) {
                if (!addon.namespace().equals("propwash")) {
                    addon.table().ifPresent(builder::installed);
                }
            }
            this.legacyTables = builder.build();
        }
        return this.legacyTables;
    }

    private void legacyVector(String name, JsonObject vector) {
        String where = "sharecode/vectors.json#" + name;
        String code = vector.get("code").getAsString();
        ShareDecoded decoded = ShareFormat.decode(code, this.legacyTables());
        this.count("legacy_vectors");
        this.deep(where + ".status", vector.get("status"), new JsonPrimitive(decoded.status().name()));
        if (!decoded.ok()) {
            return;
        }
        JsonObject content = ShareSpecJson.content(decoded.require());
        JsonArray layers = new JsonArray();
        decoded.layers().forEach(layer -> layers.add(layer.key()));
        this.deep(where + ".layers", vector.get("layers"), layers);
        this.deep(where + ".decoded", vector.get("decoded"), legacyIds(content));
        JsonArray unknown = new JsonArray();
        decoded.unknownParts().forEach(id -> unknown.add(id.toString()));
        this.deep(where + ".unknown_parts", vector.get("unknown_parts"), legacyIds(unknown));
        JsonArray skipped = new JsonArray();
        decoded.skipped().forEach(skipped::add);
        this.deep(where + ".skipped", vector.get("skipped"), skipped);
        ShareConvert.Conversion conversion = ShareConvert.toPw3(code);
        boolean unknownNumbers = false;
        for (JsonElement id : vector.getAsJsonArray("unknown_parts")) {
            unknownNumbers |= id.getAsString().startsWith("?");
        }
        if (unknownNumbers) {
            if (conversion.ok()) {
                this.difference(where + ": unbekannte PW2-Nummern, trotzdem umgerechnet zu " + conversion.code().orElse(""));
            }
            return;
        }
        if (!conversion.ok() || conversion.code().isEmpty()) {
            this.difference(where + ": nicht nach PW3 umrechenbar (" + conversion.status() + ")");
            return;
        }
        ShareDecoded again = ShareFormat.decode(conversion.code().get(), this.legacyTables());
        if (!again.ok()) {
            this.difference(where + ": umgerechneter Code " + conversion.code().get() + " nicht lesbar (" + again.status() + ")");
            return;
        }
        this.count("legacy_conversions");
        this.deep(where + " nach PW3", content, ShareSpecJson.content(again.require()));
    }

    private JsonObject oldPw2Parts() {
        if (this.newSpec == null) {
            return null;
        }
        Path spec = this.old.resolve("sharecode/spec.json");
        try {
            JsonObject before = Files.isRegularFile(spec) ? read(spec).getAsJsonObject() : null;
            return before != null && before.has("pw2") && before.getAsJsonObject("pw2").has("parts") ? before.getAsJsonObject("pw2").getAsJsonObject("parts")
                    : null;
        } catch (IOException e) {
            this.difference("sharecode/spec.json nicht lesbar: " + e.getMessage());
            return null;
        }
    }

    private JsonObject tableOf(String namespace, String source) throws IOException {
        if ("mod".equals(source)) {
            Path file = this.current.resolve(namespace).resolve(WebExport.SHARE_TABLE);
            return Files.isRegularFile(file) ? read(file).getAsJsonObject() : null;
        }
        if ("directory".equals(source) && this.newSpec != null && this.newSpec.get("directory").isJsonObject()) {
            for (JsonElement addon : this.newSpec.getAsJsonObject("directory").getAsJsonArray("addons")) {
                if (namespace.equals(stringOf(addon.getAsJsonObject().get("namespace"))) && addon.getAsJsonObject().has("table")) {
                    return addon.getAsJsonObject().getAsJsonObject("table");
                }
            }
        }
        return null;
    }

    static String tableCrc(JsonObject table) {
        StringBuilder text = new StringBuilder();
        JsonObject parts = table.getAsJsonObject("parts");
        for (String kind : PART_KINDS) {
            if (parts == null || !parts.has(kind)) {
                continue;
            }
            JsonArray entries = parts.getAsJsonArray(kind);
            for (int i = 0; i < entries.size(); i++) {
                text.append(kind).append(':').append(i + 1).append(':').append(entries.get(i).getAsJsonObject().get("id").getAsString()).append('\n');
            }
        }
        CRC32 crc = new CRC32();
        crc.update(text.toString().getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "%08x", crc.getValue());
    }

    private void tables() throws IOException {
        JsonObject pw2 = this.oldPw2Parts();
        if (pw2 == null) {
            return;
        }
        for (String namespace : this.scope) {
            Path file = this.current.resolve(namespace).resolve("manifest.json");
            if (!Files.isRegularFile(file)) {
                this.difference(namespace + "/manifest.json fehlt im neuen Export");
                continue;
            }
            JsonObject manifest = read(file).getAsJsonObject();
            String where = namespace + "/manifest.json";
            String source = stringOf(manifest.get("table_source"));
            JsonObject table = this.tableOf(namespace, source);
            boolean legacy = false;
            for (String kind : PART_KINDS) {
                if (pw2.has(kind)) {
                    for (JsonElement id : pw2.getAsJsonArray(kind)) {
                        legacy |= namespace(id.getAsString()).equals(namespace);
                    }
                }
            }
            if (table == null) {
                if (legacy || source != null) {
                    this.difference(where + ": keine Teile-Tabelle (table_source " + source + "), obwohl PW2 Teile dieses Namensraums kennt");
                }
                continue;
            }
            this.count("tables");
            String crc = tableCrc(table);
            this.deep(where + "#table_crc", new JsonPrimitive(crc), manifest.get("table_crc"));
            int entries = 0;
            for (String kind : PART_KINDS) {
                entries += table.getAsJsonObject("parts").has(kind) ? table.getAsJsonObject("parts").getAsJsonArray(kind).size() : 0;
            }
            this.deep(where + "#table_entries", new JsonPrimitive(entries), manifest.get("table_entries"));
            for (String kind : PART_KINDS) {
                List<String> before = new ArrayList<>();
                if (pw2.has(kind)) {
                    for (JsonElement id : pw2.getAsJsonArray(kind)) {
                        if (namespace(id.getAsString()).equals(namespace)) {
                            before.add(id.getAsString().substring(namespace.length() + 1));
                        }
                    }
                }
                List<String> after = new ArrayList<>();
                if (table.getAsJsonObject("parts").has(kind)) {
                    table.getAsJsonObject("parts").getAsJsonArray(kind).forEach(e -> after.add(e.getAsJsonObject().get("id").getAsString()));
                }
                if (after.size() < before.size() || !after.subList(0, before.size()).equals(before)) {
                    this.difference(where + ": Tabelle " + kind + " beginnt nicht mit der PW2-Reihenfolge (alt " + before + ", neu " + after + ")");
                }
            }
        }
    }
}

package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.CameraView;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareTable;
import de.lardtom.propwash.share.ShareTables;
import de.lardtom.propwash.share.legacy.Pw1Tables;
import de.lardtom.propwash.share.legacy.Pw2Tables;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class WebExport {
    public static final int FORMAT = 2;
    public static final String ALL = "all";
    public static final String BASE = "base";
    public static final String FOLDER = "web-export";
    public static final String SHARE_TABLE = "share_table.json";
    public static final List<PartKind> ORDER = List.of(PartKind.FRAME, PartKind.STACK, PartKind.MOTOR, PartKind.PROP, PartKind.VIDEO,
            PartKind.BATTERY, PartKind.ACCESSORY);
    public static final Set<Identifier> CREATIVE_ONLY = Set.of(Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, "creative_fc"));
    private static final Pattern TOML_VALUE = Pattern.compile("^\\s*(\\w+)\\s*=\\s*\"([^\"]*)\"");

    public record Result(boolean pass, String summary, List<String> namespaces, int parts, long errors, long warnings) {
    }

    private final Path target;
    private final ModResources resources;
    private final ShareTables tables;
    private final Problems problems = new Problems();
    private final LangTable lang = new LangTable(this.problems);
    private final AssetIndex assets;
    private final List<String> written = new ArrayList<>();
    private List<PartDefinition> parts = List.of();
    private List<DronePreset> presets = List.of();
    private int assetFiles;
    private int vectorCount;

    private WebExport(Path target, ModResources resources) {
        this.target = target;
        this.resources = resources;
        this.tables = ShareTables.current();
        this.assets = new AssetIndex(this.problems, resources);
    }

    public static Path target(Path gameDir) {
        return gameDir.resolve(PropwashApi.MOD_ID).resolve(FOLDER);
    }

    public static Result run(Path target, String selection, ModResources resources) {
        SortedSet<String> available = namespaces();
        List<String> chosen;
        if (ALL.equals(selection)) {
            chosen = List.copyOf(available);
        } else if (available.contains(selection)) {
            chosen = List.of(selection);
        } else {
            throw new IllegalArgumentException("unbekannter Namensraum " + selection + ", vorhanden: " + String.join(", ", available));
        }
        return new WebExport(target, resources).export(selection, available, chosen);
    }

    public static SortedSet<String> namespaces() {
        SortedSet<String> out = new TreeSet<>(List.of(PropwashApi.MOD_ID));
        sortedParts().forEach(part -> out.add(part.id().getNamespace()));
        PropwashApi.builds().presets().forEach(preset -> out.add(preset.id().getNamespace()));
        PropwashApi.parts().chemistries().forEach(chemistry -> out.add(chemistry.id().getNamespace()));
        return out;
    }

    static boolean exported(PartDefinition part) {
        return !CREATIVE_ONLY.contains(part.id());
    }

    static List<PartDefinition> sortedParts() {
        List<PartDefinition> out = new ArrayList<>(PropwashApi.parts().all());
        out.removeIf(part -> !exported(part));
        out.sort(Comparator.comparingInt((PartDefinition p) -> ORDER.indexOf(p.kind())).thenComparingInt(p -> p.basics().sortOrder())
                .thenComparing(p -> p.id().toString()));
        return out;
    }

    private Result export(String selection, SortedSet<String> available, List<String> chosen) {
        this.clean();
        this.parts = sortedParts();
        this.presets = PropwashApi.builds().presets();
        for (String namespace : available) {
            this.assets.load(namespace);
        }
        for (String path : this.assets.languageFiles()) {
            this.lang.load(path, this.assets.files().get(path));
        }
        this.assets.readPaint();
        int mismatches = RuleCheck.verify(this.problems);
        boolean base = chosen.contains(PropwashApi.MOD_ID);
        if (base) {
            this.base(mismatches);
        }
        int partCount = 0;
        int presetCount = 0;
        for (String namespace : chosen) {
            this.lang.scope(namespace);
            List<PartDefinition> own = this.parts.stream().filter(part -> part.id().getNamespace().equals(namespace)).toList();
            List<DronePreset> ownPresets = this.presets.stream().filter(preset -> preset.id().getNamespace().equals(namespace)).toList();
            this.namespace(namespace, own, ownPresets);
            partCount += own.size();
            presetCount += ownPresets.size();
        }
        this.lang.scope("");
        this.write("problems.json", this.problems.toJson());
        this.write("manifest.json", this.manifestJson(selection, available, chosen, base, partCount, presetCount));
        if (this.problems.errors() > 0) {
            List<String> first = new ArrayList<>();
            for (Problems.Entry entry : this.problems.firstErrors(3)) {
                first.add(entry.where() + ": " + entry.message());
            }
            return new Result(false, "FAIL " + this.problems.errors() + " Fehler im Web-Export: " + String.join(" | ", first), chosen, partCount,
                    this.problems.errors(), this.problems.warnings());
        }
        return new Result(true, "PASS " + partCount + " Teile, Namensräume " + String.join(", ", chosen) + ", " + this.problems.warnings()
                + " Warnungen", chosen, partCount, 0, this.problems.warnings());
    }

    private void clean() {
        if (Files.exists(this.target)) {
            if (!Files.isDirectory(this.target)) {
                throw new UncheckedIOException(new IOException("Web-Export: " + this.target + " ist kein Ordner"));
            }
            try (Stream<Path> walk = Files.walk(this.target)) {
                for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                    if (!file.equals(this.target)) {
                        Files.delete(file);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Web-Export: " + this.target + " nicht geleert", e);
            }
        }
    }

    private void write(String name, JsonElement element) {
        ExportJson.write(this.target.resolve(name), element);
        this.written.add(name);
    }

    private void base(int mismatches) {
        this.lang.scope(PropwashApi.MOD_ID);
        this.write(BASE + "/rules.json", RuleCheck.toJson(this.lang, mismatches));
        this.write(BASE + "/paint.json", this.paintJson());
        this.write(BASE + "/tune.json", this.tuneJson());
        this.write(BASE + "/analysis_labels.json", this.analysisLabelsJson());
        for (ImportStatus status : ImportStatus.values()) {
            this.lang.both(status.translationKey(), PropwashApi.MOD_ID + "/lang", true);
        }
        for (ImportLayer layer : ImportLayer.values()) {
            this.lang.both(layer.translationKey(), PropwashApi.MOD_ID + "/lang", true);
        }
        this.write(BASE + "/" + ShareExport.SPEC, ShareExport.spec());
        JsonArray vectors = ShareExport.vectors(this.presets, this.tables, this.problems);
        this.vectorCount = vectors.size();
        this.write(BASE + "/" + ShareExport.VECTORS, vectors);
        for (String key : ShareExport.translationKeys()) {
            this.lang.both(key, BASE + "/" + ShareExport.SPEC, true);
        }
    }

    private void namespace(String namespace, List<PartDefinition> own, List<DronePreset> ownPresets) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> files = new ArrayList<>();
        this.written(namespace, files, "parts.json", this.partsJson(own, counts));
        this.written(namespace, files, "mounts.json", this.mountsJson(namespace, own));
        this.written(namespace, files, "chemistries.json", this.chemistriesJson(namespace, own, counts));
        this.written(namespace, files, "presets.json", this.presetsJson(namespace, ownPresets, counts));
        for (DronePreset preset : ownPresets) {
            this.written(namespace, files, "analysis/" + preset.id().getPath() + ".json", this.analysis(preset));
        }
        Optional<Integer> shortId = this.shareTable(namespace, files);
        this.assets.useLayoutFiles(namespace);
        int copied = 0;
        for (String path : this.assets.used(namespace)) {
            byte[] data = this.assets.files().get(path);
            if (data != null) {
                ExportJson.write(this.target.resolve(namespace).resolve(path), data);
                copied++;
            }
        }
        this.assetFiles += copied;
        counts.put("asset_files", copied);
        for (String language : LangTable.LANGUAGES) {
            this.written(namespace, files, "lang/" + language + ".json", this.lang.usedJson(namespace, language));
        }
        files.add("manifest.json");
        files.sort(String::compareTo);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", FORMAT);
        manifest.addProperty("namespace", namespace);
        Optional<ModMeta> meta = this.resources.meta(namespace);
        if (meta.isPresent()) {
            JsonObject mod = new JsonObject();
            mod.addProperty("id", meta.get().id());
            mod.addProperty("name", meta.get().name());
            mod.addProperty("version", meta.get().version());
            manifest.add("mod", mod);
        } else {
            manifest.add("mod", JsonNull.INSTANCE);
            this.problems.warning(namespace + "/manifest.json", "keine Mod mit der Id " + namespace + " geladen");
        }
        manifest.add("short_id", shortId.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
        Optional<ShareExport.Table> table = ShareExport.table(namespace, this.tables);
        manifest.add("table_source", ExportJson.str(table.map(ShareExport.Table::source)));
        manifest.addProperty("table_entries", table.map(ShareExport.Table::entries).orElse(0));
        manifest.add("table_crc", ExportJson.str(table.map(ShareExport.Table::crc)));
        this.tableCoverage(namespace, own, table);
        JsonObject requires = new JsonObject();
        requires(this.resources, namespace, this.problems).forEach(requires::addProperty);
        manifest.add("requires", requires);
        manifest.add("languages", ExportJson.strings(LangTable.LANGUAGES));
        JsonObject countJson = new JsonObject();
        counts.forEach(countJson::addProperty);
        manifest.add("counts", countJson);
        manifest.add("files", ExportJson.strings(files));
        this.write(namespace + "/manifest.json", manifest);
    }

    private void tableCoverage(String namespace, List<PartDefinition> own, Optional<ShareExport.Table> table) {
        if (own.isEmpty()) {
            return;
        }
        if (table.isEmpty()) {
            this.problems.warning(namespace + "/manifest.json", "keine Teile-Tabelle (data/" + namespace
                    + "/propwash_share/table.json): Teile gehen im Sharecode als Text");
            return;
        }
        ShareTable shareTable = table.get().table();
        for (PartDefinition part : own) {
            if (shareTable.find(part.id()).isEmpty()) {
                this.problems.warning(part.id().toString(), "kein Eintrag in der Teile-Tabelle: geht im Sharecode als Pfad");
            }
        }
    }

    private void written(String namespace, List<String> files, String name, JsonElement element) {
        this.write(namespace + "/" + name, element);
        files.add(name);
    }

    private Optional<Integer> shareTable(String namespace, List<String> files) {
        if (!this.resources.loaded(namespace)) {
            return Optional.empty();
        }
        String path = "data/" + namespace + "/propwash_share/table.json";
        byte[] data;
        try {
            data = this.resources.read(namespace, "data/" + namespace + "/propwash_share", "table.json").get(path);
        } catch (RuntimeException e) {
            this.problems.warning(path, "nicht lesbar: " + e.getMessage());
            return Optional.empty();
        }
        if (data == null) {
            return Optional.empty();
        }
        ExportJson.write(this.target.resolve(namespace).resolve(SHARE_TABLE), data);
        this.written.add(namespace + "/" + SHARE_TABLE);
        files.add(SHARE_TABLE);
        try {
            JsonObject table = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            if (table.has("short_id") && table.get("short_id").isJsonPrimitive()) {
                return Optional.of(table.get("short_id").getAsInt());
            }
        } catch (RuntimeException e) {
            this.problems.warning(path, "Tabelle nicht lesbar: " + e.getMessage());
        }
        return Optional.empty();
    }

    static Map<String, String> requires(ModResources resources, String namespace, Problems problems) {
        Map<String, String> out = new TreeMap<>();
        if (PropwashApi.MOD_ID.equals(namespace) || !resources.loaded(namespace)) {
            return out;
        }
        String loader = resources.loader();
        try {
            byte[] fabric = loader.equals("neoforge") ? null : resources.read(namespace, "", "fabric.mod.json").get("fabric.mod.json");
            if (fabric != null) {
                JsonObject depends = JsonParser.parseString(new String(fabric, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("depends");
                JsonElement propwash = depends == null ? null : depends.get(PropwashApi.MOD_ID);
                if (propwash != null && propwash.isJsonArray()) {
                    List<String> ranges = new ArrayList<>();
                    propwash.getAsJsonArray().forEach(range -> ranges.add(range.getAsString()));
                    out.put(PropwashApi.MOD_ID, String.join(" || ", ranges));
                } else if (propwash != null && propwash.isJsonPrimitive()) {
                    out.put(PropwashApi.MOD_ID, propwash.getAsString());
                }
            }
        } catch (RuntimeException e) {
            problems.warning(namespace + "/manifest.json", "fabric.mod.json nicht lesbar: " + e.getMessage());
        }
        try {
            byte[] neoforge = loader.equals("fabric") ? null
                    : resources.read(namespace, "META-INF", "neoforge.mods.toml").get("META-INF/neoforge.mods.toml");
            if (neoforge != null && !out.containsKey(PropwashApi.MOD_ID)) {
                tomlRange(new String(neoforge, StandardCharsets.UTF_8), PropwashApi.MOD_ID).ifPresent(range -> out.put(PropwashApi.MOD_ID, range));
            }
        } catch (RuntimeException e) {
            problems.warning(namespace + "/manifest.json", "neoforge.mods.toml nicht lesbar: " + e.getMessage());
        }
        return out;
    }

    static Optional<String> tomlRange(String toml, String modId) {
        Optional<String> found = Optional.empty();
        boolean dependency = false;
        String mod = null;
        String range = null;
        for (String raw : (toml + "\n[end]").split("\n")) {
            String line = raw.strip();
            if (line.startsWith("[")) {
                if (dependency && modId.equals(mod) && range != null && found.isEmpty()) {
                    found = Optional.of(range);
                }
                dependency = line.startsWith("[[dependencies.");
                mod = null;
                range = null;
                continue;
            }
            Matcher value = TOML_VALUE.matcher(line);
            if (dependency && value.find()) {
                if (value.group(1).equals("modId")) {
                    mod = value.group(2);
                } else if (value.group(1).equals("versionRange")) {
                    range = value.group(2);
                }
            }
        }
        return found;
    }

    private String prefix(Identifier id) {
        return PropwashApi.parts().isBuiltin(id) ? PropwashApi.MOD_ID : id.getNamespace();
    }

    private JsonArray partsJson(List<PartDefinition> own, Map<String, Integer> counts) {
        JsonArray out = new JsonArray();
        for (PartKind kind : ORDER) {
            counts.put(kind.key(), 0);
        }
        for (PartDefinition part : own) {
            Identifier id = part.id();
            boolean builtin = PropwashApi.parts().isBuiltin(id);
            String where = id.toString();
            counts.merge(part.kind().key(), 1, Integer::sum);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            entry.addProperty("category", part.kind().key());
            entry.addProperty("builtin", builtin);
            entry.add("name", this.lang.both("part." + id.getNamespace() + "." + id.getPath(), where, true));
            if (part.kind() == PartKind.FRAME) {
                String key = builtin ? "propwash.content.frame_short." + id.getPath() : id.getNamespace() + ".frame_short." + id.getPath();
                entry.add("short_name", this.lang.has(key) ? this.lang.both(key, where, true) : JsonNull.INSTANCE);
                if (!builtin && !this.lang.has(key)) {
                    this.problems.warning(where, "Kurzname fehlt: " + key);
                }
            } else {
                entry.add("short_name", JsonNull.INSTANCE);
            }
            entry.add("help", this.help(this.prefix(id) + ".help." + id.getPath(), where));
            entry.add("basics", PartData.basics(part.basics()));
            entry.add("data", PartData.data(part));
            entry.add("derived", PartData.derived(part));
            AssetIndex.Model model = this.assets.model(part);
            JsonObject paint = new JsonObject();
            paint.add("slots", ExportJson.strings(PartData.paintSlots(part)));
            paint.addProperty("paintable_model", model.paintable());
            paint.add("default_colors", defaultColors(part, model));
            entry.add("paint", paint);
            JsonObject modelJson = new JsonObject();
            modelJson.addProperty("kind", model.kind());
            modelJson.add("item", ExportJson.str(model.item()));
            modelJson.add("drone", ExportJson.str(model.drone()));
            modelJson.add("variants", ExportJson.strings(model.variants()));
            modelJson.add("textures", ExportJson.strings(model.textures()));
            entry.add("model", modelJson);
            out.add(entry);
        }
        return out;
    }

    static JsonObject defaultColors(PartDefinition part, AssetIndex.Model model) {
        JsonObject colors = new JsonObject();
        if (!model.baseColors().isEmpty()) {
            for (String slot : PartData.paintSlots(part)) {
                Integer rgb = model.baseColors().get(slot);
                if (rgb != null) {
                    colors.addProperty(slot, ExportJson.hex(rgb));
                }
            }
        } else if (part.basics().visual().accentRgb().isPresent()) {
            for (String slot : PartData.paintSlots(part)) {
                colors.addProperty(slot, ExportJson.hex(part.basics().visual().accentRgb().getAsInt()));
            }
        }
        return colors;
    }

    private JsonObject help(String prefix, String where) {
        JsonObject out = new JsonObject();
        for (String language : LangTable.LANGUAGES) {
            JsonObject help = new JsonObject();
            Optional<String> summary = this.lang.get(language, prefix + ".summary");
            help.add("summary", ExportJson.str(summary));
            if (summary.isEmpty()) {
                this.problems.warning(where, "Hilfe fehlt (" + language + "): " + prefix + ".summary");
            }
            for (String block : List.of("what", "use")) {
                JsonArray lines = new JsonArray();
                for (int i = 1; i <= 8; i++) {
                    this.lang.get(language, prefix + "." + block + "." + i).ifPresent(lines::add);
                }
                help.add(block, lines);
            }
            out.add(language, help);
        }
        return out;
    }

    private JsonElement mountName(String kind, Identifier mount, String where) {
        String key = PropwashApi.MOD_ID.equals(mount.getNamespace()) ? "propwash.content.mount." + kind + "." + mount.getPath()
                : mount.getNamespace() + ".mount." + kind + "." + mount.getPath();
        if (!this.lang.has(key)) {
            this.problems.warning(where, "Halterung ohne Namen: " + key);
        }
        return this.lang.both(key, where, false);
    }

    private JsonObject mountsJson(String namespace, List<PartDefinition> own) {
        Map<Identifier, List<String>> motorFrames = new TreeMap<>();
        Map<Identifier, List<String>> motorParts = new TreeMap<>();
        Map<Identifier, List<String>> stackFrames = new TreeMap<>();
        Map<Identifier, List<String>> stackParts = new TreeMap<>();
        Map<Identifier, List<String>> propMotors = new TreeMap<>();
        Map<Identifier, List<String>> propParts = new TreeMap<>();
        for (PartDefinition part : own) {
            String id = part.id().toString();
            switch (part) {
                case FrameDefinition frame -> {
                    frame.motorMounts().forEach(m -> motorFrames.computeIfAbsent(m, k -> new ArrayList<>()).add(id));
                    frame.stackMounts().forEach(m -> stackFrames.computeIfAbsent(m, k -> new ArrayList<>()).add(id));
                }
                case MotorDefinition motor -> {
                    motorParts.computeIfAbsent(motor.mount(), k -> new ArrayList<>()).add(id);
                    propMotors.computeIfAbsent(motor.propMount(), k -> new ArrayList<>()).add(id);
                }
                case StackDefinition stack -> stackParts.computeIfAbsent(stack.mount(), k -> new ArrayList<>()).add(id);
                case PropDefinition prop -> propParts.computeIfAbsent(prop.mount(), k -> new ArrayList<>()).add(id);
                default -> {
                }
            }
        }
        String where = namespace + "/mounts.json";
        JsonObject out = new JsonObject();
        out.add("motor", this.mountList("motor", motorFrames, motorParts, "frames", where));
        out.add("stack", this.mountList("stack", stackFrames, stackParts, "frames", where));
        out.add("prop", this.mountList("prop", propMotors, propParts, "motors", where));
        return out;
    }

    private JsonArray mountList(String kind, Map<Identifier, List<String>> holders, Map<Identifier, List<String>> parts, String holderName,
                                String where) {
        TreeSet<Identifier> all = new TreeSet<>(holders.keySet());
        all.addAll(parts.keySet());
        JsonArray out = new JsonArray();
        for (Identifier mount : all) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", mount.toString());
            entry.add("name", this.mountName(kind, mount, where));
            entry.add(holderName, ExportJson.strings(holders.getOrDefault(mount, List.of())));
            entry.add("parts", ExportJson.strings(parts.getOrDefault(mount, List.of())));
            out.add(entry);
        }
        return out;
    }

    private JsonArray chemistriesJson(String namespace, List<PartDefinition> own, Map<String, Integer> counts) {
        Map<Identifier, List<String>> users = new TreeMap<>();
        for (PartDefinition part : own) {
            if (part instanceof BatteryDefinition battery) {
                users.computeIfAbsent(battery.chemistry(), k -> new ArrayList<>()).add(part.id().toString());
            }
        }
        List<CellChemistry> chemistries = new ArrayList<>();
        for (CellChemistry chemistry : PropwashApi.parts().chemistries()) {
            if (chemistry.id().getNamespace().equals(namespace) || users.containsKey(chemistry.id())) {
                chemistries.add(chemistry);
            }
        }
        chemistries.sort(Comparator.comparing(c -> c.id().toString()));
        counts.put("chemistry", chemistries.size());
        JsonArray out = new JsonArray();
        for (CellChemistry chemistry : chemistries) {
            Identifier id = chemistry.id();
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            String key = PropwashApi.MOD_ID.equals(id.getNamespace()) ? "propwash.content.tooltip.chemistry." + id.getPath()
                    : id.getNamespace() + ".chemistry." + id.getPath();
            entry.add("name", this.lang.both(key, namespace + "/chemistries.json#" + id, true));
            PartData.chemistry(chemistry).entrySet().forEach(e -> entry.add(e.getKey(), e.getValue()));
            entry.add("used_by", ExportJson.strings(users.getOrDefault(id, List.of())));
            out.add(entry);
        }
        return out;
    }

    private JsonObject paintJson() {
        JsonObject out = new JsonObject();
        JsonArray slots = new JsonArray();
        for (String slot : PaintSpec.SLOTS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", slot);
            entry.addProperty("bit", Pw3Params.paintBit(slot));
            entry.add("name", this.lang.both("propwash.paint.slot." + slot, BASE + "/paint.json", true));
            entry.add("hint", this.lang.both("propwash.paint.slot_hint." + slot, BASE + "/paint.json", false));
            JsonArray categories = new JsonArray();
            for (PartKind kind : ORDER) {
                if (slotsOf(kind).contains(slot)) {
                    categories.add(kind.key());
                }
            }
            entry.add("categories", categories);
            slots.add(entry);
        }
        out.add("slots", slots);
        return out;
    }

    private static List<String> slotsOf(PartKind kind) {
        return switch (kind) {
            case FRAME -> List.of("frame", "tpu");
            case MOTOR -> List.of("motors");
            case PROP -> List.of("prop_fl", "prop_fr", "prop_rl", "prop_rr");
            case STACK -> List.of("stack");
            case BATTERY -> List.of("battery");
            case VIDEO -> List.of("camera", "antenna");
            case ACCESSORY -> List.of("accessories");
            default -> List.of();
        };
    }

    static JsonElement tuneValue(float value) {
        return Float.isFinite(value) ? new JsonPrimitive(Double.parseDouble(Float.toString(value))) : JsonNull.INSTANCE;
    }

    static JsonObject tune(TuneSpec tune) {
        JsonObject out = new JsonObject();
        new TreeMap<>(tune.values()).forEach((key, value) -> out.add(key, tuneValue(value)));
        return out;
    }

    private JsonObject tuneJson() {
        JsonObject out = new JsonObject();
        JsonObject groups = new JsonObject();
        JsonArray flight = new JsonArray();
        JsonObject names = new JsonObject();
        for (TuneGroup group : TuneGroup.values()) {
            String key = group.name().toLowerCase(Locale.ROOT);
            if (TuneGroup.FLIGHT.contains(group)) {
                flight.add(key);
            }
            names.add(key, this.lang.both(group.translationKey(), BASE + "/tune.json", true));
        }
        groups.add("flight", flight);
        groups.add("shared", ExportJson.strings(ShareExport.sharedGroups()));
        groups.add("never", ExportJson.strings(ShareExport.neverGroups()));
        out.add("groups", groups);
        out.add("group_names", names);
        out.add("pw1_keys", ShareExport.pw1Keys());
        return out;
    }

    static JsonObject build(BuildSpec build) {
        JsonObject out = new JsonObject();
        out.addProperty("frame", build.frame().toString());
        out.addProperty("stack", build.stack().toString());
        out.addProperty("motor", build.motor().toString());
        out.addProperty("prop", build.prop().toString());
        out.addProperty("video", build.video().toString());
        out.addProperty("battery", build.battery().toString());
        JsonObject accessories = new JsonObject();
        for (AccessorySlot slot : AccessorySlot.values()) {
            build.accessory(slot).ifPresent(id -> accessories.addProperty(slot.key(), id.toString()));
        }
        out.add("accessories", accessories);
        return out;
    }

    static JsonObject paint(PaintSpec paint) {
        JsonObject out = new JsonObject();
        for (String slot : PaintSpec.SLOTS) {
            paint.color(slot).ifPresent(rgb -> out.addProperty(slot, ExportJson.hex(rgb)));
        }
        return out;
    }

    static JsonObject check(BuildCheck check) {
        JsonObject out = new JsonObject();
        out.add("problems", ExportJson.strings(check.problems()));
        out.add("warnings", ExportJson.strings(check.warnings()));
        return out;
    }

    static final String[][] ANALYSIS_LABELS = {
            {"mixed_flight_time_min", "propwash.content.workbench.stat.flight_time"},
            {"hover_flight_time_min", "propwash.content.workbench.details.hover_time"},
            {"cruise_flight_time_min", "propwash.content.workbench.details.cruise_time"},
            {"mixed_flight_time_detail", "propwash.content.workbench.details.mixed_time"},
            {"aggressive_flight_time_min", "propwash.content.workbench.details.aggressive_time"},
            {"hover_mixed_current_a", "propwash.content.workbench.details.currents"},
            {"landing_cell_voltage", "propwash.content.workbench.details.landing_voltage"},
            {"avionics_power_w", "propwash.content.workbench.details.avionics_power"},
            {"motor_temp_minute_c", "propwash.content.workbench.details.motor_temp"},
            {"mixed_motor_temp_c", "propwash.content.workbench.details.motor_temp_flight"},
            {"mass_grams", "propwash.content.workbench.stat.weight"},
            {"thrust_to_weight", "propwash.content.workbench.stat.thrust_to_weight"},
            {"hover_throttle_percent", "propwash.content.workbench.stat.hover_throttle"},
            {"top_speed_kmh", "propwash.content.workbench.stat.top_speed"},
            {"full_throttle_current_a", "propwash.content.workbench.stat.max_current"}};

    private JsonObject analysisLabelsJson() {
        JsonObject out = new JsonObject();
        for (String[] entry : ANALYSIS_LABELS) {
            JsonObject label = new JsonObject();
            label.addProperty("key", entry[1]);
            label.add("text", this.lang.both(entry[1], BASE + "/analysis_labels.json", true));
            out.add(entry[0], label);
        }
        return out;
    }

    static JsonObject stats(BuildStats stats) {
        JsonObject out = new JsonObject();
        out.add("mass_grams", ExportJson.num(stats.massGrams()));
        out.add("thrust_to_weight", ExportJson.num(stats.thrustToWeight()));
        out.add("hover_throttle_percent", ExportJson.num(stats.hoverThrottlePercent()));
        out.add("hover_flight_time_min", ExportJson.num(stats.hoverFlightTimeMin()));
        out.add("cruise_flight_time_min", ExportJson.num(stats.cruiseFlightTimeMin()));
        out.add("mixed_flight_time_min", ExportJson.num(stats.mixedFlightTimeMin()));
        out.add("aggressive_flight_time_min", ExportJson.num(stats.aggressiveFlightTimeMin()));
        out.add("cruise_speed_kmh", ExportJson.num(stats.cruiseSpeedKmh()));
        out.add("hover_current_a", ExportJson.num(stats.hoverCurrentA()));
        out.add("cruise_current_a", ExportJson.num(stats.cruiseCurrentA()));
        out.add("mixed_current_a", ExportJson.num(stats.mixedCurrentA()));
        out.add("landing_cell_voltage", ExportJson.num(stats.landingCellVoltage()));
        out.add("avionics_power_w", ExportJson.num(stats.avionicsPowerW()));
        out.add("top_speed_kmh", ExportJson.num(stats.topSpeedKmh()));
        out.add("full_throttle_current_a", ExportJson.num(stats.fullThrottleCurrentA()));
        out.add("esc_load_percent", ExportJson.num(stats.escLoadPercent()));
        out.add("battery_load_percent", ExportJson.num(stats.batteryLoadPercent()));
        out.add("motor_response_ms", ExportJson.num(stats.motorResponseMs()));
        out.add("crash_speed_ms", ExportJson.num(stats.crashSpeedMs()));
        out.add("sustained_motor_temp_c", ExportJson.num(stats.sustainedMotorTempC()));
        out.add("motor_temp_minute_c", ExportJson.num(stats.motorTempMinuteC()));
        out.add("mixed_motor_temp_c", ExportJson.num(stats.mixedMotorTempC()));
        out.add("props_in_view_percent", ExportJson.num(stats.propsInViewPercent()));
        out.add("warnings", ExportJson.strings(stats.warnings()));
        return out;
    }

    static JsonObject cameraView(CameraView view) {
        JsonObject out = new JsonObject();
        out.add("lens_mm", vec(view.lensMm()));
        out.add("uptilt_deg", ExportJson.num(view.uptiltDeg()));
        out.add("uptilt_min_deg", ExportJson.num(view.uptiltMinDeg()));
        out.add("uptilt_max_deg", ExportJson.num(view.uptiltMaxDeg()));
        out.add("horizontal_fov_deg", ExportJson.num(view.horizontalFovDeg()));
        out.add("vertical_fov_deg", ExportJson.num(view.verticalFovDeg()));
        out.add("aspect", ExportJson.num(view.aspect()));
        out.addProperty("projection", "rectilinear");
        JsonArray props = new JsonArray();
        for (CameraView.PropDisc disc : view.props()) {
            JsonObject entry = new JsonObject();
            entry.add("center_mm", vec(disc.centerMm()));
            entry.add("radius_mm", ExportJson.num(disc.radiusMm()));
            entry.add("hub_radius_mm", ExportJson.num(disc.hubRadiusMm()));
            props.add(entry);
        }
        out.add("props", props);
        out.add("props_in_view_percent", ExportJson.num(view.propsInViewPercent()));
        return out;
    }

    private static JsonArray vec(Vec3Mm v) {
        return ExportJson.nums(v.x(), v.y(), v.z());
    }

    private static JsonElement cameraView(BuildSpec build) {
        return PropwashApi.builds().cameraView(build).<JsonElement>map(WebExport::cameraView).orElse(JsonNull.INSTANCE);
    }

    private JsonElement presetName(DronePreset preset, String where) {
        if (preset.data().name().isPresent()) {
            JsonObject out = new JsonObject();
            for (String language : LangTable.LANGUAGES) {
                out.addProperty(language, preset.data().name().get());
            }
            return out;
        }
        return this.lang.both(preset.translationKey(), where, true);
    }

    private JsonArray presetsJson(String namespace, List<DronePreset> own, Map<String, Integer> counts) {
        counts.put("preset", own.size());
        JsonArray out = new JsonArray();
        for (DronePreset preset : own) {
            String where = namespace + "/presets.json#" + preset.id();
            JsonObject entry = new JsonObject();
            entry.addProperty("id", preset.id().toString());
            entry.add("name", this.presetName(preset, where));
            entry.addProperty("creative", preset.creativeListed());
            entry.add("build", build(preset.data().build()));
            entry.add("paint", paint(preset.data().paint()));
            entry.add("tune", tune(preset.data().tune()));
            entry.add("sharecode", ExportJson.str(ShareExport.presetCode(preset, this.tables, this.problems, where)));
            try {
                entry.add("default_tune", tune(PropwashApi.builds().defaultTune(preset.data().build())));
            } catch (IllegalArgumentException e) {
                entry.add("default_tune", JsonNull.INSTANCE);
                this.problems.error(where, "Standard-Tuning nicht berechenbar: " + e.getMessage());
            }
            BuildCheck check = PropwashApi.builds().check(preset.data().build());
            entry.add("check", check(check));
            Optional<BuildStats> stats = PropwashApi.builds().analyze(preset.data().build());
            entry.add("analysis", stats.<JsonElement>map(WebExport::stats).orElse(JsonNull.INSTANCE));
            entry.add("camera_view", cameraView(preset.data().build()));
            if (!check.buildable()) {
                this.problems.warning(where, "Preset nicht baubar: " + check.unknownParts() + " " + check.problems());
            }
            out.add(entry);
        }
        return out;
    }

    private JsonObject analysis(DronePreset preset) {
        BuildSpec base = preset.data().build();
        JsonObject out = new JsonObject();
        out.addProperty("preset", preset.id().toString());
        out.add("camera_view", cameraView(base));
        JsonArray variants = new JsonArray();
        for (PartDefinition part : this.parts) {
            BuildSpec variant;
            if (part instanceof AccessoryDefinition accessory) {
                variant = base.withAccessory(accessory.slot(), part.id());
            } else {
                variant = base.withPart(part.kind(), part.id());
            }
            BuildCheck check = PropwashApi.builds().check(variant);
            if (!check.buildable()) {
                continue;
            }
            Optional<BuildStats> stats = PropwashApi.builds().analyze(variant);
            if (stats.isEmpty()) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("category", part.kind().key());
            entry.addProperty("id", part.id().toString());
            entry.add("check", check(check));
            entry.add("analysis", stats(stats.get()));
            entry.add("camera_view", cameraView(variant));
            variants.add(entry);
        }
        out.add("variants", variants);
        return out;
    }

    private JsonObject manifestJson(String selection, SortedSet<String> available, List<String> chosen, boolean base, int partCount,
                                    int presetCount) {
        JsonObject out = new JsonObject();
        out.addProperty("format", FORMAT);
        out.addProperty("generated_at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        String version = this.resources.version(PropwashApi.MOD_ID).orElse("?");
        out.addProperty("generator", PropwashApi.MOD_ID + " " + version);
        out.addProperty("loader", this.resources.loader());
        out.addProperty("minecraft", this.resources.version("minecraft").orElse("?"));
        out.addProperty("propwash", version);
        out.addProperty("propwash_api", PropwashApi.API_VERSION);
        out.addProperty("selection", selection);
        out.addProperty("base", base);
        out.add("namespaces", ExportJson.strings(chosen));
        JsonObject sharecode = new JsonObject();
        sharecode.addProperty("format", Pw3Params.FORMAT);
        sharecode.addProperty("prefix", Pw3Params.PREFIX);
        sharecode.addProperty("revision", Pw3Params.REVISION);
        sharecode.add("reads", ExportJson.strings(List.of(Pw1Tables.FORMAT, Pw2Tables.FORMAT, Pw3Params.FORMAT)));
        sharecode.addProperty("pw1_revision", Pw1Tables.REVISION);
        sharecode.addProperty("pw2_revision", Pw2Tables.REVISION);
        out.add("sharecode", sharecode);
        JsonObject mods = new JsonObject();
        for (String namespace : available) {
            mods.add(namespace, ExportJson.str(this.resources.version(namespace)));
        }
        out.add("mods", mods);
        out.add("languages", ExportJson.strings(LangTable.LANGUAGES));
        JsonObject counts = new JsonObject();
        counts.addProperty("namespaces", chosen.size());
        counts.addProperty("parts", partCount);
        counts.addProperty("presets", presetCount);
        counts.addProperty("asset_files", this.assetFiles);
        counts.addProperty("vectors", this.vectorCount);
        out.add("counts", counts);
        out.addProperty("fingerprint", PropwashApi.parts().fingerprint());
        List<String> files = new ArrayList<>(this.written);
        files.add("manifest.json");
        files.sort(String::compareTo);
        out.add("files", ExportJson.strings(files));
        return out;
    }
}

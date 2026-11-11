package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.PartDefinition;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class AssetIndex {
    public static final Map<String, List<String>> CHANNEL_SLOTS = Map.of(
            "frame", List.of("frame"), "tpu", List.of("tpu"), "motor", List.of("motors"),
            "prop", List.of("prop_fl", "prop_fr", "prop_rl", "prop_rr"), "stack", List.of("stack"), "battery", List.of("battery"),
            "camera", List.of("camera"), "antenna", List.of("antenna"), "accessory", List.of("accessories"));
    public static final List<String> SUFFIXES = List.of(".json", ".png", ".mcmeta");
    public static final List<String> LAYOUT_FILES = List.of("drone_layout.json", "drone_paint.json");
    private static final Pattern VARIANT = Pattern.compile("((_(worn|damaged|broken|ccw|blur|under|paint))*)\\.json");

    private final SortedMap<String, byte[]> files = new TreeMap<>();
    private final Map<String, SortedSet<String>> used = new TreeMap<>();
    private final Map<String, List<Paint>> paint = new LinkedHashMap<>();
    private final Map<Identifier, Model> models = new TreeMap<>();
    private final Problems problems;
    private final ModResources resources;

    public record Paint(String channel, int rgb) {
    }

    public record Model(String kind, Optional<String> item, Optional<String> drone, List<String> variants, List<String> textures, boolean paintable,
                        Map<String, Integer> baseColors) {
    }

    public AssetIndex(Problems problems, ModResources resources) {
        this.problems = problems;
        this.resources = resources;
    }

    public void load(String modId) {
        if (!this.resources.loaded(modId)) {
            return;
        }
        for (String suffix : SUFFIXES) {
            this.resources.read(modId, "assets", suffix).forEach(this.files::putIfAbsent);
        }
    }

    public SortedMap<String, byte[]> files() {
        return this.files;
    }

    public List<String> languageFiles() {
        List<String> out = new ArrayList<>();
        for (String path : this.files.keySet()) {
            String[] parts = path.split("/");
            if (parts.length == 4 && parts[0].equals("assets") && parts[2].equals("lang") && parts[3].endsWith(".json")) {
                out.add(path);
            }
        }
        return out;
    }

    public void readPaint() {
        for (Map.Entry<String, byte[]> entry : this.files.entrySet()) {
            String[] parts = entry.getKey().split("/");
            if (parts.length != 3 || !parts[2].equals("drone_paint.json")) {
                continue;
            }
            String namespace = parts[1];
            try {
                JsonObject root = JsonParser.parseString(new String(entry.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject models = root.has("models") ? root.getAsJsonObject("models") : new JsonObject();
                for (Map.Entry<String, JsonElement> model : models.entrySet()) {
                    String key = model.getKey().contains(":") ? model.getKey() : namespace + ":" + model.getKey();
                    List<Paint> layers = new ArrayList<>();
                    for (JsonElement layer : model.getValue().getAsJsonArray()) {
                        JsonObject object = layer.getAsJsonObject();
                        String base = object.get("base").getAsString();
                        layers.add(new Paint(object.get("channel").getAsString(), Integer.parseInt(base.substring(1), 16)));
                    }
                    this.paint.put(key, layers);
                }
            } catch (RuntimeException e) {
                this.problems.warning(entry.getKey(), "drone_paint.json nicht lesbar: " + e.getMessage());
            }
        }
    }

    public boolean has(String path) {
        return this.files.containsKey(path);
    }

    public Model model(PartDefinition definition) {
        return this.models.computeIfAbsent(definition.id(), id -> this.find(definition));
    }

    private Model find(PartDefinition definition) {
        Identifier id = definition.id();
        String scope = id.getNamespace();
        String category = definition.kind().key();
        String itemPath = "assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json";
        Optional<String> item = Optional.empty();
        List<String> textures = new ArrayList<>();
        if (this.has(itemPath)) {
            item = Optional.of(itemPath);
            this.use(scope, itemPath, textures);
        } else {
            this.problems.warning(id.toString(), "Inventar-Symbol fehlt: " + itemPath);
        }
        String renderBase;
        Optional<Identifier> renderModel = definition.basics().visual().renderModel();
        if (renderModel.isPresent()) {
            Identifier model = renderModel.get();
            renderBase = "assets/" + model.getNamespace() + "/items/" + model.getPath();
        } else {
            renderBase = "assets/" + id.getNamespace() + "/items/drone_render/" + category + "/" + id.getPath();
        }
        Optional<String> drone = Optional.empty();
        List<String> variants = new ArrayList<>();
        if (this.has(renderBase + ".json")) {
            drone = Optional.of(renderBase + ".json");
            this.use(scope, renderBase + ".json", textures);
            for (String path : this.files.subMap(renderBase + "_", renderBase + "_￿").keySet()) {
                if (VARIANT.matcher(path.substring(renderBase.length())).matches()) {
                    variants.add(path);
                    this.use(scope, path, textures);
                }
            }
        }
        String paintKey = renderModel.map(m -> m.getNamespace() + ":" + stripDroneRender(m.getPath()))
                .orElse(id.getNamespace() + ":" + category + "/" + id.getPath());
        List<Paint> layers = this.paint.getOrDefault(paintKey, List.of());
        boolean paintable = !layers.isEmpty() && this.has(renderBase + "_paint.json");
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (Paint layer : layers) {
            for (String slot : CHANNEL_SLOTS.getOrDefault(layer.channel(), List.of())) {
                colors.putIfAbsent(slot, layer.rgb());
            }
        }
        return new Model(drone.isPresent() ? "model" : "procedural", item, drone, variants, List.copyOf(new TreeSet<>(textures)), paintable, colors);
    }

    private static String stripDroneRender(String path) {
        return path.startsWith("drone_render/") ? path.substring("drone_render/".length()) : path;
    }

    private void use(String scope, String path, List<String> textures) {
        this.follow(this.used.computeIfAbsent(scope, s -> new TreeSet<>()), path, textures, new TreeSet<>(), 0);
    }

    private void follow(Set<String> into, String path, List<String> textures, Set<String> visited, int depth) {
        if (depth > 16 || !visited.add(path)) {
            return;
        }
        into.add(path);
        if (path.endsWith(".png")) {
            textures.add(path);
            String meta = path + ".mcmeta";
            if (this.has(meta)) {
                into.add(meta);
            }
            return;
        }
        byte[] data = this.files.get(path);
        if (data == null) {
            return;
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            this.problems.warning(path, "JSON nicht lesbar: " + e.getMessage());
            return;
        }
        this.walk(into, root, path.contains("/models/"), textures, visited, depth, path);
    }

    private void walk(Set<String> into, JsonElement element, boolean model, List<String> textures, Set<String> visited, int depth, String from) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                this.walk(into, child, model, textures, visited, depth, from);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            String key = entry.getKey();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString();
                if (!model && (key.equals("model") || key.equals("base")) || model && key.equals("parent")) {
                    this.reference(into, text, "models", ".json", textures, visited, depth, from);
                }
            } else if (model && key.equals("textures") && value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> texture : value.getAsJsonObject().entrySet()) {
                    if (texture.getValue().isJsonPrimitive()) {
                        String text = texture.getValue().getAsString();
                        if (!text.startsWith("#")) {
                            this.reference(into, text, "textures", ".png", textures, visited, depth, from);
                        }
                    }
                }
            } else {
                this.walk(into, value, model, textures, visited, depth, from);
            }
        }
    }

    private void reference(Set<String> into, String text, String folder, String suffix, List<String> textures, Set<String> visited, int depth,
                           String from) {
        Identifier id = Identifier.tryParse(text);
        if (id == null || id.getNamespace().equals("minecraft")) {
            return;
        }
        String path = "assets/" + id.getNamespace() + "/" + folder + "/" + id.getPath() + suffix;
        if (!this.has(path)) {
            if (!id.getPath().startsWith("builtin/")) {
                this.problems.warning(from, "Verweis ohne Datei: " + path);
            }
            return;
        }
        this.follow(into, path, textures, visited, depth + 1);
    }

    public void useLayoutFiles(String namespace) {
        for (String name : LAYOUT_FILES) {
            String path = "assets/" + namespace + "/" + name;
            if (this.has(path)) {
                this.used.computeIfAbsent(namespace, s -> new TreeSet<>()).add(path);
            }
        }
    }

    public SortedSet<String> used(String namespace) {
        return this.used.getOrDefault(namespace, new TreeSet<>());
    }
}

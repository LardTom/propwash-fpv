package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record AddonDirectory(List<Addon> addons, List<String> problems) {
    public static final String RESOURCE = "/data/propwash/propwash_share/directory.json";
    public static final String ADDONS_PAGE = "https://propwashfpv.com/addons/";
    public static final AddonDirectory EMPTY = new AddonDirectory(List.of(), List.of());

    public AddonDirectory {
        addons = List.copyOf(Objects.requireNonNullElse(addons, List.of()));
        problems = List.copyOf(Objects.requireNonNullElse(problems, List.of()));
    }

    public record Addon(String namespace, int shortId, Map<String, String> name, Optional<String> modrinth, Optional<String> curseforge,
                        Optional<String> version, Optional<ShareTable> table) {
        public Addon {
            Objects.requireNonNull(namespace, "namespace");
            name = Map.copyOf(Objects.requireNonNullElse(name, Map.of()));
            modrinth = Objects.requireNonNullElse(modrinth, Optional.empty());
            curseforge = Objects.requireNonNullElse(curseforge, Optional.empty());
            version = Objects.requireNonNullElse(version, Optional.empty());
            table = Objects.requireNonNullElse(table, Optional.empty());
        }

        public String displayName(String language) {
            String key = language == null ? "en_us" : language.toLowerCase(java.util.Locale.ROOT);
            return this.name.getOrDefault(key, this.name.getOrDefault("en_us", this.namespace));
        }
    }

    public Optional<Addon> byNamespace(String namespace) {
        for (Addon addon : this.addons) {
            if (addon.namespace().equals(namespace)) {
                return Optional.of(addon);
            }
        }
        return Optional.empty();
    }

    public Optional<Addon> byShortId(int shortId) {
        for (Addon addon : this.addons) {
            if (addon.shortId() == shortId) {
                return Optional.of(addon);
            }
        }
        return Optional.empty();
    }

    private static volatile AddonDirectory builtin;

    public static AddonDirectory builtin() {
        AddonDirectory value = builtin;
        if (value == null) {
            value = readBuiltin();
            builtin = value;
        }
        return value;
    }

    private static AddonDirectory readBuiltin() {
        try (InputStream in = AddonDirectory.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new AddonDirectory(List.of(), List.of("Verzeichnis-Abzug fehlt: " + RESOURCE));
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new AddonDirectory(List.of(), List.of("Verzeichnis-Abzug nicht lesbar: " + e.getMessage()));
        }
    }

    public static AddonDirectory parse(String json) {
        List<String> problems = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return new AddonDirectory(List.of(), List.of("Verzeichnis ist kein JSON-Objekt"));
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return new AddonDirectory(List.of(), List.of("Verzeichnis: ungültiges JSON: " + e.getMessage()));
        }
        if (ShareTableJson.intValue(root, "format", -1) != ShareTableJson.FORMAT) {
            return new AddonDirectory(List.of(), List.of("Verzeichnis: unbekanntes Format"));
        }
        List<Addon> addons = new ArrayList<>();
        JsonArray array = root.has("addons") && root.get("addons").isJsonArray() ? root.getAsJsonArray("addons") : new JsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                problems.add("Verzeichnis: Eintrag ist kein Objekt");
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String namespace = ShareTableJson.string(object, "namespace").orElse("");
            int shortId = ShareTableJson.intValue(object, "short_id", 0);
            if (!Pw3Params.validNamespace(namespace) || shortId < 1) {
                problems.add("Verzeichnis: ungültiger Eintrag \"" + namespace + "\"");
                continue;
            }
            Map<String, String> names = new LinkedHashMap<>();
            if (object.has("name") && object.get("name").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("name").entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        names.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            Optional<ShareTable> table = Optional.empty();
            if (object.has("table") && object.get("table").isJsonObject()) {
                ShareTableJson.Parsed parsed = ShareTableJson.parse(namespace, object.getAsJsonObject("table"));
                for (String problem : parsed.problems()) {
                    problems.add("Verzeichnis " + namespace + ": " + problem);
                }
                table = parsed.table();
            }
            addons.add(new Addon(namespace, shortId, names, ShareTableJson.string(object, "modrinth").filter(Pw3Params::validSlug),
                    ShareTableJson.string(object, "curseforge").filter(Pw3Params::validSlug), ShareTableJson.string(object, "version"), table));
        }
        return new AddonDirectory(addons, problems);
    }
}

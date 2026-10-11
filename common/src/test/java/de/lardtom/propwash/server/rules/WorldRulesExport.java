package de.lardtom.propwash.server.rules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

final class WorldRulesExport {
    static final int FORMAT = 1;
    static final List<String> LANGUAGES = List.of("de_de", "en_us");
    static final String OUT_ENV = "PROPWASH_RULES_OUT";

    private WorldRulesExport() {
    }

    static Path root() {
        Path here = Path.of("").toAbsolutePath();
        return Files.isDirectory(here.resolve("common/src/lang")) ? here : here.getParent();
    }

    static Path output() {
        String env = System.getenv(OUT_ENV);
        return env != null && !env.isBlank() ? Path.of(env) : root().resolve("build/web-export/gamerules.json");
    }

    static Map<String, Map<String, String>> languages() throws IOException {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Path lang = root().resolve("common/src/lang");
        Gson gson = new Gson();
        for (String language : LANGUAGES) {
            Map<String, String> merged = new LinkedHashMap<>();
            try (DirectoryStream<Path> modules = Files.newDirectoryStream(lang, Files::isDirectory)) {
                for (Path module : modules) {
                    Path file = module.resolve(language + ".json");
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonObject json = gson.fromJson(reader, JsonObject.class);
                        json.entrySet().forEach(entry -> merged.put(entry.getKey(), entry.getValue().getAsString()));
                    }
                }
            }
            out.put(language, merged);
        }
        return out;
    }

    static String version() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(root().resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            return "";
        }
        return properties.getProperty("version", "");
    }

    static List<String> missingTexts(Map<String, Map<String, String>> languages) {
        List<String> missing = new ArrayList<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            List<String> keys = new ArrayList<>(List.of(spec.translationKey(), spec.descriptionKey(), spec.group().key()));
            for (int value : labelledValues(spec)) {
                keys.add(spec.valueKey(value));
            }
            for (String language : LANGUAGES) {
                for (String key : keys) {
                    String text = languages.get(language).get(key);
                    if (text == null || text.isBlank()) {
                        missing.add(language + ": " + key);
                    }
                }
            }
        }
        return missing;
    }

    static List<Integer> labelledValues(RuleSpec spec) {
        List<Integer> values = new ArrayList<>();
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            return values;
        }
        if (spec.display() == RuleSpec.Display.LEVELS) {
            for (int v = spec.min(); v <= spec.max(); v++) {
                values.add(v);
            }
        } else if (spec.zeroSpecial()) {
            values.add(0);
        }
        return values;
    }

    static boolean changesPhysics(RuleSpec spec) {
        int other = spec.defaultValue() == spec.max() ? spec.min() : spec.max();
        return PhysicsRules.of(RuleValues.DEFAULTS.with(spec, other)).modified();
    }

    private static JsonObject texts(Map<String, Map<String, String>> languages, String key) {
        JsonObject out = new JsonObject();
        for (String language : LANGUAGES) {
            out.addProperty(language.substring(0, 2), languages.get(language).getOrDefault(key, ""));
        }
        return out;
    }

    static JsonObject build(Map<String, Map<String, String>> languages) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("mod", RuleSpec.NAMESPACE);
        root.addProperty("version", version());
        root.addProperty("command", "/gamerule <id> <value>");
        root.add("category", texts(languages, "gamerule.category.propwash.propwash"));
        JsonArray groups = new JsonArray();
        for (RuleSpec.Group group : RuleSpec.Group.values()) {
            if (WorldRuleSpecs.all().stream().noneMatch(spec -> spec.group() == group)) {
                continue;
            }
            JsonObject g = new JsonObject();
            g.addProperty("id", group.name().toLowerCase(Locale.ROOT));
            g.add("title", texts(languages, group.key()));
            groups.add(g);
        }
        root.add("groups", groups);
        JsonArray rules = new JsonArray();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            JsonObject r = new JsonObject();
            r.addProperty("id", spec.id());
            r.addProperty("name", spec.name());
            r.addProperty("group", spec.group().name().toLowerCase(Locale.ROOT));
            boolean bool = spec.kind() == RuleSpec.Kind.BOOL;
            r.addProperty("type", bool ? "boolean" : "integer");
            r.addProperty("display", spec.display().name().toLowerCase(Locale.ROOT));
            if (bool) {
                r.addProperty("default", spec.defaultOn());
            } else {
                r.addProperty("default", spec.defaultValue());
                r.addProperty("min", spec.min());
                r.addProperty("max", spec.max());
                r.addProperty("step", spec.step());
                if (spec.display() == RuleSpec.Display.PERCENT) {
                    r.addProperty("unit", "%");
                }
            }
            r.addProperty("changes_physics", changesPhysics(spec));
            r.add("title", texts(languages, spec.translationKey()));
            r.add("description", texts(languages, spec.descriptionKey()));
            List<Integer> labelled = labelledValues(spec);
            if (!labelled.isEmpty()) {
                JsonObject values = new JsonObject();
                for (int value : labelled) {
                    values.add(Integer.toString(value), texts(languages, spec.valueKey(value)));
                }
                r.add("values", values);
            }
            r.addProperty("example", "/gamerule " + spec.id() + " " + spec.serialize(bool ? 1 - spec.defaultValue()
                    : spec.defaultValue() == spec.max() ? spec.min() : Math.min(spec.max(), spec.defaultValue() + spec.step())));
            rules.add(r);
        }
        root.add("rules", rules);
        JsonArray presets = new JsonArray();
        for (RulePreset preset : RulePreset.values()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", preset.id());
            p.add("title", texts(languages, preset.nameKey()));
            p.add("description", texts(languages, preset.descriptionKey()));
            p.addProperty("changes_physics", PhysicsRules.of(preset.ruleValues()).modified());
            JsonObject values = new JsonObject();
            for (RuleSpec spec : WorldRuleSpecs.all()) {
                if (spec.kind() == RuleSpec.Kind.BOOL) {
                    values.addProperty(spec.name(), preset.value(spec) != 0);
                } else {
                    values.addProperty(spec.name(), preset.value(spec));
                }
            }
            p.add("values", values);
            JsonArray differs = new JsonArray();
            preset.differences().forEach(spec -> differs.add(spec.name()));
            p.add("differs_from_default", differs);
            p.addProperty("command", "/propwash rules preset " + preset.id());
            presets.add(p);
        }
        root.add("presets", presets);
        return root;
    }

    static Path write(JsonObject json) throws IOException {
        Path out = output();
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.writeString(out, gson.toJson(json) + "\n", StandardCharsets.UTF_8);
        return out;
    }
}

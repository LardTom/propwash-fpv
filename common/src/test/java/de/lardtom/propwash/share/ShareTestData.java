package de.lardtom.propwash.share;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.platform.ModMeta;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

final class ShareTestData {
    static final String REGENERATE = "PROPWASH_SHARE_REGENERATE";
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private ShareTestData() {
    }

    static boolean regenerate() {
        return "1".equals(System.getenv(REGENERATE)) || Boolean.getBoolean("propwash.share.regenerate");
    }

    static Path resource(String name) {
        for (Path candidate : List.of(Path.of("common/src/test/resources/sharecode", name), Path.of("src/test/resources/sharecode", name))) {
            if (Files.exists(candidate) || Files.isDirectory(candidate.getParent())) {
                return candidate;
            }
        }
        return Path.of("common/src/test/resources/sharecode", name);
    }

    static Path repo(String path) {
        for (Path candidate : List.of(Path.of(path), Path.of("..", path))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return Path.of(path);
    }

    static JsonObject read(String name) {
        try {
            return JsonParser.parseString(Files.readString(resource(name), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<JsonObject> vectors(String name) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement element : read(name).getAsJsonArray("vectors")) {
            out.add(element.getAsJsonObject());
        }
        return out;
    }

    static void write(String name, JsonObject root) {
        StringBuilder text = new StringBuilder("{\n");
        List<String> keys = new ArrayList<>(root.keySet());
        for (int k = 0; k < keys.size(); k++) {
            String key = keys.get(k);
            JsonElement value = root.get(key);
            text.append("  ").append(GSON.toJson(key)).append(": ");
            if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                text.append("[\n");
                for (int i = 0; i < array.size(); i++) {
                    text.append("    ").append(GSON.toJson(array.get(i))).append(i + 1 < array.size() ? ",\n" : "\n");
                }
                text.append("  ]");
            } else if (value.isJsonObject() && !value.getAsJsonObject().isEmpty()) {
                text.append("{\n");
                List<String> inner = new ArrayList<>(value.getAsJsonObject().keySet());
                for (int i = 0; i < inner.size(); i++) {
                    text.append("    ").append(GSON.toJson(inner.get(i))).append(": ").append(GSON.toJson(value.getAsJsonObject().get(inner.get(i))))
                            .append(i + 1 < inner.size() ? ",\n" : "\n");
                }
                text.append("  }");
            } else {
                text.append(GSON.toJson(value));
            }
            text.append(k + 1 < keys.size() ? ",\n" : "\n");
        }
        text.append("}\n");
        try {
            Files.createDirectories(resource(name).getParent());
            Files.writeString(resource(name), text.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonObject content(ShareContent c) {
        JsonObject out = new JsonObject();
        out.add("build", build(c.build()));
        out.add("paint", c.paint().<JsonElement>map(ShareTestData::paint).orElse(JsonNull.INSTANCE));
        out.add("tune", c.tune().<JsonElement>map(t -> {
            JsonObject o = new JsonObject();
            new TreeMap<>(t.values()).forEach((k, v) -> o.add(k, new JsonPrimitive(Double.parseDouble(Float.toString(v)))));
            return o;
        }).orElse(JsonNull.INSTANCE));
        out.add("osd", c.osd().<JsonElement>map(o -> {
            JsonObject j = new JsonObject();
            j.addProperty("version", o.version());
            j.addProperty("json", o.json());
            return j;
        }).orElse(JsonNull.INSTANCE));
        out.add("name", c.name().<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
        return out;
    }

    static JsonObject build(BuildSpec b) {
        JsonObject out = new JsonObject();
        out.addProperty("frame", b.frame().toString());
        out.addProperty("stack", b.stack().toString());
        out.addProperty("motor", b.motor().toString());
        out.addProperty("prop", b.prop().toString());
        out.addProperty("video", b.video().toString());
        out.addProperty("battery", b.battery().toString());
        JsonObject acc = new JsonObject();
        for (AccessorySlot slot : AccessorySlot.values()) {
            b.accessory(slot).ifPresent(id -> acc.addProperty(slot.key(), id.toString()));
        }
        out.add("accessories", acc);
        return out;
    }

    static JsonObject paint(PaintSpec p) {
        JsonObject out = new JsonObject();
        for (String slot : PaintSpec.SLOTS) {
            p.color(slot).ifPresent(rgb -> out.addProperty(slot, String.format("#%06x", rgb)));
        }
        return out;
    }

    static ShareContent content(JsonObject json) {
        BuildSpec build = build(json.getAsJsonObject("build"));
        Optional<PaintSpec> paint = Optional.empty();
        if (json.has("paint") && !json.get("paint").isJsonNull()) {
            Map<String, Integer> colors = new LinkedHashMap<>();
            json.getAsJsonObject("paint").entrySet().forEach(e -> colors.put(e.getKey(), Integer.parseInt(e.getValue().getAsString().substring(1), 16)));
            paint = Optional.of(new PaintSpec(colors));
        }
        Optional<TuneSpec> tune = Optional.empty();
        if (json.has("tune") && !json.get("tune").isJsonNull()) {
            Map<String, Float> values = new TreeMap<>();
            json.getAsJsonObject("tune").entrySet().forEach(e -> values.put(e.getKey(), Float.parseFloat(e.getValue().getAsString())));
            tune = Optional.of(new TuneSpec(values));
        }
        Optional<ShareOsd> osd = Optional.empty();
        if (json.has("osd") && !json.get("osd").isJsonNull()) {
            JsonObject o = json.getAsJsonObject("osd");
            osd = Optional.of(new ShareOsd(o.get("version").getAsInt(), o.get("json").getAsString()));
        }
        Optional<String> name = json.has("name") && !json.get("name").isJsonNull() ? Optional.of(json.get("name").getAsString()) : Optional.empty();
        return new ShareContent(build, paint, tune, osd, name);
    }

    static BuildSpec build(JsonObject b) {
        EnumMap<AccessorySlot, Identifier> acc = new EnumMap<>(AccessorySlot.class);
        b.getAsJsonObject("accessories").entrySet().forEach(e -> acc.put(AccessorySlot.byKey(e.getKey()).orElseThrow(),
                Identifier.parse(e.getValue().getAsString())));
        return new BuildSpec(id(b, "frame"), id(b, "stack"), id(b, "motor"), id(b, "prop"), id(b, "video"), id(b, "battery"), acc);
    }

    private static Identifier id(JsonObject b, String key) {
        return Identifier.parse(b.get(key).getAsString());
    }

    static JsonArray strings(List<String> values) {
        JsonArray out = new JsonArray();
        values.forEach(out::add);
        return out;
    }

    static JsonArray layers(List<ShareLayer> layers) {
        JsonArray out = new JsonArray();
        layers.forEach(l -> out.add(l.key()));
        return out;
    }

    static List<String> stringList(JsonElement array) {
        List<String> out = new ArrayList<>();
        array.getAsJsonArray().forEach(e -> out.add(e.getAsString()));
        return out;
    }

    static ShareTable table(String json) {
        return ShareTableJson.parse(null, json).table().orElseThrow();
    }

    static final String EXTRA_V1 = "{\"format\": 1, \"namespace\": \"extraparts\", \"parts\": {"
            + "\"frame\": [{\"id\": \"x_frame_1\", \"since\": \"1.0.0\", \"fallback\": \"propwash:freestyle_5\"},"
            + " {\"id\": \"x_frame_2\", \"since\": \"1.1.0\", \"fallback\": \"propwash:race_5\"}],"
            + "\"motor\": [{\"id\": \"old_motor\", \"since\": \"1.0.0\", \"deprecated\": \"1.1.0\", \"replaced_by\": \"extraparts:new_motor\", \"fallback\": \"propwash:m2207_1750\"},"
            + " {\"id\": \"new_motor\", \"since\": \"1.1.0\", \"fallback\": \"propwash:m2207_1750\"}]}}";
    static final String EXTRA_V2 = "{\"format\": 1, \"namespace\": \"extraparts\", \"parts\": {"
            + "\"frame\": [{\"id\": \"x_frame_1\", \"since\": \"1.0.0\", \"fallback\": \"propwash:freestyle_5\"},"
            + " {\"id\": \"x_frame_2\", \"since\": \"1.1.0\", \"fallback\": \"propwash:race_5\"},"
            + " {\"id\": \"x_frame_3\", \"since\": \"1.2.0\", \"fallback\": \"propwash:long_range_7\"}],"
            + "\"motor\": [{\"id\": \"old_motor\", \"since\": \"1.0.0\", \"deprecated\": \"1.1.0\", \"replaced_by\": \"extraparts:new_motor\", \"fallback\": \"propwash:m2207_1750\"},"
            + " {\"id\": \"new_motor\", \"since\": \"1.1.0\", \"fallback\": \"propwash:m2207_1750\"}]}}";

    static JsonObject contexts() {
        JsonObject out = new JsonObject();
        JsonObject frozen = new JsonObject();
        frozen.addProperty("description", "Umrechnung: Tabelle von Propwash (Jar) und JMP-Tabelle aus dem Verzeichnis-Abzug, beide als installiert, keine Teile angemeldet, keine Versionen, keine Infos");
        out.add("frozen", frozen);
        out.add("extra", context(EXTRA_V1, "1.1.0", List.of("extraparts:x_frame_1", "extraparts:x_frame_2", "extraparts:new_motor")));
        out.add("extra_newer", context(EXTRA_V2, "1.2.0", List.of("extraparts:x_frame_1", "extraparts:x_frame_2", "extraparts:x_frame_3",
                "extraparts:new_motor")));
        return out;
    }

    private static JsonObject context(String table, String version, List<String> extraParts) {
        JsonObject out = new JsonObject();
        out.addProperty("description", "Propwash-Tabelle aus dem Jar, Verzeichnis-Abzug, alle eingebauten Teile angemeldet, dazu die Tabelle und Teile unten; JMP fehlt");
        JsonArray tables = new JsonArray();
        tables.add(JsonParser.parseString(table));
        out.add("tables", tables);
        JsonObject versions = new JsonObject();
        versions.addProperty("extraparts", version);
        versions.addProperty("otheraddon", "2.1.0");
        versions.addProperty("freeaddon", "0.3.0");
        versions.addProperty("betaaddon", "1.3.0-beta.1");
        out.add("versions", versions);
        JsonObject mods = new JsonObject();
        mods.add("extraparts", meta("Extra Parts", "https://modrinth.com/mod/extra-parts"));
        mods.add("otheraddon", meta("Other Addon", "https://modrinth.com/mod/other-addon"));
        mods.add("freeaddon", meta("Free Addon", "https://example.org/free-addon"));
        mods.add("betaaddon", meta("Beta Addon", "https://www.curseforge.com/minecraft/mc-mods/beta-addon"));
        out.add("mods", mods);
        JsonObject registered = new JsonObject();
        JsonArray frames = new JsonArray();
        JsonArray motors = new JsonArray();
        for (String id : extraParts) {
            (id.contains("frame") ? frames : motors).add(id);
        }
        frames.add("otheraddon:frame_x");
        frames.add("freeaddon:fp_frame");
        motors.add("betaaddon:beta_motor");
        registered.add("frame", frames);
        registered.add("motor", motors);
        out.addProperty("registered_builtin", true);
        out.add("registered", registered);
        return out;
    }

    private static JsonObject meta(String name, String homepage) {
        JsonObject out = new JsonObject();
        out.addProperty("name", name);
        out.addProperty("homepage", homepage);
        return out;
    }

    static ShareTables context(String name) {
        if (name.equals("frozen")) {
            return ShareTables.frozen();
        }
        JsonObject json = contexts().getAsJsonObject(name);
        ShareTables.Builder builder = ShareTables.builder().installed(ShareTables.propwashTable()).directory(AddonDirectory.builtin());
        for (JsonElement table : json.getAsJsonArray("tables")) {
            builder.installed(table(table.toString()));
        }
        json.getAsJsonObject("versions").entrySet().forEach(e -> builder.version(e.getKey(), e.getValue().getAsString()));
        json.getAsJsonObject("mods").entrySet().forEach(e -> {
            JsonObject m = e.getValue().getAsJsonObject();
            builder.mod(e.getKey(), new ModMeta(e.getKey(), m.get("name").getAsString(), json.getAsJsonObject("versions").get(e.getKey()).getAsString(),
                    m.get("homepage").getAsString(), false));
        });
        Map<Identifier, PartKind> kinds = new HashMap<>();
        for (PartInfo part : PartIndex.registered()) {
            if (part.builtin()) {
                kinds.put(part.id(), part.definition().kind());
            }
        }
        json.getAsJsonObject("registered").entrySet().forEach(e -> e.getValue().getAsJsonArray()
                .forEach(id -> kinds.put(Identifier.parse(id.getAsString()), PartKind.byKey(e.getKey()).orElseThrow())));
        builder.parts(id -> Optional.ofNullable(kinds.get(id)));
        return builder.build();
    }
}

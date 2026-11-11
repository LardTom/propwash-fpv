package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CovPropBlurCenterModelsTest {
    private static final List<String> MODEL_DIRS = List.of("src/main/resources/assets/propwash/models/part/prop",
            "src/devtest/resources/assets/propwash_rendertest/models/part/prop");
    private static final String CORE = "src/main/resources/assets/propwash/models/part/prop/p51_3b";

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve(MODEL_DIRS.getFirst()))) {
                return candidate;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject core(String suffix) throws IOException {
        return read(common().resolve(CORE + suffix + ".json"));
    }

    private static int count(JsonObject model, String name) {
        int count = 0;
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            if (element.has("name") && element.get("name").getAsString().equals(name)) {
                count++;
            }
        }
        return count;
    }

    private static List<JsonObject> named(JsonObject model, String name) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            if (element.has("name") && element.get("name").getAsString().equals(name)) {
                out.add(element);
            }
        }
        return out;
    }

    private static void remove(JsonObject model, String name) {
        JsonArray kept = new JsonArray();
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            if (!element.has("name") || !element.get("name").getAsString().equals(name)) {
                kept.add(element);
            }
        }
        model.add("elements", kept);
    }

    record Pair(Path standing, Path blur) {
        String label() {
            return this.standing.getFileName() + " / " + this.blur.getFileName();
        }
    }

    private static List<Pair> pairs() throws IOException {
        List<Pair> out = new ArrayList<>();
        for (String dir : MODEL_DIRS) {
            Path folder = common().resolve(dir);
            List<Path> blurs;
            try (Stream<Path> files = Files.list(folder)) {
                blurs = files.filter(p -> p.getFileName().toString().matches(".*_blur(_paint)?\\.json")).sorted().toList();
            }
            for (Path blurFile : blurs) {
                String name = blurFile.getFileName().toString();
                boolean paint = name.endsWith("_blur_paint.json");
                String key = name.substring(0, name.length() - (paint ? "_blur_paint.json" : "_blur.json").length());
                for (String spin : List.of("", "_ccw")) {
                    Path standingFile = folder.resolve(key + spin + (paint ? "_paint" : "") + ".json");
                    if (Files.isRegularFile(standingFile)) {
                        out.add(new Pair(standingFile, blurFile));
                    }
                }
            }
        }
        return out;
    }

    private static Map<String, Integer> centerParts(JsonObject blur) {
        Map<String, Integer> out = new TreeMap<>();
        List<CovPropBlurCenterModels.Element> elements = CovPropBlurCenterModels.elements(blur);
        assertNotNull(elements);
        for (CovPropBlurCenterModels.Element element : elements) {
            if (!element.translucent()) {
                out.merge(element.name(), 1, Integer::sum);
            }
        }
        return out;
    }

    @Test
    void everyPropOfPropwashHasTheStandingCenterInItsBlurModel() throws IOException {
        List<Pair> pairs = pairs();
        List<String> problems = new ArrayList<>();
        for (Pair pair : pairs) {
            CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(read(pair.standing()), read(pair.blur()));
            if (result == null) {
                problems.add(pair.label() + ": nicht verglichen");
            } else if (!result.same() || result.bladeParts() == 0) {
                problems.add(String.format(Locale.ROOT, "%s: fehlen %s, zusätzlich %s, %d Blattteile", pair.label(), result.missing(), result.extra(),
                        result.bladeParts()));
            }
        }
        assertTrue(pairs.size() >= 28, "nur " + pairs.size() + " Modellpaare gefunden");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyPartOfTheCenterOfEveryPropwashPropIsFoundWhenItIsLeftOut() throws IOException {
        int cases = 0;
        List<String> problems = new ArrayList<>();
        for (Pair pair : pairs()) {
            CovPropBlurCenterModels.Result full = CovPropBlurCenterModels.compare(read(pair.standing()), read(pair.blur()));
            assertNotNull(full, pair.label() + ": nicht verglichen");
            for (Map.Entry<String, Integer> part : centerParts(read(pair.blur())).entrySet()) {
                JsonObject blur = read(pair.blur());
                remove(blur, part.getKey());
                CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(read(pair.standing()), blur);
                cases++;
                if (result == null || !result.missing().equals(Collections.nCopies(part.getValue(), part.getKey())) || !result.extra().isEmpty()
                        || result.bladeParts() != full.bladeParts()) {
                    problems.add(String.format(Locale.ROOT, "%s ohne %s: fehlen %s, zusätzlich %s, %s Blattteile statt %d", pair.label(), part.getKey(),
                            result == null ? "?" : result.missing(), result == null ? "?" : result.extra(), result == null ? "?" : result.bladeParts(),
                            full.bladeParts()));
                }
            }
        }
        assertTrue(cases >= 64, "nur " + cases + " Fälle");
        assertTrue(problems.isEmpty(), problems.size() + " von " + cases + " Fällen:\n" + String.join("\n", problems));
    }

    @Test
    void bladeTipsOfAFoldingPropCountAsBlades() {
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(folding(false), folding(true));
        assertNotNull(result);
        assertTrue(result.same(), "fehlen " + result.missing() + ", zusätzlich " + result.extra());
        assertEquals(10, result.bladeParts());
    }

    @Test
    void bladesNeedNoName() throws IOException {
        JsonObject standing = core("");
        int blades = count(standing, "blade");
        for (JsonObject blade : named(standing, "blade")) {
            blade.remove("name");
        }
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(standing, core("_blur"));
        assertNotNull(result);
        assertTrue(result.same(), "fehlen " + result.missing() + ", zusätzlich " + result.extra());
        assertEquals(blades, result.bladeParts());
    }

    @Test
    void theDiscIsFoundByItsTranslucentTextureNotByItsName() throws IOException {
        JsonObject blur = core("_blur");
        named(blur, "blur_disc").getFirst().addProperty("name", "scheibe");
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(core(""), blur);
        assertNotNull(result);
        assertTrue(result.same(), "fehlen " + result.missing() + ", zusätzlich " + result.extra());
    }

    @Test
    void aBlurModelWithoutTranslucentDiscIsNotCompared() throws IOException {
        JsonObject blur = core("_blur");
        JsonObject textures = blur.getAsJsonObject("textures");
        textures.addProperty("0", textures.getAsJsonObject("0").get("sprite").getAsString());
        assertNull(CovPropBlurCenterModels.compare(core(""), blur));
    }

    @Test
    void aMissingNutIsStillFound() throws IOException {
        JsonObject blur = core("_blur");
        int nuts = count(blur, "nut");
        remove(blur, "nut");
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(core(""), blur);
        assertNotNull(result);
        assertFalse(result.same());
        assertEquals(nuts, result.missing().size(), "fehlen " + result.missing());
        assertTrue(result.missing().stream().allMatch("nut"::equals), "fehlen " + result.missing());
    }

    @Test
    void aMissingScrewOutsideTheHubIsStillFound() {
        JsonObject blur = folding(true);
        JsonArray kept = new JsonArray();
        for (JsonElement entry : blur.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            if (!element.get("name").getAsString().equals("screw") || element.getAsJsonArray("from").get(0).getAsFloat() > 8.0F) {
                kept.add(element);
            }
        }
        blur.add("elements", kept);
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(folding(false), blur);
        assertNotNull(result);
        assertFalse(result.same());
        assertEquals(List.of("screw", "screw"), result.missing());
    }

    @Test
    void aBlurModelWithoutTheHubIsFound() throws IOException {
        JsonObject blur = core("_blur");
        int hubs = count(blur, "hub");
        remove(blur, "hub");
        JsonObject standing = core("");
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(standing, blur);
        assertNotNull(result);
        assertFalse(result.same());
        assertEquals(Collections.nCopies(hubs, "hub"), result.missing());
        assertEquals(count(standing, "blade"), result.bladeParts());
    }

    @Test
    void aFoldingBlurModelWithoutScrewsIsFound() {
        JsonObject blur = folding(true);
        int screws = count(blur, "screw");
        remove(blur, "screw");
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(folding(false), blur);
        assertNotNull(result);
        assertFalse(result.same());
        assertEquals(Collections.nCopies(screws, "screw"), result.missing());
        assertEquals(10, result.bladeParts());
    }

    @Test
    void aFoldingBlurModelWithoutHubIsFound() {
        JsonObject blur = folding(true);
        int hubs = count(blur, "hub");
        remove(blur, "hub");
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(folding(false), blur);
        assertNotNull(result);
        assertFalse(result.same());
        assertEquals(Collections.nCopies(hubs, "hub"), result.missing());
        assertEquals(10, result.bladeParts());
    }

    @Test
    void bladeTipsOfAnAddonPropStayBladesWhenTheCenterIsIncomplete() {
        JsonObject blur = folding(true);
        remove(blur, "screw");
        remove(blur, "hub");
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(folding(false), blur);
        assertNotNull(result);
        assertFalse(result.same());
        assertTrue(result.missing().stream().noneMatch(name -> name.startsWith("blade")), "fehlen " + result.missing());
        assertEquals(10, result.missing().size(), "fehlen " + result.missing());
        assertEquals(10, result.bladeParts());
    }

    @Test
    void aTiltedPartWithinTheCenterBelongsToTheCenter() throws IOException {
        String washer = element("washer", "[7.75, 8.875, 7.75]", "[8.25, 8.925, 8.25]", "{\"x\": 10, \"y\": 0, \"z\": 0, \"origin\": [8, 8.9, 8]}", "#0");
        JsonObject standing = core("");
        standing.getAsJsonArray("elements").add(JsonParser.parseString(washer));
        JsonObject blur = core("_blur");
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(standing, blur);
        assertNotNull(result);
        assertEquals(List.of("washer"), result.missing());
        blur.getAsJsonArray("elements").add(JsonParser.parseString(washer.replace("\"#0\"", "\"#1\"")));
        result = CovPropBlurCenterModels.compare(standing, blur);
        assertNotNull(result);
        assertTrue(result.same(), "fehlen " + result.missing() + ", zusätzlich " + result.extra());
    }

    @Test
    void aShiftedHubIsFound() throws IOException {
        JsonObject blur = core("_blur");
        JsonArray from = named(blur, "hub").getFirst().getAsJsonArray("from");
        from.set(0, new JsonPrimitive(from.get(0).getAsFloat() + 0.25F));
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(core(""), blur);
        assertNotNull(result);
        assertFalse(result.same());
        assertEquals(List.of("hub"), result.extra());
    }

    @Test
    void aBlurModelWithOnlyTheDiscHasNoCenter() throws IOException {
        JsonObject blur = core("_blur");
        for (String part : List.of("hub", "nut", "nut_cap")) {
            remove(blur, part);
        }
        JsonObject standing = core("");
        CovPropBlurCenterModels.Result result = CovPropBlurCenterModels.compare(standing, blur);
        assertNotNull(result);
        assertFalse(result.same());
        assertEquals(count(standing, "hub") + count(standing, "nut") + count(standing, "nut_cap"), result.missing().size(), "fehlen " + result.missing());
        assertTrue(result.missing().contains("hub") && result.missing().contains("nut") && !result.missing().contains("blade"), "fehlen " + result.missing());
        assertEquals(count(standing, "blade"), result.bladeParts());
    }

    static JsonObject folding(boolean blur) {
        String texture = blur ? "#1" : "#0";
        StringBuilder elements = new StringBuilder();
        if (blur) {
            elements.append(element("blur_disc", "[-1.5, 8.38, -1.5]", "[17.5, 8.4, 17.5]", null, "#0")).append(',');
        }
        elements.append(element("hub", "[7.15, 8, 7.65]", "[8.85, 8.8, 8.35]", null, texture)).append(',');
        elements.append(element("hub", "[7.65, 8, 7.15]", "[8.35, 8.8, 7.65]", null, texture)).append(',');
        elements.append(element("hub", "[7.65, 8, 8.35]", "[8.35, 8.8, 8.85]", null, texture)).append(',');
        elements.append(element("hub", "[7.15, 8.1, 7.65]", "[8.85, 8.7, 8.35]", yaw(45, "[8, 8.4, 8]"), texture)).append(',');
        elements.append(element("hub", "[8.5, 8.1, 7.65]", "[9.8, 8.7, 8.35]", yaw(15, "[8, 8.4, 8]"), texture)).append(',');
        elements.append(element("hub", "[8.5, 8.1, 7.65]", "[9.8, 8.7, 8.35]", yaw(195, "[8, 8.4, 8]"), texture)).append(',');
        for (String screw : List.of("[9.45, 8.7, 7.45]", "[6.15, 8.7, 8.35]")) {
            elements.append(element("screw", screw, shift(screw, 0.4F, 0.1F, 0.2F), null, texture)).append(',');
            elements.append(element("screw", screw, shift(screw, 0.4F, 0.1F, 0.2F), yaw(45, shift(screw, 0.2F, 0.05F, 0.1F)), texture)).append(',');
        }
        if (!blur) {
            for (int side : new int[]{0, 180}) {
                elements.append(element("blade", "[9.8, 8.28, 7.48]", "[10.25, 8.5, 8.52]", blade(-15.7F, 15 + side), texture)).append(',');
                elements.append(element("blade", "[10.15, 8.28, 7.23]", "[14.7, 8.5, 8.77]", blade(-12.9F, 18 + side), texture)).append(',');
                elements.append(element("blade", "[14.6, 8.28, 7.44]", "[16.2, 8.5, 8.56]", blade(-11F, 21 + side), texture)).append(',');
                elements.append(element("blade_tip", "[16.2, 8.28, 7.44]", "[16.65, 8.5, 8.56]", blade(-11F, 21 + side), texture)).append(',');
                elements.append(element("blade_tip", "[16.55, 8.28, 7.65]", "[17.5, 8.5, 8.35]", blade(-10.1F, 24 + side), texture)).append(',');
            }
        }
        elements.setLength(elements.length() - 1);
        String textures = blur
                ? "{\"0\": {\"sprite\": \"example:item/part/prop/folding_blur\", \"force_translucent\": true}, \"1\": \"example:item/part/prop/folding\"}"
                : "{\"0\": \"example:item/part/prop/folding\"}";
        return JsonParser.parseString("{\"textures\": " + textures + ", \"elements\": [" + elements + "]}").getAsJsonObject();
    }

    private static String element(String name, String from, String to, @Nullable String rotation, String texture) {
        StringBuilder faces = new StringBuilder();
        for (String face : List.of("north", "east", "south", "west", "up", "down")) {
            faces.append(faces.isEmpty() ? "" : ",").append('"').append(face).append("\": {\"uv\": [0, 0, 1, 1], \"texture\": \"").append(texture).append("\"}");
        }
        return "{\"name\": \"" + name + "\", \"from\": " + from + ", \"to\": " + to + (rotation == null ? "" : ", \"rotation\": " + rotation)
                + ", \"faces\": {" + faces + "}}";
    }

    private static String yaw(float angle, String origin) {
        return String.format(Locale.ROOT, "{\"angle\": %s, \"axis\": \"y\", \"origin\": %s}", angle, origin);
    }

    private static String blade(float pitch, float yaw) {
        return String.format(Locale.ROOT, "{\"x\": %s, \"y\": %s, \"z\": 0, \"origin\": [8, 8.39, 8]}", pitch, yaw);
    }

    private static String shift(String vector, float dx, float dy, float dz) {
        JsonArray array = JsonParser.parseString(vector).getAsJsonArray();
        return String.format(Locale.ROOT, "[%s, %s, %s]", array.get(0).getAsFloat() + dx, array.get(1).getAsFloat() + dy, array.get(2).getAsFloat() + dz);
    }
}

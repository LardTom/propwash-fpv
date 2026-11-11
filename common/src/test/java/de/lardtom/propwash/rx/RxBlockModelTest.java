package de.lardtom.propwash.rx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RxBlockModelTest {
    private static final int LEVELS = 16;

    private static Path assets() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            Path assets = candidate.resolve("src/main/resources/assets/propwash");
            if (Files.isDirectory(assets)) {
                return assets;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static JsonObject read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            fail("nicht lesbar: " + file + " (" + e.getMessage() + ")");
            return new JsonObject();
        }
    }

    private static Path file(String ref, String folder, String extension) {
        String[] parts = ref.split(":", 2);
        if (parts.length != 2 || !parts[0].equals("propwash")) {
            return Path.of("");
        }
        return assets().resolve(folder).resolve(parts[1] + extension);
    }

    private record Model(Map<String, String> textures, List<JsonObject> elements) {
    }

    private static Model model(String ref) {
        if (ref.startsWith("minecraft:")) {
            return new Model(new HashMap<>(), List.of());
        }
        Path path = file(ref, "models", ".json");
        assertTrue(Files.isRegularFile(path), "Modell fehlt: " + ref);
        JsonObject json = read(path);
        Model parent = json.has("parent") ? model(json.get("parent").getAsString()) : new Model(new HashMap<>(), List.of());
        Map<String, String> textures = new HashMap<>(parent.textures());
        if (json.has("textures")) {
            json.getAsJsonObject("textures").entrySet().forEach(e -> textures.put(e.getKey(), e.getValue().getAsString()));
        }
        List<JsonObject> elements = new ArrayList<>(parent.elements());
        if (json.has("elements")) {
            elements.clear();
            json.getAsJsonArray("elements").forEach(e -> elements.add(e.getAsJsonObject()));
        }
        return new Model(textures, elements);
    }

    private static String texture(Map<String, String> textures, String ref) {
        String current = ref;
        for (int depth = 0; depth < 8 && current.startsWith("#"); depth++) {
            String next = textures.get(current.substring(1));
            if (next == null) {
                fail("Textur " + current + " nicht aufgelöst");
            }
            current = next;
        }
        return current;
    }

    private static List<String> resolved(String ref) {
        Model model = model(ref);
        List<String> out = new ArrayList<>();
        for (JsonObject element : model.elements()) {
            JsonObject copy = element.deepCopy();
            copy.remove("name");
            if (copy.has("faces")) {
                for (Map.Entry<String, JsonElement> face : copy.getAsJsonObject("faces").entrySet()) {
                    JsonObject f = face.getValue().getAsJsonObject();
                    f.addProperty("texture", texture(model.textures(), f.get("texture").getAsString()));
                }
            }
            out.add(copy.toString());
        }
        return out;
    }

    private static boolean matches(JsonObject when, Map<String, String> state) {
        if (when.has("OR")) {
            for (JsonElement option : when.getAsJsonArray("OR")) {
                if (matches(option.getAsJsonObject(), state)) {
                    return true;
                }
            }
            return false;
        }
        if (when.has("AND")) {
            for (JsonElement option : when.getAsJsonArray("AND")) {
                if (!matches(option.getAsJsonObject(), state)) {
                    return false;
                }
            }
            return true;
        }
        for (Map.Entry<String, JsonElement> condition : when.entrySet()) {
            String value = state.get(condition.getKey());
            assertTrue(value != null, "unbekannte Eigenschaft " + condition.getKey());
            String expected = condition.getValue().getAsString();
            boolean negate = expected.startsWith("!");
            Set<String> values = Set.of((negate ? expected.substring(1) : expected).split("\\|"));
            if (values.contains(value) == negate) {
                return false;
            }
        }
        return true;
    }

    private record Applied(String model, int y) {
    }

    private static Applied applied(JsonElement apply) {
        JsonObject first = (apply.isJsonArray() ? apply.getAsJsonArray().get(0) : apply).getAsJsonObject();
        return new Applied(first.get("model").getAsString(), first.has("y") ? first.get("y").getAsInt() : 0);
    }

    private static List<String> models(Map<String, String> state) {
        List<String> out = new ArrayList<>();
        for (Applied applied : parts(state)) {
            out.add(applied.model());
        }
        return out;
    }

    private static List<Applied> parts(Map<String, String> state) {
        JsonObject blockstate = read(assets().resolve("blockstates/rx_block.json"));
        List<Applied> out = new ArrayList<>();
        if (blockstate.has("variants")) {
            for (Map.Entry<String, JsonElement> variant : blockstate.getAsJsonObject("variants").entrySet()) {
                JsonObject when = new JsonObject();
                if (!variant.getKey().isEmpty()) {
                    for (String condition : variant.getKey().split(",")) {
                        String[] kv = condition.split("=", 2);
                        when.addProperty(kv[0], kv[1]);
                    }
                }
                if (matches(when, state)) {
                    out.add(applied(variant.getValue()));
                }
            }
        }
        if (blockstate.has("multipart")) {
            for (JsonElement part : blockstate.getAsJsonArray("multipart")) {
                JsonObject p = part.getAsJsonObject();
                if (!p.has("when") || matches(p.getAsJsonObject("when"), state)) {
                    out.add(applied(p.get("apply")));
                }
            }
        }
        assertFalse(out.isEmpty(), "kein Modell für " + state);
        return out;
    }

    private static final String OUTPUT_MODEL = "propwash:block/rx_block_output";
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    private static Map<String, String> state(RxLinkState link, int power) {
        return state(link, power, "north", "front");
    }

    private static Map<String, String> state(RxLinkState link, int power, String facing, String output) {
        Map<String, String> state = new LinkedHashMap<>();
        state.put("link", link.getSerializedName());
        state.put("power", Integer.toString(power));
        state.put("facing", facing);
        state.put("output", output);
        return state;
    }

    private static List<String> look(RxLinkState link, int power) {
        return look(state(link, power));
    }

    private static List<String> look(Map<String, String> state) {
        List<String> out = new ArrayList<>();
        for (Applied part : parts(state)) {
            for (String element : resolved(part.model())) {
                out.add(rotated(element, part.y()));
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private static double[] turn(double x, double z, int y) {
        return switch (Math.floorMod(y, 360)) {
            case 90 -> new double[]{16.0 - z, x};
            case 180 -> new double[]{16.0 - x, 16.0 - z};
            case 270 -> new double[]{z, 16.0 - x};
            default -> new double[]{x, z};
        };
    }

    private static String turnFace(String face, int y) {
        List<String> ring = List.of("north", "east", "south", "west");
        int index = ring.indexOf(face);
        return index < 0 ? face : ring.get(Math.floorMod(index + Math.floorMod(y, 360) / 90, 4));
    }

    private static String rotated(String element, int y) {
        JsonObject json = JsonParser.parseString(element).getAsJsonObject();
        if (Math.floorMod(y, 360) == 0) {
            return json.toString();
        }
        double[] from = box(json, "from");
        double[] to = box(json, "to");
        double[] a = turn(from[0], from[2], y);
        double[] b = turn(to[0], to[2], y);
        JsonObject out = json.deepCopy();
        out.add("from", JsonParser.parseString("[" + Math.min(a[0], b[0]) + "," + from[1] + "," + Math.min(a[1], b[1]) + "]"));
        out.add("to", JsonParser.parseString("[" + Math.max(a[0], b[0]) + "," + to[1] + "," + Math.max(a[1], b[1]) + "]"));
        if (json.has("faces")) {
            JsonObject faces = new JsonObject();
            for (Map.Entry<String, JsonElement> face : json.getAsJsonObject("faces").entrySet()) {
                faces.add(turnFace(face.getKey(), y), face.getValue());
            }
            out.add("faces", faces);
        }
        return out.toString();
    }

    private static double[] box(JsonObject element, String key) {
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[i] = element.getAsJsonArray(key).get(i).getAsDouble();
        }
        return out;
    }

    private static List<double[]> outputCentres(Map<String, String> state) {
        List<double[]> out = new ArrayList<>();
        for (Applied part : parts(state)) {
            if (!part.model().equals(OUTPUT_MODEL)) {
                continue;
            }
            for (String element : resolved(part.model())) {
                JsonObject json = JsonParser.parseString(rotated(element, part.y())).getAsJsonObject();
                double[] from = box(json, "from");
                double[] to = box(json, "to");
                out.add(new double[]{(from[0] + to[0]) / 2.0 - 8.0, (from[2] + to[2]) / 2.0 - 8.0});
            }
        }
        return out;
    }

    private static String side(double[] centre) {
        if (Math.abs(centre[0]) > Math.abs(centre[1])) {
            return centre[0] > 0 ? "east" : "west";
        }
        return centre[1] > 0 ? "south" : "north";
    }

    private static Set<String> textures(List<String> look) {
        Set<String> out = new HashSet<>();
        for (String element : look) {
            JsonObject faces = JsonParser.parseString(element).getAsJsonObject().getAsJsonObject("faces");
            faces.entrySet().forEach(face -> out.add(face.getValue().getAsJsonObject().get("texture").getAsString()));
        }
        return out;
    }

    private static int glow(List<String> look) {
        int sum = 0;
        for (String element : look) {
            JsonObject json = JsonParser.parseString(element).getAsJsonObject();
            if (json.has("light_emission")) {
                sum += json.get("light_emission").getAsInt();
            }
        }
        return sum;
    }

    @Test
    void everyPowerLevelHasItsOwnLook() {
        for (RxLinkState link : RxLinkState.values()) {
            Map<List<String>, Integer> seen = new HashMap<>();
            for (int power = 0; power < LEVELS; power++) {
                Integer before = seen.put(look(link, power), power);
                if (before != null) {
                    fail("link=" + link.getSerializedName() + ": Stärke " + power + " sieht aus wie Stärke " + before
                            + " (Modelle " + models(state(link, power)) + ")");
                }
            }
        }
    }

    @Test
    void glowRisesWithPower() {
        for (RxLinkState link : RxLinkState.values()) {
            for (int power = 1; power < LEVELS; power++) {
                int lower = glow(look(link, power - 1));
                int higher = glow(look(link, power));
                assertTrue(higher > lower, "link=" + link.getSerializedName() + ": Leuchten bei Stärke " + power + " (" + higher
                        + ") nicht heller als bei " + (power - 1) + " (" + lower + ")");
            }
        }
    }

    @Test
    void ledStillFollowsLink() {
        for (int power = 0; power < LEVELS; power++) {
            Set<List<String>> looks = new HashSet<>();
            for (RxLinkState link : RxLinkState.values()) {
                looks.add(look(link, power));
            }
            assertEquals(RxLinkState.values().length, looks.size(), "Stärke " + power + ": LED-Zustände nicht unterscheidbar");
        }
    }

    @Test
    void everyLevelHasItsOwnTextureForResourcePacks() {
        Map<Integer, Set<String>> perLevel = new HashMap<>();
        for (int power = 0; power < LEVELS; power++) {
            perLevel.put(power, textures(look(RxLinkState.NONE, power)));
        }
        for (int power = 0; power < LEVELS; power++) {
            Set<String> own = new HashSet<>(perLevel.get(power));
            for (int other = 0; other < LEVELS; other++) {
                if (other != power) {
                    own.removeAll(perLevel.get(other));
                }
            }
            assertFalse(own.isEmpty(), "Stärke " + power + " hat keine eigene Textur, ein Ressourcenpaket kann sie nicht einzeln ändern");
            for (String texture : own) {
                assertTrue(Files.isRegularFile(file(texture, "textures", ".png")), "Textur fehlt: " + texture);
            }
        }
    }

    @Test
    void everyReferencedTextureExists() {
        for (RxLinkState link : RxLinkState.values()) {
            for (int power = 0; power < LEVELS; power++) {
                for (String texture : textures(look(link, power))) {
                    assertTrue(Files.isRegularFile(file(texture, "textures", ".png")), "Textur fehlt: " + texture);
                }
            }
        }
    }

    @Test
    void itemShowsThePlacedBlockWithoutSignal() {
        JsonObject item = read(assets().resolve("items/rx_block.json"));
        String ref = item.getAsJsonObject("model").get("model").getAsString();
        List<String> itemLook = resolved(ref);
        itemLook.sort(String::compareTo);
        assertEquals(look(RxLinkState.NONE, 0), itemLook, "Item-Modell weicht vom gesetzten Block (ungebunden, Stärke 0) ab");
        assertNotEquals(look(RxLinkState.NONE, 15), itemLook);
    }

    @Test
    void outputPointsToTheFacingSide() {
        for (String facing : FACINGS) {
            for (RxLinkState link : RxLinkState.values()) {
                List<double[]> centres = outputCentres(state(link, 7, facing, "front"));
                assertEquals(1, centres.size(), "facing=" + facing + ", link=" + link.getSerializedName() + ": genau ein Ausgang erwartet");
                double[] centre = centres.getFirst();
                assertEquals(facing, side(centre), "Ausgang zeigt nicht nach " + facing);
                assertTrue(Math.max(Math.abs(centre[0]), Math.abs(centre[1])) >= 5.0, "Ausgang nicht am Rand: " + centre[0] + "/" + centre[1]);
            }
        }
    }

    @Test
    void everyFacingTurnsTheWholeBlock() {
        Set<List<String>> looks = new HashSet<>();
        for (String facing : FACINGS) {
            looks.add(look(state(RxLinkState.LINKED, 9, facing, "front")));
        }
        assertEquals(FACINGS.size(), looks.size(), "die vier Richtungen sehen nicht verschieden aus");
        List<String> north = look(state(RxLinkState.LINKED, 9, "north", "front"));
        for (String facing : FACINGS) {
            int y = 90 * FACINGS.indexOf(facing);
            List<String> turned = new ArrayList<>();
            for (String element : north) {
                turned.add(rotated(element, y));
            }
            turned.sort(String::compareTo);
            assertEquals(turned, look(state(RxLinkState.LINKED, 9, facing, "front")), "facing=" + facing + " ist nicht der gedrehte Block");
        }
    }

    @Test
    void oldBlocksWithoutDirectionShowOutputsOnAllFourSides() {
        for (String facing : FACINGS) {
            Set<String> sides = new HashSet<>();
            for (double[] centre : outputCentres(state(RxLinkState.LINKED, 4, facing, "all"))) {
                sides.add(side(centre));
            }
            assertEquals(Set.copyOf(FACINGS), sides, "output=all, facing=" + facing);
        }
        JsonObject blockstate = read(assets().resolve("blockstates/rx_block.json"));
        assertTrue(blockstate.toString().contains("\"output\""), "Blockzustand ohne Eigenschaft output");
    }

    @Test
    void outputTextureShowsAnArrow() {
        List<String> faces = resolved(OUTPUT_MODEL);
        assertFalse(faces.isEmpty(), "Ausgangsmodell ohne Elemente");
        Set<String> textures = textures(faces);
        assertEquals(1, textures.size(), "Ausgang mit mehreren Texturen: " + textures);
        assertTrue(Files.isRegularFile(file(textures.iterator().next(), "textures", ".png")), "Textur fehlt: " + textures);
    }
}

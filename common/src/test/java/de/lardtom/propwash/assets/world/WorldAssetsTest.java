package de.lardtom.propwash.assets.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.WorkbenchLayout;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorldAssetsTest {
    private static final double PIXELS_PER_BLOCK = 8.0;
    private static final double FRAME = 0.5;
    private static final double DEPTH = 0.5;
    private static final Map<String, double[]> GATES = Map.of(
            "small", new double[]{3.0, 3.0},
            "medium", new double[]{4.0, 4.0},
            "large", new double[]{6.0, 4.0});
    private static final List<String> GOGGLES = List.of("goggles_analog", "goggles_digital", "goggles_creative");
    private static final double[][] REMOTE_PIVOTS = {{4.0, 6.5, 10.25}, {12.0, 6.5, 10.25}};
    private static final double[][] REMOTE_PLATES = {{2.25, 4.75, 10.0, 5.75, 8.25, 10.25}, {10.25, 4.75, 10.0, 13.75, 8.25, 10.25}};
    private static final List<String> REMOTE_DISPLAY = List.of("thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand", "gui", "ground", "fixed", "head");
    private static final Map<String, Integer> FACINGS = Map.of("north", 0, "east", 90, "south", 180, "west", 270);
    private static final List<String> CHARGER_STATES = List.of("idle", "charging", "done");
    private static final List<String> BAY_STATES = List.of("empty", "charging", "full");
    private static final int CHARGER_BAYS = 4;
    private static final int[] CHARGER_SLOTS = {17, 59, 101, 143};
    private static final List<String> SLOT_SPRITES = List.of("frame", "stack", "motor", "prop", "video", "battery", "drone",
            "accessory_top", "accessory_bottom");

    private static Path resources() {
        for (Path candidate : List.of(Path.of("common/src/main/resources"), Path.of("src/main/resources"))) {
            if (Files.isDirectory(candidate.resolve("assets/propwash"))) {
                return candidate;
            }
        }
        fail("Ressourcen nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static Path assets() {
        return resources().resolve("assets/propwash");
    }

    private static JsonObject read(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "fehlt: " + file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static BufferedImage image(Path file, int width, int height) throws IOException {
        assertTrue(Files.isRegularFile(file), "fehlt: " + file);
        BufferedImage img = ImageIO.read(file.toFile());
        assertNotNull(img, "kein Bild: " + file);
        assertEquals(width, img.getWidth(), "Breite von " + file);
        assertEquals(height, img.getHeight(), "Höhe von " + file);
        return img;
    }

    private static Path modelFile(String ref) {
        assertTrue(ref.startsWith("propwash:"), "fremdes Modell " + ref);
        return assets().resolve("models/" + ref.substring("propwash:".length()) + ".json");
    }

    private static JsonObject checkModel(String ref) throws IOException {
        JsonObject model = read(modelFile(ref));
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> entry : model.getAsJsonObject("textures").entrySet()) {
                String value = entry.getValue().getAsString();
                if (value.startsWith("#") || !value.startsWith("propwash:")) {
                    continue;
                }
                String path = value.substring("propwash:".length());
                assertTrue(path.startsWith("item/") || path.startsWith("block/"),
                        ref + ": Textur " + value + " liegt in keinem Modell-Atlas");
                assertTrue(Files.isRegularFile(assets().resolve("textures/" + path + ".png")), ref + ": Textur fehlt " + value);
            }
        }
        if (model.has("elements")) {
            for (JsonElement element : model.getAsJsonArray("elements")) {
                JsonObject object = element.getAsJsonObject();
                for (String key : List.of("from", "to")) {
                    for (JsonElement value : object.getAsJsonArray(key)) {
                        double v = value.getAsDouble();
                        assertTrue(v >= -16.0 && v <= 32.0, ref + ": " + key + " außerhalb -16..32");
                    }
                }
                for (Map.Entry<String, JsonElement> face : object.getAsJsonObject("faces").entrySet()) {
                    JsonObject faceObject = face.getValue().getAsJsonObject();
                    if (faceObject.has("uv")) {
                        for (JsonElement value : faceObject.getAsJsonArray("uv")) {
                            double v = value.getAsDouble();
                            assertTrue(v >= 0.0 && v <= 16.0, ref + ": uv außerhalb 0..16");
                        }
                    }
                    String texture = faceObject.get("texture").getAsString();
                    assertTrue(texture.startsWith("#") && model.getAsJsonObject("textures").has(texture.substring(1)),
                            ref + ": Texturvariable " + texture + " fehlt");
                }
            }
        }
        return model;
    }

    private static void checkItem(String id) throws IOException {
        JsonObject definition = read(assets().resolve("items/" + id + ".json"));
        for (String ref : itemModels(definition.getAsJsonObject("model"), id)) {
            checkModel(ref);
        }
    }

    private static List<String> itemModels(JsonObject node, String id) {
        String type = node.get("type").getAsString();
        List<String> refs = new ArrayList<>();
        if (type.equals("minecraft:model")) {
            refs.add(node.get("model").getAsString());
        } else if (type.equals("minecraft:select")) {
            for (JsonElement entry : node.getAsJsonArray("cases")) {
                refs.addAll(itemModels(entry.getAsJsonObject().getAsJsonObject("model"), id));
            }
            refs.addAll(itemModels(node.getAsJsonObject("fallback"), id));
        } else if (type.equals("minecraft:composite")) {
            for (JsonElement entry : node.getAsJsonArray("models")) {
                refs.addAll(itemModels(entry.getAsJsonObject(), id));
            }
        } else if (type.equals("minecraft:special")) {
            refs.add(node.get("base").getAsString());
        } else {
            fail(id + ": unerwarteter Item-Modell-Typ " + type);
        }
        return refs;
    }

    @Test
    void workbenchBlockAndItem() throws IOException {
        JsonObject blockstate = read(assets().resolve("blockstates/drone_workbench.json"));
        JsonObject variants = blockstate.getAsJsonObject("variants");
        assertEquals(1, variants.size());
        checkModel(variants.getAsJsonObject("").get("model").getAsString());
        checkItem("drone_workbench");
        for (String face : List.of("top", "front", "side", "bottom")) {
            image(assets().resolve("textures/block/drone_workbench_" + face + ".png"), 16, 16);
        }
    }

    @Test
    void gogglesAreWornAsThreeDimensionalHeadModel() throws IOException {
        for (String id : GOGGLES) {
            checkItem(id);
            image(assets().resolve("textures/item/" + id + ".png"), 16, 16);
            image(assets().resolve("textures/item/" + id + "_worn.png"), 64, 64);
            JsonObject model = read(assets().resolve("items/" + id + ".json")).getAsJsonObject("model");
            assertEquals("minecraft:select", model.get("type").getAsString(), id);
            assertEquals("minecraft:display_context", model.get("property").getAsString(), id);
            boolean head = false;
            for (JsonElement entry : model.getAsJsonArray("cases")) {
                JsonObject object = entry.getAsJsonObject();
                boolean headCase = false;
                for (JsonElement when : object.getAsJsonArray("when")) {
                    headCase |= when.getAsString().equals("head");
                }
                if (headCase) {
                    head = true;
                    assertEquals("propwash:item/" + id + "_worn", object.getAsJsonObject("model").get("model").getAsString(), id);
                }
            }
            assertTrue(head, id + ": kein Modell für den Kopf-Kontext");
            assertEquals("propwash:item/" + id, model.getAsJsonObject("fallback").get("model").getAsString(), id);
            JsonObject worn = checkModel("propwash:item/" + id + "_worn");
            double[] box = bounds(worn.getAsJsonArray("elements"));
            double unit = 1.6;
            assertTrue(box[2] < 8.0 - 6.0 * unit, id + ": Brillenkasten ragt nicht vor das Gesicht");
            assertTrue(box[0] < 8.0 - 4.0 * unit && box[3] > 8.0 + 4.0 * unit && box[5] > 8.0 + 4.0 * unit,
                    id + ": Kopfband umschließt den Kopf nicht");
            assertTrue(box[4] > 8.0 + 4.0 * unit, id + ": nichts ragt über den Kopf (Antenne)");
            assertTrue(!Files.exists(assets().resolve("equipment/" + id + ".json")), id + ": altes Equipment-Asset noch vorhanden");
        }
    }

    @Test
    void gateItemsAndCourseTool() throws IOException {
        for (String size : GATES.keySet()) {
            checkItem("gate_" + size);
            image(assets().resolve("textures/item/gate_" + size + ".png"), 16, 16);
        }
        checkItem("course_tool");
        image(assets().resolve("textures/item/course_tool.png"), 16, 16);
    }

    @Test
    void gateModelsMatchPublishedDimensions() throws IOException {
        for (Map.Entry<String, double[]> gate : GATES.entrySet()) {
            double width = gate.getValue()[0];
            double height = gate.getValue()[1];
            JsonObject frame = checkModel("propwash:gate/" + gate.getKey() + "_frame");
            double[] box = bounds(frame.getAsJsonArray("elements"));
            assertEquals(8.0 - width / 2.0 * PIXELS_PER_BLOCK, box[0], 1e-6, gate.getKey() + " minX");
            assertEquals(8.0 + width / 2.0 * PIXELS_PER_BLOCK, box[3], 1e-6, gate.getKey() + " maxX");
            assertEquals(0.0, box[1], 1e-6, gate.getKey() + " minY");
            assertEquals(height * PIXELS_PER_BLOCK, box[4], 1e-6, gate.getKey() + " maxY");
            assertEquals(8.0, (box[2] + box[5]) / 2.0, 1e-6, gate.getKey() + " mittig in Z");
            double inner = (width / 2.0 - FRAME) * PIXELS_PER_BLOCK;
            for (JsonElement element : frame.getAsJsonArray("elements")) {
                double[] b = bounds(List.of(element));
                boolean overlapsOpening = b[0] < 8.0 + inner && b[3] > 8.0 - inner
                        && b[1] < (height - FRAME) * PIXELS_PER_BLOCK && b[4] > FRAME * PIXELS_PER_BLOCK;
                assertTrue(!overlapsOpening, gate.getKey() + ": Element ragt in die Öffnung");
                if (element.getAsJsonObject().get("name").getAsString().startsWith("post")) {
                    assertEquals(DEPTH * PIXELS_PER_BLOCK, b[5] - b[2], 1e-6, gate.getKey() + " Pfostentiefe");
                }
            }
            JsonObject start = checkModel("propwash:gate/" + gate.getKey() + "_start_frame");
            assertEquals(frame.get("elements"), start.get("elements"), gate.getKey() + ": Start-/Ziel-Rahmen hat dieselbe Form");
            assertEquals("propwash:item/gate/start_frame", start.getAsJsonObject("textures").get("frame").getAsString());
            JsonObject banner = checkModel("propwash:gate/" + gate.getKey() + "_banner");
            double[] bannerBox = bounds(banner.getAsJsonArray("elements"));
            assertEquals(box[0], bannerBox[0], 1e-6, gate.getKey() + " Banner minX");
            assertEquals(box[3], bannerBox[3], 1e-6, gate.getKey() + " Banner maxX");
            assertEquals(0.0, bannerBox[1], 1e-6, gate.getKey() + " Banner minY");
            assertEquals(PIXELS_PER_BLOCK, bannerBox[4], 1e-6, gate.getKey() + " Banner maxY");
            assertEquals(8.0, (bannerBox[2] + bannerBox[5]) / 2.0, 1e-6, gate.getKey() + " Banner mittig in Z");
            JsonObject lights = checkModel("propwash:gate/" + gate.getKey() + "_lights");
            for (JsonElement element : lights.getAsJsonArray("elements")) {
                JsonObject object = element.getAsJsonObject();
                assertEquals(15, object.get("light_emission").getAsInt());
                for (Map.Entry<String, JsonElement> face : object.getAsJsonObject("faces").entrySet()) {
                    assertEquals(0, face.getValue().getAsJsonObject().get("tintindex").getAsInt(), "Lichter brauchen tintindex 0");
                }
            }
        }
        image(assets().resolve("textures/item/gate/frame.png"), 64, 64);
        image(assets().resolve("textures/item/gate/lights.png"), 32, 32);
        image(assets().resolve("textures/item/gate/start_frame.png"), 64, 64);
        image(assets().resolve("textures/item/gate/banner.png"), 32, 32);
    }

    private static double[] bounds(Iterable<JsonElement> elements) {
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (JsonElement element : elements) {
            JsonArray from = element.getAsJsonObject().getAsJsonArray("from");
            JsonArray to = element.getAsJsonObject().getAsJsonArray("to");
            for (int i = 0; i < 3; i++) {
                box[i] = Math.min(box[i], from.get(i).getAsDouble());
                box[i + 3] = Math.max(box[i + 3], to.get(i).getAsDouble());
            }
        }
        return box;
    }

    @Test
    void guiTextureSlotSpritesAndIcons() throws IOException {
        BufferedImage gui = image(assets().resolve("textures/gui/drone_workbench.png"), 512, 256);
        int slot = gui.getRGB(80 + 8, 64 + 8);
        List<int[]> slots = new ArrayList<>(List.of(
                new int[]{80, 64}, new int[]{80, 42}, new int[]{34, 30}, new int[]{126, 30}, new int[]{34, 98}, new int[]{126, 98},
                new int[]{10, 30}, new int[]{150, 30}, new int[]{10, 98}, new int[]{150, 98}, new int[]{80, 20}, new int[]{80, 87},
                new int[]{WorkbenchLayout.RESULT_X, WorkbenchLayout.RESULT_Y}, new int[]{WorkbenchLayout.SALVAGE_X, WorkbenchLayout.SALVAGE_Y}));
        for (int part = 0; part < BuildSlots.COUNT; part++) {
            slots.add(new int[]{WorkbenchLayout.partX(part), WorkbenchLayout.partY(part)});
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slots.add(new int[]{8 + column * 18, 142 + row * 18});
            }
        }
        for (int column = 0; column < 9; column++) {
            slots.add(new int[]{8 + column * 18, 200});
        }
        for (int[] s : slots) {
            assertEquals(slot, gui.getRGB(s[0], s[1]), "Slot bei " + s[0] + "," + s[1]);
            assertEquals(slot, gui.getRGB(s[0] + 15, s[1] + 15), "Slot bei " + s[0] + "," + s[1]);
        }
        assertEquals(0, gui.getRGB(400, 200) >>> 24, "außerhalb 312x224 transparent");
        for (String sprite : SLOT_SPRITES) {
            image(assets().resolve("textures/gui/sprites/gui/slot/" + sprite + ".png"), 16, 16);
        }
        image(assets().resolve("icon.png"), 256, 256);
        image(resources().resolve("pack.png"), 128, 128);
    }

    @Test
    void remoteItemDefinitionMatchesContract() throws IOException {
        checkItem("remote");
        JsonObject model = read(assets().resolve("items/remote.json")).getAsJsonObject("model");
        assertEquals("minecraft:select", model.get("type").getAsString());
        assertEquals("minecraft:display_context", model.get("property").getAsString());
        JsonObject flat = model.getAsJsonArray("cases").get(0).getAsJsonObject();
        List<String> contexts = new ArrayList<>();
        flat.getAsJsonArray("when").forEach(when -> contexts.add(when.getAsString()));
        assertEquals(List.of("gui", "ground", "fixed", "on_shelf"), contexts);
        assertEquals("propwash:item/remote", flat.getAsJsonObject("model").get("model").getAsString());
        JsonObject fallback = model.getAsJsonObject("fallback");
        assertEquals("minecraft:composite", fallback.get("type").getAsString());
        JsonArray parts = fallback.getAsJsonArray("models");
        assertEquals(2, parts.size());
        assertEquals("propwash:item/remote_body", parts.get(0).getAsJsonObject().get("model").getAsString());
        JsonObject special = parts.get(1).getAsJsonObject();
        assertEquals("minecraft:special", special.get("type").getAsString());
        assertEquals("propwash:item/remote_body", special.get("base").getAsString());
        assertEquals("propwash:remote_gimbals", special.getAsJsonObject("model").get("type").getAsString());
    }

    @Test
    void remoteModelsSplitSticksFromBody() throws IOException {
        JsonObject full = checkModel("propwash:item/remote");
        JsonObject body = checkModel("propwash:item/remote_body");
        image(assets().resolve("textures/item/remote.png"), 64, 64);
        BufferedImage gimbal = image(assets().resolve("textures/entity/remote/gimbal.png"), 16, 16);
        for (int x = 0; x < 16; x++) {
            assertEquals(0xFFBB00, gimbal.getRGB(x, 8) & 0xFFFFFF, "gimbal.png: Amber-Ring in Zeile 8");
        }
        assertEquals(0x8C949E, gimbal.getRGB(8, 3) & 0xFFFFFF, "gimbal.png: Metall-Schaft");
        for (JsonObject model : List.of(full, body)) {
            assertEquals("[64,64]", model.get("texture_size").toString());
            for (String key : REMOTE_DISPLAY) {
                assertTrue(model.getAsJsonObject("display").has(key), "display." + key + " fehlt");
            }
        }
        assertEquals(full.get("display"), body.get("display"), "display-Blöcke müssen gleich sein");
        List<String> fullNames = names(full);
        List<String> bodyNames = names(body);
        assertEquals(fullNames.stream().filter(n -> !n.startsWith("gimbal_left_") && !n.startsWith("gimbal_right_") && !n.startsWith("switch_"))
                .toList(), bodyNames, "remote_body = remote ohne Knüppel und Schalter");
        assertEquals(List.of("switch_0", "switch_1", "switch_2", "switch_3"), fullNames.stream().filter(n -> n.startsWith("switch_")).toList(),
                "remote: vier Schalter als Vorlage für das bewegte Modell");
        assertEquals(0x5F6770, gimbal.getRGB(6, 0) & 0xFFFFFF, "gimbal.png: Schalter-Fuß");
        assertEquals(0xFFBB00, gimbal.getRGB(7, 2) & 0xFFFFFF, "gimbal.png: Schalter-Spitze");
        assertEquals(0x5F6770, gimbal.getRGB(10, 1) & 0xFFFFFF, "gimbal.png: Taste");
        assertEquals(0xFFBB00, gimbal.getRGB(13, 1) & 0xFFFFFF, "gimbal.png: Amber-Taste");
        String[] sides = {"left", "right"};
        for (int i = 0; i < 2; i++) {
            double[] pivot = REMOTE_PIVOTS[i];
            double[] plate = REMOTE_PLATES[i];
            assertEquals((plate[0] + plate[3]) / 2.0, pivot[0], 1e-6);
            assertEquals((plate[1] + plate[4]) / 2.0, pivot[1], 1e-6);
            assertEquals(plate[5], pivot[2], 1e-6);
            JsonObject group = group(full, "gimbal_" + sides[i]);
            assertNotNull(group, "Gruppe gimbal_" + sides[i] + " fehlt");
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(pivot[axis], group.getAsJsonArray("origin").get(axis).getAsDouble(), 1e-6, "Drehpunkt gimbal_" + sides[i]);
            }
            List<String> children = new ArrayList<>();
            group.getAsJsonArray("children").forEach(c -> children.add(fullNames.get(c.getAsInt())));
            assertEquals(List.of("gimbal_" + sides[i] + "_shaft", "gimbal_" + sides[i] + "_cap"), children);
            double[] shaft = bounds(List.of(element(full, "gimbal_" + sides[i] + "_shaft")));
            assertEquals(pivot[0], (shaft[0] + shaft[3]) / 2.0, 1e-6);
            assertEquals(pivot[1], (shaft[1] + shaft[4]) / 2.0, 1e-6);
            assertEquals(pivot[2], shaft[2], 1e-6);
            assertTrue(group(body, "gimbal_" + sides[i]) == null, "remote_body ohne Knüppel-Gruppe");
            double[] stick = bounds(List.of(element(full, "gimbal_" + sides[i] + "_shaft"), element(full, "gimbal_" + sides[i] + "_cap")));
            for (JsonElement element : body.getAsJsonArray("elements")) {
                double[] b = bounds(List.of(element));
                boolean overlap = b[0] < stick[3] && b[3] > stick[0] && b[1] < stick[4] && b[4] > stick[1] && b[2] < stick[5] && b[5] > stick[2];
                assertTrue(!overlap, "remote_body: " + element.getAsJsonObject().get("name") + " ragt in die Knüppel-Box");
            }
        }
        JsonObject display = element(full, "display");
        assertEquals(6, display.get("light_emission").getAsInt());
        assertEquals(List.of("south"), List.copyOf(display.getAsJsonObject("faces").keySet()));
    }

    @Test
    void chargerBlockstateModelsAndItem() throws IOException {
        JsonArray parts = read(assets().resolve("blockstates/battery_charger.json")).getAsJsonArray("multipart");
        assertEquals(FACINGS.size() * (CHARGER_STATES.size() + CHARGER_BAYS * BAY_STATES.size()), parts.size());
        List<String> seen = new ArrayList<>();
        for (JsonElement part : parts) {
            JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
            JsonObject apply = part.getAsJsonObject().getAsJsonObject("apply");
            String facing = when.get("facing").getAsString();
            int y = apply.has("y") ? apply.get("y").getAsInt() : 0;
            assertEquals(FACINGS.get(facing).intValue(), y, when + " y");
            String model;
            if (when.has("state")) {
                model = "propwash:block/battery_charger_" + when.get("state").getAsString();
            } else {
                String bay = when.keySet().stream().filter(k -> k.startsWith("bay_")).findFirst().orElseThrow();
                model = "propwash:block/battery_charger_" + bay + "_" + when.get(bay).getAsString();
            }
            assertEquals(model, apply.get("model").getAsString(), when.toString());
            seen.add(facing + " " + model);
        }
        assertEquals(parts.size(), seen.stream().distinct().count(), "doppelte Teile im Blockzustand");
        for (String state : CHARGER_STATES) {
            JsonObject model = checkModel("propwash:block/battery_charger_" + state);
            assertEquals("minecraft:block/block", model.get("parent").getAsString());
            double[] box = bounds(model.getAsJsonArray("elements"));
            double[] limit = {1.0, 0.0, 1.4, 15.0, 8.0, 14.0};
            for (int i = 0; i < 3; i++) {
                assertTrue(box[i] >= limit[i] - 1e-6 && box[i + 3] <= limit[i + 3] + 1e-6, state + ": Bounding-Box außerhalb der Code-Form");
            }
            int lit = 0;
            for (JsonElement element : model.getAsJsonArray("elements")) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("light_emission")) {
                    assertEquals(12, object.get("light_emission").getAsInt(), state);
                    lit++;
                }
            }
            assertEquals(state.equals("idle") ? 0 : 1, lit, state + ": leuchtende Elemente (Display)");
            image(assets().resolve("textures/block/battery_charger_front_" + state + ".png"), 32, 32);
        }
        for (int bay = 0; bay < CHARGER_BAYS; bay++) {
            for (String bayState : BAY_STATES) {
                String ref = "propwash:block/battery_charger_bay_" + bay + "_" + bayState;
                JsonObject model = checkModel(ref);
                int leds = 0;
                int packs = 0;
                for (JsonElement element : model.getAsJsonArray("elements")) {
                    JsonObject object = element.getAsJsonObject();
                    String name = object.get("name").getAsString();
                    if (name.equals("led_" + bay)) {
                        leds++;
                        assertEquals(bayState.equals("empty") ? 0 : 12, object.has("light_emission") ? object.get("light_emission").getAsInt() : 0, ref);
                    } else if (name.equals("pack_" + bay)) {
                        packs++;
                    }
                }
                assertEquals(1, leds, ref + ": LED");
                assertEquals(bayState.equals("empty") ? 0 : 1, packs, ref + ": Akku im Schacht");
            }
        }
        for (String face : List.of("top", "side", "bottom", "pack")) {
            image(assets().resolve("textures/block/battery_charger_" + face + ".png"), 32, 32);
        }
        checkItem("battery_charger");
        JsonObject item = read(assets().resolve("items/battery_charger.json")).getAsJsonObject("model");
        assertEquals("propwash:item/battery_charger", item.get("model").getAsString());
        JsonObject icon = read(assets().resolve("models/item/battery_charger.json"));
        assertEquals("propwash:block/battery_charger_inventory", icon.get("parent").getAsString());
        checkModel("propwash:block/battery_charger_inventory");
    }

    @Test
    void chargerAndServiceGuiTextures() throws IOException {
        int slot = image(assets().resolve("textures/gui/drone_workbench.png"), 512, 256).getRGB(80 + 8, 64 + 8);
        BufferedImage charger = image(assets().resolve("textures/gui/battery_charger.png"), 256, 256);
        checkPanel(charger, 176, 207);
        checkInventory(charger, slot, 8, 125, 183);
        for (int x : CHARGER_SLOTS) {
            assertEquals(slot, charger.getRGB(x, 30), "Ladeschacht-Slot bei " + x);
            assertEquals(slot, charger.getRGB(x + 15, 45), "Ladeschacht-Slot bei " + x);
            assertEquals(slot, charger.getRGB(x + 8, 38), "Ladeschacht-Slot bei " + x + " ohne eingebackenes Symbol");
            int fill = charger.getRGB(x + 2, 52);
            for (int px = x + 2; px < x + 14; px++) {
                for (int py = 52; py < 74; py++) {
                    assertEquals(fill, charger.getRGB(px, py), "Balkenrinne bei " + x + " innen nicht leer");
                }
            }
            int rim = charger.getRGB(x + 1, 51);
            assertTrue(rim != fill, "Balkenrinne bei " + x + " ohne Rahmen");
            for (int px = x + 1; px < x + 15; px++) {
                assertEquals(rim, charger.getRGB(px, 51), "Balkenrahmen oben bei " + x);
                assertEquals(rim, charger.getRGB(px, 74), "Balkenrahmen unten bei " + x);
            }
        }
        BufferedImage service = image(assets().resolve("textures/gui/drone_service.png"), 256, 256);
        checkPanel(service, 236, 244);
        checkInventory(service, slot, 38, 164, 222);
        assertEquals(0xFFBB00, service.getRGB(12, 125) & 0xFFFFFF, "Kamera-Feld ohne Amber-Linie");
        assertEquals(slot, service.getRGB(131, 81), "Akku-Slot");
        assertEquals(slot, service.getRGB(146, 96), "Akku-Slot");
        assertEquals(slot, service.getRGB(139, 89), "Akku-Slot ohne eingebackenes Symbol");
        assertEquals(0xFFBB00, service.getRGB(128, 78) & 0xFFFFFF, "Akku-Slot ohne Amber-Ring");
        assertEquals(0xFFBB00, service.getRGB(159, 69) & 0xFFFFFF, "Info-Feld ohne Amber-Linie");
        for (int[] prop : new int[][]{{17, 33}, {95, 33}, {17, 75}, {95, 75}}) {
            assertEquals(slot, service.getRGB(prop[0], prop[1]), "Prop-Slot " + prop[0] + ", " + prop[1]);
            assertEquals(slot, service.getRGB(prop[0] + 15, prop[1] + 15), "Prop-Slot " + prop[0] + ", " + prop[1]);
        }
    }

    private static void checkPanel(BufferedImage gui, int width, int height) {
        assertEquals(0, gui.getRGB(0, 0) >>> 24, "Ecke (0,0) transparent");
        assertTrue((gui.getRGB(width / 2, height / 2) >>> 24) != 0, "Panel-Mitte gefüllt");
        for (int[] p : List.of(new int[]{width, 0}, new int[]{0, height}, new int[]{width + 5, height + 5}, new int[]{255, 255})) {
            assertEquals(0, gui.getRGB(p[0], p[1]) >>> 24, "außerhalb " + width + "x" + height + " transparent");
        }
    }

    private static void checkInventory(BufferedImage gui, int slot, int left, int top, int hotbar) {
        for (int column = 0; column < 9; column++) {
            int x = left + column * 18;
            for (int y : new int[]{top, top + 18, top + 36, hotbar}) {
                assertEquals(slot, gui.getRGB(x, y), "Inventar-Slot bei " + x + "," + y);
                assertEquals(slot, gui.getRGB(x + 15, y + 15), "Inventar-Slot bei " + x + "," + y);
            }
        }
    }

    private static List<String> names(JsonObject model) {
        List<String> names = new ArrayList<>();
        model.getAsJsonArray("elements").forEach(e -> names.add(e.getAsJsonObject().get("name").getAsString()));
        return names;
    }

    private static JsonObject element(JsonObject model, String name) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            if (e.getAsJsonObject().get("name").getAsString().equals(name)) {
                return e.getAsJsonObject();
            }
        }
        fail("Element " + name + " fehlt");
        return null;
    }

    private static JsonObject group(JsonObject model, String name) {
        if (!model.has("groups")) {
            return null;
        }
        for (JsonElement g : model.getAsJsonArray("groups")) {
            if (g.getAsJsonObject().get("name").getAsString().equals(name)) {
                return g.getAsJsonObject();
            }
        }
        return null;
    }
}

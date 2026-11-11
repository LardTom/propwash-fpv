package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.StackType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlugAnchorsTest {
    private static final Path MODELS = LayoutFromDefinitionTest.resources().resolve("assets/propwash/models/part");
    private static final float EPS = 1.0E-3F;

    static JsonObject model(String category, String key) throws IOException {
        return JsonParser.parseString(Files.readString(MODELS.resolve(category).resolve(key + ".json"))).getAsJsonObject();
    }

    static boolean exists(String category, String key) {
        return Files.isRegularFile(MODELS.resolve(category).resolve(key + ".json"));
    }

    private static JsonObject elements(String... boxes) {
        StringBuilder json = new StringBuilder("{\"elements\":[");
        for (int i = 0; i < boxes.length; i++) {
            json.append(i == 0 ? "" : ",").append(boxes[i]);
        }
        return JsonParser.parseString(json.append("]}").toString()).getAsJsonObject();
    }

    private static String box(String name, float x0, float y0, float z0, float x1, float y1, float z1) {
        return "{\"name\":\"" + name + "\",\"from\":[" + (8.0F + x0 / 8.0F) + "," + (8.0F + y0 / 8.0F) + "," + (8.0F + z0 / 8.0F)
                + "],\"to\":[" + (8.0F + x1 / 8.0F) + "," + (8.0F + y1 / 8.0F) + "," + (8.0F + z1 / 8.0F) + "],\"faces\":{}}";
    }

    private static String scaledItem(String model, float scale) {
        float shift = 0.5F * (1.0F - scale);
        return "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\",\"transformation\":{\"translation\":[" + shift + ","
                + shift + "," + shift + "],\"left_rotation\":[0,0,0,1],\"scale\":[" + scale + "," + scale + "," + scale
                + "],\"right_rotation\":[0,0,0,1]}}}";
    }

    private static void item(Path pack, String path, String json) throws IOException {
        Path file = pack.resolve("assets/propwash/items/drone_render/" + path + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }

    private static DroneRenderResources.Plugs plugsWith(Path overlay) {
        PackResources shipped = new PathPackResources(new PackLocationInfo("propwash", Component.literal("propwash"), PackSource.BUILT_IN,
                Optional.empty()), LayoutFromDefinitionTest.resources());
        PackResources pack = new PathPackResources(new PackLocationInfo("scaled", Component.literal("scaled"), PackSource.DEFAULT,
                Optional.empty()), overlay);
        try (MultiPackResourceManager manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(shipped, pack))) {
            return DroneRenderResources.readPlugs(manager);
        }
    }

    private static Vector3f block(float xMm, float yMm, float zMm) {
        return new Vector3f(xMm / 8.0F + 8.0F, yMm / 8.0F + 8.0F, zMm / 8.0F + 8.0F).div(16.0F);
    }

    private static PlugAnchors.Box union(List<PlugAnchors.Box> boxes) {
        PlugAnchors.Box out = boxes.getFirst();
        for (PlugAnchors.Box box : boxes) {
            out = out.union(box);
        }
        return out;
    }

    @Test
    void everyBuiltinBatteryHidesItsPlugsAndLeadsOnly() throws IOException {
        int checked = 0;
        for (BatteryType battery : BatteryType.values()) {
            if (!battery.builtin()) {
                continue;
            }
            List<String> keys = new ArrayList<>(List.of(battery.key()));
            if (exists("battery", battery.key() + PartModel.UNDER_SUFFIX)) {
                keys.add(battery.key() + PartModel.UNDER_SUFFIX);
            }
            for (String key : keys) {
                JsonObject model = model("battery", key);
                List<PlugAnchors.Box> plug = PlugAnchors.batteryPlug(model);
                boolean bt = PlugAnchors.count(model, PlugAnchors.BT_CONNECTOR) > 0;
                assertEquals(bt ? 3 : 6, plug.size(),
                        key + (bt ? ": Buchse und zwei Kabel" : ": Buchse, Griffleiste, zwei Kabel, Balancer-Kabel und -Stecker"));
                for (PlugAnchors.Element element : PlugAnchors.elements(model)) {
                    boolean expected = PlugAnchors.PLUG_ELEMENTS.contains(element.name()) || element.name().equals(PlugAnchors.BALANCE_PLUG)
                            || element.name().equals(PlugAnchors.WIRE);
                    assertEquals(expected, plug.contains(element.box()), key + ": " + element.name() + " " + element.box());
                }
                checked++;
            }
        }
        assertEquals(7, checked, "5 Akkus und 2 hängende Varianten");
    }

    @Test
    void theHiddenPartOfThe1300RunsFromThePackToTheSocketFace() throws IOException {
        PlugAnchors.Box hidden = union(PlugAnchors.batteryPlug(model("battery", "b6s_1300")));
        assertEquals(-8.0F, hidden.minX(), EPS);
        assertEquals(14.5F, hidden.maxX(), EPS, "Balancer-Stecker rechts neben der Buchse");
        assertEquals(39.0F, hidden.minZ(), EPS, "Kabel beginnt an der Stirnseite des Packs (78 mm lang)");
        assertEquals(68.5F, hidden.maxZ(), EPS, "Steckseite der Buchse");
        assertEquals(26.0F, hidden.minY(), EPS);
        assertTrue(hidden.maxY() <= 34.8F + EPS, "nur Buchse und Griffleiste über dem Pack");
    }

    @Test
    void stacksWithAPlugShipAMountedVariantWithTwoPigtails() throws IOException {
        int mounted = 0;
        for (StackType stack : StackType.values()) {
            if (!stack.builtin()) {
                continue;
            }
            JsonObject base = model("stack", stack.key());
            if (!PlugAnchors.hasPlug(base)) {
                continue;
            }
            String key = stack.key() + PartModel.MOUNTED_SUFFIX;
            assertTrue(exists("stack", key), key + " fehlt");
            JsonObject variant = model("stack", key);
            assertFalse(PlugAnchors.hasPlug(variant), key + ": Stecker-Element im Modell");
            assertEquals(2, PlugAnchors.count(variant, PlugAnchors.PIGTAIL), key);
            assertEquals(0, PlugAnchors.count(variant, PlugAnchors.WIRE), key);
            int plug = PlugAnchors.count(base, PlugAnchors.CONNECTOR) + PlugAnchors.count(base, PlugAnchors.CONNECTOR_GRIP)
                    + PlugAnchors.count(base, PlugAnchors.BT_CONNECTOR);
            assertEquals(PlugAnchors.elements(base).size() - plug, PlugAnchors.elements(variant).size(), key + ": nur der Stecker fehlt");
            List<PlugAnchors.Box> wires = new ArrayList<>();
            for (PlugAnchors.Element element : PlugAnchors.elements(base)) {
                if (element.name().equals(PlugAnchors.WIRE)) {
                    wires.add(element.box());
                }
            }
            assertEquals(wires, PlugAnchors.pigtails(variant), key + ": die Kabel des Grundmodells, nur umbenannt");
            assertEquals(model("stack", stack.key()).get("textures"), variant.get("textures"), key + ": eigene Textur");
            mounted++;
        }
        assertEquals(4, mounted, "alle 4 eingebauten Stacks haben einen Stecker");
    }

    @Test
    void pigtailsNeedExactlyTwoLeadsAndNoPlug() {
        String red = box(PlugAnchors.PIGTAIL, 2.0F, 5.0F, 10.0F, 4.0F, 7.0F, 30.0F);
        String black = box(PlugAnchors.PIGTAIL, -4.0F, 5.0F, 10.0F, -2.0F, 7.0F, 28.0F);
        assertEquals(2, PlugAnchors.pigtails(elements(red, black)).size());
        assertTrue(PlugAnchors.pigtails(elements(red)).isEmpty());
        assertTrue(PlugAnchors.pigtails(elements(red, black, red)).isEmpty());
        assertTrue(PlugAnchors.pigtails(elements(red, black, box(PlugAnchors.CONNECTOR, -8, 2, 30, 8, 10, 45))).isEmpty());
        assertTrue(PlugAnchors.pigtails(elements(red, box(PlugAnchors.PIGTAIL, 0, 0, 0, 0, 2, 20))).isEmpty(), "Kabel ohne Querschnitt");
        assertTrue(PlugAnchors.batteryPlug(elements(red, black)).isEmpty());
    }

    @Test
    void onlyWiresTouchingAPlugBelongToIt() {
        String socket = box(PlugAnchors.CONNECTOR, -8, 2, 30, 8, 10, 45);
        String grip = box(PlugAnchors.CONNECTOR_GRIP, -7, 10, 31, 7, 10.8F, 38);
        String lead = box(PlugAnchors.WIRE, -3, 5, 10, -1, 7, 30);
        String balance = box(PlugAnchors.WIRE, 12, 5, 10, 13, 6, 20);
        String balancePlug = box(PlugAnchors.BALANCE_PLUG, 11, 4, 20, 14, 7, 26);
        String loose = box(PlugAnchors.WIRE, -15, -10, -20, -14, -9, 0);
        String pack = box("pack", -18, -20, -30, 18, 12, 10);
        List<PlugAnchors.Box> plug = PlugAnchors.batteryPlug(elements(pack, socket, grip, lead, balance, balancePlug, loose));
        assertEquals(5, plug.size(), "Buchse, Griffleiste, Kabel bis zur Buchse, Balancer-Stecker und sein Kabel");
        assertTrue(plug.contains(PlugAnchors.elements(elements(lead)).getFirst().box()));
        assertTrue(plug.contains(PlugAnchors.elements(elements(balance)).getFirst().box()));
        assertFalse(plug.contains(PlugAnchors.elements(elements(loose)).getFirst().box()), "Kabel ohne Stecker bleibt");
        assertFalse(plug.contains(PlugAnchors.elements(elements(pack)).getFirst().box()));
        assertTrue(PlugAnchors.batteryPlug(elements(pack, grip, lead)).isEmpty(), "Griffleiste allein ist keine Buchse");
        assertTrue(PlugAnchors.batteryPlug(elements(pack, balancePlug, balance)).isEmpty(), "ohne Buchse bleibt der Akku, wie er ist");
        assertEquals(2, PlugAnchors.batteryPlug(elements(box(PlugAnchors.BT_CONNECTOR, -3, 1, 20, 3, 4, 25), box(PlugAnchors.WIRE, -1, 2, 10, 0, 3, 20)))
                .size());
    }

    @Test
    void rotatedElementsUseTheirTurnedCorners() {
        JsonObject frame = JsonParser.parseString("{\"elements\":[{\"name\":\"rear_plate\",\"from\":[7,8,8],\"to\":[9,8.25,12],"
                + "\"rotation\":{\"angle\":90,\"axis\":\"y\",\"origin\":[8,8,8]},\"faces\":{}}]}").getAsJsonObject();
        PlugAnchors.Box box = PlugAnchors.elements(frame).getFirst().box();
        assertEquals(8.0F, box.maxZ(), EPS);
        assertEquals(32.0F, box.maxX(), EPS);
        assertEquals(-8.0F, box.minZ(), EPS);
    }

    @Test
    void theMaskHidesOnlyFacesThatLieCompletelyInsideAHiddenElement() {
        PlugMask mask = new PlugMask(List.of(new PlugAnchors.Box(-8, 26, 53, 8, 34, 68.5F)));
        assertTrue(mask.covers(block(-8, 26, 68.5F), block(8, 26, 68.5F), block(8, 34, 68.5F), block(-8, 34, 68.5F)), "Steckseite der Buchse");
        assertTrue(mask.covers(block(-8, 34, 53), block(8, 34, 53), block(8, 34, 68.5F), block(-8, 34, 68.5F)), "Oberseite der Buchse");
        assertFalse(mask.covers(block(-18, 0, 39), block(18, 0, 39), block(18, 34, 39), block(-18, 34, 39)), "Stirnseite des Packs bleibt");
        assertFalse(mask.covers(block(-8, 26, 53), block(8, 26, 53), block(8, 26, 70), block(-8, 26, 70)), "Fläche, die über die Buchse hinausreicht");
        assertTrue(PlugMask.NONE.isEmpty());
        assertFalse(PlugMask.NONE.covers(block(0, 30, 60), block(0, 30, 60), block(0, 30, 60), block(0, 30, 60)));
    }

    @Test
    void masksAreReadFromTheModelsOfBatteriesAndMountedStacks(@TempDir Path pack) {
        DroneRenderResources.Plugs plugs = plugsWith(pack);
        assertEquals(6, plugs.batteryPlugs().get(PartModel.of(BatteryType.B6S_1300)).boxes().size());
        assertEquals(6, plugs.batteryPlugs().get(PartModel.batteryUnder(BatteryType.B6S_1300)).boxes().size());
        assertEquals(3, plugs.batteryPlugs().get(PartModel.of(BatteryType.B1S_300_HV)).boxes().size(), "BT2.0 ohne Griffleiste");
        for (StackType stack : List.of(StackType.STACK_30, StackType.AIO_20, StackType.AIO_WHOOP, StackType.CREATIVE_FC)) {
            assertEquals(2, plugs.cables().get(PartModel.stackMounted(stack)).boxes().size(), stack.key());
        }
        assertTrue(plugs.fallbacks().isEmpty(), "eingebaute Stacks fallen nicht zurück: " + plugs.fallbacks());
    }

    @Test
    void scaledRenderItemsKeepTheMasksInModelSpace(@TempDir Path pack) throws IOException {
        item(pack, "battery/b6s_1300", scaledItem("propwash:part/battery/b6s_1300", 1.5F));
        item(pack, "stack/stack_30", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"propwash:part/stack/stack_30\"}}");
        item(pack, "stack/stack_30_mounted", scaledItem("propwash:part/stack/stack_30_mounted", 1.25F));
        DroneRenderResources.Plugs plugs = plugsWith(pack);
        assertEquals(PlugAnchors.batteryPlug(model("battery", "b6s_1300")), plugs.batteryPlugs().get(PartModel.of(BatteryType.B6S_1300)).boxes(),
                "Minecraft übergibt die Flächen vor der transformation");
        assertEquals(PlugAnchors.pigtails(model("stack", "stack_30_mounted")), plugs.cables().get(PartModel.stackMounted(StackType.STACK_30)).boxes());
    }
}

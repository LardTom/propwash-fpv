package de.lardtom.propwash.bando;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandoMapDataTest {
    private static final String MAP = "propwash:bando_map";
    private static final String TRADE = "propwash:cartographer/3/emerald_and_compass_bando_map";
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int MAX_SEARCH_BLOCKS = 12000;

    private static Path resources() {
        return BandoDataTest.root().resolve("common/src/main/resources");
    }

    private static JsonArray tradeFunctions() throws IOException {
        return BandoDataTest.read(BandoDataTest.data().resolve("villager_trade/cartographer/3/emerald_and_compass_bando_map.json"))
                .getAsJsonArray("given_item_modifier");
    }

    private static List<String> values(Path tag) throws IOException {
        JsonObject json = BandoDataTest.read(tag);
        assertFalse(json.has("replace") && json.get("replace").getAsBoolean(), tag + " darf Vanilla nicht ersetzen");
        List<String> values = new ArrayList<>();
        json.getAsJsonArray("values").forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private static BufferedImage png(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path + " fehlt");
        try (InputStream in = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image, path + " ist kein PNG");
            return image;
        }
    }

    @Test
    void tradeAndLootShareOneExplorerMapFunction() throws IOException {
        JsonArray trade = tradeFunctions();
        JsonObject modifier = BandoDataTest.read(BandoDataTest.data().resolve("item_modifier/bando_map.json"));
        assertEquals("minecraft:sequence", modifier.get("type").getAsString());
        assertEquals(trade, modifier.getAsJsonArray("functions"), "Handel und Beute zeichnen verschiedene Karten");
        assertEquals(2, trade.size());
        JsonObject explore = trade.get(0).getAsJsonObject();
        assertEquals("minecraft:exploration_map", explore.get("type").getAsString());
        assertEquals("#propwash:bandos", explore.get("destination").getAsString());
        assertEquals("propwash:bando", explore.get("decoration").getAsString());
        assertTrue(!explore.has("skip_existing_chunks") || explore.get("skip_existing_chunks").getAsBoolean(),
                "die Karte soll zum nächsten unerkundeten Bando zeigen");
        int zoom = explore.get("zoom").getAsInt();
        assertTrue(zoom >= 1 && zoom <= 3, "Maßstab " + zoom);
        JsonObject placement = BandoDataTest.read(BandoDataTest.data().resolve("worldgen/structure_set/bandos.json")).getAsJsonObject("placement");
        int cell = placement.get("spacing").getAsInt() * BLOCKS_PER_CHUNK;
        int radius = explore.get("search_radius").getAsInt();
        assertTrue(radius >= 2, "Suchradius " + radius + " Zellen findet zu selten einen Bando");
        assertTrue(radius * cell <= MAX_SEARCH_BLOCKS, "Suchradius " + radius * cell + " Blöcke: Suche zu lang, Ziel zu weit");
        JsonObject filtered = trade.get(1).getAsJsonObject();
        assertEquals("minecraft:filtered", filtered.get("type").getAsString());
        assertTrue(filtered.getAsJsonObject("item_filter").getAsJsonObject("predicates").has("minecraft:map_id"));
        assertEquals("minecraft:discard", filtered.getAsJsonObject("on_fail").get("type").getAsString(),
                "ohne Bando (Bauwerke aus) darf keine leere Karte entstehen");
    }

    @Test
    void journeymanCartographerSellsTheMap() throws IOException {
        JsonObject trade = BandoDataTest.read(BandoDataTest.data().resolve("villager_trade/cartographer/3/emerald_and_compass_bando_map.json"));
        assertEquals(MAP, trade.getAsJsonObject("gives").get("id").getAsString());
        assertEquals("minecraft:emerald", trade.getAsJsonObject("wants").get("id").getAsString());
        int emeralds = trade.getAsJsonObject("wants").get("count").getAsInt();
        assertTrue(emeralds >= 5 && emeralds <= 16, emeralds + " Smaragde");
        assertEquals("minecraft:compass", trade.getAsJsonObject("additional_wants").get("id").getAsString());
        Path tag = resources().resolve("data/minecraft/tags/villager_trade/cartographer/level_3.json");
        assertEquals(List.of(TRADE), values(tag));
        Path vanilla = BandoDataTest.vanilla().resolve("data/minecraft");
        JsonObject set = BandoDataTest.read(vanilla.resolve("trade_set/cartographer/level_3.json"));
        assertEquals("#minecraft:cartographer/level_3", set.get("trades").getAsString(), "Vanilla-Handelsset liest das Tag nicht mehr");
        JsonObject monument = BandoDataTest.read(vanilla.resolve("villager_trade/cartographer/3/emerald_and_compass_ocean_monument_map.json"));
        assertEquals(monument.keySet(), trade.keySet(), "Aufbau wie die Vanilla-Entdeckerkarte");
    }

    @Test
    void chestsHideTheMapRarelyAndAlwaysWithTheFunction() throws IOException {
        JsonArray pools = BandoDataTest.read(BandoDataTest.data().resolve("loot_table/chests/bando.json")).getAsJsonArray("pools");
        JsonObject mapPool = pools.get(pools.size() - 1).getAsJsonObject();
        assertEquals(1, mapPool.get("rolls").getAsInt());
        assertTrue(chance(mapPool) >= 0.05 && chance(mapPool) <= 0.15, "Bando-Truhe: " + chance(mapPool));
        JsonObject cartographer = BandoDataTest.read(BandoDataTest.data().resolve("loot_table/inject/village_cartographer.json"))
                .getAsJsonArray("pools").get(0).getAsJsonObject();
        assertTrue(chance(cartographer) >= 0.05 && chance(cartographer) <= 0.2, "Kartografen-Truhe: " + chance(cartographer));
        int maps = 0;
        try (Stream<Path> files = Files.walk(BandoDataTest.data().resolve("loot_table"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                for (JsonElement pool : BandoDataTest.read(file).getAsJsonArray("pools")) {
                    for (JsonElement element : pool.getAsJsonObject().getAsJsonArray("entries")) {
                        JsonObject entry = element.getAsJsonObject();
                        if (entry.has("name") && MAP.equals(entry.get("name").getAsString())) {
                            assertTrue(entry.has("modifier") && MAP.equals(entry.get("modifier").getAsString()),
                                    file + ": Bando-Karte ohne Funktion " + MAP);
                            maps++;
                        }
                    }
                }
            }
        }
        assertEquals(2, maps, "Bando-Karte in Bando- und Kartografen-Truhen");
    }

    private static double chance(JsonObject pool) {
        int total = 0;
        int map = 0;
        for (JsonElement element : pool.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            int weight = entry.get("weight").getAsInt();
            total += weight;
            if (entry.has("name") && MAP.equals(entry.get("name").getAsString())) {
                map += weight;
            }
        }
        return map / (double) total;
    }

    @Test
    void mapIsClonableAndHasItsAssets() throws IOException {
        assertTrue(values(resources().resolve("data/minecraft/tags/item/clonable_maps.json")).contains(MAP));
        Path assets = resources().resolve("assets/propwash");
        assertTrue(Files.isRegularFile(assets.resolve("items/bando_map.json")));
        BufferedImage icon = png(assets.resolve("textures/item/bando_map.png"));
        assertEquals(16, icon.getWidth());
        assertEquals(16, icon.getHeight());
        BufferedImage decoration = png(assets.resolve("textures/map/decorations/bando.png"));
        assertEquals(8, decoration.getWidth());
        assertEquals(8, decoration.getHeight());
        JsonObject atlas = BandoDataTest.read(BandoDataTest.vanilla().resolve("assets/minecraft/atlases/map_decorations.json"));
        assertTrue(atlas.toString().contains("\"map/decorations\""), "Atlas der Kartensymbole liest map/decorations nicht mehr");
        for (String language : List.of("de_de", "en_us")) {
            JsonObject lang = BandoDataTest.read(BandoDataTest.root().resolve("common/src/lang/bando/" + language + ".json"));
            assertTrue(lang.has("item.propwash.bando_map"), language + ": Name der Bando-Karte");
            assertTrue(lang.has("propwash.guide.scene.bandos.step.map.text"), language + ": Flugschule");
        }
    }
}

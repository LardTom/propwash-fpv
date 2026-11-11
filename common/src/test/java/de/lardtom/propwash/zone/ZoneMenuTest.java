package de.lardtom.propwash.zone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneMenuTest {
    private static final ZoneOwner ALICE = new ZoneOwner(UUID.fromString("00000000-0000-4000-8000-00000000a11c"), "Alice");
    private static final int FLOOR = -64;
    private static final ZoneOwner BOB = new ZoneOwner(UUID.fromString("00000000-0000-4000-8000-000000000b0b"), "Bob");

    private static Zone zone(int id, String name, ZoneBox box) {
        return new Zone(id, Zone.OVERWORLD, name, ZoneShape.BOX, box, 0, false, ZoneLimits.DEFAULT_TRANSITION_TENTHS, Map.of());
    }

    private static Zone zone(int id, String name) {
        return zone(id, name, new ZoneBox(0, 64, 0, 16, 72, 16));
    }

    @Test
    void playersSeeOnlyTheirOwnZonesOperatorsSeeAll() {
        Zone admin = zone(1, "Admin");
        Zone alice = zone(2, "Alice").withOwner(ALICE);
        Zone bob = zone(3, "Bob").withOwner(BOB);
        List<Zone> all = List.of(admin, alice, bob);
        assertEquals(List.of(admin, alice, bob), ZoneMenu.listed(all, true, ALICE.id()));
        assertEquals(List.of(alice), ZoneMenu.listed(all, false, ALICE.id()));
        assertEquals(List.of(bob), ZoneMenu.listed(all, false, BOB.id()));
        assertEquals(List.of(), ZoneMenu.listed(all, false, null));
        assertEquals(List.of(), ZoneMenu.listed(all, false, UUID.fromString("00000000-0000-4000-8000-000000000c0c")));
        assertTrue(ZoneMenu.listed(true, admin, null));
        assertFalse(ZoneMenu.listed(false, admin, ALICE.id()));
    }

    @Test
    void actionsFollowTheRightsOfTheZone() {
        Zone admin = zone(1, "Admin");
        Zone alice = zone(2, "Alice").withOwner(ALICE);
        assertEquals(List.of(ZoneMenu.Action.SHOW, ZoneMenu.Action.EDIT, ZoneMenu.Action.DELETE), ZoneMenu.actions(false, true, false, alice, ALICE.id()));
        assertEquals(List.of(ZoneMenu.Action.SHOW), ZoneMenu.actions(false, false, false, alice, ALICE.id()));
        assertEquals(List.of(ZoneMenu.Action.SHOW, ZoneMenu.Action.JUMP, ZoneMenu.Action.EDIT, ZoneMenu.Action.DELETE),
                ZoneMenu.actions(false, true, true, alice, ALICE.id()));
        assertEquals(List.of(), ZoneMenu.actions(false, true, true, alice, BOB.id()));
        assertEquals(List.of(), ZoneMenu.actions(false, true, true, admin, ALICE.id()));
        assertEquals(List.of(ZoneMenu.Action.SHOW, ZoneMenu.Action.EDIT, ZoneMenu.Action.DELETE), ZoneMenu.actions(true, false, false, alice, BOB.id()));
        assertEquals(List.of(ZoneMenu.Action.SHOW, ZoneMenu.Action.JUMP, ZoneMenu.Action.EDIT, ZoneMenu.Action.DELETE),
                ZoneMenu.actions(true, false, true, admin, null));
    }

    @Test
    void jumpLandsOnTheFirstFloorInsideTheZoneElseOnTheFirstFloorBelow() {
        Zone zone = zone(5, "Platz", new ZoneBox(10, 64, 20, 22, 72, 30));
        Set<Long> floors = Set.of(key(16, 66, 25), key(16, 70, 25));
        assertArrayEquals(new double[]{16.5, 66, 25.5}, ZoneMenu.jumpTarget(zone, FLOOR, (x, y, z) -> floors.contains(key(x, y, z))));
        Set<Long> below = Set.of(key(16, 40, 25), key(16, -20, 25));
        assertArrayEquals(new double[]{16.5, 40, 25.5}, ZoneMenu.jumpTarget(zone, FLOOR, (x, y, z) -> below.contains(key(x, y, z))));
        Set<Long> deep = Set.of(key(16, -1, 25));
        assertArrayEquals(new double[]{16.5, -1, 25.5}, ZoneMenu.jumpTarget(zone, FLOOR, (x, y, z) -> deep.contains(key(x, y, z))));
        Zone negative = zone(6, "Minus", new ZoneBox(-21, 10, -11, -9, 14, -3));
        assertArrayEquals(new double[]{-14.5, 12, -6.5}, ZoneMenu.jumpTarget(negative, FLOOR, (x, y, z) -> x == -15 && y == 12 && z == -7));
    }

    @Test
    void jumpFromAZoneHighInTheSkyLandsOnTheGroundFarBelowInsteadOfInTheAir() {
        Zone sky = zone(7, "Schwerelos", new ZoneBox(10, 150, 20, 22, 160, 30));
        assertArrayEquals(new double[]{16.5, 64, 25.5}, ZoneMenu.jumpTarget(sky, FLOOR, (x, y, z) -> x == 16 && y == 64 && z == 25));
    }

    @Test
    void jumpWithoutAnyFloorInOrBelowTheZoneHasNoTarget() {
        Zone sky = zone(8, "Leere", new ZoneBox(10, 150, 20, 22, 160, 30));
        assertNull(ZoneMenu.jumpTarget(sky, FLOOR, (x, y, z) -> false));
        assertNull(ZoneMenu.jumpTarget(sky, FLOOR, (x, y, z) -> y > 160));
        assertNull(ZoneMenu.jumpTarget(sky, FLOOR, (x, y, z) -> y < FLOOR));
        assertArrayEquals(new double[]{16.5, FLOOR, 25.5}, ZoneMenu.jumpTarget(sky, FLOOR, (x, y, z) -> y <= FLOOR));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | (long) z & 0xFFFFF;
    }

    @Test
    void arrowPointsRelativeToTheView() {
        assertEquals("↑", ZoneMenu.arrow(0, 10, 0.0F));
        assertEquals("↓", ZoneMenu.arrow(0, -10, 0.0F));
        assertEquals("←", ZoneMenu.arrow(10, 0, 0.0F));
        assertEquals("→", ZoneMenu.arrow(-10, 0, 0.0F));
        assertEquals("↗", ZoneMenu.arrow(-10, 10, 0.0F));
        assertEquals("↑", ZoneMenu.arrow(10, 0, -90.0F));
        assertEquals("↑", ZoneMenu.arrow(-10, 0, 450.0F));
        assertEquals("↓", ZoneMenu.arrow(0, 10, 180.0F));
        assertEquals("↑", ZoneMenu.arrow(0, 0, 37.0F));
    }

    @Test
    void jumpPacketSurvivesTheWire() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ZoneJumpC2S.CODEC.encode(buf, new ZoneJumpC2S(4711));
        assertEquals(new ZoneJumpC2S(4711), ZoneJumpC2S.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void menuTextsExistInBothLanguagesWithTheSamePlaceholders() throws Exception {
        JsonObject de = load("de_de");
        JsonObject en = load("en_us");
        for (String key : List.of("menu.tab", "menu.all", "menu.own", "menu.show", "menu.jump", "menu.empty_own", "menu.deleting", "menu.footer",
                "menu.footer_read_only", "menu.intro.admin", "menu.intro.player", "menu.intro.locked", "menu.intro.unknown", "menu.about",
                "focus.distance", "focus.inside", "jump.done", "jump.denied", "jump.elsewhere", "jump.no_ground")) {
            String full = ZoneText.OPEN_KEY + key;
            assertTrue(de.has(full), full);
            assertTrue(en.has(full), full);
            assertEquals(placeholders(de.get(full).getAsString()), placeholders(en.get(full).getAsString()), full);
        }
        assertEquals("Zonen", de.get(ZoneText.OPEN_KEY + "menu.tab").getAsString());
        assertEquals("Hinspringen", de.get(ZoneText.OPEN_KEY + "menu.jump").getAsString());
        assertFalse(de.has(ZoneText.OPEN_KEY + "marker.none_here"));
    }

    @Test
    void everyChoiceOfShowZonesHasATextInBothLanguages() throws Exception {
        JsonObject de = load("de_de");
        JsonObject en = load("en_us");
        for (ZoneView.Show show : ZoneView.Show.values()) {
            String full = ZoneText.OPEN_KEY + "menu.view.show." + show.key();
            assertTrue(de.has(full), full);
            assertTrue(en.has(full), full);
        }
        String key = ZoneText.OPEN_KEY + "menu.view.show.";
        assertEquals("Mit Werkzeug oder im Flug", de.get(key + "tool_or_flight").getAsString());
        assertEquals("With tool or in flight", en.get(key + "tool_or_flight").getAsString());
        assertTrue(de.get(key + "tooltip").getAsString().contains("Mit Werkzeug oder im Flug:"));
        assertTrue(en.get(key + "tooltip").getAsString().contains("With tool or in flight:"));
    }

    private static int placeholders(String text) {
        int count = 0;
        for (int i = text.indexOf("%s"); i >= 0; i = text.indexOf("%s", i + 2)) {
            count++;
        }
        return count;
    }

    private static JsonObject load(String language) throws Exception {
        Path path = Path.of("common/src/lang/zones-open", language + ".json");
        if (!Files.exists(path)) {
            path = Path.of("src/lang/zones-open", language + ".json");
        }
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}

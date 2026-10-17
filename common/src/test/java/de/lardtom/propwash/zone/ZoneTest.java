package de.lardtom.propwash.zone;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneTest {
    static final String OVERWORLD = Zone.OVERWORLD;

    static Zone box(int id, String name, int priority, Map<String, Integer> overrides) {
        return new Zone(id, OVERWORLD, name, ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), priority, false, 5, overrides);
    }

    @Test
    void boxContainsItsFacesAndNothingOutside() {
        Zone zone = box(1, "a", 0, Map.of());
        assertTrue(zone.contains(0, 0, 0));
        assertTrue(zone.contains(10, 10, 10));
        assertTrue(zone.contains(5, 5, 5));
        assertFalse(zone.contains(10.01, 5, 5));
        assertFalse(zone.contains(5, -0.01, 5));
    }

    @Test
    void cylinderIsRoundAndUsesTheSmallerHorizontalSize() {
        ZoneBox box = ZoneBox.cylinder(0.5, 64, 0.5, 4, 6);
        Zone zone = Zone.create(OVERWORLD, "rund", ZoneShape.CYLINDER, box);
        assertEquals(4.0, zone.box().radius(), 1.0E-9);
        assertTrue(zone.contains(0.5, 64, 4.4));
        assertFalse(zone.contains(3.8, 64, 3.8), "Ecke des Quaders liegt außerhalb des Zylinders");
        assertFalse(zone.contains(0.5, 70.1, 0.5));
        assertEquals(1.0, zone.box().distanceSq(ZoneShape.CYLINDER, 5.5, 64, 0.5), 1.0E-9);
    }

    @Test
    void blockCornersAreInclusive() {
        ZoneBox box = ZoneBox.blocks(3, 64, -2, 0, 60, 1);
        assertEquals(new ZoneBox(0, 60, -2, 4, 65, 2), box);
        assertEquals(4.0, box.sizeX());
    }

    @Test
    void cylinderShapeSquaresTheBox() {
        Zone zone = box(1, "a", 0, Map.of()).withBox(new ZoneBox(0, 0, 0, 10, 4, 6)).withShape(ZoneShape.CYLINDER);
        assertEquals(10.0, zone.box().sizeX(), 1.0E-9);
        assertEquals(10.0, zone.box().sizeZ(), 1.0E-9);
        assertEquals(3.0, zone.box().centerZ(), 1.0E-9);
    }

    @Test
    void namesAreCleanedAndLimited() {
        assertEquals("Zero-G Tunnel", Zone.cleanName("  Zero-G\u0007 Tunnel§ "));
        assertEquals(ZoneLimits.MAX_NAME, Zone.cleanName("x".repeat(80)).length());
        assertEquals("Übungsplatz", Zone.cleanName("Übungsplatz"));
    }

    @Test
    void overridesKeepOnlyValidPhysicsRulesInCatalogOrder() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        raw.put("battery_scale", 0);
        raw.put("gravity_scale", 2000);
        raw.put("wind", 3);
        raw.put("drone_trails", 1);
        raw.put("unknown", 1);
        Zone zone = box(1, "a", 0, raw);
        assertEquals(List.of("wind", "battery_scale"), List.copyOf(zone.overrides().keySet()));
        assertTrue(zone.overrides(WorldRuleSpecs.BATTERY_SCALE));
        assertFalse(zone.overrides(WorldRuleSpecs.GRAVITY_SCALE));
        assertTrue(zone.changesPhysics());
        assertTrue(zone.countsAsAssist());
        assertFalse(zone.withRaceLegal(true).countsAsAssist());
        assertFalse(box(1, "a", 0, Map.of("gravity_scale", 100)).changesPhysics(), "Standardwert ändert die Physik nicht");
    }

    @Test
    void codecRoundTripAndDefaults() {
        Zone zone = box(7, "Zero-G", 3, Map.of("gravity_scale", 0)).withRaceLegal(true).withTransition(12).withShape(ZoneShape.CYLINDER);
        JsonElement json = Zone.CODEC.encodeStart(JsonOps.INSTANCE, zone).getOrThrow();
        assertEquals(zone, Zone.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        Zone minimal = Zone.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"id\":2,\"name\":\"alt\",\"box\":[0,0,0,1,2,3]}")).getOrThrow();
        assertEquals(OVERWORLD, minimal.dimension());
        assertEquals(ZoneShape.BOX, minimal.shape());
        assertEquals(0, minimal.priority());
        assertFalse(minimal.raceLegal());
        assertEquals(ZoneLimits.DEFAULT_TRANSITION_TENTHS, minimal.transitionTenths());
        assertTrue(minimal.overrides().isEmpty());
    }

    @Test
    void wireRoundTrip() {
        Zone zone = box(9, "Halle Ä", -4, Map.of("wind", 0, "battery_scale", 250)).withDimension("minecraft:the_nether");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ZoneWire.write(buf, zone);
        assertEquals(zone, ZoneWire.read(buf));
        ZonesS2C payload = new ZonesS2C(true, OVERWORLD, List.of(zone, box(10, "b", 0, Map.of())));
        FriendlyByteBuf sync = new FriendlyByteBuf(Unpooled.buffer());
        ZonesS2C.CODEC.encode(sync, payload);
        assertEquals(payload, ZonesS2C.CODEC.decode(sync));
        ZoneEditC2S edit = new ZoneEditC2S(ZoneEditC2S.Action.DELETE, zone);
        FriendlyByteBuf c2s = new FriendlyByteBuf(Unpooled.buffer());
        ZoneEditC2S.CODEC.encode(c2s, edit);
        assertEquals(edit, ZoneEditC2S.CODEC.decode(c2s));
        FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
        bad.writeByte(9);
        assertThrows(RuntimeException.class, () -> ZoneEditC2S.CODEC.decode(bad));
    }

    @Test
    void checksRejectBrokenZones() {
        Zone ok = box(0, "neu", 0, Map.of());
        assertNull(ZoneCheck.problem(ok));
        assertEquals(ZoneCheck.NAME_EMPTY, ZoneCheck.problem(ok.withName("  ")));
        assertEquals(ZoneCheck.TOO_SMALL, ZoneCheck.problem(ok.withBox(new ZoneBox(0, 0, 0, 0.5, 4, 4))));
        assertEquals(ZoneCheck.TOO_LARGE, ZoneCheck.problem(ok.withBox(new ZoneBox(0, 0, 0, 2000, 4, 4))));
        assertEquals(ZoneCheck.OUTSIDE, ZoneCheck.problem(ok.withBox(new ZoneBox(0, 5000, 0, 4, 5004, 4))));
        assertEquals(ZoneCheck.OUTSIDE, ZoneCheck.problem(ok.withBox(new ZoneBox(0, 0, 0, Double.NaN, 4, 4))));
        List<Zone> existing = List.of(box(1, "Halle", 0, Map.of()), box(2, "Tunnel", 0, Map.of()));
        assertEquals(ZoneCheck.NAME_TAKEN, ZoneCheck.problem(ok.withName("halle"), existing));
        assertNull(ZoneCheck.problem(box(1, "HALLE", 0, Map.of()), existing), "eigener Name darf bleiben");
        assertEquals(ZoneCheck.UNKNOWN, ZoneCheck.problem(box(5, "x", 0, Map.of()), existing));
        List<Zone> full = new java.util.ArrayList<>();
        for (int i = 1; i <= ZoneLimits.MAX_ZONES; i++) {
            full.add(box(i, "z" + i, 0, Map.of()));
        }
        assertEquals(ZoneCheck.TOO_MANY, ZoneCheck.problem(ok, full));
    }
}

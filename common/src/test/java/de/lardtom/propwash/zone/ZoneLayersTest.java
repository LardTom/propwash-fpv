package de.lardtom.propwash.zone;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneLayersTest {
    private static final String DIM = Zone.OVERWORLD;

    private static Zone outer() {
        return new Zone(1, DIM, "Zero G", ZoneShape.BOX, new ZoneBox(0, 0, 0, 100, 40, 100), 0, false, 0,
                Map.of("gravity_scale", 0, "battery_scale", 0));
    }

    private static Zone inner() {
        return new Zone(2, DIM, "Normal gravity", ZoneShape.BOX, new ZoneBox(40, 0, 40, 60, 20, 60), 1, false, 0, Map.of());
    }

    @Test
    void innerZoneWithWorldValueEndsTheOuterZeroGravity() {
        Zone normal = inner().withWorld(WorldRuleSpecs.GRAVITY_SCALE);
        assertEquals(ZoneRuleMode.WORLD, normal.mode(WorldRuleSpecs.GRAVITY_SCALE));
        ZoneResolution here = ZoneResolver.resolve(List.of(outer(), normal), DIM, PhysicsRules.DEFAULT, 50, 5, 50);
        assertEquals(100, here.rules().gravityPercent(), "„Weltwert erzwingen“ in der inneren Zone hebt die äußere Schwerelosigkeit auf");
        assertTrue(here.rules().infiniteBattery(), "der Akku kommt weiter aus der äußeren Zone");
        assertNull(here.source(WorldRuleSpecs.GRAVITY_SCALE), "der Wert stammt aus der Welt");
        assertEquals(outer(), here.source(WorldRuleSpecs.BATTERY_SCALE));
        PhysicsRules heavy = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 130));
        assertEquals(130, ZoneResolver.resolve(List.of(outer(), normal), DIM, heavy, 50, 5, 50).rules().gravityPercent(),
                "der erzwungene Weltwert folgt der Welt-Regel");
        assertEquals(0, ZoneResolver.resolve(List.of(outer(), normal), DIM, PhysicsRules.DEFAULT, 10, 5, 10).rules().gravityPercent(),
                "außerhalb der inneren Zone bleibt die äußere Schwerelosigkeit");
    }

    @Test
    void belowInheritsFromTheZoneUnderneathAndOwnValueWins() {
        ZoneResolution inherited = ZoneResolver.resolve(List.of(outer(), inner()), DIM, PhysicsRules.DEFAULT, 50, 5, 50);
        assertEquals(ZoneRuleMode.BELOW, inner().mode(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(0, inherited.rules().gravityPercent(), "„wie darunter“ erbt von der äußeren Zone");
        assertEquals(outer(), inherited.source(WorldRuleSpecs.GRAVITY_SCALE));
        Zone own = inner().withOverride(WorldRuleSpecs.GRAVITY_SCALE, 80);
        assertEquals(ZoneRuleMode.OWN, own.mode(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(80, ZoneResolver.resolve(List.of(outer(), own), DIM, PhysicsRules.DEFAULT, 50, 5, 50).rules().gravityPercent());
    }

    @Test
    void forcedWorldValueCountsLikeTheWorldForAssists() {
        Zone legalOuter = outer().withRaceLegal(true);
        Zone normal = inner().withWorld(WorldRuleSpecs.GRAVITY_SCALE);
        PhysicsRules light = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 50));
        ZoneResolution here = ZoneResolver.resolve(List.of(legalOuter, normal.withRaceLegal(true)), DIM, light, 50, 5, 50);
        assertEquals(50, here.rules().gravityPercent());
        assertTrue(here.assisted(), "eine rennfreie Zone macht den erzwungenen Weltwert nicht fair");
        assertEquals(List.of(AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, 50)), here.worldDetails());
        assertTrue(here.zoneDetails().isEmpty());
        assertEquals(50, here.worldPart().gravityPercent(), "die Welt liefert die Schwerkraft");
        ZoneResolution clean = ZoneResolver.resolve(List.of(outer(), normal), DIM, PhysicsRules.DEFAULT, 50, 5, 50);
        assertFalse(clean.zoneDetails().stream().anyMatch(detail -> detail.equals(AssistDetail.zone(1, WorldRuleSpecs.GRAVITY_SCALE, 0))),
                "die äußere Schwerelosigkeit zählt in der inneren Zone nicht mehr");
    }

    @Test
    void blendGlidesToTheForcedWorldValue() {
        Zone slowOuter = outer();
        Zone normal = new Zone(2, DIM, "Normal gravity", ZoneShape.BOX, new ZoneBox(40, 0, 40, 60, 20, 60), 1, false, 10, Map.of())
                .withWorld(WorldRuleSpecs.GRAVITY_SCALE);
        List<Zone> zones = List.of(slowOuter, normal);
        ZoneField field = new ZoneField();
        field.update(zones, DIM, PhysicsRules.DEFAULT, 30, 5, 50, 0.0);
        assertEquals(0.0, field.gravityPercent(), "in der äußeren Zone schwerelos");
        field.update(zones, DIM, PhysicsRules.DEFAULT, 50, 5, 50, 0.5);
        assertEquals(50.0, field.gravityPercent(), 1.0E-9, "nach der halben Übergangszeit halb zurück auf den Weltwert");
        field.update(zones, DIM, PhysicsRules.DEFAULT, 50, 5, 50, 0.6);
        assertEquals(100.0, field.gravityPercent(), 1.0E-9);
        List<String> shares = new ArrayList<>();
        field.shares(WorldRuleSpecs.GRAVITY_SCALE, (value, zone) -> shares.add(value + "@" + (zone == null ? "world" : zone.name())));
        assertEquals(List.of("100@world"), shares, "nach dem Übergang liefert nur die Welt die Schwerkraft");
        assertTrue(field.blendDetails().isEmpty());
    }

    @Test
    void oldZonesWithoutTheFieldInheritAndTheFieldIsOnlyWrittenWhenNeeded() {
        Zone old = Zone.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":2,\"name\":\"alt\",\"box\":[0,0,0,1,2,3],\"overrides\":{\"wind\":0}}")).getOrThrow();
        assertEquals(Set.of(), old.world());
        assertEquals(ZoneRuleMode.BELOW, old.mode(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(ZoneRuleMode.OWN, old.mode(WorldRuleSpecs.WIND));
        JsonObject plain = Zone.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
        assertFalse(plain.has("force_world"), "ohne erzwungene Weltwerte bleiben die Weltdaten wie bisher");
        Zone forced = old.withWorld(WorldRuleSpecs.GRAVITY_SCALE).withWorld(WorldRuleSpecs.BATTERY_SCALE);
        JsonElement json = Zone.CODEC.encodeStart(JsonOps.INSTANCE, forced).getOrThrow();
        assertEquals("[\"gravity_scale\",\"battery_scale\"]", json.getAsJsonObject().get("force_world").toString());
        assertEquals(forced, Zone.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        Zone both = Zone.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"id\":3,\"name\":\"beides\",\"box\":[0,0,0,1,2,3],\"overrides\":{\"wind\":2},\"force_world\":[\"wind\",\"nope\",\"prop_wash\"]}"))
                .getOrThrow();
        assertEquals(ZoneRuleMode.OWN, both.mode(WorldRuleSpecs.WIND), "eigener Wert geht vor, unbekannte Regeln fallen weg");
        assertEquals(Set.of("prop_wash"), both.world());
    }

    @Test
    void forcedWorldValuesTravelOverTheNetwork() {
        Zone forced = inner().withWorld(WorldRuleSpecs.GRAVITY_SCALE).withOverride(WorldRuleSpecs.WIND, 2);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ZoneWire.write(buf, forced);
        Zone read = ZoneWire.read(buf);
        assertEquals(forced, read);
        assertEquals(ZoneRuleMode.WORLD, read.mode(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void modesReplaceEachOther() {
        Zone zone = inner().withOverride(WorldRuleSpecs.GRAVITY_SCALE, 40).withWorld(WorldRuleSpecs.GRAVITY_SCALE);
        assertEquals(ZoneRuleMode.WORLD, zone.mode(WorldRuleSpecs.GRAVITY_SCALE));
        assertFalse(zone.overrides(WorldRuleSpecs.GRAVITY_SCALE));
        zone = zone.withOverride(WorldRuleSpecs.GRAVITY_SCALE, 40);
        assertEquals(ZoneRuleMode.OWN, zone.mode(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(Set.of(), zone.world());
        assertEquals(ZoneRuleMode.BELOW, zone.without(WorldRuleSpecs.GRAVITY_SCALE).mode(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(ZoneRuleMode.BELOW, zone.withWorld(WorldRuleSpecs.GRAVITY_SCALE).without(WorldRuleSpecs.GRAVITY_SCALE).mode(WorldRuleSpecs.GRAVITY_SCALE));
    }

    @Test
    void editorSwitchesBetweenTheThreeStates() {
        ZoneDraft draft = new ZoneDraft(inner());
        ZoneDraft.Row gravity = new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.GRAVITY_SCALE);
        draft.reset(gravity);
        assertEquals(ZoneRuleMode.WORLD, draft.zone().mode(WorldRuleSpecs.GRAVITY_SCALE), "Entf auf „wie darunter“ erzwingt den Weltwert");
        draft.reset(gravity);
        assertEquals(ZoneRuleMode.BELOW, draft.zone().mode(WorldRuleSpecs.GRAVITY_SCALE), "noch einmal Entf: wieder „wie darunter“");
        draft.change(gravity, 1, false, PhysicsRules.DEFAULT);
        assertEquals(ZoneRuleMode.OWN, draft.zone().mode(WorldRuleSpecs.GRAVITY_SCALE));
        draft.reset(gravity);
        assertEquals(ZoneRuleMode.BELOW, draft.zone().mode(WorldRuleSpecs.GRAVITY_SCALE), "Entf auf eigenem Wert: zurück auf „wie darunter“");
        ZoneDraft.Row disarm = new ZoneDraft.Row(ZoneDraft.Kind.RULE, WorldRuleSpecs.CRASH_DISARM);
        List<String> cycle = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            draft.change(disarm, 1, false, PhysicsRules.DEFAULT);
            Zone zone = draft.zone();
            ZoneRuleMode mode = zone.mode(WorldRuleSpecs.CRASH_DISARM);
            cycle.add(mode == ZoneRuleMode.OWN ? mode.key() + zone.value(WorldRuleSpecs.CRASH_DISARM, -1) : mode.key());
        }
        assertEquals(List.of("own1", "own0", "world", "below"), cycle, "Schalter: darunter → an → aus → Welt → darunter");
    }

    @Test
    void lockedRulesKeepTheForcedWorldValueStoredButDoNotApplyIt() {
        ZoneRelease locked = ZoneRelease.DEFAULT.with(WorldRuleSpecs.GRAVITY_SCALE.id(), false);
        Zone normal = inner().withWorld(WorldRuleSpecs.GRAVITY_SCALE);
        assertEquals(List.of(WorldRuleSpecs.GRAVITY_SCALE), locked.blocked(normal));
        assertEquals(ZoneRuleMode.BELOW, locked.effective(normal).mode(WorldRuleSpecs.GRAVITY_SCALE));
        Zone attempt = normal.without(WorldRuleSpecs.GRAVITY_SCALE);
        assertEquals(ZoneRuleMode.WORLD, locked.enforce(attempt, normal).mode(WorldRuleSpecs.GRAVITY_SCALE), "der Server übernimmt den gespeicherten Zustand");
        assertEquals(ZoneRuleMode.BELOW, locked.enforce(attempt.withWorld(WorldRuleSpecs.GRAVITY_SCALE), inner()).mode(WorldRuleSpecs.GRAVITY_SCALE),
                "eine gesperrte Regel lässt sich nicht neu auf den Weltwert setzen");
        ZoneDraft draft = new ZoneDraft(normal, locked);
        assertTrue(draft.rows().contains(new ZoneDraft.Row(ZoneDraft.Kind.LOCKED, WorldRuleSpecs.GRAVITY_SCALE)));
    }
}

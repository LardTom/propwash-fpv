package de.lardtom.propwash.race;

import de.lardtom.propwash.server.rules.PersonalPresets;
import de.lardtom.propwash.server.rules.PersonalRules;
import de.lardtom.propwash.server.rules.PersonalRulesDraft;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.RuleTiers;
import de.lardtom.propwash.server.rules.RuleValues;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static de.lardtom.propwash.server.rules.WorldRuleSpecs.BATTERY_SCALE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.CRASH_DISARM;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.GRAVITY_SCALE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.WIND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalRulesUiTest {
    private static final RuleTiers RELEASED = RuleTiers.DEFAULTS.with(GRAVITY_SCALE, RuleTier.PLAYERS).with(BATTERY_SCALE, RuleTier.PLAYERS)
            .with(CRASH_DISARM, RuleTier.PLAYERS);
    private static final PhysicsRules SERVER = PhysicsRules.DEFAULT;

    private static PersonalPresets.Preset preset(String name, Map<String, Integer> values) {
        return new PersonalPresets.Preset(name, values);
    }

    @Test
    void raceLegalIsBuiltInWithoutDeviationsAndCannotBeTakenRenamedOrDeleted() {
        PersonalPresets.Preset legal = PersonalPresets.RACE_LEGAL;
        assertTrue(legal.raceLegal());
        assertTrue(legal.values().isEmpty());
        List<PersonalPresets.Preset> own = List.of(preset("Mond", Map.of("gravity_scale", 20)));
        assertEquals(List.of(legal, own.getFirst()), PersonalPresets.choices(own));
        assertEquals(PersonalPresets.Outcome.RESERVED, PersonalPresets.save(own, "race legal", Map.of("wind", 0)).outcome());
        assertEquals(PersonalPresets.Outcome.RESERVED, PersonalPresets.rename(own, "Mond", " Race  Legal ").outcome());
        assertEquals(PersonalPresets.Outcome.MISSING, PersonalPresets.rename(own, PersonalPresets.RACE_LEGAL_NAME, "Anders").outcome());
        assertEquals(PersonalPresets.Outcome.MISSING, PersonalPresets.delete(own, PersonalPresets.RACE_LEGAL_NAME).outcome());
        assertFalse(preset("Race Legal", Map.of("wind", 0)).raceLegal(), "nur das eingebaute Preset ist Race Legal");
        assertEquals(List.of(own.getFirst()), PersonalPresets.validate(List.of(preset("Race Legal", Map.of()), own.getFirst())),
                "ein gespeichertes Preset mit dem festen Namen wird verworfen");
    }

    @Test
    void ownPresetsCanBeSavedUpdatedRenamedAndDeleted() {
        PersonalPresets.Change saved = PersonalPresets.save(List.of(), "  Mond  ", Map.of("gravity_scale", 20));
        assertEquals(PersonalPresets.Outcome.SAVED, saved.outcome());
        assertEquals("Mond", saved.name());
        PersonalPresets.Change updated = PersonalPresets.save(saved.presets(), "mond", Map.of("gravity_scale", 30));
        assertEquals(PersonalPresets.Outcome.UPDATED, updated.outcome());
        assertEquals(List.of(preset("mond", Map.of("gravity_scale", 30))), updated.presets());
        PersonalPresets.Change second = PersonalPresets.save(updated.presets(), "Lang", Map.of("battery_scale", 0));
        assertEquals(PersonalPresets.Outcome.TAKEN, PersonalPresets.rename(second.presets(), "Lang", "MOND").outcome());
        assertEquals(PersonalPresets.Outcome.EMPTY_NAME, PersonalPresets.rename(second.presets(), "Lang", "   ").outcome());
        PersonalPresets.Change renamed = PersonalPresets.rename(second.presets(), "Lang", "Ausdauer");
        assertEquals(PersonalPresets.Outcome.RENAMED, renamed.outcome());
        assertEquals(List.of("mond", "Ausdauer"), renamed.presets().stream().map(PersonalPresets.Preset::name).toList());
        PersonalPresets.Change deleted = PersonalPresets.delete(renamed.presets(), "mond");
        assertEquals(PersonalPresets.Outcome.DELETED, deleted.outcome());
        assertEquals(List.of(preset("Ausdauer", Map.of("battery_scale", 0))), deleted.presets());
        assertEquals("Ausdauer (2)", PersonalPresets.uniqueName(deleted.presets(), "Ausdauer"));
        List<PersonalPresets.Preset> full = new ArrayList<>();
        for (int i = 0; i < PersonalPresets.MAX_PRESETS; i++) {
            full.add(preset("P" + i, Map.of()));
        }
        assertEquals(PersonalPresets.Outcome.FULL, PersonalPresets.save(full, "Neu", Map.of()).outcome());
    }

    @Test
    void storedPresetsKeepOnlyPhysicsRulesWithValidValues() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        raw.put("gravity_scale", 50);
        raw.put("wind", 99);
        raw.put("crash_damage", 0);
        raw.put("unbekannt", 1);
        List<PersonalPresets.Preset> stored = PersonalPresets.validate(List.of(preset("A", raw), preset("a", Map.of()), preset(" ", Map.of())));
        assertEquals(List.of(preset("A", Map.of("gravity_scale", 50))), stored);
    }

    @Test
    void applyingAPresetSkipsRulesTheServerDoesNotRelease() {
        PersonalPresets.Preset moon = preset("Mond", Map.of("gravity_scale", 20, "wind", 0, "battery_scale", 0));
        PersonalPresets.Applied applied = PersonalPresets.apply(moon, RELEASED);
        assertEquals(Map.of("gravity_scale", 20, "battery_scale", 0), applied.values());
        assertEquals(List.of(WIND), applied.skipped());
        PersonalPresets.Applied legal = PersonalPresets.apply(PersonalPresets.RACE_LEGAL, RELEASED);
        assertTrue(legal.values().isEmpty());
        assertTrue(legal.skipped().isEmpty());
        assertTrue(PersonalPresets.matches(PersonalPresets.RACE_LEGAL, Map.of(), SERVER, RELEASED));
        assertTrue(PersonalPresets.matches(moon, Map.of("gravity_scale", 20, "battery_scale", 0), SERVER, RELEASED),
                "nicht freigegebene Regeln zählen beim Vergleich nicht");
        assertFalse(PersonalPresets.matches(moon, Map.of("gravity_scale", 20), SERVER, RELEASED));
        assertTrue(PersonalPresets.matches(preset("Hundert", Map.of("gravity_scale", 100)), Map.of(), SERVER, RELEASED),
                "ein Preset-Wert gleich dem Serverwert ist keine Abweichung");
    }

    @Test
    void draftShowsServerValuesEditsOnlyReleasedRulesAndResetFollowsTheServer() {
        PhysicsRules server = PhysicsRules.of(RuleValues.DEFAULTS.with(GRAVITY_SCALE, 80));
        PersonalRulesDraft draft = new PersonalRulesDraft(server, RELEASED, PersonalRules.NONE.with(GRAVITY_SCALE, 50));
        assertEquals(50, draft.value(GRAVITY_SCALE));
        assertEquals(80, draft.server(GRAVITY_SCALE));
        assertTrue(draft.deviates(GRAVITY_SCALE));
        assertFalse(draft.changed());
        assertFalse(draft.set(WIND, 0), "Wind ist nicht freigegeben");
        assertEquals(1, draft.value(WIND));
        assertTrue(draft.set(BATTERY_SCALE, 0));
        assertFalse(draft.set(BATTERY_SCALE, 5000), "Wert außerhalb des Bereichs");
        assertTrue(draft.changed());
        assertEquals(Map.of("gravity_scale", 50, "battery_scale", 0), draft.desired());
        assertTrue(draft.reset(GRAVITY_SCALE));
        assertEquals(80, draft.value(GRAVITY_SCALE));
        assertEquals(Map.of("battery_scale", 0), draft.desired());
        assertTrue(draft.set(BATTERY_SCALE, 100));
        assertEquals(Map.of(), draft.desired(), "Serverwert gesetzt = keine Abweichung");
        draft.sync(server, RELEASED, PersonalRules.NONE);
        assertFalse(draft.changed(), "Stand vom Server entspricht dem Entwurf");
        draft.discard();
        assertEquals(80, draft.value(GRAVITY_SCALE));
    }

    @Test
    void draftStatusIsRaceLegalNeutralOrAssisted() {
        PersonalRulesDraft draft = new PersonalRulesDraft(SERVER, RELEASED, PersonalRules.NONE);
        assertEquals(PersonalRulesDraft.Status.RACE_LEGAL, draft.status());
        draft.set(CRASH_DISARM, 0);
        assertEquals(PersonalRulesDraft.Status.NEUTRAL, draft.status(), "„Disarm on crash“ ist rennneutral");
        assertEquals(List.of(CRASH_DISARM), draft.deviations());
        assertEquals(List.of(), draft.assisting());
        draft.set(GRAVITY_SCALE, 120);
        assertEquals(PersonalRulesDraft.Status.ASSISTED, draft.status());
        assertEquals(List.of(GRAVITY_SCALE), draft.assisting());
        PersonalPresets.Applied applied = draft.apply(PersonalPresets.RACE_LEGAL);
        assertTrue(applied.skipped().isEmpty());
        assertEquals(PersonalRulesDraft.Status.RACE_LEGAL, draft.status());
        assertTrue(draft.desired().isEmpty());
    }

    @Test
    void draftAppliesPresetsAndKeepsLockedDeviationsReadOnly() {
        PersonalRules own = PersonalRules.NONE.with(WIND, 0).with(GRAVITY_SCALE, 50);
        PersonalRulesDraft draft = new PersonalRulesDraft(SERVER, RELEASED, own);
        assertEquals(List.of(WIND), draft.locked());
        assertEquals(OptionalInt.of(0), draft.stored(WIND));
        assertEquals(1, draft.value(WIND), "gesperrte Abweichung wirkt nicht");
        assertFalse(draft.reset(WIND), "gesperrte Regeln nur lesbar");
        PersonalPresets.Applied applied = draft.apply(preset("Mond", Map.of("gravity_scale", 20, "wind", 0)));
        assertEquals(List.of(WIND), applied.skipped());
        assertEquals(Map.of("gravity_scale", 20), draft.desired());
        assertTrue(draft.changed());
        assertEquals(Map.of("gravity_scale", 20), draft.capture(), "„aktuelle Einstellungen als Preset“ speichert nur die Abweichungen");
        draft.sync(SERVER, RELEASED.with(WIND, RuleTier.PLAYERS), own);
        assertEquals(0, draft.value(WIND), "erneute Freigabe: gespeicherte Abweichung wirkt wieder");
    }

    @Test
    void serverRulesBadgeListsDeviationsFromDefaultsAndFromTheCurrentServerRules() {
        assertTrue(ServerRulesBadge.board(RaceStandard.DEFAULTS).isEmpty());
        RaceStandard calm = RaceStandard.DEFAULTS.with("wind", 0);
        RaceRules.Rule wind = RaceRules.find("wind").orElseThrow();
        assertEquals(List.of(new ServerRulesBadge.Line(wind, 0, 1)), ServerRulesBadge.board(calm));
        assertTrue(ServerRulesBadge.entry(null, calm).isEmpty(), "alte Zeiten ohne Regelsatz bekommen kein Abzeichen");
        assertTrue(ServerRulesBadge.entry(calm, calm).isEmpty());
        assertEquals(List.of(new ServerRulesBadge.Line(wind, 1, 0)), ServerRulesBadge.entry(RaceStandard.DEFAULTS, calm),
                "Geist mit Standardwind in einer Welt mit Wind aus");
        RaceStandard heavy = RaceStandard.DEFAULTS.with("gravity_scale", 150).with("wind", 0);
        RaceRules.Rule gravity = RaceRules.find("gravity_scale").orElseThrow();
        assertEquals(List.of(new ServerRulesBadge.Line(gravity, 150, 100)), ServerRulesBadge.entry(heavy, calm));
    }
}

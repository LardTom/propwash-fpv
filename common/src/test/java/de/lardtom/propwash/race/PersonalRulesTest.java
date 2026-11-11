package de.lardtom.propwash.race;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.rules.PersonalRuleEdit;
import de.lardtom.propwash.server.rules.PersonalRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleBase;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.RuleTiers;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.ZoneRelease;
import de.lardtom.propwash.zone.ZoneResolution;
import de.lardtom.propwash.zone.ZoneResolver;
import de.lardtom.propwash.zone.ZoneShape;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.lardtom.propwash.server.rules.WorldRuleSpecs.BATTERY_SCALE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.CRASH_DISARM;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.GRAVITY_SCALE;
import static de.lardtom.propwash.server.rules.WorldRuleSpecs.WIND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalRulesTest {
    private static final String DIM = Zone.OVERWORLD;
    private static final long S = 1_000_000_000L;
    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String FRAME = "race_5";
    private static final PhysicsRules SERVER = PhysicsRules.DEFAULT;

    private static Zone zone(int id, boolean raceLegal, Map<String, Integer> overrides, int priority) {
        return new Zone(id, DIM, "z" + id, ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), priority, raceLegal, 0, overrides);
    }

    private static PhysicsRules server(RuleSpec spec, int value) {
        return PhysicsRules.of(RuleValues.DEFAULTS.with(spec, value));
    }

    private static RuleBase base(PhysicsRules server, RuleTiers tiers, PersonalRules own) {
        return RuleBase.of(server, tiers, own);
    }

    private static final RuleTiers PLAYERS = RuleTiers.DEFAULTS.with(GRAVITY_SCALE, RuleTier.PLAYERS).with(BATTERY_SCALE, RuleTier.PLAYERS)
            .with(WIND, RuleTier.PLAYERS).with(CRASH_DISARM, RuleTier.PLAYERS);
    private static final PersonalRules LIGHT = PersonalRules.NONE.with(GRAVITY_SCALE, 50);

    @Test
    void outsideZonesThePersonalValueOnlyCountsOnTierPlayers() {
        assertEquals(50, base(SERVER, PLAYERS, LIGHT).personal().gravityPercent());
        assertEquals(100, base(SERVER, RuleTiers.DEFAULTS, LIGHT).personal().gravityPercent(), "Stufe Zonen: Abweichung wirkungslos");
        assertEquals(100, base(SERVER, PLAYERS.with(GRAVITY_SCALE, RuleTier.FIXED), LIGHT).personal().gravityPercent());
        ZoneResolution here = ZoneResolver.resolve(List.of(), DIM, base(SERVER, PLAYERS, LIGHT), 50, 5, 50);
        assertEquals(50, here.rules().gravityPercent());
        assertEquals(List.of(AssistDetail.personal(GRAVITY_SCALE, 50, 100)), here.personalDetails());
    }

    @Test
    void zonesWinOverThePlayerAndWorldModeMeansTheServerValue() {
        RuleBase light = base(SERVER, PLAYERS, LIGHT);
        Zone own = zone(1, false, Map.of("gravity_scale", 80), 0);
        assertEquals(80, ZoneResolver.resolve(List.of(own), DIM, light, 5, 5, 5).rules().gravityPercent(), "Zone vor Spieler");
        Zone world = zone(2, false, Map.of(), 0).withWorld(GRAVITY_SCALE);
        ZoneResolution forced = ZoneResolver.resolve(List.of(world), DIM, light, 5, 5, 5);
        assertEquals(100, forced.rules().gravityPercent(), "Modus Welt = Serverwert, nicht der persönliche");
        assertTrue(forced.personalDetails().isEmpty());
        Zone below = zone(3, false, Map.of("wind", 3), 0);
        assertEquals(50, ZoneResolver.resolve(List.of(below), DIM, light, 5, 5, 5).rules().gravityPercent(), "Darunter ohne Zone = persönlich");
        Zone lower = zone(4, false, Map.of("gravity_scale", 70), 0);
        Zone upper = zone(5, false, Map.of("wind", 2), 5);
        assertEquals(70, ZoneResolver.resolve(List.of(lower, upper), DIM, light, 5, 5, 5).rules().gravityPercent(), "Darunter = Zone darunter");
    }

    @Test
    void fixedLocksZonesAndPlayersAndReleasingRestoresBoth() {
        Zone own = zone(1, false, Map.of("gravity_scale", 80), 0);
        RuleTiers fixed = PLAYERS.with(GRAVITY_SCALE, RuleTier.FIXED);
        ZoneResolution locked = ZoneResolver.resolve(List.of(own), DIM, base(SERVER, fixed, LIGHT), 5, 5, 5);
        assertEquals(100, locked.rules().gravityPercent());
        assertNull(locked.source(GRAVITY_SCALE), "Zonen-Überschreibung auf fester Regel wirkungslos");
        assertTrue(own.overrides(GRAVITY_SCALE), "bleibt in der Zone gespeichert");
        assertEquals(80, ZoneResolver.resolve(List.of(own), DIM, base(SERVER, fixed.with(GRAVITY_SCALE, RuleTier.ZONES), LIGHT), 5, 5, 5)
                .rules().gravityPercent(), "wieder freigegeben wirkt die Zone");
        assertEquals(100, base(SERVER, fixed.with(GRAVITY_SCALE, RuleTier.ZONES), LIGHT).personal().gravityPercent());
        assertEquals(50, base(SERVER, fixed.with(GRAVITY_SCALE, RuleTier.PLAYERS), LIGHT).personal().gravityPercent(),
                "gespeicherte Abweichung wirkt nach erneuter Freigabe");
        assertEquals(List.of(GRAVITY_SCALE), LIGHT.locked(fixed));
    }

    @Test
    void blendedRulesUseThePersonalBaseAndTheServerForWorldZones() {
        ZoneField field = new ZoneField();
        field.update(List.of(), DIM, base(SERVER, PLAYERS, LIGHT), 50, 5, 50, 0.0);
        assertEquals(50.0, field.gravityPercent());
        assertEquals(List.of(AssistDetail.personal(GRAVITY_SCALE, 50, 100)), field.personalDetails());
        Zone world = zone(2, false, Map.of(), 0).withWorld(GRAVITY_SCALE);
        ZoneField inside = new ZoneField();
        inside.update(List.of(world), DIM, base(SERVER, PLAYERS, LIGHT), 5, 5, 5, 0.0);
        assertEquals(100.0, inside.gravityPercent());
        assertTrue(inside.personalDetails().isEmpty());
        ZoneField fixed = new ZoneField();
        fixed.update(List.of(zone(3, false, Map.of("gravity_scale", 10), 0)), DIM, base(SERVER, RuleTiers.DEFAULTS.with(GRAVITY_SCALE, RuleTier.FIXED),
                LIGHT), 5, 5, 5, 0.0);
        assertEquals(100.0, fixed.gravityPercent());
    }

    private static RaceStandard standard(PhysicsRules server) {
        Map<String, Integer> values = new HashMap<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            values.put(spec.name(), server.value(spec));
        }
        return RaceStandard.adopt(RaceRules.values(new RuleValues(values), Map.of()));
    }

    private static RaceRuleSet observe(PhysicsRules server, RuleTiers tiers, PersonalRules own, List<Zone> zones) {
        ZoneField field = new ZoneField();
        field.update(zones, DIM, base(server, tiers, own), 5, 5, 5, 0.0);
        Map<String, Integer> values = new HashMap<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            values.put(spec.name(), server.value(spec));
        }
        return RaceRules.observe(RaceRules.values(new RuleValues(values), Map.of()), field.resolution(), field);
    }

    @Test
    void serverRulesNeverMakeALapAssisted() {
        PhysicsRules heavy = server(GRAVITY_SCALE, 80);
        RaceRuleSet rules = observe(heavy, RuleTiers.DEFAULTS, PersonalRules.NONE, List.of());
        assertFalse(rules.isDefault(), "der Regelsatz trägt die Serverregeln");
        assertTrue(rules.personalReasons(standard(heavy)).none(), "Serverregeln machen keine Runde assisted");
        assertFalse(rules.matches(RaceStandard.DEFAULTS), "gegen den Propwash-Standard weicht sie ab (Abzeichen)");
    }

    @Test
    void personalBatteryIsAssistedUntilTheServerUsesTheSameValue() {
        PersonalRules infinite = PersonalRules.NONE.with(BATTERY_SCALE, 0);
        RaceRuleSet rules = observe(SERVER, PLAYERS, infinite, List.of());
        AssistReasons reasons = rules.personalReasons(standard(SERVER));
        assertEquals(LapAssist.PERSONAL_RULES.bit(), reasons.mask());
        assertEquals(List.of(new AssistDetail(LapAssist.PERSONAL_RULES, "battery_scale", 0, 100)), reasons.details());
        PhysicsRules serverInfinite = server(BATTERY_SCALE, 0);
        RaceRuleSet same = observe(serverInfinite, PLAYERS, infinite, List.of());
        assertTrue(same.personalReasons(standard(serverInfinite)).none(), "gleicher Wert wie der Server zählt als Serverwert");
        assertTrue(rules.personalReasons(standard(serverInfinite)).none(), "verglichen werden Werte, nicht wer sie gesetzt hat");
        RaceRuleSet full = observe(SERVER, PLAYERS, PersonalRules.NONE.with(BATTERY_SCALE, 100), List.of());
        assertTrue(full.personalReasons(standard(SERVER)).none(), "mit 100 % zählt die Runde normal");
    }

    @Test
    void neutralPersonalRulesAndRaceLegalZonesStayClean() {
        RaceRuleSet disarm = observe(SERVER, PLAYERS, PersonalRules.NONE.with(CRASH_DISARM, 0), List.of());
        assertTrue(disarm.personalReasons(standard(SERVER)).none());
        RaceRuleSet legal = observe(SERVER, PLAYERS, LIGHT, List.of(zone(7, true, Map.of("gravity_scale", 30), 0)));
        assertTrue(legal.personalReasons(standard(SERVER)).none(), "renntaugliche Zone gehört zum Rennstandard");
        RaceRuleSet practice = observe(SERVER, PLAYERS, PersonalRules.NONE, List.of(zone(8, false, Map.of("gravity_scale", 30), 0)));
        AssistReasons zone = practice.personalReasons(standard(SERVER));
        assertEquals(LapAssist.ZONE_PHYSICS.bit(), zone.mask(), "nicht renntaugliche Zonen bleiben ZONE_PHYSICS");
    }

    @Test
    void enumBitsStayStable() {
        List<String> before = List.of("HOLD_MODE", "RESCUE", "WORLD_PHYSICS", "CREATIVE_DRONE", "PARTS_MISMATCH", "GOGGLES_LATENCY", "OTHER",
                "ZONE_PHYSICS", "SCALE");
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i), LapAssist.values()[i].name());
            assertEquals(1 << i, LapAssist.valueOf(before.get(i)).bit());
        }
        assertEquals(LapAssist.values().length - 1, LapAssist.PERSONAL_RULES.ordinal(), "neuer Grund am Ende");
        assertEquals(1 << 9, LapAssist.PERSONAL_RULES.bit());
        assertTrue(LapAssist.PERSONAL_RULES.in(LapAssist.ALL));
        assertTrue(LapAssist.PERSONAL_RULES.serverSide(), "nie vom Client übernommen");
        assertEquals(LapAssist.PERSONAL_RULES, LapAssist.byKey("personal_rules"));
        assertEquals(AssistChecklist.Category.PHYSICS, AssistChecklist.Category.of(LapAssist.PERSONAL_RULES));
    }

    @Test
    void editsOnlyAcceptReleasedRulesAndValidValues() {
        RuleTiers tiers = RuleTiers.DEFAULTS.with(BATTERY_SCALE, RuleTier.PLAYERS);
        PersonalRuleEdit.Result zones = PersonalRuleEdit.apply(PersonalRules.NONE, tiers, SERVER, PersonalRuleEdit.Op.SET, Map.of("gravity_scale", 50));
        assertEquals(List.of("gravity_scale"), zones.refused(), "Stufe Zonen: abgelehnt");
        assertTrue(zones.rules().isEmpty());
        PersonalRuleEdit.Result range = PersonalRuleEdit.apply(PersonalRules.NONE, tiers, SERVER, PersonalRuleEdit.Op.SET, Map.of("battery_scale", 5000));
        assertEquals(List.of("battery_scale"), range.refused(), "außerhalb des Bereichs");
        PersonalRuleEdit.Result other = PersonalRuleEdit.apply(PersonalRules.NONE, tiers, SERVER, PersonalRuleEdit.Op.SET,
                Map.of("drones_break_blocks", 1, "nonsense", 1));
        assertEquals(List.of("drones_break_blocks", "nonsense"), other.refused(), "nur Physikregeln");
        PersonalRules set = PersonalRuleEdit.apply(PersonalRules.NONE, tiers, SERVER, PersonalRuleEdit.Op.SET, Map.of("battery_scale", 0)).rules();
        assertEquals(Map.of("battery_scale", 0), set.values(), "nur Abweichungen gespeichert");
        assertTrue(PersonalRuleEdit.apply(set, tiers, SERVER, PersonalRuleEdit.Op.SET, Map.of("battery_scale", 100)).rules().isEmpty(),
                "Serverwert = keine Abweichung");
        assertTrue(PersonalRuleEdit.apply(set, tiers, SERVER, PersonalRuleEdit.Op.RESET, Map.of("battery_scale", 0)).rules().isEmpty());
        PersonalRules locked = set.with(GRAVITY_SCALE, 50);
        PersonalRuleEdit.Result legal = PersonalRuleEdit.apply(locked, tiers, SERVER, PersonalRuleEdit.Op.APPLY, Map.of());
        assertEquals(Map.of("gravity_scale", 50), legal.rules().values(), "Race Legal leert nur freigegebene, gesperrte bleiben gespeichert");
        PersonalRuleEdit.Result preset = PersonalRuleEdit.apply(PersonalRules.NONE, tiers, SERVER, PersonalRuleEdit.Op.APPLY,
                Map.of("battery_scale", 200, "wind", 0));
        assertEquals(Map.of("battery_scale", 200), preset.rules().values());
        assertEquals(List.of("wind"), preset.refused(), "nicht freigegebene werden übersprungen");
        assertTrue(new PersonalRules(Map.of("battery_scale", -5, "drones_break_blocks", 1)).isEmpty(), "ungültige Werte nie gespeichert");
    }

    @Test
    void oldWorldsStartWithTierZonesAndOtherRulesStayFixed() {
        PersonalRulesServer empty = PersonalRulesServer.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        RuleTiers decoded = empty.tiers(ZoneRelease.DEFAULT);
        for (RuleSpec spec : PhysicsRules.SPECS) {
            assertEquals(RuleTier.ZONES, decoded.tier(spec), spec.name());
        }
        assertTrue(empty.players().isEmpty());
        assertEquals(RuleTier.FIXED, decoded.tier(WorldRuleSpecs.DRONES_BREAK_BLOCKS));
        assertEquals(RuleTier.FIXED, decoded.tier(WorldRuleSpecs.ALLOW_FREECAM));
        assertEquals(decoded, RuleTiers.DEFAULTS.with(WorldRuleSpecs.DRONES_BREAK_BLOCKS, RuleTier.PLAYERS));
        ZoneRelease locked = ZoneRelease.DEFAULT.with(GRAVITY_SCALE.id(), false);
        assertEquals(RuleTier.FIXED, empty.tiers(locked).tier(GRAVITY_SCALE), "für Zonen gesperrte Regel = Fest");
        JsonObject raw = new JsonObject();
        JsonArray list = new JsonArray();
        list.add("wind");
        list.add("bogus");
        list.add("drones_break_blocks");
        raw.add("player_rules", list);
        PersonalRulesServer read = PersonalRulesServer.CODEC.parse(JsonOps.INSTANCE, raw).getOrThrow();
        assertEquals(Map.of("wind", RuleTier.PLAYERS), read.tiers(ZoneRelease.DEFAULT).tiers());
        assertEquals(RuleTier.FIXED, read.tiers(ZoneRelease.DEFAULT.with(WIND.id(), false)).tier(WIND), "Fest gewinnt");
        JsonElement back = PersonalRulesServer.CODEC.encodeStart(JsonOps.INSTANCE, read).getOrThrow();
        assertEquals(read.tiers(ZoneRelease.DEFAULT), PersonalRulesServer.CODEC.parse(JsonOps.INSTANCE, back).getOrThrow().tiers(ZoneRelease.DEFAULT));
    }

    @Test
    void newLapsCarryTheServerRulesAndOldLapsKeepTheirMarking() {
        Leaderboard board = new Leaderboard();
        board.score(new Leaderboard.Scoring(RaceStandard.DEFAULTS));
        PhysicsRules heavy = server(GRAVITY_SCALE, 80);
        RaceRuleSet rules = observe(heavy, RuleTiers.DEFAULTS, PersonalRules.NONE, List.of());
        Leaderboard.Result clean = board.submitTime(ANNA, "Anna", FRAME, 30 * S, 1L, AssistReasons.NONE, rules, false, standard(heavy));
        assertTrue(clean.clean(), "Serverregeln Schwerkraft 80 %: rennfrei");
        assertEquals(standard(heavy), board.best(ANNA, FRAME).server());
        Leaderboard.Result old = board.submitTime(ANNA, "Anna", FRAME, 29 * S, 2L, AssistReasons.NONE, rules, true);
        assertFalse(old.clean(), "alte Runde ohne Regelsatz bleibt gegen den gespeicherten Rennstandard gewertet");
        AssistReasons personal = AssistReasons.of(List.of(AssistDetail.personal(BATTERY_SCALE, 0, 100)));
        Leaderboard.Result flagged = board.submitTime(ANNA, "Anna", FRAME, 28 * S, 3L, personal, rules, true, standard(heavy));
        assertFalse(flagged.clean());
        assertTrue(LapAssist.PERSONAL_RULES.in(board.bestAssisted(ANNA, FRAME).assist()));
        JsonElement saved = Leaderboard.CODEC.encodeStart(JsonOps.INSTANCE, board).getOrThrow();
        Leaderboard loaded = Leaderboard.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        loaded.score(new Leaderboard.Scoring(RaceStandard.DEFAULTS));
        assertEquals(30 * S, loaded.best(ANNA, FRAME).nanos());
        assertEquals(standard(heavy), loaded.best(ANNA, FRAME).server());
        assertNull(loaded.storedTimes().stream().filter(e -> e.nanos() == 29 * S).findFirst().orElseThrow().server());
    }

    @Test
    void serverLapVerdictFlagsOnlyPersonalDeviations() {
        AssistTimeline assist = new AssistTimeline();
        RuleTimeline timeline = new RuleTimeline();
        PhysicsRules heavy = server(GRAVITY_SCALE, 80);
        RaceRuleSet calm = observe(heavy, PLAYERS, PersonalRules.NONE.with(WIND, 0), List.of());
        for (long t = 0L; t <= S; t += S / 20) {
            timeline.observe(t, calm, standard(heavy));
        }
        AssistCheck.LapVerdict verdict = AssistCheck.serverLapVerdict(assist, timeline, S / 10, S * 9 / 10, AssistReasons.NONE, RaceRuleSet.DEFAULTS,
                AssistReasons.NONE, standard(heavy));
        assertEquals(List.of(new AssistDetail(LapAssist.PERSONAL_RULES, "wind", 0, 1)), verdict.reasons().details());
        assertEquals(verdict.reasons(), verdict.fixed(), "neue Runden: Urteil steht beim Fliegen fest");
        assertFalse(verdict.reasons().has(LapAssist.WORLD_PHYSICS));
    }

    @Test
    void serverChangeDuringALapIsJudgedAgainstTheServerValueOfThatMoment() {
        PhysicsRules mild = server(WIND, 1);
        PhysicsRules calm = server(WIND, 0);
        RuleTimeline plain = new RuleTimeline();
        RuleTimeline personal = new RuleTimeline();
        for (long t = 0L; t <= S; t += S / 20) {
            PhysicsRules now = t < S / 2 ? mild : calm;
            plain.observe(t, observe(now, PLAYERS, PersonalRules.NONE, List.of()), standard(now));
            personal.observe(t, observe(now, PLAYERS, PersonalRules.NONE.with(WIND, 0), List.of()), standard(now));
        }
        AssistCheck.LapVerdict own = AssistCheck.serverLapVerdict(new AssistTimeline(), plain, S / 10, S * 9 / 10, AssistReasons.NONE,
                RaceRuleSet.DEFAULTS, AssistReasons.NONE, standard(calm));
        assertTrue(own.clean(), "ohne persönliche Regeln: Server stellt Wind während der Runde um, Runde bleibt rennfrei: " + own.reasons());
        assertEquals(2, own.rules().seen(RaceRules.find("wind").orElseThrow()).size(), "Regelsatz der Runde nennt beide Werte");
        AssistCheck.LapVerdict deviated = AssistCheck.serverLapVerdict(new AssistTimeline(), personal, S / 10, S * 9 / 10, AssistReasons.NONE,
                RaceRuleSet.DEFAULTS, AssistReasons.NONE, standard(calm));
        assertEquals(List.of(new AssistDetail(LapAssist.PERSONAL_RULES, "wind", 0, 1)), deviated.reasons().details(),
                "persönlich Wind 0, solange der Server Wind 1 hatte: mit Flughilfe, auch wenn der Server später Wind 0 stellt");
    }

    @Test
    void foldedSpansKeepTheVerdictOfTheirMoment() {
        PhysicsRules mild = server(WIND, 1);
        PhysicsRules calm = server(WIND, 0);
        RuleTimeline timeline = new RuleTimeline();
        timeline.observe(0L, observe(mild, PLAYERS, PersonalRules.NONE.with(WIND, 0), List.of()), standard(mild));
        for (int i = 1; i <= 3 * RuleTimeline.MAX_SPANS; i++) {
            PhysicsRules now = i % 2 == 0 ? mild : calm;
            timeline.observe(i * S / 10, observe(now, PLAYERS, PersonalRules.NONE, List.of()), standard(now));
        }
        assertTrue(timeline.spans() <= RuleTimeline.MAX_SPANS);
        assertEquals(List.of(new AssistDetail(LapAssist.PERSONAL_RULES, "wind", 0, 1)), timeline.personalBetween(0L, 3L * RuleTimeline.MAX_SPANS * S / 10)
                .details(), "Abweichung am Anfang bleibt nach dem Zusammenlegen erhalten, Serverwechsel danach zählen nicht");
        assertTrue(timeline.personalBetween(3L * RuleTimeline.MAX_SPANS * S / 10 - S, 3L * RuleTimeline.MAX_SPANS * S / 10).none());
    }

    @Test
    void zoneReasonsFromTheServerTimelineJudgeOnlyTheirOwnRule() {
        PhysicsRules heavy = server(GRAVITY_SCALE, 80);
        AssistTimeline assist = new AssistTimeline();
        AssistDetail zone = new AssistDetail(LapAssist.ZONE_PHYSICS, "wind", 3, 7);
        for (long t = 0L; t <= S; t += S / 20) {
            assist.observe(t, AssistReasons.of(List.of(zone)));
        }
        AssistCheck.LapVerdict verdict = AssistCheck.serverLapVerdict(assist, new RuleTimeline(), S / 10, S * 9 / 10, AssistReasons.NONE,
                observe(heavy, PLAYERS, PersonalRules.NONE, List.of()), AssistReasons.NONE, standard(heavy));
        assertEquals(List.of(zone), verdict.reasons().details(), "Server-Schwerkraft 80 % gilt nicht als persönliche Abweichung");
    }
}

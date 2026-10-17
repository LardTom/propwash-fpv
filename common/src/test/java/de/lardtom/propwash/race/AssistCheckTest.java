package de.lardtom.propwash.race;

import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistCheckTest {
    private static final AssistCheck.Setup CLEAN = AssistCheck.Setup.clean();

    private static int changedValue(RuleSpec spec) {
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            return spec.defaultValue() == 0 ? 1 : 0;
        }
        int up = spec.defaultValue() + spec.step();
        return up <= spec.max() ? up : spec.defaultValue() - spec.step();
    }

    private static AssistReasons withRules(RuleValues values) {
        return AssistCheck.evaluate(CLEAN.withPhysics(PhysicsRules.of(values), false));
    }

    @Test
    void cleanSetupIsNeverAssisted() {
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(CLEAN));
        assertEquals(AssistReasons.NONE, withRules(RuleValues.DEFAULTS));
        assertFalse(PhysicsRules.of(RuleValues.DEFAULTS).modified());
        assertTrue(PhysicsRules.DEFAULT.changedSpecs().isEmpty());
    }

    @Test
    void everyRuleWrittenOutAtItsDefaultStaysClean() {
        Map<String, Integer> all = new HashMap<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            int parsed = spec.parse(spec.serialize(spec.defaultValue()));
            assertEquals(spec.defaultValue(), parsed, spec.name());
            all.put(spec.name(), parsed);
        }
        RuleValues explicit = new RuleValues(all);
        assertFalse(PhysicsRules.of(explicit).modified());
        assertEquals(AssistReasons.NONE, withRules(explicit));
        assertEquals(AssistReasons.NONE, withRules(RulePreset.REALISTIC.ruleValues()));
    }

    @Test
    void everyPhysicsRuleNamesItselfWithCurrentAndDefaultValue() {
        for (RuleSpec spec : PhysicsRules.SPECS) {
            int value = changedValue(spec);
            AssistReasons reasons = withRules(RuleValues.DEFAULTS.with(spec, value));
            assertEquals(LapAssist.WORLD_PHYSICS.bit(), reasons.mask(), spec.name());
            assertEquals(List.of(AssistDetail.rule(spec, value)), reasons.details(), spec.name());
            AssistDetail detail = reasons.details().getFirst();
            assertEquals(spec, detail.rule().orElseThrow());
            assertEquals(value, detail.value());
            assertEquals(spec.defaultValue(), detail.reference());
            assertEquals(value, PhysicsRules.of(RuleValues.DEFAULTS.with(spec, value)).value(spec), spec.name());
        }
    }

    @Test
    void rulesWithoutPhysicsNeverCount() {
        List<RuleSpec> others = new ArrayList<>(WorldRuleSpecs.all());
        others.removeAll(PhysicsRules.SPECS);
        assertFalse(others.isEmpty());
        RuleValues values = RuleValues.DEFAULTS;
        for (RuleSpec spec : others) {
            values = values.with(spec, changedValue(spec));
            assertEquals(AssistReasons.NONE, withRules(RuleValues.DEFAULTS.with(spec, changedValue(spec))), spec.name());
        }
        assertEquals(AssistReasons.NONE, withRules(values));
        assertTrue(others.contains(WorldRuleSpecs.SAVE_ASSISTED_LAPS));
        assertTrue(others.contains(WorldRuleSpecs.PART_COMPATIBILITY));
        assertTrue(others.contains(WorldRuleSpecs.DRONE_TRAILS));
        assertTrue(others.contains(WorldRuleSpecs.INVULNERABLE_PILOTS));
        assertTrue(others.contains(WorldRuleSpecs.CRASH_DAMAGE));
    }

    @Test
    void gravityExactlyOneHundredPercentIsClean() {
        assertEquals(AssistReasons.NONE, withRules(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 100)));
        for (int percent : new int[]{99, 101, 0, 1000}) {
            AssistReasons reasons = withRules(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, percent));
            assertEquals(List.of(new AssistDetail(LapAssist.WORLD_PHYSICS, "gravity_scale", percent, 100)), reasons.details());
        }
    }

    @Test
    void presetsNameExactlyTheirPhysicsDifferences() {
        for (RulePreset preset : RulePreset.values()) {
            AssistReasons reasons = withRules(preset.ruleValues());
            List<String> expected = new ArrayList<>();
            for (RuleSpec spec : preset.differences()) {
                if (PhysicsRules.SPECS.contains(spec)) {
                    expected.add(spec.name() + "=" + preset.value(spec));
                }
            }
            List<String> named = new ArrayList<>();
            for (AssistDetail detail : reasons.details()) {
                named.add(detail.subject() + "=" + detail.value());
            }
            expected.sort(null);
            named.sort(null);
            assertEquals(expected, named, preset.id());
            assertEquals(expected.isEmpty() ? LapAssist.NONE : LapAssist.WORLD_PHYSICS.bit(), reasons.mask(), preset.id());
        }
        assertEquals(LapAssist.NONE, withRules(RulePreset.REALISTIC.ruleValues()).mask());
    }

    @Test
    void holdModesNameWhichHoldWasOn() {
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(CLEAN.withHold(HoldMode.OFF)));
        AssistReasons altitude = AssistCheck.evaluate(CLEAN.withHold(HoldMode.ALTHOLD));
        assertEquals(LapAssist.HOLD_MODE.bit(), altitude.mask());
        assertEquals(AssistDetail.ALTITUDE, altitude.details().getFirst().subject());
        AssistReasons position = AssistCheck.evaluate(CLEAN.withHold(HoldMode.POSHOLD));
        assertEquals(AssistDetail.POSITION, position.details().getFirst().subject());
    }

    @Test
    void rescueNamesItsTrigger() {
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(CLEAN.withRescue(null)));
        for (String trigger : List.of(AssistDetail.FAILSAFE, AssistDetail.SWITCH, AssistDetail.KEY, "")) {
            AssistReasons reasons = AssistCheck.evaluate(CLEAN.withRescue(trigger));
            assertEquals(LapAssist.RESCUE.bit(), reasons.mask(), trigger);
            assertEquals(trigger, reasons.details().getFirst().subject());
        }
    }

    @Test
    void creativeDroneAndPartIssuesAreNamed() {
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(CLEAN.withDrone(false, List.of())));
        AssistReasons creative = AssistCheck.evaluate(CLEAN.withDrone(true, List.of()));
        assertEquals(LapAssist.CREATIVE_DRONE.bit(), creative.mask());
        AssistReasons parts = AssistCheck.evaluate(CLEAN.withDrone(false, List.of("prop_too_large", "battery_frame")));
        assertEquals(LapAssist.PARTS_MISMATCH.bit(), parts.mask());
        assertEquals(List.of("prop_too_large", "battery_frame"), parts.details().stream().map(AssistDetail::subject).toList());
    }

    @Test
    void creativeGogglesOnlyCountBelowTheTransmitterLatency() {
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(CLEAN.withGoggles(false, true, 0, 25)));
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(CLEAN.withGoggles(true, false, 0, 25)));
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(CLEAN.withGoggles(true, true, 25, 25)));
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(CLEAN.withGoggles(true, true, 120, 25)));
        AssistReasons faster = AssistCheck.evaluate(CLEAN.withGoggles(true, true, 20, 25));
        assertEquals(List.of(AssistDetail.goggles(20, 25)), faster.details());
    }

    @Test
    void clientPhysicsClaimOnlyShowsWhenNoRuleExplainsIt() {
        AssistReasons claim = AssistCheck.evaluate(CLEAN.withPhysics(PhysicsRules.DEFAULT, true));
        assertEquals(List.of(AssistDetail.clientPhysics()), claim.details());
        PhysicsRules wind = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 3));
        assertEquals(List.of(AssistDetail.rule(WorldRuleSpecs.WIND, 3)), AssistCheck.evaluate(CLEAN.withPhysics(wind, true)).details());
    }

    @Test
    void maskMatchesTheOlderFlightCheck() {
        AssistCheck.Setup all = new AssistCheck.Setup(HoldMode.ALTHOLD, AssistDetail.SWITCH, PhysicsRules.of(RuleValues.DEFAULTS.with(
                WorldRuleSpecs.GRAVITY_SCALE, 130)), false, true, List.of("prop_too_large"), true, true, 0, 30);
        LapAssist.Flight flight = new LapAssist.Flight(true, true, true, true, true, true, true, 0, 30, true, true, 3);
        assertEquals(LapAssist.of(flight), AssistCheck.evaluate(all).mask());
        assertEquals(LapAssist.of(LapAssist.Flight.plain()), AssistCheck.evaluate(CLEAN).mask());
    }

    @Test
    void reasonGroupsSplitServerAndClientKnowledge() {
        assertEquals(LapAssist.ALL, LapAssist.SERVER_REASONS | LapAssist.CLIENT_REASONS);
        assertEquals(LapAssist.NONE, LapAssist.SERVER_REASONS & LapAssist.CLIENT_REASONS);
        assertTrue(LapAssist.WORLD_PHYSICS.serverSide());
        assertTrue(LapAssist.CREATIVE_DRONE.serverSide());
        assertTrue(LapAssist.PARTS_MISMATCH.serverSide());
        assertFalse(LapAssist.HOLD_MODE.serverSide());
        assertFalse(LapAssist.GOGGLES_LATENCY.serverSide());
        assertEquals(LapAssist.ALL, AssistChecklist.covered());
        for (LapAssist reason : LapAssist.values()) {
            assertEquals(reason, LapAssist.byKey(reason.key()));
        }
        assertEquals(LapAssist.OTHER, LapAssist.byKey("from_a_newer_version"));
    }
}

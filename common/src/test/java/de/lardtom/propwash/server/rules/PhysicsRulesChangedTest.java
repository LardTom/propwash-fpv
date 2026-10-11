package de.lardtom.propwash.server.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsRulesChangedTest {
    @Test
    void physicsSpecsAreExactlyTheRulesThatChangeThePhysics() {
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            int other = spec.defaultValue() == spec.max() ? spec.min() : spec.max();
            RuleValues changed = RuleValues.DEFAULTS.with(spec, other);
            boolean modified = PhysicsRules.of(changed).modified();
            assertEquals(modified, PhysicsRules.SPECS.contains(spec), spec.name());
            assertEquals(modified ? List.of(spec) : List.of(), PhysicsRules.changed(changed), spec.name());
        }
    }

    @Test
    void defaultsNameNoRuleAndTheRepeaterRuleIsNoPhysics() {
        assertTrue(PhysicsRules.changed(RuleValues.DEFAULTS).isEmpty());
        assertFalse(PhysicsRules.SPECS.contains(WorldRuleSpecs.SIGNAL_REPEATERS));
        assertEquals(4, WorldRuleSpecs.SIGNAL_REPEATERS.defaultValue());
        assertEquals(0, WorldRuleSpecs.SIGNAL_REPEATERS.min());
        assertEquals(4, WorldRuleSpecs.SIGNAL_REPEATERS.max());
    }
}

package de.lardtom.propwash.freecam;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.freecam.FreecamRules.Denial;
import de.lardtom.propwash.freecam.FreecamRules.End;
import de.lardtom.propwash.freecam.FreecamRules.RunState;
import de.lardtom.propwash.freecam.FreecamRules.StartState;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreecamRulesTest {
    private static final StartState READY = new StartState(true, true, true, true, false, false, false);
    private static final RunState RUNNING = new RunState(true, true, true, true, 20.0F, 20.0F, true, false, false);

    @Test
    void worldRuleIsOffByDefault() {
        RuleSpec spec = WorldRuleSpecs.ALLOW_FREECAM;
        assertEquals("allow_freecam", spec.name());
        assertEquals(RuleSpec.Group.GAMEPLAY, spec.group());
        assertEquals(0, spec.defaultValue());
        assertFalse(RuleValues.DEFAULTS.on(spec));
        assertTrue(WorldRuleSpecs.byName("allow_freecam").isPresent());
    }

    @Test
    void startIsAllowedOnlyWithTheSyncedRuleOn() {
        assertEquals(Denial.NONE, FreecamRules.start(READY));
        assertTrue(FreecamRules.start(READY).allowed());
        assertFalse(FreecamRules.allowedByRule(false, true));
        assertFalse(FreecamRules.allowedByRule(true, false));
        assertTrue(FreecamRules.allowedByRule(true, true));
    }

    @Test
    void everyStartReasonIsReported() {
        assertEquals(Denial.NO_WORLD, FreecamRules.start(new StartState(false, true, true, true, false, false, false)));
        assertEquals(Denial.RULES_UNKNOWN, FreecamRules.start(new StartState(true, false, true, true, false, false, false)));
        assertEquals(Denial.RULE_OFF, FreecamRules.start(new StartState(true, true, false, true, false, false, false)));
        assertEquals(Denial.DEAD, FreecamRules.start(new StartState(true, true, true, false, false, false, false)));
        assertEquals(Denial.PILOTING, FreecamRules.start(new StartState(true, true, true, true, true, false, false)));
        assertEquals(Denial.SPECTATING, FreecamRules.start(new StartState(true, true, true, true, false, true, false)));
        assertEquals(Denial.SCREEN_OPEN, FreecamRules.start(new StartState(true, true, true, true, false, false, true)));
    }

    @Test
    void ruleReasonsWinOverEverythingButTheMissingWorld() {
        assertEquals(Denial.RULES_UNKNOWN, FreecamRules.start(new StartState(true, false, false, false, true, true, true)));
        assertEquals(Denial.RULE_OFF, FreecamRules.start(new StartState(true, true, false, false, true, true, true)));
        assertTrue(Denial.RULE_OFF.ruleRelated());
        assertTrue(Denial.RULES_UNKNOWN.ruleRelated());
        assertFalse(Denial.PILOTING.ruleRelated());
    }

    @Test
    void denialTextsAreDistinct() {
        Set<String> keys = new HashSet<>();
        for (Denial denial : Denial.values()) {
            String key = denial.translationKey();
            if (denial == Denial.NONE || denial == Denial.NO_WORLD) {
                assertEquals("", key);
                continue;
            }
            assertTrue(key.startsWith("propwash.freecam.denied"), key);
            assertTrue(keys.add(key), key);
        }
        assertEquals("propwash.freecam.denied", Denial.RULE_OFF.translationKey());
    }

    @Test
    void runningSessionKeepsGoingWhileNothingChanges() {
        assertEquals(End.NONE, FreecamRules.check(RUNNING));
        assertEquals(End.NONE, FreecamRules.check(new RunState(true, true, true, true, 20.0F, 18.0F, true, false, false)));
    }

    @Test
    void everyEndReasonIsReported() {
        assertEquals(End.DISCONNECT, FreecamRules.check(new RunState(false, true, true, true, 20.0F, 20.0F, true, false, false)));
        assertEquals(End.DIED, FreecamRules.check(new RunState(true, true, true, false, 20.0F, 20.0F, true, false, false)));
        assertEquals(End.DIED, FreecamRules.check(new RunState(true, true, true, true, 0.0F, 20.0F, true, false, false)));
        assertEquals(End.DIMENSION, FreecamRules.check(new RunState(true, true, true, true, 20.0F, 20.0F, false, false, false)));
        assertEquals(End.RULE_OFF, FreecamRules.check(new RunState(true, true, false, true, 20.0F, 20.0F, true, false, false)));
        assertEquals(End.RULE_OFF, FreecamRules.check(new RunState(true, false, true, true, 20.0F, 20.0F, true, false, false)));
        assertEquals(End.HURT, FreecamRules.check(new RunState(true, true, true, true, 19.0F, 20.0F, true, false, false)));
        assertEquals(End.PILOTING, FreecamRules.check(new RunState(true, true, true, true, 20.0F, 20.0F, true, true, false)));
        assertEquals(End.SPECTATING, FreecamRules.check(new RunState(true, true, true, true, 20.0F, 20.0F, true, false, true)));
    }

    @Test
    void endTextsNameTheReason() {
        assertEquals("", End.NONE.translationKey());
        assertEquals("", End.DISCONNECT.translationKey());
        assertEquals("propwash.freecam.ended", End.KEY.translationKey());
        assertEquals("propwash.freecam.ended", End.BUTTON.translationKey());
        assertEquals("propwash.freecam.ended.hurt", End.HURT.translationKey());
        assertEquals("propwash.freecam.ended.rule", End.RULE_OFF.translationKey());
        assertEquals("propwash.freecam.ended.died", End.DIED.translationKey());
        assertEquals("propwash.freecam.ended.dimension", End.DIMENSION.translationKey());
        assertEquals("propwash.freecam.ended.piloting", End.PILOTING.translationKey());
        assertEquals("propwash.freecam.ended.spectating", End.SPECTATING.translationKey());
    }

    @Test
    void freecamKeyIsF6AndSharedOnlyWithTheVanillaDebugCombination() {
        assertEquals(InputConstants.KEY_F6, KeyDefaults.FREECAM.key());
        assertEquals("key.propwash.freecam", KeyDefaults.FREECAM.mappingName());
        assertTrue(KeyDefaults.OTHER_MODULES.contains(KeyDefaults.FREECAM));
        assertFalse(KeyDefaults.FREECAM.own());
        assertTrue(KeyDefaults.sharesKey(KeyDefaults.FREECAM.mappingName(), KeyDefaults.VANILLA_DEBUG_OPTIONS));
        assertFalse(KeyDefaults.sharesKey(KeyDefaults.FREECAM.mappingName(), "key.screenshot"));
        for (KeyDefaults.Entry entry : KeyDefaults.all()) {
            if (entry != KeyDefaults.FREECAM) {
                assertTrue(entry.key() != InputConstants.KEY_F6 || entry.mouse(), entry.name());
            }
        }
    }
}

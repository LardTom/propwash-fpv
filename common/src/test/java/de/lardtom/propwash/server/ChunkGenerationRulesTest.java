package de.lardtom.propwash.server;

import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkGenerationRulesTest {
    private static final IntPredicate NORMAL_PLAYER = level -> level <= 0;
    private static final IntPredicate OPERATOR = level -> level <= 2;
    private static final IntPredicate OWNER = level -> level <= 4;

    @Test
    void defaultsGenerateForEveryPilot() {
        ServerRulesConfig config = new ServerRulesConfig();
        assertTrue(config.allowUnlimitedRange);
        assertEquals(0, config.unlimitedRangePermissionLevel);
        assertTrue(config.allowsGeneration(true, NORMAL_PLAYER), "Einzelspieler ohne Cheats erzeugt Chunks");
        assertTrue(config.allowsGeneration(false, OPERATOR), "Operatoren erzeugen Chunks");
        assertTrue(config.allowsGeneration(false, NORMAL_PLAYER), "normale Spieler auf Dedicated Servern erzeugen Chunks");
    }

    @Test
    void operatorsOnlyWhenTheServerRaisesTheLevel() {
        ServerRulesConfig config = new ServerRulesConfig();
        config.generateChunksPermissionLevel = 2;
        assertTrue(config.allowsGeneration(true, NORMAL_PLAYER));
        assertTrue(config.allowsGeneration(false, OPERATOR));
        assertFalse(config.allowsGeneration(false, NORMAL_PLAYER));
    }

    @Test
    void switchingOffTheRestrictionLetsEveryoneGenerate() {
        ServerRulesConfig config = new ServerRulesConfig();
        config.onlyGeneratedChunks = false;
        assertTrue(config.allowsGeneration(false, NORMAL_PLAYER));
    }

    @Test
    void nobodyLevelAndDisabledHostBlockEvenTheOwner() {
        ServerRulesConfig config = new ServerRulesConfig();
        config.hostGeneratesChunks = false;
        config.generateChunksPermissionLevel = ServerRulesConfig.GENERATE_NOBODY;
        assertFalse(config.allowsGeneration(true, OWNER));
        config.generateChunksPermissionLevel = 0;
        assertTrue(config.allowsGeneration(false, NORMAL_PLAYER));
    }

    @Test
    void validationClampsTheGenerationLevel() {
        ServerRulesConfig config = new ServerRulesConfig();
        config.generateChunksPermissionLevel = 42;
        ServerRulesConfig.validate(config);
        assertEquals(ServerRulesConfig.GENERATE_NOBODY, config.generateChunksPermissionLevel);
        config.generateChunksPermissionLevel = -3;
        ServerRulesConfig.validate(config);
        assertEquals(0, config.generateChunksPermissionLevel);
    }

    @Test
    void copyKeepsTheGenerationRules() {
        ServerRulesConfig config = new ServerRulesConfig();
        config.hostGeneratesChunks = false;
        config.generateChunksPermissionLevel = 3;
        ServerRulesConfig copy = config.copy();
        assertFalse(copy.hostGeneratesChunks);
        assertEquals(3, copy.generateChunksPermissionLevel);
    }
}

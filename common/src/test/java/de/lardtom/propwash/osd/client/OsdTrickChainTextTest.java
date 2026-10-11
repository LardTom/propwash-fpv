package de.lardtom.propwash.osd.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdTrickChainTextTest {
    @Test
    void shortChainsShowEveryName() {
        assertEquals("FLIP", OsdRenderer.chainText(List.of("FLIP")));
        assertEquals("JUICY FLICK + ROLLE", OsdRenderer.chainText(List.of("JUICY FLICK", "ROLLE")));
        assertEquals("FLIP + ROLLE + FLIP", OsdRenderer.chainText(List.of("FLIP", "ROLLE", "FLIP")));
    }

    @Test
    void longChainsKeepTheLatestTricksWithinTheBudget() {
        String text = OsdRenderer.chainText(List.of("YAW-SPIN KOPFÜBER", "RUBIK'S CUBE", "KORKENZIEHER"));
        assertEquals(OsdRenderer.CHAIN_MORE + "RUBIK'S CUBE + KORKENZIEHER", text);
        assertTrue(text.length() <= OsdRenderer.TRICK_NAME_BUDGET + OsdRenderer.CHAIN_MORE.length());
        assertEquals(OsdRenderer.CHAIN_MORE + "DOPPEL-YAW-SPIN", OsdRenderer.chainText(List.of("YAW-SPIN KOPFÜBER", "DOPPEL-YAW-SPIN")));
        assertEquals("", OsdRenderer.chainText(List.of()));
    }
}

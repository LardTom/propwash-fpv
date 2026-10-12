package de.lardtom.propwash.drone.tag;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneTagRulesTest {
    private static Set<String> apply(Set<String> tags, DroneTagRules.Change change) {
        Set<String> out = new HashSet<>(tags);
        change.remove().forEach(out::remove);
        out.addAll(change.add());
        return out;
    }

    @Test
    void tagNamesCarryThePlayerName() {
        assertEquals("pw_owner_Steve", DroneTagRules.ownerTag("Steve"));
        assertEquals("pw_pilot_Alex_99", DroneTagRules.pilotTag("Alex_99"));
        assertEquals("pw_flying", DroneTagRules.FLYING);
        assertEquals("pw_autopilot", DroneTagRules.AUTOPILOT);
    }

    @Test
    void namesKeepTheirCaseBecauseSelectorsCompareExactly() {
        assertEquals("pw_owner_StEvE", DroneTagRules.ownerTag("StEvE"));
    }

    @Test
    void offlineNamesWithOtherCharactersBecomeSelectorSafe() {
        assertEquals("pw_owner_Bot_1", DroneTagRules.ownerTag("Bot.1"));
        assertEquals("pw_pilot___x", DroneTagRules.pilotTag("[ x"));
        assertEquals("pw_owner_J_rg", DroneTagRules.ownerTag("Jörg"));
        assertEquals("pw_owner__", DroneTagRules.ownerTag(""));
        assertEquals("pw_owner__", DroneTagRules.ownerTag(null));
        for (char c : DroneTagRules.ownerTag("a-b+c.d!e,f=g{h}i\"j k").toCharArray()) {
            assertTrue(DroneTagRules.selectorSafe(c), "Zeichen " + c);
        }
    }

    @Test
    void everyValidPlayerNameIsSelectorSafeAsIs() {
        String all = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
        assertEquals(all, DroneTagRules.safeName(all));
    }

    @Test
    void placingAddsTheOwnerTag() {
        DroneTagRules.Change change = DroneTagRules.replace(Set.of(), DroneTagRules.OWNER_PREFIX, "pw_owner_Steve");
        assertEquals(List.of(), change.remove());
        assertEquals(List.of("pw_owner_Steve"), change.add());
    }

    @Test
    void placingAgainReplacesTheOlderOwnerAndKeepsEverythingElse() {
        Set<String> tags = Set.of("pw_owner_Steve", "pw_owner_Old", "pw_pilot_Steve", "pw_flying", "arena", "pw_ownerless");
        Set<String> after = apply(tags, DroneTagRules.replace(tags, DroneTagRules.OWNER_PREFIX, "pw_owner_Alex"));
        assertEquals(Set.of("pw_owner_Alex", "pw_pilot_Steve", "pw_flying", "arena", "pw_ownerless"), after);
    }

    @Test
    void renamedPlayerGetsTheNewNameOnTheNextFlight() {
        Set<String> tags = Set.of("pw_owner_Steve", "pw_pilot_Steve");
        Set<String> after = apply(tags, DroneTagRules.replace(tags, DroneTagRules.PILOT_PREFIX, DroneTagRules.pilotTag("Steve2")));
        assertEquals(Set.of("pw_owner_Steve", "pw_pilot_Steve2"), after);
    }

    @Test
    void sameOwnerAgainChangesNothing() {
        assertTrue(DroneTagRules.replace(Set.of("pw_owner_Steve"), DroneTagRules.OWNER_PREFIX, "pw_owner_Steve").isEmpty());
    }

    @Test
    void replaceRefusesATagOutsideItsPrefix() {
        assertThrows(IllegalArgumentException.class, () -> DroneTagRules.replace(Set.of(), DroneTagRules.OWNER_PREFIX, "pw_pilot_Steve"));
    }

    @Test
    void playerNameReadsBackFromTheTag() {
        assertEquals(Optional.of("Steve"), DroneTagRules.player(Set.of("pw_owner_Steve", "pw_flying"), DroneTagRules.OWNER_PREFIX));
        assertEquals(Optional.empty(), DroneTagRules.player(Set.of("pw_flying", "pw_owner_"), DroneTagRules.OWNER_PREFIX));
        assertEquals(Optional.of("Alex"), DroneTagRules.player(Set.of("pw_pilot_Steve", "pw_pilot_Alex"), DroneTagRules.PILOT_PREFIX));
    }

    @Test
    void flyingNeedsALivingPilot() {
        assertTrue(DroneTagRules.flying(true, true));
        assertFalse(DroneTagRules.flying(true, false));
        assertFalse(DroneTagRules.flying(false, true));
        assertFalse(DroneTagRules.flying(false, false));
    }

    @Test
    void autopilotOnlyWithoutPilot() {
        assertTrue(DroneTagRules.autopilot(false, true));
        assertFalse(DroneTagRules.autopilot(true, true));
        assertFalse(DroneTagRules.autopilot(false, false));
    }

    @Test
    void sessionStartAddsFlyingAndStopRemovesIt() {
        Set<String> parked = Set.of("pw_owner_Steve");
        Set<String> flown = apply(parked, DroneTagRules.flightState(parked, true, false));
        assertEquals(Set.of("pw_owner_Steve", "pw_flying"), flown);
        Set<String> stopped = apply(flown, DroneTagRules.flightState(flown, false, false));
        assertEquals(parked, stopped);
    }

    @Test
    void handoverToTheServerSwapsFlyingForAutopilot() {
        Set<String> flown = Set.of("pw_flying", "pw_pilot_Steve");
        Set<String> rescued = apply(flown, DroneTagRules.flightState(flown, false, true));
        assertEquals(Set.of("pw_autopilot", "pw_pilot_Steve"), rescued);
        Set<String> landed = apply(rescued, DroneTagRules.flightState(rescued, false, false));
        assertEquals(Set.of("pw_pilot_Steve"), landed);
    }

    @Test
    void loadingAfterACrashDropsStaleFlightTags() {
        Set<String> saved = Set.of("pw_owner_Steve", "pw_pilot_Steve", "pw_flying", "pw_autopilot");
        Set<String> loaded = apply(saved, DroneTagRules.flightState(saved, DroneTagRules.flying(false, false),
                DroneTagRules.autopilot(false, false)));
        assertEquals(Set.of("pw_owner_Steve", "pw_pilot_Steve"), loaded);
    }

    @Test
    void unchangedStateIsNoChange() {
        assertTrue(DroneTagRules.flightState(Set.of("pw_flying"), true, false).isEmpty());
        assertTrue(DroneTagRules.flightState(Set.of(), false, false).isEmpty());
    }
}

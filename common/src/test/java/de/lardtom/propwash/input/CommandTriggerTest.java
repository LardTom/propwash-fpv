package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTriggerTest {
    private static final int ALL = 0xFF;
    private static final int S0 = CommandTrigger.bit(0);
    private static final int S1 = CommandTrigger.bit(1);
    private static final CommandTrigger.Gate OPEN = CommandTrigger.Gate.OPEN;
    private static final CommandTrigger.Gate BLOCKED = CommandTrigger.Gate.BLOCKED;
    private static final CommandTrigger.Gate OFF = CommandTrigger.Gate.OFF;

    private static CommandTrigger.Event run(int slot, boolean on) {
        return new CommandTrigger.Event(slot, on, CommandTrigger.Outcome.RUN);
    }

    @Test
    void theCurrentSwitchPositionNeverFiresOnStart() {
        CommandTrigger trigger = new CommandTrigger();
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OPEN, 1000L));
        assertTrue(trigger.primed());
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OPEN, 1016L));
        assertEquals(List.of(run(0, false)), trigger.update(0, ALL, ALL, ALL, OPEN, 1032L));
    }

    @Test
    void flippingOnAndOffRunsTheMatchingCommand() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, ALL, ALL, OPEN, 0L);
        assertEquals(List.of(run(0, true)), trigger.update(S0, ALL, ALL, ALL, OPEN, 100L));
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OPEN, 200L));
        assertEquals(List.of(run(0, false)), trigger.update(0, ALL, ALL, ALL, OPEN, 1000L));
    }

    @Test
    void withoutAnOffCommandSwitchingOffDoesNothingAndCostsNoCooldown() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, S0, 0, OPEN, 0L);
        assertEquals(List.of(run(0, true)), trigger.update(S0, ALL, S0, 0, OPEN, 1000L));
        assertEquals(List.of(), trigger.update(0, ALL, S0, 0, OPEN, 1100L));
        assertEquals(List.of(), trigger.update(S0, ALL, S0, 0, OPEN, 1200L), "Sperrzeit nach dem letzten Befehl");
        assertEquals(List.of(run(0, true)), trigger.update(S0, ALL, S0, 0, OPEN, 1500L), "nach der Sperrzeit läuft der Befehl nach");
        assertEquals(List.of(), trigger.update(0, ALL, S0, 0, OPEN, 1600L));
        assertEquals(List.of(), trigger.update(0, ALL, S0, 0, OPEN, 2600L));
    }

    @Test
    void switchBounceRunsOnlyOnceAndTheFinalPositionWins() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, ALL, ALL, OPEN, 0L);
        assertEquals(List.of(run(0, true)), trigger.update(S0, ALL, ALL, ALL, OPEN, 1000L));
        assertEquals(List.of(), trigger.update(0, ALL, ALL, ALL, OPEN, 1010L));
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OPEN, 1020L));
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OPEN, 1600L), "Schalter steht wieder wie beim letzten Befehl");
        assertEquals(List.of(run(0, false)), trigger.update(0, ALL, ALL, ALL, OPEN, 1700L));
    }

    @Test
    void quickOnOffStillEndsWithTheOffCommand() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, ALL, ALL, OPEN, 0L);
        assertEquals(List.of(run(0, true)), trigger.update(S0, ALL, ALL, ALL, OPEN, 1000L));
        assertEquals(List.of(), trigger.update(0, ALL, ALL, ALL, OPEN, 1100L));
        assertEquals(List.of(), trigger.update(0, ALL, ALL, ALL, OPEN, 1499L));
        assertEquals(List.of(run(0, false)), trigger.update(0, ALL, ALL, ALL, OPEN, 1500L));
    }

    @Test
    void nothingRunsWhileAMenuIsOpenAndNothingCatchesUpAfterwards() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, ALL, ALL, OPEN, 0L);
        assertEquals(List.of(new CommandTrigger.Event(0, true, CommandTrigger.Outcome.BLOCKED)),
                trigger.update(S0, ALL, ALL, ALL, BLOCKED, 1000L));
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, BLOCKED, 1100L));
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OPEN, 2000L), "nach dem Schließen läuft nichts nach");
        assertEquals(List.of(run(0, false)), trigger.update(0, ALL, ALL, ALL, OPEN, 2100L));
    }

    @Test
    void aSwitchWithoutCommandStaysQuietEvenWithAMenuOpen() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, 0, 0, OPEN, 0L);
        assertEquals(List.of(), trigger.update(S0, ALL, 0, 0, BLOCKED, 1000L));
        assertEquals(List.of(), trigger.update(0, ALL, 0, 0, OPEN, 2000L));
    }

    @Test
    void outsideAFlightCommandsAreOffAndReprimeOnTheNextFlight() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, ALL, ALL, OPEN, 0L);
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OFF, 1000L));
        assertFalse(trigger.primed());
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OPEN, 2000L), "Flugstart mit Schalter an löst nichts aus");
        assertEquals(List.of(run(0, false)), trigger.update(0, ALL, ALL, ALL, OPEN, 3000L));
    }

    @Test
    void unreadableSlotsNeverFire() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(S0, ALL, ALL, ALL, OPEN, 0L);
        assertEquals(List.of(), trigger.update(0, S1, ALL, ALL, OPEN, 1000L), "Gerät weg ist kein Ausschalten");
        assertEquals(List.of(), trigger.update(S0, ALL, ALL, ALL, OPEN, 2000L));
    }

    @Test
    void aSlotThatBecomesReadableAgainStartsFromItsCurrentPosition() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(S0, ALL, ALL, ALL, OPEN, 0L);
        assertEquals(List.of(), trigger.update(0, S1, ALL, ALL, OPEN, 1000L));
        assertEquals(List.of(), trigger.update(0, ALL, ALL, ALL, OPEN, 2000L), "wieder lesbar: Stellung ist die neue Ausgangslage");
        assertEquals(List.of(), trigger.update(0, ALL, ALL, ALL, BLOCKED, 3000L));
        assertEquals(List.of(run(0, true)), trigger.update(S0, ALL, ALL, ALL, OPEN, 4000L));
        assertEquals(List.of(), trigger.update(S0, S1, ALL, ALL, BLOCKED, 5000L));
        assertEquals(List.of(), trigger.update(0, ALL, ALL, ALL, BLOCKED, 6000L), "auch bei offenem Fenster keine Meldung");
    }

    @Test
    void manyCommandsAreLimitedBelowTheServerSpamKick() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, ALL, ALL, OPEN, 0L);
        int states = 0;
        int runs = 0;
        int limited = 0;
        for (int slot = 0; slot < CommandTrigger.SLOTS; slot++) {
            states |= CommandTrigger.bit(slot);
            for (CommandTrigger.Event event : trigger.update(states, ALL, ALL, ALL, OPEN, 10_000L)) {
                if (event.outcome() == CommandTrigger.Outcome.RUN) {
                    runs++;
                } else if (event.outcome() == CommandTrigger.Outcome.RATE_LIMITED) {
                    limited++;
                }
            }
        }
        assertEquals(CommandTrigger.BUDGET_MILLIS / CommandTrigger.BUDGET_COST_MILLIS, runs);
        assertEquals(CommandTrigger.SLOTS - runs, limited);
        assertEquals(List.of(), trigger.update(states, ALL, ALL, ALL, OPEN, 10_100L));
        assertEquals(List.of(run(0, false)), trigger.update(states & ~S0, ALL, ALL, ALL, OPEN, 11_000L));
        assertEquals(List.of(new CommandTrigger.Event(1, false, CommandTrigger.Outcome.RATE_LIMITED)),
                trigger.update(states & ~S0 & ~S1, ALL, ALL, ALL, OPEN, 11_100L));
    }

    @Test
    void sustainedRateStaysAtOneCommandPerSecond() {
        CommandTrigger trigger = new CommandTrigger();
        trigger.update(0, ALL, ALL, ALL, OPEN, 0L);
        int state = 0;
        int runs = 0;
        long now = 0L;
        for (int i = 0; i < 200; i++) {
            now += 250L;
            state ^= ALL;
            for (CommandTrigger.Event event : trigger.update(state, ALL, ALL, ALL, OPEN, now)) {
                if (event.outcome() == CommandTrigger.Outcome.RUN) {
                    runs++;
                }
            }
        }
        long seconds = now / 1000L;
        assertTrue(runs <= seconds + CommandTrigger.BUDGET_MILLIS / CommandTrigger.BUDGET_COST_MILLIS, runs + " in " + seconds + " s");
        assertTrue(runs >= seconds - 1, runs + " in " + seconds + " s");
    }

    @Test
    void aKeyRunsTheOnCommandEveryPress() {
        CommandTrigger trigger = new CommandTrigger();
        assertEquals(run(2, true), trigger.keyPress(2, true, false, OPEN, 0L));
        assertNull(trigger.keyPress(2, true, false, OPEN, 100L), "Sperrzeit");
        assertEquals(run(2, true), trigger.keyPress(2, true, false, OPEN, 600L));
        assertFalse(trigger.keyLatched(2));
    }

    @Test
    void aKeyWithAnOffCommandAlternates() {
        CommandTrigger trigger = new CommandTrigger();
        assertEquals(run(1, true), trigger.keyPress(1, true, true, OPEN, 0L));
        assertTrue(trigger.keyLatched(1));
        assertEquals(run(1, false), trigger.keyPress(1, true, true, OPEN, 1000L));
        assertFalse(trigger.keyLatched(1));
        assertEquals(run(1, true), trigger.keyPress(1, true, true, OPEN, 2000L));
        trigger.update(0, ALL, ALL, ALL, OFF, 3000L);
        assertFalse(trigger.keyLatched(1), "neuer Flug beginnt mit dem Einschalt-Befehl");
    }

    @Test
    void anEmptyKeyReportsItAndNothingRunsOutsideAFlight() {
        CommandTrigger trigger = new CommandTrigger();
        assertEquals(new CommandTrigger.Event(4, true, CommandTrigger.Outcome.EMPTY), trigger.keyPress(4, false, false, OPEN, 0L));
        assertNull(trigger.keyPress(4, true, true, OFF, 0L));
        assertEquals(new CommandTrigger.Event(4, true, CommandTrigger.Outcome.BLOCKED), trigger.keyPress(4, true, true, BLOCKED, 0L));
        assertFalse(trigger.keyLatched(4));
        assertNull(trigger.keyPress(-1, true, true, OPEN, 0L));
        assertNull(trigger.keyPress(CommandTrigger.SLOTS, true, true, OPEN, 0L));
    }
}

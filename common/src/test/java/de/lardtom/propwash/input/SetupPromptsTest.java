package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupPromptsTest {
    private static final long S = 1_000_000_000L;
    private static final long T0 = 50 * S;
    private static final Function<String, SetupStatus> ALL_NEW = guid -> SetupStatus.NEW;
    private static final List<SetupPrompts.Device> RADIO = List.of(new SetupPrompts.Device("radio", true));

    private static SetupPrompts.Step prompt(String guid) {
        return new SetupPrompts.Step(SetupPrompts.Action.PROMPT, guid);
    }

    private static SetupPrompts.Step notify(String guid) {
        return new SetupPrompts.Step(SetupPrompts.Action.NOTIFY, guid);
    }

    private static long promptNow(SetupPrompts prompts, String guid, long now) {
        prompts.update(List.of(new SetupPrompts.Device(guid, true)), ALL_NEW, now);
        assertNull(prompts.poll(now, true, true));
        long at = now + Math.max(SetupPrompts.CONNECT_DELAY_NANOS, SetupPrompts.SETTLE_NANOS);
        assertEquals(prompt(guid), prompts.poll(at, true, true));
        return at;
    }

    @Test
    void newDeviceIsPromptedOnlyAfterTheConnectDelayAtASettledSafeMoment() {
        SetupPrompts prompts = new SetupPrompts();
        assertEquals(List.of("radio"), prompts.update(RADIO, ALL_NEW, T0));
        assertEquals(List.of(), prompts.update(RADIO, ALL_NEW, T0 + S / 10));
        assertNull(prompts.poll(T0, true, true));
        assertNull(prompts.poll(T0 + SetupPrompts.CONNECT_DELAY_NANOS, true, true));
        assertEquals(prompt("radio"), prompts.poll(T0 + SetupPrompts.SETTLE_NANOS, true, true));
        assertTrue(prompts.busy());
        assertNull(prompts.poll(T0 + 10 * S, true, true));
    }

    @Test
    void neverPopsUpWhileFlyingOrInAScreenButNotifiesOnce() {
        SetupPrompts prompts = new SetupPrompts();
        prompts.update(RADIO, ALL_NEW, T0);
        assertNull(prompts.poll(T0 + S / 2, true, false));
        assertEquals(notify("radio"), prompts.poll(T0 + 2 * S, true, false));
        assertNull(prompts.poll(T0 + 3 * S, true, false));
        assertNull(prompts.poll(T0 + 60 * S, true, false));
        assertNull(prompts.poll(T0 + 61 * S, true, true));
        assertNull(prompts.poll(T0 + 61 * S + SetupPrompts.SETTLE_NANOS - 1, true, true));
        assertEquals(prompt("radio"), prompts.poll(T0 + 61 * S + SetupPrompts.SETTLE_NANOS, true, true));
    }

    @Test
    void titleScreenNeitherPromptsNorNotifies() {
        SetupPrompts prompts = new SetupPrompts();
        prompts.update(RADIO, ALL_NEW, T0);
        for (long t = T0; t < T0 + 30 * S; t += S) {
            assertNull(prompts.poll(t, false, false));
        }
        assertNull(prompts.poll(T0 + 30 * S, true, true));
        assertEquals(prompt("radio"), prompts.poll(T0 + 30 * S + SetupPrompts.SETTLE_NANOS, true, true));
    }

    @Test
    void onlyNewOrUnfinishedEligibleDevicesAreQueued() {
        InputConfig config = new InputConfig();
        DeviceProfile done = ProfileDefaults.radio("Boxer", 8);
        done.setupDone = true;
        config.profiles.put("done", done);
        config.profiles.put("half", ProfileDefaults.radio("Pocket", 8));
        config.neverAskFor("skip");
        assertEquals(SetupStatus.COMPLETED, config.setupStatus("done"));
        assertEquals(SetupStatus.INCOMPLETE, config.setupStatus("half"));
        assertEquals(SetupStatus.NEVER, config.setupStatus("skip"));
        assertEquals(SetupStatus.NEW, config.setupStatus("fresh"));

        SetupPrompts prompts = new SetupPrompts();
        List<SetupPrompts.Device> devices = List.of(new SetupPrompts.Device("done", true), new SetupPrompts.Device("half", true),
                new SetupPrompts.Device("skip", true), new SetupPrompts.Device("fresh", true), new SetupPrompts.Device("wheel", false),
                new SetupPrompts.Device("fresh", true));
        assertEquals(List.of("half", "fresh"), prompts.update(devices, config::setupStatus, T0));
        long at = T0 + 5 * S;
        prompts.poll(T0, true, true);
        assertEquals(prompt("half"), prompts.poll(at, true, true));
        prompts.answer("half", SetupPrompts.Answer.NEVER, at);
        assertEquals(prompt("fresh"), prompts.poll(at + S, true, true));
        prompts.answer("fresh", SetupPrompts.Answer.NEVER, at + S);
        assertNull(prompts.poll(at + 1000 * S, true, true));
        assertFalse(prompts.waiting("wheel"));
    }

    @Test
    void laterRemindsAtMostOncePerSession() {
        SetupPrompts prompts = new SetupPrompts();
        long t = promptNow(prompts, "radio", T0);
        prompts.answer("radio", SetupPrompts.Answer.LATER, t);
        assertTrue(prompts.waiting("radio"));
        assertEquals(1, prompts.reminders("radio"));
        assertNull(prompts.poll(t + S, true, true));
        assertNull(prompts.poll(t + SetupPrompts.REMIND_DELAY_NANOS - 1, true, true));
        assertEquals(prompt("radio"), prompts.poll(t + SetupPrompts.REMIND_DELAY_NANOS, true, true));
        long second = t + SetupPrompts.REMIND_DELAY_NANOS;
        prompts.answer("radio", SetupPrompts.Answer.LATER, second);
        assertFalse(prompts.waiting("radio"));
        assertTrue(prompts.dismissed("radio"));
        prompts.update(List.of(), ALL_NEW, second + S);
        assertEquals(List.of(), prompts.update(RADIO, ALL_NEW, second + 2 * S));
        for (long now = second; now < second + 3 * SetupPrompts.REMIND_DELAY_NANOS; now += 60 * S) {
            assertNull(prompts.poll(now, true, true));
        }
    }

    @Test
    void reminderWaitsForTheNextSafeMomentAndNotifiesAgain() {
        SetupPrompts prompts = new SetupPrompts();
        long t = promptNow(prompts, "radio", T0);
        prompts.answer("radio", SetupPrompts.Answer.LATER, t);
        long due = t + SetupPrompts.REMIND_DELAY_NANOS;
        assertEquals(notify("radio"), prompts.poll(due + S, true, false));
        assertNull(prompts.poll(due + 2 * S, true, true));
        assertEquals(prompt("radio"), prompts.poll(due + 2 * S + SetupPrompts.SETTLE_NANOS, true, true));
    }

    @Test
    void finishingTheWizardResolvesAndCancellingCountsAsLater() {
        SetupPrompts prompts = new SetupPrompts();
        long t = promptNow(prompts, "radio", T0);
        prompts.answer("radio", SetupPrompts.Answer.SET_UP, t);
        assertTrue(prompts.busy());
        assertNull(prompts.poll(t + 100 * S, true, true));
        prompts.setupClosed("radio", false, t + 100 * S);
        assertFalse(prompts.busy());
        assertEquals(1, prompts.reminders("radio"));
        long due = t + 100 * S + SetupPrompts.REMIND_DELAY_NANOS;
        assertNull(prompts.poll(due - S, true, true));
        assertEquals(prompt("radio"), prompts.poll(due, true, true));
        prompts.answer("radio", SetupPrompts.Answer.SET_UP, due);
        prompts.setupClosed("radio", true, due + S);
        assertFalse(prompts.waiting("radio"));
        assertEquals(List.of(), prompts.update(RADIO, guid -> SetupStatus.COMPLETED, due + 2 * S));
        assertNull(prompts.poll(due + 100 * S, true, true));
    }

    @Test
    void completedElsewhereWhileWaitingIsNeverPrompted() {
        SetupPrompts prompts = new SetupPrompts();
        prompts.update(RADIO, ALL_NEW, T0);
        prompts.update(RADIO, guid -> SetupStatus.COMPLETED, T0 + S / 2);
        assertNull(prompts.poll(T0, true, true));
        assertNull(prompts.poll(T0 + 10 * S, true, true));
        assertFalse(prompts.waiting("radio"));
    }

    @Test
    void unpluggedBeforeThePromptWaitsForTheNextConnect() {
        SetupPrompts prompts = new SetupPrompts();
        prompts.update(RADIO, ALL_NEW, T0);
        prompts.update(List.of(), ALL_NEW, T0 + S / 2);
        prompts.poll(T0, true, true);
        assertNull(prompts.poll(T0 + 10 * S, true, true));
        assertTrue(prompts.waiting("radio"));
        assertNull(prompts.firstWaiting());
        assertEquals(List.of("radio"), prompts.update(RADIO, ALL_NEW, T0 + 20 * S));
        assertEquals("radio", prompts.firstWaiting());
        assertNull(prompts.poll(T0 + 20 * S + SetupPrompts.CONNECT_DELAY_NANOS - 1, true, true));
        assertEquals(prompt("radio"), prompts.poll(T0 + 20 * S + SetupPrompts.CONNECT_DELAY_NANOS, true, true));
    }

    @Test
    void withdrawnPromptComesBackWithoutUsingTheReminder() {
        SetupPrompts prompts = new SetupPrompts();
        long t = promptNow(prompts, "radio", T0);
        prompts.withdraw("radio");
        assertEquals(0, prompts.reminders("radio"));
        assertTrue(prompts.waiting("radio"));
        assertNull(prompts.poll(t + S, true, false));
        assertNull(prompts.poll(t + 2 * S, true, true));
        assertEquals(prompt("radio"), prompts.poll(t + 2 * S + SetupPrompts.SETTLE_NANOS, true, true));
    }

    @Test
    void onePromptAtATimeInConnectOrder() {
        SetupPrompts prompts = new SetupPrompts();
        prompts.update(List.of(new SetupPrompts.Device("pad", true)), ALL_NEW, T0);
        prompts.update(List.of(new SetupPrompts.Device("pad", true), new SetupPrompts.Device("radio", true)), ALL_NEW, T0 + S);
        prompts.poll(T0, true, true);
        long t = T0 + 5 * S;
        assertEquals(prompt("pad"), prompts.poll(t, true, true));
        assertNull(prompts.poll(t + S, true, true));
        prompts.answer("pad", SetupPrompts.Answer.LATER, t + S);
        assertEquals(prompt("radio"), prompts.poll(t + 2 * S, true, true));
        assertEquals("pad", prompts.firstWaiting());
    }

    @Test
    void neverAskIsRememberedAndClearedBySetup() {
        InputConfig config = new InputConfig();
        config.neverAskFor("radio");
        config.neverAskFor("radio");
        config.neverAskFor(InputConfig.KEYBOARD_GUID);
        config.neverAskFor("");
        assertEquals(List.of("radio"), config.neverAsk);
        InputConfig copy = config.copy();
        copy.neverAsk.clear();
        assertEquals(SetupStatus.NEVER, config.setupStatus("radio"));

        config.profiles.put("radio", ProfileDefaults.radio("Boxer", 8));
        config.profiles.put("pad", ProfileDefaults.gamepad("Pad"));
        assertEquals(SetupStatus.NEVER, config.setupStatus("radio"));
        assertTrue(config.markSetupDone("radio"));
        assertFalse(config.markSetupDone("missing"));
        assertEquals(SetupStatus.COMPLETED, config.setupStatus("radio"));
        assertEquals(List.of(), config.neverAsk);
        assertTrue(config.isNewest("radio"));
    }

    @Test
    void neverAskSanitizeDropsJunkAndKeepsTheNewest() {
        InputConfig config = new InputConfig();
        config.neverAsk = new java.util.ArrayList<>();
        config.neverAsk.add(null);
        config.neverAsk.add("");
        config.neverAsk.add("x".repeat(65));
        config.neverAsk.add(InputConfig.KEYBOARD_GUID);
        for (int i = 0; i < InputConfig.MAX_PROFILES + 3; i++) {
            config.neverAsk.add("g" + i);
        }
        config.neverAsk.add("g5");
        config.sanitize();
        assertEquals(InputConfig.MAX_PROFILES, config.neverAsk.size());
        assertFalse(config.neverAsk.contains("g2"));
        assertTrue(config.neverAsk.contains("g5"));
        assertEquals("g" + (InputConfig.MAX_PROFILES + 2), config.neverAsk.getLast());

        InputConfig empty = new InputConfig();
        empty.neverAsk = null;
        empty.sanitize();
        assertEquals(List.of(), empty.neverAsk);
    }
}

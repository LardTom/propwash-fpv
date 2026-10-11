package de.lardtom.propwash.video.record;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordTriggerTest {
    @Test
    void switchStartsOnAndStopsOff() {
        RecordTrigger trigger = new RecordTrigger();
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.SWITCH, true, false));
        assertEquals(RecordTrigger.Action.START, trigger.update(RecordTrigger.Kind.SWITCH, true, true));
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.SWITCH, true, true));
        assertEquals(RecordTrigger.Action.STOP, trigger.update(RecordTrigger.Kind.SWITCH, true, false));
    }

    @Test
    void switchAlreadyOnWhenTheFlightStartsRecords() {
        RecordTrigger trigger = new RecordTrigger();
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.SWITCH, false, true));
        assertEquals(RecordTrigger.Action.START, trigger.update(RecordTrigger.Kind.SWITCH, true, true));
    }

    @Test
    void momentaryButtonTogglesOnPressOnly() {
        RecordTrigger trigger = new RecordTrigger();
        trigger.update(RecordTrigger.Kind.BUTTON, true, false);
        assertEquals(RecordTrigger.Action.TOGGLE, trigger.update(RecordTrigger.Kind.BUTTON, true, true));
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.BUTTON, true, false));
        assertEquals(RecordTrigger.Action.TOGGLE, trigger.update(RecordTrigger.Kind.BUTTON, true, true));
    }

    @Test
    void latchedButtonTogglesOnEveryFlip() {
        RecordTrigger trigger = new RecordTrigger();
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.LATCH, true, true));
        assertEquals(RecordTrigger.Action.TOGGLE, trigger.update(RecordTrigger.Kind.LATCH, true, false));
        assertEquals(RecordTrigger.Action.TOGGLE, trigger.update(RecordTrigger.Kind.LATCH, true, true));
    }

    @Test
    void buttonHeldWhenTheFlightStartsDoesNothing() {
        RecordTrigger trigger = new RecordTrigger();
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.BUTTON, true, true));
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.BUTTON, true, false));
    }

    @Test
    void endingTheSessionRearms() {
        RecordTrigger trigger = new RecordTrigger();
        trigger.update(RecordTrigger.Kind.SWITCH, true, true);
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.SWITCH, false, true));
        assertEquals(RecordTrigger.Action.START, trigger.update(RecordTrigger.Kind.SWITCH, true, true));
    }

    @Test
    void unassignedDoesNothing() {
        RecordTrigger trigger = new RecordTrigger();
        trigger.update(RecordTrigger.Kind.NONE, true, false);
        assertEquals(RecordTrigger.Action.NONE, trigger.update(RecordTrigger.Kind.NONE, true, true));
    }
}

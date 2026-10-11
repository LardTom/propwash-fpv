package de.lardtom.propwash.input;

import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldSwitchTest {
    @Test
    void holdFunctionsAreToggleableSwitches() {
        assertTrue(SwitchFunction.ALT_HOLD.supportsToggle());
        assertTrue(SwitchFunction.POS_HOLD.supportsToggle());
        assertTrue(SwitchFunction.ALT_HOLD.latchesOnGamepad());
        assertEquals("propwash.input.switch.alt_hold", SwitchFunction.ALT_HOLD.translationKey());
        assertEquals("propwash.input.switch.pos_hold", SwitchFunction.POS_HOLD.translationKey());
        assertEquals(KeyDefaults.ALT_HOLD, FunctionKeys.entry(SwitchFunction.ALT_HOLD));
        assertEquals(KeyDefaults.POS_HOLD, FunctionKeys.entry(SwitchFunction.POS_HOLD));
        assertTrue(KeyDefaults.duplicates(KeyDefaults.all()).isEmpty(), KeyDefaults.duplicates(KeyDefaults.all()).toString());
    }

    @Test
    void holdSwitchesWorkWithRangesInversionAndToggles() {
        DeviceProfile p = new DeviceProfile();
        p.setBinding(SwitchFunction.ALT_HOLD, SwitchAssignment.axisMicros(5, 1300, 2100));
        SwitchAssignment pos = SwitchAssignment.axisMicros(5, 1700, 2100);
        p.setBinding(SwitchFunction.POS_HOLD, pos);
        SwitchEvaluator e = new SwitchEvaluator();
        boolean[] none = new boolean[0];
        int low = e.evaluate(p, new float[]{0, 0, 0, 0, 0, -1.0F}, none);
        assertFalse(SwitchEvaluator.isSet(low, SwitchFunction.ALT_HOLD));
        assertFalse(SwitchEvaluator.isSet(low, SwitchFunction.POS_HOLD));
        int mid = e.evaluate(p, new float[]{0, 0, 0, 0, 0, 0.0F}, none);
        assertTrue(SwitchEvaluator.isSet(mid, SwitchFunction.ALT_HOLD));
        assertFalse(SwitchEvaluator.isSet(mid, SwitchFunction.POS_HOLD));
        int high = e.evaluate(p, new float[]{0, 0, 0, 0, 0, 1.0F}, none);
        assertTrue(SwitchEvaluator.isSet(high, SwitchFunction.ALT_HOLD));
        assertTrue(SwitchEvaluator.isSet(high, SwitchFunction.POS_HOLD));
        pos.invert = true;
        int inverted = e.evaluate(p, new float[]{0, 0, 0, 0, 0, -1.0F}, none);
        assertTrue(SwitchEvaluator.isSet(inverted, SwitchFunction.POS_HOLD));

        DeviceProfile pad = new DeviceProfile();
        pad.setBinding(SwitchFunction.ALT_HOLD, SwitchAssignment.toggleButton(2));
        SwitchEvaluator t = new SwitchEvaluator();
        float[] axes = new float[0];
        assertFalse(SwitchEvaluator.isSet(t.evaluate(pad, axes, new boolean[]{false, false, false}), SwitchFunction.ALT_HOLD));
        assertTrue(SwitchEvaluator.isSet(t.evaluate(pad, axes, new boolean[]{false, false, true}), SwitchFunction.ALT_HOLD));
        assertTrue(SwitchEvaluator.isSet(t.evaluate(pad, axes, new boolean[]{false, false, false}), SwitchFunction.ALT_HOLD));
        t.evaluate(pad, axes, new boolean[]{false, false, true});
        assertFalse(SwitchEvaluator.isSet(t.evaluate(pad, axes, new boolean[]{false, false, false}), SwitchFunction.ALT_HOLD));
    }

    @Test
    void holdBindingsSurviveSanitizing() {
        DeviceProfile p = new DeviceProfile();
        p.setBinding(SwitchFunction.POS_HOLD, SwitchAssignment.toggleButton(4));
        p.sanitize();
        assertTrue(p.binding(SwitchFunction.POS_HOLD).bound());
        assertTrue(p.binding(SwitchFunction.POS_HOLD).toggle);
    }
}

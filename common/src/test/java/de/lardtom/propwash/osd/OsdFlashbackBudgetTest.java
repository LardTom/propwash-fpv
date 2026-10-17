package de.lardtom.propwash.osd;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdFlashbackBudgetTest {
    @Test
    void elementIsOnInEveryPresetAndSitsInTheStatusColumn() {
        for (OsdPreset preset : OsdPreset.values()) {
            assertTrue(preset.enabled(OsdElement.FLASHBACK_BUDGET), preset.key());
        }
        assertSame(OsdElement.Group.STATUS, OsdElement.FLASHBACK_BUDGET.group());
        assertSame(OsdElement.Align.RIGHT, OsdElement.FLASHBACK_BUDGET.align());
        assertTrue(OsdElement.FLASHBACK_BUDGET.movable());
        assertTrue(OsdPreset.MINIMAL.row(OsdElement.FLASHBACK_BUDGET) > OsdPreset.MINIMAL.row(OsdElement.RECORDING));
        assertTrue(OsdPreset.STANDARD.row(OsdElement.FLASHBACK_BUDGET) > OsdPreset.STANDARD.row(OsdElement.RECORDING));
        assertSame(OsdElement.FLASHBACK_BUDGET, OsdElement.byKey("flashback_budget"));
    }

    @Test
    void oldConfigsGainTheElement() {
        OsdConfig config = OsdConfig.defaults();
        config.elements.remove(OsdElement.FLASHBACK_BUDGET.key());
        OsdConfig.sanitize(config);
        assertTrue(config.enabled(OsdElement.FLASHBACK_BUDGET));
        assertTrue(OsdPreset.MINIMAL.matches(config));
    }

    @Test
    void fullWarningOnlyWithoutLimit() {
        OsdData data = new OsdData();
        data.active = true;
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        data.flashbackFill = 1.3;
        data.flashbackLimit = true;
        OsdWarnings.collect(data, warnings);
        assertFalse(warnings.contains(OsdWarnings.Warning.FLASHBACK_FULL));
        data.flashbackLimit = false;
        OsdWarnings.collect(data, warnings);
        assertTrue(warnings.contains(OsdWarnings.Warning.FLASHBACK_FULL));
        data.flashbackFill = 0.9;
        OsdWarnings.collect(data, warnings);
        assertFalse(warnings.contains(OsdWarnings.Warning.FLASHBACK_FULL));
        data.flashbackFill = Double.NaN;
        OsdWarnings.collect(data, warnings);
        assertFalse(warnings.contains(OsdWarnings.Warning.FLASHBACK_FULL));
    }

    @Test
    void resetHidesTheElementAndSampleShowsIt() {
        OsdData data = OsdData.sample();
        assertTrue(Double.isFinite(data.flashbackFill));
        data.reset();
        assertTrue(Double.isNaN(data.flashbackFill));
        assertTrue(data.flashbackLimit);
    }
}

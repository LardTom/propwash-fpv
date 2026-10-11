package de.lardtom.propwash.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneBuildBatteryIssuesTest {
    private static DroneBuild withBattery(DroneBuild base, BatteryType battery) {
        return new DroneBuild(base.frame(), base.stack(), base.motor(), base.prop(), base.video(), battery);
    }

    @Test
    void whoopWithSixCellsIsNotBuildable() {
        DroneBuild build = withBattery(DroneBuild.WHOOP, BatteryType.B6S_1300);
        assertTrue(build.problems().contains(DroneBuild.Issue.BATTERY_FRAME));
        assertFalse(build.buildable());
    }

    @Test
    void heavyLithiumIonDoesNotFitFiveInch() {
        DroneBuild build = withBattery(DroneBuild.FREESTYLE, BatteryType.B6S_4000_LION);
        assertTrue(build.problems().contains(DroneBuild.Issue.BATTERY_FRAME));
    }

    @Test
    void fourCellsOnFiveInchIsWeakButBuildable() {
        DroneBuild build = withBattery(DroneBuild.FREESTYLE, BatteryType.B4S_650);
        assertTrue(build.warnings().contains(DroneBuild.Issue.BATTERY_UNDERSIZED));
        assertFalse(build.problems().contains(DroneBuild.Issue.BATTERY_FRAME));
        assertTrue(build.buildable());
    }

    @Test
    void smallPackOnSevenInchIsWeak() {
        DroneBuild build = withBattery(DroneBuild.LONG_RANGE, BatteryType.B6S_1100);
        assertTrue(build.warnings().contains(DroneBuild.Issue.BATTERY_UNDERSIZED));
        assertTrue(build.buildable());
    }

    @Test
    void presetsHaveNoBatteryIssues() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            assertFalse(preset.problems().contains(DroneBuild.Issue.BATTERY_FRAME), preset.toString());
            assertFalse(preset.warnings().contains(DroneBuild.Issue.BATTERY_UNDERSIZED), preset.toString());
        }
    }
}

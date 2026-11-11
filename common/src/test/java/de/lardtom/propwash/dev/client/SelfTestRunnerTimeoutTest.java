package de.lardtom.propwash.dev.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfTestRunnerTimeoutTest {
    private static final int TICKS_PER_SECOND = 20;
    private static final int TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND;
    private static final double SLOWEST_MEAN_STEP_SECONDS = 57.6 * 60.0 / 713.0;

    @Test
    void shortSelectionsKeepEightyMinutesAndTheWatchdogAtNinetySix() {
        for (int steps : new int[] {0, 1, 18, 100}) {
            assertEquals(80 * TICKS_PER_MINUTE, SelfTestRunner.runTimeoutTicks(steps), steps + " Schritte");
        }
        assertEquals(96L * 60L * 1000L, SelfTestRunner.watchdogMillis(SelfTestRunner.runTimeoutTicks(18)));
    }

    @Test
    void fabricPartTwoOfTheWaveCloseRunsToTheEnd() {
        int steps = 1293;
        int limit = SelfTestRunner.runTimeoutTicks(steps);
        int needed = 86 * TICKS_PER_MINUTE;
        assertTrue(limit >= needed * 1.2, "Teil 2 mit " + steps + " Schritten brauchte 86 min, Grenze " + limit / TICKS_PER_MINUTE + " min");
        assertTrue(SelfTestRunner.watchdogMillis(limit) > limit * 50L, "Wächter vor der Zeitgrenze");
    }

    @Test
    void joinTestWatchdogKeepsFiveMinutesAndFollowsLongerJoinSteps() {
        assertEquals(5L * 60L * 1000L, SelfTestRunner.smokeWatchdogMillis(150 * TICKS_PER_SECOND));
        int joinTicks = 150 * TICKS_PER_SECOND + 18 * TICKS_PER_MINUTE + 30 * TICKS_PER_SECOND;
        long watchdog = SelfTestRunner.smokeWatchdogMillis(joinTicks);
        assertTrue(watchdog >= joinTicks * 50L + 60L * 1000L, "Wächter " + watchdog / 60000L + " min für " + joinTicks / TICKS_PER_MINUTE + " min Beitrittstest");
    }

    @Test
    void limitGrowsWithTheSelectionAndTheWatchdogFollowsIt() {
        int previous = 0;
        for (int steps : new int[] {500, 730, 1000, 1293, 2000, 5000}) {
            int limit = SelfTestRunner.runTimeoutTicks(steps);
            assertTrue(limit >= previous, steps + " Schritte");
            double needed = steps * SLOWEST_MEAN_STEP_SECONDS * 1.2 * TICKS_PER_SECOND;
            assertTrue(limit >= needed, steps + " Schritte brauchen bis " + (int) Math.ceil(needed / TICKS_PER_MINUTE)
                    + " min, Grenze " + limit / TICKS_PER_MINUTE + " min");
            long watchdog = SelfTestRunner.watchdogMillis(limit);
            assertTrue(watchdog >= limit * 50L + 16L * 60L * 1000L, steps + " Schritte: Wächter " + watchdog / 60000L + " min");
            previous = limit;
        }
    }
}

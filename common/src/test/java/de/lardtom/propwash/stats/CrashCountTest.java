package de.lardtom.propwash.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrashCountTest {
    @Test
    void watchReportsEachNewCrashOnceAndRestartsWithEverySession() {
        CrashWatch watch = new CrashWatch();
        assertEquals(0, watch.observe(true, 41, 0));
        assertEquals(1, watch.observe(true, 41, 1));
        assertEquals(0, watch.observe(true, 41, 1));
        assertEquals(2, watch.observe(true, 41, 3), "zwei Crashes in einem Tick");
        assertEquals(0, watch.observe(false, CrashWatch.NONE, 0));
        assertEquals(1, watch.observe(true, 41, 1), "neue Sitzung auf derselben Drohne zählt ab 0");
        assertEquals(1, watch.observe(true, 77, 1), "Wechsel auf eine andere Drohne");
        assertEquals(0, watch.observe(true, 77, 0), "zurückgesetzter Zähler meldet nichts");
        assertEquals(2, watch.observe(true, 77, 2));
    }

    @Test
    void budgetAllowsABurstAndThenFourCrashesPerSecond() {
        CrashBudget budget = new CrashBudget();
        assertEquals(CrashBudget.CAPACITY, budget.admit(100L, 50));
        assertEquals(0, budget.admit(100L, 1));
        assertEquals(0, budget.admit(104L, 1));
        assertEquals(1, budget.admit(105L, 3));
        int granted = 0;
        for (long tick = 106L; tick < 306L; tick++) {
            granted += budget.admit(tick, 1);
        }
        assertEquals(40, granted, "200 Ticks bei je einem Versuch: alle 5 Ticks einer");
        assertEquals(CrashBudget.CAPACITY, budget.admit(10_000L, 20), "nach langer Pause wieder ein voller Vorrat, nicht mehr");
        assertEquals(0, budget.admit(10_000L, 0));
        assertEquals(0, budget.admit(10_000L, -3));
    }
}

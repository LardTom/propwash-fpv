package de.lardtom.propwash.race.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChevronWaveTest {
    private static final int ARC_CHEVRONS = 6;
    private static final float PERIOD_TICKS = 1.0F / ChevronWave.SPEED;
    private static final float STEP = 0.01F;
    private static final float THIRD = PERIOD_TICKS / ChevronWave.PERIOD;

    private static float peak(int stepsLeft) {
        float best = 0.0F;
        float value = -1.0F;
        for (float age = 0.0F; age < PERIOD_TICKS; age += STEP) {
            float brightness = ChevronWave.brightness(stepsLeft, age);
            if (brightness > value) {
                value = brightness;
                best = age;
            }
        }
        return best;
    }

    private static void lightMovesFromTo(int from, int to, String label) {
        float gap = (peak(to) - peak(from)) % PERIOD_TICKS;
        gap = gap < 0.0F ? gap + PERIOD_TICKS : gap;
        assertEquals(THIRD, gap, 0.05F, label + ": der nächste Pfeil in Flugrichtung leuchtet ein Drittel des Takts später");
    }

    @Test
    void lightRunsTowardTheGateOverTheFloorChevrons() {
        for (int fromTarget = 2; fromTarget >= 1; fromTarget--) {
            lightMovesFromTo(ChevronWave.approach(fromTarget), ChevronWave.approach(fromTarget - 1), "Pfeil " + fromTarget + " zu Pfeil " + (fromTarget - 1));
        }
        assertTrue(ChevronWave.brightness(ChevronWave.approach(2), peak(ChevronWave.approach(2))) > 0.99F, "Pfeil 2 erreicht volle Helligkeit");
    }

    @Test
    void lightLeavesTheElementInFlightDirection() {
        for (int count : new int[]{2, 3}) {
            for (int i = 0; i + 1 < count; i++) {
                lightMovesFromTo(ChevronWave.leaving(i, count), ChevronWave.leaving(i + 1, count), "Ausgang " + count + ", Pfeil " + i);
            }
        }
    }

    @Test
    void arcChevronsFollowTheTurnFromEntryToExit() {
        int entryAndArc = ARC_CHEVRONS + 1;
        for (int i = 0; i + 1 < entryAndArc; i++) {
            lightMovesFromTo(ChevronWave.leaving(i, entryAndArc), ChevronWave.leaving(i + 1, entryAndArc), "Bogen, Pfeil " + i);
        }
    }

    @Test
    void brightnessStaysVisibleAndNeverExceedsFull() {
        for (float age = 0.0F; age < 2.0F * PERIOD_TICKS; age += 0.1F) {
            for (int i = 0; i < 7; i++) {
                float value = ChevronWave.brightness(i, age);
                assertTrue(value >= ChevronWave.MIN_GLOW && value <= 1.0F, "Helligkeit " + value);
            }
        }
    }
}

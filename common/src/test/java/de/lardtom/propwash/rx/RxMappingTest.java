package de.lardtom.propwash.rx;

import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.StickMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxMappingTest {
    @Test
    void endpointsGiveZeroAndFifteen() {
        assertEquals(0, RxMapping.strength(-1.0F, -1.0F, 1.0F));
        assertEquals(15, RxMapping.strength(1.0F, -1.0F, 1.0F));
        assertEquals(0, RxMapping.strength(0.2F, 0.2F, 0.8F));
        assertEquals(15, RxMapping.strength(0.8F, 0.2F, 0.8F));
    }

    @Test
    void centreIsEight() {
        assertEquals(8, RxMapping.strength(0.0F, -1.0F, 1.0F));
        assertEquals(8, RxMapping.strength(0.0F, -0.8F, 0.8F));
        assertEquals(8, RxMapping.strength(0.0F, 1.0F, -1.0F));
        assertEquals(8, RxMapping.strength(0.5F, 0.0F, 1.0F));
        assertEquals(8, RxMapping.strength(-0.25F, -1.0F, 0.5F));
    }

    @Test
    void everyStepHasItsOwnStickPosition() {
        for (int level = 0; level <= 15; level++) {
            float t = level <= 8 ? level / 16.0F : 0.5F + (level - 8) / 14.0F;
            float value = -1.0F + 2.0F * t;
            assertEquals(level, RxMapping.strength(value, -1.0F, 1.0F), "Stufe " + level + " bei " + value);
        }
    }

    private static int[] stepWidths(float low, float high, int samples) {
        int[] widths = new int[RxMapping.MAX + 1];
        for (int i = 0; i <= samples; i++) {
            float value = low + (high - low) * i / samples;
            widths[RxMapping.strength(value, low, high)]++;
        }
        return widths;
    }

    @Test
    void stepsBelowAndAboveTheCentreAreEven() {
        int samples = 22400;
        int[] widths = stepWidths(-1.0F, 1.0F, samples);
        for (int level = 1; level <= 7; level++) {
            assertEquals(samples / 16.0, widths[level], 2.0, "Stufe " + level + " unter der Mitte");
        }
        for (int level = 9; level <= 14; level++) {
            assertEquals(samples / 14.0, widths[level], 2.0, "Stufe " + level + " über der Mitte");
        }
        assertEquals(samples / 32.0, widths[0], 2.0, "Stufe 0 am Anschlag");
        assertEquals(samples / 28.0, widths[15], 2.0, "Stufe 15 am Anschlag");
        assertEquals(samples / 32.0 + samples / 28.0, widths[8], 2.0, "Stufe 8 in der Mitte");
        for (int i = 0; i <= samples; i++) {
            float value = -1.0F + 2.0F * i / samples;
            int level = RxMapping.strength(value, -1.0F, 1.0F);
            if (value < -0.0625F - 1.0E-4F) {
                assertTrue(level <= 7, "unter der Mitte Stufe " + level + " bei " + value);
            } else if (value > 1.0F / 14.0F + 1.0E-4F) {
                assertTrue(level >= 9, "über der Mitte Stufe " + level + " bei " + value);
            } else if (value > -0.0625F + 1.0E-4F && value < 1.0F / 14.0F - 1.0E-4F) {
                assertEquals(8, level, "Mitte bei " + value);
            }
        }
    }

    private static int sweep(float from, float to, float low, float high, int start) {
        int state = start;
        int steps = 512;
        for (int i = 0; i <= steps; i++) {
            state = RxMapping.strength(from + (to - from) * i / steps, low, high, state);
        }
        return state;
    }

    @Test
    void centreIsEightComingFromZeroAndFromFull() {
        for (float[] ends : new float[][]{{-1.0F, 1.0F}, {-0.8F, 0.8F}, {1.0F, -1.0F}}) {
            float low = ends[0];
            float high = ends[1];
            assertEquals(8, sweep(low, 0.0F, low, high, RxMapping.strength(low, low, high)), "von 0 kommend, Endpunkte " + low + "/" + high);
            assertEquals(8, sweep(high, 0.0F, low, high, RxMapping.strength(high, low, high)), "von 15 kommend, Endpunkte " + low + "/" + high);
        }
        int released = RxMapping.strength(-1.0F, -1.0F, 1.0F);
        for (float spring : new float[]{-0.6F, -0.2F, 0.04F, -0.03F, 0.01F, 0.0F}) {
            released = RxMapping.strength(spring, -1.0F, 1.0F, released);
        }
        assertEquals(8, released, "Nick ganz unten und losgelassen");
    }

    @Test
    void sameStickPositionGivesTheSameStepFromEveryDirection() {
        int grid = 256;
        for (float[] ends : new float[][]{{-1.0F, 1.0F}, {-0.8F, 0.8F}, {1.0F, -1.0F}, {-0.4F, 0.6F}, {0.0F, 1.0F}}) {
            float low = ends[0];
            float high = ends[1];
            for (int a = 0; a <= grid; a++) {
                float start = -1.0F + 2.0F * a / grid;
                int before = RxMapping.strength(start, low, high);
                for (int b = 0; b <= grid; b++) {
                    float target = -1.0F + 2.0F * b / grid + 0.37F / grid;
                    assertEquals(RxMapping.strength(target, low, high), RxMapping.strength(target, low, high, before),
                            "Stellung " + target + " von " + start + " (Endpunkte " + low + "/" + high + ")");
                }
            }
        }
    }

    @Test
    void wholeCurveIsTheSameUpAndDown() {
        int samples = 8192;
        int[] up = new int[samples + 1];
        int state = RxMapping.NONE;
        for (int i = 0; i <= samples; i++) {
            state = RxMapping.strength(-1.0F + 2.0F * i / samples, -1.0F, 1.0F, state);
            up[i] = state;
        }
        for (int i = samples; i >= 0; i--) {
            float value = -1.0F + 2.0F * i / samples;
            state = RxMapping.strength(value, -1.0F, 1.0F, state);
            assertEquals(up[i], state, "Stellung " + value + " aufwärts " + up[i] + ", abwärts " + state);
        }
        assertEquals(0, up[0]);
        assertEquals(8, up[samples / 2]);
        assertEquals(15, up[samples]);
    }

    @Test
    void centreHoldsEightAcrossTheChannelDeadband() {
        for (float deadband : new float[]{0.0F, 0.01F, 0.02F, 0.08F, DeviceProfile.MAX_DEADBAND}) {
            for (int i = -100; i <= 100; i++) {
                float raw = deadband * i / 100.0F;
                float stick = StickMapper.applyDeadband(raw, deadband);
                assertEquals(8, RxMapping.strength(stick, -1.0F, 1.0F, 0), "Totband " + deadband + ", roh " + raw + ", von 0 kommend");
                assertEquals(8, RxMapping.strength(stick, -1.0F, 1.0F, 15), "Totband " + deadband + ", roh " + raw + ", von 15 kommend");
            }
        }
        for (int i = -60; i <= 60; i++) {
            float axis = i / 1000.0F;
            assertEquals(8, RxMapping.strength(axis, -1.0F, 1.0F), "rohe Achse " + axis);
        }
    }

    @Test
    void lowAboveHighInverts() {
        assertEquals(15, RxMapping.strength(-1.0F, 1.0F, -1.0F));
        assertEquals(0, RxMapping.strength(1.0F, 1.0F, -1.0F));
        assertEquals(4, RxMapping.strength(0.5F, 1.0F, -1.0F));
    }

    @Test
    void equalEndpointsActAsSwitch() {
        assertEquals(15, RxMapping.strength(0.3F, 0.3F, 0.3F));
        assertEquals(15, RxMapping.strength(0.9F, 0.3F, 0.3F));
        assertEquals(0, RxMapping.strength(0.2999F, 0.3F, 0.3F));
        assertEquals(0, RxMapping.strength(-1.0F, 0.3F, 0.3F, 15));
    }

    @Test
    void valuesOutsideTheEndpointsAreClamped() {
        assertEquals(0, RxMapping.strength(-1.0F, -0.5F, 0.5F));
        assertEquals(15, RxMapping.strength(1.0F, -0.5F, 0.5F));
        assertEquals(15, RxMapping.strength(7.0F, -1.0F, 1.0F));
        assertEquals(0, RxMapping.strength(-7.0F, -1.0F, 1.0F));
        assertEquals(15.0F, RxMapping.level(3.0F, -1.0F, 1.0F));
        assertEquals(0.0F, RxMapping.level(-3.0F, -1.0F, 1.0F));
    }

    @Test
    void brokenValuesHoldTheLastStrength() {
        assertEquals(9, RxMapping.strength(Float.NaN, -1.0F, 1.0F, 9));
        assertEquals(0, RxMapping.strength(Float.NaN, -1.0F, 1.0F));
        assertEquals(9, RxMapping.strength(0.5F, Float.NaN, 1.0F, 9));
        assertEquals(9, RxMapping.strength(Float.POSITIVE_INFINITY, -1.0F, 1.0F, 9));
    }

    @Test
    void settingsMapThroughTheirEndpoints() {
        RxSettings settings = RxSettings.EMPTY.withSource(RxSource.GAS).withLow(-0.5F).withHigh(0.5F);
        assertEquals(0, settings.strength(-0.5F, RxMapping.NONE));
        assertEquals(15, settings.strength(0.5F, RxMapping.NONE));
        RxSettings button = RxSettings.EMPTY.withSource(RxSource.button(2));
        assertEquals(0, button.strength(0.0F, RxMapping.NONE));
        assertEquals(15, button.strength(1.0F, RxMapping.NONE));
    }
}

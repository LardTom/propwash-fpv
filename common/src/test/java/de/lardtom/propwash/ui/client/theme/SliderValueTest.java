package de.lardtom.propwash.ui.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderValueTest {
    @Test
    void valueTextStaysCenteredWhereverTheHandleIs() {
        int trackLeft = 200;
        int trackWidth = 150;
        int x0 = trackLeft + 3;
        int x1 = trackLeft + trackWidth - 3;
        for (int textWidth : new int[]{12, 24, 40, 60}) {
            int expected = x0 + (x1 - x0 - textWidth) / 2;
            for (int step = 0; step <= 100; step++) {
                assertEquals(expected, ThemedWidgets.sliderValueX(x0, x1, textWidth), "width " + textWidth + " @ " + step);
            }
        }
    }

    @Test
    void handleOverTheTextMakesRoomForIt() {
        int center = Metrics.sliderHandleCenter(0, 100, 0.5);
        int[] covered = ThemedWidgets.handleCover(40, 20, center, 0, 100);
        int half = Metrics.SLIDER_HANDLE / 2;
        assertArrayEquals(new int[]{center - half, center + half}, covered);
        assertTrue(covered[0] >= 40 && covered[1] <= 60);
    }

    @Test
    void handleBesideTheTextStaysWhole() {
        assertNull(ThemedWidgets.handleCover(40, 20, Metrics.sliderHandleCenter(0, 100, 0.0), 0, 100));
        assertNull(ThemedWidgets.handleCover(40, 20, Metrics.sliderHandleCenter(0, 100, 1.0), 0, 100));
    }

    @Test
    void valueIsCenteredOnEverySliderAndOnlyOutlinedWhereTheHandlePasses() {
        int trackLeft = 200;
        int trackRight = 350;
        int x0 = trackLeft + 3;
        int x1 = trackRight - 3;
        int outlined = 0;
        for (int textWidth : new int[]{12, 24, 40}) {
            int textX = ThemedWidgets.sliderValueX(x0, x1, textWidth);
            assertEquals(x0 + (x1 - x0 - textWidth) / 2, textX);
            for (int step = 0; step <= 100; step++) {
                int center = Metrics.sliderHandleCenter(trackLeft, trackRight - trackLeft, step / 100.0);
                int[] cover = ThemedWidgets.handleCover(textX, textWidth, center, trackLeft, trackRight);
                boolean overlaps = center + Metrics.SLIDER_HANDLE / 2 > textX && center - Metrics.SLIDER_HANDLE / 2 < textX + textWidth;
                assertEquals(overlaps, cover != null, "width " + textWidth + " @ " + step);
                outlined += cover == null ? 0 : 1;
            }
        }
        assertTrue(outlined > 0);
    }
}

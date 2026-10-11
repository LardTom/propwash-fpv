package de.lardtom.propwash.ui.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsTest {
    @Test
    void shortLabelsKeepTheSharedColumn() {
        assertEquals(86, Metrics.labelWidth(158));
        assertEquals(86, Metrics.labelWidth(158, 40));
        assertEquals(86, Metrics.labelWidth(158, 80));
        assertEquals(140, Metrics.labelWidth(280, 100));
    }

    @Test
    void longLabelsTakeRoomFromTheControl() {
        assertEquals(101, Metrics.labelWidth(158, 95));
        assertEquals(196, Metrics.labelWidth(280, 190));
    }

    @Test
    void theControlKeepsItsMinimum() {
        assertEquals(158 - Metrics.CONTROL_MIN, Metrics.labelWidth(158, 300));
        assertEquals(280 - Metrics.CONTROL_MIN, Metrics.labelWidth(280, 400));
    }

    @Test
    void aLongValuePushesTheLabelColumnBackToWhatTheLabelNeeds() {
        assertEquals(122, Metrics.labelWidth(222, 38, 60));
        assertEquals(68, Metrics.labelWidth(222, 38, 154));
        assertEquals(44, Metrics.labelWidth(222, 38, 400));
        assertEquals(101, Metrics.labelWidth(158, 95, 150));
    }

    @Test
    void narrowRowsNeverGetANegativeColumn() {
        assertEquals(0, Metrics.labelWidth(30, 200));
        assertEquals(24, Metrics.labelWidth(64, 200));
    }
}

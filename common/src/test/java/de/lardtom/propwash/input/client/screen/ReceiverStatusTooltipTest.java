package de.lardtom.propwash.input.client.screen;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReceiverStatusTooltipTest {
    private static final Component LONG = Component.literal("EdgeTX Radiomaster Boxer Joystick verbunden – Kanäle eingerichtet");

    @Test
    void truncatedStatusWithoutNoticeShowsTheFullStatus() {
        assertSame(LONG, ReceiverTab.statusTooltip(LONG, "EdgeTX Radiomaster…", false));
    }

    @Test
    void statusThatFitsNeedsNoTooltip() {
        assertNull(ReceiverTab.statusTooltip(LONG, LONG.getString(), false));
    }

    @Test
    void noticeAlwaysGetsATooltip() {
        Component notice = Component.literal("Profil gespeichert");
        assertSame(notice, ReceiverTab.statusTooltip(notice, notice.getString(), true));
    }

    @Test
    void directionSelectorIsWideEnoughForItsText() {
        assertEquals(53, ReceiverTab.cycleWidth(27));
        assertEquals(2, ReceiverTab.channelsPerRow(322, 64, 53));
        assertEquals(1, ReceiverTab.channelsPerRow(225, 64, 53));
        assertEquals(2, ReceiverTab.channelsPerRow(287, 64, 53));
    }

    @Test
    void statusHeightGrowsWithItsLines() {
        assertEquals(14, ReceiverTab.statusHeight(1));
        assertEquals(24, ReceiverTab.statusHeight(2));
        assertEquals(34, ReceiverTab.statusHeight(3));
        assertEquals(14, ReceiverTab.statusHeight(0));
    }
}

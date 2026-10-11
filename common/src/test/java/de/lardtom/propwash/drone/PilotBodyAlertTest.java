package de.lardtom.propwash.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotBodyAlertTest {
    private static final long MS = 1_000_000L;

    private static PilotBodyAlert healthy() {
        PilotBodyAlert alert = new PilotBodyAlert();
        assertFalse(alert.update(20.0F, 0.0F, 20.0F, 300, 300, 0L));
        return alert;
    }

    @Test
    void firstReadingIsOnlyTheBaseline() {
        PilotBodyAlert alert = healthy();
        assertTrue(alert.known());
        assertEquals(0.0F, alert.flash(0L));
        assertEquals(PilotBodyAlert.Notice.NONE, alert.notice(0L));
    }

    @Test
    void hitFlashesBrieflyAndFadesOut() {
        PilotBodyAlert alert = healthy();
        assertTrue(alert.update(16.0F, 0.0F, 20.0F, 300, 300, 1000 * MS));
        float peak = alert.flash(1000 * MS);
        assertEquals(PilotBodyAlert.FLASH_MIN + PilotBodyAlert.FLASH_PER_SHARE * 4.0F / 20.0F, peak, 1.0E-5F);
        assertTrue(alert.flash(1300 * MS) < peak);
        assertEquals(0.0F, alert.flash(1000 * MS + PilotBodyAlert.FLASH_NANOS));
        assertEquals(1, alert.hits());
    }

    @Test
    void bigHitIsCappedAndSmallHitStillVisible() {
        PilotBodyAlert alert = healthy();
        alert.update(1.0F, 0.0F, 20.0F, 300, 300, 10 * MS);
        assertEquals(PilotBodyAlert.FLASH_MAX, alert.flash(10 * MS), 1.0E-5F);
        PilotBodyAlert small = healthy();
        small.update(19.5F, 0.0F, 20.0F, 300, 300, 10 * MS);
        assertTrue(small.flash(10 * MS) >= PilotBodyAlert.FLASH_MIN - 1.0E-5F);
    }

    @Test
    void healingAndTinyChangesDoNotFlash() {
        PilotBodyAlert alert = new PilotBodyAlert();
        alert.update(10.0F, 0.0F, 20.0F, 300, 300, 0L);
        assertFalse(alert.update(12.0F, 0.0F, 20.0F, 300, 300, 50 * MS));
        assertFalse(alert.update(11.995F, 0.0F, 20.0F, 300, 300, 100 * MS));
        assertEquals(0, alert.hits());
    }

    @Test
    void damageTakenByAbsorptionStillCounts() {
        PilotBodyAlert alert = new PilotBodyAlert();
        alert.update(20.0F, 4.0F, 20.0F, 300, 300, 0L);
        assertTrue(alert.update(20.0F, 1.0F, 20.0F, 300, 300, 50 * MS));
    }

    @Test
    void hitNoticeHoldsThenFades() {
        PilotBodyAlert alert = healthy();
        alert.update(17.0F, 0.0F, 20.0F, 300, 300, 0L);
        assertEquals(PilotBodyAlert.Notice.HIT, alert.notice(100 * MS));
        assertEquals(1.0F, alert.noticeAlpha(100 * MS));
        long fading = PilotBodyAlert.NOTICE_NANOS - PilotBodyAlert.NOTICE_FADE_NANOS / 2L;
        assertEquals(0.5F, alert.noticeAlpha(fading), 0.01F);
        assertEquals(PilotBodyAlert.Notice.NONE, alert.notice(PilotBodyAlert.NOTICE_NANOS));
    }

    @Test
    void lowHealthStaysCriticalUntilHealed() {
        PilotBodyAlert alert = healthy();
        alert.update(6.0F, 0.0F, 20.0F, 300, 300, 0L);
        assertTrue(alert.critical());
        assertEquals(PilotBodyAlert.Notice.CRITICAL, alert.notice(60_000 * MS));
        assertEquals(0.3F, alert.healthShare(), 1.0E-5F);
        alert.update(8.0F, 0.0F, 20.0F, 300, 300, 61_000 * MS);
        assertFalse(alert.critical());
        assertEquals(PilotBodyAlert.Notice.NONE, alert.notice(61_000 * MS));
    }

    @Test
    void airShowsWhileTheBodyIsUnderWater() {
        PilotBodyAlert alert = healthy();
        alert.update(20.0F, 0.0F, 20.0F, 150, 300, 0L);
        assertEquals(PilotBodyAlert.Notice.AIR, alert.notice(0L));
        assertEquals(50, alert.airPercent());
        alert.update(20.0F, 0.0F, 20.0F, -20, 300, 0L);
        assertEquals(0, alert.airPercent());
    }

    @Test
    void drowningKeepsShowingAirInsteadOfTheHitNotice() {
        PilotBodyAlert alert = healthy();
        alert.update(20.0F, 0.0F, 20.0F, 0, 300, 0L);
        assertTrue(alert.update(18.0F, 0.0F, 20.0F, 0, 300, 1000 * MS));
        assertEquals(PilotBodyAlert.Notice.AIR, alert.notice(1100 * MS));
        assertTrue(alert.flash(1100 * MS) > 0.0F);
        alert.update(18.0F, 0.0F, 20.0F, 300, 300, 1200 * MS);
        assertEquals(PilotBodyAlert.Notice.HIT, alert.notice(1300 * MS));
    }

    @Test
    void resetForgetsTheBaselineSoRespawnDoesNotFlash() {
        PilotBodyAlert alert = healthy();
        alert.update(2.0F, 0.0F, 20.0F, 300, 300, 0L);
        alert.reset();
        assertFalse(alert.known());
        assertEquals(PilotBodyAlert.Notice.NONE, alert.notice(0L));
        assertFalse(alert.update(20.0F, 0.0F, 20.0F, 300, 300, 10 * MS));
        assertEquals(0.0F, alert.flash(10 * MS));
    }
}

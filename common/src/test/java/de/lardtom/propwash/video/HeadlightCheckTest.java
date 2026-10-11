package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlightCheckTest {
    private static final int PIXELS = 1920 * HeadlightCheck.BAND_ROWS;

    @Test
    void unchangedOrBrighterPictureIsFine() {
        byte[] before = band(PIXELS, 30, 40, 20);
        HeadlightCheck.Result same = HeadlightCheck.compare(before, before.clone(), PIXELS);
        assertEquals(HeadlightCheck.Verdict.PASS, same.verdict());
        assertEquals(PIXELS, same.eligible());
        assertEquals(0, same.darkened());
        byte[] lit = band(PIXELS, 90, 110, 70);
        HeadlightCheck.Result brighter = HeadlightCheck.compare(before, lit, PIXELS);
        assertEquals(HeadlightCheck.Verdict.PASS, brighter.verdict());
        assertEquals(PIXELS, brighter.brightened());
    }

    @Test
    void blackPictureFails() {
        byte[] before = band(PIXELS, 30, 40, 20);
        HeadlightCheck.Result black = HeadlightCheck.compare(before, band(PIXELS, 0, 0, 0), PIXELS);
        assertEquals(HeadlightCheck.Verdict.FAIL, black.verdict());
        assertEquals(PIXELS, black.darkened());
        HeadlightCheck.Result nearlyBlack = HeadlightCheck.compare(before, band(PIXELS, 6, 8, 4), PIXELS);
        assertEquals(HeadlightCheck.Verdict.FAIL, nearlyBlack.verdict());
    }

    @Test
    void darkNightSceneThatTurnsBlackFails() {
        byte[] before = band(PIXELS, 6, 7, 12);
        byte[] after = band(PIXELS, 1, 1, 2);
        assertEquals(HeadlightCheck.Verdict.FAIL, HeadlightCheck.compare(before, after, PIXELS).verdict());
    }

    @Test
    void roundingAndSingleChannelNoiseAreTolerated() {
        byte[] before = band(PIXELS, 30, 40, 20);
        byte[] after = band(PIXELS, 30 - HeadlightCheck.TOLERANCE, 40 - HeadlightCheck.TOLERANCE, 20 - HeadlightCheck.TOLERANCE);
        assertEquals(HeadlightCheck.Verdict.PASS, HeadlightCheck.compare(before, after, PIXELS).verdict());
    }

    @Test
    void aFewDarkerPixelsDoNotSwitchTheEffectOff() {
        byte[] before = band(PIXELS, 30, 40, 20);
        byte[] after = before.clone();
        int few = Math.min(HeadlightCheck.MIN_DARKENED - 1, PIXELS);
        for (int i = 0; i < few; i++) {
            after[i * 4] = 0;
        }
        assertEquals(HeadlightCheck.Verdict.PASS, HeadlightCheck.compare(before, after, PIXELS).verdict());
        int share = (int) Math.floor(PIXELS * HeadlightCheck.DARKENED_SHARE) - 1;
        byte[] edges = before.clone();
        for (int i = 0; i < share; i++) {
            edges[i * 4 + 1] = 0;
        }
        assertEquals(HeadlightCheck.Verdict.PASS, HeadlightCheck.compare(before, edges, PIXELS).verdict());
        byte[] patch = before.clone();
        for (int i = 0; i < share + 2; i++) {
            patch[i * 4 + 1] = 0;
        }
        assertEquals(HeadlightCheck.Verdict.FAIL, HeadlightCheck.compare(before, patch, PIXELS).verdict());
    }

    @Test
    void pitchBlackSceneCannotBeJudged() {
        byte[] before = band(PIXELS, 1, 2, 3);
        HeadlightCheck.Result result = HeadlightCheck.compare(before, band(PIXELS, 0, 0, 0), PIXELS);
        assertEquals(HeadlightCheck.Verdict.INCONCLUSIVE, result.verdict());
        assertEquals(0, result.eligible());
    }

    @Test
    void alphaIsIgnoredAndShortBuffersAreSafe() {
        byte[] before = band(PIXELS, 30, 40, 20);
        byte[] after = before.clone();
        for (int i = 0; i < PIXELS; i++) {
            after[i * 4 + 3] = 0;
        }
        assertEquals(HeadlightCheck.Verdict.PASS, HeadlightCheck.compare(before, after, PIXELS).verdict());
        HeadlightCheck.Result shorter = HeadlightCheck.compare(before, new byte[40], PIXELS);
        assertEquals(10, shorter.pixels());
        assertEquals(HeadlightCheck.Verdict.INCONCLUSIVE, shorter.verdict());
        assertEquals(0, HeadlightCheck.compare(new byte[0], new byte[0], 5).pixels());
    }

    @Test
    void bandsStayInsideThePicture() {
        for (int height : new int[]{1, 2, 3, 4, 5, 240, 1080, 1440}) {
            for (int index = 0; index < 7; index++) {
                int start = HeadlightCheck.bandStart(height, index);
                int rows = HeadlightCheck.bandRows(height);
                assertTrue(start >= 0 && start + rows <= height, "Höhe " + height + ", Prüfung " + index + ": " + start + "+" + rows);
            }
        }
        assertEquals(538, HeadlightCheck.bandStart(1080, 0));
        assertTrue(HeadlightCheck.bandStart(1080, 1) < HeadlightCheck.bandStart(1080, 0));
        assertTrue(HeadlightCheck.bandStart(1080, 2) > HeadlightCheck.bandStart(1080, 0));
    }

    @Test
    void checksRunSoonAfterTheStartThenSettleToARareWatch() {
        HeadlightCheck check = new HeadlightCheck();
        int firstAt = framesUntilDue(check);
        assertEquals(HeadlightCheck.FIRST_FRAME, firstAt);
        for (int i = 0; i < HeadlightCheck.SETTLE_PASSES; i++) {
            check.issue();
            assertTrue(check.inFlight());
            check.frame();
            assertFalse(check.due(), "keine zweite Prüfung, solange eine läuft");
            check.record(pass());
            int wait = framesUntilDue(check);
            int expected = i + 1 < HeadlightCheck.SETTLE_PASSES ? HeadlightCheck.SETTLE_INTERVAL : HeadlightCheck.WATCH_INTERVAL;
            assertEquals(expected, wait, "Abstand nach Prüfung " + (i + 1));
        }
        assertEquals(HeadlightCheck.SETTLE_PASSES, check.passes());
    }

    @Test
    void lostReadbackDoesNotBlockLaterChecks() {
        HeadlightCheck check = new HeadlightCheck();
        framesUntilDue(check);
        check.issue();
        for (int i = 0; i < HeadlightCheck.STALE_FRAMES; i++) {
            check.frame();
            assertFalse(check.due());
        }
        check.frame();
        assertTrue(check.due(), "verlorene Rückmeldung gibt die Prüfung wieder frei");
        check.abandon();
        assertEquals(HeadlightCheck.SETTLE_INTERVAL, framesUntilDue(check));
    }

    @Test
    void restartChecksAgainRightAway() {
        HeadlightCheck check = new HeadlightCheck();
        framesUntilDue(check);
        check.issue();
        check.record(pass());
        check.restart();
        assertEquals(0, check.passes());
        assertFalse(check.inFlight());
        assertEquals(HeadlightCheck.FIRST_FRAME, framesUntilDue(check));
    }

    private static int framesUntilDue(HeadlightCheck check) {
        int frames = 0;
        while (!check.due()) {
            check.frame();
            frames++;
            assertTrue(frames < 100_000, "Prüfung wird nie fällig");
        }
        return frames;
    }

    private static HeadlightCheck.Result pass() {
        return new HeadlightCheck.Result(HeadlightCheck.Verdict.PASS, PIXELS, PIXELS, 0, 0);
    }

    private static byte[] band(int pixels, int red, int green, int blue) {
        byte[] data = new byte[pixels * HeadlightCheck.BYTES_PER_PIXEL];
        for (int i = 0; i < pixels; i++) {
            data[i * 4] = (byte) red;
            data[i * 4 + 1] = (byte) green;
            data[i * 4 + 2] = (byte) blue;
            data[i * 4 + 3] = (byte) 255;
        }
        return data;
    }
}

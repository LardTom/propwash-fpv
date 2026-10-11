package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFitTest {
    @Test
    void textThatFitsStaysUntouched() {
        assertEquals(TextFit.Mode.PLAIN, TextFit.mode(80, 80));
        assertEquals(TextFit.Mode.PLAIN, TextFit.mode(0, 0));
        assertEquals(1.0F, TextFit.factor(80, 120), 1e-6F);
    }

    @Test
    void slightlyTooLongTextIsSqueezedToTheExactWidth() {
        assertEquals(TextFit.Mode.SQUEEZED, TextFit.mode(100, 99));
        assertEquals(TextFit.Mode.SQUEEZED, TextFit.mode(100, 70));
        assertEquals(0.7F, TextFit.factor(100, 70), 1e-6F);
        assertEquals(0.9F, TextFit.factor(100, 90), 1e-6F);
    }

    @Test
    void muchTooLongTextIsCutInsteadOfBecomingUnreadable() {
        assertEquals(TextFit.Mode.CUT, TextFit.mode(100, 69));
        assertEquals(TextFit.Mode.CUT, TextFit.mode(100, 0));
        assertEquals(TextFit.Mode.CUT, TextFit.mode(100, -5));
        assertEquals(1.0F, TextFit.factor(100, 40), 1e-6F);
    }
}

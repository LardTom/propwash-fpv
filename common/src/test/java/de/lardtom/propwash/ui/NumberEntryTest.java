package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberEntryTest {
    private static NumberEntry typed(String keys, char separator) {
        NumberEntry entry = new NumberEntry("", separator, true, true);
        keys.codePoints().forEach(entry::type);
        return entry;
    }

    @Test
    void digitsFromRowAndKeypadArriveAsTextAndAreKept() {
        NumberEntry entry = typed("720", '.');
        assertEquals("720", entry.text());
        assertEquals(3, entry.caret());
        assertEquals(NumberEntry.Status.OK, entry.evaluate(10, 1998).status());
        assertEquals(720.0, entry.evaluate(10, 1998).value());
    }

    @Test
    void bothDecimalKeysBecomeTheLocaleSeparator() {
        assertEquals("0,35", typed("0.35", ',').text());
        assertEquals("0,35", typed("0,35", ',').text());
        assertEquals("0.35", typed("0,35", '.').text());
        assertEquals(0.35, typed("0,35", ',').evaluate(0, 1).value(), 1e-9);
        assertEquals(0.35, NumberEntry.parse("0,35").orElseThrow(), 1e-9);
    }

    @Test
    void onlyOneSeparatorAndNoneForIntegers() {
        assertEquals("1.25", typed("1.2.5", '.').text());
        NumberEntry integers = new NumberEntry("", '.', false, false);
        "12.5".codePoints().forEach(integers::type);
        assertEquals("125", integers.text());
    }

    @Test
    void minusTogglesTheSignAtTheFrontAndIsRejectedWithoutNegatives() {
        NumberEntry entry = typed("12-", '.');
        assertEquals("-12", entry.text());
        entry.type('-');
        assertEquals("12", entry.text());
        NumberEntry positive = new NumberEntry("", '.', true, false);
        assertFalse(positive.type('-'));
        assertEquals("", positive.text());
    }

    @Test
    void backspaceDeleteAndCaretMovement() {
        NumberEntry entry = typed("1234", '.');
        entry.left(false);
        entry.left(false);
        entry.backspace(false);
        assertEquals("134", entry.text());
        entry.delete(false);
        assertEquals("14", entry.text());
        entry.home(false);
        entry.type('9');
        assertEquals("914", entry.text());
        entry.end(false);
        entry.backspace(true);
        assertEquals("", entry.text());
    }

    @Test
    void selectAllReplacesOnTyping() {
        NumberEntry entry = new NumberEntry("670", '.', true, true);
        entry.selectAll();
        assertTrue(entry.hasSelection());
        entry.type('4');
        assertEquals("4", entry.text());
        NumberEntry shift = new NumberEntry("12345", '.', true, true);
        shift.home(false);
        shift.right(true);
        shift.right(true);
        assertEquals("12", shift.selectedText());
        assertEquals("12", shift.cut());
        assertEquals("345", shift.text());
    }

    @Test
    void separatorInsideTheSelectionMayBeReplaced() {
        NumberEntry entry = new NumberEntry("0.5", '.', true, true);
        entry.selectAll();
        entry.type('.');
        entry.type('7');
        assertEquals(".7", entry.text());
        assertEquals(0.7, entry.evaluate(0, 1).value(), 1e-9);
    }

    @Test
    void pastedTextIsFiltered() {
        NumberEntry entry = new NumberEntry("", ',', true, true);
        entry.insert(" -1 2a3.4 °/s");
        assertEquals("-123,4", entry.text());
    }

    @Test
    void lengthIsLimited() {
        NumberEntry entry = typed("12345678901234567890", '.');
        assertEquals(NumberEntry.MAX_LENGTH, entry.text().length());
    }

    @Test
    void evaluationClampsAndRejects() {
        assertEquals(new NumberEntry.Result(NumberEntry.Status.CLAMPED, 1998.0), NumberEntry.evaluate("5000", 10, 1998));
        assertEquals(new NumberEntry.Result(NumberEntry.Status.CLAMPED, 10.0), NumberEntry.evaluate("3", 10, 1998));
        assertEquals(NumberEntry.Status.INVALID, NumberEntry.evaluate("-", 0, 1).status());
        assertEquals(NumberEntry.Status.INVALID, NumberEntry.evaluate(".", 0, 1).status());
        assertEquals(NumberEntry.Status.INVALID, NumberEntry.evaluate("1.2.3", 0, 9).status());
        assertEquals(NumberEntry.Status.EMPTY, NumberEntry.evaluate("  ", 0, 1).status());
        assertEquals(NumberEntry.Status.OK, NumberEntry.evaluate("-5", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).status());
        assertTrue(NumberEntry.evaluate("5000", 10, 1998).usable());
        assertFalse(NumberEntry.evaluate("x", 10, 1998).usable());
    }

    @Test
    void numericPartOfFormattedValues() {
        assertEquals("720", NumberEntry.numericPart("720", '.'));
        assertEquals("0.50", NumberEntry.numericPart("0.50", '.'));
        assertEquals("0,50", NumberEntry.numericPart("0.50", ','));
        assertEquals("5,0", NumberEntry.numericPart("5.0%", ','));
        assertEquals("-12,5", NumberEntry.numericPart("-12,5 °", ','));
        assertEquals("150", NumberEntry.numericPart("150 Hz", '.'));
        assertEquals("", NumberEntry.numericPart("Off", '.'));
        assertEquals(',', NumberEntry.separatorOf("1,5"));
        assertEquals('.', NumberEntry.separatorOf("1.5"));
    }
}

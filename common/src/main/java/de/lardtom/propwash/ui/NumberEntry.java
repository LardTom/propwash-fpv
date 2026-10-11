package de.lardtom.propwash.ui;

import java.util.OptionalDouble;

public final class NumberEntry {
    public static final int MAX_LENGTH = 12;

    public enum Status {
        EMPTY,
        INVALID,
        OK,
        CLAMPED
    }

    public record Result(Status status, double value) {
        public boolean usable() {
            return this.status == Status.OK || this.status == Status.CLAMPED;
        }
    }

    private final char separator;
    private final boolean decimals;
    private final boolean negatives;
    private final StringBuilder text = new StringBuilder();
    private int caret;
    private int anchor;

    public NumberEntry(String initial, char separator, boolean decimals, boolean negatives) {
        this.separator = separator == ',' ? ',' : '.';
        this.decimals = decimals;
        this.negatives = negatives;
        this.insert(initial);
        this.anchor = this.caret;
    }

    public static boolean numericChar(int codepoint) {
        return (codepoint >= '0' && codepoint <= '9') || codepoint == '.' || codepoint == ',' || codepoint == '-' || codepoint == '−';
    }

    public static char separatorOf(String sample) {
        for (int i = 0; i < sample.length(); i++) {
            char c = sample.charAt(i);
            if (c == ',' || c == '.') {
                return c;
            }
        }
        return '.';
    }

    public static String numericPart(String formatted, char separator) {
        StringBuilder out = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            if (!started) {
                if (digit) {
                    started = true;
                    if (i > 0 && (formatted.charAt(i - 1) == '-' || formatted.charAt(i - 1) == '−')) {
                        out.append('-');
                    }
                    out.append(c);
                }
                continue;
            }
            if (digit) {
                out.append(c);
            } else if ((c == '.' || c == ',') && i + 1 < formatted.length() && Character.isDigit(formatted.charAt(i + 1))) {
                out.append(separator);
            } else {
                break;
            }
        }
        return out.toString();
    }

    public String text() {
        return this.text.toString();
    }

    public int caret() {
        return this.caret;
    }

    public int selectionStart() {
        return Math.min(this.caret, this.anchor);
    }

    public int selectionEnd() {
        return Math.max(this.caret, this.anchor);
    }

    public boolean hasSelection() {
        return this.caret != this.anchor;
    }

    public String selectedText() {
        return this.text.substring(this.selectionStart(), this.selectionEnd());
    }

    public void selectAll() {
        this.anchor = 0;
        this.caret = this.text.length();
    }

    public boolean type(int codepoint) {
        if (codepoint >= '0' && codepoint <= '9') {
            return this.put((char) codepoint);
        }
        if (codepoint == '.' || codepoint == ',') {
            if (!this.decimals) {
                return false;
            }
            int existing = this.text.indexOf(String.valueOf(this.separator));
            if (existing >= 0 && (existing < this.selectionStart() || existing >= this.selectionEnd())) {
                return false;
            }
            return this.put(this.separator);
        }
        if (codepoint == '-' || codepoint == '−') {
            return this.toggleSign();
        }
        return false;
    }

    public boolean insert(String pasted) {
        boolean any = false;
        for (int i = 0; i < pasted.length(); i++) {
            char c = pasted.charAt(i);
            if (c == '-' || c == '−') {
                if (this.negatives && this.text.isEmpty() && !this.hasSelection()) {
                    any |= this.put('-');
                }
                continue;
            }
            if (numericChar(c)) {
                any |= this.type(c);
            }
        }
        return any;
    }

    private boolean put(char c) {
        this.removeSelection();
        if (this.text.length() >= MAX_LENGTH) {
            return false;
        }
        if (this.caret == 0 && !this.text.isEmpty() && this.text.charAt(0) == '-') {
            this.caret = 1;
        }
        this.text.insert(this.caret, c);
        this.caret++;
        this.anchor = this.caret;
        return true;
    }

    private boolean toggleSign() {
        if (!this.negatives) {
            return false;
        }
        if (this.hasSelection() && this.selectionStart() == 0 && this.selectionEnd() == this.text.length()) {
            this.text.setLength(0);
            this.text.append('-');
            this.caret = 1;
            this.anchor = 1;
            return true;
        }
        if (!this.text.isEmpty() && this.text.charAt(0) == '-') {
            this.text.deleteCharAt(0);
            this.caret = Math.max(0, this.caret - 1);
            this.anchor = Math.max(0, this.anchor - 1);
            return true;
        }
        if (this.text.length() >= MAX_LENGTH) {
            return false;
        }
        this.text.insert(0, '-');
        this.caret++;
        this.anchor++;
        return true;
    }

    private boolean removeSelection() {
        if (!this.hasSelection()) {
            return false;
        }
        int start = this.selectionStart();
        this.text.delete(start, this.selectionEnd());
        this.caret = start;
        this.anchor = start;
        return true;
    }

    public boolean backspace(boolean toStart) {
        if (this.removeSelection()) {
            return true;
        }
        if (this.caret == 0) {
            return false;
        }
        int from = toStart ? 0 : this.caret - 1;
        this.text.delete(from, this.caret);
        this.caret = from;
        this.anchor = from;
        return true;
    }

    public boolean delete(boolean toEnd) {
        if (this.removeSelection()) {
            return true;
        }
        if (this.caret >= this.text.length()) {
            return false;
        }
        this.text.delete(this.caret, toEnd ? this.text.length() : this.caret + 1);
        this.anchor = this.caret;
        return true;
    }

    public String cut() {
        String selected = this.selectedText();
        this.removeSelection();
        return selected;
    }

    public void left(boolean select) {
        if (!select && this.hasSelection()) {
            this.moveTo(this.selectionStart(), false);
            return;
        }
        this.moveTo(this.caret - 1, select);
    }

    public void right(boolean select) {
        if (!select && this.hasSelection()) {
            this.moveTo(this.selectionEnd(), false);
            return;
        }
        this.moveTo(this.caret + 1, select);
    }

    public void home(boolean select) {
        this.moveTo(0, select);
    }

    public void end(boolean select) {
        this.moveTo(this.text.length(), select);
    }

    public void moveTo(int position, boolean select) {
        this.caret = Math.max(0, Math.min(this.text.length(), position));
        if (!select) {
            this.anchor = this.caret;
        }
    }

    public OptionalDouble parse() {
        return parse(this.text.toString());
    }

    public static OptionalDouble parse(String raw) {
        String text = raw.strip().replace('−', '-').replace(',', '.');
        if (text.isEmpty() || text.equals("-") || text.equals(".") || text.equals("-.")) {
            return OptionalDouble.empty();
        }
        int dots = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.') {
                dots++;
            } else if (c == '-') {
                if (i != 0) {
                    return OptionalDouble.empty();
                }
            } else if (c < '0' || c > '9') {
                return OptionalDouble.empty();
            }
        }
        if (dots > 1) {
            return OptionalDouble.empty();
        }
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    public Result evaluate(double min, double max) {
        return evaluate(this.text.toString(), min, max);
    }

    public static Result evaluate(String text, double min, double max) {
        if (text.strip().isEmpty()) {
            return new Result(Status.EMPTY, Double.NaN);
        }
        OptionalDouble parsed = parse(text);
        if (parsed.isEmpty()) {
            return new Result(Status.INVALID, Double.NaN);
        }
        double value = parsed.getAsDouble();
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        if (Double.isFinite(lo) && value < lo) {
            return new Result(Status.CLAMPED, lo);
        }
        if (Double.isFinite(hi) && value > hi) {
            return new Result(Status.CLAMPED, hi);
        }
        return new Result(Status.OK, value);
    }
}

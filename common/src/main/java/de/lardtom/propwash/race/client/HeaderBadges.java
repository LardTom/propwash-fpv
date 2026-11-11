package de.lardtom.propwash.race.client;

public final class HeaderBadges {
    public static final int GAP = 6;

    public enum Form {
        NONE,
        SHORT,
        FULL
    }

    public record Layout(Form rules, boolean creative) {
    }

    private HeaderBadges() {
    }

    public static Layout place(int start, int limit, boolean rules, int rulesFull, int rulesShort, boolean creative, int creativeWidth) {
        Form[] forms = rules ? new Form[]{Form.FULL, Form.SHORT} : new Form[]{Form.NONE};
        for (Form form : forms) {
            int right = start + width(form, rulesFull, rulesShort);
            if (form != Form.NONE && right >= limit) {
                continue;
            }
            if (!creative || right + GAP + creativeWidth < limit) {
                return new Layout(form, creative);
            }
        }
        if (!rules) {
            return new Layout(Form.NONE, false);
        }
        Form alone = start + width(Form.FULL, rulesFull, rulesShort) < limit ? Form.FULL
                : start + width(Form.SHORT, rulesFull, rulesShort) < limit ? Form.SHORT : Form.NONE;
        return new Layout(alone, false);
    }

    private static int width(Form form, int full, int shortened) {
        return switch (form) {
            case NONE -> 0;
            case SHORT -> GAP + shortened;
            case FULL -> GAP + full;
        };
    }
}

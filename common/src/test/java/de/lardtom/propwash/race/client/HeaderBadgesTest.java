package de.lardtom.propwash.race.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeaderBadgesTest {
    private static final int TITLE = 169;
    private static final int NARROW = 304;
    private static final int WIDE = 411;
    private static final int RULES = 75;
    private static final int RULES_SHORT = 14;
    private static final int CREATIVE = 106;

    private static HeaderBadges.Layout place(int limit, boolean rules, boolean creative) {
        return HeaderBadges.place(TITLE, limit, rules, RULES, RULES_SHORT, creative, CREATIVE);
    }

    @Test
    void narrowHeaderShortensServerRulesSoCreativeFcStays() {
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.SHORT, true), place(NARROW, true, true));
    }

    @Test
    void wideHeaderKeepsBothFull() {
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.FULL, true), place(WIDE, true, true));
    }

    @Test
    void singleBadgesStayFullWhereTheyFit() {
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.FULL, false), place(NARROW, true, false));
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.NONE, true), place(NARROW, false, true));
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.NONE, false), place(NARROW, false, false));
    }

    @Test
    void serverRulesFallBackToShortThenDisappear() {
        int full = TITLE + HeaderBadges.GAP + RULES;
        int shortened = TITLE + HeaderBadges.GAP + RULES_SHORT;
        assertEquals(HeaderBadges.Form.FULL, place(full + 1, true, false).rules());
        assertEquals(HeaderBadges.Form.SHORT, place(full, true, false).rules());
        assertEquals(HeaderBadges.Form.SHORT, place(shortened + 1, true, false).rules());
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.NONE, false), place(shortened, true, false));
    }

    @Test
    void creativeFcNeedsStrictRoomBehindTheServerRules() {
        int fits = TITLE + HeaderBadges.GAP + RULES_SHORT + HeaderBadges.GAP + CREATIVE + 1;
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.SHORT, true), place(fits, true, true));
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.FULL, false), place(fits - 1, true, true));
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.NONE, true), place(TITLE + HeaderBadges.GAP + CREATIVE + 1, false, true));
        assertEquals(new HeaderBadges.Layout(HeaderBadges.Form.NONE, false), place(TITLE + HeaderBadges.GAP + CREATIVE, false, true));
    }
}

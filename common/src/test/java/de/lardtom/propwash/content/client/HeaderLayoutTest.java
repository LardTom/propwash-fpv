package de.lardtom.propwash.content.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderLayoutTest {
    private static final int GAP = 3;
    private static final int MORE = 15;
    private static final int MAX = WorkbenchHost.MAX_HEADER_BUTTONS;
    private static final int SPACE_EN = 77;
    private static final int SPACE_DE = 39;
    private static final Identifier SHARE_ICON = Identifier.fromNamespaceAndPath("propwash", "share/share_code");
    private static final Map<String, Integer> TEXT = Map.of("Share code", 58, "Sharecode", 54, "Code", 24, "Example import", 73, "Import", 32,
            "Size", 20);
    private static final ToIntFunction<Component> WIDTH = label -> Math.min(TEXT.get(label.getString()) + 14, 80);

    @Test
    void fullLabelsWhenEverythingFits() {
        HeaderLayout.Result result = HeaderLayout.fit(200, GAP, MORE, 3, List.of(new int[]{70, 40}, new int[]{50}));
        assertEquals(2, result.shown());
        assertFalse(result.more());
        assertArrayEquals(new int[]{0, 0}, result.variants());
        assertEquals(70 + GAP + 50 + GAP, result.width());
    }

    @Test
    void widestButtonSwitchesToItsShortLabelFirst() {
        HeaderLayout.Result result = HeaderLayout.fit(100, GAP, MORE, 3, List.of(new int[]{70, 40}, new int[]{50}));
        assertEquals(2, result.shown());
        assertEquals(1, result.variant(0));
        assertEquals(0, result.variant(1));
        assertTrue(result.width() <= 100);
    }

    @Test
    void overflowGoesIntoTheMoreButton() {
        HeaderLayout.Result result = HeaderLayout.fit(61, GAP, MORE, 3, List.of(new int[]{70, 40}, new int[]{50}));
        assertEquals(1, result.shown());
        assertTrue(result.more());
        assertEquals(40 + GAP + MORE + GAP, result.width());
    }

    @Test
    void maxShownLimitsTheHeaderEvenWithSpace() {
        HeaderLayout.Result result = HeaderLayout.fit(500, GAP, MORE, 1, List.of(new int[]{13}, new int[]{13}));
        assertEquals(1, result.shown());
        assertTrue(result.more());
    }

    @Test
    void noSpaceLeavesOnlyTheMoreButton() {
        HeaderLayout.Result result = HeaderLayout.fit(10, GAP, MORE, 3, List.of(new int[]{70, 40}));
        assertEquals(0, result.shown());
        assertTrue(result.more());
        assertEquals(MORE + GAP, result.width());
    }

    @Test
    void shareButtonFallsBackToItsIconBeforeAnAddonButtonOverflows() {
        WorkbenchHost.Entry share = share("Share code");
        WorkbenchHost.Entry addon = addon();
        assertArrayEquals(new int[]{72, 38, 13}, share.widths(WIDTH));
        HeaderLayout.Result result = HeaderLayout.fit(SPACE_EN, GAP, MORE, MAX, List.of(share.widths(WIDTH), addon.widths(WIDTH)));
        assertEquals(2, result.shown());
        assertFalse(result.more());
        assertEquals(SHARE_ICON, share.iconAt(result.variant(0)));
        assertEquals("Share code – tooltip", share.titleAt(result.variant(0)).getString());
        assertNull(addon.iconAt(result.variant(1)));
        assertEquals("Import", addon.textAt(result.variant(1)).getString());
        assertTrue(result.width() <= SPACE_EN);
    }

    @Test
    void textOnlyShareButtonPushedTheAddonButtonBehindMore() {
        WorkbenchHost.Entry textOnly = new WorkbenchHost.Entry(Component.literal("Share code"), Component.literal("Code"), null, Component.empty(), () -> {
        });
        HeaderLayout.Result result = HeaderLayout.fit(SPACE_EN, GAP, MORE, MAX, List.of(textOnly.widths(WIDTH), addon().widths(WIDTH)));
        assertEquals(1, result.shown());
        assertTrue(result.more());
    }

    @Test
    void shareButtonAloneKeepsTextShortLabelOrIconByItsOwnSpace() {
        WorkbenchHost.Entry english = share("Share code");
        HeaderLayout.Result wide = HeaderLayout.fit(SPACE_EN, GAP, MORE, MAX, List.of(english.widths(WIDTH)));
        assertEquals(0, wide.variant(0));
        assertNull(english.iconAt(wide.variant(0)));
        assertEquals("Share code", english.textAt(wide.variant(0)).getString());
        HeaderLayout.Result narrow = HeaderLayout.fit(50, GAP, MORE, MAX, List.of(english.widths(WIDTH)));
        assertEquals("Code", english.textAt(narrow.variant(0)).getString());
        assertNull(english.iconAt(narrow.variant(0)));
        WorkbenchHost.Entry german = share("Sharecode");
        HeaderLayout.Result small = HeaderLayout.fit(SPACE_DE, GAP, MORE, MAX, List.of(german.widths(WIDTH)));
        assertEquals(1, small.shown());
        assertEquals(SHARE_ICON, german.iconAt(small.variant(0)));
    }

    @Test
    void addonButtonsKeepTheirOrderAndTheShareButtonStillShrinksFirstToItsShortLabel() {
        WorkbenchHost.Entry size = new WorkbenchHost.Entry(Component.literal("Size"), null, Identifier.fromNamespaceAndPath("propwash_example",
                "workbench/size"), Component.empty(), () -> {
        });
        WorkbenchHost.Entry share = share("Share code");
        HeaderLayout.Result result = HeaderLayout.fit(SPACE_EN, GAP, 17, MAX, List.of(size.widths(WIDTH), share.widths(WIDTH), addon().widths(WIDTH)));
        assertEquals(2, result.shown());
        assertTrue(result.more());
        assertEquals("Code", share.textAt(result.variant(1)).getString());
        assertNull(share.iconAt(result.variant(1)));
    }

    @Test
    void iconStaysReachableWhenTheShortLabelIsAsWideAsTheFullOne() {
        HeaderLayout.Result result = HeaderLayout.fit(20, GAP, MORE, MAX, List.of(new int[]{80, 80, 13}));
        assertEquals(1, result.shown());
        assertEquals(2, result.variant(0));
    }

    private static WorkbenchHost.Entry share(String label) {
        return new WorkbenchHost.Entry(Component.literal(label), Component.literal("Code"), null, Component.literal("tooltip"), () -> {
        }, SHARE_ICON, Component.literal("Share code – tooltip"));
    }

    private static WorkbenchHost.Entry addon() {
        return new WorkbenchHost.Entry(Component.literal("Example import"), Component.literal("Import"), null, Component.empty(), () -> {
        });
    }
}

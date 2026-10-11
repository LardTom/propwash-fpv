package de.lardtom.propwash.join;

import de.lardtom.propwash.join.JoinReport.Action;
import de.lardtom.propwash.join.JoinReport.AddonLine;
import de.lardtom.propwash.join.JoinReport.Relevance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinReportTest {
    private static final AddonInfo JMP_110 = new AddonInfo("justmoreparts", "Propwash: Just More Parts", "1.1.0",
            "https://modrinth.com/mod/propwash-just-more-parts", List.of());
    private static final AddonInfo JMP_100 = new AddonInfo("justmoreparts", "Propwash: Just More Parts", "1.0.0", "", List.of());
    private static final AddonInfo EXAMPLE = new AddonInfo("example_mod", "Example Add-on", "2.3.0", "https://example.org/addon",
            List.of("example_parts"));
    private static final AddonInfo HUD = new AddonInfo("fancy_hud", "Fancy HUD", "1.0", "", List.of());

    private static JoinInfo info(int protocol, AddonInfo... addons) {
        return new JoinInfo("0.5.0", protocol, "builtin", List.of(addons));
    }

    private static List<PartRef> parts(String... spec) {
        List<PartRef> out = new ArrayList<>();
        for (String entry : spec) {
            String[] split = entry.split("=");
            out.add(new PartRef("motor", split[0], split.length > 1 ? split[1] : "h"));
        }
        return out;
    }

    private static AddonLine line(JoinReport report, String modId) {
        for (AddonLine line : report.addons()) {
            if (line.modId().equals(modId)) {
                return line;
            }
        }
        return null;
    }

    @Test
    void identicalSetupsHaveNoDifferences() {
        JoinInfo both = info(13, JMP_110);
        JoinReport report = JoinReport.compute(both, both, parts("justmoreparts:m1"), parts("justmoreparts:m1"));
        assertFalse(report.hasDifferences());
        assertFalse(report.blocking());
    }

    @Test
    void addonMissingOnClientIsGroupedWithItsParts() {
        JoinReport report = JoinReport.compute(info(13, JMP_110), info(13), parts("justmoreparts:m1", "justmoreparts:m2", "justmoreparts:p1"),
                parts());
        assertTrue(report.blocking());
        assertEquals(1, report.addons().size());
        AddonLine jmp = report.addons().getFirst();
        assertEquals(Action.INSTALL, jmp.action());
        assertEquals("1.1.0", jmp.serverVersion());
        assertNull(jmp.clientVersion());
        assertEquals(3, jmp.missingOnClient());
        assertEquals("https://modrinth.com/mod/propwash-just-more-parts", jmp.homepage());
        assertEquals(Relevance.REQUIRED, report.relevance(jmp));
        assertTrue(report.unknown().isEmpty());
    }

    @Test
    void addonOnlyOnClientStaysInactiveInsteadOfBlocking() {
        JoinReport report = JoinReport.compute(info(13), info(13, EXAMPLE), parts(), parts("example_parts:frame", "example_parts:motor"));
        AddonLine example = line(report, "example_mod");
        assertNotNull(example);
        assertEquals(Action.REMOVE, example.action());
        assertEquals(0, example.missingOnServer());
        assertEquals(2, example.inactive());
        assertFalse(example.partsDiffer());
        assertEquals(Relevance.INFO, report.relevance(example));
        assertFalse(report.blocking());
        assertTrue(report.hasDifferences());
        assertEquals(2, report.inactiveParts());
    }

    @Test
    void clientOnlyAddonNextToAMissingServerAddonIsOnlyInformation() {
        JoinReport report = JoinReport.compute(info(13, EXAMPLE), info(13, JMP_110), parts("example_parts:frame"),
                parts("justmoreparts:m1", "justmoreparts:p1"));
        assertTrue(report.blocking());
        assertEquals(Relevance.REQUIRED, report.relevance(line(report, "example_mod")));
        AddonLine jmp = line(report, "justmoreparts");
        assertEquals(Relevance.INFO, report.relevance(jmp));
        assertEquals(2, jmp.inactive());
        assertEquals("example_mod", report.addons().getFirst().modId());
    }

    @Test
    void extraPartsOfAnAddonBothSidesHaveStillBlock() {
        JoinReport report = JoinReport.compute(info(13, JMP_110), info(13, JMP_110), parts("justmoreparts:m1"),
                parts("justmoreparts:m1", "justmoreparts:m2"));
        AddonLine jmp = report.addons().getFirst();
        assertEquals(1, jmp.missingOnServer());
        assertEquals(0, jmp.inactive());
        assertEquals(Action.REINSTALL, jmp.action());
        assertTrue(report.blocking());
    }

    @Test
    void olderAndNewerVersionsGetDifferentActions() {
        JoinReport older = JoinReport.compute(info(13, JMP_110), info(13, JMP_100), parts("justmoreparts:m1=a"), parts("justmoreparts:m1=b"));
        assertEquals(Action.UPDATE, older.addons().getFirst().action());
        assertEquals(1, older.addons().getFirst().different());
        JoinReport newer = JoinReport.compute(info(13, JMP_100), info(13, JMP_110), parts("justmoreparts:m1=a"), parts("justmoreparts:m1=b"));
        assertEquals(Action.SWITCH, newer.addons().getFirst().action());
    }

    @Test
    void sameVersionWithOtherPartDataAsksForReinstall() {
        JoinReport report = JoinReport.compute(info(13, JMP_110), info(13, JMP_110), parts("justmoreparts:m1=a"), parts("justmoreparts:m1=b"));
        assertEquals(Action.REINSTALL, report.addons().getFirst().action());
        assertTrue(report.blocking());
    }

    @Test
    void versionOnlyDifferenceIsLikelyWhenNothingBlocksAndInfoOtherwise() {
        JoinReport quiet = JoinReport.compute(info(13, JMP_110), info(13, JMP_100), parts("justmoreparts:m1"), parts("justmoreparts:m1"));
        assertFalse(quiet.blocking());
        assertEquals(Relevance.LIKELY, quiet.relevance(quiet.addons().getFirst()));
        JoinReport loud = JoinReport.compute(info(13, JMP_110, EXAMPLE), info(13, JMP_100), parts("justmoreparts:m1", "example_parts:m"),
                parts("justmoreparts:m1"));
        assertTrue(loud.blocking());
        assertEquals(Relevance.INFO, loud.relevance(line(loud, "justmoreparts")));
        assertEquals(Relevance.REQUIRED, loud.relevance(line(loud, "example_mod")));
        assertEquals("example_mod", loud.addons().getFirst().modId());
        assertEquals(1, loud.lines(false).size());
    }

    @Test
    void onlyInformationIsNotActionableSoAForeignDisconnectKeepsItsScreen() {
        JoinReport hudOnly = JoinReport.compute(info(13), info(13, HUD), parts(), parts());
        assertTrue(hudOnly.hasDifferences());
        assertFalse(hudOnly.actionable());
        JoinReport quiet = JoinReport.compute(info(13, JMP_110), info(13, JMP_100), parts("justmoreparts:m1"), parts("justmoreparts:m1"));
        assertTrue(quiet.actionable());
        JoinReport loud = JoinReport.compute(info(13, JMP_110, EXAMPLE), info(13, JMP_100), parts("justmoreparts:m1", "example_parts:m"),
                parts("justmoreparts:m1"));
        assertTrue(loud.actionable());
    }

    @Test
    void clientOnlyAddonWithoutPartsIsOnlyInformation() {
        JoinReport report = JoinReport.compute(info(13), info(13, HUD), parts(), parts());
        assertTrue(report.hasDifferences());
        assertFalse(report.blocking());
        assertEquals(Relevance.INFO, report.relevance(report.addons().getFirst()));
        assertTrue(report.lines(true).isEmpty());
    }

    @Test
    void partsWithoutKnownAddonAreUnknown() {
        JoinReport report = JoinReport.compute(info(13), info(13), parts("mystery:m1", "mystery:m2", "other:x=a"), parts("other:x=b", "own:y"));
        assertTrue(report.addons().isEmpty());
        assertEquals(3, report.unknown().size());
        JoinReport.UnknownParts mystery = report.unknown().get(0);
        assertEquals("mystery", mystery.namespace());
        assertEquals(List.of("mystery:m1", "mystery:m2"), mystery.missingOnClient());
        assertEquals("other", report.unknown().get(1).namespace());
        assertEquals(List.of("other:x"), report.unknown().get(1).different());
        assertEquals(List.of(), report.unknown().get(2).missingOnServer());
        assertEquals(List.of("own:y"), report.unknown().get(2).inactive());
        assertFalse(report.unknown().get(2).blocking());
        assertTrue(report.blocking());
        assertFalse(JoinReport.compute(info(13), info(13), parts(), parts("own:y")).blocking());
    }

    @Test
    void protocolMismatchListsPropwashAndEveryAddonDifference() {
        JoinReport report = JoinReport.compute(info(13, JMP_110), new JoinInfo("0.5.1", 14, "other", List.of(HUD)), null, null);
        assertNotNull(report.propwash());
        assertEquals(JoinReport.PropwashLine.Kind.PROTOCOL, report.propwash().kind());
        assertFalse(report.partsChecked());
        assertEquals(2, report.addons().size());
        assertEquals(Relevance.LIKELY, report.relevance(line(report, "justmoreparts")));
        assertEquals(Relevance.INFO, report.relevance(line(report, "fancy_hud")));
        JoinReport withParts = JoinReport.compute(info(13), new JoinInfo("0.5.1", 14, "other", List.of(EXAMPLE)), null, null);
        assertEquals(Relevance.INFO, withParts.relevance(withParts.addons().getFirst()));
    }

    @Test
    void builtinDifferenceIsAPropwashLine() {
        JoinReport report = JoinReport.compute(new JoinInfo("0.5.0", 13, "aaa", List.of()), new JoinInfo("0.5.0-dev.4", 13, "bbb", List.of()),
                parts(), parts());
        assertNotNull(report.propwash());
        assertEquals(JoinReport.PropwashLine.Kind.BUILTINS, report.propwash().kind());
        assertTrue(report.blocking());
    }

    @Test
    void namespaceFromEitherSideFindsTheAddon() {
        JoinReport report = JoinReport.compute(info(13), info(13, EXAMPLE), parts("example_parts:only_server"), parts());
        AddonLine example = line(report, "example_mod");
        assertNotNull(example);
        assertEquals(1, example.missingOnClient());
        assertTrue(report.unknown().isEmpty());
    }
}

package de.lardtom.propwash.guard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTest {
    @Test
    void logLineNamesModCategoryCountAndIds() {
        GuardReport report = new GuardReport();
        assertTrue(report.add("nsprobe", GuardCategory.REGISTRY, "propwash:nsprobe_item"));
        assertFalse(report.add("nsprobe", GuardCategory.REGISTRY, "propwash:nsprobe_item"));
        report.add("nsprobe", GuardCategory.TRANSLATION, "propwash.nsprobe.text");
        report.add("nsprobe", GuardCategory.TRANSLATION, "key.propwash.nsprobe");
        assertEquals(List.of(
                "Mod 'nsprobe' uses the reserved namespace propwash (registry ids, 1): propwash:nsprobe_item",
                "Mod 'nsprobe' uses the reserved namespace propwash (translation keys, 2): key.propwash.nsprobe, propwash.nsprobe.text"),
                report.drainLogLines());
        assertEquals(3, report.count());
        assertEquals(List.of("nsprobe"), report.mods());
    }

    @Test
    void lineIsCappedAtTwentyIdsAndCountsTheRest() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ids.add(String.format("propwash:id_%02d", i));
        }
        String line = GuardReport.line("many", GuardCategory.DATA, ids);
        assertTrue(line.startsWith("Mod 'many' uses the reserved namespace propwash (data files, 25): propwash:id_00, "), line);
        assertTrue(line.endsWith("propwash:id_19 and 5 more"), line);
        assertFalse(line.contains("propwash:id_20"), line);
        assertEquals(GuardReport.MAX_IDS, line.substring(line.indexOf("): ") + 3).split(", ").length);
        assertEquals("a, b", GuardReport.capped(List.of("a", "b")));
    }

    @Test
    void eachFindingIsLoggedOncePerStart() {
        GuardReport report = new GuardReport();
        report.add("nsprobe", GuardCategory.COMMAND, "/propwash nsprobe");
        assertEquals(1, report.drainLogLines().size());
        report.add("nsprobe", GuardCategory.COMMAND, "/propwash nsprobe");
        assertEquals(List.of(), report.drainLogLines());
        report.add("nsprobe", GuardCategory.COMMAND, "/propwash nsprobe other");
        assertEquals(List.of("Mod 'nsprobe' uses the reserved namespace propwash (commands, 1): /propwash nsprobe other"), report.drainLogLines());
        assertEquals(List.of(), report.drainLogLines());
    }

    @Test
    void givenNameNamesAModThatIsNotLoaded() {
        GuardReport report = new GuardReport();
        report.add("nsprobe", GuardCategory.REGISTRY, "propwash:nsprobe_item");
        report.mixins("nsprobe", List.of("de.lardtom.propwash.input.SwitchFunction"));
        report.name("nsprobe", "Namespace Probe");
        assertEquals("Namespace Probe", NamespaceGuard.name(report, "nsprobe"));
        assertEquals(Map.of("nsprobe", "Namespace Probe"), NamespaceGuard.modNames(report));
        String text = report.text(NamespaceGuard.modNames(report));
        assertTrue(text.contains("\nNamespace Probe (nsprobe)\n"), text);
        assertTrue(text.contains("  Namespace Probe: 1 classes"), text);
    }

    @Test
    void mixinsAreInfoAndNotFindings() {
        GuardReport report = new GuardReport();
        report.mixins("othermod", List.of("de.lardtom.propwash.input.SwitchFunction", "de.lardtom.propwash.drone.DronePiloting"));
        assertEquals(0, report.count());
        assertEquals(List.of(), report.mods());
        assertEquals(List.of("Mod 'othermod' has mixins into 2 Propwash classes (allowed, at own risk): "
                + "de.lardtom.propwash.drone.DronePiloting, de.lardtom.propwash.input.SwitchFunction"), report.drainMixinLines());
        assertEquals(List.of(), report.drainMixinLines());
    }

    @Test
    void serverCategoriesAndTextForCopying() {
        GuardReport report = new GuardReport();
        report.add("nsprobe", GuardCategory.TRANSLATION, "key.propwash.nsprobe");
        report.add("other", GuardCategory.NETWORK, "propwash:other_ping");
        assertEquals(List.of("other"), report.mods(GuardCategory::server));
        assertEquals(1, report.count(GuardCategory::server));
        assertEquals(List.of("propwash:other_ping"), report.ids(GuardCategory.NETWORK));
        String text = report.text(Map.of("nsprobe", "Namespace Probe"));
        assertTrue(text.contains("Namespace Probe (nsprobe)"), text);
        assertTrue(text.contains("  translation keys (1):\n    key.propwash.nsprobe"), text);
        assertTrue(new GuardReport().text(Map.of()).contains("No add-on uses the reserved namespace."));
        assertEquals(Map.of("nsprobe", 1, "other", 1), report.summary());
    }
}

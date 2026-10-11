package de.lardtom.propwash.join;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.build.PartIndex;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientOnlyAddonsTest {
    private static final AddonInfo JMP = new AddonInfo("justmoreparts", "Propwash: Just More Parts", "1.1.0", "", List.of());
    private static final AddonInfo EXAMPLE = new AddonInfo("example_mod", "Example Add-on", "2.3.0", "", List.of("example_parts"));
    private static final AddonInfo HUD = new AddonInfo("fancy_hud", "Fancy HUD", "1.0", "", List.of());

    @AfterEach
    void reset() {
        AddonSession.clear();
    }

    private static JoinInfo info(AddonInfo... addons) {
        return new JoinInfo("0.5.0", 13, "builtin", List.of(addons));
    }

    private static List<PartRef> parts(String... ids) {
        List<PartRef> out = new ArrayList<>();
        for (String id : ids) {
            out.add(new PartRef(id.startsWith("chem:") ? "chemistry" : "motor", id.startsWith("chem:") ? id.substring(5) : id, "h"));
        }
        return out;
    }

    @Test
    void addonTheServerLacksBecomesInactiveWithAllItsNamespaces() {
        ClientOnlyAddons inactive = ClientOnlyAddons.compute(info(), info(EXAMPLE, HUD), parts(),
                parts("example_parts:frame", "example_parts:motor", "chem:example_parts:lihv"));
        assertEquals(Set.of("example_mod", "example_parts"), inactive.namespaces());
        assertEquals(List.of("example_mod"), inactive.addons().stream().map(AddonInfo::modId).toList());
        assertEquals(3, inactive.parts());
        assertEquals(List.of("Example Add-on"), inactive.names());
        assertFalse(inactive.covers("fancy_hud"));
    }

    @Test
    void sameSetupHasNothingInactive() {
        assertSame(ClientOnlyAddons.NONE, ClientOnlyAddons.compute(info(JMP), info(JMP), parts("justmoreparts:m1"), parts("justmoreparts:m1")));
        assertSame(ClientOnlyAddons.NONE, ClientOnlyAddons.compute(info(), info(HUD), parts(), parts()));
    }

    @Test
    void addonOnBothSidesIsNeverSwitchedOff() {
        AddonInfo newer = new AddonInfo("justmoreparts", "Propwash: Just More Parts", "1.2.0", "", List.of("justmoreparts", "justmoreparts_extra"));
        ClientOnlyAddons inactive = ClientOnlyAddons.compute(info(JMP), info(newer), parts("justmoreparts:m1"),
                parts("justmoreparts:m1", "justmoreparts:m2", "justmoreparts_extra:x"));
        assertTrue(inactive.isEmpty());
        assertTrue(JoinReport.compute(info(JMP), info(newer), parts("justmoreparts:m1"), parts("justmoreparts:m1", "justmoreparts_extra:x")).blocking());
    }

    @Test
    void namespaceTheServerUsesStaysActive() {
        assertTrue(ClientOnlyAddons.compute(info(), info(), parts("shared:a"), parts("shared:a", "shared:b")).isEmpty());
        assertTrue(ClientOnlyAddons.compute(info(new AddonInfo("shared", "Shared", "1", "", List.of())), info(), parts(), parts("shared:b")).isEmpty());
    }

    @Test
    void partsWithoutAddonAreSwitchedOffByNamespace() {
        ClientOnlyAddons inactive = ClientOnlyAddons.compute(info(), info(), parts(), parts("loose:a", "loose:b"));
        assertEquals(Set.of("loose"), inactive.namespaces());
        assertTrue(inactive.addons().isEmpty());
        assertEquals(List.of("loose"), inactive.names());
    }

    @Test
    void propwashAndMinecraftAreNeverInactive() {
        ClientOnlyAddons inactive = ClientOnlyAddons.compute(info(), info(), parts(), parts("propwash:secret", "minecraft:stick", "jmp:a"));
        assertEquals(Set.of("jmp"), inactive.namespaces());
        PartAvailability.set(List.of("propwash", "minecraft", "jmp"));
        assertEquals(Set.of("jmp"), PartAvailability.inactiveNamespaces());
    }

    @Test
    void sessionFiltersPartsAndClearsAgain() {
        int frames = PartIndex.all(PartKind.FRAME).size();
        AddonSession.activate(ClientOnlyAddons.compute(info(), info(JMP), parts(), parts("justmoreparts:m1")));
        assertFalse(PartAvailability.active("justmoreparts"));
        assertFalse(PartAvailability.active(Identifier.fromNamespaceAndPath("justmoreparts", "m1")));
        assertFalse(PartAvailability.activeKey("justmoreparts:m1"));
        assertTrue(PartAvailability.activeKey("freestyle_5"));
        assertFalse(PartAvailability.activeNamespacedId("justmoreparts:frames"));
        assertTrue(PartAvailability.activeNamespacedId("tricks"));
        assertTrue(PartIndex.byId(FrameType.FREESTYLE_5.id()).isPresent());
        assertEquals(frames, PartIndex.all(PartKind.FRAME).size());
        assertEquals(List.of("justmoreparts:m1"), SessionItems.unavailableKeys(Map.of(PartKind.MOTOR, "justmoreparts:m1", PartKind.FRAME, "freestyle_5")));
        assertEquals(List.of("justmoreparts:m1"), SessionItems.unavailableIds(List.of(Identifier.fromNamespaceAndPath("justmoreparts", "m1"),
                FrameType.FREESTYLE_5.id())));
        assertTrue(AddonSession.takeAnnouncement());
        assertFalse(AddonSession.takeAnnouncement());
        AddonSession.clear();
        assertTrue(PartAvailability.active("justmoreparts"));
        assertTrue(SessionItems.unavailableKeys(List.of("justmoreparts:m1")).isEmpty());
        assertFalse(AddonSession.takeAnnouncement());
    }

    @Test
    void sessionTextsNameTheAddons() {
        assertEquals("Propwash: Just More Parts is not installed on this server and is inactive for this session.",
                JoinText.sessionMessage(List.of("Propwash: Just More Parts")).getString());
        assertEquals("A, B are not installed on this server and are inactive for this session.", JoinText.sessionMessage(List.of("A", "B")).getString());
        assertEquals("Add-on inactive on this server", JoinText.sessionTitle(1).getString());
        assertEquals("This code uses parts that are not available on this server: justmoreparts:m1",
                JoinText.codeParts(List.of("justmoreparts:m1")).getString());
    }

    @Test
    void reportDetailsSayTheAddonStaysInactive() {
        JoinReport report = JoinReport.compute(info(), info(JMP), parts(), parts("justmoreparts:m1", "justmoreparts:m2", "loose:z"));
        assertFalse(report.blocking());
        String details = JoinText.details(report, null);
        assertTrue(details.contains("Propwash lets you join anyway, Propwash: Just More Parts stays inactive while you play there."), details);
        assertTrue(details.contains("2 parts inactive on this server"), details);
        assertTrue(details.contains("Only on your side, inactive on this server: loose:z"), details);
        assertTrue(details.contains("nothing to do"), details);
        assertFalse(JoinText.disconnect(report).getString().contains("loose"));
    }
}

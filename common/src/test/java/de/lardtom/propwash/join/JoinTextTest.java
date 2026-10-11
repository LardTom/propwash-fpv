package de.lardtom.propwash.join;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinTextTest {
    private static final AddonInfo JMP = new AddonInfo("justmoreparts", "Propwash: Just More Parts", "1.1.0",
            "https://modrinth.com/mod/propwash-just-more-parts", List.of());

    @Test
    void disconnectMessageNamesTheAddonAndWhatToDo() {
        JoinReport report = JoinReport.compute(new JoinInfo("0.5.0", 13, "b", List.of(JMP)), new JoinInfo("0.5.0", 13, "b", List.of()),
                List.of(new PartRef("motor", "justmoreparts:m1", "h")), List.of());
        Component message = JoinText.disconnect(report);
        String text = message.getString();
        assertTrue(text.startsWith("Your Propwash add-ons don't match this server."), text);
        assertTrue(text.contains("• Propwash: Just More Parts (Server: 1.1.0 · You: not installed): Install Propwash: Just More Parts 1.1.0."), text);
        assertTrue(JoinText.ours(message));
        assertFalse(JoinText.ours(Component.literal("Received 151 registry entries that are unknown to this client.")));
        String details = JoinText.details(report, null);
        assertTrue(details.contains("(justmoreparts): Missing on your side."), details);
        assertTrue(details.contains("[1 part missing on your side] https://modrinth.com/mod/propwash-just-more-parts"), details);
    }

    @Test
    void protocolMismatchComesFirstAndLongListsAreCut() {
        List<AddonInfo> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(new AddonInfo("addon_" + i, "Add-on " + i, "1.0", "", List.of()));
        }
        JoinReport report = JoinReport.compute(new JoinInfo("0.5.0", 13, "b", many), new JoinInfo("0.5.1", 14, "c", List.of()), null, null);
        String text = JoinText.disconnect(report).getString();
        String[] lines = text.split("\n");
        assertTrue(lines[1].startsWith("• Propwash FPV (Server: 0.5.0 (network protocol 13) · You: 0.5.1 (network protocol 14)): Install Propwash FPV 0.5.0"),
                lines[1]);
        assertEquals(1 + JoinText.MAX_MESSAGE_LINES + 1, lines.length, text);
        assertEquals("(+5 more)", lines[lines.length - 1]);
    }

    @Test
    void unknownPartsAndForeignReasonAreInTheDetails() {
        JoinReport report = JoinReport.compute(new JoinInfo("0.5.0", 13, "b", List.of()), new JoinInfo("0.5.0", 13, "b", List.of()),
                List.of(new PartRef("motor", "mystery:a", "h"), new PartRef("motor", "mystery:b", "h")), List.of());
        String details = JoinText.details(report, Component.literal("Received 3 registry entries\nthat are unknown"));
        assertTrue(details.contains("Unknown parts (mystery): Only on the server: mystery:a, mystery:b."), details);
        assertTrue(details.contains("Message: Received 3 registry entries that are unknown"), details);
    }

    @Test
    void missingPropwashListsTheServerAddons() {
        String text = JoinText.missingPropwash("0.5.0", 13, List.of(JMP)).getString();
        assertEquals("This server requires Propwash FPV 0.5.0 (network protocol 13). Install exactly this version.\n"
                + "Propwash add-ons on this server: Propwash: Just More Parts 1.1.0", text);
    }
}

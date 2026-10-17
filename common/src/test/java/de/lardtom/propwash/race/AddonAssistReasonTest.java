package de.lardtom.propwash.race;

import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.sim.HoldMode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonAssistReasonTest {
    @Test
    void engagedAddonAssistIsAClientReasonWithItsId() {
        AssistReasons clean = AssistCheck.evaluate(AssistCheck.Setup.clean());
        assertSame(clean, AssistCheck.withAddon(clean, null, false));
        AssistReasons reasons = AssistCheck.withAddon(clean, "example:cine_assist", false);
        assertEquals(LapAssist.OTHER.bit(), reasons.mask());
        assertEquals(List.of(new AssistDetail(LapAssist.OTHER, "example:cine_assist", 0, 0)), reasons.details());
        assertTrue(LapAssist.OTHER.in(LapAssist.CLIENT_REASONS));
        AssistReasons verdict = AssistCheck.lapVerdict(new AssistTimeline(), 0L, 1L, AssistReasons.NONE, reasons);
        assertEquals(LapAssist.OTHER.bit(), verdict.mask());
    }

    @Test
    void holdFlownByTheAddonIsNotBlamedOnTheBuiltInHold() {
        AssistReasons hold = AssistCheck.evaluate(AssistCheck.Setup.clean().withHold(HoldMode.ALTHOLD));
        AssistReasons byAddon = AssistCheck.withAddon(hold, "example:cine_assist", true);
        assertEquals(LapAssist.OTHER.bit(), byAddon.mask());
        AssistReasons both = AssistCheck.withAddon(hold, "example:cine_assist", false);
        assertEquals(LapAssist.OTHER.bit() | LapAssist.HOLD_MODE.bit(), both.mask());
    }

    @Test
    void addonIdsKeepNamespaceCharactersOnlyForOther() {
        assertEquals("my-mod:assist/v2.1", AssistDetail.addon("My-Mod:Assist/v2.1 !?").subject());
        assertEquals("gravityscale", new AssistDetail(LapAssist.WORLD_PHYSICS, "gravity:scale", 0, 0).subject());
    }

    @Test
    void boardRowsKeepEveryDetailOfAnArcadeWorldWithZoneAndCreativeDrone() {
        List<AssistDetail> details = new ArrayList<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            details.add(AssistDetail.rule(spec, spec.defaultValue() + 1));
            if (details.size() == 9) {
                break;
            }
        }
        details.add(AssistDetail.creative());
        details.add(AssistDetail.zone(3, PhysicsRules.SPECS.getFirst(), 0));
        AssistReasons reasons = AssistReasons.of(details);
        assertEquals(11, reasons.details().size());
        RacePayloads.BoardRow row = new RacePayloads.BoardRow("Pilot", "race_5", 1L, 0L, false, 0, 0, reasons.mask(), reasons.details());
        assertEquals(11, row.details().size());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.writeDetails(buf, row.details());
        assertEquals(row.details(), RacePayloads.readDetails(buf));
    }
}

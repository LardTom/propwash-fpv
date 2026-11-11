package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateStackLimitPreviewTest {
    private static final GatePlacement.Preference STANDING = GatePlacement.Preference.DEFAULT;

    private static GatePreview.Target target(boolean free, boolean allowed, GateStacking.StackRefusal refusal) {
        GateGeometry fifth = GateGeometry.of(0.5, 64.0 + 3.5 * GateStacking.MAX_STACK, 0.5, 0.0F, GateSize.MEDIUM);
        return new GatePreview.Target(fifth, null, free, GateMount.Surface.FLOOR, allowed, false, GateStacking.Base.STACKED, false, refusal);
    }

    private static Component pose() {
        GateMount.Surface floor = GateMount.Surface.FLOOR;
        return GateMounting.describe(STANDING.mount(floor), STANDING.turn(floor), STANDING.fine(floor));
    }

    private static String key(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? contents.getKey() : text.getString();
    }

    private static List<Object> args(Component text) {
        return text.getContents() instanceof TranslatableContents contents ? Arrays.asList(contents.getArgs()) : List.of();
    }

    @Test
    void theFifthGateOfAStackNamesTheLimitInsteadOfNoRoom() {
        Component status = GatePreview.status(target(false, true, GateStacking.StackRefusal.TOO_HIGH), STANDING);
        assertEquals("propwash.race.stack.preview_too_high", key(status), "Hinweis beim fünften Gate: " + key(status));
        assertEquals(List.of(pose(), GateStacking.MAX_STACK), args(status), "Lage und Grenze 4 im Hinweis");
    }

    @Test
    void otherStackRefusalsNameTheirReason() {
        assertEquals("propwash.race.stack.preview_too_wide", key(GatePreview.status(target(false, true, GateStacking.StackRefusal.TOO_WIDE), STANDING)));
        assertEquals("propwash.race.stack.preview_occupied", key(GatePreview.status(target(false, true, GateStacking.StackRefusal.OCCUPIED), STANDING)));
    }

    @Test
    void realCollisionsAndBuildRightsKeepTheirTexts() {
        assertEquals(Component.translatable("propwash.race.mount.preview_blocked", pose()),
                GatePreview.status(target(false, true, GateStacking.StackRefusal.BLOCKED), STANDING), "Block oder Kurs-Element im Weg: kein Platz");
        assertEquals(Component.translatable("propwash.race.mount.preview_not_allowed", pose()),
                GatePreview.status(target(false, false, GateStacking.StackRefusal.TOO_HIGH), STANDING), "ohne Baurecht zuerst das");
        assertEquals(pose(), GatePreview.status(target(true, true, GateStacking.StackRefusal.NONE), STANDING), "freier Platz zeigt nur die Lage");
        GatePreview.Target floor = new GatePreview.Target(GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM), null, false, GateMount.Surface.FLOOR,
                true);
        assertEquals(GateStacking.StackRefusal.NONE, floor.refusal(), "am Boden gibt es keine Stapel-Ablehnung");
        assertTrue(key(GatePreview.status(floor, STANDING)).equals("propwash.race.mount.preview_blocked"));
    }
}

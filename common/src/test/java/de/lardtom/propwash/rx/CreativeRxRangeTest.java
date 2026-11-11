package de.lardtom.propwash.rx;

import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scenes.RxBlockScene;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeRxRangeTest {
    private static final double EYE_Y = 65.62;

    private static final class Stone implements RxLink.Cells {
        private int reads;

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            this.reads++;
            return true;
        }
    }

    @Test
    void unlimitedBlockLinksAtAnyDistanceThroughAnyWall() {
        for (int distance : List.of(1, RxLink.MAX_RANGE + 1, 2_000, 29_999_000)) {
            Stone stone = new Stone();
            assertTrue(RxLink.linked(true, 0.5, EYE_Y, 0.5, distance, 64, 0, RxLink.MIN_RANGE, stone), "Entfernung " + distance);
            assertEquals(0, stone.reads, "zählt keine Blöcke");
        }
    }

    @Test
    void normalBlockKeepsItsRangeAndWalls() {
        Stone stone = new Stone();
        assertFalse(RxLink.linked(false, 0.5, EYE_Y, 0.5, 400, 64, 0, RxLink.MAX_RANGE, stone));
        assertFalse(RxLink.linked(false, 0.5, EYE_Y, 0.5, 100, 64, 0, RxLink.MAX_RANGE, stone), "100 Blöcke Stein dämpfen über 128");
        assertEquals(RxLink.inRange(0.5, EYE_Y, 0.5, 20, 64, 0, 64, new Stone()), RxLink.linked(false, 0.5, EYE_Y, 0.5, 20, 64, 0, 64, new Stone()));
    }

    @Test
    void unlimitedBlockIsAlwaysDemandedNormalBlockOnlyWithinRangeAndMargin() {
        double margin = RxServer.DEMAND_MARGIN;
        assertTrue(RxLink.demands(true, 1.0E12, RxLink.MIN_RANGE, margin));
        double reach = RxLink.DEFAULT_RANGE + margin;
        assertTrue(RxLink.demands(false, reach * reach, RxLink.DEFAULT_RANGE, margin));
        assertFalse(RxLink.demands(false, (reach + 0.01) * (reach + 0.01), RxLink.DEFAULT_RANGE, margin));
        double clamped = RxLink.MAX_RANGE + margin;
        assertFalse(RxLink.demands(false, (clamped + 0.01) * (clamped + 0.01), 10_000, margin), "Reichweite über 128 zählt als 128");
    }

    @Test
    void statusNamesTheUnlimitedRange() {
        RxSettings settings = new RxSettings(UUID.fromString("00000000-0000-4000-8000-000000000001"), RxSource.GAS, -1.0F, 1.0F, 64);
        Component unlimited = RxText.status(settings, 7, "Tom", true);
        Component normal = RxText.status(settings, 7, "Tom", false);
        Object range = ((TranslatableContents) unlimited.getContents()).getArgs()[3];
        assertEquals(RxText.RANGE_UNLIMITED, ((TranslatableContents) ((Component) range).getContents()).getKey());
        assertEquals(RxText.status(settings, 7, "Tom"), normal);
        assertEquals(RxText.range(64), RxText.range(64, false));
    }

    @Test
    void creativeTooltipFitsInBothLanguages() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            String text = CovRxTextTest.lang("rx", language).get(RxText.CREATIVE_TOOLTIP).getAsString();
            List<String> lines = RxText.tooltipLines(text);
            assertEquals(text, String.join(" ", lines), language);
            assertTrue(lines.size() <= 2, language + ": " + lines);
            for (String line : lines) {
                assertTrue(line.length() <= RxText.TOOLTIP_WIDTH, language + " zu lang: " + line);
            }
        }
    }

    @Test
    void guideSceneOpensForTheCreativeBlockAndNamesIt() {
        Scene scene = RxBlockScene.create();
        assertTrue(scene.items().matches(RxBlockScene.CREATIVE_RX_BLOCK, false));
        assertTrue(scene.translationKeys().contains("propwash.guide.scene.rx_block.callout.creative"));
        assertEquals("propwash:creative_rx_block", RxBlockScene.CREATIVE_RX_BLOCK);
    }
}

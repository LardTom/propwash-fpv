package de.lardtom.propwash.video;

import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import de.lardtom.propwash.video.vtx.VtxTable;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtxTablesTest {
    @Test
    void builtInModulesHaveRealisticTables() {
        VtxTable nano = VtxTables.of(VideoType.ANALOG_NANO);
        assertArrayEquals(new int[]{25, 100, 200, 400}, nano.levels());
        assertEquals(200, nano.ratedMw());
        VtxTable race = VtxTables.of(VideoType.ANALOG_RACE);
        assertArrayEquals(new int[]{25, 200, 400, 800, 1600}, race.levels());
        assertEquals(400, race.ratedMw());
        assertTrue(race.supportsAuto());
        VtxTable lite = VtxTables.of(VideoType.DIGITAL_LITE);
        assertArrayEquals(new int[]{25, 100, 400, 700}, lite.levels());
        VtxTable hd = VtxTables.of(VideoType.DIGITAL_HD);
        assertArrayEquals(new int[]{25, 100, 400, 700, 1200}, hd.levels());
        assertEquals(700, hd.ratedMw());
        assertTrue(hd.supportsAuto());
        assertEquals(9.0, hd.ratedSupplyW(), 0.0);
    }

    @Test
    void defaultTuneKeepsTheRatedRange() {
        for (VideoType video : List.of(VideoType.ANALOG_NANO, VideoType.ANALOG_RACE, VideoType.DIGITAL_LITE, VideoType.DIGITAL_HD)) {
            assertEquals(0.0, VtxTables.configuredGainDb(Map.of(), video), video.key());
            assertEquals(VtxTables.of(video).ratedMw(), VtxTables.configuredMw(Map.of(), video), video.key());
        }
        assertEquals(6.02, VtxTables.configuredGainDb(VtxPowerSettings.withLevel(Map.of(), 1600), VideoType.ANALOG_RACE), 0.01);
        assertEquals(0.0, VtxTables.configuredGainDb(VtxPowerSettings.withAuto(Map.of()), VideoType.DIGITAL_HD), 0.0);
    }

    @Test
    void definitionValidatesLevels() {
        Identifier id = Identifier.fromNamespaceAndPath("test", "vtx");
        PartBasics basics = PartBasics.withMass(null, 8.0);
        VideoDefinition.Builder base = VideoDefinition.builder(id).basics(basics).link(VideoLink.ANALOG).latency(12).range(300).imageQuality(0.6);
        assertEquals(List.of(25, 400, 800), base.vtxPower(400).vtxLevels(25, 400, 800).build().vtxLevelsMw());
        assertThrows(IllegalArgumentException.class, () -> base.vtxPower(0).vtxLevels(25, 400).build());
        assertThrows(IllegalArgumentException.class, () -> base.vtxPower(400).vtxLevels(25, 800).build());
        assertThrows(IllegalArgumentException.class, () -> base.vtxPower(400).vtxLevels(400, 25).build());
        assertThrows(IllegalArgumentException.class, () -> base.vtxPower(400).vtxLevels(25, 50, 100, 200, 300, 400, 500, 600, 700).build());
        assertEquals(List.of(), base.vtxPower(400).vtxLevels().build().vtxLevelsMw());
    }
}

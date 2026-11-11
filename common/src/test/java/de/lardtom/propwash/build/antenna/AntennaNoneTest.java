package de.lardtom.propwash.build.antenna;

import com.google.gson.JsonObject;
import de.lardtom.propwash.api.part.AntennaCap;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.AntennaRules;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.parts.JsonText;
import de.lardtom.propwash.parts.PartDataProblems;
import de.lardtom.propwash.parts.PartJson;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntennaNoneTest {
    static final String NONE_JSON = AntennaDataTest.VIDEO_JSON.replace("{\"count\": 2, \"length_mm\": 95.0, \"cap\": \"pagoda\"}", "\"none\"");

    static VideoDefinition none() {
        return (VideoDefinition) AntennaDataTest.decode(PartKind.VIDEO, "gimbal_cam", NONE_JSON);
    }

    @Test
    void videoAntennaNoneHasNoAntennas() {
        VideoAntenna none = VideoAntenna.none();
        assertEquals(0, none.count());
        assertTrue(none.isNone());
        assertEquals(0.0, none.lengthMm(), 0.0);
        assertEquals(none, new VideoAntenna(0, 80.0, AntennaCap.PAGODA));
        assertEquals(none, new VideoAntenna(0, 0.0, null));
        assertFalse(new VideoAntenna(1, 50.0, AntennaCap.WHIP).isNone());
        assertThrows(IllegalArgumentException.class, () -> new VideoAntenna(-1, 50.0, AntennaCap.WHIP));
        assertThrows(IllegalArgumentException.class, () -> new VideoAntenna(3, 50.0, AntennaCap.WHIP));
        assertThrows(IllegalArgumentException.class, () -> new VideoAntenna(1, 5.0, AntennaCap.WHIP));
        assertThrows(NullPointerException.class, () -> new VideoAntenna(1, 50.0, null));
    }

    @Test
    void partJsonReadsAndWritesAntennaNone() {
        assertTrue(NONE_JSON.contains("\"antenna\": \"none\""), NONE_JSON);
        VideoDefinition video = none();
        assertEquals(Optional.of(VideoAntenna.none()), video.antenna());
        JsonObject json = PartJson.encode(video);
        assertTrue(json.get("antenna").isJsonPrimitive(), JsonText.pretty(json));
        assertEquals("none", json.get("antenna").getAsString());
        assertEquals(video, AntennaDataTest.decode(PartKind.VIDEO, "gimbal_cam", JsonText.pretty(json)));
        VideoDefinition built = VideoDefinition.builder(video.id()).basics(video.basics()).link(video.link()).latency(video.latencyMs())
                .range(video.rangeBlocks()).imageQuality(video.imageQuality()).cameraWidth(video.cameraWidthMm()).vtxPower(video.vtxPowerMw())
                .antenna(VideoAntenna.none()).build();
        assertEquals("none", PartJson.encode(built).get("antenna").getAsString());
        PartDataProblems problems = new PartDataProblems("broken.json");
        JsonObject broken = PartJson.parse(NONE_JSON.replace("\"none\"", "\"nothing\""), problems);
        assertNotNull(broken);
        assertNull(PartJson.decode(PartKind.VIDEO, Identifier.fromNamespaceAndPath("justmoreparts", "broken"), broken, problems));
        assertTrue(problems.errors().toString().contains("none"), problems.errors().toString());
    }

    @Test
    void withoutAntennasTheFrameGetsNoneAndPartsWithoutTheFieldKeepDerivedOnes() {
        VideoDefinition video = none();
        FrameDefinition frame = (FrameDefinition) AntennaDataTest.decode(PartKind.FRAME, "fs_test", AntennaDataTest.FRAME_JSON);
        assertEquals(VideoAntenna.none(), AntennaRules.antenna(video));
        assertEquals(VideoAntenna.none(), AntennaRules.antenna(video, frame));
        VideoDefinition plain = AntennaDataTest.vtx("plain", 24.0, VideoLink.DIGITAL, 24, 650, 1.0, 1000);
        assertEquals(Optional.empty(), plain.antenna());
        JsonObject plainJson = PartJson.encode(plain);
        assertFalse(plainJson.has("antenna"), JsonText.pretty(plainJson));
        assertEquals(AntennaRules.derived(plain), AntennaRules.antenna(plain));
        assertFalse(AntennaRules.antenna(plain).isNone());
        for (VideoType core : VideoType.values()) {
            assertFalse(AntennaRules.antenna(core.definition()).isNone(), core.key());
        }
    }

    @Test
    void noAntennasLeaveMassRadioAndVideoLinkAlone() {
        VideoDefinition video = none();
        VideoDefinition withAntennas = (VideoDefinition) AntennaDataTest.decode(PartKind.VIDEO, "gimbal_cam", AntennaDataTest.VIDEO_JSON);
        assertEquals(withAntennas.basics().massGrams(), video.basics().massGrams(), 0.0);
        assertEquals(withAntennas.rangeBlocks(), video.rangeBlocks());
        assertEquals(withAntennas.link(), video.link());
        assertEquals(VideoLink.DIGITAL, video.link());
        assertEquals(withAntennas.latencyMs(), video.latencyMs());
        assertEquals(withAntennas.vtxPowerMw(), video.vtxPowerMw());
        DroneBuild a = with((VideoType) PartIndex.detached(withAntennas));
        DroneBuild b = with((VideoType) PartIndex.detached(video));
        assertEquals(a.totalMassGrams(), b.totalMassGrams(), 0.0);
    }

    static DroneBuild with(VideoType video) {
        DroneBuild base = DroneBuild.FREESTYLE;
        return new DroneBuild(base.frame(), base.stack(), base.motor(), base.prop(), video, base.battery());
    }
}

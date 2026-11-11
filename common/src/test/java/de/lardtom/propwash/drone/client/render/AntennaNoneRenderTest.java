package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntennaNoneRenderTest {
    static VideoType without(VideoType video) {
        VideoDefinition d = video.definition();
        return (VideoType) PartIndex.detached(VideoDefinition.builder(d.id()).basics(d.basics()).link(d.link()).latency(d.latencyMs())
                .range(d.rangeBlocks()).imageQuality(d.imageQuality()).cameraWidth(d.cameraWidthMm()).vtxPower(d.vtxPowerMw())
                .antenna(VideoAntenna.none()).build());
    }

    @Test
    void noPiecesAndNoShapeWithoutAntennas() {
        assertEquals(0, AntennaGeometry.pieces(VideoAntenna.none(), 45.0, 226.0).size());
        assertEquals(0, AntennaGeometry.pieces(VideoAntenna.none(), 45.0, 226.0, 15.0).size());
        assertSame(Shape.EMPTY, AntennaGeometry.shape(VideoAntenna.none(), 45.0, 226.0));
        assertSame(Shape.EMPTY, AntennaGeometry.shape(VideoAntenna.none(), 45.0, 226.0, 8.0, 15.0));
        assertFalse(AntennaGeometry.pieces(new VideoAntenna(1, 50.0, de.lardtom.propwash.api.part.AntennaCap.WHIP), 45.0, 226.0).isEmpty());
    }

    @Test
    void theAntennaPartOfEveryFrameIsEmptyWithoutAntennas() {
        for (FrameType frame : FrameType.values()) {
            DroneLayout layout = DroneLayout.fromDefinition(frame);
            for (VideoType video : VideoType.values()) {
                DroneAssembly.Part with = DroneAssembly.antennaPart(layout, frame, video);
                DroneAssembly.Part none = DroneAssembly.antennaPart(layout, frame, without(video), 6.0F);
                assertFalse(with.solid().isEmpty(), frame.key() + " " + video.key());
                assertTrue(none.solid().isEmpty(), frame.key() + " " + video.key());
                assertTrue(none.translucent().isEmpty(), frame.key() + " " + video.key());
            }
        }
    }
}

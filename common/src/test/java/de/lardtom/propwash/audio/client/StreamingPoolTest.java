package de.lardtom.propwash.audio.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingPoolTest {
    @Test
    void parsesStreamingMaxFromVanillaDebugString() {
        assertEquals(8, StreamingPool.parseMax("Sounds: 12/247 + 3/8"));
        assertEquals(2, StreamingPool.parseMax("Sounds: 0/30 + 0/2"));
    }

    @Test
    void unknownFormatsFallBack() {
        assertEquals(StreamingPool.UNKNOWN, StreamingPool.parseMax(null));
        assertEquals(StreamingPool.UNKNOWN, StreamingPool.parseMax("Sounds: n/a"));
        assertEquals(AudioConfig.MAX_VOICES, StreamingPool.synthCap(StreamingPool.UNKNOWN));
    }

    @Test
    void capLeavesRoomForMusicAndAmbience() {
        assertEquals(6, StreamingPool.synthCap(8));
        assertEquals(3, StreamingPool.synthCap(5));
        assertEquals(1, StreamingPool.synthCap(2));
        assertEquals(1, StreamingPool.synthCap(1));
    }
}

package de.lardtom.propwash.video.client;

import de.lardtom.propwash.race.AssistCheck;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CreativeLatencyStartTest {
    @Test
    void switchingToOwnLatencyNeverStartsBelowTheTransmitter() {
        assertEquals(25, VideoConfig.ownLatencyStart(0, 22));
        assertEquals(30, VideoConfig.ownLatencyStart(0, 30));
        assertEquals(40, VideoConfig.ownLatencyStart(40, 22));
        assertEquals(0, VideoConfig.ownLatencyStart(0, 0));
        assertEquals(VideoConfig.MAX_CREATIVE_LATENCY_MS, VideoConfig.ownLatencyStart(0, VideoConfig.MAX_CREATIVE_LATENCY_MS + 7));
        for (int module = 0; module <= 120; module++) {
            int start = VideoConfig.ownLatencyStart(0, module);
            assertEquals(0, start % VideoConfig.CREATIVE_LATENCY_STEP_MS, "module " + module);
            assertFalse(AssistCheck.gogglesFaster(true, true, start, module), "module " + module);
        }
    }
}

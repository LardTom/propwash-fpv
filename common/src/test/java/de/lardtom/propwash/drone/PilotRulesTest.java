package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.drone.PilotRules.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotRulesTest {
    private static final VideoSystem[] TRANSMITTERS = {VideoSystem.ANALOG, VideoSystem.DIGITAL};

    @Test
    void noGogglesMeansLineOfSight() {
        for (VideoSystem video : TRANSMITTERS) {
            Decision decision = PilotRules.decide(null, video);
            assertEquals(Decision.LINE_OF_SIGHT, decision, video.name());
            assertEquals(PilotMode.LINE_OF_SIGHT, decision.mode());
            assertTrue(decision.connects());
            assertFalse(decision.blind());
        }
    }

    @Test
    void matchingGogglesFlyFpvWithoutAnyBinding() {
        assertEquals(Decision.FPV, PilotRules.decide(VideoSystem.ANALOG, VideoSystem.ANALOG));
        assertEquals(Decision.FPV, PilotRules.decide(VideoSystem.DIGITAL, VideoSystem.DIGITAL));
        assertEquals(PilotMode.FPV, Decision.FPV.mode());
        assertTrue(Decision.FPV.connects());
        assertFalse(Decision.FPV.blind());
    }

    @Test
    void creativeGogglesMatchEverySystem() {
        for (VideoSystem video : TRANSMITTERS) {
            assertEquals(Decision.FPV, PilotRules.decide(VideoSystem.CREATIVE, video), video.name());
        }
    }

    @Test
    void mismatchedGogglesRefuseTheLinkInsteadOfFallingBackToLineOfSight() {
        for (Decision decision : new Decision[]{PilotRules.decide(VideoSystem.ANALOG, VideoSystem.DIGITAL),
                PilotRules.decide(VideoSystem.DIGITAL, VideoSystem.ANALOG)}) {
            assertEquals(Decision.NO_VIDEO, decision);
            assertFalse(decision.connects());
            assertTrue(decision.blind());
            assertEquals(PilotMode.LINE_OF_SIGHT, decision.mode());
        }
    }

    @Test
    void onlyLineOfSightWithoutGogglesAndFpvWithMatchingOnesConnect() {
        for (VideoSystem goggles : VideoSystem.values()) {
            for (VideoSystem video : TRANSMITTERS) {
                Decision decision = PilotRules.decide(goggles, video);
                assertEquals(goggles.receives(video), decision.connects(), goggles + " → " + video);
                assertEquals(goggles.receives(video) ? PilotMode.FPV : PilotMode.LINE_OF_SIGHT, decision.mode(), goggles + " → " + video);
            }
        }
    }

    @Test
    void changesAreReportedOnlyAfterTheFirstCheck() {
        assertFalse(PilotRules.changed(null, Decision.FPV));
        assertFalse(PilotRules.changed(Decision.FPV, Decision.FPV));
        assertTrue(PilotRules.changed(Decision.FPV, Decision.LINE_OF_SIGHT));
        assertTrue(PilotRules.changed(Decision.LINE_OF_SIGHT, Decision.NO_VIDEO));
        assertTrue(PilotRules.changed(Decision.NO_VIDEO, Decision.FPV));
    }
}

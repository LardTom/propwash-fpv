package de.lardtom.propwash.race;

import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LapAssistTest {
    @Test
    void plainFlightCounts() {
        assertEquals(LapAssist.NONE, LapAssist.of(LapAssist.Flight.plain()));
        assertNull(LapAssist.first(LapAssist.NONE));
    }

    @Test
    void creativeGogglesRangeAndCleanVideoDoNotCountAsAssist() {
        LapAssist.Flight goggles = LapAssist.Flight.plain().withCreativeGoggles(false, 0, VideoType.DIGITAL_HD.latencyMs);
        assertEquals(LapAssist.NONE, LapAssist.of(goggles));
        LapAssist.Flight slower = LapAssist.Flight.plain().withCreativeGoggles(true, 120, VideoType.ANALOG_RACE.latencyMs);
        assertEquals(LapAssist.NONE, LapAssist.of(slower));
        LapAssist.Flight equal = LapAssist.Flight.plain().withCreativeGoggles(true, VideoType.ANALOG_RACE.latencyMs, VideoType.ANALOG_RACE.latencyMs);
        assertEquals(LapAssist.NONE, LapAssist.of(equal));
    }

    @Test
    void creativeGogglesFasterThanTheTransmitterCount() {
        LapAssist.Flight faster = LapAssist.Flight.plain().withCreativeGoggles(true, 0, VideoType.DIGITAL_HD.latencyMs);
        int mask = LapAssist.of(faster);
        assertEquals(LapAssist.GOGGLES_LATENCY.bit(), mask);
        assertSame(LapAssist.GOGGLES_LATENCY, LapAssist.first(mask));
        assertEquals("propwash.race.assist.goggles_latency", LapAssist.GOGGLES_LATENCY.translationKey());
    }

    @Test
    void repeatersNeverCountAsAssist() {
        assertEquals(LapAssist.NONE, LapAssist.of(LapAssist.Flight.plain().withRelays(4)));
        assertEquals(LapAssist.NONE, LapAssist.of(LapAssist.Flight.plain().withCreativeGoggles(false, 0, 30).withRelays(2)));
    }

    @Test
    void flightAidsAndChangedPhysicsStillCountAndNameTheirRule() {
        LapAssist.Flight all = new LapAssist.Flight(true, true, true, true, true, true, true, 0, 30, true, true, 3);
        int mask = LapAssist.of(all);
        assertEquals(List.of(LapAssist.HOLD_MODE, LapAssist.RESCUE, LapAssist.WORLD_PHYSICS, LapAssist.CREATIVE_DRONE, LapAssist.PARTS_MISMATCH,
                LapAssist.GOGGLES_LATENCY), LapAssist.list(mask));
        assertEquals(LapAssist.NONE, mask & LapAssist.OTHER.bit());
        assertSame(LapAssist.HOLD_MODE, LapAssist.first(mask));
        assertEquals(LapAssist.WORLD_PHYSICS.bit(), LapAssist.of(new LapAssist.Flight(false, false, true, false, false, false, false, 0, 0,
                false, false, 0)));
        assertEquals(LapAssist.CREATIVE_DRONE.bit() | LapAssist.PARTS_MISMATCH.bit(), LapAssist.of(new LapAssist.Flight(false, false, false,
                true, true, false, false, 0, 0, false, false, 0)));
        assertEquals(LapAssist.RESCUE.bit(), LapAssist.of(new LapAssist.Flight(false, true, false, false, false, false, false, 0, 0,
                false, false, 0)));
    }
}

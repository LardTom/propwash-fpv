package de.lardtom.propwash.race;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LapVerdictTest {
    private static RacePayloads.LapVerdictS2C roundTrip(RacePayloads.LapVerdictS2C payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.LapVerdictS2C.CODEC.encode(buf, payload);
        RacePayloads.LapVerdictS2C decoded = RacePayloads.LapVerdictS2C.CODEC.decode(buf);
        assertFalse(buf.isReadable(), "Paket vollständig gelesen");
        return decoded;
    }

    @Test
    void verdictTravelsWithElementAndCount() {
        RacePayloads.LapVerdictS2C missed = new RacePayloads.LapVerdictS2C(65, 1, RacePayloads.LapResult.MISSED, 24, 0);
        assertEquals(missed, roundTrip(missed));
        RacePayloads.LapVerdictS2C race = new RacePayloads.LapVerdictS2C(65, 3, RacePayloads.LapResult.RACE, RacePayloads.LapVerdictS2C.NO_ELEMENT, 2);
        assertEquals(race, roundTrip(race));
        assertEquals(RacePayloads.LapVerdictS2C.NO_ELEMENT, new RacePayloads.LapVerdictS2C(1, 1, RacePayloads.LapResult.REJECTED, -7, 0).element());
    }

    @Test
    void unknownResultFallsBackToRejected() {
        assertEquals(RacePayloads.LapResult.REJECTED, RacePayloads.LapResult.byOrdinal(99));
        assertEquals(RacePayloads.LapResult.REJECTED, new RacePayloads.LapVerdictS2C(1, 1, null, 2, 0).result());
    }

    @Test
    void checkpointLeadsBackToTheElement() {
        List<CourseElement> kinds = List.of(CourseElement.GATE, CourseElement.POLE, CourseElement.FLAG, CourseElement.GATE);
        assertEquals(6, CourseCheckpoints.count(kinds));
        assertEquals(0, CourseCheckpoints.element(kinds, 0));
        assertEquals(1, CourseCheckpoints.element(kinds, 1));
        assertEquals(2, CourseCheckpoints.element(kinds, 2));
        assertEquals(2, CourseCheckpoints.element(kinds, 3));
        assertEquals(2, CourseCheckpoints.element(kinds, 4));
        assertEquals(3, CourseCheckpoints.element(kinds, 5));
        assertEquals(CourseCheckpoints.NONE, CourseCheckpoints.element(kinds, 6));
        assertEquals(CourseCheckpoints.NONE, CourseCheckpoints.element(kinds, -1));
    }
}

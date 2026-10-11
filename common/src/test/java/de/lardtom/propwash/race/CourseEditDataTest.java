package de.lardtom.propwash.race;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseEditDataTest {
    private static final UUID OWNER = new UUID(7L, 9L);

    private static List<CourseData.Gate> gates(int... order) {
        List<CourseData.Gate> list = new ArrayList<>();
        for (int i = 0; i < order.length; i++) {
            int spot = order[i];
            list.add(new CourseData.Gate(i, spot * 12.0, 64.0, spot * -5.0, spot * 30.0F, GateSize.MEDIUM));
        }
        return list;
    }

    private static CourseData roundTrip(CourseData data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        return decoded;
    }

    @Test
    void nameLapsAndOwnerTravelWithTheCourse() {
        CourseData data = new CourseData(4, "minecraft:overworld", gates(0, 1, 2), "Große Schleife", 5, OWNER, "lardtom");
        CourseData decoded = roundTrip(data);
        assertEquals(data, decoded);
        assertEquals("Große Schleife", decoded.name());
        assertEquals(5, decoded.laps());
        assertEquals(OWNER, decoded.owner());
        assertEquals("lardtom", decoded.ownerName());
        assertTrue(decoded.named());
    }

    @Test
    void coursesWithoutNameLapsAndOwnerStayThatWay() {
        CourseData decoded = roundTrip(new CourseData(1, "minecraft:overworld", gates(0, 1)));
        assertEquals("", decoded.name());
        assertEquals(CourseLaps.UNSET, decoded.laps());
        assertNull(decoded.owner());
        assertFalse(decoded.named());
        CourseData endless = roundTrip(new CourseData(1, "minecraft:overworld", gates(0, 1), "", CourseLaps.ENDLESS, null, ""));
        assertEquals(CourseLaps.ENDLESS, endless.laps());
    }

    @Test
    void theRecordCleansWhatItGets() {
        CourseData data = new CourseData(2, "minecraft:overworld", gates(0), "  §aZu   langer Name " + "x".repeat(60), 400, null, "n".repeat(80));
        assertEquals(CourseNames.MAX, data.name().length());
        assertTrue(data.name().startsWith("aZu langer Name"));
        assertEquals(CourseLaps.MAX, data.laps());
        assertEquals(CourseData.MAX_OWNER_NAME, data.ownerName().length());
        assertEquals(data, roundTrip(data));
    }

    @Test
    void reorderingChangesTheLayoutButNameAndLapsDoNot() {
        CourseData base = new CourseData(3, "minecraft:overworld", gates(0, 1, 2, 3), "A", 3, OWNER, "lardtom");
        CourseData renamed = new CourseData(3, "minecraft:overworld", gates(0, 1, 2, 3), "B", 7, null, "");
        assertEquals(base.layoutHash(), renamed.layoutHash());
        CourseData swapped = new CourseData(3, "minecraft:overworld", gates(0, 2, 1, 3), "A", 3, OWNER, "lardtom");
        assertNotEquals(base.layoutHash(), swapped.layoutHash());
        CourseData rotated = new CourseData(3, "minecraft:overworld", gates(2, 3, 0, 1), "A", 3, OWNER, "lardtom");
        assertNotEquals(base.layoutHash(), rotated.layoutHash());
        CourseData shorter = new CourseData(3, "minecraft:overworld", gates(0, 1, 2), "A", 3, OWNER, "lardtom");
        assertNotEquals(base.layoutHash(), shorter.layoutHash());
    }

    @Test
    void editPacketsRoundTrip() {
        for (CourseEdits.Action action : CourseEdits.Action.values()) {
            RacePayloads.CourseEditC2S payload = new RacePayloads.CourseEditC2S(12, action, 3, 7, "Wald über Öl");
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            RacePayloads.CourseEditC2S.CODEC.encode(buf, payload);
            assertEquals(payload, RacePayloads.CourseEditC2S.CODEC.decode(buf));
            assertFalse(buf.isReadable());
        }
        assertEquals(CourseEdits.Action.RENAME, CourseEdits.Action.byOrdinal(99));
        RacePayloads.CourseEditC2S clipped = new RacePayloads.CourseEditC2S(1, CourseEdits.Action.MOVE, -4, -1, null);
        assertEquals(0, clipped.first());
        assertEquals(0, clipped.second());
        assertEquals("", clipped.text());
        assertEquals(CourseLaps.MAX, new RacePayloads.CourseDefaultsC2S(5000).laps());
        assertEquals(CourseLaps.ENDLESS, new RacePayloads.CourseDefaultsC2S(-3).laps());
    }

    @Test
    void leaderboardCarriesTheRaceOfTheCourse() {
        RacePayloads.LeaderboardS2C payload = new RacePayloads.LeaderboardS2C(5, 2, 8, true, 14, List.of(), 3, 61_234_000_000L, 2);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.LeaderboardS2C.CODEC.encode(buf, payload);
        RacePayloads.LeaderboardS2C decoded = RacePayloads.LeaderboardS2C.CODEC.decode(buf);
        assertEquals(payload, decoded);
        assertTrue(decoded.hasRaceBest());
        assertEquals(2, decoded.archived());
        RacePayloads.LeaderboardS2C plain = new RacePayloads.LeaderboardS2C(5, 2, 8, true, 14, List.of());
        assertFalse(plain.hasRaceBest());
        buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.LeaderboardS2C.CODEC.encode(buf, plain);
        assertEquals(plain, RacePayloads.LeaderboardS2C.CODEC.decode(buf));
        assertFalse(new RacePayloads.LeaderboardS2C(5, 2, 8, true, 14, List.of(), 0, 61_234_000_000L, 0).hasRaceBest());
    }
}

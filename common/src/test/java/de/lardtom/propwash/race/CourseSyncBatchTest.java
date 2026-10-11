package de.lardtom.propwash.race;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseSyncBatchTest {
    private static final int S2C_LIMIT = 1024 * 1024;

    private static List<CourseData> courses(int count, int gates) {
        List<CourseData> list = new ArrayList<>();
        for (int id = 1; id <= count; id++) {
            List<CourseData.Gate> gateList = new ArrayList<>();
            for (int i = 0; i < gates; i++) {
                gateList.add(new CourseData.Gate(i, i * 3.0, 64.0, -i * 2.0, i * 7.0F, GateSize.LARGE));
            }
            list.add(new CourseData(id, "minecraft:the_nether_with_a_rather_long_dimension_name", gateList));
        }
        return list;
    }

    @Test
    void emptyRegistryStillSendsOneReplacingBatch() {
        List<RacePayloads.CourseSyncS2C> batches = CourseRegistry.joinBatches(List.of());
        assertEquals(1, batches.size());
        assertTrue(batches.getFirst().replaceAll());
        assertTrue(batches.getFirst().courses().isEmpty());
    }

    @Test
    void fullRegistryIsSplitIntoDecodableBatchesUnderTheLimit() {
        List<CourseData> all = courses(CourseRegistry.MAX_COURSES, CourseData.MAX_GATES);
        List<RacePayloads.CourseSyncS2C> batches = CourseRegistry.joinBatches(all);
        int total = 0;
        for (int i = 0; i < batches.size(); i++) {
            RacePayloads.CourseSyncS2C batch = batches.get(i);
            assertEquals(i == 0, batch.replaceAll());
            assertTrue(batch.courses().size() <= CourseRegistry.SYNC_BATCH);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            RacePayloads.CourseSyncS2C.CODEC.encode(buf, batch);
            assertTrue(buf.readableBytes() < S2C_LIMIT / 2, "Batch " + i + " hat " + buf.readableBytes() + " Bytes");
            RacePayloads.CourseSyncS2C decoded = RacePayloads.CourseSyncS2C.CODEC.decode(buf);
            assertEquals(batch.courses().size(), decoded.courses().size());
            assertFalse(buf.isReadable());
            total += decoded.courses().size();
        }
        assertEquals(all.size(), total);
    }
}

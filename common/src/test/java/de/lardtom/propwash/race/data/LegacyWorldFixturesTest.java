package de.lardtom.propwash.race.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RecordGhosts;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceSnapshots;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LegacyWorldFixturesTest {
    public static final String FIXTURES = "PROPWASH_FIXTURES";
    private static final List<String> STANDS = List.of("0.4.0", "0.5.0", "0.5.2");

    @TempDir
    Path temp;

    private static Map<String, byte[]> extract(Path zip) throws IOException {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith("/data/propwash_race.json") || name.endsWith("/data/propwash/race_records.dat")) {
                    out.put(name.substring(name.indexOf("/data/") + 1), in.readAllBytes());
                }
            }
        }
        return out;
    }

    private static RaceRecords records(byte[] data) throws IOException {
        try (InputStream in = new ByteArrayInputStream(data)) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            Codec<RaceRecords> codec = RaceRecords.TYPE.codec();
            return codec.parse(NbtOps.INSTANCE, tag.get("data")).getOrThrow();
        }
    }

    private static UUID player(JsonElement ints) {
        JsonArray array = ints.getAsJsonArray();
        return UUIDUtil.uuidFromIntArray(new int[]{array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt(), array.get(3).getAsInt()});
    }

    @Test
    void fixtureWorldsMigrateWithStableIdsAndUnchangedBoards() throws IOException {
        String root = System.getenv(FIXTURES);
        assumeTrue(root != null && Files.isDirectory(Path.of(root)), FIXTURES + " zeigt nicht auf die Testwelten (nur lokal)");
        int checked = 0;
        for (String stand : STANDS) {
            Path dir = Path.of(root, stand);
            if (!Files.isRegularFile(dir.resolve("world.zip"))) {
                continue;
            }
            Map<String, byte[]> files = extract(dir.resolve("world.zip"));
            JsonObject expected = JsonParser.parseString(Files.readString(dir.resolve("expected.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            Path world = this.temp.resolve(stand);
            Files.createDirectories(world);
            Path file = world.resolve(TestWorlds.FILE);
            Files.write(file, files.get("data/propwash_race.json"));
            RaceRecords records = records(files.get("data/propwash/race_records.dat"));
            CourseRegistry registry = CourseRegistry.offline(file, records, new RecordGhosts());
            int count = expected.getAsJsonObject("courseRegistry").get("count").getAsInt();
            assertEquals(count, registry.courses().size(), stand);
            assertEquals(count, registry.assignedKeys(), stand + ": jeder Kurs bekommt beim ersten Laden eine ID");
            Set<UUID> keys = new HashSet<>();
            Map<Integer, UUID> byNumber = new HashMap<>();
            for (CourseRegistry.Course course : registry.courses()) {
                assertTrue(keys.add(course.key()), stand);
                assertEquals(4, course.key().version(), stand);
                byNumber.put(course.id, course.key());
            }
            for (JsonElement element : expected.getAsJsonArray("courses")) {
                JsonObject want = element.getAsJsonObject();
                int id = want.get("id").getAsInt();
                CourseRegistry.Course course = registry.course(id);
                String where = stand + " Kurs " + id;
                assertEquals(want.get("layoutHash").getAsInt(), course.toData().layoutHash(), where + ": Prüfsumme");
                assertEquals(want.get("layout").getAsInt(), course.layout, where);
                JsonObject board = want.getAsJsonObject("leaderboard");
                BoardSnapshot top = RaceSnapshots.board(course, records, BoardQuery.laps().limit(BoardQuery.MAX_LIMIT)).orElseThrow();
                JsonArray wantTop = board.getAsJsonArray("top");
                assertEquals(wantTop.size(), top.entries().size(), where + ": Bestenliste");
                for (int i = 0; i < wantTop.size(); i++) {
                    JsonObject row = wantTop.get(i).getAsJsonObject();
                    BoardEntry entry = top.entries().get(i);
                    assertEquals(player(row.get("player")), entry.player(), where);
                    assertEquals(row.get("name").getAsString(), entry.name(), where);
                    assertEquals(row.get("nanos").getAsLong(), entry.timeNanos(), where);
                    assertEquals(row.get("frame").getAsString(), entry.frame(), where);
                }
                int assisted = board.has("assistedSize") ? board.get("assistedSize").getAsInt() : 0;
                assertEquals(assisted, RaceSnapshots.board(course, records, BoardQuery.laps().assisted().limit(BoardQuery.MAX_LIMIT)).orElseThrow().total(),
                        where);
                CourseInfo info = RaceSnapshots.info(course, records, List.of());
                String json = RaceDataJson.leaderboard(info, course, records);
                JsonArray layouts = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("layouts");
                assertEquals(1 + want.getAsJsonArray("archive").size(), layouts.size(), where + ": aktuelles Layout und Archiv im Spiegel");
                if (!wantTop.isEmpty()) {
                    assertTrue(json.contains("\"timeMs\": " + BoardEntry.millis(wantTop.get(0).getAsJsonObject().get("nanos").getAsLong())), where);
                }
            }
            registry.save();
            CourseRegistry again = CourseRegistry.offline(file, records, new RecordGhosts());
            assertEquals(0, again.assignedKeys(), stand + ": danach stabil");
            Map<Integer, UUID> reloaded = new HashMap<>();
            for (CourseRegistry.Course course : again.courses()) {
                reloaded.put(course.id, course.key());
            }
            assertEquals(byNumber, reloaded, stand);
            List<CourseInfo> infos = new ArrayList<>();
            for (CourseRegistry.Course course : again.courses()) {
                infos.add(RaceSnapshots.info(course, records, List.of()));
            }
            String courses = RaceDataJson.courses(again, infos);
            for (UUID key : keys) {
                assertTrue(courses.contains(key.toString()), stand);
            }
            checked++;
        }
        assertTrue(checked > 0, "keine Testwelt gefunden unter " + root);
    }
}

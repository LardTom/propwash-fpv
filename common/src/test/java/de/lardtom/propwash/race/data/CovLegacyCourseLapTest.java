package de.lardtom.propwash.race.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceLine;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RecordGhosts;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CovLegacyCourseLapTest {
    private static final List<String> STANDS = List.of("0.4.0", "0.5.0", "0.5.2");
    private static final long MS = 1_000_000L;
    private static final double LEAD = 3.0;
    private static final UUID PILOT = UUID.fromString("00000000-0000-4000-8000-0000000c0fe1");

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

    static List<double[]> lapPath(List<GateGeometry> gates) {
        List<double[]> path = new ArrayList<>();
        int count = gates.size();
        for (int i = 0; i <= count; i++) {
            GateGeometry gate = gates.get(i % count);
            if (i > 0) {
                GateGeometry previous = gates.get(i - 1);
                if (RaceLine.needsDetour(previous, gate)) {
                    double[] exit = path.getLast();
                    double reach = Math.max(previous.width(), gate.width()) * 0.5 + 2.5;
                    GateGeometry.Waypoint first = gate.waypoints().getFirst();
                    double ex = exit[0] + previous.sideX() * reach;
                    double ez = exit[2] + previous.sideZ() * reach;
                    path.add(new double[]{ex, exit[1], ez});
                    double ax = first.x() - first.dx() * LEAD + previous.sideX() * reach;
                    double az = first.z() - first.dz() * LEAD + previous.sideZ() * reach;
                    path.add(new double[]{ax, first.y(), az});
                }
            }
            List<GateGeometry.Waypoint> points = i == count ? List.of(gate.waypoints().getFirst()) : gate.waypoints();
            for (GateGeometry.Waypoint point : points) {
                path.add(new double[]{point.x() - point.dx() * LEAD, point.y() - point.dy() * LEAD, point.z() - point.dz() * LEAD});
                path.add(new double[]{point.x() + point.dx() * LEAD, point.y() + point.dy() * LEAD, point.z() + point.dz() * LEAD});
            }
        }
        return path;
    }

    static List<LapTracker.Event> fly(int course, List<GateGeometry> gates, List<double[]> path) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(course, gates, 0L);
        tracker.setOpenings(index -> GateStacking.base(gates, index) == GateStacking.Base.LEGS);
        long now = 1_000L * MS;
        double[] at = path.getFirst();
        tracker.update(now, at[0], at[1], at[2]);
        List<LapTracker.Event> events = new ArrayList<>();
        for (int leg = 1; leg < path.size(); leg++) {
            double[] to = path.get(leg);
            double length = Math.sqrt(Math.pow(to[0] - at[0], 2) + Math.pow(to[1] - at[1], 2) + Math.pow(to[2] - at[2], 2));
            int steps = Math.max(1, (int) Math.ceil(length / 0.25));
            for (int i = 1; i <= steps; i++) {
                now += 15 * MS;
                tracker.update(now, at[0] + (to[0] - at[0]) * i / steps, at[1] + (to[1] - at[1]) * i / steps, at[2] + (to[2] - at[2]) * i / steps);
                events.addAll(tracker.drainEvents());
            }
            at = to;
        }
        return events;
    }

    private static List<Integer> of(List<LapTracker.Event> events, LapTracker.EventType type) {
        return events.stream().filter(e -> e.type() == type).map(LapTracker.Event::gate).toList();
    }

    @Test
    void everyCourseOfEveryFixtureCountsAScriptedLap() throws IOException {
        String root = System.getenv(LegacyWorldFixturesTest.FIXTURES);
        assumeTrue(root != null && Files.isDirectory(Path.of(root)), LegacyWorldFixturesTest.FIXTURES + " zeigt nicht auf die Testwelten (nur lokal)");
        int flown = 0;
        for (String stand : STANDS) {
            Path dir = Path.of(root, stand);
            if (!Files.isRegularFile(dir.resolve("world.zip"))) {
                continue;
            }
            Map<String, byte[]> files = extract(dir.resolve("world.zip"));
            Path world = this.temp.resolve("all-" + stand);
            Files.createDirectories(world);
            Path file = world.resolve(TestWorlds.FILE);
            Files.write(file, files.get("data/propwash_race.json"));
            CourseRegistry registry = CourseRegistry.offline(file, records(files.get("data/propwash/race_records.dat")), new RecordGhosts());
            for (CourseRegistry.Course course : registry.courses()) {
                String where = stand + " Kurs " + course.id + " (" + course.name + ")";
                List<GateGeometry> gates = course.toData().geometry();
                List<LapTracker.Event> events = fly(course.id, gates, lapPath(gates));
                List<Integer> expectedGates = new ArrayList<>();
                for (int i = 1; i < gates.size(); i++) {
                    expectedGates.add(i);
                }
                assertEquals(expectedGates, of(events, LapTracker.EventType.GATE), where + ": " + events);
                assertEquals(1, of(events, LapTracker.EventType.LAP).size(), where + ": " + events);
                assertTrue(of(events, LapTracker.EventType.MISSED).isEmpty(), where + ": " + events);
                flown++;
            }
        }
        assertTrue(flown > 0, "keine Testwelt gefunden unter " + root);
    }

    @Test
    void oneCourseOfEachFixtureLoadsCountsALapAndKeepsItsBoard() throws IOException {
        String root = System.getenv(LegacyWorldFixturesTest.FIXTURES);
        assumeTrue(root != null && Files.isDirectory(Path.of(root)), LegacyWorldFixturesTest.FIXTURES + " zeigt nicht auf die Testwelten (nur lokal)");
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
            JsonObject sample = null;
            for (JsonElement element : expected.getAsJsonArray("courses")) {
                JsonObject course = element.getAsJsonObject();
                if (sample == null || course.getAsJsonObject("leaderboard").getAsJsonArray("top").size()
                        > sample.getAsJsonObject("leaderboard").getAsJsonArray("top").size()) {
                    sample = course;
                }
            }
            assertNotNull(sample, stand + ": kein Kurs in der Erwartung");
            int id = sample.get("id").getAsInt();
            String where = stand + " Kurs " + id;
            CourseRegistry.Course course = registry.course(id);
            assertNotNull(course, where + " lädt nicht");
            CourseData data = course.toData();
            assertEquals(sample.get("layoutHash").getAsInt(), data.layoutHash(), where + ": Prüfsumme");
            List<GateGeometry> gates = data.geometry();
            List<LapTracker.Event> events = fly(id, gates, lapPath(gates));
            List<Integer> expectedGates = new ArrayList<>();
            for (int i = 1; i < gates.size(); i++) {
                expectedGates.add(i);
            }
            assertEquals(1, of(events, LapTracker.EventType.START).size(), where + ": " + events);
            assertEquals(expectedGates, of(events, LapTracker.EventType.GATE), where + ": " + events);
            assertEquals(1, of(events, LapTracker.EventType.LAP).size(), where + ": " + events);
            assertTrue(of(events, LapTracker.EventType.MISSED).isEmpty(), where + ": " + events);
            LapTracker.Event lap = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).findFirst().orElseThrow();
            List<Leaderboard.Entry> before = List.copyOf(registry.board(course).entries());
            JsonArray top = sample.getAsJsonObject("leaderboard").getAsJsonArray("top");
            assertTrue(before.size() >= top.size(), where + ": Bestenliste nach dem Laden " + before.size() + " statt " + top.size());
            for (JsonElement element : top) {
                JsonObject row = element.getAsJsonObject();
                long nanos = row.get("nanos").getAsLong();
                String name = row.get("name").getAsString();
                assertTrue(before.stream().anyMatch(entry -> entry.nanos() == nanos && entry.name().equals(name)),
                        where + ": Eintrag " + name + " " + nanos + " fehlt");
            }
            long slowest = before.stream().mapToLong(Leaderboard.Entry::nanos).max().orElse(0L);
            long flown = Math.max(lap.valueNanos(), slowest + 1_000L * MS);
            int layout = course.layout;
            registry.submitLap(course, PILOT, "CovPilot", "race_5", flown, 1_792_400_000_000L);
            List<Leaderboard.Entry> after = registry.board(course).entries();
            assertEquals(layout, course.layout, where + ": Layout bleibt");
            assertEquals(before.size() + 1, after.size(), where);
            for (int i = 0; i < before.size(); i++) {
                assertEquals(before.get(i), after.get(i), where + ": alter Eintrag " + i + " unverändert");
            }
            assertEquals(PILOT, after.getLast().player(), where);
            registry.save();
            CourseRegistry again = CourseRegistry.offline(file, records, new RecordGhosts());
            CourseRegistry.Course reloaded = again.course(id);
            assertNotNull(reloaded, where);
            assertEquals(data.layoutHash(), reloaded.toData().layoutHash(), where + ": Prüfsumme nach dem Speichern");
            assertEquals(course.key(), reloaded.key(), where + ": Kurs-ID bleibt");
            assertFalse(again.board(reloaded).entries().isEmpty(), where);
            assertEquals(after, again.board(reloaded).entries(), where + ": Bestenliste nach dem Neuladen");
            checked++;
        }
        assertTrue(checked > 0, "keine Testwelt gefunden unter " + root);
    }
}

package de.lardtom.propwash.race.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseOrder;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RecordGhosts;
import de.lardtom.propwash.race.ScoreArea;
import de.lardtom.propwash.testutil.TestPaths;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorldCourseMechanicsTest {
    private static final String LINES = "/worldcourse/lines.json";
    private static final long MS = 1_000_000L;
    private static final double STEP = 0.25;
    private static final Map<String, String> LEGACY = Map.of(
            "canyon_run_badlands", "db3b91a75856f656f3269d2f4f7129569b9ff5c4165fb4865eb76517b82580da",
            "canyon_run_desert", "e5169928e8a0c72b559b92cf65efca21b839f6e5fce82b3fdcf1f7e9bd3bfdcd",
            "drone_arena", "6dbe34ec4f6644aa0f58582f7f707b434b3634c0eab2dfd6badb81e5e09a80f5",
            "field_slalom", "32dfd364a1e62fba2f4628479a0f9c0e56b45fcb41d134917b25ed82836482e7",
            "forest_weave_birch", "5ed21e7f5cb305ca0e4e341bbb353835de979b6871d1c575ed0fb9ac463929b0",
            "forest_weave_oak", "d0b2e5f5f9fbf03225c8987b472d5cca99cfc702ce16bb8d47e064b86edfe7aa",
            "forest_weave_spruce", "bf98f1751da49c533bab73490855ee6216a91d7edabc0978dafebe7e7bd44f13");
    private static final Set<String> ENTITY_KEYS = Set.of("id", "Rotation", "Size", "Kind", "Variant", "GateIndex", "Course", "WorldCourse",
            "OpeningWidth", "OpeningHeight", "ScoreRole", "ScoreSide", "ScoreReach", "ScoreBelow", "ScoreAbove");
    private static final Set<String> MARKER_KEYS = Set.of("Variant", "Slot", "Local", "LocalYaw", "Board", "Passes", "Mode");

    private record Piece(int entity, CourseElement kind, double x, double y, double z, float yaw, GateSize size, int variant, double width,
                         double height, ScoreArea score, List<CourseOrder.Pass> passes, String mode, String course) {
        GateGeometry placed() {
            return new CourseData.Gate(0, this.x, this.y, this.z, this.yaw, this.size, GateMount.STANDING, false, GateTilt.NONE, this.width,
                    this.height, this.kind, this.variant, null, this.score, false).geometry();
        }

        GateGeometry pass(boolean reverse) {
            return new CourseData.Gate(0, this.x, this.y, this.z, this.yaw, this.size, GateMount.STANDING, false, GateTilt.NONE, this.width,
                    this.height, this.kind, this.variant, null, this.score, reverse).geometry();
        }
    }

    private record Course(String template, List<Piece> pieces, List<Piece> order, List<Boolean> reverse, String mode) {
        boolean pointToPoint() {
            return this.mode.equals("point_to_point");
        }

        List<GateGeometry> geometry() {
            List<GateGeometry> list = new ArrayList<>();
            for (int i = 0; i < this.order.size(); i++) {
                list.add(this.order.get(i).pass(this.reverse.get(i)));
            }
            return list;
        }
    }

    private static Path data() {
        return TestPaths.mainResources().resolve("data/propwash");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<String> referencedTemplates() throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (WorldCourseVariant variant : WorldCourseVariant.values()) {
            JsonObject structure = read(data().resolve("worldgen/structure/" + variant.key() + ".json"));
            ids.add(structure.get("template").getAsString());
            for (JsonElement entry : structure.getAsJsonArray("biome_templates")) {
                ids.add(entry.getAsJsonObject().get("template").getAsString());
            }
        }
        return ids;
    }

    private static Path file(String id) {
        return data().resolve("structure/" + id.substring("propwash:".length()) + ".nbt");
    }

    private static String name(String id) {
        String path = id.substring(id.lastIndexOf('/') + 1);
        return path.endsWith(".nbt") ? path.substring(0, path.length() - 4) : path;
    }

    private static Course load(String id) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file(id), NbtAccounter.unlimitedHeap());
        ListTag entities = root.getListOrEmpty("entities");
        List<Piece> pieces = new ArrayList<>();
        Map<Integer, Piece> bySlot = new LinkedHashMap<>();
        Map<Integer, Boolean> reverseBySlot = new LinkedHashMap<>();
        Set<String> modes = new TreeSet<>();
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = entities.getCompoundOrEmpty(i);
            CompoundTag nbt = entity.getCompoundOrEmpty("nbt");
            ListTag pos = entity.getListOrEmpty("pos");
            CourseElement kind = CourseElement.byKey(nbt.getStringOr("Kind", CourseElement.GATE.key()));
            GateSize size = GateSize.byKey(nbt.getStringOr("Size", GateSize.MEDIUM.key()));
            double width;
            double height;
            if (kind.gate()) {
                width = size.width();
                height = size.height();
            } else {
                width = kind.outerWidth(nbt.getDoubleOr("OpeningWidth", kind.defaultWidth()));
                height = kind.outerHeight(nbt.getDoubleOr("OpeningHeight", kind.defaultHeight()));
            }
            ScoreArea score = new ScoreArea(ScoreArea.Role.byKey(nbt.getStringOr("ScoreRole", ScoreArea.Role.NONE.key())),
                    ScoreArea.sideByKey(nbt.getStringOr("ScoreSide", ScoreArea.sideKey(CourseElement.LEFT))),
                    nbt.getDoubleOr("ScoreReach", ScoreArea.DEFAULT_REACH), nbt.getDoubleOr("ScoreBelow", ScoreArea.DEFAULT_BELOW),
                    nbt.getDoubleOr("ScoreAbove", ScoreArea.DEFAULT_ABOVE), false);
            CompoundTag marker = nbt.getCompoundOrEmpty("WorldCourse");
            int slot = marker.getIntOr("Slot", -1);
            List<CourseOrder.Pass> passes = CourseOrder.decode(marker.getStringOr("Passes", ""), slot);
            String mode = marker.getStringOr("Mode", "laps");
            modes.add(mode);
            Piece piece = new Piece(i, kind, pos.getDoubleOr(0, 0), pos.getDoubleOr(1, 0), pos.getDoubleOr(2, 0),
                    nbt.getListOrEmpty("Rotation").getFloatOr(0, 0.0F), size, nbt.getIntOr("Variant", 0), width, height, score, passes, mode,
                    nbt.getStringOr("id", ""));
            pieces.add(piece);
            for (CourseOrder.Pass pass : passes) {
                assertFalse(bySlot.containsKey(pass.index()), id + ": Platz " + pass.index() + " doppelt");
                bySlot.put(pass.index(), piece);
                reverseBySlot.put(pass.index(), pass.reverse());
            }
        }
        assertEquals(1, modes.size(), id + ": Gates nennen verschiedene Modi " + modes);
        List<Piece> order = new ArrayList<>();
        List<Boolean> reverse = new ArrayList<>();
        for (int slot = 0; slot < bySlot.size(); slot++) {
            Piece piece = bySlot.get(slot);
            assertNotNull(piece, id + ": Kursreihenfolge lückenhaft bei " + slot);
            order.add(piece);
            reverse.add(reverseBySlot.get(slot));
        }
        return new Course(name(id), pieces, order, reverse, modes.iterator().next());
    }

    private static List<Course> current() throws IOException {
        List<Course> list = new ArrayList<>();
        for (String id : referencedTemplates()) {
            list.add(load(id));
        }
        return list;
    }

    private static boolean sixteenth(double value) {
        return Math.abs(value * 16.0 - Math.rint(value * 16.0)) < 1.0E-9;
    }

    private static boolean alternating(List<Integer> sides) {
        for (int i = 1; i < sides.size(); i++) {
            if (sides.get(i).equals(sides.get(i - 1))) {
                return false;
            }
        }
        return sides.size() >= 3;
    }

    private static List<Integer> runSides(Course course, CourseElement kind) {
        List<Integer> best = new ArrayList<>();
        List<Integer> run = new ArrayList<>();
        for (Piece piece : course.order()) {
            if (piece.kind() == kind) {
                run.add(kind == CourseElement.GATE_POLE ? piece.score().side() : piece.variant());
                if (run.size() > best.size()) {
                    best = new ArrayList<>(run);
                }
            } else {
                run.clear();
            }
        }
        return best;
    }

    private static boolean raisedToGround(Piece piece) {
        return piece.kind() == CourseElement.FLAG && piece.y() > 1.5 && piece.y() - piece.score().below() <= 1.0 - ScoreArea.DEFAULT_BELOW + 1.0E-9;
    }

    private static boolean sharesPost(Course course) {
        List<GateGeometry> gates = new ArrayList<>();
        for (Piece piece : course.pieces()) {
            if (piece.kind() == CourseElement.GATE) {
                gates.add(piece.placed());
            }
        }
        for (int a = 0; a < gates.size(); a++) {
            for (int b = a + 1; b < gates.size(); b++) {
                if (GateStacking.sharedSide(gates.get(a), gates.get(b)) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void newWorldsGetCoursesWithTheMechanicsOf060() throws IOException {
        boolean loop = false;
        boolean reverse = false;
        boolean cube = false;
        boolean row = false;
        boolean fine = false;
        boolean pointToPoint = false;
        boolean flagToGround = false;
        for (Course course : current()) {
            String t = course.template();
            boolean courseLoop = false;
            boolean courseReverse = false;
            for (Piece piece : course.pieces()) {
                assertTrue(sixteenth(piece.x()) && sixteenth(piece.y()) && sixteenth(piece.z()), t + ": Lage nicht im 1/16-Raster " + piece);
                assertFalse(piece.passes().isEmpty(), t + ": Element ohne Platz im Kurs " + piece);
                courseLoop |= piece.passes().size() > 1;
                courseReverse |= piece.passes().stream().anyMatch(CourseOrder.Pass::reverse);
                fine |= Math.abs(piece.y() - Math.rint(piece.y())) > 1.0E-9;
                cube |= piece.kind() == CourseElement.CUBE;
                flagToGround |= raisedToGround(piece);
            }
            loop |= courseLoop;
            reverse |= courseReverse;
            row |= sharesPost(course);
            pointToPoint |= course.pointToPoint();
            if (t.startsWith("canyon_run")) {
                assertTrue(course.pointToPoint(), t + ": Canyon-Run soll von Punkt zu Punkt fliegen");
                assertTrue(course.pieces().stream().anyMatch(p -> p.kind() == CourseElement.FLAG), t + ": Wendeflagge fehlt");
                assertTrue(runSides(course, CourseElement.POLE).size() >= 2, t + ": Slalomstangen fehlen");
            } else {
                assertFalse(course.pointToPoint(), t + ": Rundkurs erwartet");
            }
            if (t.startsWith("drone_arena")) {
                assertTrue(courseLoop && courseReverse, t + ": Schleife und Rückwärts-Durchflug fehlen");
                assertTrue(course.pieces().stream().anyMatch(p -> p.kind() == CourseElement.CUBE), t + ": Tauchwürfel fehlt");
                assertTrue(course.pieces().stream().anyMatch(WorldCourseMechanicsTest::raisedToGround),
                        t + ": hohe Flagge, die bis zum Boden wertet, fehlt");
            }
            if (t.startsWith("forest_weave")) {
                List<Integer> sides = runSides(course, CourseElement.GATE_POLE);
                assertTrue(alternating(sides), t + ": Pfosten-Slalom aus mindestens drei Gate-Stangen mit wechselnder Seite fehlt: " + sides);
                for (Piece piece : course.pieces()) {
                    if (piece.kind() == CourseElement.GATE_POLE) {
                        assertEquals(ScoreArea.Role.PASS, piece.score().role(), t + ": Gate-Stange wertet nicht");
                    }
                }
            }
            if (t.startsWith("field_slalom")) {
                assertTrue(alternating(runSides(course, CourseElement.POLE)), t + ": Slalom aus Slalomstangen fehlt");
                assertTrue(sharesPost(course), t + ": Gate-Reihe mit gemeinsamem Pfosten fehlt");
            }
        }
        assertTrue(loop, "kein Kurs mit Schleife");
        assertTrue(reverse, "kein Kurs mit Rückwärts-Durchflug");
        assertTrue(cube, "kein Kurs mit Tauchwürfel");
        assertTrue(row, "kein Kurs mit Gate-Reihe");
        assertTrue(fine, "kein Kurs mit feiner Lage (1/16)");
        assertTrue(pointToPoint, "kein Kurs von Punkt zu Punkt");
        assertTrue(flagToGround, "keine Flagge, die bis zum Boden wertet");
    }

    @Test
    void templatesNeverCarryACourseIdOrUuid() throws IOException {
        Set<String> ids = new LinkedHashSet<>(referencedTemplates());
        for (String legacy : LEGACY.keySet()) {
            ids.add("propwash:world_course/" + legacy);
        }
        for (String id : ids) {
            CompoundTag root = NbtIo.readCompressed(file(id), NbtAccounter.unlimitedHeap());
            ListTag entities = root.getListOrEmpty("entities");
            assertFalse(entities.isEmpty(), id);
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag nbt = entities.getCompoundOrEmpty(i).getCompoundOrEmpty("nbt");
                assertTrue(ENTITY_KEYS.containsAll(nbt.keySet()), id + ": unerwartete Schlüssel " + nbt.keySet());
                assertEquals(0, nbt.getIntOr("Course", -5), id + ": Vorlage nennt eine Kurs-Nummer");
                assertEquals(-1, nbt.getIntOr("GateIndex", -5), id + ": Vorlage nennt einen Platz im Kurs");
                assertTrue(MARKER_KEYS.containsAll(nbt.getCompoundOrEmpty("WorldCourse").keySet()),
                        id + ": unerwartete Marker-Schlüssel " + nbt.getCompoundOrEmpty("WorldCourse").keySet());
            }
        }
    }

    @Test
    void oldTemplatesStayForStructuresStartedInOldWorlds() throws IOException, NoSuchAlgorithmException {
        Set<String> referenced = referencedTemplates();
        for (Map.Entry<String, String> legacy : LEGACY.entrySet()) {
            String id = "propwash:world_course/" + legacy.getKey();
            Path path = file(id);
            assertTrue(Files.exists(path), id + ": alte Vorlage fehlt – halb erzeugte Kurse in alten Welten brauchen sie");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            assertEquals(legacy.getValue(), hash, id + ": alte Vorlage verändert");
            assertFalse(referenced.contains(id), id + ": neue Welten sollen die Vorlagen von 0.6.0 bekommen");
        }
    }

    private static JsonObject lines() throws IOException {
        Path file = TestPaths.common().resolve("src/devtest/resources" + LINES);
        if (!Files.exists(file)) {
            fail("Fluglinien fehlen: " + file + " (python3 tools/data/world_courses.py)");
        }
        return read(file);
    }

    private static List<double[]> line(JsonObject all, String template) {
        JsonObject entry = all.getAsJsonObject(template);
        assertNotNull(entry, template + ": keine Fluglinie");
        List<double[]> points = new ArrayList<>();
        for (JsonElement element : entry.getAsJsonArray("line")) {
            JsonArray point = element.getAsJsonArray();
            points.add(new double[]{point.get(0).getAsDouble(), point.get(1).getAsDouble(), point.get(2).getAsDouble()});
        }
        return points;
    }

    static List<LapTracker.Event> fly(List<GateGeometry> gates, boolean pointToPoint, List<double[]> line) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(1, gates, pointToPoint, 0L);
        List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double[] at = line.getFirst();
        tracker.update(now, at[0], at[1], at[2]);
        for (int i = 1; i < line.size(); i++) {
            double[] to = line.get(i);
            double length = Math.sqrt(Math.pow(to[0] - at[0], 2) + Math.pow(to[1] - at[1], 2) + Math.pow(to[2] - at[2], 2));
            int steps = Math.max(1, (int) Math.ceil(length / STEP));
            for (int s = 1; s <= steps; s++) {
                double f = s / (double) steps;
                now += 20L * MS;
                tracker.update(now, at[0] + (to[0] - at[0]) * f, at[1] + (to[1] - at[1]) * f, at[2] + (to[2] - at[2]) * f);
                tracker.tick(now);
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
    void everyCourseIsFlownCleanlyAlongItsLine() throws IOException {
        JsonObject all = lines();
        for (Course course : current()) {
            String t = course.template();
            JsonObject entry = all.getAsJsonObject(t);
            assertNotNull(entry, t + ": keine Fluglinie");
            assertEquals(course.mode(), entry.get("mode").getAsString(), t + ": Modus der Fluglinie");
            assertEquals(course.order().size(), entry.get("passes").getAsInt(), t + ": Durchgänge der Fluglinie");
            List<LapTracker.Event> events = fly(course.geometry(), course.pointToPoint(), line(all, t));
            int passes = course.order().size();
            List<Integer> expected = new ArrayList<>();
            for (int i = 1; i < (course.pointToPoint() ? passes - 1 : passes); i++) {
                expected.add(i);
            }
            String seen = t + ": " + events;
            assertEquals(1, of(events, LapTracker.EventType.START).size(), seen);
            assertEquals(expected, of(events, LapTracker.EventType.GATE), seen);
            assertEquals(1, of(events, LapTracker.EventType.LAP).size(), seen);
            assertEquals(course.pointToPoint() ? 1 : 0, of(events, LapTracker.EventType.FINISH).size(), seen);
            for (LapTracker.EventType bad : List.of(LapTracker.EventType.MISSED, LapTracker.EventType.WRONG_WAY, LapTracker.EventType.WRONG_SIDE,
                    LapTracker.EventType.ABORT)) {
                assertTrue(of(events, bad).isEmpty(), seen);
            }
        }
    }

    @Test
    void markersOfOldAndNewTemplatesGiveTheirPassesAndMode() throws IOException {
        Set<String> ids = new LinkedHashSet<>(referencedTemplates());
        for (String legacy : LEGACY.keySet()) {
            ids.add("propwash:world_course/" + legacy);
        }
        for (String id : ids) {
            CompoundTag root = NbtIo.readCompressed(file(id), NbtAccounter.unlimitedHeap());
            ListTag entities = root.getListOrEmpty("entities");
            boolean legacy = LEGACY.containsKey(name(id)) && !referencedTemplates().contains(id);
            Course course = legacy ? null : load(id);
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag tag = entities.getCompoundOrEmpty(i).getCompoundOrEmpty("nbt").getCompoundOrEmpty("WorldCourse");
                DataResult<WorldCourseMarker> parsed = WorldCourseMarker.CODEC.parse(NbtOps.INSTANCE, tag);
                WorldCourseMarker marker = parsed.getOrThrow(message -> new AssertionError(id + ": " + message));
                if (legacy) {
                    assertEquals(List.of(new CourseOrder.Pass(tag.getIntOr("Slot", -1), false)), marker.passes(), id);
                    assertEquals(CourseMode.LAPS, marker.mode(), id);
                    assertEquals(tag, WorldCourseMarker.CODEC.encodeStart(NbtOps.INSTANCE, marker).getOrThrow(), id + ": alter Marker verändert");
                } else {
                    assertEquals(course.pieces().get(i).passes(), marker.passes(), id);
                    assertEquals(course.pointToPoint() ? CourseMode.POINT_TO_POINT : CourseMode.LAPS, marker.mode(), id);
                }
            }
        }
    }

    @Test
    void twoFindsOfTheSameStructureBecomeTwoCoursesWithTheirOwnLeaderboards(@TempDir Path dir) {
        CourseRegistry registry = CourseRegistry.offline(dir.resolve("propwash_race.json"), new RaceRecords(), new RecordGhosts());
        String dimension = "minecraft:overworld";
        WorldCourseSite first = new WorldCourseSite(96, 64, -40, 0);
        WorldCourseSite second = new WorldCourseSite(4192, 71, 512, 1);
        int a = registry.createWorldCourse(dimension, "drone_arena", first.key(), new int[]{100, 65, -38}, CourseMode.LAPS);
        int b = registry.createWorldCourse(dimension, "drone_arena", second.key(), new int[]{4190, 72, 516}, CourseMode.LAPS);
        CourseRegistry.Course one = registry.course(a);
        CourseRegistry.Course two = registry.course(b);
        assertNotNull(one);
        assertNotNull(two);
        assertNotEquals(a, b, "zwei Fundorte teilen sich eine Kurs-Nummer");
        assertNotEquals(one.key(), two.key(), "zwei Fundorte teilen sich eine Kurs-ID");
        assertSame(one, registry.worldCourse(dimension, first.key()));
        assertSame(two, registry.worldCourse(dimension, second.key()));
        UUID pilot = UUID.fromString("00000000-0000-4000-8000-000000000042");
        registry.submitLap(one, pilot, "Pilot", "race_5", 42_318_000_000L, 1_000L);
        assertEquals(1, registry.board(one).entries().size());
        assertTrue(registry.board(two).entries().isEmpty(), "Zeit der ersten Arena steht in der Bestenliste der zweiten");
        int canyon = registry.createWorldCourse(dimension, "canyon_run", new WorldCourseSite(-800, 80, 1600, 2).key(), new int[]{-800, 81, 1600},
                CourseMode.POINT_TO_POINT);
        assertEquals(CourseMode.POINT_TO_POINT, registry.course(canyon).mode());
        assertEquals(CourseMode.LAPS, one.mode());
    }
}

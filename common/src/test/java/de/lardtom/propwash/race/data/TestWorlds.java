package de.lardtom.propwash.race.data;

import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RecordGhosts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

final class TestWorlds {
    static final String FILE = "propwash_race.json";
    static final String OVERWORLD = "minecraft:overworld";
    static final UUID ANNA = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID BEN = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    static final UUID CARL = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    static final long S = 1_000_000_000L;
    static final long DAY = 1_792_300_000_000L;

    static final String V040 = """
            {"nextCourseId": 2, "courses": [{"id": 1, "dimension": "minecraft:overworld", "owner": "", "ownerName": "", "layout": 1,
              "worldCourse": "", "site": "", "gates": [
                {"uuid": "00000000-0000-0000-0000-000000000011", "index": 0, "x": -30.5, "y": -60.0, "z": 16.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000012", "index": 1, "x": -30.5, "y": -56.5, "z": 30.5, "yaw": 180.0, "size": "small",
                 "mount": "standing", "flipped": false, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000013", "index": 2, "x": 2.5, "y": -60.0, "z": 30.5, "yaw": 90.0, "size": "large",
                 "slot": -1}],
              "leaderboard": [], "archive": []}]}
            """;

    static final String V050 = """
            {"nextCourseId": 3, "courses": [
              {"id": 1, "dimension": "minecraft:overworld", "owner": "00000000-0000-4000-8000-0000000000a1", "ownerName": "Anna",
               "name": "Stapel", "laps": 3, "layout": 1, "worldCourse": "", "site": "", "gates": [
                {"uuid": "00000000-0000-0000-0000-000000000031", "index": 0, "x": -12.5, "y": -60.0, "z": -12.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000032", "index": 1, "x": -12.5, "y": -60.0, "z": 3.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1}],
               "leaderboard": [], "archive": []},
              {"id": 2, "dimension": "minecraft:overworld", "owner": "", "ownerName": "", "name": "Elemente",
              "laps": 3, "layout": 2, "worldCourse": "", "site": "", "gates": [
                {"uuid": "00000000-0000-0000-0000-000000000021", "index": 0, "x": -42.5, "y": -60.0, "z": -37.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 45.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000022", "index": 1, "x": -42.5, "y": -60.0, "z": -29.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1,
                 "kind": "pole", "variant": 0},
                {"uuid": "00000000-0000-0000-0000-000000000023", "index": 2, "x": -42.5, "y": -60.0, "z": -19.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 6.0, "height": 5.0, "slot": -1,
                 "kind": "flag", "variant": 1},
                {"uuid": "00000000-0000-0000-0000-000000000024", "index": 3, "x": -30.0, "y": -59.5, "z": -10.0, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1,
                 "kind": "freeform", "variant": 0, "region": "f1:z:3x2:1,0:Pw=="},
                {"uuid": "00000000-0000-0000-0000-000000000025", "index": 4, "x": -20.5, "y": -60.0, "z": -10.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 4.5, "height": 4.5, "slot": -1,
                 "kind": "cube", "variant": 2}],
              "leaderboard": [], "archive": []}]}
            """;

    static final String V052 = """
            {"nextCourseId": 4, "courses": [
              {"id": 1, "dimension": "minecraft:overworld", "owner": "00000000-0000-4000-8000-0000000000a1", "ownerName": "Anna",
               "name": "Stapel-Kurs", "laps": 3, "layout": 1, "worldCourse": "", "site": "", "gates": [
                {"uuid": "00000000-0000-0000-0000-000000000041", "index": 0, "x": -48.5, "y": -60.0, "z": -39.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000042", "index": 1, "x": -48.5, "y": -60.0, "z": -31.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000043", "index": 2, "x": -40.5, "y": -60.0, "z": -31.5, "yaw": 90.0, "size": "large",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1}],
               "leaderboard": [], "archive": []},
              {"id": 2, "dimension": "minecraft:overworld", "owner": "00000000-0000-4000-8000-0000000000a1", "ownerName": "Anna",
               "name": "Frei gedreht", "laps": -1, "layout": 1, "worldCourse": "", "site": "", "gates": [
                {"uuid": "00000000-0000-0000-0000-000000000051", "index": 0, "x": 10.5, "y": -60.0, "z": 10.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 30.0, "pitch": 15.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000052", "index": 1, "x": 10.5, "y": -60.0, "z": 30.5, "yaw": 0.0, "size": "medium",
                 "mount": "floor_flat", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1}],
               "leaderboard": [], "archive": []},
              {"id": 3, "dimension": "minecraft:the_nether", "owner": "", "ownerName": "", "name": "", "laps": 0, "layout": 1,
               "worldCourse": "", "site": "", "gates": [
                {"uuid": "00000000-0000-0000-0000-000000000061", "index": 0, "x": 0.5, "y": 70.0, "z": 0.5, "yaw": 0.0, "size": "small",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1}],
               "leaderboard": [], "archive": []}]}
            """;

    private TestWorlds() {
    }

    static Path write(Path dir, String json) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(FILE);
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    static CourseRegistry open(Path dir, String json) throws IOException {
        return CourseRegistry.offline(write(dir, json), new RaceRecords(), new RecordGhosts());
    }

    static CourseRegistry reopen(Path file) {
        return CourseRegistry.offline(file, new RaceRecords(), new RecordGhosts());
    }
}

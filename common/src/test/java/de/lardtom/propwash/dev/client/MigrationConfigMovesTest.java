package de.lardtom.propwash.dev.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationConfigMovesTest {
    private static final String DRONE_040 = """
            {"propWash": true, "groundEffect": true, "batterySag": true, "infiniteBattery": false, "crashDisarm": true,
             "propStrikes": true, "wind": true, "turbulence": true, "hardwareLimits": true, "crashTolerance": 1.0,
             "crashDamage": true, "crashDamageStrength": 1.0, "gravity": 1.0, "stateSendHz": 20}""";
    private static final String SERVER_040 = """
            {"pilotPermissionLevel": 0, "allowUnlimitedRange": true, "maxPilotDistance": 160.0, "batteryChargeSeconds": 90.0,
             "partWear": true, "worldCourses": true, "allowSpectating": true}""";
    private static final String ACTION_CAM_040 = """
            {"height": 1080, "fps": 60, "stabilization": "SMOOTH", "quality": "STANDARD", "grain": true, "autoRecord": false,
             "audio": true, "showIndicator": true, "memoryMb": 512, "encoderThreads": 0}""";
    private static final String DRONE_RENDER_040 = """
            {"visualScale": 1.75, "propBlurThreshold": 0.12, "armLeds": true, "shadow": true, "renderDistance": 128, "fpvProps": true}""";
    private static final String RACE_040 = """
            {"laps": 3, "countdown": false, "countdownSeconds": 3, "startHint": true, "showMessages": true, "reportResults": true,
             "ghost": true, "recordGhost": true, "startLights": true, "raceLine": true, "raceLineGates": 3}""";
    private static final String RACE_052 = """
            {"laps": 3, "countdown": false, "countdownSeconds": 3, "startHint": false, "showMessages": true, "reportResults": true,
             "ghost": true, "recordGhost": true, "startLights": true, "raceLine": true, "raceLineGates": 3, "gateRotateStep": 90.0}""";
    private static final String OSD_040 = """
            {"version": 3, "preset": "MINIMAL", "opacity": 60, "scale": 1.0, "hideVanillaHud": true,
             "elements": {"crosshair": {"enabled": true, "col": 15, "row": 8, "color": "", "scale": 0.0}}}""";

    @TempDir
    Path dir;

    private record Result(List<String> ok, List<String> open, List<String> failures) {
        String moves() {
            return this.ok.stream().filter(line -> line.startsWith("bewusste Umzüge")).findFirst().orElse("");
        }
    }

    private Result check(String stand, Map<String, String> before, Map<String, String> onDisk) throws IOException {
        JsonObject files = new JsonObject();
        for (Map.Entry<String, String> file : before.entrySet()) {
            files.add(file.getKey(), JsonParser.parseString(file.getValue()));
            Files.writeString(this.dir.resolve(file.getKey()), onDisk.getOrDefault(file.getKey(), file.getValue()), StandardCharsets.UTF_8);
        }
        JsonObject configs = new JsonObject();
        configs.add("files", files);
        List<String> ok = new ArrayList<>();
        List<String> open = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        MigrationSelfTests.configChecks(this.dir, configs, stand, false, ok, open, failures);
        return new Result(ok, open, failures);
    }

    @Test
    void configsFromZeroFourKnowTheDeliberateMovesOfZeroFive() throws IOException {
        Result result = check("0.4.0", Map.of("drone.json", DRONE_040, "server.json", SERVER_040, "action_cam.json", ACTION_CAM_040,
                "drone_render.json", DRONE_RENDER_040), Map.of());
        assertEquals(List.of(), result.failures());
        String moves = result.moves();
        assertTrue(moves.contains("drone 13 Physikfelder → Welt-Regeln"), moves);
        assertTrue(moves.contains("server partWear → Gamerule crash_damage"), moves);
        assertTrue(moves.contains("action_cam memoryMb entfällt"), moves);
        assertTrue(moves.contains("drone_render visualScale 1.75 → 1.0 mit sizeNotice"), moves);
    }

    @Test
    void oldDefaultSizeIsComparedAsTodaysSizeWithNotice() throws IOException {
        String withoutNotice = "{\"version\": 2, \"sizeNotice\": false, \"visualScale\": 1.0}";
        Result result = check("0.4.0", Map.of("drone_render.json", DRONE_RENDER_040), Map.of("drone_render.json", withoutNotice));
        assertEquals(List.of("Config drone_render.json.sizeNotice: erwartet true, ist false"), result.failures());
    }

    @Test
    void ownSizeAndCurrentConfigVersionStayAsTheyAre() throws IOException {
        Result own = check("0.4.0", Map.of("drone_render.json", "{\"visualScale\": 2.0, \"renderDistance\": 128}"), Map.of());
        assertEquals(List.of(), own.failures());
        assertFalse(own.moves().contains("drone_render"), own.moves());
        Result current = check("0.5.2", Map.of("drone_render.json", "{\"version\": 2, \"sizeNotice\": false, \"visualScale\": 1.75}"), Map.of());
        assertEquals(List.of(), current.failures());
        assertFalse(current.moves().contains("drone_render"), current.moves());
    }

    @Test
    void movesDoNotHideOtherDifferences() throws IOException {
        Result result = check("0.4.0", Map.of("drone.json", DRONE_040.replace("\"stateSendHz\": 20", "\"stateSendHz\": 30"),
                "server.json", SERVER_040.replace("\"worldCourses\": true", "\"worldCourses\": false")), Map.of("drone.json", DRONE_040,
                "server.json", SERVER_040));
        assertEquals(List.of("Config drone.json.stateSendHz: erwartet 30, ist 20", "Config server.json.worldCourses: erwartet false, ist true"),
                result.failures().stream().sorted().toList());
    }

    @Test
    void ghostSwitchesOfOldStandsAreComparedAsTheGhostMode() throws IOException {
        for (Map.Entry<String, String> stand : Map.of("0.4.0", RACE_040, "0.5.0", RACE_052, "0.5.2", RACE_052).entrySet()) {
            Result result = check(stand.getKey(), Map.of("race.json", stand.getValue()), Map.of());
            assertEquals(List.of(), result.failures(), stand.getKey());
            assertTrue(result.moves().contains("race ghost true, recordGhost true → ghostMode OWN"), result.moves());
        }
    }

    @Test
    void everyOldGhostPairHasItsMode() throws IOException {
        Map<String, String> modes = Map.of("\"ghost\": true, \"recordGhost\": false", "OWN", "\"ghost\": false, \"recordGhost\": true", "BOARD",
                "\"ghost\": false, \"recordGhost\": false", "OFF");
        for (Map.Entry<String, String> pair : modes.entrySet()) {
            Result result = check("0.5.2", Map.of("race.json", RACE_052.replace("\"ghost\": true, \"recordGhost\": true", pair.getKey())), Map.of());
            assertEquals(List.of(), result.failures(), pair.getKey());
            assertTrue(result.moves().contains("→ ghostMode " + pair.getValue() + " "), result.moves());
        }
    }

    @Test
    void anotherGhostModeThanTheMoveIsADifference() throws IOException {
        String off = RACE_052.replace("\"ghost\": true, \"recordGhost\": true", "\"ghostMode\": \"OFF\"");
        Result result = check("0.5.2", Map.of("race.json", RACE_052), Map.of("race.json", off));
        assertEquals(List.of("Config race.json.ghostMode: erwartet \"OWN\", ist \"OFF\""), result.failures());
    }

    @Test
    void currentGhostModeStaysWithoutMove() throws IOException {
        String current = RACE_052.replace("\"ghost\": true, \"recordGhost\": true", "\"ghostMode\": \"BOARD\"");
        Result result = check("0.6.0", Map.of("race.json", current), Map.of());
        assertEquals(List.of(), result.failures());
        assertFalse(result.moves().contains("race"), result.moves());
    }

    @Test
    void markerIsCheckedWithoutOwnOsdLayoutInTheExpectation() throws IOException {
        Result result = check("0.4.0", Map.of("osd.json", OSD_040), Map.of());
        assertEquals(List.of(), result.failures());
        assertTrue(result.ok().contains("Marker aus"), String.valueOf(result.ok()));
        assertFalse(result.ok().contains("eigenes OSD-Layout erhalten"), String.valueOf(result.ok()));
    }

    @Test
    void markerSwitchedOnIsADifferenceWithoutOwnOsdLayoutToo() throws IOException {
        String on = OSD_040.replace("\"elements\": {", "\"elements\": {\"flight_path_marker\": {\"enabled\": true, \"col\": 10, \"row\": 5, "
                + "\"color\": \"\", \"scale\": 0.0}, ");
        Result result = check("0.4.0", Map.of("osd.json", OSD_040), Map.of("osd.json", on));
        assertTrue(result.failures().contains("Flugbahn-Marker nach dem Umstieg an"), String.valueOf(result.failures()));
    }

    @Test
    void withoutOsdConfigTheMarkerIsOpen() throws IOException {
        Result result = check("0.4.0", Map.of("drone.json", DRONE_040), Map.of());
        assertTrue(result.open().stream().anyMatch(line -> line.startsWith("Flugbahn-Marker nicht geprüft")), String.valueOf(result.open()));
    }
}

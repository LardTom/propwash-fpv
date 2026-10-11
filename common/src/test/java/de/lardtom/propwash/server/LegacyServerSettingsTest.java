package de.lardtom.propwash.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyServerSettingsTest {
    @Test
    void onlyAnExplicitFalseCounts() {
        assertTrue(LegacyServerSettings.partWearOff("{\"pilotPermissionLevel\": 0, \"partWear\": false, \"worldCourses\": true}"));
        assertFalse(LegacyServerSettings.partWearOff("{\"partWear\": true}"));
        assertFalse(LegacyServerSettings.partWearOff("{\"pilotPermissionLevel\": 0}"));
        assertFalse(LegacyServerSettings.partWearOff("{\"partWear\": \"false\"}"));
        assertFalse(LegacyServerSettings.partWearOff("{\"partWear\": null}"));
        assertFalse(LegacyServerSettings.partWearOff("{\"partWear\": {}}"));
        assertFalse(LegacyServerSettings.partWearOff("[false]"));
        assertFalse(LegacyServerSettings.partWearOff(""));
        assertFalse(LegacyServerSettings.partWearOff("{\"partWear\": fal"));
    }

    @Test
    void readsTheOldServerFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("server.json");
        assertFalse(LegacyServerSettings.partWearOff(file));
        Files.writeString(file, "{\n  \"pilotPermissionLevel\": 0,\n  \"partWear\": false\n}\n", StandardCharsets.UTF_8);
        assertTrue(LegacyServerSettings.partWearOff(file));
        Files.writeString(file, "{\n  \"partWear\": true\n}\n", StandardCharsets.UTF_8);
        assertFalse(LegacyServerSettings.partWearOff(file));
        assertFalse(LegacyServerSettings.partWearOff(dir));
    }
}

package de.lardtom.propwash.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LegacyServerSettings {
    public static final String PART_WEAR = "partWear";

    private LegacyServerSettings() {
    }

    public static boolean partWearOff(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return false;
            }
            JsonElement value = root.getAsJsonObject().get(PART_WEAR);
            return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && !value.getAsBoolean();
        } catch (JsonParseException | IllegalStateException e) {
            return false;
        }
    }

    public static boolean partWearOff(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return partWearOff(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
    }
}

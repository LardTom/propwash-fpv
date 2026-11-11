package de.lardtom.propwash.webexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

public final class ExportJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private ExportJson() {
    }

    public static JsonElement num(double value) {
        return Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    }

    public static JsonElement num(long value) {
        return new JsonPrimitive(value);
    }

    public static JsonElement num(OptionalDouble value) {
        return value.isPresent() ? num(value.getAsDouble()) : JsonNull.INSTANCE;
    }

    public static JsonElement str(String value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }

    public static JsonElement str(Optional<String> value) {
        return value.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE);
    }

    public static JsonElement id(Identifier value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value.toString());
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    public static JsonArray nums(double... values) {
        JsonArray out = new JsonArray();
        for (double value : values) {
            out.add(num(value));
        }
        return out;
    }

    public static JsonArray ints(int... values) {
        JsonArray out = new JsonArray();
        for (int value : values) {
            out.add(value);
        }
        return out;
    }

    public static JsonArray strings(Collection<String> values) {
        JsonArray out = new JsonArray();
        for (String value : values) {
            out.add(value);
        }
        return out;
    }

    public static String text(JsonElement element) {
        return GSON.toJson(element) + "\n";
    }

    public static void write(Path file, JsonElement element) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text(element), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Web-Export: " + file + " nicht geschrieben", e);
        }
    }

    public static void write(Path file, byte[] data) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Web-Export: " + file + " nicht geschrieben", e);
        }
    }
}

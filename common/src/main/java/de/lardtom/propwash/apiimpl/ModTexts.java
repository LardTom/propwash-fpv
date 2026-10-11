package de.lardtom.propwash.apiimpl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class ModTexts {
    public static final ModTexts INSTANCE = new ModTexts(ModTexts::readFromMods);
    public static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z0-9_]{2,16}");

    private final Function<String, List<byte[]>> source;
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public ModTexts(Function<String, List<byte[]>> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public Optional<String> text(String languageCode, String key) {
        Objects.requireNonNull(languageCode, "languageCode");
        Objects.requireNonNull(key, "key");
        String code = languageCode.toLowerCase(Locale.ROOT);
        if (!LANGUAGE_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Ungültiger Sprachcode: " + languageCode);
        }
        return Optional.ofNullable(this.cache.computeIfAbsent(code, c -> merge(this.source.apply(c))).get(key));
    }

    public static Map<String, String> merge(List<byte[]> files) {
        Map<String, String> out = new HashMap<>();
        for (byte[] file : files) {
            JsonObject json;
            try {
                JsonElement element = JsonParser.parseString(new String(file, StandardCharsets.UTF_8));
                if (!element.isJsonObject()) {
                    continue;
                }
                json = element.getAsJsonObject();
            } catch (JsonParseException | IllegalStateException e) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    out.putIfAbsent(entry.getKey(), value.getAsString());
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    public static boolean isLanguageFile(String path, String code) {
        String[] segments = path.split("/");
        return segments.length == 4 && "assets".equals(segments[0]) && "lang".equals(segments[2]) && (code + ".json").equals(segments[3]);
    }

    private static List<byte[]> readFromMods(String code) {
        List<byte[]> files = new ArrayList<>();
        for (String modId : Services.PLATFORM.resourceModIds()) {
            try {
                for (Map.Entry<String, byte[]> file : Services.PLATFORM.readModResources(modId, "assets", "/lang/" + code + ".json").entrySet()) {
                    if (isLanguageFile(file.getKey(), code)) {
                        files.add(file.getValue());
                    }
                }
            } catch (RuntimeException e) {
                Constants.LOG.debug("Sprachdateien von {} nicht lesbar: {}", modId, e.getMessage());
            }
        }
        return files;
    }
}

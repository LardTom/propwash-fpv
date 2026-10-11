package de.lardtom.propwash.sim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

final class GoldenFile {
    private GoldenFile() {
    }

    static String hash(CharSequence text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void bits(StringBuilder out, double value) {
        out.append(Long.toHexString(Double.doubleToRawLongBits(value))).append(',');
    }

    static Path path(String resource) {
        for (Path candidate : List.of(Path.of("common/src/test/resources", resource), Path.of("src/test/resources", resource))) {
            if (Files.isDirectory(candidate.getParent())) {
                return candidate;
            }
        }
        return Path.of("common/src/test/resources", resource);
    }

    static void verify(String resource, Map<String, String> actual, String message) {
        Path path = path(resource);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path.getParent());
                StringBuilder text = new StringBuilder();
                actual.forEach((key, hash) -> text.append(key).append(' ').append(hash).append('\n'));
                Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            fail("Golden-Datei fehlte und wurde neu geschrieben: " + path + " — nur einchecken, wenn die Änderung gewollt ist");
        }
        Map<String, String> expected = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    int space = line.lastIndexOf(' ');
                    expected.put(line.substring(0, space), line.substring(space + 1));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!entry.getValue().equals(actual.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        assertEquals(List.of(), changed, message);
        assertEquals(expected.keySet(), actual.keySet());
    }
}

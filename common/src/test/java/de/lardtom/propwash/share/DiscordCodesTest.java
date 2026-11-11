package de.lardtom.propwash.share;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordCodesTest {
    static final String PROPERTY = "propwash.share.realCodes";
    static final String OUT_PROPERTY = "propwash.share.realCodesOut";

    private static String setting(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(property.toUpperCase().replace('.', '_'));
        }
        return value == null || value.isBlank() ? null : value;
    }

    @Test
    void realCodesConvertAndKeepTheirContent() throws IOException {
        String file = setting(PROPERTY);
        Assumptions.assumeTrue(file != null && Files.isRegularFile(Path.of(file)), "keine lokale Datei mit echten Codes (" + PROPERTY + ")");
        ShareTables offline = ShareTables.builder().build();
        Map<ShareStatus, Integer> statuses = new EnumMap<>(ShareStatus.class);
        List<String> unexplained = new ArrayList<>();
        List<String> out = new ArrayList<>();
        int total = 0;
        int maxLength = 0;
        for (String line : Files.readAllLines(Path.of(file), StandardCharsets.UTF_8)) {
            String code = line.split("\t", 2)[0].strip();
            if (code.isEmpty()) {
                continue;
            }
            total++;
            ShareConvert.Conversion conversion = ShareConvert.toPw3(code);
            statuses.merge(conversion.status(), 1, Integer::sum);
            if (!conversion.ok()) {
                unexplained.add("Zeile " + total + ": " + conversion.status() + conversion.reason().map(r -> " " + r).orElse(""));
                out.add(code + "\t" + conversion.status());
                continue;
            }
            ShareContent legacy = ShareFormat.decode(code, offline, false).require();
            ShareDecoded pw3 = ShareFormat.decode(conversion.code().orElseThrow(), ShareTables.frozen(), false);
            assertEquals(ShareStatus.OK, pw3.status(), "Zeile " + total);
            assertEquals(legacy, pw3.require(), "Zeile " + total);
            assertEquals(conversion.code().orElseThrow(), ShareConvert.toPw3(code).code().orElseThrow(), "Zeile " + total);
            maxLength = Math.max(maxLength, conversion.code().orElseThrow().length());
            out.add(code + "\t" + conversion.code().orElseThrow());
        }
        String target = setting(OUT_PROPERTY);
        if (target != null) {
            Files.write(Path.of(target), out, StandardCharsets.UTF_8);
        }
        System.out.println("Echte Codes: " + total + " gelesen, Status " + statuses + ", längster PW3-Code " + maxLength + " Zeichen");
        assertTrue(total > 0);
        assertEquals(List.of(), unexplained);
    }
}

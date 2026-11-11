package de.lardtom.propwash.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareTextTest {
    private static final List<String> SUBSTITUTES = List.of("fuer", "ueber", "pruef", "aelter", "groess", "moeglich", "koenn", "muess", "waehl",
            "schluess", "ungueltig", "zurueck", "laenge", "naechst", "spaet", "hinzufueg", "oeffn", "fuehr", "aender", "gehoert", "hoechst", "waere",
            "laesst", "beschaedig", "vollstaendig", "unvollstaendig", "zubehoer", "groesse", "strasse", "heiss");

    private static JsonObject lang(String code) throws IOException {
        return JsonParser.parseString(Files.readString(ShareTestData.repo("common/src/lang/pw3-codec/" + code + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    static TreeSet<String> keys() {
        TreeSet<String> out = new TreeSet<>(ShareException.KEYS);
        for (ShareStatus status : ShareStatus.values()) {
            out.add(status.translationKey());
        }
        for (NamespaceReport.Result result : NamespaceReport.Result.values()) {
            out.add(result.translationKey());
        }
        for (MissingAddons.Kind kind : MissingAddons.Kind.values()) {
            out.add(kind.translationKey());
        }
        for (ShareLayer layer : ShareLayer.values()) {
            out.add(layer.translationKey());
        }
        return out;
    }

    @Test
    void everyKeyExistsInBothLanguages() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        assertEquals(keys(), new TreeSet<>(en.keySet()));
        assertEquals(keys(), new TreeSet<>(de.keySet()));
        for (String key : keys()) {
            assertEquals(placeholders(en.get(key).getAsString()), placeholders(de.get(key).getAsString()), key);
            assertTrue(!en.get(key).getAsString().isBlank() && !de.get(key).getAsString().isBlank(), key);
        }
    }

    @Test
    void germanUsesUmlautsAndEszett() throws IOException {
        JsonObject de = lang("de_de");
        List<String> bad = new ArrayList<>();
        for (String key : de.keySet()) {
            String text = de.get(key).getAsString().toLowerCase(Locale.ROOT);
            for (String substitute : SUBSTITUTES) {
                if (text.contains(substitute)) {
                    bad.add(key + ": " + substitute);
                }
            }
        }
        assertEquals(List.of(), bad);
        String all = de.toString();
        assertTrue(all.contains("ü") && all.contains("ä") && all.contains("ö") && all.contains("ß"));
    }

    private static int placeholders(String text) {
        Matcher matcher = Pattern.compile("%(\\d+\\$)?s").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}

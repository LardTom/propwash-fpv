package de.lardtom.propwash.trick;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TrickDocsTest {
    private static final String BEGIN = "<!-- trick-repertoire:begin -->";
    private static final String END = "<!-- trick-repertoire:end -->";
    private static final String DOC = "docs/design/freestyle-tricks.md";

    private static Path repository() {
        for (Path candidate : List.of(Path.of(".."), Path.of("."))) {
            if (Files.isRegularFile(candidate.resolve(DOC))) {
                return candidate;
            }
        }
        fail("Repository nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static JsonObject lang(String language) throws IOException {
        try (Reader reader = Files.newBufferedReader(repository().resolve("common/src/lang/trick/" + language + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    static String table(JsonObject de, JsonObject en) {
        List<String> rows = new ArrayList<>();
        rows.add("| Familie | Trick | Englisch | Schlüssel | Punkte | Erklärung im Tab |");
        rows.add("|---|---|---|---|---|---|");
        for (TrickType type : TrickType.REPERTOIRE) {
            String points = (type.scaled() ? "ab " : "") + type.basePoints();
            rows.add("| " + de.get(type.family().translationKey()).getAsString() + " | " + de.get(type.translationKey()).getAsString() + " | "
                    + en.get(type.translationKey()).getAsString() + " | `" + type.key() + "` | " + points + " | "
                    + de.get(type.descriptionKey()).getAsString() + " |");
        }
        return String.join("\n", rows);
    }

    @Test
    void repertoireTableMatchesTheCode() throws IOException {
        String expected = table(lang("de_de"), lang("en_us"));
        Path out = repository().resolve("common/build/docs/trick-repertoire.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, expected + "\n", StandardCharsets.UTF_8);
        String text = Files.readString(repository().resolve(DOC), StandardCharsets.UTF_8);
        int from = text.indexOf(BEGIN);
        int to = text.indexOf(END);
        assertTrue(from >= 0 && to > from, DOC + ": Markierungen fehlen");
        assertEquals(expected, text.substring(from + BEGIN.length(), to).strip(),
                DOC + " weicht ab; erwarteter Block steht in " + out.toAbsolutePath());
    }

    @Test
    void everyTrickIsInTheRepertoireOnce() {
        assertEquals(TrickType.values().length, TrickType.REPERTOIRE.size());
        assertEquals(TrickType.values().length, TrickType.REPERTOIRE.stream().distinct().count());
    }

    @Test
    void everyTrickHasNamesAndExplanationsInBothLanguages() throws IOException {
        JsonObject de = lang("de_de");
        JsonObject en = lang("en_us");
        assertEquals(de.keySet(), en.keySet());
        for (TrickType type : TrickType.values()) {
            for (String key : List.of(type.translationKey(), type.descriptionKey())) {
                assertTrue(de.has(key) && en.has(key), key);
                assertTrue(de.get(key).getAsString().length() <= (key.endsWith(".desc") ? 200 : 24), key);
            }
        }
    }
}

package de.lardtom.propwash.content.client;

import de.lardtom.propwash.Constants;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SurvivalSelfTestsRecipeListTest {
    private static Path recipes() {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            Path dir = candidate.resolve("common/src/main/resources/data/propwash/recipe");
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        fail("Rezept-Ordner nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static Set<String> recipeFiles() throws IOException {
        Set<String> ids = new TreeSet<>();
        try (Stream<Path> files = Files.list(recipes())) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> ids.add(Constants.MOD_ID + ":" + p.getFileName().toString().replace(".json", "")));
        }
        return ids;
    }

    private static Set<String> listed() {
        Set<String> ids = new TreeSet<>();
        for (Identifier id : SurvivalSelfTests.craftableIds()) {
            ids.add(id.toString());
        }
        return ids;
    }

    @Test
    void everyRecipeFileIsListedInTheSurvivalSelfTest() throws IOException {
        Set<String> missing = recipeFiles();
        missing.removeAll(listed());
        assertTrue(missing.isEmpty(), "Rezepte ohne Eintrag in SurvivalSelfTests.craftableIds(): " + missing);
    }

    @Test
    void everyListedItemHasARecipeFile() throws IOException {
        Set<String> extra = listed();
        extra.removeAll(recipeFiles());
        assertTrue(extra.isEmpty(), "Einträge in SurvivalSelfTests.craftableIds() ohne Rezeptdatei: " + extra);
    }
}

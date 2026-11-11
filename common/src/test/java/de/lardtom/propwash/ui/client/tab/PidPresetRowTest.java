package de.lardtom.propwash.ui.client.tab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.ui.ChromeLayout;
import de.lardtom.propwash.ui.client.theme.Metrics;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PidPresetRowTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static final int SMALLEST_GUI = 320;
    private static final int LONGEST_TITLE = 1000;
    private static final int WIDEST_WINDOW = 720;
    private static final String STANDARD = "propwash.ui.preset.pid.standard";
    private static final String LABEL = "propwash.ui.rates.preset";
    private static final String APPLY = "propwash.ui.rates.apply";
    private static final Map<Integer, Integer> ADVANCES = advances();

    static int narrowestTab(int guiWidth) {
        int sidebar = ChromeLayout.sidebarWidth(guiWidth, LONGEST_TITLE);
        return guiWidth - 6 - (sidebar + 8) - Metrics.CONTENT_INSET;
    }

    private record Texts(int widestGrouped, int standard, int label, int apply) {
        static Texts of(String language) {
            Map<String, String> lang = lang(language);
            int widest = 0;
            for (String key : PidTab.GROUPED) {
                String text = lang.get("propwash.ui.tune." + key);
                assertNotNull(text, language + ": Beschriftung fehlt für " + key);
                widest = Math.max(widest, width(text));
            }
            return new Texts(widest, width(text(lang, language, STANDARD)), width(text(lang, language, LABEL)), width(text(lang, language, APPLY)));
        }

        PresetPanel.Row row(int tab) {
            int aligned = UiTab.labelColumn(this.widestGrouped, PidTab.columnWidth(tab));
            return PresetPanel.row(tab, aligned, this.label, this.apply, this.standard);
        }
    }

    @Test
    void narrowestTabAtSmallestGuiHasTheWidestSidebar() {
        assertEquals(96, ChromeLayout.sidebarWidth(SMALLEST_GUI, LONGEST_TITLE));
        assertEquals(204, narrowestTab(SMALLEST_GUI));
    }

    @Test
    void builtinPresetStaysWholeInEveryTabWidth() {
        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            Texts texts = Texts.of(language);
            for (int tab = narrowestTab(SMALLEST_GUI); tab <= WIDEST_WINDOW - 6 - Metrics.CONTENT_INSET; tab++) {
                PresetPanel.Row row = texts.row(tab);
                String where = language + ", Tab " + tab + " px, Beschriftung " + row.labelWidth() + " px, Auswahl " + row.choiceWidth() + " px";
                if (row.valueRoom() < texts.standard()) {
                    problems.add(where + ": „Standard“ (" + texts.standard() + " px) hat nur " + row.valueRoom() + " px");
                }
                if (texts.label() > row.labelWidth() - 4) {
                    problems.add(where + ": Beschriftung „Preset“ (" + texts.label() + " px) passt nicht");
                }
                if (row.applyWidth() > tab) {
                    problems.add(where + ": Knopf Übernehmen " + row.applyWidth() + " px breiter als der Tab");
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " Befunde, zuerst: " + String.join("\n", problems.subList(0, Math.min(12, problems.size()))));
    }

    @Test
    void labelStaysInTheColumnOfTheGroupsWhereTheNameFits() {
        for (String language : LANGUAGES) {
            Texts texts = Texts.of(language);
            for (int tab : new int[]{322, 400, WIDEST_WINDOW - 6 - Metrics.CONTENT_INSET}) {
                PresetPanel.Row row = texts.row(tab);
                int aligned = Math.max(UiTab.labelColumn(texts.widestGrouped(), PidTab.columnWidth(tab)), Math.min(tab / 2, texts.label() + 8));
                assertEquals(aligned, row.labelWidth(), language + ", Tab " + tab + " px");
            }
        }
    }

    private static String text(Map<String, String> lang, String language, String key) {
        String text = lang.get(key);
        assertNotNull(text, language + ": " + key + " fehlt");
        return text;
    }

    private static Map<String, String> lang(String language) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json("assets/propwash/lang/" + language + ".json").entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    static int width(String text) {
        int sum = 0;
        for (int codePoint : text.codePoints().toArray()) {
            Integer advance = ADVANCES.get(codePoint);
            assertNotNull(advance, "Zeichen U+" + Integer.toHexString(codePoint) + " fehlt in der Bitmap-Schrift: " + text);
            sum += advance;
        }
        return sum;
    }

    private static InputStream resource(String path) {
        InputStream in = PidPresetRowTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, "fehlt auf dem Klassenpfad: " + path);
        return in;
    }

    private static JsonObject json(String path) {
        try (InputStream in = resource(path)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<Integer, Integer> advances() {
        Map<Integer, Integer> out = new HashMap<>();
        for (JsonElement element : json("assets/minecraft/font/include/space.json").getAsJsonArray("providers")) {
            for (Map.Entry<String, JsonElement> advance : element.getAsJsonObject().getAsJsonObject("advances").entrySet()) {
                out.put(advance.getKey().codePointAt(0), advance.getValue().getAsInt());
            }
        }
        for (JsonElement element : json("assets/minecraft/font/include/default.json").getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (!provider.get("type").getAsString().equals("bitmap")) {
                continue;
            }
            BufferedImage image;
            try (InputStream in = resource("assets/minecraft/textures/" + provider.get("file").getAsString().replace("minecraft:", ""))) {
                image = ImageIO.read(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            JsonArray rows = provider.getAsJsonArray("chars");
            int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            int cellWidth = image.getWidth() / columns;
            int cellHeight = image.getHeight() / rows.size();
            float scale = (float) (provider.has("height") ? provider.get("height").getAsInt() : 8) / cellHeight;
            for (int row = 0; row < rows.size(); row++) {
                int[] chars = rows.get(row).getAsString().codePoints().toArray();
                for (int column = 0; column < chars.length; column++) {
                    if (chars[column] != 0 && !out.containsKey(chars[column])) {
                        out.put(chars[column], (int) (0.5F + glyphWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight) * scale) + 1);
                    }
                }
            }
        }
        return out;
    }

    private static int glyphWidth(BufferedImage image, int x0, int y0, int cellWidth, int cellHeight) {
        for (int x = cellWidth - 1; x >= 0; x--) {
            for (int y = 0; y < cellHeight; y++) {
                if ((image.getRGB(x0 + x, y0 + y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }
}

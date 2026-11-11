package de.lardtom.propwash.race.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.race.client.RaceStandardSection.Buttons;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceStandardButtonsTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static final int SMALLEST_GUI = 320;
    private static final int LONGEST_TITLE = 1000;
    private static final int WIDEST_WINDOW = 720;
    private static final int ROW_WITHOUT_ADDON_TABS = 219;
    private static final String ADOPT = RaceStandardSection.KEY + "adopt";
    private static final String RESET = RaceStandardSection.KEY + "reset";
    private static final Map<Integer, Integer> ADVANCES = advances();

    static int narrowestTab(int guiWidth) {
        int sidebar = ChromeLayout.sidebarWidth(guiWidth, LONGEST_TITLE);
        return guiWidth - 6 - (sidebar + 8) - Metrics.CONTENT_INSET;
    }

    private record Labels(String language, int adopt, int reset) {
        static Labels of(String language) {
            Map<String, String> lang = lang(language);
            return new Labels(language, width(text(lang, language, ADOPT)), width(text(lang, language, RESET)));
        }

        Buttons buttons(int width) {
            return RaceStandardSection.buttons(width, this.adopt, this.reset);
        }
    }

    @Test
    void narrowestTabAtSmallestGuiHasTheWidestSidebar() {
        assertEquals(204, narrowestTab(SMALLEST_GUI));
    }

    @Test
    void bothLabelsStayWholeInEveryTabWidth() {
        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            Labels labels = Labels.of(language);
            for (int width = narrowestTab(SMALLEST_GUI); width <= WIDEST_WINDOW - 6 - Metrics.CONTENT_INSET; width++) {
                Buttons buttons = labels.buttons(width);
                String where = language + ", Zeile " + width + " px, " + buttons;
                if (!RaceStandardSection.labelFits(labels.adopt(), buttons.adoptWidth())) {
                    problems.add(where + ": „Aktuelle Regeln übernehmen“ (" + labels.adopt() + " px) gekürzt");
                }
                if (!RaceStandardSection.labelFits(labels.reset(), buttons.resetWidth())) {
                    problems.add(where + ": „Propwash-Standard“ (" + labels.reset() + " px) gekürzt");
                }
                if (buttons.adoptWidth() > width || buttons.resetX() + buttons.resetWidth() > width) {
                    problems.add(where + ": Knopf breiter als die Zeile");
                }
                boolean apart = buttons.stacked() ? buttons.resetY() >= RaceStandardSection.FIELD
                        : buttons.resetX() >= buttons.adoptWidth() + Metrics.GAP;
                if (!apart || buttons.height() != buttons.resetY() + RaceStandardSection.FIELD) {
                    problems.add(where + ": Knöpfe überlappen oder Höhe passt nicht");
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " Befunde, zuerst: " + String.join("\n", problems.subList(0, Math.min(12, problems.size()))));
    }

    @Test
    void germanLabelGetsTheWholeRowInTheNarrowestTab() {
        Labels german = Labels.of("de_de");
        int width = narrowestTab(SMALLEST_GUI);
        Buttons buttons = german.buttons(width);
        assertTrue(buttons.stacked(), "Knöpfe untereinander bei " + width + " px: " + buttons);
        assertEquals(width, buttons.adoptWidth());
        assertEquals(0, buttons.resetX());
        assertEquals(width, buttons.resetWidth());
        assertTrue(buttons.resetY() >= RaceStandardSection.FIELD, buttons.toString());
    }

    @Test
    void buttonsStayBesideEachOtherWhereBothLabelsFit() {
        Buttons english = Labels.of("en_us").buttons(narrowestTab(SMALLEST_GUI));
        assertFalse(english.stacked(), english.toString());
        Buttons german = Labels.of("de_de").buttons(ROW_WITHOUT_ADDON_TABS);
        int half = (ROW_WITHOUT_ADDON_TABS - Metrics.GAP) / 2;
        assertEquals(new Buttons(half, half + Metrics.GAP, 0, ROW_WITHOUT_ADDON_TABS - half - Metrics.GAP, RaceStandardSection.FIELD), german);
        for (String language : LANGUAGES) {
            assertFalse(Labels.of(language).buttons(WIDEST_WINDOW - 6 - Metrics.CONTENT_INSET).stacked(), language);
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
        InputStream in = RaceStandardButtonsTest.class.getClassLoader().getResourceAsStream(path);
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

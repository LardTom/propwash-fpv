package de.lardtom.propwash.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.client.WorkbenchReport;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchStatsFitTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static final String KEY = "propwash.content.workbench.";
    private static final Map<Integer, Integer> ADVANCES = advances();

    private static InputStream resource(String path) {
        InputStream in = WorkbenchStatsFitTest.class.getClassLoader().getResourceAsStream(path);
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
            JsonObject provider = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> advance : provider.getAsJsonObject("advances").entrySet()) {
                out.put(advance.getKey().codePointAt(0), advance.getValue().getAsInt());
            }
        }
        for (JsonElement element : json("assets/minecraft/font/include/default.json").getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (!provider.get("type").getAsString().equals("bitmap")) {
                continue;
            }
            String file = provider.get("file").getAsString().replace("minecraft:", "");
            BufferedImage image;
            try (InputStream in = resource("assets/minecraft/textures/" + file)) {
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
                    if (chars[column] == 0 || out.containsKey(chars[column])) {
                        continue;
                    }
                    out.put(chars[column], (int) (0.5F + glyphWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight) * scale) + 1);
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

    static int width(String text) {
        int sum = 0;
        for (int codePoint : text.codePoints().toArray()) {
            Integer advance = ADVANCES.get(codePoint);
            assertNotNull(advance, "Zeichen U+" + Integer.toHexString(codePoint) + " fehlt in der Bitmap-Schrift: " + text);
            sum += advance;
        }
        return sum;
    }

    private static Map<String, String> lang(String language) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json("assets/propwash/lang/" + language + ".json").entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    static String resolve(Component component, Map<String, String> lang) {
        StringBuilder out = new StringBuilder();
        switch (component.getContents()) {
            case TranslatableContents t -> out.append(format(lang.getOrDefault(t.getKey(), t.getKey()), t.getArgs(), lang));
            case PlainTextContents p -> out.append(p.text());
            default -> out.append(component.getContents());
        }
        for (Component sibling : component.getSiblings()) {
            out.append(resolve(sibling, lang));
        }
        return out.toString();
    }

    private static String format(String pattern, Object[] args, Map<String, String> lang) {
        StringBuilder out = new StringBuilder();
        Matcher m = FORMAT.matcher(pattern);
        int next = 0;
        int end = 0;
        while (m.find()) {
            out.append(pattern, end, m.start());
            end = m.end();
            if ("%".equals(m.group(2))) {
                out.append('%');
                continue;
            }
            int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
            Object arg = args[index];
            out.append(arg instanceof Component c ? resolve(c, lang) : String.valueOf(arg));
        }
        out.append(pattern.substring(end));
        return out.toString();
    }

    private static Component unit(String unit, Component value) {
        return Component.translatable(KEY + "unit." + unit, value);
    }

    private static Map<String, List<Component>> widestValues() {
        Map<String, List<Component>> out = new LinkedHashMap<>();
        Component none = Component.translatable("propwash.content.number.none");
        out.put("dry_weight", List.of(unit("grams", Numbers.of(8888.0, 0)), none));
        out.put("takeoff_weight", List.of(unit("grams", Numbers.of(8888.0, 0)), none));
        out.put("thrust_to_weight", List.of(unit("ratio", Numbers.fixed(88.8, 1)), none));
        out.put("hover_throttle", List.of(unit("percent", Numbers.of(100.0, 0)), none));
        out.put("top_speed", List.of(unit("kmh", Numbers.of(888.0, 0)), none));
        out.put("flight_time", List.of(unit("minutes", Numbers.minutes(88.0 + 58.0 / 60.0)), none));
        return out;
    }

    @Test
    void theBitmapFontMeasuresLikeMinecraft() {
        assertEquals(4, width(" "));
        assertEquals(6, width("A"));
        assertEquals(2, width("i"));
        assertEquals(87, width("Flight time (mixed)"));
    }

    @Test
    void everyStatLabelFitsBesideItsWidestValueInEveryLanguage() {
        List<String> tight = new ArrayList<>();
        for (String language : LANGUAGES) {
            Map<String, String> lang = lang(language);
            for (Map.Entry<String, List<Component>> row : widestValues().entrySet()) {
                String label = resolve(Component.translatable(KEY + "stat." + row.getKey()), lang);
                for (Component value : row.getValue()) {
                    String text = resolve(value, lang);
                    if (width(label) > WorkbenchLayout.statLabelWidth(width(text))) {
                        tight.add(language + ": „" + label + "“ " + width(label) + " px neben „" + text + "“ " + width(text) + " px, Platz "
                                + WorkbenchLayout.statLabelWidth(width(text)) + " px");
                    }
                }
            }
        }
        assertEquals(List.of(), tight);
    }

    @Test
    void everyPresetRowFitsInEveryLanguage() {
        List<String> tight = new ArrayList<>();
        int rows = 0;
        for (String language : LANGUAGES) {
            Map<String, String> lang = lang(language);
            for (DroneBuild build : DroneBuild.PRESETS) {
                WorkbenchReport report = WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(build)), true);
                for (WorkbenchReport.Row row : report.rows()) {
                    String label = resolve(row.label(), lang);
                    String value = resolve(row.value(), lang);
                    rows++;
                    if (width(label) > WorkbenchLayout.statLabelWidth(width(value))) {
                        tight.add(language + " " + build.frame().key() + ": „" + label + "“ neben „" + value + "“");
                    }
                }
            }
        }
        assertTrue(rows >= 60, "Zeilen " + rows);
        assertEquals(List.of(), tight);
    }

    @Test
    void weightRowsShowDryAndAllUpWeight() {
        Map<String, String> en = lang("en_us");
        Map<String, String> de = lang("de_de");
        for (DroneBuild build : DroneBuild.PRESETS) {
            WorkbenchReport report = WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(build)), true);
            List<WorkbenchReport.Row> rows = report.rows();
            assertEquals("Dry weight", resolve(rows.get(0).label(), en));
            assertEquals("All-up weight", resolve(rows.get(1).label(), en));
            assertEquals("Trockengewicht", resolve(rows.get(0).label(), de));
            assertEquals("Abfluggewicht", resolve(rows.get(1).label(), de));
            assertEquals(Math.round(build.totalMassGrams() - build.battery().massGrams()) + " g", resolve(rows.get(0).value(), en));
            assertEquals(Math.round(build.totalMassGrams()) + " g", resolve(rows.get(1).value(), en));
            assertEquals(build.totalMassGrams(), build.dryMassGrams() + build.battery().massGrams(), 1e-9);
        }
        DroneBuild typical7 = DroneBuild.LONG_RANGE.withBattery(BatteryType.B6S_1300);
        assertEquals(426.2, typical7.dryMassGrams(), 1e-9);
        assertEquals(636.2, typical7.totalMassGrams(), 1e-9);
    }
}

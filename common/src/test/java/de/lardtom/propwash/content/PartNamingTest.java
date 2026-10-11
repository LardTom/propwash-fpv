package de.lardtom.propwash.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PartNamingTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static final Set<String> NAMED_BY_ROLE = Set.of("creative_fc");
    private static final Map<String, String[]> MATERIALS = Map.of(
            "polycarbonate", new String[]{"PC", "PC"},
            "glass_fiber_nylon", new String[]{"glass-fiber nylon", "Glasfaser-Nylon"},
            "carbon_fiber_nylon", new String[]{"carbon-fiber nylon", "Carbonfaser-Nylon"},
            "carbon", new String[]{"carbon", "Carbon"});
    private static final Map<String, String> CHEMISTRIES = Map.of("propwash:lipo", "", "propwash:lihv", "LiHV", "propwash:lion", "Li-Ion");
    private static final Map<String, String> STACK_FORMS = Map.of("propwash:whoop_aio", "Whoop", "propwash:s20", "20×20", "propwash:s30", "30×30");
    private static final Map<String, String> FRAME_CLASSES = Map.of("whoop", "Whoop", "cinewhoop", "Cinewhoop", "freestyle", "Freestyle",
            "race", "Race", "long_range", "Long Range", "toothpick", "Toothpick", "x_class", "X-Class");
    private static final Pattern ASCII_INCH = Pattern.compile("\\d\"");
    private static final String DASH = " – ";
    private static final Map<Integer, String[]> BLADE_WORDS = Map.of(2, new String[]{"two-blade", "Zweiblatt"}, 3, new String[]{"tri-blade", "Dreiblatt"},
            4, new String[]{"quad-blade", "Vierblatt"}, 5, new String[]{"five-blade", "Fünfblatt"});
    private static final Map<String, String[]> SHORT_CHEMISTRIES = Map.of("propwash:lipo", new String[]{"LiPo", "LiPo"},
            "propwash:lihv", new String[]{"LiHV", "LiHV"}, "propwash:lion", new String[]{"Li-Ion", "Li-Ion"});
    private static final Map<String, String[]> SUMMARY_CLASSES = Map.of("whoop", new String[]{"whoop", "Whoop"},
            "cinewhoop", new String[]{"cinewhoop", "Cinewhoop"}, "freestyle", new String[]{"freestyle", "Freestyle"},
            "race", new String[]{"race", "Race"}, "long_range", new String[]{"long-range", "Long-Range"},
            "toothpick", new String[]{"toothpick", "Toothpick"}, "x_class", new String[]{"X-Class", "X-Class"});
    private static final Set<String> FRAME_FORMS = Set.of("true X", "stretched X", "squashed X", "deadcat", "H-frame",
            "True-X", "Stretched-X", "Squashed-X", "Deadcat", "H-Frame");
    private static final Pattern FRAME_NAME_EN = Pattern.compile("^(\\d+ mm|\\d+(?:\\.\\d)?″) .+ Frame(?: \\(([^()]+)\\))?$");
    private static final Pattern FRAME_NAME_DE = Pattern.compile("^(\\d+-mm|\\d+(?:,\\d)?-Zoll)-.+-Rahmen(?: \\(([^()]+)\\))?$");
    private static final Pattern VIDEO_NAME_EN = Pattern.compile("^(Analog|Digital)(?: (.+?))? (?:Camera \\+ VTX|Video System)(?: \\(([^()]+)\\))?$");
    private static final Pattern VIDEO_NAME_DE = Pattern.compile("^(?:Analoge (.+)-Kamera mit Sender|Digitales (?:(.+)-)?Videosystem)(?: \\(([^()]+)\\))?$");
    private static final Pattern DEVICE_NAME = Pattern.compile("^([^()]+?)(?: \\(([^()]+)\\))?$");
    private static final Pattern TITLE_WORD = Pattern.compile("^[A-Z][a-z]");

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/main/resources/data/propwash"))) {
                return candidate;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Map<String, JsonObject> parts(String category) throws IOException {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        Path dir = common().resolve("src/main/resources/data/propwash/propwash_parts/" + category);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString();
                String key = name.substring(0, name.length() - 5);
                if (!NAMED_BY_ROLE.contains(key)) {
                    out.put(key, read(file));
                }
            }
        }
        return out;
    }

    private static JsonObject lang(String language) throws IOException {
        return read(common().resolve("src/main/resources/assets/propwash/lang/" + language + ".json"));
    }

    private static String number(double value, int decimals, boolean german) {
        String text = String.format(Locale.ROOT, "%." + decimals + "f", value);
        return german ? text.replace('.', ',') : text;
    }

    private static String size(double value, boolean german) {
        String text = number(value, 1, german);
        return text.endsWith(",0") || text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    static String motorName(JsonObject motor, boolean german) {
        String stator = motor.get("stator").getAsString();
        int kv = motor.get("kv").getAsInt();
        return german ? stator.replace('.', ',') + "-Motor, " + kv + " KV" : stator + " Motor, " + kv + " KV";
    }

    static String propName(JsonObject prop, boolean german) {
        int mm = prop.get("diameter_mm").getAsInt();
        double pitch = prop.get("pitch_in").getAsDouble();
        int blades = prop.get("blades").getAsInt();
        String material = MATERIALS.get(prop.get("material").getAsString())[german ? 1 : 0];
        double inch = Math.round(mm / 25.4 * 10.0) / 10.0;
        if (german) {
            String diameter = mm < 50 ? mm + "-mm" : size(inch, true) + "-Zoll";
            return diameter + "-Propeller (" + blades + " Blätter, " + number(pitch, 1, true) + "″ Steigung, " + material + ")";
        }
        String diameter = mm < 50 ? mm + " mm" : size(inch, false) + "″";
        return diameter + " Propeller (" + blades + "-blade, " + number(pitch, 1, false) + "″ pitch, " + material + ")";
    }

    static String batteryName(JsonObject battery, boolean german) {
        int cells = battery.get("cells").getAsInt();
        int mah = battery.get("capacity_mah").getAsInt();
        String chemistry = CHEMISTRIES.get(battery.has("chemistry") ? battery.get("chemistry").getAsString() : "propwash:lipo");
        String suffix = chemistry.isEmpty() ? "" : " (" + chemistry + ")";
        return german ? cells + "S-Akku " + mah + " mAh" + suffix : cells + "S " + mah + " mAh Battery" + suffix;
    }

    static String stackName(JsonObject stack, boolean german) {
        JsonArray cells = stack.getAsJsonArray("cells");
        int low = cells.get(0).getAsInt();
        int high = cells.get(1).getAsInt();
        String range = low == high ? low + "S" : low + "–" + high + "S";
        long amps = Math.round(stack.get("esc_continuous_a").getAsDouble());
        boolean aio = stack.has("all_in_one") && stack.get("all_in_one").getAsBoolean();
        String form = STACK_FORMS.get(stack.get("mount").getAsString());
        String device = aio ? (german ? "-AIO-Board" : " AIO Board") : (german ? "-FC/ESC-Stack" : " FC/ESC Stack");
        return form + device + " (" + range + ", " + amps + " A)";
    }

    @Test
    void builtInNamesFollowTheNamingScheme() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            JsonObject table = lang(language);
            boolean german = "de_de".equals(language);
            check(table, language, parts("motor"), m -> motorName(m, german), problems);
            check(table, language, parts("prop"), p -> propName(p, german), problems);
            check(table, language, parts("battery"), b -> batteryName(b, german), problems);
            check(table, language, parts("stack"), s -> stackName(s, german), problems);
        }
        assertTrue(problems.isEmpty(), "docs/design/part-naming.md:\n" + String.join("\n", problems));
    }

    @Test
    void frameNamesStartWithSizeAndClass() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            JsonObject table = lang(language);
            boolean german = "de_de".equals(language);
            for (Map.Entry<String, JsonObject> frame : parts("frame").entrySet()) {
                String role = FRAME_CLASSES.get(frame.getValue().get("role").getAsString());
                String name = table.get("part.propwash." + frame.getKey()).getAsString();
                String pattern = german
                        ? "^\\d+(,\\d)?-(mm|Zoll)-" + role.replace(' ', '-') + "-Rahmen( \\(.+\\))?$"
                        : "^(\\d+ mm|\\d+(\\.\\d)?″) " + role + " Frame( \\(.+\\))?$";
                if (!name.matches(pattern)) {
                    problems.add(language + " " + frame.getKey() + ": „" + name + "“");
                }
            }
        }
        assertTrue(problems.isEmpty(), "docs/design/part-naming.md:\n" + String.join("\n", problems));
    }

    @Test
    void raceClassesUseTheFrameShortNames() throws IOException {
        for (String language : LANGUAGES) {
            JsonObject table = lang(language);
            for (String frame : parts("frame").keySet()) {
                String shortName = table.get("propwash.content.frame_short." + frame).getAsString();
                assertEquals(shortName, table.get("propwash.race.class." + frame).getAsString(), language + " " + frame);
                assertTrue(shortName.length() <= 14, language + " " + frame + ": Kurzname länger als 14 Zeichen");
            }
        }
    }

    @Test
    void partTextsUseInchMarksAndChemistryNames() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            JsonObject table = lang(language);
            for (String key : table.keySet()) {
                boolean named = key.startsWith("part.propwash.") || key.startsWith("propwash.content.frame_short.")
                        || key.startsWith("propwash.race.class.") || key.startsWith("propwash.help.");
                if (!named) {
                    continue;
                }
                String value = table.get(key).getAsString();
                if (ASCII_INCH.matcher(value).find()) {
                    problems.add(language + " " + key + ": Zoll als ″ schreiben: „" + value + "“");
                }
                if ("en_us".equals(language) && (value.contains("Li-ion") || value.contains("fibre"))) {
                    problems.add(language + " " + key + ": „Li-Ion“/„fiber“ schreiben: „" + value + "“");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    static String propSummaryHead(JsonObject prop, boolean german) {
        int mm = prop.get("diameter_mm").getAsInt();
        String word = BLADE_WORDS.get(prop.get("blades").getAsInt())[german ? 1 : 0];
        String material = MATERIALS.get(prop.has("material") ? prop.get("material").getAsString() : "polycarbonate")[german ? 1 : 0];
        String inch = size(Math.round(mm / 25.4 * 10.0) / 10.0, german) + "″";
        String diameter = mm < 50 ? (german ? mm + "-mm-" : mm + " mm ") : (german ? inch + "-" : inch + " ");
        return diameter + word + ", " + material;
    }

    static String batterySummaryHead(JsonObject battery, boolean german) {
        String chemistry = SHORT_CHEMISTRIES.get(battery.has("chemistry") ? battery.get("chemistry").getAsString() : "propwash:lipo")[german ? 1 : 0];
        return battery.get("cells").getAsInt() + "S" + (german ? "-" : " ") + chemistry + ", " + battery.get("capacity_mah").getAsInt() + " mAh";
    }

    static String stackSummaryHead(JsonObject stack, boolean german) {
        String name = stackName(stack, german);
        return german ? name.replace("-AIO-Board (", "-AIO (").replace("-FC/ESC-Stack (", "-Stack (")
                : name.replace(" AIO Board (", " AIO (").replace(" FC/ESC Stack (", " stack (");
    }

    static String frameSummaryHead(String name, JsonObject frame, boolean german) {
        Matcher m = (german ? FRAME_NAME_DE : FRAME_NAME_EN).matcher(name);
        String[] role = SUMMARY_CLASSES.get(frame.get("role").getAsString());
        if (!m.matches() || role == null) {
            return null;
        }
        String size = german && m.group(1).endsWith("-Zoll") ? m.group(1).replace("-Zoll", "″") : m.group(1);
        List<String> variants = extras(m.group(2), german).stream().filter(e -> !FRAME_FORMS.contains(e)).toList();
        String suffix = variants.isEmpty() ? "" : " (" + String.join(", ", variants) + ")";
        return german ? size + "-" + role[1] + "-Rahmen" + suffix : size + " " + role[0] + " frame" + suffix;
    }

    static List<String> videoSummaryHeads(String name, boolean german) {
        Matcher m = (german ? VIDEO_NAME_DE : VIDEO_NAME_EN).matcher(name);
        if (!m.matches()) {
            return List.of();
        }
        String model = String.join(", ", extras(m.group(3), german));
        String head;
        if (german) {
            head = m.group(1) != null ? "Analoge " + m.group(1) + "-Kamera" : "Digitales " + (m.group(2) == null ? "" : m.group(2) + "-") + "Video";
        } else {
            String cls = m.group(2) == null ? "" : lowerWords(m.group(2)).replace("long range", "long-range");
            head = "Analog".equals(m.group(1)) ? "Analog " + cls + " camera" : "Digital " + (cls.isEmpty() ? "" : cls + " ") + "video";
        }
        return model.isEmpty() ? List.of(head) : List.of(head + " (" + model + ")", model);
    }

    static String accessorySummaryHead(String name, boolean german) {
        Matcher m = DEVICE_NAME.matcher(name);
        if (!m.matches()) {
            return null;
        }
        String model = String.join(", ", extras(m.group(2), german));
        int space = m.group(1).indexOf(' ');
        String device = german || space < 0 ? m.group(1) : m.group(1).substring(0, space + 1) + lowerWords(m.group(1).substring(space + 1));
        return device + (model.isEmpty() ? "" : " (" + model + ")");
    }

    private static List<String> extras(String text, boolean german) {
        if (text == null) {
            return List.of();
        }
        String hint = german ? "Beispiel" : "example";
        return Arrays.stream(text.split(", ")).filter(e -> !e.isEmpty() && !e.equals(hint)).toList();
    }

    private static String lowerWords(String text) {
        List<String> out = new ArrayList<>();
        for (String word : text.split(" ")) {
            out.add(TITLE_WORD.matcher(word).find() && !word.equals("X-Class") ? word.toLowerCase(Locale.ROOT) : word);
        }
        return String.join(" ", out);
    }

    @Test
    void builtInSummariesStartWithTheShortIdentification() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            JsonObject table = lang(language);
            boolean german = "de_de".equals(language);
            for (String category : List.of("motor", "prop", "battery", "stack", "frame", "video", "accessory")) {
                for (Map.Entry<String, JsonObject> part : parts(category).entrySet()) {
                    String name = table.get("part.propwash." + part.getKey()).getAsString();
                    JsonObject data = part.getValue();
                    List<String> heads = switch (category) {
                        case "motor" -> List.of(german ? motorName(data, true) : motorName(data, false).replace(" Motor, ", " motor, "));
                        case "prop" -> List.of(propSummaryHead(data, german));
                        case "battery" -> List.of(batterySummaryHead(data, german));
                        case "stack" -> List.of(stackSummaryHead(data, german));
                        case "frame" -> optional(frameSummaryHead(name, data, german));
                        case "video" -> videoSummaryHeads(name, german);
                        default -> optional(accessorySummaryHead(name, german));
                    };
                    summary(table, language, part.getKey(), heads, problems);
                }
            }
        }
        assertTrue(problems.isEmpty(), "docs/design/part-naming.md §6:\n" + String.join("\n", problems));
    }

    @Test
    void summaryHeadsFollowTheExamplesOfTheScheme() {
        assertEquals("Whoop AIO (1–2S, 5 A)", stackSummaryHead(JsonParser.parseString(
                "{\"mount\":\"propwash:whoop_aio\",\"esc_continuous_a\":5.0,\"cells\":[1,2],\"all_in_one\":true}").getAsJsonObject(), false));
        assertEquals("30×30-Stack (3–6S, 60 A)", stackSummaryHead(JsonParser.parseString(
                "{\"mount\":\"propwash:s30\",\"esc_continuous_a\":60.0,\"cells\":[3,6]}").getAsJsonObject(), true));
        JsonObject race = JsonParser.parseString("{\"role\":\"race\"}").getAsJsonObject();
        assertEquals("5″ race frame (Slim)", frameSummaryHead("5″ Race Frame (Slim)", race, false));
        assertEquals("5″-Race-Rahmen (Slim)", frameSummaryHead("5-Zoll-Race-Rahmen (Slim)", race, true));
        JsonObject longRange = JsonParser.parseString("{\"role\":\"long_range\"}").getAsJsonObject();
        assertEquals("7″ long-range frame", frameSummaryHead("7″ Long Range Frame (deadcat)", longRange, false));
        assertEquals("7″-Long-Range-Rahmen", frameSummaryHead("7-Zoll-Long-Range-Rahmen (Deadcat)", longRange, true));
        assertEquals("65-mm-Whoop-Rahmen (Lite)", frameSummaryHead("65-mm-Whoop-Rahmen (Lite)",
                JsonParser.parseString("{\"role\":\"whoop\"}").getAsJsonObject(), true));
        assertEquals(List.of("Analog whoop camera (Firefly Nano)", "Firefly Nano"), videoSummaryHeads("Analog Whoop Camera + VTX (Firefly Nano)", false));
        assertEquals(List.of("Digital long-range video (Horizon LR)", "Horizon LR"), videoSummaryHeads("Digital Long Range Video System (Horizon LR)", false));
        assertEquals(List.of("Digitales HD-Video"), videoSummaryHeads("Digitales HD-Videosystem", true));
        assertEquals("Action cam (Light)", accessorySummaryHead("Action Cam (Light)", false));
        assertEquals("LED strip", accessorySummaryHead("LED Strip", false));
        assertEquals("6S LiPo, 2200 mAh", batterySummaryHead(JsonParser.parseString("{\"cells\":6,\"capacity_mah\":2200}").getAsJsonObject(), false));
        assertEquals("5,1″-Dreiblatt, PC", propSummaryHead(JsonParser.parseString("{\"diameter_mm\":130,\"blades\":3}").getAsJsonObject(), true));
        assertEquals("31 mm quad-blade, carbon", propSummaryHead(JsonParser.parseString(
                "{\"diameter_mm\":31,\"blades\":4,\"material\":\"carbon\"}").getAsJsonObject(), false));
    }

    private static List<String> optional(String head) {
        return head == null ? List.of() : List.of(head);
    }

    private static void summary(JsonObject table, String language, String path, List<String> heads, List<String> problems) {
        String key = "propwash.help." + path + ".summary";
        String summary = table.has(key) ? table.get(key).getAsString() : null;
        if (summary == null || heads.isEmpty()) {
            problems.add(language + " " + path + ": Kurzbeschreibung fehlt oder Name ohne Muster");
            return;
        }
        String head = heads.stream().filter(h -> summary.startsWith(h + DASH)).findFirst().orElse(null);
        if (head == null) {
            problems.add(language + " " + path + ": „" + summary + "“ beginnt nicht mit „" + String.join(" – “ oder „", heads) + " – “");
            return;
        }
        String use = summary.substring(head.length() + DASH.length());
        if (use.isBlank() || use.contains(DASH) || use.endsWith(".")) {
            problems.add(language + " " + path + ": „" + summary + "“: nach der Kurzkennung genau ein Einsatz ohne Punkt");
        }
        if ("en_us".equals(language) && TITLE_WORD.matcher(use).find()) {
            problems.add(language + " " + path + ": „" + summary + "“: Einsatz klein beginnen");
        }
        if (summary.contains("Schutzring")) {
            problems.add(language + " " + path + ": „" + summary + "“: „Ducts“ statt „Schutzringe“");
        }
    }

    private static void check(JsonObject table, String language, Map<String, JsonObject> parts,
                              Function<JsonObject, String> expected, List<String> problems) {
        for (Map.Entry<String, JsonObject> part : parts.entrySet()) {
            String key = "part.propwash." + part.getKey();
            String actual = table.has(key) ? table.get(key).getAsString() : null;
            String wanted = expected.apply(part.getValue());
            if (!wanted.equals(actual)) {
                problems.add(language + " " + part.getKey() + ": „" + actual + "“ statt „" + wanted + "“");
            }
        }
    }
}

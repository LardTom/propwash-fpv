package de.lardtom.propwash.osd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import de.lardtom.propwash.api.client.osd.OsdWarning;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;
import de.lardtom.propwash.osd.addon.client.AddonOsdContext;
import de.lardtom.propwash.osd.addon.client.AddonOsdLayer;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonOsdTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final AddonOsdLayer.Contexts CONTEXTS = (data, preview) -> new AddonOsdContext(Optional.empty(), AddonOsdContext.telemetry(data),
            true, false, preview, 0L, 0.0F);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("addon_osd_test", path);
    }

    private static OsdElementSpec spec(String path, int col, int row, boolean enabled, OsdContentStub content) {
        return OsdElementSpec.builder(id(path)).position(col, row).enabledByDefault(enabled).maxChars(8).content(content::text).build();
    }

    @FunctionalInterface
    private interface OsdContentStub {
        OsdText text(OsdContext context);
    }

    private static AddonOsdLayer layer(List<OsdElementSpec> specs, List<Api17Registries.Registered<OsdWarning>> warnings) {
        return new AddonOsdLayer(() -> specs, () -> warnings, CONTEXTS);
    }

    @Test
    void addonKeysStayInTheConfigWithoutTheAddon() {
        OsdConfig config = new OsdConfig();
        OsdConfig.Slot ammo = new OsdConfig.Slot(true, 7, 4);
        ammo.color = "#ff8800";
        ammo.scale = 1.23F;
        config.elements.put("mobdefense:ammo", ammo);
        config.elements.put("noclipmod:ghost/state", new OsdConfig.Slot(false, 2, 2));
        config.elements.put("unknown_element", new OsdConfig.Slot(true, 1, 1));
        config.elements.put("Bad:Key", new OsdConfig.Slot(true, 1, 1));
        config.elements.put("propwash:fake", new OsdConfig.Slot(true, 1, 1));
        config.elements.put("x".repeat(40) + ":" + "y".repeat(40), new OsdConfig.Slot(true, 1, 1));
        OsdConfig.sanitize(config);
        assertEquals(OsdConfig.VERSION, config.version);
        assertEquals(4, OsdConfig.VERSION);
        assertEquals(OsdElement.values().length + 2, config.elements.size());
        OsdConfig.Slot kept = config.elements.get("mobdefense:ammo");
        assertNotNull(kept);
        assertTrue(kept.enabled);
        assertEquals(7, kept.col);
        assertEquals(4, kept.row);
        assertEquals("#FF8800", kept.color);
        assertEquals(1.2F, kept.scale);
        assertTrue(config.elements.containsKey("noclipmod:ghost/state"));
        assertFalse(config.elements.containsKey("unknown_element"));
        assertFalse(config.elements.containsKey("Bad:Key"));
        assertFalse(config.elements.containsKey("propwash:fake"));

        OsdConfig loaded = GSON.fromJson(GSON.toJson(config), OsdConfig.class);
        OsdConfig.sanitize(loaded);
        assertEquals(7, loaded.elements.get("mobdefense:ammo").col);
        assertFalse(loaded.elements.get("noclipmod:ghost/state").enabled);
        OsdConfig copy = loaded.copy();
        assertEquals(4, copy.elements.get("mobdefense:ammo").row);
        assertTrue(copy.elements.keySet().stream().toList().indexOf("mobdefense:ammo") >= OsdElement.values().length);

        OsdConfig legacy = new OsdConfig();
        legacy.version = 1;
        legacy.elements.put("mobdefense:ammo", new OsdConfig.Slot(true, 3, 3));
        OsdConfig.sanitize(legacy);
        assertEquals(3, legacy.elements.get("mobdefense:ammo").col);
    }

    @Test
    void addonKeysAreRecognised() {
        assertTrue(AddonOsdKeys.isAddonKey("mobdefense:ammo"));
        assertTrue(AddonOsdKeys.isAddonKey("cam_drones:gimbal.tilt"));
        assertFalse(AddonOsdKeys.isAddonKey("speed"));
        assertFalse(AddonOsdKeys.isAddonKey("propwash:ammo"));
        assertFalse(AddonOsdKeys.isAddonKey("Mob:ammo"));
        assertFalse(AddonOsdKeys.isAddonKey(":ammo"));
        assertFalse(AddonOsdKeys.isAddonKey(null));
        for (OsdElement element : OsdElement.values()) {
            assertFalse(AddonOsdKeys.isAddonKey(element.key()), element.key());
        }
    }

    @Test
    void addonElementStartsAtItsDefaultPlace() {
        OsdElementSpec spec = spec("place", 4, 9, true, context -> OsdText.of("X"));
        OsdConfig config = OsdConfig.defaults();
        assertFalse(AddonOsdKeys.customized(config, spec));
        OsdConfig.Slot slot = AddonOsdKeys.slot(config, spec);
        assertTrue(slot.enabled);
        assertEquals(4, slot.col);
        assertEquals(9, slot.row);
        assertSame(slot, AddonOsdKeys.slot(config, spec));
        assertFalse(AddonOsdKeys.customized(config, spec));
        AddonOsdKeys.move(config, spec, 12, 3);
        slot.color = "#00FF00";
        assertTrue(AddonOsdKeys.customized(config, spec));
        OsdConfig copy = config.copy();
        assertEquals(12, AddonOsdKeys.slot(copy, spec).col);
        AddonOsdKeys.reset(config, spec);
        assertEquals(4, slot.col);
        assertEquals(9, slot.row);
        assertFalse(slot.hasOverrides());
        assertFalse(AddonOsdKeys.customized(config, spec));
        OsdElementSpec hidden = spec("hidden", 0, 0, false, context -> OsdText.of("X"));
        assertFalse(AddonOsdKeys.slot(config, hidden).enabled);
    }

    @Test
    void positionsAreClampedIntoTheGrid() {
        OsdElementSpec spec = spec("clamp", 29, 15, true, context -> OsdText.of("X"));
        OsdConfig config = new OsdConfig();
        AddonOsdKeys.move(config, spec, 40, -3);
        OsdConfig.Slot slot = AddonOsdKeys.slot(config, spec);
        assertEquals(OsdGrid.COLS - 1, slot.col);
        assertEquals(0, slot.row);
        AddonOsdKeys.move(config, spec, -5, 99);
        assertEquals(0, slot.col);
        assertEquals(OsdGrid.ROWS - 1, slot.row);
        config.elements.put("mobdefense:far", new OsdConfig.Slot(true, 99, -4));
        OsdConfig.sanitize(config);
        assertEquals(OsdGrid.COLS - 1, config.elements.get("mobdefense:far").col);
        assertEquals(0, config.elements.get("mobdefense:far").row);
        assertEquals(OsdElement.Align.RIGHT, AddonOsdKeys.align(OsdAlign.RIGHT));
        assertEquals(OsdElement.Align.CENTER, AddonOsdKeys.align(OsdAlign.CENTER));
        assertEquals(OsdElement.Align.LEFT, AddonOsdKeys.align(OsdAlign.LEFT));
    }

    @Test
    void onlyPrintableAsciiIsDrawnAndLongTextIsCut() {
        assertEquals("AMMO 12", AddonOsdKeys.sanitize("AMMO 12", 30));
        assertEquals("?BER", AddonOsdKeys.sanitize("ÜBER", 30));
        assertEquals("A?B", AddonOsdKeys.sanitize("A😀B", 30));
        assertEquals("?X?", AddonOsdKeys.sanitize("\tX\u007F", 30));
        assertEquals("~ !", AddonOsdKeys.sanitize("~ !", 30));
        assertEquals("ABCD", AddonOsdKeys.sanitize("ABCDEFGH", 4));
        assertEquals("A", AddonOsdKeys.sanitize("ABC", 0));
        assertEquals(OsdElementSpec.MAX_CHARS, AddonOsdKeys.sanitize("X".repeat(50), 99).length());
        assertEquals("", AddonOsdKeys.sanitize(null, 8));
        assertEquals("", AddonOsdKeys.sanitize("", 8));
    }

    @Test
    void layerShowsCleanTextAndSwitchesFailingElementsOff() {
        AtomicInteger calls = new AtomicInteger();
        OsdElementSpec ok = spec("ok", 1, 1, true, context -> OsdText.of("Ä-1234567890", OsdTone.CAUTION));
        OsdElementSpec off = spec("off", 1, 2, false, context -> OsdText.of("NEVER"));
        OsdElementSpec empty = spec("empty", 1, 3, true, context -> null);
        OsdElementSpec broken = spec("broken", 1, 4, true, context -> {
            calls.incrementAndGet();
            throw new IllegalStateException("kaputt");
        });
        AddonOsdLayer layer = layer(List.of(ok, off, empty, broken), List.of());
        OsdConfig config = OsdConfig.defaults();
        OsdData data = OsdData.sample();
        layer.collect(data, config, false);
        assertEquals(1, layer.shown().size());
        AddonOsdLayer.Shown shown = layer.shown().getFirst();
        assertEquals(ok, shown.spec());
        assertEquals("?-123456", shown.text());
        assertEquals(OsdTone.CAUTION, shown.tone());
        assertEquals(1, shown.slot().col);
        assertTrue(AddonOsdLayer.failed(broken.id()));
        assertFalse(AddonOsdLayer.failed(ok.id()));
        assertEquals(1, calls.get());
        layer.collect(data, config, false);
        assertEquals(1, calls.get());
        assertFalse(layer.drawn(ok.id()));
        shown.record(10.0F, 20.0F, 30.0F, 28.0F);
        assertTrue(layer.drawn(ok.id()));
        assertEquals(ok.id(), layer.at(15.0, 25.0));
        assertNull(layer.at(50.0, 25.0));
        assertEquals("?-123456", layer.text(ok.id()));
        layer.collect(data, config, false);
        assertFalse(layer.drawn(ok.id()));
        AddonOsdKeys.slot(config, ok).enabled = false;
        layer.collect(data, config, false);
        assertTrue(layer.shown().isEmpty());
    }

    @Test
    void editorShowsThePreview() {
        OsdElementSpec withPreview = OsdElementSpec.builder(id("preview")).position(2, 2).enabledByDefault(true).maxChars(8).preview("AMMO 12")
                .content(context -> context.preview() ? OsdText.of("WRONG") : OsdText.of("LIVE")).build();
        OsdElementSpec fromContent = OsdElementSpec.builder(id("content")).position(2, 3).enabledByDefault(true)
                .content(context -> context.preview() ? OsdText.of("PREVIEW") : null).build();
        OsdElementSpec named = OsdElementSpec.builder(id("named")).position(2, 4).enabledByDefault(true).content(context -> null).build();
        AddonOsdLayer layer = layer(List.of(withPreview, fromContent, named), List.of());
        layer.collect(OsdData.sample(), OsdConfig.defaults(), true);
        List<String> texts = new ArrayList<>();
        for (AddonOsdLayer.Shown shown : layer.shown()) {
            texts.add(shown.text());
        }
        assertEquals(List.of("AMMO 12", "PREVIEW", named.nameKey().toUpperCase(java.util.Locale.ROOT)), texts);
        layer.collect(OsdData.sample(), OsdConfig.defaults(), false);
        assertEquals(1, layer.shown().size());
        assertEquals("LIVE", layer.shown().getFirst().text());
    }

    @Test
    void lineOfSightCollectsOnlyElementsWithTheContextOfTheFlight() {
        AtomicInteger warned = new AtomicInteger();
        List<OsdContext> seen = new ArrayList<>();
        OsdElementSpec ammo = spec("sight_ammo", 1, 10, true, context -> {
            seen.add(context);
            return OsdText.of("AMMO 12");
        });
        List<Api17Registries.Registered<OsdWarning>> warnings = List.of(new Api17Registries.Registered<>(id("sight_ghost"), context -> {
            warned.incrementAndGet();
            return OsdText.of("GHOST", OsdTone.CAUTION);
        }));
        AddonOsdLayer.Contexts sight = (data, preview) -> new AddonOsdContext(Optional.empty(), AddonOsdContext.telemetry(data), false, !preview,
                preview, 7L, 0.0F);
        AddonOsdLayer layer = new AddonOsdLayer(() -> List.of(ammo), () -> warnings, sight);
        assertNull(layer.context());
        layer.collectElements(OsdData.sample(), OsdConfig.defaults());
        assertSame(layer, AddonOsdLayer.live());
        assertEquals(1, layer.shown().size());
        assertEquals("AMMO 12", layer.shown().getFirst().text());
        assertEquals(0, layer.warningCount());
        assertEquals(0, warned.get());
        OsdContext context = layer.context();
        assertNotNull(context);
        assertSame(seen.getFirst(), context);
        assertTrue(context.lineOfSight());
        assertFalse(context.fpv());
        assertFalse(context.preview());
        layer.collect(OsdData.sample(), OsdConfig.defaults(), false);
        assertEquals(1, layer.warningCount());
        assertEquals(1, warned.get());
        OsdContext live = layer.context();
        layer.collect(OsdData.sample(), OsdConfig.defaults(), true);
        assertSame(live, layer.context());
        assertEquals(1, warned.get());
    }

    @Test
    void addonWarningsFollowPropwashWarningsAndTakeTurns() {
        double cycle = OsdWarnings.CYCLE_SECONDS;
        List<OsdWarnings.Warning> none = List.of();
        assertEquals(-1, AddonOsdKeys.visibleWarning(none, 0, 1.0));
        assertEquals(0, AddonOsdKeys.visibleWarning(none, 2, 0.0));
        assertEquals(1, AddonOsdKeys.visibleWarning(none, 2, cycle * 1.01));
        assertEquals(0, AddonOsdKeys.visibleWarning(none, 2, cycle * 2.01));
        List<OsdWarnings.Warning> low = List.of(OsdWarnings.Warning.BATTERY_LOW);
        assertEquals(0, AddonOsdKeys.visibleWarning(low, 2, 0.1));
        assertEquals(1, AddonOsdKeys.visibleWarning(low, 2, cycle * 1.01));
        assertEquals(2, AddonOsdKeys.visibleWarning(low, 2, cycle * 2.01));
        assertEquals(0, AddonOsdKeys.visibleWarning(low, 2, cycle * 3.01));
        List<OsdWarnings.Warning> critical = List.of(OsdWarnings.Warning.BATTERY_LOW, OsdWarnings.Warning.FAILSAFE);
        for (int step = 0; step < 6; step++) {
            assertEquals(1, AddonOsdKeys.visibleWarning(critical, 3, cycle * (step + 0.5)));
        }
        List<List<OsdWarnings.Warning>> lists = List.of(none, low, critical,
                List.of(OsdWarnings.Warning.WEAK_SIGNAL, OsdWarnings.Warning.MOTOR_HOT, OsdWarnings.Warning.ARM_SWITCH));
        for (List<OsdWarnings.Warning> list : lists) {
            for (double t : new double[]{Double.NaN, -1.0, 0.0, 1.0, 2.5, 7.9, 100.0}) {
                assertEquals(OsdWarnings.visibleIndex(list, t), AddonOsdKeys.visibleWarning(list, 0, t), list + " bei " + t);
            }
        }
    }

    @Test
    void layerCollectsCleanWarningsAndSkipsBrokenOnes() {
        AtomicInteger broken = new AtomicInteger();
        List<Api17Registries.Registered<OsdWarning>> warnings = List.of(
                new Api17Registries.Registered<>(id("ghost"), context -> OsdText.of("GHOST", OsdTone.CAUTION)),
                new Api17Registries.Registered<>(id("quiet"), context -> null),
                new Api17Registries.Registered<>(id("loud"), context -> OsdText.of("ÖL " + "X".repeat(40), OsdTone.WARNING)),
                new Api17Registries.Registered<>(id("broken_warning"), context -> {
                    broken.incrementAndGet();
                    throw new IllegalStateException("kaputt");
                }));
        AddonOsdLayer layer = layer(List.of(), warnings);
        layer.collect(OsdData.sample(), OsdConfig.defaults(), false);
        assertEquals(2, layer.warningCount());
        assertEquals("GHOST", layer.showWarning(0));
        assertEquals(OsdTone.CAUTION, layer.warningTone(0));
        assertEquals("GHOST", layer.shownWarning().text());
        assertEquals("?L " + "X".repeat(OsdElementSpec.MAX_CHARS - 3), layer.showWarning(1));
        assertEquals(OsdTone.WARNING, layer.warningTone(1));
        assertTrue(AddonOsdLayer.failed(id("broken_warning")));
        layer.collect(OsdData.sample(), OsdConfig.defaults(), false);
        assertEquals(1, broken.get());
        assertNull(layer.shownWarning());
        layer.collect(OsdData.sample(), OsdConfig.defaults(), true);
        assertEquals(0, layer.warningCount());
    }

    @Test
    void layoutExportAndImportCarryAddonKeys() {
        OsdConfig sender = OsdConfig.defaults();
        OsdConfig.Slot ammo = new OsdConfig.Slot(true, 7, 4);
        ammo.color = "#ff8800";
        ammo.scale = 1.2F;
        sender.elements.put("mobdefense:ammo", ammo);
        sender.elements.put("cam_drones:gimbal.tilt", new OsdConfig.Slot(false, 2, 2));
        OsdConfig.sanitize(sender);
        String json = OsdLayoutCodec.encode(sender);
        JsonObject elements = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("elements");
        assertEquals(OsdElement.values().length + 2, elements.size());
        List<String> keys = new ArrayList<>(elements.keySet());
        assertEquals(List.of("mobdefense:ammo", "cam_drones:gimbal.tilt"), keys.subList(OsdElement.values().length, keys.size()));
        JsonObject entry = elements.getAsJsonObject("mobdefense:ammo");
        assertTrue(entry.get("on").getAsBoolean());
        assertEquals(7, entry.get("col").getAsInt());
        assertEquals(4, entry.get("row").getAsInt());
        assertEquals("#ff8800", entry.get("color").getAsString());
        assertEquals(1.2F, entry.get("scale").getAsFloat());
        assertFalse(elements.getAsJsonObject("cam_drones:gimbal.tilt").has("color"));

        OsdConfig receiver = OsdConfig.defaults();
        receiver.elements.put("mobdefense:ammo", new OsdConfig.Slot(false, 20, 3));
        receiver.elements.put("noclipmod:ghost", new OsdConfig.Slot(true, 9, 9));
        OsdConfig.sanitize(receiver);
        OsdConfig applied = OsdLayoutCodec.decode(json, receiver);
        OsdConfig.Slot got = applied.elements.get("mobdefense:ammo");
        assertTrue(got.enabled);
        assertEquals(7, got.col);
        assertEquals(4, got.row);
        assertEquals("#FF8800", got.color);
        assertEquals(1.2F, got.scale);
        assertFalse(applied.elements.get("cam_drones:gimbal.tilt").enabled);
        assertEquals(2, applied.elements.get("cam_drones:gimbal.tilt").col);
        assertEquals(9, applied.elements.get("noclipmod:ghost").col);
        assertTrue(applied.elements.get("noclipmod:ghost").enabled);
        assertFalse(receiver.elements.get("mobdefense:ammo").enabled);
        assertEquals(json, OsdLayoutCodec.encode(OsdLayoutCodec.decode(json, OsdConfig.defaults())));

        OsdConfig untouched = OsdLayoutCodec.decode(OsdLayoutCodec.encode(OsdConfig.defaults()), applied);
        assertEquals(7, untouched.elements.get("mobdefense:ammo").col);
        assertEquals("#FF8800", untouched.elements.get("mobdefense:ammo").color);
        assertEquals(9, untouched.elements.get("noclipmod:ghost").row);

        String hostile = "{\"v\":1,\"elements\":{\"propwash:fake\":{\"on\":true},\"Bad:Key\":{\"on\":true},\"mobdefense:ammo\":5,"
                + "\"" + "a".repeat(40) + ":" + "b".repeat(40) + "\":{\"on\":true},"
                + "\"x:y\":{\"on\":true,\"col\":99,\"row\":-3,\"color\":\"lila\",\"scale\":9}}}";
        OsdConfig cleaned = OsdLayoutCodec.decode(hostile, OsdConfig.defaults());
        assertFalse(cleaned.elements.containsKey("propwash:fake"));
        assertFalse(cleaned.elements.containsKey("Bad:Key"));
        assertFalse(cleaned.elements.containsKey("mobdefense:ammo"));
        assertEquals(OsdElement.values().length + 1, cleaned.elements.size());
        OsdConfig.Slot xy = cleaned.elements.get("x:y");
        assertTrue(xy.enabled);
        assertEquals(OsdGrid.clampCol(99), xy.col);
        assertEquals(OsdGrid.clampRow(-3), xy.row);
        assertEquals("", xy.color);
        assertEquals(OsdConfig.MAX_SCALE, xy.scale);
    }

    @Test
    void layoutExportWithManyAddonKeysStaysWithinTheLimit() {
        OsdConfig config = OsdConfig.defaults();
        for (OsdElement element : OsdElement.values()) {
            config.slot(element).enabled = true;
            config.slot(element).color = "#123456";
            config.slot(element).scale = 1.9F;
        }
        for (int i = 0; i < 200; i++) {
            OsdConfig.Slot slot = new OsdConfig.Slot(i % 2 == 0, 29, 15);
            slot.color = "#123456";
            slot.scale = 1.9F;
            config.elements.put("addon_" + (100 + i) + ":" + "x".repeat(50), slot);
        }
        OsdConfig.sanitize(config);
        String json = OsdLayoutCodec.encode(config);
        assertTrue(json.length() <= OsdLayoutCodec.MAX_LENGTH, json.length() + " Zeichen");
        assertEquals(json, new OsdLayoutSpec(OsdLayoutCodec.VERSION, json).json());
        assertEquals(json, OsdLayoutCodec.encode(config));
        OsdConfig decoded = OsdLayoutCodec.decode(json, OsdConfig.defaults());
        List<OsdConfig.Slot> carried = decoded.elements.entrySet().stream().filter(e -> AddonOsdKeys.isAddonKey(e.getKey())).map(Map.Entry::getValue).toList();
        assertTrue(carried.size() > 10, carried.size() + " Einträge");
        assertTrue(carried.size() < 200, carried.size() + " Einträge");
        assertTrue(carried.stream().allMatch(slot -> slot.enabled), "ausgeschaltete Einträge vor eingeschalteten mitgenommen");
    }

    private static Path doc(String name) {
        for (Path candidate : List.of(Path.of(".."), Path.of("."))) {
            Path file = candidate.resolve(name);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        throw new AssertionError(name + " nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
    }

    private static List<String> paragraphs(List<String> lines) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String text = line.strip();
            boolean starts = text.isEmpty() || text.startsWith("#") || text.startsWith("|") || text.startsWith("```")
                    || text.startsWith("- ") || text.startsWith("* ") || text.matches("\\d+\\. .*");
            if (starts && !current.isEmpty()) {
                out.add(current.toString());
                current.setLength(0);
            }
            if (!text.isEmpty()) {
                current.append(current.isEmpty() ? "" : " ").append(text);
            }
        }
        if (!current.isEmpty()) {
            out.add(current.toString());
        }
        return out;
    }

    @Test
    void docsSayTheLayoutCarriesAddonElements() throws IOException {
        List<String> docs = List.of("docs/api/changes.md", "docs/api/addon-guide.md", "docs/addon-api-1.7.md", "docs/addon-osd.md");
        List<String> notCarried = List.of("nicht mit", "nicht übernommen", "nicht exportiert", "not carr", "not take", "not includ", "not export");
        List<String> wrong = new ArrayList<>();
        for (String name : docs) {
            int found = 0;
            for (String paragraph : paragraphs(Files.readAllLines(doc(name), StandardCharsets.UTF_8))) {
                String lower = paragraph.toLowerCase(Locale.ROOT);
                boolean layout = paragraph.contains("OsdLayoutSpec") || paragraph.contains("PropwashClientApi.osd()");
                if (layout && (lower.contains("add-on-element") || lower.contains("add-on element"))) {
                    found++;
                    if (notCarried.stream().anyMatch(lower::contains)) {
                        wrong.add(name + ": " + paragraph);
                    }
                }
            }
            if (found == 0) {
                wrong.add(name + ": kein Absatz, der OsdLayoutSpec bzw. PropwashClientApi.osd() mit Add-on-Elementen nennt");
            }
        }
        assertEquals(List.of(), wrong, "Export und Import über PropwashClientApi.osd() und Sharecodes nehmen Add-on-Elemente mit (OsdLayoutCodec, layoutExportAndImportCarryAddonKeys)");
    }

    @Test
    void toneColorsFollowThePlayerSettings() {
        assertEquals(1, AddonOsdLayer.color(OsdTone.NORMAL, 1, 2, 3, 4));
        assertEquals(2, AddonOsdLayer.color(OsdTone.ACCENT, 1, 2, 3, 4));
        assertEquals(3, AddonOsdLayer.color(OsdTone.CAUTION, 1, 2, 3, 4));
        assertEquals(4, AddonOsdLayer.color(OsdTone.WARNING, 1, 2, 3, 4));
    }
}

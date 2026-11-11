package de.lardtom.propwash.race;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.sim.HoldMode;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class AssistScaleTest {
    private static final long MS = 1_000_000L;
    private static final int SCALE = LapAssist.SCALE.bit();

    @Test
    void normalSizeIsNoFlightAid() {
        assertEquals(AssistReasons.NONE, AssistCheck.drone(false, List.of(), 1.0F));
        assertEquals(AssistReasons.NONE, AssistCheck.drone(false, List.of(), 1.00001F));
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(AssistCheck.Setup.clean()));
        assertEquals(AssistReasons.NONE, AssistCheck.evaluate(AssistCheck.Setup.clean().withScale(1.0F)));
    }

    @Test
    void anyOtherSizeCountsAsScaling() {
        for (float scale : new float[]{0.0625F, 0.25F, 0.5F, 0.99F, 1.01F, 3.0F, 16.0F}) {
            AssistReasons reasons = AssistCheck.drone(false, List.of(), scale);
            assertEquals(SCALE, reasons.mask(), "Skalierung " + scale);
            assertEquals(List.of(AssistDetail.scale(scale)), reasons.details());
        }
        AssistDetail half = AssistCheck.drone(false, List.of(), 0.5F).details().getFirst();
        assertSame(LapAssist.SCALE, half.reason());
        assertEquals(AssistDetail.SCALE_PERCENT, half.subject());
        assertEquals(50, half.value());
        assertEquals(AssistDetail.FULL_SIZE_PERCENT, half.reference());
    }

    @Test
    void scalingComesWithTheOtherDroneReasons() {
        AssistReasons all = AssistCheck.drone(true, List.of("prop_too_large"), 3.0F);
        assertEquals(LapAssist.CREATIVE_DRONE.bit() | LapAssist.PARTS_MISMATCH.bit() | SCALE, all.mask());
        assertEquals(List.of(LapAssist.CREATIVE_DRONE, LapAssist.PARTS_MISMATCH, LapAssist.SCALE), all.list());
        assertEquals(AssistCheck.drone(true, List.of("prop_too_large")).mask() | SCALE, all.mask());
    }

    @Test
    void setupKeepsTheScaleThroughEveryChange() {
        AssistCheck.Setup setup = AssistCheck.Setup.clean().withScale(0.5F);
        assertEquals(0.5F, setup.scale());
        AssistCheck.Setup changed = setup.withHold(HoldMode.ALTHOLD).withRescue(null).withDrone(false, List.of())
                .withGoggles(false, false, 0, 0).withPhysics(setup.physics(), false);
        assertEquals(0.5F, changed.scale());
        assertEquals(LapAssist.HOLD_MODE.bit() | SCALE, AssistCheck.evaluate(changed).mask());
        assertEquals(1.0F, AssistCheck.Setup.clean().scale());
        assertEquals(16.0F, AssistCheck.Setup.clean().withScale(99.0F).scale());
        assertEquals(1.0F, AssistCheck.Setup.clean().withScale(Float.NaN).scale());
    }

    @Test
    void scalingIsKnownToTheServerAndListedUnderTheDrone() {
        assertTrue(LapAssist.SCALE.serverSide());
        assertEquals(LapAssist.NONE, LapAssist.CLIENT_REASONS & SCALE);
        assertEquals(LapAssist.ALL, LapAssist.SERVER_REASONS | LapAssist.CLIENT_REASONS);
        assertEquals(LapAssist.ALL, AssistChecklist.covered());
        assertSame(AssistChecklist.Category.DRONE, AssistChecklist.Category.of(LapAssist.SCALE));
        assertEquals(LapAssist.SCALE, LapAssist.byKey("scale"));
        assertEquals("propwash.race.assist.scale", LapAssist.SCALE.translationKey());
        List<AssistChecklist.Item> items = AssistChecklist.items(AssistCheck.drone(false, List.of(), 3.0F), true, true);
        for (AssistChecklist.Item item : items) {
            AssistChecklist.Status expected = item.category() == AssistChecklist.Category.DRONE ? AssistChecklist.Status.FLAGGED
                    : AssistChecklist.Status.OK;
            assertEquals(expected, item.status(), item.category().name());
        }
    }

    @Test
    void oldMasksKeepTheirBits() {
        assertEquals(1 << 7, LapAssist.ZONE_PHYSICS.bit());
        assertEquals(1 << 8, SCALE);
        assertEquals(SCALE, RacePayloads.RaceReportC2S.assistFlags(SCALE) >>> RacePayloads.RaceReportC2S.ASSIST_SHIFT);
    }

    @Test
    void lapWithScaledDroneIsAssistedEvenIfTheClientSaysNothing() {
        AssistTimeline timeline = new AssistTimeline();
        AssistReasons half = AssistCheck.drone(false, List.of(), 0.5F);
        for (long t = 0; t <= 10_000 * MS; t += 50 * MS) {
            timeline.observe(t, half);
        }
        AssistReasons lap = AssistCheck.lapVerdict(timeline, 1_000 * MS, 9_000 * MS, AssistReasons.NONE, AssistReasons.NONE);
        assertEquals(SCALE, lap.mask());
        assertEquals(50, lap.details(LapAssist.SCALE).getFirst().value());
        AssistReasons claimed = AssistCheck.lapVerdict(new AssistTimeline(), 0, 1_000 * MS, AssistReasons.NONE, half);
        assertEquals(AssistReasons.NONE, claimed, "der Client kann keine Skalierung melden, nur der Server");
    }

    @Test
    void assistTextNamesTheSize() {
        AssistReasons reasons = AssistCheck.drone(false, List.of(), 3.0F);
        MutableComponent past = AssistWords.reason(LapAssist.SCALE, reasons.details(LapAssist.SCALE));
        assertKey(past, "propwash.race.assist.scale.percent", 300);
        MutableComponent now = AssistWords.reason(LapAssist.SCALE, reasons.details(LapAssist.SCALE), true);
        assertKey(now, "propwash.race.assist.scale.percent.now", 300);
        MutableComponent bare = AssistWords.reason(LapAssist.SCALE, List.of());
        assertKey(bare, "propwash.race.assist.scale");
        assertKey(AssistWords.reason(LapAssist.SCALE, List.of(), true), "propwash.race.assist.scale.now");
    }

    @Test
    void textsExistInEnglishAndGerman() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        assertEquals(en.keySet(), de.keySet());
        Set<String> keys = Set.of("propwash.race.assist.scale", "propwash.race.assist.scale.now", "propwash.race.assist.scale.percent",
                "propwash.race.assist.scale.percent.now");
        assertEquals(keys, en.keySet());
        for (String key : keys) {
            for (JsonObject texts : List.of(en, de)) {
                String text = texts.get(key).getAsString();
                assertFalse(text.isBlank(), key);
                assertEquals(key.contains(".percent"), text.contains("%s"), key + ": " + text);
            }
        }
        assertTrue(de.get("propwash.race.assist.scale.percent").getAsString().contains("%s %%"));
        assertTrue(en.get("propwash.race.assist.scale.percent").getAsString().contains("%s%%"));
    }

    private static void assertKey(MutableComponent component, String key, Object... args) {
        if (!(component.getContents() instanceof TranslatableContents translatable)) {
            fail("kein Übersetzungsschlüssel: " + component);
            return;
        }
        assertEquals(key, translatable.getKey());
        assertArrayEquals(args, translatable.getArgs());
    }

    private static JsonObject lang(String language) throws IOException {
        for (Path candidate : List.of(Path.of("common/src/lang/drone-scale"), Path.of("src/lang/drone-scale"))) {
            Path file = candidate.resolve(language + ".json");
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        fail("Sprachquellen drone-scale nicht gefunden");
        return new JsonObject();
    }
}

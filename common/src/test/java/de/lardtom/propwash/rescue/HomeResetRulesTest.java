package de.lardtom.propwash.rescue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.server.GeneratedArea;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class HomeResetRulesTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final HomePoint HOME = new HomePoint(10.0, 70.0, -20.0, OVERWORLD, 5L, HomePoint.Source.ARM);
    private static final Vec3 LAUNCH = new Vec3(1.0, 64.0, 2.0);

    private static HomeResetRules.Target choose(boolean allowed, HomePoint home, Vec3 launch) {
        return HomeResetRules.choose(allowed, true, 1_000L, Long.MIN_VALUE, home, launch, OVERWORLD);
    }

    @Test
    void homePointWinsOverLaunchPoint() {
        HomeResetRules.Target target = choose(true, HOME, LAUNCH);
        assertEquals(HomeResetRules.Outcome.HOME, target.outcome());
        assertEquals(10.0, target.x());
        assertEquals(70.0, target.y());
        assertEquals(-20.0, target.z());
    }

    @Test
    void ruleOffRefusesEvenWithHomePoint() {
        HomeResetRules.Target target = choose(false, HOME, LAUNCH);
        assertEquals(HomeResetRules.Outcome.DISABLED, target.outcome());
        assertFalse(target.outcome().moves());
        assertFalse(target.outcome().silent());
    }

    @Test
    void onlyThePilotMayReset() {
        HomeResetRules.Target target = HomeResetRules.choose(true, false, 1_000L, Long.MIN_VALUE, HOME, LAUNCH, OVERWORLD);
        assertEquals(HomeResetRules.Outcome.NOT_PILOT, target.outcome());
        assertTrue(target.outcome().silent());
    }

    @Test
    void cooldownSwallowsRepeatedPresses() {
        assertEquals(HomeResetRules.Outcome.COOLDOWN, HomeResetRules.choose(true, true, 1_010L, 1_000L, HOME, LAUNCH, OVERWORLD).outcome());
        assertEquals(HomeResetRules.Outcome.HOME,
                HomeResetRules.choose(true, true, 1_000L + HomeResetRules.COOLDOWN_TICKS, 1_000L, HOME, LAUNCH, OVERWORLD).outcome());
        assertEquals(HomeResetRules.Outcome.HOME, HomeResetRules.choose(true, true, 5L, 1_000L, HOME, LAUNCH, OVERWORLD).outcome());
    }

    @Test
    void withoutHomePointTheLaunchPointIsTheFallback() {
        HomeResetRules.Target target = choose(true, null, LAUNCH);
        assertEquals(HomeResetRules.Outcome.LAUNCH, target.outcome());
        assertEquals(LAUNCH, new Vec3(target.x(), target.y(), target.z()));
        assertEquals(HomeResetRules.Outcome.NO_HOME, choose(true, null, null).outcome());
    }

    @Test
    void neverResetsIntoAnotherDimension() {
        HomePoint nether = new HomePoint(0.0, 64.0, 0.0, NETHER, 5L, HomePoint.Source.MANUAL);
        assertEquals(HomeResetRules.Outcome.LAUNCH, choose(true, nether, LAUNCH).outcome());
        assertEquals(HomeResetRules.Outcome.OTHER_DIMENSION, choose(true, nether, null).outcome());
    }

    @Test
    void targetMustBeInsideBorderAndBuildHeight() {
        assertTrue(HomeResetRules.inside(64.0, -64, 320, true));
        assertFalse(HomeResetRules.inside(64.0, -64, 320, false));
        assertFalse(HomeResetRules.inside(-65.0, -64, 320, true));
        assertFalse(HomeResetRules.inside(321.0, -64, 320, true));
        assertFalse(HomeResetRules.inside(Double.NaN, -64, 320, true));
    }

    @Test
    void resetNeverGoesWhereThePilotMayNotFly() {
        assertEquals(HomeResetRules.Outcome.OUT_OF_RANGE, HomeResetRules.restriction(false, false, GeneratedArea.Status.GENERATED, true));
        assertEquals(HomeResetRules.Outcome.PROTECTED, HomeResetRules.restriction(true, true, GeneratedArea.Status.GENERATED, true));
        assertEquals(null, HomeResetRules.restriction(true, false, GeneratedArea.Status.MISSING, true));
        assertEquals(null, HomeResetRules.restriction(true, false, GeneratedArea.Status.GENERATED, false));
        assertEquals(HomeResetRules.Outcome.UNEXPLORED, HomeResetRules.restriction(true, false, GeneratedArea.Status.MISSING, false));
        assertEquals(HomeResetRules.Outcome.LOADING, HomeResetRules.restriction(true, false, GeneratedArea.Status.UNKNOWN, false));
        assertTrue(HomeResetRules.Outcome.LOADING.silent());
        assertFalse(HomeResetRules.Outcome.UNEXPLORED.silent());
    }

    @Test
    void clearanceSearchClimbsOutOfBlocks() {
        assertEquals(64.0, HomeResetRules.clearY(64.0, y -> true));
        assertEquals(65.0, HomeResetRules.clearY(64.0, y -> y >= 65.0));
        assertEquals(64.0 + HomeResetRules.CLEARANCE, HomeResetRules.clearY(64.0, y -> y >= 64.0 + HomeResetRules.CLEARANCE));
        assertTrue(Double.isNaN(HomeResetRules.clearY(64.0, y -> false)));
    }

    @Test
    void levelKeepsHeadingAndDropsTilt() {
        for (float yaw : new float[]{0.0F, 0.7F, -2.1F, 3.0F}) {
            Quaternionf tilted = new Quaternionf().rotationY(yaw).rotateX(0.6F).rotateZ(-0.9F);
            Quaternionf level = HomeResetRules.level(tilted);
            Vector3f up = level.transform(new Vector3f(0.0F, 1.0F, 0.0F));
            assertEquals(1.0F, up.y, 1.0E-5F);
            Vector3f before = tilted.transform(new Vector3f(0.0F, 0.0F, -1.0F));
            Vector3f after = level.transform(new Vector3f(0.0F, 0.0F, -1.0F));
            assertEquals(Math.atan2(before.x, before.z), Math.atan2(after.x, after.z), 1.0E-4);
        }
        Quaternionf upsideDown = new Quaternionf().rotationY(1.2F).rotateZ((float) Math.PI);
        Vector3f up = HomeResetRules.level(upsideDown).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        assertEquals(1.0F, up.y, 1.0E-5F);
        Quaternionf noseDown = new Quaternionf().rotationY(0.4F).rotateX((float) (-Math.PI / 2.0));
        Vector3f flat = HomeResetRules.level(noseDown).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        assertEquals(1.0F, flat.y, 1.0E-5F);
    }

    @Test
    void allowResetRuleIsOnByDefaultAndInEveryPreset() {
        RuleSpec spec = WorldRuleSpecs.ALLOW_RESET;
        assertEquals("propwash:allow_reset", spec.id());
        assertEquals(RuleSpec.Kind.BOOL, spec.kind());
        assertTrue(spec.defaultOn());
        assertEquals(RuleSpec.Group.GAMEPLAY, spec.group());
        for (RulePreset preset : RulePreset.values()) {
            assertEquals(1, preset.value(spec), preset.id());
        }
    }

    @Test
    void everyShownOutcomeAndTheRuleHaveTexts() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject rescue = read(common().resolve("src/lang/rescue/" + language + ".json"));
            for (HomeResetRules.Outcome outcome : HomeResetRules.Outcome.values()) {
                if (!outcome.silent()) {
                    assertTrue(rescue.has(outcome.messageKey()), language + " " + outcome.messageKey());
                }
            }
            assertTrue(rescue.has(WorldRuleSpecs.ALLOW_RESET.translationKey()), language);
            assertTrue(rescue.has(WorldRuleSpecs.ALLOW_RESET.translationKey() + ".description"), language);
            assertTrue(rescue.has("key.propwash.reset"), language);
            JsonObject race = read(common().resolve("src/lang/race/" + language + ".json"));
            for (LapTracker.AbortReason reason : LapTracker.AbortReason.values()) {
                if (reason != LapTracker.AbortReason.SESSION_ENDED) {
                    String key = "propwash.race.msg.abort." + reason.name().toLowerCase(Locale.ROOT);
                    assertTrue(race.has(key), language + " " + key);
                }
            }
            assertFalse(race.has("key.propwash.race_restart"), language);
        }
    }

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/lang/rescue"))) {
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
}

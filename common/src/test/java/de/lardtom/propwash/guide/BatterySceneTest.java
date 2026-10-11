package de.lardtom.propwash.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import de.lardtom.propwash.guide.scenes.BatteryScene;
import de.lardtom.propwash.guide.scenes.SceneKit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BatterySceneTest {
    private static final String KEY = "propwash.guide.scene.battery.callout.";

    private static Scene scene() {
        Scene scene = GuideScenes.byId(BatteryScene.ID);
        assertNotNull(scene);
        return scene;
    }

    private static int step(Scene scene, String name) {
        for (int i = 0; i < scene.stepCount(); i++) {
            if (scene.steps().get(i).name().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("Schritt fehlt: " + name);
    }

    private static ActorState actor(SceneState state, String id) {
        ActorState actor = state.actor(id);
        assertNotNull(actor, id + " fehlt bei " + state.time());
        return actor;
    }

    private static Optional<SceneState.CalloutView> callout(SceneState state, String name) {
        return state.callouts().stream().filter(c -> c.textKey().equals(KEY + name)).findFirst();
    }

    private static float distance(ScenePos a, ScenePos b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        float dz = a.z() - b.z();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean inside(SceneState.HighlightView box, ScenePos p) {
        return p.x() >= box.min().x() && p.x() <= box.max().x() && p.y() >= box.min().y() && p.y() <= box.max().y()
                && p.z() >= box.min().z() && p.z() <= box.max().z();
    }

    private static JsonObject lang(String language) throws IOException {
        for (Path dir : List.of(Path.of("common/src/lang/guide"), Path.of("src/lang/guide"))) {
            if (Files.isDirectory(dir)) {
                try (Reader reader = Files.newBufferedReader(dir.resolve(language + ".json"), StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        fail("Sprachquellen der Flugschule nicht gefunden");
        return new JsonObject();
    }

    private static String grams(double grams) {
        return Math.round(grams) + "\u00a0g";
    }

    @Test
    void theFrameRefusesTheHeavyPackRightAtTheDrone() {
        Scene scene = scene();
        float fit = scene.stepStart(step(scene, "fit"));
        ActorState pushed = actor(SceneEvaluator.evaluate(scene, fit + BatteryScene.REFUSED_AT - 0.5F), BatteryScene.HEAVY);
        SceneState refused = SceneEvaluator.evaluate(scene, fit + BatteryScene.REFUSED_AT + 20.0F);
        ActorState drone = actor(refused, BatteryScene.DRONE);
        assertTrue(distance(pushed.position(), drone.position()) < 0.6F, "der schwere Akku kommt bis an die Drohne");
        ActorState no = actor(refused, BatteryScene.NO);
        assertTrue(distance(no.position(), drone.position()) < 0.4F, "Sperrzeichen sitzt an der Drohne");
        SceneState.CalloutView frame = callout(refused, "frame").orElseThrow(() -> new AssertionError("Hinweis Rahmen fehlt"));
        assertTrue(distance(frame.anchor(), drone.position()) < 0.2F, "Hinweis Rahmen zeigt auf die Drohne");
        assertTrue(refused.highlights().stream().anyMatch(box -> inside(box, drone.position().add(0.0F, 0.05F, 0.0F))),
                "Rahmen hervorgehoben");
        assertTrue(distance(actor(refused, BatteryScene.HEAVY).position(), drone.position()) < 0.6F, "der Akku schwebt über der Drohne");
        ActorState back = actor(SceneEvaluator.evaluate(scene, fit + BatteryScene.BOUNCE_AT + 20.0F), BatteryScene.HEAVY);
        assertTrue(distance(back.position(), pushed.position()) > 0.4F
                && distance(back.position(), drone.position()) > distance(pushed.position(), drone.position()), "der Akku prallt zurück");
        SceneState rule = SceneEvaluator.evaluate(scene, fit + BatteryScene.RULE_AT + 10.0F);
        SceneState.CalloutView hint = callout(rule, "rule").orElseThrow(() -> new AssertionError("Hinweis Welt-Regel fehlt"));
        assertTrue(distance(hint.anchor(), actor(rule, BatteryScene.HEAVY).position()) < 0.2F);
        ActorState gone = rule.actor(BatteryScene.NO);
        assertTrue(gone == null || !gone.visible(), "Sperrzeichen weg, sobald die Welt-Regel genannt wird");
    }

    @Test
    void noChargerIsInThePictureWhileAPackIsRefused() {
        Scene scene = scene();
        int fit = step(scene, "fit");
        float from = scene.stepStart(fit) + BatteryScene.HEAVY_AT;
        for (float t = from; t < scene.stepEnd(fit); t += 2.0F) {
            SceneState state = SceneEvaluator.evaluate(scene, t);
            assertNull(state.block(BatteryScene.CHARGER_X, 1, BatteryScene.CHARGER_Z), "Ladegerät im Bild bei " + t);
        }
        SceneState charging = SceneEvaluator.evaluate(scene, scene.stepStart(step(scene, "charger")) + 20.0F);
        assertNotNull(charging.block(BatteryScene.CHARGER_X, 1, BatteryScene.CHARGER_Z), "Ladegerät kommt zurück");
    }

    @Test
    void theChargerTakesLiPoLiHvAndLiIon() {
        Scene scene = scene();
        int charger = step(scene, "charger");
        SceneStep step = scene.steps().get(charger);
        Set<String> inserted = new HashSet<>();
        for (TimedCue timed : step.cues()) {
            if (timed.cue() instanceof Cue.Spawn spawn && spawn.spec() instanceof ActorSpec.Item item) {
                inserted.add(item.itemId());
            }
        }
        Set<String> chemistries = new HashSet<>();
        for (BatteryType type : BatteryScene.CHARGED) {
            assertTrue(inserted.contains(SceneKit.part(type)), "nicht ins Ladegerät gelegt: " + type);
            chemistries.add(type.chemistry.key());
        }
        assertEquals(3, chemistries.size(), "LiPo, LiHV und Li-Ion: " + chemistries);
        assertTrue(chemistries.contains(BatteryType.B6S_4000_LION.chemistry.key()));
        SceneState full = SceneEvaluator.evaluate(scene, scene.stepStart(charger) + BatteryScene.CHEMISTRY_AT + 4.0F);
        SceneState.BlockView block = full.block(BatteryScene.CHARGER_X, 1, BatteryScene.CHARGER_Z);
        assertNotNull(block);
        long charging = block.block().properties().values().stream().filter("charging"::equals).count();
        assertEquals(1 + BatteryScene.CHARGED.size(), charging, "Zustand und drei Fächer laden: " + block.block().properties());
        assertTrue(callout(full, "chemistry").isPresent());
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        long done = end.block(BatteryScene.CHARGER_X, 1, BatteryScene.CHARGER_Z).block().properties().values().stream().filter("full"::equals).count();
        assertEquals(BatteryScene.CHARGED.size(), done);
    }

    @Test
    void textsNameTheRealWeightsFromThePartData() throws IOException {
        String limit = grams(BatteryFit.rule(BatteryScene.BUILD.frame()).maxMassGrams());
        String heavy = grams(BatteryType.B6S_4000_LION.massGrams());
        String fitted = grams(BatteryType.B6S_1300.massGrams());
        assertEquals(BatteryFit.Result.TOO_HEAVY, BatteryFit.check(BatteryScene.BUILD.frame(), BatteryType.B6S_4000_LION));
        assertEquals(BatteryFit.Result.OK, BatteryFit.check(BatteryScene.BUILD.frame(), BatteryType.B6S_1300));
        assertEquals(BatteryFit.Result.WEAK, BatteryFit.check(BatteryScene.BUILD.frame(), BatteryType.B4S_650));
        for (String language : List.of("de_de", "en_us")) {
            JsonObject texts = lang(language);
            assertTrue(texts.get(KEY + "frame").getAsString().contains(limit), language + ": " + limit);
            assertTrue(texts.get(KEY + "heavy").getAsString().contains(heavy), language + ": " + heavy);
            assertTrue(texts.get(KEY + "frame").getAsString().contains(heavy), language + ": " + heavy);
            assertTrue(texts.get(KEY + "ok").getAsString().contains(fitted), language + ": " + fitted);
            assertTrue(texts.get(KEY + "chemistry").getAsString().contains("Li-Ion"), language);
            assertFalse(texts.has(KEY + "bad"), language + ": alter Hinweis „abgelehnt“ am Akku");
        }
        assertTrue(lang("de_de").get(KEY + "frame").getAsString().contains("Rahmen"));
        assertTrue(lang("en_us").get(KEY + "frame").getAsString().contains("frame"));
        assertTrue(lang("de_de").get(KEY + "rule").getAsString().contains("Teile müssen zusammenpassen"));
    }
}

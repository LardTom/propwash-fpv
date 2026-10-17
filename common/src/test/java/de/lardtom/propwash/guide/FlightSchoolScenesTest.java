package de.lardtom.propwash.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import de.lardtom.propwash.guide.scenes.SceneKit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FlightSchoolScenesTest {
    static final List<String> ORDER = List.of("drone_build", "bind", "fpv_los", "battery", "race", "gate_free", "course_elements", "tricks", "video", "action_cam", "gripper",
            "configurator", "rescue", "hold", "mode_3d", "recovery", "repair", "survival");
    private static final float[][] VIEWPORTS = {{616.0F, 256.0F}, {456.0F, 166.0F}, {748.0F, 378.0F}};
    private static final float MARGIN = 6.0F;
    private static final float SAMPLE = 1.0F;
    private static final Set<String> KNOWN_KEYS = Set.of("key.use", "key.attack", "key.sneak", "key.inventory", SceneKit.KEY_ARM,
            SceneKit.KEY_FLIGHT_MODE, SceneKit.KEY_FLIP, SceneKit.KEY_EXIT, SceneKit.KEY_BEEPER, SceneKit.KEY_RESCUE, SceneKit.KEY_SET_HOME,
            SceneKit.KEY_RESET, SceneKit.KEY_CONFIGURATOR, SceneKit.KEY_RECORD, SceneKit.KEY_ALT_HOLD, SceneKit.KEY_POS_HOLD,
            SceneKit.KEY_GATE_ROTATE, SceneKit.KEY_GATE_EDIT_MOUSE, SceneKit.KEY_CARGO_DROP, SceneKit.KEY_MODE_3D, SceneKit.KEY_TURTLE, SceneKit.KEY_ROLL_RIGHT);

    private static Path guideLang() {
        for (Path candidate : List.of(Path.of("common/src/lang/guide"), Path.of("src/lang/guide"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        fail("Sprachquellen der Flugschule nicht gefunden");
        return Path.of(".");
    }

    private static JsonObject lang(String language) throws IOException {
        try (Reader reader = Files.newBufferedReader(guideLang().resolve(language + ".json"), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    static List<String> propwashItems() {
        List<String> ids = new ArrayList<>(List.of(SceneKit.WORKBENCH, SceneKit.CHARGER, SceneKit.DRONE_ITEM, SceneKit.REMOTE,
                SceneKit.GOGGLES_ANALOG, SceneKit.GOGGLES_DIGITAL, SceneKit.GOGGLES_CREATIVE, SceneKit.GATE_SMALL, SceneKit.GATE_MEDIUM,
                SceneKit.GATE_LARGE, SceneKit.COURSE_TOOL, SceneKit.RACE_FLAG, SceneKit.SLALOM_POLE, SceneKit.DIVE_CUBE,
                SceneKit.FREEFORM_GATE, SceneKit.GATE_POLE));
        for (PartInfo[] parts : List.<PartInfo[]>of(FrameType.values(), StackType.values(), MotorType.values(), PropType.values(),
                VideoType.values(), BatteryType.values(), AccessoryType.values())) {
            for (PartInfo part : parts) {
                ids.add(SceneKit.part(part));
            }
        }
        return ids;
    }

    private static boolean isPart(String id) {
        return !id.equals(SceneKit.WORKBENCH) && !id.equals(SceneKit.CHARGER) && !id.equals(SceneKit.DRONE_ITEM) && !id.equals(SceneKit.REMOTE)
                && !id.startsWith(SceneKit.NAMESPACE + "goggles_") && !id.startsWith(SceneKit.NAMESPACE + "gate_") && !id.equals(SceneKit.COURSE_TOOL)
                && !id.equals(SceneKit.RACE_FLAG) && !id.equals(SceneKit.SLALOM_POLE) && !id.equals(SceneKit.DIVE_CUBE)
                && !id.equals(SceneKit.FREEFORM_GATE);
    }

    @Test
    void allScenesAreRegisteredInOrder() {
        List<String> ids = GuideScenes.all().stream().map(Scene::id).toList();
        assertEquals(ORDER, ids);
        for (Scene scene : GuideScenes.all()) {
            assertTrue(scene.stepCount() >= 3 && scene.stepCount() <= 8, scene.id() + ": " + scene.stepCount() + " Schritte");
            assertTrue(scene.duration() <= 60.0F * 20.0F, scene.id() + " zu lang: " + scene.duration());
        }
    }

    @Test
    void everyPropwashItemOpensAScene() {
        for (String id : propwashItems()) {
            assertFalse(GuideScenes.forItem(id, isPart(id)).isEmpty(), "keine Szene für " + id);
        }
        assertTrue(GuideScenes.forItem(SceneKit.part(BatteryType.B6S_1300), true).stream().anyMatch(s -> s.id().equals("battery")));
        assertTrue(GuideScenes.forItem(SceneKit.part(VideoType.ANALOG_RACE), true).stream().anyMatch(s -> s.id().equals("video")));
        assertTrue(GuideScenes.forItem(SceneKit.part(StackType.STACK_30), true).stream().anyMatch(s -> s.id().equals("configurator")));
        assertTrue(GuideScenes.forItem(SceneKit.GATE_MEDIUM, false).stream().anyMatch(s -> s.id().equals("race")));
        assertTrue(GuideScenes.forItem(SceneKit.COURSE_TOOL, false).stream().anyMatch(s -> s.id().equals("race")));
        assertTrue(GuideScenes.forItem(SceneKit.GOGGLES_CREATIVE, false).stream().anyMatch(s -> s.id().equals("video")));
        assertTrue(GuideScenes.forItem(SceneKit.REMOTE, false).stream().anyMatch(s -> s.id().equals("bind")));
    }

    @Test
    void referencedPropwashItemsExist() {
        Set<String> known = new HashSet<>(propwashItems());
        for (Scene scene : GuideScenes.all()) {
            Set<String> used = new LinkedHashSet<>(scene.items().ids());
            used.add(scene.icon());
            for (SceneStep step : scene.steps()) {
                for (TimedCue timed : step.cues()) {
                    switch (timed.cue()) {
                        case Cue.Spawn spawn -> {
                            switch (spawn.spec()) {
                                case ActorSpec.Pilot pilot -> {
                                    used.add(pilot.headItem());
                                    used.add(pilot.handItem());
                                }
                                case ActorSpec.Item item -> used.add(item.itemId());
                                case ActorSpec.Remote remote -> used.add(SceneKit.REMOTE);
                                case ActorSpec.Block block -> used.add(block.block().id());
                                default -> {
                                }
                            }
                        }
                        case Cue.Equip equip -> used.add(equip.item());
                        case Cue.PlaceBlock place -> used.add(place.block().id());
                        case Cue.Keys keys -> {
                            for (KeyToken token : keys.keys()) {
                                if (token.kind() == KeyToken.Kind.MAPPING) {
                                    assertTrue(KNOWN_KEYS.contains(token.value()), scene.id() + ": unbekannte Taste " + token.value());
                                }
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
            for (String id : used) {
                if (id.startsWith(SceneKit.NAMESPACE) && !id.equals(SceneKit.WORKBENCH) && !id.equals(SceneKit.CHARGER)) {
                    assertTrue(known.contains(id), scene.id() + ": unbekanntes Propwash-Item " + id);
                }
            }
        }
    }

    @Test
    void textsFitTheirPlaces() throws IOException {
        for (String language : List.of("de_de", "en_us")) {
            JsonObject texts = lang(language);
            for (Scene scene : GuideScenes.all()) {
                assertTrue(texts.get(scene.titleKey()).getAsString().length() <= 38, language + " Titel zu lang: " + scene.id());
                assertTrue(texts.get(scene.descriptionKey()).getAsString().length() <= 64, language + " Beschreibung zu lang: " + scene.id());
                for (String key : scene.translationKeys()) {
                    int length = texts.get(key).getAsString().length();
                    int limit = key.contains(".step.") && key.endsWith(".text") ? 150
                            : key.contains(".step.") ? 34
                            : key.contains(".hint.") ? 28 : 90;
                    assertTrue(length <= limit, language + " " + key + " hat " + length + " Zeichen (max. " + limit + ")");
                }
            }
        }
    }

    @Test
    void everyStepExplainsSomething() {
        for (Scene scene : GuideScenes.all()) {
            Set<String> seen = new HashSet<>();
            for (int step = 0; step < scene.stepCount(); step++) {
                boolean any = false;
                for (float t = scene.stepStart(step); t < scene.stepEnd(step); t += SAMPLE) {
                    SceneState state = SceneEvaluator.evaluate(scene, t);
                    for (SceneState.CalloutView callout : state.callouts()) {
                        seen.add(callout.textKey());
                        any = true;
                    }
                    for (SceneState.KeyHintView hint : state.keyHints()) {
                        seen.add(hint.textKey());
                    }
                }
                assertTrue(any, scene.id() + ": Schritt ohne Hinweis: " + scene.steps().get(step).name());
            }
            for (String key : scene.translationKeys()) {
                if (key.contains(".callout.") || key.contains(".hint.")) {
                    assertTrue(seen.contains(key), scene.id() + ": nie sichtbar: " + key.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    @Test
    void anchorsAndDronesStayInView() {
        List<String> problems = new ArrayList<>();
        for (Scene scene : GuideScenes.all()) {
            SceneFraming framing = SceneFraming.of(scene);
            for (float t = 0.0F; t <= scene.duration(); t += SAMPLE) {
                SceneState state = SceneEvaluator.evaluate(scene, t);
                assertTrue(Float.isFinite(state.camera().yaw()) && Float.isFinite(state.camera().zoom()), scene.id() + " Kamera bei " + t);
                for (float[] viewport : VIEWPORTS) {
                    float w = viewport[0];
                    float h = viewport[1];
                    SceneProjection projection = framing.projection(state.camera(), state.time(),
                            SceneProjection.basePixelsPerBlock(w, h, scene.viewRadius()), 0.0F, 0.0F, w, h, 1.0F);
                    String where = scene.id() + "/" + scene.steps().get(state.step()).name() + " t=" + t + " " + (int) w + "x" + (int) h;
                    for (SceneState.CalloutView callout : state.callouts()) {
                        if (callout.alpha() >= 0.5F) {
                            check(projection, callout.anchor(), w, h, MARGIN, where + " Hinweis " + callout.textKey(), problems);
                        }
                    }
                    for (SceneState.KeyHintView hint : state.keyHints()) {
                        if (hint.anchor() != null && hint.alpha() >= 0.5F) {
                            check(projection, hint.anchor(), w, h, MARGIN, where + " Taste " + hint.textKey(), problems);
                        }
                    }
                    for (ActorState actor : state.visibleActors()) {
                        assertTrue(Float.isFinite(actor.position().x()) && Float.isFinite(actor.position().y())
                                && Float.isFinite(actor.position().z()) && Float.isFinite(actor.yaw()), where + " " + actor.id());
                        if (actor.spec() instanceof ActorSpec.Drone && actor.presence() >= 0.99F) {
                            check(projection, actor.position().add(0.0F, 0.1F, 0.0F), w, h, 0.0F, where + " Drohne " + actor.id(), problems);
                        }
                    }
                }
            }
        }
        Map<String, String> first = new LinkedHashMap<>();
        for (String problem : problems) {
            first.putIfAbsent(problem.replaceAll(" t=[0-9.]+", "").replaceAll(" bei .*", ""), problem);
        }
        assertTrue(problems.isEmpty(), problems.size() + " Probleme an " + first.size() + " Stellen:\n" + String.join("\n", first.values()));
    }

    private static void check(SceneProjection projection, ScenePos pos, float w, float h, float margin, String what, List<String> problems) {
        float[] p = projection.project(pos);
        if (p[0] < margin || p[0] > w - margin || p[1] < margin || p[1] > h - margin) {
            problems.add(what + String.format(Locale.ROOT, " bei %.0f/%.0f", p[0], p[1]));
        }
    }
}

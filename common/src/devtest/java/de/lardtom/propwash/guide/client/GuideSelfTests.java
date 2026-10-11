package de.lardtom.propwash.guide.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.creative.CreativeScene;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.render.SceneAssets;
import de.lardtom.propwash.guide.client.render.SceneFrame;
import de.lardtom.propwash.guide.client.render.SceneRenderer;
import de.lardtom.propwash.guide.client.screen.GuideTab;
import de.lardtom.propwash.guide.client.screen.SceneIndexScreen;
import de.lardtom.propwash.guide.client.screen.SceneOverlay;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.layout.CalloutLayout;
import de.lardtom.propwash.guide.layout.LabelRect;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.DronePart;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePlayback;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import de.lardtom.propwash.guide.scenes.BatteryScene;
import de.lardtom.propwash.guide.scenes.ConfiguratorScene;
import de.lardtom.propwash.guide.scenes.DroneBuildScene;
import de.lardtom.propwash.guide.scenes.CourseElementsScene;
import de.lardtom.propwash.guide.scenes.GateFreeScene;
import de.lardtom.propwash.guide.scenes.RaceScene;
import de.lardtom.propwash.guide.scenes.SceneKit;
import de.lardtom.propwash.guide.scenes.GripperScene;
import de.lardtom.propwash.guide.scenes.Mode3dScene;
import de.lardtom.propwash.guide.scenes.RepairScene;
import de.lardtom.propwash.guide.scenes.RecoveryScene;
import de.lardtom.propwash.guide.scenes.RescueScene;
import de.lardtom.propwash.guide.scenes.SurvivalScene;
import de.lardtom.propwash.guide.scenes.TricksScene;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class GuideSelfTests {
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String RENDERED = "rendered";
    private static final String FAILED = "failed";
    private static final String SHOT = "shot";
    private static final String FUTURE = "future";
    private static final String HINTS = "hints";
    private static final String SCENE = "scene";
    private static final String SAMPLE = "sample";
    private static final String FRAMES = "frames";
    private static final float MAX_OWNER_COVER = 0.2F;
    private static final String CHECKED = "checked";
    private static final String NEXT = "next";
    private static final String NAME = "name";
    private static final String TARGETS = "targets";
    private static final String ANCHORS = "anchors";
    private static final String TICKS = "ticks";
    private static final String SETTLING = "settling";
    private static final String STILL = "still";
    private static final String FRAME = "frame";
    private static final String VIEW = "view";
    private static final String RESTARTS = "restarts";
    private static final int MAX_VIEW_RESTARTS = 3;
    private static final int STILL_FRAMES = 4;
    private static final float PLAY_SPEED = 4.0F;
    private static final float[] PLAY_SHOTS = {0.3F, 0.65F};
    private static final float MAX_GLIDE_SPEED = 40.0F;
    private static final float AMBIGUOUS_PX = 40.0F;
    private static final int END_SETTLE_TICKS = 40;
    private static final float TURN_YAW = 1.1F;
    private static final int SETTLE_TICKS = 25;
    private static final int SHOT_SETTLE_TICKS = 40;
    private static final String LABEL_SHOTS = "flight_school_labels/";
    private static final float[] SAMPLE_POINTS = {0.12F, 0.55F, 0.96F};
    private static final List<String> UI_KEYS = List.of("propwash.guide.title", "propwash.guide.hold", "propwash.guide.key.unbound",
            "propwash.guide.player.play", "propwash.guide.player.pause", "propwash.guide.player.previous", "propwash.guide.player.next",
            "propwash.guide.player.replay", "propwash.guide.player.reset_view", "propwash.guide.player.close", "propwash.guide.player.step",
            "propwash.guide.player.controls", "propwash.guide.player.finished", "propwash.guide.player.paused", "propwash.guide.player.no_3d",
            "propwash.guide.index.all", "propwash.guide.index.for_item", "propwash.guide.index.show_all", "propwash.guide.index.close",
            "propwash.guide.index.tip", "propwash.guide.index.meta", "propwash.guide.index.empty", "propwash.guide.tab.section",
            "propwash.guide.tab.intro", "propwash.guide.mouse.left", "propwash.guide.mouse.right", "propwash.guide.mouse.middle",
            "key.propwash." + GuideKeys.NAME);
    private static final List<Shot> SHOTS = List.of(
            new Shot("flight_school_build", DroneBuildScene.ID, 2, DroneBuildScene.PROPS_LANDED + 14.0F, false),
            new Shot("flight_school_fly", DroneBuildScene.ID, 7, 70.0F, false),
            new Shot("flight_school_bench", DroneBuildScene.ID, 0, 70.0F, false),
            new Shot("flight_school_build_packs", DroneBuildScene.ID, 4, 100.0F, false),
            new Shot("flight_school_build_rule", DroneBuildScene.ID, 4, DroneBuildScene.RULE_AT + 14.0F, false),
            new Shot("flight_school_battery_fit", BatteryScene.ID, 3, BatteryScene.REFUSED_AT + 26.0F, false),
            new Shot("flight_school_battery_rule", BatteryScene.ID, 3, BatteryScene.RULE_AT + 16.0F, false),
            new Shot("flight_school_battery_charger", BatteryScene.ID, 4, BatteryScene.CHEMISTRY_AT + 14.0F, false),
            new Shot("flight_school_race", RaceScene.ID, 3, RaceScene.passTime(1) - 3.0F, false),
            new Shot("flight_school_race_mount", RaceScene.ID, 6, 90.0F, false),
            new Shot("flight_school_race_dive", RaceScene.ID, 6, RaceScene.DIVE_START_TIME + RaceScene.DIVE_LENGTH * 0.42F, false),
            new Shot("flight_school_race_remote", RaceScene.ID, 6, RaceScene.DIVE_START_TIME + 4.0F, false),
            new Shot("flight_school_gate_free", GateFreeScene.ID, 1, 140.0F, false),
            new Shot("flight_school_gate_free_size", GateFreeScene.ID, 2, 70.0F, false),
            new Shot("flight_school_gate_free_freeform", GateFreeScene.ID, 4, GateFreeScene.FREEFORM_FLY + GateFreeScene.FREEFORM_FLY_LENGTH * 0.45F,
                    false),
            new Shot("flight_school_course_elements", CourseElementsScene.ID, 0, 120.0F, false),
            new Shot("flight_school_course_elements_flag", CourseElementsScene.ID, 1, 70.0F, false),
            new Shot("flight_school_course_elements_cube", CourseElementsScene.ID, 2, 70.0F, false),
            new Shot("flight_school_course_elements_fly", CourseElementsScene.ID, 3,
                    CourseElementsScene.FLY_START + CourseElementsScene.FLY_LENGTH * 16.4F / CourseElementsScene.PATH.size(), false),
            new Shot("flight_school_sticks", ConfiguratorScene.ID, 0, 70.0F, false),
            new Shot("flight_school_rates", ConfiguratorScene.ID, 1, 37.0F, false),
            new Shot("flight_school_commands", ConfiguratorScene.ID, 6, 40.0F, false),
            new Shot("flight_school_rescue", RescueScene.ID, 3, 90.0F, false),
            new Shot("flight_school_tricks", TricksScene.ID, 1, 33.0F, false),
            new Shot("flight_school_tricks_new_school", TricksScene.ID, 5, 42.0F, false),
            new Shot("flight_school_gripper", GripperScene.ID, 1, 130.0F, false),
            new Shot("flight_school_gripper_drop", GripperScene.ID, 2, 80.0F, false),
            new Shot("flight_school_3d", Mode3dScene.ID, 2, 80.0F, false),
            new Shot("flight_school_3d_takeoff", Mode3dScene.ID, 4, 150.0F, false),
            new Shot("flight_school_repair_material", RepairScene.ID, 2, 64.0F, false),
            new Shot("flight_school_arm_air", RecoveryScene.ID, 2, 48.0F, false),
            new Shot("flight_school_creative", CreativeScene.ID, 0, 100.0F, false),
            new Shot("flight_school_creative_editor", CreativeScene.ID, 1, 80.0F, false),
            new Shot("flight_school_index", DroneBuildScene.ID, 0, 0.0F, true));

    private static final String WINDOW = "window";
    private static final String CONFIG = "config";
    private static final float ANCHOR_TOLERANCE = 1.5F;
    private static final List<Fit> FITS = List.of(
            new Fit(854, 480, 0, SurvivalScene.ID, 1, 70.0F, 0.0F, 0.0F, 1.0F),
            new Fit(1280, 720, 2, SurvivalScene.ID, 1, 70.0F, 35.0F, 15.0F, 1.6F),
            new Fit(1920, 1080, 3, SurvivalScene.ID, 1, 70.0F, 0.0F, 0.0F, 1.0F),
            new Fit(1920, 1080, 1, DroneBuildScene.ID, 1, 40.0F, 120.0F, 30.0F, 1.3F),
            new Fit(2560, 1600, 4, SurvivalScene.ID, 1, 70.0F, -60.0F, 10.0F, 0.7F),
            new Fit(2000, 1300, 0, SurvivalScene.ID, 1, 70.0F, 0.0F, 0.0F, 1.0F),
            new Fit(2560, 1080, 3, DroneBuildScene.ID, 1, 40.0F, 0.0F, 0.0F, 1.0F));

    private GuideSelfTests() {
    }

    private record Fit(int width, int height, int guiScale, String scene, int step, float offset, float yaw, float pitch, float zoom) {
        String file() {
            return "flight_school_callouts_" + this.width + "x" + this.height + "_gui" + this.guiScale;
        }
    }

    private record Shot(String file, String scene, int step, float offset, boolean index) {
    }

    public static void register() {
        SelfTest.clientStep("guide: Szenen, Assets und Sprachtexte", 1, GuideSelfTests::assetsStep);
        SelfTest.clientStep("guide: Zeitleiste der Demo-Szene", 1, GuideSelfTests::timelineStep);
        SelfTest.clientStep("guide: Taste und Konfigurator-Tab", 1, GuideSelfTests::keyAndTabStep);
        SelfTest.clientStep("guide: Szenen-Player öffnen und abspielen", 600, GuideSelfTests::playerStep);
        SelfTest.clientStep("guide: alle Akteur-Arten zeichnen", 300, GuideSelfTests::actorKindsStep);
        SelfTest.clientStep("guide: alle Szenen Schritt für Schritt abspielen", 4000, GuideSelfTests::allScenesStep);
        SelfTest.clientStep("guide: Hinweis-Anker treffen ihr Ziel bei allen Fenstergrößen", 3000, GuideSelfTests::anchorFitStep);
        SelfTest.clientStep("guide: Hinweise bleiben beim Drehen und Zoomen stehen", 800, GuideSelfTests::labelTurnStep);
        SelfTest.clientStep("guide: Hinweise ruhig in allen Lektionen (abspielen, drehen, zoomen)", 14000, GuideSelfTests::labelPlayStep);
        SelfTest.clientStep("guide: Übersicht öffnen", 200, GuideSelfTests::indexStep);
        SelfTest.clientStep("guide: Taste halten im Inventar öffnet die Szene", 400, GuideSelfTests::holdStep);
        if (Screenshots.enabled()) {
            SelfTest.clientStep("guide: Bildschirmfotos der Flugschule", 5000, GuideSelfTests::screenshotStep);
        }
        SelfTest.afterLeaveStep("guide: Szenen-Player ohne Welt", 400, GuideSelfTests::noWorldStep);
        TrainingSelfTests.register();
    }

    private static Scene demo() {
        Scene scene = GuideScenes.byId(DroneBuildScene.ID);
        if (scene == null) {
            throw new IllegalStateException("Demo-Szene fehlt");
        }
        return scene;
    }

    private static StepResult assetsStep(SelfTest.StepContext ctx) {
        List<Scene> scenes = GuideScenes.all();
        if (scenes.isEmpty() || GuideScenes.byId(DroneBuildScene.ID) == null) {
            return StepResult.fail("Demo-Szene nicht registriert");
        }
        Language language = Language.getInstance();
        List<String> missing = new ArrayList<>();
        for (String key : UI_KEYS) {
            if (!language.has(key)) {
                missing.add(key);
            }
        }
        for (Scene scene : scenes) {
            for (String key : scene.translationKeys()) {
                if (!language.has(key)) {
                    missing.add(key);
                }
            }
            if (SceneAssets.item(scene.icon()).isEmpty()) {
                missing.add("Symbol " + scene.icon());
            }
            for (SceneStep step : scene.steps()) {
                for (TimedCue timed : step.cues()) {
                    checkCue(timed.cue(), missing);
                }
            }
        }
        return StepResult.check(missing.isEmpty(), "fehlt: " + missing);
    }

    private static void checkCue(Cue cue, List<String> missing) {
        switch (cue) {
            case Cue.PlaceBlock place -> {
                if (SceneAssets.block(place.block()) == null) {
                    missing.add("Block " + place.block());
                }
            }
            case Cue.Spawn spawn -> {
                switch (spawn.spec()) {
                    case ActorSpec.Pilot pilot -> {
                        item(pilot.headItem(), missing);
                        item(pilot.handItem(), missing);
                    }
                    case ActorSpec.Item item -> item(item.itemId(), missing);
                    case ActorSpec.Remote remote -> item(SceneKit.REMOTE, missing);
                    case ActorSpec.Block block -> {
                        if (SceneAssets.block(block.block()) == null) {
                            missing.add("Block " + block.block());
                        }
                    }
                    default -> {
                    }
                }
            }
            case Cue.Equip equip -> item(equip.item(), missing);
            case Cue.Keys keys -> {
                for (KeyToken token : keys.keys()) {
                    if (token.kind() == KeyToken.Kind.MAPPING && KeyMapping.get(token.value()) == null) {
                        missing.add("Taste " + token.value());
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void item(String id, List<String> missing) {
        if (!id.isEmpty() && SceneAssets.item(id).isEmpty()) {
            missing.add("Item " + id);
        }
    }

    private static StepResult timelineStep(SelfTest.StepContext ctx) {
        Scene scene = demo();
        ScenePlayback playback = new ScenePlayback(scene);
        playback.next();
        if (playback.step() != 1 || playback.time() != scene.stepStart(1)) {
            return StepResult.fail("Weiter springt nicht zum zweiten Schritt: " + playback.time());
        }
        playback.previous();
        if (playback.step() != 0) {
            return StepResult.fail("Zurück springt nicht zum ersten Schritt");
        }
        playback.seek(scene.duration());
        if (!playback.finished() || playback.playing()) {
            return StepResult.fail("Ende der Szene hält nicht an");
        }
        playback.replay();
        if (playback.time() != 0.0F || !playback.playing()) {
            return StepResult.fail("Nochmal startet nicht bei 0");
        }
        SceneState end = SceneEvaluator.evaluate(scene, scene.duration());
        ActorState drone = end.actor(DroneBuildScene.DRONE);
        ActorState pilot = end.actor(DroneBuildScene.PILOT);
        if (drone == null || !drone.fullyAssembled() || drone.armed() || drone.position().y() > 1.2F) {
            return StepResult.fail("Drohne am Ende nicht fertig, gelandet und entschärft");
        }
        ActorState flying = SceneEvaluator.evaluate(scene, scene.stepStart(scene.stepCount() - 1) + 70.0F).actor(DroneBuildScene.DRONE);
        if (flying == null || !flying.armed() || flying.throttle() < 0.5F || flying.position().y() < 1.7F) {
            return StepResult.fail("Drohne fliegt im letzten Schritt nicht");
        }
        if (pilot == null || !DroneBuildScene.REMOTE.equals(pilot.handItem())) {
            return StepResult.fail("Pilot hält am Ende keine Funke");
        }
        if (end.blocks().size() != 27) {
            return StepResult.fail("Blöcke am Ende: " + end.blocks().size());
        }
        SceneState building = SceneEvaluator.evaluate(scene, scene.stepStart(1) + 40.0F);
        ActorState partial = building.actor(DroneBuildScene.DRONE);
        if (partial == null || partial.fullyAssembled() || !partial.part(DronePart.FRAME).attached() || partial.part(DronePart.BATTERY).visible()) {
            return StepResult.fail("Rahmen-Schritt zeigt nicht nur den Rahmen");
        }
        SceneState bench = SceneEvaluator.evaluate(scene, 60.0F);
        if (bench.callouts().isEmpty() || bench.highlights().isEmpty() || bench.keyHints().isEmpty()) {
            return StepResult.fail("Werkbank-Schritt ohne Hinweis, Rahmen oder Tastenhinweis");
        }
        return StepResult.pass();
    }

    private static StepResult keyAndTabStep(SelfTest.StepContext ctx) {
        KeyMapping mapping = GuideKeys.mapping();
        if (mapping == null || !ClientRegistrar.keyMappings().contains(mapping)) {
            return StepResult.fail("Flugschul-Taste nicht registriert");
        }
        InputConstants.Key key = mapping.getDefaultKey();
        if (key.getType() != InputConstants.Type.KEYBOARD || key.getValue() != GuideKeys.DEFAULT_KEY) {
            return StepResult.fail("Standardtaste nicht M: " + key.getName());
        }
        for (KeyMapping other : ClientRegistrar.keyMappings()) {
            if (other != mapping && other.getDefaultKey().equals(key)) {
                return StepResult.fail("Standardtaste kollidiert mit " + other.getName());
            }
        }
        for (KeyMapping vanilla : Minecraft.getInstance().options.keyMappings) {
            if (vanilla != mapping && !ClientRegistrar.keyMappings().contains(vanilla) && vanilla.getDefaultKey().equals(key)) {
                return StepResult.fail("Standardtaste kollidiert mit " + vanilla.getName());
            }
        }
        GuideTab tab = null;
        for (ConfigTab candidate : ConfigTabs.create()) {
            if (candidate instanceof GuideTab guide) {
                tab = guide;
            }
        }
        if (tab == null) {
            return StepResult.fail("Flugschul-Tab nicht im Konfigurator");
        }
        tab.attach(new StubHost(), 0, 0, 420, 240);
        if (tab.list().scenes().isEmpty()) {
            return StepResult.fail("Flugschul-Tab ohne Szenen");
        }
        boolean hit = false;
        for (int y = 0; y < 240 && !hit; y += 4) {
            hit = tab.list().sceneAt(40, y) != null;
        }
        return StepResult.check(hit, "Szenenkarte im Tab nicht anklickbar");
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void phase(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static StepResult playerStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        Scene scene = demo();
        int phase = phase(ctx);
        if (phase == 0) {
            state.put(RENDERED, SceneRenderer.renderedFrames());
            state.put(FAILED, SceneRenderer.failedDrawables());
            mc.gui.setScreen(new SceneScreen(scene, null));
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof SceneScreen screen)) {
            return StepResult.fail("Szenen-Player nicht offen: " + mc.gui.screen());
        }
        long failed = SceneRenderer.failedDrawables() - (long) state.get(FAILED);
        if (failed > 0) {
            return StepResult.fail(failed + " Szenen-Elemente mit Fehler");
        }
        switch (phase) {
            case 1 -> {
                long rendered = SceneRenderer.renderedFrames() - (long) state.get(RENDERED);
                if (screen.frames() < 10 || rendered < 5) {
                    return since(ctx) > 200 ? StepResult.fail("3D-Szene nicht gezeichnet (" + rendered + " Bilder)") : StepResult.pending();
                }
                if (!screen.sceneSubmitted()) {
                    return StepResult.fail("3D-Szene nicht an den GUI-Renderer übergeben");
                }
                screen.playback().seek(scene.stepStart(1) + 40.0F);
                screen.playback().pause();
                phase(ctx, 2);
            }
            case 2 -> {
                if (since(ctx) < 4) {
                    return StepResult.pending();
                }
                if (screen.lastCallouts() < 1) {
                    return StepResult.fail("Rahmen-Hinweis nicht gezeichnet");
                }
                SceneProjection projection = screen.lastProjection();
                SceneState last = screen.lastState();
                if (projection == null || last == null || last.callouts().isEmpty()) {
                    return StepResult.fail("keine Projektion für Hinweise");
                }
                float[] point = projection.project(last.callouts().getFirst().anchor());
                ScreenRectangle vp = screen.viewport();
                if (!vp.containsPoint(Math.round(point[0]), Math.round(point[1]))) {
                    return StepResult.fail("Hinweis-Anker außerhalb der Ansicht: " + point[0] + "/" + point[1]);
                }
                screen.playback().seek(60.0F);
                phase(ctx, 3);
            }
            case 3 -> {
                if (since(ctx) < 4) {
                    return StepResult.pending();
                }
                if (screen.lastKeyHints() < 1 || screen.lastCallouts() < 1) {
                    return StepResult.fail("Werkbank-Schritt ohne Tastenhinweis oder Hinweis");
                }
                screen.next();
                if (screen.playback().step() != 1) {
                    return StepResult.fail("Weiter-Knopf wechselt nicht den Schritt");
                }
                screen.previous();
                if (screen.playback().step() != 0) {
                    return StepResult.fail("Zurück-Knopf wechselt nicht den Schritt");
                }
                screen.playback().seek(scene.duration());
                phase(ctx, 4);
            }
            case 4 -> {
                if (since(ctx) < 4) {
                    return StepResult.pending();
                }
                if (!screen.playback().finished()) {
                    return StepResult.fail("Szene nicht am Ende");
                }
                screen.replay();
                if (screen.playback().time() > 1.0F || !screen.playback().playing()) {
                    return StepResult.fail("Nochmal startet nicht neu");
                }
                screen.onClose();
                return StepResult.check(mc.gui.screen() == null, "Szenen-Player schließt nicht");
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    static Scene actorShowcase() {
        return SceneBuilder.scene("selftest_actors")
                .icon(DroneBuildScene.WORKBENCH)
                .view(4.0F)
                .camera(new ScenePos(0.5F, 1.5F, 0.5F), 30.0F, 25.0F, 1.0F)
                .step("all", 60.0F, s -> {
                    s.at(0.0F).floor(-2, -2, 2, 2, 0, BlockSpec.of("minecraft:grass_block"), 0.5F, 4.0F);
                    s.at(0.0F).spawn("gate", new ActorSpec.Gate("small"), new ScenePos(0.5F, 1.0F, -1.5F), 0.0F, Cue.Appear.POP, 6.0F);
                    s.at(0.0F).light("gate", true);
                    s.at(0.0F).spawn("item", new ActorSpec.Item(DroneBuildScene.REMOTE, 1.5F, true), new ScenePos(-1.0F, 1.4F, 0.5F), 0.0F,
                            Cue.Appear.POP, 6.0F);
                    s.at(0.0F).spawn("block", new ActorSpec.Block(BlockSpec.of(DroneBuildScene.CHARGER).with("state", "done")),
                            new ScenePos(1.5F, 1.0F, 1.5F), 45.0F, Cue.Appear.DROP, 6.0F);
                    s.at(0.0F).spawn("pilot", new ActorSpec.Pilot(DroneBuildScene.GOGGLES, DroneBuildScene.REMOTE), new ScenePos(-1.0F, 1.0F, 1.5F),
                            200.0F, Cue.Appear.INSTANT, 0.0F);
                    s.at(0.0F).crouch("pilot", true);
                    s.at(0.0F).spawn("drone", new ActorSpec.Drone(DroneBuild.RACER, true), new ScenePos(0.5F, 2.0F, 0.5F), 30.0F,
                            Cue.Appear.INSTANT, 0.0F);
                    s.at(0.0F).arm("drone", true);
                    s.at(0.0F).throttle("drone", 0.5F, 10.0F);
                    s.at(10.0F).highlight(new ScenePos(-2.0F, 0.0F, -2.0F), new ScenePos(3.0F, 1.0F, 3.0F), 0.0F);
                    s.at(10.0F).keys("all", 0.0F, KeyToken.sneak(), KeyToken.use(), KeyToken.mouse(KeyToken.MOUSE_MIDDLE));
                })
                .build();
    }

    private static StepResult actorKindsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (phase(ctx) == 0) {
            state.put(RENDERED, SceneRenderer.renderedFrames());
            state.put(FAILED, SceneRenderer.failedDrawables());
            SceneScreen screen = new SceneScreen(actorShowcase(), null);
            mc.gui.setScreen(screen);
            screen.playback().seek(30.0F);
            screen.playback().pause();
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof SceneScreen screen)) {
            return StepResult.fail("Akteur-Szene nicht offen: " + mc.gui.screen());
        }
        if (SceneRenderer.failedDrawables() > (long) state.get(FAILED)) {
            screen.onClose();
            return StepResult.fail("Akteur mit Fehler gezeichnet (siehe Log)");
        }
        if (screen.frames() < 10 || SceneRenderer.renderedFrames() - (long) state.get(RENDERED) < 5) {
            return since(ctx) > 250 ? StepResult.fail("Akteur-Szene nicht gezeichnet") : StepResult.pending();
        }
        SceneState last = screen.lastState();
        boolean complete = last != null && last.visibleActors().size() == 5 && last.keyHints().size() == 1 && screen.lastKeyHints() == 1;
        screen.onClose();
        return StepResult.check(complete, "Akteur-Szene unvollständig");
    }

    private static StepResult allScenesStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        List<Scene> scenes = GuideScenes.all();
        if (phase(ctx) == 0) {
            state.put(FAILED, SceneRenderer.failedDrawables());
            state.put(SCENE, 0);
            state.put(SAMPLE, 0);
            state.put(CHECKED, 0);
            phase(ctx, 1);
        }
        int sceneIndex = (int) state.get(SCENE);
        if (sceneIndex >= scenes.size()) {
            mc.gui.setScreen(null);
            ctx.log(scenes.size() + " Szenen, " + state.get(CHECKED) + " Stellen geprüft");
            return StepResult.check(scenes.size() >= 10, "nur " + scenes.size() + " Szenen registriert");
        }
        Scene scene = scenes.get(sceneIndex);
        if (SceneRenderer.failedDrawables() > (long) state.get(FAILED)) {
            mc.gui.setScreen(null);
            return StepResult.fail(scene.id() + ": Szenen-Element mit Fehler gezeichnet (siehe Log)");
        }
        int sample = (int) state.get(SAMPLE);
        int total = scene.stepCount() * SAMPLE_POINTS.length;
        int phase = phase(ctx);
        if (phase == 1) {
            SceneScreen opened = new SceneScreen(scene, null);
            mc.gui.setScreen(opened);
            opened.playback().pause();
            phase(ctx, 2);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof SceneScreen screen) || screen.scene() != scene) {
            mc.gui.setScreen(null);
            return StepResult.fail(scene.id() + ": Szenen-Player nicht offen: " + mc.gui.screen());
        }
        switch (phase) {
            case 2 -> {
                if (sample >= total) {
                    screen.playback().seek(scene.duration());
                    phase(ctx, 4);
                    return StepResult.pending();
                }
                int step = sample / SAMPLE_POINTS.length;
                float t = scene.stepStart(step) + (scene.stepEnd(step) - scene.stepStart(step)) * SAMPLE_POINTS[sample % SAMPLE_POINTS.length];
                screen.playback().seek(t);
                screen.playback().pause();
                state.put(FRAMES, screen.frames());
                phase(ctx, 3);
            }
            case 3 -> {
                if (since(ctx) < 2 || screen.frames() < (long) state.get(FRAMES) + 2) {
                    if (since(ctx) > 200) {
                        mc.gui.setScreen(null);
                        return StepResult.fail(scene.id() + ": Szene wird nicht gezeichnet");
                    }
                    return StepResult.pending();
                }
                int step = sample / SAMPLE_POINTS.length;
                String problem = sampleProblem(screen, step);
                if (problem != null) {
                    mc.gui.setScreen(null);
                    return StepResult.fail(scene.id() + "/" + scene.steps().get(step).name() + " bei " + Math.round(screen.playback().time()) + ": "
                            + problem);
                }
                state.put(SAMPLE, sample + 1);
                state.put(CHECKED, (int) state.get(CHECKED) + 1);
                phase(ctx, 2);
            }
            case 4 -> {
                if (since(ctx) < 2) {
                    return StepResult.pending();
                }
                if (!screen.playback().finished() || screen.lastState() == null || screen.lastState().step() != scene.stepCount() - 1) {
                    mc.gui.setScreen(null);
                    return StepResult.fail(scene.id() + ": Ende der Szene nicht erreicht");
                }
                screen.onClose();
                state.put(SCENE, sceneIndex + 1);
                state.put(SAMPLE, 0);
                phase(ctx, 1);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String sampleProblem(SceneScreen screen, int step) {
        if (!screen.sceneSubmitted()) {
            return "3D-Szene nicht übergeben";
        }
        SceneState last = screen.lastState();
        SceneProjection projection = screen.lastProjection();
        if (last == null || projection == null) {
            return "kein Zustand";
        }
        if (last.step() != step) {
            return "falscher Schritt " + last.step();
        }
        int callouts = 0;
        for (SceneState.CalloutView callout : last.callouts()) {
            if (callout.alpha() > 0.02F) {
                callouts++;
            }
        }
        if (screen.lastCallouts() != callouts) {
            return screen.lastCallouts() + " von " + callouts + " Hinweisen gezeichnet";
        }
        int hints = 0;
        for (SceneState.KeyHintView hint : last.keyHints()) {
            if (hint.alpha() > 0.02F) {
                hints++;
            }
        }
        if (screen.lastKeyHints() != hints) {
            return screen.lastKeyHints() + " von " + hints + " Tastenhinweisen gezeichnet";
        }
        ScreenRectangle vp = screen.viewport();
        for (SceneState.CalloutView callout : last.callouts()) {
            if (callout.alpha() >= 0.5F && !inside(vp, projection.project(callout.anchor()))) {
                return "Hinweis außerhalb der Ansicht: " + callout.textKey();
            }
        }
        for (ActorState actor : last.visibleActors()) {
            if (actor.spec() instanceof ActorSpec.Drone && actor.presence() >= 0.99F && !inside(vp, projection.project(actor.position()))) {
                return "Drohne außerhalb der Ansicht: " + actor.id();
            }
        }
        List<SceneOverlay.Placed> placed = screen.lastPlaced();
        SceneFrame frame = screen.lastFrame();
        SceneFrame.Rendered rendered = frame != null ? frame.rendered() : null;
        Set<String> leaving = leavingKeys(last);
        for (int i = 0; i < placed.size(); i++) {
            SceneOverlay.Placed box = placed.get(i);
            if (rendered != null) {
                for (SceneState.CalloutView callout : last.callouts()) {
                    if (callout.textKey().equals(box.textKey())) {
                        float[] target = rendered.toGui(callout.anchor().x(), callout.anchor().y(), callout.anchor().z());
                        if (Math.abs(target[0] - box.anchorX()) > ANCHOR_TOLERANCE || Math.abs(target[1] - box.anchorY()) > ANCHOR_TOLERANCE) {
                            return "Anker " + box.textKey() + " zeigt neben sein Ziel im 3D-Bild";
                        }
                        break;
                    }
                }
            }
            if (box.owner() != null && box.ownerCover() > MAX_OWNER_COVER) {
                return "Hinweis verdeckt " + box.owner() + " zu " + Math.round(box.ownerCover() * 100.0F) + " %: " + box.textKey();
            }
            for (int j = i + 1; j < placed.size(); j++) {
                SceneOverlay.Placed other = placed.get(j);
                if (box.x0() < other.x1() && box.x1() > other.x0() && box.y0() < other.y1() && box.y1() > other.y0()
                        && !leaving.contains(box.textKey()) && !leaving.contains(other.textKey())) {
                    return "Hinweise überlappen: " + box.textKey() + " / " + other.textKey();
                }
            }
        }
        return null;
    }

    private static Set<String> leavingKeys(SceneState state) {
        Set<String> keys = new HashSet<>();
        for (SceneState.CalloutView callout : state.callouts()) {
            if (SceneOverlay.leaving(callout)) {
                keys.add(callout.textKey());
            }
        }
        return keys;
    }

    private static boolean inside(ScreenRectangle vp, float[] point) {
        return point[0] >= vp.left() && point[0] <= vp.right() && point[1] >= vp.top() && point[1] <= vp.bottom();
    }

    @SuppressWarnings("unchecked")
    private static StepResult anchorFitStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        Map<String, Object> state = ctx.state();
        int phase = phase(ctx);
        if (phase == 0) {
            state.put(WINDOW, new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()});
            state.put(CONFIG, 0);
            mc.gui.setScreen(null);
            Screenshots.hideMarker(Screenshots.enabled());
            phase(ctx, 1);
            return StepResult.pending();
        }
        int index = (int) state.get(CONFIG);
        if (index >= FITS.size()) {
            return restoreFitWindow(mc, state, null);
        }
        Fit fit = FITS.get(index);
        switch (phase) {
            case 1 -> {
                mc.gui.setScreen(null);
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(fit.width() / density), Math.round(fit.height() / density));
                phase(ctx, 2);
            }
            case 2 -> {
                boolean sized = Math.abs(window.getWidth() - fit.width()) <= 2 && Math.abs(window.getHeight() - fit.height()) <= 2;
                if (!sized && since(ctx) < 40) {
                    return StepResult.pending();
                }
                mc.options.guiScale().set(fit.guiScale());
                mc.resizeGui();
                Scene scene = GuideScenes.byId(fit.scene());
                if (scene == null) {
                    return restoreFitWindow(mc, state, "Szene fehlt: " + fit.scene());
                }
                SceneScreen screen = new SceneScreen(scene, null);
                mc.gui.setScreen(screen);
                screen.playback().seek(scene.stepStart(fit.step()) + fit.offset());
                screen.playback().pause();
                screen.snapView(fit.yaw(), fit.pitch(), fit.zoom());
                Screenshots.clearOverlays(mc);
                ctx.log("Fenster " + window.getWidth() + "x" + window.getHeight() + " (Dichte " + window.getPixelDensity() + ", GUI-Skala "
                        + window.getGuiScale() + ", GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + ") für " + fit.file());
                phase(ctx, 3);
            }
            case 3 -> {
                if (!(mc.gui.screen() instanceof SceneScreen screen)) {
                    return restoreFitWindow(mc, state, "Szenen-Player nicht offen: " + mc.gui.screen());
                }
                mc.mouseHandler.onMove(window.handle(), 1.0, 1.0, 0.0, 0.0);
                SceneFrame frame = screen.lastFrame();
                if (since(ctx) < 12 || frame == null || frame.rendered() == null) {
                    return since(ctx) > 200 ? restoreFitWindow(mc, state, fit.file() + ": 3D-Szene nicht gezeichnet") : StepResult.pending();
                }
                String problem = fitProblem(screen, frame, window.getGuiScale());
                if (problem != null) {
                    return restoreFitWindow(mc, state, fit.file() + " (" + window.getWidth() + "x" + window.getHeight() + "): " + problem);
                }
                ctx.log(fit.file() + ": " + screen.lastPlaced().size() + " Anker passen");
                if (Screenshots.enabled()) {
                    state.put(FUTURE, Screenshots.capture(fit.file()));
                    phase(ctx, 4);
                } else {
                    state.put(CONFIG, index + 1);
                    phase(ctx, 1);
                }
            }
            case 4 -> {
                CompletableFuture<String> future = (CompletableFuture<String>) state.get(FUTURE);
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                if (future.isCompletedExceptionally()) {
                    return restoreFitWindow(mc, state, "Bildschirmfoto " + fit.file() + ": " + future.exceptionNow());
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(fit.file() + ".png") + " (" + future.join() + ")");
                state.put(CONFIG, index + 1);
                phase(ctx, 1);
            }
            default -> {
                return restoreFitWindow(mc, state, "unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult labelPlayStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        List<Scene> scenes = GuideScenes.registered();
        int phase = phase(ctx);
        if (phase == 0) {
            mc.gui.setScreen(null);
            Screenshots.hideMarker(Screenshots.enabled());
            state.put(SCENE, 0);
            state.put(CHECKED, 0);
            phase(ctx, 1);
            return StepResult.pending();
        }
        int index = (int) state.get(SCENE);
        if (index >= scenes.size()) {
            mc.gui.setScreen(null);
            Screenshots.hideMarker(false);
            ctx.log(scenes.size() + " Lektionen abgespielt, " + state.get(CHECKED) + " Bilder geprüft");
            return StepResult.check(scenes.size() >= 10, "nur " + scenes.size() + " Lektionen registriert");
        }
        Scene scene = scenes.get(index);
        if (phase == 1) {
            SceneScreen opened = new SceneScreen(scene, null);
            mc.gui.setScreen(opened);
            opened.playback().speed(PLAY_SPEED);
            state.put(NEXT, 0);
            phase(ctx, 2);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof SceneScreen screen) || screen.scene() != scene) {
            Screenshots.hideMarker(false);
            mc.gui.setScreen(null);
            return StepResult.fail(scene.id() + ": Szenen-Player nicht offen: " + mc.gui.screen());
        }
        switch (phase) {
            case 2 -> {
                if (since(ctx) < 2 || screen.lastState() == null) {
                    return StepResult.pending();
                }
                int tick = ctx.tick();
                screen.turnView(TURN_YAW, 0.3F * (float) Math.sin(tick * 0.05F), (float) Math.exp(0.012F * Math.sin(tick * 0.04F)));
                String problem = labelProblem(screen);
                if (problem != null) {
                    return closeLabels(mc, scene.id() + " bei " + Math.round(screen.playback().time()) + ": " + problem);
                }
                state.put(CHECKED, (int) state.get(CHECKED) + 1);
                int next = (int) state.get(NEXT);
                if (screen.playback().finished()) {
                    phase(ctx, 6);
                } else if (Screenshots.enabled() && next < PLAY_SHOTS.length && screen.playback().time() >= scene.duration() * PLAY_SHOTS[next]) {
                    screen.playback().pause();
                    state.put(NEXT, next + 1);
                    state.put(NAME, LABEL_SHOTS + scene.id().replace(':', '_') + "_" + (next + 1));
                    phase(ctx, 3);
                }
            }
            case 3 -> {
                Screenshots.clearOverlays(mc);
                if (since(ctx) >= SHOT_SETTLE_TICKS) {
                    state.put(FUTURE, Screenshots.capture((String) state.get(NAME)));
                    phase(ctx, 4);
                }
            }
            case 4 -> {
                CompletableFuture<String> future = (CompletableFuture<String>) state.get(FUTURE);
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                if (future.isCompletedExceptionally()) {
                    return closeLabels(mc, "Bildschirmfoto " + state.get(NAME) + ": " + future.exceptionNow());
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(state.get(NAME) + ".png") + " (" + future.join() + ")");
                if (screen.playback().finished()) {
                    phase(ctx, 5);
                } else {
                    screen.playback().play();
                    phase(ctx, 2);
                }
            }
            case 6 -> {
                if (since(ctx) < END_SETTLE_TICKS) {
                    return StepResult.pending();
                }
                String end = endProblem(screen, scene);
                if (end != null) {
                    return closeLabels(mc, scene.id() + " am Ende: " + end);
                }
                state.put(NAME, LABEL_SHOTS + scene.id().replace(':', '_') + "_end");
                phase(ctx, Screenshots.enabled() ? 3 : 5);
            }
            case 5 -> {
                CalloutLayout.Stats stats = screen.calloutStats();
                float fastest = stats.fastest() / screen.textScale();
                ctx.log(String.format(Locale.ROOT, "%s: %d Hinweise gesetzt, %d Umzüge, schnellstens %.1f px/Tick, %d Bilder", scene.id(),
                        stats.placements(), stats.moves(), fastest, stats.frames()));
                if (stats.moves() > 2L * stats.placements() + 6L) {
                    return closeLabels(mc, scene.id() + ": zu viele Umzüge (" + stats.moves() + " bei " + stats.placements() + " Hinweisen)");
                }
                if (fastest > MAX_GLIDE_SPEED) {
                    return closeLabels(mc, scene.id() + ": Hinweis springt mit " + fastest + " px/Tick");
                }
                screen.onClose();
                state.put(SCENE, index + 1);
                phase(ctx, 1);
            }
            default -> {
                return closeLabels(mc, "unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static String viewKey(Minecraft mc, SceneScreen screen) {
        ScreenRectangle vp = screen.viewport();
        return "GUI " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + ", Szene " + vp.width() + "x" + vp.height()
                + " bei " + vp.left() + "/" + vp.top();
    }

    private static StepResult closeLabels(Minecraft mc, String failure) {
        mc.gui.setScreen(null);
        Screenshots.hideMarker(false);
        return StepResult.fail(failure);
    }

    private static @Nullable String labelProblem(SceneScreen screen) {
        SceneState last = screen.lastState();
        SceneProjection projection = screen.lastProjection();
        if (last == null || projection == null) {
            return null;
        }
        ScreenRectangle vp = screen.viewport();
        LabelRect area = SceneOverlay.labelArea(vp);
        Map<String, SceneState.CalloutView> byId = new HashMap<>();
        for (SceneState.CalloutView callout : last.callouts()) {
            byId.put(callout.layoutId(), callout);
        }
        List<SceneOverlay.Placed> placed = screen.lastPlaced();
        for (int i = 0; i < placed.size(); i++) {
            SceneOverlay.Placed box = placed.get(i);
            SceneState.CalloutView callout = byId.get(box.id());
            if (callout == null) {
                return "Hinweis ohne Zustand: " + box.textKey();
            }
            float[] anchor = projection.project(callout.anchor());
            if (Math.abs(anchor[0] - box.anchorX()) > 0.01F || Math.abs(anchor[1] - box.anchorY()) > 0.01F) {
                return "Zeigelinie folgt nicht dem Punkt: " + box.textKey();
            }
            LabelRect target = box.target();
            boolean fits = target.width() <= area.width() && target.height() <= area.height();
            if (fits && !target.within(area, 0.5F)) {
                return "Hinweis ragt aus der Ansicht: " + box.textKey();
            }
            if (inside(vp, anchor) && target.contains(anchor[0], anchor[1], 0.0F)) {
                return "Hinweis verdeckt seinen Punkt: " + box.textKey();
            }
            LabelRect drawn = new LabelRect(box.x0(), box.y0(), box.x1(), box.y1());
            float slideX = box.x0() - box.layoutBox().x0();
            float slideY = box.y0() - box.layoutBox().y0();
            if (box.gliding() && box.fade() >= 0.999F && inside(vp, anchor) && drawn.contains(anchor[0], anchor[1], 0.0F)
                    && !sameSpot(drawn, shifted(box.from(), slideX, slideY)) && !sameSpot(drawn, shifted(target, slideX, slideY))) {
                return "Hinweis gleitet über seinen Punkt: " + box.textKey();
            }
            for (SceneOverlay.HintBox hint : screen.lastHints()) {
                if (target.overlaps(hint.rect(), 0.0F)) {
                    return "Hinweis liegt auf einem Tastenhinweis: " + box.textKey();
                }
            }
            for (int j = i + 1; j < placed.size(); j++) {
                SceneState.CalloutView otherCallout = byId.get(placed.get(j).id());
                boolean eitherLeaving = SceneOverlay.leaving(callout) || otherCallout != null && SceneOverlay.leaving(otherCallout);
                if (!eitherLeaving && target.overlaps(placed.get(j).target(), 0.0F)) {
                    return "Hinweise überlappen: " + box.textKey() + " / " + placed.get(j).textKey();
                }
            }
        }
        return null;
    }

    private static LabelRect shifted(LabelRect rect, float dx, float dy) {
        return new LabelRect(rect.x0() + dx, rect.y0() + dy, rect.x1() + dx, rect.y1() + dy);
    }

    private static boolean sameSpot(LabelRect a, LabelRect b) {
        return Math.abs(a.x0() - b.x0()) <= 1.5F && Math.abs(a.y0() - b.y0()) <= 1.5F;
    }

    private static @Nullable String endProblem(SceneScreen screen, Scene scene) {
        SceneState last = screen.lastState();
        if (last == null) {
            return "kein Zustand";
        }
        ScreenRectangle vp = screen.viewport();
        float k = Math.max(1, screen.textScale());
        for (SceneOverlay.Placed box : screen.lastPlaced()) {
            if (box.gliding()) {
                continue;
            }
            float own = box.target().distance(box.anchorX(), box.anchorY());
            for (SceneOverlay.Placed other : screen.lastPlaced()) {
                if (other != box && inside(vp, new float[]{other.anchorX(), other.anchorY()})
                        && box.target().distance(other.anchorX(), other.anchorY()) + AMBIGUOUS_PX * k < own) {
                    return "Hinweis steht näher am Punkt eines anderen: " + box.textKey() + " / " + other.textKey();
                }
            }
        }
        List<SceneState.CalloutView> shown = last.callouts().stream().filter(c -> c.alpha() > 0.02F).toList();
        for (SceneState.CalloutView callout : shown) {
            if (callout.age() > SceneEvaluator.FADE_TICKS && callout.alpha() < 1.0F) {
                return "Hinweis blendet am Ende aus: " + callout.textKey();
            }
        }
        for (int a = 0; a < shown.size(); a++) {
            Cue.Callout first = calloutCue(scene, shown.get(a).cue());
            for (int b = a + 1; b < shown.size(); b++) {
                Cue.Callout second = calloutCue(scene, shown.get(b).cue());
                if (first != null && second != null && (Scene.supersedes(first, second) || Scene.supersedes(second, first))) {
                    return "zwei Hinweise für dieselbe Stelle: " + first.textKey() + " / " + second.textKey();
                }
            }
        }
        return null;
    }

    private static Cue.@Nullable Callout calloutCue(Scene scene, int id) {
        for (int i = 0; i < scene.stepCount(); i++) {
            List<TimedCue> cues = scene.steps().get(i).cues();
            for (int c = 0; c < cues.size(); c++) {
                if (scene.cueId(i, c) == id && cues.get(c).cue() instanceof Cue.Callout callout) {
                    return callout;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static StepResult labelTurnStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = phase(ctx);
        Scene scene = GuideScenes.byId(SurvivalScene.ID);
        if (scene == null) {
            return StepResult.fail("Szene fehlt: " + SurvivalScene.ID);
        }
        if (phase == 0) {
            mc.gui.setScreen(null);
            Screenshots.hideMarker(Screenshots.enabled());
            SceneScreen screen = new SceneScreen(scene, null);
            mc.gui.setScreen(screen);
            screen.playback().seek(scene.stepStart(1) + 70.0F);
            screen.playback().pause();
            screen.snapView(0.0F, 0.0F, 1.0F);
            Screenshots.clearOverlays(mc);
            state.put(TICKS, 0);
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof SceneScreen screen)) {
            Screenshots.hideMarker(false);
            return StepResult.fail("Szenen-Player nicht offen: " + mc.gui.screen());
        }
        int turn = (int) state.get(TICKS);
        switch (phase) {
            case 1 -> {
                mc.mouseHandler.onMove(mc.getWindow().handle(), 1.0, 1.0, 0.0, 0.0);
                if (since(ctx) < SETTLE_TICKS || screen.lastPlaced().isEmpty()) {
                    return since(ctx) > 300 ? closeLabels(mc, "keine Hinweise gezeichnet") : StepResult.pending();
                }
                List<SceneOverlay.Placed> placed = screen.lastPlaced();
                if (placed == state.get(FRAME)) {
                    return since(ctx) > 600 ? closeLabels(mc, "Ansicht kommt nach dem Drehen nicht zur Ruhe") : StepResult.pending();
                }
                state.put(FRAME, placed);
                Map<String, LabelRect> targets = new HashMap<>();
                Map<String, float[]> anchors = new HashMap<>();
                for (SceneOverlay.Placed box : placed) {
                    targets.put(box.id(), box.target());
                    anchors.put(box.id(), new float[]{box.anchorX(), box.anchorY()});
                }
                Map<String, float[]> settling = (Map<String, float[]>) state.get(SETTLING);
                boolean still = settling != null && settling.keySet().equals(anchors.keySet());
                if (still) {
                    for (Map.Entry<String, float[]> entry : anchors.entrySet()) {
                        float[] old = settling.get(entry.getKey());
                        still &= Math.abs(entry.getValue()[0] - old[0]) + Math.abs(entry.getValue()[1] - old[1]) < 0.5F;
                    }
                }
                state.put(SETTLING, anchors);
                int stillFrames = still ? (int) state.getOrDefault(STILL, 0) + 1 : 0;
                state.put(STILL, stillFrames);
                if (stillFrames < STILL_FRAMES) {
                    return since(ctx) > 600 ? closeLabels(mc, "Ansicht kommt nach dem Drehen nicht zur Ruhe") : StepResult.pending();
                }
                state.remove(SETTLING);
                state.remove(STILL);
                state.remove(FRAME);
                String view = viewKey(mc, screen);
                if (turn == 0) {
                    state.put(TARGETS, targets);
                    state.put(ANCHORS, anchors);
                    state.put(VIEW, view);
                    ctx.log("Ansicht: " + view);
                } else {
                    if (!view.equals(state.get(VIEW))) {
                        int restarts = (int) state.getOrDefault(RESTARTS, 0) + 1;
                        if (restarts > MAX_VIEW_RESTARTS) {
                            return closeLabels(mc, "Fenster kommt während der Prüfung nicht zur Ruhe: " + state.get(VIEW) + " -> " + view);
                        }
                        ctx.log("Fenster hat sich während der Prüfung geändert (" + state.get(VIEW) + " -> " + view + "), Prüfung beginnt neu");
                        state.put(RESTARTS, restarts);
                        state.remove(TARGETS);
                        state.remove(ANCHORS);
                        state.remove(VIEW);
                        phase(ctx, 0);
                        return StepResult.pending();
                    }
                    Map<String, LabelRect> before = (Map<String, LabelRect>) state.get(TARGETS);
                    Map<String, float[]> anchorsBefore = (Map<String, float[]>) state.get(ANCHORS);
                    if (!before.equals(targets)) {
                        return closeLabels(mc, "Hinweis ist beim Drehen umgezogen: " + before.values() + " -> " + targets.values() + " (" + view
                                + ", Szenenzeit " + screen.playback().time() + (screen.playback().playing() ? ", läuft" : ", pausiert") + ")");
                    }
                    float moved = 0.0F;
                    for (Map.Entry<String, float[]> entry : anchors.entrySet()) {
                        float[] old = anchorsBefore.get(entry.getKey());
                        moved = Math.max(moved, Math.abs(entry.getValue()[0] - old[0]) + Math.abs(entry.getValue()[1] - old[1]));
                    }
                    if (moved < 3.0F) {
                        return closeLabels(mc, "Ansicht hat sich nicht gedreht (Anker " + moved + " px)");
                    }
                    ctx.log("Drehung " + turn + ": Kasten steht, Anker um " + Math.round(moved) + " px gewandert");
                    state.put(ANCHORS, anchors);
                }
                String problem = labelProblem(screen);
                if (problem != null) {
                    return closeLabels(mc, problem);
                }
                if (Screenshots.enabled()) {
                    state.put(NAME, "flight_school_labels_turn_" + turn);
                    state.put(FUTURE, Screenshots.capture((String) state.get(NAME)));
                }
                phase(ctx, 2);
            }
            case 2 -> {
                if (Screenshots.enabled()) {
                    CompletableFuture<String> future = (CompletableFuture<String>) state.get(FUTURE);
                    if (!future.isDone()) {
                        return StepResult.pending();
                    }
                    if (future.isCompletedExceptionally()) {
                        return closeLabels(mc, "Bildschirmfoto " + state.get(NAME) + ": " + future.exceptionNow());
                    }
                    ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(state.get(NAME) + ".png") + " (" + future.join() + ")");
                }
                if (turn >= 2) {
                    long moves = screen.calloutStats().moves();
                    mc.gui.setScreen(null);
                    Screenshots.hideMarker(false);
                    return StepResult.check(moves == 0L, moves + " Umzüge beim Drehen");
                }
                if (turn == 0) {
                    screen.turnView(14.0F, 0.0F, 1.0F);
                } else {
                    screen.turnView(-6.0F, 7.0F, 1.15F);
                }
                state.put(TICKS, turn + 1);
                phase(ctx, 1);
            }
            default -> {
                return closeLabels(mc, "unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String fitProblem(SceneScreen screen, SceneFrame frame, int guiScale) {
        SceneFrame.Rendered rendered = frame.rendered();
        SceneState last = screen.lastState();
        ScreenRectangle vp = screen.viewport();
        if (rendered == null || last == null) {
            return "kein gezeichnetes Bild";
        }
        if (rendered.pixelScale() != guiScale) {
            return "Bild mit Skala " + rendered.pixelScale() + " statt GUI-Skala " + guiScale + " gezeichnet";
        }
        if (Math.abs(rendered.textureWidth() - vp.width() * guiScale) > 0.5F || Math.abs(rendered.textureHeight() - vp.height() * guiScale) > 0.5F) {
            return "Bildgröße " + rendered.textureWidth() + "x" + rendered.textureHeight() + " passt nicht zur Ansicht " + vp.width() + "x"
                    + vp.height();
        }
        if (rendered.left() != vp.left() || rendered.top() != vp.top()) {
            return "Bild an " + rendered.left() + "/" + rendered.top() + " statt " + vp.left() + "/" + vp.top();
        }
        List<SceneOverlay.Placed> placed = screen.lastPlaced();
        if (placed.isEmpty()) {
            return "keine Hinweise gezeichnet";
        }
        Set<String> leaving = leavingKeys(last);
        for (int i = 0; i < placed.size(); i++) {
            SceneOverlay.Placed box = placed.get(i);
            ScenePos anchor = null;
            for (SceneState.CalloutView callout : last.callouts()) {
                if (callout.textKey().equals(box.textKey())) {
                    anchor = callout.anchor();
                    break;
                }
            }
            if (anchor == null) {
                return "Anker fehlt: " + box.textKey();
            }
            float[] target = rendered.toGui(anchor.x(), anchor.y(), anchor.z());
            float dx = target[0] - box.anchorX();
            float dy = target[1] - box.anchorY();
            if (Math.abs(dx) > ANCHOR_TOLERANCE || Math.abs(dy) > ANCHOR_TOLERANCE) {
                return "Anker " + box.textKey() + " bei " + box.anchorX() + "/" + box.anchorY() + ", Ziel im 3D-Bild bei " + target[0] + "/" + target[1];
            }
            if (box.x0() < vp.left() || box.y0() < vp.top() || box.x1() > vp.right() || box.y1() > vp.bottom()) {
                return "Hinweis ragt aus der Ansicht: " + box.textKey();
            }
            for (int j = i + 1; j < placed.size(); j++) {
                SceneOverlay.Placed other = placed.get(j);
                if (box.x0() < other.x1() && box.x1() > other.x0() && box.y0() < other.y1() && box.y1() > other.y0()
                        && !leaving.contains(box.textKey()) && !leaving.contains(other.textKey())) {
                    return "Hinweise überlappen: " + box.textKey() + " / " + other.textKey();
                }
            }
        }
        return null;
    }

    private static StepResult restoreFitWindow(Minecraft mc, Map<String, Object> state, @Nullable String failure) {
        mc.gui.setScreen(null);
        Screenshots.hideMarker(false);
        int[] saved = (int[]) state.get(WINDOW);
        if (saved != null) {
            mc.options.guiScale().set(saved[2]);
            mc.getWindow().setWindowed(saved[0], saved[1]);
            mc.resizeGui();
        }
        return failure == null ? StepResult.pass() : StepResult.fail(failure);
    }

    private static StepResult indexStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (phase(ctx) == 0) {
            mc.gui.setScreen(new SceneIndexScreen(null, SceneAssets.item(DroneBuildScene.WORKBENCH)));
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof SceneIndexScreen screen)) {
            return StepResult.fail("Übersicht nicht offen: " + mc.gui.screen());
        }
        if (screen.frames() < 5) {
            return StepResult.pending();
        }
        boolean found = screen.shownScenes().stream().anyMatch(scene -> scene.id().equals(DroneBuildScene.ID));
        boolean hudHidden = GuideClient.flightSchoolOpen();
        screen.onClose();
        if (!hudHidden || GuideClient.flightSchoolOpen()) {
            return StepResult.fail("Vanilla-HUD wird unter der Flugschule nicht ausgeblendet oder bleibt danach aus");
        }
        return StepResult.check(found && mc.gui.screen() == null, "Werkbank-Szene fehlt in der gefilterten Übersicht");
    }

    private static StepResult holdStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (phase(ctx) == 0) {
            if (mc.player == null) {
                return StepResult.fail("kein Spieler");
            }
            state.put(HINTS, HoldToOpen.hintFrames());
            HoldToOpen.testOverride(SceneAssets.item(DroneBuildScene.WORKBENCH), true);
            mc.gui.setScreen(new InventoryScreen(mc.player));
            phase(ctx, 1);
            return StepResult.pending();
        }
        List<Scene> expected = GuideOpener.scenesFor(SceneAssets.item(DroneBuildScene.WORKBENCH));
        if (mc.gui.screen() instanceof SceneScreen screen) {
            HoldToOpen.testOverride(null, null);
            boolean right = expected.size() == 1 && screen.scene().id().equals(expected.getFirst().id())
                    && screen.parent() instanceof AbstractContainerScreen<?>;
            boolean hinted = HoldToOpen.hintFrames() > (long) state.get(HINTS);
            screen.onClose();
            boolean back = mc.gui.screen() instanceof AbstractContainerScreen<?>;
            mc.gui.setScreen(null);
            return StepResult.check(right && hinted && back, "Halten öffnet falsch (Szene/Eltern " + right + ", Hinweis " + hinted + ", zurück " + back + ")");
        }
        if (mc.gui.screen() instanceof SceneIndexScreen index) {
            HoldToOpen.testOverride(null, null);
            boolean right = expected.size() > 1 && index.shownScenes().equals(expected);
            boolean hinted = HoldToOpen.hintFrames() > (long) state.get(HINTS);
            index.onClose();
            boolean back = mc.gui.screen() instanceof AbstractContainerScreen<?>;
            mc.gui.setScreen(null);
            return StepResult.check(right && hinted && back, "Halten öffnet falsche Übersicht (Szenen " + index.shownScenes().size() + "/"
                    + expected.size() + ", Hinweis " + hinted + ", zurück " + back + ")");
        }
        if (since(ctx) > 300) {
            HoldToOpen.testOverride(null, null);
            mc.gui.setScreen(null);
            return StepResult.fail("Halten öffnet keine Szene (Inventar-Bilder " + HoldToOpen.frames() + ")");
        }
        return StepResult.pending();
    }

    private static StepResult noWorldStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = phase(ctx);
        if (phase == 0) {
            state.put(RENDERED, SceneRenderer.renderedFrames());
            state.put(FAILED, SceneRenderer.failedDrawables());
            mc.gui.setScreen(new SceneScreen(demo(), mc.gui.screen()));
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof SceneScreen screen)) {
            return StepResult.fail("Szenen-Player ohne Welt nicht offen: " + mc.gui.screen());
        }
        if (SceneRenderer.failedDrawables() > (long) state.get(FAILED)) {
            return StepResult.fail("Szenen-Elemente ohne Welt mit Fehler");
        }
        if (phase == 1) {
            if (screen.frames() < 10 || SceneRenderer.renderedFrames() - (long) state.get(RENDERED) < 5) {
                return since(ctx) > 300 ? StepResult.fail("3D-Szene ohne Welt nicht gezeichnet") : StepResult.pending();
            }
            screen.playback().seek(screen.scene().duration());
            phase(ctx, 2);
            return StepResult.pending();
        }
        if (since(ctx) < 4) {
            return StepResult.pending();
        }
        screen.onClose();
        return StepResult.check(!(mc.gui.screen() instanceof SceneScreen), "Szenen-Player ohne Welt schließt nicht");
    }

    @SuppressWarnings("unchecked")
    private static StepResult screenshotStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = phase(ctx);
        if (phase == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            state.put(SHOT, 0);
            phase(ctx, 1);
            return StepResult.pending();
        }
        int index = (int) state.get(SHOT);
        if (index >= SHOTS.size()) {
            mc.gui.setScreen(null);
            Screenshots.hideMarker(false);
            return Screenshots.restoreWindow(ctx);
        }
        Shot shot = SHOTS.get(index);
        Scene scene = GuideScenes.byId(shot.scene());
        if (scene == null) {
            return StepResult.fail("Szene für Bildschirmfoto fehlt: " + shot.scene());
        }
        switch (phase) {
            case 1 -> {
                mc.gui.setScreen(shot.index() ? new SceneIndexScreen(null, ItemStack.EMPTY) : new SceneScreen(scene, null));
                Screenshots.clearOverlays(mc);
                phase(ctx, 2);
            }
            case 2 -> {
                mc.mouseHandler.onMove(mc.getWindow().handle(), 1.0, 1.0, 0.0, 0.0);
                if (mc.gui.screen() instanceof SceneScreen screen) {
                    screen.playback().seek(scene.stepStart(shot.step()) + shot.offset());
                    screen.playback().pause();
                }
                if (since(ctx) >= 30) {
                    state.put(FUTURE, Screenshots.capture(shot.file()));
                    phase(ctx, 3);
                }
            }
            case 3 -> {
                CompletableFuture<String> future = (CompletableFuture<String>) state.get(FUTURE);
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Bildschirmfoto " + shot.file() + ": " + future.exceptionNow());
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(shot.file() + ".png") + " (" + future.join() + ")");
                state.put(SHOT, index + 1);
                phase(ctx, 1);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    public static Screenshots.Scene sceneShot(String sceneId, int step, float offset) {
        return new Screenshots.Scene() {
            @Override
            public StepResult prepare(SelfTest.StepContext ctx) {
                Minecraft mc = Minecraft.getInstance();
                Scene scene = GuideScenes.byId(sceneId);
                if (scene == null) {
                    return StepResult.fail("Szene für Bildschirmfoto fehlt: " + sceneId);
                }
                if (!(mc.gui.screen() instanceof SceneScreen)) {
                    mc.gui.setScreen(new SceneScreen(scene, null));
                    return StepResult.pending();
                }
                return StepResult.pass();
            }

            @Override
            public void hold(SelfTest.StepContext ctx) {
                Minecraft mc = Minecraft.getInstance();
                Scene scene = GuideScenes.byId(sceneId);
                mc.mouseHandler.onMove(mc.getWindow().handle(), 1.0, 1.0, 0.0, 0.0);
                if (scene != null && mc.gui.screen() instanceof SceneScreen screen) {
                    screen.playback().seek(scene.stepStart(step) + offset);
                    screen.playback().pause();
                }
            }

            @Override
            public void finish(SelfTest.StepContext ctx) {
                Minecraft.getInstance().gui.setScreen(null);
                Screenshots.parkMouse(Minecraft.getInstance());
            }
        };
    }

    private static final class StubHost implements ConfigTabHost {
        private final Screen screen = new SceneIndexScreen(null, ItemStack.EMPTY);

        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
            return widget;
        }

        @Override
        public Font font() {
            return Minecraft.getInstance().font;
        }

        @Override
        public Minecraft minecraft() {
            return Minecraft.getInstance();
        }

        @Override
        public Screen screen() {
            return this.screen;
        }

        @Override
        public void rebuild() {
        }

        @Override
        public void changed() {
        }
    }
}

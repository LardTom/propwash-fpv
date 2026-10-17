package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.apiimpl.client.ApiShowcase;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.blackbox.client.BlackboxShowcase;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PaintSwatch;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.compat.iris.client.IrisShowcase;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryChargerBlock;
import de.lardtom.propwash.content.BatteryChargerBlockEntity;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentMaterials;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.CraftingMaterial;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.content.client.BatteryChargerScreen;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.content.client.PartFreedomSelfTests;
import de.lardtom.propwash.content.client.PropRepairShowcase;
import de.lardtom.propwash.content.client.WorkbenchFitSelfTest;
import de.lardtom.propwash.content.client.paint.PaintScreen;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.creative.client.CreativeShowcase;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.drone.bind.client.BindSelfTests;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.DroneServiceScreen;
import de.lardtom.propwash.drone.client.trail.TrailShowcase;
import de.lardtom.propwash.drone.client.Mode3dShowcase;
import de.lardtom.propwash.drone.client.WaterproofTurtleShowcase;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.WearWeightSelfTests;
import de.lardtom.propwash.drone.client.dust.DustConfig;
import de.lardtom.propwash.drone.client.dust.DustEffects;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.drone.dust.DustModel;
import de.lardtom.propwash.drone.led.DroneLeds;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.drone.led.LedMode;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.DeviceInfo;
import de.lardtom.propwash.input.client.MacControllerSelfTests;
import de.lardtom.propwash.guide.client.GuideSelfTests;
import de.lardtom.propwash.guide.client.TrainingShowcase;
import de.lardtom.propwash.guide.scenes.BatteryScene;
import de.lardtom.propwash.input.client.MacControllers;
import de.lardtom.propwash.input.client.ThrottleWizardSelfTests;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.MouseSelfTests;
import de.lardtom.propwash.input.client.SwitchCommandSelfTests;
import de.lardtom.propwash.input.client.StickModeSelfTests;
import de.lardtom.propwash.input.client.WizardRemoteSelfTests;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.client.BatterySagSelfTests;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.osd.client.StickOverlaySelfTests;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GhostLook;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.AssistCheckSelfTests;
import de.lardtom.propwash.race.client.AssistedLapSelfTests;
import de.lardtom.propwash.race.client.ClientCourses;
import de.lardtom.propwash.race.client.ClientLeaderboards;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.race.client.CourseEditSelfTests;
import de.lardtom.propwash.race.client.CourseElementSelfTests;
import de.lardtom.propwash.race.client.FreeformGateSelfTests;
import de.lardtom.propwash.race.client.GateRedstoneSelfTests;
import de.lardtom.propwash.race.client.GateFreeSelfTests;
import de.lardtom.propwash.race.client.GateKeySelfTests;
import de.lardtom.propwash.race.client.GateRenderer;
import de.lardtom.propwash.race.client.GateMountSelfTests;
import de.lardtom.propwash.race.client.GatePoleSelfTests;
import de.lardtom.propwash.race.client.GateStackSelfTests;
import de.lardtom.propwash.race.client.LeaderboardScreen;
import de.lardtom.propwash.race.client.RaceHud;
import de.lardtom.propwash.race.client.RaceLineRenderer;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.relay.client.RelayShowcase;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StickMath;
import de.lardtom.propwash.trick.TrickScore;
import de.lardtom.propwash.trick.client.ProTrickShowcase;
import de.lardtom.propwash.trick.client.TrickTracker;
import de.lardtom.propwash.ui.client.ArmAngleSelfTests;
import de.lardtom.propwash.ui.client.ConfigInputSelfTests;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.HoverSweepSelfTests;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.video.AnalogBreakup;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.SpectateServer;
import de.lardtom.propwash.video.client.AspectShowcase;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.Headlight;
import de.lardtom.propwash.video.client.HeadlightShowcase;
import de.lardtom.propwash.video.client.ShadowSelfTests;
import de.lardtom.propwash.video.client.VtxPowerShowcase;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.client.VideoBreakupProbe;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.FfmpegShowcase;
import de.lardtom.propwash.video.client.record.FlashbackCamShowcase;
import de.lardtom.propwash.video.client.record.RecordingThumbnails;
import de.lardtom.propwash.video.client.record.RecordingsScreen;
import de.lardtom.propwash.video.client.VideoTabSelfTests;
import de.lardtom.propwash.server.client.GenerationEdgeSelfTests;
import de.lardtom.propwash.server.client.WorldRulesSelfTests;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.zone.client.ZoneScenes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

public final class ShowcaseTour {
    private static final int OFFSET = 48;
    private static final int GATE_COUNT = 4;
    private static final int[] GATE_X = {0, 3, 1, -3};
    private static final int GATE_SPACING = 9;
    private static final int GATE_START = 8;
    private static final float HOVER_HEIGHT = 2.2F;
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final List<CaveBlock> CAVE = new ArrayList<>();
    private static final int CAVE_WIDTH = 11;
    private static final int CAVE_HEIGHT = 8;
    private static final int CAVE_LENGTH = 30;
    private static final List<Integer> GATES = new ArrayList<>();
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String TABS = "tabs";
    private static final String INDEX = "index";
    private static final String FRAMES = "frames";
    private static final String SHOT = "shot";
    private static final String DONE = "done";
    private static final String LAST_Y = "last_y";
    private static final String INTEGRAL = "integral";
    private static final String TARGET_Y = "target_y";
    private static CameraType savedCamera;
    private static int savedFov = -1;
    private static @Nullable DroneRenderConfig savedRenderConfig;

    private ShowcaseTour() {
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep("bildschirmfotos: Fenster und Szene vorbereiten", 400, ShowcaseTour::prepareStep);
        SelfTest.clientStep("bildschirmfotos: Konfigurator-Tabs", 2400, ShowcaseTour::configuratorStep);
        SelfTest.setupStep("bildschirmfotos: Props im FPV-Bild für die bisherigen FPV-Bilder aus", 1, ShowcaseTour::fpvPropsOffStep);
        Screenshots.step("bildschirmfotos: Werkbank mit FREESTYLE", "workbench_freestyle", 50, new WorkbenchScene());
        Screenshots.step("bildschirmfotos: Werkbank mit WHOOP", "workbench_whoop", 50, new WorkbenchScene(DroneBuild.WHOOP, "Whoop 65"));
        Screenshots.step("bildschirmfotos: Werkbank mit CINEWHOOP", "workbench_cinewhoop", 50,
                new WorkbenchScene(DroneBuild.CINEWHOOP, "Cinewhoop 3\""));
        Screenshots.step("bildschirmfotos: Werkbank mit RACER (Akku unter dem Rahmen)", "workbench_racer", 50,
                new WorkbenchScene(DroneBuild.RACER, "Race 5\""));
        Screenshots.step("bildschirmfotos: Werkbank mit FREESTYLE, GUI-Skala 2", "workbench_freestyle_gui2", 50,
                Screenshots.guiScale(2, new WorkbenchScene()));
        Screenshots.step("bildschirmfotos: Werkbank mit FREESTYLE, GUI-Skala 4", "workbench_freestyle_gui4", 50,
                Screenshots.guiScale(4, new WorkbenchScene()));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Schwerkraft 250 % und Tooltip", "configurator_world_rules_gravity", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.GRAVITY_SCALE, 250));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Tooltip Wind", "configurator_world_rules_wind", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.WIND, 2));
        Screenshots.step("bildschirmfotos: Welt-Regeln ohne Recht nur lesbar", "configurator_world_rules_readonly", 30,
                WorldRulesSelfTests.readOnlyScene());
        Screenshots.step("bildschirmfotos: Werkbank mit unmöglicher Drohne und Tooltip, Teile müssen zusammenpassen aus", "workbench_part_freedom", 50,
                PartFreedomSelfTests.workbenchScene(true));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Tooltip Teile müssen zusammenpassen", "configurator_world_rules_part_compatibility", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.PART_COMPATIBILITY, 0));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Voreinstellung Training und Tooltip Arcade", "configurator_world_rules_presets", 30,
                WorldRulesSelfTests.presetScene(RulePreset.TRAINING, RulePreset.ARCADE));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Tooltip Drohnen erzeugen neues Gelände", "configurator_world_rules_drones_generate_chunks", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.DRONES_GENERATE_CHUNKS, 0));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Tooltip Drohnen zerbrechen Blöcke", "configurator_world_rules_drones_break_blocks", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.DRONES_BREAK_BLOCKS, 1));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Tooltip Wucht beim Durchbruch", "configurator_world_rules_break_impact_scale", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.BREAK_IMPACT_SCALE, 200));
        Screenshots.step("bildschirmfotos: Weltrand im FPV mit OSD-Warnung", "world_edge_fpv", 20, GenerationEdgeSelfTests.fpvScene());
        Screenshots.step("bildschirmfotos: Bestenliste mit Abschnitt Mit Flughilfe und Tooltip", "leaderboard_assisted", 20, AssistedLapSelfTests.boardScene());
        Screenshots.step("bildschirmfotos: Rennen im FPV mit Flughilfe", "race_fpv_assisted", 10, AssistedLapSelfTests.raceScene());
        Screenshots.step("bildschirmfotos: Flughilfe-Prüfung im Race-Tab mit Gründen", "configurator_race_assist_check", 20,
                AssistCheckSelfTests.tabScene(true));
        Screenshots.step("bildschirmfotos: Flughilfe-Prüfung im Race-Tab ohne Gründe", "configurator_race_assist_clean", 20,
                AssistCheckSelfTests.tabScene(false));
        ZoneScenes.registerScreenshots(false);
        Screenshots.step("bildschirmfotos: Empfänger-Tab mit langem Funkennamen, Statuszeile bricht um", "configurator_receiver_status_wrapped", 20,
                HoverSweepSelfTests.statusScene());
        SelfTest.switchStep("bildschirmfotos: Sprache Deutsch", "bildschirmfotos: Sprache zurücksetzen", 1200,
                ctx -> Screenshots.language(ctx, "de_de"));
        SelfTest.clientStep("werkbank: Kennzahlen passen neben ihre Werte (Spielschrift, deutsch)", 20, WorkbenchFitSelfTest::check);
        Screenshots.step("bildschirmfotos: Werkbank mit FREESTYLE deutsch", "workbench_freestyle_de", 50, new WorkbenchScene());
        Screenshots.step("bildschirmfotos: Werkbank mit unmöglicher Drohne deutsch", "workbench_part_freedom_de", 50,
                PartFreedomSelfTests.workbenchScene(false));
        Screenshots.step("bildschirmfotos: Wartungsmenü mit eigenem Kamerawinkel deutsch", "drone_service_camera_de", 30,
                new CameraServiceScene(DroneBuild.RACER, 52.0F));
        Screenshots.step("bildschirmfotos: Video-Tab mit analoger Brille deutsch", "configurator_video_analog_de", 30,
                new VideoTabScene(ContentIds.GOGGLES_ANALOG, DroneBuild.RACER, true));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Schwerkraft 250 % und Tooltip deutsch", "configurator_world_rules_gravity_de", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.GRAVITY_SCALE, 250));
        Screenshots.step("bildschirmfotos: Welt-Regeln deutsch, Abschnitt Video und Funk", "configurator_world_rules_video_de", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.MAX_VIDEO_CHANNELS, 4));
        Screenshots.step("bildschirmfotos: Welt-Regeln ohne Recht nur lesbar deutsch", "configurator_world_rules_readonly_de", 30,
                WorldRulesSelfTests.readOnlyScene());
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Voreinstellung Arcade und Tooltip deutsch", "configurator_world_rules_presets_de", 30,
                WorldRulesSelfTests.presetScene(RulePreset.ARCADE, RulePreset.ARCADE));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Voreinstellung Training und Tooltip deutsch", "configurator_world_rules_presets_training_de", 30,
                WorldRulesSelfTests.presetScene(RulePreset.TRAINING, RulePreset.TRAINING));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Tooltip Runden mit Flughilfe speichern deutsch", "configurator_world_rules_save_assisted_laps_de", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Tooltip Drohnen zerbrechen Blöcke deutsch", "configurator_world_rules_drones_break_blocks_de", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.DRONES_BREAK_BLOCKS, 1));
        Screenshots.step("bildschirmfotos: Welt-Regeln mit Tooltip Wucht beim Durchbruch deutsch", "configurator_world_rules_break_impact_scale_de", 30,
                WorldRulesSelfTests.tabScene(WorldRuleSpecs.BREAK_IMPACT_SCALE, 0));
        Screenshots.step("bildschirmfotos: Weltrand im FPV mit OSD-Warnung deutsch", "world_edge_fpv_de", 20, GenerationEdgeSelfTests.fpvScene());
        Screenshots.step("bildschirmfotos: Bestenliste mit Abschnitt Mit Flughilfe und Tooltip deutsch", "leaderboard_assisted_de", 20,
                AssistedLapSelfTests.boardScene());
        Screenshots.step("bildschirmfotos: Rennen im FPV mit Flughilfe deutsch", "race_fpv_assisted_de", 10, AssistedLapSelfTests.raceScene());
        Screenshots.step("bildschirmfotos: Flughilfe-Prüfung im Race-Tab mit Gründen deutsch", "configurator_race_assist_check_de", 20,
                AssistCheckSelfTests.tabScene(true));
        Screenshots.step("bildschirmfotos: Flughilfe-Prüfung im Race-Tab ohne Gründe deutsch", "configurator_race_assist_clean_de", 20,
                AssistCheckSelfTests.tabScene(false));
        CourseEditSelfTests.registerGermanScreenshots();
        Screenshots.step("bildschirmfotos: Flugschule, Rahmen lehnt zu schweren Akku ab deutsch", "flight_school_battery_fit_de", 30,
                GuideSelfTests.sceneShot(BatteryScene.ID, 3, BatteryScene.REFUSED_AT + 26.0F));
        Screenshots.step("bildschirmfotos: Flugschule, Ladegerät lädt jeden Akku deutsch", "flight_school_battery_charger_de", 30,
                GuideSelfTests.sceneShot(BatteryScene.ID, 4, BatteryScene.CHEMISTRY_AT + 14.0F));
        Screenshots.step("bildschirmfotos: Steuerung mit R für Flugmodus und Gate drehen deutsch", "controls_shared_key_de", 20,
                GateKeySelfTests.controlsScene());
        Screenshots.step("bildschirmfotos: Empfänger-Tab ohne Gerät, macOS-Einstellung deutsch", "configurator_receiver_mac_de", 30,
                MacControllerSelfTests.receiverScene(true));
        Screenshots.step("bildschirmfotos: Empfänger-Tab mit langem Funkennamen, Statuszeile bricht um deutsch", "configurator_receiver_status_wrapped_de", 20,
                HoverSweepSelfTests.statusScene());
        Screenshots.step("bildschirmfotos: Empfänger-Assistent, Gas nach dem Weg nach oben deutsch", "receiver_wizard_throttle_de", 6,
                ThrottleWizardSelfTests.throttleScene());
        WizardRemoteSelfTests.registerScreenshots(true);
        StickModeSelfTests.registerScreenshots(true);
        SwitchCommandSelfTests.registerScreenshots(true);
        ConfigInputSelfTests.registerScreenshots(true);
        MouseSelfTests.registerScreenshots(true);
        Screenshots.step("bildschirmfotos: Tab Konfiguration mit Scharf-Winkel 180° deutsch", "configurator_arm_angle_de", 30, ArmAngleSelfTests.tabScene());
        Screenshots.step("bildschirmfotos: Shift-Tooltip Gate mit Taste R deutsch", "tooltip_gate_rotate_de", 30, GateKeySelfTests.tooltipScene(false));
        Screenshots.step("bildschirmfotos: Gate einstellen im Spiel mit Live-Vorschau deutsch", "gate_edit_mode_de", 30,
                GateFreeSelfTests.editScene(ShowcaseTour::base));
        Screenshots.step("bildschirmfotos: Gate einstellen, Hinweis zur freien Maus beim ersten Mal hervorgehoben deutsch", "gate_edit_mouse_tip_de",
                30, GateFreeSelfTests.firstEditScene(ShowcaseTour::base));
        Screenshots.step("bildschirmfotos: Shift-Tooltip Kurs-Werkzeug mit Taste R deutsch", "tooltip_course_tool_rotate_de", 30,
                GateKeySelfTests.tooltipScene(true));
        Screenshots.step("bildschirmfotos: Tauchwürfel einstellen im Spiel mit Ausgang deutsch", "course_element_edit_mode_de", 30,
                CourseElementSelfTests.editScene(ShowcaseTour::base));
        Screenshots.step("bildschirmfotos: Shift-Tooltip Rennflagge deutsch", "tooltip_race_flag_de", 30,
                GateKeySelfTests.tooltipScene(() -> new ItemStack(RaceModule.RACE_FLAG.get())));
        Screenshots.step("bildschirmfotos: Shift-Tooltip der Action-Cam (Flashback) deutsch", "tooltip_action_cam_flashback_de", 30,
                FlashbackCamShowcase.tooltipScene());
        Screenshots.step("bildschirmfotos: Shift-Tooltip Prop mit Absturz-Verhalten deutsch", "tooltip_prop_crash_de", 30,
                WearWeightSelfTests.tooltipScene(WearWeightSelfTests::propStack));
        Screenshots.step("bildschirmfotos: Shift-Tooltip 7-Zoll-Drohne mit Trocken- und Abfluggewicht deutsch", "tooltip_drone_weight_de", 30,
                WearWeightSelfTests.tooltipScene(WearWeightSelfTests::droneStack));
        BindSelfTests.registerScreenshots(true);
        VideoTabSelfTests.registerScreenshots(true);
        ZoneScenes.registerScreenshots(true);
        SelfTest.clientStep("bildschirmfotos: Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        Screenshots.step("bildschirmfotos: Steuerung mit R für Flugmodus und Gate drehen", "controls_shared_key", 20,
                Screenshots.guiScale(2, GateKeySelfTests.controlsScene()));
        Screenshots.step("bildschirmfotos: Empfänger-Tab ohne Gerät, macOS-Einstellung", "configurator_receiver_mac", 30,
                MacControllerSelfTests.receiverScene(true));
        Screenshots.step("bildschirmfotos: Empfänger-Tab ohne Gerät auf anderem System (keine macOS-Einstellung)", "configurator_receiver_other_os", 30,
                MacControllerSelfTests.receiverScene(false));
        Screenshots.step("bildschirmfotos: Lackierung in der Werkbank", "paint_screen", 40, new PaintScene());
        Screenshots.step("bildschirmfotos: Shift-Tooltip", "tooltip_shift", 50, new TooltipScene());
        Screenshots.step("bildschirmfotos: Shift-Tooltip Prop mit Absturz-Verhalten", "tooltip_prop_crash", 30,
                WearWeightSelfTests.tooltipScene(WearWeightSelfTests::propStack));
        Screenshots.step("bildschirmfotos: Shift-Tooltip 7-Zoll-Drohne mit Trocken- und Abfluggewicht", "tooltip_drone_weight", 30,
                WearWeightSelfTests.tooltipScene(WearWeightSelfTests::droneStack));
        Screenshots.step("bildschirmfotos: Funke, Akku und Ladegerät", "items_remote_battery_charger", 50, new ItemsScene());
        Screenshots.step("bildschirmfotos: Funke beidhändig, Blick nach unten", "remote_first_person", 30, new RemoteHandsScene(40.0F));
        Screenshots.step("bildschirmfotos: Funke beidhändig, Blick geradeaus", "remote_first_person_ahead", 30, new RemoteHandsScene(5.0F));
        Screenshots.step("bildschirmfotos: Funke im Sichtflug, Nebenhand belegt, Sichtfeld 70", "remote_first_person_los", 30,
                new RemoteLosScene(70, 3.5));
        Screenshots.step("bildschirmfotos: Funke im Sichtflug, Nebenhand belegt, Sichtfeld 110", "remote_first_person_los_fov110", 30,
                new RemoteLosScene(110, 7.0));
        Screenshots.step("bildschirmfotos: Ladegerät-Menü", "charger_gui", 30, new ChargerGuiScene());
        Screenshots.step("bildschirmfotos: Wartungsmenü der Drohne", "drone_service_menu", 30, new ServiceMenuScene());
        Screenshots.step("bildschirmfotos: Wartungsmenü mit eigenem Kamerawinkel (Race 5″)", "drone_service_camera", 30,
                new CameraServiceScene(DroneBuild.RACER, 52.0F));
        BindSelfTests.registerScreenshots(false);
        VideoTabSelfTests.registerScreenshots(false);
        Screenshots.step("bildschirmfotos: Video-Tab mit analoger Brille (Race-Kamera)", "configurator_video_analog", 30,
                new VideoTabScene(ContentIds.GOGGLES_ANALOG, DroneBuild.RACER, true));
        Screenshots.step("bildschirmfotos: Video-Tab mit digitaler Brille (Lite-System des Cinewhoops)", "configurator_video_digital", 30,
                new VideoTabScene(ContentIds.GOGGLES_DIGITAL, DroneBuild.CINEWHOOP, true));
        Screenshots.step("bildschirmfotos: Video-Tab mit Kreativ-Brille und eigenem Sichtfeld", "configurator_video_creative", 30,
                new VideoTabScene(ContentIds.GOGGLES_CREATIVE, DroneBuild.FREESTYLE, false));
        Screenshots.step("bildschirmfotos: OSD-Editor mit 4:3-Vorschau", "configurator_osd_4_3", 30,
                AspectShowcase.format(ImageFormat.FOUR_THREE, AspectShowcase.osdEditor()));
        Screenshots.step("bildschirmfotos: Video-Tab mit Bildformat Auto bei analoger Brille", "configurator_video_format_auto", 30,
                AspectShowcase.format(ImageFormat.AUTO, new VideoTabScene(ContentIds.GOGGLES_ANALOG, DroneBuild.RACER, true)));
        Screenshots.step("bildschirmfotos: Empfänger-Assistent", "receiver_wizard", 30, new WizardScene(false));
        Screenshots.step("bildschirmfotos: Empfänger-Assistent ohne Gerät auf macOS", "receiver_wizard_mac", 30, new WizardScene(true));
        Screenshots.step("bildschirmfotos: Empfänger-Assistent, Gas nach dem Weg nach oben", "receiver_wizard_throttle", 6,
                ThrottleWizardSelfTests.throttleScene());
        Screenshots.step("bildschirmfotos: Empfänger-Assistent, Gas unten halten nach beiden Enden", "receiver_wizard_throttle_hold", 1,
                ThrottleWizardSelfTests.holdScene());
        Screenshots.step("bildschirmfotos: Empfänger-Assistent, Prüfansicht nach der Kanalerkennung", "receiver_wizard_check", 6,
                ThrottleWizardSelfTests.checkScene());
        WizardRemoteSelfTests.registerScreenshots(false);
        StickModeSelfTests.registerScreenshots(false);
        StickOverlaySelfTests.registerScreenshots();
        SwitchCommandSelfTests.registerScreenshots(false);
        ConfigInputSelfTests.registerScreenshots(false);
        MouseSelfTests.registerScreenshots(false);
        Screenshots.step("bildschirmfotos: Tab Konfiguration mit Scharf-Winkel 180°", "configurator_arm_angle", 30, ArmAngleSelfTests.tabScene());
        Screenshots.step("bildschirmfotos: Gerätemeldung über dem Assistenten", "device_toast", 30, new DeviceToastScene());
        Screenshots.step("bildschirmfotos: Inventar mit Brillen und Gates", "inventory_icons", 30, new InventoryIconsScene());
        Screenshots.step("bildschirmfotos: Inventar mit allen Drohnenklassen nebeneinander", "inventory_drones", 30, new DroneInventoryScene());
        Screenshots.step("bildschirmfotos: Rezept im Rezeptbuch", "recipe_book", 30, new RecipeBookScene());
        Screenshots.step("bildschirmfotos: Funke im Rahmen, in der Hand eines Rüstungsständers und am Boden", "remote_display_contexts", 30, new DisplayScene());
        Screenshots.step("bildschirmfotos: Pilot mit Brille (dritte Person, von vorn)", "pilot_third_person", 30,
                new ThirdPersonScene(CameraType.THIRD_PERSON_FRONT, 20.0F, 12.0F, 0.0F));
        Screenshots.step("bildschirmfotos: Pilot mit Funke (dritte Person, von der Seite)", "pilot_third_person_side", 30,
                new ThirdPersonScene(CameraType.THIRD_PERSON_BACK, 20.0F, 6.0F, 90.0F));
        Screenshots.step("bildschirmfotos: Pilot mit Funke (dritte Person, von hinten)", "pilot_third_person_back", 30,
                new ThirdPersonScene(CameraType.THIRD_PERSON_BACK, 20.0F, 30.0F, 0.0F));
        Screenshots.step("bildschirmfotos: Pilot mit Brille, Kopf 45° gedreht und angehoben", "pilot_head_turned", 30,
                new ThirdPersonScene(CameraType.THIRD_PERSON_FRONT, 20.0F, -25.0F, -60.0F, 45.0F));
        Screenshots.step("bildschirmfotos: Staub beim Abheben", "dust_takeoff", 24, new DustTakeoffScene(DustModel.DEFAULT_INTENSITY));
        Screenshots.step("bildschirmfotos: Staub beim Abheben (Staubmenge 100 %)", "dust_takeoff_max", 24, new DustTakeoffScene(DustModel.MAX_INTENSITY));
        Screenshots.step("bildschirmfotos: Drohne aus der Nähe", "drone_closeup", 25, new CloseUpScene(DroneBuild.FREESTYLE, PartWear.PRISTINE));
        Screenshots.step("bildschirmfotos: Drohne mit leichter Action-Cam und LED-Streifen", "drone_closeup_action_cam_light", 25,
                new CloseUpScene(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_LIGHT).withAccessory(AccessoryType.LED_STRIP),
                        PartWear.PRISTINE));
        Screenshots.step("bildschirmfotos: Drohne mit schwerer Action-Cam", "drone_closeup_action_cam_heavy", 25,
                new CloseUpScene(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY), PartWear.PRISTINE));
        Screenshots.step("bildschirmfotos: Drohne mit Action-Cam (Flashback)", "drone_closeup_action_cam_flashback", 25,
                new CloseUpScene(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_FLASHBACK), PartWear.PRISTINE));
        Screenshots.step("bildschirmfotos: Drohne mit beschädigten Props", "drone_closeup_damaged_props", 25,
                new CloseUpScene(DroneBuild.FREESTYLE, damagedProps()));
        Screenshots.step("bildschirmfotos: lackierte Drohne (Neon)", "drone_closeup_paint_neon", 25,
                new CloseUpScene(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_LIGHT).withAccessory(AccessoryType.LED_STRIP),
                        PartWear.PRISTINE, PaintPreset.NEON.paint()));
        Screenshots.step("bildschirmfotos: lackierte Drohne (Race Team, Props einzeln)", "drone_closeup_paint_race_team", 25,
                new CloseUpScene(DroneBuild.RACER, PartWear.PRISTINE, PaintPreset.RACE_TEAM.paint()
                        .with(PaintSlot.PROP_FRONT_RIGHT, PaintSwatch.YELLOW.rgb()).with(PaintSlot.PROP_REAR_LEFT, PaintSwatch.LIGHT_BLUE.rgb())));
        Screenshots.step("bildschirmfotos: Zustandsanzeige einer beschädigten Drohne", "drone_condition", 30, new ConditionScene());
        Screenshots.step("bildschirmfotos: Gate-Kurs vor dem Start mit grünem Start-Gate", "race_gates", 50, new RaceScene());
        Screenshots.step("bildschirmfotos: Start-/Ziel-Gate von der Einflugseite", "gate_start_finish", 30,
                new GateViewScene(0, -2.5, -7.5, 0.9));
        Screenshots.step("bildschirmfotos: Gate-Markierungen mit Pfeilen und Nummer", "gate_markings", 30,
                new GateViewScene(1, -3.5, -6.5, 0.3));
        Screenshots.step("bildschirmfotos: Gate von der Rückseite (falsche Richtung)", "gate_exit_side", 30,
                new GateViewScene(1, 2.0, 6.0, 0.3));
        Screenshots.step("bildschirmfotos: Leiter aus drei Gates und Doppel-Gate", "gate_stacks", 30, new GateStackScene());
        GateMountSelfTests.registerScreenshots(ShowcaseTour::base);
        Screenshots.step("bildschirmfotos: Ideallinie im Rennen (dritte Person)", "race_line_third_person", 40, new RaceLineThirdPersonScene());
        Screenshots.step("bildschirmfotos: Geist der Bestrunde fliegt im Rennen mit", "race_ghost", 24, new GhostScene());
        Screenshots.step("bildschirmfotos: Whoop-Geist mit Lackierung auf dem Kurs", "race_ghost_whoop", 24,
                new GhostScene(DroneBuild.WHOOP, PaintPreset.NEON.paint(), 35));
        Screenshots.step("bildschirmfotos: stehender Start, Startampel rot", "race_start_lights", 2, new StartLightsScene(false));
        Screenshots.step("bildschirmfotos: stehender Start, Startampel grün", "race_start_go", 2, new StartLightsScene(true));
        Screenshots.step("bildschirmfotos: Bestenliste des Kurses", "leaderboard_screen", 20, new LeaderboardScene());
        Screenshots.step("bildschirmfotos: OSD im Flug", "osd_flight", 26, new OsdScene());
        Screenshots.step("bildschirmfotos: Ideallinie im Rennen (FPV)", "race_line_fpv", 26, new RaceLineFpvScene());
        Screenshots.step("bildschirmfotos: Flip im Flug mit Trick-Meldung im OSD", "trick_popup_fpv", 3, new TrickScene());
        Screenshots.step("bildschirmfotos: Kette aus Juicy Flick und Rolle im OSD", "trick_chain_fpv", 3, new TrickChainScene());
        ProTrickShowcase.register();
        Screenshots.step("bildschirmfotos: Analogbild Stufe 2 (Glitches) mit OSD", "video_analog_stage2", 20,
                new BreakupScene(0.68F, VideoSystem.ANALOG, AnalogBreakup.Stage.GLITCHES));
        Screenshots.step("bildschirmfotos: Analogbild Stufe 3 (Farbverlust) mit OSD", "video_analog_stage3", 20,
                new BreakupScene(0.47F, VideoSystem.ANALOG, AnalogBreakup.Stage.COLOUR_LOSS));
        Screenshots.step("bildschirmfotos: Analogbild Stufe 4 (Schnee und Sync) mit OSD", "video_analog_stage4", 20,
                new BreakupScene(0.25F, VideoSystem.ANALOG, AnalogBreakup.Stage.SNOW));
        Screenshots.step("bildschirmfotos: Analogbild Stufe 5 (Rauschen) mit OSD", "video_analog_stage5", 20,
                new BreakupScene(0.0F, VideoSystem.ANALOG, AnalogBreakup.Stage.STATIC));
        Screenshots.step("bildschirmfotos: Digitalbild bei schwachem Signal mit OSD", "video_digital_breakup", 24,
                new BreakupScene(0.25F, VideoSystem.DIGITAL, null));
        Screenshots.step("bildschirmfotos: Digitalbild ohne Signal mit OSD", "video_digital_lost", 24,
                new BreakupScene(0.0F, VideoSystem.DIGITAL, null));
        SelfTest.clientStep("bildschirmfotos: FPV-Bild 16:9 und 4:3 aus derselben Lage, 4:3 auch mit GUI-Skala 2 und 4", 1200,
                new FormatCompareStep()::run);
        Screenshots.step("bildschirmfotos: Analogbild Stufe 3 (Farbverlust) in 4:3 (Auto)", "video_4_3_analog_stage3", 20,
                AspectShowcase.format(ImageFormat.AUTO, new BreakupScene(0.47F, VideoSystem.ANALOG, AnalogBreakup.Stage.COLOUR_LOSS)));
        Screenshots.step("bildschirmfotos: Analogbild Stufe 4 (Schnee und Sync) in 4:3 (Auto)", "video_4_3_analog_stage4", 20,
                AspectShowcase.format(ImageFormat.AUTO, new BreakupScene(0.25F, VideoSystem.ANALOG, AnalogBreakup.Stage.SNOW)));
        Screenshots.step("bildschirmfotos: Digitalbild bei schwachem Signal mit Auto (16:9)", "video_auto_digital_breakup", 24,
                AspectShowcase.format(ImageFormat.AUTO, new BreakupScene(0.25F, VideoSystem.DIGITAL, null)));
        Screenshots.step("bildschirmfotos: Digitalbild bei schwachem Signal in 4:3", "video_4_3_digital_breakup", 24,
                AspectShowcase.format(ImageFormat.FOUR_THREE, new BreakupScene(0.25F, VideoSystem.DIGITAL, null)));
        Screenshots.step("bildschirmfotos: REC-Anzeige der Action-Cam im OSD", "action_cam_rec_osd", 20, new RecOsdScene());
        Screenshots.step("bildschirmfotos: Aufnahmen-Liste der Action-Cam", "action_cam_recordings", 20, new RecordingsScene());
        Screenshots.step("bildschirmfotos: Pilot aus Sicht der Drohne", "pilot_from_drone", 40, new PilotViewScene());
        Screenshots.step("bildschirmfotos: FPV in der Höhle ohne Scheinwerfer (scharf, gleiche Lage wie mit)", "headlight_cave_off", 30, new CaveScene(false));
        Screenshots.step("bildschirmfotos: FPV in der Höhle mit LED-Scheinwerfer (scharf, gleiche Lage wie ohne)", "headlight_cave", 30, new CaveScene(true));
        Screenshots.step("bildschirmfotos: FPV in der Höhle mit LED-Scheinwerfer in 4:3", "headlight_cave_4_3", 30,
                AspectShowcase.format(ImageFormat.FOUR_THREE, new CaveScene(true)));
        Screenshots.step("bildschirmfotos: leuchtende LEDs einer scharfen Drohne in der Höhle", "drone_leds_cave", 30, new LedCaveScene());
        Screenshots.step("bildschirmfotos: Kanal-Tab mit aktiven Kanälen anderer Piloten", "configurator_video_channel_live", 30, new ChannelListScene());
        Screenshots.step("bildschirmfotos: Zuschauen – FPV-Bild der Drohne einer anderen Pilotin", "spectate_view", 40, new SpectateScene());
        Screenshots.step("bildschirmfotos: Zuschauen mit Bildformat Auto (analoger Sender, 4:3)", "spectate_view_4_3", 40,
                AspectShowcase.format(ImageFormat.AUTO, new SpectateScene()));
        ShadowSelfTests.registerShowcase();
        RelayShowcase.register();
        HeadlightShowcase.register();
        IrisShowcase.register();
        TrainingShowcase.register();
        ApiShowcase.register();
        FlashbackCamShowcase.register();
        FfmpegShowcase.register();
        Mode3dShowcase.register();
        WaterproofTurtleShowcase.register();
        CreativeShowcase.register();
        GateFreeSelfTests.registerScreenshots(ShowcaseTour::base);
        CourseElementSelfTests.registerScreenshots(ShowcaseTour::base);
        FreeformGateSelfTests.registerScreenshots(ShowcaseTour::base);
        GateRedstoneSelfTests.registerScreenshots(ShowcaseTour::base);
        GatePoleSelfTests.registerScreenshots(ShowcaseTour::base);
        CourseEditSelfTests.registerScreenshots();
        PropRepairShowcase.register();
        BlackboxShowcase.register();
        VtxPowerShowcase.register();
        TrailShowcase.register();
        BatterySagSelfTests.registerShowcase();
        SelfTest.setupStep("bildschirmfotos: Aufräumen", 200, ShowcaseTour::cleanupStep);
    }

    private static void clearBlock(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        if (level.getBlockState(pos.below()).is(Blocks.DIRT)) {
            level.setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    public static void ensureBase(MinecraftServer server) {
        if (BASE.get() != null) {
            return;
        }
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int x = player.getBlockX();
        int z = player.getBlockZ();
        BASE.set(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z));
    }

    public static Screenshots.Scene workbenchScene() {
        return new WorkbenchScene();
    }

    public static Screenshots.Scene paintScene() {
        return new PaintScene();
    }

    public static Screenshots.Scene chargerScene() {
        return new ChargerGuiScene();
    }

    public static Screenshots.Scene serviceScene() {
        return new ServiceMenuScene();
    }

    public static Screenshots.Scene recordingsScene() {
        return new RecordingsScene();
    }

    public static BlockPos base() {
        BlockPos pos = BASE.get();
        if (pos == null) {
            throw new IllegalStateException("Szene nicht vorbereitet");
        }
        return pos;
    }

    private static void place(ServerPlayer player, double x, double y, double z, float yRot, float xRot) {
        player.teleportTo(player.level(), x, y, z, Set.of(), yRot, xRot, true);
    }

    private static void look(LocalPlayer player, double x, double y, double z) {
        Vec3 eye = player.getEyePosition();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        turn(player, yaw, pitch);
    }

    private static void parkMouse(Minecraft mc) {
        Screenshots.parkMouse(mc);
    }

    private static float yawTowards(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    private static void turn(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
    }

    private static CompletableFuture<?> future(SelfTest.StepContext ctx) {
        return (CompletableFuture<?>) ctx.state().get(FUTURE);
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = future(ctx);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static ItemStack goggles() {
        return ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ItemStack droneItem() {
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        return stack;
    }

    private static void select(ServerPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ClientboundSetHeldSlotPacket(slot));
    }

    private static void fillHotbar(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(DroneModule.REMOTE.get()));
        inventory.setItem(1, droneItem());
        inventory.setItem(2, goggles());
        inventory.setItem(3, Batteries.stackOf(BatteryType.B6S_1300, 1.0F));
        inventory.setItem(4, Batteries.stackOf(BatteryType.B6S_1100, 0.62F));
        inventory.setItem(5, Batteries.stackOf(BatteryType.B4S_650, 0.18F));
        ContentIds.item(ContentIds.BATTERY_CHARGER).ifPresent(item -> inventory.setItem(6, new ItemStack(item)));
        ContentIds.item(ContentIds.DRONE_WORKBENCH).ifPresent(item -> inventory.setItem(7, new ItemStack(item)));
        ContentIds.item(ContentIds.GATE_MEDIUM).ifPresent(item -> inventory.setItem(8, new ItemStack(item, 8)));
        select(player, 0);
    }

    private static PartWear damagedProps() {
        return PartWear.PRISTINE.withProps(new float[]{0.32F, 0.0F, 0.62F, 0.55F});
    }

    private static QuadcopterEntity spawnDrone(ServerLevel level, Vec3 pos, float facingYaw) {
        return spawnDrone(level, pos, facingYaw, DroneBuild.FREESTYLE);
    }

    private static QuadcopterEntity spawnDrone(ServerLevel level, Vec3 pos, float facingYaw, DroneBuild build) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = droneItem();
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, facingYaw, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-facingYaw) + (float) Math.PI));
        DRONE.set(drone.getId());
        return drone;
    }

    private static void discardDrone(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            OsdClient.CONFIG.set(OsdConfig.defaults());
            mc.getTutorial().setStep(TutorialSteps.NONE);
            for (KeyMapping mapping : ClientRegistrar.keyMappings()) {
                mapping.setKey(mapping.getDefaultKey());
            }
            KeyMapping.resetMapping();
            savedCamera = mc.options.getCameraType();
            savedFov = mc.options.fov().get();
            state.put(PHASE, 1);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET;
                int z = player.getBlockZ() + OFFSET;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BASE.set(new BlockPos(x, y, z));
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                fillHotbar(player);
                place(player, x + 0.5, y, z + 0.5, 0.0F, 0.0F);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Szene vorbereiten");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        BlockPos base = BASE.get();
        if (mc.player == null || mc.level == null || base == null || mc.player.blockPosition().distManhattan(base) > 2
                || !mc.level.isLoaded(base.offset(0, 0, GATE_START + GATE_SPACING * GATE_COUNT))) {
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult configuratorStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(TABS)) {
            List<String> ids = new ArrayList<>();
            for (ConfigTab tab : ConfigTabs.create()) {
                ids.add(tab.id());
            }
            state.put(TABS, ids);
            state.put(INDEX, 0);
            state.put(FUTURE, ctx.onServer(server -> select(player(server), 1)));
            MacControllers.overrideOptionShown(false);
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) state.get(TABS);
        int index = (int) state.get(INDEX);
        if (index >= ids.size()) {
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            MacControllers.overrideOptionShown(null);
            ctx.log("Konfigurator-Tabs fotografiert: " + ids);
            return StepResult.pass();
        }
        StepResult server = awaitServer(ctx, "Drohne in die Hand");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        String id = ids.get(index);
        parkMouse(mc);
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, id);
            return StepResult.pending();
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !id.equals(active.id())) {
            screen.selectTab(id);
            state.put(FRAMES, screen.extractedFrames(id));
            state.put(SINCE, ctx.tick());
            state.remove(SHOT);
            return StepResult.pending();
        }
        if (!state.containsKey(SINCE)) {
            state.put(FRAMES, screen.extractedFrames(id));
            state.put(SINCE, ctx.tick());
            Screenshots.clearOverlays(mc);
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(SINCE) < 12 || screen.extractedFrames(id) <= (int) state.get(FRAMES) + 3) {
            return StepResult.pending();
        }
        if (!state.containsKey(SHOT)) {
            state.put(SHOT, Screenshots.capture("configurator_" + id));
            return StepResult.pending();
        }
        CompletableFuture<?> shot = (CompletableFuture<?>) state.get(SHOT);
        if (!shot.isDone()) {
            return StepResult.pending();
        }
        if (shot.isCompletedExceptionally()) {
            screen.discardAndClose();
            MacControllers.overrideOptionShown(null);
            return StepResult.fail("Tab " + id + ": " + shot.exceptionNow());
        }
        state.remove(SHOT);
        state.remove(SINCE);
        state.put(INDEX, index + 1);
        if (index + 1 < ids.size()) {
            screen.selectTab(ids.get(index + 1));
        }
        return StepResult.pending();
    }

    private static StepResult fpvPropsOffStep(SelfTest.StepContext ctx) {
        if (savedRenderConfig == null) {
            savedRenderConfig = DroneRenderConfig.get().copy();
        }
        DroneRenderConfig config = DroneRenderConfig.get().copy();
        config.fpvProps = false;
        config.fpvDucts = false;
        DroneRenderConfig.CONFIG.set(config);
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (savedRenderConfig != null) {
                DroneRenderConfig.CONFIG.set(savedRenderConfig);
                savedRenderConfig = null;
            }
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            TooltipShift.override(null);
            if (savedCamera != null) {
                mc.options.setCameraType(savedCamera);
            }
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.hideMarker(false);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                for (int id : GATES) {
                    Entity gate = level.getEntity(id);
                    if (gate != null) {
                        gate.discard();
                    }
                }
                GATES.clear();
                for (int i = CAVE.size() - 1; i >= 0; i--) {
                    CaveBlock block = CAVE.get(i);
                    level.setBlockAndUpdate(block.pos(), block.state());
                }
                CAVE.clear();
                BlockPos base = BASE.get();
                if (base != null) {
                    for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, 3, 6))) {
                        if (!level.getBlockState(pos).isAir()) {
                            clearBlock(level, pos.immutable());
                        }
                    }
                }
                player.getInventory().clearContent();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                player.setGameMode(GameType.CREATIVE);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return StepResult.pending();
        }
        return Screenshots.restoreWindow(ctx);
    }

    private static final class WorkbenchScene implements Screenshots.Scene {
        private final DroneBuild shown;
        private final String name;

        private WorkbenchScene() {
            this(DroneBuild.FREESTYLE, "Freestyle 5\"");
        }

        private WorkbenchScene(DroneBuild shown, String name) {
            this.shown = shown;
            this.name = name;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos pos = base().offset(0, 0, 2);
                    BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
                    level.setBlockAndUpdate(pos, bench);
                    fillHotbar(player);
                    if (player.openMenu(bench.getMenuProvider(level, pos)).isEmpty()
                            || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                        throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
                    }
                    DroneBuild build = this.shown;
                    ItemStack frame = ContentItems.stack(build.frame());
                    frame.set(DataComponents.CUSTOM_NAME, Component.literal(this.name));
                    put(menu, player, BuildSlots.FRAME, frame);
                    put(menu, player, BuildSlots.STACK, ContentItems.stack(build.stack()));
                    put(menu, player, BuildSlots.VIDEO, ContentItems.stack(build.video()));
                    put(menu, player, BuildSlots.BATTERY, Batteries.stackOf(build.battery(), 0.86F));
                    for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
                        put(menu, player, BuildSlots.MOTOR + i, ContentItems.stack(build.motor()));
                        put(menu, player, BuildSlots.PROP + i, ContentItems.stack(build.prop()));
                    }
                    if (!DroneAssembly.isDrone(menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem())) {
                        throw new IllegalStateException("kein Ergebnis in der Werkbank: " + menu.check());
                    }
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Werkbank");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                return ctx.tick() > 200 ? StepResult.fail("Werkbank-Screen fehlt: " + mc.gui.screen()) : StepResult.pending();
            }
            return screen.getMenu().resultPreview().isEmpty() ? StepResult.pending() : StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        private static void put(DroneWorkbenchMenu menu, ServerPlayer player, int slot, ItemStack stack) {
            menu.setCarried(stack);
            menu.clicked(slot, 0, ContainerInput.PICKUP, player);
            if (!menu.getCarried().isEmpty()) {
                throw new IllegalStateException("Slot " + slot + " nahm " + stack + " nicht an");
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.closeContainer();
                clearBlock(player.level(), base().offset(0, 0, 2));
                fillHotbar(player);
            });
        }
    }

    private static final class PaintScene implements Screenshots.Scene {
        private static final String OPENED = "paint_opened";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos pos = base().offset(0, 0, 2);
                    BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
                    level.setBlockAndUpdate(pos, bench);
                    fillHotbar(player);
                    if (player.openMenu(bench.getMenuProvider(level, pos)).isEmpty()
                            || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                        throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
                    }
                    DroneBuild build = DroneBuild.FREESTYLE;
                    WorkbenchScene.put(menu, player, BuildSlots.FRAME, ContentItems.stack(build.frame()));
                    WorkbenchScene.put(menu, player, BuildSlots.STACK, ContentItems.stack(build.stack()));
                    WorkbenchScene.put(menu, player, BuildSlots.VIDEO, ContentItems.stack(build.video()));
                    WorkbenchScene.put(menu, player, BuildSlots.BATTERY, Batteries.stackOf(build.battery(), 0.86F));
                    WorkbenchScene.put(menu, player, BuildSlots.accessorySlot(AccessoryMount.TOP),
                            ContentItems.stack(AccessoryType.ACTION_CAM_LIGHT));
                    for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
                        WorkbenchScene.put(menu, player, BuildSlots.MOTOR + i, ContentItems.stack(build.motor()));
                        WorkbenchScene.put(menu, player, BuildSlots.PROP + i, ContentItems.stack(build.prop()));
                    }
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Lackierung");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.gui.screen() instanceof DroneWorkbenchScreen workbench) {
                if (workbench.getMenu().resultPreview().isEmpty()) {
                    return ctx.tick() > 200 ? StepResult.fail("Werkbank ohne Ergebnis") : StepResult.pending();
                }
                if (workbench.openPaint() == null) {
                    return StepResult.fail("Lackierung ließ sich nicht öffnen");
                }
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof PaintScreen paint)) {
                return ctx.tick() > 200 ? StepResult.fail("Lackier-Screen fehlt: " + mc.gui.screen()) : StepResult.pending();
            }
            if (!state.containsKey(OPENED)) {
                state.put(OPENED, Boolean.TRUE);
                paint.applyPreset(PaintPreset.NEON);
                paint.select(PaintSlot.PROP_FRONT_RIGHT);
                paint.paintSelected(PaintSwatch.ORANGE.rgb());
                paint.select(PaintSlot.PROP_FRONT_RIGHT);
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.closeContainer();
                clearBlock(player.level(), base().offset(0, 0, 2));
                fillHotbar(player);
            });
        }
    }

    private static final class TooltipScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.closeContainer();
                    player.setGameMode(GameType.SURVIVAL);
                    fillHotbar(player);
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    QuadcopterEntity drone = spawnDrone(player.level(), Vec3.atBottomCenterOf(base().offset(0, 0, 3)), 180.0F);
                    drone.setCustomName(Component.literal("Nighthawk"));
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.getInventory().setItem(0, ItemStack.EMPTY);
                    player.getInventory().setItem(9, remote);
                    player.getInventory().setItem(13, Batteries.stackOf(BatteryType.B6S_4000_LION, 0.93F));
                    player.getInventory().setItem(14, ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElse(ItemStack.EMPTY));
                    player.getInventory().setItem(15, ContentIds.item(ContentIds.COURSE_TOOL).map(ItemStack::new).orElse(ItemStack.EMPTY));
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Tooltip-Szene");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.player.hasInfiniteMaterials() || !ContentIds.isRemote(mc.player.getInventory().getItem(9))
                    || ContentIds.boundDrone(mc.player.getInventory().getItem(9)).isEmpty()) {
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                TooltipShift.override(false);
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, 9));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TooltipShift.override(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                player.setGameMode(GameType.CREATIVE);
                fillHotbar(player);
            });
        }
    }

    private static final class ItemsScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos base = base();
                    place(player, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 30.0F);
                    fillHotbar(player);
                    player.setItemSlot(EquipmentSlot.OFFHAND, Batteries.stackOf(BatteryType.B6S_1300, 1.0F));
                    BlockPos charger = chargerPos();
                    level.setBlockAndUpdate(charger, ContentBlocks.BATTERY_CHARGER.block().get().defaultBlockState()
                            .setValue(BatteryChargerBlock.FACING, Direction.NORTH));
                    if (!(level.getBlockEntity(charger) instanceof BatteryChargerBlockEntity entity)) {
                        throw new IllegalStateException("Ladegerät ohne Block-Entity");
                    }
                    entity.setItem(0, Batteries.stackOf(BatteryType.B6S_1300, 0.42F));
                    entity.setItem(1, Batteries.stackOf(BatteryType.B6S_1100, 0.71F));
                    entity.setItem(2, Batteries.stackOf(BatteryType.B4S_650, 0.25F));
                    level.setBlockAndUpdate(charger.east(), Blocks.SMOOTH_STONE.defaultBlockState());
                    level.setBlockAndUpdate(charger.west(), Blocks.SMOOTH_STONE.defaultBlockState());
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Item-Szene");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.level == null || !ContentIds.isRemote(mc.player.getMainHandItem())) {
                return StepResult.pending();
            }
            BlockState charger = mc.level.getBlockState(chargerPos());
            if (!charger.hasProperty(BatteryChargerBlock.STATE) || charger.getValue(BatteryChargerBlock.STATE).getSerializedName().equals("idle")) {
                return ctx.tick() > 200 ? StepResult.fail("Ladegerät lädt nicht: " + charger) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos charger = chargerPos();
            if (mc.player != null) {
                look(mc.player, charger.getX() + 0.9, charger.getY() + 0.1, charger.getZ() + 0.3);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos charger = chargerPos();
                if (level.getBlockEntity(charger) instanceof BatteryChargerBlockEntity entity) {
                    entity.clearContent();
                }
                clearBlock(level, charger);
                clearBlock(level, charger.east());
                clearBlock(level, charger.west());
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            });
        }
    }

    private static final class RemoteHandsScene implements Screenshots.Scene {
        private final float pitch;

        private RemoteHandsScene(float pitch) {
            this.pitch = pitch;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    BlockPos base = base();
                    place(player, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 180.0F, this.pitch);
                    fillHotbar(player);
                    player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Funke beidhändig");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || !ContentIds.isRemote(mc.player.getMainHandItem()) || !mc.player.getOffhandItem().isEmpty()) {
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                turn(mc.player, 180.0F, this.pitch);
            }
        }
    }

    private static final class RemoteLosScene implements Screenshots.Scene {
        private final int fov;
        private final double distance;

        private RemoteLosScene(int fov, double distance) {
            this.fov = fov;
            this.distance = distance;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                PilotSession.INSTANCE.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    BlockPos base = base();
                    Vec3 eye = Vec3.atBottomCenterOf(base);
                    Vec3 pad = eye.add(0.6, 0.0, -this.distance);
                    place(player, eye.x, eye.y, eye.z, yawTowards(eye, pad), 20.0F);
                    fillHotbar(player);
                    spawnDrone(player.level(), pad, yawTowards(pad, eye) + 30.0F);
                    startSession(player, false);
                    player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.CHEST));
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Funke im Sichtflug");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!PilotSession.INSTANCE.isLineOfSight()) {
                return ctx.tick() > 200 ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
            }
            if (mc.player == null || !ContentIds.isRemote(mc.player.getMainHandItem()) || mc.player.getOffhandItem().isEmpty()) {
                return ctx.tick() > 200 ? StepResult.fail("Funke und Nebenhand-Item fehlen") : StepResult.pending();
            }
            mc.options.fov().set(this.fov);
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            PilotSession session = PilotSession.INSTANCE;
            session.clearTestInput();
            if (session.isActive()) {
                session.requestStop();
            }
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                fillHotbar(player);
            });
        }
    }

    private static final class DisplayScene implements Screenshots.Scene {
        private static final List<Integer> SHOWN = new ArrayList<>();

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos base = base();
                    place(player, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 15.0F);
                    select(player, 8);
                    BlockPos wall = base.offset(0, 1, 3);
                    level.setBlockAndUpdate(wall, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    level.setBlockAndUpdate(wall.below(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    ItemFrame frame = new ItemFrame(level, wall.north(), Direction.NORTH);
                    frame.setItem(new ItemStack(DroneModule.REMOTE.get()));
                    level.addFreshEntity(frame);
                    ArmorStand stand = new ArmorStand(level, base.getX() + 2.2, base.getY(), base.getZ() + 2.8);
                    stand.setYRot(160.0F);
                    stand.setYHeadRot(160.0F);
                    stand.setYBodyRot(160.0F);
                    stand.setShowArms(true);
                    stand.setRightArmPose(new Rotations(-60.0F, 0.0F, 0.0F));
                    stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(DroneModule.REMOTE.get()));
                    stand.setItemSlot(EquipmentSlot.HEAD, goggles());
                    level.addFreshEntity(stand);
                    ItemEntity dropped = new ItemEntity(level, base.getX() - 1.3, base.getY() + 0.1, base.getZ() + 2.2,
                            new ItemStack(DroneModule.REMOTE.get()), 0.0, 0.0, 0.0);
                    dropped.setNeverPickUp();
                    dropped.setUnlimitedLifetime();
                    level.addFreshEntity(dropped);
                    SHOWN.clear();
                    SHOWN.add(frame.getId());
                    SHOWN.add(stand.getId());
                    SHOWN.add(dropped.getId());
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Anzeige-Szene");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.level == null || SHOWN.size() < 3) {
                return StepResult.pending();
            }
            for (int id : SHOWN) {
                if (mc.level.getEntity(id) == null) {
                    return ctx.tick() > 200 ? StepResult.fail("Anzeige-Entity fehlt: " + id) : StepResult.pending();
                }
            }
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos base = base();
            if (mc.player != null) {
                look(mc.player, base.getX() + 0.6, base.getY() + 0.9, base.getZ() + 2.8);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            List<Integer> ids = List.copyOf(SHOWN);
            ctx.onServer(server -> {
                ServerLevel level = player(server).level();
                for (int id : ids) {
                    Entity entity = level.getEntity(id);
                    if (entity != null) {
                        entity.discard();
                    }
                }
                BlockPos wall = base().offset(0, 1, 3);
                level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
                clearBlock(level, wall.below());
                select(player(server), 0);
            });
        }
    }

    private static BlockPos chargerPos() {
        return base().offset(-1, 0, 2);
    }

    private static final class ChargerGuiScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos charger = chargerPos();
                    level.setBlockAndUpdate(charger, ContentBlocks.BATTERY_CHARGER.block().get().defaultBlockState()
                            .setValue(BatteryChargerBlock.FACING, Direction.NORTH));
                    if (!(level.getBlockEntity(charger) instanceof BatteryChargerBlockEntity entity)) {
                        throw new IllegalStateException("Ladegerät ohne Block-Entity");
                    }
                    entity.setItem(0, Batteries.stackOf(BatteryType.B6S_1300, 0.42F));
                    entity.setItem(1, Batteries.stackOf(BatteryType.B6S_1100, 1.0F));
                    entity.setItem(3, Batteries.stackOf(BatteryType.B4S_650, 0.12F));
                    player.openMenu(entity);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Ladegerät-Menü");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!(mc.gui.screen() instanceof BatteryChargerScreen)) {
                return ctx.tick() > 200 ? StepResult.fail("Ladegerät-Menü fehlt: " + mc.gui.screen()) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.closeContainer();
                ServerLevel level = player.level();
                if (level.getBlockEntity(chargerPos()) instanceof BatteryChargerBlockEntity entity) {
                    entity.clearContent();
                }
                clearBlock(level, chargerPos());
                fillHotbar(player);
            });
        }
    }

    private static final class ServiceMenuScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity drone = spawnDrone(player.level(), Vec3.atBottomCenterOf(base().offset(0, 0, 3)), 180.0F);
                    drone.setCustomName(Component.literal("Nighthawk"));
                    drone.setWear(PartWear.PRISTINE.withProp(PartWear.FRONT_LEFT, 0.0F).withProp(PartWear.FRONT_RIGHT, 0.56F)
                            .withProp(PartWear.REAR_RIGHT, 0.83F));
                    player.getInventory().setItem(8, ContentItems.stack(PropType.P51_3B).copyWithCount(4));
                    Services.PLATFORM.openMenu(player, new SimpleMenuProvider((containerId, inventory, p) -> new DroneServiceMenu(containerId, inventory, drone),
                            DroneServiceMenu.TITLE), ByteBufCodecs.VAR_INT, drone.getId());
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Wartungsmenü");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!(mc.gui.screen() instanceof DroneServiceScreen)) {
                return ctx.tick() > 200 ? StepResult.fail("Wartungsmenü fehlt: " + mc.gui.screen()) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                player(server).closeContainer();
                discardDrone(server);
                fillHotbar(player(server));
            });
        }
    }

    private static final class CameraServiceScene implements Screenshots.Scene {
        private final DroneBuild build;
        private final float uptilt;

        CameraServiceScene(DroneBuild build, float uptilt) {
            this.build = build;
            this.uptilt = uptilt;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity drone = spawnDrone(player.level(), Vec3.atBottomCenterOf(base().offset(0, 0, 3)), 180.0F, this.build);
                    DroneServiceMenu.setUptilt(drone, this.uptilt);
                    Services.PLATFORM.openMenu(player, new SimpleMenuProvider((containerId, inventory, p) -> new DroneServiceMenu(containerId, inventory, drone),
                            DroneServiceMenu.TITLE), ByteBufCodecs.VAR_INT, drone.getId());
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Wartungsmenü mit Kamerawinkel");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!(mc.gui.screen() instanceof DroneServiceScreen screen) || screen.uptiltSlider() == null
                    || screen.uptiltSlider().current() != this.uptilt) {
                return ctx.tick() > 200 ? StepResult.fail("Wartungsmenü ohne Kamerawinkel " + this.uptilt + ": " + mc.gui.screen()) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                player(server).closeContainer();
                discardDrone(server);
                fillHotbar(player(server));
            });
        }
    }

    private static final class VideoTabScene implements Screenshots.Scene {
        private final Identifier goggles;
        private final DroneBuild build;
        private final boolean fromModule;
        private @Nullable VideoConfig saved;

        VideoTabScene(Identifier goggles, DroneBuild build, boolean fromModule) {
            this.goggles = goggles;
            this.build = build;
            this.fromModule = fromModule;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                this.saved = VideoSettings.get().copy();
                VideoConfig config = VideoSettings.get().copy();
                config.creativeFovFromModule = this.fromModule;
                config.fovCreative = 140;
                VideoSettings.CONFIG.set(config);
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setItemSlot(EquipmentSlot.HEAD, ContentIds.item(this.goggles).map(ItemStack::new).orElse(ItemStack.EMPTY));
                    ItemStack drone = droneItem();
                    drone.set(CoreComponents.DRONE_BUILD.get(), this.build);
                    player.getInventory().setItem(1, drone);
                    select(player, 1);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Brille und Drohne für den Video-Tab");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            parkMouse(mc);
            if (mc.player == null || !ContentIds.wornGogglesSystem(mc.player).isPresent()
                    || !this.build.equals(mc.player.getMainHandItem().get(CoreComponents.DRONE_BUILD.get()))) {
                return ctx.tick() > 200 ? StepResult.fail("Brille oder Drohne fehlt") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, "video");
                return StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !"video".equals(active.id())) {
                screen.selectTab("video");
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            if (this.saved != null) {
                VideoSettings.CONFIG.set(this.saved);
                this.saved = null;
            }
            ctx.onServer(server -> {
                player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                fillHotbar(player(server));
            });
        }
    }

    private static final class WizardScene implements Screenshots.Scene {
        private final boolean macOption;

        private WizardScene(boolean macOption) {
            this.macOption = macOption;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof ReceiverWizardScreen)) {
                if (ctx.tick() > 200) {
                    return StepResult.fail("Empfänger-Assistent fehlt: " + mc.gui.screen());
                }
                MacControllers.overrideOptionShown(this.macOption);
                ReceiverWizardScreen wizard = ReceiverWizardScreen.standalone(null);
                mc.gui.setScreen(wizard);
                if (this.macOption) {
                    wizard.showPage(ReceiverWizardScreen.Page.DEVICE);
                }
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            MacControllers.overrideOptionShown(null);
        }
    }

    private static final class DeviceToastScene implements Screenshots.Scene {
        private final WizardScene wizard = new WizardScene(false);
        private boolean shown;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            this.shown = false;
            return this.wizard.prepare(ctx);
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            this.wizard.hold(ctx);
            if (!this.shown) {
                this.shown = true;
                TransmitterInput.showDeviceToast(new DeviceInfo(-1, "OpenTX Radiomaster Boxer Joystick", "0300f0f0091200004f54000000000000", false,
                        0x1209, 0x4F54), false);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.toastManager().clear();
            this.wizard.finish(ctx);
        }
    }

    private static final class InventoryIconsScene implements Screenshots.Scene {
        private static final int EMPTY_CRAFTING_SLOT = 1;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.SURVIVAL);
                    fillHotbar(player);
                    Inventory inventory = player.getInventory();
                    int slot = 9;
                    for (Identifier id : List.of(ContentIds.GOGGLES_ANALOG, ContentIds.GOGGLES_DIGITAL, ContentIds.GOGGLES_CREATIVE, ContentIds.GATE_SMALL,
                            ContentIds.GATE_MEDIUM, ContentIds.GATE_LARGE, ContentIds.COURSE_TOOL)) {
                        int target = slot++;
                        ContentIds.item(id).ifPresent(item -> inventory.setItem(target, new ItemStack(item)));
                    }
                    inventory.setItem(slot, Batteries.stackOf(BatteryType.B6S_4000_LION, 0.93F));
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Inventar-Szene");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.player.hasInfiniteMaterials() || mc.player.getInventory().getItem(9).isEmpty()) {
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, EMPTY_CRAFTING_SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.setGameMode(GameType.CREATIVE);
                fillHotbar(player);
            });
        }
    }

    private static final class DroneInventoryScene implements Screenshots.Scene {
        private static final int EMPTY_CRAFTING_SLOT = 1;
        private static final int FIRST_ROW = 9;
        private static final int SECOND_ROW = 18;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.SURVIVAL);
                    fillHotbar(player);
                    Inventory inventory = player.getInventory();
                    int slot = FIRST_ROW;
                    for (DroneBuild preset : DroneBuild.PRESETS) {
                        inventory.setItem(slot++, ContentTab.presetDrone(preset));
                    }
                    ItemStack racer = ContentTab.presetDrone(DroneBuild.RACER);
                    PaintItems.store(racer, PaintPreset.RACE_TEAM.paint());
                    inventory.setItem(SECOND_ROW, racer);
                    ItemStack whoop = ContentTab.presetDrone(DroneBuild.WHOOP.withAccessory(AccessoryType.LED_STRIP));
                    PaintItems.store(whoop, PaintPreset.NEON.paint());
                    inventory.setItem(SECOND_ROW + 1, whoop);
                    ItemStack cinewhoop = ContentTab.presetDrone(DroneBuild.CINEWHOOP);
                    cinewhoop.set(CoreComponents.PART_WEAR.get(), damagedProps());
                    inventory.setItem(SECOND_ROW + 2, cinewhoop);
                    inventory.setItem(SECOND_ROW + 3, ContentTab.presetDrone(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY)
                            .withAccessory(AccessoryType.LED_STRIP)));
                    ItemStack longRange = ContentTab.presetDrone(DroneBuild.LONG_RANGE.withAccessory(AccessoryType.ACTION_CAM_LIGHT));
                    longRange.set(CoreComponents.BATTERY_INSTALLED.get(), Boolean.FALSE);
                    inventory.setItem(SECOND_ROW + 4, longRange);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Drohnen-Inventar");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.player.hasInfiniteMaterials() || mc.player.getInventory().getItem(SECOND_ROW + 4).isEmpty()) {
                return StepResult.pending();
            }
            for (int i = 0; i < DroneBuild.PRESETS.size(); i++) {
                DroneBuild shown = mc.player.getInventory().getItem(FIRST_ROW + i).get(CoreComponents.DRONE_BUILD.get());
                if (!DroneBuild.PRESETS.get(i).equals(shown)) {
                    return StepResult.fail("Slot " + (FIRST_ROW + i) + " trägt nicht " + DroneBuild.PRESETS.get(i).frame().key());
                }
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, EMPTY_CRAFTING_SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.setGameMode(GameType.CREATIVE);
                fillHotbar(player);
            });
        }
    }

    private static final class RecipeBookScene implements Screenshots.Scene {
        private static final Identifier SHOWN = Constants.id("m2306_1860");
        private static final String SEARCH = Constants.MOD_ID + ":";
        private static final String TYPED = "typed";
        private static final String PLACED = "placed";
        private static final int SEARCH_X = 25 + 40;
        private static final int SEARCH_Y = 13 + 7;
        private static final int BOOK_WIDTH = 147;
        private static final int BOOK_HEIGHT = 166;
        private static final int BOOK_OFFSET = 86;
        private static final int RESULT_SLOT = 0;
        private static final int TABLE_DISTANCE = 2;
        private final AtomicReference<BlockPos> table = new AtomicReference<>();

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                if (mc.player == null) {
                    return StepResult.pending();
                }
                mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.SURVIVAL);
                    fillHotbar(player);
                    List<RecipeHolder<?>> recipes = server.getRecipeManager().getRecipes().stream()
                            .filter(holder -> holder.id().identifier().getNamespace().equals(Constants.MOD_ID))
                            .toList();
                    player.awardRecipes(recipes);
                    Inventory inventory = player.getInventory();
                    inventory.setItem(9, new ItemStack(Items.IRON_INGOT, 12));
                    inventory.setItem(10, ContentMaterials.stack(CraftingMaterial.COPPER_COIL).copyWithCount(16));
                    inventory.setItem(11, new ItemStack(Items.QUARTZ, 5));
                    ServerLevel level = player.level();
                    BlockPos pos = player.blockPosition().relative(player.getDirection().getOpposite(), TABLE_DISTANCE);
                    BlockState tableState = Blocks.CRAFTING_TABLE.defaultBlockState();
                    level.setBlockAndUpdate(pos, tableState);
                    this.table.set(pos);
                    player.openMenu(tableState.getMenuProvider(level, pos));
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Rezeptbuch");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!(mc.gui.screen() instanceof CraftingScreen screen) || mc.player == null || mc.gameMode == null) {
                return ctx.tick() > 200 ? StepResult.fail("Werkbank-Menü fehlt: " + mc.gui.screen()) : StepResult.pending();
            }
            if (!state.containsKey(TYPED)) {
                int bookX = (screen.width - BOOK_WIDTH) / 2 - BOOK_OFFSET;
                int bookY = (screen.height - BOOK_HEIGHT) / 2;
                screen.mouseClicked(new MouseButtonEvent(bookX + SEARCH_X, bookY + SEARCH_Y,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                SEARCH.codePoints().forEach(codepoint -> screen.charTyped(new CharacterEvent(codepoint)));
                state.put(TYPED, ctx.tick());
                return StepResult.pending();
            }
            if (!state.containsKey(PLACED)) {
                Optional<RecipeDisplayEntry> entry = shownRecipe(mc);
                if (entry.isEmpty()) {
                    return ctx.tick() > 300 ? StepResult.fail("Rezept " + SHOWN + " fehlt im Rezeptbuch") : StepResult.pending();
                }
                mc.gameMode.handlePlaceRecipe(screen.getMenu().containerId, entry.get().id(), false);
                state.put(PLACED, ctx.tick());
                return StepResult.pending();
            }
            ItemStack result = screen.getMenu().getSlot(RESULT_SLOT).getItem();
            if (!ContentIds.is(result, SHOWN)) {
                return ctx.tick() > 400 ? StepResult.fail("Rezept nicht gelegt: " + result) : StepResult.pending();
            }
            return StepResult.pass();
        }

        private static Optional<RecipeDisplayEntry> shownRecipe(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                return Optional.empty();
            }
            ContextMap context = SlotDisplayContext.fromLevel(mc.level);
            return mc.player.getRecipeBook().getCollections().stream()
                    .flatMap(collection -> collection.getRecipes().stream())
                    .filter(entry -> entry.resultItems(context).stream().anyMatch(stack -> ContentIds.is(stack, SHOWN)))
                    .findFirst();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
            }
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.closeContainer();
                BlockPos pos = this.table.getAndSet(null);
                if (pos != null) {
                    clearBlock(player.level(), pos);
                }
                player.setGameMode(GameType.CREATIVE);
                fillHotbar(player);
            });
        }
    }

    private static final class ThirdPersonScene implements Screenshots.Scene {
        private final CameraType camera;
        private final float yaw;
        private final float pitch;
        private final float bodyTurn;
        private final float headTurn;

        ThirdPersonScene(CameraType camera, float yaw, float pitch, float bodyTurn) {
            this(camera, yaw, pitch, bodyTurn, 0.0F);
        }

        ThirdPersonScene(CameraType camera, float yaw, float pitch, float bodyTurn, float headTurn) {
            this.camera = camera;
            this.yaw = yaw;
            this.pitch = pitch;
            this.bodyTurn = bodyTurn;
            this.headTurn = headTurn;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    BlockPos base = base();
                    place(player, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 0.0F);
                    fillHotbar(player);
                    player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.HEAD, goggles());
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Pilot-Szene");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || ContentIds.wornGoggles(mc.player).isEmpty() || !ContentIds.isRemote(mc.player.getMainHandItem())
                    || !mc.player.getOffhandItem().isEmpty()) {
                return StepResult.pending();
            }
            mc.options.setCameraType(this.camera);
            mc.options.fov().set(50);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) {
                return;
            }
            turn(player, this.yaw, this.pitch);
            if (this.bodyTurn != 0.0F) {
                float body = this.yaw + this.bodyTurn;
                player.setYHeadRot(body);
                player.yHeadRotO = body;
                player.setYBodyRot(body);
                player.yBodyRotO = body;
                if (this.headTurn != 0.0F) {
                    player.setYHeadRot(body + this.headTurn);
                    player.yHeadRotO = body + this.headTurn;
                }
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            ctx.onServer(server -> player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
        }
    }

    private static final class DustTakeoffScene implements Screenshots.Scene {
        private static final double HOVER_HEIGHT = 0.22;
        private static final float HOVER_THROTTLE = 0.195F;
        private static final int PUNCH_TICKS = 3;
        private static final String ARMED = "armed";
        private static final String HELD = "held";
        private static final String LAST_H = "last_h";
        private static final String RESTORE = "restore_dust";
        private static final String SUMMARY = "summary";
        private final float intensity;

        private DustTakeoffScene(float intensity) {
            this.intensity = intensity;
        }

        private static BlockPos pad() {
            return base().offset(0, 0, -5);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                PilotSession.INSTANCE.clearTestInput();
                DustConfig config = DustConfig.get();
                state.put(RESTORE, config.copy());
                config.enabled = true;
                config.intensity = this.intensity;
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    Vec3 start = Vec3.atBottomCenterOf(pad());
                    Vec3 eye = start.add(1.4, 0.0, 1.8);
                    place(player, eye.x, eye.y, eye.z, yawTowards(eye, start), 30.0F);
                    fillHotbar(player);
                    spawnDrone(player.level(), start, yawTowards(start, eye) + 55.0F);
                    startSession(player, false);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Startplatz");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            PilotSession session = PilotSession.INSTANCE;
            if (!session.isLineOfSight() || session.sim() == null) {
                return ctx.tick() > 200 ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
            }
            int since = (int) state.computeIfAbsent(ARMED, key -> ctx.tick());
            int elapsed = ctx.tick() - since;
            session.setTestInput(StickPositions.IDLE, elapsed >= 10, StabMode.ANGLE);
            if (!session.sim().telemetry().armed || elapsed < 20) {
                return elapsed > 140 ? StepResult.fail("nicht scharf: " + session.sim().telemetry().armBlockers) : StepResult.pending();
            }
            mc.options.fov().set(50);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            QuadcopterEntity drone = session.drone();
            if (session.sim() == null || drone == null) {
                return;
            }
            Map<String, Object> state = ctx.state();
            int held = (int) state.merge(HELD, 1, (a, b) -> (int) a + (int) b);
            double height = drone.getY() - pad().getY();
            double last = (double) state.getOrDefault(LAST_H, height);
            double climb = (height - last) * 20.0;
            double error = HOVER_HEIGHT - height;
            double integral = Math.clamp((double) state.getOrDefault(INTEGRAL, 0.0) + error * 0.004, -0.08, 0.08);
            state.put(LAST_H, height);
            state.put(INTEGRAL, integral);
            float throttle = held <= PUNCH_TICKS ? 0.32F
                    : (float) Math.clamp(HOVER_THROTTLE + integral + error * 0.25 - climb * 0.12, 0.08, 0.45);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
            DustModel.Wash wash = DustEffects.lastWash(drone);
            state.put(SUMMARY, String.format(Locale.ROOT, "Höhe %.2f, Reichweite %.2f, %.1f Partikel/s, %d Partikel sichtbar",
                    height, wash.reach(), wash.ratePerSecond(), DustEffects.live()));
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.state().get(SUMMARY) instanceof String summary) {
                ctx.log("Staub bei Staubmenge " + Math.round(this.intensity * 100.0F) + " %: " + summary);
            }
            if (ctx.state().get(RESTORE) instanceof DustConfig saved) {
                DustConfig.get().enabled = saved.enabled;
                DustConfig.get().intensity = saved.intensity;
            }
            PilotSession session = PilotSession.INSTANCE;
            session.clearTestInput();
            if (session.isActive()) {
                session.requestStop();
            }
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                BlockPos base = base();
                place(player, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 0.0F);
                fillHotbar(player);
            });
        }
    }

    private static final class CloseUpScene implements Screenshots.Scene {
        private final DroneBuild build;
        private final PartWear wear;
        private final DronePaint paint;

        private CloseUpScene(DroneBuild build, PartWear wear) {
            this(build, wear, DronePaint.DEFAULT);
        }

        private CloseUpScene(DroneBuild build, PartWear wear, DronePaint paint) {
            this.build = build;
            this.wear = wear;
            this.paint = paint;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                DroneBuild build = this.build;
                PartWear wear = this.wear;
                DronePaint paint = this.paint;
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos base = base();
                    place(player, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 25.0F);
                    select(player, 8);
                    BlockPos stand = base.offset(0, 0, 1);
                    level.setBlockAndUpdate(stand, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    QuadcopterEntity drone = spawnDrone(level, Vec3.atBottomCenterOf(stand.above()), 215.0F, build);
                    drone.setWear(wear);
                    drone.setPaint(paint);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Nahaufnahme");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.level == null || !(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone)
                    || !drone.build().equals(this.build) || !drone.wear().equals(this.wear) || !drone.paint().equals(this.paint)) {
                return ctx.tick() > 200 ? StepResult.fail("Nahaufnahme: Drohne mit Aufbau/Verschleiß kam nicht beim Client an") : StepResult.pending();
            }
            mc.options.fov().set(30);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos stand = base().offset(0, 0, 1);
            if (mc.player != null) {
                look(mc.player, stand.getX() + 0.5, stand.getY() + 1.08, stand.getZ() + 0.5);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                clearBlock(player.level(), base().offset(0, 0, 1));
                select(player, 0);
            });
        }
    }

    private static Vec3 gateCenter(int index) {
        BlockPos base = base();
        return new Vec3(base.getX() + GATE_X[index] + 0.5, base.getY() + 2.0, base.getZ() + GATE_START + GATE_SPACING * index + 0.5);
    }

    private static Vec3 parkPosition() {
        Vec3 gate = gateCenter(0);
        return new Vec3(gate.x, base().getY(), gate.z + 2.5);
    }

    private static void buildCourse(ServerPlayer player) {
        ServerLevel level = player.level();
        ItemStack gates = new ItemStack(RaceModule.GATE_MEDIUM.get(), GATE_COUNT);
        ItemStack previous = player.getMainHandItem().copy();
        float yaw = player.getYRot();
        player.setYRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, gates);
        List<GateEntity> placed = new ArrayList<>();
        try {
            for (int i = 0; i < GATE_COUNT; i++) {
                Vec3 center = gateCenter(i);
                BlockPos ground = BlockPos.containing(center.x, base().getY() - 1, center.z);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
                gates.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(ground.above()).inflate(0.6));
                if (found.size() != 1) {
                    throw new IllegalStateException("Gate " + i + " nicht platziert");
                }
                placed.add(found.getFirst());
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(yaw);
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : placed) {
            CourseToolItem.useOnGate(tool, player, gate);
            GATES.add(gate.getId());
        }
        if (CourseToolItem.courseOf(tool) == GateEntity.NO_COURSE) {
            throw new IllegalStateException("Kurs nicht angelegt");
        }
    }

    private static void startSession(ServerPlayer player, boolean fpv) {
        if (!(player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne fehlt");
        }
        if (drone.pilot().isPresent()) {
            DronePiloting.release(drone);
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        select(player, 0);
        if (fpv) {
            ItemStack worn = goggles();
            worn.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, worn);
        } else {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, fpv ? PilotMode.FPV : PilotMode.LINE_OF_SIGHT);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Start verweigert: " + result);
        }
    }

    private static Vec3 approachPosition() {
        Vec3 gate = gateCenter(0);
        return new Vec3(gate.x, base().getY(), gate.z - 4.0);
    }

    private static boolean passStartGate() {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        if (ClientCourses.all().isEmpty()) {
            return false;
        }
        if (tracker.tracker().racing()) {
            return tracker.nextGateIndex() == 1;
        }
        Vec3 before = gateCenter(0).add(0.0, 0.0, -3.0);
        Vec3 after = parkPosition().add(0.0, 0.1, 0.0);
        long now = tracker.now();
        tracker.selectCourseAt(Minecraft.getInstance(), before);
        tracker.tracker().resetPosition();
        tracker.feed(now, before);
        tracker.feed(now + 150_000_000L, gateCenter(0).add(0.0, -0.8, 1.0));
        tracker.feed(now + 300_000_000L, after);
        return tracker.tracker().racing() && tracker.nextGateIndex() == 1;
    }

    private static final class RaceScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    BlockPos base = base();
                    place(player, base.getX() + 8.5, base.getY(), base.getZ() + 3.5, 45.0F, 10.0F);
                    fillHotbar(player);
                    buildCourse(player);
                    spawnDrone(player.level(), approachPosition(), yawTowards(approachPosition(), gateCenter(0)));
                    startSession(player, false);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Kurs");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            PilotSession session = PilotSession.INSTANCE;
            if (!session.isLineOfSight()) {
                return ctx.tick() > 200 ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
            }
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            if (ClientCourses.all().isEmpty()) {
                return ctx.tick() > 240 ? StepResult.fail("kein Kurs beim Client") : StepResult.pending();
            }
            if (tracker.tracker().phase() != LapTracker.Phase.IDLE) {
                tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
                tracker.tracker().drainEvents();
            }
            tracker.selectCourseAt(mc, approachPosition());
            if (tracker.courseId() == LapTracker.NO_COURSE || RaceLineRenderer.currentPath(0.0F).isEmpty()) {
                return ctx.tick() > 240 ? StepResult.fail("Start-Gate nicht als nächstes Ziel: Kurse=" + ClientCourses.all().size()
                        + " Phase=" + tracker.tracker().phase()) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
        }
    }

    private static void releaseDrone(ServerPlayer player) {
        if (player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone && drone.pilot().isPresent()) {
            DronePiloting.release(drone);
        }
    }

    private static void parkDrone(ServerPlayer player) {
        if (!(player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne fehlt");
        }
        releaseDrone(player);
        Vec3 start = parkPosition().add(0.0, 0.1, 0.0);
        float yaw = yawTowards(start, gateCenter(1));
        drone.resetTo(start, new Quaternionf().rotationY((float) Math.toRadians(-yaw) + (float) Math.PI));
    }

    private static final class GateViewScene implements Screenshots.Scene {
        private final int gate;
        private final double dx;
        private final double dz;
        private final double lookUp;

        private GateViewScene(int gate, double dx, double dz, double lookUp) {
            this.gate = gate;
            this.dx = dx;
            this.dz = dz;
            this.lookUp = lookUp;
        }

        private Vec3 target() {
            return gateCenter(this.gate).add(0.0, this.lookUp, 0.0);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                PilotSession.INSTANCE.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    releaseDrone(player);
                    Vec3 center = gateCenter(this.gate);
                    Vec3 feet = new Vec3(center.x + this.dx, base().getY(), center.z + this.dz);
                    place(player, feet.x, feet.y, feet.z, yawTowards(feet, center), 0.0F);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Gate-Ansicht");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.level == null || mc.player == null || PilotSession.INSTANCE.isActive() || RaceLineRenderer.active()) {
                return ctx.tick() > 200 ? StepResult.fail("Rennen oder Sitzung läuft noch") : StepResult.pending();
            }
            if (GATES.size() <= this.gate || !(mc.level.getEntity(GATES.get(this.gate)) instanceof GateEntity entity)) {
                return StepResult.fail("Gate " + this.gate + " fehlt");
            }
            GateRenderer.Marking expected = this.gate == 0 ? GateRenderer.Marking.START : GateRenderer.Marking.NUMBER;
            if (GateRenderer.marking(entity) != expected) {
                return ctx.tick() > 200 ? StepResult.fail("Markierung " + GateRenderer.marking(entity) + " statt " + expected) : StepResult.pending();
            }
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                Vec3 target = this.target();
                look(player, target.x, target.y, target.z);
            }
        }
    }

    private static final class GateStackScene implements Screenshots.Scene {
        private static final int OFFSET_X = -16;
        private static final int OFFSET_Z = 14;
        private final List<Integer> ids = new ArrayList<>();
        private int course = GateEntity.NO_COURSE;

        private Vec3 target() {
            BlockPos base = base();
            return new Vec3(base.getX() + OFFSET_X + GateStackSelfTests.DOUBLE_OFFSET * 0.5 + 0.5, base.getY() + 4.6,
                    base.getZ() + OFFSET_Z + 0.5);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                PilotSession.INSTANCE.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    releaseDrone(player);
                    BlockPos base = base();
                    GateStackSelfTests.Built built = GateStackSelfTests.build(player, base.getX() + OFFSET_X, base.getY(), base.getZ() + OFFSET_Z);
                    for (GateEntity gate : built.gates()) {
                        this.ids.add(gate.getId());
                    }
                    this.course = built.course();
                    Vec3 center = this.target();
                    Vec3 feet = new Vec3(center.x - 1.5, base.getY(), center.z - 15.0);
                    place(player, feet.x, feet.y, feet.z, yawTowards(feet, center), 0.0F);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Gate-Stapel");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.level == null || mc.player == null || PilotSession.INSTANCE.isActive() || RaceLineRenderer.active()) {
                return ctx.tick() > 200 ? StepResult.fail("Rennen oder Sitzung läuft noch") : StepResult.pending();
            }
            boolean[] covered = {true, true, false, true, false};
            for (int i = 0; i < this.ids.size(); i++) {
                if (!(mc.level.getEntity(this.ids.get(i)) instanceof GateEntity gate) || gate.covered() != covered[i] || !gate.inCourse()) {
                    return ctx.tick() > 200 ? StepResult.fail("Gate-Stapel " + i + " nicht beim Client") : StepResult.pending();
                }
            }
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                Vec3 target = this.target();
                look(player, target.x, target.y, target.z);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            List<Integer> placed = List.copyOf(this.ids);
            int id = this.course;
            this.ids.clear();
            ctx.onServer(server -> {
                GateStackSelfTests.discard(player(server).level(), placed);
                CourseRegistry registry = CourseRegistry.get();
                if (registry != null && id != GateEntity.NO_COURSE) {
                    registry.deleteCourse(id);
                }
            });
        }
    }

    private static final class RaceLineThirdPersonScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                PilotSession.INSTANCE.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    parkDrone(player);
                    Vec3 center = gateCenter(0);
                    place(player, center.x - 1.5, base().getY(), center.z - 7.0, 0.0F, 10.0F);
                    startSession(player, false);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Sichtflug im Rennen");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            PilotSession session = PilotSession.INSTANCE;
            if (!session.isLineOfSight()) {
                return ctx.tick() > 200 ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
            }
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            if (!passStartGate()) {
                return ctx.tick() > 260 ? StepResult.fail("Rennen startet nicht") : StepResult.pending();
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (RaceLineRenderer.active() && RaceLineRenderer.lastSamples() > 0) {
                return StepResult.pass();
            }
            return ctx.tick() > 320 ? StepResult.fail("Ideallinie wird im Rennen nicht gezeichnet") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
            PilotSession.INSTANCE.clearTestInput();
        }
    }

    private static final class RaceLineFpvScene implements Screenshots.Scene {
        private static final String UP = "up";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (!state.containsKey(FUTURE)) {
                session.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    parkDrone(player);
                    startSession(player, true);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "FPV im Rennen");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!session.isFpv()) {
                return ctx.tick() > 200 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
            }
            if (!state.containsKey(UP)) {
                if (!passStartGate()) {
                    session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                    return ctx.tick() > 260 ? StepResult.fail("Rennen startet im FPV nicht") : StepResult.pending();
                }
                StepResult up = flyUp(ctx, base().getY() + HOVER_HEIGHT);
                if (up.status() != StepResult.Status.PASS) {
                    return up;
                }
                state.put(UP, Boolean.TRUE);
            }
            this.hold(ctx);
            if (RaceLineRenderer.active() && RaceLineRenderer.lastSamples() > 0) {
                return StepResult.pass();
            }
            return ctx.tick() > 900 ? StepResult.fail("Ideallinie im FPV nicht gezeichnet") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }
    }

    private static float holdAltitude(SelfTest.StepContext ctx, PilotSession session) {
        Map<String, Object> state = ctx.state();
        double y = session.comPosition(new Vector3d()).y;
        double target = (double) state.getOrDefault(TARGET_Y, y);
        double last = (double) state.getOrDefault(LAST_Y, y);
        double integral = (double) state.getOrDefault(INTEGRAL, 0.0);
        double vy = (y - last) * 20.0;
        double error = target - y;
        integral = Math.max(-0.2, Math.min(0.2, integral + error * 0.004));
        state.put(LAST_Y, y);
        state.put(INTEGRAL, integral);
        return (float) Math.max(0.05, Math.min(0.7, 0.28 + integral + error * 0.12 - vy * 0.08));
    }

    private static StepResult flyUp(SelfTest.StepContext ctx, double targetY) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (sim == null) {
            return StepResult.fail("keine Sitzung");
        }
        Map<String, Object> state = ctx.state();
        int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
        int elapsed = ctx.tick() - since;
        if (elapsed < 20 || !sim.telemetry().armed) {
            session.setTestInput(StickPositions.IDLE, sim.telemetry().armed || (elapsed >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5), StabMode.ANGLE);
            return elapsed > 120 ? StepResult.fail("nicht scharf: " + sim.telemetry().armBlockers + " " + poseReport(session, sim))
                    : StepResult.pending();
        }
        state.put(TARGET_Y, targetY);
        float throttle = holdAltitude(ctx, session);
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        double y = session.comPosition(new Vector3d()).y;
        if (Math.abs(y - targetY) < 0.35 && Math.abs((double) state.getOrDefault(LAST_Y, y) - y) < 0.02) {
            state.put(DONE, Boolean.TRUE);
            return StepResult.pass();
        }
        return elapsed > 600 ? StepResult.fail("Höhe nicht erreicht: " + y + " statt " + targetY) : StepResult.pending();
    }

    private static String poseReport(PilotSession session, DroneSim sim) {
        Telemetry t = sim.telemetry();
        Vector3d com = session.comPosition(new Vector3d());
        QuadcopterEntity drone = session.drone();
        String below = "";
        Minecraft mc = Minecraft.getInstance();
        if (drone != null && mc.level != null) {
            below = mc.level.getBlockState(drone.blockPosition().below()).toString() + "/" + mc.level.getBlockState(drone.blockPosition());
        }
        return String.format(Locale.ROOT, "(Neigung %.1f°, Roll %.1f°, Nick %.1f°, am Boden %s, Schwerpunkt %.2f %.2f %.2f, Entity %s, Block %s)",
                t.tiltDeg, t.rollDeg, t.pitchDeg, t.grounded, com.x, com.y, com.z, drone == null ? "-" : drone.position(), below);
    }

    private static final class OsdScene implements Screenshots.Scene {
        private static final String HELD = "held";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                PilotSession.INSTANCE.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> startSession(player(server), true)));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "FPV-Start");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            PilotSession session = PilotSession.INSTANCE;
            if (!session.isFpv()) {
                return ctx.tick() > 200 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
            }
            if (!passStartGate()) {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                return ctx.tick() > 260 ? StepResult.fail("Rennen startet im FPV nicht") : StepResult.pending();
            }
            return flyUp(ctx, base().getY() + HOVER_HEIGHT);
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            int held = ctx.state().merge(HELD, 1, (a, b) -> (int) a + (int) b) instanceof Integer count ? count : 0;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, held > 8 ? 0.35F : 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }
    }

    private static final class FormatCompareStep {
        private static final String SHOT_INDEX = "format_shot";
        private static final String SHOT_SINCE = "format_since";
        private static final String SHOT_FILE = "format_file";
        private static final String SAVED_VIDEO = "format_saved";
        private static final String STEADY_SINCE = "format_steady";
        private static final String FIRST_POSE = "format_pose";
        private static final int STEADY_TICKS = 40;
        private static final int FIRST_SETTLE_TICKS = 10;
        private static final int NEXT_SETTLE_TICKS = 3;
        private static final List<Shot> SHOTS = List.of(new Shot("fpv_format_wide", ImageFormat.WIDE, Screenshots.GUI_SCALE),
                new Shot("fpv_format_4_3", ImageFormat.FOUR_THREE, Screenshots.GUI_SCALE), new Shot("fpv_format_4_3_gui2", ImageFormat.FOUR_THREE, 2),
                new Shot("fpv_format_4_3_gui4", ImageFormat.FOUR_THREE, 4));

        @SuppressWarnings("unchecked")
        StepResult run(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (!state.containsKey(FUTURE)) {
                state.put(SAVED_VIDEO, VideoSettings.get().copy());
                session.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    parkDrone(player);
                    startSession(player, true);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "FPV für den Bildformat-Vergleich");
            if (server.status() != StepResult.Status.PASS) {
                return this.finish(ctx, server);
            }
            if (!session.isFpv()) {
                return ctx.tick() > 200 ? this.finish(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
            }
            if (!state.containsKey(DONE)) {
                StepResult up = flyUp(ctx, base().getY() + HOVER_HEIGHT);
                if (up.status() == StepResult.Status.FAIL) {
                    return this.finish(ctx, up);
                }
                if (up.status() != StepResult.Status.PASS) {
                    return up;
                }
            }
            this.hold(ctx);
            int steady = (int) state.computeIfAbsent(STEADY_SINCE, key -> ctx.tick());
            if (ctx.tick() - steady < STEADY_TICKS) {
                return StepResult.pending();
            }
            int index = (int) state.getOrDefault(SHOT_INDEX, -1);
            if (index < 0 || state.containsKey(SHOT_FILE) && ((CompletableFuture<String>) state.get(SHOT_FILE)).isDone()) {
                if (index >= 0) {
                    CompletableFuture<String> done = (CompletableFuture<String>) state.remove(SHOT_FILE);
                    if (done.isCompletedExceptionally()) {
                        return this.finish(ctx, StepResult.fail("Bildschirmfoto " + SHOTS.get(index).file() + ": " + done.exceptionNow()));
                    }
                    ctx.log(done.join());
                }
                index++;
                if (index >= SHOTS.size()) {
                    return this.finish(ctx, StepResult.pass());
                }
                Shot shot = SHOTS.get(index);
                VideoConfig config = VideoSettings.get().copy();
                config.imageFormat = shot.format();
                VideoSettings.CONFIG.set(config);
                if (mc.options.guiScale().get() != shot.guiScale()) {
                    mc.options.guiScale().set(shot.guiScale());
                    mc.resizeGui();
                }
                Screenshots.clearOverlays(mc);
                state.put(SHOT_INDEX, index);
                state.put(SHOT_SINCE, ctx.tick());
                return StepResult.pending();
            }
            int settle = index == 0 ? FIRST_SETTLE_TICKS : NEXT_SETTLE_TICKS;
            if (!state.containsKey(SHOT_FILE) && ctx.tick() - (int) state.get(SHOT_SINCE) >= settle) {
                Shot shot = SHOTS.get(index);
                Camera camera = mc.gameRenderer.mainCamera();
                Vec3 position = camera.position();
                Quaternionf rotation = new Quaternionf(camera.rotation());
                String drift = "";
                if (state.get(FIRST_POSE) instanceof Pose first) {
                    double angle = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(first.rotation().dot(rotation)))));
                    drift = String.format(Locale.ROOT, ", Abweichung zur Lage des 16:9-Bilds %.3f Blöcke, %.2f°", first.position().distanceTo(position), angle);
                } else {
                    state.put(FIRST_POSE, new Pose(position, rotation));
                }
                ctx.log("Bildformat " + shot.format() + ", GUI-Skala " + mc.options.guiScale().get() + ", Bildbereich " + FpvImage.area()
                        + String.format(Locale.ROOT, ", Sichtfeld %.1f° hoch, Höhe %.2f", camera.getFov(), session.comPosition(new Vector3d()).y) + drift);
                state.put(SHOT_FILE, Screenshots.capture(shot.file()));
            }
            return StepResult.pending();
        }

        private void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }

        private StepResult finish(SelfTest.StepContext ctx, StepResult result) {
            if (ctx.state().remove(SAVED_VIDEO) instanceof VideoConfig saved) {
                VideoSettings.CONFIG.set(saved);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.guiScale().get() != Screenshots.GUI_SCALE) {
                mc.options.guiScale().set(Screenshots.GUI_SCALE);
                mc.resizeGui();
            }
            return result;
        }

        private record Shot(String file, ImageFormat format, int guiScale) {
        }

        private record Pose(Vec3 position, Quaternionf rotation) {
        }
    }

    private static final class RecOsdScene implements Screenshots.Scene {
        private static final String UP = "up";
        private static final String STARTED = "started";
        private static final long RECORD_NANOS = 2_300_000_000L;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
            if (!state.containsKey(FUTURE)) {
                session.clearTestInput();
                recorder.cancelAll();
                recorder.overrideFormat(null);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    if (!(player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
                        throw new IllegalStateException("Drohne fehlt");
                    }
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY));
                    drone.setCameraCharge(1.0F);
                    Vec3 start = parkPosition().add(0.0, 0.1, 0.0);
                    float yaw = yawTowards(start, gateCenter(1));
                    drone.resetTo(start, new Quaternionf().rotationY((float) Math.toRadians(-yaw) + (float) Math.PI));
                    startSession(player, true);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Action-Cam-Drohne");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            QuadcopterEntity drone = session.drone();
            if (!session.isFpv() || drone == null || ActionCamRecorder.cameraOf(drone).orElse(null) != AccessoryType.ACTION_CAM_HEAVY) {
                return ctx.tick() > 240 ? StepResult.fail("keine FPV-Sitzung mit Action-Cam") : StepResult.pending();
            }
            if (!state.containsKey(UP)) {
                StepResult up = flyUp(ctx, base().getY() + HOVER_HEIGHT);
                if (up.status() != StepResult.Status.PASS) {
                    return up;
                }
                state.put(UP, Boolean.TRUE);
            }
            this.hold(ctx);
            if (!state.containsKey(STARTED)) {
                ActionCamRecorder.Result result = recorder.start(mc, false);
                if (result != ActionCamRecorder.Result.STARTED) {
                    return StepResult.fail("Aufnahme startet nicht: " + result);
                }
                state.put(STARTED, Boolean.TRUE);
                return StepResult.pending();
            }
            if (!recorder.recording()) {
                return StepResult.fail("Aufnahme endete vorzeitig: " + recorder.lastError());
            }
            return recorder.recordedNanos() >= RECORD_NANOS ? StepResult.pass()
                    : ctx.tick() > 900 ? StepResult.fail("Aufnahmezeit läuft nicht") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }
    }

    private static final class RecordingsScene implements Screenshots.Scene {
        private static final String STEP = "rec_step";
        private static final String SAVED = "rec_saved";
        private static final long SECOND_CLIP_NANOS = 1_200_000_000L;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
            int step = (int) state.getOrDefault(STEP, 0);
            this.hold(ctx);
            switch (step) {
                case 0 -> {
                    if (recorder.recording()) {
                        recorder.stop(mc, ActionCamRecorder.StopReason.USER);
                    }
                    state.put(STEP, 1);
                    return StepResult.pending();
                }
                case 1 -> {
                    if (recorder.phase() != ActionCamRecorder.Phase.IDLE || !recorder.saving().isEmpty()) {
                        return ctx.tick() > 900 ? StepResult.fail("erstes Video wird nicht fertig") : StepResult.pending();
                    }
                    Path saved = recorder.lastSaved();
                    if (saved == null) {
                        return StepResult.fail("erstes Video nicht gespeichert: " + recorder.lastError());
                    }
                    state.put(SAVED, saved);
                    state.put(FUTURE, RecordingThumbnails.flushed());
                    state.put(STEP, 2);
                    return StepResult.pending();
                }
                case 2 -> {
                    if (!future(ctx).isDone()) {
                        return StepResult.pending();
                    }
                    ActionCamRecorder.Result result = recorder.start(mc, false);
                    if (result != ActionCamRecorder.Result.STARTED) {
                        return StepResult.fail("zweite Aufnahme startet nicht: " + result);
                    }
                    state.put(STEP, 3);
                    return StepResult.pending();
                }
                case 3 -> {
                    if (recorder.recordedNanos() < SECOND_CLIP_NANOS) {
                        return ctx.tick() > 1200 ? StepResult.fail("zweite Aufnahme läuft nicht") : StepResult.pending();
                    }
                    RecordingsScreen.open(null);
                    state.put(STEP, 4);
                    return StepResult.pending();
                }
                default -> {
                    if (!(mc.gui.screen() instanceof RecordingsScreen screen)) {
                        return StepResult.fail("Aufnahmen-Liste nicht offen");
                    }
                    if (screen.clips().isEmpty() || screen.loadedThumbnails() == 0 || screen.frames() < 5) {
                        return ctx.tick() > 1400 ? StepResult.fail("Liste ohne Video oder Vorschaubild") : StepResult.pending();
                    }
                    return StepResult.pass();
                }
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            if (session.isActive()) {
                ctx.state().putIfAbsent(TARGET_Y, session.comPosition(new Vector3d()).y);
                float throttle = holdAltitude(ctx, session);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
            if (mc.gui.screen() instanceof RecordingsScreen) {
                mc.gui.setScreen(null);
            }
            recorder.cancelAll();
            recorder.overrideFormat(null);
            if (ctx.state().get(SAVED) instanceof Path saved) {
                try {
                    recorder.library().delete(saved);
                } catch (IOException e) {
                    ctx.log("Tour-Video nicht gelöscht: " + e.getMessage());
                }
            }
            PilotSession.INSTANCE.clearTestInput();
        }
    }

    private static void abortRace() {
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
    }

    private static Vec3 startPosition() {
        Vec3 gate = gateCenter(0);
        return new Vec3(gate.x, base().getY() + 0.1, gate.z - 2.6);
    }

    private static GhostTrack ghostTrack(CourseData course, String frame) {
        List<Vec3> path = new ArrayList<>();
        path.add(gateCenter(0).add(0.0, -0.2, -2.5));
        for (int i = 0; i < GATE_COUNT; i++) {
            path.add(gateCenter(i).add(0.0, -0.2, 0.0));
        }
        path.add(gateCenter(GATE_COUNT - 1).add(0.0, -0.2, 6.0));
        long interval = GhostTrack.INTERVAL_NANOS;
        int samples = (int) (GhostScene.LAP_NANOS / interval) + 1;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        Quaternionf rotation = new Quaternionf();
        for (int i = 0; i < samples; i++) {
            double along = GhostScene.SPEED * i * interval / 1.0E9;
            int segment = 0;
            while (segment < path.size() - 2 && along > path.get(segment).distanceTo(path.get(segment + 1))) {
                along -= path.get(segment).distanceTo(path.get(segment + 1));
                segment++;
            }
            Vec3 a = path.get(segment);
            Vec3 b = path.get(segment + 1);
            double length = Math.max(1.0E-6, a.distanceTo(b));
            Vec3 at = a.add(b.subtract(a).scale(Math.min(1.0, along / length)));
            Vec3 direction = b.subtract(a).normalize();
            positions[i * 3] = at.x;
            positions[i * 3 + 1] = at.y + 0.15 * Math.sin(i * 0.2);
            positions[i * 3 + 2] = at.z;
            rotation.rotationY((float) Math.atan2(-direction.x, -direction.z)).rotateX(-0.3F);
            rotations[i * 4] = rotation.x;
            rotations[i * 4 + 1] = rotation.y;
            rotations[i * 4 + 2] = rotation.z;
            rotations[i * 4 + 3] = rotation.w;
        }
        return new GhostTrack(interval, positions, rotations, GhostScene.LAP_NANOS, frame, course.layoutHash());
    }

    private static final class GhostScene implements Screenshots.Scene {
        static final double SPEED = 3.2;
        static final long LAP_NANOS = 30L * LapTracker.SECOND;
        private static final String ABORTED = "ghost_aborted";
        private static final String GHOST = "ghost";
        private static final String BUILD = "ghost_build";
        private final DroneBuild build;
        private final DronePaint paint;
        private final int fov;

        private GhostScene() {
            this(DroneBuild.FREESTYLE, DronePaint.DEFAULT, 0);
        }

        private GhostScene(DroneBuild build, DronePaint paint, int fov) {
            this.build = build;
            this.paint = paint;
            this.fov = fov;
        }

        private boolean custom() {
            return !this.build.equals(DroneBuild.FREESTYLE);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            String frame = this.build.frame().key();
            if (!state.containsKey(FUTURE)) {
                PilotSession.INSTANCE.clearTestInput();
                DroneBuild shown = this.custom() ? this.build : null;
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    parkDrone(player);
                    if (shown != null && player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
                        drone.setBuild(shown);
                    }
                    Vec3 center = gateCenter(0);
                    place(player, center.x - 1.8, base().getY(), center.z - 6.5, 0.0F, 8.0F);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    startSession(player, false);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Geister-Rennen");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            PilotSession session = PilotSession.INSTANCE;
            if (!session.isLineOfSight()) {
                return ctx.tick() > 200 ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
            }
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            if (this.custom() && !state.containsKey(BUILD)) {
                QuadcopterEntity drone = session.drone();
                if (drone == null || !drone.build().equals(this.build) || !frame.equals(tracker.frame())) {
                    return ctx.tick() > 240 ? StepResult.fail("Drohne der Klasse " + frame + " kommt nicht an") : StepResult.pending();
                }
                if (this.fov > 0) {
                    mc.options.fov().set(this.fov);
                }
                state.put(BUILD, Boolean.TRUE);
            }
            if (!state.containsKey(ABORTED)) {
                abortRace();
                state.put(ABORTED, Boolean.TRUE);
                return StepResult.pending();
            }
            if (!state.containsKey(GHOST)) {
                RaceHud.clear();
                if (tracker.course() == null) {
                    tracker.selectCourseAt(mc, startPosition());
                }
                CourseData course = tracker.course();
                if (course == null) {
                    return ctx.tick() > 240 ? StepResult.fail("kein Kurs für den Geist") : StepResult.pending();
                }
                tracker.ghosts().setPersist(false);
                tracker.syncGhostsFor(mc, frame);
                GhostTrack track = ghostTrack(course, frame);
                if (this.custom()) {
                    track = track.withLook(GhostLook.of(this.build, this.paint));
                }
                if (!tracker.ghosts().offerLap(mc, course, track, false)) {
                    return StepResult.fail("Geisterspur nicht angenommen");
                }
                state.put(GHOST, Boolean.TRUE);
            }
            if (!passStartGate()) {
                return ctx.tick() > 400 ? StepResult.fail("Rennen mit Geist startet nicht") : StepResult.pending();
            }
            GhostTrack personal = tracker.ghosts().personal();
            if (personal == null) {
                return StepResult.fail("Geist fehlt nach dem Start");
            }
            if (this.custom() && (personal.look() == null || !personal.look().build().equals(this.build))) {
                return StepResult.fail("Geist trägt nicht den Bauplan " + frame);
            }
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            GhostTrack ghost = tracker.ghosts().personal();
            LocalPlayer player = Minecraft.getInstance().player;
            double[] position = new double[3];
            if (ghost != null && player != null && ghost.pose(tracker.tracker().lapElapsedNanos(tracker.now()), position, new Quaternionf())) {
                look(player, position[0], position[1] - 0.4, position[2] + 1.5);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            tracker.ghosts().clear();
            tracker.ghosts().setPersist(true);
            if (this.fov > 0 && savedFov > 0) {
                Minecraft.getInstance().options.fov().set(savedFov);
            }
            if (this.custom()) {
                ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    if (player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
                        drone.setBuild(DroneBuild.FREESTYLE);
                    }
                });
            }
        }
    }

    private static final class StartLightsScene implements Screenshots.Scene {
        private static final long COUNTDOWN_NANOS = 5L * LapTracker.SECOND;
        private static final long SHOT_REMAINING_NANOS = 2_400_000_000L;
        private static final long GO_WINDOW_NANOS = 350_000_000L;
        private static final String ABORTED = "start_aborted";
        private static final String COUNTING = "counting";
        private final boolean go;

        private StartLightsScene(boolean go) {
            this.go = go;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            PilotSession session = PilotSession.INSTANCE;
            if (this.go) {
                this.hold(ctx);
                long remaining = tracker.tracker().countdownRemainingNanos(tracker.now());
                if (remaining >= 0L) {
                    return ctx.tick() > 200 ? StepResult.fail("Countdown endet nicht") : StepResult.pending();
                }
                if (!tracker.tracker().racing() || tracker.sinceGoNanos() > GO_WINDOW_NANOS) {
                    return StepResult.fail("kein LOS nach dem Countdown (Phase " + tracker.tracker().phase() + ")");
                }
                return StepResult.pass();
            }
            if (!state.containsKey(FUTURE)) {
                session.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    if (!(player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
                        throw new IllegalStateException("Drohne fehlt");
                    }
                    releaseDrone(player);
                    Vec3 start = startPosition();
                    float yaw = yawTowards(start, gateCenter(1));
                    drone.resetTo(start, new Quaternionf().rotationY((float) Math.toRadians(-yaw) + (float) Math.PI));
                    place(player, start.x - 1.4, base().getY(), start.z - 3.2, 0.0F, 12.0F);
                    startSession(player, false);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Startaufstellung");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!session.isLineOfSight()) {
                return ctx.tick() > 200 ? StepResult.fail("keine Sichtflug-Sitzung am Start") : StepResult.pending();
            }
            this.hold(ctx);
            if (!state.containsKey(ABORTED)) {
                abortRace();
                state.put(ABORTED, Boolean.TRUE);
                return StepResult.pending();
            }
            if (!state.containsKey(COUNTING)) {
                tracker.selectCourseAt(mc, startPosition());
                if (tracker.course() == null || !tracker.beginCountdown(tracker.now(), COUNTDOWN_NANOS, 1)) {
                    return ctx.tick() > 300 ? StepResult.fail("Countdown startet nicht") : StepResult.pending();
                }
                state.put(COUNTING, Boolean.TRUE);
                return StepResult.pending();
            }
            long remaining = tracker.tracker().countdownRemainingNanos(tracker.now());
            if (remaining < 0L) {
                return StepResult.fail("Countdown lief ab, bevor das Bild entstand");
            }
            return remaining <= SHOT_REMAINING_NANOS ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                Vec3 gate = gateCenter(0);
                look(player, gate.x, gate.y - 0.6, gate.z);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.go) {
                PilotSession.INSTANCE.clearTestInput();
                abortRace();
            }
        }
    }

    private static void seedLeaderboard(MinecraftServer server, int courseId) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(courseId);
        if (course == null) {
            throw new IllegalStateException("Kurs " + courseId + " fehlt auf dem Server");
        }
        Leaderboard board = registry.board(course);
        ServerPlayer player = player(server);
        long now = System.currentTimeMillis();
        long hour = 3_600_000L;
        String[] names = {"Lotte", "Mika", "Jonas", "Ava", "Kenji", "Nora", "Felix", "Ida"};
        String[] frames = {"race_5", "freestyle_5", "race_5", "whoop_65", "freestyle_5", "race_5", "long_range_7", "cinewhoop_3"};
        long[] millis = {12_480L, 13_115L, 13_902L, 14_260L, 15_031L, 15_744L, 17_390L, 19_215L};
        for (int i = 0; i < names.length; i++) {
            board.submit(new UUID(0x70A2L, i), names[i], frames[i], millis[i] * 1_000_000L, now - (i + 1) * 7L * hour);
        }
        board.submit(player.getUUID(), player.getGameProfile().name(), DroneBuild.FREESTYLE.frame().key(), 14_677L * 1_000_000L, now - hour);
        registry.records().changed();
    }

    private static final class LeaderboardScene implements Screenshots.Scene {
        private static final String OPENED = "opened";
        private static final String COURSE = "course";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                if (tracker.course() == null) {
                    tracker.selectCourseAt(mc, startPosition());
                }
                int course = tracker.courseId();
                if (course == LapTracker.NO_COURSE) {
                    return ctx.tick() > 100 ? StepResult.fail("kein Kurs für die Bestenliste") : StepResult.pending();
                }
                state.put(COURSE, course);
                state.put(FUTURE, ctx.onServer(server -> seedLeaderboard(server, course)));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Bestenliste");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            int course = (int) state.get(COURSE);
            if (!state.containsKey(OPENED)) {
                PilotSession.INSTANCE.clearTestInput();
                mc.gui.setScreen(new LeaderboardScreen(null, course));
                state.put(OPENED, Boolean.TRUE);
                return StepResult.pending();
            }
            LeaderboardScreen screen = LeaderboardScreen.active();
            if (screen == null || ClientLeaderboards.get(course) == null || screen.frames() < 2 || screen.shownRows().size() < 5) {
                return ctx.tick() > 300 ? StepResult.fail("Bestenliste nicht geladen") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof LeaderboardScreen) {
                mc.gui.setScreen(null);
            }
        }
    }

    private static final class TrickScene implements Screenshots.Scene {
        private static final double ALTITUDE = 9.0;
        private static final double FLIP_RATE_DPS = 650.0;
        private static final int FLIP_TICKS = 11;
        private static final float FLIP_THROTTLE = 0.3F;
        private static final String STAGE = "trick_stage";
        private static final String FLIP_AT = "flip_at";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (!session.isFpv() || sim == null) {
                return ctx.tick() > 100 ? StepResult.fail("keine FPV-Sitzung für den Trick") : StepResult.pending();
            }
            int stage = (int) state.getOrDefault(STAGE, 0);
            switch (stage) {
                case 0 -> {
                    abortRace();
                    state.put(STAGE, 1);
                    return StepResult.pending();
                }
                case 1 -> {
                    StepResult up = flyUp(ctx, base().getY() + ALTITUDE);
                    if (up.status() != StepResult.Status.PASS) {
                        return up;
                    }
                    if (!TrickTracker.INSTANCE.active()) {
                        return StepResult.fail("Trick-Erkennung für die Drohne aus");
                    }
                    state.put(STAGE, 2);
                    state.put(FLIP_AT, ctx.tick());
                    return StepResult.pending();
                }
                case 2 -> {
                    int since = ctx.tick() - (int) state.get(FLIP_AT);
                    if (since < FLIP_TICKS) {
                        float pitch = (float) StickMath.rateToStick(FLIP_RATE_DPS, sim.config().rates, Axis.PITCH);
                        session.setTestInput(new StickPositions(0.0F, pitch, 0.0F, FLIP_THROTTLE), true, StabMode.ACRO);
                        return StepResult.pending();
                    }
                    state.put(STAGE, 3);
                    return StepResult.pending();
                }
                default -> {
                    this.hold(ctx);
                    if (TrickTracker.INSTANCE.session().score().tricks() > 0) {
                        return StepResult.pass();
                    }
                    int since = ctx.tick() - (int) state.get(FLIP_AT);
                    return since > 120 ? StepResult.fail("kein Trick erkannt") : StepResult.pending();
                }
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            if (session.isActive() && (int) ctx.state().getOrDefault(STAGE, 0) >= 3) {
                float throttle = holdAltitude(ctx, session);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
        }
    }

    private static final class TrickChainScene implements Screenshots.Scene {
        private static final double ALTITUDE = 18.0;
        private static final double RATE_DPS = 650.0;
        private static final int FLICK_TICKS = 6;
        private static final int HALF_ROLL_TICKS = 6;
        private static final int ROLL_TICKS = 11;
        private static final String STAGE = "chain_stage";
        private static final String CHAIN_AT = "chain_at";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (!session.isFpv() || sim == null) {
                return ctx.tick() > 100 ? StepResult.fail("keine FPV-Sitzung für die Kette") : StepResult.pending();
            }
            int stage = (int) state.getOrDefault(STAGE, 0);
            switch (stage) {
                case 0 -> {
                    abortRace();
                    state.put(STAGE, 1);
                    return StepResult.pending();
                }
                case 1 -> {
                    StepResult up = flyUp(ctx, base().getY() + ALTITUDE);
                    if (up.status() != StepResult.Status.PASS) {
                        return up;
                    }
                    if (!TrickTracker.INSTANCE.active()) {
                        return StepResult.fail("Trick-Erkennung für die Drohne aus");
                    }
                    state.put(STAGE, 2);
                    state.put(CHAIN_AT, ctx.tick());
                    return StepResult.pending();
                }
                case 2 -> {
                    int since = ctx.tick() - (int) state.get(CHAIN_AT);
                    float stick = (float) StickMath.rateToStick(RATE_DPS, sim.config().rates, Axis.PITCH);
                    float rollStick = (float) StickMath.rateToStick(RATE_DPS, sim.config().rates, Axis.ROLL);
                    if (since < FLICK_TICKS) {
                        session.setTestInput(new StickPositions(0.0F, stick, 0.0F, 0.55F), true, StabMode.ACRO);
                    } else if (since < FLICK_TICKS + HALF_ROLL_TICKS) {
                        session.setTestInput(new StickPositions(rollStick, 0.0F, 0.0F, 0.3F), true, StabMode.ACRO);
                    } else if (since < FLICK_TICKS + HALF_ROLL_TICKS + ROLL_TICKS) {
                        session.setTestInput(new StickPositions(-rollStick, 0.0F, 0.0F, 0.35F), true, StabMode.ACRO);
                    } else {
                        state.put(STAGE, 3);
                    }
                    return StepResult.pending();
                }
                default -> {
                    this.hold(ctx);
                    TrickScore score = TrickTracker.INSTANCE.session().score();
                    if (score.popup() == TrickScore.Popup.TRICK && score.popupTypeCount() >= 2) {
                        return StepResult.pass();
                    }
                    int since = ctx.tick() - (int) state.get(CHAIN_AT);
                    return since > 140 ? StepResult.fail("keine Kette erkannt: " + score.tricks() + " Tricks, Meldung " + score.popup()
                            + " mit " + score.popupTypeCount()) : StepResult.pending();
                }
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            if (session.isActive() && (int) ctx.state().getOrDefault(STAGE, 0) >= 3) {
                float throttle = holdAltitude(ctx, session);
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
        }
    }

    private record CaveBlock(BlockPos pos, BlockState state) {
    }

    private static BlockPos caveOrigin() {
        return base().offset(-26, 0, -2);
    }

    private static int caveHash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        h ^= h >>> 15;
        return h & 0x7FFFFFFF;
    }

    private static boolean caveAir(int x, int y, int z) {
        if (x < 1 || x > CAVE_WIDTH - 2 || z < 1 || z > CAVE_LENGTH - 2 || y < 0 || y > CAVE_HEIGHT - 2) {
            return false;
        }
        if ((x == 3 && z == 13) || (x == 8 && z == 20) || (x == 2 && z == 24)) {
            return false;
        }
        int h = caveHash(x, 0, z);
        if ((x == 1 || x == CAVE_WIDTH - 2) && h % 3 == 0) {
            return false;
        }
        if (y == CAVE_HEIGHT - 2 && (h % 4 == 0 || x == 1 || x == CAVE_WIDTH - 2)) {
            return false;
        }
        return !(y == 0 && h % 9 == 0 && Math.abs(x - CAVE_WIDTH / 2) > 1 && z > 5);
    }

    private static BlockState caveRock(int x, int y, int z) {
        int h = caveHash(x, y, z) % 100;
        if (y < 0) {
            return h < 45 ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }
        if (h < 4) {
            return Blocks.COAL_ORE.defaultBlockState();
        }
        if (h < 7) {
            return Blocks.IRON_ORE.defaultBlockState();
        }
        if (h < 9) {
            return Blocks.COPPER_ORE.defaultBlockState();
        }
        if (h < 10) {
            return Blocks.DIAMOND_ORE.defaultBlockState();
        }
        if (h < 24) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        if (h < 32) {
            return Blocks.TUFF.defaultBlockState();
        }
        if (h < 38) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private static void buildCave(ServerLevel level) {
        if (!CAVE.isEmpty()) {
            return;
        }
        BlockPos origin = caveOrigin();
        for (int x = 0; x < CAVE_WIDTH; x++) {
            for (int y = -1; y < CAVE_HEIGHT; y++) {
                for (int z = 0; z < CAVE_LENGTH; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState wanted = caveAir(x, y, z) ? Blocks.AIR.defaultBlockState() : caveRock(x, y, z);
                    BlockState before = level.getBlockState(pos);
                    if (!before.equals(wanted)) {
                        CAVE.add(new CaveBlock(pos.immutable(), before));
                        level.setBlockAndUpdate(pos, wanted);
                    }
                }
            }
        }
    }

    private static FlightTune caveTune(boolean headlight) {
        Map<String, Float> values = new TreeMap<>(new LedSettings(LedMode.STATIC, LedColor.WHITE, 100, headlight).values());
        values.put(TuneMapping.CAMERA_UPTILT, 8.0F);
        return new FlightTune(values);
    }

    private static final class CaveScene implements Screenshots.Scene {
        private static final String PLACED = "placed";
        private static final String PLACED_TICK = "placed_tick";
        private static final String RIGHTED = "cave_righted";
        private static final double HOVER = 1.9;
        private static final int SETTLE_TICKS = 12;
        private final boolean headlight;

        private CaveScene(boolean headlight) {
            this.headlight = headlight;
        }

        private static Vec3 shotPosition() {
            BlockPos origin = caveOrigin();
            return new Vec3(origin.getX() + CAVE_WIDTH / 2 + 0.5, origin.getY() + HOVER, origin.getZ() + 3.5);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (this.headlight) {
                state.put(TARGET_Y, caveOrigin().getY() + HOVER);
                this.hold(ctx);
            }
            if (!state.containsKey(FUTURE)) {
                boolean enter = !this.headlight;
                if (enter) {
                    session.clearTestInput();
                }
                FlightTune tune = caveTune(this.headlight);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    if (!(level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
                        throw new IllegalStateException("Drohne fehlt");
                    }
                    drone.setTune(tune);
                    if (!enter) {
                        return;
                    }
                    buildCave(level);
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    BlockPos origin = caveOrigin();
                    double cx = origin.getX() + CAVE_WIDTH / 2 + 0.5;
                    drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP));
                    drone.setBatteryCharge(1.0F);
                    drone.resetTo(new Vec3(cx, origin.getY() + 0.3, origin.getZ() + 3.5), new Quaternionf().rotationY((float) Math.PI));
                    place(player, cx, origin.getY(), origin.getZ() + 1.6, 0.0F, 0.0F);
                    startSession(player, true);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Höhle");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            QuadcopterEntity drone = session.drone();
            if (!session.isFpv() || drone == null) {
                return ctx.tick() > 240 ? StepResult.fail("keine FPV-Sitzung in der Höhle") : StepResult.pending();
            }
            if (!this.headlight) {
                if (mc.level == null || mc.level.getMaxLocalRawBrightness(drone.blockPosition()) > 1) {
                    session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                    return ctx.tick() > 400 ? StepResult.fail("Höhle wird nicht dunkel") : StepResult.pending();
                }
                DroneSim start = session.sim();
                if (start != null && !start.telemetry().armed && start.telemetry().grounded && start.telemetry().tiltDeg > 90.0
                        && !state.containsKey(RIGHTED) && ctx.tick() > 30) {
                    int id = drone.getId();
                    BlockPos origin = caveOrigin();
                    Vec3 floor = new Vec3(origin.getX() + CAVE_WIDTH / 2 + 0.5, origin.getY() + 0.3, origin.getZ() + 3.5);
                    ctx.log("Drohne liegt nach dem Zurücksetzen kopfüber, neu aufgestellt " + poseReport(session, start));
                    state.put(RIGHTED, ctx.onServer(server2 -> {
                        if (player(server2).level().getEntity(id) instanceof QuadcopterEntity serverDrone) {
                            serverDrone.resetTo(floor, new Quaternionf().rotationY((float) Math.PI));
                        }
                    }));
                    state.remove(SINCE);
                    return StepResult.pending();
                }
                if (!state.containsKey(DONE)) {
                    StepResult climb = flyUp(ctx, caveOrigin().getY() + HOVER);
                    if (climb.status() != StepResult.Status.PASS) {
                        return climb;
                    }
                }
                this.hold(ctx);
            }
            DroneSim sim = session.sim();
            if (sim == null || !sim.telemetry().armed) {
                return ctx.tick() > 200 ? StepResult.fail("in der Höhle nicht scharf: " + (sim == null ? "keine Sitzung" : sim.telemetry().armBlockers + " " + poseReport(session, sim)))
                        : StepResult.pending();
            }
            if (!state.containsKey(PLACED)) {
                int id = drone.getId();
                Vec3 shot = shotPosition();
                state.put(PLACED, ctx.onServer(server2 -> {
                    if (player(server2).level().getEntity(id) instanceof QuadcopterEntity serverDrone) {
                        serverDrone.resetTo(shot, new Quaternionf().rotationY((float) Math.PI));
                    }
                }));
                state.put(PLACED_TICK, ctx.tick());
                return StepResult.pending();
            }
            if (!((CompletableFuture<?>) state.get(PLACED)).isDone()
                    || ctx.tick() - (int) state.get(PLACED_TICK) < SETTLE_TICKS) {
                return StepResult.pending();
            }
            if (this.headlight != Headlight.INSTANCE.active()) {
                return ctx.tick() > 400 ? StepResult.fail("Scheinwerfer in der Höhle " + (this.headlight ? "aus" : "an")) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (sim == null || !sim.telemetry().armed) {
                session.setTestInput(StickPositions.IDLE, sim != null && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5, StabMode.ANGLE);
                return;
            }
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }
    }

    private static final class ConditionScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                PartWear wear = damagedProps();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.closeContainer();
                    player.setGameMode(GameType.SURVIVAL);
                    fillHotbar(player);
                    ItemStack drone = droneItem();
                    DroneAssembly.storeWear(drone, wear);
                    drone.set(DataComponents.CUSTOM_NAME, Component.literal("Nighthawk"));
                    player.getInventory().setItem(9, drone);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Zustands-Szene");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.player.hasInfiniteMaterials()
                    || mc.player.getInventory().getItem(9).getOrDefault(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE).isNew()) {
                return ctx.tick() > 200 ? StepResult.fail("beschädigte Drohne kam nicht ins Inventar") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                TooltipShift.override(true);
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, 9));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TooltipShift.override(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.setGameMode(GameType.CREATIVE);
                fillHotbar(player);
            });
        }
    }

    private static final class LedCaveScene implements Screenshots.Scene {
        private static final double HOVER = 2.8;
        private static final double DRONE_Z = 7.5;
        private static final double PILOT_Z = 5.0;
        private static final String IDLE_SINCE = "idle_since";
        private static final int IDLE_TICKS = 20;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (!state.containsKey(FUTURE)) {
                session.clearTestInput();
                FlightTune tune = new FlightTune(new TreeMap<>(new LedSettings(LedMode.STATIC, LedColor.CYAN, 100, false).values()));
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    if (!(level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
                        throw new IllegalStateException("Drohne fehlt");
                    }
                    buildCave(level);
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    BlockPos origin = caveOrigin();
                    double cx = origin.getX() + CAVE_WIDTH / 2 + 0.5;
                    drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP));
                    drone.setTune(tune);
                    drone.resetTo(new Vec3(cx, origin.getY() + 0.3, origin.getZ() + DRONE_Z), new Quaternionf().rotationY((float) Math.PI));
                    place(player, cx, origin.getY(), origin.getZ() + PILOT_Z, 0.0F, -20.0F);
                    startSession(player, false);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "LED-Höhle");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            QuadcopterEntity drone = session.drone();
            if (!session.isLineOfSight() || drone == null) {
                return ctx.tick() > 240 ? StepResult.fail("keine Sichtflug-Sitzung in der Höhle") : StepResult.pending();
            }
            int idleSince = (int) state.computeIfAbsent(IDLE_SINCE, key -> ctx.tick());
            if (ctx.tick() - idleSince < IDLE_TICKS) {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                return StepResult.pending();
            }
            if (!state.containsKey(DONE)) {
                StepResult climb = flyUp(ctx, caveOrigin().getY() + HOVER);
                return climb.status() == StepResult.Status.FAIL ? climb : StepResult.pending();
            }
            this.hold(ctx);
            Minecraft mc = Minecraft.getInstance();
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            if (!drone.isArmed() || DroneLeds.strip(drone).isEmpty()) {
                return ctx.tick() > 400 ? StepResult.fail("LED-Drohne nicht scharf oder ohne Streifen") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
            QuadcopterEntity drone = session.drone();
            LocalPlayer player = Minecraft.getInstance().player;
            if (drone != null && player != null) {
                look(player, drone.getX(), drone.getY() + 0.05, drone.getZ());
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        }
    }

    private static final class BreakupScene implements Screenshots.Scene {
        private final float quality;
        private final VideoSystem system;
        private final AnalogBreakup.@Nullable Stage emphasis;

        private BreakupScene(float quality, VideoSystem system, AnalogBreakup.@Nullable Stage emphasis) {
            this.quality = quality;
            this.system = system;
            this.emphasis = emphasis;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (!state.containsKey(FUTURE)) {
                VideoBreakupProbe.clear();
                session.clearTestInput();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    parkDrone(player);
                    startSession(player, true);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "FPV für das Videobild");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!session.isFpv()) {
                return ctx.tick() > 200 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
            }
            if (!state.containsKey(DONE)) {
                StepResult up = flyUp(ctx, base().getY() + HOVER_HEIGHT);
                if (up.status() != StepResult.Status.PASS) {
                    return up;
                }
            }
            VideoBreakupProbe.force(this.quality, this.system);
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            VideoBreakupProbe.force(this.quality, this.system);
            VideoBreakupProbe.emphasize(this.emphasis);
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.log(VideoBreakupProbe.describe());
            VideoBreakupProbe.clear();
        }
    }

    private static final class PilotViewScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (!session.isFpv()) {
                return StepResult.fail("keine FPV-Sitzung");
            }
            DroneSim sim = session.sim();
            if (!state.containsKey(FUTURE) && (sim == null || !sim.telemetry().armed)) {
                session.setTestInput(StickPositions.IDLE, sim != null && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5, StabMode.ANGLE);
                return ctx.tick() > 120 ? StepResult.fail("nicht scharf: " + (sim == null ? "keine Sitzung" : sim.telemetry().armBlockers))
                        : StepResult.pending();
            }
            if (!state.containsKey(FUTURE)) {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.3F), true, StabMode.ANGLE);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    if (!(player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
                        throw new IllegalStateException("Drohne fehlt");
                    }
                    Vec3 front = player.position().add(0.0, 1.3, 4.8);
                    drone.resetTo(front, new Quaternionf());
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Drohne vor den Piloten");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!state.containsKey(TARGET_Y)) {
                QuadcopterEntity drone = session.drone();
                if (drone == null || drone.getY() < base().getY() + 1.0) {
                    return ctx.tick() > 100 ? StepResult.fail("Drohne nicht versetzt") : StepResult.pending();
                }
                state.put(TARGET_Y, session.comPosition(new Vector3d()).y);
            }
            this.hold(ctx);
            return ctx.tick() > 20 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        }
    }

    private static final List<UUID> SPECTATE_DRONES = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static QuadcopterEntity spawnTransmitter(ServerLevel level, Vec3 pos, float facingYaw, DroneBuild build, String pilot) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(build);
        drone.setNoGravity(true);
        drone.snapTo(pos.x, pos.y, pos.z, facingYaw, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-facingYaw) + (float) Math.PI).rotateX((float) Math.toRadians(-8.0)));
        SpectateServer.simulateTransmitter(drone, pilot);
        SPECTATE_DRONES.add(drone.getUUID());
        return drone;
    }

    private static void prepareSpectators(MinecraftServer server) {
        ServerPlayer player = player(server);
        if (player.level().getEntity(DRONE.get()) instanceof QuadcopterEntity main && main.pilot().isPresent()) {
            DronePiloting.release(main);
        }
        BlockPos base = base();
        place(player, base.getX() + 8.5, base.getY(), base.getZ() + 3.5, 45.0F, 10.0F);
        player.setItemSlot(EquipmentSlot.HEAD, ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElse(ItemStack.EMPTY));
        if (SPECTATE_DRONES.isEmpty()) {
            Vec3 start = gateCenter(0).add(-0.8, -1.0, -3.2);
            spawnTransmitter(player.level(), start, yawTowards(start, gateCenter(1)), DroneBuild.RACER, "Lotte");
            Vec3 second = gateCenter(1).add(3.0, 1.5, 2.0);
            spawnTransmitter(player.level(), second, yawTowards(second, gateCenter(2)), DroneBuild.FREESTYLE, "Ben");
        }
        SpectateServer.refreshSoon();
    }

    private static void removeSpectators(MinecraftServer server) {
        for (UUID id : SPECTATE_DRONES) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                    SpectateServer.simulateTransmitter(drone, null);
                    drone.discard();
                }
            }
        }
        SPECTATE_DRONES.clear();
        player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        SpectateServer.refreshSoon();
    }

    private static final class ChannelListScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                PilotSession.INSTANCE.clearTestInput();
                state.put(FUTURE, ctx.onServer(ShowcaseTour::prepareSpectators));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Sender");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (SpectateSession.INSTANCE.channels().size() < 2 || PilotSession.INSTANCE.isActive()) {
                return ctx.tick() > 200 ? StepResult.fail("Kanalliste kommt nicht an: " + SpectateSession.INSTANCE.channels().size())
                        : StepResult.pending();
            }
            parkMouse(mc);
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, "video_channel");
                return StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !"video_channel".equals(active.id())) {
                screen.selectTab("video_channel");
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
        }
    }

    private static final class SpectateScene implements Screenshots.Scene {
        private static final String REQUESTED = "requested";

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(ShowcaseTour::prepareSpectators));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Sender");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            SpectateSession session = SpectateSession.INSTANCE;
            if (!state.containsKey(REQUESTED)) {
                if (mc.gui.screen() != null || session.watchable(mc).isEmpty()) {
                    return ctx.tick() > 200 ? StepResult.fail("kein Kanal zum Zuschauen") : StepResult.pending();
                }
                if (!session.request(mc, session.watchable(mc).getFirst())) {
                    return StepResult.fail("Zuschauen verweigert: " + session.lastResult());
                }
                state.put(REQUESTED, ctx.tick());
                return StepResult.pending();
            }
            if (!session.watching()) {
                return ctx.tick() - (int) state.get(REQUESTED) > 200 ? StepResult.fail("Zuschauen startet nicht: " + session.phase())
                        : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            SpectateSession.INSTANCE.stop(Minecraft.getInstance(), true);
            ctx.onServer(ShowcaseTour::removeSpectators);
        }
    }
}

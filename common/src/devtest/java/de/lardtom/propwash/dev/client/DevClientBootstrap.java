package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.accessory.client.CompassSelfTests;
import de.lardtom.propwash.apiimpl.client.ApiLootSelfTests;
import de.lardtom.propwash.apiimpl.client.ApiSelfTests;
import de.lardtom.propwash.assets.world.client.WorldAssetsSelfTests;
import de.lardtom.propwash.audio.client.AudioSelfTests;
import de.lardtom.propwash.audio.client.DroneHearingSelfTests;
import de.lardtom.propwash.bando.client.BandoSelfTests;
import de.lardtom.propwash.barrier.client.BarrierSelfTests;
import de.lardtom.propwash.blackbox.BlackboxLibrary;
import de.lardtom.propwash.blackbox.client.BlackboxSelfTests;
import de.lardtom.propwash.blackbox.client.BlackboxSession;
import de.lardtom.propwash.build.placeholder.client.PlaceholderSelfTests;
import de.lardtom.propwash.compat.iris.client.IrisSelfTests;
import de.lardtom.propwash.content.client.ContentSelfTests;
import de.lardtom.propwash.content.client.PartFreedomSelfTests;
import de.lardtom.propwash.content.client.PropRepairSelfTests;
import de.lardtom.propwash.content.client.RecycleSelfTests;
import de.lardtom.propwash.content.client.SalvageSelfTests;
import de.lardtom.propwash.content.client.SurvivalSelfTests;
import de.lardtom.propwash.creative.client.CreativeSelfTests;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.example.client.ExampleAddonClient;
import de.lardtom.propwash.dev.network.SelfTestNet;
import de.lardtom.propwash.drone.bind.client.BindSelfTests;
import de.lardtom.propwash.drone.client.CameraAngleSelfTests;
import de.lardtom.propwash.drone.client.AccessoryStandSelfTests;
import de.lardtom.propwash.drone.client.CameraClipSelfTests;
import de.lardtom.propwash.drone.client.DroneScaleSelfTests;
import de.lardtom.propwash.drone.client.DroneSelfTests;
import de.lardtom.propwash.drone.client.FpvFeelSelfTests;
import de.lardtom.propwash.drone.client.render.AntennaSelfTests;
import de.lardtom.propwash.drone.client.render.AntennaNoneSelfTests;
import de.lardtom.propwash.freecam.client.FreecamSelfTests;
import de.lardtom.propwash.freecam.client.CovFreecamSelfTests;
import de.lardtom.propwash.drone.client.PartsFlightSelfTests;
import de.lardtom.propwash.drone.client.PilotBodySelfTests;
import de.lardtom.propwash.drone.client.WearWeightSelfTests;
import de.lardtom.propwash.drone.client.dust.DustSelfTests;
import de.lardtom.propwash.drone.data.client.DroneDataSelfTests;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.drone.client.render.DroneRenderSelfTests;
import de.lardtom.propwash.drone.client.render.GroundContactSelfTests;
import de.lardtom.propwash.drone.client.trail.TrailSelfTests;
import de.lardtom.propwash.drone.collision.client.GhostSelfTests;
import de.lardtom.propwash.drone.operator.client.OperatorGimbalSelfTests;
import de.lardtom.propwash.guard.client.GuardSelfTests;
import de.lardtom.propwash.guide.client.GuideSelfTests;
import de.lardtom.propwash.input.addon.client.AddonSwitchSelfTests;
import de.lardtom.propwash.input.client.InputSelfTests;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.interact.client.AccessoryFitSelfTests;
import de.lardtom.propwash.interact.client.AccessoryFixSelfTests;
import de.lardtom.propwash.interact.client.AccessoryGapsSelfTests;
import de.lardtom.propwash.interact.client.AccessoryHangSelfTests;
import de.lardtom.propwash.interact.client.AddonApiSelfTests;
import de.lardtom.propwash.interact.client.BlockBreakSelfTests;
import de.lardtom.propwash.interact.client.GlassFeelSelfTests;
import de.lardtom.propwash.interact.client.SnagSelfTests;
import de.lardtom.propwash.interact.client.InteractSelfTests;
import de.lardtom.propwash.interact.client.DroneTriggerSelfTests;
import de.lardtom.propwash.join.client.JoinSelfTests;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.addon.client.AddonOsdSelfTests;
import de.lardtom.propwash.drone.client.CovJmpFlightSelfTests;
import de.lardtom.propwash.drone.client.CovLandingSelfTests;
import de.lardtom.propwash.drone.client.CovMotorHeatSelfTests;
import de.lardtom.propwash.drone.client.CovSurfaceSelfTests;
import de.lardtom.propwash.interact.client.CovDroneTriggerSelfTests;
import de.lardtom.propwash.interact.client.CovVineBeamSelfTests;
import de.lardtom.propwash.interact.client.CovVineBeamTopSelfTests;
import de.lardtom.propwash.interact.client.DroneBossSelfTests;
import de.lardtom.propwash.interact.client.DroneSculkMissionSelfTests;
import de.lardtom.propwash.interact.client.DroneSculkSelfTests;
import de.lardtom.propwash.osd.client.BatterySagSelfTests;
import de.lardtom.propwash.osd.client.CovFlightPathMarkerSelfTests;
import de.lardtom.propwash.osd.client.FlightPathMarkerSelfTests;
import de.lardtom.propwash.osd.client.OsdOverlapSelfTests;
import de.lardtom.propwash.osd.client.OsdSelfTests;
import de.lardtom.propwash.osd.client.StickOverlaySelfTests;
import de.lardtom.propwash.parts.client.PartsSelfTests;
import de.lardtom.propwash.race.client.AssistCheckSelfTests;
import de.lardtom.propwash.race.client.AssistedLapSelfTests;
import de.lardtom.propwash.race.client.GateEditor;
import de.lardtom.propwash.race.client.GateHintSelfTests;
import de.lardtom.propwash.race.client.RaceDataSelfTests;
import de.lardtom.propwash.race.client.RaceSelfTests;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.relay.client.RelaySelfTests;
import de.lardtom.propwash.rescue.client.AutonomyCargoSelfTests;
import de.lardtom.propwash.rescue.client.AutonomyGroundSelfTests;
import de.lardtom.propwash.rescue.client.AutonomySelfTests;
import de.lardtom.propwash.rescue.client.RescueSelfTests;
import de.lardtom.propwash.rx.client.RxSelfTests;
import de.lardtom.propwash.server.client.AddonWorldRulesSelfTests;
import de.lardtom.propwash.server.client.ClearSelfTests;
import de.lardtom.propwash.server.client.ServerSelfTests;
import de.lardtom.propwash.share.client.ShareSelfTests;
import de.lardtom.propwash.share.client.CovShareSelfTests;
import de.lardtom.propwash.drone.client.render.CovJmpBatteryPlugSelfTests;
import de.lardtom.propwash.share.chat.ShareChatSelfTests;
import de.lardtom.propwash.share.client.ShareSmokeCheck;
import de.lardtom.propwash.trick.client.TrickSelfTests;
import de.lardtom.propwash.ui.client.FirstUseHints;
import de.lardtom.propwash.ui.client.OverlayMessageSelfTests;
import de.lardtom.propwash.ui.client.UiSelfTests;
import de.lardtom.propwash.video.client.CameraAngleMenuSelfTests;
import de.lardtom.propwash.video.client.CovCameraAngleMenuSelfTests;
import de.lardtom.propwash.video.client.CovSignalMemorySelfTests;
import de.lardtom.propwash.video.client.FisheyeSelfTests;
import de.lardtom.propwash.video.client.SignalMemorySelfTests;
import de.lardtom.propwash.video.client.SpectateFeedSelfTests;
import de.lardtom.propwash.video.client.VideoSelfTests;
import de.lardtom.propwash.video.client.record.FlashbackCamSelfTests;
import de.lardtom.propwash.zone.client.ZoneSelfTests;
import de.lardtom.propwash.drone.client.render.CovBatteryPlugIrisShowcase;
import de.lardtom.propwash.drone.client.render.CovBatteryPlugSelfTests;
import de.lardtom.propwash.rx.client.CovRxSelfTests;
import de.lardtom.propwash.server.client.CovClearSelfTests;
import de.lardtom.propwash.server.client.CovLanGuestHost;
import de.lardtom.propwash.zone.client.CovZoneSelfTests;
import de.lardtom.propwash.zone.client.ZonesOpenSelfTests;
import de.lardtom.propwash.race.client.CovGateHintSelfTests;
import de.lardtom.propwash.race.client.CovGateStackSelfTests;
import de.lardtom.propwash.race.client.CovRaceSystemSelfTests;
import de.lardtom.propwash.race.client.RaceRulesSelfTests;
import de.lardtom.propwash.race.client.RaceP2pSelfTests;
import de.lardtom.propwash.race.client.RaceVerdictSelfTests;
import de.lardtom.propwash.race.client.CourseMenuSelfTests;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import de.lardtom.propwash.guard.client.CovGuardSelfTests;
import de.lardtom.propwash.drone.collision.client.CovGhostSelfTests;
import de.lardtom.propwash.guard.client.CovNoclipMenuSelfTests;
import de.lardtom.propwash.input.addon.client.CovAddonSwitchSelfTests;
import de.lardtom.propwash.input.client.CovWizardAddonSelfTests;
import de.lardtom.propwash.osd.addon.client.CovAddonOsdSelfTests;
import de.lardtom.propwash.osd.addon.client.CovAddonComboSelfTests;
import de.lardtom.propwash.guard.client.CovComboGuardSelfTests;
import de.lardtom.propwash.share.client.CovComboShareSelfTests;
import de.lardtom.propwash.server.client.CovAddonWorldRulesSelfTests;
import de.lardtom.propwash.input.client.ExtraFunctionWizardSelfTests;
import de.lardtom.propwash.input.client.AutoArmSelfTests;
import de.lardtom.propwash.gpucompat.client.GpuCompatSelfTests;
import de.lardtom.propwash.stats.client.StatsSelfTests;
import de.lardtom.propwash.capture.client.CaptureSelfTests;
import de.lardtom.propwash.input.client.HotplugSelfTests;
import de.lardtom.propwash.preview.client.PreviewSelfTests;
import de.lardtom.propwash.drone.client.CovTurtleEdgeSelfTests;
import de.lardtom.propwash.drone.client.CovTurtleRimSelfTests;
import de.lardtom.propwash.drone.client.CovCrashRestSelfTests;
import de.lardtom.propwash.drone.client.CovCrashRulesSelfTests;
import de.lardtom.propwash.drone.client.CovCrashWallSelfTests;
import de.lardtom.propwash.drone.client.render.CovPropBlurCenterSelfTests;
import de.lardtom.propwash.drone.client.RemoteMotionSelfTests;
import de.lardtom.propwash.drone.client.PlacedDroneSelfTests;

public final class DevClientBootstrap {
    private DevClientBootstrap() {
    }

    public static void init() {
        ClientNet.handle(SelfTestNet.PongS2C.TYPE, CoreSelfTests::onPong);
        if (JoinTestEnv.exampleAddon()) {
            ExampleAddonClient.init();
        }
        if (DroneRenderConfig.get().sizeNotice) {
            DroneRenderConfig quiet = DroneRenderConfig.get().copy();
            quiet.sizeNotice = false;
            DroneRenderConfig.CONFIG.set(quiet);
        }
        if (SelfTest.enabled()) {
            SelfTest.baseStep("dev: Testfenster ohne Mausgriff und Fokus", 5, ctx -> DetachedMouse.check());
            SelfTest.baseStep("dev: Propwash-Tasten auf Standard", 5, ctx -> {
                for (KeyMapping mapping : ClientRegistrar.keyMappings()) {
                    mapping.setKey(mapping.getDefaultKey());
                }
                KeyMapping.resetMapping();
                return SelfTest.StepResult.pass();
            });
            SelfTest.baseStep("dev: FPV-Darstellung auf Standard", 1, ctx -> {
                DroneRenderConfig defaults = new DroneRenderConfig();
                DroneRenderConfig render = DroneRenderConfig.get();
                if (render.fpvProps != defaults.fpvProps || render.fpvDucts != defaults.fpvDucts) {
                    DroneRenderConfig reset = render.copy();
                    reset.fpvProps = defaults.fpvProps;
                    reset.fpvDucts = defaults.fpvDucts;
                    DroneRenderConfig.CONFIG.set(reset);
                }
                return SelfTest.StepResult.pass();
            });
            SelfTest.baseStep("dev: Kamera-Vibration aus (eigene Schritte schalten sie ein)", 1, ctx -> {
                FpvFeelSelfTests.muteShake();
                return SelfTest.StepResult.pass();
            });
            SelfTest.baseStep("dev: Hinweis zur freien Maus im Gate-Editor gilt als bekannt, Start-Hinweis aus (eigene Schritte schalten beides ein)", 1,
                    ctx -> {
                        FirstUseHints.learned(GateEditor.MOUSE_HINT, GateEditor.MOUSE_HINT_TIMES);
                        GateEditor.muteStartNotice(true);
                        return SelfTest.StepResult.pass();
                    });
            SelfTest.baseStep("dev: angeschlossene Funken und Gamepads ausgeblendet (Selbsttest wie ohne Gerät)", 1, ctx -> {
                TransmitterInput.INSTANCE.setSuppressDevice(true);
                return SelfTest.StepResult.pass();
            });
            SelfTest.baseStep("dev: Objektiv flach (eigene Schritte schalten Fisheye ein)", 1, ctx -> {
                FisheyeSelfTests.flatForOtherSteps();
                return SelfTest.StepResult.pass();
            });
            SelfTest.baseStep("dev: Blackbox-Logs früherer Läufe entfernen", 5, ctx -> {
                Path directory = BlackboxSession.directory(Minecraft.getInstance());
                int removed = 0;
                if (Files.isDirectory(directory)) {
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + BlackboxLibrary.EXTENSION)) {
                        for (Path file : files) {
                            if (Files.isRegularFile(file) && Files.deleteIfExists(file)) {
                                removed++;
                            }
                        }
                    } catch (IOException e) {
                        return SelfTest.StepResult.fail("Blackbox-Ordner " + directory.getFileName() + ": " + e);
                    }
                }
                ctx.log(removed + " Blackbox-Logs früherer Läufe entfernt");
                return SelfTest.StepResult.pass();
            });
            DevPlayerCheck.register();
            CoreSelfTests.register();
            SelfTestRunner.install();
            ContentSelfTests.register();
            SurvivalSelfTests.register();
            PropRepairSelfTests.register();
            PartsSelfTests.register();
            JoinSelfTests.register();
            DroneRenderSelfTests.register();
            DustSelfTests.register();
            TrailSelfTests.register();
            DroneSelfTests.register();
            BindSelfTests.register();
            WearWeightSelfTests.register();
            CameraAngleSelfTests.register();
            CameraClipSelfTests.register();
            GroundContactSelfTests.register();
            FpvFeelSelfTests.register();
            PartsFlightSelfTests.register();
            PartFreedomSelfTests.register();
            RaceSelfTests.register();
            AssistedLapSelfTests.register();
            AssistCheckSelfTests.register();
            ZoneSelfTests.register();
            ServerSelfTests.register();
            RescueSelfTests.register();
            PilotBodySelfTests.register();
            InputSelfTests.register();
            VideoSelfTests.register();
            RelaySelfTests.register();
            IrisSelfTests.register();
            AudioSelfTests.register();
            UiSelfTests.register();
            OverlayMessageSelfTests.register();
            OsdSelfTests.register();
            StickOverlaySelfTests.register();
            OsdOverlapSelfTests.register();
            BatterySagSelfTests.register();
            FlightPathMarkerSelfTests.register();
            TrickSelfTests.register();
            BlackboxSelfTests.register();
            InteractSelfTests.register();
            BlockBreakSelfTests.register();
            GlassFeelSelfTests.register();
            SnagSelfTests.register();
            CreativeSelfTests.register();
            GuideSelfTests.register();
            ApiSelfTests.register();
            ApiLootSelfTests.register();
            AddonApiSelfTests.register();
            WorldAssetsSelfTests.register();
            UiScaleSelfTests.register();
            ShowcaseTour.register();
            FlashbackCamSelfTests.registerReplay();
            BandoSelfTests.register();
            CameraAngleMenuSelfTests.register();
            GuardSelfTests.register();
            RxSelfTests.register();
            DroneTriggerSelfTests.register();
            SignalMemorySelfTests.register();
            GateHintSelfTests.register();
            ClearSelfTests.register();
            DrehweltSelfTests.register();
            MigrationSelfTests.register();
            AddonSwitchSelfTests.register();
            AddonOsdSelfTests.register();
            DroneDataSelfTests.register();
            AddonWorldRulesSelfTests.register();
            GhostSelfTests.register();
            ShareSelfTests.register();
            ShareChatSelfTests.register();
            FreecamSelfTests.register();
            AccessoryGapsSelfTests.register();
            AutonomySelfTests.register();
            CovFreecamSelfTests.register();
            AccessoryHangSelfTests.register();
            CompassSelfTests.register();
            RaceDataSelfTests.register();
            OperatorGimbalSelfTests.register();
            PlaceholderSelfTests.register();
            AccessoryFixSelfTests.register();
            CovFlightPathMarkerSelfTests.register();
            CovCameraAngleMenuSelfTests.register();
            CovSurfaceSelfTests.register();
            CovDroneTriggerSelfTests.register();
            CovLandingSelfTests.register();
            CovMotorHeatSelfTests.register();
            CovSignalMemorySelfTests.register();
            CovJmpFlightSelfTests.register();
            CovBatteryPlugSelfTests.register();
            CovBatteryPlugIrisShowcase.register();
            CovZoneSelfTests.register();
            CovRxSelfTests.register();
            CovClearSelfTests.register();
            CovLanGuestHost.register();
            DroneScaleSelfTests.register();
            BarrierSelfTests.register();
            ZonesOpenSelfTests.register();
            CovAddonSwitchSelfTests.register();
            CovWizardAddonSelfTests.register();
            CovAddonOsdSelfTests.register();
            CovAddonWorldRulesSelfTests.register();
            CovGuardSelfTests.register();
            CovGhostSelfTests.register();
            CovNoclipMenuSelfTests.register();
            CovGateStackSelfTests.register();
            CovGateHintSelfTests.register();
            CovRaceSystemSelfTests.register();
            ExtraFunctionWizardSelfTests.register();
            AutoArmSelfTests.register();
            RaceRulesSelfTests.register();
            CovShareSelfTests.register();
            CovJmpBatteryPlugSelfTests.register();
            RaceP2pSelfTests.register();
            RaceVerdictSelfTests.register();
            CovMigrationSelfTests.register();
            CovAddonComboSelfTests.register();
            CovComboGuardSelfTests.register();
            CovComboShareSelfTests.register();
            CovAddonCycleSelfTests.register();
            GpuCompatSelfTests.register();
            StatsSelfTests.register();
            CaptureSelfTests.register();
            HotplugSelfTests.register();
            PreviewSelfTests.register();
            SalvageSelfTests.register();
            RecycleSelfTests.register();
            CourseMenuSelfTests.register();
            AntennaSelfTests.register();
            CovTurtleEdgeSelfTests.register();
            AccessoryStandSelfTests.register();
            SpectateFeedSelfTests.register();
            CovTurtleRimSelfTests.register();
            CovCrashRestSelfTests.register();
            CovVineBeamSelfTests.register();
            CovCrashRulesSelfTests.register();
            CovVineBeamTopSelfTests.register();
            AntennaNoneSelfTests.register();
            AccessoryFitSelfTests.register();
            AutonomyGroundSelfTests.register();
            DroneHearingSelfTests.register();
            CovCrashWallSelfTests.register();
            AutonomyCargoSelfTests.register();
            DroneSculkSelfTests.register();
            DroneSculkMissionSelfTests.register();
            CovPropBlurCenterSelfTests.register();
            DroneBossSelfTests.register();
            RemoteMotionSelfTests.register();
            PlacedDroneSelfTests.register();
        } else if (SelfTest.smokeEnabled()) {
            SelfTestRunner.installSmoke();
        }
        ShareSmokeCheck.init();
    }
}

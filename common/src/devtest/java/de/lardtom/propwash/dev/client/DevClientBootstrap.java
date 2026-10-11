package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.apiimpl.client.ApiLootSelfTests;
import de.lardtom.propwash.apiimpl.client.ApiSelfTests;
import de.lardtom.propwash.assets.world.client.WorldAssetsSelfTests;
import de.lardtom.propwash.audio.client.AudioSelfTests;
import de.lardtom.propwash.bando.client.BandoSelfTests;
import de.lardtom.propwash.blackbox.BlackboxLibrary;
import de.lardtom.propwash.blackbox.client.BlackboxSelfTests;
import de.lardtom.propwash.blackbox.client.BlackboxSession;
import de.lardtom.propwash.compat.iris.client.IrisSelfTests;
import de.lardtom.propwash.content.client.ContentSelfTests;
import de.lardtom.propwash.content.client.PartFreedomSelfTests;
import de.lardtom.propwash.content.client.PropRepairSelfTests;
import de.lardtom.propwash.content.client.SurvivalSelfTests;
import de.lardtom.propwash.creative.client.CreativeSelfTests;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.example.client.ExampleAddonClient;
import de.lardtom.propwash.dev.network.SelfTestNet;
import de.lardtom.propwash.drone.bind.client.BindSelfTests;
import de.lardtom.propwash.drone.client.CameraAngleSelfTests;
import de.lardtom.propwash.drone.client.CameraClipSelfTests;
import de.lardtom.propwash.drone.client.DroneSelfTests;
import de.lardtom.propwash.drone.client.FpvFeelSelfTests;
import de.lardtom.propwash.drone.client.PartsFlightSelfTests;
import de.lardtom.propwash.drone.client.PilotBodySelfTests;
import de.lardtom.propwash.drone.client.WearWeightSelfTests;
import de.lardtom.propwash.drone.client.dust.DustSelfTests;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.drone.client.render.DroneRenderSelfTests;
import de.lardtom.propwash.drone.client.render.GroundContactSelfTests;
import de.lardtom.propwash.drone.client.trail.TrailSelfTests;
import de.lardtom.propwash.guide.client.GuideSelfTests;
import de.lardtom.propwash.input.client.InputSelfTests;
import de.lardtom.propwash.interact.client.BlockBreakSelfTests;
import de.lardtom.propwash.interact.client.GlassFeelSelfTests;
import de.lardtom.propwash.interact.client.InteractSelfTests;
import de.lardtom.propwash.join.client.JoinSelfTests;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.client.OsdSelfTests;
import de.lardtom.propwash.parts.client.PartsSelfTests;
import de.lardtom.propwash.race.client.AssistedLapSelfTests;
import de.lardtom.propwash.race.client.RaceSelfTests;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.relay.client.RelaySelfTests;
import de.lardtom.propwash.rescue.client.RescueSelfTests;
import de.lardtom.propwash.server.client.ServerSelfTests;
import de.lardtom.propwash.trick.client.TrickSelfTests;
import de.lardtom.propwash.ui.client.OverlayMessageSelfTests;
import de.lardtom.propwash.ui.client.UiSelfTests;
import de.lardtom.propwash.video.client.VideoSelfTests;
import de.lardtom.propwash.video.client.record.FlashbackCamSelfTests;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

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
            TrickSelfTests.register();
            BlackboxSelfTests.register();
            InteractSelfTests.register();
            BlockBreakSelfTests.register();
            GlassFeelSelfTests.register();
            CreativeSelfTests.register();
            GuideSelfTests.register();
            ApiSelfTests.register();
            ApiLootSelfTests.register();
            WorldAssetsSelfTests.register();
            UiScaleSelfTests.register();
            ShowcaseTour.register();
            FlashbackCamSelfTests.registerReplay();
            BandoSelfTests.register();
        } else if (SelfTest.smokeEnabled()) {
            SelfTestRunner.installSmoke();
        }
    }
}

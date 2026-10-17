package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import de.lardtom.propwash.video.vtx.VtxUnit;
import net.minecraft.client.Minecraft;

import java.util.Map;

public final class VtxPowerShowcase {
    private VtxPowerShowcase() {
    }

    public static void register() {
        Screenshots.step("bildschirmfotos: VTX-Leistung – Tab Videokanal mit Sendeleistung", "configurator_video_channel_vtx_power", 20,
                new TabScene(true));
        Screenshots.step("bildschirmfotos: VTX-Leistung – Tab Videokanal analog mit 1600 mW", "configurator_video_channel_vtx_power_analog", 20,
                new TabScene(false));
        Screenshots.step("bildschirmfotos: VTX-Leistung – OSD mit Sendeleistung und Hitzeschutz", "fpv_osd_vtx_power_hot", 20, new HotScene());
        SelfTest.switchStep("bildschirmfotos: VTX-Leistung – Sprache Deutsch", "bildschirmfotos: VTX-Leistung – Sprache zurücksetzen", 1200,
                ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step("bildschirmfotos: VTX-Leistung – Tab Videokanal deutsch", "configurator_video_channel_vtx_power_de", 20, new TabScene(true));
        Screenshots.step("bildschirmfotos: VTX-Leistung – Tab Videokanal analog mit 1600 mW deutsch", "configurator_video_channel_vtx_power_analog_de", 20,
                new TabScene(false));
        Screenshots.step("bildschirmfotos: VTX-Leistung – OSD Hitzeschutz deutsch", "fpv_osd_vtx_power_hot_de", 20, new HotScene());
        SelfTest.clientStep("bildschirmfotos: VTX-Leistung – Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        SelfTest.clientStep("bildschirmfotos: VTX-Leistung – aufräumen", 300, VtxPowerSelfTests::cleanupStep);
    }

    private static final class TabScene implements Screenshots.Scene {
        private final boolean digital;
        private int phase;
        private int since;

        private TabScene(boolean digital) {
            this.digital = digital;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                    screen.discardAndClose();
                }
                StepResult started = this.digital
                        ? VtxPowerSelfTests.startFlight(ctx, DroneBuild.FREESTYLE, ContentIds.GOGGLES_DIGITAL, VtxPowerSettings.withAuto(Map.of()),
                        VtxPowerSelfTests.NEAR)
                        : VtxPowerSelfTests.startFlight(ctx, DroneBuild.RACER, ContentIds.GOGGLES_ANALOG, VtxPowerSettings.withLevel(Map.of(), 1600),
                        VtxPowerSelfTests.NEAR);
                if (started.status() != StepResult.Status.PASS) {
                    return started;
                }
                this.phase = 1;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (this.phase == 1) {
                VtxUnit unit = VtxRuntime.INSTANCE.unit(VtxFixture.piloted());
                if (unit == null) {
                    return StepResult.fail("Testflug endete");
                }
                if (this.digital ? unit.effectiveMw() > 25 && ctx.tick() - this.since < 400 : ctx.tick() - this.since < 40) {
                    return StepResult.pending();
                }
                ConfigTabs.open(null, ChannelTab.ID);
                this.phase = 2;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return ctx.tick() - this.since > 100 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !ChannelTab.ID.equals(active.id())) {
                screen.selectTab(ChannelTab.ID);
                return StepResult.pending();
            }
            return ctx.tick() - this.since < 10 ? StepResult.pending() : StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            this.phase = 0;
        }
    }

    private static final class HotScene implements Screenshots.Scene {
        private int phase;
        private int since;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                StepResult started = VtxPowerSelfTests.startFlight(ctx, DroneBuild.RACER, ContentIds.GOGGLES_ANALOG,
                        VtxPowerSettings.withLevel(Map.of(), 1600), VtxPowerSelfTests.NEAR);
                if (started.status() != StepResult.Status.PASS) {
                    return started;
                }
                this.phase = 1;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            VtxUnit unit = VtxRuntime.INSTANCE.unit(VtxFixture.piloted());
            if (unit == null) {
                return StepResult.fail("Testflug endete");
            }
            if (this.phase == 1) {
                if (ctx.tick() - this.since < 20) {
                    return StepResult.pending();
                }
                VtxRuntime.INSTANCE.overrideTestTemperature(92.0);
                this.phase = 2;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (!unit.throttled()) {
                return ctx.tick() - this.since > 100 ? StepResult.fail("Hitzeschutz greift nicht") : StepResult.pending();
            }
            return ctx.tick() - this.since < 10 ? StepResult.pending() : StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            VtxRuntime.INSTANCE.overrideTestTemperature(22.0);
            this.phase = 0;
        }
    }
}

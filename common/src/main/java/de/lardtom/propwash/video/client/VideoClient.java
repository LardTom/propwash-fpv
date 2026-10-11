package de.lardtom.propwash.video.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.registry.client.HudVisibility;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.video.client.record.ActionCamCapture;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.ActionCamTab;
import de.lardtom.propwash.video.client.record.FlashbackCam;
import de.lardtom.propwash.video.client.record.RecordingConfig;
import de.lardtom.propwash.video.client.record.RecordingHud;
import de.lardtom.propwash.video.client.record.RecordingKeys;
import de.lardtom.propwash.video.net.ChannelListS2C;
import de.lardtom.propwash.video.net.SpectateS2C;

public final class VideoClient {
    private VideoClient() {
    }

    public static void init() {
        VideoSettings.CONFIG.load();
        RecordingConfig.CONFIG.load();
        RecordingKeys.bootstrap();
        SpectateConfig.CONFIG.load();
        SpectateKeys.bootstrap();
        ClientNet.handle(ChannelListS2C.TYPE, (payload, context) -> SpectateSession.INSTANCE.onChannelList(payload));
        ClientNet.handle(SpectateS2C.TYPE, (payload, context) -> SpectateSession.INSTANCE.onResult(context.minecraft(), payload));
        ClientRegistrar.renderPipeline(VideoPass.PIPELINE);
        ClientRegistrar.renderPipeline(ActionCamCapture.FRAME_PIPELINE);
        ClientRegistrar.renderPipeline(ActionCamCapture.PACK_PIPELINE);
        ClientRegistrar.renderPipeline(HeadlightPass.PIPELINE);
        ClientRegistrar.renderPipeline(HeadlightOverlay.BOOST_PIPELINE);
        ClientRegistrar.renderPipeline(HeadlightOverlay.LIFT_PIPELINE);
        ClientRegistrar.hudLayer(Constants.id("video_feed"), VideoOverlay::extract, HudVisibility.ALWAYS);
        ClientRegistrar.hudLayer(RecordingHud.ID, RecordingHud::extract);
        ClientRegistrar.hudLayer(SpectateHud.ID, SpectateHud::extract);
        ClientBus.REGISTER_HUD_LAYERS.register(() -> ClientRegistrar.hudLayer(Constants.id("video_link"), VideoOverlay::extractCard,
                HudVisibility.ALWAYS));
        ClientBus.FRAME_START.register(GpuInfo::logOnce);
        ClientBus.FRAME_START.register(SpectateSession.INSTANCE::onFrame);
        ClientBus.FRAME_START.register(VideoState.INSTANCE::onFrame);
        ClientBus.FRAME_START.register(Headlight.INSTANCE::onFrame);
        ClientBus.FRAME_START.register(FlashbackCam.INSTANCE::onFrame);
        ClientBus.TICK_END.register(SpectateSession.INSTANCE::onClientTick);
        ClientBus.TICK_END.register(VideoState.INSTANCE::onTick);
        ClientBus.TICK_END.register(ActionCamRecorder.INSTANCE::onClientTick);
        ClientBus.TICK_END.register(FlashbackCam.INSTANCE::onClientTick);
        ClientBus.DISCONNECT.register(minecraft -> {
            SpectateSession.INSTANCE.onDisconnect(minecraft);
            VideoState.INSTANCE.onDisconnect(minecraft);
            VideoPass.INSTANCE.release();
            VideoPass.INSTANCE.retry();
            ActionCamRecorder.INSTANCE.onDisconnect(minecraft);
            FlashbackCam.INSTANCE.onDisconnect(minecraft);
            Headlight.INSTANCE.reset();
            HeadlightPass.INSTANCE.release();
            HeadlightPass.INSTANCE.retry();
        });
        ConfigTabs.register(VideoConfigTab::new);
        ConfigTabs.register(ActionCamTab::new);
        ConfigTabs.register(ChannelTab::new);
    }
}

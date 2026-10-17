package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseLaps;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class RaceClient {
    private static boolean initialized;
    private static int sentLaps = CourseLaps.UNSET;

    private RaceClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RaceConfig.CONFIG.load();
        ClientRegistrar.entityRenderer(RaceModule.GATE, GateRenderer::new);
        ClientNet.handle(RacePayloads.CourseSyncS2C.TYPE, (payload, context) -> ClientCourses.apply(payload));
        ClientNet.handle(RacePayloads.RaceSettingsS2C.TYPE, (payload, context) -> applyLaps(payload.laps()));
        ClientNet.handle(RacePayloads.LeaderboardS2C.TYPE, (payload, context) -> ClientLeaderboards.apply(context.minecraft(), payload));
        ClientNet.handle(RacePayloads.GhostS2C.TYPE, (payload, context) -> ClientRaceTracker.INSTANCE.ghosts().accept(payload));
        ClientNet.handle(RacePayloads.LobbyS2C.TYPE, (payload, context) -> ClientRaceTracker.INSTANCE.onLobby(payload));
        ClientNet.handle(RacePayloads.RaceStartS2C.TYPE, (payload, context) -> ClientRaceTracker.INSTANCE.onStart(context.minecraft(), payload));
        GateMountKeys.register();
        GateEditor.register();
        GateEntity.setEditor(GateEditor::start);
        ClientBus.TICK_END.register(GateMountKeys::tick);
        ClientBus.TICK_END.register(GateEditor::tick);
        ClientBus.TICK_END.register(RaceClient::syncDefaults);
        ClientBus.LOGIN.register(minecraft -> sentLaps = CourseLaps.UNSET);
        PilotSession.HOME_RESET.register(ClientRaceTracker.INSTANCE::onHomeReset);
        ClientBus.FRAME_START.register((minecraft, deltaTracker) -> ClientRaceTracker.INSTANCE.onFrame(minecraft));
        ClientBus.DISCONNECT.register(minecraft -> {
            ClientRaceTracker.INSTANCE.reset();
            sentLaps = CourseLaps.UNSET;
            ClientCourses.clear();
            ClientLeaderboards.clear();
            GateMountKeys.reset();
            GateEditor.stop();
            FreeformGateRenderer.forget();
            FreeformPreview.forget();
        });
        ClientBus.REGISTER_HUD_LAYERS.register(() -> ClientRegistrar.hudLayer(Constants.id("race"), RaceHud::extract));
        ClientBus.REGISTER_HUD_LAYERS.register(() -> ClientRegistrar.hudLayer(Constants.id("gate_mount"), GatePreview::extractHud));
        ClientBus.REGISTER_HUD_LAYERS.register(() -> ClientRegistrar.hudLayer(Constants.id("gate_edit"), GateEditor::extractHud));
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(GhostRenderer::submit);
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(RaceLineRenderer::submit);
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(GatePreview::submit);
        ClientBus.SUBMIT_WORLD_GEOMETRY.register(GateEditor::submit);
        ConfigTabs.register(RaceConfigTab::new);
    }

    private static void syncDefaults(Minecraft minecraft) {
        int laps = RaceConfig.CONFIG.get().laps;
        if (laps == sentLaps || minecraft.player == null || !ClientNet.canSend(RacePayloads.CourseDefaultsC2S.TYPE)) {
            return;
        }
        sentLaps = laps;
        ClientNet.sendToServer(new RacePayloads.CourseDefaultsC2S(laps));
    }

    public static int sentDefaultLaps() {
        return sentLaps;
    }

    static void applyLaps(int laps) {
        RaceConfig config = RaceConfig.CONFIG.get();
        config.laps = Mth.clamp(laps, 0, RaceConfig.MAX_LAPS);
        RaceConfig.CONFIG.save();
        RaceHud.message(config.laps == 0 ? Component.translatable("propwash.race.msg.laps_set_endless")
                : Component.translatable("propwash.race.msg.laps_set", config.laps), RaceHud.WHITE);
    }
}

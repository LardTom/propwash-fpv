package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.bind.client.BindClient;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.client.dust.DustClient;
import de.lardtom.propwash.drone.client.trail.TrailClient;
import de.lardtom.propwash.drone.client.render.DroneRenderClient;
import de.lardtom.propwash.drone.client.render.PropDamageProperty;
import de.lardtom.propwash.drone.client.render.RemoteGimbalModel;
import de.lardtom.propwash.drone.client.render.DroneItemRenderer;
import de.lardtom.propwash.drone.client.render.RemoteGimbalRenderer;
import de.lardtom.propwash.drone.net.DroneResetS2C;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.registry.client.HudVisibility;
import de.lardtom.propwash.registry.client.VanillaHudLayer;
import de.lardtom.propwash.server.client.ClientServerRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class DroneClient {
    private static boolean initialized;

    private DroneClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        DroneClientConfig.CONFIG.load();
        InputKeys.bootstrap();
        QuadcopterEntity.setLocalPlayerCheck(PilotSession::isLocalPlayer);
        QuadcopterEntity.CLIENT_TICK.register(PilotSession.INSTANCE::onDroneClientTick);
        ClientBus.TICK_END.register(PilotSession.INSTANCE::onClientTick);
        ClientBus.TICK_END.register(PilotView::onClientTick);
        ClientBus.FRAME_START.register(PilotSession.INSTANCE::onFrame);
        ClientBus.FRAME_START.register(LosView::onFrame);
        ClientBus.FRAME_START.register(PilotBodyHud::onFrame);
        ClientRegistrar.hudLayer(PilotBodyHud.ID, PilotBodyHud::extract, HudVisibility.ALWAYS);
        QuadcopterEntity.CLIENT_TICK.register(PilotHeads::onDroneTick);
        ClientBus.TICK_END.register(PilotHeads::onClientTick);
        ClientBus.FRAME_START.register(PilotHeads::onFrame);
        ClientBus.DISCONNECT.register(mc -> PilotHeads.reset());
        ClientBus.DISCONNECT.register(PilotSession.INSTANCE::onDisconnect);
        ClientBus.DISCONNECT.register(mc -> PilotView.reset());
        RadioLink.SERVER_LIMIT = () -> ClientServerRules.INSTANCE.maxPilotDistance();
        RadioLink.LOCAL_FREE_RANGE = () -> RadioLink.freeRange(Minecraft.getInstance().player);
        ClientNet.handle(DroneResetS2C.TYPE, (payload, context) -> onReset(payload, context));
        ClientRegistrar.hideVanillaHud(VanillaHudLayer.CROSSHAIR, PilotView::active);
        ClientRegistrar.menuScreen(DroneModule.DRONE_SERVICE_MENU, DroneServiceScreen::new);
        ClientRegistrar.modelLayer(RemoteGimbalModel.LAYER, RemoteGimbalModel::createLayer);
        ClientRegistrar.specialModelRenderer(RemoteGimbalRenderer.TYPE, RemoteGimbalRenderer.Unbaked.MAP_CODEC);
        ClientRegistrar.specialModelRenderer(DroneItemRenderer.TYPE, DroneItemRenderer.Unbaked.MAP_CODEC);
        ClientRegistrar.rangeSelectItemProperty(PropDamageProperty.ID, PropDamageProperty.MAP_CODEC);
        ClientRegistrar.hideVanillaHud(VanillaHudLayer.CAMERA_OVERLAYS, FpvView::active);
        DroneRenderClient.init();
        DustClient.init();
        TrailClient.init();
        BindClient.init();
    }

    private static void onReset(DroneResetS2C payload, ClientNet.ClientPayloadContext context) {
        if (context.minecraft().level == null) {
            return;
        }
        Entity entity = context.minecraft().level.getEntity(payload.entityId());
        if (!(entity instanceof QuadcopterEntity drone)) {
            return;
        }
        if (PilotSession.INSTANCE.drone() == drone) {
            PilotSession.INSTANCE.onReset(payload);
            return;
        }
        Quaternionf orientation = new Quaternionf(payload.qx(), payload.qy(), payload.qz(), payload.qw()).normalize();
        drone.snapClientPose(new Vec3(payload.x(), payload.y(), payload.z()), orientation);
    }
}

package de.lardtom.propwash.fabric.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.lardtom.propwash.client.PropwashClient;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.join.JoinClientState;
import de.lardtom.propwash.network.JoinInfoPayload;
import de.lardtom.propwash.network.PartsManifestPayload;
import de.lardtom.propwash.network.ProtocolVersionPayload;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.parts.PartsModule;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.registry.client.HudVisibility;
import de.lardtom.propwash.registry.client.MenuScreenFactory;
import de.lardtom.propwash.registry.client.VanillaHudLayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

public final class PropwashFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PropwashClient.init();
        ClientRegistrar.freezeConfigScreen();
        registerKeyMappings();
        registerRenderers();
        registerMenuScreens();
        registerHud();
        registerRenderPipelines();
        registerSpecialModelRenderers();
        registerRangeSelectItemProperties();
        registerPictureInPictureRenderers();
        registerBlockTints();
        registerReloadListeners();
        registerPayloadHandlers();
        registerClientEvents();
    }

    private static void registerKeyMappings() {
        List<KeyMapping> mappings = ClientRegistrar.freezeKeyMappings();
        for (KeyMapping mapping : mappings) {
            KeyMappingHelper.registerKeyMapping(mapping);
        }
        ClientBus.KEY_MAPPINGS_REGISTERED.fire(l -> l.accept(mappings));
    }

    private static void registerRenderers() {
        ClientRegistrar.flushEntityRenderers(new ClientRegistrar.EntityRendererSink() {
            @Override
            public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                EntityRenderers.register(type, provider);
            }
        });
        ClientRegistrar.flushBlockEntityRenderers(new ClientRegistrar.BlockEntityRendererSink() {
            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void register(BlockEntityType<? extends T> type,
                                                                                         BlockEntityRendererProvider<T, S> provider) {
                BlockEntityRenderers.register(type, provider);
            }
        });
        for (ClientRegistrar.ModelLayerEntry entry : ClientRegistrar.freezeModelLayers()) {
            ModelLayerRegistry.registerModelLayer(entry.location(), () -> entry.definition().get());
        }
    }

    private static void registerMenuScreens() {
        ClientRegistrar.flushMenuScreens(new ClientRegistrar.MenuScreenSink() {
            @Override
            public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type,
                                                                                                   MenuScreenFactory<M, U> factory) {
                MenuScreens.<M, U>register(type, factory::create);
            }
        });
    }

    private static void registerHud() {
        ClientBus.REGISTER_HUD_LAYERS.fire(Runnable::run);
        Identifier lastAlways = VanillaHudElements.SLEEP;
        for (ClientRegistrar.HudLayerEntry entry : ClientRegistrar.freezeHudLayers()) {
            if (entry.visibility() == HudVisibility.ALWAYS) {
                HudElementRegistry.attachElementAfter(lastAlways, entry.id(), entry.layer()::extract);
                lastAlways = entry.id();
            } else {
                HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, entry.id(), entry.layer()::extract);
            }
        }
        for (ClientRegistrar.HiddenVanillaHud hidden : ClientRegistrar.freezeHiddenVanillaHud()) {
            HudElementRegistry.replaceElement(vanillaId(hidden.layer()), original -> (graphics, deltaTracker) -> {
                if (!hidden.hideWhen().getAsBoolean()) {
                    original.extractRenderState(graphics, deltaTracker);
                }
            });
        }
    }

    private static Identifier vanillaId(VanillaHudLayer layer) {
        return switch (layer) {
            case CAMERA_OVERLAYS -> VanillaHudElements.MISC_OVERLAYS;
            case CROSSHAIR -> VanillaHudElements.CROSSHAIR;
            case HOTBAR -> VanillaHudElements.HOTBAR;
            case HEALTH -> VanillaHudElements.HEALTH_BAR;
            case ARMOR -> VanillaHudElements.ARMOR_BAR;
            case FOOD -> VanillaHudElements.FOOD_BAR;
            case AIR -> VanillaHudElements.AIR_BAR;
            case VEHICLE_HEALTH -> VanillaHudElements.MOUNT_HEALTH;
            case EXPERIENCE_LEVEL -> VanillaHudElements.EXPERIENCE_LEVEL;
            case INFO_BAR -> VanillaHudElements.INFO_BAR;
            case HELD_ITEM_NAME -> VanillaHudElements.HELD_ITEM_TOOLTIP;
            case EFFECTS -> VanillaHudElements.MOB_EFFECTS;
            case BOSS_BAR -> VanillaHudElements.BOSS_BAR;
            case SCOREBOARD -> VanillaHudElements.SCOREBOARD;
            case OVERLAY_MESSAGE -> VanillaHudElements.OVERLAY_MESSAGE;
            case TITLE -> VanillaHudElements.TITLE_AND_SUBTITLE;
            case CHAT -> VanillaHudElements.CHAT;
            case PLAYER_LIST -> VanillaHudElements.PLAYER_LIST;
        };
    }

    private static void registerRenderPipelines() {
        for (RenderPipeline pipeline : ClientRegistrar.freezeRenderPipelines()) {
            RenderPipelines.registerOptional(pipeline);
        }
    }

    private static void registerSpecialModelRenderers() {
        for (ClientRegistrar.SpecialModelRendererEntry entry : ClientRegistrar.freezeSpecialModelRenderers()) {
            SpecialModelRenderers.ID_MAPPER.put(entry.id(), entry.codec());
        }
    }

    private static void registerRangeSelectItemProperties() {
        for (ClientRegistrar.RangeSelectPropertyEntry entry : ClientRegistrar.freezeRangeSelectItemProperties()) {
            RangeSelectItemModelProperties.ID_MAPPER.put(entry.id(), entry.codec());
        }
    }

    private static void registerPictureInPictureRenderers() {
        for (ClientRegistrar.PictureInPictureEntry<?> entry : ClientRegistrar.freezePictureInPictureRenderers()) {
            PictureInPictureRendererRegistry.register(context -> entry.create());
        }
    }

    private static void registerBlockTints() {
        for (ClientRegistrar.BlockTintEntry entry : ClientRegistrar.freezeBlockTints()) {
            BlockColorRegistry.register(entry.layers(), entry.block().get());
        }
    }

    private static void registerReloadListeners() {
        ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        for (ClientRegistrar.ReloadListenerEntry entry : ClientRegistrar.freezeReloadListeners()) {
            loader.registerReloadListener(entry.id(), entry.listener());
        }
    }

    private static void registerPayloadHandlers() {
        for (ClientNet.Handler<?> handler : ClientNet.freeze()) {
            registerHandler(handler);
        }
        ClientConfigurationNetworking.registerGlobalReceiver(ProtocolVersionPayload.TYPE, (payload, context) ->
                context.responseSender().sendPacket(ProtocolVersionPayload.local()));
        ClientConfigurationNetworking.registerGlobalReceiver(JoinInfoPayload.TYPE, (payload, context) ->
                context.responseSender().sendPacket(JoinClientState.onServerInfo(payload)));
        ClientConfigurationNetworking.registerGlobalReceiver(PartsManifestPayload.TYPE, (payload, context) ->
                context.responseSender().sendPacket(JoinClientState.onServerParts(payload)));
    }

    private static <T extends CustomPacketPayload> void registerHandler(ClientNet.Handler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(handler.type(), (payload, context) ->
                handler.handler().handle(payload, new ClientNet.ClientPayloadContext(context.client(), context.player())));
    }

    private static void registerClientEvents() {
        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> PartsModule.freeze());
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> ClientBus.TICK_END.fire(l -> l.accept(minecraft)));
        ClientPlayConnectionEvents.JOIN.register((listener, sender, minecraft) -> ClientBus.fireLogin(minecraft));
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> ClientBus.SUBMIT_WORLD_GEOMETRY.fire(l ->
                l.submit(context.submitNodeCollector(), context.poseStack(), context.levelState())));
    }
}

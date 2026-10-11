package de.lardtom.propwash.neoforge.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.client.PropwashClient;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.registry.client.ConfigScreenFactory;
import de.lardtom.propwash.registry.client.HudVisibility;
import de.lardtom.propwash.registry.client.MenuScreenFactory;
import de.lardtom.propwash.registry.client.VanillaHudLayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class PropwashNeoForgeClient {
    public PropwashNeoForgeClient(IEventBus modBus, ModContainer container) {
        PropwashClient.init();
        ConfigScreenFactory configScreen = ClientRegistrar.freezeConfigScreen();
        if (configScreen != null) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> configScreen.create(parent));
        }
        modBus.addListener(RegisterKeyMappingsEvent.class, PropwashNeoForgeClient::onRegisterKeyMappings);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, PropwashNeoForgeClient::onRegisterRenderers);
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, PropwashNeoForgeClient::onRegisterLayers);
        modBus.addListener(RegisterMenuScreensEvent.class, PropwashNeoForgeClient::onRegisterMenuScreens);
        modBus.addListener(RegisterGuiLayersEvent.class, PropwashNeoForgeClient::onRegisterGuiLayers);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, PropwashNeoForgeClient::onRegisterPayloadHandlers);
        modBus.addListener(RegisterRenderPipelinesEvent.class, PropwashNeoForgeClient::onRegisterRenderPipelines);
        modBus.addListener(RegisterSpecialModelRendererEvent.class, PropwashNeoForgeClient::onRegisterSpecialModelRenderers);
        modBus.addListener(RegisterRangeSelectItemModelPropertyEvent.class, PropwashNeoForgeClient::onRegisterRangeSelectItemProperties);
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class, PropwashNeoForgeClient::onRegisterPictureInPictureRenderers);
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class, PropwashNeoForgeClient::onRegisterBlockTints);
        modBus.addListener(AddClientReloadListenersEvent.class, PropwashNeoForgeClient::onAddReloadListeners);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> {
            Minecraft minecraft = Minecraft.getInstance();
            ClientBus.TICK_END.fire(l -> l.accept(minecraft));
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> ClientBus.fireLogin(Minecraft.getInstance()));
        bus.addListener(SubmitCustomGeometryEvent.class, e -> ClientBus.SUBMIT_WORLD_GEOMETRY.fire(l ->
                l.submit(e.getSubmitNodeCollector(), e.getPoseStack(), e.getLevelRenderState())));
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        List<KeyMapping> mappings = ClientRegistrar.freezeKeyMappings();
        KeyMapping.Category category = ClientRegistrar.createdKeyCategory();
        if (category != null) {
            event.registerCategory(category);
        }
        for (KeyMapping mapping : mappings) {
            event.register(mapping);
        }
        ClientBus.KEY_MAPPINGS_REGISTERED.fire(l -> l.accept(mappings));
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ClientRegistrar.flushEntityRenderers(new ClientRegistrar.EntityRendererSink() {
            @Override
            public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                event.registerEntityRenderer(type, provider);
            }
        });
        ClientRegistrar.flushBlockEntityRenderers(new ClientRegistrar.BlockEntityRendererSink() {
            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void register(BlockEntityType<? extends T> type,
                                                                                         BlockEntityRendererProvider<T, S> provider) {
                event.registerBlockEntityRenderer(type, provider);
            }
        });
    }

    private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (ClientRegistrar.ModelLayerEntry entry : ClientRegistrar.freezeModelLayers()) {
            event.registerLayerDefinition(entry.location(), entry.definition());
        }
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        ClientRegistrar.flushMenuScreens(new ClientRegistrar.MenuScreenSink() {
            @Override
            public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type,
                                                                                                   MenuScreenFactory<M, U> factory) {
                event.<M, U>register(type, factory::create);
            }
        });
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        ClientBus.REGISTER_HUD_LAYERS.fire(Runnable::run);
        Identifier lastAlways = VanillaGuiLayers.SLEEP_OVERLAY;
        for (ClientRegistrar.HudLayerEntry entry : ClientRegistrar.freezeHudLayers()) {
            if (entry.visibility() == HudVisibility.ALWAYS) {
                event.registerAbove(lastAlways, entry.id(), entry.layer()::extract);
                lastAlways = entry.id();
            } else {
                event.registerBelow(VanillaGuiLayers.CHAT, entry.id(), entry.layer()::extract);
            }
        }
        for (ClientRegistrar.HiddenVanillaHud hidden : ClientRegistrar.freezeHiddenVanillaHud()) {
            for (Identifier id : vanillaIds(hidden.layer())) {
                event.wrapLayer(id, original -> (graphics, deltaTracker) -> {
                    if (!hidden.hideWhen().getAsBoolean()) {
                        original.render(graphics, deltaTracker);
                    }
                });
            }
        }
    }

    private static List<Identifier> vanillaIds(VanillaHudLayer layer) {
        return switch (layer) {
            case CAMERA_OVERLAYS -> List.of(VanillaGuiLayers.CAMERA_OVERLAYS);
            case CROSSHAIR -> List.of(VanillaGuiLayers.CROSSHAIR);
            case HOTBAR -> List.of(VanillaGuiLayers.HOTBAR);
            case HEALTH -> List.of(VanillaGuiLayers.PLAYER_HEALTH);
            case ARMOR -> List.of(VanillaGuiLayers.ARMOR_LEVEL);
            case FOOD -> List.of(VanillaGuiLayers.FOOD_LEVEL);
            case AIR -> List.of(VanillaGuiLayers.AIR_LEVEL);
            case VEHICLE_HEALTH -> List.of(VanillaGuiLayers.VEHICLE_HEALTH);
            case EXPERIENCE_LEVEL -> List.of(VanillaGuiLayers.EXPERIENCE_LEVEL);
            case INFO_BAR -> List.of(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, VanillaGuiLayers.CONTEXTUAL_INFO_BAR);
            case HELD_ITEM_NAME -> List.of(VanillaGuiLayers.SELECTED_ITEM_NAME);
            case EFFECTS -> List.of(VanillaGuiLayers.EFFECTS);
            case BOSS_BAR -> List.of(VanillaGuiLayers.BOSS_OVERLAY);
            case SCOREBOARD -> List.of(VanillaGuiLayers.SCOREBOARD_SIDEBAR);
            case OVERLAY_MESSAGE -> List.of(VanillaGuiLayers.OVERLAY_MESSAGE);
            case TITLE -> List.of(VanillaGuiLayers.TITLE);
            case CHAT -> List.of(VanillaGuiLayers.CHAT);
            case PLAYER_LIST -> List.of(VanillaGuiLayers.TAB_LIST);
        };
    }

    private static void onRegisterSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        for (ClientRegistrar.SpecialModelRendererEntry entry : ClientRegistrar.freezeSpecialModelRenderers()) {
            event.register(entry.id(), entry.codec());
        }
    }

    private static void onRegisterRangeSelectItemProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        for (ClientRegistrar.RangeSelectPropertyEntry entry : ClientRegistrar.freezeRangeSelectItemProperties()) {
            event.register(entry.id(), entry.codec());
        }
    }

    private static void onRegisterPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        for (ClientRegistrar.PictureInPictureEntry<?> entry : ClientRegistrar.freezePictureInPictureRenderers()) {
            registerPictureInPicture(event, entry);
        }
    }

    private static <T extends PictureInPictureRenderState> void registerPictureInPicture(RegisterPictureInPictureRenderersEvent event,
                                                                                        ClientRegistrar.PictureInPictureEntry<T> entry) {
        event.register(entry.stateClass(), entry::create);
    }

    private static void onRegisterBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        for (ClientRegistrar.BlockTintEntry entry : ClientRegistrar.freezeBlockTints()) {
            event.register(entry.layers(), entry.block().get());
        }
    }

    private static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        for (ClientRegistrar.ReloadListenerEntry entry : ClientRegistrar.freezeReloadListeners()) {
            event.addListener(entry.id(), entry.listener());
        }
    }

    private static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        for (RenderPipeline pipeline : ClientRegistrar.freezeRenderPipelines()) {
            event.registerOptionalPipeline(pipeline);
        }
    }

    private static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        for (ClientNet.Handler<?> handler : ClientNet.freeze()) {
            registerHandler(event, handler);
        }
    }

    private static <T extends CustomPacketPayload> void registerHandler(RegisterClientPayloadHandlersEvent event, ClientNet.Handler<T> handler) {
        event.register(handler.type(), (payload, context) -> {
            if (context.player() instanceof LocalPlayer player) {
                handler.handler().handle(payload, new ClientNet.ClientPayloadContext(Minecraft.getInstance(), player));
            }
        });
    }
}

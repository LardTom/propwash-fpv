package de.lardtom.propwash.registry.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.serialization.MapCodec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.client.ClientServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class ClientRegistrar {
    public static final Identifier KEY_CATEGORY_ID = Constants.id("main");

    private static KeyMapping.@Nullable Category keyCategory;
    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<EntityRendererEntry<?>> ENTITY_RENDERERS = new ArrayList<>();
    private static final List<BlockEntityRendererEntry<?, ?>> BLOCK_ENTITY_RENDERERS = new ArrayList<>();
    private static final List<ModelLayerEntry> MODEL_LAYERS = new ArrayList<>();
    private static final List<MenuScreenEntry<?, ?>> MENU_SCREENS = new ArrayList<>();
    private static final List<HudLayerEntry> HUD_LAYERS = new ArrayList<>();
    private static final List<HiddenVanillaHud> HIDDEN_VANILLA = new ArrayList<>();
    private static final Set<Identifier> HUD_IDS = new HashSet<>();
    private static final List<RenderPipeline> RENDER_PIPELINES = new ArrayList<>();
    private static final Set<Identifier> PIPELINE_IDS = new HashSet<>();
    private static final List<SpecialModelRendererEntry> SPECIAL_MODEL_RENDERERS = new ArrayList<>();
    private static final Set<Identifier> SPECIAL_MODEL_RENDERER_IDS = new HashSet<>();
    private static final List<RangeSelectPropertyEntry> RANGE_SELECT_PROPERTIES = new ArrayList<>();
    private static final Set<Identifier> RANGE_SELECT_PROPERTY_IDS = new HashSet<>();
    private static final List<PictureInPictureEntry<?>> PICTURE_IN_PICTURE = new ArrayList<>();
    private static final Set<Class<?>> PICTURE_IN_PICTURE_STATES = new HashSet<>();
    private static final List<BlockTintEntry> BLOCK_TINTS = new ArrayList<>();
    private static final List<ReloadListenerEntry> RELOAD_LISTENERS = new ArrayList<>();
    private static final Set<Identifier> RELOAD_LISTENER_IDS = new HashSet<>();
    private static boolean keysFrozen;
    private static boolean renderersFrozen;
    private static boolean layersFrozen;
    private static boolean screensFrozen;
    private static boolean hudFrozen;
    private static boolean pipelinesFrozen;
    private static boolean specialRenderersFrozen;
    private static boolean rangeSelectPropertiesFrozen;
    private static boolean pictureInPictureFrozen;
    private static boolean blockTintsFrozen;
    private static boolean reloadListenersFrozen;
    private static @Nullable ConfigScreenFactory configScreen;
    private static boolean configScreenFrozen;

    private ClientRegistrar() {
    }

    public static synchronized KeyMapping.Category keyCategory() {
        if (keyCategory == null) {
            keyCategory = ClientServices.PLATFORM.createKeyCategory(KEY_CATEGORY_ID);
        }
        return keyCategory;
    }

    public static KeyMapping keyMapping(String name, int defaultKey) {
        return keyMapping(name, InputConstants.Type.KEYBOARD, defaultKey);
    }

    public static KeyMapping keyMapping(String name, InputConstants.Type type, int defaultValue) {
        checkOpen(keysFrozen, "Tastenbelegung " + name);
        KeyMapping mapping = new KeyMapping("key." + Constants.MOD_ID + "." + name, type, defaultValue, keyCategory());
        KEY_MAPPINGS.add(mapping);
        return mapping;
    }

    public static List<KeyMapping> keyMappings() {
        return Collections.unmodifiableList(KEY_MAPPINGS);
    }

    public static List<KeyMapping> freezeKeyMappings() {
        keysFrozen = true;
        return keyMappings();
    }

    public static KeyMapping.@Nullable Category createdKeyCategory() {
        return keyCategory;
    }

    public static <T extends Entity> void entityRenderer(Supplier<? extends EntityType<? extends T>> type, EntityRendererProvider<T> provider) {
        checkOpen(renderersFrozen, "Entity-Renderer");
        ENTITY_RENDERERS.add(new EntityRendererEntry<>(type, provider));
    }

    public static <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(
            Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider) {
        checkOpen(renderersFrozen, "Block-Entity-Renderer");
        BLOCK_ENTITY_RENDERERS.add(new BlockEntityRendererEntry<>(type, provider));
    }

    public static void modelLayer(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
        checkOpen(layersFrozen, "Modell-Ebene " + location);
        MODEL_LAYERS.add(new ModelLayerEntry(location, definition));
    }

    public static <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void menuScreen(
            Supplier<? extends MenuType<? extends M>> type, MenuScreenFactory<M, U> factory) {
        checkOpen(screensFrozen, "Menü-Screen");
        MENU_SCREENS.add(new MenuScreenEntry<>(type, factory));
    }

    public static void hudLayer(Identifier id, HudLayer layer) {
        hudLayer(id, layer, HudVisibility.WITH_HUD);
    }

    public static void hudLayer(Identifier id, HudLayer layer, HudVisibility visibility) {
        checkOpen(hudFrozen, "HUD-Ebene " + id);
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(visibility, "visibility");
        if (!HUD_IDS.add(id)) {
            throw new IllegalArgumentException("Doppelte HUD-Ebene " + id);
        }
        HUD_LAYERS.add(new HudLayerEntry(id, layer, visibility));
    }

    public static void hideVanillaHud(VanillaHudLayer layer, BooleanSupplier hideWhen) {
        checkOpen(hudFrozen, "HUD-Ausblendung " + layer);
        HIDDEN_VANILLA.add(new HiddenVanillaHud(layer, hideWhen));
    }

    public static void configScreen(ConfigScreenFactory factory) {
        checkOpen(configScreenFrozen, "Konfigurations-Screen");
        Objects.requireNonNull(factory, "factory");
        if (configScreen != null) {
            throw new IllegalStateException("Konfigurations-Screen ist schon angemeldet");
        }
        configScreen = factory;
    }

    public static @Nullable ConfigScreenFactory freezeConfigScreen() {
        configScreenFrozen = true;
        return configScreen;
    }

    public static @Nullable ConfigScreenFactory configScreenFactory() {
        return configScreen;
    }

    public static RenderPipeline renderPipeline(RenderPipeline pipeline) {
        checkOpen(pipelinesFrozen, "Render-Pipeline " + pipeline.getLocation());
        if (!Constants.MOD_ID.equals(pipeline.getLocation().getNamespace())) {
            throw new IllegalArgumentException("Render-Pipeline muss im Namespace " + Constants.MOD_ID + " liegen: " + pipeline.getLocation());
        }
        if (!PIPELINE_IDS.add(pipeline.getLocation())) {
            throw new IllegalArgumentException("Doppelte Render-Pipeline " + pipeline.getLocation());
        }
        RENDER_PIPELINES.add(pipeline);
        return pipeline;
    }

    public static List<RenderPipeline> freezeRenderPipelines() {
        pipelinesFrozen = true;
        return Collections.unmodifiableList(RENDER_PIPELINES);
    }

    public static void specialModelRenderer(Identifier id, MapCodec<? extends SpecialModelRenderer.Unbaked<?>> codec) {
        checkOpen(specialRenderersFrozen, "Special-Item-Renderer " + id);
        Objects.requireNonNull(codec, "codec");
        if (!Constants.MOD_ID.equals(id.getNamespace())) {
            throw new IllegalArgumentException("Special-Item-Renderer muss im Namespace " + Constants.MOD_ID + " liegen: " + id);
        }
        if (!SPECIAL_MODEL_RENDERER_IDS.add(id)) {
            throw new IllegalArgumentException("Doppelter Special-Item-Renderer " + id);
        }
        SPECIAL_MODEL_RENDERERS.add(new SpecialModelRendererEntry(id, codec));
    }

    public static List<SpecialModelRendererEntry> freezeSpecialModelRenderers() {
        specialRenderersFrozen = true;
        return Collections.unmodifiableList(SPECIAL_MODEL_RENDERERS);
    }

    public static void rangeSelectItemProperty(Identifier id, MapCodec<? extends RangeSelectItemModelProperty> codec) {
        checkOpen(rangeSelectPropertiesFrozen, "Item-Modell-Eigenschaft " + id);
        Objects.requireNonNull(codec, "codec");
        if (!Constants.MOD_ID.equals(id.getNamespace())) {
            throw new IllegalArgumentException("Item-Modell-Eigenschaft muss im Namespace " + Constants.MOD_ID + " liegen: " + id);
        }
        if (!RANGE_SELECT_PROPERTY_IDS.add(id)) {
            throw new IllegalArgumentException("Doppelte Item-Modell-Eigenschaft " + id);
        }
        RANGE_SELECT_PROPERTIES.add(new RangeSelectPropertyEntry(id, codec));
    }

    public static List<RangeSelectPropertyEntry> freezeRangeSelectItemProperties() {
        rangeSelectPropertiesFrozen = true;
        return Collections.unmodifiableList(RANGE_SELECT_PROPERTIES);
    }

    public static <T extends PictureInPictureRenderState> void pictureInPictureRenderer(Class<T> stateClass,
                                                                                      Supplier<? extends PictureInPictureRenderer<T>> factory) {
        checkOpen(pictureInPictureFrozen, "Bild-im-Bild-Renderer " + stateClass.getName());
        Objects.requireNonNull(factory, "factory");
        if (!PICTURE_IN_PICTURE_STATES.add(stateClass)) {
            throw new IllegalArgumentException("Doppelter Bild-im-Bild-Renderer für " + stateClass.getName());
        }
        PICTURE_IN_PICTURE.add(new PictureInPictureEntry<>(stateClass, factory));
    }

    public static List<PictureInPictureEntry<?>> freezePictureInPictureRenderers() {
        pictureInPictureFrozen = true;
        return Collections.unmodifiableList(PICTURE_IN_PICTURE);
    }

    public static void blockTints(Supplier<? extends Block> block, List<BlockTintSource> layers) {
        checkOpen(blockTintsFrozen, "Block-Farbe");
        Objects.requireNonNull(block, "block");
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("Block-Farbe ohne Ebenen");
        }
        BLOCK_TINTS.add(new BlockTintEntry(block, List.copyOf(layers)));
    }

    public static List<BlockTintEntry> freezeBlockTints() {
        blockTintsFrozen = true;
        return Collections.unmodifiableList(BLOCK_TINTS);
    }

    public static void reloadListener(Identifier id, PreparableReloadListener listener) {
        checkOpen(reloadListenersFrozen, "Reload-Listener " + id);
        Objects.requireNonNull(listener, "listener");
        if (!Constants.MOD_ID.equals(id.getNamespace())) {
            throw new IllegalArgumentException("Reload-Listener muss im Namespace " + Constants.MOD_ID + " liegen: " + id);
        }
        if (!RELOAD_LISTENER_IDS.add(id)) {
            throw new IllegalArgumentException("Doppelter Reload-Listener " + id);
        }
        RELOAD_LISTENERS.add(new ReloadListenerEntry(id, listener));
    }

    public static List<ReloadListenerEntry> freezeReloadListeners() {
        reloadListenersFrozen = true;
        return Collections.unmodifiableList(RELOAD_LISTENERS);
    }

    public static void flushEntityRenderers(EntityRendererSink sink) {
        renderersFrozen = true;
        for (EntityRendererEntry<?> e : ENTITY_RENDERERS) {
            e.registerTo(sink);
        }
    }

    public static void flushBlockEntityRenderers(BlockEntityRendererSink sink) {
        renderersFrozen = true;
        for (BlockEntityRendererEntry<?, ?> e : BLOCK_ENTITY_RENDERERS) {
            e.registerTo(sink);
        }
    }

    public static List<ModelLayerEntry> freezeModelLayers() {
        layersFrozen = true;
        return Collections.unmodifiableList(MODEL_LAYERS);
    }

    public static void flushMenuScreens(MenuScreenSink sink) {
        screensFrozen = true;
        for (MenuScreenEntry<?, ?> e : MENU_SCREENS) {
            e.registerTo(sink);
        }
    }

    public static List<HudLayerEntry> freezeHudLayers() {
        hudFrozen = true;
        List<HudLayerEntry> guarded = new ArrayList<>();
        for (HudLayerEntry e : HUD_LAYERS) {
            if (e.visibility() == HudVisibility.ALWAYS) {
                guarded.add(e);
                continue;
            }
            HudLayer inner = e.layer();
            guarded.add(new HudLayerEntry(e.id(), (g, dt) -> {
                if (!Minecraft.getInstance().gui.hud.isHidden()) {
                    inner.extract(g, dt);
                }
            }, e.visibility()));
        }
        return Collections.unmodifiableList(guarded);
    }

    public static List<HiddenVanillaHud> freezeHiddenVanillaHud() {
        hudFrozen = true;
        return Collections.unmodifiableList(HIDDEN_VANILLA);
    }

    private static void checkOpen(boolean frozen, String what) {
        if (frozen) {
            throw new IllegalStateException(what + " zu spät registriert (nur in der Client-init() des Moduls)");
        }
    }

    public interface EntityRendererSink {
        <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider);
    }

    public interface BlockEntityRendererSink {
        <T extends BlockEntity, S extends BlockEntityRenderState> void register(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider);
    }

    public interface MenuScreenSink {
        <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type, MenuScreenFactory<M, U> factory);
    }

    public record ModelLayerEntry(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
    }

    public record SpecialModelRendererEntry(Identifier id, MapCodec<? extends SpecialModelRenderer.Unbaked<?>> codec) {
    }

    public record RangeSelectPropertyEntry(Identifier id, MapCodec<? extends RangeSelectItemModelProperty> codec) {
    }

    public record PictureInPictureEntry<T extends PictureInPictureRenderState>(Class<T> stateClass,
                                                                               Supplier<? extends PictureInPictureRenderer<T>> factory) {
        public PictureInPictureRenderer<T> create() {
            return this.factory.get();
        }
    }

    public record HudLayerEntry(Identifier id, HudLayer layer, HudVisibility visibility) {
    }

    public record BlockTintEntry(Supplier<? extends Block> block, List<BlockTintSource> layers) {
    }

    public record ReloadListenerEntry(Identifier id, PreparableReloadListener listener) {
    }

    public record HiddenVanillaHud(VanillaHudLayer layer, BooleanSupplier hideWhen) {
    }

    private record EntityRendererEntry<T extends Entity>(Supplier<? extends EntityType<? extends T>> type, EntityRendererProvider<T> provider) {
        void registerTo(EntityRendererSink sink) {
            sink.register(this.type.get(), this.provider);
        }
    }

    private record BlockEntityRendererEntry<T extends BlockEntity, S extends BlockEntityRenderState>(
            Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererProvider<T, S> provider) {
        void registerTo(BlockEntityRendererSink sink) {
            sink.register(this.type.get(), this.provider);
        }
    }

    private record MenuScreenEntry<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>>(
            Supplier<? extends MenuType<? extends M>> type, MenuScreenFactory<M, U> factory) {
        void registerTo(MenuScreenSink sink) {
            sink.register(this.type.get(), this.factory);
        }
    }
}

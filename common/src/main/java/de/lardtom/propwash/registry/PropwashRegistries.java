package de.lardtom.propwash.registry;

import com.mojang.serialization.MapCodec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class PropwashRegistries {
    public static final Identifier TOOLTIP_STYLE = Constants.id("propwash");
    private static final Map<ResourceKey<? extends Registry<?>>, RegList<?>> LISTS = new LinkedHashMap<>();
    private static boolean listsClosed;
    private static volatile boolean commonInitClosed;

    public static final RegList<DataComponentType<?>> DATA_COMPONENTS = list(Registries.DATA_COMPONENT_TYPE);
    public static final RegList<SoundEvent> SOUNDS = list(Registries.SOUND_EVENT);
    public static final RegList<Block> BLOCKS = list(Registries.BLOCK);
    public static final RegList<EntityType<?>> ENTITY_TYPES = list(Registries.ENTITY_TYPE);
    public static final RegList<Item> ITEMS = list(Registries.ITEM);
    public static final RegList<BlockEntityType<?>> BLOCK_ENTITY_TYPES = list(Registries.BLOCK_ENTITY_TYPE);
    public static final RegList<MenuType<?>> MENUS = list(Registries.MENU);
    public static final RegList<RecipeSerializer<?>> RECIPE_SERIALIZERS = list(Registries.RECIPE_SERIALIZER);
    public static final RegList<CreativeModeTab> CREATIVE_TABS = list(Registries.CREATIVE_MODE_TAB);
    public static final RegList<TicketType> TICKET_TYPES = list(Registries.TICKET_TYPE);

    private static final List<SerializerEntry> ENTITY_DATA_SERIALIZERS = new ArrayList<>();
    private static final Set<Identifier> SERIALIZER_IDS = new HashSet<>();
    private static boolean serializersFlushed;

    private PropwashRegistries() {
    }

    @SuppressWarnings("unchecked")
    public static synchronized <R> RegList<R> list(ResourceKey<? extends Registry<R>> key) {
        RegList<?> existing = LISTS.get(key);
        if (existing != null) {
            return (RegList<R>) existing;
        }
        if (listsClosed || commonInitClosed) {
            throw new IllegalStateException("Register-Liste für " + key.identifier() + " zu spät angelegt (nur in der Common-init() des Moduls)");
        }
        if (!BuiltInRegistries.REGISTRY.containsKey(key.identifier())) {
            throw new IllegalArgumentException(key.identifier() + " ist kein statisches Register (BuiltInRegistries);"
                    + " Datapack-Register werden per JSON unter data/" + Constants.MOD_ID + "/ befüllt");
        }
        RegList<R> list = new RegList<>(key);
        LISTS.put(key, list);
        return list;
    }

    public static void closeAfterCommonInit() {
        commonInitClosed = true;
    }

    public static boolean isClosedAfterCommonInit() {
        return commonInitClosed;
    }

    static String lateRegistrationHint(String what) {
        return what + " nach PropwashCommon.init() angemeldet: Registereinträge, SoundEvents, Serializer und Payloads gehören"
                + " in die Common-init() des Moduls (z. B. audio.AudioModule), nie in die Client-init()";
    }

    public static synchronized List<RegList<?>> inFlushOrder() {
        listsClosed = true;
        Map<Identifier, RegList<?>> byId = new LinkedHashMap<>();
        for (RegList<?> list : LISTS.values()) {
            byId.put(list.registryKey().identifier(), list);
        }
        List<RegList<?>> ordered = new ArrayList<>(byId.size());
        for (Identifier id : registrationOrder()) {
            RegList<?> list = byId.remove(id);
            if (list != null) {
                ordered.add(list);
            }
        }
        ordered.addAll(byId.values());
        return List.copyOf(ordered);
    }

    public static Set<Identifier> registrationOrder() {
        Set<Identifier> order = new LinkedHashSet<>();
        order.add(Registries.ATTRIBUTE.identifier());
        order.add(Registries.DATA_COMPONENT_TYPE.identifier());
        order.add(Registries.PARTICLE_TYPE.identifier());
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            Identifier id = registry.key().identifier();
            if (Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                order.add(id);
            }
        }
        BuiltInRegistries.REGISTRY.keySet().stream()
                .sorted(Comparator.comparing(Identifier::getNamespace).thenComparing(Identifier::getPath))
                .forEach(order::add);
        return order;
    }

    private static Item.Properties itemProperties() {
        return new Item.Properties().component(DataComponents.TOOLTIP_STYLE, TOOLTIP_STYLE);
    }

    public static <T extends Item> RegHolder<T> item(String name, Function<Item.Properties, T> constructor, UnaryOperator<Item.Properties> props) {
        return ITEMS.add(name, key -> constructor.apply(props.apply(itemProperties()).setId(key)));
    }

    public static RegHolder<Item> item(String name, UnaryOperator<Item.Properties> props) {
        return item(name, Item::new, props);
    }

    public static <T extends Block> RegHolder<T> block(String name, Function<BlockBehaviour.Properties, T> constructor,
                                                        Supplier<BlockBehaviour.Properties> props) {
        return BLOCKS.add(name, key -> constructor.apply(props.get().setId(key)));
    }

    public static RegHolder<BlockItem> blockItem(RegHolder<? extends Block> block, UnaryOperator<Item.Properties> props) {
        String name = block.id().getPath();
        return ITEMS.add(name, key -> new BlockItem(block.get(), props.apply(itemProperties()).useBlockDescriptionPrefix().setId(key)));
    }

    public static <T extends Block> BlockWithItem<T> blockWithItem(String name, Function<BlockBehaviour.Properties, T> constructor,
                                                                   Supplier<BlockBehaviour.Properties> props,
                                                                   UnaryOperator<Item.Properties> itemProps) {
        RegHolder<T> block = block(name, constructor, props);
        return new BlockWithItem<>(block, blockItem(block, itemProps));
    }

    public static RegHolder<BlockItem> blockItem(RegHolder<? extends Block> block, BiFunction<Block, Item.Properties, ? extends BlockItem> constructor,
                                                 UnaryOperator<Item.Properties> props) {
        String name = block.id().getPath();
        return ITEMS.<BlockItem>add(name, key -> constructor.apply(block.get(), props.apply(itemProperties()).useBlockDescriptionPrefix().setId(key)));
    }

    public static <T extends Block> BlockWithItem<T> blockWithItem(String name, Function<BlockBehaviour.Properties, T> constructor,
                                                                   Supplier<BlockBehaviour.Properties> props,
                                                                   BiFunction<Block, Item.Properties, ? extends BlockItem> itemConstructor,
                                                                   UnaryOperator<Item.Properties> itemProps) {
        RegHolder<T> block = block(name, constructor, props);
        return new BlockWithItem<>(block, blockItem(block, itemConstructor, itemProps));
    }

    public record BlockWithItem<T extends Block>(RegHolder<T> block, RegHolder<BlockItem> item) {
    }

    @SafeVarargs
    public static <T extends BlockEntity> RegHolder<BlockEntityType<T>> blockEntity(String name, BlockEntityType.BlockEntitySupplier<T> factory,
                                                                                    RegHolder<? extends Block>... validBlocks) {
        return BLOCK_ENTITY_TYPES.add(name, key -> {
            Set<Block> blocks = new HashSet<>();
            for (RegHolder<? extends Block> b : validBlocks) {
                blocks.add(b.get());
            }
            return new BlockEntityType<T>(factory, Set.copyOf(blocks));
        });
    }

    public static <T extends Entity> RegHolder<EntityType<T>> entity(String name, Supplier<EntityType.Builder<T>> builder) {
        return ENTITY_TYPES.add(name, key -> builder.get().build(key));
    }

    public static <T> EntityDataSerializer<T> entityDataSerializer(String name, EntityDataSerializer<T> serializer) {
        if (serializersFlushed) {
            throw new IllegalStateException("Entity-Data-Serializer '" + name + "' zu spät angemeldet");
        }
        if (commonInitClosed) {
            throw new IllegalStateException(lateRegistrationHint("Entity-Data-Serializer '" + name + "'"));
        }
        Identifier id = Constants.id(name);
        if (!SERIALIZER_IDS.add(id)) {
            throw new IllegalArgumentException("Doppelter Entity-Data-Serializer " + id);
        }
        ENTITY_DATA_SERIALIZERS.add(new SerializerEntry(id, serializer));
        return serializer;
    }

    public static List<SerializerEntry> flushEntityDataSerializers() {
        serializersFlushed = true;
        return Collections.unmodifiableList(ENTITY_DATA_SERIALIZERS);
    }

    public record SerializerEntry(Identifier id, EntityDataSerializer<?> serializer) {
    }

    public static <T> RegHolder<DataComponentType<T>> component(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return DATA_COMPONENTS.add(name, key -> builder.apply(DataComponentType.<T>builder()).build());
    }

    public static RegHolder<SoundEvent> sound(String name) {
        return SOUNDS.add(name, key -> SoundEvent.createVariableRangeEvent(key.identifier()));
    }

    public static <T extends AbstractContainerMenu> RegHolder<MenuType<T>> menu(String name, SimpleMenuFactory<T> clientFactory) {
        return MENUS.add(name, key -> new MenuType<T>(clientFactory::create, FeatureFlags.VANILLA_SET));
    }

    public static <T extends AbstractContainerMenu, D> RegHolder<MenuType<T>> extendedMenu(String name, MenuDataFactory<T, D> clientFactory,
                                                                                         StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return MENUS.add(name, key -> Services.PLATFORM.extendedMenuType(clientFactory, codec));
    }

    public static RegHolder<CreativeModeTab> creativeTab(String name, UnaryOperator<CreativeModeTab.Builder> builder) {
        return CREATIVE_TABS.add(name, key -> builder.apply(Services.PLATFORM.creativeTabBuilder()).build());
    }

    public static RegHolder<TicketType> ticketType(String name, long timeout, int flags) {
        return TICKET_TYPES.add(name, key -> new TicketType(timeout, flags));
    }

    public static <T extends Recipe<?>> RegHolder<RecipeSerializer<T>> recipeSerializer(String name, MapCodec<T> codec,
                                                                                     StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
        return RECIPE_SERIALIZERS.add(name, key -> new RecipeSerializer<T>(codec, streamCodec));
    }
}

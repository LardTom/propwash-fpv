# Registration & content plumbing (MC 26.3, Fabric + NeoForge)

> **Projekt-Layout veraltet:** Die eine Mixin-Config `propwash.mixins.json` (stattdessen je Modul `propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`), Registrierungs-Klassen (`PropwashItems`, `PropwashEntities`, `PropwashComponents`, `RegList.Ref`, …), `CommonClass`, das Paket `net` (heute `network`) und Loader-Einstiege in dieser Notiz stammen aus der Zeit vor der Foundation. Kommentare in den Code-Beispielen sind nur Erklärung: im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`). Maßgeblich sind `docs/CONVENTIONS.md` und `docs/FOUNDATION.md` (Kasten „Abweichungen von docs/api“). Die Minecraft-/Loader-APIs hier stimmen weiterhin.

All paths are relative to `<repo>/.ref/`:
`vanilla/`, `neoforge/` (patched MC), `neoforge-api/`, `fabric-api/<module>/`.

## 0. TL;DR: what changed since 1.21.x (all verified in the sources)

- `ResourceLocation` is now **`net.minecraft.resources.Identifier`** (`fromNamespaceAndPath`, `parse`, `withDefaultNamespace`): `vanilla/net/minecraft/resources/Identifier.java:40-48`.
- `Item.Properties` and `BlockBehaviour.Properties` **must get `setId(ResourceKey)` before the constructor runs**. Otherwise you get `NullPointerException: "Item id not set"` / `"Block id not set"` (`vanilla/net/minecraft/world/item/Item.java:680`, `vanilla/net/minecraft/world/level/block/state/BlockBehaviour.java:1167`).
- Item default components are **lazy**. `Item(Properties)` only queues a `DataComponentInitializers.Initializer` (`Item.java:148-153`), and the initializers run on datapack reload (`vanilla/net/minecraft/server/ReloadableServerResources.java:87`). Until then `item.components()` throws `"Components not bound yet"` (`vanilla/net/minecraft/core/Holder.java:269`). `new ItemStack(item)` calls it too (`ItemStack.java:249`), so **do not build ItemStacks during registration or in static init**. The client binds them on join (`vanilla/net/minecraft/client/multiplayer/RegistryDataCollector.java:154`). `Holder#areComponentsBound()` (`Holder.java:21`) lets you check. NeoForge also fires `DefaultDataComponentsBoundEvent` afterwards (`neoforge-api/net/neoforged/neoforge/event/DefaultDataComponentsBoundEvent.java`).
- The `Block` subclass `codec()` requirement is gone. `CraftingTableBlock` and `StonecutterBlock` have no codec.
- Vanilla constants moved into plural holder classes: `EntityTypes.PIG` (`vanilla/net/minecraft/world/entity/EntityTypes.java:791`), `BlockEntityTypes.FURNACE` (`vanilla/net/minecraft/world/level/block/entity/BlockEntityTypes.java:20`) and `RecipeSerializers.bootstrap` (`vanilla/net/minecraft/world/item/crafting/RecipeSerializers.java:6`). Only the id constants `ItemIds`, `BlockItemIds` and `BlockIds` live in `vanilla/net/minecraft/references/`. `EntityTypeIds` is in `world/entity/`.
- `RecipeSerializer` is now a **record** `RecipeSerializer<T>(MapCodec<T> codec, @Deprecated StreamCodec<RegistryFriendlyByteBuf,T> streamCodec)` (`vanilla/net/minecraft/world/item/crafting/RecipeSerializer.java`).
- GUI rendering: `GuiGraphics` became **`GuiGraphicsExtractor`** (`vanilla/net/minecraft/client/gui/GuiGraphicsExtractor.java`; no `GuiGraphics.java` exists). Container screens no longer have `renderBg`. You override `Screen#extractBackground(GuiGraphicsExtractor, int mouseX, int mouseY, float a)` (`vanilla/net/minecraft/client/gui/screens/Screen.java:383`). The in-game HUD is `Hud` (`vanilla/net/minecraft/client/gui/Hud.java:89`). `Gui` **still exists** (`client/gui/Gui.java:70`), but now it is the screen/overlay/toast manager (`setScreen`, `toastManager`, …), not the HUD.
- Fabric API has Mojang names: **`FabricItemGroup` → `FabricCreativeModeTab`** (`fabric-creative-tab-api-v1`), and **`ExtendedScreenHandlerType` → `ExtendedMenuType`** (`fabric-menu-api-v1`). A grep over `fabric-api/` finds no match for the old names.
- NeoForge **freezes all registries outside `RegisterEvent`**. The mod constructor runs inside `gatherAndInitializeMods`, and only `GameData.unfreezeData(); postRegisterEvents(); freezeData()` opens a window (`neoforge-api/net/neoforged/neoforge/internal/CommonModLoader.java:48-56`). So **common code must never call `Registry.register` at class-init time**. Fabric instead delays vanilla's freeze: `BootstrapMixin` redirects `BuiltInRegistries.bootStrap()` to `createContents()` only (`fabric-api/fabric-registry-sync-v0/net/fabricmc/fabric/mixin/registry/sync/BootstrapMixin.java:59-62`), so `onInitialize` can register directly.
- Pack formats for 26.3: **resource 97.1, data 121.0** (`vanilla/version.json`, `vanilla/net/minecraft/SharedConstants.java:27-33`).

## 1. Vanilla registration primitives

```java
// vanilla/net/minecraft/core/Registry.java:107-126
static <T> T register(Registry<? super T> registry, String name, T value)
static <V, T extends V> T register(Registry<V> registry, Identifier location, T value)
static <V, T extends V> T register(Registry<V> registry, ResourceKey<V> key, T value)
static <R, T extends R> Holder.Reference<T> registerForHolder(Registry<R> registry, ResourceKey<R> key, T value)
Holder<T> wrapAsHolder(T value);                         // Registry.java:136
// vanilla/net/minecraft/resources/ResourceKey.java:28
static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryName, Identifier location)
```
The registry keys are in `vanilla/net/minecraft/core/registries/Registries.java`: `ITEM`, `BLOCK`, `BLOCK_ENTITY_TYPE`, `ENTITY_TYPE`, `DATA_COMPONENT_TYPE`, `CREATIVE_MODE_TAB`, `MENU`, `SOUND_EVENT`, `RECIPE_SERIALIZER`, `RECIPE_TYPE`. The instances are in `BuiltInRegistries.java`.

This is how vanilla registers an item (`vanilla/net/minecraft/world/item/Items.java:3026`):
```java
private static Item registerItem(ResourceKey<Item> id, Function<Item.Properties, Item> itemFactory, Item.Properties properties) {
    Item item = itemFactory.apply(properties.setId(id));
    if (item instanceof BlockItem blockItem) blockItem.registerBlocks(Item.BY_BLOCK, item);
    return Registry.register(BuiltInRegistries.ITEM, id, item);
}
```
Blocks follow the same shape (`vanilla/net/minecraft/world/level/block/Blocks.java:5922`): `factory.apply(properties.setId(id))`, then `Registry.register`. Mods do not need the `BY_BLOCK` line. Fabric does it in `fabric-api/fabric-registry-sync-v0/net/fabricmc/fabric/impl/registry/sync/trackers/vanilla/BlockItemTracker.java:37`, and NeoForge does it in `neoforge-api/net/neoforged/neoforge/registries/NeoForgeRegistryCallbacks.java:99`.

## 2. Recommended multi-loader pattern: common declares, the loader flushes

Common code holds **all definitions** as lazy factories in a tiny list class and has no loader API. Each loader calls `flush` once, while its registry is writable. On Fabric that is directly in `onInitialize()`. On NeoForge it is inside `RegisterEvent`. You don't need a ServiceLoader or the mod bus inside common.

```java
// common: de/lardtom/propwash/registry/RegList.java
public final class RegList<R> {
    private final ResourceKey<? extends Registry<R>> registryKey;
    private final List<Entry<R, ?>> entries = new ArrayList<>();
    public RegList(ResourceKey<? extends Registry<R>> registryKey) { this.registryKey = registryKey; }
    public ResourceKey<? extends Registry<R>> registryKey() { return registryKey; }

    public <T extends R> Ref<T> add(String path, Function<ResourceKey<R>, T> factory) {
        ResourceKey<R> key = ResourceKey.create(registryKey, Identifier.fromNamespaceAndPath(Constants.MOD_ID, path));
        Entry<R, T> e = new Entry<>(key, factory, new Ref<>(key));
        entries.add(e);
        return e.ref;
    }
    /** Loader calls this exactly once while the registry is unfrozen. */
    public void flush(BiConsumer<ResourceKey<R>, R> sink) { for (Entry<R, ?> e : entries) e.create(sink); }

    public static final class Ref<T> implements Supplier<T> {
        private final ResourceKey<?> key; private @Nullable T value;
        Ref(ResourceKey<?> key) { this.key = key; }
        public ResourceKey<?> key() { return key; }
        @Override public T get() { if (value == null) throw new IllegalStateException("Not registered yet: " + key); return value; }
    }
    private record Entry<R, T extends R>(ResourceKey<R> key, Function<ResourceKey<R>, T> factory, Ref<T> ref) {
        void create(BiConsumer<ResourceKey<R>, R> sink) { T v = factory.apply(key); sink.accept(key, v); ref.value = v; }
    }
}
```

**Fabric glue** (`fabric/.../Propwash.java`, in `onInitialize`). The order matters because Fabric has no ordering of its own:
```java
static <R> void flush(Registry<R> reg, RegList<R> list) { list.flush((k, v) -> Registry.register(reg, k, v)); }
// onInitialize():
flush(BuiltInRegistries.DATA_COMPONENT_TYPE, PropwashComponents.COMPONENTS); // items reference them
flush(BuiltInRegistries.SOUND_EVENT,         PropwashSounds.SOUNDS);         // equip sounds
flush(BuiltInRegistries.BLOCK,               PropwashBlocks.BLOCKS);
flush(BuiltInRegistries.ENTITY_TYPE,         PropwashEntities.ENTITIES);
flush(BuiltInRegistries.ITEM,                PropwashItems.ITEMS);           // BlockItems need blocks
flush(BuiltInRegistries.BLOCK_ENTITY_TYPE,   PropwashBlockEntities.TYPES);
flush(BuiltInRegistries.MENU,                PropwashMenus.MENUS);
flush(BuiltInRegistries.RECIPE_SERIALIZER,   PropwashRecipes.SERIALIZERS);   // only if custom recipes
flush(BuiltInRegistries.CREATIVE_MODE_TAB,   PropwashTabs.TABS);
CommonClass.init();
```

**NeoForge glue** (`neoforge/.../Propwash.java`, the constructor gets the mod bus):
```java
public Propwash(IEventBus modBus) {
    modBus.addListener(RegisterEvent.class, e -> {
        flush(e, PropwashComponents.COMPONENTS); flush(e, PropwashSounds.SOUNDS); flush(e, PropwashBlocks.BLOCKS);
        flush(e, PropwashEntities.ENTITIES);     flush(e, PropwashItems.ITEMS);   flush(e, PropwashBlockEntities.TYPES);
        flush(e, PropwashMenus.MENUS);           flush(e, PropwashRecipes.SERIALIZERS); flush(e, PropwashTabs.TABS);
    });
    CommonClass.init();
}
static <R> void flush(RegisterEvent e, RegList<R> list) {
    e.register(list.registryKey(), helper -> list.flush((k, v) -> helper.register(k, v)));
}
```
- `RegisterEvent.register(ResourceKey<? extends Registry<T>>, Consumer<RegisterHelper<T>>)` only runs the consumer when the key matches the current event (`neoforge-api/net/neoforged/neoforge/registries/RegisterEvent.java:60`). That makes it safe to call every flush in every event.
- `RegisterHelper.register(ResourceKey<T>, T)` is at `RegisterEvent.java:99`.
- `IEventBus` is `net.neoforged.bus.api.IEventBus` (the bus library, not in .ref; see the import in `neoforge/src/main/java/de/lardtom/propwash/Propwash.java`). The `addListener(Class<E>, Consumer<E>)` form is the one NeoForge's own javadoc uses (`neoforge-api/net/neoforged/neoforge/capabilities/BlockCapability.java:47`).
- NeoForge fires `RegisterEvent` in a fixed order: ATTRIBUTE, then **DATA_COMPONENT_TYPE**, then PARTICLE_TYPE, then the vanilla `BuiltInRegistries` declaration order (SOUND_EVENT, BLOCK, ENTITY_TYPE, ITEM, BLOCK_ENTITY_TYPE, MENU, RECIPE_TYPE, RECIPE_SERIALIZER, CREATIVE_MODE_TAB), then modded registries (`neoforge-api/net/neoforged/neoforge/registries/GameData.java:125-131`; the vanilla order is `BuiltInRegistries.getVanillaRegistrationOrder()`, `neoforge/net/minecraft/core/registries/BuiltInRegistries.java:445`). The Fabric order above mirrors it.
- The NeoForge alternative is `DeferredRegister.createItems(modid)` with `registerItem(name, Function<Item.Properties,I>, UnaryOperator<Item.Properties>)`, which calls `setId` for you (`neoforge-api/net/neoforged/neoforge/registries/DeferredRegister.java:679-697`; its `registerSimpleBlockItem` adds `useBlockDescriptionPrefix()` itself, line 573-574). It is NeoForge-only, so we don't use it in common.

## 3. Items and blocks

```java
// common: PropwashBlocks
public static final RegList<Block> BLOCKS = new RegList<>(Registries.BLOCK);
public static final RegList.Ref<DroneWorkbenchBlock> DRONE_WORKBENCH = BLOCKS.add("drone_workbench",
    key -> new DroneWorkbenchBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL).setId(key)));   // setId LAST is fine, but before ctor

// common: PropwashItems
public static final RegList<Item> ITEMS = new RegList<>(Registries.ITEM);
public static final RegList.Ref<BlockItem> DRONE_WORKBENCH = ITEMS.add("drone_workbench",
    key -> new BlockItem(PropwashBlocks.DRONE_WORKBENCH.get(), new Item.Properties().useBlockDescriptionPrefix().setId(key)));
public static final RegList.Ref<Item> MOTOR_2207 = item("motor_2207", Item::new,
    p -> p.component(PropwashComponents.MOTOR.get(), MotorType.M2207_1750));
// One component type per part enum (MOTOR, FRAME, STACK, PROP, VIDEO, BATTERY) whose value is the existing
// de.lardtom.propwash.build.* enum. Enums are immutable and use identity equals, so they are ideal component values (section 5).

static <T extends Item> RegList.Ref<T> item(String name, Function<Item.Properties, T> ctor, UnaryOperator<Item.Properties> props) {
    return ITEMS.add(name, key -> ctor.apply(props.apply(new Item.Properties()).setId(key)));
}
```
Relevant `Item.Properties` methods (`vanilla/net/minecraft/world/item/Item.java:384-720`):
- `stacksTo(int)`, `durability(int)`, `rarity(Rarity)`, `fireResistant()`
- `equippable(EquipmentSlot)`, `equippableUnswappable(EquipmentSlot)`, `humanoidArmor(ArmorMaterial, ArmorType)`
- `component(DataComponentType<T>, T)`, `delayedComponent(...)`
- `useBlockDescriptionPrefix()`, `overrideDescription(String)`, `setId(ResourceKey<Item>)`, `effectiveModel()`

The model id defaults to the item id (`model = ResourceKey::identifier`, line 394). `finalizeInitializer` then sets `ITEM_NAME` and `ITEM_MODEL` automatically (lines 723-725). Other useful methods are `delayedHolderComponent(DataComponentType<Holder<T>>, ResourceKey<T>)` (line 701) and `useItemDescriptionPrefix()` (line 674).

Pitfalls:
- A `BlockItem` must call **`useBlockDescriptionPrefix()`**, as vanilla does at `Items.java:2999`. Without it the name key becomes `item.propwash.drone_workbench` instead of `block.propwash.drone_workbench`.
- In the IDE, vanilla logs an error when an Item subclass name does not end in `Item` (`Item.java:156-160`) or a Block subclass name does not end in `Block` (`vanilla/net/minecraft/world/level/block/Block.java:233-234`). Name the classes `FpvGogglesItem`, `DroneWorkbenchBlock`, and so on.
- A validator throws `"Item cannot have both durability and be stackable"` (`Item.java:727`).
- The default block loot table is `<ns>:blocks/<path>` (`BlockBehaviour.java:989`). Ship `data/propwash/loot_table/blocks/drone_workbench.json`, or the block drops nothing.

## 4. Block entities, entity types, sounds (short)

```java
// BlockEntityType ctor is PUBLIC in 26.3: vanilla/net/minecraft/world/level/block/entity/BlockEntityType.java:17
public BlockEntityType(BlockEntityType.BlockEntitySupplier<? extends T> factory, Set<Block> validBlocks)
// BlockEntitySupplier: T create(BlockPos worldPosition, BlockState blockState)   (line 46)
// NeoForge adds (BlockEntitySupplier, Block...), (…, Set, boolean onlyOpCanSetNbt) and (…, boolean, Block...) overloads (neoforge/net/minecraft/world/level/block/entity/BlockEntityType.java:24-36). They are NOT visible to common.
TYPES.add("drone_workbench", key -> new BlockEntityType<>(DroneWorkbenchBlockEntity::new, Set.of(PropwashBlocks.DRONE_WORKBENCH.get())));
```
- The BlockEntity save API is `saveAdditional(ValueOutput)` / `loadAdditional(ValueInput)` (`vanilla/net/minecraft/world/level/block/entity/BlockEntity.java:95,107`). The block implements `EntityBlock#newBlockEntity(BlockPos, BlockState)` (`vanilla/net/minecraft/world/level/block/EntityBlock.java:14`).
- Fabric's `FabricBlockEntityTypeBuilder` class is **not** deprecated. Only its `build(Type)` overload is (`fabric-api/fabric-object-builder-api-v1/net/fabricmc/fabric/api/object/builder/v1/block/entity/FabricBlockEntityTypeBuilder.java:104`). Its only extra is `canPotentiallyExecuteCommands`. Use the public vanilla constructor in common.
- Entity type: `EntityType.Builder.of(EntityFactory<T>, MobCategory).sized(w,h).clientTrackingRange(n).updateInterval(n).build(ResourceKey<EntityType<?>>)` (`vanilla/net/minecraft/world/entity/EntityType.java:483-603`). The factory is `@Nullable T create(EntityType<T>, Level)` (lines 630-631). Vanilla calls it as `Registry.register(BuiltInRegistries.ENTITY_TYPE, id, builder.build(id))` (`vanilla/net/minecraft/world/entity/EntityTypes.java:1240-1241`). The key passed to `build` must match the registry key.
- Sounds: `record SoundEvent(Identifier location, Optional<Float> fixedRange)` with `SoundEvent.createVariableRangeEvent(Identifier)` (`vanilla/net/minecraft/sounds/SoundEvent.java:35`). When an API wants a `Holder<SoundEvent>` (for example `Equippable`), use `BuiltInRegistries.SOUND_EVENT.wrapAsHolder(PropwashSounds.X.get())`. That works after the flush: `MappedRegistry.wrapAsHolder` returns the registered `Holder.Reference`, or a `Holder.direct` if the value is not registered (`vanilla/net/minecraft/core/MappedRegistry.java:170-172`). Vanilla's own `Equippable.Builder` does the same for its shear sound (`Equippable.java:191`).
- `Item`, `Block`, `EntityType` and `BlockEntityType` create an **intrusive holder** in their constructor (for example `BlockEntityType.java:15`). Constructing one that you never register makes the freeze fail with `"Some intrusive holders were not registered"` (`MappedRegistry.java:295`). So only construct them inside the flush factories.

## 5. Data components

```java
// vanilla/net/minecraft/core/component/DataComponentType.java:26-80
static <T> DataComponentType.Builder<T> builder()
Builder<T> persistent(Codec<T> codec)                                    // saved to disk / commands / recipe JSON
Builder<T> networkSynchronized(StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec)
Builder<T> cacheEncoding();  Builder<T> ignoreSwapAnimation();  DataComponentType<T> build()
```
The vanilla call site is `DataComponents.java:475`: `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, builder.apply(DataComponentType.builder()).build())`, for example `DYED_COLOR` at line 239 with `DyedItemColor.CODEC` and `STREAM_CODEC`.

**The drone model already exists:** `common/src/main/java/de/lardtom/propwash/build/DroneBuild.java` is the Minecraft-free record `DroneBuild(FrameType frame, StackType stack, MotorType motor, PropType prop, VideoType video, BatteryType battery)`, with 4 identical motors and props (`MOTOR_COUNT = 4`). Every part enum implements `PartInfo` with a stable `key()`. Its javadoc puts the codecs in a separate `DroneBuildCodecs`, so no `PartType`/`Map` model is needed. The enums are immutable, which makes them valid component values as they are.
```java
// common: de/lardtom/propwash/registry/DroneBuildCodecs.java  (MC-dependent, keeps build/ MC-free)
public static <E extends Enum<E> & PartInfo> Codec<E> byKey(E[] values) {       // saved by stable key, never by ordinal
    Map<String, E> map = Arrays.stream(values).collect(Collectors.toUnmodifiableMap(PartInfo::key, e -> e));
    return Codec.stringResolver(PartInfo::key, map::get);                // DFU; vanilla use: StringRepresentable.java:98
}
public static <E extends Enum<E>> StreamCodec<ByteBuf, E> byOrdinal(E[] v) {  // network only: both sides run the same jar
    return ByteBufCodecs.idMapper(i -> v[i], Enum::ordinal);             // ByteBufCodecs.java:619
}
public static final Codec<MotorType> MOTOR = byKey(MotorType.values());  // …same for FRAME, STACK, PROP, VIDEO, BATTERY
public static final Codec<DroneBuild> CODEC = RecordCodecBuilder.create(i -> i.group(
        byKey(FrameType.values()).fieldOf("frame").forGetter(DroneBuild::frame),
        byKey(StackType.values()).fieldOf("stack").forGetter(DroneBuild::stack),
        MOTOR.fieldOf("motor").forGetter(DroneBuild::motor),
        byKey(PropType.values()).fieldOf("prop").forGetter(DroneBuild::prop),
        byKey(VideoType.values()).fieldOf("video").forGetter(DroneBuild::video),
        byKey(BatteryType.values()).fieldOf("battery").forGetter(DroneBuild::battery))
    .apply(i, DroneBuild::new));
public static final StreamCodec<ByteBuf, DroneBuild> STREAM_CODEC = StreamCodec.composite(  // 6-arity: StreamCodec.java:249
    byOrdinal(FrameType.values()), DroneBuild::frame, byOrdinal(StackType.values()), DroneBuild::stack,
    byOrdinal(MotorType.values()), DroneBuild::motor, byOrdinal(PropType.values()), DroneBuild::prop,
    byOrdinal(VideoType.values()), DroneBuild::video, byOrdinal(BatteryType.values()), DroneBuild::battery, DroneBuild::new);

// PropwashComponents
public static final RegList<DataComponentType<?>> COMPONENTS = new RegList<>(Registries.DATA_COMPONENT_TYPE);
public static final RegList.Ref<DataComponentType<DroneBuild>> DRONE_BUILD = COMPONENTS.add("drone_build",
    k -> DataComponentType.<DroneBuild>builder().persistent(DroneBuildCodecs.CODEC).networkSynchronized(DroneBuildCodecs.STREAM_CODEC).cacheEncoding().build());
public static final RegList.Ref<DataComponentType<MotorType>> MOTOR = COMPONENTS.add("motor",
    k -> DataComponentType.<MotorType>builder().persistent(DroneBuildCodecs.MOTOR).networkSynchronized(DroneBuildCodecs.byOrdinal(MotorType.values())).build());
```
`networkSynchronized` takes `StreamCodec<? super RegistryFriendlyByteBuf, T>`, so a `StreamCodec<ByteBuf, T>` fits. `RecordCodecBuilder`/`Codec.stringResolver` come from DFU (`com.mojang.serialization`), which is **not in .ref**. Their use is copied from vanilla call sites (`Equippable.java:47`, `StringRepresentable.java:98`).

If a component has to hold **real item stacks** (for example to keep the NBT/components of the inserted parts), use `ItemStackTemplate`, not `ItemStack`, which is mutable. The source is `record ItemStackTemplate(Holder<Item> item, int count, DataComponentPatch components) implements ItemInstance` (`vanilla/net/minecraft/world/item/ItemStackTemplate.java:19`) with `MAP_CODEC`/`CODEC`/`STREAM_CODEC` (lines 21-30), `fromNonEmptyStack(ItemStack)` (62), `fromStack` (70) and `create()` (86). Its constructor **throws for air or count 0** (line 52). `ItemContainerContents` uses it with `ByteBufCodecs.list(256)` (`vanilla/net/minecraft/world/item/component/ItemContainerContents.java:31-34`). The related helpers are `ByteBufCodecs.map(IntFunction, K, V)` (`ByteBufCodecs.java:527`) and `list()`/`list(int)` (483/487).

Reading and writing on an ItemStack. The methods are `ItemStack.java:763-785` plus `get`/`getOrDefault`/`has` from `vanilla/net/minecraft/core/component/DataComponentHolder.java:10,23,27`:
```java
DroneBuild b = stack.get(PropwashComponents.DRONE_BUILD.get());            // @Nullable
DroneBuild b2 = stack.getOrDefault(TYPE, DroneBuild.FREESTYLE);   // DroneBuild has no EMPTY; use a preset or @Nullable get
stack.set(TYPE, newBuild);   stack.remove(TYPE);
stack.update(TYPE, DroneBuild.FREESTYLE, old -> withVideo(old, v));   // update(type, default, UnaryOperator), ItemStack.java:779
boolean has = stack.has(TYPE);
```
Pitfalls:
- A component value must be **immutable** and implement `equals` and `hashCode`, because stacks merge by equality.
- Without `persistent`, the value is not saved. Without `networkSynchronized`, the client never sees it (tooltips, item model).
- The component is also accepted in recipe `result.components` and in `/give` as `propwash:drone_build`.

## 6. Creative tab

Vanilla has only `CreativeModeTab.builder(Row, int)` (`vanilla/net/minecraft/world/item/CreativeModeTab.java:49`). NeoForge adds a no-arg `builder()` and deprecates the row/column variant (`neoforge/net/minecraft/world/item/CreativeModeTab.java:69,78`). On Fabric the documented way is `FabricCreativeModeTab.builder()` (`fabric-api/fabric-creative-tab-api-v1/net/fabricmc/fabric/api/creativetab/v1/FabricCreativeModeTab.java`). Its impl only calls `super(null, -1)` and throws `"No display name set for CreativeModeTab"` when `title` is missing (`fabric-api/fabric-creative-tab-api-v1/net/fabricmc/fabric/impl/creativetab/FabricCreativeModeTabBuilderImpl.java:25-42`). **Pagination is not done by the builder.** `CreativeModeTabsMixin` puts every non-vanilla tab on a page and overwrites its row and column, whatever builder made it (`fabric-api/fabric-creative-tab-api-v1/net/fabricmc/fabric/mixin/creativetab/CreativeModeTabsMixin.java:87-104`). Common code *could* therefore use vanilla `builder(Row.TOP, 0)`, but it is deprecated on NeoForge. So add one method to the platform service:
```java
// IPlatformHelper
CreativeModeTab.Builder creativeTabBuilder();
// Fabric:   return FabricCreativeModeTab.builder();
// NeoForge: return CreativeModeTab.builder();
// common PropwashTabs
public static final RegList<CreativeModeTab> TABS = new RegList<>(Registries.CREATIVE_MODE_TAB);
public static final RegList.Ref<CreativeModeTab> MAIN = TABS.add("main", k -> Services.PLATFORM.creativeTabBuilder()
    .title(Component.translatable("itemGroup.propwash.main"))
    .icon(() -> new ItemStack(PropwashItems.FPV_GOGGLES.get()))           // lazy supplier: OK
    .displayItems((params, out) -> { out.accept(PropwashItems.DRONE_WORKBENCH.get()); out.accept(PropwashItems.FPV_GOGGLES.get()); })
    .build());
```
Pitfall: `CreativeModeTab.Output` is `protected` in vanilla (`CreativeModeTab.java:246`), so calling `out.accept` **does not compile in `common`** unless you add an AT line (section 12). At runtime it is already public: Fabric has `transitive-accessible class net/minecraft/world/item/CreativeModeTab$Output` (and `$TabVisibility`) in `fabric-api/fabric-creative-tab-api-v1/fabric-creative-tab-api-v1.classtweaker`, and NeoForge an AT (`neoforge-api/META-INF/accesstransformer.cfg:204`).

To add items to vanilla tabs:
- Fabric: `CreativeModeTabEvents.modifyOutputEvent(ResourceKey<CreativeModeTab>).register(output -> ...)` (`fabric-api/fabric-creative-tab-api-v1/net/fabricmc/fabric/api/creativetab/v1/CreativeModeTabEvents.java:53`). `output` is a `FabricCreativeModeTabOutput` (implements `CreativeModeTab.Output`, adds `prepend`/`insertAfter`/`insertBefore`), and the keys are in `CreativeModeTabs` (Fabric widens them).
- NeoForge: `BuildCreativeModeTabContentsEvent` on the mod bus, which implements `CreativeModeTab.Output` (`neoforge-api/net/neoforged/neoforge/event/BuildCreativeModeTabContentsEvent.java:25`). Check `event.getTabKey()` (line 47) and use `insertAfter`/`insertBefore` (lines 104/126).

## 7. Menus and screens

API facts:
- `MenuType(MenuSupplier<T>, FeatureFlagSet)` is **private** in vanilla (`vanilla/net/minecraft/world/inventory/MenuType.java:48`), and so is `MenuSupplier` (line 62). NeoForge makes both public (AT lines 202-203). Fabric makes them `transitive-accessible` (`fabric-api/fabric-transitive-access-wideners-v1/fabric-transitive-access-wideners-v1.classtweaker:7-8`). The NeoForge-patched source already shows the constructor as public (`neoforge/net/minecraft/world/inventory/MenuType.java:50`). For **common** you need the same two AT lines (section 12).
- `AbstractContainerMenu` (`vanilla/net/minecraft/world/inventory/AbstractContainerMenu.java`) provides:
  - `protected AbstractContainerMenu(@Nullable MenuType<?>, int containerId)`
  - `addSlot(Slot)` and `addStandardInventorySlots(Container, int left, int top)` (line 89: 3×9 at top, hotbar at top+58)
  - `abstract ItemStack quickMoveStack(Player, int)` (line 312) and `abstract boolean stillValid(Player)` (line 650)
  - `static stillValid(ContainerLevelAccess, Player, Block)` (line 96)
  - `moveItemStackTo(ItemStack, int start, int end, boolean backwards)` (line 652)
  - `removed(Player)` and `clearContainer(Player, Container)` (lines 601, 622)
  - `clickMenuButton(Player, int)` (line 304), called from the client with `minecraft.gameMode.handleInventoryButtonClick(containerId, id)` (`StonecutterScreen.java:149`)
  - `slotsChanged(Container)`, `addDataSlots(ContainerData)`
- `Slot(Container, int slot, int x, int y)` (`vanilla/net/minecraft/world/inventory/Slot.java:17`) can be overridden with `mayPlace(ItemStack)`, `getMaxStackSize()`, `onTake(Player, ItemStack)`, `mayPickup(Player)` and `getNoItemIcon()`.
- `ContainerLevelAccess.NULL` and `ContainerLevelAccess.create(Level, BlockPos)` provide `execute(BiConsumer)` and `evaluate(...)`. On the client the menu gets `NULL`, so `execute` blocks run server-only.

**A simple menu with no extra data, registered in common** (needs the AT):
```java
public static final RegList<MenuType<?>> MENUS = new RegList<>(Registries.MENU);
public static final RegList.Ref<MenuType<DroneWorkbenchMenu>> DRONE_WORKBENCH =
    MENUS.add("drone_workbench", k -> new MenuType<>(DroneWorkbenchMenu::new, FeatureFlags.VANILLA_SET)); // (int, Inventory) ctor
```
**A menu with extra open data** (for example the entity id of a drone for a tuning screen) goes through the platform service:
- Fabric provides `ExtendedMenuType<T,D>(ExtendedFactory<T,D>, StreamCodec<? super RegistryFriendlyByteBuf,D>)` (`fabric-api/fabric-menu-api-v1/net/fabricmc/fabric/api/menu/v1/ExtendedMenuType.java`) and `ExtendedMenuProvider<D> extends MenuProvider { D getScreenOpeningData(ServerPlayer) }` (`fabric-api/fabric-menu-api-v1/net/fabricmc/fabric/api/menu/v1/ExtendedMenuProvider.java`). `ServerPlayerMixin` (`fabric-api/fabric-menu-api-v1/net/fabricmc/fabric/mixin/menu/ServerPlayerMixin.java:68-92`) handles `player.openMenu(extendedProvider)`. It **throws** when an `ExtendedMenuType` is opened without an `ExtendedMenuProvider` (a `SimpleMenuProvider` wrapping one also counts), and it also throws the other way round. `ExtendedMenuType.create(int, Inventory)` always throws `UnsupportedOperationException`.
- NeoForge provides `IMenuTypeExtension.create(IContainerFactory<T>)` with `T create(int, Inventory, RegistryFriendlyByteBuf)` (`neoforge-api/net/neoforged/neoforge/common/extensions/IMenuTypeExtension.java`, `neoforge-api/net/neoforged/neoforge/network/IContainerFactory.java`), plus `ServerPlayer.openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)` (`neoforge/net/minecraft/server/level/ServerPlayer.java:1434`) and the shortcut `openMenu(MenuProvider, BlockPos)` (`neoforge-api/net/neoforged/neoforge/common/extensions/IPlayerExtension.java:58`). `IMenuTypeExtension.create` uses `FeatureFlags.DEFAULT_FLAGS`.
```java
// common
@FunctionalInterface public interface MenuDataFactory<T extends AbstractContainerMenu, D> { T create(int id, Inventory inv, D data); }
// IPlatformHelper
<T extends AbstractContainerMenu, D> MenuType<T> extendedMenuType(MenuDataFactory<T, D> f, StreamCodec<? super RegistryFriendlyByteBuf, D> codec);
<D> void openMenu(ServerPlayer p, MenuProvider provider, StreamCodec<? super RegistryFriendlyByteBuf, D> codec, D data);
// Fabric impl
return new ExtendedMenuType<>(f::create, codec);
p.openMenu(new ExtendedMenuProvider<D>() {
    public D getScreenOpeningData(ServerPlayer sp) { return data; }
    public Component getDisplayName() { return provider.getDisplayName(); }
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) { return provider.createMenu(id, inv, pl); } });
// NeoForge impl
return IMenuTypeExtension.create((id, inv, buf) -> f.create(id, inv, codec.decode(buf)));
p.openMenu(provider, buf -> codec.encode(buf, data));
```
Pitfall: on NeoForge, `IContainerFactory.create(int, Inventory)` passes a **null buffer** (`neoforge-api/net/neoforged/neoforge/network/IContainerFactory.java`). Never call `type.create(id, inv)` on the server; the server builds the menu through the `MenuProvider`.

**Screen registration is loader-only and client-only.**
- Fabric: `MenuScreens.register(MenuType, ScreenConstructor)` is private in vanilla (`vanilla/net/minecraft/client/gui/screens/MenuScreens.java:56`) but transitive-accessible, together with `MenuScreens$ScreenConstructor`, through `fabric-api/fabric-transitive-access-wideners-v1/fabric-transitive-access-wideners-v1.classtweaker:11-12`. Call it in a `ClientModInitializer` (fabric-loader API, not in .ref) and add `"client": ["de.lardtom.propwash.PropwashClient"]` to `fabric.mod.json`.
- NeoForge: use `RegisterMenuScreensEvent.register(MenuType, ScreenConstructor)` on the mod bus (`neoforge-api/net/neoforged/neoforge/client/event/RegisterMenuScreensEvent.java:27`; `MenuScreens$ScreenConstructor` is made public by the NeoForge AT, `neoforge-api/META-INF/accesstransformer.cfg:20`), from a `@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)` class. This is the same pattern as `neoforge-api/net/neoforged/neoforge/client/ClientNeoForgeMod.java:95`.
```java
// Fabric
@Override public void onInitializeClient() { MenuScreens.register(PropwashMenus.DRONE_WORKBENCH.get(), DroneWorkbenchScreen::new); }
// NeoForge
public PropwashClient(IEventBus modBus) {
    modBus.addListener(RegisterMenuScreensEvent.class, e -> e.register(PropwashMenus.DRONE_WORKBENCH.get(), DroneWorkbenchScreen::new)); }
// common/client screen: new GUI API (see StonecutterScreen.java / CraftingScreen.java:34)
public class DroneWorkbenchScreen extends AbstractContainerScreen<DroneWorkbenchMenu> {
    private static final Identifier BG = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/container/drone_workbench.png");
    public DroneWorkbenchScreen(DroneWorkbenchMenu m, Inventory inv, Component title) { super(m, inv, title, 176, 166); }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        g.blit(RenderPipelines.GUI_TEXTURED, BG, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256); } }
```

## 8. Drone workbench (a block that opens a GUI to assemble the drone)

The vanilla pattern, used by both `CraftingTableBlock` (`vanilla/net/minecraft/world/level/block/CraftingTableBlock.java`) and `StonecutterBlock` (`vanilla/net/minecraft/world/level/block/StonecutterBlock.java:41-55`), needs no block entity:
```java
@Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    if (!level.isClientSide()) { player.openMenu(state.getMenuProvider(level, pos)); }
    return InteractionResult.SUCCESS;
}
@Override protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
    return new SimpleMenuProvider((id, inv, p) -> new DroneWorkbenchMenu(id, inv, ContainerLevelAccess.create(level, pos)), TITLE);
}
```
`SimpleMenuProvider(MenuConstructor, Component)` is at `vanilla/net/minecraft/world/SimpleMenuProvider.java:13`, and `MenuConstructor.createMenu(int, Inventory, Player)` is in `vanilla/net/minecraft/world/inventory/MenuConstructor.java`. For a horizontal facing, copy `StonecutterBlock`: `FACING = HorizontalDirectionalBlock.FACING`, `getStateForPlacement`, `createBlockStateDefinition`, `rotate`, `mirror`.

**The menu template is `GrindstoneMenu`** (`vanilla/net/minecraft/world/inventory/GrindstoneMenu.java:31-110,201-209`). It combines input slots in a `SimpleContainer` (`vanilla/net/minecraft/world/SimpleContainer.java:18`) whose `setChanged()` calls `slotsChanged`, a `ResultContainer` output slot (`world/inventory/ResultContainer.java`) whose `onTake` consumes the inputs, `removed` → `clearContainer`, and `stillValid(access, player, block)`. The `quickMoveStack` shape comes from `CraftingMenu.java:110-152`. Recommended MVP (no block entity). The slot kinds follow the existing `DroneBuild`: one slot per category, and the motor/prop slots need **4 identical items** (`DroneBuild.MOTOR_COUNT`):
```java
public class DroneWorkbenchMenu extends AbstractContainerMenu {
    static final int FRAME = 0, STACK = 1, MOTOR = 2, PROP = 3, VIDEO = 4, BATTERY = 5, PARTS = 6;
    static final int RESULT = PARTS, INV_START = PARTS + 1, INV_END = INV_START + 36;
    static int need(int kind) { return kind == MOTOR || kind == PROP ? DroneBuild.MOTOR_COUNT : 1; }
    private final ContainerLevelAccess access;
    private final Container parts = new SimpleContainer(PARTS) { @Override public void setChanged() { super.setChanged(); slotsChanged(this); } };
    private final ResultContainer result = new ResultContainer();

    public DroneWorkbenchMenu(int id, Inventory inv) { this(id, inv, ContainerLevelAccess.NULL); }   // client (MenuSupplier)
    public DroneWorkbenchMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(PropwashMenus.DRONE_WORKBENCH.get(), id);
        this.access = access;
        for (int i = 0; i < PARTS; i++) addSlot(new PartSlot(parts, i, 8 + i * 18, 20));
        addSlot(new Slot(result, 0, 134, 50) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public void onTake(Player p, ItemStack s) { for (int i = 0; i < PARTS; i++) parts.removeItem(i, need(i)); super.onTake(p, s); } });
        addStandardInventorySlots(inv, 8, 84);
    }
    @Override public void slotsChanged(Container c) { super.slotsChanged(c); if (c == parts) result.setItem(0, DroneAssembly.tryBuild(parts)); }
    @Override public void removed(Player p) { super.removed(p); access.execute((lvl, pos) -> clearContainer(p, parts)); }
    @Override public boolean stillValid(Player p) { return stillValid(access, p, PropwashBlocks.DRONE_WORKBENCH.get()); }
    @Override public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index == RESULT) { if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY; slot.onQuickCraft(stack, copy); }
        else if (index >= INV_START) { if (!moveItemStackTo(stack, 0, PARTS, false)) return ItemStack.EMPTY; }  // honours PartSlot.mayPlace/max size
        else if (!moveItemStackTo(stack, INV_START, INV_END, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack); return copy;
    }
}
class PartSlot extends Slot {
    PartSlot(Container c, int kind, int x, int y) { super(c, kind, x, y); }
    @Override public boolean mayPlace(ItemStack s) { return s.has(PropwashComponents.forKind(getContainerSlot())); } // MOTOR/FRAME/… component
    @Override public int getMaxStackSize() { return DroneWorkbenchMenu.need(getContainerSlot()); }
}
```
- `DroneAssembly.tryBuild(parts)` reads the six part components, checks `count >= need(i)`, builds a `DroneBuild`, and returns a drone item with the `DRONE_BUILD` component. It returns `ItemStack.EMPTY` if something is missing, and it may reject builds whose `DroneBuild.problems()` is not empty. Disassembly works in reverse. Motor and prop items must stay stackable (≥ 4): `Slot.getMaxStackSize(ItemStack)` takes the minimum of the slot and item limits (`vanilla/net/minecraft/world/inventory/Slot.java:77`).
- Filtering by tag also works: `stack.is(TagKey<Item>)` comes from `TypedInstance.is(TagKey)` (`vanilla/net/minecraft/core/TypedInstance.java:14`, `ItemStack implements ItemInstance extends TypedInstance<Item>`), with `TagKey.create(Registries.ITEM, id)` and tag files at `data/propwash/tags/item/*.json`.
- Use a block entity (`EntityBlock` plus BE-backed `Container`) only if parts should **stay on the bench**, for example to show the drone on the table with a BlockEntityRenderer. In that case the client needs the BlockPos, so use the extended menu from section 7 with `BlockPos.STREAM_CODEC` (`vanilla/net/minecraft/core/BlockPos.java:37`).

## 9. Recipes (data/propwash/recipe/*.json)

These are verbatim vanilla formats from `vanilla/data/minecraft/recipe/`. An ingredient is an item id, a `#tag`, or a list of ids (`Ingredient.CODEC` = non-empty `HolderSet` codec, `Ingredient.java:34`). `category` for crafting is `building`, `redstone`, `equipment` or `misc`, with `misc` as the default (`vanilla/net/minecraft/world/item/crafting/CraftingBookCategory.java:12-15`, `CraftingRecipe.java:50`). `group` is optional and defaults to `""` (`Recipe.java:64`). `show_notification` is optional and defaults to true (`Recipe.java:83`). The fields are `key`/`pattern` (`ShapedRecipePattern.java:239-240`) and `ingredients` (1-9 entries, `ShapelessRecipe.java:23`). `result` is an `ItemStackTemplate` with `id`, an optional `count` (1-99, default 1) and optional `components` (`ItemStackTemplate.java:21-28`).
```json
{ "type": "minecraft:crafting_shaped", "category": "misc", "group": "propwash_parts",
  "key": { "#": "minecraft:iron_ingot", "R": "minecraft:redstone", "C": "#minecraft:planks" },
  "pattern": [ "#R#", "#C#" ],
  "result": { "id": "propwash:drone_workbench", "count": 1 } }
```
```json
{ "type": "minecraft:crafting_shapeless", "category": "equipment",
  "ingredients": [ "minecraft:iron_ingot", "minecraft:flint" ],
  "result": { "id": "propwash:motor_2207", "components": { "propwash:motor": "m2207_1750" } } }
```
These match `crafting_table.json`, `flint_and_steel.json`, and `suspicious_stew_from_blue_orchid.json` (components in the result).

Custom recipe types are optional; the workbench logic can live in code. If you do need one, build a `RecipeType` as an anonymous `new RecipeType<>(){}`, because `RecipeType.register` hard-codes the minecraft namespace (`vanilla/net/minecraft/world/item/crafting/RecipeType.java:17-18`). Then add `new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC)` and implement `Recipe<I>`: `matches`, `assemble`, `showNotification`, `group`, `getSerializer`, `getType`, `placementInfo`, `recipeBookCategory` (`vanilla/net/minecraft/world/item/crafting/Recipe.java:29-51`; note that `assemble(T input)` now takes only the input). `recipeBookCategory()` needs a registered `RecipeBookCategory`, which is a public empty class registered via `new RecipeBookCategory()` into `Registries.RECIPE_BOOK_CATEGORY` (`RecipeBookCategories.java:21-22`, `Registries.java:234`). That is two more `RegList`s.

## 10. Assets and data formats

- **Item model definition** (required for every item): `assets/propwash/items/<id>.json`, for example `vanilla/assets/minecraft/items/crafting_table.json`:
  `{ "model": { "type": "minecraft:model", "model": "propwash:item/fpv_goggles" } }`.
  - Types: `empty`, `model`, `range_dispatch`, `special`, `composite`, `select`, `condition`, `bundle/selected_item` (`vanilla/net/minecraft/client/renderer/item/ItemModels.java:16-23`).
  - Useful conditions are `has_component` and `component` (`ConditionalItemModelProperties.java:21,28`), for example a different drone model when `propwash:drone_build` is present. The select property `component` is at `SelectItemModelProperties.java:24`.
  - Extra top-level fields: `hand_animation_on_swap`, `oversized_in_gui`, `swap_animation_scale` (`vanilla/net/minecraft/client/renderer/item/ClientItem.java:30-32`).
- **Item model**: `assets/propwash/models/item/fpv_goggles.json` → `{ "parent": "minecraft:item/generated", "textures": { "layer0": "propwash:item/fpv_goggles" } }`. The texture goes in `textures/item/fpv_goggles.png`. A block item points its `items/` file at `propwash:block/drone_workbench`.
- **Blockstate**: `assets/propwash/blockstates/drone_workbench.json`. Copy `vanilla/assets/minecraft/blockstates/stonecutter.json` for a facing block (`"facing=east": {"model": "...", "y": 90}` and so on), or use `{"variants": {"": {"model": "propwash:block/drone_workbench"}}}`.
- **Block model**: `models/block/drone_workbench.json`. Use `"parent": "minecraft:block/cube"` with `down/up/north/south/east/west/particle` textures (`vanilla/assets/minecraft/models/block/crafting_table.json`).
- **Loot table**: `data/propwash/loot_table/blocks/drone_workbench.json`. Note the 26.3 format uses `"condition"` (singular) and `"random_sequence"`:
  `{"type":"minecraft:block","pools":[{"condition":{"type":"minecraft:survives_explosion"},"entries":[{"type":"minecraft:item","name":"propwash:drone_workbench"}],"rolls":1}],"random_sequence":"propwash:blocks/drone_workbench"}` (`vanilla/data/minecraft/loot_table/blocks/crafting_table.json`).
- **Lang**: `assets/propwash/lang/en_us.json` (and `de_de.json`). Keys:
  - `item.propwash.<id>`
  - `block.propwash.<id>`, also used for BlockItems with `useBlockDescriptionPrefix`
  - `itemGroup.propwash.main`
  - `container.propwash.drone_workbench`
  - `subtitles.propwash.<sound>`
- **sounds.json**: `assets/propwash/sounds.json`. Fields: `replace`, `subtitle`, `sounds[]`, where each entry is a string or an object with `name`, `volume`, `pitch`, `weight`, `stream`, `preload`, `attenuation_distance` and `type` (`vanilla/net/minecraft/client/resources/sounds/SoundEventRegistrationSerializer.java:27-70`). Use `"stream": true` for long motor loops. The `.ogg` files go in `assets/propwash/sounds/…`.
- **pack.mcmeta**: `common/src/main/resources/pack.mcmeta` currently says `"pack_format": 8`, which is outdated. The 26.x codec uses `min_format`/`max_format`, and if either is present both are required (`vanilla/net/minecraft/server/packs/metadata/pack/PackFormat.java:103-146`). A `min_format` at or below the last pre-minor version (resources 64, data 81, `PackFormat.java:63-67`) would also require `supported_formats`, so use 97 or higher. `[major, minor]` or a bare int are both accepted, and a bare `max_format` int means `major.*` (`fullCodec`, lines 24-29). One file serves both assets (97.1) and data (121.0):
  `{ "pack": { "description": "${mod_name}", "min_format": 97, "max_format": [121, 0] } }` (NeoForge's own uses `min_format: 121, max_format: [121,0]`, `neoforge-api/pack.mcmeta`). Deleting the file is also an option. Fabric synthesises a `pack.mcmeta` for mod packs that ship none (`fabric-api/fabric-resource-loader-v1/net/fabricmc/fabric/impl/resource/pack/ModPackResourcesUtil.java:170-190`), and NeoForge reads mod packs through `readWithOptionalMeta` (`neoforge-api/net/neoforged/neoforge/resource/ResourcePackLoader.java:133`). UNVERIFIED: whether either loader rejects a mod jar whose stale `pack_format: 8` is still present.

## 11. FPV goggles (head slot)

- `Equippable` record (`vanilla/net/minecraft/world/item/equipment/Equippable.java:34`) has the fields `slot`, `equipSound` (Holder), `assetId` (Optional `ResourceKey<EquipmentAsset>`), `cameraOverlay` (Optional Identifier), `allowedEntities`, `dispensable`, `swappable`, `damageOnHurt`, `equipOnInteract`, `canBeSheared` and `shearingSound`.
- `Equippable.builder(EquipmentSlot)` (line 125) provides `setEquipSound`, `setAsset`, `setCameraOverlay`, `setSwappable`, `setDamageOnHurt`, …
- The builder defaults are `ARMOR_EQUIP_GENERIC`, `dispensable`, `swappable`, `damageOnHurt=true` (lines 180-193).

```java
public static final ResourceKey<EquipmentAsset> GOGGLES_ASSET =
    ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fpv_goggles")); // EquipmentAssets.java:11
public static final RegList.Ref<FpvGogglesItem> FPV_GOGGLES = item("fpv_goggles", FpvGogglesItem::new, p -> p.stacksTo(1)
    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
        .setAsset(GOGGLES_ASSET).setDamageOnHurt(false)
        // .setCameraOverlay(Identifier.fromNamespaceAndPath("propwash", "misc/goggles_overlay"))  -> textures/misc/goggles_overlay.png
        .build()));
```
- The vanilla reference is `Item.Properties.humanoidArmor`, which does `Equippable.builder(type.getSlot()).setEquipSound(...).setAsset(material.assetId())` (`Item.java:566-574`). The carved pumpkin uses the camera overlay: `setCameraOverlay(Identifier.withDefaultNamespace("misc/pumpkinblur"))` (`Items.java:852`), and `Hud.java:290-291` resolves it to `textures/<path>.png`. **Don't use the overlay for the FPV view itself**; that is a separate render hook. It is only good for a "goggles on, not flying" vignette.
- **Equipment asset**: `assets/propwash/equipment/fpv_goggles.json`:
  `{ "layers": { "humanoid": [ { "texture": "propwash:fpv_goggles" } ] } }` (`vanilla/assets/minecraft/equipment/turtle_scute.json`). The texture resolves to `assets/propwash/textures/entity/equipment/humanoid/fpv_goggles.png` (`vanilla/net/minecraft/client/resources/model/EquipmentClientInfo.java:122-123`). It is a 64×32 armor-layer texture.
- **Alternative 3D look**: if `EQUIPPABLE` has **no** `assetId`, `HumanoidArmorLayer.shouldRender` is false (`vanilla/net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer.java:37-44`). The head item is then drawn as its **item model** on the head by `CustomHeadLayer` (`vanilla/net/minecraft/client/renderer/entity/LivingEntityRenderer.java:283-294` fills `state.headItem`; `client/renderer/entity/layers/CustomHeadLayer.java:45-62` submits it), the same way as the carved pumpkin. That lets the goggles be a Blockbench item model with a `head` display transform instead of a flat armor texture.
  **Propwash uses this** (playtest feedback: the flat armor layer was barely visible): the goggles have no `assetId`, and the item definition selects `propwash:item/goggles_<kind>_worn` for the `head` display context (`minecraft:select` on `minecraft:display_context`, like `vanilla/assets/minecraft/items/spyglass.json`). Scale: `CustomHeadLayer.translateToHead` translates to the head centre, rotates 180° about Y and scales by 0.625, so 16 model units = 10 skin pixels, model −Z = face, model +X = the wearer's right (`docs/ASSETS.md`, "Brillen-Modell").

## 12. Access transformer for `common`

Create `common/src/main/resources/META-INF/accesstransformer.cfg`. `common/build.gradle` and `neoforge/build.gradle` pick it up automatically if the file exists. Every entry already has a runtime equivalent: the NeoForge AT (`neoforge-api/META-INF/accesstransformer.cfg:202-205`) and the Fabric transitive AW/classtweakers. So Fabric needs **no** `propwash.accesswidener` for these, and runtime behaviour does not change. `fabric/build.gradle` would pick up `common/src/main/resources/propwash.accesswidener` if it existed. If you later add an AT entry that NeoForge does **not** already ship, the runtime needs it declared in `neoforge/src/main/resources/META-INF/neoforge.mods.toml` as `[[accessTransformers]] file="META-INF/accesstransformer.cfg"`. That is how NeoForge declares its own (`neoforge-api/META-INF/neoforge.mods.toml:21-22`). Our toml has no such block yet. UNVERIFIED: whether FML falls back to that path by default (FML is not in .ref).
```
public net.minecraft.world.inventory.MenuType <init>(Lnet/minecraft/world/inventory/MenuType$MenuSupplier;Lnet/minecraft/world/flag/FeatureFlagSet;)V
public net.minecraft.world.inventory.MenuType$MenuSupplier
public net.minecraft.world.item.CreativeModeTab$Output
public net.minecraft.world.item.CreativeModeTab$TabVisibility
```
(`MenuScreens.register` is not needed in common, because screen registration is loader code.)

## 13. Pitfalls checklist

1. Calling `Registry.register` in a static initializer or the NeoForge constructor fails, because the registry is frozen. Always go through `RegList.flush` inside `RegisterEvent` or Fabric `onInitialize`.
2. A `RegList.Ref.get()` before the flush throws. Read refs only inside factories that run later, or at runtime.
3. A missing `setId(key)` causes an NPE in the `Item`/`Block` constructor. The key passed to `setId` must equal the registered key.
4. `new ItemStack(...)` or `item.components()` before the first datapack load throws `"Components not bound yet"`. Creative tab `icon`/`displayItems` are lazy and fine; `static final ItemStack` fields are not.
5. Flush data components (and sounds) before items on Fabric, because item factories call `PropwashComponents.X.get()`, which throws before the flush. NeoForge already fires `DATA_COMPONENT_TYPE` first (`GameData.java:128`).
6. `CreativeModeTab.Output` and the `MenuType` constructor don't compile in common without the AT from section 12.
7. On Fabric, pagination applies to every modded tab, whatever builder made it (`CreativeModeTabsMixin.java:87-104`). `FabricCreativeModeTab.builder()` only enforces `title`. Still use the platform `creativeTabBuilder()`, because `builder(Row,int)` is deprecated on NeoForge.
8. Extended menus: never call `MenuType.create(id, inv)`. NeoForge passes a null buffer and Fabric's `ExtendedMenuType` throws. On Fabric, opening an `ExtendedMenuType` needs an `ExtendedMenuProvider`, or `ServerPlayerMixin` throws.
9. `useBlockDescriptionPrefix()` on BlockItems. Ship the loot table, the `items/<id>.json` model definition (without it the item renders as a missing model) and lang keys.
10. Component values must be immutable and have value equality: records or enums, such as the existing `build/*` enums and `DroneBuild`. Use `ItemStackTemplate`, not `ItemStack`, inside components. Add both `persistent` and `networkSynchronized`.
11. Never construct an `Item`/`Block`/`EntityType`/`BlockEntityType` that you don't register (intrusive holder, `MappedRegistry.java:295`).

## 14. Chest loot injection (verified for the survival work, 26.3)

- **26.3 loot JSON:** pools and entries use a single `"condition"` and a single `"modifier"` (an object, or a list that is
  read as a sequence, `LootItemFunctions.DIRECT_CODEC` = `either(TYPED_CODEC, SequenceFunction.INLINE_CODEC)`), `"rolls"` is a
  `ContextIntProvider` (`LootPool.java` codec). Condition and function objects use `"type"`. Nested tables are
  `{"type": "minecraft:loot_table", "value": "<id>"}` (`chests/trial_chambers/reward.json`), empty entries
  `{"type": "minecraft:empty", "weight": n}`. Custom components go through `minecraft:set_components`
  (`equipment/trial_chamber.json`), e.g. `{"propwash:battery_charge": 0.4, "minecraft:item_name": {"translate": "…"}}`.
- **Fatal validation:** `ReloadableServerRegistries#validateLootRegistries` throws on fatal loot problems, so a broken loot
  table stops the world from loading (not just a log warning).
- **Fabric:** `net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY` → `(ResourceKey<LootTable> key, LootTable.Builder builder,
  LootTableSource source, HolderLookup.Provider registries)`. Add a pool with
  `builder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(registries.getOrThrow(tableKey))))`.
  `lootTableReference` takes a `Holder<LootTable>` in 26.3; during loading `registries.getOrThrow` hands out a reference that
  is bound later (`LootTableHolderProvider` → `MappedRegistry#createRegistrationLookup`), so the target table may load before
  the referenced one.
- **NeoForge:** `LootModifierManager` (`neoforge-api/…/common/loot/LootModifierManager.java`) is a
  `SimpleJsonResourceReloadListener` over `data/<ns>/loot_modifiers/**.json` — every file there is one modifier, there is no
  `global_loot_modifiers.json` list any more. Built-in `neoforge:add_table` (`AddTableLootModifier`, fields `condition`,
  `priority`, `table`) plus condition `neoforge:loot_table_id` (`LootTableIdCondition`, field `loot_table_id`):
  `{"type": "neoforge:add_table", "condition": {"type": "neoforge:loot_table_id", "loot_table_id": "minecraft:chests/simple_dungeon"}, "table": "propwash:inject/simple_dungeon"}`.
  GLMs run in the patched `LootTable#getRandomItems(LootContext)` (also for `fill` and vault ejection), not in
  `getRandomItemsRaw`, so nested tables are not modified twice. Alternative: `LootTableLoadEvent` + `LootTable#addPool`
  (NeoForge-patched, table not frozen yet at that point).
- **Rolling a table in tests:** `server.reloadableRegistries().getLootTable(key)` (returns `LootTable.EMPTY` if missing),
  params `new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, pos).create(LootContextParamSets.CHEST)`,
  `table.getRandomItems(params, seed)` with a seed ≠ 0 is deterministic.

## Verification

Checked on 2026-09-23 against `.ref/` (vanilla, neoforge, neoforge-api, fabric-api) and the project tree. Every class name, signature and line number was opened with grep/sed. About 150 claims were checked.

**Confirmed as written (selection):** `Identifier` factories; `Registry.register`/`registerForHolder`/`wrapAsHolder`; `ResourceKey.create`; every `Registries.*` key used; `Items.registerItem`/`Blocks.register`; `setId` NPE messages; the lazy `DataComponentInitializers` flow; `RecipeSerializer` as a record; `RegisterEvent.register` + `RegisterHelper`; `CommonModLoader` freeze window; `DeferredRegister.Items.registerItem`; all `Item.Properties` methods listed; the `BlockItem` prefix; public `BlockEntityType` ctor; `EntityType.Builder`; `SoundEvent` record; `DataComponentType.Builder`; `ItemStackTemplate`; `ByteBufCodecs.map/idMapper/list`; ItemStack component accessors; `CreativeModeTab.Output`/`TabVisibility` protected + NeoForge AT 202-205 + Fabric classtweakers; NeoForge `builder()`; `BuildCreativeModeTabContentsEvent`; private `MenuType` ctor/`MenuSupplier`; all `AbstractContainerMenu`/`Slot`/`ContainerLevelAccess` members; `ExtendedMenuType`/`ExtendedMenuProvider`; `IMenuTypeExtension`/`IContainerFactory`; `ServerPlayer.openMenu(…, Consumer)`; `RegisterMenuScreensEvent`; `StonecutterBlock` pattern; `GrindstoneMenu` structure; recipe/loot/blockstate/model/equipment/item-definition JSON (compared with vanilla files); `ItemModels` types; `ClientItem` properties; sounds.json keys; pack versions 97.1/121.0; `Equippable` record/builder/defaults; camera overlay in `Hud`; equipment texture path and 64×32 size; `CustomHeadLayer` fallback; the build scripts' auto-pickup of the AT/AW.

**Corrected:**
- Line numbers: `Holder` 269, `GameData` 125-131, `Item` 394/723-727, `BlockBehaviour` 989, `BlockEntitySupplier` 46, `CraftingMenu` 110-152, `PackFormat` 103-146, `StonecutterBlock` 41-55, `LivingEntityRenderer` 283-294, `Block` 233-234.
- The "moved constants" list: only the id classes are in `references/`.
- `Gui` still exists and is not the HUD. `extractBackground` is inherited from `Screen`; `AbstractContainerScreen` has no `renderBg`.
- `FabricBlockEntityTypeBuilder` is not deprecated; only `build(Type)` is.
- Fabric creative-tab pagination comes from `CreativeModeTabsMixin` for all modded tabs, not from `FabricCreativeModeTab.builder()` (section 6, pitfall 7).
- `EntityFactory.create` is `@Nullable`.
- The component/menu sketches invented a `PartType`/`PartSpec`/`Map<PartType, ItemStackTemplate>` model that conflicts with the existing `common/.../build/DroneBuild.java`. That map could not even hold 4 motors, because the keys collide. Sections 3, 5, 8 and 9 are rewritten around the existing enums, with a `DroneBuildCodecs` sketch and per-part enum components. `DroneBuild.EMPTY`/`.with` do not exist.
- The workbench result slot now consumes 4 motors/props and calls `super.onTake`.
- The elided `…/` paths in loader-specific statements are expanded to full paths.

**Added:** Fabric's delayed freeze (`BootstrapMixin`); the intrusive-holder freeze error; `wrapAsHolder` semantics; client-side component binding + `DefaultDataComponentsBoundEvent`; `delayedHolderComponent`; Fabric `ServerPlayerMixin` throw rules and `ExtendedMenuType.create` throwing; NeoForge `openMenu(MenuProvider, BlockPos)`; the `MenuScreens` classtweaker lines / NeoForge AT line 20; recipe field defaults and the `ItemStackTemplate` result codec; `RecipeBookCategory` registration for custom recipes; pack-format pre-minor rule; the NeoForge `[[accessTransformers]]` toml declaration.

**Open questions / UNVERIFIED:**
1. DFU (`Codec.stringResolver`, `RecordCodecBuilder`, `Codec.unboundedMap`), `IEventBus`, FML and fabric-loader (`ClientModInitializer`, `"client"` entrypoint) are not in `.ref`. Their signatures are inferred from vanilla/NeoForge call sites only.
2. Does FML apply `META-INF/accesstransformer.cfg` without a `[[accessTransformers]]` entry in `neoforge.mods.toml`? This only matters once we add entries that NeoForge does not already ship. Where does Fabric re-freeze `BuiltInRegistries` after `onInitialize`? That lives in fabric-loader/another module, not found in `.ref`.
3. How do the loaders treat the stale `pack_format: 8` in `common/src/main/resources/pack.mcmeta`: ignored, warned or rejected? Also, both loader entrypoints call `CommonClass.init()`, but `common/src/main/java/de/lardtom/propwash/CommonClass.java` does not exist right now, so the template will not compile until it is re-added.

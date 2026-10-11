# Propwash: misc APIs (MC 26.3, Fabric + NeoForge)

> **Projekt-Layout veraltet:** Die eine Mixin-Config `propwash.mixins.json` (stattdessen je Modul `propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`), Registrierungs-Klassen (`PropwashItems`, `PropwashEntities`, `PropwashComponents`, `RegList.Ref`, …), `CommonClass`, das Paket `net` (heute `network`) und Loader-Einstiege in dieser Notiz stammen aus der Zeit vor der Foundation. Kommentare in den Code-Beispielen sind nur Erklärung: im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`). Maßgeblich sind `docs/CONVENTIONS.md` und `docs/FOUNDATION.md` (Kasten „Abweichungen von docs/api“). Die Minecraft-/Loader-APIs hier stimmen weiterhin.

Paths in parentheses are relative to `.ref/` unless marked `project:` (repo root). Everything below was checked against those sources. Where something was read from a Gradle-cached jar instead of source, it says so.

## Overview: key findings
- **Game is unobfuscated.** Mixin configs need no refmap. Class tweaker and AT files use the `official` namespace, which now means Mojang names.
- **Permissions were rewritten.** `Player#hasPermissions(int)` and `CommandSourceStack#hasPermission(int)` are **gone**. Use `permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)` and `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` instead.
- **Game rules are a registry now** (`Registries.GAME_RULE`). Values are read with `getGameRules().get(rule)`.
- `LevelReader#hasChunkAt` still exists but is `@Deprecated`. `Level#getBlockState` **loads chunks synchronously**, so guard long raycasts.
- **Test support:**
  - Fabric has server gametests and **client gametests** (`fabric-client-gametest-api-v1`, experimental). Loom 1.18.1 wires both through `fabricApi.configureTests`.
  - NeoForge has server gametests through `RegisterGameTestsEvent` and a `gameTestServer` run type. It has **no client test API**, only a boot smoke test (`NEOFORGE_CLIENT_SELFTEST`).
- **Unused template setting.** `neoforge.enabledGameTestNamespaces`, set in `project:neoforge/build.gradle`, is read nowhere in NeoForge 26.3.
- **Workbench (user request).** `MenuType`'s constructor and `MenuScreens.register` are private in vanilla.
  - NeoForge's AT makes the `MenuType` constructor, `MenuSupplier` and `ScreenConstructor` public, but not `MenuScreens.register`. For screens it has `RegisterMenuScreensEvent` instead.
  - Fabric API's `fabric-transitive-access-wideners-v1` makes all four **transitive-accessible**, so our Fabric module can use them directly.
  - Common can therefore build the `MenuType` itself, given 2 lines in our own AT. Only screen registration needs per-loader glue: see §8.

---

## 1. Mixins in `common`

**Setup (project).**
- `project:common/src/main/resources/propwash.mixins.json`: package `de.lardtom.propwash.mixin`, `"client": ["MixinMinecraft"]`, `defaultRequire: 1`, `JAVA_25`.
- Loader configs: `propwash.fabric.mixins.json` and `propwash.neoforge.mixins.json`.
- Fabric loads both configs through `fabric.mod.json` `"mixins"`. NeoForge loads both through `[[mixins]] config=` in `neoforge.mods.toml`.
- Common compiles against `compileOnly net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7` and `compileOnly(annotationProcessor(io.github.llamalad7:mixinextras-common:0.5.3))` (`project:common/build.gradle`).
- Both loaders bundle MixinExtras at runtime, so all 0.5.x features are usable: `@WrapOperation`, `@ModifyExpressionValue`, `@Local`, `@Share`.
  - Fabric Loader 0.19.5 jar-in-jars `META-INF/jars/mixinextras-fabric-0.5.5.jar` (its `fabric.mod.json`).
  - The NeoForge 26.3.0.10-beta POM depends on `mixinextras-neoforge 0.5.4` and `sponge-mixin 0.17.3+mixin.0.8.7`.
  - Common compiles against 0.5.3, which is older than both, so this is safe.
- The loader modules compile common's *sources* (`commonJava` in `project:build-logic/src/main/groovy/multiloader-loader.gradle`). So each common mixin is compiled once per loader.

**Real examples.**
- `@WrapOperation` + `Operation<Void>` + `@Local(argsOnly = true)`: `fabric-api/fabric-item-api-v1/net/fabricmc/fabric/mixin/item/ItemStackMixin.java:66`.
- `@Accessor` + `@Mutable` setter on an interface mixin: `neoforge-api/net/neoforged/neoforge/mixins/BlockEntityTypeAccessor.java`.
- Static `@Invoker`: `fabric-api/fabric-renderer-indigo/net/fabricmc/fabric/mixin/client/indigo/renderer/ItemFeatureRendererAccessor.java`.

**Pattern: accessors instead of AW/AT.** Example target: `Camera#setRotation(float,float)` and `Camera#setPosition(Vec3)` are `protected` (`vanilla/net/minecraft/client/Camera.java:343,357`).
```java
// common/src/main/java/de/lardtom/propwash/mixin/client/CameraAccessor.java  -> "client" array
@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setRotation") void propwash$setRotation(float yRot, float xRot);
    @Invoker("setPosition") void propwash$setPosition(Vec3 pos);   // overloaded (also (double,double,double) :353); the invoker's descriptor picks (Vec3)
    // NO accessor for xRot: `private float xRot` (Camera.java:58) already has a public getter `float xRot()` (:371), and `yRot()` (:375)
}
// use: ((CameraAccessor) camera).propwash$setRotation(yaw, pitch);
```
- **Camera differs between loaders.** NeoForge patches `Camera`: it adds `private float roll` and `protected void setRotation(float yRot, float xRot, float roll)`, and makes the 2-arg `setRotation` call the 3-arg one. It also adds `public float getRoll()` (`neoforge/net/minecraft/client/Camera.java:60,353,357,523`). Vanilla has **no roll**.
  - An FPV camera roll therefore needs its own mixin on the `rotation` quaternion in common.
  - On NeoForge only, it can use the 3-arg invoker or `ViewportEvent.ComputeCameraAngles` instead (see camera-gui.md).
```java
// MixinExtras: wrap one call inside a vanilla method
@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/...;foo(I)V"))
private void propwash$wrapFoo(Target instance, int arg, Operation<Void> original) {
    if (!DroneControl.isFlying()) original.call(instance, arg);
}
```

**Pitfalls.**
- **Check both sources before targeting.** NeoForge patches vanilla method bodies; for example, the `Commands` constructor gets an event hook at `neoforge/net/minecraft/commands/Commands.java:306`. Before writing a common `@At("INVOKE")` or ordinal-based target, compare `.ref/vanilla/...` with `.ref/neoforge/...`. With `defaultRequire: 1`, a failed injection crashes at startup, which is good because it fails loudly.
- **Accessors cannot widen *types*.** A private nested type in a signature, like `MenuType.MenuSupplier` or `MenuScreens.ScreenConstructor`, cannot be named, so no `@Invoker` can expose it. That needs an AW + AT pair. The template supports both, but only if the file exists; **neither file exists yet** in the project:
  - **AW.** Loom picks up `project:common/src/main/resources/propwash.accesswidener` via `accessWidenerPath` (`project:fabric/build.gradle`, if `aw.exists()`).
    - The template's `fabric.mod.json` has **no `"accessWidener"` key** yet; add `"accessWidener": "propwash.accesswidener"`. Fabric API uses the same key for `.classtweaker` files (`fabric-api/fabric-menu-api-v1/fabric.mod.json:33`).
    - File header: `accessWidener v2 official` (`fabric-api/fabric-transitive-access-wideners-v1/*.classtweaker:1`) or `classTweaker v1 official` (`fabric-api/fabric-command-api-v2/fabric-command-api-v2.classtweaker:1`).
  - **AT.** `project:common/src/main/resources/META-INF/accesstransformer.cfg` is used by **both** common (NeoForm) and neoforge (`project:common/build.gradle`, `project:neoforge/build.gradle`, if it exists).
    - At runtime, FML 12.0.0 (the NeoForge 26.3 dependency) falls back to `META-INF/accesstransformer.cfg` when the jar contains it. See `ModFile.lambda$new$3` in the `loader-12.0.0.jar` bytecode.
    - NeoForge itself declares ATs explicitly with `[[accessTransformers]] file="META-INF/accesstransformer.cfg"` (`neoforge-api/META-INF/neoforge.mods.toml:22-23`). Adding the same block to our `neoforge.mods.toml` is the safer choice.
  - **Rule.** Anything common code uses must be accessible on **both** loaders. Put it in our AW and our AT, unless Fabric API's transitive AW or NeoForge's own AT already covers it (see §8).
- **Side placement.** Client-target mixins go in the `"client"` array. Otherwise the dedicated server tries to load `net.minecraft.client.*` and crashes.
- **Naming.** Prefix handler and accessor names with `propwash$` to avoid clashes.

---

## 2. Platform services (template pattern)

**What exists.**
- `project:common/.../platform/Services.java`: `Services.load(Class<T>)` via `ServiceLoader.load(clazz, Services.class.getClassLoader()).findFirst()`.
- `IPlatformHelper` has `String getPlatformName()`, `boolean isModLoaded(String)`, `boolean isDevelopmentEnvironment()`, and the default `String getEnvironmentName()`.
  - NeoForge implements these with `ModList.get().isLoaded` and `!FMLLoader.getCurrent().isProduction()` (`project:neoforge/.../platform/NeoForgePlatformHelper.java`).
- Implementations are registered in `META-INF/services/de.lardtom.propwash.platform.IPlatformHelper` per loader (`FabricPlatformHelper`, `NeoForgePlatformHelper`).

**Extend it this way.**
1. Use **one interface per concern**: `IPlatformHelper` (env, config dir), `INetworkHelper` (server→client), `IClientNetworkHelper` (client→server, **client only**), `IMenuHelper` (see §8). Add one services file per interface in each loader.
2. **Events flow loader → common.** Common exposes plain static hooks. Each loader entrypoint subscribes to its own event and calls the hook. Common never touches loader event types.
3. **Registries.** Common keeps its own list of `(ResourceKey<Registry<T>>, Identifier, Supplier<T>)` entries, and each loader drains that list:
   - Fabric: `Registry.register(...)` in `onInitialize`.
   - NeoForge: `RegisterEvent#register(ResourceKey<? extends Registry<T>>, Consumer<RegisterHelper<T>>)` (`neoforge-api/net/neoforged/neoforge/registries/RegisterEvent.java:60`) or `DeferredRegister.create(ResourceKey, String)` (`.../DeferredRegister.java:116`).
```java
// common
public interface IPlatformHelper { /* existing */ Path getConfigDir(); boolean isClientDist(); }
public interface INetworkHelper { void sendToPlayer(ServerPlayer p, CustomPacketPayload payload); }
public interface IClientNetworkHelper { void sendToServer(CustomPacketPayload payload); }   // client only
public final class ClientServices {   // ONLY referenced from client code
    public static final IClientNetworkHelper NET = Services.load(IClientNetworkHelper.class);
}
public final class PropwashHooks {    // called by loaders
    public static void registerCommands(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx, Commands.CommandSelection sel) { PropwashCommands.register(d, ctx); }
    public static void onServerStarting(MinecraftServer server) { PropwashServerConfig.load(server); }
}
```
```java
// fabric  (FabricLoader: getConfigDir() used at fabric-api/fabric-renderer-indigo/.../Indigo.java:88;
//          getEnvironmentType()==EnvType.SERVER at fabric-api/fabric-registry-sync-v0/.../MainMixin.java:43)
public Path getConfigDir() { return FabricLoader.getInstance().getConfigDir(); }
public boolean isClientDist() { return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT; }
public void sendToPlayer(ServerPlayer p, CustomPacketPayload m) { ServerPlayNetworking.send(p, m); }  // fabric-networking-api-v1/.../ServerPlayNetworking.java:287
public void sendToServer(CustomPacketPayload m) { ClientPlayNetworking.send(m); }                   // .../client/networking/v1/ClientPlayNetworking.java:240
```
```java
// neoforge  (FMLPaths.CONFIGDIR.get(): neoforge-api/net/neoforged/neoforge/internal/CommonModLoader.java:62, import net.neoforged.fml.loading.FMLPaths;
//            FMLEnvironment.getDist(): same file :61)
public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
public boolean isClientDist() { return FMLEnvironment.getDist().isClient(); }
public void sendToPlayer(ServerPlayer p, CustomPacketPayload m) { PacketDistributor.sendToPlayer(p, m); }   // neoforge-api/.../network/PacketDistributor.java:36
public void sendToServer(CustomPacketPayload m) { ClientPacketDistributor.sendToServer(m); }                 // neoforge-api/.../client/network/ClientPacketDistributor.java:22
```
**Pitfalls.**
- The services filename must be the interface FQN. `Services.load` uses `findFirst()`, so put exactly one implementation per file; any extras are silently ignored.
- A missing file throws a `NullPointerException("Failed to load service for <FQN>")` from `Services.load`, which becomes an `ExceptionInInitializerError` when it happens in a static field.
- Keep client-only services in their own holder (`ClientServices`) so the dedicated server never initializes them.

---

## 3. Level queries

### 3.1 Heightmaps (`vanilla/net/minecraft/world/level/levelgen/Heightmap.java`, enum `Heightmap.Types`)

| Type | Usage | On client? | Counts as "top" (Heightmap.java:28-29,146-158) |
|---|---|---|---|
| `WORLD_SURFACE_WG` / `OCEAN_FLOOR_WG` | WORLDGEN | no (`keepAfterWorldgen()` false) | `NOT_AIR` / tag `BLOCKS_MOTION_IN_HEIGHTMAP` |
| `WORLD_SURFACE` | CLIENT | yes (`sendToClient()`) | any non-air |
| `OCEAN_FLOOR` | LIVE_WORLD | **server only** | tag `BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP` (fluids ignored) |
| `MOTION_BLOCKING` | CLIENT | yes | tag `BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP` or non-empty fluid |
| `MOTION_BLOCKING_NO_LEAVES` | CLIENT | yes | tag `BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES` or non-empty fluid |

- `int LevelReader#getHeight(Heightmap.Types type, int x, int z)` / `getHeight(Types, BlockPos)` (`vanilla/net/minecraft/world/level/LevelReader.java:36-40`).
- `BlockPos getHeightmapPos(Types, BlockPos)` (`:84`).
- `Level#getHeight` (`Level.java:323`) returns **top block Y + 1** (the first free Y). It returns `getMinY()` if the chunk isn't loaded, and `getSeaLevel()+1` outside ±30M.
- Vanilla call site: `ServerLevel.java:588` `getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos)`.
- Use for AGL altitude and spawn height: `level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)` works on both sides.
- For height above ground under overhangs or gates, **raycast down** instead (heightmap = column top).

### 3.2 Loaded checks
- `@Deprecated boolean hasChunkAt(int blockX, int blockZ)` / `hasChunkAt(BlockPos)` / `hasChunksAt(...)` (`LevelReader.java:186-220`). Still present, only deprecated.
- Non-deprecated alternatives:
  - `Level#isLoaded(BlockPos)` (`Level.java:633`).
  - `level.getChunkSource().getChunkNow(cx, cz)` → `@Nullable LevelChunk` without loading (`world/level/chunk/ChunkSource.java:16`).
  - Server only: `ServerLevel#isPositionEntityTicking(BlockPos)` (`ServerLevel.java:1881`).
- **Pitfall:** `Level#getBlockState` calls `getChunk(x,z)`, which requests `ChunkStatus.FULL` with load=true (`Level.java:344-350`, `:201`). On the server this **synchronously loads or generates** chunks. Before stepping a long ray or doing a physics sweep, check `isLoaded`. Treat unloaded space as solid, or stop the drone.

### 3.3 Raycasts (`vanilla/net/minecraft/world/level/ClipContext.java`)
```java
public ClipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity)
public ClipContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, CollisionContext collisionContext)
enum Block { COLLIDER, OUTLINE, VISUAL, FALLDAMAGE_RESETTING }   enum Fluid { NONE, SOURCE_ONLY, ANY, WATER }
```
- `BlockHitResult BlockGetter#clip(ClipContext)` (`world/level/BlockGetter.java:65`).
- `BlockHitResult CollisionGetter#clipIncludingBorder(ClipContext)` (`world/level/CollisionGetter.java:113`) also stops at the world border.
- `BlockHitResult` (`world/phys/BlockHitResult.java`): `getType()` (MISS/BLOCK), `getLocation()`, `getBlockPos()`, `getDirection()`, `isInside()`, `isWorldBorderHit()`.
- `isInside()` is true when the ray **starts inside** the shape. `VoxelShape#clip` tests `from + 0.001*(to-from)` and then returns `location = that test point` and the opposite approach direction (`world/phys/shapes/VoxelShape.java:163-181`). For a drone this means it is already embedded: push out, don't treat as a normal hit.
- Vanilla call sites:
  - `Entity.java:2045`: `level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, withLiquids ? Fluid.ANY : Fluid.NONE, this))`.
  - `PathNavigation.java:385` uses `COLLIDER`.
  - `ProjectileUtil.java:67` uses `clipIncludingBorder`.
```java
static @Nullable BlockHitResult groundBelow(Level level, Vec3 pos, double maxDist) {
    Vec3 to = pos.subtract(0, maxDist, 0);
    if (!level.isLoaded(BlockPos.containing(to))) return null;
    BlockHitResult hit = level.clip(new ClipContext(pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
    return hit.getType() == HitResult.Type.MISS ? null : hit;
}
```
**Pitfalls.**
- `new ClipContext(..., (Entity) null)` → NPE. `CollisionContext.of(Entity)` pattern-switches on the entity (`world/phys/shapes/CollisionContext.java:24`); use `CollisionContext.empty()` instead.
- A MISS still has `location = to` and `blockPos = containing(to)`, so always check `getType()`.
- Cost grows with ray length (block-by-block `traverseBlocks`).

### 3.4 Collision shapes (`vanilla/net/minecraft/world/level/block/state/BlockBehaviour.java`, inner `BlockStateBase`)
- Methods on `BlockStateBase`:
  - `VoxelShape getCollisionShape(BlockGetter, BlockPos)` (`:664`, cached, empty context).
  - `getCollisionShape(BlockGetter, BlockPos, CollisionContext)` (`:668`).
  - `getShape(...)` (outline, `:656/:660`).
  - `getVisualShape(...)` (`:680`).
  - `isCollisionShapeFullBlock(BlockGetter, BlockPos)` (`:878`).
- `VoxelShape`: `isEmpty()`, `bounds()`, `toAabbs()`, `max(Direction.Axis)`, `move(Vec3|Vec3i|double...)`, `clip(Vec3, Vec3, BlockPos)`.
- `Iterable<VoxelShape> CollisionGetter#getBlockCollisions(@Nullable Entity, AABB)` (`CollisionGetter.java:96`) yields **world-space** shapes; they are already moved (`BlockCollisions.java:96`).
- Other `CollisionGetter` helpers: `noCollision(AABB)` / `noCollision(Entity, AABB)` / `noBlockCollision(...)` (`:39-59`).
- `static Vec3 Entity.collideBoundingBox(Entity|CollisionContext, Vec3 movement, AABB, Level, List<VoxelShape> entityColliders)` (`Entity.java:1226,1231`) is vanilla's swept-AABB resolver. Reuse it for drone-vs-block movement if the drone is an `Entity`.

### 3.5 Entity queries (`vanilla/net/minecraft/world/level/Level.java`, `EntityGetter.java`)
```java
List<Entity> getEntities(@Nullable Entity except, AABB bb, Predicate<? super Entity> selector)                // Level.java:687
<T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB bb, Predicate<? super T> sel)     // :706
<T extends Entity> void getEntities(EntityTypeTest<Entity,T>, AABB, Predicate<? super T>, List<? super T> out, int maxResults) // :716
default <T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB bb[, Predicate<? super T>])           // EntityGetter.java:23,50
```
- Predicates: `EntitySelector.NO_SPECTATORS`, `ENTITY_STILL_ALIVE`, `CAN_BE_COLLIDED_WITH` (`world/entity/EntitySelector.java`).
- `EntityTypeTest.forClass(...)` lives in `world/level/entity/EntityTypeTest.java`.
- AABB factories: `new AABB(minX..maxZ)`, `new AABB(Vec3,Vec3)`, `new AABB(BlockPos)`, `AABB.ofSize(Vec3 center, sx, sy, sz)`, `inflate`, `move`, `intersects`, `clip(Vec3,Vec3)` → `Optional<Vec3>` (`world/phys/AABB.java`).
- Vanilla call sites: `BeaconBlockEntity.java:189` `level.getEntitiesOfClass(ServerPlayer.class, box)`; `ExperienceOrb.java:209` `getEntities(EntityTypeTest.forClass(...), box, pred)`.
- **Gate crossing:** don't query per gate per tick. Test the drone's swept segment `prev→pos` against each gate's AABB with `aabb.clip(prev, pos)`.
  - Pitfall: `AABB.clip(Vec3,Vec3)` (`AABB.java:294-310`) only reports a **face entry**. If `prev` is already inside the box, it returns `Optional.empty()`.
  - So model the gate as a thin plane box and also check `aabb.contains(pos)`. Or do our own plane-crossing test, e.g. a sign change of `dot(pos - gateCenter, gateNormal)` between ticks.

---

## 4. Commands (Brigadier, 26.3)

**Vanilla.**
- `Commands.literal(String)` / `Commands.argument(String, ArgumentType<T>)` (`vanilla/net/minecraft/commands/Commands.java:443,447`).
- Permission gate: `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`. `hasPermission(PermissionCheck)` is at `:545`, and the constants `LEVEL_ALL/MODERATORS/GAMEMASTERS/ADMINS/OWNERS` at `:165-169`.
- Full example: `server/commands/PublishCommand.java` (Bool and Integer args, `SimpleCommandExceptionType`, `source.sendSuccess(Supplier<Component>, boolean)`).
- `CommandSourceStack` (`commands/CommandSourceStack.java`): `getPlayerOrException()` (`:398`), `getPlayer()` (`:406`), `getLevel()` (`:382`), `getServer()` (`:418`), `sendSuccess` (`:461`), `sendFailure(Component)` (`:492`).
- Brigadier sources are not in `.ref`. Vanilla uses `IntegerArgumentType.integer(min,max)` / `getInteger(ctx,name)`, `StringArgumentType.word()` / `getString`, `BoolArgumentType` (see `PublishCommand`, `BanIpCommands.java:28`).
- Player arguments: `EntityArgument.player()` / `EntityArgument.getPlayer(ctx, name)` (`commands/arguments/EntityArgument.java:77,81`).
```java
// common
public final class PropwashCommands {
    public static void register(CommandDispatcher<CommandSourceStack> d, CommandBuildContext ctx) {
        d.register(Commands.literal("propwash")
            .then(Commands.literal("race").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reset").executes(c -> RaceManager.reset(c.getSource(), null))
                    .then(Commands.argument("track", StringArgumentType.word())
                        .executes(c -> RaceManager.reset(c.getSource(), StringArgumentType.getString(c, "track"))))))
            .then(Commands.literal("config").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("reload").executes(c -> PropwashServerConfig.reload(c.getSource())))));
    }
}
```
- **Fabric:** `CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> PropwashHooks.registerCommands(dispatcher, buildContext, selection));`. Signature: `void register(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)` (`fabric-api/fabric-command-api-v2/net/fabricmc/fabric/api/command/v2/CommandRegistrationCallback.java`).
- **NeoForge:** `NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> PropwashHooks.registerCommands(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection()));`. The event lives in `neoforge-api/net/neoforged/neoforge/event/RegisterCommandsEvent.java` and is fired on the **game bus** by `EventHooks.onCommandRegister` (`neoforge-api/.../event/EventHooks.java:842`).

**Pitfalls.**
- **Callbacks run many times.** A new `Commands` is built for every datapack load or reload (`server/ReloadableServerResources.java:45`). In the IDE it is also built once at bootstrap for validation (`server/Bootstrap.java:119-121` → `Commands.validate()`, `Commands.java:524`). Keep registration pure: build the tree and never touch server state.
- **Client commands are not portable.** Fabric uses `ClientCommandRegistrationCallback` with `FabricClientCommandSource` (`fabric-command-api-v2/.../client/command/v2/`). NeoForge uses `RegisterClientCommandsEvent` (`neoforge-api/.../client/event/`). Avoid them in common, or keep them per loader.

---

## 5. Permissions, game rules, server config

### 5.1 Permissions (`vanilla/net/minecraft/server/permissions/`)
- Core types:
  - `PermissionSet#hasPermission(Permission)`.
  - `Permissions.COMMANDS_MODERATOR/GAMEMASTER/ADMIN/OWNER` (levels 1-4).
  - `PermissionLevel` enum `ALL(0)..OWNERS(4)` with `isEqualOrHigherThan`.
  - `LevelBasedPermissionSet`.
- Where to get a permission set:
  - `Player#permissions()` returns `NO_PERMISSIONS` (`world/entity/player/Player.java:1825`).
  - `ServerPlayer#permissions()` returns `server.getProfilePermissions(nameAndId())` (`server/level/ServerPlayer.java:1927`, `MinecraftServer.java:1802`).
  - `LocalPlayer#permissions()` returns the server-synced set (`client/player/LocalPlayer.java:437`). Use it for UI only.
  - `CommandSourceStack#permissions()` (`:374`).
- Vanilla use: `BlockItem.java:178` `player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`.
```java
static boolean canEditTrack(ServerPlayer p) { return p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }
```
- Optional permission-node APIs are loader specific, so wrap them in a service only if needed:
  - Fabric `fabric-permission-api-v1`: `PermissionContextOwner#checkPermission(Identifier, PermissionLevel)`, injected on Entity and CommandSourceStack.
  - NeoForge: `neoforge-api/net/neoforged/neoforge/server/permission/PermissionAPI.java`.

### 5.2 Game rules (`vanilla/net/minecraft/world/level/gamerules/`)
- `GameRule<T>` has a **public** constructor: `(GameRuleCategory, GameRuleType, ArgumentType<T>, GameRules.VisitorCaller<T>, Codec<T>, ToIntFunction<T>, T defaultValue, FeatureFlagSet)` (`GameRule.java:26`).
- Vanilla registers with `Registry.register(BuiltInRegistries.GAME_RULE, id, new GameRule<>(...))` from private helpers (`GameRules.java:167-218`).
- Read: `serverLevel.getGameRules().get(rule)` (`ServerLevel.java:372`, `:1911`) or `server.getGameRules()` (`MinecraftServer.java:2124`).
- Write: `GameRules#set(GameRule<T>, T, @Nullable MinecraftServer)` (`GameRules.java:133`).
- Lang key: `gamerule.<ns>.<path>` (`GameRule#getDescriptionId`).
- Categories: `GameRuleCategory` is a public `record GameRuleCategory(Identifier id)`. The static `GameRuleCategory.register(Identifier)` (`GameRuleCategory.java:28`) is public in vanilla but **`@deprecated` on NeoForge**, where the replacement is `RegisterGameRuleCategoryEvent` (`neoforge/net/minecraft/world/level/gamerules/GameRuleCategory.java:28-30`). **Recommendation: reuse `GameRuleCategory.MISC`** and avoid the split.
```java
// common: build the rule object; register it via the registry list from §2
public static final GameRule<Boolean> DRONE_BLOCK_DAMAGE = new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL,
    BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean, Codec.BOOL, b -> b ? 1 : 0, false, FeatureFlagSet.of());
// fabric: Registry.register(BuiltInRegistries.GAME_RULE, id, RULE)   (or GameRuleBuilder.forBoolean(false).buildAndRegister(id),
//         fabric-api/fabric-game-rule-api-v1/net/fabricmc/fabric/api/gamerule/v1/GameRuleBuilder.java:81,157)
// neoforge: RegisterEvent / DeferredRegister on Registries.GAME_RULE (Registries.java:169; type Registry<GameRule<?>>, BuiltInRegistries.java:306).
//           NeoForge has NO rule builder, but it has:
//           - RegisterGameRuleCategoryEvent (mod bus, register(GameRuleCategory)) — neoforge-api/net/neoforged/neoforge/event/RegisterGameRuleCategoryEvent.java
//           - GameRuleChangedEvent (game bus, runIfMatching(GameRule<T>, Consumer<T>)) — neoforge-api/.../event/level/GameRuleChangedEvent.java:58,
//             posted from EventHooks.java:1169
// fabric change hook: GameRuleEvents.changeCallback(GameRule<T>) -> Event<ValueUpdate<T>> (fabric-api/fabric-game-rule-api-v1/.../GameRuleEvents.java:32)
```
- **Pitfall:** register before any world loads. `GameRules` snapshots the registry at world creation (`GameRules.java:113`).
- **Reading and writing:** `GameRules#get` throws `IllegalArgumentException` for a rule that isn't in the map, while `set` only logs a warning (`GameRules.java:124-142`).

### 5.3 Server JSON config (recommended over game rules for anything non-trivial)
- **Portable pattern:** Codec + JsonOps, as vanilla does in `server/PlayerAdvancements.java:111-139`:
  - Read: `StrictJsonParser.parse(reader)` (`util/StrictJsonParser.java:16`) → `codec.parse(JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new)`.
  - Write: `codec.encodeStart(...)` → pretty `Gson`, then `FileUtil.createDirectoriesSafe(dir)` (`util/FileUtil.java:173`).
- **Location:**
  - Global: `Services.PLATFORM.getConfigDir().resolve("propwash-server.json")`.
  - Per-world: `server.getWorldPath(new LevelResource("serverconfig"))`. `LevelResource` is a public record (`world/level/storage/LevelResource.java:3`), and NeoForge uses the same dir for its SERVER configs (`neoforge-api/.../server/ServerLifecycleHooks.java:66-73`).
- **Load hook:**
  - Fabric: `ServerLifecycleEvents.SERVER_STARTING.register(server -> ...)` (`fabric-lifecycle-events-v1/.../ServerLifecycleEvents.java:36`).
  - NeoForge: `NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> ...)` (`neoforge-api/.../event/server/ServerStartingEvent.java`, `getServer()` from `ServerLifecycleEvent`).
  - Reload through `/propwash config reload`.
  - If the client needs values (e.g. max rates), sync them with a payload on join.
- NeoForge's `ModConfigSpec` (`neoforge-api/.../common/ModConfigSpec.java`) is NeoForge-only. Skip it for common.
```java
public record ServerCfg(int maxDronesPerPlayer, boolean allowDigital) {
    public static final Codec<ServerCfg> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("max_drones_per_player", 4).forGetter(ServerCfg::maxDronesPerPlayer),
        Codec.BOOL.optionalFieldOf("allow_digital", true).forGetter(ServerCfg::allowDigital)).apply(i, ServerCfg::new));
}
```
Use `optionalFieldOf` defaults so that old files keep working, and write the file back after load so new keys appear.

---

## 6. Client / dedicated-server separation in `common`
- **Annotation-based stripping can't be relied on in common.**
  - NeoForge's `@OnlyIn` no longer strips anything. In dev only (`!FMLEnvironment.isProduction()`), it logs an error and adds a load warning (`neoforge-api/net/neoforged/neoforge/common/OnlyInWarningsHandler.java:26-45`).
  - Fabric's `@Environment` **does** strip mod code. In Fabric Loader 0.19.5, `MinecraftGameProvider.getBuiltinTransforms(name)` returns `TRANSFORM_STRIPENV` for every non-Minecraft class, and `FabricTransformer.transform` then applies `EnvironmentStrippingData`/`ClassStripper`. This is from the `fabric-loader-0.19.5.jar` bytecode; its source is not in `.ref`.
  - `net.fabricmc.api.Environment` is not on common's classpath anyway, and NeoForge would ignore it.
  - So the class structure itself must keep client classes off the server.
- **Rules:**
  1. All code touching `net.minecraft.client.*` goes under `de.lardtom.propwash.client..`. Common/server classes must never have fields, method signatures, casts, `instanceof` or lambdas that reference client types.
  2. **Client entrypoints:**
     - Fabric: add `"client": ["de.lardtom.propwash.PropwashFabricClient"]` to `fabric.mod.json` and implement `ClientModInitializer#onInitializeClient` (example: `fabric-api/fabric-renderer-indigo/.../Indigo.java:43,133`).
     - NeoForge: a second class `@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)` with constructor `(IEventBus, ModContainer)` (`neoforge-api/net/neoforged/neoforge/client/ClientNeoForgeMod.java:95-101`; imports `net.neoforged.api.distmarker.Dist`, `net.neoforged.fml.common.Mod`).
     - Both call `PropwashClient.init()` in common.
  3. **Runtime checks:**
     - `level.isClientSide()` (`Level.java:162`) is the logical side.
     - `server.isDedicatedServer()` (`MinecraftServer.java:1324`).
     - For the physical dist, use `Services.PLATFORM.isClientDist()` (§2).
  4. **Shared code that must reach client code** (e.g. an entity opening a screen) goes through a proxy interface set by the client init:
     ```java
     public final class ClientBridge { public static Consumer<QuadcopterEntity> openDroneScreen = d -> {}; }  // common
     // PropwashClient.init(): ClientBridge.openDroneScreen = d -> Minecraft.getInstance().setScreen(new DroneScreen(d));
     ```
  5. Client mixins go in the `"client"` array (§1). Client services go in `ClientServices` (§2).
- **How to verify:**
  - Run `./gradlew :fabric:runServer` and `:neoforge:runServer`, and join once from a client.
  - For CI on NeoForge, `NEOFORGE_DEDICATED_SERVER_SELFTEST=<file>` starts a dedicated server, writes `<file>` once it ticks, then halts (`neoforge-api/.../common/util/SelfTest.java:33-45`).
  - Loom 1.18.1 also has `net.fabricmc.loom.task.prod.ServerProductionRunTask` / `ClientProductionRunTask` for production-like runs. The properties `mods`, `jvmArgs`, `programArgs`, `runDir` and `useXVFB` were read from the jar's class constants, not from source.

---

## 7. Automated tests

### 7.1 Fabric server gametests (`fabric-api/fabric-gametest-api-v1`)
- `@net.fabricmc.fabric.api.gametest.v1.GameTest` goes on **public, non-static** methods with exactly one `GameTestHelper` parameter. `TestAnnotationLocator.java:104-113` rejects anything else.
- Annotation attributes (`.../api/gametest/v1/GameTest.java:41-98`):
  - `environment` (default `minecraft:default`), `dimension` (default `minecraft:overworld`), `structure` (default `fabric-gametest-api-v1:empty`).
  - `maxTicks=20`, `setupTicks=0`, `required=true`, `rotation`, `manualOnly`, `maxAttempts=1`, `requiredSuccesses=1`, `skyAccess=false`, `padding=1`.
- Register the class under the entrypoint key `"fabric-gametest"` (`.../impl/gametest/TestAnnotationLocator.java:47`).
- Enabled with `-Dfabric-api.gametest`. Report with `-Dfabric-api.gametest.report-file=<junit.xml>`, filter with `-Dfabric-api.gametest.filter` (`.../impl/gametest/GameTestSystemProperties.java:23-33`).
- Runs as a headless dedicated server.

### 7.2 Fabric client gametests (`fabric-api/fabric-client-gametest-api-v1`, lifecycle "experimental")
- `interface FabricClientGameTest { void runTest(ClientGameTestContext context); }`, registered under the entrypoint key `"fabric-client-gametest"` (`.../impl/client/gametest/FabricClientGameTestRunner.java:36`).
- Enabled with `-Dfabric.client.gametest`. That string comes from `ClientGameTestMixinConfigPlugin` in the cached `fabric-client-gametest-api-v1-6.0.7` jar; its source is not in `.ref`, but `TestSystemProperties.java:26` references its `ENABLED` flag.
- Filter with `-Dfabric.client.gametest.modid` (comma-separated, `TestSystemProperties.java:37`).
- `ClientGameTestContext` (`.../context/ClientGameTestContext.java`) methods:
  - Waiting: `waitTick()`, `waitTicks(int)`, `waitFor(Predicate<Minecraft>[, timeout])`, `waitForScreen(Class)`.
  - Screens: `setScreen(Supplier<Screen>)`, `clickScreenButton(String translationKey)`.
  - Screenshots: `takeScreenshot(TestScreenshotOptions)`, `assertScreenshotEquals(...)`.
  - `getInput()`, `worldBuilder()`, `runOnClient(FailableConsumer<Minecraft,E>)`, `computeOnClient(...)`.
- `TestWorldBuilder.create()` returns a `TestSingleplayerContext`:
  - `getServer()` gives `runCommand(String)`, `runOnServer(...)` and `computeOnServer(...)`.
  - `getConnection()` gives `getClientPlayer()`, `getServerPlayer()`, `getClientLevel()`, `getServerLevel()`, `waitForChunksRender(...)`, `waitForClientboundPackets()` and `waitForServerboundPackets()`.
  - `close()`.
- `createServer(Properties)` starts a real dedicated server connection.
- `TestInput` (`.../TestInput.java`): `holdKey/releaseKey/pressKey/holdKeyFor(KeyMapping|Function<Options,KeyMapping>|InputConstants.Key|int, ticks)`, `holdMouse`, `lookAt(yaw,pitch)`, `moveCursor`, `scroll`, `typeChars`, `resizeWindow`.
- **Determinism:** the game stays paused between waits, and there is exactly one server tick per client tick (package-info).
- **Not headless:** it opens a real window, which is fine on macOS. The only headless route is `ClientProductionRunTask.useXVFB`, which is Linux-only (jar string "XVFB is only supported on Linux").
- A gamepad or RC transmitter cannot be injected through `TestInput`. So make our input layer take a `StickInput` interface that tests can feed directly.
```java
public class DroneClientTest implements FabricClientGameTest {
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender(true, 600);           // check exact overloads in TestServerConnection.java:62-107
            sp.getServer().runCommand("summon propwash:drone ~ ~2 ~");
            ctx.runOnClient(mc -> TestStickInput.set(0.6f, 0, 0, 0));    // throttle 60%
            ctx.waitTicks(40);
            double y = sp.getServer().computeOnServer(s -> DroneQueries.firstDroneY(s));
            if (y < 2.5) throw new AssertionError("drone did not climb: " + y);
            ctx.takeScreenshot(TestScreenshotOptions.of("fpv_climb"));
        }
    }
}
```
**Loom wiring (Loom 1.18.1).**
- The documented DSL is in the client-gametest `package-info.java`: `fabricApi { configureTests { createSourceSet = true; modId = 'propwash-test' } }`.
- The other `GameTestSettings` property names come from the jar: `enableGameTests`, `enableClientGameTests`, `eula`, `clearRunDirectory`, `username`.
- It creates run configs `gameTest` and `clientGameTest`, with run dirs `build/run/gameTest` and `build/run/clientGameTest`, giving the tasks `:fabric:runGameTest` and `:fabric:runClientGameTest`. The run names and dirs are string constants in `FabricApiTesting.class`. The task names follow Loom's `run<Name>` convention (UNVERIFIED for these two).
- The test source set is named `gametest` (string constant in `FabricApiTesting.class`), so its code lives in `fabric/src/gametest/java`.
- UNVERIFIED: whether Loom generates a `fabric.mod.json` for it. Plan to write `fabric/src/gametest/resources/fabric.mod.json` ourselves, with `"id": "propwash-test"` and the `fabric-gametest` / `fabric-client-gametest` entrypoints.

### 7.3 NeoForge gametests
- **Enable condition:** `GameTestHooks.isGametestEnabled()` = `!production && (SharedConstants.IS_RUNNING_IN_IDE || gameTestServer || -Dneoforge.enableGameTest)` (`neoforge-api/net/neoforged/neoforge/gametest/GameTestHooks.java:14`).
- **Run types** in NeoForge 26.3.0.10-beta (`config.json` in the userdev jar in the Gradle cache):
  - `client`: sets `neoforge.enableGameTest=true`, so `/test` works in the dev client.
  - `server`, `clientData`, `serverData`, `junit`.
  - `gameTestServer`: main `net.neoforged.fml.startup.GameTestServer`.
  - In ModDevGradle: `neoForge { runs { gameTestServer { type = "gameTestServer" } } }`. `RunModel` has a `type` property but no `gameTestServer()` shortcut, per the class constants of MDG 2.0.147.
- **Registration:** `RegisterGameTestsEvent` is fired on the **mod bus** (`neoforge-api/.../event/RegisterGameTestsEvent.java`, posted from `neoforge/net/minecraft/resources/RegistryDataLoader.java:272`).
  - It is posted **only if** `fromResources && GameTestHooks.isGametestEnabled()` (`RegistryDataLoader.java:267`). A missing test is therefore usually the enable flag, not the listener.
  - Its methods (`:39,43,49,53`):
  ```java
  Holder<TestEnvironmentDefinition<?>> registerEnvironment(Identifier name, TestEnvironmentDefinition<?>... defs)   // wraps in new TestEnvironmentDefinition.AllOf(defs)
  Holder<TestEnvironmentDefinition<?>> registerEnvironment(Identifier name, TestEnvironmentDefinition<?> def)
  void registerTest(Identifier name, GameTestInstance test)
  void registerTest(Identifier name, Function<TestData<Holder<TestEnvironmentDefinition<?>>>, GameTestInstance> factory, TestData<...> data)
  ```
- **Test functions** are registry entries: `Registries.TEST_FUNCTION` → `Registry<Consumer<GameTestHelper>>` (`vanilla/net/minecraft/core/registries/Registries.java:255`). Register them via `DeferredRegister`.
- `new FunctionGameTestInstance(ResourceKey<Consumer<GameTestHelper>> fn, TestData<Holder<TestEnvironmentDefinition<?>>> info)` (`vanilla/net/minecraft/gametest/framework/FunctionGameTestInstance.java:22`).
- `new TestData<>(env, Identifier structure, maxTicks, setupTicks, required[, rotation])` (`TestData.java:47,51`). The built-in empty structure is `minecraft:empty` (`vanilla/data/minecraft/structure/empty.nbt`).
```java
static final DeferredRegister<Consumer<GameTestHelper>> FNS = DeferredRegister.create(Registries.TEST_FUNCTION, Constants.MOD_ID);
static { FNS.register("hover", () -> (Consumer<GameTestHelper>) PropwashTests::hover); }  // cast avoids inference trouble; FNS.register(modBus) in @Mod ctor (DeferredRegister.java:214,315)
modBus.addListener((RegisterGameTestsEvent e) -> {
    var env = e.registerEnvironment(id("default"));                   // empty AllOf
    e.registerTest(id("hover"), new FunctionGameTestInstance(
        ResourceKey.create(Registries.TEST_FUNCTION, id("hover")),
        new TestData<>(env, Identifier.withDefaultNamespace("empty"), 100, 0, true)));
});
```
- **Client:** no client gametest API exists in NeoForge 26.3.
  - Only `NEOFORGE_CLIENT_SELFTEST=<file>` exists: it boots to the title screen, writes the file and stops (`neoforge-api/.../client/internal/SelfTestClient.java`). That makes it a startup and mixin smoke test for CI.
  - JUnit with the game loaded: MDG `neoForge { unitTest { enable(); testedMod = mods.propwash } }`. The DSL names `enable`, `testedMod` and `loadedMods` come from the `UnitTest.class` constants.

### 7.4 Recommended cross-loader test layout
- **Shared test bodies.** Put them in common as `static void xyz(GameTestHelper h)`, using vanilla `GameTestHelper` (`vanilla/net/minecraft/gametest/framework/GameTestHelper.java`): `getLevel()` `:105`, `spawn(EntityType,Vec3)` `:171`, `assertTrue(boolean,String)` `:1089`, `succeed()` `:927`, `succeedWhen(Runnable)` `:944`, `runAfterDelay(long,Runnable)` `:962`.
- **Loader wrappers.** Fabric uses `@GameTest` methods that delegate; NeoForge uses the `TEST_FUNCTION` DeferredRegister.
- **Flight model.** Keep the flight model (rates, PID, motor mixing) as pure Java with no MC types, and unit-test it with plain JUnit in `common`.
  - `project:common/build.gradle` **already** has `testImplementation('org.junit.jupiter:junit-jupiter:5.11.3')`, `testRuntimeOnly('org.junit.platform:junit-platform-launcher')` and `test { useJUnitPlatform() }`.
  - So only `common/src/test/java` is missing. Run the tests with `./gradlew :common:test`.
- **Template cleanup.** Remove `systemProperty('neoforge.enabledGameTestNamespaces', mod_id)` from `project:neoforge/build.gradle`, because nothing reads it. If gametests should also run in the `server` run type, set `neoforge.enableGameTest=true` there.

---

## 8. Workbench (drone assembly block: menu + screen)
The user wants a workbench block where drones are assembled.

### 8.1 Who can access what (verified)
| Member (vanilla: private) | Vanilla | NeoForge | Fabric API |
|---|---|---|---|
| `MenuType(MenuSupplier<T>, FeatureFlagSet)` ctor | private (`MenuType.java:48`) | **public** (patched `neoforge/.../MenuType.java:50`, AT `neoforge-api/META-INF/accesstransformer.cfg:202`) | **transitive-accessible** (`fabric-api/fabric-transitive-access-wideners-v1/fabric-transitive-access-wideners-v1.classtweaker:8`) |
| `MenuType.MenuSupplier<T>` (`T create(int containerId, Inventory)`) | private (`:62`) | public (AT `:203`) | transitive-accessible (`:7`) |
| `MenuScreens.register(MenuType<? extends M>, ScreenConstructor<M,U>)` | private static (`MenuScreens.java:56`) | **still private** + `@Deprecated` → use `RegisterMenuScreensEvent` (`neoforge/.../MenuScreens.java:61-64`) | transitive-accessible (`:12`) |
| `MenuScreens.ScreenConstructor<T,U>` (`U create(T menu, Inventory, Component title)`, `:113`) | private (`:106`) | public (AT `:20`) | transitive-accessible (`:11`) |

- Fabric API's `fabric-menu-api-v1.classtweaker:2-7` repeats these as plain `accessible`, which only matters inside that module. The **transitive** file is what makes them usable for our Fabric module, at compile time and at runtime.
- Common compiles against plain NeoForm vanilla, so it needs **our own AT** to name the `MenuType` constructor. This is the only thing common needs. Copy NeoForge's lines verbatim into `common/src/main/resources/META-INF/accesstransformer.cfg`:
  ```
  public net.minecraft.world.inventory.MenuType <init>(Lnet/minecraft/world/inventory/MenuType$MenuSupplier;Lnet/minecraft/world/flag/FeatureFlagSet;)V
  public net.minecraft.world.inventory.MenuType$MenuSupplier
  ```
  - NeoForge already has both at runtime, and the Fabric module compiles against Loom's jar, where the transitive AW applies. So **no `propwash.accesswidener` entry is needed** for this.

### 8.2 Vanilla pattern to copy (`vanilla/net/minecraft/world/level/block/CraftingTableBlock.java:25-40`)
- The block overrides `protected InteractionResult useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)`. On the server (`!level.isClientSide()`) it calls `player.openMenu(state.getMenuProvider(level, pos))` and returns `InteractionResult.SUCCESS`.
  - `Player#openMenu(@Nullable MenuProvider)` returns `OptionalInt` (`Player.java:791`, `ServerPlayer.java:1386`).
- `protected MenuProvider getMenuProvider(BlockState, Level, BlockPos)` returns `new SimpleMenuProvider(MenuConstructor, Component title)` (`world/SimpleMenuProvider.java:13`).
  - `MenuConstructor#createMenu(int containerId, Inventory, Player)` (`world/inventory/MenuConstructor.java:9`).
- The menu has **two constructors**:
  - Client: `(int containerId, Inventory)`, which uses `ContainerLevelAccess.NULL`.
  - Server: `(int, Inventory, ContainerLevelAccess)`, which uses `ContainerLevelAccess.create(level, pos)` (`CraftingMenu.java:35,39`; `ContainerLevelAccess.java:10,17`).
- Base class: `protected AbstractContainerMenu(@Nullable MenuType<?>, int containerId)` (`AbstractContainerMenu.java:70`).
  - Abstract `quickMoveStack(Player, int)` (`:312`) and `stillValid(Player)` (`:650`).
  - Helper `protected static boolean stillValid(ContainerLevelAccess, Player, Block)` (`:96`).
- Screen: `public AbstractContainerScreen(T menu, Inventory, Component title[, int imageWidth, int imageHeight])` (`client/gui/screens/inventory/AbstractContainerScreen.java:60,64`), so `WorkbenchScreen::new` fits `ScreenConstructor`.
- Slot contents sync to the client automatically through the menu. The client side uses a `SimpleContainer` of the same size. So **no extra open-data is needed** for a parts bench.

### 8.3 Recommended cross-loader layout
```java
// common: menu + type (compiles thanks to the 2 AT lines above)
public class WorkbenchMenu extends AbstractContainerMenu {
    public WorkbenchMenu(int id, Inventory inv) { this(id, inv, new SimpleContainer(PARTS), ContainerLevelAccess.NULL); }   // client
    public WorkbenchMenu(int id, Inventory inv, Container parts, ContainerLevelAccess access) { super(PropwashMenus.WORKBENCH, id); /* addSlot(...) */ }
    @Override public boolean stillValid(Player p) { return stillValid(access, p, PropwashBlocks.WORKBENCH); }
    @Override public ItemStack quickMoveStack(Player p, int slot) { /* ... */ return ItemStack.EMPTY; }
}
public final class PropwashMenus {
    public static final MenuType<WorkbenchMenu> WORKBENCH = new MenuType<>(WorkbenchMenu::new, FeatureFlags.VANILLA_SET); // FeatureFlags.java:15
    // add (Registries.MENU /*Registries.java:222*/, id("workbench"), () -> WORKBENCH) to the §2 registry list
}
// fabric client (PropwashFabricClient#onInitializeClient) — transitive AW makes this legal:
MenuScreens.register(PropwashMenus.WORKBENCH, WorkbenchScreen::new);
// neoforge client (@Mod(dist = Dist.CLIENT) ctor):
modBus.addListener((RegisterMenuScreensEvent e) -> e.register(PropwashMenus.WORKBENCH, WorkbenchScreen::new));
//   RegisterMenuScreensEvent#register(MenuType<? extends M>, MenuScreens.ScreenConstructor<M,U>) — client/event/RegisterMenuScreensEvent.java:27;
//   mod-bus event (IModBusEvent), posted by MenuScreens.init() from ClientHooks.java:808
```
**If the client ever needs open-data** (e.g. the bench `BlockPos` for a 3D drone preview), the menu types differ per loader, so put type creation behind an `IMenuHelper` service:
- NeoForge: `IMenuTypeExtension.create(IContainerFactory<T>)` (`neoforge-api/.../common/extensions/IMenuTypeExtension.java:25`), where `IContainerFactory#create(int, Inventory, RegistryFriendlyByteBuf)` (`network/IContainerFactory.java:31`).
  - Open with `player.openMenu(provider, pos)` or `openMenu(provider, Consumer<RegistryFriendlyByteBuf>)` (`IPlayerExtension.java:58,75`; `ServerPlayer.java:1434` patched).
- Fabric: `new ExtendedMenuType<>(ExtendedFactory<T,D>, StreamCodec<? super RegistryFriendlyByteBuf, D>)` (`fabric-api/fabric-menu-api-v1/.../api/menu/v1/ExtendedMenuType.java:112`).
  - The provider implements `ExtendedMenuProvider<D>#getScreenOpeningData(ServerPlayer)` (`ExtendedMenuProvider.java:28-29`) and is opened with plain `player.openMenu(provider)`.
  - `BlockPos.STREAM_CODEC` (`core/BlockPos.java:37`) works as `D`.

**Pitfalls.**
- `MenuType` objects must be registered in `Registries.MENU` before the first open. `ClientboundOpenScreenPacket` encodes the type with `ByteBufCodecs.registry(Registries.MENU)` (`network/protocol/game/ClientboundOpenScreenPacket.java:17`), so an unregistered type cannot be sent.
- On NeoForge, registering the same type twice in `RegisterMenuScreensEvent` throws `IllegalStateException("Duplicate attempt to register screen")` (`RegisterMenuScreensEvent.java:29-31`).
- `WorkbenchScreen` references `net.minecraft.client.*`, so keep it under `de.lardtom.propwash.client..` and register it only from the client entrypoints (§6).

---

## Verification
Checked by a second pass against `.ref` (vanilla, NeoForge-patched, NeoForge API, Fabric API) and the project files. Anything not in `.ref` was checked against jars in `~/.gradle/caches` with `javap` or `unzip`: fabric-loader 0.19.5, FML loader 12.0.0, NeoForge 26.3.0.10-beta userdev/POM, Loom 1.18.1, MDG 2.0.147, fabric-client-gametest-api-v1 6.0.7.

**Checked (about 185 claims)**
- §1:
  - Project mixin configs, `fabric.mod.json` and `neoforge.mods.toml`, `common/build.gradle` deps, `multiloader-loader.gradle` `commonJava`.
  - The 3 example mixin files and their line numbers.
  - `Camera` members, the NeoForge `Commands` hook at :306, classtweaker headers.
- §2:
  - `Services.load` and its NPE message.
  - `RegisterEvent#register` :60, `DeferredRegister.create` :116.
  - Fabric `getConfigDir`/`getEnvironmentType` call sites, `ServerPlayNetworking.send` :287, `ClientPlayNetworking.send` :240.
  - `PacketDistributor.sendToPlayer` :36 (the real signature has `CustomPacketPayload...` varargs), `ClientPacketDistributor.sendToServer` :22, `FMLPaths`/`FMLEnvironment` in `CommonModLoader` :61-62, `Dist.isClient()` usage.
- §3:
  - Heightmap types and usages, `LevelReader` :36-40/:84/:187-207, `Level` :162/:201/:323/:344/:633/:687-716.
  - `ChunkSource.getChunkNow` :16, `ServerLevel` :588/:1881.
  - `ClipContext` constructors and enums, `BlockGetter.clip` :65 (miss = `to`/`containing(to)`), `clipIncludingBorder` :113, `BlockHitResult` getters, `VoxelShape.clip` :163-181, `CollisionContext.of` switch and `empty()`.
  - All 3 vanilla clip call sites, `BlockStateBase` :656-878, `CollisionGetter` :39-104, `BlockCollisions` :96.
  - `Entity.collideBoundingBox` :1226/1231, `EntityGetter` :23/50, `EntitySelector`, `EntityTypeTest.forClass`, `AABB` ctors and methods, Beacon/ExperienceOrb call sites.
- §4:
  - `Commands` :165-169/:443/:447/:524/:545 and the `PermissionProviderCheck` Predicate.
  - `PublishCommand`, `BanIpCommands` :28, `CommandSourceStack` :374-492, `EntityArgument` :77/81.
  - Fabric `CommandRegistrationCallback` :54, NeoForge `RegisterCommandsEvent` getters, `EventHooks` :842, `ReloadableServerResources` :45, `Bootstrap` validate, client-command classes.
- §5:
  - All `server/permissions` types, the `permissions()` overrides :1825/:1927/:437, `MinecraftServer` :1802, `BlockItem` :178, removal of `hasPermission(int)`.
  - Fabric `PermissionContextOwner` :91 + interface injection, NeoForge `PermissionAPI`.
  - `GameRule` ctor :26, `GameRules` :113/:124/:133/:167-219, `GameRuleCategory`, `Registries.GAME_RULE` :169, `GameRuleBuilder` :81/:157.
  - `PlayerAdvancements` :111-139, `StrictJsonParser` :16, `FileUtil` :173, `LevelResource`, `getWorldPath` :1956, `ServerLifecycleHooks` :66-73, lifecycle events, `ModConfigSpec`.
- §6: `OnlyInWarningsHandler`, `Indigo` :43/133, `ClientNeoForgeMod` :95-101, `isDedicatedServer` :1324, `SelfTest` :33-45, Loom production-task properties (javap).
- §7:
  - Fabric `GameTest` attributes, entrypoint keys, system properties, `ClientGameTestContext`/`TestWorldBuilder`/`TestServerContext`/`TestServerConnection`/`TestInput`/`TestScreenshotOptions` signatures.
  - Loom `GameTestSettings`, run names and dirs, the XVFB string.
  - `GameTestHooks` :14, userdev `config.json` run types and props, MDG `RunModel.getType()` / no `gameTestServer()`, `UnitTest` DSL.
  - `RegisterGameTestsEvent` methods and fire site, `Registries.TEST_FUNCTION` :255, `FunctionGameTestInstance` :22, `TestData` :47/51, `minecraft:empty` structure, `DeferredRegister` :214/:315, `SelfTestClient`, `GameTestHelper` lines, `enabledGameTestNamespaces` (read nowhere, including the FML/MDG/NeoForge jars).
- §8: all members in the table in 8.1, both classtweakers, NeoForge AT lines, `IMenuTypeExtension`, `IContainerFactory`, `RegisterMenuScreensEvent`, `ExtendedMenuType`/`ExtendedMenuProvider`, `CraftingTableBlock`/`CraftingMenu`/`AbstractContainerMenu`/`AbstractContainerScreen`, `IPlayerExtension.openMenu`.

**Corrected (16)**
1. **Workbench on Fabric (§8, Overview).** The notes said Fabric widens `MenuType`/`MenuScreens` only non-transitively and that we need our own AW. **Wrong:** `fabric-transitive-access-wideners-v1` makes the ctor, `MenuSupplier`, `ScreenConstructor` and `MenuScreens.register` transitive-accessible. §8 is rewritten: common creates the `MenuType` (2 AT lines), and only screen registration is per loader.
2. **Fabric `@Environment` (§6).** The notes said it does not strip mod code. **Wrong:** Loader 0.19.5 strips environment annotations in all non-Minecraft classes (bytecode of `MinecraftGameProvider.getBuiltinTransforms` / `FabricTransformer`). The conclusion (structure-based separation in common) still stands.
3. **JUnit in common (§7.4).** The notes said the `testImplementation` dependency is missing. **Wrong:** JUnit Jupiter 5.11.3 and `useJUnitPlatform()` are already in `common/build.gradle`.
4. **NeoForge game rules (§5.2).** The notes said NeoForge has no game-rule API. It has `RegisterGameRuleCategoryEvent` and `GameRuleChangedEvent`, and deprecates `GameRuleCategory.register`. Also added Fabric `GameRuleEvents.changeCallback`.
5. **Heightmap predicates (§3.1).** These are block **tags** (`BLOCKS_MOTION_IN_HEIGHTMAP`, `..._NO_LEAVES`), not "material". `OCEAN_FLOOR` ignores fluids.
6. **Fabric `@GameTest` (§7.1).** Added the missing `dimension` attribute and the defaults. Methods must be public **and** non-static.
7. **MixinExtras versions (§1).** Stated the exact runtime versions: Fabric 0.5.5 (jar-in-jar), NeoForge 0.5.4 (POM).
8. **AW/AT files (§1).** They do not exist yet. Documented the FML 12 fallback to `META-INF/accesstransformer.cfg` and NeoForge's explicit `[[accessTransformers]]` block.
9. **Camera accessor (§1).** The `xRot` accessor is unnecessary because a public `xRot()` getter exists. Added the NeoForge `Camera` roll patch.
10. **`IPlatformHelper` (§2).** Fixed the method names (`getPlatformName`, `getEnvironmentName`).
11. **Services (§2).** "Must hold exactly one implementation" is really `findFirst()` semantics. Clarified the NPE → `ExceptionInInitializerError`.
12. **`AABB.clip` (§3.5).** Added the pitfall: it returns empty when the start point is inside the box.
13. **`RegisterGameTestsEvent` (§7.3).** It is posted only when `isGametestEnabled()`. Added the single-definition `registerEnvironment` overload.
14. **Client gametest filter (§7.2).** Cited `TestSystemProperties`; the filter is comma-separated.
15. **Loom test source set (§7.2).** It is named `gametest`. Whether Loom generates its `fabric.mod.json` is now marked UNVERIFIED.
16. **`GameRules#get`/`set` (§5.2).** Added their failure behaviour: `get` throws, `set` only warns.

**Open questions**
1. Does FML 12 still apply `META-INF/accesstransformer.cfg` when `neoforge.mods.toml` has **no** `[[accessTransformers]]` block? The bytecode fallback exists, but the exact condition wasn't traced. Needs a test run with an AT entry that NeoForge doesn't already cover, or just add the explicit block.
2. Loom 1.18.1 `configureTests`: does it generate or merge a `fabric.mod.json` for the `gametest` source set, and does that source set see common's sources? This also decides whether shared test bodies in common reach Fabric gametests.
3. Game rules on NeoForge: will registering a `GameRule` via `DeferredRegister` on `Registries.GAME_RULE` interact badly with NeoForge registry sync or freezing? `GameData` only touches `GameRuleCategory`.
   - Verified: vanilla sends rule values to the client **only on request, only to gamemasters, and only for `InWorldGameRulesScreen`**. See `ServerGamePacketListenerImpl.java:1982-1995` and `ClientPacketListener.java:2103-2107`.
   - So any client-side read of a Propwash rule (e.g. a HUD limit) needs our own sync payload.

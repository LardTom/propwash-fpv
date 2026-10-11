# Networking, entities, collisions and view anchoring (MC 26.3)

> **Projekt-Layout veraltet:** Die eine Mixin-Config `propwash.mixins.json` (stattdessen je Modul `propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`), Registrierungs-Klassen (`PropwashItems`, `PropwashEntities`, `PropwashComponents`, `RegList.Ref`, …), `CommonClass`, das Paket `net` (heute `network`) und Loader-Einstiege in dieser Notiz stammen aus der Zeit vor der Foundation. Kommentare in den Code-Beispielen sind nur Erklärung: im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`). Maßgeblich sind `docs/CONVENTIONS.md` und `docs/FOUNDATION.md` (Kasten „Abweichungen von docs/api“). Die Minecraft-/Loader-APIs hier stimmen weiterhin.
>
> **Besitz (FOUNDATION §11 `server/ServerRules`):** Sichtanker, Chunk-Tickets und die Mixins aus Abschnitt 4 gehören server, nicht drone; `QuadcopterEntity#tick` fasst keine Tickets an. server reagiert auf `QuadcopterEntity.PILOT_CHANGED` und `ServerBus.TICK_END`.
>
> **Speziell in dieser Notiz:** `QuadcopterEntity` synchronisiert `DroneBuild` über den eigenen Entity-Data-Serializer `CoreComponents.DRONE_BUILD_SERIALIZER` (auf beiden Loadern registriert), nicht über einen gepackten `LONG` wie in 2.3/2.5 skizziert. Ticket-Typen kommen aus `PropwashRegistries.ticketType(...)` im eigenen Modulpaket, nicht aus einer Klasse `PropwashTickets` mit `Registry.register`/`DeferredRegister` (4.3). Payloads laufen über `network.PropwashNet`/`network.client.ClientNet` (1.x), die Gate-Entity ist `race.GateEntity` (`RaceModule.GATE`, 3.3), und `ExtraCollider` ersetzt das `RaceGateEntity#collectShapes`/`MAX_HALF_EXTENT` der Skizze.

Paths are relative to `.ref/` (`vanilla/`, `neoforge/` = NeoForge-patched MC, `neoforge-api/`, `fabric-api/<module>/`).
Loader jars that are not in `.ref` were checked in the Gradle cache (constant pool grep, no JDK installed):
fabric-loader 0.19.5, FML `net.neoforged.fancymodloader:loader:12.0.0` (the version the NeoForge 26.3.0.10-beta POM pulls in).

## TL;DR

- **Payloads**: a `record implements CustomPacketPayload` + `CustomPacketPayload.Type<T>` + `StreamCodec`. Register per loader.
  Fabric uses `PayloadTypeRegistry.serverboundPlay()/clientboundPlay().register(type, codec)`, then `ServerPlayNetworking/ClientPlayNetworking.registerGlobalReceiver`.
  NeoForge uses `RegisterPayloadHandlersEvent#registrar("1").playToServer/playToClient`, and client handlers go in `RegisterClientPayloadHandlersEvent`.
  On both loaders the handlers already run on the main thread. **NeoForge throws** when you send a payload the other side has not registered, so guard every broadcast with `hasChannel`/`canSend`.
- **Entity API changes**: NBT is gone from entities. Saving uses `readAdditionalSaveData(ValueInput)` / `addAdditionalSaveData(ValueOutput)`.
  Damage is `hurtServer(ServerLevel, DamageSource, float)`, and interaction is `interact(Player, InteractionHand, Vec3)`.
  `EntityType.Builder#build(ResourceKey)` needs a key. Vanilla types moved to `EntityTypes` and `EntityTypeIds`.
  `lerpTo` is gone. Clients interpolate with `InterpolationHandler` (`createInterpolationHandler()`).
- **Serializers**: `EntityDataSerializers.QUATERNION` (Quaternionfc) and `VECTOR3` exist. **There is no `OPTIONAL_UUID`.**
  Use `OPTIONAL_LIVING_ENTITY_REFERENCE` (`Optional<EntityReference<LivingEntity>>`) for the pilot.
- **Pilot authority**: override `isLocalClientAuthoritative()` and `isClientAuthoritative()` in the drone. Vanilla then stops snapping the pilot's own drone back when position packets arrive.
  The drone's `tick()` must still only call `move(MoverType.SELF, …)` when `canSimulateMovement()` is true. **NeoForge dev runs throw an `IllegalStateException` otherwise** (2.4).
- **Gate collisions**: the vanilla entity-vs-entity collision uses **one AABB per entity** (`EntityGetter#getEntityCollisions`).
  Vanilla dragon parts are hard-coded to `EnderDragon`. NeoForge generalizes them (`PartEntity`), Fabric has nothing.
  → Give the gate one non-colliding entity that exposes its own `VoxelShape`s, and clip drone movement against them in `QuadcopterEntity#move` (no mixin needed).
- **View anchoring**: `ChunkMap#updateChunkTracking` runs **every tick** for every player and centers the chunk view on `player.chunkPosition()`.
  Three `@ModifyExpressionValue` mixins handle the rest: `updateChunkTracking`, `ChunkMap$TrackedEntity#updatePlayer` and `PlayerChunkSender#sendNextChunks`.
  Add a custom `TicketType` refreshed by the drone (the ender pearl pattern) and the view follows the drone without moving the body.
  **Blocker/pitfall:** on the client the pilot's body chunk gets unloaded. Players are always-ticking, so the body keeps ticking, and `LivingEntity#travel` → `travelInAir` then sinks it at 0.1 blocks/tick. The body must be frozen while piloting.

---

## 1. Custom payloads

### 1.1 Vanilla building blocks
| API | Signature (copied) | Path |
|---|---|---|
| payload | `CustomPacketPayload.Type<? extends CustomPacketPayload> type();` | `vanilla/net/minecraft/network/protocol/common/custom/CustomPacketPayload.java` |
| type | `record Type<T extends CustomPacketPayload>(Identifier id)` (same file); `createType(String)` is **minecraft namespace only** | same |
| id | `Identifier.fromNamespaceAndPath(String namespace, String path)` (`ResourceLocation` is now `Identifier`) | `vanilla/net/minecraft/resources/Identifier.java:40` |
| codec | `StreamCodec.composite(codec1, getter1, …, constructor)`, with overloads for 1–14 fields | `vanilla/net/minecraft/network/codec/StreamCodec.java:118…673` |
| prims | `ByteBufCodecs.BOOL, BYTE, SHORT, VAR_INT, INT, VAR_LONG, LONG, FLOAT, DOUBLE, STRING_UTF8, VECTOR3F (Vector3fc), QUATERNIONF (Quaternionfc), COMPOUND_TAG`; also `optional(..)`, `list()`, `collection(..)`, `idMapper(..)`, `fromCodecWithRegistries(..)` | `vanilla/net/minecraft/network/codec/ByteBufCodecs.java` |
| misc | `Vec3.STREAM_CODEC`, `Vec3.LP_STREAM_CODEC` (lossy/packed), `UUIDUtil.STREAM_CODEC`, `Identifier.STREAM_CODEC` | `vanilla/net/minecraft/world/phys/Vec3.java:25,36`, `vanilla/net/minecraft/core/UUIDUtil.java:42` |
| wire | `record ClientboundCustomPayloadPacket(CustomPacketPayload payload)` (max 1 048 576 B), `record ServerboundCustomPayloadPacket(...)` (**max 32 767 B**) | `vanilla/net/minecraft/network/protocol/common/` |

Vanilla example: `BrandPayload` uses `CustomPacketPayload.codec(BrandPayload::write, BrandPayload::new)` (`vanilla/net/minecraft/network/protocol/common/custom/BrandPayload.java`).

```java
// common: de.lardtom.propwash.net.DroneStateC2S
public record DroneStateC2S(int entityId, Vec3 pos, Vec3 vel, Quaternionfc rot, float throttle) implements CustomPacketPayload {
    public static final Type<DroneStateC2S> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "flight_state"));
    public static final StreamCodec<ByteBuf, DroneStateC2S> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, DroneStateC2S::entityId,
        Vec3.STREAM_CODEC, DroneStateC2S::pos,
        Vec3.STREAM_CODEC, DroneStateC2S::vel,
        ByteBufCodecs.QUATERNIONF, DroneStateC2S::rot,
        ByteBufCodecs.FLOAT, DroneStateC2S::throttle,
        DroneStateC2S::new);
    @Override public Type<DroneStateC2S> type() { return TYPE; }
}
```
A `StreamCodec<ByteBuf, T>` fits wherever `StreamCodec<? super RegistryFriendlyByteBuf, T>` is expected. Use `RegistryFriendlyByteBuf` codecs (`ByteBufCodecs.fromCodecWithRegistries`, `ItemStack.STREAM_CODEC`) only when you need registries.

### 1.2 Fabric (`fabric-api/fabric-networking-api-v1/net/fabricmc/fabric/api/...`)
- `networking/v1/PayloadTypeRegistry.java`: `static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay()` / `clientboundPlay()`, plus
  `<T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec)`
  and `registerLarge(type, codec, int maxPacketSize)`.
- `networking/v1/ServerPlayNetworking.java`: `static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler)`,
  `void receive(T payload, Context context)` with `context.server()`, `context.player()` (ServerPlayer), `context.responseSender()`.
  Also `static void send(ServerPlayer player, CustomPacketPayload payload)` and `static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type)`.
  Javadoc: *"This is called on the server thread, and can safely manipulate the world."*
- `client/networking/v1/ClientPlayNetworking.java`: `registerGlobalReceiver(type, handler)`, `static void send(CustomPacketPayload payload)`, `canSend(type)`, `Context#client()/player()`.
  Javadoc: *"called on the render thread"*.
- `networking/v1/PlayerLookup.java`: `tracking(Entity)`, `tracking(ServerLevel, ChunkPos)`, `around(ServerLevel, Vec3, double)`, `level(ServerLevel)`, `all(MinecraftServer)`.
- Real call site: `fabric-api/fabric-client-gametest-api-v1/.../FabricClientGameTestImpl.java:40` (`PayloadTypeRegistry.clientboundPlay().register(...)`).

```java
// fabric: Propwash#onInitialize (register BOTH directions here, even for S2C; clients have no separate registry)
PayloadTypeRegistry.serverboundPlay().register(DroneStateC2S.TYPE, DroneStateC2S.CODEC);
PayloadTypeRegistry.clientboundPlay().register(RaceEventS2C.TYPE, RaceEventS2C.CODEC);
ServerPlayNetworking.registerGlobalReceiver(DroneStateC2S.TYPE, (p, ctx) -> PropwashNet.onDroneState(ctx.player(), p));
// fabric client entrypoint
ClientPlayNetworking.registerGlobalReceiver(RaceEventS2C.TYPE, (p, ctx) -> ClientNet.onRaceEvent(p));
```

### 1.3 NeoForge (`neoforge-api/net/neoforged/neoforge/network/...`)
- `event/RegisterPayloadHandlersEvent.java` (mod bus): `public PayloadRegistrar registrar(String version)`.
- `registration/PayloadRegistrar.java`: `playToServer(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>, IPayloadHandler<T>)`,
  `playToClient(type, codec, handler)`, `playToClient(type, codec)` (handler registered later), `playBidirectional(...)`, `executesOn(HandlerThread)`, `versioned(String)`, `optional()`.
  **The default thread is `HandlerThread.MAIN`** (`registration/PayloadRegistrar.java:29`; handlers get wrapped in `handling/MainThreadPayloadHandler.java` at `PayloadRegistrar.java:224-229`; enum in `registration/HandlerThread.java`).
- `handling/IPayloadHandler.java`: `void handle(T payload, IPayloadContext context);`. `IPayloadContext`: `Player player()`, `reply(payload)`, `enqueueWork(Runnable)`, `disconnect(Component)`.
- `client/network/event/RegisterClientPayloadHandlersEvent.java` (mod bus, client only): `register(CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler)`.
- Sending: `PacketDistributor.sendToPlayer(ServerPlayer, payload, ...)`, `sendToPlayersTrackingEntity(Entity, ...)`, `sendToPlayersTrackingEntityAndSelf`, `sendToPlayersNear(ServerLevel, @Nullable ServerPlayer excluded, x, y, z, radius, ...)`, `sendToPlayersInDimension`, `sendToAllPlayers`.
  Client side: `neoforge-api/net/neoforged/neoforge/client/network/ClientPacketDistributor.java:22` `sendToServer(payload, ...)`.
- Real call site: `neoforge-api/net/neoforged/neoforge/network/NetworkInitialization.java:43` (`event.registrar("1").optional()` … `.playToClient(...)`).

```java
// neoforge: in the @Mod constructor: modBus.addListener(PropwashNeoNet::register); (client class: modBus.addListener(PropwashNeoNet::registerClient))
static void register(RegisterPayloadHandlersEvent e) {
    PayloadRegistrar r = e.registrar("1");
    r.playToServer(DroneStateC2S.TYPE, DroneStateC2S.CODEC, (p, ctx) -> PropwashNet.onDroneState((ServerPlayer) ctx.player(), p));
    r.playToClient(RaceEventS2C.TYPE, RaceEventS2C.CODEC);            // client handler below, keeps client classes off the server
}
static void registerClient(RegisterClientPayloadHandlersEvent e) {
    e.register(RaceEventS2C.TYPE, (p, ctx) -> ClientNet.onRaceEvent(p));
}
```

### 1.4 Sending from common code (no loader API needed)
Both loaders encode modded payloads inside the vanilla packet, so common code can do this:
- `serverPlayer.connection.send(new ClientboundCustomPayloadPacket(payload))` (`ServerCommonPacketListenerImpl#send(Packet<?>)`, `vanilla/net/minecraft/server/network/ServerCommonPacketListenerImpl.java:156`).
- To every tracking player: `serverLevel.getChunkSource().sendToTrackingPlayersFiltered(Entity, Packet<? super ClientGamePacketListener>, Predicate<ServerPlayer>)`,
  or use `sendToTrackingPlayers` / `sendToTrackingPlayersAndSelf` (`vanilla/net/minecraft/server/level/ServerChunkCache.java:532-540`).
  `ClientboundCustomPayloadPacket` is a `Packet<ClientCommonPacketListener>`, so it fits.
- For client→server, common code can also stay loader-free: `level().sendPacketToServer(new ServerboundCustomPayloadPacket(payload))` (`vanilla/net/minecraft/client/multiplayer/ClientLevel.java:734` → `connection.send`; `Level#sendPacketToServer` throws on the server, `world/level/Level.java:942`; vanilla boats do exactly this in `world/entity/vehicle/boat/AbstractBoat.java:242`).
  Fabric's `ClientPlayNetworking.send` builds the same `new ServerboundCustomPayloadPacket(payload)` (`fabric-networking-api-v1/net/fabricmc/fabric/impl/networking/client/ClientNetworkingImpl.java:74-79`), and NeoForge's `ClientPacketDistributor.sendToServer` goes through the same patched `send` with `NetworkRegistry.checkPacket` (`neoforge/net/minecraft/client/multiplayer/ClientCommonPacketListenerImpl.java:395`).
  If you prefer the platform service (`IPlatformHelper`): Fabric uses `ClientPlayNetworking.send`, NeoForge uses `ClientPacketDistributor.sendToServer`.
  Add a `boolean canSend(ServerPlayer, Type<?>)` there too: Fabric `ServerPlayNetworking.canSend`, NeoForge `player.connection.hasChannel(type)` (`neoforge-api/.../common/extensions/ICommonPacketListener.java:65`).

### 1.5 Pitfalls
- **NeoForge send check** (both directions: `neoforge/net/minecraft/server/network/ServerCommonPacketListenerImpl.java:194` and `neoforge/net/minecraft/client/multiplayer/ClientCommonPacketListenerImpl.java:395`; the client side throws `"Payload %s may not be sent to the server!"`, `NetworkRegistry.java:447-458`): the patched `ServerCommonPacketListenerImpl.send` calls `NetworkRegistry.checkPacket`. That throws `UnsupportedOperationException("Payload %s may not be sent to the client!")` when the client lacks the channel (`neoforge-api/.../registration/NetworkRegistry.java:425`). Fabric does not check (`ServerPlayNetworking.send` only null-checks).
  → Always filter broadcasts with `canSend`.
- NeoForge rejects the `minecraft` namespace, duplicate registration and registration after the event (`NetworkRegistry.java:160-189`).
- Clients without the mod: UNVERIFIED (the registry-sync rejection path was not traced in `.ref`): our entity types, items and blocks are registry-synced content, so vanilla clients cannot join in practice. `optional()` / `canSend` only make the channel robust. Keep all channels **non-optional** except future cosmetic ones.
- Validate every C2S payload on the server: the sender is the pilot, the entity is in the same level, all values are finite (`Double.isFinite`), and the step per tick is plausible.

---

## 2. Entities

### 2.1 Entity skeleton (`vanilla/net/minecraft/world/entity/Entity.java`)
| Member | 26.3 signature | Line |
|---|---|---|
| ctor | `public Entity(EntityType<?> type, Level level)`, which calls `defineSynchedData` and then `createInterpolationHandler()` | 309 |
| synced data | `protected abstract void defineSynchedData(SynchedEntityData.Builder entityData);` → `entityData.define(ACCESSOR, default)` | 420 |
| load/save | `protected abstract void readAdditionalSaveData(ValueInput input);` / `protected abstract void addAdditionalSaveData(ValueOutput output);` | 2268/2270 |
| damage | `public abstract boolean hurtServer(ServerLevel level, DamageSource source, float damage);`, `public boolean hurtClient(DamageSource)`, `public final boolean hurtOrSimulate(...)` | 1967/1969/1963 |
| use | `public InteractionResult interact(Player player, InteractionHand hand, Vec3 location)` (new `Vec3` param; called from `Player#interactOn`, `vanilla/.../world/entity/player/Player.java:804`) | 2317 |
| pick | `public boolean isPickable()`, `public @Nullable ItemStack getPickResult()` | 2052/3977 |
| collide | `public boolean canBeCollidedWith(@Nullable Entity other)` (the parameter is new), `public boolean canCollideWith(Entity entity)`, `public boolean isPushable()` | 2426/2422/2060 |
| attack | `public boolean isAttackable()`, `public boolean skipAttackInteraction(Entity source)` | 3067/3071 |
| move | `public void move(MoverType moverType, Vec3 delta)` | 731 |
| bbox | `protected AABB makeBoundingBox(Vec3 position)` (overridable: `monster/Shulker.java`, `Interaction.java`) | 487 |
| pairing | `public void startSeenByPlayer(ServerPlayer)` / `stopSeenByPlayer` (called after the spawn bundle, `server/level/ServerEntity.java:298-300`) | 3583/3586 |
| setters | `setPos(Vec3)` (final), `snapTo(Vec3, float, float)` (was `moveTo`), `absSnapTo(...)`, `setDeltaMovement(Vec3)` | 474/1830/1797/3845 |

`ValueInput`/`ValueOutput` (`vanilla/net/minecraft/world/level/storage/`) offer `getFloatOr(name, def)`, `getIntOr`, `getLongOr`, `getBooleanOr`, `read(name, Codec<T>) -> Optional<T>`,
`Optional<ValueInput> child(name)` / `ValueInput childOrEmpty(name)` (input side) and `putFloat/putInt/putLong/putBoolean`, `store(name, Codec<T>, T)`, `storeNullable`, `ValueOutput child(name)` (output side). `ValueInput#lookup()` gives registries.
Vanilla reference: `vehicle/VehicleEntity.java:35` (`hurtServer` of a non-living entity). Entity reference save: `EntityReference.store(ref, output, "Owner")` / `EntityReference.read(input, key)` (`world/entity/EntityReference.java:96,120`).
`TamableAnimal.java:61` uses `store`; its load at `:68` uses `EntityReference.readWithOldOwnerConversion(input, "Owner", level)` (legacy-UUID variant, `EntityReference.java:124`), which we do not need.

### 2.2 EntityType + registration
- Builder: `EntityType.Builder.of(EntityType.EntityFactory<T> factory, MobCategory category)`, `.sized(w, h)`, `.eyeHeight(f)`, `.clientTrackingRange(int clientChunkRange)` (**in chunks**, default 5), `.updateInterval(int)` (default 3), `.noUpdateInterval()`, `.dontTrackDeltas()`, `.fireImmune()`, `.noLootTable()`, `.noSummon()`, `.noSave()`.
  Build with `public EntityType<T> build(ResourceKey<EntityType<?>> name)` (`world/entity/EntityType.java:483-603`). The factory is `EntityType.EntityFactory<T>` with `@Nullable T create(final EntityType<T> entityType, final Level level)` (`EntityType.java:630-631`).
  `noUpdateInterval()` is `updateInterval(Integer.MAX_VALUE)`, which makes `hasUpdateInterval()` false (`EntityType.java:426,579`).
- Vanilla constants moved: `world/entity/EntityTypes.java` (`register(ResourceKey, Builder)` → `Registry.register(BuiltInRegistries.ENTITY_TYPE, id, builder.build(id))`, line 1240). Keys live in `world/entity/EntityTypeIds.java`.
  Example: `EntityTypes.OAK_BOAT` (`.sized(1.375F, 0.5625F).clientTrackingRange(10)`, line 722).
- Fabric: `Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type)` (`vanilla/net/minecraft/core/Registry.java`, `register(Registry<V>, ResourceKey<V>, T)`).
- NeoForge: `DeferredRegister.createEntities(modid)` → `registerEntityType(String name, EntityType.EntityFactory<E>, MobCategory, UnaryOperator<EntityType.Builder<E>>)` (`neoforge-api/.../registries/DeferredRegister.java:184,824`),
  or plain `DeferredRegister.create(Registries.ENTITY_TYPE, modid)` with `() -> builder.build(key)`.
- Pitfall: `build()` calls `Util.fetchChoiceType` (`SharedConstants.CHECK_DATA_FIXER_SCHEMA = true`, `SharedConstants.java:127`), which logs `ERROR No data fixer registered for propwash:drone` on Fabric (NeoForge downgrades it to debug and disables the rethrow with `&& false`: `neoforge/net/minecraft/util/Util.java:321-322`). This is harmless.
  The vanilla rethrow only fires when `SharedConstants.IS_RUNNING_IN_IDE` is true. Vanilla and Fabric never set it, **but NeoForge sets it in every dev run**: `IS_RUNNING_IN_IDE = !FMLEnvironment.isProduction()` (`neoforge/net/minecraft/SharedConstants.java:129`). See the `Entity#move` pitfall in 2.4.

### 2.3 Synced data (`vanilla/net/minecraft/network/syncher/EntityDataSerializers.java`)
Available: `BYTE, INT, LONG, FLOAT, STRING, COMPONENT, ITEM_STACK, BOOLEAN, BLOCK_POS, DIRECTION, POSE, VECTOR3 (Vector3fc), QUATERNION (Quaternionfc), OPTIONAL_LIVING_ENTITY_REFERENCE, OPTIONAL_GLOBAL_POS, OPTIONAL_UNSIGNED_INT, DYE_COLOR, ...`.
**No `OPTIONAL_UUID`, no `COMPOUND_TAG`.** Accessor: `SynchedEntityData.defineId(Class<? extends SyncedDataHolder>, EntityDataSerializer<T>)`. Change listener: `onSyncedDataUpdated(EntityDataAccessor<?>)`.
Custom serializers need registration on both loaders: Fabric `FabricEntityDataRegistry.register(Identifier, EntityDataSerializer<?>)` (`fabric-api/fabric-object-builder-api-v1/.../entity/FabricEntityDataRegistry.java:44`), NeoForge registry `NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS`.
→ Avoid custom serializers. Pack `DroneBuild` into a `LONG` (enum ordinals × 8 bits) or a `STRING` id.

### 2.4 Interpolation and pilot authority
- `lerpTo` no longer exists. The client calls `moveOrInterpolateTo(PositionPath, yRot, xRot)` (`Entity.java:2598-2616`), which feeds `InterpolationHandler#interpolateTo(...)`.
  If that returns false, the position is set directly. `Entity#commonTick` calls `getInterpolation().interpolate()` on the client every tick (`Entity.java:517-528`).
- Choose via `protected InterpolationHandler createInterpolationHandler()` (default `InterpolationHandler.NO_OP` = snap). Vanilla uses `LinearInterpolationHandler.create(this, 3)` in `vehicle/boat/AbstractBoat.java:202` and `SteppedInterpolationHandler.create(this)` in `LivingEntity.java:3274`.
- Roll/pitch/yaw of the drone: entity `yRot/xRot` are sent as bytes (~1.4°) and have no roll. Sync the attitude through a `QUATERNION` data accessor and slerp on the client in the renderer.
- Authority (`Entity.java:3693-3720`):
  ```java
  public final boolean isLocalInstanceAuthoritative() { return level.isClientSide() ? isLocalClientAuthoritative() : !isClientAuthoritative(); }
  protected boolean isLocalClientAuthoritative()   // override: true on the pilot's client
  public boolean isClientAuthoritative()           // override: true on the server while piloted
  ```
  `ClientPacketListener#handleMoveEntity` / `handleEntityPositionSync` skip `moveOrInterpolateTo` for authoritative entities (`vanilla/net/minecraft/client/multiplayer/ClientPacketListener.java:650,726`).
  `canSimulateMovement()` then returns false on the server (`MoveSimulationType.AUTHORITATIVE_SIDE`, `Entity.java:3707-3716`). **Vanilla does not skip `tick()` for you.** The drone's own tick has to gate physics the way the boat does:
  `if (isLocalInstanceAuthoritative()) { …; move(MoverType.SELF, getDeltaMovement()); } else { setDeltaMovement(Vec3.ZERO); }` (`world/entity/vehicle/boat/AbstractBoat.java:234-248`). The server then only applies the states the client sends (`setPos`/`absSnapTo` from the C2S handler, not `move`).
  Vanilla's `ServerboundMoveVehiclePacket` path only covers ridden vehicles. An unridden drone needs our own C2S payload.
- **NeoForge dev-run crash:** `Entity#move` starts with `if (SharedConstants.IS_RUNNING_IN_IDE && !moverType.isServerAndClientSimulated() && !canSimulateMovement()) throw new IllegalStateException("Attempted to move entity on a logical side not permitted to simulate movement")` (`Entity.java:731-736`; `MoverType.SELF` is the only type that is not "server and client simulated", `world/entity/MoverType.java:10-11`).
  NeoForge sets that flag in dev (see 2.2), so a server-side or non-pilot-client `move(SELF, …)` on a piloted drone throws in `runClient`/`runServer`, while it runs silently on Fabric and in production. Always gate on `canSimulateMovement()`.
- Forcing a reset on the pilot: `handleTeleportEntity` does **not** skip authoritative entities (`ClientPacketListener.java:671-690`), unlike the two handlers above.
  But a plain server `teleportTo` sends `ClientboundTeleportEntityPacket` to trackers only when `!type.hasUpdateInterval() || getMoveSimulationType() == SERVER_AND_CLIENT`, or to riding passengers (`Entity#sendTeleportTransitionToPlayers`, `Entity.java:3235-3259`). Otherwise the change travels as a normal `ClientboundEntityPositionSyncPacket`, and the pilot ignores that.
  → For a race-start or rubber-band reset, send it explicitly: `pilot.connection.send(ClientboundTeleportEntityPacket.teleport(drone.getId(), PositionMoveRotation.of(drone), Set.of(), drone.onGround()))` (`network/protocol/game/ClientboundTeleportEntityPacket.java:26`, `world/entity/PositionMoveRotation.java:25`), or use our own S2C payload.

### 2.5 Tracking range / update rate (`vanilla/net/minecraft/server/level/ChunkMap.java`)
- `addEntity`: `range = type.clientTrackingRange() * 16`; a range of 0 means the entity is never tracked. The update interval is `type.hasUpdateInterval() ? UpdateInterval.periodic(type.updateInterval()) : UpdateInterval.NEVER` (line 1139-1148; `UpdateInterval` lives in `world/entity/UpdateInterval.java`).
- `TrackedEntity#updatePlayer`: `visibleRange = min(getEffectiveRange(), playerViewDistance * 16)`. Distance is **horizontal only** and measured from `player.position()`. The entity must also pass `entity.broadcastToPlayer(player)`, and its chunk must be in the player's tracking view (line 1378-1400).
  The effective range is scaled by `server.getScaledTrackingDistance` (`entity-broadcast-range-percentage`, `server/dedicated/DedicatedServer.java:733`; integrated server returns it unscaled, `server/MinecraftServer.java:1284`).
- `ChunkMap#tick` only calls `serverEntity.sendChanges()` when the entity changed section, has `needsSync`, or its chunk is **entity-ticking** (`distanceManager.inEntityTickingRange`, `ChunkMap.java:1201`). A drone in a merely loaded chunk is neither ticked nor broadcast, which is one more reason for the simulation ticket in 4.3.
- `ServerEntity#sendChanges` (`server/level/ServerEntity.java:96ff`) syncs when `needsSync || updateInterval.test(tickCount) || entityData.isDirty()` (`:130`). **Dirty synced data triggers an immediate send, regardless of the interval.**
  Positions go out as a delta `ClientboundMoveEntityPacket` or a full `ClientboundEntityPositionSyncPacket` carrying a `PositionPath` from `InterpolationTracker`. A position is forced every 60 ticks and a full sync every 400 (`FORCED_POS_UPDATE_PERIOD`/`FORCED_TELEPORT_PERIOD`, `:60-61`), and always a full sync while `Entity#getRequiresPrecisePosition()` is set (`Entity.java:378-383`, `ServerEntity#createMovePacket`).
- Initial state: `ServerEntity#addPairing` → `sendPairingData` puts the add-entity packet **and the non-default synced data** into one `ClientboundBundlePacket` (`ServerEntity.java:296-312`). Synced data therefore covers spawn state, and no extra spawn buffer is needed.
  NeoForge also has `IEntityWithComplexSpawn#writeSpawnData/readSpawnData(RegistryFriendlyByteBuf)` (`neoforge-api/net/neoforged/neoforge/entity/IEntityWithComplexSpawn.java:21,29`). Fabric has no equivalent in `.ref`, so do not use it.
- Drone: `.clientTrackingRange(16).updateInterval(1)`. Gate: `.clientTrackingRange(16).noUpdateInterval().dontTrackDeltas()` (the same as `END_CRYSTAL`, `EntityTypes.java:420`).

```java
public class QuadcopterEntity extends Entity {
    static final EntityDataAccessor<Quaternionfc> ATTITUDE = SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.QUATERNION);
    static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> PILOT = SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);
    static final EntityDataAccessor<Long> BUILD = SynchedEntityData.defineId(QuadcopterEntity.class, EntityDataSerializers.LONG);
    public QuadcopterEntity(EntityType<? extends QuadcopterEntity> t, Level l) { super(t, l); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(ATTITUDE, new Quaternionf()); b.define(PILOT, Optional.empty()); b.define(BUILD, 0L); }
    @Override protected void readAdditionalSaveData(ValueInput in) { entityData.set(BUILD, in.getLongOr("Build", 0L)); }
    @Override protected void addAdditionalSaveData(ValueOutput out) { out.putLong("Build", entityData.get(BUILD)); }
    @Override public boolean hurtServer(ServerLevel lvl, DamageSource src, float dmg) { if (isInvulnerableToBase(src)) return false; /* drop item */ discard(); return true; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public InteractionResult interact(Player p, InteractionHand h, Vec3 at) { return InteractionResult.SUCCESS; }
    @Override protected InterpolationHandler createInterpolationHandler() { return LinearInterpolationHandler.create(this, 2); }
    @Override protected boolean isLocalClientAuthoritative() { return pilotUuid().filter(PilotClientState::isLocalPlayer).isPresent(); } // static UUID set by client code
    @Override public boolean isClientAuthoritative() { return pilotUuid().isPresent(); }
    Optional<UUID> pilotUuid() { return entityData.get(PILOT).map(EntityReference::getUUID); }
}
```

---

## 3. Race gates: separate collision boxes

### 3.1 How entity collisions reach `Entity#move`
- `Entity#move` → private `collide(Vec3)` (`Entity.java:1174`) → `level().getEntityCollisions(this, aabb.expandTowards(movement)...)` → `collideBoundingBox(...)`.
- `EntityGetter#getEntityCollisions(@Nullable Entity source, AABB testArea)` (`vanilla/net/minecraft/world/level/EntityGetter.java:54`) filters with `EntitySelector.NO_SPECTATORS.and(source::canCollideWith)` and adds **`Shapes.create(entity.getBoundingBox())` per entity: one box, no custom shape**.
  `canCollideWith(e)` = `e.canBeCollidedWith(this) && !isPassengerOfSameVehicle(e)`.
- Vanilla collidable entities: `AbstractBoat` (true), `Shulker` (alive), `HappyGhast` (conditional on `other`). The `other` parameter lets a gate collide **only with drones**: `return other instanceof QuadcopterEntity;`.
- Public static helpers: `Entity.collideBoundingBox(Entity source, Vec3 movement, AABB box, Level level, List<VoxelShape> entityColliders)` (`Entity.java:1226`),
  `Shapes.collide(Direction.Axis, AABB moving, Iterable<VoxelShape>, double distance)` (`world/phys/shapes/Shapes.java:229`), and `Direction.axisStepOrder(Vec3)` (`core/Direction.java:379`).
  `collideWithShapes` is private.

### 3.2 Multipart state
- Vanilla: `EnderDragonPart extends Entity` (`world/entity/boss/enderdragon/EnderDragonPart.java`). It is not saved, `getAddEntityPacket` throws, and `hurtServer` forwards to its parent. It does **not** override `canBeCollidedWith`, so parts only matter for picking and damage.
  The special cases are hard-wired to `EnderDragon`:
  - `Level#getEntities(except, bb, pred)` iterates `dragonParts()` (`world/level/Level.java:696`).
  - `ServerLevel` fills `dragonParts` only for `EnderDragon` (`server/level/ServerLevel.java:2014`).
  - `ChunkMap#addEntity` skips parts (`:1140`).
  - Client ids are `parentId+1..n` via `EnderDragon#recreateFromPacket` (`EnderDragon.java:871`).
- NeoForge: `EnderDragonPart extends net.neoforged.neoforge.entity.PartEntity<EnderDragon>` (`neoforge/.../EnderDragonPart.java`). `PartEntity(T parent)` and `getParent()` (`neoforge-api/.../entity/PartEntity.java`), plus `IEntityExtension#isMultipartEntity()` / `getParts()` (`neoforge-api/.../common/extensions/IEntityExtension.java:137,155`).
  The patched `ServerLevel` and `Level#getEntities` handle any `PartEntity`.
- Fabric: **no multipart API** (nothing in fabric-api). A Fabric version would need mixins on `ServerLevel`, `ClientLevel`, `Level#getEntities` and `ChunkMap`. → **Do not use part entities.**

### 3.3 Recommended pattern: one gate entity with its own shapes, clipped in the drone
The gate entity overrides `makeBoundingBox(Vec3)` so its AABB covers the whole frame (for picking and rendering) and sets `canBeCollidedWith(other) = false`.
It exposes world-space post and bar boxes. Rotated gates are approximated by several small axis-aligned boxes. The drone clips its movement against those boxes before vanilla movement runs.
This works on both loaders and needs no mixin.
```java
// QuadcopterEntity
@Override public void move(MoverType type, Vec3 delta) {
    if (!noPhysics) delta = GateCollision.clip(this, delta);
    super.move(type, delta);            // blocks, world border and vanilla entities as usual
}
// GateCollision (common)
static Vec3 clip(Entity mover, Vec3 delta) {
    AABB box = mover.getBoundingBox(), swept = box.expandTowards(delta);
    List<VoxelShape> shapes = new ArrayList<>();
    for (RaceGateEntity g : mover.level().getEntitiesOfClass(RaceGateEntity.class, swept.inflate(RaceGateEntity.MAX_HALF_EXTENT)))
        g.collectShapes(swept, shapes);          // Shapes.create(minX,..,maxZ) per post/bar
    if (shapes.isEmpty()) return delta;
    Vec3 out = Vec3.ZERO;
    for (Direction.Axis axis : Direction.axisStepOrder(delta)) {
        double d = delta.get(axis);
        if (d != 0.0) out = out.with(axis, Shapes.collide(axis, box.move(out), shapes, d));
    }
    return out;   // out != delta → crash/bounce logic in the flight model
}
```
If players should bump into posts too, use a common mixin instead: a MixinExtras `@ModifyExpressionValue` on the `Level;getEntityCollisions` INVOKE inside `Entity#collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;` that appends the gate shapes (UNVERIFIED: INVOKE owner `Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;` at `Entity.java:1176`, where the method is an `EntityGetter` default; `collide` is unchanged in the NeoForge patch). MixinExtras is bundled by both loaders (fabric-loader 0.19.5 jar-in-jar `mixinextras-fabric-0.5.5`, NeoForge POM `mixinextras-neoforge:0.5.4`), and `common/build.gradle:22` already has it (`compileOnly(annotationProcessor('io.github.llamalad7:mixinextras-common:0.5.3'))`).

### 3.4 Pitfalls
- Entity lookups are indexed by section of the entity **position**. The section pre-filter only widens the query box by 2 blocks on X/Z, 4 blocks down and **0 blocks up** (`world/level/entity/EntitySectionStorage.java:38-43`); after that each entity's bounding box is intersected. A wide gate queried near its far post, or a gate whose position sits more than a section above the query, can be missed.
  → Inflate queries by the gate's max half-extent, or place the entity position at the gate center.
- Collision clipping does not tunnel: `Shapes.collide` sweeps the full `distance` per axis. The gate *pass* check (drone through the opening) is separate: do a segment/plane test with the previous and current position.
- `canBeCollidedWith` = true on the whole gate AABB would block the opening. Keep it false.

---

## 4. Unlimited-range piloting: anchoring the server view at the drone

### 4.1 Vanilla flow (`vanilla/net/minecraft/server/level/ChunkMap.java`, `server/network/PlayerChunkSender.java`)
| Method | What it uses | Line |
|---|---|---|
| `protected void tick()` | calls `updateChunkTracking(player)` for **all players every tick**, then updates entity tracking for entities whose section changed | 1180 |
| `private void updateChunkTracking(ServerPlayer player)` | `ChunkPos chunkPos = player.chunkPosition();` + `getPlayerViewDistance(player)` → `applyChunkTrackingView(player, ChunkTrackingView.of(chunkPos, dist))` | 1096 |
| `private void applyChunkTrackingView(ServerPlayer, ChunkTrackingView next)` | sends `ClientboundSetChunkCacheCenterPacket` when the center changes; `ChunkTrackingView.difference(...)` → `markChunkPendingToSend` / `dropChunk` | 1108 |
| `public void move(ServerPlayer player)` | `trackedEntity.updatePlayer(player)` for **all** tracked entities; `SectionPos.of(player)` → `distanceManager.removePlayer/addPlayer` (player loading tickets) | 1060 |
| `private void updatePlayerStatus(ServerPlayer, boolean added)` / `private void updatePlayerPos(ServerPlayer)` | join/leave; `player.setLastSectionPos(SectionPos.of(player))` | 1032/1055 |
| `private int getPlayerViewDistance(ServerPlayer)` | `Mth.clamp(player.requestedViewDistance(), 2, serverViewDistance)` | 816 |
| `ChunkMap$TrackedEntity#updatePlayer(ServerPlayer)` | `player.position().subtract(entity.position())`, horizontal distance ≤ min(range, viewDist*16) && `isChunkTracked` | 1378 |
| `public boolean isChunkTracked(ServerPlayer, int, int)` | `player.getChunkTrackingView().contains(x,z) && !chunkSender.isPending(..)` | 226 |
| `PlayerChunkSender#sendNextChunks(ServerPlayer)` | batch sorted by distance to `player.chunkPosition()`, called from `MinecraftServer.java:1128` | 50 |
| `ChunkTrackingView` | interface; `of(ChunkPos, int)` → `record Positioned(ChunkPos center, int viewDistance)`; `difference` only diffs two `Positioned` views **whose squares intersect** (`squareIntersects`). Any other case (other view type, or a jump further than the view square) gets a **full drop + resend** | `server/level/ChunkTrackingView.java:23-48,80,98` |

`ChunkPos` is now a record: `x()`, `z()`, `pack()`, `ChunkPos.containing(BlockPos)` (`world/level/ChunkPos.java`).
NeoForge patches: `getVisibleChunkIfPresent` is made public, and watch/unwatch events fire in `markChunkPendingToSend`/`dropChunk`. The method bodies above are otherwise unchanged (`diff vanilla neoforge ChunkMap.java`).

**Do not use `ServerPlayer#setCamera(Entity)`** (`ServerPlayer.java:1957`, tick at `:601`). It teleports the *body* to the camera every tick (`absSnapTo` + `getChunkSource().move(this)`), and sneaking (`wantsToStopRiding`) exits.

### 4.2 Mixins (common, MixinExtras)
UNVERIFIED: the INVOKE owners. `.ref` only holds decompiled source, so check them in a bytecode viewer. javac emits the receiver's static type (`ServerPlayer`), and there is exactly one `player.chunkPosition()` call in `updateChunkTracking` (`ChunkMap.java:1097`), one `player.position()` in `TrackedEntity#updatePlayer` (`:1380`) and one `player.chunkPosition()` in `sendNextChunks` (`PlayerChunkSender.java:58`). All three bodies are identical in the NeoForge patch.
```java
@Mixin(ChunkMap.class)
abstract class ChunkMapAnchorMixin {
    @ModifyExpressionValue(method = "updateChunkTracking",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos propwash$center(ChunkPos original, @Local(argsOnly = true) ServerPlayer player) {
        ViewAnchor a = ViewAnchors.get(player);          // server-side map UUID -> drone, same level only
        return a != null ? a.chunkPos() : original;
    }
}
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")   // private inner class
abstract class TrackedEntityAnchorMixin {
    @ModifyExpressionValue(method = "updatePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 propwash$pos(Vec3 original, @Local(argsOnly = true) ServerPlayer player) {
        ViewAnchor a = ViewAnchors.get(player); return a != null ? a.position() : original;
    }   // this.entity.position() has owner Entity, so it is not matched
}
@Mixin(PlayerChunkSender.class)   // optional: send the chunks nearest the drone first
abstract class PlayerChunkSenderMixin {
    @ModifyExpressionValue(method = "sendNextChunks",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos propwash$sort(ChunkPos original, @Local(argsOnly = true) ServerPlayer player) { /* same as above */ }
}
```
Leave `move`, `updatePlayerPos` and `DistanceManager` alone. The body keeps its own player tickets, so its chunk stays loaded on the server. The drone loads its surroundings with its own ticket (4.3).
When the anchor is set or cleared, or the drone changes section, call `serverLevel.getChunkSource().move(pilot)` (`ServerChunkCache.java:511` → `ChunkMap#move`). This re-evaluates `updatePlayer` for all tracked entities; vanilla only does that when the *player's* section changes. The chunk view recenters automatically on the next `ChunkMap#tick`.

### 4.3 Chunk tickets (`vanilla/net/minecraft/server/level/TicketType.java`, `world/level/TicketStorage.java`)
- `public record TicketType(long timeout, @TicketType.Flags int flags)` with `FLAG_PERSIST=1, FLAG_LOADING=2, FLAG_SIMULATION=4, FLAG_KEEP_DIMENSION_ACTIVE=8, FLAG_CAN_EXPIRE_IF_UNLOADED=16`. `NO_TIMEOUT = 0`.
  Registered into `BuiltInRegistries.TICKET_TYPE` / `Registries.TICKET_TYPE`. **There is no longer a generic `TicketType<T>` or a comparator.**
  Vanilla examples: `ENDER_PEARL = ("ender_pearl", 40L, 14)`, `DRAGON = (0L, 6)`, `PLAYER_LOADING = (0L, 2)`.
- NeoForge patch: `record TicketType(long timeout, int flags, boolean forceNaturalSpawning)` plus the constructor `TicketType(long, int)` (`neoforge/net/minecraft/server/level/TicketType.java:10-14`). A common `new TicketType(40L, flags)` has the same descriptor, so it is binary compatible.
  Real NeoForge registration call site: `DeferredRegister.create(Registries.TICKET_TYPE, MOD_ID)` + `TICKET_TYPES.register("entity", () -> new TicketType(0L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION))` (`neoforge-api/net/neoforged/neoforge/common/NeoForgeMod.java:407-412`).
  Fabric/common: `Registry.register(Registry<V>, Identifier, T)` (`vanilla/net/minecraft/core/Registry.java:111`) on `BuiltInRegistries.TICKET_TYPE` (`core/registries/BuiltInRegistries.java:338`).
- `ServerChunkCache` (`vanilla/.../server/level/ServerChunkCache.java:473-503`):
  `void addTicketWithRadius(TicketType type, ChunkPos pos, int radius)`, `removeTicketWithRadius(...)`, `addTicket(Ticket, ChunkPos)`,
  `CompletableFuture<?> addTicketAndLoadWithRadius(TicketType, ChunkPos, int)` (throws if the type cannot load or can expire), `boolean updateChunkForced(ChunkPos, boolean)`.
  The removed `addRegionTicket` is replaced by `addTicketWithRadius`.
- Level math: `addTicketWithRadius` creates `new Ticket(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius)` (`world/level/TicketStorage.java:138-141`) with FULL=33, BLOCK_TICKING=32, ENTITY_TICKING=31 (`server/level/ChunkLevel.java:10-12`, `byStatus(FullChunkStatus)` at `:48-55`).
  → radius r: FULL within r, entity-ticking within r-2 (simulation flag required).
- Re-adding the same type and level resets `ticksLeft` (`TicketStorage.java:147-156`, identity compare on the type). Timeouts count down in `purgeStaleTickets`.
- Vanilla pattern to copy: `ThrownEnderpearl#tick` (`world/entity/projectile/throwableitemprojectile/ThrownEnderpearl.java:161ff`, field `ticketTimer` at `:34`) refreshes when the ticket timer runs out or the chunk changes (`:178-183`).
  It does that via `serverPlayer.registerAndUpdateEnderPearlTicket(this)` → `static long ServerPlayer.placeEnderPearlTicket(ServerLevel, ChunkPos)` (`ServerPlayer.java:2285,2296`: `addTicketWithRadius(TicketType.ENDER_PEARL, chunk, 2)`, returns the timeout).
- `TicketType` is a **record**, so `equals` compares values (`TicketStorage` checks `ticket.getType().equals(TicketType.FORCED)`, `:173`). Same-ticket refresh uses `==` (`:181`), and registries are identity maps (`core/MappedRegistry.java:36,39`). Two distinct instances with equal values are therefore fine, but never reuse `(0L, 15)` (= FORCED).
- NeoForge `TicketController` (`neoforge-api/.../common/world/chunk/TicketController.java`, `RegisterTicketControllersEvent`) is for persistent *forced* chunks. It is not needed here.

```java
public final class PropwashTickets {   // common
    public static final TicketType DRONE      = new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION);
    public static final TicketType DRONE_VIEW = new TicketType(40L, TicketType.FLAG_LOADING);
}
// Fabric onInitialize: Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "drone"), PropwashTickets.DRONE); (same for drone_view)
// NeoForge: DeferredRegister.create(Registries.TICKET_TYPE, MOD_ID).register("drone", () -> PropwashTickets.DRONE);
// QuadcopterEntity#tick (server, while piloted):
if (level() instanceof ServerLevel sl && pilotUuid().isPresent()) {
    ChunkPos cp = chunkPosition();
    if (--ticketTimer <= 0 || !cp.equals(lastTicketChunk)) {
        sl.getChunkSource().addTicketWithRadius(PropwashTickets.DRONE, cp, 2);            // drone chunk entity-ticking
        sl.getChunkSource().addTicketWithRadius(PropwashTickets.DRONE_VIEW, cp, viewDist); // FULL chunks → sendable
        ticketTimer = 20; lastTicketChunk = cp;                                             // old tickets expire after 40 ticks
    }
}
```

### 4.4 Pitfalls
- **The pilot body sinks on the client (blocker for the input/client area).** With the view centered on the drone, the body's chunk leaves the client cache (`ClientChunkCache` storage radius = view + 3 around the cache center, `client/multiplayer/ClientChunkCache.java:125-153`).
  The body keeps ticking anyway, because `Player#isAlwaysTicking()` returns true (`world/entity/player/Player.java:1890`) and the client's `TransientEntitySectionManager` never drops always-ticking entities from `tickingEntities` (`world/level/entity/TransientEntitySectionManager.java:34,45,62`).
  `LivingEntity#travel(Vec3)` (`:2399`) → private `travelInAir(Vec3)` (`:2437`) then sets `movementY = -0.1` when `!hasChunkAt(posBelow)` on the client, **ignoring noGravity** (`world/entity/LivingEntity.java:2449-2453`; unchanged in the NeoForge patch). The LocalPlayer is client-authoritative, so the server accepts the sinking.
  → While piloting, cancel `LivingEntity#travel(Vec3)` for the local player (a mixin, since `travelInAir` is private), or cancel `LocalPlayer#aiStep` movement.
- `ChunkTrackingView` must stay `Positioned`. A custom union view triggers a full drop and resend on every change, and the client cache cannot hold two centers anyway.
- The anchor must be in the **same level** (`applyChunkTrackingView` checks `player.level() == this.level`). Clear it on dimension change, death and disconnect.
- Server broadcasts by distance still use the body: `PlayerList#broadcast(except, x, y, z, radius, dim, packet)` (sounds) and `ServerLevel#sendParticles`. Play drone and gate sounds on the client at the drone.
- `ClientLevel#shouldTickDeath` uses the local player's chunk (`client/multiplayer/ClientLevel.java:469`). This is cosmetic.
- The client ticks an entity only if its chunk is loaded on the client (`tickingEntities`). This is fine because the drone chunk is the view center.
- **The drone must never enter a chunk that is not FULL on the server.** A server entity that moves into a section whose chunk is not loaded gets `Visibility.HIDDEN` (`world/level/entity/Visibility.java:30`, default for unknown chunks `PersistentEntitySectionManager.java:48`); `Callback#updateStatus` then calls `stopTracking` (`:411-412`), which removes the drone from every client, including the pilot's (the FPV session ends). Propwash's `DronePiloting` therefore rejects movement packets into unloaded chunks (`level.isLoaded`) and resets the drone. At high speed this only stays invisible to the player if the chunks ahead are already FULL: the server module places a loading-only lookahead ticket along the drone's estimated velocity (`FOUNDATION.md` §11 `ServerRules`).
- Custom tickets (`addTicketWithRadius`) are **not** throttled like player tickets (`DistanceManager` runs player loading through a `ThrottlingChunkTaskDispatcher` with 4 chunks in flight, `DistanceManager.java:54`); all their chunks go straight to the worldgen queue, ordered by ticket level (lower = sooner). A ticket of radius r gives its centre level 33 − r, so the lookahead ticket centre gets the same priority as the view ticket centre.

---

## 5. Gamemode, permissions, config
- Gamemode: `Player#isSpectator()`, `isCreative()`, `abstract @Nullable GameType gameMode()` (`world/entity/player/Player.java:1630-1647`), `ServerPlayer#gameMode()` (`server/level/ServerPlayer.java:1810`), `getAbilities().instabuild`.
- Permissions (new in 26.x, `vanilla/net/minecraft/server/permissions/`): `PermissionSet#hasPermission(Permission)`, `ServerPlayer#permissions()` (= `server.getProfilePermissions(nameAndId())`, `:1927`), `Permissions.COMMANDS_GAMEMASTER` (level 2), `COMMANDS_ADMIN`, `COMMANDS_OWNER`.
  Call site: `ServerGamePacketListenerImpl.java:812` `if (!this.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))`.
  Commands: `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` (`commands/Commands.java:167,545`). `GameModeCommand` uses the same pattern with its own constant `PERMISSION_CHECK = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)` (`server/commands/GameModeCommand.java:19,24`). `hasPermissions(int)` is gone (no hit in `CommandSourceStack`, `Player`, `ServerPlayer`).
  Fabric also ships `fabric-permission-api-v1` (`PermissionNode`, `PermissionPredicates`), but it is not needed.
- Config dir: Fabric `FabricLoader.getInstance().getConfigDir()` (fabric-loader 0.19.5 jar). NeoForge `FMLPaths.CONFIGDIR.get()` (FML 12.0.0 jar).
  NeoForge-native specs use `ModConfigSpec.Builder` → `container.registerConfig(ModConfig.Type.SERVER, SPEC)` (`neoforge-api/.../common/NeoForgeMod.java:593`).
  SERVER configs are loaded from the **global** `FMLPaths.CONFIGDIR`. A file in `<world>/serverconfig/` only **overrides** it (`ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, FMLPaths.CONFIGDIR.get(), getServerConfigPath(server))` plus the generated readme text, `neoforge-api/.../server/ServerLifecycleHooks.java:66-99`).
  → Cross-loader: add `Path getConfigDir()` to `IPlatformHelper`, load `propwash-server.json` on server start (Fabric `ServerLifecycleEvents.SERVER_STARTING`, NeoForge `ServerStartingEvent`), and push the values clients need with an S2C payload on join (Fabric `ServerPlayConnectionEvents.JOIN`, NeoForge `PlayerEvent.PlayerLoggedInEvent`).
- Per-entity extra sync on first sight: override `Entity#startSeenByPlayer(ServerPlayer)` in common code. Alternatives are Fabric `EntityTrackingEvents.START_TRACKING` and NeoForge `PlayerEvent.StartTracking`.

## 6. Workbench (drone assembly): network touchpoints
The user asked for a workbench block on which drones are assembled. The block and menu belong to another area. What matters for networking:
- **Buttons ("assemble", "next part")**: use vanilla `AbstractContainerMenu#clickMenuButton(Player player, int buttonId)` (`world/inventory/AbstractContainerMenu.java:304`). It is sent by `MultiPlayerGameMode#handleInventoryButtonClick(int containerId, int buttonId)` (`client/multiplayer/MultiPlayerGameMode.java:501`), and `EnchantmentMenu.java:135` is a working example. No custom payload is needed, and it is validated server-side against the open menu.
- **Opening data (for example, the preset `DroneBuild` or the block pos)**: Fabric `ExtendedMenuType<T, D>(ExtendedFactory, StreamCodec<? super RegistryFriendlyByteBuf, D>)` + `ExtendedMenuProvider<D>` (`fabric-api/fabric-menu-api-v1/.../menu/v1/`).
  NeoForge `IMenuTypeExtension.create(IContainerFactory)` with `T create(int windowId, Inventory inv, RegistryFriendlyByteBuf data)` + `player.openMenu(provider, buf -> ...)` (`neoforge-api/.../common/extensions/IPlayerExtension.java:58,75`). Wrap both behind `IPlatformHelper`.
- **Live values in the open menu (selected part index, stats)**: `AbstractContainerMenu#addDataSlot(DataSlot)` / `addDataSlots(ContainerData)` (`world/inventory/AbstractContainerMenu.java:134,140`), received client-side in `setData(int id, int value)` (`:646`).
  **Pitfall:** vanilla/Fabric send them as `ClientboundContainerSetDataPacket` with `writeShort` for id **and value** (`network/protocol/game/ClientboundContainerSetDataPacket.java:24-31`). NeoForge sends a full `int` via `AdvancedContainerSetDataPayload` when the channel exists (`neoforge/net/minecraft/server/level/ServerPlayer.java:323-328`).
  Keep every slot value in `short` range, or it breaks on Fabric only. Never put a packed `DroneBuild` long in a data slot.
- **Showing the assembled drone on the bench**: `BlockEntity#getUpdatePacket()` → `ClientboundBlockEntityDataPacket.create(this)` and `getUpdateTag(HolderLookup.Provider)` (vanilla `SignBlockEntity.java:230-236`). Then `level.sendBlockUpdated(pos, old, new, flags)` to push the change.
- The finished drone leaves the bench as an item (build packed into a data component). Placing the item spawns `QuadcopterEntity` with `BUILD` set, and `addAdditionalSaveData` persists it.

## Verification
Verified 2026-09-23 against `.ref` (vanilla, NeoForge-patched MC, neoforge-api, fabric-api) plus the Gradle-cache jars for fabric-loader 0.19.5, FML 12.0.0 and the NeoForge 26.3.0.10-beta POM. Every class, signature, field, constant and line number above was grepped or read. Diffs vanilla ↔ NeoForge were checked for `ChunkMap`, `PlayerChunkSender`, `ChunkTrackingView`, `TicketType`, `Entity#collide/move/authority`, `LivingEntity#travelInAir`, `ClientPacketListener#handleMoveEntity`, `Util.fetchChoiceType` and the custom payload packets.

**Checked and correct as written (about 230 claims):** all payload API (`CustomPacketPayload`, `Type`, `StreamCodec.composite` 1–14, `ByteBufCodecs` fields/helpers, Vec3/UUID/Identifier codecs, packet size limits, `BrandPayload`).
Also Fabric `PayloadTypeRegistry`/`ServerPlayNetworking`/`ClientPlayNetworking`/`PlayerLookup` + javadoc threads; NeoForge `RegisterPayloadHandlersEvent`/`PayloadRegistrar`/`IPayloadContext`/`RegisterClientPayloadHandlersEvent`/`PacketDistributor`/`ClientPacketDistributor`/`hasChannel`/`checkPacket`/registration errors; and the full `Entity` member table.
Also the `EntityType.Builder`, `EntityTypes`/`EntityTypeIds`, `EntityDataSerializers` list (no `OPTIONAL_UUID`/`COMPOUND_TAG`), interpolation handlers, authority methods, `ChunkMap`/`TrackedEntity`/`PlayerChunkSender` flow, `setCamera`, `TicketType` flags/constants, `ServerChunkCache` ticket methods, `TicketStorage` refresh, dragon-part hardwiring, NeoForge `PartEntity`, collision helpers, permissions, config APIs, lifecycle/tracking events, workbench menu APIs and MixinExtras bundling (`mixinextras-fabric-0.5.5` jar-in-jar, POM `mixinextras-neoforge:0.5.4`, `common/build.gradle:22`).

**Corrected (16):**
1. `MainThreadPayloadHandler` wrapping lives in `PayloadRegistrar.java:224-229` / `handling/`, not `HandlerThread.java`.
2. `TamableAnimal.java:68` uses `readWithOldOwnerConversion`, not `EntityReference.read`.
3. `ValueInput#child` returns `Optional<ValueInput>` (added `childOrEmpty`).
4. The `EntityFactory` interface is at `EntityType.java:630-631`, not inside 483-620.
5. `noUpdateInterval()` yields `UpdateInterval.NEVER`, not `periodic(...)`.
6. "Vanilla never sets `IS_RUNNING_IN_IDE`" was misleading: **NeoForge sets it in every dev run** (`neoforge/net/minecraft/SharedConstants.java:129`).
7. `canSimulateMovement()` does not make vanilla skip server physics. The drone tick must gate itself (boat pattern).
8. `ServerEntity#sendChanges`: dirty synced data is sent immediately, not only on the interval, and `sendChanges` only runs for entities in entity-ticking chunks.
9. `TrackedEntity#updatePlayer` also requires `entity.broadcastToPlayer(player)`.
10. The `EntitySectionStorage` pre-filter is ±2 on X/Z, −4/+0 on Y, not "2 blocks".
11. `ChunkTrackingView.difference` also needs `squareIntersects`, otherwise it does a full resend.
12. `ChunkLevel` constants are at `:10-12` (`byStatus` at `:48-55`).
13. `ThrownEnderpearl` goes through `registerAndUpdateEnderPearlTicket` (`ServerPlayer.java:2285`).
14. The `-0.1` sink is in private `LivingEntity#travelInAir` (`:2437`), and the body only keeps ticking because `Player#isAlwaysTicking()` is true.
15. NeoForge SERVER configs load from the global config dir. `<world>/serverconfig/` only overrides.
16. `GameModeCommand` uses its own `PERMISSION_CHECK` constant.

**Added:**
- Loader-free C2S via `Level#sendPacketToServer`.
- NeoForge client-side `checkPacket`.
- NeoForge dev-run `IllegalStateException` in `Entity#move`.
- How to force-reset a client-authoritative drone (`ClientboundTeleportEntityPacket` sent directly; a plain `teleportTo` does not reach the pilot).
- The pairing bundle already carries synced data (plus NeoForge-only `IEntityWithComplexSpawn`).
- `getRequiresPrecisePosition`.
- `TicketType` record-equality note and the NeoForge `TICKET_TYPE` DeferredRegister call site (`NeoForgeMod.java:407-412`).
- `Registry.register(Registry, Identifier, T)` line.
- Container data-slot `short` truncation on Fabric vs full `int` on NeoForge.

**Open questions:**
- UNVERIFIED: the mixin INVOKE owners (`ServerPlayer;chunkPosition()`, `ServerPlayer;position()`, `Level;getEntityCollisions`) are inferred from decompiled source. No `javap` is available here, so confirm them against the compiled 26.3 jar before writing the mixins.
- UNVERIFIED: server-side behaviour of a frozen body whose chunk is outside its own (drone-centered) tracking view was not traced: specifically the `ServerGamePacketListenerImpl` movement and chunk-loaded checks while the body idles far from the drone. Needs a runtime test.
- UNVERIFIED: vanilla-client rejection through registry sync, and what a Fabric client does when it receives a payload with no registered receiver (Fabric's server `send` does not check `canSend`).

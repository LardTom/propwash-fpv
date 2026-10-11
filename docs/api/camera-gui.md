# Camera, HUD, GUI, keybinds, client hooks in MC 26.3 (Fabric + NeoForge)

> **Projekt-Layout veraltet:** Die eine Mixin-Config `propwash.mixins.json` (stattdessen je Modul `propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`), Registrierungs-Klassen (`PropwashItems`, `PropwashEntities`, `PropwashComponents`, `RegList.Ref`, …), `CommonClass`, das Paket `net` (heute `network`) und Loader-Einstiege in dieser Notiz stammen aus der Zeit vor der Foundation. Kommentare in den Code-Beispielen sind nur Erklärung: im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`). Maßgeblich sind `docs/CONVENTIONS.md` und `docs/FOUNDATION.md` (Kasten „Abweichungen von docs/api“). Die Minecraft-/Loader-APIs hier stimmen weiterhin.
>
> **Besitz (FOUNDATION §11 „Kamera und Ansicht“):** Kamera-Pose (`alignWithEntity`), Frustum, Hand/Körper und Maus gehören drone (`mixin.drone.client`), das FOV (`calculateFov`) und die Post-Effekte gehören video (`mixin.video.client`). Das eine `CameraMixin` aus Abschnitt 1 wird also auf zwei Klassen verteilt.

All paths are relative to `.ref/`: `vanilla/`, `neoforge/` (patched MC), `neoforge-api/`, `fabric-api/<module>/`.
Everything below was checked against those sources. FML/bus-API and Fabric Loader sources are not in `.ref`, but
their use is confirmed through call sites that are: `@Mod(value = "neoforge", dist = Dist.CLIENT)` plus constructor
`(IEventBus modEventBus, ModContainer container)` (`neoforge-api/net/neoforged/neoforge/client/ClientNeoForgeMod.java:95,101`),
`NeoForge.EVENT_BUS.addListener(Class<E>, Consumer<E>)` (same file l.128), and `implements ClientModInitializer` →
`public void onInitializeClient()` (`fabric-api/fabric-rendering-v1/.../impl/client/rendering/hud/HudStatusBarHeightRegistryImpl.java:61,197`).

## 0. Changes from 1.21.x (read this first)

| 1.21.x habit | 26.3 reality | Source |
|---|---|---|
| `GuiGraphics`, `render(...)` | **`GuiGraphicsExtractor`**, methods named `extract*`; screens/widgets override `extractRenderState(...)` / `extractWidgetRenderState(...)` | `vanilla/net/minecraft/client/gui/GuiGraphicsExtractor.java` |
| `drawString` / `blit` names | `text(...)`, `centeredText(...)`, `fill`, `fillGradient`, `outline`, `blit(RenderPipeline, ...)`, `blitSprite(RenderPipeline, ...)` | same file, l.161-434 |
| `minecraft.setScreen(s)` / `minecraft.screen` | **`minecraft.gui.setScreen(s)`** / `minecraft.gui.screen()`. `Minecraft#setScreen` no longer exists | `vanilla/net/minecraft/client/gui/Gui.java:217,221`, `Minecraft.java:2035` |
| `minecraft.gui` = HUD | `Gui` owns screen, overlay and `public final Hud hud`. The HUD code lives in `Hud` | `Gui.java:76`, `Hud.java` |
| `options.hideGui` | `minecraft.gui.hud.isHidden()` / `toggle()` (F1) | `Hud.java:208-214` |
| GLFW key codes (`GLFW_KEY_R = 82`) | **SDL3 scancodes**: `InputConstants.KEY_R = 21`, `KEY_ESCAPE = 41`, `MOUSE_BUTTON_LEFT = 1`, `MOUSE_BUTTON_RIGHT = 3`. Always use `InputConstants.KEY_*` | `vanilla/com/mojang/blaze3d/platform/InputConstants.java:41-150` |
| `mouseClicked(double,double,int)` | `mouseClicked(MouseButtonEvent event, boolean doubleClick)`, `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)` | `vanilla/net/minecraft/client/gui/components/events/GuiEventListener.java:21-45` |
| `GameRenderer.getFov`, camera in `GameRenderer.renderLevel` | **`Camera` owns FOV** (`calculateFov`) and gets its per-frame update from `GameRenderer.update(DeltaTracker)`. Rendering reads a `CameraRenderState` snapshot | `vanilla/net/minecraft/client/Camera.java:93-165` |
| `EntityType.MARKER` | `EntityTypes.MARKER` (constants moved to `EntityTypes`) | `vanilla/net/minecraft/world/entity/EntityTypes.java:699` |
| Fabric `HudRenderCallback` | **gone**. Use `HudElementRegistry` | `fabric-api/fabric-rendering-v1/.../hud/HudElementRegistry.java` |
| `KeyMapping(String,int,String category)` | `KeyMapping(String, int, KeyMapping.Category)`. `Category` is a record around an `Identifier` | `vanilla/net/minecraft/client/KeyMapping.java:90-98,203` |

Frame order in `Minecraft.renderFrame(boolean)` (`vanilla/net/minecraft/client/Minecraft.java:1239`, body l.1288-1306; called from `runTick` l.1217):
`gui.update()` → `level.update()` → **`gameRenderer.update(dt)` (runs `Camera.update` → `alignWithEntity`)** → `pick()`
→ **`gameRenderer.extract(dt, advanceGameTime)`** (camera, level and HUD are copied into render states) → `gameRenderer.render()`.
Earlier in `runTick`: client ticks (`Minecraft.tick`, 0..10 times, l.1191-1196), then `soundManager.updateSource(mainCamera())` (l.1210).

---

## 1. Camera: view through another entity, per-frame pose with roll

### 1.1 Vanilla API
- `Minecraft#setCameraEntity(@Nullable Entity)` sets `gameRenderer.mainCamera().setEntity(e)` and calls `checkEntityPostEffect(e)`. `Minecraft#getCameraEntity()` reads `mainCamera().entity()` (`Minecraft.java:2698-2705`).
- `GameRenderer#mainCamera()` (`renderer/GameRenderer.java:804`). There is one `Camera` per `GameRenderer` (l.134).
- `Camera#update(DeltaTracker)` (`Camera.java:93`): if `entity == null` it falls back to the player, then runs `alignWithEntity(partialTicks)`, `calculateFov`, `prepareCullFrustum`, `setupPerspective`.
- `private void alignWithEntity(float partialTicks)` (`Camera.java:253`): position = lerp(`entity.xo`→`getX()`) plus the lerped eye height. It calls `setRotation(entity.getViewYRot(pt), entity.getViewXRot(pt))`. For detached (F5) cameras it also calls `move(-zoom,0,0)`.
- `protected void setRotation(float yRot, float xRot)` (`Camera.java:343`) sets `rotation.rotationYXZ(PI - yRot°, -xRot°, 0)`. **Vanilla roll is hard-coded 0.** It then rotates the `FORWARDS/UP/LEFT` constants into `forwards/up/left` and sets `matrixPropertiesDirty |= 3`.
- `protected void setPosition(Vec3)`, `public Quaternionf rotation()`, `forwardVector()`, `upVector()`, `leftVector()`, `getFov()`, `isDetached()`.
- `Camera#extractRenderState(CameraRenderState, DeltaTracker)` (`Camera.java:118`) copies `pos`, `xRot`, `yRot`, **`orientation` (the quaternion)**, `viewRotationMatrix` (= conjugate of `rotation`), `projectionMatrix` and `hudFov`. The level and billboard renderers use `orientation` (`renderer/SubmitNodeCollection.java:131` and others), so **a roll written into `rotation` shows up everywhere**. Euler `xRot/yRot` readers: `renderer/entity/DisplayRenderer.java:133-134`, `renderer/extract/LevelExtractor.java:142-143` (section-frustum refresh, see 1.4 pitfall), `renderer/GameRenderer.java:835` (`projectHorizonToScreen`, locator bar), `renderer/DebugCrosshairRenderer.java:61`, `Camera.yaw()` (l.380, `TrackedWaypoint.Camera`).
- The sound listener follows the camera: `soundManager.updateSource(mainCamera())` (`Minecraft.java:1210` → `sounds/SoundEngine.java:511`, uses `position()`, `forwardVector()`, `upVector()`). Audio therefore comes from the drone. It runs before `renderFrame`, so it uses the previous frame's pose (one frame lag, harmless).

### 1.2 NeoForge
Patched `neoforge/net/minecraft/client/Camera.java` adds `float roll`, `setRotation(float yRot, float xRot, float roll)` (the 2-arg version is `@Deprecated` and delegates with roll 0) and `getRoll()`. `alignWithEntity` posts:
```java
// neoforge-api/net/neoforged/neoforge/client/event/ViewportEvent.java:240  (NeoForge.EVENT_BUS, client)
public static class ComputeCameraAngles extends ViewportEvent {
    public float getYaw(); public void setYaw(float); public float getPitch(); public void setPitch(float);
    public float getRoll(); public void setRoll(float); public Camera getCamera(); public float getPartialTick();
}
```
It fires once per frame inside `Camera.update`, before the frustum is built (post site `neoforge/net/minecraft/client/Camera.java:270-273`). It does **not** fire in the minecart-lerp branch. Pitch and yaw are not clamped here. The `roll` field is NeoForge-only, so a common mixin cannot `@Shadow` it; if the mixin in 1.4 writes `rotation` directly, `getRoll()` stays at the event value (no NeoForge code reads `getRoll()`, grep over `neoforge/` + `neoforge-api/`).

### 1.3 Fabric
Fabric API has **no camera event**. `grep "import net.minecraft.client.Camera;"` finds only `fabric-rendering-v1/.../api/client/rendering/v1/level/LevelExtractionContext.java` (read-only) plus its impl/mixin. You need a mixin.

### 1.4 Recommended: one common mixin for both loaders (quaternion, no Euler)
A drone flips and dives vertically, and YXZ Euler angles hit gimbal lock at ±90° pitch. `Entity#setXRot` clamps to ±90 (`world/entity/Entity.java:4014`). So do not drive the camera through entity rotation. Overwrite the camera at the **TAIL of `alignWithEntity`**. That point runs after vanilla and after NeoForge's `ComputeCameraAngles`, and before `prepareCullFrustum`, so culling stays consistent. Put the mixin in `common/src/main/resources/propwash.mixins.json` → `"client"`.
```java
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow @Final private static Vector3fc FORWARDS;
    @Shadow @Final private static Vector3fc UP;
    @Shadow @Final private static Vector3fc LEFT;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private boolean detached;
    @Shadow private int matrixPropertiesDirty;
    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void propwash$fpv(float partialTicks, CallbackInfo ci) {
        FpvPose p = FpvView.sampleForFrame();          // null = FPV inactive
        if (p == null) return;
        setPosition(p.pos());                          // delayed pose (see 1.6)
        rotation.set(p.orientation());                 // world-from-camera, camera looks along -Z, up +Y
        FORWARDS.rotate(rotation, forwards); UP.rotate(rotation, up); LEFT.rotate(rotation, left);
        yRot = p.yawDeg(); xRot = p.pitchDeg();        // keep the Euler consumers roughly right
        detached = false;                              // ignore F5 while flying
        matrixPropertiesDirty |= 3;                    // same flags vanilla setRotation sets
    }
}
```
Conventions (from `setRotation`): `rotationYXZ(PI - yaw, -pitch, -roll)`, all in radians. Yaw 0 looks toward +Z (south). Positive pitch looks down. For a camera tilted up by `t` on a drone with body quaternion `qBody` (same convention): `cam = new Quaternionf(qBody).rotateX(t)`.
To use the NeoForge event instead (Euler only): `q.getEulerAnglesYXZ(v)` (JOML 1.10.9 per `~/.gradle/caches/fabric-loom/26.3/mojang_minecraft_info.json`, not in `.ref`; UNVERIFIED: exact signature, only the method names `getEulerAnglesYXZ`, `rotateX`, `slerp` were found in `joml-1.10.9.jar` since no JDK/javap is installed), then convert back with the formula above. That is fine for level flight but jitters near vertical.

**Pitfall – section culling ignores roll.** `LevelExtractor.extract` re-applies the section frustum only when
`sectionOcclusionGraph().consumeFrustumUpdate()` is true or `floor(xRot/2)`/`floor(yRot/2)` changed
(`renderer/extract/LevelExtractor.java:141-149`). A pure roll (or any rotation that leaves the fake Euler values
unchanged) keeps the old visible-section list, so terrain pops at the screen corners. Common fix (MixinExtras is
on common's classpath, `common/build.gradle`: `mixinextras-common:0.5.3`):
```java
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"))
    private boolean propwash$refreshWhileFpv(boolean original) { return original || FpvView.active(); }
}
```
(`applyFrustum` just re-filters the already-built graph, `LevelExtractor.java:488-497`, so doing it every frame is cheap.)

### 1.5 FOV override
- Vanilla: `private float calculateFov(float partialTicks)` = `options.fov() * lerp(fovModifier)` → `modifyFovBasedOnDeathOrFluid` (`Camera.java:225-251`). `options.fov()` goes up to 110 (`Options.java:814`). The FOV is **vertical** in degrees. Convert an FPV spec: `vFov = 2*atan(tan(hFov/2)/aspect)`.
- NeoForge: `ViewportEvent.ComputeFov` (`getFOV/setFOV`, `neoforge-api/.../client/event/ViewportEvent.java:313`) is posted via `ClientHooks.getFieldOfView` (`neoforge-api/.../client/ClientHooks.java:319`) **inside `modifyFovBasedOnDeathOrFluid`** (`neoforge/net/minecraft/client/Camera.java:254`). That method runs for both the world FOV **and** the HUD/hand FOV (`calculateHudFov` passes 70). The event cannot tell the two apart. `ComputeFovModifierEvent` only changes the player's FOV *modifier* (sprint/speed, bow, spyglass; posted from `AbstractClientPlayer.getFieldOfViewModifier`, `neoforge/.../player/AbstractClientPlayer.java:116`).
- **Recommended (both loaders):** a common mixin `@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)` with `CallbackInfoReturnable<Float> cir` → `cir.setReturnValue(FpvView.vFovDeg())` while flying. Culling uses `max(fov, options.fov)` (`Camera.java:181`), so wide FOVs still cull correctly.

### 1.5a Near plane and blocks in front of the lens (FPV eye guard)
Checked 2026-10-02 against `.ref/vanilla` and `.ref/neoforge`:
- `Camera#update` (`Camera.java:93`, NeoForge identical) runs in this order: `alignWithEntity(pt)` → `this.fov =
  calculateFov(pt)` → `this.hudFov = …` → `prepareCullFrustum(getViewRotationMatrix(…), createProjectionMatrixForCulling(),
  this.position)` → `setupPerspective(0.05F, depthFar, fov, windowWidth, windowHeight)`. An injection at
  `@At(value = "FIELD", target = "Lnet/minecraft/client/Camera;fov:F", opcode = PUTFIELD, shift = AFTER)` sees the final
  FOV and can still move `position` before the cull frustum is built; `@ModifyArg(setupPerspective(FFFFF)V, index = 0)`
  changes the near plane of the real projection. The cull projection keeps its own `0.05F` (`Camera.java:189`).
- `Camera.PROJECTION_Z_NEAR` = 0.05F (`Camera.java:43`). `Camera#getNearPlane(float fov)` (`:431`) builds the near-plane
  rectangle from `projection.zNear()` and the window aspect; `NearPlane#getTopLeft/…/getPointOnPlane(x, y)` return
  offsets from the camera position. `getFluidInCamera` (`:447`) samples it with `options.fov()`.
- Reverse depth: `Projection#getMatrix` passes `near = zFar, far = zNear` to `Matrix4f#setPerspective` (`Projection.java:68-75`).
  The main target is `D32_FLOAT` (`com/mojang/blaze3d/pipeline/MainTarget.java:21`). `DeviceInfo#isZZeroToOne` is true on
  Vulkan (`renderpearl/backend/vulkan/VulkanDevice.java:91`) and on OpenGL only with `GL_ARB_clip_control`
  (`renderpearl/backend/opengl/GlHeuristics.java:90`, not on macOS).
- What the camera may not be inside: vanilla's third-person zoom uses `ClipContext.Block.VISUAL` (`Camera#getMaxZoom`,
  `:301`), i.e. `BlockState#getVisualShape(BlockGetter, BlockPos, CollisionContext)` (`BlockBehaviour.java:680`).
  `SnowLayerBlock`: `getCollisionShape` = `SHAPES[layers - 1]`, `getVisualShape` = `SHAPES[layers]` (`:46`, `:56`) –
  entities sink one layer into snow. `PowderSnowBlock#getVisualShape` is empty (`:133`). Iterate shape boxes without
  allocation via `VoxelShape#forAllBoxes(Shapes.DoubleLineConsumer)` (`VoxelShape.java:117`, `Shapes.java:462`).

### 1.6 A client-only camera entity at a delayed pose (video latency)
**It works.** `Camera` only reads fields from the entity: `xo/getX`, `getViewYRot`, `getEyeHeight()`, `isPassenger()` and `instanceof LivingEntity/AbstractClientPlayer`. `Camera.update` only needs `minecraft.player` and `level` to be non-null. It never checks that the entity is in the level.
```java
Marker anchor = new Marker(EntityTypes.MARKER, mc.level);  // world/entity/Marker.java:15. Size 0, never added to the level
mc.setCameraEntity(anchor);                                // restore with mc.setCameraEntity(mc.player)
```
Effects of a camera entity that is not the player (these are mostly what you want):
- `LevelExtractor.extractVisibleEntities` renders a `LocalPlayer` **only if it is the camera entity** (`renderer/extract/LevelExtractor.java:274-275`), so the pilot's body becomes invisible. Showing it needs a mixin on that condition. NeoForge patches the condition to also render the local player when it is not the camera entity (`neoforge/.../LevelExtractor.java:283`); vanilla/Fabric do not. Propwash: `mixin.drone.client.LevelExtractorMixin` modifies the `instanceof LocalPlayer` check (MixinExtras `@ModifyExpressionValue` with `@At(value = "CONSTANT", args = "classValue=net/minecraft/client/player/LocalPlayer")`, present once in both loaders). Since the body is then not the camera entity, `LivingEntityRenderer.shouldShowName` (`:227`) would show your own name tag; `AvatarRendererMixin` clears `nameTag` at the TAIL of `AvatarRenderer#extractRenderState(Avatar, AvatarRenderState, float)`.
- `LocalPlayer.isControlledCamera()` is false (`client/player/LocalPlayer.java:762`). WASD no longer moves the player (`applyInput`, l.711) and `sendPosition` skips the move packet (l.298). The pilot stays put.
- There is no view bob (`GameRenderer.bobView` needs `entityRenderState.isPlayer`), no hurt tilt (`bobHurt` needs `isLiving`) and no block outline (`shouldRenderBlockOutline` needs `cameraEntity instanceof Player`, `GameRenderer.java:608`).
- **Hands still render.** `renderItemInHand` checks `playerState.hasPlayer` (set whenever `minecraft.player != null`, `renderer/extract/LevelExtractor.java:416-417`), first-person `cameraType`, not sleeping, `!guiRenderState.isHudHidden` (F1) and not spectator (`GameRenderer.java:375-382`). It never looks at the camera entity. See 1.7.
- **Screen overlays still come from the pilot**, not the drone: in-block overlay, fire, underwater tint, nausea/portal warp are extracted from `minecraft.player` in `LevelExtractor.extractPlayerState` (l.413-455). Standing in water/portal while flying distorts the FPV feed. (Fog/fluid colour use the camera position, `Camera.getFluidInCamera`, so those are correct.)
- **Your own drone is visible from inside.** The entity loop skips only `camera.entity()` (`LevelExtractor.java:274`). With a Marker as camera the real drone entity is rendered around the lens: skip it in the drone renderer while it is the FPV source, or use the drone entity itself as camera entity (then vanilla hides it; the mixin in 1.4 still owns the pose).
- `Minecraft.pick` raycasts from the camera entity (`Minecraft.java:2988` → `LocalPlayer.raycastHitResult`, `client/player/LocalPlayer.java:1276`). Swallow `options.keyAttack` / `keyUse` clicks while flying (see 5.3).
- Mouse movement still turns the player (`MouseHandler.turnPlayer`, l.325). NeoForge has `CalculatePlayerTurnEvent` (`get/setMouseSensitivity`, `get/setCinematicCameraEnabled`, `neoforge-api/.../client/event/CalculatePlayerTurnEvent.java:37-62`; sensitivity 0 still turns, `ss = s*0.6+0.2`, `neoforge/.../MouseHandler.java:349-352`). Portable: common mixin `@Inject(method="turnPlayer", at=@At("HEAD"), cancellable=true)`.

Pitfalls:
- A Marker is never ticked, so `xo` is never updated. Either set `anchor.setPos(p)` and `anchor.xo/yo/zo` (public fields, `Entity.java:220`) to the same value every frame, or just override the position in the mixin (1.4). Do not rely on `alignWithEntity`'s lerp.
- Vanilla resets the camera entity on login (`multiplayer/ClientPacketListener.java:539`, `handleLogin`), respawn/dimension change (l.1283,1312, `handleRespawn`), `handleSetCamera` (l.1876) and level change/disconnect (`Minecraft.java:2230`, `updateLevelInEngines`). Reset FPV state also on disconnect: Fabric `ClientPlayConnectionEvents.DISCONNECT` (`fabric-networking-api-v1/.../client/networking/v1/ClientPlayConnectionEvents.java:59`), NeoForge `ClientPlayerNetworkEvent.LoggingOut` (`neoforge-api/.../client/event/ClientPlayerNetworkEvent.java:86`, game bus). The Camera never clears a *removed* entity. **Check every client tick**: if `mc.getCameraEntity() != anchor` or `anchor.level() != mc.level`, drop or rebuild FPV state.
- Chunks are streamed around the **player**, not the camera. A drone beyond the view distance flies into unloaded terrain.
- Delay buffer: record `(Util.getNanos(), pos, quat)` for every physics sub-step or frame and sample at `now - latencyNs` with `lerp`/`slerp`. Sample **inside the mixin** (1.4). That point runs per frame before extraction.

### 1.7 Hand, bobbing, HUD hidden
- Hide hands on both loaders with a common mixin on `GameRenderer`: `@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)`. Signature: `private void renderItemInHand(CameraRenderState, PlayerRenderState, GpuTextureView)` (`GameRenderer.java:375`, same in `neoforge/...:379`). NeoForge alternative: cancel `RenderHandEvent` (cancellable, one post per hand, `neoforge-api/.../client/event/RenderHandEvent.java:30`, posted from `neoforge/.../FirstPersonHandsAndItemsRenderer.java:343,362`).
- Bobbing: `private void bobView(CameraRenderState, PoseStack)` (`GameRenderer.java:361`). If you keep the player as camera entity, cancel it at HEAD. Do not flip the user's `options.bobView()`.
- Do not use `hud.toggle()`/F1 to hide hands. That also hides your own FPV HUD (Fabric: every registration position is F1-gated in practice, see 2.1; NeoForge: if you check `isHidden()` as recommended).

---

## 2. HUD overlay registration and hiding vanilla layers

### 2.1 Fabric: `fabric-rendering-v1/net/fabricmc/fabric/api/client/rendering/v1/hud/`
```java
public interface HudElement { void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker); }
public interface HudElementRegistry {
    static void addFirst(Identifier id, HudElement e);      static void addLast(Identifier id, HudElement e);
    static void attachElementBefore(Identifier beforeThis, Identifier id, HudElement e);
    static void attachElementAfter(Identifier afterThis, Identifier id, HudElement e);
    static void removeElement(Identifier id);               static void replaceElement(Identifier id, Function<HudElement, HudElement> replacer);
}
```
- Elements attached relative to a vanilla element **inherit its condition (`!Hud.isHidden()`)**. The javadoc claims `addFirst`/`addLast` do not (`HudElementRegistry.java:30-36`, "as of 1.21.6"), **but the 26.3 implementation contradicts it**: `addFirst` goes into the `MISC_OVERLAYS` root (`impl/client/rendering/hud/HudElementRegistryImpl.java:72,79-82`), which only runs inside vanilla's `if (!this.isHidden)` (`vanilla/.../gui/Hud.java:225-227`); `addLast` goes into the `SUBTITLES` root, which wraps `SubtitleOverlay.extractRenderState` (`mixin/client/rendering/SubtitleOverlayMixin.java:32-39`). That is called only when not hidden (or when an in-game-UI screen is open) and is usually *deferred* (`Hud.java:234-245`), so `addLast` elements draw after the debug overlay and toasts (`Gui.java:207`) or inside `Screen.extractBackground` (`screens/Screen.java:395`). Treat every Fabric position as F1-gated.
- Registration throws `IllegalArgumentException` for a duplicate id or an unknown anchor id (`HudElementRegistryImpl.java:79-137,140-149`).
- Vanilla IDs are in `VanillaHudElements`: `MISC_OVERLAYS, CROSSHAIR, SPECTATOR_MENU, HOTBAR, ARMOR_BAR, HEALTH_BAR, FOOD_BAR, AIR_BAR, MOUNT_HEALTH, INFO_BAR, EXPERIENCE_LEVEL, HELD_ITEM_TOOLTIP, SPECTATOR_TOOLTIP, MOB_EFFECTS, BOSS_BAR, SLEEP, DEMO_TIMER, SCOREBOARD, OVERLAY_MESSAGE, TITLE_AND_SUBTITLE, CHAT, PLAYER_LIST, SUBTITLES`.
- Implementation: `WrapOperation`s in `fabric-rendering-v1/net/fabricmc/fabric/mixin/client/rendering/HudMixin.java`.

### 2.2 NeoForge: `RegisterGuiLayersEvent` (mod bus, `IModBusEvent`)
```java
// neoforge-api/net/neoforged/neoforge/client/event/RegisterGuiLayersEvent.java
public void registerBelowAll(Identifier id, GuiLayer layer); public void registerBelow(Identifier other, Identifier id, GuiLayer layer);
public void registerAbove(Identifier other, Identifier id, GuiLayer layer); public void registerAboveAll(Identifier id, GuiLayer layer);
public void replaceLayer(Identifier id, GuiLayer replacement); public void wrapLayer(Identifier id, UnaryOperator<GuiLayer> wrapper);
// neoforge-api/.../client/gui/GuiLayer.java
public interface GuiLayer { void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker); }
```
- IDs are in `neoforge-api/.../client/gui/VanillaGuiLayers.java`: `CAMERA_OVERLAYS, CROSSHAIR, AFTER_CAMERA_DECORATIONS, HOTBAR, PLAYER_HEALTH, ARMOR_LEVEL, FOOD_LEVEL, VEHICLE_HEALTH, AIR_LEVEL, CONTEXTUAL_INFO_BAR_BACKGROUND, EXPERIENCE_LEVEL, CONTEXTUAL_INFO_BAR, SELECTED_ITEM_NAME, SPECTATOR_TOOLTIP, EFFECTS, BOSS_OVERLAY, SLEEP_OVERLAY, DEMO_OVERLAY, SCOREBOARD_SIDEBAR, OVERLAY_MESSAGE, TITLE, CHAT, TAB_LIST, SUBTITLE_OVERLAY`.
- The patched `Hud` gives vanilla layers a `hudVisible` condition (`neoforge/net/minecraft/client/gui/Hud.java:242-278`; vanilla layers registered at l.211, modded via `GuiLayerManager.initModdedLayers` at l.1614). **Modded layers get no F1 condition**, so check `mc.gui.hud.isHidden()` yourself.
- Per-frame cancel alternative: `RenderGuiLayerEvent.Pre` (cancellable, `getName()`) and `RenderGuiEvent.Pre/Post` on `NeoForge.EVENT_BUS` (`neoforge-api/.../gui/GuiLayerManager.java:63-80`).

**The IDs differ between loaders** (for example `health_bar` vs `player_health`), so keep one ID list per loader.

| purpose | Fabric `VanillaHudElements` | NeoForge `VanillaGuiLayers` |
|---|---|---|
| pumpkin/vignette/portal/powder-snow | `MISC_OVERLAYS` | `CAMERA_OVERLAYS` |
| crosshair / hotbar | `CROSSHAIR` / `HOTBAR` | `CROSSHAIR` / `HOTBAR` |
| hearts/armor/food/air | `HEALTH_BAR, ARMOR_BAR, FOOD_BAR, AIR_BAR` | `PLAYER_HEALTH, ARMOR_LEVEL, FOOD_LEVEL, AIR_LEVEL` |
| xp / locator bar | `EXPERIENCE_LEVEL, INFO_BAR` | `EXPERIENCE_LEVEL, CONTEXTUAL_INFO_BAR(_BACKGROUND)` |
| item name, effects, mount | `HELD_ITEM_TOOLTIP, MOB_EFFECTS, MOUNT_HEALTH` | `SELECTED_ITEM_NAME, EFFECTS, VEHICLE_HEALTH` |

### 2.3 Pattern
```java
// common: client/FpvHud.java  (only vanilla types here: HudElement/GuiLayer are loader types)
public final class FpvHud {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fpv_osd");
    public static void extract(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!FpvView.active() || mc.gui.hud.isHidden()) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        OsdRenderer.draw(g, mc.font, FpvView.telemetry(), pt);
    }
}
// Fabric client init (fabric module)
HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, FpvHud.ID, FpvHud::extract); // same layer as Neo below CHAT
for (Identifier id : FABRIC_HIDE)
    HudElementRegistry.replaceElement(id, old -> (g, dt) -> { if (!FpvView.active()) old.extractRenderState(g, dt); });
// NeoForge client constructor (neoforge module)
modBus.addListener(RegisterGuiLayersEvent.class, e -> {
    e.registerBelow(VanillaGuiLayers.CHAT, FpvHud.ID, FpvHud::extract);
    for (Identifier id : NEO_HIDE) e.wrapLayer(id, old -> (g, dt) -> { if (!FpvView.active()) old.render(g, dt); });
});
```
`HudElement` and `GuiLayer` have the same shape, so both loaders take `FpvHud::extract`. `wrapLayer` throws if the ID is not registered yet. Vanilla layers are registered before the event.

---

## 3. GUI drawing API: `GuiGraphicsExtractor` (`vanilla/net/minecraft/client/gui/GuiGraphicsExtractor.java`)
Drawing calls **record** render states into `GuiRenderState`. Nothing is drawn right away. The actual draw happens later in `GuiRenderer`.
```java
int guiWidth(); int guiHeight(); Matrix3x2fStack pose(); void nextStratum(); void blurBeforeThisStratum();
void enableScissor(int x0,int y0,int x1,int y1); void disableScissor(); boolean containsPointInScissor(int x,int y);
void horizontalLine(int x0,int x1,int y,int col); void verticalLine(int x,int y0,int y1,int col);
void fill(int x0,int y0,int x1,int y1,int col); void fill(RenderPipeline p,int x0,int y0,int x1,int y1,int col);
void fillGradient(int x0,int y0,int x1,int y1,int colTop,int colBottom); void outline(int x,int y,int w,int h,int col);
void text(Font f, String|FormattedCharSequence|Component s, int x, int y, int color[, boolean dropShadow]); // default shadow = true
void centeredText(Font f, String|Component|FormattedCharSequence s, int x, int y, int color);
int  textWithWordWrap(Font f, FormattedText s, int x, int y, int width, int col[, boolean dropShadow]);
void blit(RenderPipeline p, Identifier tex, int x,int y, float u,float v, int w,int h, int texW,int texH[, int color]);
void blit(RenderPipeline p, Identifier tex, int x,int y, float u,float v, int w,int h, int srcW,int srcH, int texW,int texH[, int color]);
void blit(Identifier tex, int x0,int y0,int x1,int y1, float u0,float u1,float v0,float v1);
void blitSprite(RenderPipeline p, Identifier sprite, int x,int y,int w,int h[, float alpha | int color]);
void item(ItemStack s,int x,int y); void setTooltipForNextFrame(Font f, Component c, int x, int y);
void entity(EntityRenderState rs, float scale, Vector3fc translation, Quaternionfc rot, @Nullable Quaternionfc camAngle, int x0,int y0,int x1,int y1);
```
- Pipelines: `RenderPipelines.GUI` (plain fill), `GUI_TEXTURED` (blit/sprites), `CROSSHAIR` (inverting blend, used by `Hud.extractCrosshair`, `Hud.java:450`). Constants: `renderer/RenderPipelines.java:1097,1107,1138`.
- Sprites load from the GUI atlas: `assets/<ns>/textures/gui/sprites/<path>.png` (`vanilla/net/minecraft/client/data/AtlasProvider.java:87`). A plain `blit(..., Identifier tex, ...)` takes a full texture path such as `propwash:textures/gui/container/workbench.png`.
- **Color is ARGB and alpha must be non-zero.** `text(...)` returns early when `ARGB.alpha(color) == 0` (l.247-252), so `0xFFFFFF` is invisible. Write `0xFFFFFFFF`. Helpers: `ARGB.color(a,r,g,b)`, `ARGB.white(float)`, `ARGB.colorFromFloat(a,r,g,b)` (`vanilla/net/minecraft/util/ARGB.java`).
- `Font`: `lineHeight` (= 9), `width(String|FormattedText|FormattedCharSequence)` (`gui/Font.java:36,118-126`).
- **Outlined text:** the GUI has no outline helper. `Font.prepare8xTextOutline` is used for world text only. Draw 8 offset copies in the outline color with no shadow, then the main text:
```java
static void outlined(GuiGraphicsExtractor g, Font f, String s, int x, int y, int col, int outline) {
    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
        if (dx != 0 || dy != 0) g.text(f, s, x + dx, y + dy, outline, false);
    g.text(f, s, x, y, col, false);
}
```
- **Pose transforms** use JOML `Matrix3x2fStack`: `pushMatrix()`, `popMatrix()`, `translate(float,float)`, `rotate(float radians)`, `scale(float,float)`. Vanilla call sites: map decorations `GuiGraphicsExtractor.java:919-922` and `Hud.java:379-389` (title text `scale(4,4)`). Each element copies the current matrix (`new Matrix3x2f(this.pose)`), so fills and text can be rotated.
```java
// artificial horizon: rotate by -roll around the screen center, shift by pitch
var p = g.pose(); p.pushMatrix();
p.translate(g.guiWidth() / 2f, g.guiHeight() / 2f); p.rotate(-rollRad); p.translate(0, pitchDeg * pxPerDeg);
g.fill(-60, 0, 60, 1, 0xFFFFFFFF);              // 1px line in rotated space
p.pushMatrix(); p.scale(0.5f, 0.5f); g.text(font, "10", 124, -4, 0xFFFFFFFF, false); p.popMatrix(); // half-size text
p.popMatrix();
```
- **Scissor** is converted with `transformAxisAligned(pose)` (l.148-151). Under a rotation you get the axis-aligned bounding box. Every `enableScissor` needs its own `disableScissor` (it is a stack).
- **Layering:** `GuiRenderState` automatically moves an element above earlier elements whose bounds it intersects (`renderer/state/gui/GuiRenderState.java:120-145`), so painter's order holds. `nextStratum()` forces a new layer.
- **Only one blur per frame**: `blurBeforeThisStratum()` throws `IllegalStateException("Can only blur once per frame")` the second time (`GuiRenderState.java:45-48`). Never call it from the HUD. `Screen.extractBlurredBackground` already uses it for non-in-game screens when the blur option is ≥ 1 (`screens/Screen.java:398-403`).

---

## 4. Screens and widgets

### 4.1 `Screen` lifecycle (`vanilla/net/minecraft/client/gui/screens/Screen.java`)
```java
protected Screen(Component title);                                   // l.85
protected void init();              // l.371, called by init(w,h) l.332 on first show; later shows and resize → repositionElements() → rebuildWidgets() → clearWidgets()+init()
public void tick();                 // l.374
public void removed(); public void added(); public void onClose();  // onClose() = minecraft.gui.setScreen(null)
public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);   // l.117, draws all renderables
public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);    // l.383
public boolean keyPressed(KeyEvent e);                               // l.124, Esc + shouldCloseOnEsc() → onClose()
public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick);// from ContainerEventHandler
public boolean mouseReleased(MouseButtonEvent e); boolean mouseDragged(MouseButtonEvent e, double dx, double dy);
public boolean mouseScrolled(double x, double y, double scrollX, double scrollY); boolean charTyped(CharacterEvent e);
public boolean isPauseScreen();     // default true (l.428). AbstractContainerScreen → false
public boolean isInGameUi();        // default false. true = transparent gradient, no blur/menu texture
protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T w);
protected <T extends Renderable> T addRenderableOnly(T r); protected <T extends GuiEventListener & NarratableEntry> T addWidget(T w);
```
- Order: `extractRenderStateWithTooltipAndSubtitles` → `nextStratum` → `extractBackground` → `nextStratum` → `extractRenderState` → deferred (tooltips) (l.108-114). **Do not call `extractBackground` from your own `extractRenderState`**: for a normal screen that is a second blur and throws; for an in-game-UI screen it draws the gradient twice.
- `extractBackground`: if `isInGameUi()`, you get a transparent gradient; otherwise blur plus the menu background texture (`INWORLD_MENU_BACKGROUND` when a level is loaded).
- Input records (`vanilla/net/minecraft/client/input/`): `KeyEvent(int key, int keycode, int modifiers)` (`key` is an SDL scancode, compare it with `InputConstants.KEY_*`; `keycode`/`shortcutKey()` is the layout-dependent SDL keycode, e.g. 9 = Tab, 97 = 'a', `KeyEvent.java:12-21`, `Screen.java:134-140`); `MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo)` with `.button()`; `CharacterEvent(int codepoint)`. Helpers on `InputWithModifiers`: `isEscape() isConfirmation() isLeft() isRight() isUp() isDown() hasShiftDown() hasControlDown() hasAltDown()`.
- Open a screen with `Minecraft.getInstance().gui.setScreen(new DroneTuneScreen(...))`. Call it from the game thread only (`Gui.java:222-223` logs an error only when `SharedConstants.IS_RUNNING_IN_IDE`; in production nothing warns you).

### 4.2 Widgets (`vanilla/net/minecraft/client/gui/components/`)
```java
Button.builder(Component msg, Button.OnPress onPress)   // OnPress: void onPress(Button b)
      .bounds(x, y, w, h) | .pos(x,y).size(w,h) | .width(w) ; .tooltip(Tooltip) ; .build();              // Button.java:24-107
// real call site: screens/ErrorScreen.java:27
new AbstractSliderButton(int x, int y, int w, int h, Component msg, double initial0to1) {           // AbstractSliderButton.java:35
    protected void updateMessage() { setMessage(...); }   // abstract
    protected void applyValue() { ... this.value ... }    // abstract, `protected double value` in 0..1
}   // call site: screens/inventory/JigsawBlockEditScreen.java:137-150 (calls updateMessage() in an instance initializer)
CycleButton.builder(Function<T,Component> stringifier, T default).withValues(Collection<T>|T...).withTooltip(v -> Tooltip)
      .create(x, y, w, h, Component name, (btn, value) -> ...);                      // CycleButton.java:182-267
CycleButton.onOffBuilder(boolean).create(Component name, OnValueChange<Boolean>);    // call site: screens/WorldOptionsScreen.java:239-252
new EditBox(Font f, int x, int y, int w, int h, Component narration); setValue/getValue/setResponder(Consumer<String>)/setMaxLength/setHint
      // EditBox.java:72,86,100,112,500,630. NOTE: no setFilter in 26.3; validate inside setResponder.
      // addFormatter(EditBox.TextFormatter) (l.90) only changes how text is displayed
Tooltip.create(Component [, Component narration]);  widget.setTooltip(Tooltip); setTooltipDelay(Duration)
```
Custom widget: extend `AbstractWidget(int x,int y,int w,int h,Component msg)`. Implement `protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float a)` and `protected void updateWidgetNarration(NarrationElementOutput out)`. Override `onClick(MouseButtonEvent e, boolean dbl)`, `onDrag(MouseButtonEvent e,double dx,double dy)` and `onRelease(MouseButtonEvent e)`. The default `isValidClickButton` is `button() == 1` (left in SDL, `AbstractWidget.java:140`). `active` and `visible` are public fields. Layout helpers such as `LinearLayout`, `GridLayout`, `HeaderAndFooterLayout` and `FrameLayout` are in `gui/layouts/`.

### 4.3 Tuning/settings screen sketch (common, client package)
```java
public class RatesScreen extends Screen {
    private final Screen parent; private double rate = 0.7;
    public RatesScreen(Screen parent) { super(Component.translatable("propwash.screen.rates")); this.parent = parent; }
    @Override protected void init() {
        int cx = this.width / 2;
        addRenderableWidget(new AbstractSliderButton(cx - 100, 40, 200, 20, Component.empty(), rate) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.literal("Rate: %.2f".formatted(value * 2))); }
            @Override protected void applyValue() { rate = value; }
        });
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(cx - 100, height - 28, 200, 20).build());
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; } // keep physics running in SP if wanted
}
```

### 4.4 Drone workbench GUI (container screen, client side)
Design (`docs/ARCHITECTURE.md` "Werkbank"): slots laid out as a top view of the drone (frame, stack, 4 motors,
4 props, video, battery), 3D preview, stats (mass, thrust/weight, flight time, top speed), problems/warnings, take
the finished drone out. Block, `MenuType` and server `AbstractContainerMenu` belong to the registration/network
areas; this is the client side. `DroneBuild` is **planned** (ARCHITECTURE.md, package `build`), not in code yet.
- `AbstractContainerScreen<T extends AbstractContainerMenu>(T menu, Inventory inv, Component title[, int imageWidth, int imageHeight])` (`screens/inventory/AbstractContainerScreen.java:60-64`, default 176x166). **`imageWidth/imageHeight` are `protected final`** (l.38-39): a bigger panel must be passed to the 5-arg constructor. `init()` (l.79) sets `leftPos/topPos` (l.80-81); `titleLabelX/Y`, `inventoryLabelX/Y` (= `imageHeight - 94`) are mutable.
- Slot positions come from the **menu** (`new Slot(Container, int index, int x, int y)`, `world/inventory/Slot.java:17`; `addSlot` / `addStandardInventorySlots(container, left, top)`, `AbstractContainerMenu.java:126,89`), in panel-relative pixels. Per-part filtering: override `Slot.mayPlace(ItemStack)` (l.44), `getMaxStackSize()` (l.73).
- **Empty-slot silhouettes** (motor/prop/battery icons): override `Slot.getNoItemIcon()` → `@Nullable Identifier` (l.81); the screen draws it with `blitSprite(GUI_TEXTURED, icon, x, y, 16, 16)` (`AbstractContainerScreen.java:227-231`). The id is a GUI-atlas sprite: `propwash:container/slot/motor` → `assets/propwash/textures/gui/sprites/container/slot/motor.png`. Vanilla: `world/inventory/BrewingStandMenu.java:18,148`.
- Override `extractBackground(g, mx, my, a)`: call `super` (in-game gradient, since `isInGameUi()` is true, l.552), then blit the panel. Example: `screens/inventory/BrewingStandScreen.java:31-36` does `g.blit(RenderPipelines.GUI_TEXTURED, TEX, xo, yo, 0, 0, imageWidth, imageHeight, 256, 256)`. Panel texture path: `assets/propwash/textures/gui/container/workbench.png` (vanilla ones: `vanilla/assets/minecraft/textures/gui/container/`).
- `extractLabels(g, xm, ym)` (l.192-195) runs with the pose already translated to `(leftPos, topPos)` (`extractContents`, l.97-113). Default colour `-12566464` (0xFF404040), no shadow. Draw stats/problem lines here in panel coordinates.
- **3D preview**: `g.entity(EntityRenderState rs, float scale, Vector3fc translation, Quaternionfc rotation, @Nullable Quaternionfc cameraAngle, x0, y0, x1, y1)` (`GuiGraphicsExtractor.java:972`). Real call site with a free-standing render state (no entity in the world): `SmithingScreen.java:60-70,110` builds an `ArmorStandRenderState`, sets `entityType = EntityTypes.ARMOR_STAND` and calls `graphics.entity(armorStandPreview, 25.0F, ARMOR_STAND_TRANSLATION, ARMOR_STAND_ANGLE, null, x0, y0, x1, y1)` from `extractBackground`. The renderer is found by `renderState.entityType` (`renderer/entity/EntityRenderDispatcher.java:112-116`, PiP in `gui/render/pip/GuiEntityRenderer.java`), so a `DroneRenderState` with `entityType = PropwashEntities.DRONE` renders through our normal drone renderer. Animate the spin by updating `rotation` per frame.
- **Buttons → server** (vanilla round trip): client calls `menu.clickMenuButton(minecraft.player, id)` for prediction, then `minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id)` (`client/multiplayer/MultiPlayerGameMode.java:501`; call site `screens/inventory/StonecutterScreen.java:147-149`); the server runs `containerMenu.clickMenuButton(player, id)` (`server/network/ServerGamePacketListenerImpl.java:2086`). Use this for "assemble"/"disassemble"/preset ids; only a custom payload can carry more than an int.
- Widgets (preset `CycleButton`, "assemble" `Button`) are added in `init()` after `super.init()`, positioned from `leftPos/topPos`.
```java
public class WorkbenchScreen extends AbstractContainerScreen<WorkbenchMenu> {        // common, client package
    private static final Identifier TEX = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/container/workbench.png");
    private final DroneRenderState preview = new DroneRenderState();                  // our EntityRenderState subclass
    public WorkbenchScreen(WorkbenchMenu m, Inventory inv, Component t) { super(m, inv, t, 230, 220); preview.entityType = PropwashEntities.DRONE; }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("propwash.workbench.assemble"), b -> {
            if (menu.clickMenuButton(minecraft.player, WorkbenchMenu.BTN_ASSEMBLE))
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WorkbenchMenu.BTN_ASSEMBLE);
        }).bounds(leftPos + 150, topPos + 100, 70, 20).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractBackground(g, mx, my, a);
        g.blit(RenderPipelines.GUI_TEXTURED, TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        g.entity(preview, 30f, new Vector3f(0, 0.2f, 0), new Quaternionf().rotationXYZ(0.5f, spinRad(), (float) Math.PI), null,
                 leftPos + 150, topPos + 20, leftPos + 220, topPos + 90);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym);
        g.text(font, Component.literal(massText()), 150, 95, 0xFF404040, false);    // panel coords
    }
}
```
- Registering the screen:
  - NeoForge: `RegisterMenuScreensEvent` (mod bus, `IModBusEvent`): `e.register(MenuType<? extends M>, MenuScreens.ScreenConstructor<M,U>)` (`neoforge-api/.../client/event/RegisterMenuScreensEvent.java:19,27`; `ScreenConstructor` is public only in the patched `neoforge/.../screens/MenuScreens.java:121`). Extra open data: `IMenuTypeExtension.create(IContainerFactory)` (`neoforge-api/.../common/extensions/IMenuTypeExtension.java:25`) + `player.openMenu(provider, buf -> ...)` (`IPlayerExtension.java:75`).
  - Fabric: `MenuScreens.register(type, WorkbenchScreen::new)`. The method is private in vanilla (`screens/MenuScreens.java:56`, `ScreenConstructor` private at l.106) but made `transitive-accessible` by `fabric-api/fabric-transitive-access-wideners-v1/fabric-transitive-access-wideners-v1.classtweaker:11-12`. **This compiles only in the fabric module**, not in common. Extra open data: `ExtendedMenuType<T, D>(ExtendedFactory<T,D>, StreamCodec<? super RegistryFriendlyByteBuf, D>)` + `ExtendedMenuProvider<D>` (`fabric-menu-api-v1/net/fabricmc/fabric/api/menu/v1/ExtendedMenuType.java:103,112`, `ExtendedMenuProvider.java:28`).

---

## 5. Keybindings
```java
// vanilla/net/minecraft/client/KeyMapping.java
public KeyMapping(String name, int keysym, KeyMapping.Category category);                        // keyboard
public KeyMapping(String name, InputConstants.Type type, int value, KeyMapping.Category category[, int order]);
public boolean isDown(); public boolean consumeClick(); public boolean matches(KeyEvent e); public boolean matchesMouse(MouseButtonEvent e);
public record Category(Identifier id) { public static Category register(Identifier id); public Component label(); }
// label() = Component.translatable(id.toLanguageKey("key.category")) → "key.category.propwash.drone"
```
Vanilla example: `options.keyTogglePerspective = new KeyMapping("key.togglePerspective", 62, Category.MISC)`. 62 is the SDL scancode for F5 (`Options.java:689`).

### 5.1 Per loader
- **Fabric**: `KeyMappingHelper.registerKeyMapping(KeyMapping)` (`fabric-key-mapping-api-v1/net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper.java:50`, returns the mapping). It **throws `IllegalStateException` once `Minecraft.getInstance().options` exists** (`impl/client/keymapping/KeyMappingRegistryImpl.java:34-35`), so call it in `onInitializeClient`. Register the category with vanilla `KeyMapping.Category.register(id)` (public, `vanilla/.../KeyMapping.java:218`, throws on a duplicate id); Fabric re-sorts the category list after every `register` (`mixin/client/keymapping/KeyMappingCategoryMixin.java`, vanilla first, then by namespace/path). Fabric has no category API of its own.
- **NeoForge**: `RegisterKeyMappingsEvent` (mod bus): `register(KeyMapping)` and `registerCategory(KeyMapping.Category)` (`neoforge-api/.../client/event/RegisterKeyMappingsEvent.java:42,49`). The patched `Category.register(Identifier)` is `@Deprecated`, so create the category with `new KeyMapping.Category(id)` (`neoforge/net/minecraft/client/KeyMapping.java:319-323`). NeoForge also adds `KeyMapping(String, IKeyConflictContext, InputConstants.Type, int, Category)` overloads. Use `KeyConflictContext.IN_GAME` for flight keys (optional).
- `KeyMapping.compareTo` compares categories with `==` (`KeyMapping.java:146-147`), so **use one `Category` instance for all our mappings**. Build it through the platform service: Fabric returns `Category.register(id)`, NeoForge returns `new Category(id)` and passes the same instance to `registerCategory`.

### 5.2 Common holder
```java
public final class PropwashKeys {
    public static final KeyMapping.Category CATEGORY = Services.PLATFORM.keyCategory(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "drone")); // NEW method, add to IPlatformHelper
    public static final KeyMapping ARM   = new KeyMapping("key.propwash.arm",   InputConstants.KEY_B, CATEGORY);
    public static final KeyMapping FPV   = new KeyMapping("key.propwash.fpv",   InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping RATES = new KeyMapping("key.propwash.rates", InputConstants.KEY_G, CATEGORY);
    public static List<KeyMapping> all() { return List.of(ARM, FPV, RATES); }
}
```

### 5.3 Consuming
`Minecraft.tick` calls `handleKeybinds()` only when `gui.overlay() == null && gui.screen() == null` (`Minecraft.java:1807-1810`). Poll in the client tick: `while (PropwashKeys.FPV.consumeClick()) FpvView.toggle(mc);`. Continuous stick input uses `isDown()`.
While flying, drain vanilla keys in the **pre**-tick, which runs before `handleKeybinds`. `while (mc.options.keyTogglePerspective.consumeClick()) {}` blocks F5. Do the same for `keyAttack`/`keyUse` (`Options.java:678-689`). Draining clicks is not enough for held buttons: `continueAttack(... keyAttack.isDown() ...)` and held use read `isDown()` (`Minecraft.java:1986-1990`), so also call `KeyMapping#setDown(false)` (public, `KeyMapping.java:191`) in the pre-tick. Loader hooks: NeoForge `InputEvent.InteractionKeyMappingTriggered` (cancellable; `isAttack()/isUseItem()/isPickBlock()`, `neoforge-api/.../client/event/InputEvent.java:313`, posted from `neoforge/.../Minecraft.java:1653,1703,1767,2471`); Fabric `ClientPreAttackCallback.EVENT` (return true to cancel, fires on click and hold, `fabric-events-interaction-v0/.../api/event/client/player/ClientPreAttackCallback.java:42,60`).
Note: vanilla initializes SDL with `SDL_Init(32)`, which is video only (`vanilla/com/mojang/blaze3d/systems/RenderSystem.java:216`). Gamepad/joystick subsystems are **not** initialized, so the input area has to init them itself for RC transmitters.

---

## 6. Client tick and per-frame hooks
| hook | Fabric | NeoForge |
|---|---|---|
| client tick start/end | `ClientTickEvents.START_CLIENT_TICK / END_CLIENT_TICK` (`fabric-lifecycle-events-v1/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java:32,41`), `onStartTick(Minecraft)` / `onEndTick(Minecraft)` at HEAD / RETURN of `Minecraft.tick` (`mixin/event/lifecycle/client/MinecraftMixin.java:34,39`) | `ClientTickEvent.Pre / Post` on `NeoForge.EVENT_BUS` (`neoforge-api/.../client/event/ClientTickEvent.java`), fired in `Minecraft.tick` only after `gameLoadFinished` (`neoforge/.../Minecraft.java:1837,1942`) |
| level tick | `ClientTickEvents.START_LEVEL_TICK / END_LEVEL_TICK` (`onStartTick(ClientLevel)`, same file l.50,61) | `LevelTickEvent` `.Pre/.Post` (`neoforge-api/net/neoforged/neoforge/event/tick/LevelTickEvent.java:50,61`, `getLevel()`; also fires server-side, check `getLevel().isClientSide()`) |
| per frame, **before the camera update** | none, use the Camera mixin (1.4) | `ViewportEvent.ComputeCameraAngles` (inside `Camera.update`) or the same mixin |
| per frame, after update+extract | `LevelExtractionEvents.END_EXTRACTION` (RETURN of `LevelExtractor.extract`, `fabric-rendering-v1/.../api/client/rendering/v1/level/LevelExtractionEvents.java:70`); `LevelRenderEvents.*` (`.../level/LevelRenderEvents.java`) run during level rendering. Both only when a level renders | `RenderFrameEvent.Pre/Post(DeltaTracker)` wrap only `gameRenderer.render()` (`neoforge/.../Minecraft.java:1328-1330`). **Too late to move the camera for this frame** |

Recommendation: run physics and input on a fixed step, either from the client tick or with your own sub-stepping using `Util.getNanos()`. Sample the delayed pose for the camera **in the Camera mixin**. That point is per frame, identical on both loaders and before extraction. Write HUD data to a volatile snapshot and read it from the HUD element.

---

## 7. Wiring summary
- common `propwash.mixins.json` `"client"`: `CameraMixin` (alignWithEntity TAIL + calculateFov RETURN), `LevelExtractorMixin` (frustum refresh, 1.4), `GameRendererMixin` (renderItemInHand HEAD cancel, optional bobView HEAD cancel), optional `MouseHandlerMixin` (turnPlayer HEAD cancel). With `defaultRequire: 1`, a moved target crashes at startup, which is the behaviour we want.
- Fabric: add `"client": ["de.lardtom.propwash.PropwashFabricClient"]` to `fabric.mod.json` (only `main` exists today). The client initializer (`implements ClientModInitializer`) registers keys, the HUD (`attachElementBefore(CHAT)` + `replaceElement`), `ClientTickEvents`, `ClientPlayConnectionEvents.DISCONNECT` and `MenuScreens.register`.
- NeoForge: add a client-only entry class `@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)` with constructor `(IEventBus modBus, ModContainer container)` (pattern: `neoforge-api/.../client/ClientNeoForgeMod.java:95,101`). It adds mod-bus listeners for `RegisterKeyMappingsEvent`, `RegisterGuiLayersEvent` and `RegisterMenuScreensEvent`, and game-bus (`NeoForge.EVENT_BUS`) listeners for `ClientTickEvent.Pre/Post`, `ClientPlayerNetworkEvent.LoggingOut` (plus `RenderHandEvent` if you do not use the mixin).
- Keep every class that touches `Minecraft`, `Camera` or `GuiGraphicsExtractor` in a `client` package. Dedicated servers must never load them.

## 8. Pitfalls checklist
1. `Minecraft#setScreen` does not exist; use `mc.gui.setScreen`. HUD state lives in `mc.gui.hud`.
2. Key constants are SDL scancodes. A hard-coded GLFW number maps to the wrong key.
3. Text color without alpha is not drawn.
4. `ComputeFov` fires twice per frame (world + HUD). Prefer the `calculateFov` mixin.
5. `Entity#setXRot` clamps to ±90, so camera attitude has to go through the quaternion mixin.
6. With a Marker camera, hands still render and the pilot's body is hidden. Vanilla resets the camera entity on respawn/dimension change, so re-check it every tick.
7. NeoForge modded GUI layers ignore F1 (check `hud.isHidden()` yourself); on Fabric every position is F1-gated in 26.3, despite the `addFirst/addLast` javadoc.
8. Only one blur per frame. Never blur from the HUD, and never call `extractBackground` twice.
9. `init()` runs once per open, and resize rebuilds widgets. Keep state in fields, not in widgets.
10. Chunks load around the player. Long-range flights need a server-side chunk-loading solution (another area).
11. Section culling refreshes on Euler changes only: add the `LevelExtractor` mixin (1.4) or rolls leave holes.
12. The own drone model and the pilot's screen overlays (fire/water/portal) show up in the FPV view (1.6).
13. The FPV lens can end up inside blocks (the drone's collision box does not rotate, snow layers sink entities) and
    the 0.05 near plane of a 104–160° lens reaches 4–16 cm below the eye: guard the eye and the whole near-plane
    pyramid every frame (1.5a, `docs/design/fpv-eye-clearance.md`).

## Verification
Checked 2026-09-23 against `.ref/` (vanilla, neoforge, neoforge-api, fabric-api) plus the project files
(`common/build.gradle`, `common/src/main/resources/propwash.mixins.json`, `fabric.mod.json`, `docs/ARCHITECTURE.md`).

**Checked (≈210 claims):** every class/method/field/constructor and line reference in sections 0-8: `Gui`/`Hud`
screen+F1 API, `InputConstants` scancodes, input records, `Camera` (fields, `update`, `alignWithEntity`, `setRotation`,
`calculateFov`, `extractRenderState`, culling), `GameRenderer` (`mainCamera`, `update`, `extract`, hand/bob/outline),
`Minecraft` (`renderFrame`, `tick`, `handleKeybinds`, `pick`, camera entity), `LevelExtractor`, `LocalPlayer`, `Marker`/
`EntityTypes.MARKER`, sound listener, NeoForge `Camera` patch + `ViewportEvent`/`ClientHooks`, Fabric HUD API + impl
+ mixins, NeoForge GUI layers, `GuiGraphicsExtractor` drawing API, pipelines, GUI atlas JSON
(`vanilla/assets/minecraft/atlases/gui.json`, `gui/sprites`), `Screen`/widgets/layouts, `AbstractContainerScreen`,
`MenuScreens` + Fabric class tweaker + `RegisterMenuScreensEvent`, `KeyMapping`/`Category` (vanilla, NeoForge, Fabric),
tick/frame events on both loaders, `SDL_Init(32)`.

**Corrected:**
1. Frame order lives in `Minecraft.renderFrame` (not `runTick`); `extract(dt, advanceGameTime)`.
2. Euler `xRot/yRot` consumers were incomplete (added GameRenderer horizon, DebugCrosshair, `Camera.yaw()`).
3. Fabric `addFirst/addLast` are **not** F1-free in 26.3 (javadoc is stale); `addLast` is deferred behind F3/toasts.
4. HUD sketch put Fabric's `HudElement` into common (would not compile); now `attachElementBefore(CHAT)` in fabric.
5. `renderItemInHand` conditions were incomplete (also F1, sleeping, spectator).
6. Camera-entity reset sites: added `handleLogin`; `Minecraft.java:2230` is `updateLevelInEngines`.
7. `DroneBuild` was called "existing"; it is only planned (ARCHITECTURE.md).
8. `extractLabels` line ref; `imageWidth/imageHeight` are `final` (constructor only).
9. Line refs: `Hud.java:453→450`, NeoForge `Hud.java:243-271→242-278`, `KeyMapping.java:147→146-147`, container `init`/`extractContents`.
10. `Gui.setScreen` thread check logs only in the IDE; `init()` wording; "second blur" only for non-in-game screens.
11. `CalculatePlayerTurnEvent`/`ComputeFovModifierEvent` descriptions; Fabric key registration exception + category sorting.
12. Draining `consumeClick` does not stop held attack/use (`isDown`); added `setDown(false)` + loader hooks.
13. Fabric per-frame hook "none" → `LevelExtractionEvents.END_EXTRACTION`; NeoForge client `@Mod(dist=CLIENT)` and
    Fabric `ClientModInitializer` now backed by call sites instead of "verify in FML".

**Added (missing critical APIs):** section-frustum refresh pitfall + `LevelExtractor` mixin; own-drone-visible and
pilot-overlay pitfalls; disconnect hooks; `KeyEvent.shortcutKey()`; `EditBox.addFormatter`; workbench: `Slot.getNoItemIcon`,
`clickMenuButton` + `handleInventoryButtonClick` round trip, `SmithingScreen` render-state preview, NeoForge
`IMenuTypeExtension.create` / `openMenu(provider, buf)`, Fabric `ExtendedMenuType` constructor, full `WorkbenchScreen` sketch.

**Open questions:**
1. Fabric HUD vs F1: code says every position is F1-gated, javadoc says otherwise. Confirm in game, and decide whether the
   FPV OSD should survive F1 (Fabric then needs a mixin on `Hud.extractRenderState`).
2. JOML (1.10.9) and MixinExtras signatures are outside `.ref`; common compiles against `mixinextras-common:0.5.3`
   while NeoForge ships 0.5.4 (`neoforge-26.3.0.10-beta.pom`). `@ModifyExpressionValue` must be smoke-tested on both loaders.
3. Marker vs drone entity as camera entity (depends on the entity/network area), and whether a custom `DroneRenderState`
   renders correctly through `GuiEntityRenderer` (needs the drone `EntityRenderer` from the entity-render area). Both need an in-game test.


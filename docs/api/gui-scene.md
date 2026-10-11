# 3D scenes inside a GUI (MC 26.3): picture-in-picture for blocks, entities and items

> **Projekt-Layout:** Diese Notiz ist nach der Foundation geschrieben; Paket- und Klassennamen stimmen mit dem Code
> (`guide.client.render.*`, `registry.client.ClientRegistrar#pictureInPictureRenderer`). Code-Beispiele enthalten
> erklärende Kommentare, im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`).

All paths are relative to `.ref/`. `vanilla/` = vanilla 26.3 (Mojang names), `neoforge/` = NeoForge-patched MC,
`neoforge-api/` = NeoForge API, `fabric-api/<module>/` = Fabric API sources.

Used by the flight school (`de.lardtom.propwash.guide`): one small diorama (blocks + drone + pilot + gates + items +
highlight boxes) rendered into a rectangle of a screen, with 2D callouts drawn on top at projected 3D points.

## 0. TL;DR

- GUI 3D in 26.3 is **picture-in-picture (PIP)**: a `PictureInPictureRenderState` (a record with the target rectangle,
  a scale and the data you need) is queued during *extract*; later the `GuiRenderer` hands it to the
  `PictureInPictureRenderer` registered for **exactly that state class**, which submits geometry into its own
  `SubmitNodeStorage`, renders it into an offscreen color+depth texture of `rect × guiScale` pixels and blits the
  texture back into the GUI layer where the state was queued.
- `GuiGraphicsExtractor#entity(...)` is the vanilla PIP for a **single** entity (own texture, own depth). Several calls
  per frame work on both loaders (pools), but they do **not** share a depth buffer, so a diorama of many blocks and
  entities needs **one custom PIP** that submits everything.
- A custom PIP needs (a) registration per loader and (b) a way to queue the state. Propwash: (a)
  `ClientRegistrar.pictureInPictureRenderer(StateClass.class, Renderer::new)` in the client `init()`, (b)
  `((GuiLayerAccess) graphics).propwash$renderState().addPicturesInPictureState(state)` (ui mixin, both loaders).
- Inside `renderToTexture` you get a `PoseStack` already set to `translate(w/2, getTranslateY) · scale(k, k, -k)` with
  `k = guiScale × state.scale()` (GUI pixels per block × gui scale) and a `SubmitNodeCollector`. Blocks:
  `BlockModelResolver` + `BlockModelRenderState#submit`. Entities: `EntityRenderDispatcher#submit(state, camera, 0,0,0,
  poseStack, collector)` with a hand-filled render state (no `Entity` needed). Items: `ItemModelResolver#updateForTopItem`
  + `ItemStackRenderState#submit`. Lines/boxes: `submitCustomGeometry`.
- The vanilla PIP projection only keeps ±1000 texture pixels of depth. A zoomed diorama needs more: set your own
  orthographic projection in `renderToTexture` (§4).

## 1. The PIP pipeline (vanilla)

`vanilla/net/minecraft/client/gui/render/pip/PictureInPictureRenderer.java`
```java
public abstract class PictureInPictureRenderer<T extends PictureInPictureRenderState> implements AutoCloseable {
    public void prepare(T state, GuiRenderState gui, FeatureRenderDispatcher dispatcher, int guiScale)   // :40
    protected void blitTexture(T state, GuiRenderState gui)
    protected boolean textureIsReadyToBlit(T state)            // :117, default false → re-render every frame
    protected float getTranslateY(int height, int guiScale)    // :121, default height (origin at the BOTTOM edge)
    public abstract Class<T> getRenderStateClass();
    protected abstract void renderToTexture(T state, PoseStack poseStack, SubmitNodeCollector collector);   // called :54
    protected abstract String getTextureLabel();
}
```
`prepare` (:40-70): texture `(x1-x0)·guiScale × (y1-y0)·guiScale`, depth cleared to **0.0** (:112, reverse Z, see §4),
`projection.setupOrtho(-1000, 1000, w, h, true)` (:113), `poseStack.translate(w/2, getTranslateY(h, guiScale), 0)`,
`poseStack.scale(guiScale·scale, guiScale·scale, -guiScale·scale)`, then `renderToTexture`, then
`FeatureRenderDispatcher.prepareFrame(storage)` + `renderAllFeatures` into the offscreen target, then
`blitTexture` → `GuiRenderState#addBlitToCurrentLayer` with `RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA` and
v flipped (`0,1,1,0`). The texture is cleared transparent, so whatever you drew as background before the state shows
through.

State interface `vanilla/net/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState.java`:
`x0() x1() y0() y1()` (absolute GUI coords, **the `graphics.pose()` matrix is not applied**), `float scale()`,
`default Matrix3x2fc pose()` (identity), `@Nullable ScreenRectangle scissorArea()`, plus `bounds()` from `ScreenArea`.
Helper `PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor)`. Vanilla example: `GuiEntityRenderState`
(record) + `GuiEntityRenderer` (`vanilla/.../gui/render/pip/GuiEntityRenderer.java`, overrides `getTranslateY` to `h/2`
and calls `lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)`).

Dispatch: `GuiRenderState#addPicturesInPictureState` (`vanilla/.../state/gui/GuiRenderState.java:76`) puts the state
into the layer node chosen by its bounds (same rules as fills: above earlier intersecting elements), and
`GuiRenderer#preparePictureInPicture` looks the renderer up **by `state.getClass()`** (exact class, so make the state a
`final` record). The blit lands in that node, so 2D drawn **after** queuing the state and overlapping it (callouts,
key hints) is drawn above the 3D picture, and a background filled **before** it is below.

## 2. Registration per loader

| | API | Notes |
|---|---|---|
| Fabric | `PictureInPictureRendererRegistry.register(ctx -> new MyRenderer())` (`fabric-api/fabric-rendering-v1/.../v1/PictureInPictureRendererRegistry.java:40`) | in the client entrypoint; frozen when `GuiRenderer` is built (`PictureInPictureRendererRegistryImpl.onReady`, :61). Keyed by `getRenderStateClass()`. Extra renderers per class come from the factory (`PictureInPictureRendererPool#substitute`, :34) |
| NeoForge | mod bus `RegisterPictureInPictureRenderersEvent#register(Class<T>, Supplier<PictureInPictureRenderer<T>>)` (`neoforge-api/.../client/event/RegisterPictureInPictureRenderersEvent.java:37`) | fired from `ClientHooks.gatherPictureInPictureRenderers` (:947) while the `GameRenderer` is built. Pool per class (`client/gui/PictureInPictureRendererPool.java`): reuse by state `equals` first, then by texture size (`PictureInPictureRenderer#canBeReusedFor`, `neoforge/.../pip/PictureInPictureRenderer.java:157`) |

Propwash wraps both: `ClientRegistrar.pictureInPictureRenderer(Class<T>, Supplier<? extends PictureInPictureRenderer<T>>)`
(client `init()` only, one renderer per state class); the loader glue flushes `freezePictureInPictureRenderers()`.

Queueing without a loader API: vanilla keeps `GuiGraphicsExtractor.guiRenderState` private (`vanilla/.../gui/GuiGraphicsExtractor.java:95`);
NeoForge adds `submitPictureInPictureRenderState` (`neoforge/.../GuiGraphicsExtractor.java:1304`), Fabric widens the field
(TAW). In common use the ui mixin: `if (graphics instanceof GuiLayerAccess access) access.propwash$renderState()
.addPicturesInPictureState(new SceneRenderState(frame, x0, y0, x1, y1, pixelsPerBlock, access.propwash$scissor()));`.

## 3. What to submit inside `renderToTexture`

All of these work without a level and without an `Entity` (also on the title screen):

```java
// blocks — BlockModelResolver has a public ctor; Minecraft does not expose its instance
BlockModelResolver resolver = new BlockModelResolver(Minecraft.getInstance().getModelManager());   // block/BlockModelResolver.java:17
BlockModelRenderState block = new BlockModelRenderState();
resolver.update(block, blockState, BlockDisplayContext.create());   // :21, tints from BlockTintSource(blockState), light emission
poseStack.translate(x, y, z);                                        // model origin = block min corner
block.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);   // BlockModelRenderState.java:72
// render type Sheets.cutoutBlockItemSheet()/translucentBlockItemSheet(): lit like an item, all quads (no face culling)

// entities: fill a render state by hand, then dispatch (renderer chosen by state.entityType, avatars by skin model)
AvatarRenderState pilot = new AvatarRenderState();                  // skin, bodyRot, xRot, ageInTicks, headItem, rightHandItem…
pilot.lightCoords = LightCoordsUtil.FULL_BRIGHT;
minecraft.getItemModelResolver().updateForTopItem(pilot.headItem, goggles, ItemDisplayContext.HEAD, minecraft.level, null, 0);
minecraft.getEntityRenderDispatcher().submit(pilot, new CameraRenderState(), 0, 0, 0, poseStack, collector);  // EntityRenderDispatcher.java:143
// keep shadowPieces empty (the dispatcher would add a shadow), nameTag null, outlineColor 0

// items
ItemStackRenderState item = new ItemStackRenderState();
minecraft.getItemModelResolver().updateForTopItem(item, stack, ItemDisplayContext.GROUND, minecraft.level, null, 0);  // item/ItemModelResolver.java:41
item.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);

// own geometry (outline beams): collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(tex), (pose, buffer) -> …)
```
Lighting: call `Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)` first
(`vanilla/com/mojang/blaze3d/platform/Lighting.java:75`; light directions `(0.2,-1,1)` and `(-0.2,-1,0)` in the PIP's
Y-down space = from above/front). The dispatcher/`submit` calls copy the pose, so one `PoseStack` can be reused; Propwash
gives every element its own `PoseStack` (`local.last().set(scenePose)`) so an exception in one element cannot unbalance the
stack for the next.

Render states are mutable objects that the collector keeps references to until the frame is rendered; extract and render
run on the render thread one after the other, so a state may be refilled in the next frame's extract, but not while the
current frame is still pending.

## 4. Camera, depth range and 2D overlay math

`Projection#getMatrix` (`vanilla/net/minecraft/client/renderer/Projection.java:63-77`) swaps near and far
(`setOrtho(0, w, invertY ? h : 0, invertY ? 0 : h, zFar, zNear)`), pipelines test `GREATER_THAN_OR_EQUAL`
(`DepthStencilState.DEFAULT`, `vanilla/com/mojang/renderpearl/api/pipeline/DepthStencilState.java:8`) and the PIP clears
depth to 0: **reverse Z — larger texture-space z is nearer**. With the PIP base pose `scale(k, k, -k)` a model-space
point with smaller z ends up nearer.

A right-handed orbit camera (world Y up, camera looking at `target`, `yaw`/`pitch` in degrees) maps into the PIP like this:
```java
Quaternionf view = new Quaternionf().rotationX(toRadians(pitch)).rotateY(toRadians(-yaw));   // world → standard eye space
poseStack.rotate(new Quaternionf().rotationX((float) Math.PI).mul(view));                     // eye (x right, y up, z to viewer) → PIP (y down, z away)
poseStack.translate(-target.x, -target.y, -target.z);
// yaw 0 = camera on +Z looking north, +X right; both rotations are proper, so face culling stays correct
```
The matching 2D projection for callouts (GUI pixels, `ppb` = `state.scale()`):
`eye = view.transform(p - target)`, `guiX = cx + eye.x·ppb`, `guiY = cy - eye.y·ppb`, `eye.z` = depth (larger = nearer).
`(cx, cy)` is **not** necessarily the PIP centre: `SceneFraming` shifts the subject into the free area of the stage. The
vanilla base pose (`translate(w/2, getTranslateY)`) knows nothing about that shift, so a renderer that keeps the base pose
draws the scene somewhere else than the 2D overlay expects (flight school bug: callout dots pointed into empty space).
Propwash therefore replaces the base pose completely: `SceneRenderer` calls `poseStack.setIdentity()` and then
`SceneProjection.applyRaster(x0, y0, pixelScale, sink)` with `pixelScale = textureWidth / (x1 - x0)` (= GUI scale; texture
pixels, y down, origin at the PIP's top left):
```java
translate((cx - x0)·s, (cy - y0)·s, 0) · scale(ppb·s, ppb·s, -ppb·s) · rotate(rotX(π)·view) · translate(-target)
```
`SceneProjection.project` (callouts, key hints, actor boxes) and `applyRaster` (renderer) are one class; `rasterMatrix` +
`rasterToGui` build the same transform as a `Matrix4f`. `SceneRasterTest` checks both against each other for all scenes, window
sizes, GUI scales 1–4, aspect ratios and orbits; the renderer stores the pose it really used in `SceneFrame.rendered()`, and
the selftest „Hinweis-Anker treffen ihr Ziel“ compares every drawn anchor with that pose at several real window sizes.
Window/framebuffer size and Retina density never enter the math: the PIP texture is `(x1-x0)·guiScale` pixels and is blitted
back onto exactly `x0..x1` GUI pixels.

Depth range: vanilla's `setupOrtho(-1000, 1000, …)` covers only ±1000 texture pixels, i.e. ±1000/(guiScale·ppb) blocks
(±1.3 blocks at gui scale 3 and 250 px/block). `renderToTexture` runs after `prepareTexturesAndProjection` and before
`renderAllFeatures`, so a renderer may replace the projection:
```java
Matrix4f base = poseStack.last().pose();                 // translate(w/2, h/2) · scale(k,k,-k)
float w = base.m30() * 2, h = base.m31() * 2;            // texture size (getTranslateY = h/2)
projection.setupOrtho(-40000, 40000, w, h, true);        // own net.minecraft.client.renderer.Projection
RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);  // own ProjectionMatrixBuffer, closed in close()
```
`GuiRenderer#draw` sets the GUI projection again afterwards (`vanilla/.../gui/render/GuiRenderer.java:189`), and every
other PIP sets its own in `prepareTexturesAndProjection`. Do **not** squash z with a non-uniform `scale` instead: the
normal matrix follows the squashed geometry and the directional GUI lighting goes flat.

## 5. Pitfalls

1. The state class must match the registered class exactly (lookup by `getClass()`); a subclass silently renders nothing.
2. Registration only in the client `init()`; Fabric throws once the `GuiRenderer` exists, NeoForge only reads the event once.
3. `x0..y1` are absolute: the `graphics.pose()` translation/scale is ignored (same as `graphics.entity`).
4. `textureIsReadyToBlit` false → rendered every frame (needed for animation; cost = one offscreen pass per frame).
5. Vanilla (without loader pools) shares one renderer per class per frame; both loaders pool, but keep to one scene state
   per frame anyway.
6. NeoForge creates the PIP depth texture in the main target's depth format (`neoforge/.../pip/PictureInPictureRenderer.java:109`);
   nothing to do, just don't assume `D32_FLOAT`.
7. Entity renderers that read world state in `submit` (instead of `extractRenderState`) break here. The drone
   (`DroneVisuals.submit`), gates (`GateRenderer#submit`) and avatars are fine.
8. `EntityRenderDispatcher#submit` adds fire and shadow from the state: keep `displayFireAnimation` false and
   `shadowPieces` empty.

## Verification

Checked against `.ref` (vanilla 26.3, NeoForge-patched MC, neoforge-api, fabric-api): `PictureInPictureRenderer`
(`prepare`, `getTranslateY`, `textureIsReadyToBlit`, clear to 0, `setupOrtho(-1000, 1000, …, true)`), `PictureInPictureRenderState`,
`GuiEntityRenderState`/`GuiEntityRenderer`, `GuiRenderState#addPicturesInPictureState`/`forEachPictureInPicture`,
`GuiRenderer#preparePictureInPicture` (vanilla and NeoForge two-pass pool), `GuiRenderer#draw` projection reset,
Fabric `PictureInPictureRendererRegistry`/`Impl`/`Pool`, NeoForge `RegisterPictureInPictureRenderersEvent`,
`PictureInPictureRendererRegistration`, `PictureInPictureRendererPool`, `canBeReusedFor`, depth-format patch,
`GuiGraphicsExtractor#entity`/`submitPictureInPictureRenderState`, `Projection#getMatrix`, `DepthStencilState.DEFAULT`,
`ProjectionMatrixBuffer`, `RenderSystem#setProjectionMatrix`, `BlockModelResolver`, `BlockModelRenderState#submit`,
`BlockDisplayContext#create`, `BlockStateModelWrapper#update` (tints), `EntityRenderDispatcher#submit`/`getRenderer`,
`AvatarRenderState`/`ArmedEntityRenderState`/`LivingEntityRenderState` fields, `ItemModelResolver#updateForTopItem`,
`Lighting#setupFor`. Compiles against the fastcheck classpath of all three modules.

**Not verified in game (no client start in this workstream):** the actual picture (lighting look of blocks next to
entities, translucent prop discs against blocks, pilot skin), the projection override on the Vulkan backend of
renderpearl, and performance with many scene elements.

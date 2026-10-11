# Entity rendering in 26.3 (Propwash notes)

> **Projekt-Layout veraltet:** Die eine Mixin-Config `propwash.mixins.json` (stattdessen je Modul `propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`), Registrierungs-Klassen (`PropwashItems`, `PropwashEntities`, `PropwashComponents`, `RegList.Ref`, …), `CommonClass`, das Paket `net` (heute `network`) und Loader-Einstiege in dieser Notiz stammen aus der Zeit vor der Foundation. Kommentare in den Code-Beispielen sind nur Erklärung: im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`). Maßgeblich sind `docs/CONVENTIONS.md` und `docs/FOUNDATION.md` (Kasten „Abweichungen von docs/api“). Die Minecraft-/Loader-APIs hier stimmen weiterhin.

All paths are relative to `.ref/`. `vanilla/` = vanilla 26.3 (Mojang names), `neoforge/` = NeoForge-patched MC,
`neoforge-api/` = NeoForge API, `fabric-api/<module>/` = Fabric API sources.

## Overview: what changed since 1.21.x

- The render pass is split into **extract** (game state → plain `EntityRenderState`) and **submit** (state →
  draw commands queued in a `SubmitNodeCollector`). `render(..., MultiBufferSource, ...)` no longer exists on
  `EntityRenderer`. You never get a `VertexConsumer` in `submit`. The queued nodes are drawn later by
  `FeatureRenderDispatcher` (`vanilla/net/minecraft/client/renderer/feature/*`).
- `GuiGraphics` is now **`GuiGraphicsExtractor`**, and `Screen.render` is now `extractRenderState` / `extractBackground`.
  3D-in-GUI goes through picture-in-picture (PIP) renderers.
- `LightTexture.FULL_BRIGHT` is now `net.minecraft.util.LightCoordsUtil.FULL_BRIGHT` (`15728880`).
  `ResourceLocation` is now `Identifier`. `RenderType` factories live in `net.minecraft.client.renderer.rendertype.RenderTypes`.
- `PoseStack.mulPose(Quaternion)` is replaced by `rotate(Quaternionfc)`, `rotate(Axis, radians)` and `rotateDegrees(Axis, deg)`.
- Fabric: `EntityRendererRegistry` is deprecated, and `EntityModelLayerRegistry` **does not exist** (it is now `ModelLayerRegistry`).
- Other renames you will hit in this area: vanilla entity-type constants are in `net.minecraft.world.entity.EntityTypes`
  (`EntityTypes.ARMOR_STAND`, `vanilla/net/minecraft/world/entity/EntityTypes.java:187`), `Util` is `net.minecraft.util.Util`,
  `RenderType` is `net.minecraft.client.renderer.rendertype.RenderType`, `CameraRenderState` is
  `net.minecraft.client.renderer.state.level.CameraRenderState`, `MouseButtonEvent` is `net.minecraft.client.input.MouseButtonEvent`.

**Recommendation for the drone:** build it from **part item models** (JSON, `assets/propwash/items/...`) and draw each
part with its own `ItemStackRenderState` at its arm position (§3). The workbench preview reuses the **same
`DroneRenderState` + `QuadcopterRenderer`** through vanilla `GuiGraphicsExtractor.entity(...)` (§7). That needs no custom
PIP renderer and no access wideners. Use `ModelPart`/`LayerDefinition` (§4) only for things that must animate
per-cube, such as the gates.

---

## 1. EntityRenderer / EntityRenderState pipeline

`vanilla/net/minecraft/client/renderer/entity/EntityRenderer.java`
```java
public abstract class EntityRenderer<T extends Entity, S extends EntityRenderState>
protected EntityRenderer(EntityRendererProvider.Context context)
protected float shadowRadius;  protected float shadowStrength = 1.0F;
public abstract S createRenderState();
public final S createRenderState(T entity, float partialTicks)      // new S each frame → extract → finalize
public void extractRenderState(T entity, S state, float partialTicks)
public void submit(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera)
public Vec3 getRenderOffset(S state)
protected AABB getBoundingBoxForCulling(T entity, float partialTicks)
protected boolean affectedByCulling(T entity)
protected int getBlockLightLevel(T entity, BlockPos blockPos)      // override for self-lit things
protected boolean shouldShowName(T entity, double distanceToCameraSq)
```
The base `extractRenderState` fills `entityType`, `x/y/z` (lerped from `xOld..getX()`), `ageInTicks = tickCount + partialTicks`,
`isInvisible`, bounding box, name tag, leash, `outlineColor` (glowing) and `lightCoords`. The shadow is **not** set there:
`finalizeRenderState(T, S)` → `extractShadow` fills `shadowRadius/shadowPieces` after your extract. The base `submit` draws
leash and name tag only, so **call `super.submit`**. Fire and shadow are submitted by `EntityRenderDispatcher.submit`
itself after your `submit` returns (`vanilla/.../entity/EntityRenderDispatcher.java:143-172`).

`vanilla/net/minecraft/client/renderer/entity/state/EntityRenderState.java` has these public fields:
`entityType, x, y, z, ageInTicks, boundingBoxWidth/Height, eyeHeight, distanceToCameraSq, isInvisible, isDiscrete,
displayFireAnimation, lightCoords (=15728880 default), outlineColor, nameTag, shadowRadius, shadowPieces`.
NeoForge adds `public float partialTick` and makes the class extend `net.neoforged.neoforge.client.renderstate.BaseRenderState`
(`neoforge/net/minecraft/client/renderer/entity/state/EntityRenderState.java`, set in `neoforge/.../entity/EntityRenderer.java#extractRenderState`),
so **don't use it from `common`**. Use `ageInTicks` or store your own field.

Dispatch (`vanilla/.../entity/EntityRenderDispatcher.java`):
`submit(S renderState, CameraRenderState camera, double x, double y, double z, PoseStack, SubmitNodeCollector)`
looks the renderer up **by `renderState.entityType`** (`getRenderer(S)`, line ~112). The world loop is
`vanilla/net/minecraft/client/renderer/extract/LevelExtractor.java#extractVisibleEntities` (:260-275). It skips
`camera.entity()` unless detached (or sleeping), and in **vanilla/Fabric** it skips the `LocalPlayer` unless the player is the camera entity.
**NeoForge patches that condition** (`neoforge/net/minecraft/client/renderer/extract/LevelExtractor.java:283`):
`|| (entity == minecraft.player && !minecraft.player.isSpectator())` ("render local player entity when it is not the camera entity").
So in FPV (camera = drone) the drone itself is hidden on both loaders, but the pilot's own body is **visible on NeoForge and
hidden on Fabric**. Fabric API does not touch this (`fabric-api/fabric-rendering-v1/.../mixin/client/rendering/LevelExtractorMixin.java`
only injects into `extractBlockOutline`/`extract`/`allChanged`).

**Vanilla example (smallest complete one): `vanilla/.../entity/AbstractBoatRenderer.java`** (abridged: the hurt-wobble and
bubble-column rotations and `submitTypeAdditions` are omitted)
```java
public void submit(BoatRenderState state, PoseStack poseStack, SubmitNodeCollector c, CameraRenderState camera) {
    poseStack.pushPose();
    poseStack.translate(0.0F, 0.375F, 0.0F);
    poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
    poseStack.scale(-1.0F, -1.0F, 1.0F);      // ModelPart models are authored upside down
    poseStack.rotateDegrees(Axis.YP, 90.0F);
    c.submitModel(this.model(), state, poseStack, this.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
    poseStack.popPose();
    super.submit(state, poseStack, c, camera);
}
public void extractRenderState(AbstractBoat entity, BoatRenderState state, float partialTicks) {
    super.extractRenderState(entity, state, partialTicks);
    state.yRot = entity.getYRot(partialTicks); ...
}
```
Items in an entity: `vanilla/.../entity/ItemFrameRenderer.java` (see §3).

`EntityRendererProvider.Context` (`vanilla/.../entity/EntityRendererProvider.java`) has these getters:
`getItemModelResolver()`, `getBlockModelResolver()`, `bakeLayer(ModelLayerLocation)`, `getModelSet()`,
`getEntityRenderDispatcher()`, `getFont()`, `getSprites()`, `getAtlas(Identifier)`, `getResourceManager()`.

`LivingEntityRenderer` + `RenderLayer` ("feature renderers" in the old sense, `vanilla/.../entity/layers/`) apply only to
`LivingEntity`. Our drone and gates are plain `Entity`, so all their drawing goes in `submit`.

## 2. SubmitNodeCollector (what you can queue)

`vanilla/net/minecraft/client/renderer/SubmitNodeCollector.java` extends `OrderedSubmitNodeCollector` and adds
`OrderedSubmitNodeCollector order(int order)`. The following signatures come from `vanilla/.../OrderedSubmitNodeCollector.java`:
```java
<S> void submitModel(Model<? super S> model, S state, PoseStack, RenderType, int lightCoords, int overlayCoords,
                     int tintedColor, @Nullable UvMapping uvMapping, int outlineColor)
default <S> void submitModel(Model<? super S>, S, PoseStack, RenderType, int light, int overlay, int outlineColor)   // tint -1
default <S> void submitModel(Model<? super S>, S, PoseStack, Identifier texture, int light, int overlay, int outlineColor) // model.renderType(tex)
default void submitModelPart(ModelPart, PoseStack, RenderType, int light, int overlay, @Nullable UvMapping, int tintedColor, int outlineColor)
void submitItem(PoseStack, ItemDisplayContext, int light, int overlay, int outlineColor, int[] tintLayers, ItemQuads, ItemStackRenderState.FoilType)
void submitBlockModel(PoseStack, RenderType, List<BlockStateModelPart>, int[] tintLayers, int light, int overlay, int outlineColor)
void submitCustomGeometry(PoseStack, RenderType, SubmitNodeCollector.CustomGeometryRenderer)  // (pose, VertexConsumer) -> {}
void submitText(...); void submitNameTag(...); void submitShadow(...); void submitLeash(...)
```
There is also `default <S> void submitModel(Model<S>, S, PoseStack, int light, int overlay, int tint, SpriteId, SpriteGetter, int outline)`
(sprite-atlas variant) and `submitCrumblingOverlay`, `submitMovingBlock`, `submitShapeOutline`, `submitFlame`, `submitTextBackground`.
The collector you receive is `SubmitNodeStorage` (`vanilla/.../SubmitNodeStorage.java`): the un-ordered methods forward to `order(0)`,
and each order is its own `SubmitNodeCollection` (`Int2ObjectAVLTreeMap`, iterated ascending).
Implementation (`vanilla/.../SubmitNodeCollection.java` ~220/376/414): model/item/custom submits **copy `poseStack.last()`**
(shadow/leash/text copy the `Matrix4f`), so you can reuse one `PoseStack` with push/pop freely. Model submits call
`model.setupAnim(state)` **at draw time** (`vanilla/.../feature/ModelFeatureRenderer.java#prepareModel` :28-40), not at submit time.
`submitModel` routes by render type: blending → `translucentModels` phase, otherwise `solid` (SubmitNodeCollection.java:231-244).
Loader-only extensions (never from common): NeoForge `submitMultiLayerBlockModel`/`submitSpecial`
(`neoforge-api/net/neoforged/neoforge/client/extensions/OrderedSubmitNodeCollectorExtension.java:28,41`); Fabric
`FabricOrderedSubmitNodeCollector#submitCustom(SubmitRenderPhase<T>, T)`
(`fabric-api/fabric-rendering-v1/net/fabricmc/fabric/api/client/rendering/v1/FabricOrderedSubmitNodeCollector.java:40`).

## 3. Drone from part item models (recommended)

### 3.1 APIs
`vanilla/net/minecraft/client/renderer/item/ItemModelResolver.java`
```java
public void updateForLiving(ItemStackRenderState out, ItemStack, ItemDisplayContext, LivingEntity)
public void updateForNonLiving(ItemStackRenderState out, ItemStack, ItemDisplayContext, Entity)   // seed = entity.getId()
public void updateForTopItem(ItemStackRenderState out, ItemStack, ItemDisplayContext, @Nullable Level, @Nullable ItemOwner, int seed) // clear()+append
public void appendItemLayers(ItemStackRenderState out, ItemStack, ItemDisplayContext, @Nullable Level, @Nullable ItemOwner, int seed) // no clear
```
It resolves `item.get(DataComponents.ITEM_MODEL)` → `ModelManager.getItemModel(Identifier)` (public,
`vanilla/.../client/resources/model/ModelManager.java:80`) → `ItemModel.update(...)`. Get the resolver with
`Minecraft.getInstance().getItemModelResolver()` or `context.getItemModelResolver()`.

`vanilla/net/minecraft/client/renderer/item/ItemStackRenderState.java`
```java
public void submit(PoseStack, SubmitNodeCollector, int lightCoords, int overlayCoords, int outlineColor)
public boolean isEmpty();  public void clear();  public AABB getModelBoundingBox();
```
Each layer applies `ItemTransform` for the display context and then the JSON `transformation`. With
`ItemDisplayContext.NONE` the transform is `ItemTransform.NO_TRANSFORM` (`default` branch of
`vanilla/.../resources/model/cuboid/ItemTransforms.java#getTransform`, applied via `ModelRenderProperties#applyToLayer`), which only does
`translate(-0.5,-0.5,-0.5)` (`vanilla/.../resources/model/cuboid/ItemTransform.java#apply`). So **model pixel (8,8,8) lands on the current pose origin,
and 16 px = 1 block**. The order inside a layer is `itemTransform.apply(...)` (ends with the -0.5 translate) and **then**
`mulPose(localTransform)` (`ItemStackRenderState.java:242-245`), so a JSON `"transformation"` acts in raw model space
(pivot = model pixel (0,0,0), translation in blocks), not around (8,8,8).
The per-quad render type (cutout/translucent item sheet, items vs blocks atlas) is chosen at bake time from the sprite
(`vanilla/.../resources/model/cuboid/FaceBakery.java#computeMaterialTransparency` → `.../geometry/BakedQuad.java` `MaterialInfo.of`
→ `Sheets.cutoutItemSheet()/translucentItemSheet()`), and `ItemQuads#split` separates solid/translucent lists.
To force translucency, use `"textures": {"blur": {"sprite": "propwash:item/part/prop_blur", "force_translucent": true}}`
(`vanilla/.../resources/model/sprite/Material.java` `FULL_CODEC`, parsed by `.../sprite/TextureSlots.java#parseEntry`; vanilla sample
`vanilla/assets/minecraft/models/block/brown_stained_glass_pane_side.json`).

Vanilla call site (`vanilla/.../entity/ItemFrameRenderer.java`):
```java
// extract:
this.itemModelResolver.updateForNonLiving(state.item, itemStack, ItemDisplayContext.FIXED, entity);
// submit:
poseStack.scale(0.5F, 0.5F, 0.5F);
state.item.submit(poseStack, submitNodeCollector, lightVal, OverlayTexture.NO_OVERLAY, state.outlineColor);
```
The state holds `public final ItemStackRenderState item = new ItemStackRenderState();`
(`vanilla/.../entity/state/ItemFrameRenderState.java`). `ItemEntityRenderer` does the same through
`state.extractItemGroupRenderState(...)` and `item.submit(...)` in a loop with different translations.

### 3.2 Assets
Client item definitions are loaded from **every** `assets/<ns>/items/*.json`, whether or not an item is registered
(`vanilla/.../resources/model/ClientItemInfoLoader.java`: `FileToIdConverter.json("items")`). That leaves two options:

- **Real part items with context-dependent models.** The part item (e.g. `propwash:motor_2306`) shows its icon in
  inventories and its 3D mount model on the drone:
```json
// assets/propwash/items/motor_2306.json
{ "model": { "type": "minecraft:select", "property": "minecraft:display_context",
    "cases": [ { "when": "none", "model": { "type": "minecraft:model", "model": "propwash:part/motor_2306" } } ],
    "fallback": { "type": "minecraft:model", "model": "propwash:item/motor_2306" } } }
```
  (`select` + `display_context`: `vanilla/.../item/properties/select/SelectItemModelProperties.java`; `"when"` accepts one value or a list.)
- **Render-only ids** (e.g. `assets/propwash/items/render/frame_5in.json`) set on a cached dummy stack via
  `stack.set(DataComponents.ITEM_MODEL, id)`. The resolver only reads `ITEM_MODEL`.

Author every part model (`assets/propwash/models/part/*.json`, textures under `textures/item/...`) with its **mount
point at pixel (8,8,8)**: the motor's axis is centred there, and the frame's centre of mass is centred there. Element rotations
accept arbitrary Euler angles: `"rotation": {"origin":[8,8,8], "x":0, "y":45, "z":0}` (or the old `"axis"`+`"angle"` form, optional
`"rescale"`) (`vanilla/.../resources/model/cuboid/CuboidModelElement.java` :93-117). Element `from/to` must stay within -16..32 px
(:161-164). A model's quads must all come from **one atlas**, either `items` (`textures/item/**`, `vanilla/assets/minecraft/atlases/items.json`)
or `blocks` (`textures/block/**`, `.../atlases/blocks.json`). Otherwise baking throws
(`vanilla/.../renderer/item/CuboidItemModelWrapper.java#validateAtlasUsage` :108-124).

### 3.3 Java sketch (common, client package)
```java
public final class DroneRenderState extends EntityRenderState {
    public final Quaternionf orientation = new Quaternionf();
    public final ItemStackRenderState frame = new ItemStackRenderState(), stack = new ItemStackRenderState(),
            camera = new ItemStackRenderState(), vtx = new ItemStackRenderState(), battery = new ItemStackRenderState();
    public final ItemStackRenderState[] motors = new ItemStackRenderState[4], props = new ItemStackRenderState[4];
    public final Vector3f[] motorPos = new Vector3f[4];   // blocks, frame space (from the frame part's data)
    public final float[] rotorAngle = new float[4];        // radians
    public boolean armed;
    { for (int i = 0; i < 4; i++) { motors[i] = new ItemStackRenderState(); props[i] = new ItemStackRenderState(); motorPos[i] = new Vector3f(); } }
}

/** Shared by the entity renderer AND the workbench preview. No Entity needed. */
public final class DroneVisuals {
    public static void fill(DroneRenderState s, DroneBuild b, ItemModelResolver r, @Nullable Level level,
                            @Nullable ItemOwner owner, int seed, float ageTicks, boolean armed) {
        ItemDisplayContext ctx = ItemDisplayContext.NONE;
        r.updateForTopItem(s.frame, b.frameStack(), ctx, level, owner, seed);
        r.updateForTopItem(s.battery, b.batteryStack(), ctx, level, owner, seed);
        // ... stack, camera, vtx
        for (int i = 0; i < 4; i++) {
            r.updateForTopItem(s.motors[i], b.motorStack(i), ctx, level, owner, seed);
            r.updateForTopItem(s.props[i], b.propStack(i, armed), ctx, level, owner, seed); // blur model when armed
            s.motorPos[i].set(b.frame().motorMount(i));
            float dir = (i == 0 || i == 3) ? 1f : -1f;                                        // props-in / props-out
            s.rotorAngle[i] = armed ? dir * ageTicks * 2.3f : i * 0.7f;
        }
        s.armed = armed;
    }
}

public final class QuadcopterRenderer extends EntityRenderer<QuadcopterEntity, DroneRenderState> {
    private final ItemModelResolver items;
    public QuadcopterRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.items = ctx.getItemModelResolver(); this.shadowRadius = 0.25F; }
    @Override public DroneRenderState createRenderState() { return new DroneRenderState(); }
    @Override public void extractRenderState(QuadcopterEntity e, DroneRenderState s, float pt) {
        super.extractRenderState(e, s, pt);
        e.renderAttitude(pt, s.orientation);            // our slerp(prev, cur, pt)
        DroneVisuals.fill(s, e.build(), items, e.level(), e, e.getId(), s.ageInTicks, e.isArmed());
    }
    @Override public void submit(DroneRenderState s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
        int light = s.lightCoords, ov = OverlayTexture.NO_OVERLAY, outline = s.outlineColor;
        ps.pushPose();
        ps.translate(0.0F, BODY_CENTER_Y, 0.0F);
        ps.rotate(s.orientation);                          // PoseStack.rotate(Quaternionfc)
        s.frame.submit(ps, c, light, ov, outline);
        s.stack.submit(ps, c, light, ov, outline);         // add ps.translate(...) per mount as needed
        for (int i = 0; i < 4; i++) {
            Vector3f p = s.motorPos[i];
            ps.pushPose();
            ps.translate(p.x(), p.y(), p.z());
            s.motors[i].submit(ps, c, light, ov, outline);
            ps.translate(0.0F, PROP_Z_OFFSET, 0.0F);
            ps.rotate(Axis.YP, s.rotorAngle[i]);            // radians; rotateDegrees(...) for degrees
            s.props[i].submit(ps, c, light, ov, outline);
            ps.popPose();
        }
        ps.popPose();
        super.submit(s, ps, c, cam);
    }
}
```
If you want a bigger visual scale, do it with one `ps.scale(k,k,k)` after `rotate`. Motor positions then stay in frame units.
Put everything between the outer `pushPose`/`popPose` into a static `DroneVisuals.submit(state, ps, c, light)` so the workbench
block entity renderer (§7b) can reuse it. Checked in the sources: `PoseStack.rotate(Quaternionfc)`/`rotate(Axis, float radians)`
(`vanilla/com/mojang/blaze3d/vertex/PoseStack.java:44-52`), `Entity implements ItemOwner` (`vanilla/.../world/entity/Entity.java:166`).

## 4. Alternative: ModelPart / LayerDefinition / EntityModel

- `vanilla/net/minecraft/client/model/Model.java`: `Model<S>(ModelPart root, Function<Identifier, RenderType> renderType)`,
  `setupAnim(S)` (default `resetPose()`), `root()`, `renderType(Identifier)`, and `Model.Simple(ModelPart, Function)` for a `Unit` state.
- `vanilla/.../client/model/EntityModel.java`: `EntityModel<T extends EntityRenderState>(ModelPart root)` defaults to
  `RenderTypes::entityCutout`. `MODEL_Y_OFFSET = -1.501F`.
- `vanilla/.../model/geom/ModelPart.java`: public fields `x,y,z,xRot,yRot,zRot,xScale..,visible,skipDraw`, plus `getChild(String)`,
  `resetPose()`, `setRotation(...)`, `rotateBy(Quaternionf)`.
- Builders (`vanilla/.../model/geom/builders/`): `MeshDefinition#getRoot()`,
  `PartDefinition#addOrReplaceChild(String, CubeListBuilder, PartPose)`, `CubeListBuilder.create().texOffs(u,v).addBox(x0,y0,z0,w,h,d)`
  (float w/h/d overload, `CubeListBuilder.java:47`), `LayerDefinition.create(MeshDefinition, texW, texH)`.
  `PartPose` is **not** in `builders/` but in `vanilla/.../model/geom/PartPose.java`: `ZERO`, `offset(x,y,z)`, `rotation(x,y,z)`,
  `offsetAndRotation(...)`.
- `Model.Simple#setupAnim` is a no-op (it does **not** `resetPose()`), and `submitModelPart` wraps the part in a `Model.Simple`
  (`OrderedSubmitNodeCollector.java` 8-arg `submitModelPart`).
- `ModelLayerLocation` is `record (Identifier model, String layer)` (`vanilla/.../model/geom/ModelLayerLocation.java`), and
  `context.bakeLayer(loc)` throws `No model for layer` when the layer is unregistered.
- Vanilla example: `vanilla/.../model/object/boat/BoatModel.java#createBoatModel` and `BoatRenderer` (`new BoatModel(context.bakeLayer(modelId))`).

```java
public final class GateModel extends EntityModel<GateRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("propwash", "gate"), "main");
    private final ModelPart leds;
    public GateModel(ModelPart root) { super(root); this.leds = root.getChild("leds"); }
    public ModelPart leds() { return leds; }
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition(); PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("frame", CubeListBuilder.create().texOffs(0, 0).addBox(-16F, -32F, -1F, 2F, 32F, 2F)
                .addBox(14F, -32F, -1F, 2F, 32F, 2F).addBox(-16F, -34F, -1F, 32F, 2F, 2F), PartPose.ZERO);
        root.addOrReplaceChild("leds", CubeListBuilder.create().texOffs(0, 40).addBox(-14F, -32F, -1.2F, 28F, 1F, 0.4F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
    @Override public void setupAnim(GateRenderState s) { super.setupAnim(s); /* pose parts only from s */ }
}
```
**Pitfall:** because `setupAnim` runs during feature rendering, *all* pose changes must come from the state passed to
`submitModel`. Changing `ModelPart` fields in `submit()` affects every later draw of that model instance. A part drawn on its own
via `submitModelPart` (e.g. the LED overlay in §6) gets **no** `setupAnim`, so its pose is whatever the *last* `setupAnim` of the
shared model left behind. And translucent phases run after all solid ones, so that is the last gate drawn, not "this" gate.
Keep separately submitted parts un-animated, or give them their own model instance.

## 5. Registration per loader

| What | Fabric (fabric module) | NeoForge (neoforge module) |
|---|---|---|
| Entity renderer | `EntityRenderers.register(type, QuadcopterRenderer::new)` in `ClientModInitializer`. The method is private in vanilla and widened by `fabric-api/fabric-transitive-access-wideners-v1/...classtweaker` line 45. `EntityRendererRegistry.register` still exists but is `@Deprecated` (`fabric-api/fabric-rendering-v1/.../v1/EntityRendererRegistry.java`) | `EntityRenderersEvent.RegisterRenderers#registerEntityRenderer(EntityType<? extends T>, EntityRendererProvider<T>)` (`neoforge-api/.../client/event/EntityRenderersEvent.java:103`), mod bus |
| Model layer | `ModelLayerRegistry.registerModelLayer(ModelLayerLocation, TexturedLayerDefinitionProvider)` (`fabric-api/fabric-rendering-v1/.../v1/ModelLayerRegistry.java`); the provider is `() -> LayerDefinition` | `EntityRenderersEvent.RegisterLayerDefinitions#registerLayerDefinition(ModelLayerLocation, Supplier<LayerDefinition>)` (:79) |
| Block entity renderer (workbench, §7b) | `BlockEntityRenderers.register(type, WorkbenchRenderer::new)`; private in vanilla, widened by TAW line 42. `BlockEntityRendererRegistry.register` is `@Deprecated` (`fabric-api/fabric-rendering-v1/.../v1/BlockEntityRendererRegistry.java:35,46`) | `EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer(BlockEntityType<? extends T>, BlockEntityRendererProvider<T, S>)` (`EntityRenderersEvent.java:113`) |
| Custom item-model type | `ItemModels.ID_MAPPER.put(id, MAP_CODEC)` (private in vanilla `ItemModels.java`, TAW line 48) | `RegisterItemModelsEvent#register(Identifier, MapCodec<? extends ItemModel.Unbaked>)` (`neoforge-api/.../client/event/RegisterItemModelsEvent.java:32`) |
| Custom PIP renderer | `PictureInPictureRendererRegistry.register(Factory)`, `Factory#createRenderer(Context ctx)` → `ctx -> new X()` (`fabric-api/fabric-rendering-v1/.../v1/PictureInPictureRendererRegistry.java`) | `RegisterPictureInPictureRenderersEvent#register(Class<T>, Supplier<PictureInPictureRenderer<T>>)` (`neoforge-api/.../client/event/RegisterPictureInPictureRenderersEvent.java:37`) |

`common` compiles against plain vanilla, where `EntityRenderers.register` (`vanilla/.../entity/EntityRenderers.java:28`) is
**private**. In the loader modules it is public on both sides: Fabric via TAW, NeoForge via its own AT
(`neoforge-api/META-INF/accesstransformer.cfg:64`; `BlockEntityRenderers.register` is :62). On NeoForge still prefer the event.
Keep all registration calls in the loader modules and expose them as `PropwashClientRenderers.forEachRenderer(BiConsumer)` from common.
The Fabric client entrypoint is `net.fabricmc.api.ClientModInitializer` (fabric-loader, not in `.ref`; usage e.g.
`fabric-api/fabric-renderer-indigo/net/fabricmc/fabric/impl/client/indigo/Indigo.java`).
```java
// fabric: PropwashFabricClient implements ClientModInitializer
@Override public void onInitializeClient() {
    EntityRenderers.register(PropwashEntities.DRONE.get(), QuadcopterRenderer::new);
    ModelLayerRegistry.registerModelLayer(GateModel.LAYER, GateModel::createLayer);
}
// neoforge: pattern from neoforge-api/.../client/ClientNeoForgeMod.java:95-101 (@Mod(value=..., dist=Dist.CLIENT), ctor(IEventBus, ModContainer)).
// addListener(Class<E>, Consumer<E>) is used there (:128) on NeoForge.EVENT_BUS; IEventBus itself (bus library) is not in .ref.
@Mod(value = Propwash.MOD_ID, dist = Dist.CLIENT)
public final class PropwashNeoForgeClient {
    public PropwashNeoForgeClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(PropwashEntities.DRONE.get(), QuadcopterRenderer::new));
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class,
                e -> e.registerLayerDefinition(GateModel.LAYER, GateModel::createLayer));
    }
}
```

## 6. Render types, tint, emissive, light

`vanilla/net/minecraft/client/renderer/rendertype/RenderTypes.java` (all take `Identifier texture`):
`entitySolid`, `entityCutout(tex[, affectsOutline])` (there is **no `entityCutoutNoCull`**; `entityCutout` is the no-cull one, and
`entityCutoutCull` culls), `entityCutoutZOffset`, `entityTranslucent(tex[, affectsOutline])`,
`entityTranslucentCull`, `entityTranslucentEmissive(tex)`, `eyes(tex)`, `itemCutout/itemTranslucent`.
- `eyes` = pipeline `EYES` (`vanilla/.../RenderPipelines.java:652`, snippet `EYES_SNIPPET` :156): defines `EMISSIVE`, `NO_OVERLAY`,
  `NO_CARDINAL_LIGHTING`, built on `ENTITY_NO_LIGHTMAP_SNIPPET` (no lightmap), translucent blend, depth test `GREATER_THAN_OR_EQUAL`
  with **no depth write**. It is always full-bright and unshaded, which makes it the best choice for LED strips drawn as an overlay.
  It also has an OIT variant (`OIT_EYES`, used when "Improved Transparency" is on, `RenderTypes.java:367-371`).
- `entityTranslucentEmissive` = `EMISSIVE` + `PER_FACE_LIGHTING`, no lightmap, no cull, translucent blend, **no depth write**
  (`RenderPipelines.java:558-567`). Used by Warden (`vanilla/.../entity/WardenRenderer.java:34`).
- Cull: `entityCutout` pipeline has `.withCull(false)` (`RenderPipelines.java:507-515`), `ENTITY_CUTOUT_CULL` (:499) keeps the default cull.
- Overlay pass like `EyesLayer` (`vanilla/.../entity/layers/EyesLayer.java`):
  `c.order(1).submitModel(model, state, ps, RenderTypes.eyes(TEX), light, OverlayTexture.NO_OVERLAY, state.outlineColor)`.

**Tint** (ARGB int; `-1` = white/no tint; helpers in `vanilla/net/minecraft/util/ARGB.java`: `color(a,r,g,b)`, `opaque(rgb)`, `multiply`):
- ModelPart path: pass `tintedColor` to `submitModel(..., tintedColor, null, outline)` or `submitModelPart(..., uv=null, tintedColor, outline)`.
  Gate LEDs: draw `leds` twice, once normally and once with `c.order(1).submitModelPart(model.leds(), ps, RenderTypes.eyes(LED_TEX), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, next ? 0xFF33FF55 : 0xFFFFFFFF, 0)`.
  Compute the color in `extractRenderState` from client race state (`state.ledColor`), never in `submit`.
- Item-model path: `ItemStackRenderState.submit` has **no color parameter**. Tints come from the client item's
  `"tints"` list on a `minecraft:model` entry (`vanilla/.../client/color/item/ItemTintSources.java`: `custom_model_data`, `constant`,
  `dye`, `grass`, `firework`, `potion`, `team`). Entry *i* colors faces with `"tintindex": i`
  (`ItemFeatureRenderer.java:91-92`). For per-instance color use `custom_model_data`
  (`vanilla/.../client/color/item/CustomModelDataSource.java`: fields `index` (default 0) and `default` (required), returns `ARGB.opaque(colors[index])`):
```json
{ "model": { "type": "minecraft:model", "model": "propwash:gate/led_strip",
    "tints": [ { "type": "minecraft:custom_model_data", "index": 0, "default": 16777215 } ] } }
```
```java
ItemStack led = new ItemStack(Items.PAPER);                      // cache one stack per color
led.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("propwash", "gate/led_strip"));
led.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(0x33FF55)));
```
  (`CustomModelData(List<Float>, List<Boolean>, List<String>, List<Integer> colors)`: `vanilla/.../world/item/component/CustomModelData.java`)

**Emissive item-model quads.** Put `"light_emission": 15` (integer 0-15, else `JsonParseException`) on the element
(`vanilla/.../cuboid/CuboidModelElement.java` :62-72; vanilla sample `vanilla/assets/minecraft/models/block/cross_emissive.json`).
`VertexConsumer.putBakedQuad` (`vanilla/com/mojang/blaze3d/vertex/VertexConsumer.java:71`, called from
`vanilla/.../feature/ItemFeatureRenderer.java:60`) raises the vertex light via `QuadInstance#getLightCoordsWithEmission`
(`QuadInstance.java:41` → `LightCoordsUtil.lightCoordsWithEmission`: `max(block, e)`, `max(sky, e)`). This works in the world and in
the GUI with no extra code, which makes it the easiest route to glowing LEDs, the camera lens, and VTX status lights. It only maxes the
**lightmap**: the item pipeline's directional (normal-based) shading still darkens side/bottom faces. `"shade_direction_override"`
does **not** help here, because it is read only by the terrain lighter (`vanilla/.../renderer/block/BlockModelLighter.java:275`),
never by item/entity rendering. For a fully flat glow use the `eyes` overlay instead.

**Light.** `state.lightCoords` is set by the base extract (`getPackedLightCoords`). Use `LightCoordsUtil.FULL_BRIGHT`
(`vanilla/net/minecraft/util/LightCoordsUtil.java:9`) for self-lit parts, or `LightCoordsUtil.pack(block, sky)`. To light the whole entity,
override `getBlockLightLevel` (see `ItemFrameRenderer`: glow frame → `Math.max(5, ...)`). Overlay: `OverlayTexture.NO_OVERLAY`.

## 7. GUI: icons and the rotating 3D preview in the workbench

Screens: `Screen#extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float a)`,
`extractBackground(...)` (same args), and `AbstractContainerScreen#extractContents/extractLabels/extractSlots`.
`extractLabels` runs under `pose().translate(leftPos, topPos)` (`vanilla/.../gui/screens/inventory/AbstractContainerScreen.java` :97-101).
`extractBackground` is called first, from `Screen#extractRenderStateWithTooltipAndSubtitles` (`vanilla/.../gui/screens/Screen.java:108-110`),
with an untranslated pose.
Dragging uses `mouseDragged(MouseButtonEvent event, double dx, double dy)` (:376).

**Item icons** (`vanilla/net/minecraft/client/gui/GuiGraphicsExtractor.java` ~831-873):
`item(ItemStack, int x, int y)`, `item(ItemStack, x, y, int seed)`, `fakeItem(ItemStack, x, y)`, `itemDecorations(Font, ItemStack, x, y)`.
These resolve with `ItemDisplayContext.GUI` and are rasterised into a GUI item atlas at `16 * guiScale` px
(`vanilla/.../gui/render/GuiRenderer.java:261`). Scaling with `graphics.pose().pushMatrix(); .scale(3,3)` only upsamples, so the result is blurry.
Setting `"oversized_in_gui": true` in the client item JSON (`vanilla/.../renderer/item/ClientItem.java:31`) lets the model draw past its
slot (`OversizedItemRenderer`). The cache is keyed by `TrackingItemStackRenderState.getModelIdentity()`.

**3D preview: use vanilla `GuiGraphicsExtractor.entity`** (:972):
```java
public void entity(EntityRenderState renderState, float scale, Vector3fc translation, Quaternionfc rotation,
                   @Nullable Quaternionfc overrideCameraAngle, int x0, int y0, int x1, int y1)
```
It sets `renderState.lightCoords = 15728880` (full bright) and queues a `GuiEntityRenderState`
(`vanilla/net/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState.java`; it has no pose, so `pose()` is the interface
default `IDENTITY_POSE`). Then `GuiEntityRenderer` (`vanilla/.../gui/render/pip/GuiEntityRenderer.java`, created in
`vanilla/.../renderer/GameRenderer.java:169`) renders into an offscreen texture of the rectangle's size × guiScale
with `Lighting.Entry.ENTITY_IN_UI` (`vanilla/com/mojang/blaze3d/platform/Lighting.java`), origin at the rect centre
(`getTranslateY` = height/2), `scale` = GUI px per block (`poseStack.scale(s, s, -s)` in `PictureInPictureRenderer#prepare`), and Y pointing down. Then
`translate(translation)` and `rotate(rotation)` are applied, and it calls `entityRenderDispatcher.submit(state, ...)`, which picks the
renderer **by `state.entityType`**. So you need no `Entity` instance, no custom PIP, and no access widener.

The vanilla preview without an entity is `vanilla/.../gui/screens/inventory/SmithingScreen.java`:
```java
private static final Vector3fc ARMOR_STAND_TRANSLATION = new Vector3f(0.0F, 1.0F, 0.0F);
private static final Quaternionfc ARMOR_STAND_ANGLE = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, (float) Math.PI);
private final ArmorStandRenderState armorStandPreview = new ArmorStandRenderState();   // init: .entityType = EntityTypes.ARMOR_STAND
// slotChanged(): itemModelResolver.updateForTopItem(this.armorStandPreview.headItem, itemStack, ItemDisplayContext.HEAD, this.minecraft.level, null, 0);
// extractBackground():
graphics.entity(this.armorStandPreview, 25.0F, ARMOR_STAND_TRANSLATION, ARMOR_STAND_ANGLE, null, x0, y0, x1, y1);
```
(`InventoryScreen.extractEntityInInventoryFollowsMouse` is the living-entity variant: `rotation = rotateZ(PI)` then mul `rotateX(...)`.)

Workbench sketch (common):
```java
private final DroneRenderState preview = new DroneRenderState();
private float yawDeg = 210f;

@Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    super.extractBackground(g, mouseX, mouseY, a);
    preview.entityType = PropwashEntities.DRONE.get();          // REQUIRED: dispatcher key
    DroneVisuals.fill(preview, menu.previewBuild(), minecraft.getItemModelResolver(), minecraft.level, null, 0,
                      Util.getMillis() / 50f, false);            // refill only when the build changed if you like
    preview.shadowPieces.clear(); preview.outlineColor = 0;
    float spin = yawDeg * Mth.DEG_TO_RAD + (Util.getMillis() % 20000L) / 20000f * Mth.TWO_PI;
    Quaternionf rot = new Quaternionf().rotationXYZ(25f * Mth.DEG_TO_RAD, 0f, Mth.PI).rotateY(spin);
    int x0 = leftPos + PREVIEW_X, y0 = topPos + PREVIEW_Y;       // ABSOLUTE screen coords
    g.entity(preview, 90f /*px per block*/, new Vector3f(0f, BODY_CENTER_Y, 0f), rot, null, x0, y0, x0 + 80, y0 + 60);
}
@Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { /* if inside preview: yawDeg += (float) dx * 2f; return true; */ return super.mouseDragged(e, dx, dy); }
```
To refill only on change, do what `ItemCombinerScreen` does: implement `net.minecraft.world.inventory.ContainerListener`
(`slotChanged(AbstractContainerMenu, int slotIndex, ItemStack)`, `dataChanged(...)`) and call `menu.addSlotListener(this)` in `init`
and `menu.removeSlotListener(this)` in `removed` (`vanilla/.../gui/screens/inventory/ItemCombinerScreen.java:16,31,37`).
`QuadcopterRenderer.submit` must not read anything that only exists in the world, and must tolerate `owner == null` and `level`.
Choose `translation.y` so the model centre sits at the rect centre: `+halfHeight` if the model origin is at its bottom, `0` if it is centred.

**Custom PIP (only if ever needed).** Extend `PictureInPictureRenderer<T extends PictureInPictureRenderState>`
(`vanilla/.../gui/render/pip/PictureInPictureRenderer.java`) and implement `getRenderStateClass()`,
`renderToTexture(T, PoseStack, SubmitNodeCollector)` and `getTextureLabel()`. Optionally override `getTranslateY` and `textureIsReadyToBlit`.
The example is `GuiBookModelRenderer` + `GuiBookModelRenderState` (`vanilla/.../renderer/state/gui/pip/GuiBookModelRenderState.java`,
a record `(bookModel, texture, open, flip, x0, y0, x1, y1, scale, scissorArea, bounds)` implementing `PictureInPictureRenderState`).
Register it per loader (§5). The vanilla `GuiRenderer` keys PIP renderers by state class with `ImmutableMap.buildOrThrow`
(`vanilla/.../gui/render/GuiRenderer.java:100-109`), one renderer and texture per state class, so in pure vanilla two PIPs of one class in
the same frame would share one texture. Both loaders fix this with pools: NeoForge `net.neoforged.neoforge.client.gui.PictureInPictureRendererPool`
(`neoforge/.../gui/render/GuiRenderer.java:91-104`) and Fabric `fabric-api/fabric-rendering-v1/.../impl/client/rendering/PictureInPictureRendererPool.java`
(it also registers a factory for vanilla `GuiEntityRenderState`, `PictureInPictureRendererRegistryImpl.java:84`). So several
`graphics.entity(...)` calls per frame are fine on both loaders.
Queueing it needs `GuiRenderState#addPicturesInPictureState(...)` (public) on the private field `GuiGraphicsExtractor.guiRenderState`
(`vanilla/.../gui/GuiGraphicsExtractor.java:95`). Per loader:
- Fabric: TAW widens the field (classtweaker line 37), so `graphics.guiRenderState.addPicturesInPictureState(state)`.
- NeoForge: no AT, but the patched class adds **`public void submitPictureInPictureRenderState(PictureInPictureRenderState)`**
  and `peekScissorStack()` (`neoforge/net/minecraft/client/gui/GuiGraphicsExtractor.java:1304,1313`).
So a custom PIP needs a tiny per-loader hook (`Services`-style), which is still more work than `graphics.entity`.

**Drone item icon (inventory).**
- Static: client item `"type": "minecraft:composite"` with `"models": [...]`. Each child is `minecraft:model` with a
  `"transformation"` (`{"translation":[x,y,z],"left_rotation":[x,y,z,w],"scale":[..],"right_rotation":[..]}`, **all four keys
  required**, rotations also accept `{"angle":rad,"axis":[x,y,z]}`; or a flat list of 16 floats;
  `vanilla/com/mojang/math/Transformation.java:22-33`, `vanilla/net/minecraft/util/ExtraCodecs.java:111-121`,
  `vanilla/.../renderer/item/CompositeModel.java`). Translation is in **blocks**, and the pivot is model pixel (0,0,0) (see §3.1). Vanilla
  sample: `vanilla/assets/minecraft/items/purple_bed.json` (foot at `translation [0,0,1]`). Every child applies **its own**
  `display.gui` transform (`ModelRenderProperties#applyToLayer`), so give all part models a common parent with identical `display` blocks.
- Dynamic (per stored `DroneBuild`): write a custom `ItemModel.Unbaked` (`type()`, `bake(ItemModel.BakingContext, Matrix4fc)`,
  `resolveDependencies`) whose baked `ItemModel#update(ItemStackRenderState, ItemStack, ItemModelResolver, ItemDisplayContext,
  @Nullable ClientLevel, @Nullable ItemOwner, int seed)` (`vanilla/.../renderer/item/ItemModel.java`) builds the layers. Register it
  per loader (§5). It must call `output.appendModelIdentityElement(...)` with every build-dependent value, or the GUI atlas will
  show a stale icon (atlas slots are keyed by model identity, `vanilla/.../gui/render/GuiItemAtlas.java:77`).

### 7b. Workbench block: the drone on the table in the world (BlockEntityRenderer)
If the workbench block should show the drone being assembled on top of it, that is a block entity renderer, same extract/submit split
(`vanilla/net/minecraft/client/renderer/blockentity/BlockEntityRenderer.java`):
```java
public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState> {
    S createRenderState();
    default void extractRenderState(T blockEntity, S state, float partialTicks, Vec3 cameraPosition,
                                    ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)   // default: BlockEntityRenderState.extractBase(...)
    void submit(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera);
    default boolean shouldRenderOffScreen(); default int getViewDistance() /*64*/; default boolean shouldRender(T, Vec3 cameraPosition);
}
```
`BlockEntityRendererProvider.Context` is a **record** (`.../blockentity/BlockEntityRendererProvider.java:22`) with accessors
`itemModelResolver()`, `entityRenderer()` (the `EntityRenderDispatcher`), `blockModelResolver()`, `entityModelSet()`, `font()`, `sprites()`
plus `bakeLayer(ModelLayerLocation)`. `BlockEntityRenderState` (`.../blockentity/state/BlockEntityRenderState.java`) has public `blockPos`, `blockEntityType`,
`lightCoords`, `breakProgress` (`blockState` is **private** with no getter, so copy what you need, such as `FACING`, into your own field, as
`CampfireRenderer` does with `state.facing`). `extractBase` takes the light at the block's own position (:28).
Vanilla pattern with items: `vanilla/.../blockentity/CampfireRenderer.java` (`context.itemModelResolver()`, `updateForTopItem(..., FIXED, level, null, seed)`
in extract, `itemState.submit(...)` in submit; pose origin = block min corner, it translates `0.5, y, 0.5` itself).
Recommendation: move the body of `QuadcopterRenderer.submit` into a static `DroneVisuals.submit(DroneRenderState, PoseStack, SubmitNodeCollector, int light)`,
keep a `DroneRenderState` inside the workbench's render state, fill it with `DroneVisuals.fill(..., level, null, seed, ...)`, and call
the static submit after `translate(0.5, 1.0 + BODY_CENTER_Y, 0.5)`. Don't route through `EntityRenderDispatcher.submit` (it would add
shadow/fire). Light: the workbench is a full block, so its own position has block light 0. Use
`LightCoordsUtil.getLightCoords(level, pos.above())` (`vanilla/net/minecraft/util/LightCoordsUtil.java:96`) in `extractRenderState`.
Registration per loader: see the §5 table.

## 8. Pitfalls checklist

1. Never touch game objects (entity, level, client race state) in `submit`. Copy everything into the state in `extractRenderState`.
   The state is recreated every frame (`createRenderState(T, float)`).
2. `setupAnim` runs deferred, so pose `ModelPart`s only from the state object you pass to `submitModel`. Parts submitted alone
   via `submitModelPart` get no `setupAnim` at all (see §4).
3. PIP entity previews: `state.entityType` must be set, `shadowPieces` should be empty, and coords are absolute because the
   `graphics.pose()` matrix is **not** applied to `entity(...)`.
4. `ItemDisplayContext.NONE` puts model pixel (8,8,8) at the origin. Any other context applies that model's `display` block.
5. Negative scale (mirroring CW/CCW props) flips winding. Use separate `prop_cw`/`prop_ccw` models instead.
6. One atlas per item model (items **or** blocks). Put prop-blur discs in `textures/item/...` with `force_translucent`.
7. Draw order: every order's `solid` phase runs before any translucent phase (`vanilla/.../feature/FeatureRenderDispatcher.java`
   `executeSolid` / `executeTranslucent` :197-227). So an `eyes`/translucent overlay always lands on top of an opaque base, whatever its
   order. `order(1)` (as `EyesLayer` does) only sorts it after order-0 *translucent* model submits. With "Improved Transparency" on, all
   translucent submits share one OIT phase (`SubmitNodeCollection.java` ctor). Follow vanilla and use `order(1)` for overlays anyway.
   Without OIT (and always under Iris shader packs) `LevelRenderer#executeClassicTransparency` runs `executeTranslucent` (all
   translucent entity/item/custom-geometry phases) **before** the translucent terrain (water) and only `afterTerrain` (translucent
   particles, `submitShapeOutline(..., true)`) after it. `ENTITY_TRANSLUCENT` and `ITEM_TRANSLUCENT` use `DepthStencilState.DEFAULT`
   (depth write), so water behind such geometry fails the depth test and disappears. Fix used here: `AfterWaterRenderTypes` +
   `SubmitNodeCollectionMixin` (FOUNDATION §15 „Durchscheinende Drohnen-Optik nach dem Wasser“). Blur discs of drones in the
   world use `blur()` without depth write; the FPV discs drawn right in front of the lens (`FpvProps.submitDiscs`) use
   `fpvDisc()` (ENTITY_TRANSLUCENT, depth write) in the same after-water phase, so water stays visible behind them and clouds,
   weather and particles drawn later cannot cover them (0.4.2).
8. `EntityRenderState.partialTick`, `BaseRenderState` render data, and `submitSpecial` are NeoForge-only, and `FabricRenderState` /
   `submitCustom` are Fabric-only. Don't use any of them from common.
9. `EntityRenderers.register` / `BlockEntityRenderers.register` compile only in the loader modules (Fabric TAW, NeoForge AT), never in
   common. On Fabric the name is `ModelLayerRegistry`, not `EntityModelLayerRegistry`.
10. In FPV the drone (camera entity) is not extracted on either loader. The local player **is** extracted on NeoForge (patched
   `LevelExtractor`) but not on Fabric, so the pilot's body can show up in the FPV feed on NeoForge only. NeoForge-side fix: cancel
   `RenderPlayerEvent.Pre` (`neoforge-api/.../client/event/RenderPlayerEvent.java:50`, `ICancellableEvent`, game bus) when
   `event.getRenderState().id == minecraft.player.getId()` and the drone is the camera (`RenderLivingEvent#getRenderState()` :50;
   `AvatarRenderState.id`, `vanilla/.../entity/state/AvatarRenderState.java:33`, set in `.../entity/player/AvatarRenderer.java:181`).
   Do not assume vanilla behaviour on both loaders.
11. `ItemTransform`/`transformation` order: JSON `"transformation"` is applied before the -0.5 centering, so it pivots at model pixel
   (0,0,0) and translates in blocks (§3.1).
12. Emissive (`light_emission`) item quads still get directional face shading, and `shade_direction_override` is terrain-only (§6).

## Verification

Checked by grep/Read against `.ref` (vanilla 26.3, NeoForge-patched MC, neoforge-api, fabric-api). About 190 claims checked.

**Confirmed as written:** `EntityRenderer` members and signatures (ctor, `createRenderState` ×2, `extractRenderState`, `submit`,
`getRenderOffset`, `getBoundingBoxForCulling`, `affectedByCulling`, `getBlockLightLevel`, `shouldShowName`, `shadowRadius/Strength`);
`EntityRenderState` fields and `lightCoords = 15728880`; `EntityRenderDispatcher#getRenderer(S)` :112 and `submit(...)`;
`EntityRendererProvider.Context` getters; `SubmitNodeCollector`/`OrderedSubmitNodeCollector` signatures (`submitModel` ×3,
`submitModelPart`, `submitItem`, `submitBlockModel`, `submitCustomGeometry`, `order(int)`, `CustomGeometryRenderer`); pose copy in
`SubmitNodeCollection`; `ModelFeatureRenderer#prepareModel` calling `setupAnim` at draw time; `ItemModelResolver` 4 methods and seeds;
`ModelManager#getItemModel` :80; `ItemStackRenderState#submit/isEmpty/clear/getModelBoundingBox`; `ItemTransform.NO_TRANSFORM`;
`ClientItemInfoLoader` `FileToIdConverter.json("items")`; `select`/`display_context`/`when` (compact list); element Euler rotation;
`validateAtlasUsage`; `Material` `force_translucent`; `ItemFrameRenderer`/`ItemFrameRenderState`/`ItemEntityRenderer` call sites;
`Model`, `Model.Simple`, `EntityModel` (`entityCutout`, `MODEL_Y_OFFSET`), `ModelPart` fields/methods, mesh builders,
`ModelLayerLocation` record, "No model for layer" (`EntityModelSet.java:21`); boat model/renderer; Fabric TAW lines 37/42/45/48;
`EntityRendererRegistry` `@Deprecated`; `ModelLayerRegistry.registerModelLayer` + `TexturedLayerDefinitionProvider`;
no `EntityModelLayerRegistry` in fabric-api; NeoForge `EntityRenderersEvent` :79/:103, `RegisterItemModelsEvent#register`,
`RegisterPictureInPictureRenderersEvent#register`, `ClientNeoForgeMod` pattern; `RenderTypes` factory list (no `entityCutoutNoCull`);
`EYES` pipeline :652; Warden :34; `EyesLayer` `order(1)`; `ARGB` helpers; `LightCoordsUtil.FULL_BRIGHT` :9 and `pack`;
`ItemTintSources`, `CustomModelDataSource`, `CustomModelData` record; `ClientItem` `oversized_in_gui` :31; GUI item methods :831-873;
`GuiRenderer` :261 `16 * guiScale`; `GuiGraphicsExtractor#entity` :972; `GuiEntityRenderer`; `SmithingScreen` preview;
`InventoryScreen.extractEntityInInventoryFollowsMouse`; `PictureInPictureRenderer` abstract methods; `Transformation` codec;
`CompositeModel`; no `LightTexture`, `ResourceLocation`, `GuiGraphics` or `MultiBufferSource` class in vanilla 26.3.

**Corrected:**
1. §1: the shadow is filled in `finalizeRenderState`, not `extractRenderState`. Fire and shadow are submitted by the dispatcher, not `super.submit`.
2. §1: NeoForge `partialTick` lives in the patched `EntityRenderState` (which also extends `BaseRenderState`), not in `EntityRenderer`.
3. §1 and pitfall 10: **NeoForge renders the local player when it is not the camera entity** (`neoforge/.../extract/LevelExtractor.java:283`).
   In FPV the pilot body is visible on NeoForge. The old claim that it was hidden on both loaders was wrong. A fix via `RenderPlayerEvent.Pre` was added.
4. §4: `PartPose` is in `model/geom/`, not `builders/`.
5. §5 and pitfall 9: `EntityRenderers.register` is also public on NeoForge (AT `accesstransformer.cfg:64`). The old text said it was Fabric-only.
6. §6: `putBakedQuad` is at `VertexConsumer.java:71` (line 58 is `putBlockBakedQuad`).
7. §6: the `cross_emissive.json` citation now points to `vanilla/assets/...`.
8. §6: `shade_direction_override` has **no effect** on item/entity rendering, because only `BlockModelLighter` reads it. Emissive item quads still get directional shading.
9. §6: `entityTranslucentEmissive` also has no depth write, and `EYES` also defines `NO_OVERLAY`. OIT variants are noted.
10. §7: "NeoForge has no way to queue a custom PIP" was wrong. NeoForge adds `GuiGraphicsExtractor#submitPictureInPictureRenderState` (:1304).
11. Pitfall 7: rewrote the draw-order explanation. Solid phases of all orders run before translucent ones, and `order(n)` only orders within a phase.
12. §7 icon: all four `Transformation` keys are required, translation is in blocks, and the pivot is model pixel (0,0,0), because the local transform is applied before the -0.5 centering.
13. §1: the boat example is marked as abridged.
14. §4: `submitModelPart` → `Model.Simple` has a no-op `setupAnim`, and the shared-model pose leak is now a pitfall.
15. §2: shadow/leash/text copy only the `Matrix4f`, not a `Pose`. Minor line ranges were fixed (CuboidModelElement, AbstractContainerScreen).

**Added:** renamed classes (`EntityTypes`, `net.minecraft.util.Util`, `RenderType`, `CameraRenderState`, `MouseButtonEvent`);
Fabric-only `FabricOrderedSubmitNodeCollector#submitCustom`/`FabricRenderState`; `SubmitNodeStorage`; `ItemModel` interface
signature; §7b **BlockEntityRenderer** for the workbench block (API, `Context` record, `CampfireRenderer` pattern, light pitfall)
with per-loader registration rows; PIP renderer pooling on both loaders (several `graphics.entity` calls per frame are OK);
`ContainerListener` pattern for refilling the preview only on slot changes.

**Not in `.ref` (unverifiable here):** `net.fabricmc.api.ClientModInitializer` (fabric-loader) and `IEventBus#addListener(Class, Consumer)`
(bus library). Their usage is cited from fabric-api / neoforge-api call sites.

**Open questions (need an in-game test):**
1. Is the NeoForge pilot-body leak into the FPV view real in practice (camera = drone, player not spectator)? Is cancelling
   `RenderPlayerEvent.Pre` by `AvatarRenderState.id` the cleanest fix, or is a mixin/extension on the extract condition better?
2. How do emissive item-model LEDs look with directional item shading, in the world and in the `ENTITY_IN_UI` PIP? Is an `eyes`
   overlay (ModelPart or `submitCustomGeometry`) needed for a flat LED/lens glow?
3. Does the translucent prop-blur (`force_translucent`) sort correctly against the drone's own cutout parts and against other
   translucent geometry, with "Improved Transparency" (OIT) both on and off? Answered for water: with OIT off it did not (water vanished behind
   the disc), see pitfall 7.

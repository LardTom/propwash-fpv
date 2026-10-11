# Post-render & custom rendering (MC 26.3): FPV video effects

> **Projekt-Layout veraltet:** Die eine Mixin-Config `propwash.mixins.json` (stattdessen je Modul `propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`), Registrierungs-Klassen (`PropwashItems`, `PropwashEntities`, `PropwashComponents`, `RegList.Ref`, …), `CommonClass`, das Paket `net` (heute `network`) und Loader-Einstiege in dieser Notiz stammen aus der Zeit vor der Foundation. Kommentare in den Code-Beispielen sind nur Erklärung: im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`). Maßgeblich sind `docs/CONVENTIONS.md` und `docs/FOUNDATION.md` (Kasten „Abweichungen von docs/api“). Die Minecraft-/Loader-APIs hier stimmen weiterhin.

All paths are relative to `<repo>/.ref`. `V/` = `vanilla/`, `NF/` = `neoforge/` (patched MC),
`NFA/` = `neoforge-api/`, `FA/` = `fabric-api/`. Everything below was checked against these sources. Items marked UNVERIFIED could not be confirmed (see "## 10. Reading world depth in our own pass (FPV headlight, added)

Checked against `V/net/minecraft/client/renderer/GameRenderer.java` (L640-745), `NF/.../GameRenderer.java` (L669, L685),
`V/net/minecraft/client/renderer/Projection.java`, `V/com/mojang/blaze3d/pipeline/RenderTarget.java` (`createBuffers`) and
`V/assets/minecraft/shaders/core/blit_depth.fsh`.

- **Where world depth is still in main:** `renderLevel()` calls `levelRenderer.render(...)` and then
  `render3dHud(cameraState, playerState, optionsState, consistentDepthRequired)`. Right **before** `render3dHud` the main
  target holds the finished world color and world depth (terrain, entities, translucency, weather, particles). `render3dHud`
  clears the depth it draws the hand into (main depth unless a post chain is active), so hooks after it (our §6 video hook)
  must not rely on world depth. A common mixin
  `@Inject(method = "renderLevel()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))`
  hits the same spot on both loaders (NeoForge only adds `RenderLevelStageEvent.AfterLevel` before it). Forcing
  `consistentDepthRequired` would also keep world depth until after `applyPostEffects`, but costs an extra hand-depth target
  and integrate pass every frame; the early hook needs neither.
- **Projection used for the level:** `renderLevel` builds `projectionMatrix` from `cameraState.projectionMatrix` plus
  view bobbing/nausea, then calls `levelProjectionMatrixBuffer.getBuffer(projectionMatrix)` (the only
  `ProjectionMatrixBuffer#getBuffer(Matrix4f)` call in that method; the HUD projection uses the `Projection` overload in
  `render3dHud`). `@ModifyArg` on that call gives the exact matrix. `Projection#getMatrix` swaps near and far
  (**reversed-Z**) and passes `DeviceInfo#isZZeroToOne()` to JOML, so: depth `0` = far plane/sky (the clear value), NDC
  z = depth when zero-to-one, else `depth * 2 - 1`; `inverse(projection) * vec4(uv * 2 - 1, z, 1)` then `/ w` is the
  view-space position (camera looks down −Z). Distance and angle to the view axis do not depend on the Y direction of
  the texture coordinates, so the Vulkan/GL Y flip does not matter for them.
- **Sampling depth:** `RenderTarget#createBuffers` creates the depth texture with usage 15 (includes texture binding), and
  vanilla samples depth with `texture(InSampler, texCoord).r` (`blit_depth.fsh`). A pass that only has a color
  attachment may bind the main depth view as a `sampler2D` (use a NEAREST sampler). It must not write into main color
  while sampling it: copy the color first (`TextureTarget#copyColorFrom`).
- **Driver robustness (added 0.5.0):** Windows OpenGL with `GL_ARB_clip_control` and Vulkan run the zero-to-one depth
  path, the macOS dev run the `-1..1` path, so the zero-to-one branch is never exercised locally. GLSL leaves `clamp(NaN)`,
  NaN comparisons and derivatives of NaN to the driver; one Intel UHD 620 driver turned the whole FPV view black with the
  headlight on. Our pass therefore keeps the input colour for depth ≤ 0 or ≥ 1, `|w| ≤ 1e-9`, non-finite positions and
  invalid gain, accepts the result only if it is finite and not darker than the input, and verifies the invariant
  "never darker" on the real picture with two `copyTextureToBuffer` readbacks of a 4-row band before and after the pass
  (`video.HeadlightCheck`, `video.client.HeadlightProbe`, `docs/design/led-strip.md`). `isnan`/`isinf` compile through
  shaderc and SPIRV-Cross to GLSL 330 on the GL backend.
- **Custom blend in the GUI:** the GUI snippets are private, but a GUI pipeline can be rebuilt from public parts:
  `BindGroupLayouts.GLOBALS`, `PROJECTION`, `DYNAMIC_TRANSFORMS`, `SAMPLER0`, shaders `core/position_tex_color`,
  `DefaultVertexFormat.POSITION_TEX_COLOR`, `PrimitiveTopology.QUADS`, plus any `ColorTargetState(BlendFunction)`, e.g.
  `new BlendFunction(DST_COLOR, ONE, ZERO, ONE)` = `dst * (1 + src)` for a brightness boost. `GuiGraphicsExtractor#blit(RenderPipeline,
  Identifier, …)` accepts it. `GuiRenderer#executeDraw` uses `RenderSystem.getCompiledPipeline`, which **throws**
  (`IllegalStateException("Failed to find or load pipeline …")`) if compilation failed, so check
  `getCompiledPipelineNullable` before submitting such a blit.

## Verification").

## 0. Overview

- **No raw GL.** 26.3 renders through **RenderPearl** (`com.mojang.renderpearl.api.*`), which has an OpenGL and a **Vulkan** backend
  (`V/com/mojang/renderpearl/backend/{opengl,vulkan}`). Blaze3D (`com.mojang.blaze3d.*`) still provides `RenderSystem`,
  `RenderTarget`, `Std140Builder`, `FrameGraphBuilder`. Mods talk to `GpuDevice` / `CommandEncoder` / `RenderPass` / `RenderPipeline`.
- **Shaders** are Vulkan-style GLSL. They are compiled with **shaderc to SPIR-V** (`V/com/mojang/renderpearl/frontend/shaders/GlslCompiler.java`),
  and the GL backend converts them back with SPIRV-Cross to GLSL 330 (`.../backend/opengl/GlPipelineRecompiler.java`). Includes use
  `#include <ns:file.glsl>`. `#moj_import` no longer exists.
- **Post effects** are `assets/<ns>/post_effect/<name>.json` files plus `.vsh/.fsh` under `assets/<ns>/shaders/`. **The uniform values in the
  JSON are static.** They are written into immutable GPU buffers once, when the chain is created. There is no API for per-frame values.
- `GameRenderer.render()` runs this order: `renderLevel()` (world, hand, screen overlays) → `blitEntityOutline()` → **`applyPostEffects()`** → GUI.
  The effects that run come from 3 sources: `minecraft:end_of_frame`, the player's **server-synced** effect list (new `/posteffect` command),
  and the spectated-entity effect.
- **Recommendation:** write our own `FpvVideoPass` in `common`, based on `PostPass`. It has its own `RenderPipeline`s and a per-frame UBO in a
  `MappableRingBuffer`, and history targets for freezes and latency. One **common mixin** calls it right after
  `GameRenderer#applyPostEffects`. That gives the same hook on both loaders, a result after the hand and before the GUI, and full control
  of the uniforms.
- **World-space markers:** use `SubmitNodeCollector.submitShapeOutline` / `submitCustomGeometry` with `RenderTypes.lines*()` /
  `debugFilledBox()`. Hooks: NeoForge `SubmitCustomGeometryEvent`, Fabric `LevelRenderEvents.COLLECT_SUBMITS`. `Gizmos` is also an option.

## 1. Rendering abstraction: the classes we use

| Class | Path | Use |
|---|---|---|
| `GpuDevice` | `V/com/mojang/renderpearl/api/device/GpuDevice.java` | `createBuffer(Supplier<String>, int usage, long size)`, `createTexture(...)`, `createCommandEncoder()` via `RenderSystem.getDevice()` |
| `CommandEncoder` | `V/.../renderpearl/api/commands/CommandEncoder.java` | `createRenderPass(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clear)` (+ overload with `@Nullable GpuTextureView depth, OptionalDouble clearDepth`), `copyTextureToTexture(GpuTexture src, GpuTexture dst, int mip, dstX, dstY, srcX, srcY, w, h)`, `writeToBuffer(GpuBufferSlice, ByteBuffer)` (needs `USAGE_COPY_DST`), `clearColorTexture(GpuTexture, Vector4fc)`, `transientMemory()`. **`GpuDevice.createCommandEncoder()` returns one shared encoder** (`V/.../renderpearl/frontend/FrontendGpuDevice.java` L63), and any copy/clear while a `RenderPass` is open throws "Close the existing render pass before performing additional commands" (`FrontendCommandEncoder.java` L683) |
| `RenderPass` | `V/.../renderpearl/api/commands/RenderPass.java` | `setPipeline(CompiledRenderPipeline)`, `setUniform(String, GpuTextureView, GpuSampler)`, `setUniform(String, GpuBuffer\|GpuBufferSlice)`, `draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance)`, `pushConstants(ByteBuffer)` (vanilla never uses it, avoid) |
| `RenderPipeline(.Builder/.Snippet)` | `V/.../renderpearl/api/pipeline/RenderPipeline.java` | immutable pipeline description, see §2 |
| `BindGroupLayout` | `V/.../renderpearl/api/pipeline/BindGroupLayout.java` | `builder().withUniform(String name, UniformType type).build()` |
| `UniformType` | same package | `UNIFORM_BUFFER`, `COMBINED_IMAGE_SAMPLER`, `TEXEL_BUFFER` |
| `ColorTargetState` | same package | `DEFAULT = (no blend, RGBA8_UNORM, WRITE_ALL)`, `new ColorTargetState(BlendFunction)` |
| `GpuBuffer` | `V/.../renderpearl/api/buffers/GpuBuffer.java` | usage bits `USAGE_MAP_WRITE=2, USAGE_COPY_DST=8, USAGE_UNIFORM=128`, `map(boolean read, boolean write)` returns `GpuBufferSlice.MappedView` |
| `RenderSystem` | `V/com/mojang/blaze3d/systems/RenderSystem.java` | `getDevice()`, `getCompiledPipeline(RenderPipeline)` / `getCompiledPipelineNullable(...)`, `bindDefaultUniforms(RenderPass)`, `getSamplerCache()` |
| `SamplerCache` | `V/com/mojang/blaze3d/systems/SamplerCache.java` | `getClampToEdge(FilterMode)`, `getRepeat(FilterMode)` |
| `RenderTarget` / `TextureTarget` | `V/com/mojang/blaze3d/pipeline/` | `new TextureTarget(@Nullable String label, int w, int h, @Nullable GpuFormat color, @Nullable GpuFormat depth)`, `resize(w,h)`, `copyColorFrom(RenderTarget)` (copies `this.width x this.height`), `getColorTexture()`, `getColorTextureView()`, `getDepthTextureView()`, `hasDepth()`, public `width`/`height`. **No `close()`**: free with `destroyBuffers()`. Textures are created with usage 15 (COPY_DST/SRC, TEXTURE_BINDING, RENDER_ATTACHMENT) |
| `Std140Builder` / `Std140SizeCalculator` | `V/com/mojang/blaze3d/buffers/` | `Std140Builder.intoBuffer(ByteBuffer)` / `onStack(MemoryStack,int)`, then `putFloat/putVec2/putVec4/putIVec3/putMat4f` |
| `MappableRingBuffer` | `V/net/minecraft/client/renderer/MappableRingBuffer.java` | 3 buffers guarded by fences: `new MappableRingBuffer(Supplier<String>, int usage, int size)`, `currentBuffer()`, `rotate()`, `close()`. Usage must include MAP_READ or MAP_WRITE |
| `TransientMemory` | `V/.../renderpearl/api/buffers/TransientMemory.java` | `uploadGpu(ByteBuffer, long alignment, int usage)` → `GpuBufferSlice`, `allocateGpuMapped(size, alignment, usage)` → `MappedView`. Vanilla only uses it for texture staging (`V/net/minecraft/client/renderer/texture/CubeMapTexture.java` L68). UNVERIFIED: whether a slice stays valid for a UBO until the GPU consumes it. Prefer `MappableRingBuffer` (vanilla-proven for post UBOs) |

The main target is created by `new MainTarget(w, h)`, which calls `super("Main", GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT)` (`V/com/mojang/blaze3d/pipeline/MainTarget.java`). It is available through `GameRenderer#mainRenderTarget()`.
**NeoForge difference:** the main target comes from `ClientHooks.instantiateMainTarget` (`NFA/net/neoforged/neoforge/client/ClientHooks.java` L928), which uses `new MainTarget(w, h, depthFormat)` (`NF/com/mojang/blaze3d/pipeline/MainTarget.java` L24). The depth format becomes `D32_FLOAT_S8_UINT` or `D24_UNORM_S8_UINT` if any mod calls `ConfigureMainRenderTargetEvent#enableStencil()` (`NFA/net/neoforged/neoforge/client/event/ConfigureMainRenderTargetEvent.java` L36). Color stays `RGBA8_UNORM`. So never hard-code the main depth format.

## 2. Pipelines & shader conventions

**Builder** (`RenderPipeline.java` L133-430): `builder(Snippet...)`, `withLocation(Identifier)`, `withVertexShader(Identifier)`,
`withFragmentShader(Identifier)`, `withBindGroupLayout(BindGroupLayout)`, `withColorTargetState(ColorTargetState)`,
`withDepthStencilState(DepthStencilState)`, `withVertexBinding(int, VertexFormat)`, `withPrimitiveTopology(PrimitiveTopology)`,
`withShaderDefine(String[, int|float])`, `withCull(boolean)`, `build()`. `build()` throws if the location, the vertex or fragment shader,
or the topology is missing.
**Pitfall:** the `String` overloads call `Identifier.withDefaultNamespace(...)` (L161-185), so always pass `Identifier`s for our shaders.
**Pitfall:** `PipelineCache` keys on the `RenderPipeline` **object identity** (`Reference2ReferenceOpenHashMap`, `V/com/mojang/blaze3d/pipeline/PipelineCache.java`).
Build each pipeline once (`static final`). A pipeline built per frame is recompiled every frame.

- The shader id `propwash:post/fpv_analog` resolves to `assets/propwash/shaders/post/fpv_analog.fsh` (and `.vsh`). See
  `ShaderType.idConverter()` = `new FileToIdConverter("shaders", ext)` in `V/.../renderpearl/api/pipeline/ShaderType.java`.
- The include `#include <propwash:fpv_common.glsl>` resolves to `assets/propwash/shaders/include/fpv_common.glsl` (`ShaderManager.SHADER_INCLUDE_CONVERTER`,
  `V/net/minecraft/client/renderer/ShaderManager.java`). The resolver is `GlslCompiler.processInclude` → `Identifier.tryParse`.
- The header that every vanilla shader uses:
  `#version 330` + `#extension GL_ARB_separate_shader_objects : require`. Interstage variables need `layout(location = N) in/out`.
  Uniform blocks are `layout(std140) uniform Name { ... };`. Samplers are `uniform sampler2D XSampler;`. **No `binding=`**, because shaderc runs
  with `set_auto_bind_uniforms(true)` (GlslCompiler L80). The `name` in `BindGroupLayout.withUniform` must equal the GLSL block/sampler name.
- Use `gl_VertexIndex` (Vulkan GLSL), as in `V/assets/minecraft/shaders/core/screenquad.vsh` (fullscreen triangle, `draw(3,1,0,0)`, no vertex buffer).
- Compiler macros: `RENDERPEARL_DEPTH_IS_ZERO_TO_ONE`, `RENDERPEARL_EXPLICIT_DEPTH_INVARIANCE`, `RENDERPEARL_INSTANCE_INDEX_INCLUDES_BASE_INSTANCE` (GlslCompiler L84-94).
  Each one is **conditional**: `isZeroToOne`, `hintsAndWorkarounds().isExplicitDepthRequired()`, `shaderDrawParameters`. Guard with `#ifdef` and never assume one is set.
  Pipeline defines from `withShaderDefine` are added as macros too (L106-112).
  Depth is cleared to **0.0** (`RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE`), and `DepthStencilState.DEFAULT` compares with `GREATER_THAN_OR_EQUAL`
  (`V/.../renderpearl/api/pipeline/DepthStencilState.java` L8), so vanilla uses **reversed-Z**. Keep this in mind if you read depth. See also §4 for which depth is left in main at our hook.
- Built-in UBOs (`V/assets/minecraft/shaders/include/`):
  `globals.glsl` has `Globals { ivec3 CameraBlockPos; float GlintAlpha; vec3 CameraOffset; float GameTime; vec2 ScreenSize; int MenuBlurRadius; int UseRgss; }`.
  `GameTime` is the **day fraction** `((gameTime % 24000) + partial) / 24000` (`V/.../GlobalSettingsUniform.java`). It is useless for noise.
  `projection.glsl` has `Projection { mat4 ProjMat; }`. `dynamictransforms.glsl` has `DynamicTransforms { mat4 ModelViewMat; mat4 TextureMat; vec4 ColorModulator; vec3 ModelOffset; }`.
- `RenderSystem.bindDefaultUniforms(pass)` binds `Projection`, `Fog`, `Globals` and `Lighting` if they are set (RenderSystem L370-390).
  Binding a name that the pipeline does not declare is harmless, and vanilla does it in every post pass.
- **Compilation:** `ShaderManager.reload` precompiles `RenderPipelines.requiredPipelines()/optionalPipelines()` asynchronously. Any **other**
  pipeline is compiled **lazily and synchronously** on first `RenderSystem.getCompiledPipeline(p)` (`PipelineCache.get` → `device.compilePipeline(...).join()`,
  `V/com/mojang/blaze3d/pipeline/PipelineCache.java`). A resource reload swaps the whole cache, so never keep a `CompiledRenderPipeline` across frames.
  If compilation fails, `getCompiledPipelineNullable` returns `null` and **retries every call** (current cache, then fallback cache), so cache the failure yourself.
  Failures only log `Couldn't compile pipeline (...)` (`V/.../renderpearl/frontend/shaders/PipelineBuilder.java` L355). A lazily compiled pipeline is compiled
  again after every F3+T, which causes a hitch on the next use.
- **NeoForge precompile:** `RegisterRenderPipelinesEvent` (mod bus, `NFA/net/neoforged/neoforge/client/event/RegisterRenderPipelinesEvent.java`) with
  `registerPipeline(RenderPipeline)` / `registerOptionalPipeline(RenderPipeline)` / `registerOitPipelineSet(OitPipelineSet)`. It is fired from
  `RenderPipelines.registerCustomPipelines()` (`NF/net/minecraft/client/renderer/RenderPipelines.java` L1308-1318). A duplicate location throws.
  **Use `registerOptionalPipeline`.** A *required* pipeline that fails to compile makes `ShaderManager.apply` throw
  `"Failed to load required shader programs"` (`V/.../renderer/ShaderManager.java` L199-209), so the whole resource reload fails.
- **Fabric precompile (corrected):** there is no event, but Fabric API makes vanilla's private `RenderPipelines.register(RenderPipeline)` and
  `RenderPipelines.registerOptional(RenderPipeline)` **transitive-accessible** (`FA/fabric-transitive-access-wideners-v1/fabric-transitive-access-wideners-v1.classtweaker` L433-435;
  implementation at `V/.../renderer/RenderPipelines.java` L1283-1298, read by `requiredPipelines()/optionalPipelines()` on every reload).
  So from the **fabric module** (not common), call `RenderPipelines.registerOptional(FpvVideoPass.ANALOG)` in client init.
  UNVERIFIED: that the Fabric client entrypoint runs before the first shader reload. If it runs later, the next reload picks the pipeline up, and lazy compile covers the gap.
  `FA/fabric-rendering-v1/.../api/client/rendering/v1/FabricRenderPipeline.java` only adds GUI draw-mode extensions.
- **Snippets:** `RenderPipelines.POST_PROCESSING_SNIPPET` is **public** (`= builder(GLOBALS_SNIPPET).withPrimitiveTopology(TRIANGLES)`, L343-345).
  Most other snippets (`GLOBALS_SNIPPET`, `MATRICES_FOG_SNIPPET`, `LINES_SNIPPET`, ...) are private in vanilla. The public ones in vanilla are
  `OIT_DEPTH_BOUNDS_SNIPPET`, `OIT_TRANSMITTANCE_SNIPPET`, `OIT_ACCUMULATE_SNIPPET`, `GLINT_SNIPPET`, `GLINT_SPECIAL_SNIPPET`, `ANIMATE_SPRITE_SNIPPET`
  and `POST_PROCESSING_SNIPPET`. NeoForge makes the private ones public (`NFA/META-INF/accesstransformergenerated.cfg`, for example `NF/.../RenderPipelines.java` L28),
  and on Fabric they are `transitive-accessible` (`FA/fabric-transitive-access-wideners-v1/fabric-transitive-access-wideners-v1.classtweaker` L392ff).
  In `common`, use only public ones or add an AT line to `common/src/main/resources/META-INF/accesstransformer.cfg`. That file does not exist yet.
  `common/build.gradle` L9-12 and `neoforge/build.gradle` L9-12 pick it up if present. **The AT does nothing on Fabric.** Fabric reads
  `common/src/main/resources/propwash.accesswidener` (`fabric/build.gradle` L12-14), which is only needed for members that Fabric API does not already widen.
- **Validation** (`V/com/mojang/renderpearl/frontend/FrontendRenderPass.java`): `setPipeline` (L105-129) checks that the color-target count and format match
  the pass attachments (`ColorTargetState.DEFAULT` = RGBA8_UNORM = main target, OK). This check always runs.
  `setUniform(name, view, sampler)` (L132-145) requires view and sampler to be **both non-null or both null** (null removes the binding).
  UBO slice offsets must be aligned to `minUniformOffsetAlignment` (L153-160). `validateDraw` (L507-617) checks for missing uniforms, `USAGE_UNIFORM` on UBOs,
  `USAGE_TEXTURE_BINDING` on sampled textures, and whether a pipeline that wants a depth texture has a depth attachment. It runs **only when
  `FrontendGpuDevice.STRICT_VALIDATION`** (= `SharedConstants.IS_RUNNING_IN_IDE`, `FrontendGpuDevice.java` L39). So a missing binding throws in the
  dev client but silently misrenders in production. Always test in the dev run.

## 3. The vanilla post effect system (PostChain)

**Loading:** `ShaderManager.loadConfigs` scans `post_effect/*.json` (`FileToIdConverter.json("post_effect")`) and parses each file with
`PostChainConfig.CODEC` (`V/.../renderer/PostChainConfig.java`). Chains are built lazily by `ShaderManager#getPostChain(Identifier, Set<Identifier> allowedTargets)`,
which is cached per reload and returns `null` on failure. After a failure it triggers the recovery handler once (`ShaderManager.tryTriggerRecovery`, L240-245).
That handler is `Minecraft::triggerResourcePackRecovery` (`V/net/minecraft/client/Minecraft.java` L581, L888-899). It **clears resource packs, or crashes the game**
if nothing can be cleared and at most 1 pack is selected. A broken JSON chain from our mod is therefore much worse than a broken pipeline in our own pass,
which only logs.

**JSON format** (adapted from the real file `V/assets/minecraft/post_effect/creeper.json`, which has 2 passes: `main → swap` with `color_convolve`, then `swap → minecraft:main` with `bits`):
```json
{ "targets": { "swap": {} },            // InternalTarget: width?, height?, persistent=false, clear_color=0 (ARGB)
  "passes": [ {
    "vertex_shader": "minecraft:core/screenquad",
    "fragment_shader": "propwash:post/fpv_digital",
    "inputs": [ { "sampler_name": "In", "target": "minecraft:main", "bilinear": true },          // TargetInput (+ "use_depth_buffer")
                { "sampler_name": "Mask", "location": "propwash:blocks", "width": 64, "height": 64 } ], // TextureInput → textures/effect/blocks.png
    "output": "swap",
    "uniforms": { "DigitalConfig": [ { "name": "BlockSize", "type": "float", "value": 16.0 } ] } }
    /* , { ...second pass: input "swap" → "output": "minecraft:main" ... } */ ] }
```
- **The last pass must write `minecraft:main`** (as creeper.json's 2nd pass does). A chain that only writes `swap` has no visible effect.
  Target keys and `output`/`target` are `Identifier`s, so `"swap"` means `minecraft:swap` (`PostChainConfig.InternalTarget`/`Pass` codecs, `V/.../renderer/PostChainConfig.java`).
  Sampler names must be unique per pass (`INPUTS_CODEC` validation).
- Uniform `type` values are `int|ivec3|float|vec2|vec3|vec4|matrix4x4` (`UniformValue.Type`, `V/.../renderer/UniformValue.java` L106-112). Each entry is a dispatch codec
  `{type, value}`. The `name` field is not part of the codec: it is ignored and serves only as documentation, because values are packed std140 in list order.
  The block name (`DigitalConfig`) must match the GLSL `uniform` block.
- `PostChain.createPass` (`V/.../renderer/PostChain.java`) builds `RenderPipeline.builder(POST_PROCESSING_SNIPPET)` with the location
  `<chainId>/<passIndex>`. The bind group gets `<sampler_name>Sampler` for each input, then `SamplerInfo` (`vec2 OutSize; vec2 InSize...`, one vec2
  per input), then one UBO per `uniforms` key. It is `ColorTargetState.DEFAULT` with no depth state.
- For GameRenderer effects, `minecraft:main` is the only external target allowed (`LevelTargetBundle.MAIN_TARGETS`, `V/.../renderer/LevelTargetBundle.java`).
  `entity_outline` is only allowed for the outline chain.
- `"persistent": true` internal targets survive across frames (`PostChain.getOrCreatePersistentTarget`). You can do feedback or ghosting with them in pure JSON:
  read the target in pass N, then write it again later. They are freed by `closePersistentTargets()` when the effect is no longer requested.
- **Why JSON uniforms can't be dynamic:** `PostPass` constructor (`V/.../renderer/PostPass.java` L42-72) creates `customUniforms` with
  `createBuffer(label, 128 /*UNIFORM only*/, data)`. Without COPY_DST or MAP_WRITE, `writeToBuffer` and `map` are not allowed later, and the map is private.
  Only `SamplerInfo` is refreshed per frame, through a `MappableRingBuffer(..., 130 = UNIFORM|MAP_WRITE, ...)`.
- `PostChain#process(RenderTarget main, GraphicsResourceAllocator)` is `@Deprecated` but still public, and vanilla uses it (`GameRenderer.processBlurEffect`, `applyPostEffects`).
  You can run a JSON chain yourself: `mc.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS)` (**null-check**) `.process(mc.gameRenderer.mainRenderTarget(), pool)`.
  Internal targets are allocated as RGBA8 **with a D32 depth** attachment (`RenderTargetDescriptor.TextureProperties.DEFAULT_DEPTH`, `PostChain.addToFrame`).
  `PostPass` also swaps in an orthographic `Projection` around each pass (`RenderSystem.backupProjectionMatrix/setProjectionMatrix/restoreProjectionMatrix`).
  Use your own `new CrossFrameResourcePool(3)` and call `pool.endFrame()` every frame (`V/com/mojang/blaze3d/resource/CrossFrameResourcePool.java`).
  `GraphicsResourceAllocator.UNPOOLED` reallocates every frame.

## 4. How GameRenderer applies post effects each frame

`V/net/minecraft/client/Minecraft.java` L1294-1306 (`runTick`) runs `gameRenderer.update(dt)` → `pick()` → `gameRenderer.extract(dt, advance)`
→ (NeoForge: `ClientHooks.fireRenderFramePre`, `NF/.../Minecraft.java` L1328) → `gameRenderer.render()` → swapchain blit.

`V/net/minecraft/client/renderer/GameRenderer.java`:
```java
public void update(DeltaTracker dt) {            // L426
    ... this.requestedPostEffects.clear();
    this.requestedPostEffects.add(END_OF_FRAME_POST_EFFECT);                 // "minecraft:end_of_frame" (missing = silently skipped)
    if (this.minecraft.player != null) this.requestedPostEffects.addAll(this.minecraft.player.getActivePostEffects());
    if (this.spectatedEntityPostEffect != null && this.spectatedEntityEffectActive) this.requestedPostEffects.add(this.spectatedEntityPostEffect);
}
public void extract(...)  // L442: copies requestedPostEffects → gameRenderState.requestedPostEffects if playerRenderState.hasPlayer (LocalPlayer != null), minus failed ones
public void render() {    // L469
    ... globalSettingsUniform.update(...);
    if (shouldRenderLevel) { preparePostEffects(gameRenderState.requestedPostEffects); lightmap.render(..);
        renderLevel();                // world + render3dHud (hand, ScreenEffectRenderer overlays, 3D crosshair)
        tryTakeScreenshotIfNeeded(); minecraft.levelRenderer.blitEntityOutline();
        applyPostEffects();           // for each PostChain: postChain.process(mainRenderTarget, resourcePool)
    } ...  guiRenderer.render();      // GUI is drawn AFTER post effects, so it is never post-processed
}
```
- `public List<Identifier> getRequestedPostEffects()` returns the mutable list, which is cleared each `update()`. `GameRenderState.requestedPostEffects` is a
  `public final List<Identifier>` (`V/net/minecraft/client/renderer/state/GameRenderState.java`).
- If any effect is applied, `renderLevel` sets `consistentDepthRequired = !appliedPostEffects.isEmpty()` (L671). The hand then gets its own depth
  target (`hud3DTarget`), and `integrate3DHudDepth()` merges it back into main depth (L722-745). Every chain that resolves (a missing `end_of_frame` does not)
  costs this extra depth work even if the chain itself does nothing.
- **Depth at our hook (added):** `render3dHud` clears the depth it draws the hand into to 0.0 (L686-688). **Without** a vanilla chain that is the
  main depth, so after `renderLevel()` **main depth holds only the hand/screen-effect depth, not the world**. With a chain applied, main depth is
  world + hand. After `applyPostEffects()`, `render()` clears main depth again before the GUI (L507). So our pass must not rely on world depth. If a
  depth effect is ever needed, copy depth earlier (NeoForge `RenderLevelStageEvent.AfterLevel`, Fabric `LevelRenderEvents.END_MAIN`) into our own
  `TextureTarget` that has a depth format.
- **Server-driven effects (new in 26.x):** `ServerPlayer#addPostEffect(Identifier)`, `removePostEffect(Identifier)`, `clearPostEffects()`, `getPostEffects()`
  (`V/net/minecraft/server/level/ServerPlayer.java` L1060-1090). They are sent as `ClientboundPostEffectsPacket` (`sendPostEffects()`, L1055) **only when
  the list is dirty** (checked in `ServerPlayer.tick`, L623-625), on join/respawn (`V/net/minecraft/server/players/PlayerList.java` L221/L429) and on
  dimension change (ServerPlayer L1189). They land in `LocalPlayer#setActivePostEffects(List)` (`V/.../client/player/LocalPlayer.java` L1328) via
  `ClientCommonPacketListenerImpl#handlePostEffects` (`V/.../client/multiplayer/ClientCommonPacketListenerImpl.java` L203-210).
  Command: `/posteffect add|clear|list|remove` (gamemaster permission, `V/net/minecraft/server/commands/PostEffectCommand.java` L50-82).
  **Pitfall:** they are saved in player NBT as `"post_effects"` (ServerPlayer L413/433), so they survive relogs and crashes. A client-side
  `setActivePostEffects` persists until the next server packet (list change, respawn, dimension change, relog) overwrites it.
- **Client-side injection without internals:** add our id to `gameRenderer.getRequestedPostEffects()` after `update()`. On Fabric use
  `LevelExtractionEvents.END_EXTRACTION` (`FA/fabric-rendering-v1/net/fabricmc/fabric/api/client/rendering/v1/level/LevelExtractionEvents.java`, callback
  `endExtraction(LevelExtractionContext ctx)`, where `ctx.gameRenderer()` comes from `AbstractLevelRenderContext`). It fires at RETURN of `LevelExtractor.extract`
  (`FA/fabric-rendering-v1/net/fabricmc/fabric/mixin/client/rendering/LevelExtractorMixin.java` L69).
  On NeoForge use `ExtractLevelRenderStateEvent` (game bus, `NFA/net/neoforged/neoforge/client/event/ExtractLevelRenderStateEvent.java`, posted at the end of
  `LevelExtractor.extract`, `NF/net/minecraft/client/renderer/extract/LevelExtractor.java` L243). Both run inside `GameRenderer.extract()` before the copy
  loop, and only when `shouldRenderLevel`.
  Alternatively, on NeoForge only, append to `gameRenderState().requestedPostEffects` in `RenderFrameEvent.Pre` (`NFA/.../client/event/RenderFrameEvent.java`).
  That bypasses the `failedPostEffects` filter.
- **Do not ship `post_effect/end_of_frame.json`.** It is a resource-pack slot, and we would fight with resource packs over it.

## 5. Reusing the spectator "entity post effect" for the drone camera

Vanilla `GameRenderer#checkEntityPostEffect(@Nullable Entity)` is a hard switch: Creeper → `creeper`, Spider → `spider`, Enderman → `invert`,
otherwise clear. It is called from `Minecraft#setCameraEntity(Entity)` (L2702) and from F5 toggling (L1898, which passes `null` unless first person).
`keyToggleSpectatorShaderEffects` calls `gameRenderer.toggleSpectatorPostEffect()` (Minecraft L1908-1909), and `clearSpectatedEntityPostEffect()` runs while `level == null` (L1829-1830).
`toggleSpectatorPostEffect()`, `clearSpectatedEntityPostEffect()` and `spectatedEntityPostEffect()` are public (`V/.../renderer/GameRenderer.java` L217, L222, L314).
`setSpectatedEntityPostEffect` is `private` in vanilla (L243) and made `public` by NeoForge (`NF/.../GameRenderer.java` L244).
- **NeoForge:** `RegisterEntitySpectatorShadersEvent#register(EntityType<?>, Identifier)` (mod bus, `NFA/.../client/event/RegisterEntitySpectatorShadersEvent.java`).
  The patched default branch calls `ClientHooks.loadEntityShader(cameraEntity, this)` (`NFA/.../client/ClientHooks.java` L426).
- **Fabric:** no API. You would need a mixin on `checkEntityPostEffect`.
- **Verdict:** it works when you call `mc.setCameraEntity(drone)`, but it only gives **one static JSON chain per EntityType**, with no dynamic signal
  and no analog/digital switch per build. The player can also toggle it off with a vanilla key. Use it only as a zero-code fallback.

## 6. Recommended: our own FPV video pass (common)

**Hook:** a common mixin runs right after `applyPostEffects()`. This is after the hand, screen overlays and vanilla/JSON effects, and before the GUI.
The code at that spot is identical in NeoForge's `GameRenderer.render()` (`NF/.../GameRenderer.java` L504: `renderLevel` → `blitEntityOutline` → `applyPostEffects` → `guiRenderer.render`).
```java
// common/src/main/java/de/lardtom/propwash/mixin/MixinGameRendererFpv.java  (add to "client" in propwash.mixins.json)
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererFpv {
    @Inject(method = "render()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V", shift = At.Shift.AFTER))
    private void propwash$fpvVideo(CallbackInfo ci) {
        FpvVideoPass.INSTANCE.apply(((GameRenderer) (Object) this).mainRenderTarget());
    }
}
```
A NeoForge-only alternative without a mixin is `RenderLevelStageEvent.AfterLevel` (game bus, `NFA/net/neoforged/neoforge/client/event/RenderLevelStageEvent.java` L209).
**Caveat:** it is posted in `renderLevel` *before* `render3dHud` (`NF/net/minecraft/client/renderer/GameRenderer.java` L681-682), so the hand and overlays would be
drawn over our effect. Fabric has no after-level/pre-GUI event: `LevelRenderEvents.END_MAIN` fires at RETURN of the main frame-graph pass lambda
(`FA/fabric-rendering-v1/net/fabricmc/fabric/mixin/client/rendering/LevelRendererMixin.java` L129-131). That is why we use the mixin on both loaders.
`applyPostEffects` is `private`, but an `INVOKE` target on a private method works fine. `render()V` is the only `render` overload in both
`V/` (L469) and `NF/` (L473).

**Pass class.** This is a pattern copy of `PostPass.addToFrame`, executed immediately:
```java
public final class FpvVideoPass {
    public static final FpvVideoPass INSTANCE = new FpvVideoPass();
    private static Identifier id(String p) { return Identifier.fromNamespaceAndPath(Constants.MOD_ID, p); }
    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
        .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("HistorySampler", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("FpvConfig", UniformType.UNIFORM_BUFFER).build();
    private static RenderPipeline pipeline(String name) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)      // GLOBALS layout + TRIANGLES
            .withLocation(id("pipeline/" + name))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(id("post/" + name))
            .withBindGroupLayout(LAYOUT).withColorTargetState(ColorTargetState.DEFAULT).build();
    }
    public static final RenderPipeline ANALOG = pipeline("fpv_analog");
    public static final RenderPipeline DIGITAL = pipeline("fpv_digital");
    // std140: vec4 Signal (strength, noise, timeSec, seed) + vec4 Params (bleedPx, scanlines, blockPx, freeze)
    private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();

    private MappableRingBuffer ubo; private TextureTarget frame, history; private boolean broken;

    public void apply(RenderTarget main) {
        FpvVideoState s = FpvVideoState.current();          // our client state: null when not flying FPV
        if (s == null || broken) return;
        RenderPipeline p = s.digital() ? DIGITAL : ANALOG;
        CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(p);
        if (compiled == null) { broken = true; Constants.LOG.error("FPV pipeline failed: {}", p.getLocation()); return; }
        ensureTargets(main.width, main.height);
        frame.copyColorFrom(main);                          // can't sample the attachment we render into
        try (GpuBufferSlice.MappedView v = ubo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(v.data())
                .putVec4(s.signal(), s.noise(), (Util.getMillis() % 3_600_000L) / 1000f, s.seed())
                .putVec4(s.bleedPx(), s.scanlines(), s.blockPx(), s.frozen() ? 1f : 0f);
        }
        GpuSampler lin = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "propwash fpv video", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(compiled);
            RenderSystem.bindDefaultUniforms(pass);           // Globals (ScreenSize) etc.
            pass.setUniform("InSampler", frame.getColorTextureView(), lin);
            pass.setUniform("HistorySampler", history.getColorTextureView(), lin);
            pass.setUniform("FpvConfig", ubo.currentBuffer());
            pass.draw(3, 1, 0, 0);                            // fullscreen triangle from screenquad.vsh
        }
        ubo.rotate();
        if (!s.frozen()) history.copyColorFrom(frame);     // digital freeze = keep showing history
    }
    private void ensureTargets(int w, int h) {
        if (ubo == null) ubo = new MappableRingBuffer(() -> "propwash fpv ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        if (frame == null) { frame = new TextureTarget("propwash fpv frame", w, h, GpuFormat.RGBA8_UNORM, null);
                             history = new TextureTarget("propwash fpv history", w, h, GpuFormat.RGBA8_UNORM, null); clearHistory(); }
        else if (frame.width != w || frame.height != h) { frame.resize(w, h); history.resize(w, h); clearHistory(); }
    }
    // new textures are NOT cleared by createBuffers() (RenderTarget L91-110), so history would contain garbage on the first frame
    private void clearHistory() {
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(history.getColorTexture(), new Vector4f(0f, 0f, 0f, 1f));
    }
    public void close() {   // call on FPV exit / disconnect (render thread); RenderTarget has no close()
        if (ubo != null) { ubo.close(); ubo = null; }
        if (frame != null) { frame.destroyBuffers(); history.destroyBuffers(); frame = history = null; }
    }
    public void onResourceReload() { broken = false; }     // e.g. from a reload listener, so a fixed resource pack retries
}
```
- **Per-loader glue (precompile, optional):** NeoForge, on the mod event bus:
  `@SubscribeEvent static void onPipelines(RegisterRenderPipelinesEvent e) { e.registerOptionalPipeline(FpvVideoPass.ANALOG); e.registerOptionalPipeline(FpvVideoPass.DIGITAL); }`.
  Use **optional**, because a failing *required* pipeline aborts the reload (§2). Fabric, in the fabric module's client initializer:
  `RenderPipelines.registerOptional(FpvVideoPass.ANALOG); RenderPipelines.registerOptional(FpvVideoPass.DIGITAL);`. This is accessible through
  Fabric API's transitive access widener (§2), so no AW entry of our own is needed. As a fallback on both loaders, call
  `RenderSystem.getCompiledPipelineNullable(...)` once when FPV starts, to avoid a hitch in the middle of the flight.
- **Latency** (`VideoType.latencyMs`, 12-30 ms): keep a ring of K `TextureTarget` history frames, K = round(latencyMs / frameTimeMs), and sample the oldest one.
  At 1080p each frame is about 8 MB. **Freeze / stutter:** stop updating `history` and sample it. **Macroblocks:** quantize the UV to `blockPx` cells and mix
  in the stale `HistorySampler` per cell with a hash of cell and time (this looks like P-frame loss).
- Use our own time uniform (`Util.getMillis()`, `V/net/minecraft/util/Util.java`). `Globals.GameTime` pauses with the game and wraps every 20 min.

**Fragment shader skeleton** (`common/src/main/resources/assets/propwash/shaders/post/fpv_analog.fsh`):
```glsl
#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:globals.glsl>            // ScreenSize
uniform sampler2D InSampler;
uniform sampler2D HistorySampler;
layout(std140) uniform FpvConfig { vec4 Signal; vec4 Params; };   // must match Java packing order exactly
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    float bad = 1.0 - Signal.x;                                   // 0 = perfect, 1 = lost
    float line = floor(texCoord.y * ScreenSize.y);
    float t = floor(Signal.z * 60.0);
    vec2 uv = texCoord + vec2((hash(vec2(line, t)) - 0.5) * bad * bad * 0.02, 0.0);   // horizontal sync jitter
    vec3 c = texture(InSampler, uv).rgb;
    float bleed = Params.x / ScreenSize.x;                         // chroma smear to the right
    vec3 cs = texture(InSampler, uv - vec2(bleed, 0.0)).rgb;
    float y = dot(c, vec3(0.299, 0.587, 0.114));
    c = y + (cs - dot(cs, vec3(0.299, 0.587, 0.114))) * (1.0 - 0.5 * bad);  // luma sharp, chroma bleeds + desaturates
    c *= 1.0 - Params.y * 0.5 * (0.5 + 0.5 * sin(texCoord.y * ScreenSize.y * 3.14159));  // scanlines
    float n = hash(texCoord * ScreenSize + t);
    c = mix(c, vec3(n), clamp(Signal.y * bad * 1.5, 0.0, 1.0));       // snow
    fragColor = vec4(c, 1.0);                                        // always write alpha 1 (like vanilla post shaders)
}
```

## 7. Custom world-space geometry (home marker, gate highlight)

26.x renders entities and features through **submit nodes**: things are collected into `SubmitNodeStorage` during level render and drawn later
by `FeatureRenderDispatcher`. `MultiBufferSource` no longer exists in 26.3 (no file, no references).
- `OrderedSubmitNodeCollector` (`V/.../renderer/OrderedSubmitNodeCollector.java`):
  `void submitCustomGeometry(PoseStack, RenderType, SubmitNodeCollector.CustomGeometryRenderer)`, where the lambda is `(PoseStack.Pose pose, VertexConsumer buf)`.
  Also `void submitShapeOutline(PoseStack, VoxelShape, RenderType, int color, float width, boolean afterTerrain)` and
  `submitGizmoPrimitives(...)`. `SubmitNodeCollector#order(int)` returns an ordered sub-collector.
- **Coordinates are camera-relative.** Vanilla does `poseStack.translate(pos.getX() - cameraPos.x, ...)` with
  `cameraPos = levelRenderState.cameraRenderState.pos` (`V/.../renderer/LevelRenderer.java` L1071-1101, `submitBlockOutline`).
- Useful public render types (`V/.../renderer/rendertype/RenderTypes.java`): `lines()`, `linesTranslucent()`, `linesTranslucentNoDepthWrite()`,
  `linesDepthBias()`, `secondaryBlockOutline()` (format `POSITION_COLOR_NORMAL_LINE_WIDTH`, topology LINES, `LINES_SNIPPET` in `V/.../renderer/RenderPipelines.java` L233-240);
  `debugFilledBox()`, `debugQuads()` (`POSITION_COLOR`, QUADS, `BlendFunction.TRANSLUCENT`, depth test `GREATER_THAN_OR_EQUAL` without depth write, no cull:
  `DEBUG_FILLED_SNIPPET` L250-260, pipelines L958-966); also `debugPoint()`, `debugTriangleFan()` (RenderTypes L799/L807); `beaconBeam(Identifier, boolean)` (L664).
- Line vertex call site: `V/.../renderer/DebugCrosshairRenderer.java` L32 uses `addVertex(x,y,z).setColor(argb).setNormal(dx,dy,dz).setLineWidth(w)`.
  The normal is the **line direction**. With a pose, use `addVertex(PoseStack.Pose, x,y,z)` / `setNormal(PoseStack.Pose, x,y,z)` (`V/com/mojang/blaze3d/vertex/VertexConsumer.java` L118/L132).
- **Custom RenderType:** `RenderType.create(String, RenderSetup)` is **package-private** in vanilla (`V/.../rendertype/RenderType.java` L40). It is public in
  NeoForge (`NF/.../rendertype/RenderType.java` L40, via `NFA/META-INF/accesstransformer.cfg` L42) and transitive-accessible on Fabric
  (`FA/fabric-transitive-access-wideners-v1/fabric-transitive-access-wideners-v1.classtweaker` L128). To call it from common, add this AT line (compile-time
  for common + NeoForge; Fabric already has it at runtime):
  `public net.minecraft.client.renderer.rendertype.RenderType create(Ljava/lang/String;Lnet/minecraft/client/renderer/rendertype/RenderSetup;)Lnet/minecraft/client/renderer/rendertype/RenderType;`
  Build it with `RenderSetup.builder(pipeline)....createRenderSetup()`, as vanilla does for `LINES` (RenderTypes L484). Prefer the built-in types for markers.

Per-loader hook to get a collector:
```java
// NeoForge (game bus): NFA/net/neoforged/neoforge/client/event/SubmitCustomGeometryEvent.java (getters L52/L59/L66),
// posted in LevelRenderer.submitFeatures before finalizeGizmoCollection() (NF/net/minecraft/client/renderer/LevelRenderer.java L345/L347)
@SubscribeEvent static void onSubmit(SubmitCustomGeometryEvent e) {
    MarkerRenderer.submit(e.getSubmitNodeCollector(), e.getPoseStack(), e.getLevelRenderState().cameraRenderState.pos);
}
// Fabric: FA/fabric-rendering-v1/net/fabricmc/fabric/api/client/rendering/v1/level/LevelRenderEvents.java L71 (COLLECT_SUBMITS, callback
// collectSubmits(LevelRenderContext)); LevelRenderContext has submitNodeCollector()/poseStack(), AbstractLevelRenderContext has levelState().
// Fires at RETURN of LevelRenderer.submitFeatures (FA/fabric-rendering-v1/net/fabricmc/fabric/mixin/client/rendering/LevelRendererMixin.java L90-92)
LevelRenderEvents.COLLECT_SUBMITS.register(ctx ->
    MarkerRenderer.submit(ctx.submitNodeCollector(), ctx.poseStack(), ctx.levelState().cameraRenderState.pos));
// common
static void submit(SubmitNodeCollector c, PoseStack ps, Vec3 cam) {
    ps.pushPose(); ps.translate(home.x - cam.x, home.y - cam.y, home.z - cam.z);
    c.submitShapeOutline(ps, Shapes.create(-0.5, 0, -0.5, 0.5, 2, 0.5), RenderTypes.linesTranslucent(), 0xFF00FF88, 3f, false);
    c.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, buf) -> { /* 4 x addVertex(pose,..).setColor(argb) per quad */ });
    ps.popPose();
}
```
- **Gizmos alternative** (`V/net/minecraft/gizmos/Gizmos.java`): `Gizmos.cuboid(AABB, GizmoStyle)`, `line(Vec3, Vec3, int argb, float width)`,
  `circle`, `arrow`, `point`, `billboardText`. All of them return `GizmoProperties` with `setAlwaysOnTop()` (useful to show home through walls),
  `persistForMillis`, `fadeOut`. `GizmoStyle.stroke(argb[, w]) / fill(argb) / strokeAndFill(...)`. **Only valid while a collector is set.** During
  `gameRenderer.render()` that is `levelRenderer.collectPerFrameRenderThreadGizmos()` (Minecraft L1302). Emit them before `finalizeGizmoCollection()`:
  NeoForge `SubmitCustomGeometryEvent` works, and on Fabric use `LevelRenderEvents.BEFORE_GIZMOS` (L131, injected at the INVOKE of `finalizeGizmoCollection`,
  `LevelRendererMixin.java` L117-119), **not** `COLLECT_SUBMITS` (it runs after finalize). `Gizmos.addGizmo` throws
  `"Gizmos cannot be created here! No GizmoCollector has been registered."` otherwise (`Gizmos.java` L22-29; the collector is a `ThreadLocal`).
  They use world coordinates, so no camera translation is needed.
- World geometry is drawn **before** our FPV pass, so markers get noise and blocks like everything else in the video. Draw HUD/OSD-style overlays in the
  GUI instead if they must stay crisp. A real analog OSD is mixed in before transmission, so for realism draw the OSD into the frame before the pass.
  GUI hooks (added, verified): NeoForge `RegisterGuiLayersEvent#registerAboveAll(Identifier, GuiLayer)` / `registerAbove(Identifier other, Identifier, GuiLayer)`
  (`NFA/net/neoforged/neoforge/client/event/RegisterGuiLayersEvent.java` L47-81, `GuiLayer.render(GuiGraphicsExtractor, DeltaTracker)` in
  `NFA/.../client/gui/GuiLayer.java` L13). Fabric `HudElementRegistry.addLast(Identifier, HudElement)` / `attachElementAfter(...)`
  (`FA/fabric-rendering-v1/net/fabricmc/fabric/api/client/rendering/v1/hud/HudElementRegistry.java` L81-123, `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`
  L35). Note that the GUI context type is `GuiGraphicsExtractor` in 26.3, not `GuiGraphics`.

## 8. Iris / Sodium

UNVERIFIED: there are no Iris or Sodium sources in `.ref`, so nothing in this section is verified. Our pass only touches `mainRenderTarget` after vanilla's
`applyPostEffects`, and uses only public RenderPearl API. With shader packs, test whether Iris has already composited into the main target at that point.
Also check that `LevelRenderEvents`/`SubmitCustomGeometryEvent` still fire under Sodium's level renderer. Plan a config toggle to disable the pass.

## 8a. Asynchronous readback of a render target (verified for the action cam)

- `CommandEncoder#copyTextureToBuffer(GpuTexture src, GpuBuffer dst, long offset, Runnable callback, int mipLevel[, x, y, w, h])`
  (`V/com/mojang/renderpearl/api/commands/CommandEncoder.java`). Needs `USAGE_COPY_SRC` (2) on the texture (every `RenderTarget`
  texture is created with usage 15, `V/com/mojang/blaze3d/pipeline/RenderTarget.java` L104) and `USAGE_COPY_DST` (8) on the buffer;
  vanilla `Screenshot` uses a buffer with usage 9 (`MAP_READ | COPY_DST`). No render pass may be open (shared encoder).
- The callback does **not** block: the GL backend issues `glReadPixels` into the buffer (a pixel-pack buffer) and calls
  `RenderSystem.queueFencedTask(callback)` (`V/.../backend/opengl/GlCommandEncoder.java` L366-380); the Vulkan backend records
  `vkCmdCopyImageToBuffer` and runs the callback when the submit has finished (`queueForDestroy`). `RenderSystem.executePendingTasks()`
  polls the fences without waiting (`awaitCompletion(0)`) once per frame before `GameRenderer.render()` (`V/.../Minecraft.java` L1304),
  so the callback runs on the render thread one or more frames later. Map inside the callback with `buffer.map(offset, length, true,
  false)` and close the `MappedView`.
- Memory layout of the copy: rows are tightly packed (`bufferRowLength = width`, GL pack alignment = format alignment), **row 0 is
  texture row 0**, which for the main target is the **bottom** of the picture (`Screenshot` flips with `height - y - 1`). A fragment
  at `texCoord.y ≈ 0` of the fullscreen triangle writes texture row 0 on both backends; `texelFetch(sampler, ivec2(x, row), 0)` reads
  in the same row order.
- Pattern used by `video.client.record.ActionCamCapture`: render into an own `TextureTarget` (RGBA8, no depth), pack the picture there
  (I420 in an RGBA8 texture of width/4 × height·1.5), keep a ring of readback buffers and skip a frame when none is free.

## 9. Pitfalls checklist

1. You **cannot sample the texture you render into**. Copy main to a scratch target first (`RenderTarget#copyColorFrom`). `copyTextureToTexture` throws if
   the rectangle does not fit into source or destination (`FrontendCommandEncoder.java` L688-720). Format equality is not checked in the frontend
   (UNVERIFIED backend behaviour), so keep both at RGBA8_UNORM. Resize when `main.width/height` change (`GameRenderer.resize`, L332-337, only resizes vanilla targets).
2. JSON post uniforms are static. Per-frame values need our own UBO (`MappableRingBuffer`, usage `UNIFORM|MAP_WRITE`, `rotate()` after the draw).
3. std140 packing: a `vec3` is 16-byte aligned (only a following scalar packs into its 4th slot). Prefer `vec4`s and keep the Java `Std140Builder` order identical to the GLSL block.
4. Use `Identifier` overloads on the pipeline builder. The `String` ones silently use the `minecraft:` namespace.
5. Don't cache `CompiledRenderPipeline`. Remember that a failed compile retries on every call.
6. Every requested vanilla post chain forces `consistentDepthRequired` (an extra depth target and an integrate pass). Our mixin pass does not.
7. Server `addPostEffect` state is saved in player NBT. Always remove it on FPV exit and on login.
8. `hasPlayer` gates vanilla post effects: nothing runs without a `LocalPlayer`. Our mixin runs only inside `if (shouldRenderLevel)`.
9. The GUI (including our OSD and the workbench screens) is drawn after the pass and is never affected. The pause-menu blur (`processBlurEffect`) runs on
   the already-processed image.
10. Everything runs on the render thread (`RenderSystem.assertOnRenderThread()` in `RenderTarget`). Never touch GPU objects from network or tick threads.
    Read our FPV state in `apply()` or capture it during extract.
11. Draw-time validation (missing uniform, wrong usage flags) only runs in the dev client (`STRICT_VALIDATION = IS_RUNNING_IN_IDE`). A missing binding
    silently misrenders in production.
12. Keep `RenderPipeline`s as `static final` singletons (the cache is identity-keyed). Register them as **optional** on NeoForge.
13. A broken **JSON** post chain triggers `Minecraft.triggerResourcePackRecovery` (packs get disabled, or the game crashes). A broken pipeline in our own pass only logs.
14. At our hook, main depth holds **only hand depth** unless a vanilla chain is applied (§4). Don't sample world depth there.
15. Free our targets on FPV exit: `TextureTarget.destroyBuffers()` and `MappableRingBuffer.close()`. New textures are uninitialised, so clear `history` once.

## Verification

Verifier pass (2026-09-23): every claim was re-checked with grep/Read against `.ref`. About 125 individual claims were checked (class names, signatures, fields,
constants, JSON codecs, file paths, line references, loader hooks). 21 were corrected or made more precise, and 9 missing items were added.

**Confirmed as written:**
- The RenderPearl/Blaze3D package layout: `GpuDevice`, `CommandEncoder`, `RenderPass` (incl. `pushConstants`, which vanilla never calls), `RenderPipeline.Builder`
  (String overloads use `withDefaultNamespace`, `build()` checks), `BindGroupLayout`, `UniformType`, `ColorTargetState.DEFAULT`, the `GpuBuffer` usage bits and `map`,
  `GpuBufferSlice.MappedView(slice, data, onClose)`, `Std140Builder`/`Std140SizeCalculator`, `MappableRingBuffer`, `SamplerCache`, `TextureTarget`, `RenderSystem`
  (`getCompiledPipelineNullable`/`getCompiledPipeline`, `bindDefaultUniforms`, `DEFAULT_DEPTH_CLEAR_VALUE = 0.0`), and `PipelineCache` lazy compile.
- shaderc + `set_auto_bind_uniforms(true)`, SPIRV-Cross to GLSL 330, the `#include <ns:file.glsl>` resolution (`SHADER_INCLUDE_CONVERTER`), `ShaderType.idConverter()`,
  the `globals/projection/dynamictransforms.glsl` layouts, `screenquad.vsh` (`gl_VertexIndex`), and the `GameTime` day-fraction formula.
- `PostChainConfig`/`UniformValue` codecs, the `PostChain.createPass` bind layout and location, `PostPass` static UBO (usage 128) vs SamplerInfo ring (usage 130),
  `LevelTargetBundle.MAIN_TARGETS/OUTLINE_TARGETS`, persistent targets, `PostChain#process` (`@Deprecated`), `CrossFrameResourcePool`, `GraphicsResourceAllocator.UNPOOLED`.
- The `GameRenderer.update/extract/render/preparePostEffects/applyPostEffects` flow and line numbers, `hasPlayer` gating, and `consistentDepthRequired`. The
  NeoForge `render()` is identical at the hook. Also `Minecraft.runTick` order and NeoForge `fireRenderFramePre` at L1328.
- The `ServerPlayer` post-effect API, NBT key, `/posteffect` command, and client packet handler.
- The spectator effect switch, and the NeoForge `RegisterEntitySpectatorShadersEvent`/`ClientHooks.loadEntityShader`/public `setSpectatedEntityPostEffect`.
- NeoForge `RegisterRenderPipelinesEvent`, `ExtractLevelRenderStateEvent`, `RenderLevelStageEvent.AfterLevel` (before `render3dHud`), `SubmitCustomGeometryEvent`
  (before `finalizeGizmoCollection`). Fabric `LevelExtractionEvents.END_EXTRACTION`, `LevelRenderEvents.COLLECT_SUBMITS/BEFORE_GIZMOS/END_MAIN` and their mixin injection points.
- `OrderedSubmitNodeCollector.submitCustomGeometry/submitShapeOutline`, `SubmitNodeCollector.order`, `RenderTypes` line/debug types, `RenderType.create` visibility
  (vanilla package-private, NeoForge AT, Fabric TAW), the `VertexConsumer` pose overloads, the `DebugCrosshairRenderer` call site, `Gizmos`/`GizmoStyle`/`GizmoProperties`,
  and that `MultiBufferSource` is gone.
- Project facts: `Constants.MOD_ID`/`Constants.LOG`, mixin package `de.lardtom.propwash.mixin`, `VideoType.latencyMs` 12-30.

**Corrected:**
1. `PostPass` constructor lines: L41-67 → L42-72.
2. `MainTarget` has no `(String, GpuFormat, GpuFormat)` constructor. It is `new MainTarget(w, h)`, which calls super(...). Added the NeoForge stencil depth-format variant.
3. "Other snippets are private": several are public in vanilla (OIT_*, GLINT*, ANIMATE_SPRITE). Listed them, with the NeoForge AT source.
4. "Fabric has no precompile equivalent": wrong in practice. Fabric API widens `RenderPipelines.register/registerOptional` (classtweaker L433-435).
5. The compiler macros are conditional, not always defined.
6. `setUniform(name, view, sampler)`: view and sampler must be both non-null **or both null** (null unbinds).
7. Validation: the draw-time checks only run under `STRICT_VALIDATION` (dev only). Split the vague L105-160 reference into exact ranges.
8. Server post effects are not "synced on tick". They are sent when dirty, on join/respawn and on dimension change. Client-side override lifetime corrected.
9. The JSON example was presented as "a real file". It is adapted, and the final pass must output `minecraft:main`. `name` is not part of the codec.
10. The per-loader glue used `registerPipeline` (required), which aborts the reload on a shader error. Changed to `registerOptionalPipeline`, and added Fabric `registerOptional`.
11. The AT note implied that the common AT covers all loaders. It applies to common + NeoForge only. Fabric uses `propwash.accesswidener`.
12. Minecraft spectator lines: toggle via `toggleSpectatorPostEffect()` (L1908-1909), clear at L1829-1830.
13. RenderLevelStageEvent.AfterLevel line: L681 (event), L682 (`render3dHud`).
14. Pitfall 1: format equality is not validated in the frontend. Only the rectangle bounds are.
15. `CommandEncoder` signatures made exact. Noted the single shared encoder and the "no copy while a pass is open" rule.
16. `TextureTarget` has nullable args, no `close()` (use `destroyBuffers()`), and uninitialised contents. The sketch now clears `history` and has `close()`.
17. The sketch never reset `broken` after a resource reload. Added `onResourceReload()`.
18. `consistentDepthRequired` depends on *applied* chains (a missing `end_of_frame` does not count).
19. `getPostChain` failure: "recovery handler" is `Minecraft.triggerResourcePackRecovery`, which can disable packs or crash.
20. Header claim "everything verified" softened. Section 8 (Iris/Sodium) marked UNVERIFIED.
21. Section 7 now has full paths for all loader hooks (it had `NFA/...`/`FA/...` elisions).

**Added (missing critical APIs):** `TransientMemory` (with a caveat), `PipelineCache` identity keying, the required-pipeline reload failure, main depth content at our
hook (hand only), `FrontendRenderPass.STRICT_VALIDATION`, Fabric `RenderPipelines.registerOptional`, NeoForge `ConfigureMainRenderTargetEvent`, `debugPoint/debugTriangleFan`,
and GUI/OSD hooks (`RegisterGuiLayersEvent`/`GuiLayer`, `HudElementRegistry`/`HudElement`, context type `GuiGraphicsExtractor`).

**Open questions (UNVERIFIED):**
1. Does the Fabric client entrypoint run before the first `ShaderManager` reload? This decides whether `RenderPipelines.registerOptional` precompiles on the first
   load or only after the next F3+T. No Fabric loader/`Minecraft` init ordering source was checked.
2. Is a `TransientMemory.uploadGpu(..., USAGE_UNIFORM)` slice valid as a UBO until the GPU finishes the frame? That would be a simpler alternative to `MappableRingBuffer`.
3. How do the GL and Vulkan backends behave on `copyTextureToTexture` with mismatched formats, and on an RGBA8 post pass when a NeoForge stencil main depth is attached?
   (Our pass attaches no depth, so it should be unaffected.)
4. Iris/Sodium: are `mainRenderTarget` contents and our mixin target intact under shader packs? Do `SubmitCustomGeometryEvent`/`COLLECT_SUBMITS` fire? No sources in `.ref`.
5. `FrontendRenderPass.setPipeline` re-applies previously set uniforms. The exact behaviour of `backend.setUniform` for names absent from the pipeline
   (`uniformIndex == -1` → ignored in the frontend) was verified in the frontend only, not in each backend.

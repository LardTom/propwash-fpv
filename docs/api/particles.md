# Particles in 26.3: API notes for Propwash

All paths are relative to `.ref/` (`vanilla/`, `neoforge/` = patched MC). Every signature below was copied from those
sources. Used by the propwash dust (`drone.client.dust`, see `docs/design/propwash-dust.md`).

## Overview / decisions

- **No particle type, no provider.** Custom particles are plain `SingleQuadParticle` subclasses that are handed to the
  engine directly: `Minecraft#particleEngine.add(Particle)` (`vanilla/net/minecraft/client/particle/ParticleEngine.java`).
  That needs no `ParticleType` registration and no particle provider (the Foundation has no provider hook, §8), and it
  behaves the same on both loaders.
- **Engine-side hard cap:** override `Particle#getParticleLimit()` and return `Optional.of(new ParticleLimit(n))`
  (`vanilla/net/minecraft/core/particles/ParticleLimit.java`, public record). `ParticleEngine#add` drops the particle when
  the tracked count for that limit is full; `ParticleGroup#tickParticles` decrements the count when it dies and
  `clearParticles()` resets all counts (level change, resource reload). `ParticleLimit` is a record, so two limits with
  the same number share one counter: pick an unusual number (Propwash: 97).
- **Particle setting:** `Minecraft#options.particles().get()` → `net.minecraft.server.level.ParticleStatus`
  (`ALL`, `DECREASED`, `MINIMAL`). Vanilla skips non-essential particles at `MINIMAL` and a third of them at
  `DECREASED` (`ClientLevel#doAddParticle`, `calculateParticleLevel`); `ParticleEngine#add` itself ignores the setting,
  so direct adds must check it. Vanilla also skips particles more than 32 blocks from the camera
  (`camera.position().distanceToSqr(...) > 1024`).

## Classes

- `SingleQuadParticle(ClientLevel, double x, double y, double z, TextureAtlasSprite)` — protected in vanilla, public
  in NeoForge. Protected fields `quadSize`, `rCol/gCol/bCol`, `alpha`, `sprite`; `setColor(r, g, b)`, `setSprite(...)`.
  Abstract `protected SingleQuadParticle.Layer getLayer()`; `SingleQuadParticle.Layer.TRANSLUCENT` = particle atlas +
  `RenderPipelines.TRANSLUCENT_PARTICLE` (OIT set `OIT_PARTICLE`). `Layer.bySprite(sprite)` would pick `OPAQUE` for
  sprites without translucent pixels (the `generic_*` sprites), so low alpha needs `TRANSLUCENT` explicitly.
- `public void extract(QuadParticleRenderState, Camera, float partialTickTime)` runs per frame for particles whose
  position is in the frustum (`QuadParticleGroup#extractRenderState`); it reads `alpha` directly, so a per-frame fade can
  set `alpha` there before calling `super.extract`. `public float getQuadSize(float partialTick)` scales the quad.
- `Particle#tick()`: `age++ >= lifetime` → `remove()`, else `yd -= 0.04 * gravity`, `move(...)`, then `xd/yd/zd *= friction`
  (and `*= 0.7` horizontally on the ground). Negative `gravity` = slow lift. With `hasPhysics` the particle collides
  with blocks (`Entity.collideBoundingBox`) and **freezes completely** once a downward move is blocked
  (`stoppedByCollision`). `setSize(w, h)` sets the collision box.
- `Particle#isAlive()`, `getLifetime()`, `setLifetime(int)`; `age`, `lifetime`, `x/y/z`, `xd/yd/zd`, `level` are protected.

## Sprites without a provider

```java
TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
        .get(new SpriteId(TextureAtlas.LOCATION_PARTICLES, Identifier.withDefaultNamespace("generic_5")));
```
- `SpriteId(Identifier atlasLocation, Identifier texture)` (`vanilla/net/minecraft/client/resources/model/sprite/SpriteId.java`);
  `AtlasManager#get(SpriteId)` returns the atlas' missing sprite for unknown names. The particle atlas stitches every
  PNG in `textures/particle/` with an empty prefix (`assets/minecraft/atlases/particles.json`), so the id is
  `minecraft:<file name>`.
- Sprites are rebuilt on every resource reload: look them up when needed, do not cache `TextureAtlasSprite`s.
- `generic_0` … `generic_7` are white speckle clusters (1 px at `_0`, 8×8 cloud at `_7`), used by smoke/poof in the
  order 7 → 0; tinted with `setColor` they make fine dust.
- `TextureAtlasSprite#contents().name()` vs. `MissingTextureAtlasSprite.getLocation()` tells whether the lookup failed.

## Ground under a point (used for the dust)

- `BlockState#getCollisionShape(level, pos)`, `getShape(level, pos)` (outline), `VoxelShape#max(Direction.Axis.Y)`.
- `BlockState#getFluidState()`, `FluidState#is(FluidTags.WATER)`, `FluidState#getHeight(level, pos)` (source 8/9).
- `BlockState#is(TagKey<Block>)` comes from `TypedInstance` (`vanilla/net/minecraft/core/TypedInstance.java`).
  26.3 tags: `BlockTags.SAND` (sand, red sand, suspicious sand), `BlockTags.DIRT` (dirt, coarse dirt, rooted dirt only),
  `BlockTags.SNOW` (snow layer, snow block, powder snow), `BlockTags.CONCRETE_POWDERS`, `BlockTags.ICE`, `BlockTags.LEAVES`.
- `BlockState#getSoundType()` (`SoundType.SAND`, `GRAVEL`, `GRASS`, `SNOW`, `MOSS`, …) is a useful material hint for
  modded blocks. `BlockState#getMapColor(level, pos).col` is the block's RGB map colour (`MapColor.NONE.col == 0`).
- A single snow layer (`SnowLayerBlock.LAYERS == 1`) has an empty collision shape; its visible top is `layers / 8`.

# Audio in 26.3: API notes for Propwash

> **Projekt-Layout veraltet:** Die eine Mixin-Config `propwash.mixins.json` (stattdessen je Modul `propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`), Registrierungs-Klassen (`PropwashItems`, `PropwashEntities`, `PropwashComponents`, `RegList.Ref`, …), `CommonClass`, das Paket `net` (heute `network`) und Loader-Einstiege in dieser Notiz stammen aus der Zeit vor der Foundation. Kommentare in den Code-Beispielen sind nur Erklärung: im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`). Maßgeblich sind `docs/CONVENTIONS.md` und `docs/FOUNDATION.md` (Kasten „Abweichungen von docs/api“). Die Minecraft-/Loader-APIs hier stimmen weiterhin.

All paths are relative to `.ref/` (`vanilla/`, `neoforge/` = patched MC, `neoforge-api/`, `fabric-api/`).
Every signature below was copied from those sources. Package of our code: `de.lardtom.propwash`.

## Overview / decisions

- **Motor sound: synthesize it with a custom `AudioStream`.** Both loaders let a `SoundInstance` supply its own
  stream: NeoForge patches `SoundInstance#getStream(...)` directly into vanilla, and Fabric injects `FabricSoundInstance#getAudioStream(...)`.
  One class in `common` can override both (no `@Override`), so **no per-loader glue is needed**. The pitch is baked into the
  samples, so the 0.5–2.0 pitch clamp does not matter. Keep AL pitch at 1.0.
- **Latency is the main problem.** Vanilla sizes stream buffers at 1 s each and queues 4 of them. It refills them once per
  client tick (50 ms), so a stream that honours `expectedSize` lags 3–4 s behind. Our stream must **ignore `expectedSize`**
  and return small chunks (about 15 ms). We also pump the channel more often ourselves, which needs 2 `@Accessor`s. An underrun
  **kills the sound**: the AL source goes STOPPED, `ChannelAccess` releases it, and we must replay it.
- **Use one stream per drone**, with all 4 motors mixed into one mono stream. The streaming pool has at most **8** channels. Music, music discs and the
  streamed biome ambience loops (`ambient.underwater`, the nether biome loops) share it. This comes from vanilla `sounds.json` in asset index 17
  (`~/.gradle/caches/neoformruntime/assets`), which is not part of `.ref`.
- **Distance falloff (since 0.5.0):** the motor sounds no longer use `LINEAR` attenuation. They play with
  `Attenuation.NONE` (non-relative, so OpenAL still pans by direction), and Propwash applies its own inverse-distance
  curve and audible range inside the synthesizer (`audio.SoundReach`, `docs/design/drone-sound.md`). The `LINEAR`
  examples below are from the original research.
- **Beeps and countdown:** use small OGG files played with `SimpleSoundInstance.forUI(...)`, or with a positional `SimpleSoundInstance`.
- **Ship our own silent placeholder** `assets/propwash/sounds/silence.ogg`. `sounds.json` entries for synthesized sounds need an existing
  file and `"stream": true`. Otherwise the custom-stream hook is never reached.

## 1. Playback pipeline and threads

```
main thread:  SoundManager.play(inst) -> SoundEngine.play(inst)        (vanilla/net/minecraft/client/sounds/SoundEngine.java)
              Minecraft.tick() -> SoundManager.tick(pause) -> SoundEngine.tick -> tickInGameSound + channelAccess.scheduleTick()
              Minecraft.runTick() -> soundManager.updateSource(camera)  (per FRAME; listener pos/orientation)
sound thread: SoundEngineExecutor "Sound engine" (BlockableEventLoop): all OpenAL calls, Channel.*, AudioStream.read()
```

- `vanilla/net/minecraft/client/Minecraft.java:1834`: `this.soundManager.tick(this.pause);` runs once per client tick.
  `:1210` `this.soundManager.updateSource(this.gameRenderer.mainCamera());` runs once per frame. `:1232` pauses with
  `soundManager.pauseAllExcept(SoundSource.MUSIC, SoundSource.UI)`, and `gui/Gui.java:274` resumes with `getSoundManager().resume()`.
- `SoundEngine.tick(boolean paused)` always calls `channelAccess.scheduleTick()`, even while paused. That call schedules this code on the sound thread:
  `for each handle: handle.channel.updateStream(); if (handle.channel.stopped()) { handle.release(); it.remove(); }`
  (`vanilla/net/minecraft/client/sounds/ChannelAccess.java`).
- `ChannelAccess.ChannelHandle` (public inner class): `public void execute(Consumer<Channel> action)` runs on the sound
  thread and is a no-op once released. `public boolean isStopped()` and `public void release()` are also public. `BlockableEventLoop.execute` uses a
  `ConcurrentLinkedQueue` (`vanilla/net/minecraft/util/thread/BlockableEventLoop.java:32,92-104`), so it is **safe to call from any thread**.
- `SoundEngine.play` blocks the main thread on `channelAccess.createHandle(pool).join()`, which waits for the sound thread.
  A slow `AudioStream.read()` therefore also stalls the game.
- `SoundEngineExecutor` (`vanilla/net/minecraft/client/sounds/SoundEngineExecutor.java`) parks while idle and is unparked by every `schedule`.
  `shutDown()` drops pending tasks and joins the thread, and `startUp()` creates a **new** thread. `stopAll()` uses both.
- Per-frame caveat: `updateSource` (`Minecraft.java:1210`) runs **before** `renderFrame`, and `gameRenderer.update(deltaTracker)` (`:1294`,
  which calls `Camera.update`) runs after it. The listener transform therefore uses the camera from the previous frame, one frame behind.

## 2. SoundInstance API

`vanilla/net/minecraft/client/resources/sounds/SoundInstance.java` (interface):
```java
Identifier getIdentifier();  @Nullable WeighedSoundEvents getOrResolve(SoundManager soundManager);
@Nullable Sound getSound();  @Nullable WeighedSoundEvents getSoundEvent();  SoundSource getSource();
boolean isLooping(); boolean isRelative(); int getDelay(); float getVolume(); float getPitch();
double getX(); double getY(); double getZ(); SoundInstance.Attenuation getAttenuation();   // enum { NONE, LINEAR }
default boolean canStartSilent() { return false; }   default boolean canPlaySound() { return true; }
static RandomSource createUnseededRandom()
```
`TickableSoundInstance extends SoundInstance { boolean isStopped(); void tick(); }`

`AbstractSoundInstance` has these protected fields: `sound, soundEvent, source, identifier, volume=1, pitch=1, x, y, z, looping, delay,
attenuation=LINEAR, relative, random`. Its constructors are `(SoundEvent, SoundSource, RandomSource)` and `(Identifier, SoundSource, RandomSource)`.
`getVolume()` returns `volume * sound.getVolume().sample(random)`, and `getPitch()` works the same way. Both throw an NPE before the instance is resolved.

`AbstractTickableSoundInstance` (`.../AbstractTickableSoundInstance.java`) has **only** the constructor
`protected AbstractTickableSoundInstance(SoundEvent event, SoundSource source, RandomSource random)` and
`protected final void stop()`, which sets `stopped=true, looping=false`. For an unregistered id, pass
`SoundEvent.createVariableRangeEvent(id)`. The constructor only uses `event.location()`.

**Vanilla example:** `MinecartSoundInstance` sets `looping=true, delay=0, volume=0`, returns `canStartSilent() == true`, and in `tick()`
updates `x/y/z` and `volume`, or calls `stop()` when the entity is removed. It also declares its **own** `private float pitch`, which shadows
`AbstractSoundInstance.pitch`, so its pitch ramp never reaches `getPitch()`. That is a vanilla quirk; do not copy it. It is started by
`vanilla/net/minecraft/client/multiplayer/ClientPacketListener.java:610`
`this.minecraft.getSoundManager().play(new MinecartSoundInstance(minecart));`. Bees use `getSoundManager().queueTickingSound(...)` (`:620`),
which plays the sound on the next sound tick if `canPlaySound()`.

`SoundManager` has these public methods (`vanilla/net/minecraft/client/sounds/SoundManager.java`):
`SoundEngine.PlayResult play(SoundInstance)` returns one of `STARTED`, `STARTED_SILENTLY` or `NOT_STARTED`. The others are `void queueTickingSound(TickableSoundInstance)`,
`void playDelayed(SoundInstance, int delay)`, `void stop(SoundInstance)`, `void stop(@Nullable Identifier, @Nullable SoundSource)`,
`boolean isActive(SoundInstance)`, `void updateSource(Camera)`, `@Nullable WeighedSoundEvents getSoundEvent(Identifier)`,
`String getChannelDebugString()` ("Sounds: static used/max + streaming used/max"), `void stop()` (= `SoundEngine.stopAll()`),
`void pauseAllExcept(SoundSource...)`, `void resume()`, `void updateCategoryVolume(SoundSource, float gain)`. The field `private final SoundEngine soundEngine`
(`SoundManager.java:59`) needs an accessor.
Access it with `Minecraft.getInstance().getSoundManager()` (`Minecraft.java:2658`).

### Where clamps / attenuation are applied (`SoundEngine.java`)
```java
private float calculatePitch(SoundInstance instance) { return Mth.clamp(instance.getPitch(), 0.5F, 2.0F); }
private float calculateVolume(float volume, SoundSource source) {
    return Mth.clamp(volume, 0.0F, 1.0F) * Mth.clamp(this.options.getFinalSoundSourceVolume(source), 0.0F, 1.0F) * this.gainBySource.getFloat(source);
}
```
- In `play()` the pitch and volume are applied once, along with: `linearAttenuation(sound.getAttenuationDistance(instanceVolume))` if the attenuation is `LINEAR`, otherwise
  `disableAttenuation()`; `setLooping(isLooping && !isStreaming)`; `setSelfPosition`; `setRelative(instance.isRelative())`.
- In `tickInGameSound()`, **per tick for tickables only**, the engine calls `instance.tick()`, then `channel.setVolume(volume); setPitch(pitch); setSelfPosition(pos)`.
  The **attenuation distance and relative flag are never updated after `play()`**.
- `Sound.getAttenuationDistance(float volume)` returns `Math.max(volume, 1.0F) * attenuationDistance` (`resources/sounds/Sound.java`).
- `Channel.linearAttenuation(float maxDistance)` sets `AL_DISTANCE_MODEL=AL_LINEAR_DISTANCE, AL_MAX_DISTANCE=max, ROLLOFF=1, REFERENCE=0`.
  The gain falls linearly to 0 at `attenuation_distance` blocks. There is **no doppler**: `AL_VELOCITY` is never set
  (grep of `com/mojang/blaze3d/audio/`), so compute our own doppler if we want it.
- `play()` returns `NOT_STARTED` when the final volume is 0, unless `canStartSilent()` is true (the MUSIC source is also exempt). A muted motor that
  does not override `canStartSilent` never starts, and raising the slider later will not start it either.
- Lifetime: `soundDeleteTime = tickCount + 20` (a literal 20; the constant `MIN_SOURCE_LIFETIME = 20` exists). `isActive(inst)` is effectively
  `instanceToChannel.containsKey(inst)`. A stopped or dead channel is dropped from the map in `tickInGameSound` only once 20 or more ticks have passed since play,
  and never while paused. `stopAll()` clears everything **without** calling `stop()` on our instance. Detect this with `isActive()` and replay. `stopAll()` runs on:
  resource reload/F3+T (`SoundManager.apply` -> `SoundEngine.reload()` -> `destroy()` -> `stopAll()`), an audio device change (`tick` -> `reload()`),
  **every `Minecraft.setLevel`** (world join and each dimension-changing respawn; `Minecraft.java:2088-2091` -> `updateLevelInEngines` -> `soundManager.stop()`,
  called from `ClientPacketListener.java:522,1279`), and disconnect (`Minecraft.java:2176`, only when its `stopSound` argument is true) and
  `clearClientLevel` (`:2207`).
- `SoundEngine.stop(SoundInstance)` calls `handle.execute(Channel::stop)`.

## 3. Streaming internals (what our stream must satisfy)

`vanilla/net/minecraft/client/sounds/AudioStream.java`:
```java
public interface AudioStream extends Closeable { AudioFormat getFormat(); ByteBuffer read(final int expectedSize) throws IOException; }
```
Related types: `FiniteAudioStream extends AudioStream { ByteBuffer readAll() }`, and `FloatSampleSource extends FiniteAudioStream { boolean readChunk(FloatConsumer) }`.
The default `read()` of `FloatSampleSource` loops **until `expectedSize` bytes** (1 s) are produced and allocates a new `ChunkedSampleByteBuf` each call,
so **do not use `FloatSampleSource` for real-time audio**. Implement `AudioStream` directly.
Vanilla streams are built in `SoundBufferLibrary.getStream(Identifier location, boolean looping)`, which returns
`CompletableFuture<AudioStream>`, as a `JOrbisAudioStream`, or as a `LoopingAudioStream` when looping, on `Util.nonCriticalIoPool()`. JOrbis uses the format
`new AudioFormat(info.rate, 16, info.channels, true, false)` (`JOrbisAudioStream.java:66`).

`vanilla/com/mojang/blaze3d/audio/Channel.java` (key parts):
```java
private static final int QUEUED_BUFFER_COUNT = 4;  public static final int BUFFER_DURATION_SECONDS = 1;
private final int source;  private int streamingBufferSize = 16384;  private @Nullable AudioStream stream;
public void attachBufferStream(AudioStream stream) {           // called on sound thread, then channel.play()
    this.stream = stream; this.streamingBufferSize = calculateBufferSize(stream.getFormat(), 1); this.pumpBuffers(4); }
private void pumpBuffers(int size) { for i<size: ByteBuffer b = stream.read(streamingBufferSize);
    if (b != null) new SoundBuffer(b, stream.getFormat()).releaseAlBuffer().ifPresent(id -> AL10.alSourceQueueBuffers(source, new int[]{id})); }
public void updateStream() { if (stream != null) { int processed = removeProcessedBuffers(); pumpBuffers(processed); } }  // PUBLIC
public void destroy()  // stop, stream.close(), unqueue, alDeleteSources
public void setSelfPosition(Vec3) / setPitch(float) / setVolume(float) / setRelative(boolean) / setLooping(boolean) / linearAttenuation(float) / disableAttenuation()
public void play() / pause() / unpause() / stop() / attachStaticBuffer(SoundBuffer);  public boolean playing() / stopped()   // stopped() is also true after destroy()
```
What this means for us:
- **The buffer count is fixed at 4.** Each pump refills exactly as many buffers as were processed. If `read` returns `null`, no buffer is queued and the
  queue shrinks permanently. Only return `null` when the stream should end.
- **The buffer size is up to us.** `read(expectedSize)` asks for 1 s (for example 96000 bytes at 48 kHz mono 16-bit), but `Channel` queues whatever buffer comes back.
- **The format must be PCM 8- or 16-bit, mono or stereo** (`OpenAlUtil.audioFormatToOpenAl`, which maps to 0x1100–0x1103, anything else throws). OpenAL only
  spatializes **mono** buffers, so use `new AudioFormat(48000f, 16, 1, true, false)`.
- **The ByteBuffer must be direct, native-order, and flipped** (position 0, limit = bytes). It goes to `AL10.alBufferData` through `SoundBuffer.getAlBuffer()`
  (`vanilla/com/mojang/blaze3d/audio/SoundBuffer.java`). Use `org.lwjgl.BufferUtils.createByteBuffer(n)` + `putShort` + `flip()`, exactly as
  `ChunkedSampleByteBuf.accept/get` does (`vanilla/net/minecraft/client/sounds/ChunkedSampleByteBuf.java:19-50`). `OpenAlUtil` ignores the `AudioFormat`
  endian flag, so the samples must be in native order. **Reusing one buffer is safe:** `pumpBuffers` calls
  `releaseAlBuffer()` immediately, and `alBufferData` copies the data before the next `read`.
- **An underrun is fatal.** If all queued buffers finish before the next pump, the AL source becomes STOPPED. The next `scheduleTick` sees
  `stopped()` and releases the channel. Queuing more buffers does not restart it.
- `read()` runs on the sound thread, and so does the first `pumpBuffers(4)`, inside `attachBufferStream`. `close()` is called from `Channel.destroy()`.
  That happens on the sound thread after a normal release, but **on the main thread** during `stopAll()`. There the executor is shut down first, then
  `ChannelAccess.clear()` -> `ChannelHandle.release()` -> `Library.releaseChannel` -> `CountingChannelPool.release` -> `destroy()`
  (`ChannelAccess.java:62-65`, `Library.java:213-217,284-291`). `close()` therefore has to be thread-safe (a `volatile` flag is enough).
- Our `getStream`/`getAudioStream` override itself runs on the **main thread**, inside `SoundEngine.play`.

**Latency math:** the queue holds 4 × chunk of audio, and it is refilled every *pump interval*.
| setup | queued audio ≈ latency | underrun risk |
|---|---|---|
| vanilla, honour expectedSize | 3–4 s | none |
| ignore expectedSize, 25 ms chunks, vanilla per-tick pump | 50–100 ms | **high** at any tick gap over 75 ms (lag spike, chunk load) |
| 15 ms chunks + own pump every 5 ms (recommended) | 45–60 ms | low; the sound thread keeps running while the main thread stalls |

These figures are rough estimates of the audio queued ahead of a freshly written chunk. OpenAL Soft's own device period adds some latency on top.
Its size is not visible in these sources (UNVERIFIED: the minimum safe chunk size has to be measured in game).

## 4. Custom-stream hooks (exact signatures)

**NeoForge** patches `SoundInstance` itself. `ISoundInstanceExtension#getStream` **does not exist** in 26.3 (a grep of `neoforge-api` finds nothing).
`neoforge/net/minecraft/client/resources/sounds/SoundInstance.java:60`:
```java
default java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getStream(
        net.minecraft.client.sounds.SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
    return soundBuffers.getStream(sound.getPath(), looping); }
```
It is called only in the **streaming** branch of `neoforge/net/minecraft/client/sounds/SoundEngine.java:462`:
`soundinstance.getStream(this.soundBuffers, sound, isLooping).thenAccept(stream -> handle.execute(channel -> { channel.attachBufferStream(stream); channel.play(); NeoForge.EVENT_BUS.post(new PlayStreamingSourceEvent(this, soundinstance, channel)); }));`
NeoForge also posts these events (`neoforge-api/net/neoforged/neoforge/client/event/sound/*`):
- `PlaySoundEvent`, on `NeoForge.EVENT_BUS` on the main thread from `ClientHooks.playSound` (`neoforge-api/.../client/ClientHooks.java:361`).
  Its handlers can replace the instance or null it (`setSound(@Nullable SoundInstance)`). When replaced, `instanceToChannel` is keyed by the
  **replacement**, so our `instanceToChannel.get(inst)` returns null.
- `PlaySoundSourceEvent` for static sounds and `PlayStreamingSourceEvent` for streams, both on the **sound thread** inside `handle.execute`.
- `SoundEngineLoadEvent`, an `IModBusEvent` posted via `ModLoader.postEvent`. It fires in the `SoundEngine` constructor and at the end of every `reload()`
  (resource reload, device change), but **not** on the `stopAll()` from `setLevel` or disconnect.

`getChannel()` is declared on the nested class `net.neoforged.neoforge.client.event.sound.SoundEvent.SoundSourceEvent`. The NeoForge event class
`SoundEvent` **clashes in name** with `net.minecraft.sounds.SoundEvent`, so use fully qualified imports. NeoForge also makes `SoundEngine.soundManager` public.

**Fabric** uses `fabric-api/fabric-sound-api-v1/net/fabricmc/fabric/api/client/sound/v1/FabricSoundInstance.java`. It is injected into `SoundInstance` through
the class tweaker (`transitive-inject-interface net/minecraft/client/resources/sounds/SoundInstance ...FabricSoundInstance`):
```java
Identifier EMPTY_SOUND = Identifier.fromNamespaceAndPath("fabric-sound-api-v1", "empty");
default CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, Identifier id, boolean repeatInstantly) {
    return library.getStream(id, repeatInstantly); }
```
It is wired up by `fabric-api/fabric-sound-api-v1/net/fabricmc/fabric/mixin/client/sound/SoundEngineMixin.java`, which does a `@Redirect` of
`SoundBufferLibrary.getStream(Identifier, boolean)` inside `SoundEngine.play`. The class tweaker is `fabric-sound-api-v1.classtweaker`, referenced by the
`"accessWidener"` key of that module's `fabric.mod.json`; the module is `"environment": "client"`.
Here `id` is `sound.getPath()`, for example `propwash:sounds/silence.ogg`, not the event id. The javadoc's "equal to getSound()'s location" is inaccurate.
Fabric ships a placeholder `fabric-sound-api-v1:empty` (`assets/fabric-sound-api-v1/sounds/empty.ogg`). It exists **only on Fabric**, so for cross-loader
use we still need our own `silence.ogg`.

**Common approach.** The loader modules recompile the common sources (`build-logic/src/main/groovy/multiloader-loader.gradle:27` `source(configurations.commonJava)`).
Declare **both** methods in the common class **without `@Override`**. Each one overrides the default method on the loader that has it, and does nothing on the other loader.
The game is unobfuscated, so the JVM also matches the override at runtime by name and descriptor, even if a loader's compile classpath lacked the
injected interface. (UNVERIFIED: that Loom applies `transitive-inject-interface` to our Fabric compile classpath; a build will show it, and the runtime
override works either way.)
- **Never** mixin the `getStream` call in `SoundEngine.play` from common. On Fabric it conflicts with Fabric API's `@Redirect`. On NeoForge the call no longer exists, so the
  injection fails because of `"defaultRequire": 1`.
- The custom stream is only requested if the resolved `Sound` has `"stream": true` **and** its file exists.
  `SoundManager.validateSoundResource` drops entries whose `.ogg` file is missing (load-time warning "File {} does not exist, cannot add it to event {}").
  That leaves `WeighedSoundEvents.getSound` returning `SoundManager.EMPTY_SOUND` and the log line "Unable to play empty soundEvent".
  `minecraft:intentionally_empty` returns `NOT_STARTED`, so it cannot be used as the placeholder.

## 5. Recommended implementation (all in `common`, client-only package)

```java
// de/lardtom/propwash/client/audio/MotorSynthStream.java
public final class MotorSynthStream implements AudioStream {
    public static final int RATE = 48_000;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false); // mono -> positional
    private final DroneAudioState state;            // written by main thread (volatile snapshot)
    private final int chunk;                        // samples per read, e.g. 720 = 15 ms
    private final ByteBuffer buf;                   // direct + native order, reused (see §3)
    private final double[] phase = new double[4];
    private final float[] hz = new float[4], gain = new float[4];
    private volatile boolean closed;

    public MotorSynthStream(DroneAudioState state, int chunk) {
        this.state = state; this.chunk = chunk; this.buf = BufferUtils.createByteBuffer(chunk * 2);
    }
    @Override public AudioFormat getFormat() { return FORMAT; }
    @Override public ByteBuffer read(int expectedSize) {        // expectedSize == 1 s worth -> IGNORE
        MotorSnapshot s = state.snapshot();                     // immutable record: float[4] bladeHz, float[4] gain, doppler
        final float k = 0.0039f;                                // one-pole smoothing (~30 Hz) against zipper noise
        buf.clear();
        for (int i = 0; i < chunk; i++) {
            float out = 0f;
            for (int m = 0; m < 4; m++) {
                hz[m] += k * (s.bladeHz()[m] * s.doppler() - hz[m]);
                gain[m] += k * (s.gain()[m] - gain[m]);
                phase[m] += hz[m] / RATE; phase[m] -= Math.floor(phase[m]);
                double p = phase[m] * (2 * Math.PI);
                out += gain[m] * (float) (Math.sin(p) + 0.45 * Math.sin(2 * p) + 0.2 * Math.sin(3 * p)); // + noise, pole-pair whine...
            }
            out = (float) Math.tanh(out * 0.35f);               // soft clip the 4-motor mix
            buf.putShort((short) Mth.clamp((int) (out * 32767f), -32768, 32767));
        }
        buf.flip();
        return buf;                                             // never null while alive (null shrinks the queue)
    }
    @Override public void close() { closed = true; }
    public boolean isClosed() { return closed; }
}
```
Blade-pass frequency is `rpm / 60 * PropType.blades`, and rotation is `rpm / 60`. `MotorType.kv` × pack voltage gives the maximum RPM. Anything below Nyquist (24 kHz) is fine.
If `Math.sin` shows up in a profile, switch to a wavetable. Budget: the loop must take well under 1 ms per chunk.

```java
// de/lardtom/propwash/client/audio/MotorSoundInstance.java
public final class MotorSoundInstance extends AbstractTickableSoundInstance {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "motor");
    private final DroneAudioState state;
    private @Nullable MotorSynthStream stream;

    public MotorSoundInstance(DroneAudioState state, boolean pilotView) {
        super(SoundEvent.createVariableRangeEvent(ID), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.state = state;
        this.looping = true; this.delay = 0; this.pitch = 1f; this.volume = 1f;   // pitch stays 1.0: baked into samples
        this.relative = pilotView;                                                 // own FPV drone: head-locked at 0,0,0
        this.attenuation = pilotView ? Attenuation.NONE : Attenuation.LINEAR;      // cannot change after play()
    }
    @Override public boolean canStartSilent() { return true; }
    @Override public boolean canPlaySound() { return !state.removed(); }
    @Override public void tick() {
        if (state.removed() || state.stopRequested()) { stop(); return; }
        if (!relative) { Vec3 p = state.lastFramePos(); x = p.x; y = p.y; z = p.z; }  // see per-frame hook
        volume = state.masterGain();                                               // clamped 0..1 by engine
    }
    private CompletableFuture<AudioStream> open() {
        stream = new MotorSynthStream(state, 720);
        return CompletableFuture.completedFuture(stream);
    }
    // NeoForge: overrides patched SoundInstance#getStream. Fabric: overrides FabricSoundInstance#getAudioStream. NO @Override.
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, Sound sound, boolean looping) { return open(); }
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary lib, Identifier id, boolean repeatInstantly) { return open(); }
    public boolean streamDead() { return stream != null && stream.isClosed(); }
}
```

**Accessors** go in common mixins, added to the `"client"` list of `common/src/main/resources/propwash.mixins.json`. That config's package is
`de.lardtom.propwash.mixin`, so the accessors live there, and **no** non-mixin class (such as `DroneAudio`) may be put in that package:
```java
@Mixin(SoundManager.class) public interface SoundManagerAccessor {
    @Accessor("soundEngine") SoundEngine propwash$soundEngine(); }
@Mixin(SoundEngine.class) public interface SoundEngineAccessor {
    @Accessor("instanceToChannel") Map<SoundInstance, ChannelAccess.ChannelHandle> propwash$instanceToChannel(); }
// Optional:
@Mixin(Channel.class) public interface ChannelAccessor {
    @Accessor("source") int propwash$source();                 // AL source id -> query AL_BUFFERS_QUEUED / AL_SAMPLE_OFFSET for adaptive chunking
    @Invoker("pumpBuffers") void propwash$pump(int count); }   // queue MORE than 4 buffers (e.g. 8 x 8 ms) if needed
```
No `@Invoker` is required for the basic design, because `Channel.updateStream()` and `ChannelHandle.execute()` are public.
`SoundEngine.calculatePitch(SoundInstance)` is a private **instance** method, not a static one. Only mixin it (for example with MixinExtras `@ModifyReturnValue`;
`common/build.gradle` has `mixinextras-common:0.5.3` compileOnly) if we ever pitch-shift OGG
samples beyond 2.0. A sample-based motor would need several crossfaded samples anyway, since RPM covers more than a 20:1 range.

**Pump and manager** (main thread, except for the pump thread):
```java
public final class DroneAudio {
    private static final Set<ChannelAccess.ChannelHandle> PUMPED = ConcurrentHashMap.newKeySet();
    static {  // daemon pump: keeps streams fed even when the main thread stalls
        Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "Propwash audio pump"); t.setDaemon(true); return t; })
            .scheduleAtFixedRate(() -> PUMPED.removeIf(h -> {
                if (h.isStopped()) return true;
                h.execute(Channel::updateStream);            // runs on sound thread; serial with vanilla's scheduleTick
                return false; }), 5, 5, TimeUnit.MILLISECONDS);
    }
    static void start(MotorSoundInstance inst) {          // call from client tick
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        if (sm.play(inst) == SoundEngine.PlayResult.NOT_STARTED) { /* pool full / no device: retry with backoff */ return; }
        SoundEngine eng = ((SoundManagerAccessor) sm).propwash$soundEngine();
        ChannelAccess.ChannelHandle h = ((SoundEngineAccessor) eng).propwash$instanceToChannel().get(inst); // put() is synchronous in play()
        if (h != null) PUMPED.add(h);
    }
    // per client tick: for each wanted drone -> if (!sm.isActive(inst) || inst.streamDead()) start(new MotorSoundInstance(...))
    // never replay the same instance object; limit to ~3-4 motor streams (own + nearest), the rest silent
    // do not start new instances while Minecraft.getInstance().isPaused() (Minecraft.java:2650): pauseAllExcept only pauses channels that exist at pause time
}
```
Only read `instanceToChannel` on the main thread, because it is a plain `HashMap`. `ChannelHandle.execute` can be called from any thread.
`ChannelHandle.stopped` is a plain, non-volatile field, so the pump thread may see it late. That is harmless, because `execute` is a no-op after
release. Also `PUMPED.remove(h)` from the main thread when a drone's instance is replaced. **Never call `ChannelHandle.release()` yourself**: a second release
throws `IllegalStateException("Tried to release unknown channel")` (`Library.java:213-217`).
When replacing a dead instance, also make the old one `stop()` itself (set a flag it reads in `tick()`), or vanilla keeps ticking it until it is pruned.

**Optional underrun recovery (idea, untested):** vanilla prunes a STOPPED channel only in `scheduleTick` (once per 50 ms), while our pump runs every 5 ms on
the same serial sound thread. The pump could therefore restart it with `h.execute(ch -> { ch.updateStream(); if (ch.stopped() && wantPlaying) ch.play(); })`
(`Channel.play()`/`stopped()` are public). `wantPlaying` must be a `volatile` flag that we clear before any intentional `SoundManager.stop(inst)`, because
`SoundEngine.stop` also ends in `Channel::stop`.

**Tick glue** (the only per-loader part; alternatively a common `@Inject(method = "tick", at = @At("TAIL"))` on `Minecraft`, like the existing
`common/.../mixin/MixinMinecraft.java`):
- Fabric: `ClientTickEvents.END_CLIENT_TICK.register(client -> DroneAudio.tick(client));`
  (`fabric-api/fabric-lifecycle-events-v1/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java:41,73-74`, `void onEndTick(Minecraft client)`).
  Register it from a client entrypoint (`ClientModInitializer` + `"client"` entrypoint in `fabric.mod.json`; UNVERIFIED here, since Fabric Loader is not in `.ref`).
- NeoForge: `NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> DroneAudio.tick(Minecraft.getInstance()));`
  (`neoforge-api/net/neoforged/neoforge/client/event/ClientTickEvent.java:35`, posted at `neoforge-api/.../client/ClientHooks.java:888`). Register it only on
  the physical client (UNVERIFIED here: the `@Mod(dist = Dist.CLIENT)` / `FMLEnvironment` API lives in FML, which is not in `.ref`).

**Per-frame position for spectated drones.** `setSelfPosition` normally runs only per tick, and at 30 m/s a drone moves 1.5 blocks per tick, while the listener
moves every frame. Hook `SoundManager.updateSource(Camera)` (`public void updateSource(Camera camera)`, called every frame from `Minecraft.java:1210`):
```java
@Mixin(SoundManager.class) public class MixinSoundManager {
    @Inject(method = "updateSource", at = @At("TAIL"))
    private void propwash$frame(Camera camera, CallbackInfo ci) { DroneAudio.onFrame(camera); } }
// onFrame: pos = lerp(prev, cur, partialTick); state.lastFramePos = pos; handle.execute(ch -> ch.setSelfPosition(pos));
// partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)
//   (Minecraft.java:2743 `public DeltaTracker getDeltaTracker()`, client/DeltaTracker.java:14 `float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame)`)
```
`tick()` copies `lastFramePos` into `x/y/z`, so vanilla's per-tick `setSelfPosition` does not snap the source back. Put doppler into the
snapshot too (`c = 343 m/s`, where 1 block = 1 m) if we want it.

**Thread safety of the flight model to the synth:** publish an immutable `MotorSnapshot` through a `volatile` field (or an `AtomicReference`), written per tick or per frame.
Never share mutable arrays with the sound thread.

## 6. Effect sounds (beeps, countdown)

- Use static OGG files, with no `stream` flag and optionally `"preload": true`. Play with `SimpleSoundInstance.forUI(SoundEvent sound, float pitch, float volume)`
  (`resources/sounds/SimpleSoundInstance.java`). This gives source `UI`, relative placement, `Attenuation.NONE`, and keeps playing while paused. `forUI(sound, pitch)` uses a default volume of 0.25.
  For in-world sounds such as gate or lap beeps, use `new SimpleSoundInstance(SoundEvent, SoundSource, float volume, float pitch, RandomSource, double x, double y, double z)`
  or `ClientLevel.playLocalSound(double x, double y, double z, SoundEvent, SoundSource, float volume, float pitch, boolean distanceDelay)` (`client/multiplayer/ClientLevel.java:707`).
  With `distanceDelay`, the sound is delayed by `dist/40` s when farther than 10 blocks.
- Pitch variants must stay within 0.5–2.0. For example, a 880 Hz file at pitch 1.0 for "3-2-1" and 2.0 for "GO", or separate files.
- **Positional sounds need mono OGGs** (OpenAL does not spatialize multi-channel buffers; that is OpenAL behaviour, not visible in `.ref`). The local `ffmpeg`
  (`/opt/homebrew/bin/ffmpeg` -> ffmpeg 9.0.2) only has the native `vorbis` encoder, which gives the error
  "Current FFmpeg Vorbis encoder only supports 2 channels". That is fine for UI beeps and for `silence.ogg`:
  `ffmpeg -f lavfi -i "sine=frequency=880:duration=0.15" -ac 2 -c:a vorbis -strict -2 beep.ogg` and
  `ffmpeg -f lavfi -i anullsrc=r=44100:cl=stereo -t 0.1 -c:a vorbis -strict -2 silence.ogg` (both tested).
  Mono positional OGGs need `libvorbis` (for example `brew install vorbis-tools` for `oggenc`), or they can be synthesized with a short finite custom stream.
  A synthesized sound takes one of the 8 streaming channels while it plays.
- For race logic, the server decides *when* the countdown happens and syncs it by packet with a tick timestamp. The client plays the sounds locally. Server-side
  `level.playSound(...)` also works for registered events.

## 7. SoundEvent registration and sounds.json

`vanilla/net/minecraft/sounds/SoundEvent.java`: `public record SoundEvent(Identifier location, Optional<Float> fixedRange)`, with
`static SoundEvent createVariableRangeEvent(Identifier)`, `static SoundEvent createFixedRangeEvent(Identifier, float range)`, and
`float getRange(float volume)`, which returns fixedRange or `volume > 1 ? 16*volume : 16`. That range only affects **which players the server sends the packet to**.
Client attenuation comes from `sounds.json` `attenuation_distance`.
Vanilla registers with `Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(soundId))` (`sounds/SoundEvents.java:1949-1950`).
`SoundEngine.reload()` warns "Missing sound for event: {}" for every **registered** event that has no `sounds.json` entry.

- **Client-only sounds**, such as the motor or locally played beeps, need **no registry entry**. `SoundManager` builds its map from `sounds.json` alone.
  Register only the events the server sends or references (`Holder<SoundEvent>` packets). Direct holders also encode, via `ByteBufCodecs.holder`, as id 0 followed by the direct value.
- Common: `public static final SoundEvent BEEP = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "beep"));`
- Fabric (`ModInitializer.onInitialize`): `Registry.register(BuiltInRegistries.SOUND_EVENT, PropwashSounds.BEEP.location(), PropwashSounds.BEEP);`
  (`static <V, T extends V> T register(Registry<V>, Identifier, T)`, `core/Registry.java:111`).
- NeoForge (`neoforge-api/net/neoforged/neoforge/registries/DeferredRegister.java:116` `create(ResourceKey<? extends Registry<T>>, String)`,
  `:214` `register(String, Supplier<? extends I>)` (the overload used below; `:225` is the `Function<Identifier, ? extends I>` overload), `:315` `register(IEventBus)`):
  ```java
  static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Constants.MOD_ID);
  static final DeferredHolder<SoundEvent, SoundEvent> BEEP = SOUNDS.register("beep", () -> PropwashSounds.BEEP);
  // in Propwash(IEventBus eventBus): SOUNDS.register(eventBus);
  ```
- Datagen exists (`neoforge-api/.../common/data/SoundDefinitionsProvider.java`, `fabric-api/fabric-data-generation-api-v1/.../FabricSoundsProvider.java`),
  but a hand-written `common/src/main/resources/assets/propwash/sounds.json` is simpler for a handful of entries.

**sounds.json fields** (`resources/sounds/SoundEventRegistrationSerializer.java`):
| level | key | type / default |
|---|---|---|
| event | `replace` | bool, false |
| event | `subtitle` | string translation key, null |
| event | `sounds` | array of strings (shorthand: all defaults, attenuation 16, no stream) or objects |
| sound | `name` | Identifier, required; the file is `assets/<ns>/sounds/<path>.ogg` (`Sound.SOUND_LISTER = new FileToIdConverter("sounds", ".ogg")`) |
| sound | `type` | `"file"` (default) or `"event"` (reference another event) |
| sound | `volume` / `pitch` | float > 0, default 1.0 |
| sound | `weight` | int > 0, default 1 |
| sound | `preload` | bool, false (loads the buffer at engine start) |
| sound | `stream` | bool, false (**true is required for custom AudioStreams**; uses the STREAMING pool) |
| sound | `attenuation_distance` | int, default 16 (LINEAR falloff to 0 at this distance × max(volume, 1)) |

```json
{
  "motor": { "subtitle": "subtitles.propwash.motor",
             "sounds": [ { "name": "propwash:silence", "stream": true, "attenuation_distance": 96 } ] },
  "beep":  { "subtitle": "subtitles.propwash.beep", "sounds": [ { "name": "propwash:beep", "preload": true } ] },
  "go":    { "subtitle": "subtitles.propwash.go",   "sounds": [ { "name": "propwash:beep_go", "preload": true } ] }
}
```
`SoundManager.apply` logs (ERROR) "Missing subtitle {} for sound event: {}" for **registered** events whose subtitle key has no translation, but only
if `SharedConstants.IS_RUNNING_IN_IDE`. NeoForge sets that flag to `!FMLEnvironment.isProduction()` (`neoforge/net/minecraft/SharedConstants.java:129`),
so it is on in NeoForge dev runs. Vanilla/Fabric leave it `false` (no assignment in `vanilla` or `fabric-api`). Add the lang keys anyway.
Looping tickables with volume > 0 notify the subtitle listeners every 10 ticks (the code uses a literal `10`; `LOOPING_SOUND_SUBTITLE_INTERVAL_TICKS = 10` exists).
`SubtitleOverlay.onPlaySound` refreshes an existing line with the same text instead of adding a new one (`gui/components/SubtitleOverlay.java:112-118`),
so a motor subtitle stays **permanently visible** while the motor runs. An event without `subtitle` (null) is ignored. Consider leaving it off the motor.

## 8. SoundSource categories and options keys

`vanilla/net/minecraft/sounds/SoundSource.java` (enum; `neoforge/.../SoundSource.java` is byte-identical, so NeoForge does not make it extensible. No fabric-api class tweaker uses an enum-extension directive, only `accessible/extendable/mutable/inject-interface`.
UNVERIFIED whether Fabric Loader itself offers one):
`MASTER("master"), MUSIC("music"), RECORDS("record"), WEATHER("weather"), BLOCKS("block"), HOSTILE("hostile"),
NEUTRAL("neutral"), PLAYERS("player"), AMBIENT("ambient"), VOICE("voice"), UI("ui")`.
Suggestion: use `PLAYERS` for motors and `UI` for HUD beeps. A mod-own volume slider has to multiply the instance `volume`.

`client/Options.java`: `getFinalSoundSourceVolume(SoundSource)` returns the category volume × MASTER (MASTER alone for MASTER).
`getSoundSourceOptionInstance(SoundSource)` returns an `OptionInstance<Double>`. options.txt keys are `"soundCategory_" + source.getName()` (`:1681`), for example
`soundCategory_master`, `soundCategory_player`, `soundCategory_neutral`, `soundCategory_block`, `soundCategory_record`, `soundCategory_ui`, `soundCategory_voice`.
The values are 0.0–1.0. Other keys: `soundDevice` (`""` means system default) and `directionalAudio` (HRTF).
**Muting test instances:** put `soundCategory_master:0.0` in `fabric/runs/client/options.txt` and `neoforge/runs/client/options.txt`
(run dirs from `fabric/build.gradle:21` `runDir('runs/client')` and `neoforge/build.gradle:20`; `fabric/runs` is created on first run).
The engine still runs, and our motor still starts because `canStartSilent()` is true. Without a device, `SoundEngine.loaded` is false and `play()` returns `NOT_STARTED`.

## 9. Pitfalls checklist

1. Honouring `expectedSize` (1 s), or using the default `FloatSampleSource.read`, gives 3–4 s latency.
2. Returning `null`, or not producing a buffer fast enough, shrinks or empties the queue. Once empty, the source is STOPPED and the channel is released. Detect this and replay with a **new** instance.
3. Heap `ByteBuffer`s passed to `alBufferData` cause undefined behaviour (UNVERIFIED in `.ref`, since LWJGL reads the native address). Use `BufferUtils.createByteBuffer`
   (direct, native order), as vanilla does.
4. A stereo stream is **not spatialized** (OpenAL behaviour). The motor stream must be mono. `OpenAlUtil.audioFormatToOpenAl` accepts only PCM_SIGNED/UNSIGNED 8/16-bit, 1/2 ch.
5. The streaming pool holds `clamp((int) sqrt(n), 2, 8)` channels, where `n` is the ALC attribute `4112` (= `ALC_MONO_SOURCES`), with a fallback of 30.
   That is usually 8 (`Library.java:88-92,122-150`). Music, discs and the streamed biome ambience share it. When it is full, `acquire()` returns null and
   `play()` returns `NOT_STARTED`, logging "Maximum sound pool size" only in IDE. Mix 4 motors into 1 stream and cap the number of drones.
   `Library.getChannelDebugString()` gives "Sounds: a/b + c/d". The F3 screen shows it only when the `sound_mood` debug entry is enabled
   (`gui/components/debug/DebugEntrySoundMood.java`, `DebugScreenEntries.java:39`). It is **not** in the default profile (`:79-98`).
6. `attenuation` and `relative` are only applied in `play()`. To switch between FPV and third person, stop and replay the instance, or call
   `handle.execute(ch -> { ch.setRelative(..); ch.linearAttenuation(d) /* or ch.disableAttenuation() */; })`.
7. Everything that touches a `Channel` must run on the sound thread through `ChannelHandle.execute`. The NeoForge `PlayStreamingSourceEvent` handlers already run on the sound thread.
8. `AudioStream.read` must be fast. `SoundEngine.play` on the main thread joins the sound thread.
9. Pause (singleplayer ESC, `Minecraft.java:1229-1233`) pauses **every** source except MUSIC and UI (`pauseAllExcept(MUSIC, UI)`), and only channels that
   exist at that moment. `tick()` is not called while paused (`tickMusicWhenPaused`). `Gui.java:274` `resume()` unpauses every channel. On resume, about
   60 ms of stale queued audio plays first.
10. F3+T/resource reload, a device switch, **any `Minecraft.setLevel` (join or dimension change)** or a disconnect triggers `stopAll()`. The executor restarts,
    all channels are destroyed, and `stream.close()` is called on the **main thread**. Instances are **not** told. Poll `isActive()`.
11. Keep all audio classes in a client-only package, and never load them from common server paths. The classes are `@OnlyIn(Dist.CLIENT)` in the NeoForge-patched sources.
12. Vanilla casts entity positions to float for sound positions, as `EntityBoundSoundInstance` does, and AL uses floats. Precision is fine near spawn and coarse at ±1e6.

## Verification

Checked by a second pass against `.ref` (vanilla, neoforge, neoforge-api, fabric-api) and the project build files. About 115 individual claims were checked.

**Confirmed as written** (selection): the full `SoundInstance`/`TickableSoundInstance`/`AbstractSoundInstance`/`AbstractTickableSoundInstance` API and fields;
`SoundEngine.play/tick/tickInGameSound/isActive/stop/stopAll/calculateVolume/PlayResult`; the `ChannelAccess` + `ChannelHandle` API and `scheduleTick` body;
`BlockableEventLoop.java:32,92-104`; the `Channel` fields, constants, `attachBufferStream/pumpBuffers/updateStream/destroy` and AL enums
(4131/4129/4128 = MAX_DISTANCE/ROLLOFF/REFERENCE, 53251 = LINEAR_DISTANCE, 514 = SOURCE_RELATIVE; no AL_VELOCITY 4102 anywhere);
`SoundBuffer.releaseAlBuffer`; `OpenAlUtil.audioFormatToOpenAl` 0x1100-0x1103; `AudioStream/FiniteAudioStream/FloatSampleSource` (default `read` loop);
`SoundBufferLibrary.getStream` and `JOrbisAudioStream.java:66`; `Library.java:89` pool math; `Sound.getAttenuationDistance/getPath/SOUND_LISTER`;
every `SoundEventRegistrationSerializer` key/default; `SoundManager` public methods, `validateSoundResource`, `INTENTIONALLY_EMPTY_SOUND` -> NOT_STARTED;
the NeoForge patch (`SoundInstance.java:60` `getStream`, `SoundEngine.java:462`, `ClientHooks.playSound`, public `soundManager`, `SoundEngineLoadEvent` mod bus);
no `ISoundInstanceExtension` in neoforge-api; `FabricSoundInstance` (EMPTY_SOUND, `getAudioStream`), the class tweaker line, the `SoundEngineMixin` `@Redirect`;
`multiloader-loader.gradle:27`; `"defaultRequire": 1` in `propwash.mixins.json`; `SimpleSoundInstance.forUI` (both overloads, 0.25 default);
`ClientLevel.java:707` and the distance-delay math; `SoundEvent` record/factories/`getRange`; `ByteBufCodecs.holder` direct id 0; `Registry.java:111`;
the `SoundSource` enum; the `Options` sound keys (`:1681`, `soundDevice`, `directionalAudio`, `isSoundDeviceDefault("")`); `Minecraft.java:1210,1232,1834,2658`;
`Gui.java:274`; `ClientPacketListener.java:610,620`; `Mth.clamp(int,int,int)`; `Constants.MOD_ID`, `PropType.blades`, `MotorType.kv`; the ffmpeg claims
(re-run: mono native vorbis fails with "Current FFmpeg Vorbis encoder only supports 2 channels"; the stereo silence command works).

**Corrected:**
1. `SoundEngine.calculatePitch` is a private *instance* method, not static.
2. `MinecartSoundInstance` shadows `pitch` with its own private field, so its pitch update has no effect.
3. Pause pauses **all** sources except MUSIC/UI, not only PLAYERS/NEUTRAL.
4. `stopAll()` is also triggered by every `Minecraft.setLevel` (join/dimension change). Disconnect only stops sounds when `stopSound` is true.
5. `stream.close()` can run on the **main thread** (stopAll -> `ChannelAccess.clear`), not only on the sound thread.
6. The streaming pool is also shared with streamed biome ambience (checked in asset-index `sounds.json`).
7. The F3 channel line needs the non-default `sound_mood` debug entry.
8. "Missing subtitle" is logged only when `SharedConstants.IS_RUNNING_IN_IDE` (true in NeoForge dev, false in Fabric dev).
9. A looping subtitle is refreshed, not repeated, so it stays permanently visible.
10. `DeferredRegister` Supplier overload is at `:214` (`:225` is the Function overload). `SoundEvents.java:1949-1950`.
11. Latency-table row 2 is 50-100 ms, and the table is marked as an estimate.
12. The Fabric `@param id` javadoc wording is inaccurate (it is `sound.getPath()`), and `fabric-sound-api-v1:empty` exists only on Fabric.
13. Pitfall 6 must also re-apply attenuation, not only `setRelative`.

**Added:** `Channel` play/pause/unpause/setLooping; the executor shutdown/startup semantics; the one-frame listener lag; the `PlaySoundEvent` replacement
effect on `instanceToChannel`; the NeoForge `SoundEvent` name clash; the `ChannelHandle.release()` double-release pitfall; the non-volatile `stopped`;
the underrun-recovery idea; per-loader tick glue (Fabric `ClientTickEvents.END_CLIENT_TICK`, NeoForge `ClientTickEvent.Post`); `DeltaTracker` partial
tick; not starting while paused; the mixin-package rule; the pool-full behaviour; "Missing sound for event" for registered events.

**UNVERIFIED / open questions:**
1. Whether the 15 ms chunk + 5 ms pump survives OpenAL Soft's real mixing period on macOS/Windows without underruns, and whether the pump-side
   `if (stopped) play()` recovery beats vanilla's `scheduleTick` release reliably. This needs an in-game test.
2. Whether Loom's `transitive-inject-interface` lets `getAudioStream` compile as an override inside the Fabric module (the runtime override works anyway,
   since names are unobfuscated). Also: Fabric `ClientModInitializer` and NeoForge `@Mod(dist = ...)`/`FMLEnvironment` are not in `.ref`.
3. Streaming-pool pressure: with at most 8 streaming channels shared with music, discs and biome ambience (underwater, nether), should several drones be
   mixed into **one** shared stream instead of one stream per drone? LWJGL heap-buffer behaviour and OpenAL stereo non-spatialization are also taken
   from outside `.ref`.

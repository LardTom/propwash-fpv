# Input: RC transmitter, gamepads, keyboard and mouse (MC 26.3)

> **Projekt-Layout veraltet:** Die eine Mixin-Config `propwash.mixins.json` (stattdessen je Modul `propwash.<modul>.mixins.json` mit Paket `de.lardtom.propwash.mixin.<modul>`), Registrierungs-Klassen (`PropwashItems`, `PropwashEntities`, `PropwashComponents`, `RegList.Ref`, …), `CommonClass`, das Paket `net` (heute `network`) und Loader-Einstiege in dieser Notiz stammen aus der Zeit vor der Foundation. Kommentare in den Code-Beispielen sind nur Erklärung: im Mod-Code sind Kommentare verboten (`docs/CONVENTIONS.md`). Maßgeblich sind `docs/CONVENTIONS.md` und `docs/FOUNDATION.md` (Kasten „Abweichungen von docs/api“). Die Minecraft-/Loader-APIs hier stimmen weiterhin.

All paths are relative to `.ref/` unless marked `[lwjgl-sdl]`. Those point to
`~/.gradle/caches/modules-2/files-2.1/org.lwjgl/lwjgl-sdl/3.4.3/c02146735d6ff70d070c73532b435e5c893a346b/lwjgl-sdl-3.4.3.jar`,
checked with `javap`. There is no sources jar, so the signatures below are copied from javap output.

## TL;DR

- **GLFW is gone. 26.3 runs on SDL3 through LWJGL 3.4.3 (`org.lwjgl:lwjgl-sdl:3.4.3`).** The 26.3 library list
  (`~/.gradle/caches/neoformruntime/artifacts/minecraft_26.3_version_manifest.json`) has no `lwjgl-glfw`.
  No file in vanilla imports `org.lwjgl.glfw`. `glfwGetJoystickAxes`, `glfwGetGamepadState` and similar
  calls do not exist here. Do not add lwjgl-glfw yourself: GLFW would need its own `glfwInit` next to SDL's
  Cocoa/Win32 setup.
- **Vanilla has no controller support.** Vanilla, the NeoForge patches, neoforge-api and fabric-api never
  reference `SDLJoystick` or `SDLGamepad`.
- **Minecraft initialises only `SDL_INIT_VIDEO` (32).** It does not initialise `SDL_INIT_JOYSTICK` (512) or
  `SDL_INIT_GAMEPAD` (8192). We must call `SDL_InitSubSystem` ourselves, once, on the render thread, after
  SDL_Init has run.
- **Minecraft pumps SDL once per frame and swallows every event it does not know.** That covers joystick
  and gamepad events. Some loading loops also flush them. So **poll the device state**
  (`SDL_GetJoystickAxis` and friends) once per frame, and find hotplugs by diffing `SDL_GetJoysticks()`.
  Never call `SDL_PollEvent` yourself, because that would steal Minecraft's keyboard and mouse events.
- **Keyboard key values are now SDL scancodes, and mouse buttons are 1-based SDL buttons (LEFT=1, MIDDLE=2,
  RIGHT=3).** GLFW numbers are wrong everywhere.

## 1. Window and event layer (vanilla)

| Class | Path | Role |
|---|---|---|
| `RenderSystem.initBackendSystem()` | `vanilla/com/mojang/blaze3d/systems/RenderSystem.java:201` | Sets the SDL hints, then `SDLInit.SDL_Init(32)` (VIDEO only) |
| `SDLEventHandler` | `vanilla/com/mojang/blaze3d/platform/SDLEventHandler.java` | The `SDL_PollEvent` loop that dispatches to KeyboardHandler and MouseHandler |
| `Window.handleEvent(SDL_Event)` | `vanilla/com/mojang/blaze3d/platform/Window.java:182` | The `default:` branch of the poll loop. It only handles window and display events |
| `InputConstants` | `vanilla/com/mojang/blaze3d/platform/InputConstants.java` | Key and mouse constants, `isKeyDown(int)` through `SDL_GetKeyboardState` |
| `Minecraft.run()` | `vanilla/net/minecraft/client/Minecraft.java:901` | Main loop that calls `RenderSystem.pollEvents(sdlEventHandler)` and then `runTick(...)` |

Init order (`vanilla/net/minecraft/client/main/Main.java:270-272,285` and `Minecraft.java:390` `instance = this`, `:431` `new Options`, `:456` SDL init):
```java
Thread.currentThread().setName("Render thread");
RenderSystem.initRenderThread();          // render thread == game thread == process main thread
minecraft = new Minecraft(gameConfig);    // ctor: Util.setTimeSource(RenderSystem.initBackendSystem());  -> SDL_Init(VIDEO)
minecraft.run();
```
At shutdown, `Minecraft.java:1155` calls `SDLInit.SDL_Quit()`. UNVERIFIED (SDL3 docs, not in .ref): SDL_Quit
also shuts down subsystems we started, so we don't need our own `SDL_QuitSubSystem`.
Mod code runs **before** SDL exists on both loaders: NeoForge constructs mods in `ClientModLoader.begin()`
(`neoforge/net/minecraft/client/main/Main.java:167`, before `new Minecraft`); Fabric's `onInitializeClient` runs
before `Minecraft.java:431` (`new Options`), proven by `KeyMappingRegistryImpl.registerKeyMapping` throwing once
`options != null` (`fabric-api/fabric-key-mapping-api-v1/net/fabricmc/fabric/impl/client/keymapping/KeyMappingRegistryImpl.java`).

### Event pumping (important for us)

```java
// vanilla/com/mojang/blaze3d/platform/SDLEventHandler.java
public void pollEvents() {                     // once per frame, from Minecraft.run() via RenderSystem.pollEvents
    try (SDL_Event event = SDL_Event.malloc()) {
        while (SDLEvents.SDL_PollEvent(event)) {
            switch (event.type()) {
                case 768: case 769: handleKeyEvent(event); break;   // KEY_DOWN/UP
                ... 1024..1027 mouse, 4096..4099 drop ...
                default: this.window.handleEvent(event);            // joystick 1536..1544 / gamepad 1616..1627 end up here -> ignored
            }
        }
    }
}
public void pumpEvents() { this.flushInputEvents(); this.pollEvents(); }
public void flushInputEvents() {
    SDLEvents.SDL_PumpEvents();
    SDLEvents.SDL_FlushEvents(768, 4871);   // KEY_DOWN .. PEN_AXIS -> includes ALL joystick/gamepad events
}
```
`pumpEvents()` runs from the integrated-server wait loops (`RenderSystem.pumpEvents` at `Minecraft.java:2063` and
`:2167`) and once in the constructor (`Minecraft.java:742`); the constructor also calls `flushInputEvents()` directly
(`Minecraft.java:549`). While a world loads, joystick ADDED and REMOVED events are deleted before anyone sees them.
NeoForge does not patch this class (`neoforge/com/mojang/blaze3d/platform/SDLEventHandler.java` is identical).

Consequences:
1. SDL state is still updated. `SDL_PumpEvents` is called inside `SDL_PollEvent` and `flushInputEvents`, and
   it calls `SDL_UpdateJoysticks()` (UNVERIFIED, SDL3 doc of `SDL_UpdateJoysticks`: "called automatically by the
   event loop if any joystick events are enabled"). So `SDL_GetJoystickAxis`/`Button`/`Hat` return fresh values
   every frame. The sketch calls `SDL_UpdateJoysticks()` anyway, so correctness does not depend on this.
2. We **must not** run our own `SDL_PollEvent` loop, because it would eat key and mouse events. To see events
   anyway, use `SDLEvents.SDL_AddEventWatch` (below). The simpler option is to diff device lists.
3. Key and mouse handlers run through `minecraft.execute(...)` (`BlockableEventLoop.execute`,
   `vanilla/net/minecraft/util/thread/BlockableEventLoop.java:98`). It schedules only if
   `ReentrantBlockableEventLoop.scheduleExecutables()` returns `runningTask() || !isSameThread()`
   (`vanilla/net/minecraft/util/thread/ReentrantBlockableEventLoop.java:11`). From `Minecraft.run()` we are on the
   owning thread and not inside a task, so they run **synchronously inside `pollEvents()`**, i.e. before our TAIL hook.

### Threading
- Make every SDL call from the **render thread**. It is also the game thread and, on macOS, the process main
  thread, which SDL video requires. Check with `RenderSystem.isOnRenderThread()` or
  `RenderSystem.assertOnRenderThread()` (`vanilla/com/mojang/blaze3d/systems/RenderSystem.java:137,141`).
  UNVERIFIED (SDL internals, not in .ref): SDL's macOS IOKit joystick backend runs its HID callbacks on the run
  loop of the thread that initialised it. Init and poll on the same (render) thread either way.
  Do not init from NeoForge `FMLClientSetupEvent` either: mod-bus setup events may run off the render thread.
- Never call SDL from the integrated server thread or from netty threads. Copy an immutable input snapshot
  per frame and send it to the server.

## 2. SDL3 joystick and gamepad API (LWJGL 3.4.3), exact signatures [lwjgl-sdl]

All functions are `public static` on the named class.

**`org.lwjgl.sdl.SDLInit`**
```java
boolean SDL_InitSubSystem(int flags);   void SDL_QuitSubSystem(int flags);   int SDL_WasInit(int flags);
boolean SDL_IsMainThread();
// constants: SDL_INIT_VIDEO=32, SDL_INIT_JOYSTICK=512, SDL_INIT_HAPTIC=4096, SDL_INIT_GAMEPAD=8192, SDL_INIT_EVENTS=16384
```
UNVERIFIED (SDL3 docs): `SDL_INIT_GAMEPAD` implies `SDL_INIT_JOYSTICK`. To be safe pass
`SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD` (flags are ORable ints).

**`org.lwjgl.sdl.SDLJoystick`** (IDs are `SDL_JoystickID` ints; UNVERIFIED (SDL3 docs): unique per connection,
never reused, never 0. Handles are `long` `SDL_Joystick*`, 0 on failure.)
```java
boolean      SDL_HasJoystick();
IntBuffer    SDL_GetJoysticks();                         // SDL-allocated! free with SDLStdinc.SDL_free(IntBuffer); may be null
String       SDL_GetJoystickNameForID(int id);
String       SDL_GetJoystickPathForID(int id);
SDL_GUID     SDL_GetJoystickGUIDForID(int id, SDL_GUID __result);   // struct-by-value: pass a preallocated SDL_GUID
short        SDL_GetJoystickVendorForID(int id);  short SDL_GetJoystickProductForID(int id);
int          SDL_GetJoystickTypeForID(int id);           // SDL_JOYSTICK_TYPE_* (UNKNOWN=0, GAMEPAD=1, FLIGHT_STICK=4, ...)
long         SDL_OpenJoystick(int id);                   // 0 on failure -> SDLError.SDL_GetError()
long         SDL_GetJoystickFromID(int id);              // already-open handle or 0
void         SDL_CloseJoystick(long joystick);
boolean      SDL_JoystickConnected(long joystick);
int          SDL_GetJoystickID(long joystick);
String       SDL_GetJoystickName(long joystick);
SDL_GUID     SDL_GetJoystickGUID(long joystick, SDL_GUID __result);
String       SDL_GetJoystickSerial(long joystick);
int          SDL_GetNumJoystickAxes(long j);  int SDL_GetNumJoystickButtons(long j);  int SDL_GetNumJoystickHats(long j);  // -1 on error
short        SDL_GetJoystickAxis(long j, int axis);      // SDL_JOYSTICK_AXIS_MIN=-32768 .. SDL_JOYSTICK_AXIS_MAX=32767
boolean      SDL_GetJoystickAxisInitialState(long j, int axis, ShortBuffer state);  // resting value (semantics UNVERIFIED, SDL3 docs)
boolean      SDL_GetJoystickButton(long j, int button);
byte         SDL_GetJoystickHat(long j, int hat);        // bitmask: CENTERED=0, UP=1, RIGHT=2, DOWN=4, LEFT=8
void         SDL_UpdateJoysticks();                      // manual state refresh (safe to call extra)
void         SDL_SetJoystickEventsEnabled(boolean);  boolean SDL_JoystickEventsEnabled();
void         SDL_LockJoysticks();  void SDL_UnlockJoysticks();
boolean      SDL_RumbleJoystick(long j, short low, short high, int durationMs);
```

**`org.lwjgl.sdl.SDLGamepad`** (the standard-layout layer on top of a joystick)
```java
IntBuffer SDL_GetGamepads();                 boolean SDL_IsGamepad(int joystickId);
long      SDL_OpenGamepad(int id);           void    SDL_CloseGamepad(long gamepad);
long      SDL_GetGamepadJoystick(long gamepad);        // underlying SDL_Joystick* -> reuse raw joystick code
String    SDL_GetGamepadName(long gamepad);  int     SDL_GetGamepadType(long gamepad);
short     SDL_GetGamepadAxis(long gamepad, int axis);  // sticks -32768..32767, triggers 0..32767 (ranges UNVERIFIED, SDL3 docs)
boolean   SDL_GetGamepadButton(long gamepad, int button);
boolean   SDL_GamepadConnected(long gamepad);
int       SDL_AddGamepadMapping(CharSequence mapping);  // custom SDL_GameControllerDB line
// axes: SDL_GAMEPAD_AXIS_LEFTX=0, LEFTY=1, RIGHTX=2, RIGHTY=3, LEFT_TRIGGER=4, RIGHT_TRIGGER=5
// buttons: SOUTH=0, EAST=1, WEST=2, NORTH=3, BACK=4, GUIDE=5, START=6, LEFT_STICK=7, RIGHT_STICK=8,
//          LEFT_SHOULDER=9, RIGHT_SHOULDER=10, DPAD_UP=11, DPAD_DOWN=12, DPAD_LEFT=13, DPAD_RIGHT=14
```

**GUID to string:** `org.lwjgl.sdl.SDLGUID.SDL_GUIDToString(SDL_GUID guid, ByteBuffer pszGUID)`. The bytecode
checks that the buffer has at least 33 bytes. `SDL_StringToGUID(CharSequence, SDL_GUID)` converts back.
Allocate with `SDL_GUID.malloc(MemoryStack)` or `SDL_GUID.calloc()`. The field is `ByteBuffer data()`.

**Events** (`org.lwjgl.sdl.SDLEvents` constants): `SDL_EVENT_JOYSTICK_AXIS_MOTION=1536`, `HAT_MOTION=1538`,
`BUTTON_DOWN=1539`, `BUTTON_UP=1540`, `ADDED=1541`, `REMOVED=1542`, `GAMEPAD_ADDED=1619`, `GAMEPAD_REMOVED=1620`.
Struct accessors on `SDL_Event`: `jdevice()`, `jaxis()`, `jhat()`, `jbutton()`, `gdevice()`, `gaxis()`, `gbutton()`.
`SDL_JoyAxisEvent` has `int which()`, `byte axis()`, `short value()` and `long timestamp()`. `SDL_JoyDeviceEvent` has `int which()`.
Hooks: `boolean SDL_AddEventWatch(SDL_EventFilterI filter, long userdata)` and
`void SDL_RemoveEventWatch(SDL_EventFilterI, long)`. The filter method is `boolean invoke(long userdata, long event)`.
Wrap the event pointer with `SDL_Event.create(long)`.

**Hints** (`org.lwjgl.sdl.SDLHints`, set through `boolean SDLHints.SDL_SetHint(CharSequence name, CharSequence value)`
like vanilla does in `RenderSystem.initBackendSystem()` and `MacosUtil.setCtrlClickEmulatesRightClick`):
`SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS`, `SDL_HINT_AUTO_UPDATE_JOYSTICKS`, `SDL_HINT_JOYSTICK_HIDAPI`,
`SDL_HINT_JOYSTICK_IOKIT`, `SDL_HINT_JOYSTICK_MFI` (macOS), `SDL_HINT_JOYSTICK_RAWINPUT`, `SDL_HINT_JOYSTICK_GAMEINPUT`,
`SDL_HINT_JOYSTICK_DIRECTINPUT` (Windows) and `SDL_HINT_GAMECONTROLLERCONFIG`. Backend-selection hints must be set **before**
`SDL_InitSubSystem`. UNVERIFIED (SDL3 docs, not checkable in .ref): ALLOW_BACKGROUND_EVENTS defaults to `"0"`, which
means joystick input is ignored while the window is unfocused. That is fine as a failsafe, but do not rely on
it: also check `isWindowActive()` (3b).

**macOS and Apple's GameController framework (`SDL_JOYSTICK_MFI`), checked 02.10.:**
- Vanilla 26.3 only calls `SDLInit.SDL_Init(32)` (VIDEO, `vanilla/com/mojang/blaze3d/systems/RenderSystem.java:216`) and
  sets no joystick hint; nothing in vanilla, the NeoForge patches, neoforge-api or fabric-api initialises
  `SDL_INIT_JOYSTICK`/`SDL_INIT_GAMEPAD`. Propwash's `TransmitterInput#ensureInit` is the first, so a hint set right before
  its `SDL_InitSubSystem` is in time. `SDL_WasInit(SDL_INIT_JOYSTICK)` is checked anyway and logged if another mod was
  faster.
- LWJGL 3.4.3 (javap): `SDLHints.SDL_HINT_JOYSTICK_MFI`, `boolean SDL_ResetHint(CharSequence)`,
  `String SDL_GetHint(CharSequence)`, `SDLVersion.SDL_GetVersion()`, `SDL_VERSIONNUM_MAJOR/MINOR/MICRO(int)`,
  `String SDL_GetRevision()`, `SDLInit.SDL_QuitSubSystem(int)`. The bundled `libSDL3.dylib`
  (`lwjgl-sdl-3.4.3-natives-macos-arm64.jar`) links `GameController.framework` and contains the strings
  `SDL_JOYSTICK_MFI` and `supportsHIDDevice:` (`otool -L`, `strings`).
- Checked 02.10. against the SDL source of exactly the bundled version (tag `release-3.4.14` on github.com/libsdl-org/SDL;
  the log shows `SDL 3.4.14 SDL-3.4.14-62f10da`), not part of `.ref`, matching a user report (Radiomaster Pocket on an
  M1, zero joysticks, fixed by the environment variable `SDL_JOYSTICK_MFI=0`):
  - `src/joystick/darwin/SDL_iokitjoystick.c` `JoystickAlreadyKnown`: the IOKit driver treats every HID device as
    already known (skips it) when `IOS_SupportedHIDDevice` says yes and the device is not run by the 360Controller driver.
  - `src/joystick/apple/SDL_mfijoystick.m` `IOS_SupportedHIDDevice`: returns `false` right away if
    `SDL_GetHintBoolean(SDL_HINT_JOYSTICK_MFI, true)` is false, otherwise asks `[GCController supportsHIDDevice:]`.
    `IOS_JoystickInit` returns without registering the MFi driver if the hint is false. Both read the hint when they run
    (driver init, every device-added callback), so a hint set before `SDL_InitSubSystem` – or before a re-init after
    `SDL_QuitSubSystem` – takes effect.
  - `src/joystick/hidapi/SDL_hidapi_xboxone.c` `IsSupportedDevice`: on macOS wired Xbox One/Series pads (Apple driver,
    path `DevSrvsID…`) are left to the MFi driver only while the hint is true; with `0` the HIDAPI Xbox driver takes them.
    PlayStation and Switch Pro run through HIDAPI either way.
  - `src/SDL_hints.c` `SDL_SetHintWithPriority`: an environment variable wins over every priority below
    `SDL_HINT_OVERRIDE` (`SDL_SetHint` then returns `false`, "An environment variable is taking priority"), and
    `SDL_GetHint` returns the variable. Propwash checks `System.getenv` first and leaves the hint alone.
- Propwash: `input.MfiHint` (pure decision, JUnit `MfiHintTest`), `input.client.MacControllers`, option
  `InputConfig#appleGameController` (default `false` = hint `"0"`; `true` = `SDL_ResetHint`). Changing it restarts the
  subsystem (`SDL_QuitSubSystem(GAMEPAD | JOYSTICK)`, then the normal init on the next frame), never during a pilot
  session. Player-facing troubleshooting: `docs/input-troubleshooting.md`.

## 3. Recommended pattern for Propwash

### 3a. Where to hook: one common mixin, no per-loader glue

Fabric has no general per-frame client event. `ClientTickEvents.START_CLIENT_TICK/END_CLIENT_TICK`
(`fabric-api/fabric-lifecycle-events-v1/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java:32,41`)
hook HEAD/RETURN of `Minecraft.tick()` (`.../mixin/event/lifecycle/client/MinecraftMixin.java:34-42`), i.e. 20 Hz.
`LevelRenderEvents.START_MAIN` etc. (`fabric-api/fabric-rendering-v1/net/fabricmc/fabric/api/client/rendering/v1/level/LevelRenderEvents.java:46`)
are per frame but only while a level renders (not in menus/loading). NeoForge has `RenderFrameEvent.Pre(DeltaTracker)`
(`neoforge-api/net/neoforged/neoforge/client/event/RenderFrameEvent.java:41`, posted on `NeoForge.EVENT_BUS` from
`neoforge-api/net/neoforged/neoforge/client/ClientHooks.java:855`, called at `neoforge/net/minecraft/client/Minecraft.java:1328`
just before `GameRenderer.render`).
To avoid two code paths, inject at the **TAIL of `SDLEventHandler.pollEvents()`** (`public void pollEvents()`,
`vanilla/com/mojang/blaze3d/platform/SDLEventHandler.java:37`). That point is on the render thread, right after
SDL was pumped, and once per frame (`Minecraft.java:920`, before `runTick`). It also covers the loading loops,
because `pumpEvents()` calls `pollEvents()`. Precedent: Fabric API itself mixes into this class
(`fabric-api/fabric-client-gametest-api-v1/net/fabricmc/fabric/mixin/client/gametest/input/SDLEventHandlerMixin.java`
cancels `handleKeyEvent`, `handleMouseButtonEvent`, ... at HEAD). Common mixins load on both loaders
(`fabric/src/main/resources/fabric.mod.json` and `neoforge/src/main/resources/META-INF/neoforge.mods.toml` list
`${mod_id}.mixins.json`).

```java
// common/src/main/java/de/lardtom/propwash/mixin/client/SDLEventHandlerMixin.java
// propwash.mixins.json has "package": "de.lardtom.propwash.mixin" -> add "client.SDLEventHandlerMixin" to the "client" array
@Mixin(SDLEventHandler.class)
abstract class SDLEventHandlerMixin {
    @Inject(method = "pollEvents", at = @At("TAIL"))
    private void propwash$afterPoll(CallbackInfo ci) {
        RcInput.INSTANCE.onFrame();
    }
}
```

### 3b. Device manager (common, render thread only)

```java
public final class RcInput {
    public static final RcInput INSTANCE = new RcInput();
    private boolean initDone, available;
    private long nextScanMs;
    private long joy;                // SDL_Joystick* (0 = none)
    private long pad;                // SDL_Gamepad* when device is a known gamepad (0 otherwise)
    private int joyId;               // SDL_JoystickID of the open device
    private String wantedGuid;       // from config; null = first device
    private final float[] axes = new float[32];
    private int axisCount, buttonCount;
    private long buttons;            // bits 0..63
    private volatile RcSnapshot snapshot = RcSnapshot.EMPTY;   // immutable; read by physics / HUD / screens

    public void onFrame() {
        if (!ensureInit()) return;
        SDLJoystick.SDL_UpdateJoysticks();          // defensive; normally already done by SDL_PumpEvents
        long now = Util.getMillis();                                    // vanilla/net/minecraft/util/Util.java:180
        if (joy == 0L && now >= nextScanMs) { rescan(); nextScanMs = now + 500L; }   // throttled: only while no device
        if (joy == 0L) { snapshot = RcSnapshot.EMPTY; return; }
        if (!SDLJoystick.SDL_JoystickConnected(joy)) { close(); snapshot = RcSnapshot.EMPTY; return; }
        axisCount = Math.max(0, Math.min(SDLJoystick.SDL_GetNumJoystickAxes(joy), axes.length)); // -1 on error
        for (int i = 0; i < axisCount; i++) {
            short raw = SDLJoystick.SDL_GetJoystickAxis(joy, i);
            axes[i] = raw < 0 ? raw / 32768f : raw / 32767f;       // -> [-1, 1]
        }
        buttonCount = Math.max(0, Math.min(SDLJoystick.SDL_GetNumJoystickButtons(joy), 64));
        long b = 0L;
        for (int i = 0; i < buttonCount; i++) if (SDLJoystick.SDL_GetJoystickButton(joy, i)) b |= 1L << i;
        Minecraft mc = Minecraft.getInstance();
        if (b != buttons || sticksMoved()) mc.getFramerateLimitTracker().onInputReceived(); // see pitfall 11
        buttons = b;
        snapshot = new RcSnapshot(Arrays.copyOf(axes, axisCount), buttons,
                                  Blaze3D.getTime(),              // vanilla/com/mojang/blaze3d/Blaze3D.java:24, seconds
                                  mc.isWindowActive());
    }

    private boolean ensureInit() {
        if (initDone) return available;
        if ((SDLInit.SDL_WasInit(SDLInit.SDL_INIT_VIDEO) & SDLInit.SDL_INIT_VIDEO) == 0) return false; // too early
        initDone = true;
        // optional, BEFORE init: SDLHints.SDL_SetHint(SDLHints.SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "0");
        available = SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_JOYSTICK | SDLInit.SDL_INIT_GAMEPAD);
        if (!available) Constants.LOG.warn("Propwash: SDL joystick init failed: {}", SDLError.SDL_GetError());
        return available;
    }

    /** Lists devices; used by rescan() and by the controller setup screen. */
    public static List<RcDevice> listDevices() {
        IntBuffer ids = SDLJoystick.SDL_GetJoysticks();
        if (ids == null) return List.of();
        List<RcDevice> out = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_GUID guid = SDL_GUID.malloc(stack);
            ByteBuffer str = stack.malloc(33);
            for (int i = 0; i < ids.remaining(); i++) {
                int id = ids.get(ids.position() + i);
                SDLJoystick.SDL_GetJoystickGUIDForID(id, guid);
                SDLGUID.SDL_GUIDToString(guid, str);
                out.add(new RcDevice(id, SDLJoystick.SDL_GetJoystickNameForID(id),
                        MemoryUtil.memASCII(MemoryUtil.memAddress(str)),   // NUL-terminated
                        SDLGamepad.SDL_IsGamepad(id)));
            }
        } finally {
            SDLStdinc.SDL_free(ids);
        }
        return out;
    }

    private void rescan() {
        if (joy != 0L) return;                        // keep current device; removal is caught by SDL_JoystickConnected
        for (RcDevice d : listDevices()) {
            if (wantedGuid != null && !wantedGuid.equals(d.guid())) continue;
            if (d.gamepad()) { pad = SDLGamepad.SDL_OpenGamepad(d.id()); joy = pad != 0L ? SDLGamepad.SDL_GetGamepadJoystick(pad) : 0L; }
            else             { joy = SDLJoystick.SDL_OpenJoystick(d.id()); }
            if (joy != 0L) { joyId = d.id(); return; }
            Constants.LOG.warn("Propwash: cannot open {}: {}", d.name(), SDLError.SDL_GetError());
        }
    }

    private void close() {
        if (pad != 0L) SDLGamepad.SDL_CloseGamepad(pad); else if (joy != 0L) SDLJoystick.SDL_CloseJoystick(joy);
        pad = 0L; joy = 0L; joyId = 0;
    }
    public RcSnapshot snapshot() { return snapshot; }
}
```

Notes on the sketch (`sticksMoved()` = any axis changed by more than a small deadband since the last frame;
`RcDevice`/`RcSnapshot` are our own records; `Constants.LOG` is `common/src/main/java/de/lardtom/propwash/Constants.java`):
- **RC radio (EdgeTX in USB-Joystick mode)** is a plain HID joystick. SDL does not know it as a gamepad, so
  `SDL_IsGamepad` returns false and we use the raw axes. Channel-to-axis order depends on the radio's
  USB-joystick config. Build a mapping UI: stick → axis index, invert, and centre/min/max calibration via
  `SDL_GetJoystickAxisInitialState`. Store the mapping per `guid + name`. The GUID identifies a device model,
  not one physical unit.
- **Gamepads:** when `SDL_IsGamepad(id)` is true, you can also read `SDL_GetGamepadAxis(pad, SDL_GAMEPAD_AXIS_LEFTX..)`.
  That gives standard-layout Mode-2 defaults (left Y = throttle, left X = yaw, right X/Y = roll/pitch) with no
  calibration.
- **Hotplug:** handled by listing `SDL_GetJoysticks()` every 500 ms while no device is open, plus
  `SDL_JoystickConnected` on the open one (the device-setup screen can call `listDevices()` itself). This is
  robust even through the `SDL_FlushEvents` inside `pumpEvents()`. For instant notification you could add a watch
  (`SDL_EventFilter cb = SDL_EventFilter.create((ud, ev) -> { if (SDL_Event.create(ev).type() == 1541) ...; return true; });
  SDLEvents.SDL_AddEventWatch(cb, 0L);`; `SDL_EventFilter.create(SDL_EventFilterI)` exists in the jar). Keep a strong
  reference to `cb`, and call `cb.free()` after `SDL_RemoveEventWatch`. UNVERIFIED (SDL3 docs): watches can fire on
  the thread that pushes the event, so treat the callback as "set a flag" only.
- **Sampling rate:** state is sampled once per rendered frame. Run drone physics on the client with the latest
  snapshot. If physics sub-steps faster than the frame rate, hold the value (zero-order hold). Axis events do not
  help here: they are produced in the same `SDL_UpdateJoysticks` burst.
- **Failsafe:** `Minecraft.isWindowActive()` (`vanilla/net/minecraft/client/Minecraft.java:2759`, returns
  `window.isFocused()`, `vanilla/com/mojang/blaze3d/platform/Window.java:661`) and an empty snapshot should disarm
  or cut throttle. UNVERIFIED (SDL3 docs): with the default background hint, SDL itself stops forwarding joystick
  input when the window is unfocused.

### 3c. Key mappings (arm, reset, toggle FPV, open controller setup)

`KeyMapping` (`vanilla/net/minecraft/client/KeyMapping.java`):
```java
public KeyMapping(String name, int keysym, KeyMapping.Category category)                       // :90  keysym = SDL scancode (KEYBOARD)
public KeyMapping(String name, InputConstants.Type type, int value, KeyMapping.Category category) // :94
public KeyMapping(String name, InputConstants.Type type, int value, KeyMapping.Category category, int order) // :98
public boolean isDown();  public boolean consumeClick();                                       // :108, :116
public boolean matches(KeyEvent event);  public boolean matchesMouse(MouseButtonEvent event);  // :167, :171
public boolean matches(InputConstants.Key key);                                                // :175
public record Category(Identifier id) { public static Category register(Identifier id); public Component label(); } // :203,:218,:228
```
`label()` is `Component.translatable(id.toLanguageKey("key.category"))`, so the lang key is
`key.category.propwash.<path>`; the mapping name itself is the lang key (e.g. `key.propwash.arm`).
KeyMappings only get `set/click` while no screen is open (`vanilla/net/minecraft/client/KeyboardHandler.java:528`
`handlesGameInput = gui.screen() == null`, used at `:569-575`), and
vanilla drains them with `while (km.consumeClick())` in `Minecraft.handleKeybinds()` (`Minecraft.java:1893`, called
from `tick()` at `:1810` only when `gui.screen() == null`). Drain ours per tick: Fabric
`ClientTickEvents.END_CLIENT_TICK` (path above), NeoForge `ClientTickEvent.Post`
(`neoforge-api/net/neoforged/neoforge/client/event/ClientTickEvent.java:35`, game bus, fired at
`neoforge/net/minecraft/client/Minecraft.java:1942`). Inside the workbench screen use `keyPressed` (section 4).

Categories and registration differ per loader:
- **Fabric:** `public static KeyMapping registerKeyMapping(KeyMapping)` in `onInitializeClient`
  (`fabric-api/fabric-key-mapping-api-v1/net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper.java:50`).
  It throws `IllegalStateException` once `Minecraft.getInstance().options != null`
  (`.../impl/client/keymapping/KeyMappingRegistryImpl.java`). For the category, call vanilla
  `KeyMapping.Category.register(id)`, which adds it to `SORT_ORDER` (used by `KeyMapping.compareTo`, `:146-150`);
  Fabric re-sorts `SORT_ORDER` after every register (`.../mixin/client/keymapping/KeyMappingCategoryMixin.java`).
- **NeoForge:** mod-bus (`implements IModBusEvent`) `RegisterKeyMappingsEvent`: `public void register(KeyMapping key)`
  and `public void registerCategory(KeyMapping.Category category)`
  (`neoforge-api/net/neoforged/neoforge/client/event/RegisterKeyMappingsEvent.java:42,49`; duplicate category id
  throws). In the Neo-patched source, `KeyMapping.Category.register(Identifier)` is `@Deprecated` in favour of the
  event (`neoforge/net/minecraft/client/KeyMapping.java:322-323`). Create the category with `new KeyMapping.Category(id)`.
  Neo adds ctors with `IKeyConflictContext`/`KeyModifier` (`neoforge/net/minecraft/client/KeyMapping.java:122-143`;
  `neoforge-api/net/neoforged/neoforge/client/settings/KeyConflictContext.java`: `UNIVERSAL`, `GUI`, `IN_GAME`);
  they do not exist on Fabric, so common code must use the vanilla ctors.

Common holds the ids and a factory `PropwashKeys.create(KeyMapping.Category cat)`. Each loader builds the
category its own way and passes it in. Default keys use `InputConstants.KEY_*`, which are scancodes: for example
`KEY_R = 21`, `KEY_SPACE = 44`, `KEY_RETURN = 40`, `KEY_ESCAPE = 41`, `KEY_TAB = 43`, `KEY_LSHIFT = 225`.

## 4. Keyboard and mouse events (screens, including the drone-building workbench)

Records in `vanilla/net/minecraft/client/input/`:
```java
public record KeyEvent(@InputConstants.Value int key, int keycode, int modifiers) implements InputWithModifiers
    // key = SDL scancode (physical, layout-independent), keycode = SDL keycode (layout-dependent: 'a'=97, arrows 0x4000_00xx)
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers  // button(), modifiers()
public record MouseButtonInfo(int button, int modifiers) implements InputWithModifiers
public record CharacterEvent(int codepoint)   // codepointAsString(), isAllowedChatCharacter()
public interface InputWithModifiers {         // input(), shortcutKey() (=keycode for KeyEvent), modifiers(),
    // isEscape() isConfirmation() isSelection() isLeft/Right/Up/Down() isCycleFocus()
    // hasShiftDown() hasControlDown() hasAltDown() hasControlDownWithQuirk() (Cmd on macOS) isCopy/isPaste/isCut/isSelectAll()
}
```
Constants (`vanilla/com/mojang/blaze3d/platform/InputConstants.java:145-197`): `PRESS=1`, `RELEASE=0`, `REPEAT=-1`,
`MOUSE_BUTTON_LEFT=1`, `MIDDLE=2`, `RIGHT=3`, `4..8`, `MOD_SHIFT=3`, `MOD_CONTROL=192`, `MOD_ALT=768`,
`MOD_SUPER=3072`, `MOD_CAPS_LOCK=8192`, `MOD_NUM_LOCK=4096`, and a few `KEYCODE_*` values such as `KEYCODE_UP=1073741906`.
`InputConstants.Type` is only `KEYBOARD` and `MOUSE` (`InputConstants.java:307-322`). **`SCANCODE` is gone.** Helpers:
`static InputConstants.Key getKey(KeyEvent)`, `static InputConstants.Key getKey(String name)`,
`static boolean isKeyDown(int key)` (scancode, via `SDLKeyboard.SDL_GetKeyboardState()`).
`isKeyDown` has **no window-handle parameter any more**; `grabMouse/releaseMouse(Window, double, double)` take a `Window`.
Polling modifiers outside an event: `Minecraft.hasShiftDown()/hasControlDown()/hasAltDown()` (`Minecraft.java:766-776`).

`GuiEventListener` (`vanilla/net/minecraft/client/gui/components/events/GuiEventListener.java:18-49`):
```java
void    mouseMoved(double x, double y);
boolean mouseClicked(MouseButtonEvent event, boolean doubleClick);
boolean mouseReleased(MouseButtonEvent event);
boolean mouseDragged(MouseButtonEvent event, double dx, double dy);
boolean mouseScrolled(double x, double y, double scrollX, double scrollY);
boolean keyPressed(KeyEvent event);   boolean keyReleased(KeyEvent event);
boolean charTyped(CharacterEvent event);   boolean preeditUpdated(@Nullable PreeditEvent event);
```
Vanilla call site: `Screen.keyPressed` (`vanilla/net/minecraft/client/gui/screens/Screen.java:124`) switches
on `event.shortcutKey()` for Tab (9) and the arrow keycodes. `AbstractContainerScreen.keyPressed`
(`vanilla/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java:499`) closes on
`options.keyInventory.matches(event)`. Mouse checks look like `event.button() == 1` (left) in
`vanilla/net/minecraft/client/gui/screens/ChatScreen.java:197`.
Screens are opened with `minecraft.gui.setScreen(screen)` and read with `minecraft.gui.screen()`
(`vanilla/net/minecraft/client/gui/Gui.java:217,221`; `Minecraft.setScreen` no longer exists, `gui` is
`public final Gui gui`, `Minecraft.java:299`). A menu-backed workbench screen is opened by the server's menu, not by
`setScreen` (see the GUI/menu notes).
`mouseDragged` is called from `MouseHandler.handleAccumulatedMovement()` only while a button is held
(`vanilla/net/minecraft/client/MouseHandler.java:283-288`); `dx/dy` are already GUI-scaled. The default
`ContainerEventHandler.mouseDragged` only forwards button 1 (left) to the focused child
(`vanilla/net/minecraft/client/gui/components/events/ContainerEventHandler.java:72-73`).

Workbench -> server without a custom packet (the vanilla way for Stonecutter/Loom/Enchanting buttons):
```java
// client, e.g. in mouseClicked of an "Assemble" button area
if (menu.clickMenuButton(minecraft.player, BTN_ASSEMBLE))                                   // predict locally
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BTN_ASSEMBLE);          // sends ServerboundContainerButtonClickPacket
// server: override in our AbstractContainerMenu
@Override public boolean clickMenuButton(Player player, int buttonId) { ... return true; }
```
Sources: `vanilla/net/minecraft/client/multiplayer/MultiPlayerGameMode.java:501` `public void handleInventoryButtonClick(int containerId, int buttonId)`,
`vanilla/net/minecraft/world/inventory/AbstractContainerMenu.java:304` `public boolean clickMenuButton(Player player, int buttonId)`,
call site `vanilla/net/minecraft/client/gui/screens/inventory/StonecutterScreen.java:147-149`, server side
`vanilla/net/minecraft/server/network/ServerGamePacketListenerImpl.java:2079`. `minecraft.gameMode` is `@Nullable`
(`Minecraft.java:344`). Slot clicks: `protected void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput)`
(`AbstractContainerScreen.java:475`); **`ClickType` was renamed `ContainerInput`**
(`vanilla/net/minecraft/world/inventory/ContainerInput.java`: `PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL`).

Workbench screen sketch (common):
```java
@Override public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
    if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && partSlotAt(e.x(), e.y()) instanceof PartSlot s) { pickUp(s, e.hasShiftDown()); return true; }
    if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) { rotatePreview(); return true; }
    return super.mouseClicked(e, dbl);
}
@Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {  // orbit the 3D drone preview
    if (e.button() == InputConstants.MOUSE_BUTTON_LEFT && inPreview(e.x(), e.y())) { yaw += (float) dx; pitch += (float) dy; return true; }
    return super.mouseDragged(e, dx, dy);
}
@Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {   // zoom preview
    if (inPreview(x, y)) { zoom = Mth.clamp(zoom - (float) scrollY * 0.1f, 0.5f, 3f); return true; }
    return super.mouseScrolled(x, y, scrollX, scrollY);
}
@Override public boolean keyPressed(KeyEvent e) {
    if (!(getFocused() instanceof EditBox) && e.key() == InputConstants.KEY_R) { resetPreview(); return true; } // physical R
    return super.keyPressed(e);                                             // keeps ESC/inventory-close behaviour
}
```
(`getFocused()`: `vanilla/net/minecraft/client/gui/components/events/AbstractContainerEventHandler.java:23`; without
the `EditBox` check, typing an "r" into a drone-name field would reset the preview.)
A controller-setup screen can simply read `RcInput.INSTANCE.snapshot()` in `render(...)`. The snapshot for
the frame is taken in `pollEvents` before rendering.

Loader-side screen hooks (only needed for someone else's screens):
- Fabric: `ScreenKeyboardEvents.allowKeyPress(screen)` gives `boolean allowKeyPress(Screen, KeyEvent)`, and
  `ScreenMouseEvents.allowMouseClick(screen)` gives `boolean allowMouseClick(Screen, MouseButtonEvent)`
  (`fabric-api/fabric-screen-api-v1/net/fabricmc/fabric/api/client/screen/v1/ScreenKeyboardEvents.java:49,156`,
  `.../ScreenMouseEvents.java:48,199`).
  Register them per screen inside `ScreenEvents.AFTER_INIT`
  (`fabric-api/fabric-screen-api-v1/net/fabricmc/fabric/api/client/screen/v1/ScreenEvents.java:99`).
- NeoForge: `ScreenEvent.MouseButtonPressed.Pre/Post` (`getMouseButtonEvent()`, `:378`) and
  `ScreenEvent.KeyPressed.Pre/Post` (`KeyInput.getKeyEvent()`, `:767`) in
  `neoforge-api/net/neoforged/neoforge/client/event/ScreenEvent.java:367` (MouseButtonPressed) and `:811` (KeyPressed);
  the `Pre` variants are `ICancellableEvent`.
  In-world hooks are `InputEvent.Key` (`getKeyEvent()`, `getKey()` = scancode, `getKeycode()`, `getAction()`,
  `getModifiers()`; **not cancellable**) and `InputEvent.MouseButton.Pre` (cancellable; `getButton()`, `getAction()`)
  (`neoforge-api/net/neoforged/neoforge/client/event/InputEvent.java:240` and `:105`).
- Fabric has no in-world equivalent of `InputEvent`; use KeyMappings or a common mixin.

Suppressing vanilla player control while flying (details belong to the camera and entity notes):
- Mouse look happens in `MouseHandler.handleAccumulatedMovement()` → `private void turnPlayer(double)`
  (`vanilla/net/minecraft/client/MouseHandler.java:258,325`).
- WASD handling is in `KeyboardInput.tick()` (`vanilla/net/minecraft/client/player/KeyboardInput.java:26`).
  NeoForge offers `MovementInputUpdateEvent` (`getInput()` returns `ClientInput`,
  `neoforge-api/net/neoforged/neoforge/client/event/MovementInputUpdateEvent.java:36`) and `CalculatePlayerTurnEvent`
  (`neoforge-api/net/neoforged/neoforge/client/event/CalculatePlayerTurnEvent.java`). The latter is **not cancellable**:
  it only exposes `get/setMouseSensitivity(double)` and `get/setCinematicCameraEnabled(boolean)`, read in the
  Neo-patched `turnPlayer` (`neoforge/net/minecraft/client/MouseHandler.java`). So one common mixin that cancels
  `MouseHandler.turnPlayer(double)` at HEAD while flying is simpler and works on both loaders.

## 5. Pitfalls checklist

1. **Do not use GLFW.** The class does not exist at runtime. The only `lwjgl-glfw` jar in the Gradle cache (3.3.3)
   belongs to older MC versions.
2. **Joystick subsystem is off by default.** Call `SDL_InitSubSystem(SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD)` lazily on
   the render thread after `SDL_WasInit(SDL_INIT_VIDEO)` is non-zero. Do not call it in Fabric `onInitializeClient`,
   a NeoForge mod constructor or at class-load time: all of these run before `RenderSystem.initBackendSystem()`
   (section 1).
3. **Free `SDL_GetJoysticks()` and `SDL_GetGamepads()`** with `SDLStdinc.SDL_free(IntBuffer)`. They are SDL-owned
   memory (`memIntBufferSafe` in the bytecode), and the buffer can be `null`.
4. **Never call `SDL_PollEvent` or `SDL_FlushEvents` yourself.** Doing so breaks vanilla keyboard and mouse input.
5. **Do not disable joystick events** via `SDL_SetJoystickEventsEnabled(false)` or `SDL_SetEventEnabled(1536,false)`
   unless you then call `SDL_UpdateJoysticks()` yourself every frame. UNVERIFIED (SDL3 docs): SDL's auto-update is
   tied to those events. (Both functions exist: `SDLJoystick.SDL_SetJoystickEventsEnabled(boolean)`,
   `SDLEvents.SDL_SetEventEnabled(int, boolean)`.)
6. **Axis range is asymmetric** (-32768..32767). Normalise the two halves separately (constants `SDL_JOYSTICK_AXIS_MIN/MAX` confirmed in the jar). UNVERIFIED (SDL3 docs): gamepad triggers are 0..32767.
7. **GUID is per model, not per unit.** Two identical radios share a GUID. `SDL_JoystickID` changes on every
   reconnect, so never persist it.
8. **Keys are scancodes and mouse buttons are 1-based.** `KEY_A == 4`, not 65. Left click is `1`, not `0`. Right
   click is `3`, not `1`. Use `shortcutKey()`/`keycode()` only for text or layout-dependent shortcuts.
9. **macOS:** `InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY` makes `EDIT_SHORTCUT_KEY_MODIFIER` = `MOD_SUPER` (Cmd)
   (`vanilla/net/minecraft/client/input/InputQuirks.java:15-16`), so use `hasControlDownWithQuirk()` for edit shortcuts.
   `EMULATE_RIGHT_CLICK_WITH_CTRL_KEY` only means the option exists: Ctrl+click arrives as a right click (button 3)
   only if the player enabled `options.ctrlClickEmulatesRightClick()` (`vanilla/net/minecraft/client/Options.java:1249`), applied via the SDL hint
   `SDL_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK` (`vanilla/com/mojang/blaze3d/platform/MacosUtil.java:41-44`, called at
   `Minecraft.java:699-700`). All SDL joystick work must stay on the first (main/render) thread.
10. **The integrated server never sees SDL.** Send a compact per-tick `RcSnapshot` (normalised floats) to the
    server if it has to validate. Keep drone physics authority on the client for latency.
11. **AFK frame limiter ignores the RC radio.** `FramerateLimitTracker` (`vanilla/com/mojang/blaze3d/platform/FramerateLimitTracker.java`)
    drops to 30 FPS after 60 s and 10 FPS after 600 s without input when `options.inactivityFpsLimit()` is `AFK`
    (`:39-51`, `vanilla/net/minecraft/client/InactivityFpsLimit.java`). Only keyboard/mouse call `onInputReceived()`
    (`KeyboardHandler.java:442`, `MouseHandler.java:69`). A pilot flying only with sticks would get throttled and
    our per-frame sampling would drop to 10 Hz. Call `minecraft.getFramerateLimitTracker().onInputReceived()`
    (`Minecraft.java:2892`, `FramerateLimitTracker.java:71`) whenever the sticks move or while armed.
12. **Mod init runs before SDL** on both loaders (section 1). Never touch `SDLJoystick` from a mod constructor,
    `onInitializeClient` or `FMLClientSetupEvent`; the lazy `ensureInit()` in `onFrame()` handles it.

## Verification

Checked by a second pass against `.ref` (grep/Read) and the LWJGL jars (`javap -constants`/`-c` with
`/opt/homebrew/opt/openjdk@25/bin/javap` on `lwjgl-sdl-3.4.3.jar` and `lwjgl-3.4.3.jar`). About 225 claims checked:
- **Confirmed as written:** 26.3 manifest has no `lwjgl-glfw`; no `org.lwjgl.glfw`, `SDLJoystick` or `SDLGamepad` in any
  `.ref` tree; `SDL_Init(32)` is the only SDL init (`RenderSystem.java:216`); the `SDLEventHandler` switch cases, `pumpEvents`,
  `flushInputEvents(768, 4871)`; `Window.handleEvent` cases; all `Minecraft.java` line refs (456, 742, 901, 920, 1155, 2063,
  2167, 2759); every `SDLInit`/`SDLJoystick`/`SDLGamepad`/`SDLGUID`/`SDL_GUID`/`SDL_Event`/`SDL_JoyAxisEvent`/`SDL_JoyDeviceEvent`/
  `SDLEvents`/`SDL_EventFilterI`/`SDLHints` signature and constant value in section 2; `memIntBufferSafe` in
  `SDL_GetJoysticks`/`SDL_GetGamepads`; the 33-byte check in `SDL_GUIDToString`; `SDLStdinc.SDL_free(IntBuffer)`;
  `MemoryUtil.memASCII(long)`; `KeyMapping` ctors/methods/`Category`; `KeyMappingHelper.registerKeyMapping`;
  `RegisterKeyMappingsEvent.register/registerCategory`; all `InputConstants` values; `KeyEvent`, `MouseButtonEvent`,
  `MouseButtonInfo`, `CharacterEvent`, `InputWithModifiers` members; all `GuiEventListener` signatures; the `Screen`,
  `AbstractContainerScreen`, `ChatScreen`, `Gui` call sites; Fabric `ScreenKeyboardEvents`/`ScreenMouseEvents` lines;
  `InputEvent.Key`/`MouseButton.Pre` lines; `MouseHandler`/`KeyboardInput` lines; `RenderFrameEvent` + `ClientHooks:855`.
- **Corrected (12):** `Main.java` range (`run()` is at :285); `scheduleExecutables` lives in
  `ReentrantBlockableEventLoop.java:11` (not `BlockableEventLoop:98`); `SDL_SetHint` takes `CharSequence`, not `String`;
  "Fabric has no per-frame event" (Fabric has `LevelRenderEvents`, per frame but only in a level); Neo `@Deprecated`
  on `Category.register` is at :322-323 (not :320); `ScreenEvent` line refs were swapped (MouseButtonPressed :367,
  KeyPressed :811); macOS Ctrl+click is a right click only when `ctrlClickEmulatesRightClick` is on;
  `CalculatePlayerTurnEvent` cannot cancel turning; sketch: the 500 ms rescan throttle never applied (`||` -> `&&`);
  sketch: `SDL_GetNumJoystickAxes() == -1` crashed `Arrays.copyOf`; mixin config entry must be `client.SDLEventHandlerMixin`;
  "no window-handle parameter" narrowed to `isKeyDown` (`grabMouse` takes a `Window`).
- **Added:** AFK frame limiter pitfall + `FramerateLimitTracker.onInputReceived()`; mod init order on both loaders
  (Neo `Main.java:167`, Fabric before `new Options`); where to drain KeyMappings per loader and the no-screen gate;
  workbench -> server via `clickMenuButton`/`handleInventoryButtonClick`; `ClickType` -> `ContainerInput`;
  `slotClicked` signature; GUI-scaled `mouseDragged` deltas; `EditBox` focus check; Neo `KeyConflictContext` ctors;
  Fabric's own `SDLEventHandlerMixin` as precedent; `ScreenEvents.AFTER_INIT`; lang keys for categories.
- **Marked UNVERIFIED:** everything that comes only from SDL3 documentation (auto-update inside `SDL_PumpEvents`,
  GAMEPAD implying JOYSTICK, ID uniqueness, background-events default, trigger range, event-watch thread, `SDL_Quit`
  cleanup, macOS IOKit run loop). None of this is in `.ref` or visible in the Java bindings.

Open questions (need a runtime test, not more source reading):
1. Does `SDL_PumpEvents` really refresh joystick state with Minecraft's setup, and does SDL zero the axes on focus loss?
   (Test: log `SDL_GetJoystickAxis` with and without our extra `SDL_UpdateJoysticks()`, then alt-tab.)
2. On macOS, does a late `SDL_InitSubSystem(JOYSTICK|GAMEPAD)` on the render thread pick up an EdgeTX radio as a
   plain joystick, or does HIDAPI/MFi claim it as a gamepad with a remapped axis order?
3. Is per-frame sampling enough for FPV latency, or do we need a faster poll (`SDL_LockJoysticks` + our own thread)?
   SDL thread safety for that is not visible in the bindings.

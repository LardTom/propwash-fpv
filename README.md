# Propwash FPV

An FPV drone racing and freestyle simulator for Minecraft. Build a quadcopter from real-world parts, put on your
goggles and fly it in first person with your own RC transmitter, a gamepad or the keyboard. Propwash has its own
flight model with a Betaflight-style flight controller, race gates with lap timing, analog and digital video that
breaks up the way a real video link does, full survival progression and an animated Flight School that teaches you
everything inside the game.

Fabric and NeoForge, Minecraft 26.3.

Documentation – player guide, world rules for server owners, add-on API and parts format:
[propwashfpv.com/docs](https://propwashfpv.com/docs/).

## Contents

- [Features](#features)
- [Getting started](#getting-started)
- [Controls](#controls)
- [Radio, gamepad and keyboard setup](#radio-gamepad-and-keyboard-setup)
- [Video systems](#video-systems)
- [Batteries and the charger](#batteries-and-the-charger)
- [Accessories, action cams and part wear](#accessories-action-cams-and-part-wear)
- [Paint jobs](#paint-jobs)
- [Racing](#racing)
- [Freestyle tricks](#freestyle-tricks)
- [Survival mode](#survival-mode)
- [Flight School](#flight-school)
- [Configurator](#configurator)
- [Servers and commands](#servers-and-commands)
- [Add-ons (Parts API)](#add-ons-parts-api)
- [Requirements](#requirements)
- [Known limitations](#known-limitations)
- [Building from source](#building-from-source)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Real flight model:** rigid body with motor, ESC and battery physics, rotor aerodynamics, ground effect, prop wash,
  voltage sag, wind and gusts. Every drone is derived from its parts: weight, thrust, hover throttle, top speed and
  flight time follow from the build.
- **Betaflight-style flight controller:** Betaflight and Actual rates, PID with feed-forward, I-term relax,
  anti-gravity, TPA, airmode, Acro/Angle/Horizon, 3D mode with reversible motors, arming checks, failsafe, flip-upright
  after a crash and GPS Rescue (return to home with obstacle avoidance). The tune is stored on the drone, like on a real flight controller.
- **Drones from parts:** five frames from a 65 mm whoop to a 7″ long range, flight controllers, six motors, six props,
  batteries and video units, assembled at the **Drone Workbench** with a live 3D preview and build analysis. Frames
  take more than one option where real ones do: a 3″ cinewhoop with light 1404 or punchy 2004 motors, a whoop with
  quad- or tri-blades, a race or long-range frame with a 30×30 stack or a light 20×20 AIO, a 7″ with 2807 or 2207
  motors. Each swap changes weight, thrust, current and the way the drone flies.
- **Drone Editor (creative):** the Creative Flight Controller fits every frame and opens the flight model of that drone
  in the configurator – masses, inertia, motor positions, props with thrust and power coefficients, motors, battery,
  ESC, drag and camera – with live key figures, templates from any preset and share codes. The physics stays the same,
  only its values are free; editable in creative mode only.
- **Accessories:** mount an action cam on top of the frame and an LED strip or a gripper under the nose. Their weight,
  inertia and current draw change how the drone flies.
- **Gripper:** hover low over dropped items and the servo claw picks them up (up to 400 g in 4 stacks, never more than
  half the spare thrust); the cargo makes the drone heavier. Drop it with `Enter`, a radio switch, by landing or in a
  crash.
- **Drones hit mobs and players:** a flying drone bounces off living things and hurts them by its weight × speed (up
  to five hearts, players only with PvP). Game rule `propwash:drone_hits_entities` turns it off.
- **Drones break glass:** with the game rule `propwash:drones_break_blocks` on (off by default) a fast drone smashes
  through glass, glass panes and decorated pots – and you feel it: it loses speed, gets knocked off its attitude, the
  picture jolts and shards fly ahead of you.
- **Action cams that really record:** the Action Cam (Light) and the stabilized Action Cam (Heavy) record the clean FPV
  camera image as an MP4 video (H.264, up to 1440p60, optionally with the motor sound), started with `F9`, a radio
  switch or on arming. A Recordings window lists, plays and deletes your clips.
- **LED strips and headlight:** 11 colors and Betaflight-style modes (steady, blink, arm status, battery warning,
  rainbow); in FPV the strip lights up caves. Switch it off and on in flight with a radio switch (*LEDs off*, like
  Betaflight's LED_LOW) or `F8`. Optional LambDynamicLights support makes the drone a real light source.
- **Part wear:** crashes wear props, motors, camera, frame, stack and battery down. Swap props in the field, repair
  two damaged props into one, replace other parts at the workbench.
- **Your own radio:** SDL3 joystick and gamepad support with a receiver wizard, calibration, deadband and free switch
  assignment, EdgeTX/OpenTX defaults and a keyboard fallback. The sticks on the in-game **FPV Remote** move
  with your real ones.
- **FPV and line of sight:** wear matching goggles to fly through the drone's camera, or take them off and fly line of
  sight with your own eyes while your view follows the drone. The FPV picture looks through a fisheye lens like a real
  FPV camera; a slider in the *Video* tab sets how strong (0 % is flat).
- **Analog and digital video:** analog goggles whose picture breaks up in stages (torn lines, color loss, snow,
  static), digital goggles that get softer, blockier and laggier, stutter and freeze on the last frame before "NO SIGNAL",
  latency per video system and a signal model with range and obstacles (one block of stone, brick, planks or concrete
  dampens like one house wall). The creative goggles receive everything and have free settings. VTX power from 25 mW up to the module's
  maximum (*Video channel* tab, `F10` or a switch, *Auto* by default on every drone): more range and penetration, more battery
  drain and heat.
- **OSD:** a Betaflight-like on-screen display with 43 elements, presets and a drag-and-drop editor.
- **Batteries and charging:** every pack keeps its own charge, only fits the frames it suits and only recharges in the
  **Battery Charger**.
- **Paint jobs:** color twelve parts of every drone separately at the workbench, including each prop, with dyes, five
  finishes, hex colors and presets. Other players see your colors.
- **Racing:** gates in three sizes for floors, walls (dive gates) and ceilings that stack into ladders, a course tool, lap timing with splits, standing starts with start lights for
  several pilots, a race line through the next gates, ghosts of your personal best and of the server record, and
  leaderboards per course and drone class in their own window.
- **Race courses in the world:** four kinds of ready-made courses generate in the overworld, with a record sign and a
  loot chest.
- **Bandos:** abandoned buildings to fly freestyle in generate in fitting biomes – barns, factories, water towers,
  piers, oil rigs and more, each one different, full of gaps and holes, now and then with a chest of used parts.
  Journeyman cartographers sell bando maps to the nearest unexplored one.
- **Waterproof drones and turtle mode:** spray a drone with the Waterproof Coating and it arms and flies in water; when
  it lands upside down, turtle mode (`Numpad 8` or a switch) flips it back over with its own props.
- **Video channels and spectating:** every transmitter sends on a real analog or digital channel; press `F7` to watch
  other pilots' feeds through your goggles.
- **Freestyle tricks:** 59 tricks in nine families – from flips, rolls and power loops to Matty variants, power flips,
  hammerheads, Juicy Flicks, Rubik's Cubes, Sbang combos, Guillotines and Side Trippies – are recognized while you fly, in
  3D mode also inverted flight, hovers, tic-tocs and 3D flips, with points, combos and chains in the OSD.
- **Propwash dust:** fly close to the ground and the rotors stir up a subtle cloud of dust in the color of the block
  below, or a fine spray over water. Height, amount and spread follow from thrust, prop size and tilt.
- **Sound:** real-time synthesized motor sound per drone, with Doppler effect, duct resonance on cinewhoops and beeps.
- **Survival mode:** everything except the creative goggles and the Creative Flight Controller can be crafted, five crafting materials connect vanilla
  resources to drone parts, the recipe book unlocks recipes as you go, and structure chests hold parts, batteries and
  now and then a ready-to-fly whoop.
- **Flight School:** nineteen animated 3D lessons (twenty in creative mode) covering every item and every step, opened by holding a key over any
  Propwash item.
- **Shift tooltips:** every Propwash item explains what it does, how to use it and what it fits.
- **Servers:** the drone is not tied to your render distance (the server streams chunks around the drone), shared
  chunk budget, server rules and permissions, and server-side validation of pilot movement.
- **Configurator:** a Betaflight-style settings screen with tabs for tuning, rates, receiver, modes, video, video
  channel, OSD, racing, freestyle, rescue, simulation, display, audio and server.
- **Add-ons:** a versioned Parts API lets other mods add frames of any shape (deadcat, stretched and squashed X,
  H-frames, ducted frames, X-Class up to 13″), stacks, motors, props, batteries up to 12S, video units and accessories.
  Add-on parts work everywhere: workbench, analysis, flight model, rendering, tooltips, Flight School and saves.

## Getting started

In creative mode, the **Propwash FPV** creative tab has five ready-to-fly drones (Whoop, Cinewhoop, Freestyle, Racer,
Long Range) and every part. In survival, see [Survival mode](#survival-mode) first.

1. **Build a drone.** Place a **Drone Workbench** and right-click it. Fill the slots: frame in the center, flight
   controller stack above it, four identical motors, four identical propellers, the video unit at the nose and a
   battery that fits the frame. The panel on the right shows weight, thrust-to-weight ratio, hover throttle, top speed,
   flight time and any problems. Take the finished drone out on the right. Putting a drone into the salvage slot takes
   it apart again. With the world rule *Parts must fit* turned off, any part goes with any other; the workbench still
   marks what wouldn't fit in reality.
2. **Place it.** Right-click a block with the **Drone**.
3. **Bind the remote.** Hold the **FPV Remote** and right-click the drone.
4. **Pick matching goggles.** Goggles need no binding, they only have to receive the drone's video system (analog
   goggles for analog video, digital goggles for digital video, creative goggles for everything). Right-click the drone
   with the goggles in hand to check whether they fit.
5. **Put the goggles on** (head slot, or right-click into the air with them).
6. **Take off.** Hold the bound radio and right-click into the air.
   - Wearing matching goggles: you fly **FPV** through the drone's camera, with the goggles' OSD.
   - Without goggles you fly **line of sight**: your view stays with your body and turns smoothly towards the drone, and
     a compact radio HUD shows battery, signal and flight mode.
   - Wearing goggles that can't receive the drone's video, the remote refuses to connect and names both systems. Put
     them on in mid-flight and they show no picture until you take them off.
7. **Arm and fly.** Arm with the arm switch or `Z`. Arming only works with the drone level and the throttle down.
   Walking and looking around are locked while you fly, and you have to keep holding the radio.
8. **Stop.** Land, disarm, then right-click with the radio again or press `Y` (Stop FPV flight). The drone stays bound.

Give your drone a name in the configurator's **Configuration** tab (hold or fly it), with a name tag on the placed drone
or in an anvil; the OSD and the radio show it.

With an empty hand, right-clicking a drone picks it up (battery, tune and bindings stay with it). Taking a drone apart
at the workbench keeps its tune and its binding on the flight controller stack, so rebuilding it with new props keeps
radio and goggles working. Sneak + right-click
opens the battery menu. In survival, hitting a drone also knocks it loose as an item. Sneak + right-click into the
air with the radio shows which drone it is bound to and how far away it is.

Not sure about something? Hold `M` over any Propwash item in your inventory to open the matching Flight School lesson,
and hold `Shift` over it for a detailed tooltip.

## Controls

Every function has its own key. Change them in the configurator's **Keys** tab (also listed under *Options → Controls
→ Key Binds → Propwash FPV*). Switch functions for a radio or gamepad are assigned in the **Modes** tab.

Default keys are key positions on a US layout; the game shows them with your keyboard's labels. On a German QWERTZ
keyboard, for example, the arm key shows as `Y` and the stop key as `Z`.

| Action | Default key | Switch function (radio default) | Gamepad default |
|---|---|---|---|
| Arm/disarm | `Z` | Arm (AUX1) | right shoulder |
| Cycle flight mode (acro/angle/horizon; while flying) | `R` | | |
| Flight mode Acro / Angle / Horizon | `Numpad 1` / `Numpad 2` / `Numpad 3` | Angle mode (AUX2), Horizon mode | left shoulder (angle on/off) |
| 3D mode on/off (after unlocking it in the *Modes* tab) | `Numpad 6` | 3D mode (unassigned) | |
| Flip drone upright (disarmed and at rest) | `U` | Flip upright | left face button |
| Turtle mode: flip a crashed drone back over with its own props (upside down, then arm) | `Numpad 8` | Turtle mode | |
| Stop FPV flight (also ends line of sight) | `Y` | – | – |
| Toggle locator beeper (keeps beeping after you leave the drone until you switch it off) | `.` (Period) | Beeper (AUX3) | top face button |
| GPS Rescue (return to home on/off) | `J` | Rescue / return home (AUX4) | right face button |
| Set home point | `,` (Comma) | Set home point | Start |
| Reset: drone back to its home point – level, disarmed, a running race is canceled (world rule *Allow reset*) | `Backspace` | Reset | Back |
| Rotate gate (sneak: change mount; gate or course tool in hand, not while flying; step in the *Race* tab) | `R` (shared with *Cycle flight mode*) | | |
| Action cam: start/stop recording (FPV, action cam mounted) | `F9` | Record (switch: on = recording; button: each press toggles) | |
| LED strip on/off while flying (LED strip mounted) | `F8` | LEDs off (active = strip and headlight off; unassigned = LEDs on) | |
| Step VTX power (25 mW up to the module's maximum) | `F10` | VTX power | |
| Accessory action 1–4 (add-on accessories) | `Numpad 9` / `Numpad 0` / `Numpad Enter` / `Numpad +` | Accessory action 1–4 | |
| Cargo drop: open the gripper (gripper mounted) | `Enter` | Drop cargo (button) | |
| Custom command 1–8: run your own Minecraft command (while flying) | unbound | Custom command 1–8 (*Modes* tab, page *Commands*) | |
| Spectate: next video channel (goggles on, not flying) | `F7` | | |
| Open configurator | `K` | | |
| Flight school (hold over an item; press in the world for the overview) | `M` | | |
| Open receiver wizard | unbound | | |

Right-clicking with the radio also ends a flight.

**Keyboard flight** (fallback when no radio or gamepad is connected, on by default): throttle and yaw follow your
movement keys (`W`/`S` throttle, `A`/`D` yaw) until you bind the dedicated keys; the arrow keys pitch and roll.
Keyboard flight starts in Angle mode.

**Mouse + keyboard** (pick it as input device in the *Receiver* tab or the receiver wizard): the mouse steers pitch
and roll like aiming in a shooter, `W`/`S` or the mouse wheel give throttle, `A`/`D` yaw and the middle mouse button
centers the sticks. *Automatic* holds the stick where you push it in Angle and Horizon and turns mouse speed into
stick deflection in Acro; *Speed*, *Hold*, mouse X on yaw, sensitivity, DPI, smoothing, deadzone, expo, return speed
and wheel throttle are in the *Receiver* tab, with live virtual sticks to try them out. By default pushing the mouse
forward tips the nose down like a stick; *Invert pitch*, right below the input device, reverses that for the mouse and
the pitch keys – also when you fly with the keyboard alone. See [`docs/mouse-control.md`](docs/mouse-control.md)
(German).

## Radio, gamepad and keyboard setup

- **EdgeTX/OpenTX radios:** connect the radio via USB and choose **USB Joystick (HID)** on the radio. Propwash detects
  it automatically and starts with AETR channel order, AUX1 = arm, AUX2 = flight mode (low Acro, middle Horizon,
  high Angle), AUX3 = beeper, AUX4 = rescue and full-travel throttle.
- **New devices are set up once:** the first time a radio or gamepad shows up, Propwash asks in-game (never mid-flight
  or over another menu) whether to set it up now: *Set up*, *Later* (one reminder per session) or *Never for this
  device*. Once the wizard is finished, the device reconnects silently with its profile and is picked automatically
  (most recently used first).
- **Stick mode (Mode 1–4):** *Receiver* tab, row *Stick mode*, with a small picture of both sticks. Mode 2 (throttle
  and yaw left, pitch and roll right) is the default; Mode 1 puts pitch and yaw left, throttle and roll right; Mode 3
  pitch and roll left, throttle and yaw right; Mode 4 throttle and roll left, pitch and yaw right. It is a personal
  setting of your game, applies at once and changes what Propwash shows and says: wizard prompts and the animated
  radio, the OSD stick overlay, the Flight school, the radio in your hand. A radio keeps its channels (set the mode on
  the radio itself); a gamepad's sticks follow the mode unless you mapped them yourself.
- **Gamepads** are detected automatically with the sticks of your stick mode (Mode 2: throttle and yaw on the left
  stick). The default throttle mode is *Upper half* (centered stick = throttle off); *Incremental* holds the throttle
  like cruise control.
- **Receiver wizard:** open the configurator (`K`), go to **Receiver** and click *Receiver wizard …*. It starts with
  the stick mode (Mode 2 is pre-selected; click a picture or press 1–4), then walks you
  through device, channels (move each stick when asked; push the throttle fully up, then fully down and hold it – where
  it rested before does not matter, and if the direction doesn't match where it started the wizard asks), a check page
  that shows every channel live with *Inv.* to flip it, calibration (center, then end stops) and switches (click
  *Assign* and flip the switch; the arm switch row is highlighted). An animated Propwash radio shows every movement.
- **Max arm angle:** *Configuration* tab, section *Arming* – like Betaflight's small_angle, 25° by default, 180° arms
  in any attitude (also in the air or upside down). Part of the drone's tune.
- **Modes tab:** assign every function (Arm, Angle mode, Horizon mode, Rescue / return home, Flip upright, Set
  home point, Beeper, Reset) to any AUX channel range (900–2100 µs) or button, inverted, held or as a toggle.
  *Flip switch* fills in source and range for you. **3D mode** is unlocked there once (row *3D mode*, *Unlock*); then
  the throttle center is zero thrust and below it the motors spin backwards. For 3D, calibrate the throttle center in
  the receiver wizard (throttle stick in the middle when you press *Capture center*).
- **Custom commands:** the *Commands* page of the Modes tab has eight *Custom command* functions. Put one on a switch,
  button or key and click *Set up command…* to enter a Minecraft command for switching on (e.g. `/time set night`) and
  optionally one for switching off (`/time set day`). It runs as if you typed it in chat, with your own permissions,
  only while you fly and never with a menu open; each switch fires at most every half second. Commands are stored per
  radio, the keyboard has its own set.
- **Keyboard:** the device selector also offers *Keyboard*, which always flies with the keyboard even when a controller
  is connected, and *Mouse + keyboard*, which adds mouse steering; the wizard has its own animated step for it.
- **macOS:** Propwash detects controllers without Apple's GameController framework by default, so radios such as the
  Radiomaster Pocket show up (macOS otherwise claims them without passing them on). PlayStation, Switch Pro and
  Xbox controllers keep working through SDL's own drivers. A pure MFi gamepad that only works through that framework
  needs *Apple GameController* under **Receiver → Advanced (macOS)**; a `SDL_JOYSTICK_MFI` environment
  variable set in your launcher takes precedence. Details: `docs/input-troubleshooting.md`.

Rates, PID and filters are set in **Rates** and **PID Tuning** and saved to the drone you fly or hold.

## Video systems

| Video unit | System | Latency | Range | Field of view | Picture |
|---|---|---|---|---|---|
| Analog Nano Camera + VTX | analog | 12 ms | ~250 blocks | 125° | noisy, degrades gradually |
| Analog Race Camera + VTX | analog | 12 ms | ~380 blocks | 125° | noisy, degrades gradually |
| Digital Video System (Lite) | digital | 26 ms | ~300 blocks | 104° | clear, stutters and freezes at the edge |
| Digital HD Video System | digital | 30 ms | ~520 blocks | 130° | sharpest, stutters and freezes at the edge |

The field of view is the horizontal angle of the camera in the video unit, as on real FPV cameras: a different field of
view means a different video unit. The camera angle (uptilt) belongs to the drone, see *Camera angle* below.

- **Analog FPV Goggles** receive analog video, **Digital FPV Goggles** receive digital video. Analog breaks up in stages
  with the link: a softer picture, then torn lines and sparkles, then stripes lose their color, then snow with sync
  jumps and finally full static. Digital stays digital and breaks up like DJI or Walksnail: lower bitrate (softer,
  blockier picture), more latency, stutter, then the last frame freezes and only a real loss shows "NO SIGNAL"; the
  picture comes back once the link is stable again. Low-latency digital systems (up to 18 ms) behave like HDZero: the
  picture stays sharp and keeps its latency, it breaks up into blocks instead. The OSD always stays readable on top.
- Blocks, leaves and water between you and the drone weaken the signal: one block of stone, brick, planks, concrete or
  terracotta about like one house wall (5–9 dB), glass, leaves and wool much less, metal and deep water block it.
  Thick terrain adds up, so hills and caves still cut the link, while the picture fades in and out around corners.
  The radio link sees the same obstacles: its RSSI drops behind walls, and far behind a hill or deep underground the
  drone goes to failsafe.
- **Creative FPV Goggles** (creative only) receive every system; latency, range (up to unlimited), noise and obstacle
  damping are free in the **Video** tab.
- The **Video** tab shows the field of view of the camera you fly (read-only for analog and digital goggles; the
  Creative FPV Goggles can follow the video unit or use a custom field of view) and sets effect strength and *Reduce
  flicker* (on by default).
- The radio link is separate from video: full strength up to about 800 blocks, fading after that and gone at about
  1,200 blocks; the pilot is released at 1,250 blocks. In creative mode or with the Creative FPV Goggles the radio has
  no fixed limit: it follows the goggles' range setting (unlimited by default) and only the server's own limit applies.

### Video channels and spectating

- Every video transmitter sends on a channel: analog on R1–R8 and F1–F8 with the real 5.8 GHz frequencies, digital
  on CH1–CH8. By default the server picks a free channel when you take off (starting with the usual race set R1, R2,
  F2, F4, R7, R8) and never gives two automatic drones the same frequency. Fix a channel per drone in the **Video
  channel** tab.
- Wearing goggles and not flying yourself, press `F7` to cycle through the channels of other pilots in your dimension
  that your goggles receive (analog goggles analog channels, digital goggles digital channels, creative goggles
  everything). You see their camera feed with your own video breakup, depending on your distance and the blocks
  between you and their drone. The pilot's OSD and a caption with channel, frequency, pilot and drone can be switched
  off in the tab. `F7` after the last channel, `Esc` or right-click brings you back.
- You control nothing while watching. The **Video channel** tab also lists all active channels; click one to watch.

## Batteries and the charger

Every battery is its own item with its own charge (bar, percentage and resting voltage in the tooltip). A drone holds
exactly one battery or none.

- **Swap:** sneak + right-click the drone to open the battery menu, or sneak + right-click with a fitting battery in
  hand to swap directly. Only possible when the drone is disarmed and nobody flies it.
- **Charge:** batteries only recharge in the **Battery Charger** (four bays). A full pack takes about 45 s to 2.5 min
  depending on capacity; a redstone signal makes it twice as fast. The block shows the packs in its bays with one LED
  per bay (yellow = charging, green = full), works with comparators, and hoppers below only pull out full packs.
- **Fit:** each frame takes certain packs. The workbench, the battery menu and the tooltips all use the same rule
  (world rule `propwash:part_compatibility` off: every pack fits every frame).

| Frame | Cells | 1S 300 mAh (LiHV) | 4S 650 mAh | 6S 1100 mAh | 6S 1300 mAh | 6S 4000 mAh (Li-Ion) |
|---|---|---|---|---|---|---|
| 65 mm Whoop | 1S | fits | – | – | – | – |
| 3″ Cinewhoop | 3S–4S | – | fits | – | – | – |
| 5″ Freestyle | 4S–6S | – | weak | fits | fits | too heavy |
| 5″ Race | 4S–6S | – | weak | fits | fits | too heavy |
| 7″ Long Range | 6S | – | – | weak | fits | fits |

"Weak" packs can be used, but the workbench warns you.

## Accessories, action cams and part wear

- **Accessory slots:** the Drone Workbench has two extra slots next to the frame, the action cam on top and the LED
  strip or the gripper below. Accessories are optional; their weight, inertia, frontal area and current draw go into the flight
  model.
- **Action Cam (Light)** (42 g): powered by the flight battery, raw image up to 1080p60, a bit noisy in the dark. It
  stops recording when the flight battery is empty.
- **Action Cam (Heavy)** (150 g): stabilization and optional horizon levelling up to 1440p60, its own battery (about
  90 min at 1080p) that charges over USB from the flight pack while the drone is parked with a battery.
- **Action Cam (Flashback)** (36 g): records no video but the path of your FPV camera for the replay mod
  [Flashback](https://modrinth.com/mod/flashback) (Fabric only, separate download). The same *Record* button works in FPV
  and line of sight; each flight becomes a scene in Flashback's editor with camera path, field of view and export range,
  ready to render, cut or change. If Flashback isn't recording yet, the button starts and finishes a Flashback recording
  too. Without Flashback the camera only shows a hint.
- **Recording:** press `F9` while flying FPV, use the *Record* function on a radio switch (on = recording) or button
  (each press toggles), or enable *Record on arm*. The clean camera image, without OSD and radio breakup, is saved as
  MP4 in `<game directory>/propwash/recordings`, optionally with the motor sound. A REC element in the OSD shows the
  time; encoding runs in the background. The *Recordings* window (*Action cam* tab) lists every clip with a thumbnail
  and plays or deletes it.
- **Faster recording with ffmpeg:** Propwash's own encoder runs on the CPU; slow computers keep encoding after the
  stop. With the free program [ffmpeg](https://ffmpeg.org) installed (not included; `winget install ffmpeg`,
  `brew install ffmpeg` or your Linux package) the video encoder of your graphics card or processor records instead
  and 1080p60 keeps up easily. The *Action cam* tab shows whether ffmpeg was found – version, location, hardware
  encoder and a quick speed test – has a *Test* button, a guide and a field for your own ffmpeg path, and every
  recording starts by naming the encoder at work. Guide: [propwashfpv.com/docs/guide](https://propwashfpv.com/docs/guide/#video-ffmpeg).
- **LED strip:** color, mode and brightness are stored on the drone. In FPV it throws a headlight cone ahead in the
  dark. With [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) installed (optional), the drone lights up
  the world for everyone. Put the *LEDs off* function on a radio switch in the Modes tab (or press `F8`) to switch the
  strip and the headlight off and on in flight; other players see it too, and an *LED OFF* element in the OSD reminds
  you. Without an assignment the LEDs always glow.
- **Gripper** (18 g): hangs straight down under the nose. Armed and hovering below one block over dropped items, it
  picks them up as long as weight and its 4 stacks allow: up to 400 g, but never more than half of the drone's spare
  thrust (a whoop carries about 20 g). Propwash parts weigh what they really weigh, other items 10–150 g. The load
  makes the drone heavier: more hover throttle, shorter flights. Drop it with *Cargo drop* (`Enter`) or a *Drop cargo*
  button in the Modes tab; landing and disarming sets it down, a hard crash drops it. Nothing is ever lost, and other
  players see the item hanging in the claw.
- **Hitting mobs and players:** with the game rule `propwash:drone_hits_entities` on (default), living things stop a
  flying drone like a wall: it bounces off, can crash and wears its parts. The hit deals damage and knockback from the
  drone's mass × closing speed – a whoop only bumps, a 5″ at full speed deals about four hearts, at most five. Players
  only take damage with PvP on; kills count for the pilot. Your own pilot is never hit, and hitting living things never
  damages a block.
- **Breaking glass:** with the game rule `propwash:drones_break_blocks` on (off by default), glass, tinted and stained
  glass, all glass panes and decorated pots break when the impact is strong enough – half the mass × speed² along the
  surface normal against a threshold from the block's hardness and thickness (a pane head-on 36 J, a glass block
  120 J). A whoop always bounces off, a 5″ goes through a pane from about 42 km/h and a glass block from about 77 km/h.
  The drone flies on with the speed it has left; what the block takes counts as an impact (beep, wear, a crash if you
  only just get through). Going through is felt and seen: the shards the drone drags along take some of its momentum
  (a 5″ loses about a fifth of its speed to a pane at 90 km/h), the blow knocks it off its attitude – nose down when
  it hits tilted forward, around the vertical axis when the glass is on one side only –, open props that lead into the
  glass lose revs for a moment and may get nicked at full throttle, the FPV picture gets a short jolt (it follows your
  *Camera vibration* setting), shards fly ahead and the crash of the glass travels with the drone. A pane is a kick you
  fly out, a glass block is a rough blow or a crash. The game rule `propwash:break_impact_scale` (0–300 %, default 100)
  sets how strong this is; 0 leaves only the speed the fracture itself takes. It counts as the pilot breaking the block without a tool: glass drops nothing, a decorated
  pot shatters into its sherds and drops what it held, and spawn protection, adventure mode, the world border,
  protection and claim mods and the frames of freeform gates stop it – the drone bounces off like at a wall, right from
  the first hit, and a notice says why.
  Data packs add blocks to the tag `propwash:drone_breakable`; nothing harder than hardness 1 ever breaks.
- **Part wear:** every crash wears the parts down, depending on how hard and from which side you hit: props after
  about five crashes, motors after about 45, camera and frame after about 110. A broken prop leaves the drone unable
  to fly, a damaged camera tears the picture. The OSD warns (`PROP DMG`), tooltips and the workbench show the
  condition of each part. Hold props of the same type, sneak and right-click the drone to swap every damaged prop;
  other parts are replaced at the workbench. Two damaged props of the same kind become one in the crafting grid or a
  grindstone, with both conditions added up plus 5 % (at most as good as new); broken props count too. Accessories
  do not wear. Crash damage and its strength are world rules
  (`propwash:crash_damage`, `propwash:crash_damage_scale`).
- **Camera angle:** sneak + right-click the drone to open the service menu and tilt its FPV camera (slider, `−`/`+`,
  `↺` back to the frame's default). Each frame's camera mount allows a range – whoops 10–35°, toothpicks 10–40°,
  cinewhoops 0–35°, freestyle 5″ 0–50°, race 15–70°, long range 0–40°, X-Class 0–50° – and the menu shows how much of
  the props then appears in the image. The angle is saved to the drone (and stays with the frame when you take the
  drone apart), works with any goggles and is what spectators and recordings see.

## Paint jobs

Put a frame or a finished drone (in the salvage slot) into the **Drone Workbench** and press *Paint* at the top right.

- Twelve parts can be painted separately: frame, TPU and accents, motor bells, each of the four props, stack boards,
  battery wrap, camera, antenna tips and accessories. Pick a part in the list or click it in the rotatable 3D preview.
- Colors: the 16 dye colors, five finishes (Carbon, Gunmetal, Aluminium, Gold, Copper) and any hex color. *Apply to all
  props*, copy and paste (also `Ctrl`/`Cmd`+`C`/`V`), reset per part or everything, and four presets (Carbon Classic,
  Neon, Race Team, Arctic).
- **Cost in survival:** one matching dye per repainted part; a finish costs its dye (Carbon costs black dye), a hex
  color costs the closest dye you carry. Going back to the original color is free, creative is free.
- The paint job sticks to the frame, so it survives taking the drone apart. Other players see it, and the drone's item
  icon shows it too.

## Racing

1. Place **Race Gates** (small 3×3, medium 4×4, large 6×4 blocks) facing the way you look – on the floor, on a wall or
   under a ceiling (see *Walls and ceilings* below).
2. Take the **Course Tool** and right-click the gates in flight order. Sneak + right-click a gate starts *Adjust gate*,
   where *Leave course* (hold `Alt` to use the mouse in the panel) takes it out again; sneak + right-click the air clears
   the selection, so the next gate you click starts a new course.
3. Fly through gate 1 to start the clock, or arm near gate 1 for a countdown. Your next gate lights up green. A lap
   ends when you pass gate 1 again after all other gates.

Course gates show their number on a sign, glowing arrows on the ground in front of the entry side and red markings on
the back; a gate only counts when you fly through it in that direction. Gate 1 is the checkered start/finish gate.

**Stacking gates:** right-click a placed gate with a gate item to put the new gate on top, up to four high. The stack
becomes one ladder with a shared middle bar and the bottom gate stands on legs. A gate never fits on a narrower one;
look back while stacking to flip the new gate for a zig-zag ladder. Every gate in the stack stays its own course gate,
and when you break one, the gates above it slide down.

**Walls and ceilings:** right-click a wall or a ceiling with a gate to mount it there. On a wall it becomes a **dive
gate** (lying flat with one bar on the wall, flown through from above); a **side gate** (one post on the wall, flown
along the wall) and a **window** (flat on the wall, flown through an opening you cut) are the other wall mounts. Under
a ceiling a gate **hangs** from two straps or lies **flat**, on the floor it stands or lies flat over a pit. While you
aim, a translucent preview shows the gate and an arrow for its flight direction (red: no room). *Rotate gate* (`R`)
turns the gate or flips its flight direction, for example a dive gate into a launch gate flown upwards; with sneak it
switches the mount. Looking at a placed gate while holding a gate or the course tool rotates that gate in place.
`R` is also *Cycle flight mode*: what it does depends on what you hold – a gate or the course tool rotates gates, while
flying with the radio it cycles the flight mode. The controls screen shows no conflict for this pair; rebind either one
and each key works on its own. Only standing, untilted floor gates stack.

**Turning gates freely and any size:** sneak + right-click a placed gate while holding a gate or the course tool to
start *Adjust gate*. No window opens: you keep walking and looking around while a slim panel on the right lists yaw
(compass direction), pitch (tilt) and roll (turn within the opening – 45° makes a diamond) and the width and height of
the opening from 1 to 16 blocks in half steps. The mouse wheel changes the selected row (5° steps, hold `Ctrl` for 1°),
sneak + mouse wheel picks the row, and the gate already shows itself in the new pose (red if it doesn't fit). `Enter`
or right-click applies, `Esc` cancels. Hold `Alt` (`⌥ Option` on a Mac) to use the mouse: drag the sliders or use
*Reset* and *Leave course* in the panel. The panel names the key in its first line, highlighted the first few times,
and a short notice repeats it whenever you start adjusting; `F1` hides the panel for a clear view. The gate stays on its floor, wall or ceiling,
course gates keep their number, and drones bounce off angled gates at the real angle. In the *Race* tab *Rotate gate*
can turn standing and hanging gates in 45°, 15° or 5° steps instead of 90°.

**Flags, slalom poles and dive cubes:** besides gates a course can hold three elements, placed on the ground and appended
with the course tool like gates (a course still starts with a gate). A **slalom pole** is passed on one side – arrows
on the ground show which, a red cross marks the wrong side; when you append a pole right after a pole, the course tool
gives it the other side, so a row of poles becomes a slalom. A **turn flag** is flown around left or right: past it,
around behind it and back on the other side. A **dive cube** (like in MultiGP races) is entered from the top and left
through the glowing side. Sneak + `R` switches side, direction or exit before or after placing, and sneak + right-click
adjusts it in-game like a gate (heading, side or exit, height, how far beside a pole or flag still counts, cube opening
1–16).
An element only counts when flown the right way; the HUD tells you about a wrong side, the race line leads through
them, and splits, ghosts, leaderboards and the server check work as with gates.

**Freeform gates:** a freeform gate has no frame – the blocks around an opening are its border. Right-click a block at
the edge of a hallway, cave mouth, window or shaft and the opening itself becomes the gate, in any shape up to 20 × 20
blocks (world rule *Freeform gate: largest opening*, 1–64). While you aim, an outline shows the size and the flight
direction, or why it does not fit (not closed all around, too large); `R` turns the direction around. A glowing LED
outline marks the opening, also at night and in caves, and a lap only counts when you fly through the stored opening
the right way. If the blocks change later, the gate keeps its shape and shows the gaps in red; sneak + right-click
starts *Adjust freeform gate* to turn it around (mouse wheel or `R`, then `Enter`), rescan the opening or take it out of
the course. Freeform gates join
courses like gates and may be the start.

**Redstone:** every gate and freeform gate gives a redstone pulse when a drone flies through it the right way – in free
flight and in races. The pulse goes into the block the gate is mounted on, like a button pressed on that block: the
block under a standing gate (or under the bottom gate of a stack), the ceiling block of a hanging gate, the wall block
behind a wall gate, for a gate lying over a pit or under a hole in the ceiling the rim block under (or above) the
middle of its bottom bar, and for a freeform gate the rim block under the middle of the opening's lowest row (for a
shaft or pit: the rim on its north side). Wire redstone dust, lamps, pistons, doors or a note block to that block – or use one
of them as the block itself. A pass during the pulse keeps it going. On the *Redstone* page of *Adjust gate* (sneak +
scroll past the last row, or the *Redstone »* button with the free mouse) you switch the signal off, set the pulse
from 2 to 100 ticks (default 10 ticks = 0.5 s) or choose *Races only*: then only passes in a running race on the
gate's course count, while the start/finish gate still signals on every pass. A red outline marks that block, with a thicker
frame on the face the power goes through. As with a button, slabs, glass and other blocks that don't conduct redstone pass nothing on.

**Gate poles:** a gate pole is a single side of a gate – same tube, connectors and LED strips. Stand it on the ground,
let it stick out of a wall or hang it from a ceiling; right-click a standing gate with it and it sits on the nearest top
corner like the three-way connector of a MultiGP gate, right-click a pole and it grows into a tower (up to four). Length
1–16 blocks, LEDs in 16 colors (sneak + `R`, sneak + right-click for *Gate pole settings*, or a dye). Poles are obstacles and markers, not course
elements: they don't count in laps, and the course tool leaves them out.

- **Standing starts:** press *Reset* to put the drone back on its home point at the start, then arm near the start gate
  (or always arm there with *Start when arming*) to line up.
  Other pilots at the start can join; the start lights count down 3-2-1-GO for everyone at the same moment, at the
  latest 10 seconds after the first pilot lined up. Flying through the start gate before GO adds 3 seconds to lap 1.
  With several pilots the finishing order is announced in chat.
- **Race line:** in a running race a glowing line leads through the next gates (1 to 8 gates ahead).
- **Ghosts:** your personal best lap per course and drone class flies along as a translucent blue copy of the drone that flew it, the server record of your
  drone class as a gold one. Drone items also show the exact drone stored on them. Splits at every gate show the gap to the ghost.
- **Leaderboards:** stored in the world per course and drone class (top 10 with pilot, time and date). Only laps the
  server verified count; laps with altitude or position hold, GPS Rescue or modified physics don't. Open the
  leaderboard window with a right-click into the air with the Course Tool or from the Race tab.

The **Race** tab sets your default laps (or endless practice), start when arming, start lights, the race line, both
ghosts and race messages.

**Laps per course and editing a course:** every course has its own lap count and a name. A new course takes your
default laps from the Race tab; after that a race on it runs over the course's laps for every pilot. To change a
course, right-click the air with the Course Tool (or open the leaderboard from the Race tab) and click *Edit course*:

- **Name** and **Laps** (0 = endless practice) – changing them keeps all times.
- The list shows every gate and element in flight order: the arrows move one earlier or later, *Start* moves start and
  finish to that gate (the order stays, counting begins there), `×` takes it out of the course – the gate itself stays
  in the world. Start and finish is always a gate or freeform gate.
- *Add gates* points your Course Tool at this course; every gate or element you right-click afterwards is appended.
- *Delete course* removes the course with its leaderboard and ghosts; the gates stay.

Changing the layout – order, start, adding, removing, moving or resizing a gate – starts a new leaderboard: the old
times and ghosts are archived (`/propwash race archive <course>`) and never mix with the new layout. Lap times count per
course and drone class; race times additionally per lap count, so a 3-lap and a 5-lap race are ranked separately.

Only the pilot who created a course and operators may change it; a course nobody owns (from older worlds, or one you
found in the world) can be changed by anyone standing near it, and only operators delete found courses. Everyone else
sees the same window as *View course*. On servers there are also `/propwash race laps <laps> <course>` and
`/propwash race name <course> [name]`.

**Race courses in the world:** now and then you find a ready-made course: *Field Slalom* (plains, sunflower plains,
meadows), *Canyon Run* (badlands, desert), *Forest Weave* (forests, birch forests, taiga) and the rarer *Drone Arena*
(plains, savanna, snowy plains). Their gates become a course by themselves and belong to nobody. A waxed sign at the
start shows the course name, number, record time and holder, and a chest holds a few parts and batteries. Find one with
`/locate structure #propwash:world_courses`.

**Bandos:** rarely, an abandoned building stands in the world, built differently every time and matching its biome –
barns and warehouse skeletons in plains and forests, a rusty factory, water tower or refinery in deserts and badlands,
a collapsed hall in the snow, an overgrown concrete frame in the jungle, a derelict pier or an oil rig at the coast,
a mine head in the mountains and a sunken warehouse in swamps. No gates, no timing: fly through doors, windows,
holes in the roof, along catwalks and between beams. Some hide a chest with (often used) Propwash parts. Find the
nearest with `/locate structure #propwash:bandos` or a kind with e.g. `/locate structure propwash:bando/desert`. Bandos
follow the world's *Generate Structures* setting and can be changed or switched off with a data pack like vanilla
structures.

## Freestyle tricks

While you fly, Propwash recognizes 59 freestyle tricks in nine families: flips and rolls (single, double, triple,
segmented), snap rolls, corkscrews (barrel rolls), Vanny rolls, rewinds and flip rewinds, Juicy Flicks, Rubik's Cubes,
power loops with their power flip, power roll, inverted 360 and side loop variants, Maverick loops, Baranis, Immelmanns and
Immelmatts, hammerheads, Matty flips, Anti, Half and Power Mattys, split-S, 540 split-S and splitbacks, yaw spins upright
and upside down, coin drops, Trippy Spins, McTwists, inverted hangs, knife edges, reverse knife edges and wall rides, dives,
backwards dives, tornados, orbits, gaps and proximity passes – and the new school: ejects and Sbang combos, Funky Splits,
Shinobi flips, Guillotines and Side Trippies, plus inverted flight, inverted hovers, tic-tocs, 3D flips and rolls and 3D
power loops in 3D mode. Sloppy, slow
and fast versions count too, and tricks flown straight into each other are counted one by one. Each trick scores points;
the next trick within 3 seconds raises the combo multiplier from ×1.5 up to ×4, tricks linked without settling earn a 25 %
chain bonus, repeating the same trick is worth less, and flying close to blocks or recovering cleanly adds a style bonus.
A crash or disarm loses the combo bonus. The *Tricks* OSD element shows each trick (a whole chain in one line), the
post-flight stats show trick points and the best combo. The **Freestyle** tab switches detection on, off or to automatic
(on for freestyle, whoop and cinewhoop builds), lists every trick by family with its points, finds tricks by name or scene
nickname (barrel roll, stall turn, gigatine) and explains how to fly them; the
Flight School has a lesson on them. Nothing is scored during a race, GPS Rescue or after a crash until the drone is
steady again.

## Survival mode

Everything except the Creative FPV Goggles and the Creative Flight Controller can be crafted at a normal crafting table.

- **Crafting materials:** Carbon Plate (coal and string), Copper Coil (copper and iron), Circuit Board (copper,
  redstone and glass), LiPo Cell (dried kelp, copper, redstone and lapis) and HD Video Chip (quartz, amethyst, gold
  and a circuit board).
- **Tiers:** the whole whoop, the radio, the analog goggles, the workbench and the charger need only early-game
  materials. Gold brings the 30×30 stack, the race frame and the 6S 1100 mAh pack; diamonds, quartz, amethyst,
  glowstone and echo shards go into race motors, digital video and the big 6S packs.
- **Recipe book:** recipes unlock as you pick up their ingredients. Owning a Drone Workbench unlocks the whole starter
  set; gates and the course tool appear once you own a drone. Search the recipe book for "propwash".
- **Chest loot:** village houses, toolsmiths and cartographers, mineshafts, dungeons, desert and jungle temples,
  pillager outposts, strongholds, shipwrecks, trial chambers, vaults, ancient cities and end cities can hold parts,
  batteries and materials. The rarest high-end parts only appear in strongholds, vaults, ancient cities and end
  cities, and now and then a chest holds a ready-to-fly whoop. Batteries and drones from chests come with 40 %
  storage charge, so charge them first.

Good first steps: build the Drone Workbench and the Battery Charger, then a whoop with a radio and analog goggles.

## Flight School

Nineteen animated 3D lessons (twenty in creative mode) with a small diorama, callouts that point at the right spot, your real key bindings and a
timeline you can pause and step through:

Building a drone at the workbench · Binding the remote, wearing goggles · FPV and line-of-sight flight · Swapping and charging
batteries · Gates, courses and racing · Rotating gates freely · Flags, slalom and dive cubes · Freestyle tricks · Video systems and goggles · Action cam: recording videos · Gripper: collecting items · Configurator: rates, PID, modes · Home point,
return home, failsafe · Altitude and position hold · 3D mode · Crashes, range and signal · Swapping props and damage · Survival:
crafting parts · Bandos: abandoned spots · in creative mode: Creative drone and drone editor

Open it by holding `M` over any Propwash item in an inventory or creative screen, by pressing `M` in the world for the
overview, or from the **Flight School** tab of the configurator.

## Configurator

Press `K` (also from the mod list, even on the title screen: *Config* on NeoForge, the Mod Menu button on Fabric).
Tabs: Overview, Flight School, Configuration, PID Tuning, Rates, Receiver, Modes, Keys, Video, Video Channel, OSD, LED
Strip, Action Cam, Race, Freestyle, GPS Rescue, World Rules, Display, Audio, Server – and Drone Editor for drones with the
Creative Flight Controller.

- The footer tells you where a change goes: PID Tuning and Rates are saved to the drone you fly or hold, the craft name
  too; all other tabs are game settings for every drone.
- **Keys:** every Propwash key in one list; click a key and press the new one, conflicts are marked.
- **Modes:** every switch assignment can be inverted (column *Inv.*). Like Betaflight, a drone whose arm switch becomes
  active through a changed assignment or a newly connected radio only arms after the switch is turned off and on.
- **LED Strip** (saved to the drone): mode, color, brightness and headlight, with an animated preview.
- **Action Cam:** resolution, frame rate, stabilization, sound, record on arm and the *Recordings* window.
- **Drone Editor** (saved to the drone, creative drones only): every value of the flight model with its range, key
  figures and notes, base parts, templates (installed parts, presets, add-on presets, your own) and a `PWD1-…` share
  code. Right-click a value to go back to the base or automatic value. Changes need creative mode; in survival the tab is
  read-only. Creative drones don't count for leaderboards. Details: `docs/design/creative-drone.md`.

- **World Rules:** every Propwash game rule of the world in one list with explanations (see *World rules* below). The
  host in singleplayer and everyone allowed to use `/gamerule` (operators, or admins through a permission mod) can
  change them, everyone else sees what applies. The camera angle is set on each
  drone in its service menu.
- **Display:** drone size, prop blur, arm LEDs and the **Propwash dust** section (*Stir up dust* on/off, *Dust amount*).
  Dust also follows Minecraft's Particles setting and is off at *Minimal*.
- **GPS Rescue:** home point mode (first arm, every arm, at the pilot, manual), return altitude, speed, obstacle
  avoidance, auto land, stick override and failsafe.
- **OSD:** elements, presets (Minimal, Standard, Full/Telemetry, Race), opacity, size, colors and outline.

Client settings are stored in `config/propwash/`.

## World rules

The physics and gameplay rules are game rules of the world: they apply to every player, are saved with the world and
can be set in the *World Rules* tab, with `/gamerule propwash:<rule> <value>` or in the game rules screen when you create
a world. Changed physics rules mean flights do not count for leaderboards.

At the top of the tab, three presets set every rule with one click (host and whoever may use `/gamerule`; also
`/propwash rules preset <realistic|training|arcade>`): *Realistic* is all defaults, *Training* turns off crash damage,
prop strikes, battery drain, voltage sag and hardware limits but keeps the handling (mild wind included), *Arcade* adds
any-part-fits, no wind, turbulence or prop wash, a 300 % crash tolerance without disarming, infinite range, waterproof
drones and drones that fly through mobs. Single rules stay adjustable afterwards; the tab shows which preset the world matches.

| Rule | Default | Values |
|---|---|---|
| `propwash:gravity_scale` | 100 | gravity in percent of 9.81 m/s², 0–1000 in 1 % steps |
| `propwash:battery_scale` | 100 | battery life in percent, 200 = twice as long, 0 = infinite (up to 1000) |
| `propwash:wind` | 1 | 0 off, 1 mild (weather and height), 2 windy, 3 storm |
| `propwash:turbulence` | true | gusts and the shaking in the drone's own downwash |
| `propwash:prop_wash`, `propwash:ground_effect`, `propwash:battery_sag` | true | physics switches |
| `propwash:hardware_limits` | true | motor overheating and burnt ESCs on too many cells |
| `propwash:crash_damage` | true | crashes, heat and burnt ESCs wear the parts |
| `propwash:crash_damage_scale` | 100 | wear strength in percent, 25–300 |
| `propwash:crash_tolerance` | 100 | impact speed that counts as a crash, in percent, 50–400 |
| `propwash:crash_disarm`, `propwash:prop_strikes` | true | crash behaviour |
| `propwash:infinite_range_analog`, `propwash:infinite_range_digital` | false | picture and radio link never drop for drones with that video system |
| `propwash:max_video_channels` | 0 | how many video transmitters may be on air at once, 0 = unlimited |
| `propwash:drone_hits_entities` | true | drones bounce off mobs and players and hurt them |
| `propwash:drones_break_blocks` | false | fast drones smash glass, glass panes and decorated pots (block tag `propwash:drone_breakable`) like the pilot breaking them |
| `propwash:break_impact_scale` | 100 | how clearly a drone feels going through glass, in percent (0–300): speed lost to the shards, the kick that knocks it off its attitude and shards in the front props; 0 leaves only the fracture energy. Does not change whether a block breaks or when the drone crashes |
| `propwash:allow_3d_mode` | true | pilots may fly 3D mode once they unlocked it; false keeps it off for everyone, e.g. on race servers |
| `propwash:part_compatibility` | true | *Parts must fit*: false lets any part go with any other (mounts, prop size, battery for the frame, accessory mounts); the drone flies with its parts' real data and doesn't count for leaderboards, too many cells still burn the ESC while `hardware_limits` is on |
| `propwash:signal_repeaters` | 4 | how many signal repeaters may relay video and control in a row, 0–4 |
| `propwash:autonomous_rescue` | true | a running GPS rescue keeps flying home and lands when the pilot leaves |
| `propwash:freeform_gate_max_size` | 20 | largest opening of a freeform gate in blocks per direction, 1–64 |
| `propwash:drones_generate_chunks` | true | drones create new terrain past the generated world; false brakes them gently at its edge |
| `propwash:save_assisted_laps` | false | laps flown with flight aids are saved in their own *Assisted* leaderboard |
| `propwash:drone_trails` | false | flying drones draw a thin, fading trail in their color; every player can hide it in the *Display* tab |
| `propwash:invulnerable_pilots` | false | whoever is flying a drone takes no damage (for events); off = the pilot's body stays a normal player |
| `propwash:waterproof_drones` | false | every drone flies in water as if it had the waterproof coating (on in *Arcade*) |
| `propwash:allow_reset` | true | pilots may put their drone back on its home point with *Reset*; false refuses it with a message |

**Zones** give part of a world its own physics rules (gravity, wind, battery, crash settings and the other physics
rules above): a practice field with infinite battery, a zero-gravity passage in a race. Create them in the *World rules*
tab (zone editor) or with `/propwash zone`, same permission as the world rules. A zone is either *race-legal* (laps in it
count normally) or counts as assisted; gravity and wind fade in and out over the zone's transition time (0–5 s), and
that fade still counts as the zone. Up to 128 zones per dimension.

## Servers and commands

Install Propwash FPV on the server and on every client. Client and server need the same Propwash network protocol, and
every player needs the server's Propwash add-ons (with parts) in the same versions. Add-ons that only a player has are
fine: they stay inactive while that player is on the server, and a short notice says so.

**Can't join? "Add-ons don't match this server"** lists every add-on that differs, before Fabric or NeoForge reject the
connection: its name and mod id, the server's and your version, what to do (*Install Propwash: Just More Parts 1.1.0*,
*Update … to …*) and a link to its page (Minecraft asks before opening it). Add-ons only you have are listed under
*Also different* – they don't stop you from joining. A different Propwash version shows up as its own line with both versions and network protocols. Parts that
belong to no known add-on are listed as *Unknown parts*. *Copy details* puts the overview on your clipboard for the
server owner; the server log contains the same summary. On NeoForge, a different Propwash network protocol is still
reported by NeoForge's own screen (*Channel of mod "Propwash FPV" …* with both protocol numbers).

- While a player flies, the server loads and streams chunks around the drone instead of the pilot's body, within a
  shared chunk budget. Every pilot's drone generates new terrain ahead of it by default; servers can restrict this to
  operators (`generateChunksPermissionLevel`) or to already generated chunks (`onlyGeneratedChunks`).
- Rules live in `config/propwash/server.json`: permission levels for flying, unlimited range and chunk generation,
  maximum distance when range is limited, speed limit for movement validation, view distance around the drone, chunk
  budget, flying in spawn protection, battery charge times, spectating (`allowSpectating`) and race courses in newly
  generated terrain (`worldCourses`). Physics, battery, wind, crash damage, range and video channels are world rules
  (see above).
- `/propwash rules` (server and world rules), `/propwash rules preset <name>`, `/propwash range [player]`, `/propwash pilots`
  and `/propwash reload` (operators).
- `/propwash race courses | leaderboard | laps | reset | delete | archive` for courses and leaderboards.
- `/propwash zone list | info <name>` for everyone; `create <name> box <from> <to>` or `cylinder <center> <radius>
  <height>`, `set`/`reset <name> <rule>`, `priority`, `racelegal`, `transition`, `rename`, `move` and `delete` for whoever
  may change the world rules.
- Drones keep their GPS Rescue home point in their entity data: `/data get entity <drone> HomePoint`, and admins move it
  with `/data modify … HomePoint set value {x:…,y:…,z:…}`.

## Add-ons (Parts API)

From 0.4.0 on, other mods can add drone parts and features without changing Propwash. The first one is *Propwash:
Just More Parts* with many real-world motors, props, batteries, stacks, cameras, new frame shapes and share codes for
builds, paint jobs, tunes and OSD layouts.

- Install an add-on on the server and on every client, in the same version. When you join, Propwash compares the
  add-ons and their parts and tells you per add-on what is missing or different and what to do (see *Servers and
  commands*). An add-on only you have doesn't block you: on that server its parts, presets, tabs and lessons stay
  hidden until you leave. Add-on authors: name, version and link come from your mod metadata
  (`docs/api/addon-guide.md`, 1.3); how to stay optional on servers is in 17.1.
- Add-on parts behave like built-in ones: they have items, recipes from the add-on, Shift tooltips, workbench slots,
  build analysis, paint, wear and their own look (or a generated one), and they fly with their real data.
- Every add-on gets its own creative tab right after the **Propwash FPV** tab, named *Propwash: <add-on>* (for example
  *Propwash: Just More Parts*), with its presets and parts. The Propwash tab itself keeps the built-in items.
- If you remove an add-on, drones built with its parts are kept: the missing parts show up as *Unknown part* and the
  drone cannot be armed until the add-on is back. Nothing is lost when you reinstall it.
- Share codes live in the add-on. Importing a build into the workbench takes the parts from your inventory in survival
  (and dyes for the paint job); in creative everything is free. Switch and key assignments, radio profiles, battery
  charge, wear and the video channel are never part of a code.

Developers: the add-on guide, the API reference and the JSON parts format are at
[propwashfpv.com/docs/api](https://propwashfpv.com/docs/api/). In this repository the step-by-step guide (German) is
[`docs/api/addon-guide.md`](docs/api/addon-guide.md), the full contract
[`docs/design/parts-api.md`](docs/design/parts-api.md). The API has its own semantic version, independent of the mod version: Propwash 0.5.0 and 0.5.1 ship Parts API 1.5.0, Propwash 0.5.2 ships Parts API 1.6.0.
`./gradlew publishToMavenLocal` publishes `de.lardtom:propwash-common-26.3`, `propwash-fabric-26.3` and
`propwash-neoforge-26.3` (with sources and Javadoc) to your local Maven repository for add-on development.

## Requirements

| | |
|---|---|
| Minecraft | 26.3 |
| Fabric | Fabric Loader 0.19.5+ and Fabric API 0.161.0+26.3 |
| NeoForge | 26.3.0.10-beta or newer |
| Java | 25 |

Required on both client and server.

## Known limitations

- Only Minecraft 26.3.
- The action cam records the FPV camera view only while you fly FPV, at up to 1440p60; its sound track contains only
  the motor sound of the recording drone.
- With an active shader pack (Iris/Oculus) or another renderer, the video effect falls back to a simpler overlay.
- Outside creative, the radio link reaches about 1,200 blocks; servers can limit it further.
- Other players see the sticks of your in-game radio in the center position; only the pilot sees them move.
- Spectators see the pilot's OSD approximately: flight mode, sticks and radio link are not transmitted. There is no
  monitor block yet.
- Painted parts are tinted grayscale textures, so very bright colors look slightly darker (white shows as light gray),
  and printing on a painted part takes on the paint color.
- A radio has to run in USB joystick (HID) mode; the mod does not talk to receivers or radio protocols directly.
- One pilot per drone.

## Building from source

You need a Java 25 JDK (point `JAVA_HOME` to it if your default Java is older):

```sh
./gradlew build
```

The jars end up in `fabric/build/libs/` and `neoforge/build/libs/`. Development runs, the self-test and the smoke tests
are listed in [`docs/FOUNDATION.md`](docs/FOUNDATION.md) (sections 9 and 13). Developer documentation (in German):

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): modules, packages and data flow
- [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md): code rules
- [`docs/FOUNDATION.md`](docs/FOUNDATION.md): registration, networking, events, configs, self-test and contracts
- [`docs/testing.md`](docs/testing.md): filtered self-test, list of step prefixes, parallel runs and build cache
- [`docs/ASSETS.md`](docs/ASSETS.md): model, texture and asset contracts
- [`docs/design/`](docs/design/): flight model, gameplay loop, survival mode, propwash dust and the Parts API
- [`docs/api/addon-guide.md`](docs/api/addon-guide.md): writing add-ons with the Parts API (step by step)
- [`docs/api/changes.md`](docs/api/changes.md): what changes for add-ons in each release (API, data formats, network protocol)
- [`CHANGELOG.md`](CHANGELOG.md): short player-facing changes per release; technical details in
  [`docs/RELEASE-NOTES.md`](docs/RELEASE-NOTES.md)

## Contributing

Pull requests are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) first; every pull request needs the short
contributor agreement in [`CLA.md`](CLA.md).

## License

From 0.5.0 on, the source code of Propwash FPV is published under the
[PolyForm Shield License 1.0.0](https://polyformproject.org/licenses/shield/1.0.0), the same license Sodium uses. In
plain words:

- **Allowed:** reading and inspecting the code, compiling it yourself, including Propwash in modpacks, and
  contributing through pull requests with a quick contributor agreement.
- **Not allowed:** re-uploading Propwash, publishing forks as alternative downloads, and building competing mods from
  the codebase.
- **MIT:** the add-on API (`de.lardtom.propwash.api`), the example add-on and the documentation are under the MIT
  License, so you can use them freely for your own add-ons. [`LICENSE-MIT`](LICENSE-MIT) lists the exact files.
- **Exit clause:** if there are no new releases and no maintainer activity for 12 months, the code automatically
  becomes available under the MIT License as well.
- Propwash 0.4.0 and older stay All Rights Reserved.

The license texts are binding: [`LICENSE`](LICENSE), [`LICENSE-MIT`](LICENSE-MIT) and [`NOTICE`](NOTICE). The Gradle
setup started from the [MultiLoader Template](https://github.com/jaredlll08/MultiLoader-Template).

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

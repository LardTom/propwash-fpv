# Propwash FPV

**Real FPV drone flying in Minecraft.** Build a quadcopter from real-world parts, put on your goggles and fly it in
first person with your own RC transmitter, a gamepad or the keyboard. Race through gates, freestyle over your builds,
or just cruise the world from the air.

Propwash has its own flight model and a Betaflight-style flight controller, so drones behave like the real thing:
punchy 5″ freestylers, floaty whoops, efficient long-range cruisers. Everything is playable in survival, and an
animated Flight School teaches you the whole mod inside the game.

---

## New in 0.5.0

- **Flight training:** 20 hands-on lessons in five chapters – from arming and hovering to gates, flips and a final
  exam – flown with your own drone through a holographic course that only you see, with stars, best times and split
  times; works in survival and on servers
- **World rules:** gravity, battery life, wind, turbulence, crash damage, prop strikes, infinite range, a video
  channel limit and whether parts must fit together are game rules for the whole world, with their own *World rules* tab (the host and operators change
  them, everyone else sees them) and one-click presets *Realistic*, *Training* and *Arcade*
- **Freestyle tricks that understand real flying:** 59 tricks in nine families, 43 of them new – from the Juicy Flick,
  Rubik's Cube and McTwist to Matty variants, power flips, hammerheads, new-school Sbang combos, Guillotines and Side
  Trippies, plus inverted flight, hovers, tic-tocs and 3D flips in 3D mode – recognized even when sloppy, fast or chained,
  with chain bonus and a *Freestyle tricks* lesson
- **Drones hit mobs and players:** they bounce off and hurt them by the force of the impact, up to five hearts and
  players only with PvP – switchable as a world rule
- **Gripper:** a servo claw for the bottom mount that picks up dropped items while you hover low, carries them and
  drops them where you want
- **Action Cam (Flashback):** records your flight as a camera path for the replay mod Flashback (Fabric only, separate
  download), one ready-to-render scene per flight
- **Bandos:** rare abandoned barns, factories, water towers, piers, oil rigs and mine heads to fly through, built
  differently every time, some with a chest of used parts
- **Animated receiver wizard:** the Propwash radio shows every step – sticks, switches and buttons – next to the live
  bars, highlights your arm switch and asks instead of guessing when the throttle direction is unclear
- **Max arm angle** per drone like Betaflight's small_angle: 180° arms in any attitude, even in the air
- **Binding made simple:** click the drone with your remote and its receiver remembers you – your remote connects
  right away after the workbench, picking up and placing. Goggles need no binding: wear ones that match the drone's
  video system for FPV, take them off for line of sight
- **Custom commands:** put your own Minecraft commands like `/time set night` on a radio switch, button or key – one
  for switching on, one for switching off; they run as if typed in chat, with your own permissions
- **GPS rescue without pilot:** stop flying, put the remote away or leave the game during the return to home and the
  drone flies home on its own and lands – now also around trees and buildings, out of halls through the door and
  never into water or lava
- **Gates turn freely:** diagonal, tilted and diamond gates and any opening from 1 × 1 to 16 × 16 blocks, adjusted
  in-game with the mouse wheel and a live preview while you keep walking and looking around; drones bounce off them at the real angle, and *Rotate gate* can turn in
  45°, 15° or 5° steps
- **Flags, slalom and dive cubes:** turn flags flown left or right around, slalom poles the course tool alternates for
  you and MultiGP-style dive cubes – in from the top, out through the side you choose; each counts only when flown right
- **Gate poles:** a single gate side with LEDs in 16 colors – on the ground, out of a wall, from the ceiling or, like at
  MultiGP, on the corner of a gate, up to four on top of each other
- **Freeform gates:** right-click the edge of a hallway, cave mouth, window or shaft and the opening itself becomes a
  race gate – no frame, just a glowing outline along the blocks, any shape up to 20 × 20 blocks (world rule)
- **4:3 image:** a 4:3 image format like analog goggles – black bars left and right, more view above and below, the
  OSD inside the 4:3 image; Auto picks 4:3 for analog and 16:9 for digital
- **Documentation** at [propwashfpv.com/docs](https://propwashfpv.com/docs/), and the source code is now available
  under the PolyForm Shield License (add-on API under MIT)

## New in 0.4.0

- **Add-ons for new parts:** a versioned Parts API lets other mods add frames, stacks, motors, props, batteries, video
  units and accessories that work everywhere – workbench, analysis, flight model, paint, wear, tooltips, Flight School
  and saves. First add-on: *Propwash: Just More Parts*
- **New frame shapes from add-ons:** deadcats with the props out of view, stretched and squashed X, H-frames, ducts and
  X-Class frames with props up to 13″, flown with a mixer for the real arm layout
- **Motor heat and ESC voltage:** motors throttle back and wear when they run too hot, and a battery above the ESC's
  voltage range burns it out when you arm; the workbench shows the sustained temperature and how much of the camera
  view the props cover, and your own props now show up in the FPV image
- **Share codes (in add-ons):** export and import builds, paint jobs, flight tunes and OSD layouts – checked by the
  server, paid with parts and dyes in survival
- **Realistic flight times and battery:** hover, cruise, mixed and aggressive flight times from the energy a real pack
  delivers (motor and ESC losses, video power, usable capacity, landing voltage); OSD, HUD and battery warnings follow
  the same consumption, so the pack runs out when a real one would
- **Own creative tab per add-on** right after the Propwash FPV tab, e.g. *Propwash: Just More Parts*
- **Drones as wall trophies:** in item frames drones, frames and props hang flat on the wall, camera side out and nose
  up, and turn with the frame; on shelves they tilt towards you
- **Field of view from the camera, camera angle on the drone:** like on real quads the FPV field of view comes from the
  camera in the video unit (analog 125°, digital lite 104°, HD 130°), and each drone has its own camera angle, set in
  its service menu within the range of the frame's camera mount – for every goggles, spectators and recordings
- **"Props in the camera view" matches the FPV image** and follows the frame and its camera mount (race frames show more
  prop than a true X, deadcats none), the built-in cinewhoop reaches a realistic 89 km/h, and the
  workbench stays readable in every language and GUI scale
- **Fixes:** no more x-ray through water behind spinning props, no more periodic OSD/HUD freezes with font resource
  packs
- Drones keep add-on parts as *Unknown part* when an add-on is removed and are complete again when it comes back

## New in 0.3.1

- **Stackable gates:** right-click a gate with a gate to stack up to four into a ladder with shared middle bars, like
  real drone ladders – each stays its own course gate, and the Drone Arena course now has one
- **Two-handed radio in first person:** the radio sits lower and smaller in view, the forearms come up from the bottom
  corners, and the off-hand item is hidden while you hold the radio

## New in 0.3.0

- **Paint jobs:** color every part of your drone at the workbench – frame, motors, each prop, stack, battery and more –
  with dyes, five metal and carbon finishes, any hex color and presets
- **Race courses in the world:** four rare, ready-to-fly courses generate in plains, badlands, deserts, forests and
  more, with numbered gates, a record sign and a loot chest
- **Video channels and spectating:** every transmitter sends on a real 5.8 GHz or digital channel; press `F7` to watch
  other pilots' camera feeds through your goggles
- **Ghost races and leaderboards:** race your personal best ghost and the server record ghost, leaderboards per drone
  class with a new window, standing starts with start lights for several pilots
- **Race line and gate markings:** a glowing line through the next gates, a checkered start/finish gate and arrows
  showing the flight direction
- **Freestyle tricks:** flips, rolls, power loops, Matty flips and more are recognized with points and combos in the
  OSD

## New in 0.2.0

- **Accessories:** mount an action cam on top of the frame and an LED strip under the nose – both change how the
  drone flies
- **Action cams that really record** your flight as an MP4 video, optionally with motor sound
- **LED strips** in 11 colors with Betaflight-style modes and an FPV headlight for caves; optional LambDynamicLights
  support
- **Part wear:** crashes wear props, motors and more down; swap props in the field or repair them in pairs
- Realistic top speeds, a steady FPV picture at high speed and a video link that breaks up in real stages
- A new radio is detected and set up once, every switch can be inverted, and arming is safe like in Betaflight
- Two new Flight School lessons

---

## Features

### Flight model and flight controller
- Rigid-body physics with motor, ESC and battery simulation, rotor aerodynamics, ground effect, prop wash, voltage sag,
  wind and gusts
- Every drone is derived from its parts: weight, thrust, hover throttle, top speed and flight time come from the build
- Betaflight and Actual rates, PID with feed-forward, I-term relax, anti-gravity, TPA and airmode
- Acro, Angle and Horizon modes, arming checks, failsafe and flip-upright after a crash
- 3D mode with reversible motors: unlock it in the *Modes* tab, put it on a switch – throttle center is zero thrust, below
  it the props spin backwards, so you fly upside down and take off upside down after a crash
- **GPS Rescue:** return to home with obstacle avoidance and path planning, automatic landing on a safe spot,
  failsafe on signal loss – and it keeps flying home when you leave

### Build your own drones
- Five frames from a 65 mm whoop through a 3″ cinewhoop and 5″ freestyle and race frames to a 7″ long range
- Flight controllers, six motors, five props, batteries and video units, each with real specs
- Real choices within a frame: light 1404 or punchy 2004 motors on the cinewhoop, quad- or tri-blades on the whoop,
  a 30×30 stack or a light 20×20 AIO on race and long-range frames, 2807 or 2207 motors on the 7″
- Name your drone in the configurator, with a name tag or an anvil; the name shows in the OSD and on radio and goggles
- The **Drone Workbench** puts it all together with a live 3D preview, weight, thrust-to-weight ratio, hover
  throttle, top speed and flight time, and warns you about parts that don't fit
- Five ready-to-fly presets in creative: Whoop, Cinewhoop, Freestyle, Racer and Long Range

### Fly with your real radio
- EdgeTX/OpenTX radios over USB, gamepads and a keyboard fallback
- Receiver wizard for channels, calibration and switches; free switch assignment for arm, flight modes, flip
  upright, rescue, home point, beeper, race restart and action cam recording
- The in-game **FPV Remote** moves its sticks with yours

### FPV or line of sight
- Wear matching goggles and see through the drone's camera, with a Betaflight-like OSD (43 elements, presets,
  drag-and-drop editor)
- No goggles? Fly line of sight: your view follows the drone and a compact radio HUD shows battery, signal and mode

### Analog and digital video
- **Analog:** almost no latency; the picture breaks up in stages with distance and obstacles – torn lines, fading
  color, snow with sync jumps and finally full static
- **Digital:** a clean HD picture with a little more latency, then blur, block artifacts, stutter, freezes and a hard
  cut to "NO SIGNAL" – never analog snow
- Signal model with range per video unit and damping by blocks, leaves and water
- Image format 16:9 or 4:3 (black bars like 4:3 goggles, same horizontal field of view, OSD inside the image) or Auto
  by video system
- **Creative goggles** receive every system and let you set latency, range and noise freely; in creative mode or with
  these goggles the radio range has no fixed limit either

### Batteries and charger
- Every battery is its own item with its own charge and voltage
- Each frame takes only the packs that suit it
- Swap packs with sneak + right-click; recharge them in the **Battery Charger** (four bays, faster with redstone)

### Action cams that really record
- Mount an action cam on top of the frame and record your flight as a real **MP4 video** (H.264, 30 or 60 fps,
  720p to 1440p, optionally with the motor sound as AAC), saved in `propwash/recordings` – the clean camera image
  without OSD and without radio breakup
- **Action Cam (Light)**: a bare FPV cam, 42 g, powered by the flight battery, raw image, a bit noisy in the dark
- **Action Cam (Heavy)**: a full action cam in its housing, 150 g, with stabilization and optional horizon levelling,
  its own battery (about 90 min at 1080p) that charges over USB from the flight pack while the drone is parked
- **Action Cam (Flashback)**: records your flight as a camera path for the replay mod
  [Flashback](https://modrinth.com/mod/flashback) (Fabric only, separate download) – each flight becomes a scene in
  Flashback's editor, ready to render as a cinematic video; without Flashback it only shows a hint
- The extra weight is felt in flight: more mass and more inertia, the heavy cam sits high up and makes the drone
  slower to roll and flip
- Record with **F9** (rebindable), a switch or button on your radio, or automatically when you arm; a subtle **REC**
  indicator in the OSD shows the time, encoding runs in the background so the game stays smooth
- The **Recordings** window lists every clip with thumbnail, date, length and size – play it in your system player,
  delete it or open the folder

### LED strips and headlight
- The LED strip glows in 11 colors with the modes steady, blink, arm status, battery warning and rainbow, stored on
  the drone and visible to every player
- In FPV it lights the way ahead in the dark, so caves become flyable
- Switch it off and on in flight with a switch on your radio (*LEDs off*, like Betaflight's LED_LOW) or `F8`
- With **LambDynamicLights** installed (optional), your drone becomes a real light source in the world

### Part wear
- Every crash wears the parts down, depending on how hard you hit: props go first, motors, camera and frame last much
  longer, and a really hard hit puffs up the battery
- Chipped props cost thrust and shake the picture, a broken prop keeps you on the ground; the OSD warns you
- Swap props in the field with sneak + right-click, replace other parts at the workbench; accessories never wear
- Repair props like tools: two damaged props of the same kind in the crafting grid or a grindstone become one, with
  both conditions added up plus 5 %
- Crash damage and its strength are world rules, so a world or server can turn them down or off

### Racing
- Race gates in three sizes, a course tool to link them into a track
- Stack gates up to four high into ladders and double gates, each one its own course gate
- Lap timing with splits, standing starts with start lights for several pilots and a 3-second false-start penalty
- Ghosts of your personal best and of the server record per drone class, with the gap shown at every gate
- Leaderboards per course and drone class in their own window; only server-verified laps count
- A glowing race line through the next gates; your next gate lights up green, gate 1 is a checkered start/finish gate
  and arrows on the ground show the flight direction
- Four kinds of ready-made race courses generate in the world, with a record sign and a loot chest

### Paint jobs
- Color twelve parts of every drone separately at the workbench: frame, TPU, motor bells, each prop, stack, battery,
  camera, antenna tips and accessories
- 16 dye colors, carbon, gunmetal, aluminium, gold and copper finishes, hex colors, presets, copy and paste
- Costs one dye per repainted part in survival; other players see your colors, the item icon shows them too

### Video channels and spectating
- Analog R1–R8 and F1–F8 with real 5.8 GHz frequencies, digital CH1–CH8, assigned automatically or fixed per drone
- Press `F7` with goggles on to watch other pilots' feeds, with their OSD and your own video breakup

### Freestyle tricks
- 59 tricks in nine families: flips and rolls up to triple and segmented, snap rolls, corkscrews, Vanny rolls, rewinds and
  flip rewinds, Juicy Flicks, Rubik's Cubes, power loops with power flips, power rolls, inverted 360s and side loops,
  Maverick loops, Baranis, Immelmanns and Immelmatts, hammerheads, Matty flips and Anti, Half and Power Mattys, split-S,
  540 split-S and splitbacks, yaw spins upright and inverted, coin drops, Trippy Spins, McTwists, inverted hangs, knife
  edges, reverse knife edges and wall rides, dives, backwards dives and tornados, orbits, gaps and proximity passes –
  sloppy, slow and chained versions count too
- New school: ejects and Sbang combos, Funky Splits, Shinobi flips, Guillotines and Side Trippies; in 3D mode inverted
  flight, inverted hovers, tic-tocs, 3D flips and rolls and 3D power loops
- The *Freestyle* tab groups every trick by family, with search (also by scene nicknames like barrel roll or stall turn)
- Points, combos up to ×4, chain and style bonuses in the OSD; trick score and best combo in the flight stats; every trick
  explained in the *Freestyle* tab and a Flight School lesson

### Propwash dust
Fly close to the ground and the rotors stir up a subtle cloud of dust in the color of the block below, or a fine
spray over water. How high, how much and how far it spreads follows from thrust, prop size and tilt. Adjustable, and
it respects Minecraft's particle setting.

### Survival mode
- Everything except the creative goggles can be crafted
- Five crafting materials (Carbon Plate, Copper Coil, Circuit Board, LiPo Cell, HD Video Chip) connect vanilla
  resources to drone parts
- Tiered progression: a whole whoop, the radio and analog goggles from copper, iron and redstone; high-end parts need
  gold, diamonds, quartz, amethyst, glowstone and echo shards
- Every recipe unlocks as soon as you hold one of its ingredients – and all of them once you own any Propwash item
  (search “propwash” at a crafting table)
- Chests in villages, mineshafts, dungeons, temples, outposts, strongholds, shipwrecks, trial chambers, vaults,
  ancient cities and end cities can hold parts, batteries and, rarely, a ready-to-fly whoop

### Flight School
Nineteen animated 3D lessons (twenty in creative mode) that explain everything with a small diorama, callouts and your real key bindings:
building a drone, binding the radio and wearing goggles, FPV and line of sight, batteries and charging, gates and racing, turning
gates freely, flags, slalom and dive cubes, freestyle
tricks, video systems, action cam recording, the gripper, the configurator, GPS Rescue and failsafe, altitude and position
hold, 3D mode, crashes and range, swapping props and damage, survival crafting and bandos. Flight training adds 20 lessons you fly
yourself. **Hold M over any Propwash item** to
open its lesson.

### Add-ons
Propwash 0.4.0 opens up: add-ons can add parts of every kind, new frame shapes, presets, workbench buttons,
configurator tabs, Flight School lessons and share codes. Install add-ons on the client **and** the server in the same
version; Propwash checks the part lists when you join.

### And more
- Real-time synthesized motor sound for every drone, with Doppler effect and beeps
- Shift tooltips on every item: what it does, how to use it, what it fits
- A Betaflight-style configurator (**K**, also from Mod Menu on Fabric and the mod list on NeoForge) with tabs for
  tuning, rates, receiver, modes, keys, video, video channel, OSD, LED strip, action cam, racing, freestyle, rescue, world rules,
  display, audio and server
- Multiplayer-ready: the server streams and generates chunks around the drone, so you are not limited by your render
  distance; world rules for everyone (gravity, battery life, wind, crash damage, range, video channels), permissions and
  movement validation

---

## Getting started

1. Build a drone at the **Drone Workbench** (or grab a preset from the creative tab).
2. Place it on the ground.
3. Right-click the drone with the **FPV Remote** to bind it.
4. Put on **FPV goggles** that match the drone's video system (analog or digital) – they need no binding.
5. Hold the radio and right-click into the air: FPV with matching goggles, line of sight without goggles.
6. Arm (switch or **Z**) and fly. Right-click with the radio again or press **Y** to stop.

Stuck? Hold **M** over any Propwash item for its Flight School lesson, or **Shift** for a detailed tooltip.

### Default keys

| Action | Key |
|---|---|
| Arm/disarm | Z |
| Cycle flight mode (acro/angle/horizon; while flying) | R |
| Acro / Angle / Horizon directly | Numpad 1 / 2 / 3 |
| 3D mode on/off (after unlocking) | Numpad 6 |
| Flip drone upright | U |
| Stop FPV flight | Y |
| GPS Rescue on/off | J |
| Set home point | , |
| Locator beeper | . |
| Restart race | Backspace |
| Rotate gate (gate or course tool in hand; sneak: change mount) | R (shared with *Cycle flight mode*) |
| Action cam: start/stop recording | F9 |
| Configurator | K |
| Flight School | M (hold over an item) |
| Keyboard flight | W/S throttle, A/D yaw, arrow keys pitch and roll |

Keys are positions on a US layout (on a German QWERTZ keyboard, arm is Y and stop is Z). Every key can be changed in
the configurator's **Keys** tab.

### Radio setup
Connect your EdgeTX/OpenTX radio via USB and choose **USB Joystick (HID)**. Propwash picks it up automatically (AETR,
AUX1 = arm, AUX2 = flight mode) and asks once whether to set it up now – the pre-filled wizard usually just needs a
confirmation and a quick calibration. After that the radio loads its profile silently whenever you plug it in. You can
rerun the **Receiver wizard** anytime from the configurator's **Receiver** tab.

---

## Requirements

- Minecraft **26.3**
- **Fabric:** Fabric Loader 0.19.5+ and **Fabric API**
- **NeoForge:** 26.3.0.10-beta or newer
- Java 25
- Required on **both client and server**
- Optional: [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) for drones with LED strips as real light
  sources

## Known limitations

- Only Minecraft 26.3 for now.
- With a shader pack (Iris/Oculus) the video effect falls back to a simpler overlay.
- Outside creative, the radio link reaches about 1,200 blocks; servers can limit it further.
- Radios must run in USB joystick mode.
- macOS: Propwash detects controllers without Apple's GameController framework by default, so radios such as the
  Radiomaster Pocket show up. Pure MFi gamepads that only work through it need the
  *Apple GameController* option in the *Receiver* tab.
- Spectating shows the other pilot's OSD approximately: flight mode, sticks and radio link are not transmitted.
- The action cam records up to 1440p60; how smoothly depends on your CPU. The sound track contains only the motor
  sound of the recording drone.

---

*Propwash FPV is an original mod. It is not affiliated with Betaflight, EdgeTX, OpenTX or any drone manufacturer.*

*NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.*

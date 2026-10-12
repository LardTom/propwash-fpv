# Changelog

What changed in Propwash FPV, for players and server owners. Add-on authors find their notes in
[`docs/api/changes.md`](docs/api/changes.md), technical details per release are in
[`docs/RELEASE-NOTES.md`](docs/RELEASE-NOTES.md).

## [0.5.1] — Drone trails on servers, world rules for admins, drone tags

### New
- Drones carry tags for server admins: `pw_owner_<name>` (who placed it), `pw_pilot_<name>` (who flew it last) and `pw_flying` (`pw_autopilot` while GPS Rescue brings it home alone) – e.g. remove a player's drones with `/kill @e[type=propwash:drone,tag=pw_owner_Steve]`.

### Improved
- Server admins who may use `/gamerule` – for example through a permission mod – can now edit the *World rules* tab, not only operators.

### Fixed
- Drone trails of other pilots now show on servers, not only during GPS Rescue (return to home).

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API 0.161.0+26.3) or NeoForge (26.3.0.10-beta+) · Java 25 · 0.5.0 and 0.5.1 play together – clients and servers can mix both versions; other pilots' trails show as soon as the server or your own game runs 0.5.1.
- Worlds, drones, courses and settings from 0.5.0 load unchanged; drones placed before the update get their owner tag the next time someone places them.
- *Propwash: Just More Parts* 1.1.0 and other add-ons made for 0.5.0 keep working.

## [0.5.0] — World rules, flight training, 59 freestyle tricks, 3D mode and course elements

### Highlights
- **Flight training:** 20 hands-on lessons in five chapters – from hovering to gates, flips and a final exam – flown with your own drone through a holographic course, with stars and best times.
- **World rules for everyone:** gravity, battery life, wind, crash damage, range and more apply to the whole world – set them one by one in the new *World rules* tab or with one click as *Realistic*, *Training* or *Arcade*.
- **Freestyle like the pros:** 59 tricks in nine families – from Matty flips and power loops to Sbang combos and Guillotines – are recognized even when sloppy, fast or chained, and the new 3D mode lets you fly upside down.

### New
- Flight training in the Flight School or with `M` while flying, in FPV or line of sight; only you see the course, so it works in survival and on servers.
- *World rules* tab with an explanation for every rule: the host and operators change them, everyone else sees what applies – also with `/gamerule` and `/propwash rules preset`. *Gravity* goes from 0 to 1000 %.
- Drones hit mobs and players and bounce off, up to five hearts – players only with PvP.
- Drone trails: with the world rule *Drone trails* (off by default, on in *Training* and *Arcade*) a thin, fading trail in each drone's color shows where the other pilots fly – switch it off for yourself in the *Display* tab.
- Drones break glass: with the world rule *Drones break blocks* (off by default) a fast drone smashes through glass, panes and decorated pots – never in spawn protection or claims. You feel it: the drone slows down and gets knocked off its attitude, the FPV picture jolts and shards fly ahead of you. The world rule *Break-through impact* sets how hard.
- 3D mode: put it on a switch and the motors spin backwards below center throttle – fly inverted and take off upside down after a crash.
- Trick catalogue in the *Freestyle* tab: every trick with its points, grouped into nine families, with search and chains.
- Race courses get flags, slalom poles, dive cubes and freeform gates – right-click any opening in the blocks and it becomes a gate – and *Adjust gate* turns and scales gates freely right in the game.
- Gates give a redstone signal: every drone flying through powers the block the gate stands or hangs on for half a second – light lamps, open doors or fire pistons (new *Redstone* page in *Adjust gate*).
- Gate poles: a single gate side with LEDs in 16 colors – on the ground, out of a wall, from the ceiling or on a gate's corner like at MultiGP, stacked up to four.
- Laps per course and *Edit course*: every course has its own lap count and name. From its leaderboard (right-click the air with the Course Tool) you rename it, set the laps, reorder gates, move start and finish and add or take out gates. A changed layout starts a new leaderboard – old times are archived, never mixed.
- A gripper for the bottom mount picks up and drops items, and signal repeaters pass video and control on through walls and stairwells.
- Bandos: rare abandoned barns, factories, piers, oil rigs and mine heads to fly through, some with a chest of used parts.
- Creative Flight Controller and *Drone Editor* (creative mode only): set masses, motors, props, battery and drag freely and share the drone as a code.
- Fly with mouse and keyboard: the mouse steers like aiming in a shooter, `W`/`S` or the mouse wheel set the throttle, and the receiver wizard has its own step to try it.
- Action cam: save flights as a camera path for the replay mod Flashback and record smoothly with your graphics card's encoder. That needs the free program ffmpeg – the *Action cam* tab shows whether it was found and how to install it, and every recording names the encoder at work.
- Binding made simple: click the drone with your remote and it remembers you – through the workbench, picking up, placing and restarts. A friend who wants to fly it clicks it with their own remote. Goggles are no longer bound: wear ones that match the drone's video system and you fly FPV, take them off and you fly line of sight.
- Blackbox: every armed flight as a `.bbl` log for Blackbox Explorer or PIDtoolbox (new *Blackbox* tab).
- Camera vibration: the FPV image shakes subtly like a real camera on the frame – a faint motor buzz, prop wash and hard punch-outs, while it stays calm at high speed. Image only; adjust or switch it off in the *Video* tab.
- Configurator search (`Ctrl+F`), your own rate and PID presets as codes, *Image format* 16:9 or 4:3, and your own commands such as `/time set night` on a radio switch or key.
- Documentation at [propwashfpv.com/docs](https://propwashfpv.com/docs/), and Propwash is now source-available under the PolyForm Shield License – the add-on API is MIT licensed.

### Improved
- Trick detection is more forgiving: flips and rolls count out of fast flight, a little short or with some yaw mixed in, and a half roll there and straight back counts as a rewind.
- GPS rescue climbs to a safe height, flies around trees and buildings and out of halls, never lands in water or lava – and brings the drone home even if you stop flying, leave the game or lose the signal.
- Video behind obstacles is realistic: trees, houses, water and hills weaken the signal by what they are made of.
- Flight physics checked against blackbox logs of real drones from whoop to 7″: no more floaty drops when you lower the throttle, whoops drop and dive like real ones (65 mm whoop 36 → 48 km/h), and punch-outs pull harder.
- The FPV image reacts more directly: your video system's delay no longer counts twice, so the picture arrives up to 12 ms (analog) or 10–20 ms (digital) sooner.
- Drones are shown at their real size by default, so their speed looks right. If you still had the old 1.75×, you are switched once and told where to change it back (*Display* → *Drone size*).
- Whoop and cinewhoop ducts look like real ones, and in FPV you see them in the lower corners like with a real camera.
- Props last much longer, the workbench shows dry and all-up weight, and *Rotate gate* turns gates in 45°, 15° or 5° steps.
- You can join servers that don't have one of your add-ons – it simply stays off while you play there. If an add-on is missing on your side, you see which one, in which version and where to get it.
- On servers an old remote no longer flies a drone that someone else has bound since, and when a lap doesn't count, the race says why.
- Wrong goggles for the drone's video system: the remote refuses to connect and names both systems, and in flight they show no picture until you take them off. The *Video* tab shows just the settings of the goggles you are wearing.
- Drones you hear from outside fade out by their size, Flight School labels stay calm while you rotate or zoom, and with shader packs your own drone casts its real shadow.
- Race drones carry their battery under the frame like real race quads: a lower center of gravity, a little snappier on roll and pitch, and they land on the pack. Freestyle and long-range drones keep it on top.

### Fixed
- Menus fit every GUI scale and window size – with keyboard, mouse or a transmitter connected, also while you are flying a drone: what does not fit scrolls, long texts wrap instead of being cut off, and narrow windows rearrange into a single column. Tooltips wrap and stay on screen, and very long ones scroll with the mouse wheel.
- The leaderboard scrolls when it does not fit, so your own row and the assisted laps are always reachable. Pilot names stay readable in narrow windows, and every drone class can be picked as a filter.
- While you fly a drone your body no longer hangs invulnerable in the air: it falls, burns, drowns and can die like any player. A red edge, a sound and a short notice in FPV warn you, and if you die the drone goes into its failsafe. Event servers can protect pilots with the world rule *Invulnerable pilots*.
- You can type numbers in every number field and slider: click or tab to it, `Enter` applies, `Esc` undoes.
- The receiver wizard no longer stores the throttle the wrong way round.
- Tricks flown back to back no longer cancel each other out.
- Flight mode keys always switch, and holding a toggle key no longer triggers it several times.
- At night with LEDs on, the FPV view no longer turns black on some Intel graphics, and with shader packs no dark shadow covers it.
- Gates and course elements no longer hold pressure plates down or keep tripwires triggered.
- Motors and other small parts are no longer huge in your hand.
- Other players' drones no longer sound like rushing wind.
- Players with the same add-ons are no longer turned away at random when two add-ons bring their own battery cells.
- The FPV image no longer tilts or bobs when the pilot gets hit.
- Leftover Flight School labels, race messages running off the screen and telemetry bars sticking out of the panel are gone.
- Drones sit on the ground: props, ducts and battery stay above the surface when a drone lands tilted, tips over or lies on its back, a standing drone no longer hovers a finger's width above it, a crashed drone falls flat, and drones lie on top of snow layers, soul sand, mud and honey blocks.

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API 0.161.0+26.3) or NeoForge (26.3.0.10-beta+) · Java 25 · client and server must both run 0.5.0.
- Worlds, drones, gates, courses and leaderboards from 0.4.0 load unchanged. Drones climb a little stronger and sink a little faster under throttle, the 65 mm whoop is faster, the 5″ race drone carries its battery underneath, drones from 0.4.0 keep their old binding until you bind them once, goggles bound in 0.4.0 simply work with any matching drone, and courses from 0.4.0 follow each pilot's own lap count until someone sets one for the course. Your personal *Simulation* settings are now world rules – set them again in the *World rules* tab.
- Add-ons made for 0.4.0 keep working; *Propwash: Just More Parts* 1.1.0 is made for this version. Add-ons on a server are still needed by everyone who joins; add-ons only you have stay off there.
- The new license applies from 0.5.0 on; 0.4.0 and older stay All Rights Reserved.

## [0.4.0] — Parts API for add-ons, realistic flight times and battery, props in the FPV view

### Highlights
- **Parts from add-ons:** other mods can add frames, motors, props, batteries, stacks and video units that fly with their own data – the first is *Propwash: Just More Parts*.
- **Race gates on walls and ceilings:** place any gate on the floor, a wall or a ceiling, turn it with `R` and fly dive gates, side gates and windows.
- **Realistic flight times and battery:** packs run empty as fast as real ones, and the workbench shows hover, cruise, mixed and aggressive flight times.

### New
- Add-on parts get items, Shift tooltips, workbench slots, build analysis, paint, wear, recycling and Flight School lessons.
- Every add-on gets its own creative tab right after the Propwash FPV tab.
- New frame shapes from add-ons: deadcats, stretched and squashed X, H-frames, ducted frames and X-Class with props up to 13″.
- More battery types (LiPo, LiHV, Li-Ion and more from add-ons) up to 12S; stacks have a voltage range, motors a prop mount.
- Motors heat up under load; above their limit they throttle back, the OSD shows *MOTOR OVERHEAT* and they wear.
- A battery with more cells than the stack allows burns out the ESC when you arm (*Hardware limits* in the *Simulation* tab).
- Your own props show in the FPV view where a real camera would see them, spinning props as a soft blur.
- The FPV field of view comes from the camera in the video unit, like on a real quad, instead of a setting per goggles.
- Every drone keeps its own camera angle, set in its service menu (sneak + right-click) within what the frame's mount allows.
- *Gravity* slider in the *Simulation* tab (0.80× to 1.50×) for pilots who find the drones a little floaty.
- *Rotate gate* key (`R`, still *Cycle flight mode* in flight): turn or flip the gate you place, sneak to switch the mount, or look at a placed gate to rotate it.
- A translucent preview shows where a gate will go and turns red when there is no room.
- Drones, frames and props hang flat on the wall in item frames like a trophy and tilt towards you on shelves.
- Radios such as the Radiomaster Pocket are now detected on macOS; pure MFi gamepads use the new *Apple GameController* option.
- Add-ons can share builds, paint, tunes and OSD layouts as codes; the server checks every import (survival uses your parts and dyes).
- Removing an add-on keeps your drones: its parts show as *Unknown part* until you install it again.
- Joining a server names parts that are missing or different instead of failing later.

### Improved
- All parts follow one naming scheme: size and device first, extras in brackets, e.g. *1S 300 mAh Battery (LiHV)*.
- Battery level, OSD warnings and brownouts follow realistic power draw, including the full-throttle current of small props.
- The workbench shows four flight times, currents, landing voltage and motor temperatures.
- "Props in the camera view" at the workbench matches the FPV image and depends on the frame and its camera mount.
- The built-in cinewhoop reaches a realistic 89 km/h instead of 62 km/h.
- The Flight School key (`M`) opens the matching lessons over add-on parts too, and the gates lesson covers walls and ceilings.
- Items have a sensible size on the ground, in item frames and on shelves; dropped drones show their size class.
- Big drones fit their inventory slot, and the 7″ two-blade prop no longer reaches into the screen in first person.
- Text fields in the settings, the paint screen and the OSD editor use the Propwash look.

### Fixed
- The *LOW BATTERY* warning goes away when the voltage recovers after a hard punch; *Infinite battery* shows no battery warnings.
- The receiver wizard reads the throttle and its direction from your movement (fully up, then fully down), wherever the stick starts, and ends with a check page that shows every channel live with *Inv.* to flip it.
- Crashed drones no longer hang in the air on an invisible floor; they fall and come to rest on the ground.
- The FPV camera no longer looks through the ground into caves after a crash or landing.
- Water no longer disappears behind spinning props, LED glow and ghost drones.
- No more short freezes every few seconds with the OSD on setups with font resource packs.
- Workbench stats no longer run into their values in any language or GUI scale.
- Propwash tooltips are no longer cut off at the bottom of the screen.

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API 0.161.0+26.3) or NeoForge (26.3.0.10-beta+) · Java 25 · required on client and server.
- Network protocol 8: client and server must both run 0.4.0.
- Worlds, drones, items, presets, ghosts and settings from 0.3.x load unchanged; flying is unchanged apart from the cinewhoop fix and the realistic battery.
- Add-ons use the new Parts API 1.4.0; *Propwash: Just More Parts* 1.0.0 is made for this version.

## [0.3.2] — drone items and ghosts show the real drone

### Highlights
- **Drone items show their own drone:** frame, parts, accessories, paint, prop damage and a missing battery – in the inventory, in your hand, in item frames and on the ground.
- **Ghosts look like the drone that flew the lap:** a translucent copy with its paint, bluish for your own ghost and golden for the record.

### Improved
- Small whoops and big 7″ drones are scaled to stay readable in a slot.
- Identical drones share one icon, so full inventories stay fast.
- Ghosts recorded with older versions use your current drone of the same class, otherwise a matching preset.

### Compatibility
- Minecraft 26.3 · Fabric and NeoForge · Java 25 · required on client and server.
- Network protocol 6 is unchanged.
- Worlds, drones, ghosts and settings load unchanged; record ghosts saved by a 0.3.2 server are not shown on older clients.

## [0.3.1] — two-handed radio in first person and stackable gates

### Highlights
- **Stackable race gates:** right-click a placed gate with a gate item to build a ladder up to four gates high.
- **Radio in first person:** lower and smaller in view, with the forearms coming up from the bottom corners and the thumbs on the sticks.

### New
- Stacked gates form one ladder with shared middle bars; look back while stacking to flip the new gate for zig-zag ladders.
- The *Drone Arena* world course has a three-gate ladder, and a new Flight School step shows how to stack.

### Improved
- Every stacked gate stays its own course gate; break one in the middle and the gates above slide down.
- The race line takes a detour around double gates flown in the same direction.
- While you hold the radio with both hands, the item in your other hand is hidden.

### Fixed
- The radio no longer climbs up the screen when you look down at the drone in line-of-sight flight.
- The radio pose stays the same at any field of view and aspect ratio.

### Compatibility
- Minecraft 26.3 · Fabric and NeoForge · Java 25 · required on client and server.
- Network protocol 6 is unchanged: 0.3.1 and 0.3.0 play together.
- Worlds, drones and settings load unchanged.

## [0.3.0] — paint jobs, world race courses, spectating, ghost races and freestyle tricks

### Highlights
- **Paint jobs:** paint twelve parts of your drone with dyes, metal and carbon finishes or any hex color at the workbench.
- **Race courses in the world:** four ready-made courses generate in the overworld, with the record holder on the start sign.
- **Ghost races, spectating and freestyle tricks:** race your best lap, watch other pilots' video with `F7` and score tricks with combos.

### New
- Paint screen with a rotatable 3D preview, presets and copy and paste; in survival every repainted part costs a dye.
- World courses *Field Slalom*, *Canyon Run*, *Forest Weave* and the rare *Drone Arena*, each with a loot chest.
- Video channels: analog R1–R8 and F1–F8 on real 5.8 GHz frequencies, digital CH1–CH8, picked automatically or in the *Video Channel* tab.
- Spectating: with goggles on, `F7` cycles through the feeds of other pilots, with their OSD and video breakup by distance.
- Ghosts: your best lap flies along in blue, the server record of your drone class in gold, with split times at every gate.
- Leaderboards per course and drone class, saved in the world, with their own window (course tool or *Race* tab).
- Standing starts with start lights: pilots line up at the start gate and start together on GO.
- A glowing race line guides you through the next gates in a running race.
- Course gates carry their number, arrows on the entry side and red markings on the back; gate 1 is the checkered start/finish gate.
- Freestyle tricks: flips, rolls, power loops, Matty flips, split-S, dives, yaw spins, orbits, gaps and more, with combos and style points.
- New OSD elements *VTX* and *Tricks*, and a *Freestyle* tab with the points table and your live score.

### Improved
- Damaged props show their state in three steps, in the world, at the workbench, in the Flight School and on the item.
- A false start now costs 3 seconds instead of cancelling the run.
- Only laps the server verified count; laps with altitude or position hold, GPS Rescue or modified physics do not.
- The heavy action cam is smaller and sits on a TPU mount; the light action cam sits on a cradle with a strap.
- LED strips and arm LEDs glow with soft halos, spinning prop discs are subtler and the headlight is warm white.
- The remote sits lower and closer in first person, and small and flat on an armor stand.

### Fixed
- Drifting behind the start gate no longer counts as a false start.

### Compatibility
- Minecraft 26.3 · Fabric and NeoForge · Java 25 · required on client and server.
- Network protocol 6: client and server must both run 0.3.0.
- Worlds, drones, courses and settings from 0.2.x load unchanged; old drones keep their colors and old leaderboards carry over once.
- Server owners can turn off new world courses (`worldCourses`) and spectating (`allowSpectating`).

## [0.2.1] — clearer action cam recordings, survival recipe book

### Highlights
- **Cleaner recordings:** much subtler grain, a steady image quality and a better encoder for fast flips.
- **Recipe book in survival:** every Propwash recipe can be unlocked and sits in a sensible tab.

### New
- *Camera grain* switch in the *Action Cam* tab.
- Recording quality setting (High, Standard, Small) with the estimated size per minute.
- Searching the recipe book for "propwash" lists every Propwash recipe.

### Improved
- Recordings keep a steady image quality: calm scenes give smaller files, and *Standard* never exceeds the old bitrate.
- Finer detail in flat and dark areas and much sharper fast pans.
- Picking up an ingredient unlocks its recipes; any Propwash item unlocks all of them.
- The Flight School's survival lesson points to the recipe book at the crafting table.

### Fixed
- Recordings no longer show horizontal seams or break up into blocks in fast or dark scenes.
- Flight School callouts point exactly at their target in every window size and GUI scale.

### Compatibility
- Minecraft 26.3 · Fabric and NeoForge · Java 25 · required on client and server.
- Network protocol 5 is unchanged: 0.2.1 and 0.2.0 play together.
- Worlds, drones and settings load unchanged.

## [0.2.0] — accessories, action cams, LED strips and part wear

### Highlights
- **Action cams that really record:** MP4 videos of your flights from a camera on the drone, with motor sound if you like.
- **LED strips and headlight:** 11 colors and several modes, and a light that makes caves flyable.
- **Part wear:** crashes wear down props, motors, camera and frame, and you swap props in the field.

### New
- Two accessory slots at the workbench, an action cam on top and an LED strip under the nose; both change how the drone flies.
- *Action Cam Light* (42 g, up to 1080p60) and *Action Cam Heavy* (150 g, stabilized, up to 1440p60, own battery).
- Record with `F9`, a radio switch or automatically when arming; a *Recordings* window plays, deletes and opens your clips.
- *Action Cam* and *LED Strip* tabs in the configurator, and a REC element in the OSD.
- *LEDs off* switch function (like Betaflight's LED_LOW) on any radio switch, or `F8` on the keyboard.
- Optional LambDynamicLights support: drones with an LED strip become real light sources.
- Damaged props cost thrust and shake the drone, a damaged camera tears the picture, and the OSD warns.
- Swap props in the field: hold props of the same type, sneak and right-click the drone.
- New radios and gamepads are detected and set up with a pre-filled wizard.
- Every switch assignment can be inverted.
- Flight School lessons "Action cam: recording videos" and "Swapping props and damage".

### Improved
- Realistic top speeds: the Freestyle preset reaches about 147 km/h instead of up to 202 km/h; hover and handling are unchanged.
- Realistic video breakup: analog fades in stages down to static, digital blurs, blocks and freezes before "NO SIGNAL".
- Arming safety like Betaflight: after a changed assignment or a new radio, flip the arm switch off and on again.
- The remote is held with both hands in third person, also for other players.

### Fixed
- The FPV picture no longer shakes at high speed.
- In FPV the pilot no longer turns to follow the drone.

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API 0.161.0+26.3) or NeoForge (26.3.0.10-beta+) · Java 25 · required on client and server.
- Network protocol 5: client and server must both run 0.2.0.
- Worlds and drones from 0.1.0 load unchanged; existing parts start in perfect condition.
- Server owners can turn off part wear with the `partWear` rule.

## [0.1.0] — first public release

### Highlights
- **Real FPV flying:** an own flight model and a Betaflight-style flight controller, flown with your RC radio, a gamepad or the keyboard.
- **Drones from real parts:** build them at the Drone Workbench from five frames and dozens of parts, each with its own 3D model.
- **Race, craft and learn:** race gates with lap timing, survival recipes and loot, and an animated Flight School.

### New
- Flight model with motors, ESC, battery, rotor aerodynamics, ground effect, prop wash, wind and turbulence.
- Flight controller with Betaflight and Actual rates, PID tuning, Acro, Angle and Horizon, airmode, failsafe and GPS Rescue.
- FPV through bound goggles, or line of sight with a radio HUD; about 800 blocks of full radio range.
- Five frames from the 65 mm whoop to the 7″ long range, with stacks, motors, props, batteries and video units, and five ready-to-fly presets.
- Drone Workbench with live 3D preview, build analysis and a salvage slot; tune and bindings stay on the drone.
- EdgeTX and OpenTX radios over USB and gamepads, with a receiver wizard, free switch assignment and own keys for every function.
- Analog and digital video with realistic breakup, latency and range; analog, digital and creative goggles.
- Betaflight-like OSD with 41 elements, presets and a drag-and-drop editor.
- Batteries with their own charge, a quick swap and a Battery Charger with four bays.
- Race gates in three sizes, a course tool, lap timing with splits, a best-lap ghost and leaderboards per world.
- Survival recipes for every item except the creative goggles, and chest loot from villages to end cities.
- Propwash dust: drones close to the ground stir up dust in the color of the block below.
- Flight School with ten animated 3D lessons; hold `M` over any Propwash item to open it.
- Configurator (`K`), Shift tooltips on every item and real-time motor sound with Doppler effect.
- Flip upright after a crash, a locator beeper and a name for your drone.
- Chunks load around the drone, with server rules and `/propwash` commands for server owners.

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API 0.161.0+26.3) or NeoForge (26.3.0.10-beta+) · Java 25 · required on client and server.
- Network protocol 2: client and server must run the same version and get a clear message otherwise.

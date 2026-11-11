# Changelog

What changed in Propwash FPV, for players and server owners. Add-on authors find their notes in
[`docs/api/changes.md`](docs/api/changes.md), technical details per release are in
[`docs/RELEASE-NOTES.md`](docs/RELEASE-NOTES.md).

## [0.6.0] - Share codes for every add-on, a bigger race system, freecam drone and RX block

### Highlights
- **Share codes for every add-on:** share codes now belong to Propwash. One *Share code* button in the workbench, a *Share code* tab, `/propwash code` and clickable codes in chat. The new PW3 format names every add-on a build needs, with version and download link, and old PW1 and PW2 codes keep working.
- **A bigger race system:** loops and backwards passes, point-to-point courses, gates that snap into rows and dive cubes, a course settings menu, and a replay, ghost and share code for every time on the leaderboard. A race standard decides which world rules make a lap assisted, and servers and bots read courses and leaderboards by course ID.
- **Freecam drone, RX block and play with others:** fly a camera drone that exists only on your screen (if the world allows it), turn a channel of your radio into a redstone signal, and join Fabric servers with NeoForge.

### New
- Share codes: *Share code* button in the workbench, *Share code* tab in the configurator and `/propwash code export|import|info|give|convert`. An import shows which add-ons a code needs (version, Modrinth/CurseForge link), locks parts when a code doesn't match your add-on version, offers stand-in parts and shows a shopping list in survival. Paint, tuning and OSD layout also apply when the code's parts come from an add-on you don't have. *Copy as PW3* converts old codes, and OSD layouts take the OSD elements of add-ons along.
- Share codes in chat are clickable, also in other players' messages: hover for a preview with the rendered drone, parts, weight, thrust/weight and needed add-ons. A click opens the import (nothing is applied until you confirm), shift-click copies. Switch it off under *Configuration* → *Chat*.
- Leaderboards list every time instead of a top list, each with the drone's share code, a first-person replay and a ghost you can race. With *All*, every pilot's best time per class gets its own row, and a *Server rules* badge shows when a world's rules differ from the defaults.
- Replays start up to 1 s before the start gate, mark every gate on the timeline and can be saved as an MP4 with *Record as video* (in FPV or behind the drone, needs ffmpeg).
- Course settings: one menu for name, course ID, laps, owner and rights, leaderboard, ghosts and deleting. Open it by right-clicking the air with the Course Tool or with *Settings* in the course window. Course creators can switch off the race line for their course.
- Loops: a gate can appear several times in a course, also flown backwards. Click it again with the Course Tool (from behind means backwards) or flip a pass with ⇄ in the course window. Gate plates show every number (e.g. "1 · 3←"), and the right gate flown the wrong way counts as wrong way.
- Point-to-point courses: the first element is the start, the last one the finish, no laps, splits at every element. Switch between *Laps* and *Point to point* in the course window or with `/propwash race mode`.
- Gates know their neighbours: a standing gate placed next to another snaps post on post (rows and corners without double-thick posts), also stacked gates in every stack height. Four gates with a lying gate clicked on top form a dive box, and flags, slalom poles and gate poles sit on the corners or the middle of gates and on dive cubes.
- Stacked gates can be turned: a stacked gate takes the rotation you set for the ground, and you can turn it later with the *Rotate gate* key or in the gate menu.
- Gate menu with pages: flags and slalom poles get a *Scoring* page (scoring reaches up to 32 blocks down, so a tall flag on a pillar counts down to the ground), and the *Position* page moves gates in steps of 1/16, 1/4 or 1 block (1/32 with Ctrl+Shift), also with the arrow keys and Page Up/Down. Gate poles join courses as pass or turn markers, freeform gates can ignore gaps in their edge, and the active gate shows its fly-through area (toggle in the *Race* tab).
- Laps that don't count say why ("Lap 1 not counted: Slalom Pole 25 missed"), and the next laps still count. The course editor warns when the pass zone of a pole or flag covers a neighbouring opening and offers to shorten it.
- Every race course has a course ID that stays the same when you rename or rebuild it: in the course window with a *Copy* button or with `/propwash course id`. Race data for servers and bots: courses and leaderboards by course ID through the add-on API or as JSON files in `world/propwash/race-data/`.
- Race standard: every world rule is race-relevant or neutral, and neutral rules like *Disarm on crash* never make a lap assisted. Operators set the race standard in the *World rules* tab or with `/propwash race standard`, and every time is re-rated when the standard changes. The new world rule *Free course placement* lets gates, flags and poles overlap (gate in gate), and *Creative FC race-legal* (also per course) decides whether a creative flight controller counts as an assist.
- Personal rules: admins choose for each physics rule who may change it (*Fixed*, *Zones* or *Players*) in the *Release* column of the *World rules* tab or with `/propwash rules tier`. Rules released to players show up in the new *My rules* tab, and the built-in *Race Legal* preset clears all your own values. Laps flown with your own race-relevant values count as assisted.
- Zones for everyone: right-click two corners with the new Zone Marker (green preview frame), then the zone editor opens. Every player can create zones, owners and operators change or delete them (world rule *Players may create zones*, on by default). Each rule in a zone can follow the zone below, the world value or its own value, and a *Zones* box next to each rule decides whether zones may change it. Zone outlines show while you hold the Zone Marker or the Course Tool, or while you fly.
- Freecam drone: fly a camera drone that exists only on your screen, with no model, no collision and no battery. Allowed by the new world rule *Allow freecam drone* (off by default). Press F6 (*Toggle freecam drone*) or *Start freecam* on the *Overview* page of the K menu; sizes whoop, 3.5″, 5″ and 7″ with their own rates and camera angle. It parks in the air when disarmed, flies through walls, stops at your view distance and ends on damage, death, dimension change or disconnect, or when the rule is turned off.
- RX block: bind it to yourself with your radio and turn one channel (throttle, a stick, an AUX switch or a button) into a redstone signal from 0 to 15. Learn the channel and the 0 %/100 % points in its window and set the range up to 128 blocks. It keeps its last signal out of range and works while you fly. The *Creative RX Block* works at any distance in its dimension. New Flight School chapter *RX block: redstone by radio*.
- Workbench *Recycle* tab: turn any part back into the materials of its crafting recipe, all of them at 100 % condition, half at 60 to 99 % and a quarter below 60 %.
- Trick tutorials in the *Freestyle* tab: hover a trick for a short looping video, click it for a step-by-step player with explanations, 0.5× slow motion and a stick cam in your stick mode, for all 59 tricks. Flight School lessons also get 0.5× slow motion, and *Unlock all* opens every lesson without following the order (no stars for it).
- Advancements: a new *Propwash FPV* tab with 27 advancements, from your first drone and first take-off to 100 km flown, personal bests, beating a ghost, Flight School stars, cargo flights and defeating the ender dragon or the wither with a drone.
- Bosses fight drones (world rule *Bosses fight drones*, on by default): the warden hears drones, gets angry and hits them with its sonic boom, and its darkness pulses in the FPV picture. The ender dragon throws drones away with its wings, rams them and dives at armed drones, and its breath wears out the parts. The wither's side heads shoot skulls at flown drones. Endermen dodge drone hits by teleporting away.
- Sculk hears drones: an armed drone with its motors above idle sends vibrations like a player gliding with elytra, and a crash counts as hitting the ground. Wool blocks it as usual, and the world rule *Drones trigger sculk* turns it off.
- Hear what your drone hears: whoever looks through a drone (the pilot in FPV, spectators with F7, the video channel or a camera operator) hears sounds and explosions near the drone, even far away from the body.
- Signal repeaters reach as far as a direct link on every hop (the drone's video transmitter for the picture, the remote for control), show their signal with four LED bars and work as a signal meter in your hand. The OSD element *RPT* is part of the Minimal, Standard and Race presets.
- New OSD elements: the flight path marker shows where your drone is really moving in the camera image (also with the fisheye lens and while spectating, off by default, part of the *Full / telemetry* preset), and *Spectators* counts who is watching your video.
- Spectating with F7 shows the pilot's OSD with live sticks, battery, mode, warnings and times, and during a race the active gate, start lights, countdown, lap and finish. Your own OSD settings stay unchanged.
- Camera angle in the K menu: the *Video* tab has a new section *Camera of this drone* to set the uptilt of the drone you hold or fly (slider within the frame's camera mount, −/+ with Shift for 5°, ↺ back to the frame default).
- The first gate you place in a world opens a small window that explains the gate menu (sneak + right-click: size, rotation, redstone signal), once per world. *Reset hints* in the *Flight School* tab brings it back. New Flight School chapter *Gate menu: size, rotation, redstone*.
- Creative-only barriers: the *Drone-Passable Barrier* lets drones through and stops players, mobs, items and arrows, the *Drone Barrier* stops only drones. Both are invisible like the vanilla barrier: in creative mode they show the selection outline when you aim at them, even with an empty hand, and icons while you hold one; in survival they stay invisible.
- Drones take the vanilla `minecraft:scale` attribute (0.0625 to 16): `/attribute` makes a drone bigger or smaller. Model, props, LEDs, hitbox, collisions, FPV camera and rotor dust scale with it, the flight model stays the same, and laps with a resized drone count as assisted.
- Auto-arm after reset: when you reset the drone to its home point and your arm switch is still on, it arms by itself as soon as the throttle is down (*AUTO-ARM: THROTTLE DOWN* in the OSD). Setting *Auto-arm* in *Modes* → *Arm / disarm* (on by default).
- `/propwash clear [targets]` for operators removes every drone in loaded areas, or only those in an entity selector, without drops. Pilots of removed drones are back in their own view, and the server log names who ran it.
- Slime blocks bounce a flown drone back on every side without crash, prop strike or wear, and wool and hay bales catch it softly. Data packs add blocks through the tags `propwash:drone_bouncy`, `propwash:drone_cushion` and `propwash:drone_sticky`.
- Decorative drones with spinning props: a drone item with the custom model data string `blur` shows its props spinning in item frames, on item displays, on the ground, in your hand and in inventories.
- Propwash checks at start-up whether other mods use its reserved namespace `propwash`. Findings never crash the game: they go to the log, you get one chat line with a *Details* button the first time you join a world, and crash reports gain a *Propwash add-ons* section.
- NeoForge players can join Fabric servers that run the same Propwash version and add-ons, also behind Velocity. If the server has content the player is missing, the message names the mods. A NeoForge server turns away everyone without NeoForge with a clear message, so run Fabric on servers for NeoForge and Fabric players. Thanks to ETHG for the contribution (pull request #2).
- For add-ons, add-on API 1.8: switches and analog channels of add-ons are assigned like built-in functions on the new *Add-ons* page of the *Modes* tab (kept when an add-on is missing), accessory actions show their names, and the first flight with such an accessory explains them once. Add-ons bring their own OSD readouts and warnings (group *Add-ons* in the OSD editor) and world rules in their own section of the *World rules* tab.
- For add-ons, add-on API 1.8: data stored on the drone and buttons in the service screen, collision rules for ghost flight through blocks (the server decides, laps count as assisted), accessories that dispense like a dispenser, hang straight or sit tucked under the frame, a gripper that add-ons can use on the server, compass readings for flight assists, flights on their own on the server (hold, follow, take off and land) and a second player who operates a drone's gimbal camera with their own radio.
- For add-ons: LED accessories light up along their own shape (a ring, an arc or a strip along the arms glows LED by LED in every LED mode), and accessories with more than 16 LEDs now light all of them.

### Improved
- Motor heat like on real quads: only an ESC above about 140 °C cuts power (like BLHeli_32), with the new OSD warning *ESC OVERHEAT*. Hot motors no longer throttle themselves; they lose a little torque and wear out. Whoops get properly hot at sustained full throttle, a 7″ on a LiPo still fights heat, and forward flight cools motors and ESCs.
- Replays and spectating stay smooth when the pilot has a bad connection: the pilot's game sends its own recording of a lap to the server, which checks it against its own gate times before it replaces the coarse server track (the time always stays the server's). Other players' drones are buffered and interpolated instead of freezing.
- Plugged-in batteries no longer show plugs or cables: with the battery in, the drone hides the battery's plug and leads and the stack's power leads, on every frame and in the web configurator.
- Props in the FPV view no longer float: their motors and arms are drawn with them, and spinning props keep the hub, prop nut and motor bell sharp in the middle while only the blades blur.
- The bottom gate of a stack counts all the way down to the ground, also right after stacking. A wider gate never stacks on a narrower one, and stacks are limited to four gates.
- Crashes without disarm: an armed drone no longer drives off over the ground at zero throttle, isn't thrown up after bounces and doesn't push against walls at idle, and props hitting a block lose most of their thrust. Throttle or sticks give you full control back at once.
- An upside-down drone tips over and falls off gate bars, block edges, pillars, slabs, stairs, fences and walls instead of rocking on the edge, and a drone that ends up inside a block gets out the shortest way.
- Drones with accessories underneath stand level on them, can be armed on the ground and no longer hover at step edges. The FPV camera sits where the drawn camera is and no longer looks inside slabs, stairs, snow or carpet.
- Honey blocks act on drones like on vanilla mobs: contact brakes hard without bouncing or crashing, drones slowly slide down honey walls and stick to honey for about a second on take-off.
- Accessories: loads count with their real weight (the silent 2 kg cap is gone, above 20 kg the drone shows *overloaded*), a trident weighs 1.5 kg, wind charges fired from a drone push less, front accessories sit right at the nose of every frame, parked drones can fire their front launcher, and projectiles inherit the drone's speed.
- VTX antennas sit at the back of the frame, tilted 45°. Floor arrows in front of gates run towards the gate.
- The Flashback action cam no longer exaggerates prop wash, and very fast rolls and flips come out right.
- When a server turns a player away, its list of add-ons leaves out add-ons that players don't need to install (server-only mods and add-ons marked as optional for clients). Thanks to ETHG for the contribution (pull request #3).
- The message when you switch flight modes names an add-on drone's own modes.
- World rules: zone names stay readable in small windows, and the gate menu's *Position* page is no longer cut off at small GUI sizes.

### Fixed
- In the Flight School dioramas, drones now fly camera first instead of backwards.
- Returning to your home point (*Reset* or GPS Rescue) no longer starts a race on its own; a standing start only begins when you arm at the start gate with *Start when arming* on, or join an open lineup.
- If the pass zone of a pole or flag also covers the next gate's opening, the server now counts the lap the same way your game does.
- Signal: terrain you have seen but that is no longer loaded now blocks video and control link like it should, instead of counting as open air.
- Drones press wooden and weighted pressure plates and trip tripwires, like dropped items do. Thanks to bluepanee for the fix (pull request #1).
- GPS Rescue lands right on the home point even when a door or wall stands next to it.
- A drone sitting a fraction of a millimetre inside a block counts as landed and comes to rest instead of flickering between landed and airborne. A drone stuck in a vine-covered beam at full throttle without gravity is gone too.
- Removing an add-on no longer strips accessories from drones and drone items: missing parts become placeholders with the same id and return with the add-on, and world rules of a missing add-on are kept.
- The receiver wizard no longer crashes with add-ons that add their own switch function by mixin (such as Noclip 0.1.x).
- Gate pole colours past the fourth were lost in the gate menu.
- The notice for a radio already plugged in at game start could show raw text keys instead of the message.
- Newly generated bandos no longer have holes in their yard above caves or ravines, and leaves cut off from their tree no longer cling to the beams (new chunks only).

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API 0.161.0+26.3) or NeoForge (26.3.0.10-beta+) · Java 25 · network protocol 20: client and server must both run 0.6.0. 0.5.x can't join a 0.6.0 server and the other way round.
- Worlds, drones, courses, leaderboards, ghosts and settings from 0.5.x load unchanged. Courses get their course ID the first time they load, old leaderboards stay valid, the freecam rule starts off in every world, and bandos that are already generated stay as they are.
- Going back to 0.5.2: a world played with 0.6.0 opens in Propwash 0.5.2 without a crash, but RX blocks disappear there like any block 0.5.2 doesn't know, and add-on data on drones is ignored.
- Servers: only the course owner and operators can edit a course now (courses without an owner only operators). Every player may create zones until you switch off *Players may create zones*.
- Share codes: Propwash now writes PW3 only; PW1 and PW2 codes stay readable and can be converted in the game or at propwashfpv.com/convert/. Propwash 0.5.2 can't read PW3 codes.
- Add-ons: Propwash 0.6.0 needs *Propwash: Just More Parts* 1.2.0 (with 1.1.x the game doesn't start and the loader names the reason). New official add-ons for 0.6.0: *Propwash: Camera Drones* 1.0.0 and *Propwash: Mob Defense* 1.0.0. Other add-ons made for 0.5.x keep working (add-on API 1.8 includes 1.6). The namespace `propwash` is reserved for Propwash (rules in [`ADDONS.md`](ADDONS.md)), and Noclip 0.1.2 starts on Fabric and NeoForge and works through a radio switch.

## [0.5.2] — Fisheye lens, waterproof drones, turtle mode and zones

### Highlights
- **A real FPV lens:** the FPV picture now looks through a fisheye like a real FPV camera – things ahead appear bigger and closer, the edges bend away, and speed feels like in a real feed. A slider sets how strong it is.
- **Waterproof drones and turtle mode:** spray a drone with waterproof coating and it flies in water; when it lands upside down, flip it back over with its own props, just like in Betaflight.
- **Zones:** give part of your world its own simulation settings – infinite battery on a practice field, zero gravity in a race passage – race-legal or counted as assisted.

### New
- *Fisheye* slider in the *Video* tab from 0 % (flat, the old picture) to 100 %, 80 % by default – interference, 4:3, the OSD, props in view and shader packs follow it.
- Waterproof Coating spray can (iron nuggets, honeycomb, resin clump): right-click a placed drone or combine both in the crafting grid; one can coats ten drones. The world rule *Drones are waterproof* coats every drone (on in *Arcade*).
- Turtle mode on a radio switch or key (numpad 8) in the *Modes* tab: arm while the drone lies upside down and push the stick toward the side it should tip to.
- Zones are easy to find at the top of the *World rules* tab, right below the presets; make them there with a zone editor like *Adjust gate* or with `/propwash zone`. They show a subtle border, the OSD tells you when you fly in or out, and gravity and wind fade in smoothly.
- VTX power from 25 mW up to 1600 mW in the *Video channel* tab, or step through it in flight with the *VTX power* switch or F10: more range and better penetration, but more battery drain and heat (*VTX OVERHEAT*). *Auto* is the default for every drone – digital systems follow their video link, analog ones let the flight controller follow your radio link – plus *Low when disarmed* like in Betaflight and an OSD element for the power.
- Bando maps: journeyman cartographers sell a map (10 emeralds and a compass) to the nearest unexplored bando; sometimes one lies in a bando chest or a village cartographer's house.
- Cobwebs catch your drone – punch out with full throttle; tripwire string snags the props, vines, glow lichen, hanging roots and moss brake you lightly. Fly into a bell and it rings like an arrow hit it.
- *Why is my lap assisted?* check in the *Race* tab: lists everything that would make your lap count as assisted right now.
- A 5.1″ race prop with 2.9″ pitch (the "5129" many racers fly): the same punch off the line as the 4.6″ prop, less top speed.
- *Invert pitch* in the *Receiver* tab flips forward and back for the mouse and the pitch keys.
- *Stick mode* 1–4: pick it first in the receiver wizard or later in the *Receiver* tab – the wizard's prompts and animated radio, the OSD stick overlay, the Flight school and the radio in your hand follow your mode. Radios keep their channels, gamepad sticks follow the mode.
- Action Cam (Flashback): OSD element *Flashback timeline*, a *Simplification* slider with a preview of the saved camera path that rings the spot where the two paths drift apart most, and *Check replays* to thin flights that are too long.
- Every drone remembers its GPS Rescue home point; server admins read and move it with `/data` (`HomePoint`).
- *Reset* (Backspace or a radio switch) puts your drone back on its home point – level, disarmed and standing still, also from far away. It replaces *Restart race*; a running race is canceled. Server owners can switch it off with the world rule *Allow reset*.
- For add-ons: a front mount under the nose (all frames except the Whoop), accessories with their own load (*Accessories* in the service menu) and *Accessory action 1–4* (switch functions and the keys numpad 9, 0, Enter and +).

### Improved
- Batteries discharge like real packs: nearly flat for most of the flight, a steep drop in the last ~10 %, and you feel the end of the pack in every punch. The OSD voltage is smoothed like in Betaflight and flashes while it sags below warning or cutoff.
- Drones touch down on their real frame, battery, props and ducts and settle like a real quad, and crashes end like real ones: fast hits keep their bounce and props dig in, so nose-ins, wall hits and cartwheels can leave the drone upside down or on its side – ready for turtle mode.
- Assisted laps name their exact reasons everywhere, for example "Gravity 130 % instead of 100 %" – also while a zone's gravity or wind is still fading out, for add-on flight assists and in heat results.
- The 5″ race frame is a true X like current race quads, and the Racing Drone flies the new race prop: about 147 km/h top speed instead of 168 – fit the 4.6″ prop if you want top speed.
- The rule that stops the motors after a crash is now called *Disarm on crash*; it lives in the *World rules* tab, and the settings search takes you there.
- *Adjust gate* shows at the top of its panel how to use the mouse: hold `Alt` (`⌥ Option` on a Mac).
- Standing starts: after a *Reset*, arm at the start gate to line up – other pilots join by arming there too.
- Better signal through walls: one block of stone, brick, planks or concrete dampens like one house wall, glass, leaves and wool much less, while hills, badlands mesas, sandstone cliffs, End islands and caves still cut the link. The picture breaks up gradually instead of going black behind a block – analog fades into static, digital gets softer, blockier and laggier like DJI or Walksnail (HDZero-like systems stay sharp and break up) and freezes on the last frame before *NO SIGNAL*.
- The radio link sees the same walls and terrain: RSSI drops behind obstacles, far behind a hill or deep underground the drone goes to failsafe, and VTX *Auto* reacts to obstacles also when you fly line of sight.

### Fixed
- *Restart race* seemed to do nothing: it never moved the drone and did nothing away from the start gate. It is now *Reset*.
- The locator beeper no longer stops when you leave the drone: it keeps beeping until you switch it off, pick the drone up or its battery runs flat, and everyone nearby hears it.
- OSD elements you switch on no longer land on top of others in the *Minimal*, *Standard* and *Race* layouts (Speed covered the new VTX power), and saved layouts move such elements to a free spot.
- Drones no longer end up upside down after gentle landings, on half slabs or when sliding off a step.
- Long Action Cam flights no longer crash the game when you open the replay in Flashback: Propwash thins them when saving (*Flashback limit*, on by default).
- The *Modes* tab lights up the mode your switch selects right away, also while the menu pauses the game and for ranges you haven't saved yet; toggle switches show their real state.
- Laps no longer count as assisted after world rules were set back to default mid-flight, and servers judge each lap on its own for every pilot.
- Setting *Battery life* from infinite back to a limit mid-flight no longer drains the battery.

### Compatibility
- Minecraft 26.3 · Fabric (Loader 0.19.5+, Fabric API 0.161.0+26.3) or NeoForge (26.3.0.10-beta+) · Java 25 · client and server must both run 0.5.2 – 0.5.0 and 0.5.1 can't join a 0.5.2 server and the other way round.
- Worlds, drones, courses, leaderboards and settings from 0.5.x load unchanged; drones without a VTX power setting start on *Auto*, your Flashback keyframe setting carries over, and everyone starts in stick mode 2 (the previous layout). Your *Restart race* key and switch become *Reset*.
- *Propwash: Just More Parts* 1.1.1 and other add-ons made for 0.5.x keep working; new add-on features for accessories, flight assists, gimbal cameras and HUD elements.

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

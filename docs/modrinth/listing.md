# Modrinth listing — Propwash FPV 0.5.2

Everything needed to fill in the Modrinth project page. Body text: [`description.md`](description.md) (English, paste
as the project description) and [`description_de.md`](description_de.md) (German, for a translated page or as a
spoiler section). Player-facing changelog for the version upload: [`CHANGELOG.md`](../../CHANGELOG.md), section 0.5.2.

## Project

| Field | Value |
|---|---|
| Name | Propwash FPV |
| Slug (suggestion) | `propwash-fpv` (`propwash` also fits the mod id, if free) |
| Project type | Mod |
| Icon | `common/src/main/resources/assets/propwash/icon.png` (256 × 256) |
| License | up to 0.4.0 All Rights Reserved (Modrinth: `LicenseRef-All-Rights-Reserved`). From 0.5.0: custom license `LicenseRef-PolyForm-Shield-1.0.0` with the URL of `LICENSE` in the public repository (until it exists: https://polyformproject.org/licenses/shield/1.0.0); the MIT parts are listed in `LICENSE-MIT` |
| Client side | Required |
| Server side | Required |
| Links (source, issues, wiki, Discord) | wiki: `https://propwashfpv.com/docs/` (from 0.5.0); source, issues and Discord none yet. The mod metadata points to `https://modrinth.com/mod/propwash-fpv` (`homepage_url` in `gradle.properties`, used by `fabric.mod.json` and `neoforge.mods.toml`); change it there if the slug differs |

### Summary (≤ 256 characters)

English (239 characters):

> Real FPV drone flying: build quadcopters from real parts and fly them with your RC transmitter, gamepad or keyboard.
> Betaflight-style flight controller, analog and digital video, race gates, survival crafting and an animated Flight
> School.

German (227 characters, if a translated summary is wanted):

> Echtes FPV-Fliegen: Quadcopter aus realen Teilen bauen und mit Funke, Gamepad oder Tastatur fliegen. Flight-Controller
> im Betaflight-Stil, analoges und digitales Video, Renn-Gates, Überlebensmodus und eine animierte Flugschule.

### Categories

| | |
|---|---|
| Featured categories (max. 3) | Technology, Transportation, Minigame |
| Additional categories | Game Mechanics, Equipment |

Search terms the summary and description already cover: FPV, drone, quadcopter, racing, freestyle, Betaflight,
EdgeTX, OpenTX, RC transmitter, gamepad, goggles, whoop, cinewhoop, flight simulator.

## Versions

Upload one version per loader; both are built from the same commit.

| Field | Fabric | NeoForge |
|---|---|---|
| File | `fabric/build/libs/propwash-fabric-26.3-0.5.2.jar` | `neoforge/build/libs/propwash-neoforge-26.3-0.5.2.jar` |
| Version number (suggestion) | `0.5.2+fabric` | `0.5.2+neoforge` |
| Version name (suggestion) | Propwash FPV 0.5.2 (Fabric) | Propwash FPV 0.5.2 (NeoForge) |
| Loaders | Fabric | NeoForge |
| Game versions | 26.3 | 26.3 |
| Dependencies | Fabric API — required; LambDynamicLights — optional | LambDynamicLights — optional |
| Release channel (suggestion) | Beta | Beta |
| Changelog | `CHANGELOG.md`, section 0.5.2 | same |

Do not upload the `-sources` or `-javadoc` jars as primary files.

0.5.2 is a feature update (fisheye lens, waterproof drones and turtle mode, zones, VTX power, bando maps, new battery
curve and ground contact, add-on API 1.6). Update the project description with the new “New in 0.5.2” section;
Just More Parts gets a new upload 1.1.1 (follows the new battery curve). Clients and servers need 0.5.2 together.

Order for 0.5.0 (done): the changelog says the code is source-available and takes pull requests, and the website roadmap marks
"Source code goes public" as done. Publish the GitHub repositories first (`docs/RELEASING.md` §4, step 9), then upload
0.5.0 (and Just More Parts 1.1.0, whose changelog names GitHub too) and put the website live. If GitHub has to wait,
remove the source-available line from the uploaded changelog until it is public.

Minimum versions from `gradle.properties`: Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, NeoForge 26.3.0.10-beta,
Java 25.

## Gallery

All images are 1920 × 1080 in `docs/screenshots/`. Upload in this order; Modrinth shows the featured image on the
project card and in search results.

| # | File | Title | Description |
|---|---|---|---|
| 1 ★ featured | `osd_flight.png` | FPV through the goggles | Flying FPV towards the next race gate with the Betaflight-style OSD. |
| 2 | `drone_closeup.png` | Built from real parts | A 5″ freestyle drone: frame, stack, four motors, props, HD video unit and a 6S battery. |
| 3 | `workbench_freestyle.png` | Drone Workbench | Assemble a drone slot by slot with live 3D preview, weight, thrust-to-weight, hover throttle, top speed and flight time. |
| 4 | `race_gates.png` | Racing line of sight | Race gates linked into a course, the next gate lit green, the radio HUD and the radio in hand. |
| 5 | `pilot_third_person.png` | The pilot | Goggles on, radio in both hands. |
| 6 | `pilot_from_drone.png` | Looking back | The FPV view from the drone back to the pilot. |
| 7 | `flight_school_index.png` | Flight School | Nineteen animated lessons (twenty in creative mode) that explain the whole mod in the game. |
| 8 | `flight_school_build.png` | A Flight School lesson | 3D diorama with callouts, timeline and step-by-step playback. |
| 9 | `configurator_rates.png` | Configurator: rates | Betaflight-style configurator with live rate curves. |
| 10 | `configurator_osd.png` | Configurator: OSD | Choose, place and style 43 OSD elements. |
| 11 | `items_remote_battery_charger.png` | Radio and charger | The radio held in both hands and the battery charger topping up three packs. |
| 12 | `charger_gui.png` | Battery Charger | Four bays, each pack with its own charge; faster with redstone. |
| 13 | `recipe_book.png` | Survival crafting | Every part is craftable and unlocks in the recipe book. |
| 14 | `tooltip_shift.png` | Items explain themselves | Shift tooltips and the Flight School key on every Propwash item. |

Optional extras: `drone_service_menu.png` (service menu: battery and props), `configurator_pid.png`, `configurator_video.png`,
`configurator_rescue.png`, `flight_school_race.png`, `flight_school_rescue.png`, `flight_school_rates.png`, `flight_school_sticks.png` (the real radio with moving sticks),
`remote_display_contexts.png` (radio on an armor stand, in an item frame, on the ground), `remote_first_person.png`
(the radio held with both hands), `configurator_keys.png` (every key in one list), `flight_school_fly.png`,
`dust_takeoff.png` / `dust_takeoff_max.png` (subtle dust under a drone just after take-off).

New in 0.5.0 (gallery candidates): `training_rings_fpv.png` and `training_hover_fpv.png` (flight training in FPV),
`training_screen.png` (lesson list), `configurator_world_rules.png` (World rules tab), `trick_chain_fpv.png` (trick chain
in the OSD), `drone_gripper_carry.png` (gripper carrying items), `flashback_cam_handed_over.png` (camera path handed to
Flashback), `bando_field_barn_aerial.png`, `bando_coast_platform_fpv.png` and `bando_mountain_mine_head_aerial.png`
(bandos).

New in 0.4.0: `drone_closeup_deadcat.png`, `drone_closeup_xclass.png` (add-on frame shapes from the example add-on),
`fpv_props_in_view.png` (own props in the FPV image) and `inventory_addon_parts.png` are candidates for the gallery,
as are the gate mounts: `gate_mount_wall_dive.png` (dive gates on a wall, one as course gate with arrows),
`gate_mount_preview.png` (placement preview with flight-direction arrow), `gate_mount_ceiling.png` (hanging gates) and
`flight_school_race_dive.png` (Flight School: a drone diving through a dive gate).
`api_workbench_extension.png`, `api_workbench_import.png` and `api_config_tab.png` show the add-on extension points and
belong on a developer page, not in the player gallery. Add-on developers find the Maven coordinates
(`maven.modrinth:propwash-fpv:0.5.2+fabric` / `+neoforge`) and the guide in `docs/api/addon-guide.md`.

New in 0.3.2 (good candidates for the gallery): `inventory_drones.png` (every preset and custom drones as
their own item icons) and `race_ghost_whoop.png` (a painted whoop ghost on a course), both taken by the screenshot
tour in `ShowcaseTour`.

New in 0.3.1 (good candidates for the gallery): `remote_first_person.png` (the radio held with both hands). Still
missing: `gate_stacks.png` (a three-gate ladder and a double gate, taken by the screenshot tour in `ShowcaseTour`).

New in 0.3.0 (good candidates for the gallery): `paint_screen.png` (the paint screen at the workbench),
`drone_closeup_paint_neon.png` / `drone_closeup_paint_race_team.png` (painted drones), `spectate_view.png` (watching
another pilot's feed with their OSD), `configurator_video_channel_live.png` (Video channel tab), `gate_start_finish.png`
(checkered start/finish gate), `gate_markings.png` (gate numbers and flight-direction arrows), `race_line_fpv.png` /
`race_line_third_person.png` (the race line). Still missing: a world race course from above, the leaderboard window,
the start lights and a trick popup in the OSD.

New in 0.2.0 (good candidates for the gallery): `action_cam_rec_osd.png` (recording with the REC element in the
OSD), `action_cam_recordings.png` (the Recordings window), `configurator_action_cam.png`, `configurator_led_strip.png`,
`headlight_cave_off.png` / `headlight_cave.png` (the LED headlight in a dark cave), `video_analog_stage2.png` to
`video_analog_stage5.png` (analog breakup stages), `video_digital_breakup.png` and `video_digital_lost.png` (digital
breakup and "NO SIGNAL"), `pilot_third_person_side.png` / `pilot_third_person_back.png`.

Missing for the page (needs new screenshots):

- **Propwash dust over sand or water:** the dust shots show grass; the effect reads better over sand, and the spray
  over water has no picture yet.
- **Survival chest loot** has no screenshot yet.
- **Receiver wizard with a radio connected:** `receiver_wizard.png` only shows the "No radio and no gamepad found"
  state. With a simulated radio there are now `receiver_wizard_check.png` (check page with live channel bars) and
  `receiver_wizard_throttle_hold.png` (throttle step); a calibration shot with a real radio is still missing.

Do not use:

- The internal asset contact sheets (`world_preview.png`, `parts_preview.png`) now live in `docs/asset-sheets/`, not in
  `docs/screenshots/`; they are developer references with German labels, never gallery images.
- `remote_first_person_ahead.png` — a check shot of the radio when looking straight ahead, not meant for the page.

Images inside the description: Modrinth cannot resolve repository paths. After uploading the gallery, copy the image
URLs from the gallery and embed them in the description if wanted (for example under the headings "Build your own
drones", "Racing" and "Flight School").

# Building add-ons for Propwash

Everyone may build add-ons for Propwash. The add-on API, the example add-on and the documentation are MIT licensed, and your own code can use any license you like. To keep add-ons working side by side and with future Propwash releases, please follow these rules.

**1. Use your own namespace – `propwash` is reserved.**
Use your mod id as the namespace for everything your add-on adds:
- registry ids: items, blocks, entities, parts, sounds
- translation keys: `<modid>.…` and `key.<modid>.…`
- key mappings and their category
- game rules and commands
- network payloads
- data and asset paths
- NBT and data component keys
- config files

The namespace `propwash` and the Java package `de.lardtom.propwash` belong to Propwash alone. Official add-ons use their own namespaces too, for example `justmoreparts`.

Why it matters:
- Anything you put into `propwash` can collide with a future Propwash release without warning, and then your add-on or Propwash breaks.
- From Propwash 0.6.0, share codes identify every part by its namespace. A part registered under someone else's namespace breaks every code that contains it.

**2. Only the API is stable.**
- Only `de.lardtom.propwash.api` is stable. It is versioned with `API_VERSION` (SemVer), and breaking changes come only with a major version.
- Everything else is internal and can change in any release, patch releases included.
- Mixins into internal classes are allowed, but at your own risk. Crashes caused by add-ons are not supported. Please check whether a problem also happens without add-ons before you report it.

**3. Missing something? Ask for it.**
If the API lacks a hook you need, open an issue or ask on Discord instead of working around it with a mixin. Gaps get closed, for example custom switch functions, OSD elements, collision hooks, accessories that fit only certain frames (`frames` in the part file, API 1.7), the tilt limit players set in the PID tab (`FlightState#angleLimitDeg()` and `DroneView#angleLimitDeg()`, API 1.7) and a gimbal camera separate from the FPV camera, with no props in the gimbal view and its own orientation and focal length for the Flashback camera path (`GimbalProvider#showsProps`, `#gimbalRotation` and `#gimbalFovDegrees`, API 1.7). The same goes for chunks around a camera drone far away from the player, so that Flashback records its surroundings without the player's view changing (`DroneAccess#cameraChunks()` and `PropwashClientApi.cameraChunks()`, API 1.7).

**4. Naming.**
- "Propwash: …" is the naming scheme of official add-ons: *Propwash: Just More Parts*.
- Please name yours "*Name* for Propwash", for example *Noclip for Propwash*, and don't present it as official: no Propwash logo as your icon, and no "official" in the description.

**5. Licensing in short.**
- Propwash's core is under the PolyForm Shield License 1.0.0 with additions; the API, the example add-on and the docs are MIT.
- **Fine:**
  - depending on Propwash
  - using the API
  - mixins with `@Shadow` signatures
  - your own license for your own code
- **Not fine:**
  - copying core code into your add-on
  - shipping Propwash inside your jar
  - building a competing drone mod from Propwash's code
- The binding texts are `LICENSE`, `LICENSE-MIT` and `NOTICE`. This page is a guideline and does not change them.

## Race data for servers and bots

There is no server add-on, and reading race results needs no mixin. From Propwash 0.6.0 every race course has a stable course id, and its leaderboards hang off that id. A mod reads courses and leaderboards through the read-only API `de.lardtom.propwash.api.race` (`PropwashApi.race()`) and listens to `RaceEvents` (laps, races, personal bests, course records, course changes). Without a mod, server scripts and bots read the same data as JSON files in the world folder under `propwash/race-data/`.

## Recycling your parts

From Propwash 0.6.0 the drone workbench has a Recycle tab. It turns a part back into the ingredients of the crafting recipe that makes it, divided by the recipe's result count. So a normal crafting recipe for your part is all you need.

- The yield depends on the part's condition, and Propwash sets it for every part: 100 % gives back all materials, 60–99 % half, below 60 % (broken too) a quarter. Add-ons can't change these tiers.
- Only parts without a crafting recipe use `recycle` in the part file (`RecycleYield` in `PartBasics`). Its item and count are what the part gives back at 100 % condition; the tiers above still apply. `"none"` makes such a part non-recyclable, and without `recycle` the category default applies (frame → carbon plate, stack and video → circuit board, motor → copper coil, battery → LiPo cell, props and accessories → nothing).
- Details: [`docs/api/addon-guide.md`](docs/api/addon-guide.md), section 8.

## VTX antennas

From Propwash 0.6.0 the antennas of a video transmitter sit at the back of the frame, angled up and back, and no longer turn with the camera uptilt. Propwash derives count, length and cap of the antennas from the VTX data you already have (video system, mass, latency, image quality, power, range), so your add-on needs no change. Elements in a VTX model whose name starts with `antenna` (`antenna`, `antenna_tip`, `antenna_socket`, `antenna_tee`, `antenna_joint`, `antenna_lead`) are left out on the drone, so a drone never shows two sets of antennas.

- Frames may name their antenna holder: `"antenna_anchor": {"pos": [x, y, z], "tilt_deg": 45}` in the part file or `FrameDefinition.Builder#antennaAnchor(AnchorSpec)` (foot of the antennas in mm: centre, at the height of the top plate, behind the rear edge of the frame and behind the battery; the TPU holder reaches forward to the plate; tilt back from vertical, default 45°). Without it Propwash places the holder 12 mm behind the top plate. If a battery reaches further back, the antennas move 5 mm behind it. Antennas are at most 1.2 × the wheelbase long.
- A VTX may set its antennas: `"antenna": {"count": 2, "length_mm": 95, "cap": "pagoda"}` or `VideoDefinition.Builder#antenna(new VideoAntenna(2, 95.0, AntennaCap.PAGODA))` (caps `whip`, `stubby`, `lollipop`, `pagoda`, `patch`).
- Details: [`docs/ASSETS.md`](docs/ASSETS.md), section "VTX-Antennen hinten am Rahmen".

## Compatibility

- Propwash 0.6.0 needs *Propwash: Just More Parts* 1.2.0 or newer. Just More Parts 1.1.x does not work with Propwash 0.6.0, and the game does not start with both: Fabric names the conflict on its error screen, NeoForge shows a loading error with the reason "Just More Parts 1.1.x does not work with Propwash 0.6.0 – please update to Just More Parts 1.2.0". Pre-releases of 1.2.0 load.
- Old share codes (PW1 and PW2, also the ones Just More Parts 1.1.x wrote) stay readable. Propwash 0.6.0 writes PW3.
- If your add-on needs Propwash 0.6.0, declare `"propwash": ">=0.6.0-"` (Fabric) or `versionRange = "[0.6.0-alpha,)"` (NeoForge), so that pre-releases of 0.6.0 count.

## Links

- Add-on guide: [`docs/api/addon-guide.md`](docs/api/addon-guide.md)
- What's new in add-on API 1.7: [`docs/addon-api-1.7.md`](docs/addon-api-1.7.md)
- Share code part tables: [`docs/sharecode/tables.md`](docs/sharecode/tables.md)
- Race data for servers and bots: [`docs/api/race-data.md`](docs/api/race-data.md)
- Website: [propwashfpv.com/docs/api](https://propwashfpv.com/docs/api/)
- Bug report: [issue form](https://github.com/LardTom/propwash-fpv/issues/new?template=bug_report.yml)

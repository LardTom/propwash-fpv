# Parts-API — Datenformat, Register und Speicherverträglichkeit (Implementierer A)

Ergänzt `docs/design/parts-api.md` (Vertrag, Abschnitte 4–8). Hier steht die vollständige **Schema-Referenz** der
Bauteil-JSON-Dateien, die Regeln des Registers, der Items, der Platzhalter und des Handshakes. Alles gilt für
Propwash 0.4.0 / API 1.3.0, JSON-Format `1`.

---

## 1. Eingebaute Teile sind JSON-Dateien

Alle 33 eingebauten Teile und die drei Zellchemien liegen seit 0.4.0 als Daten im Mod:

```
common/src/main/resources/data/propwash/propwash_parts/
  chemistry/lipo.json, lihv.json, lion.json
  frame/whoop_65.json … long_range_7.json
  stack/ motor/ prop/ video/ battery/ accessory/
```

- Die Konstanten `FrameType.WHOOP_65` usw. bleiben; sie laden beim Klassenladen ihre Datei über den Klassenlader
  (`build.BuiltinParts`, Ressourcenpfad `data/propwash/propwash_parts/<kategorie>/<pfad>.json`). Fehlt eine Datei oder
  ist sie ungültig, bricht der Start mit einer Meldung ab, die Datei und Feld nennt.
- **Bitgleich zu 0.3.2:** JUnit `parts/PartJsonTest#builtinDefinitionsAreIdenticalTo032` vergleicht jede geladene
  Definition (`record#toString`, Gleitkommazahlen in voller Genauigkeit) mit dem Schnappschuss
  `common/src/test/resources/de/lardtom/propwash/build/builtin_definitions.txt`, der vor der Umstellung aus den
  Java-Konstanten erzeugt wurde. `sim/AirframeGoldenTest` bleibt unverändert grün.
- Wer einen eingebauten Wert **absichtlich** ändert (z. B. Hitzegrenzen eines Motors, Abschnitt 9.1 des Vertrags), ändert
  die JSON-Datei und erneuert die betroffene Zeile im Schnappschuss.
- `tools/fastcheck.sh test` legt `common/src/main/resources` des eigenen Arbeitsbaums vor den Klassenpfad, damit JUnit
  die Dateien findet (Gradle macht das von selbst).

## 2. Laden aus einem Addon

`PropwashApi.parts().loadBundled(modId)` liest **alle** Dateien `data/<namensraum>/propwash_parts/<kategorie>/<pfad>.json`
aus den Ressourcen des Mods `modId` (Fabric: `ModContainer#getRootPaths`, NeoForge: `IModFile#getContents`, über den
Plattformdienst `IPlatformHelper#readModResources`).

| Regel | Verhalten |
|---|---|
| Id | `<namensraum>:<dateiname ohne .json>`; Pfad nur `[a-z0-9_.-]` |
| Kategorie | Ordnername: `chemistry`, `frame`, `stack`, `motor`, `prop`, `video`, `battery`, `accessory`; alles andere ist ein Fehler |
| Unterordner | Fehler (`…/motor/extra/x.json`) |
| Reihenfolge | `chemistry` → `frame` → `stack` → `motor` → `prop` → `video` → `battery` → `accessory`, innerhalb einer Kategorie nach Pfad (Namensraum, dann Datei) |
| Andere Ordner unter `data/` | werden ignoriert (Rezepte, Tags usw. lädt Minecraft) |
| Propwash selbst | `loadBundled("propwash")` überspringt `data/propwash/propwash_parts` (eingebaute Teile sind schon da) und lädt nur fremde Namensräume, z. B. das Beispiel-Addon `propwash_example` im Quell-Set `devtest` |
| Fehler | Erst werden **alle** Dateien gelesen; gibt es Fehler, wirft `loadBundled` eine `parts.PartDataException` mit allen Fehlern (Datei – Feld: Meldung) und meldet nichts an |
| Warnungen | unbekannte Felder → Log `Propwash-Bauteildaten von <mod>: <datei> – <feld>: unbekanntes Feld wird ignoriert` |
| Rückgabe | Anzahl angemeldeter Definitionen (Teile + Zellchemien) |

`PropwashEvents.PART_REGISTERED` feuert für jedes Teil (nicht für Zellchemien).

## 3. Gemeinsame Konventionen

- **Ids ohne Namensraum** (`"m22"`, `"lipo"`, `"carbon_plate"`) meinen `propwash:…`. Fremde Ids immer voll schreiben.
- Zahlen sind JSON-Zahlen; ganze Zahlen dürfen keine Nachkommastellen haben (`"kv": 1750`, nicht `1750.5`).
- `[min, max]`-Bereiche (`cells`, `props_mm`, `battery_fit.cells`) dürfen bei `cells` auch eine einzelne Zahl sein
  (`"cells": 6` = `[6, 6]`).
- Positionen `[x, y, z]` in Millimetern, Rahmen-Koordinaten wie im Vertrag (Ursprung Rahmenmitte auf Stack-Auflage,
  +X rechts, +Y oben, −Z vorn).
- `"format": 1` ist optional; ein höheres Format bricht mit „Propwash aktualisieren“ ab.
- Kodieren (`parts.PartJson#encode`) schreibt nur Werte, die von den Standards abweichen oder Pflicht sind;
  Dekodieren von kodierten Definitionen ergibt **gleiche** Records (JUnit für alle eingebauten Teile und Beispiele).
  `parts.JsonText#pretty` formatiert wie die eingebauten Dateien (kurze Objekte einzeilig, Zahlen ohne Exponent).

### 3.1 Felder jedes Teils (`PartBasics`)

| JSON | Typ | Standard | Bedeutung |
|---|---|---|---|
| `mass_g` | Zahl | **Pflicht** (0, 20000] | Masse |
| `stack_size` | ganze Zahl | 0 = Kategorie (Props 64, sonst 16) | Stapelgröße des Items (1–99) |
| `sort` | ganze Zahl | 1000 | Reihenfolge in `values()`, Kreativ-Tab |
| `creative` | bool | `true` | im Kreativ-Tab des Addons |
| `durability` | Zahl | 1,0 (Props: Material) | Teiler der Crash-Verschleißdosis |
| `recycle` | `"none"` oder `{"item": id, "count": n}` | fehlt = Kategorie | Recycling defekter Teile (`count` Standard 1) |
| `visual.accent` | `"#rrggbb"` | – | Akzentfarbe (Akkuhülle, prozedurale Form) |
| `visual.render_model` | Id | – | anderes Drohnen-Modell |

### 3.2 `frame`

| JSON | Typ | Standard |
|---|---|---|
| `role` | `whoop, toothpick, cinewhoop, freestyle, race, long_range, cinelifter, x_class` | `freestyle` |
| `shape` | `true_x, stretched_x, squashed_x, deadcat, h_frame, custom` | `true_x` |
| `wheelbase_mm` | ganze Zahl 20–2000 | **Pflicht** |
| `max_prop_mm` **oder** `props_mm: [min, max]` | ganze Zahlen ≤ 400 | **Pflicht** (eins von beiden) |
| `motor_mounts`, `stack_mounts` | Liste von Ids (oder eine Id) | **Pflicht** |
| `accessory_mounts` | Liste aus `top`, `bottom` | leer |
| `arms` | `{front_left, front_right, rear_left, rear_right}` je `{"pos": [x,y,z], "spin": "cw"\|"ccw"}` | leer = True-X aus dem Radstand; `spin` Standard „props in“ |
| `battery` | `{"on_top": bool, "plate_mm": Zahl}` | `on_top` = Radstand ≥ 100; `plate_mm` oben 0,13·Radstand, unten −0,05·Radstand |
| `electronics_mm` | Zahl | 0,05·Radstand |
| `camera` | `{"pos": [x,y,z], "tilt_deg": 0–80}` | kein `pos`; `tilt_deg` 20 |
| `center_of_mass_mm` | `[x,y,z]` | `[0,0,0]` |
| `mass_split` | `{"arms", "plate", "duct"}` (Summe 1, `duct` Standard 0) | offen 0,6/0,4/0, geduckt 0,2/0,3/0,5 |
| `accessory_anchors` | `{"top"\|"bottom": {"pos": [x,y,z], "tilt_deg", "length_mm"}}` | leer = Ersatzformel |
| `aero` | `{"frontal_area_m2", "drag_coefficient"` **Pflicht**`, "side_area_m2", "top_area_m2"}` | Seiten/Draufsicht aus der Stirnfläche; für Whoops `top_area_m2` = (1,2 × Rumpf + 1,66 × Blattfläche) / `drag_coefficient`, nicht der ganze Umriss (`realism/flight-feel-gravity.md` §10) |
| `duct` | `{}` oder `true`, optional `thrust_gain, clearance_mm, translational_lift_gain, wash_loss, wash_noise, restitution, friction` | fehlt = offene Props |
| `impact_tolerance` | Zahl (0, 100] m/s | 8,0 |
| `battery_fit` | `{"cells": [min,max]` **Pflicht**`, "max_mass_g"` **Pflicht**`, "preferred_min_cells", "preferred_min_capacity_mah"}` | bevorzugt = min. Zellen, 0 mAh |

### 3.3 `stack`

| JSON | Typ | Standard |
|---|---|---|
| `mount` | Id | **Pflicht** |
| `esc_continuous_a` | Zahl | **Pflicht** |
| `cells` | `[min, max]` 1–16 | **Pflicht** |
| `all_in_one` | bool | `false` |
| `esc_burst_a`, `esc_resistance_ohm`, `brownout_v`, `board_size_mm` | Zahl | aus den Altformeln (Vertrag 5.3) |

### 3.4 `motor`

| JSON | Typ | Standard |
|---|---|---|
| `stator` | Text (`"2207"`, `"2306.5"`) | **Pflicht** |
| `stator_mm` | `[d, h]` | aus `stator` (erste zwei Ziffern Durchmesser, Rest Höhe) |
| `kv` | ganze Zahl 50–100000 | **Pflicht** |
| `mount` | Id | **Pflicht** |
| `max_current_a`, `resistance_ohm` | Zahl | **Pflicht** |
| `cells` | `[min, max]` | **Pflicht** |
| `prop_mount` | Id | `propwash:universal` |
| `thermal` | `{heat_capacity_j_per_k, cooling_w_per_k, cooling_w_per_k_per_rad_s, derate_start_c, max_temp_c, wear_per_s_at_max}` | Standards 130 °C / 150 °C / 0,002, Wärmewerte aus der Masse |

### 3.5 `prop`

| JSON | Typ | Standard |
|---|---|---|
| `diameter_mm` | ganze Zahl 10–400 | **Pflicht** |
| `pitch_in` | Zahl | **Pflicht** |
| `blades` | ganze Zahl 2–8 | **Pflicht** |
| `ct`, `cp` | Zahl (0, 1) | **Pflicht** |
| `material` | `polycarbonate, glass_fiber_nylon, carbon_fiber_nylon, carbon` | `polycarbonate` |
| `stiffness` | Zahl | Material-Standard |
| `mount` | Id | `propwash:universal` |
| `reverse_thrust` | Zahl 0,2–1,0 | leer = normaler Prop (Modell 0,55) – Schub rückwärts im Verhältnis zu vorwärts bei gleicher Drehzahl im 3D-Modus; symmetrische 3D-Props ≈ 0,95–1,0 (seit Propwash 0.5.0) |

### 3.6 `battery`

| JSON | Typ | Standard |
|---|---|---|
| `cells` | ganze Zahl 1–12 | **Pflicht** |
| `capacity_mah` | ganze Zahl 50–50000 | **Pflicht** |
| `cell_resistance_ohm` | Zahl | **Pflicht** |
| `c_rating` | ganze Zahl 1–300 | **Pflicht** |
| `chemistry` | Id einer Zellchemie | `propwash:lipo` |
| `pack_mm` | `[l, b, h]` | aus Masse und Dichte |
| `connector` | Text ≤ 16 | leer |

### 3.7 `video`

| JSON | Typ | Standard |
|---|---|---|
| `link` | `analog`, `digital` | **Pflicht** |
| `latency_ms`, `range_blocks` | ganze Zahl | **Pflicht** |
| `image_quality` | Zahl (0, 1] | **Pflicht** |
| `camera_width_mm` | Zahl 8–40 | 19 |
| `camera_fov_deg` | Zahl 60–180 | leer = Standard des Systems (analog 120°, digital 130°); **horizontales** Sichtfeld der Kamera, seit API 1.4 das FPV-Sichtfeld und das Sichtfeld der Analyse (gezeichnet höchstens 160°) |
| `vtx_power_mw` | ganze Zahl 0–10000 | 0 = 200 mW; Nennleistung des Senders (ab API 1.6 wirksam): bei ihr gelten `range_blocks` und `power_w`; Drohnen ohne eigene Einstellung senden mit Auto |
| `vtx_levels_mw` | Liste ganzer Zahlen | leer = aus `vtx_power_mw` abgeleitet; einstellbare Stufen in mW, aufsteigend, 1–10000, höchstens 8, muss `vtx_power_mw` enthalten (dann ist `vtx_power_mw` Pflicht), ab API 1.6 (`docs/design/vtx-power.md`) |
| `power_w` | Zahl 0,1–60 | aus Verbindung, Masse und Sendeleistung (Flugmodell-Spezifikation 18.1); Leistungsaufnahme des Videosystems in W für die Energiebilanz, ab API 1.2 |

### 3.8 `accessory`

| JSON | Typ | Standard |
|---|---|---|
| `slot` | `top`, `bottom` | **Pflicht** |
| `size_mm` | `[b, h, t]` | **Pflicht** |
| `flight_battery_w`, `frontal_area_m2` | Zahl | 0 |
| `leds` | ganze Zahl 0–64 | 0 |
| `lens` | `{"x_mm", "length_mm"}` | 0/0 |
| `action_cam` | `{max_height, max_fps, stabilization, horizon_levelling, own_battery_wh, record_w, max_record_w, fov_deg, image_quality, low_light_noise, sensor_in}`, nur `top` | – ; `max_record_w` Standard `record_w`; `sensor_in` Zahl oder Bruch `"1/2.8"` |

### 3.9 `chemistry`

`full_v`, `nominal_v`, `empty_v`, `cutoff_v`, `storage_v`, `density_g_cm3` (alle **Pflicht**), optional `usable_fraction`
(0,5–1; nutzbarer Anteil der Kapazität bis zur Landespannung, ab API 1.2; ohne Angabe 0,88 bei Leerspannung unter 3,25 V,
0,85 ab 4,3 V Vollspannung, sonst 0,80) und `ocv_shape`
(**Pflicht**, 12 Werte von 0 bis 1 monoton auf dem Raster 0, 0,05, 0,1, 0,2 … 1,0).

## 4. Register (`build.PartIndex`)

- `register(definition)` prüft: Namensraum ≠ `propwash` (gehört den eingebauten Teilen), Pfad `[a-z0-9_.-]`, Schlüssel
  ≤ 128 Zeichen (`MAX_KEY_LENGTH`), Id noch frei **über alle Kategorien**, Akku-Chemie angemeldet. Fehler:
  `IllegalArgumentException` mit deutscher Meldung. Nach dem Einfrieren: `IllegalStateException`.
- `registerChemistry` prüft dieselben Id-Regeln.
- **Einfrieren** (`parts.PartsModule.freeze()`, idempotent): NeoForge im `FMLCommonSetupEvent` (per `enqueueWork` auf dem
  Hauptthread), Fabric beim ersten von `SERVER_STARTING` / `CLIENT_STARTED`. Danach: Teil-Items gesperrt,
  Querprüfung als Warnungen im Log (Motor-/Stack-Halterung ohne passenden Rahmen, Prop-Aufnahme ohne Gegenstück,
  Propellergröße ohne Rahmen, Recycling-Item unbekannt, Teil ohne Item), Zusammenfassung
  `Propwash-Bauteile eingefroren: 5 frame, 3 stack, …; aus Addons: {ns=n}; Fingerabdruck …`, danach genau einmal
  `PropwashEvents.REGISTRY_FROZEN`.
- `PartIndex.hashOf(definition)` (16 Hex-Zeichen) und `fingerprint()` (SHA-256 über alle Definitionen) dienen dem
  Handshake. Damit der Fingerabdruck nicht von der Lade-Reihenfolge der Mods abhängt, stehen Zellchemien aus Add-ons
  (wie Teile aus Add-ons) nach den eingebauten, sortiert nach Namensraum und Pfad; das gilt auch für
  `PartRegistry.chemistries()`. Bis 0.5.0 standen sie in Anmelde-Reihenfolge: Mit zwei Add-on-Chemien (NeoForge baut
  Mods parallel, Fabric mischt sie im Entwicklungslauf) wurden Spieler mit gleicher Ausstattung zufällig mit „eingebaute
  Teile anders“ abgewiesen (JUnit `PartIndexTest`).

## 5. Items fremder Teile (`parts.AddonItems`)

- `registerItems(ns)` stellt für jedes angemeldete, nicht eingebaute Teil des Namensraums ohne Item eine Anmeldung in die
  Warteschlange; `registerItem(id, props)` für ein einzelnes Teil mit eigenen `Item.Properties` (auf die
  Propwash-Standards angewandt). Item = `content.PartItem` bzw. `content.BatteryItem`, gleiche Id wie das Teil,
  Komponenten `propwash:part`, Propwash-Tooltip-Stil, bei Akkus voller Ladestand, Stapelgröße nach `stack_size`.
- **Fabric:** `PropwashFabric#onInitialize` flusht die Warteschlange direkt nach den eigenen Registern; danach werden
  weitere Anmeldungen sofort eingetragen (auch aus `onInitialize` eines später geladenen Addons).
  **NeoForge:** Flush im `RegisterEvent` für `minecraft:item`; danach und nach dem Einfrieren wirft jede Anmeldung.
- Lookups: `item(id)`, `stack(id)`, `partOf(stack)`. `ContentItems.stack(part)` fällt für fremde Teile auf
  `AddonItems` zurück, für Platzhalter auf `ItemStack.EMPTY`.

## 6. Platzhalter für fehlende Teile

- Unbekannte Schlüssel **fremder** Namensräume in Speicherständen und im Netz werden zu Platzhaltern
  (`PartIndex.missing(kind, id[, slot])`, je Kategorie/Id/Halterung genau eine Instanz): Definition des Ersatzteils
  (Rahmen `freestyle_5`, Stack `stack_30`, Motor `m2207_1750`, Prop `p51_3b`, Video `analog_race`, Akku `b6s_1300`,
  Zubehör oben `action_cam_light`, unten `led_strip`), aber die **Original-Id**; `missing() == true`,
  `name() == "MISSING"`, `translationKey()` = `propwash.parts.missing_name`. Nie in `values()`/`all()`/`byId`.
- Unbekannte Schlüssel **im Namensraum `propwash`** und falsch einsortierte Teile (Motor-Schlüssel im Rahmen-Feld,
  Kamera an der unteren Halterung) bleiben Fehler wie in 0.3.2 (kaputte Daten, kein fehlendes Mod).
- Betroffen: `DroneBuildCodecs` (NBT und Netz, auch das Zubehör je Halterung), `BoundDrone`, `race.GhostLook`
  (unbekanntes fremdes Zubehör wird im Geist weggelassen), `PartRef#resolveOrMissing()` (`resolve()` bleibt streng).
- Folgen: `DroneBuild.problems()` beginnt mit `MISSING_PART` (nicht baubar, nicht scharfschaltbar),
  `missingParts()` listet sie; Zerlegen gibt für Platzhalter nichts zurück und setzt keine Komponenten auf leere Stapel
  (Name/Lack/Tuning gehen mit einem fehlenden Rahmen/Stack verloren); die Werkbank warnt am Zerlege-Slot
  (`propwash.parts.workbench.missing_warning`).
- Byte-Format unverändert: eingebaute Teile schreiben weiter nur den Pfad, das Netz bleibt `stringUtf8` (Höchstlänge nur
  eine Prüfung, JUnit `core/MissingPartsCodecTest#networkBytesStayTheSameAndPlaceholdersSurviveTheWire`).

## 7. Handshake `propwash:parts` und Protokoll 7

- `Constants.PROTOCOL_VERSION = 7`.
- Nach der Protokollprüfung (unverändert) folgt die Konfigurations-Aufgabe `propwash:parts`: Der Server schickt
  `network.PartsManifestPayload` (Fingerabdruck + je fremdem Teil und fremder Zellchemie `(kategorie, id, hash)`), der
  Client antwortet mit seinem eigenen. Gleicher Fingerabdruck → weiter. Sonst trennt der Server mit
  `PartsHandshake#message`: „Fehlt bei dir“, „Fehlt auf dem Server“, „Andere Werte“ (zusammen höchstens 8 Ids, Rest als
  „(+n weitere)“) bzw. „Die eingebauten Bauteile unterscheiden sich“, dazu ein Hinweis. Texte mit englischem Fallback.
- Fabric: `ServerConfigurationConnectionEvents.CONFIGURE` hängt die Aufgabe hinter die Protokollprüfung; NeoForge:
  `RegisterConfigurationTasksEvent` + `configurationBidirectional`.

## 8. Tags und Rezepte

`tools/data/survival.py` erzeugt die Item-Tags `#propwash:parts/<kategorie>` (alle eingebauten Teile der Kategorie) und
`#propwash:parts` (verweist auf die sieben Kategorie-Tags). Addons ergänzen sie mit
`data/propwash/tags/item/parts/<kategorie>.json` und `"replace": false` und schalten ihre Rezepte damit frei.
Jeder Item-Tag braucht den Anzeigenamen `tag.item.propwash.<pfad mit Punkten>` in `common/src/lang/parts/` (sonst warnt
Fabric im Entwicklungsmodus „Untranslated Item Tags“); `PartRulesTest#everyItemTagHasATranslation` prüft das.

## 9. Sprachschlüssel (A)

`common/src/lang/parts/`: neue Probleme `propwash.build.issue.missing_part|prop_too_small|prop_mount`, Platzhalter
(`propwash.parts.missing_*`), Tooltip-Zeilen für Werte ≠ Standard (`propwash.parts.tooltip.*`: Propellerbereich,
Bauform bei eigener Arm-Geometrie, Prop-Aufnahme, Hitzegrenzen, Material, Stecker, Packmaße, Kamera-Sichtfeld,
Sendeleistung, Haltbarkeit), Addon-Kurzbeschreibung `propwash.parts.help.addon_summary` (wenn `<ns>.help.<pfad>.summary`
fehlt), Handshake-Meldungen, Werkbank-Erweiterungen, Analysewerte `motor_temp`/`props_in_view` und die Warnungen
`motor_thermal`/`props_in_view` für B, Tag-Namen `tag.item.propwash.parts` und `tag.item.propwash.parts.<kategorie>`.
Namensräume: Rahmen-Kurzname `<ns>.frame_short.<pfad>` (fehlt → Teilname), Halterungen `<ns>.mount.motor|stack|prop.<pfad>`
(fehlt → Pfad in Großbuchstaben), Zellchemie `<ns>.chemistry.<pfad>`, Hilfe `<ns>.help.<pfad>.summary|what.N|use.N`;
alle über `build.PartNames`.

## 10. Werkbank und Import (A-Seite)

- Erweiterungen (`api.client.WorkbenchExtension`): `content.client.WorkbenchHost` setzt `WorkbenchContext` um; bis zu
  zwei Knöpfe links neben „Lackierung“, weitere hinter „⋯“ (Theme-Fenster `WorkbenchMoreScreen`). Passen die Knöpfe nicht
  in die Kopfleiste (mindestens 64 px Titel), wandern sie hinter „⋯“; Beschriftungen über 80 px werden mit „…“ gekürzt.
  Kind-Fenster (`openChild`) kehren mit Esc zur Werkbank zurück und schließen sich, wenn der Server das Menü schließt
  (`WorkbenchChildren`, Client-Tick).
- `content.ImportService.apply(player[, containerId], request)` setzt Vertrag 11.3 um (Ebenen PARTS → PAINT → TUNE,
  jede ganz oder gar nicht). Überleben: fehlende Teile je Stück in `missingParts`, Akkus mit dem höchsten Ladestand
  und Teile im besten Zustand zuerst, verdrängte Teile ins Inventar (fehlt Platz → `NOT_ALLOWED`, nichts geändert).
  Kreativ: Teile werden neu erzeugt. Lack über das Lack-Fenster (`DroneWorkbenchMenu#applyPaint`); fehlende Farbstoffe
  je Farbstoff, eigene Farben als `custom`. Tuning als ausstehendes Tuning im Menü (`DroneWorkbenchMenu.PendingTune`),
  die entnommene Drohne trägt es (`DronePiloting.sanitize`). `containerId = -1` nimmt die offene Werkbank.
- Events: `DRONE_ASSEMBLED` beim Entnehmen aus dem Ergebnis-Slot, `DRONE_DISASSEMBLED` beim Zerlegen (Server).

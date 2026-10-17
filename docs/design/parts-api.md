# Parts-API — Vertrag für Propwash FPV 0.5.2 (API 1.6.0)

Status: **verbindlicher Vertrag**, entstanden für die vier Implementierer (A–D) der Version 0.4.0 und mit jeder
API-Nebenversion fortgeschrieben (zuletzt 1.6.0 mit Propwash 0.5.2; die neuen Bausteine für Zubehör, Aktionen,
Physik, Flughilfen, Gimbal und HUD stehen in [`addon-api-1.6.md`](addon-api-1.6.md)). Architekt: Branch
`feature/parts-api`, Basis-Commit „Parts-API: Vertrag …“ (dieser Commit). Wer vom Vertrag abweichen muss, meldet einen
„Architektur-Wunsch“ im Bericht statt still umzubauen.

Dieses Dokument gilt zusammen mit `docs/FOUNDATION.md` (§15 geht allem Älteren vor) und `docs/CONVENTIONS.md`. Im Code
stehen keine Kommentare; alle Erklärungen zur API stehen hier.

---

## 0. Kurzfassung

- Propwash 0.4.0 macht Bauteile **offen**: Ein anderes Mod (zuerst das Addon „Propwash: Just More Parts“) meldet
  Rahmen, Stacks/ESCs, Motoren, Props, Akkus (auch Li-Ion/LiHV bis 12S), Video-Einheiten und Zubehör an — per Java
  oder als JSON in seinen eigenen Ressourcen — und bekommt dafür Items, Werkbank, Analyse, Flugmodell, Rendering,
  Tooltips, einen eigenen Kreativ-Tab, Flugschule und Speichern „geschenkt“.
- Die öffentliche API liegt in `de.lardtom.propwash.api` (stabil, SemVer `API_VERSION = "1.6.0"`), Einstieg
  `PropwashApi` (gemeinsam) und `api.client.PropwashClientApi` (nur Client).
- Ein **Addon-Sharecode** (lebt im Addon, nicht in Propwash) braucht genau: `BuildAccess.read(ItemStack)` →
  `DroneData` (Teile + Lackierung + Tuning + Name), `BuildAccess.createDrone(DroneData)`, Tuning-Gruppen
  (`TuneGroup`), OSD-Layout (`OsdLayoutAccess`) und den server-geprüften Import (`ImportRequest` → `ImportResult`,
  Ebenen PARTS/PAINT/TUNE, seit 1.4 auch NAME). Schalter-/Tastenbelegung, Funkprofile, Akkustand und Verschleiß sind nie Teil davon.
- **Phase 0 ist bereits umgesetzt** (dieser Commit): Die sieben Bauteil-Enums sind registerbasierte `final`-Klassen
  gleichen Namens mit denselben Konstanten und Feldern; `values()` gibt es weiter. Nachweis: alle 1166 alten Tests grün,
  9000 Kombinationen aller eingebauten Teile plus Presets mit Zubehör sind **bitgleich** zu 0.3.2
  (`sim/AirframeGoldenTest`).
- Garantie für alle Implementierer: Jedes vorhandene Teil, Item, Speicherformat, Rezept, jede Beutetabelle, jedes Preset
  und jedes Flugverhalten bleibt **genau** wie in 0.3.2.

---

## 1. Ziele, Nicht-Ziele, Garantien

### 1.1 Ziele
1. Ein Addon kann **ohne Änderung an Propwash** neue Teile aller sieben Kategorien anmelden, inklusive neuer
   Rahmen-Bauformen (True-X, Stretched-X, Squashed-X, Deadcat mit Props außerhalb des Kamerabilds, H-Frame, Ducts,
   X-Class 10–13″), neuer Motorhalterungen, Zellchemien und Akkus bis mindestens 8S.
2. Jede Stelle in Propwash, die heute Bauteile benutzt, verarbeitet jedes angemeldete Teil (Abschnitt 8).
3. Die API ist versioniert, dokumentiert, in stabil und intern getrennt (Abschnitt 3).
4. Sharecode-Unterstützung (Abschnitt 11) samt Überlebensmodus-Regeln (Teile kosten Teile, Lack kostet Farbstoff).

### 1.2 Nicht-Ziele in API 1.x (später möglich)
- Andere Motorzahlen als 4 (Hexa/Okto). `MotorArm.COUNT = 4` ist fest.
- Neue Videosysteme oder Brillen (HDZero/Walksnail als eigenes System). `VideoLink` bleibt `ANALOG`/`DIGITAL`.
- Bauteile aus Datenpaketen der Welt oder `/reload` (Items brauchen die Teile beim Start).
- Neue OSD-Elemente, neue Lack-Plätze, neue Zubehör-Halterungen (`AccessorySlot` bleibt `TOP`/`BOTTOM`).
- Addon-Einträge in Propwashs Truhen-Beutetabellen (das Addon injiziert eigene Tabellen wie Propwash selbst).
- Eigene Item-Unterklassen für Teile (Propwash stellt die Teil-Items).
- VTX-Leistung aus dem Teil wirkt nur informativ (Tooltip). Das Kamera-Sichtfeld (`camera_fov_deg`) wirkt seit API 1.4
  auf FPV-Bild und Analyse (Abschnitt 3.1).

### 1.3 Garantien (Abnahmekriterien für jeden Implementierer)
- **Speicherstände**: NBT von Drohnen-Entities (`Build`, `Paint`, `Tune` …), Item-Komponenten (`propwash:drone_build`,
  `propwash:part`, `propwash:bound_drone`, …), Geisterspuren, Bestenlisten laden und schreiben byte-gleich wie 0.3.2,
  solange nur eingebaute Teile vorkommen.
- **Items/Rezepte/Beute**: Item-Ids `propwash:<key>`, alle Rezepte, Fortschritte und Beutetabellen unverändert.
- **Flugverhalten**: `sim/AirframeGoldenTest` bleibt grün (Hashes über alle Felder von `AirframeSpec` für 9000
  Kombinationen und über `BuildAnalysis` der Presets mit Zubehör). Neue Effekte dürfen eingebaute Teile nur dort
  berühren, wo ihr Ergebnis bitgleich bleibt oder ein Test die Neutralität belegt (Abschnitt 9.1).
- **Presets**: `DroneBuild.PRESETS` und ihre Namen unverändert.
- **Netzwerk**: Protokoll steigt auf 7 (neue Payloads), mit API 1.4 auf 8 (Import-Ebene `NAME`, Ziel `NEW_DRONE`). Byte-Format
  von `DroneBuild`/`PartRef` auf dem Draht unverändert.

---

## 2. Architektur

```
Addon (eigenes Mod)
   │  benutzt nur ↓
de.lardtom.propwash.api            stabil (Records, Interfaces, Fassade)  ← Architekt, Pflege D
   │  delegiert an ↓
de.lardtom.propwash.apiimpl        Fassade, Erweiterungen, Import-Netzwerk ← D
de.lardtom.propwash.parts          Register-Interna, JSON, Items, Sync     ← A
de.lardtom.propwash.build          Laufzeit-Teile (FrameType …), PartIndex ← A
de.lardtom.propwash.sim            Flugmodell, Analyse                      ← B
de.lardtom.propwash.drone.client.render  Rendering, Assets                   ← C
```

### 2.1 Zwei Sichten auf ein Teil
- **Definition** (`api.part.*Definition`, Record): alle Daten eines Teils, so wie ein Addon es beschreibt. Stabil.
- **Laufzeit-Teil** (`build.FrameType`, `StackType`, `MotorType`, `PropType`, `BatteryType`, `VideoType`,
  `AccessoryType`, gemeinsam `build.PartInfo`): intern, trägt die Definition (`definition()`) und die alten
  öffentlichen Felder (`wheelbaseMm`, `kv`, `diameterMm` …), damit aller bestehender Code weiterläuft. Genau **eine**
  Instanz je Id (Identitätsvergleich wie früher bei Enums; `equals` wird nicht überschrieben).
- `DroneBuild` bleibt der Record aus Laufzeit-Teilen. Addons sehen ihn nie; sie benutzen `api.build.BuildSpec` (Ids).

### 2.2 Phase 0 (in diesem Commit erledigt) — Regeln für alle
| Früher (Enum) | Jetzt | Regel |
|---|---|---|
| `enum FrameType` usw. | `final class FrameType implements PartInfo` | `PartInfo` ist `sealed` (7 Klassen); Muster-`switch` über `PartInfo` bleibt vollständig |
| `FrameType.values()` | gibt es weiter: alle angemeldeten Teile, **eingebaute zuerst in alter Reihenfolge**, danach fremde sortiert nach `sortOrder`, Namensraum, Pfad | nie annehmen, dass `values()` nur eingebaute enthält; für „nur eingebaute“ `part.builtin()` filtern |
| `switch (frame) { case WHOOP_65 -> … }` | verboten (kompiliert nicht) | Daten gehören in die Definition; Übergangsweise Identitätsvergleich `frame == FrameType.WHOOP_65` |
| `EnumMap<FrameType, …>` / `EnumSet` | verboten | `HashMap`/`LinkedHashMap`/`HashSet` |
| `ordinal()`/`name()` | `name()` existiert (alte Konstantennamen, fremde: Pfad in Großbuchstaben), `ordinal()` nicht | Reihenfolge nur über `values()`/`all()`; Schlüssel über `key()`/`id()` |
| `key()` | unverändert (`m2207_1750`); fremde Teile: `ns:pfad` | siehe Abschnitt 4 |
| `MountSize.Motor`/`Stack` (Enums) | internierte Klassen je `Identifier` (`M08`, `M14`, `M22`, `M28`, `WHOOP_AIO`, `S20`, `S30` bleiben) | `switch` über Halterungen verboten |
| `BatteryType.Chemistry` (Enum) | registerbasierte Klasse (`LIPO`, `LIHV`, `LION`) mit alten Feldern | Kurven/Dichte kommen aus `CellChemistry` |
| Tabellen `BatteryFit.rule`, `AccessoryAnchor.TABLE`, `sim.FrameGeometry`, `Batteries.packColor`, `FallbackShapes` (Farben, Kamerabreite) | lesen aus der Definition | Werte bitgleich übernommen |

Neu in `build`: `PartIndex` (Register-Kern, thread-sicher, Lesen ohne Sperre), `PartInfo#id()/definition()/builtin()`,
`PartIndex.detached(def)` (Laufzeit-Teil **ohne** Anmeldung, nur für Tests, Vorschauen und Werkzeuge).

---

## 3. Stabilität und Versionierung

### 3.1 Versionen
- `PropwashApi.API_VERSION = "1.6.0"` ist unabhängig von der Mod-Version (Propwash 0.5.2, `docs/RELEASING.md`). 1.1
  ergänzt `BuildAccess#cameraView`, 1.2 die eigenen Kreativ-Tabs der Addons (Abschnitt 6.5), `VideoDefinition#powerW`,
  `CellChemistry#usableFraction` und die Energie-Kennzahlen in `BuildStats` (Flugmodell-Spezifikation Kap. 18), 1.3 die
  Flugschul-Verknüpfungen für Items (Abschnitt 12.3), 1.4 die Truhen-Beute ohne Loader-Code (Abschnitt 6.6), die
  Server-, Analyse- und Client-Ergänzungen aus Abschnitt 3.4 und die Kamera (Abschnitt 11.6): `CameraView#uptiltRange()`
  (`CameraView.UptiltRange` mit `minDeg`, `maxDeg`, `defaultDeg`; der alte Konstruktor ohne Bereich bleibt und setzt
  0–80°), `BuildAccess#cameraView(spec, uptiltDeg)`, `BuildAccess#uptiltRange(frame)` und `TuneSpec.CAMERA_UPTILT`;
  dazu wirkt `camera_fov_deg` der Videoteile jetzt auf FPV-Bild und Analyse (Verhaltensänderung, abfragbar mit
  `isCompatible("1.4")`). 1.5 (Propwash 0.5.0) ergänzt `FlightSchoolScenes.TRICKS` für die Lektion „Freestyle-Tricks“
  (in `FlightSchoolScenes.ALL` nach `RACE`), `FlightSchoolScenes.GRIPPER` für die Lektion „Greifer“ (nach `ACTION_CAM`),
  `FlightSchoolScenes.MODE_3D` für die Lektion „3D-Modus“ (nach `HOLD`), `FlightSchoolScenes.GATE_FREE` für die Lektion
  „Gates frei drehen“ (nach `RACE`), `FlightSchoolScenes.COURSE_ELEMENTS` für die Lektion „Flaggen, Slalom und
  Tauchwürfel“ (nach `GATE_FREE`), den optionalen Umkehrschub der Props
  (`PropDefinition#reverseThrust()`, Builder `reverseThrust(r)`, JSON `reverse_thrust`; der Konstruktor ohne das Feld bleibt)
  sowie `BuildAccess#partCompatibility()` (Default-Methode) für die Welt-Regel `propwash:part_compatibility`: `check` und
  `createDrone` folgen ihr, bei abgeschalteter Regel stehen die aufgehobenen Probleme unter den Warnungen
  (`docs/design/part-freedom.md` §6). Addons für 1.0 bis 1.4 laufen unverändert weiter. 1.6 (Propwash 0.5.2,
  Netzprotokoll 16) ergänzt die Halterung vorne (`AccessorySlot.FRONT`), Zubehör-Verhalten mit Inventar und
  Aktionen (`PropwashApi.accessories()`, Paket `api.accessory`), Drohnen-Abfragen und Stöße (`PropwashApi.drones()`,
  `api.drone`), Flughilfen und Kräfte im Flugmodell (`PropwashApi.flight()`, reines Java in `api.flight`), die Events
  `DRONE_HIT_ENTITY`, `DRONE_DAMAGED`, `ACCESSORY_ACTION` und im Client HUD-Elemente, Gimbal und Eingaben
  (`api.client.hud`, `api.client.camera`, `ClientInput`); Vertrag in [`addon-api-1.6.md`](addon-api-1.6.md). Dazu kommen
  die Leistungsstufen des Videosenders: `VideoDefinition#vtxLevelsMw()` (Builder `vtxLevels(int...)`, JSON
  `vtx_levels_mw`; die Konstruktoren ohne das Feld bleiben), und `vtx_power_mw` wirkt jetzt als Nennleistung
  (Abschnitt 5.7, `docs/design/vtx-power.md`). Addons für 1.0 bis 1.5 laufen unverändert weiter.
- **SemVer**: MAJOR bei jeder inkompatiblen Änderung stabiler Typen; MINOR bei Ergänzungen; PATCH bei Fehlerbehebungen
  ohne API-Änderung. `PropwashApi.isCompatible("1.4")` = gleiche Hauptversion und mindestens 1.4.
- Addons prüfen beim Start `PropwashApi.requireVersion("justmoreparts", "1.0")` (wirft mit klarer Meldung) und deklarieren
  die Loader-Abhängigkeit (Fabric `"depends": {"propwash": ">=0.4.0"}`; NeoForge `[[dependencies.<mod>]]`
  `modId="propwash" type="required" versionRange="[0.4.0,)" ordering="AFTER" side="BOTH"`).

### 3.2 Was ist stabil?
| Stabil (API 1.x) | Pakete/Typen |
|---|---|
| Fassade | `api.PropwashApi`, `api.ApiVersion`, `api.client.PropwashClientApi` |
| Bauteil-Modell | alles in `api.part` |
| Baupläne & Import | alles in `api.build` |
| Items & Kreativ-Tabs | `api.registry.PartItems`, `api.registry.CreativeEntries`, `api.registry.ExtensionTab`, `api.registry.TabSection` |
| Events | `api.event.PropwashEvents`, `api.event.PropwashEvent#register` (`fire` ist intern) |
| Client-Erweiterungen | `api.client.*` |
| **Exportierte interne Typen** (stabil wie API, dürfen nur ergänzt werden) | `ui.client.config.ConfigTab`, `ConfigTabHost`, `StepSlider`; `ui.client.theme.Palette`, `Metrics`, `ThemeText`, `ThemeDraw`, `ThemedWidgets`, `ThemedScreen`; `ui.client.widget.FlatButton`, `ScrollPanel`; `guide.scene.*` (Szenen-DSL: `SceneBuilder`, `Scene`, `ActorSpec`, `BlockSpec`, `KeyToken`, `Anchor`, `Ease`, `DronePart`, `CameraSpec`, `ScenePos`) |

Alles andere ist **intern** und darf sich in jeder Version ändern — ausdrücklich auch `build.*`, `core.*`,
`content.*`, `parts.*`, `apiimpl.*`, `sim.*`.

### 3.3 Evolutionsregeln
- **Records** (Regel seit API 1.4; sie ersetzt die frühere Formulierung „kanonische Konstruktoren sind nicht stabil“,
  die dem Handbuch und dem Beispiel-Addon widersprach): Es gibt zwei Arten von Records.
  - **Definitionen** — `FrameDefinition`, `StackDefinition`, `MotorDefinition`, `PropDefinition`, `BatteryDefinition`,
    `VideoDefinition`, `AccessoryDefinition`, `ActionCamDefinition`, `AirframeGeometry`, `FrameAero` — baut ein Addon nur
    über Builder (`FrameDefinition.builder(id)…build()`) oder `with…`-Methoden. Ihre kanonischen Konstruktoren sind von der
    Stabilitätszusage ausgenommen; neue Komponenten kommen in MINOR-Versionen dazu.
  - **Wertrecords** — alle übrigen Records in `api.*`: `ApiVersion`, `Vec3Mm`, `MotorArm`, `PackSize`, `AnchorSpec`,
    `MassSplit`, `BatteryFitRule`, `DuctSpec`, `MotorThermal`, `CellChemistry`, `RecycleYield`, `PartVisual`, `PartBasics`,
    `BuildSpec`, `PaintSpec`, `TuneSpec`, `DroneData`, `DronePreset`, `ImportRequest`, `ImportResult`, `BuildCheck`,
    `BuildStats`, `CameraView` (samt `PropDisc`) und `api.client.OsdLayoutSpec`. Ihr kanonischer Konstruktor ist **stabil**:
    Bekommt ein Wertrecord in einer MINOR-Version eine Komponente, bleibt der bisherige Konstruktor als zusätzlicher
    Konstruktor mit der alten Signatur erhalten (neue Komponenten neutral, so wie `BuildStats` seit 1.2 und 1.4,
    `CellChemistry` seit 1.2 und `PartBasics` seit 1.4). Wo es billig ist, gibt es zusätzlich Fabriken und Builder
    (`PartBasics.of`, `MotorThermal.builder()`/`limits`, `DuctSpec.STANDARD`, `ImportRequest.wholeBuild/onHeldDrone/…`,
    `DroneData.of`, `DronePreset.of`).
  Zugriffsmethoden aller Records bleiben.
- **Enums** (`PartKind`, `FrameRole`, `FrameShape`, `PropMaterial`, `TuneGroup`, `ImportStatus`, …) dürfen in MINOR
  Konstanten dazubekommen. Addons schreiben `switch` mit `default`.
- **Interfaces**: neue Methoden nur mit `default`-Implementierung.
- **JSON**: Feld `"format": 1`. Neue optionale Felder in MINOR; unbekannte Felder werden mit Warnung (Datei + Feld)
  ignoriert; fehlende Pflichtfelder brechen den Start mit klarer Meldung ab.
- **Veralten**: frühestens eine MINOR-Version angekündigt (hier und in `docs/api/changes.md`, im CHANGELOG als Zeile
  unter *Compatibility*), entfernt erst in der nächsten MAJOR.

### 3.4 Neu in API 1.4 (Versionsgeschichte)
Alles rein ergänzend; eingebaute Teile und Addons für 1.0–1.3 verhalten sich genau wie vorher (Ausnahme: siehe
`ImportLayer.NAME`). Addons, die etwas davon benutzen, prüfen `PropwashApi.requireVersion(modId, "1.4")`.

| Ergänzung | Signatur | Abschnitt |
|---|---|---|
| Truhen-Beute ohne Loader-Code | `PropwashApi.loot()` → `LootInjections#inject(ResourceKey<LootTable> ziel, ResourceKey<LootTable> addonTabelle)`, `#tablesFor(ziel)` | 6.6 |
| Motor-Wärme ohne Konstruktor | `MotorThermal.builder()` → `heatCapacity(double)`, `cooling(double)`, `coolingPerRadS(double)`, `limits(double derate, double max)`, `wearAtMax(double)`, `build()` | 5.4 |
| Seltenheit ohne Java-Item-Code | `PartBasics#rarity(): Optional<Rarity>`, `PartBasics#withRarity(Rarity)`, JSON `"rarity"` | 5.1, 6.4 |
| Steifigkeit im Tooltip | Zeile `propwash.parts.tooltip.stiffness`, wenn die Steifigkeit vom Material-Standard abweicht | 5.5 |
| Analyse-Kennzahlen | `BuildStats#propwashNoise()`, `fullThrottleCellVoltage()`, `rollAuthorityRadS2()`, `pitchAuthorityRadS2()`, `yawAuthorityRadS2()`; `BuildStats.WARNINGS` | 9.6 |
| Durchgebrannter ESC | `PropwashEvents.ESC_BURNED` (`EscBurned#onEscBurned(ServerPlayer player, ItemStack drone, DroneData data, int cells)`) | 12.6 |
| Zustand der Teile | `BuildAccess#partCondition(ItemStack drone, PartKind kind): OptionalDouble` | 11.2 |
| Farbstoff-Kosten vorab | `BuildAccess#paintCost(ItemStack drone, PaintSpec target): Map<String, Integer>` | 11.2 |
| Akku mit Teilladung | `PartItems#stack(Identifier partId, float charge)`; Komponente `propwash:battery_charge` stabil | 6.4 |
| Name beim Import | `ImportLayer.NAME` | 11.3 |
| Drohne erzeugen ohne Befehl | `ImportTarget.NEW_DRONE`, `ImportRequest.createDrone(DroneData)`, `PropwashClientApi.sendCreateDrone(DroneData): CompletableFuture<ImportResult>` | 11.3, 12.5 |
| Import auf die gehaltene Drohne | `ImportRequest.onHeldDrone(DroneData data, Set<ImportLayer> layers)` | 11.3 |
| JSON-Codec öffentlich | `PartRegistry#toJson(PartDefinition)`, `#toJson(CellChemistry)`, `#fromJson(PartKind, Identifier, JsonObject)`, `#chemistryFromJson(Identifier, JsonObject)` | 6.7 |
| Sprachtexte ohne Client | `PropwashApi.text(String languageCode, String key): Optional<String>` | 4.3 |
| Tuning-Schlüssel dokumentiert | Katalog aller Schlüssel mit Bereich, Einheit und Wertcodes | 11.5 |
| Stabilitätsregel für Records | Wertrecords mit stabilem kanonischem Konstruktor | 3.3 |
| Werkbank-Kopf | `WorkbenchContext#headerSpace()`, `#buttonWidth(Component)`, `#addButton(label, shortLabel, tooltip, onPress)`, `#addIconButton(Identifier sprite, Component tooltip, Runnable onPress)` | 12.2 |
| Fehlende Teile | `WorkbenchContext#missingParts(DroneData): List<Identifier>` | 12.2 |
| OSD-Layout prüfen | `OsdLayoutAccess#validate(OsdLayoutSpec): boolean` | 12.5 |
| Konfigurator-Tab öffnen | `PropwashClientApi.openConfigTab(String tabId, @Nullable Screen parent): boolean` | 12.5 |
| Vorschau im festen Maßstab | `PropwashClientApi.renderPreview(…, float yawDegrees, float pixelsPerMillimetre)`, `BuildAccess#footprintMm(BuildSpec): double` | 12.5 |
| Theme-Textfeld | `ui.client.widget.TextField` (`hint(Component)`) | 12.5 |
| Shift-Hilfe ohne Taste | `PropwashClientApi.forceShiftHelp(@Nullable Boolean shown)` (Bildschirmfotos, Selbsttests; `null` = Taste) | 12.5 |
| Kategorie-Basismodelle, Lack auf Ersatzformen, große Modelle | `propwash:item/part_base/<kategorie>`; `transformation` zugesichert, Hülle bis ±8 Modell-Blöcke | 10.4 |

Netzwerk: `Constants.PROTOCOL_VERSION` 7 → **8** (neue Bedeutung der Import-Bitmasken: Ebene `NAME` = Bit 3, Ziel
`NEW_DRONE` = 2). Client und Server mit 7 und 8 trennt schon der Handshake mit klarer Meldung (FOUNDATION §5).

---

## 4. Identifikation, Schlüssel, Namen

### 4.1 Ids
- Jedes Teil hat eine `Identifier`-Id `<namespace>:<pfad>`; Pfad nur `[a-z0-9_.-]`, **ohne `/`**. Ids sind über alle
  Kategorien eindeutig (ein Teil = ein Item mit derselben Id).
- Eingebaute Teile behalten ihre Keys: `propwash:whoop_65`, `propwash:m2207_1750`, `propwash:p51_3b`,
  `propwash:b6s_1300`, `propwash:stack_30`, `propwash:digital_hd`, `propwash:led_strip` usw.
- Halterungen, Zellchemien und Presets haben ebenfalls Ids: `propwash:m08|m14|m22|m28` (Motor),
  `propwash:whoop_aio|s20|s30` (Stack), `propwash:universal` (Prop-Aufnahme), `propwash:lipo|lihv|lion`,
  Presets `propwash:whoop|cinewhoop|freestyle|racer|long_range` (Konstanten in `api.part.Mounts`,
  `BatteryDefinition.LIPO/LIHV/LION`).

### 4.2 Speicherschlüssel (`PartInfo#key()`, `PartIndex.keyOf/idOfKey`)
- Schreiben: Namensraum `propwash` → nur der Pfad (`m2207_1750`), sonst die volle Id (`justmoreparts:m2806_1300`).
- Lesen: ohne `:` → `propwash:<key>`, mit `:` → volle Id. Beides wird überall akzeptiert.
- Benutzt von: `DroneBuildCodecs` (NBT + Netz), `PartRef` (`{"category":"motor","key":"…"}`), `BoundDrone`,
  `race.GhostLook`, Geister-Klasse (`FrameType#key`), Bestenlisten-Klassen, `AirframeSpec#buildKey`.
- Höchstlänge eines Schlüssels im Netz: `DroneBuildCodecs.MAX_KEY_LENGTH = 128` (Byte-Format unverändert).

### 4.3 Sprachschlüssel
Wie die Namen selbst aufgebaut sind (eingebaute und Addon-Teile gleich), legt [`part-naming.md`](part-naming.md) fest.

| Zweck | Namensraum `propwash` (unverändert) | anderer Namensraum `ns` |
|---|---|---|
| Teilname | `part.propwash.<pfad>` | `part.<ns>.<pfad>` (**Pflicht**) |
| Shift-Hilfe | `propwash.help.<pfad>.summary/what.N/use.N` | `<ns>.help.<pfad>.summary` (empfohlen), `.what.N`, `.use.N` (optional; fehlende werden übersprungen) |
| Rahmen-Kurzname (Klasse, Bestenliste, Bindung) | `propwash.content.frame_short.<pfad>` | `<ns>.frame_short.<pfad>` (fehlt → Teilname) |
| Motorhalterung | `propwash.content.mount.motor.<pfad>` | `<ns>.mount.motor.<pfad>` |
| Stack-Halterung | `propwash.content.mount.stack.<pfad>` | `<ns>.mount.stack.<pfad>` |
| Prop-Aufnahme | — | `<ns>.mount.prop.<pfad>` |
| Zellchemie | `propwash.content.tooltip.chemistry.<pfad>` | `<ns>.chemistry.<pfad>` |
| Preset | `propwash.content.preset.<pfad>` | `<ns>.preset.<pfad>` (`DronePreset#translationKey`) |
| Flugschul-Szene `ns:name` | `propwash.guide.scene.<id>…` | `<ns>.guide.scene.<name>.title/description/step.…` |

**Texte ohne Client (API 1.4):** `PropwashApi.text(languageCode, key)` liest `assets/<namensraum>/lang/<code>.json` aus den
Dateien **aller geladenen Mods** — auf Client und dediziertem Server gleich, unabhängig von Ressourcenpaketen und der
eingestellten Sprache des Spiels. Gelesen wird je Sprachcode einmal (beim ersten Aufruf, danach zwischengespeichert);
die Mods in alphabetischer Reihenfolge ihrer Id, ihre Dateien in Pfad-Reihenfolge, der erste Eintrag eines Schlüssels
gilt. Minecraft selbst (`minecraft`, auf Fabric alle eingebauten Mods) wird nicht gelesen, Vanilla-Texte gibt es also nur
über das Spiel. Leer bei unbekanntem Schlüssel oder fehlender Sprache (kein Rückfall auf `en_us`), ungültiger Code
(`[a-z0-9_]{2,16}`, Großbuchstaben werden klein) → `IllegalArgumentException`. Gedacht für Server-Werkzeuge und Exporte
(z. B. den Web-Export eines Addons), nicht für Texte im Spiel — dort bleibt `Component.translatable`.

### 4.4 Asset-Pfade eines Teils `ns:pfad` der Kategorie `k` (`k` = `frame|stack|motor|prop|video|battery|accessory`)
| Datei | Zweck | Pflicht |
|---|---|---|
| `assets/ns/items/<pfad>.json` | Inventar-Symbol (Item-Model-Definition); darf den Sonder-Renderer `propwash:part` benutzen (Abschnitt 10.4) | ja |
| `assets/ns/items/drone_render/k/<pfad>.json` (+ Varianten) | Modell an der Drohne | nein (sonst prozedurale Form) |
| `assets/ns/models/part/k/<pfad>.json` | das eigentliche Modell | nein |
| `assets/ns/drone_paint.json` | Tint-Ebenen der Lack-Varianten dieses Namensraums | nein |
| `assets/ns/drone_layout.json` | optische Feinlage je Rahmen/Motor dieses Namensraums | nein |
| `assets/ns/lang/*.json` | Namen und Hilfen | ja (Name) |

---

## 5. Bauteil-Modell (`api.part`)

Einheiten: Masse g, Längen mm (Geometrie) bzw. m² (Flächen), Strom A, Widerstand Ω, Spannung V, Temperatur °C,
Winkel Grad. Alle Records prüfen ihre Werte im kompakten Konstruktor und werfen `IllegalArgumentException` mit
deutscher Meldung inklusive Id.

### 5.1 Gemeinsam: `PartDefinition`, `PartBasics`, `PartVisual`, `RecycleYield`
`sealed interface PartDefinition permits FrameDefinition, StackDefinition, MotorDefinition, PropDefinition,
BatteryDefinition, VideoDefinition, AccessoryDefinition` mit `id()`, `kind()` (`PartKind`), `basics()`, `massGrams()`.

| Feld (`PartBasics`) | JSON | Standard | Wirkung |
|---|---|---|---|
| `massGrams` | `mass_g` | Pflicht, (0, 20000] | Masse, Schwerpunkt, Trägheit, Tooltips |
| `stackSize` | `stack_size` | 0 = Kategorie-Standard (Props 64, sonst 16) | Item-Stapelgröße |
| `sortOrder` | `sort` | 1000 (eingebaute: 0…n) | Reihenfolge in `values()`, Kreativ-Tab, Listen |
| `creativeListed` | `creative` | `true` | im Kreativ-Tab des Addons |
| `durability` | `durability` | 1.0 (Props: Material-Standard) | Teiler der Verschleiß-Dosis bei Abstürzen (B) |
| `recycle` | `recycle` | leer = Kategorie-Standard | `{"item": "propwash:copper_coil", "count": 1}` oder `"none"`; Standard: Rahmen → Carbonplatte, Stack/Video → Platine, Motor → Kupferspule, Akku → LiPo-Zelle, Prop/Zubehör → nichts; `whoop_65` → nichts |
| `visual.accentRgb` | `visual.accent` (`"#e8b622"`) | leer | Akzentfarbe: Akku-Hülle (Ladegerät, Item), Motorglocke/Prop der prozeduralen Form |
| `visual.renderModel` | `visual.render_model` | leer = `ns:drone_render/k/<pfad>` | anderes Drohnen-Modell benutzen (z. B. ein Propwash-Modell) |
| `rarity` (API 1.4) | `rarity` (`common`, `uncommon`, `rare`, `epic`) | leer = Item-Standard (`common`) | Seltenheit (Farbe des Namens) des Teil-Items, das Propwash für das Addon anlegt (`registerItems`/`registerItem`); die `UnaryOperator<Item.Properties>` eines `registerItem` wird danach angewandt und gewinnt. Java: `PartBasics#withRarity(Rarity)` |

### 5.2 Rahmen — `FrameDefinition`
**Koordinaten (wie `drone_layout.json`)**: Ursprung = Mitte des Rahmens auf Höhe der Stack-Auflage, **+X rechts,
+Y oben, −Z vorn** (Nase), Millimeter. Arme immer in der Reihenfolge **vorne links, vorne rechts, hinten links,
hinten rechts** (`MotorArm.FRONT_LEFT…REAR_RIGHT`, dieselben Positionen wie `PartWear`, Werkbank und Lack-Plätze).

| Feld | JSON | Standard | Bedeutung / Wirkung |
|---|---|---|---|
| `role` | `role` | `freestyle` | `whoop, toothpick, cinewhoop, freestyle, race, long_range, cinelifter, x_class`; `tricksByDefault()` steuert Tricks „Automatisch“ (B) |
| `shape` | `shape` | `true_x` | `true_x, stretched_x, squashed_x, deadcat, h_frame, custom`; beschreibend (Tooltip nur bei eigener Arm-Geometrie) |
| `wheelbaseMm` | `wheelbase_mm` | Pflicht, 20–2000 | Diagonalabstand Motor–Motor; ohne `arms` Grundlage des alten True-X-Modells |
| `minPropMm`, `maxPropMm` | `props_mm: [min, max]` oder `max_prop_mm` | min 0, max Pflicht ≤ 400 | Propellerbereich (neu: `PROP_TOO_SMALL`) |
| `motorMounts` | `motor_mounts` | Pflicht | Ids der Motorhalterungen |
| `stackMounts` | `stack_mounts` | Pflicht | Ids der Stack-Halterungen |
| `accessoryMounts` | `accessory_mounts` | leer | `top`, `bottom` |
| `geometry.arms` | `arms: {front_left: {pos: [x,y,z], spin: "cw"}, …}` | leer = altes True-X aus dem Radstand | vier Motorpositionen und Drehrichtungen (Standard „props in“: VL cw, VR ccw, HL ccw, HR cw) |
| `geometry.batteryOnTop` | `battery.on_top` | Radstand ≥ 100 | Akku oben (Riemen, Freestyle, Long Range) oder unten (Whoop, Race-Rahmen wie `race_5`; seit 0.5.0 hängt ihn auch der Renderer dann unter den Rahmen) |
| `geometry.batteryPlateMm` | `battery.plate_mm` | oben 0,13·Radstand, unten −0,05·Radstand | Höhe der Akkuauflage; unten = Unterseite von Bodenplatte und Pad (Race 5″: −2,8 mm) |
| `geometry.electronicsMm` | `electronics_mm` | 0,05·Radstand | Schwerpunkthöhe Stack+Video |
| `geometry.cameraMm` | `camera.pos` | leer = altes Objektiv (0,015 m über dem Schwerpunkt, 0,7·Motorabstand vor; liegt meist über der Propebene und zeigt kaum Props) | FPV-Kameraposition (Objektiv); alle eingebauten Rahmen setzen sie (0.4.0): wenige Millimeter unter der Propebene, Whoop knapp über den Ducts |
| `geometry.cameraTiltDeg` | `camera.tilt_deg` | 20 (eingebaute: Werte aus `drone_layout.json`) | Standard-Uptilt 0–80° |
| `geometry.frameCenterOfMassMm` | `center_of_mass_mm` | `[0,0,0]` | Schwerpunkt der Rahmenmasse (Deadcat, H-Frames) |
| `geometry.massSplit` | `mass_split: {arms, plate, duct}` | offen 0,60/0,40/0, Duct 0,20/0,30/0,50 | Massenverteilung für die Trägheit |
| `geometry.accessoryAnchors` | `accessory_anchors: {top: {pos, tilt_deg, length_mm}}` | leer = Ersatzformel (`AccessoryAnchor.fallback`) | Zubehör-Anker je Halterung |
| `aero.frontalAreaM2`, `aero.dragCoefficient` | `aero.frontal_area_m2`, `aero.drag_coefficient` | Pflicht | Luftwiderstand |
| `aero.sideAreaM2`, `aero.topAreaM2` | `aero.side_area_m2`, `aero.top_area_m2` | leer = 1,15× bzw. 2,9×/2,5× Stirnfläche | Seiten-/Draufsichtfläche |
| `duct` | `duct: {}` oder mit Feldern | fehlt = offene Props | Anwesenheit = geduckt; Felder `thrust_gain, clearance_mm, translational_lift_gain, wash_loss, wash_noise, restitution, friction` überschreiben die Konstanten des Flugmodells |
| `impactTolerance` | `impact_tolerance` | 8,0 | Aufprallgeschwindigkeit (m/s) bis zum Bruch |
| `batteryFit` | `battery_fit: {cells: [min,max], max_mass_g, preferred_min_cells, preferred_min_capacity_mah}` | Pflicht | Regel für `BatteryFit` (OK/WEAK/CELLS_LOW/CELLS_HIGH/TOO_HEAVY) |

Größenklasse: `nominalPropInches()` = größter Prop in halben Zoll gerundet (330 mm → 13″). X-Class: `role: x_class`,
Radstand ≥ 800 mm, Props 10–13″ (254–330 mm).

**Eingebaute Rahmen (Phase 0, bitgleich):**
| Id | Radstand | g | Props | Motor / Stack | Duct | Akku oben / Platte / Elektronik | Tilt | Fit |
|---|---|---|---|---|---|---|---|---|
| `whoop_65` | 65 | 4,0 | ≤31 | m08 / whoop_aio | ja | nein / −3 / 4 | 12 | 1–1S, ≤10 g |
| `cinewhoop_3` | 142 | 150 | ≤76 | m14 / s20 | ja | ja / 20 / 10 | 10 | 3–4S, ≤120 g |
| `freestyle_5` | 225 | 125 | ≤130 | m22 / s30 | nein | ja / 30 / 12 | 28 | 4–6S (6S bevorzugt), ≤230 g |
| `race_5` | 215 | 88 | ≤130 | m22 / s30, s20 | nein | ja / 25 / 10 | 38 | 4–6S (6S bevorzugt), ≤230 g |
| `long_range_7` | 295 | 170 | ≤180 | m28, m22 / s30, s20 | nein | ja / 32 / 14 | 20 | 6S, ≤500 g, ≥1300 mAh bevorzugt |

Alle eingebauten Rahmen haben **keine** `arms` (Flugmodell rechnet wie bisher True-X aus dem Radstand).
`race_5` trug bis zum 10.10.2026 `shape: stretched_x` nur beschreibend, weil sein Modell gestreckt war; seit dem
Playtest ist das Modell True-X wie das Flugmodell und `shape` ist `true_x` (`realism/motors-props.md` §12.4).

### 5.3 Stack/ESC — `StackDefinition`
| Feld | JSON | Standard | Wirkung |
|---|---|---|---|
| `mount` | `mount` | Pflicht | Stack-Halterung |
| `escContinuousAmps` | `esc_continuous_a` | Pflicht | Dauerstrom je Motor; Warnung `ESC_UNDERSIZED`; Flugmodell: ESC-Widerstand, Stromgrenzen |
| `minCells`, `maxCells` | `cells: [min, max]` | Pflicht, 1–16 | Spannungsbereich; Warnung `STACK_VOLTAGE` (wie bisher nur Warnung) |
| `allInOne` | `all_in_one` | `false` | AIO (Tooltip, Form) |
| `escBurstAmps` | `esc_burst_a` | 1,3× Dauerstrom | Stromgrenze im Flugmodell |
| `escResistanceOhm` | `esc_resistance_ohm` | 0,003 + 0,15/Dauerstrom | Phasenwiderstand |
| `brownoutVoltage` | `brownout_v` | 2,9 V bei 1S-fähigen Stacks, sonst 5,5 V | Brownout |
| `boardSizeMm` | `board_size_mm` | aus der Halterung (whoop_aio 26, s20 27, s30 36, sonst 30) | Größe der prozeduralen Platine |

Eingebaut: `aio_whoop` (3,2 g, whoop_aio, 5 A, 1–2S, AIO), `aio_20` (7 g, s20, 25 A, 2–6S, AIO), `stack_30` (18 g, s30,
55 A, 3–6S).

### 5.4 Motor — `MotorDefinition` + `MotorThermal`
| Feld | JSON | Standard | Wirkung |
|---|---|---|---|
| `statorLabel` | `stator` | Pflicht (`"2207"`, `"2306.5"`, `"4214"`) | Anzeige |
| `statorDiameterMm`, `statorHeightMm` | `stator_mm: [d, h]` | aus dem Label (erste zwei Ziffern = Durchmesser, Rest = Höhe) | Glocke, Rotorträgheit, Rotorebene, Form |
| `kv` | `kv` | Pflicht | Drehzahlkonstante |
| `mount` | `mount` | Pflicht | Motorhalterung |
| `maxCurrentAmps` | `max_current_a` | Pflicht | Leerlaufstrom (3 %), Warnungen |
| `resistanceOhm` | `resistance_ohm` | Pflicht | Kupferverluste, Erwärmung |
| `minCells`, `maxCells` | `cells: [min, max]` | Pflicht | Warnungen `MOTOR_OVERVOLTAGE/UNDERVOLTAGE` |
| `propMount` | `prop_mount` | `propwash:universal` | passt nur zu Props mit gleicher Aufnahme (universal passt zu allem; neu: `PROP_MOUNT`) |
| `thermal.heatCapacityJPerK` | `thermal.heat_capacity_j_per_k` | 0,5 J/(g·K) × Masse | Wärmekapazität |
| `thermal.coolingWPerK` | `thermal.cooling_w_per_k` | 0,075 × (Masse/33 g)^⅔ | Kühlung im Stand |
| `thermal.coolingWPerKPerRadS` | `thermal.cooling_w_per_k_per_rad_s` | 1,5·10⁻⁴ × (Masse/33 g)^⅔ | Fahrtwind-Kühlung je rad/s |
| `thermal.derateStartC` | `thermal.derate_start_c` | 130 | ab hier regelt der ESC die Leistung zurück |
| `thermal.maxTempC` | `thermal.max_temp_c` | 150 | Überhitzung (Event `MOTOR_OVERHEAT` wie bisher), Leistung auf Minimum |
| `thermal.wearPerSecondAtMax` | `thermal.wear_per_s_at_max` | 0,002 | Verschleiß des Motors je Sekunde bei `maxTempC` (0 = aus) |

`MotorThermal` baut ein Addon mit `MotorThermal.limits(derate, max)` (nur Grenzen) oder seit API 1.4 mit
`MotorThermal.builder().heatCapacity(J/K).cooling(W/K).coolingPerRadS(W/(K·rad/s)).limits(derate, max).wearAtMax(1/s).build()`;
nicht gesetzte Werte bleiben auf dem Standard (`MotorThermal.DEFAULT`), die Prüfungen sind die des Records.

Eingebaut (Stator, KV, g, Halterung, A, Ω, Zellen): `m0802_22000` (0802, 22000, 2, m08, 3,5, 0,20, 1S), `m1404_4600`
(1404, 4600, 9, m14, 18, 0,14, 3–4S), `m2004_3000` (2004, 3000, 12, m14, 24, 0,10, 3–4S), `m2207_1750` (2207, 1750, 33,
m22, 45, 0,055, 6S), `m2306_1860` (2306.5, 1860, 31, m22, 42, 0,060, 6S), `m2807_1300` (2807, 1300, 47, m28, 45, 0,070,
6S); alle `MotorThermal.DEFAULT`, `propMount` universal.

### 5.5 Propeller — `PropDefinition` + `PropMaterial`
| Feld | JSON | Standard | Wirkung |
|---|---|---|---|
| `diameterMm`, `pitchInch`, `blades` | `diameter_mm`, `pitch_in`, `blades` | Pflicht (10–400 mm, 2–8 Blätter) | Schub, Leistung, Scheibe, Form, Klang |
| `thrustCoefficient`, `powerCoefficient` | `ct`, `cp` | Pflicht | Schub-/Leistungsbeiwert |
| `material` | `material` | `polycarbonate` | `polycarbonate, glass_fiber_nylon, carbon_fiber_nylon, carbon` |
| `stiffness` | `stiffness` | Material-Standard (PC 1,0 · GFN 1,2 · CFN 1,4 · Carbon 2,2) | Flattern/Propwash-Rauschen, Wirkungsgradverlust bei Last (B); 1,0 = heutiges Verhalten |
| `basics.durability` | `durability` | Material-Standard (1,0 · 0,8 · 0,9 · 1,4) | Verschleiß bei Abstürzen |
| `reverseThrust` (OptionalDouble, Builder `reverseThrust(r)`, seit 1.5) | `reverse_thrust` | leer = normaler Prop (Modell 0,55) | Umkehrschub im 3D-Modus: Schub rückwärts `r·kT`, Drehmoment `(0,6 + 0,4·r)·kQ`; Tooltip-Zeile „Umkehrschub (3D-Modus)“ nur mit Angabe; symmetrische 3D-Props ≈ 1,0 (`docs/design/mode-3d.md`) |
| `mount` | `mount` | `propwash:universal` | Prop-Aufnahme |

Eingebaut: `p31_4b`, `p31_3b`, `p3_3b`, `p51_3b`, `p7_2b` mit den bisherigen Werten, alle Polycarbonat, Steifigkeit
1,0, universal. Seit 0.5.0 dazu `p51_29_3b` (5,1×2,9×3, 4,4 g, `ct` 0,122, `cp` 0,065), der flache Race-Prop des
Race-Presets; er steht als letztes eingebautes Prop im Register (`realism/motors-props.md` §12).

Tooltip (API 1.4): Weicht `stiffness` vom Standard des Materials ab, zeigt der Daten-Abschnitt die Zeile
`propwash.parts.tooltip.stiffness` („Steifigkeit: ×1,8“); eingebaute Props und Props mit Material-Standard bleiben ohne
diese Zeile. Die Wirkung im Flugmodell meldet `BuildStats#propwashNoise()` (Abschnitt 9.6).

### 5.6 Akku — `BatteryDefinition` + `CellChemistry`
| Feld | JSON | Standard | Wirkung |
|---|---|---|---|
| `cells` | `cells` | Pflicht, 1–12 | Spannung, Kompatibilität |
| `capacityMah` | `capacity_mah` | Pflicht | Flugzeit, Ladezeit (`ChargeMath`) |
| `cellResistanceOhm` | `cell_resistance_ohm` | Pflicht | Innenwiderstand je Zelle (Spannungseinbruch) |
| `cRating` | `c_rating` | Pflicht | Dauerstrom = C × Ah, Warnung `BATTERY_WEAK` |
| `chemistry` | `chemistry` | `propwash:lipo` | Zellchemie (Kurve, Grenzen, Dichte) |
| `packSize` | `pack_mm: [l, b, h]` | aus Masse/Dichte wie bisher | Trägheit, Hülle, Form |
| `connector` | `connector` | `""` | nur Anzeige (`XT30`, `XT60`, `PH2.0`) |

`CellChemistry`: `id`, `fullCellVoltage` (`full_v`), `nominalCellVoltage` (`nominal_v`), `emptyCellVoltage` (`empty_v`),
`cutoffCellVoltage` (`cutoff_v`), `storageCellVoltage` (`storage_v`), `densityGramsPerCm3` (`density_g_cm3`),
`ocvShape` (`ocv_shape`, 12 Werte 0…1 monoton auf dem SoC-Raster `CellChemistry.SOC_GRID` =
0, 0,05, 0,1, 0,2 … 1,0). Eingebaut: `lipo` (4,20/3,70/3,50/3,30/3,80, 2,0, LiPo-Kurve), `lihv` (4,35/3,80/3,50/3,30/3,85,
2,0, LiPo-Kurve), `lion` (4,20/3,60/3,00/2,80/3,60, 2,4, Li-Ion-Kurve). Neue Chemien per
`PropwashApi.parts().registerChemistry(…)` oder JSON unter `propwash_parts/chemistry/`.

Eingebaut: `b1s_300_hv` (1S 300 mAh LiHV 7,5 g), `b4s_650`, `b6s_1100`, `b6s_1300`, `b6s_4000_lion` mit den bisherigen
Werten; Akzentfarben = bisherige Hüllenfarben.

### 5.7 Video — `VideoDefinition`
| Feld | JSON | Standard | Wirkung |
|---|---|---|---|
| `link` | `link` | Pflicht | `analog` oder `digital` (Brillen-Kompatibilität wie bisher) |
| `latencyMs`, `rangeBlocks`, `imageQuality` | `latency_ms`, `range_blocks`, `image_quality` | Pflicht | Videoverbindung, Bildstörungen |
| `cameraWidthMm` | `camera_width_mm` | 19 | Kamerabreite (Form, Sockel) |
| `cameraFovDeg` | `camera_fov_deg` | leer | **horizontales** Sichtfeld der Kamera (60–180°): seit API 1.4 das FPV-Sichtfeld mit analoger und digitaler Brille und das Sichtfeld der Analyse (gezeichnet höchstens 160°); leer = Standard des Systems (analog 120°, digital 130°). Datenblätter nennen meist das diagonale Sichtfeld: horizontal ≈ 0,8 × diagonal (4:3) bzw. 0,85 × (16:9), `docs/design/realism/power-video.md` §4.4 |
| `vtxPowerMw` | `vtx_power_mw` | 0 | bis 1.5 nur Anzeige; ab 1.6 Nennleistung des Senders: bei ihr gelten `range_blocks` und `power_w`, Drohnen ohne eigene Einstellung senden mit Auto; 0 = 200 mW |
| `vtxLevelsMw` | `vtx_levels_mw` | leer | ab 1.6: einstellbare Stufen in mW, aufsteigend, höchstens 8, muss `vtx_power_mw` enthalten; leer = aus der Nennleistung abgeleitet (analog bis 400 mW 25/100/200/400, darüber 25/200/400/800/1600/2000, digital 25/100/400/700/1200, jeweils bis zur Nennleistung). Mehr Leistung hebt die Link-Reserve um 10·log10(P/P_Nenn) dB, kostet Strom (analog 4,3 W, digital 2,5 W je W Sendeleistung) und Wärme, `docs/design/vtx-power.md` |

### 5.8 Zubehör — `AccessoryDefinition` + `ActionCamDefinition`
Felder wie bisher `AccessoryType`: `slot` (`top`/`bottom`, seit 1.6 auch `front`: hängt unter der Nase, Quader
`y ∈ [−h, 0]`, `z ∈ [−t, 0]` ab dem Anbaupunkt), `size_mm: [b, h, t]`, `flight_battery_w`, `frontal_area_m2`,
`leds`, `lens: {x_mm, length_mm}`, `action_cam: {max_height, max_fps, stabilization, horizon_levelling,
own_battery_wh, record_w, max_record_w, fov_deg, image_quality, low_light_noise, sensor_in}` (nur `top`).

### 5.9 Kompatibilität (`DroneBuild.problems()`/`warnings()`, Besitzer A)
Probleme (nicht baubar), in dieser Reihenfolge: `MISSING_PART` (neu, zuerst), `MOTOR_MOUNT`, `PROP_TOO_LARGE`,
`PROP_TOO_SMALL` (neu), `PROP_MOUNT` (neu), `STACK_MOUNT`, `BATTERY_FRAME`, `ACCESSORY_MOUNT`. Warnungen unverändert:
`MOTOR_OVERVOLTAGE`, `MOTOR_UNDERVOLTAGE`, `STACK_VOLTAGE`, `ESC_UNDERSIZED`, `BATTERY_WEAK`, `BATTERY_UNDERSIZED`.
Neue Einträge kommen **hinter** die bestehenden Enum-Konstanten (keine Ordinal-Verschiebung); eingebaute Kombinationen
erzeugen keine neuen Probleme (min. Prop 0, universelle Aufnahmen).

---

## 6. Registrierung

### 6.1 Lebenszyklus
| Phase | Wann | Erlaubt |
|---|---|---|
| **Offen** | Mod-Initialisierung: Fabric `ModInitializer#onInitialize`, NeoForge `@Mod`-Konstruktor | `parts().register/registerChemistry/loadBundled`, `partItems().registerItems/registerItem`, `builds().registerPreset`, `creativeTab().add`, `extensionTab(ns)` anlegen, `loot().inject` (1.4), `PropwashEvents.*.register` |
| **Client-Init** | Fabric `ClientModInitializer`, NeoForge `@Mod(dist = CLIENT)` | `PropwashClientApi.register…` (Tabs, Werkbank, Szenen) |
| **Eingefroren** | nach allen Initialisierungen (NeoForge `FMLCommonSetupEvent`; Fabric erstes von Server-Start/Client-Start, Details A) | nur lesen; `register` wirft `IllegalStateException` mit Hinweis |

- **Reihenfolge-unabhängig**: Fabric ordnet Entrypoints nicht nach Abhängigkeiten, NeoForge baut Mods parallel auf
  (`modloading-worker`). Alle API-Aufrufe der offenen Phase funktionieren deshalb vor, während und nach
  `PropwashCommon.init()`: `PartIndex` ist statisch und thread-sicher, Item-Anmeldungen landen in einer Warteschlange,
  die Propwash beim eigenen Flush (Fabric: sofort, falls schon geflusht; NeoForge: im `RegisterEvent` für `minecraft:item`)
  abarbeitet.
- Teile werden auf **beiden** Seiten angemeldet (gemeinsame Init), nie nur auf dem Client.
- `PropwashEvents.PART_REGISTERED` feuert für jedes fremde Teil nach dem Anmelden (eingebaute sind beim ersten Zugriff
  schon da), `REGISTRY_FROZEN` genau einmal beim Einfrieren.

### 6.2 Java
```java
public final class JustMoreParts implements ModInitializer {
    public static final String MOD_ID = "justmoreparts";

    @Override
    public void onInitialize() {
        PropwashApi.requireVersion(MOD_ID, "1.0");
        PropwashApi.parts().register(MotorDefinition.builder(Identifier.fromNamespaceAndPath(MOD_ID, "m2806_1300"))
                .mass(52.0).stator("2806.5").kv(1300).mount(Mounts.MOTOR_M28).maxCurrent(48.0).resistance(0.062).cells(6, 8)
                .thermal(MotorThermal.limits(135.0, 155.0))
                .build());
        PropwashApi.parts().loadBundled(MOD_ID);
        PropwashApi.partItems().registerItems(MOD_ID);
        PropwashApi.builds().registerPreset(DronePreset.of(Identifier.fromNamespaceAndPath(MOD_ID, "lr8s"), myLongRange8s));
    }
}
```

### 6.3 JSON in den eigenen Ressourcen
- Pfad: `data/<ns>/propwash_parts/<kategorie>/<pfad>.json`, Kategorie = `frame|stack|motor|prop|video|battery|accessory`
  oder `chemistry`. Id = `<ns>:<dateiname ohne .json>`; keine Unterordner.
- Geladen **nur** über `PropwashApi.parts().loadBundled(modId)` aus den Ressourcen **dieses Mods** (alle Namensräume
  darin), beim Aufruf, in der Reihenfolge: `chemistry`, `frame`, `stack`, `motor`, `prop`, `video`, `battery`,
  `accessory`, je Ordner alphabetisch. Nicht aus Welt-Datenpaketen, kein `/reload`.
- Fehler: ungültiges JSON, fehlende Pflichtfelder oder ungültige Werte → Start bricht mit einer Meldung ab, die alle
  fehlerhaften Dateien mit Feld nennt. Unbekannte Felder → Warnung im Log, Feld wird ignoriert.
- Rückgabe: Anzahl angemeldeter Definitionen.

Beispiel Deadcat (`data/justmoreparts/propwash_parts/frame/deadcat_5.json`):
```json
{
  "format": 1,
  "mass_g": 118.0,
  "role": "freestyle",
  "shape": "deadcat",
  "wheelbase_mm": 230,
  "max_prop_mm": 130,
  "motor_mounts": ["propwash:m22"],
  "stack_mounts": ["propwash:s30", "propwash:s20"],
  "accessory_mounts": ["top", "bottom"],
  "arms": {
    "front_left":  {"pos": [-95.0, 0.0, -60.0], "spin": "cw"},
    "front_right": {"pos": [95.0, 0.0, -60.0], "spin": "ccw"},
    "rear_left":   {"pos": [-80.0, 0.0, 80.0], "spin": "ccw"},
    "rear_right":  {"pos": [80.0, 0.0, 80.0], "spin": "cw"}
  },
  "battery": {"on_top": true, "plate_mm": 30.0},
  "electronics_mm": 12.0,
  "camera": {"pos": [0.0, 14.0, -100.0], "tilt_deg": 30.0},
  "center_of_mass_mm": [0.0, 0.0, 6.0],
  "aero": {"frontal_area_m2": 0.012, "drag_coefficient": 0.9},
  "impact_tolerance": 8.0,
  "battery_fit": {"cells": [4, 6], "max_mass_g": 260.0, "preferred_min_cells": 6, "preferred_min_capacity_mah": 0}
}
```
Beispiel X-Class 13″ (`frame/xclass_13.json`): `"role": "x_class", "wheelbase_mm": 820, "props_mm": [250, 330],
"motor_mounts": ["justmoreparts:m42"], "stack_mounts": ["propwash:s30"], "mass_g": 1450.0,
"aero": {"frontal_area_m2": 0.08, "drag_coefficient": 1.0}, "impact_tolerance": 6.0,
"battery_fit": {"cells": [6, 8], "max_mass_g": 2500.0, "preferred_min_cells": 8, "preferred_min_capacity_mah": 4000}`.

Beispiel 8S-Li-Ion (`battery/b8s_4500_lion.json`):
```json
{"format": 1, "mass_g": 560.0, "cells": 8, "capacity_mah": 4500, "cell_resistance_ohm": 0.014, "c_rating": 12,
 "chemistry": "propwash:lion", "pack_mm": [140.0, 45.0, 70.0], "connector": "XT60", "visual": {"accent": "#1a8f8b"}}
```
Beispiel Motor mit Hitzeangaben (`motor/m4214_400.json`):
```json
{"format": 1, "mass_g": 128.0, "stator": "4214", "kv": 400, "mount": "justmoreparts:m42", "max_current_a": 60.0,
 "resistance_ohm": 0.045, "cells": [6, 12], "thermal": {"derate_start_c": 140.0, "max_temp_c": 160.0}}
```
Weitere Kategorien analog zu den Tabellen in Abschnitt 5 (Feldnamen = Spalte „JSON“). A liefert die vollständige
Schema-Referenz in `docs/design/parts-api-data.md` und prüft sie per JUnit an allen eingebauten Teilen (Kodieren →
Dekodieren → gleiche Definition).

### 6.4 Items
- `PropwashApi.partItems().registerItems(ns)` meldet für **jede** angemeldete Definition des Namensraums `ns` ohne Item
  ein Propwash-Teil-Item mit **derselben Id** an (`PartItem`, bei Akkus `BatteryItem`): Komponente `propwash:part`
  (`PartRef`), Stapelgröße, Akku-Ladestand (voll), Shift-Tooltips, Verschleißbalken, Recycling, Flugschul-Zuordnung
  (`anyPart()`), Werkbank-Slots. `registerItem(id, props)` für ein einzelnes Teil mit eigenen Eigenschaften (z. B.
  Seltenheit). Rückgabe von `registerItems`: Anzahl.
- Der Namensraum der Items ist der der Teile; die Anmeldung übernimmt Propwash für das anmeldende Mod auf beiden Loadern
  (Mechanik: A). Teile ohne Item sind möglich (z. B. nur für Presets), erscheinen aber nicht in der Werkbank.
- Lookups: `item(id)`, `partOf(stack)`, `stack(id)`.
- **Seltenheit (API 1.4):** `basics.rarity` (JSON `"rarity"`) setzt beim Anlegen des Items `Item.Properties#rarity`, bevor
  die eigene `UnaryOperator<Item.Properties>` aus `registerItem` läuft — die eigene Einstellung gewinnt also. Ohne Feld
  bleibt das Item wie bisher. Eingebaute Teile tragen keine Seltenheit.
- **Akku mit Teilladung (API 1.4):** `stack(id, charge)` liefert wie `stack(id)` einen Stapel, setzt aber den Ladestand
  (`charge` 0…1, gerundet wie im Ladegerät auf `Batteries.CHARGE_STEPS` Stufen, ab 0,999 voll). Leer bei unbekannten
  Teilen; `IllegalArgumentException` bei Teilen, die kein Akku sind, und bei Werten außerhalb 0…1 oder `NaN`. Gilt auch
  für eingebaute Akkus. Die Komponente **`propwash:battery_charge`** (`float` 0…1, fehlt = voll) ist seit 1.4 stabil:
  Beutetabellen und Rezepte eines Addons dürfen sie setzen (z. B. `minecraft:set_components` mit
  `{"propwash:battery_charge": 0.35}`).

### 6.5 Presets und Kreativ-Tabs (API 1.2)
- `builds().registerPreset(DronePreset)` — Namensraum ≠ `propwash`; erscheint im Kreativ-Tab seines Addons (wenn
  `creativeListed`) und in `builds().presets()`.
- **Jedes Addon bekommt einen eigenen Tab**, sobald es ein Teil-Item, ein gelistetes Preset oder den Tab selbst
  (`PropwashApi.extensionTab(ns)`) anmeldet. Registry-Id `propwash:main-<ns>`, Titel „Propwash: <Name>“ aus dem
  Propwash-Schlüssel `itemGroup.propwash.extension` („Propwash: %s“) und dem Addon-Schlüssel `itemGroup.<ns>` (fehlt er,
  der Anzeigename des Mods ohne „Propwash:“-Präfix, sonst der Namensraum). Leere Tabs blendet Minecraft aus.
- Inhalt in der Reihenfolge `ExtensionTab#order(TabSection…)` (Standard: `PRESETS`, `FRAMES`, `STACKS`, `MOTORS`,
  `PROPS`, `VIDEO`, `BATTERIES`, `ACCESSORIES`, `ENTRIES`; nicht genannte Abschnitte hängen in dieser Reihenfolge
  hinten an): gelistete Presets des Namensraums (Anmeldereihenfolge), Teil-Items des Namensraums je Kategorie nach
  `sortOrder` → Id, `ENTRIES` = die `add(…)`-Lieferanten des Tabs. Doppelte Stapel werden entfernt.
- Symbol: `icon(Supplier<ItemStack>)` oder `icon(itemId)`, lazy beim ersten Zeichnen; sonst der erste Eintrag, sonst
  die analoge Brille.
- **Position**: direkt hinter dem Propwash-Tab, mehrere Addon-Tabs nach Namensraum. Fabric sortiert Mod-Tabs nach
  Namensraum, dann Pfad (`CreativeModeTabsMixin#paginateTabs`) — `propwash:main-<ns>` folgt `propwash:main`. NeoForge
  bekommt `withTabsBefore(propwash:main)`; bei Gleichstand sortiert `CreativeModeTabRegistry` nach Pfad, und `main-`
  (kleinstes erlaubtes Zeichen nach `main`) gewinnt gegen alle anderen Mod-Tabs außer einem Tab mit Pfad genau `main`
  und Namensraum hinter `propwash`.
- Mechanik (A): `apiimpl.ExtensionTabs` sammelt Tabs, Fabric flusht nach Propwashs Items und registriert spätere direkt
  (bis zum Einfrieren), NeoForge flusht im `RegisterEvent` für `CREATIVE_MODE_TAB`. Schlägt die Registrierung fehl
  oder kommt ein Namensraum erst nach dem Einfrieren, bleiben seine Einträge im Propwash-Tab.
- Propwash-Kreativ-Tab (Besitzer A): unverändert bis zum letzten eingebauten Eintrag; danach nur noch Teil-Items und
  Presets von Namensräumen **ohne** eigenen Tab (Rückfall), dann `creativeTab().add(…)`-Einträge (weiterhin bewusst im
  Propwash-Tab).

### 6.6 Truhen-Beute (API 1.4)
- `PropwashApi.loot()` → `api.registry.LootInjections`: `inject(ResourceKey<LootTable> ziel, ResourceKey<LootTable>
  addonTabelle)` in der offenen Phase, `tablesFor(ziel)` (unveränderliche Liste in Anmeldereihenfolge) jederzeit.
- Wirkung: Beim Laden jeder Beutetabelle hängt Propwash für jede angemeldete Addon-Tabelle einen Pool mit einem
  `minecraft:loot_table`-Eintrag (einmal würfeln) an das Ziel — Fabric über `LootTableEvents.MODIFY` (auch wenn ein
  Datenpaket das Ziel ersetzt), NeoForge über `LootTableLoadEvent` (`LootTable#addPool` vor dem Einfrieren der Tabelle).
  Propwashs eigene Beute bleibt wie sie ist (Fabric-Event, NeoForge `neoforge:add_table`-Modifier).
- Regeln: Addon-Tabelle im eigenen Namensraum (nicht `minecraft`, nicht `propwash`), Ziel ≠ Addon-Tabelle, jedes Paar
  einmal; Verstöße `IllegalArgumentException`, Anmeldung nach dem Einfrieren `IllegalStateException` (Ids in der
  Meldung). Fehlt die Addon-Tabelle in den Daten, meldet Minecraft beim Laden einen ungebundenen Verweis — wie bei
  einem Tippfehler in einer Vanilla-Tabelle.
- Mechanik (A): `apiimpl.LootInjectionsImpl` (wird in `PartsModule.freeze` geschlossen), Fabric `FabricChestLoot`,
  NeoForge `NeoForgeLootInjections`. Prüfung: JUnit `apiimpl.LootInjectionsTest`, Selbsttest „api: Addon-Beute über
  PropwashApi.loot() in Vanilla-Truhen“ (Beispiel-Addon: `propwash_example:inject/simple_dungeon` im Verlies, Fundrate
  30–70 % in 200 Truhen, späte Anmeldung abgelehnt) auf beiden Loadern.

### 6.7 JSON-Codec für Werkzeuge (API 1.4)
`PropwashApi.parts()` gibt Werkzeugen (Prüfstand, Web-Export, Daten-Generatoren) denselben Leser und Schreiber, mit dem
Propwash seine eigenen Teile und `loadBundled` arbeitet:

| Methode | Vertrag |
|---|---|
| `toJson(PartDefinition)` | JSON wie in den Teile-Dateien (`"format": 1`, nur Felder mit eigenem Wert, Reihenfolge fest); neues Objekt je Aufruf |
| `toJson(CellChemistry)` | dasselbe für Zellchemien |
| `fromJson(PartKind, Identifier, JsonObject)` | liest eine Teile-Datei; ungültige oder fehlende Felder → `IllegalArgumentException`, deren Meldung jedes fehlerhafte Feld mit Namen nennt (`"kv: Pflichtfeld fehlt"`); unbekannte Felder werden wie beim Laden ignoriert; das übergebene Objekt bleibt unverändert; meldet nichts an |
| `chemistryFromJson(Identifier, JsonObject)` | dasselbe für Zellchemien |

`fromJson(kind, id, toJson(d))` ergibt für jede Definition wieder `d` (JUnit `ApiAdditionsTest` an allen eingebauten
Teilen). Fremde Implementierungen von `PartRegistry` werfen `UnsupportedOperationException` (Default-Methoden).

---

## 7. Speicher- und Netzverträglichkeit

- **Formate unverändert** (Abschnitt 4.2). Eingebaute Teile schreiben weiter nur den Pfad.
- **Fehlende Teile** (Addon entfernt, Welt eines anderen Servers): Codecs lösen unbekannte Schlüssel nicht mehr als
  Fehler, sondern als **Platzhalter** auf (A): `PartIndex.missing(kind, id[, slot])` erzeugt je Id ein internes Teil
  mit der Definition eines Ersatzteils der Kategorie (Rahmen `freestyle_5`, Stack `stack_30`, Motor `m2207_1750`, Prop
  `p51_3b`, Video `analog_race`, Akku `b6s_1300`, Zubehör nach der Halterung des gespeicherten Eintrags: oben
  `action_cam_light`, unten `led_strip`), aber der **unbekannten Id**,
  `PartInfo#missing() == true`, `name() == "MISSING"`. Platzhalter stehen nie in `values()`/`all()`, werden aber mit
  ihrer Original-Id wieder gespeichert — kommt das Addon zurück, ist die Drohne wieder vollständig.
  Folgen: `DroneBuild.problems()` enthält `MISSING_PART` → nicht scharfschaltbar; Tooltip „Unbekanntes Bauteil
  `<id>` (Mod fehlt)“; Zerlegen in der Werkbank gibt für Platzhalter nichts zurück (Tooltip warnt vorher);
  Rendering nutzt die prozedurale Form. Items fremder Teile verschwinden beim Entfernen des Mods wie bei jedem Mod.
- **Handshake** (A): Nach der bestehenden Protokollprüfung (`ProtocolVersionPayload`, unverändertes Format) folgt eine
  zweite Konfigurations-Aufgabe `propwash:parts` mit `PartIndex.fingerprint()` und je fremdem Teil `(id, kurzer Hash
  der Definition)`. Weicht der Client ab, trennt der Server mit übersetzter Meldung (bis zu 8 Ids, gruppiert „fehlt
  bei dir“, „fehlt auf dem Server“, „andere Werte“). Grund: das Flugmodell läuft auf dem Client.
- `Constants.PROTOCOL_VERSION` = **7** (A); API 1.4 hebt es auf **8** (Abschnitt 3.4).
- **Geisterdateien** (`race.client.GhostStore`): Klassen mit `:` im Dateinamen werden dateisicher (`:` → `~`) (A).
- **Bestenlisten**: Klassen-Schlüssel sind Strings, fremde Rahmen funktionieren; Anzeige über den Rahmen-Kurznamen.

---

## 8. Wer verarbeitet beliebige Teile? (Verbraucher-Vertrag)

| Verbraucher | 0.4.0-Verhalten | Besitzer |
|---|---|---|
| Werkbank-Slots, `BuildSlots`, `DroneAssembly` | Kategorie aus `PartRef`; jedes Teil passt in seinen Slot; Zerlegen gibt die Items fremder Teile zurück (`partItems().stack(id)`) | A |
| Werkbank-Bericht (`WorkbenchReport`) | Werte aus `BuildAnalysis`, neue Felder (Motortemperatur, Props im Bild) nur wenn nicht `NaN`; Probleme/Warnungen inkl. neuer Issues | A (Anzeige), B (Werte) |
| Werkbank-Vorschau, Lackierung, `PaintPicker` | jeder Bauplan, jede Lackierung; Teile ohne Lack-Variante bleiben in Originalfarbe | C |
| Werkbank-Erweiterungen | Knöpfe/Fenster aus `WorkbenchExtension` (Abschnitt 12.2) | A (Host), D (Registry, Netz) |
| Flugmodell, Analyse | alle Definitionsfelder (Abschnitt 9) | B |
| Rendering Entity/Item/Geist/Flugschule | Modelle aus jedem Namensraum, sonst prozedural (Abschnitt 10) | C |
| Tooltips (`PartTooltips`, `ItemHelp`) | generisch aus der Definition; neue Zeilen nur im Shift-Abschnitt „Daten“ und nur für Werte ≠ Standard (eingebaute Tooltips bleiben gleich, seit 1.4 auch die Steifigkeit eines Props); Sprachschlüssel nach 4.3 | A |
| Rezeptbuch | Vanilla-Rezepte des Addons; Empfehlung: Gruppe `propwash_<kategorie>`, Reiter `equipment`, Freischalten über neue Tags `#propwash:parts` bzw. `#propwash:parts/<kategorie>` (A legt sie mit allen eingebauten Teilen an; Addons ergänzen mit `"replace": false`); Zwischenteile `propwash:carbon_plate|copper_coil|circuit_board|lipo_cell|hd_chip` sind stabile Zutaten | A |
| Kreativ-Tab | Abschnitt 6.5 | A |
| Flugschule | Halten-Taste für Items jedes Namensraums; `anyPart()`-Szenen und die Bauteil-Szenen (Akku, Video, Stack, Prop nach Teil-Art) gelten automatisch für fremde Teil-Items; eigene Szenen über `PropwashClientApi.registerFlightSchoolScene`, weitere Zuordnungen über `linkFlightSchoolItems`/`linkFlightSchoolParts` (Abschnitt 12.3) | D |
| Truhen-Beute | unverändert; Addon injiziert eigene Tabellen | — |
| Recycling | `basics.recycle` bzw. Kategorie-Standard | A |
| Ladegerät | jede Kapazität (Ladezeit), Hüllenfarbe aus `accentRgb`; Pack-Signatur ohne Ordinal-Grenze | A |
| Akkutausch, `BatteryFit` | Regel aus `frame.batteryFit`, alle Akkus in `values()` | A |
| Tricks „Automatisch“ | `role.tricksByDefault()` (eingebaute Rollen ergeben exakt die alte Liste) | B |
| Motorton (`MotorTimbre`) | Props > 178 mm klingen tiefer; eingebaute Teile bitgleich | B |
| Videoverbindung | Felder wie bisher | — |
| Geister, Bestenliste | Abschnitt 7 | A |
| Bindung (`BoundDrone`) | Rahmen per Schlüssel, Platzhalter bei fehlendem Rahmen | A |

---

## 9. Flugmodell (B)

### 9.1 Neutralität
- `sim/AirframeGoldenTest` (dieser Commit) hasht alle 77 Felder von `AirframeSpec` für jede Kombination eingebauter
  Rahmen × Stack × Motor × Prop × Video × Akku und `BuildAnalysis` der Presets mit Zubehör. Er muss grün bleiben.
  Neue Felder von `AirframeSpec` fließen nicht in den Hash ein (feste Feldliste im Test).
- Jeder neue Effekt hat einen Neutralwert, den alle eingebauten Teile tragen (Steifigkeit 1,0, Haltbarkeit 1,0,
  keine `arms`, keine Aero-/Duct-Überschreibungen, keine Packmaße, kein Kamera-Override).
- **Hitze**: Drosselung und Hitzeverschleiß gelten für alle Motoren, auch eingebaute. B beweist mit
  `ThermalNeutralityTest`, dass **keine baubare eingebaute Kombination** in den Referenzflügen (Vollgas-Steigflug und
  Schweben bis Akku leer, 25 °C) `derateStartC` erreicht. Scheitert das, hebt B `derateStartC`/`maxTempC` der
  betroffenen eingebauten Motoren in deren Definition an und dokumentiert es. `SimTuning.thermalLimits` (neu, Standard
  an) schaltet Drosselung und Hitzeverschleiß ab (wie `thermalEffects`; seit 0.5.0 Welt-Regel `propwash:hardware_limits`).

### 9.2 Geometrie (nur Rahmen mit `arms`)
- Motorpositionen aus `arms` (Rahmen-Koordinaten mm → Sim-Koordinaten m: `x_sim = −x/1000`, `z_sim = −z/1000`,
  Motor-Index nach Betaflight: 0 = hinten rechts, 1 = vorne rechts, 2 = hinten links, 3 = vorne links;
  `PartWear.simMotor`), Drehrichtung aus `spin`.
- **Schwerpunkt dreidimensional** (inkl. `frameCenterOfMassMm`, Akku, Elektronik, Zubehör); Motorpositionen relativ
  zum Schwerpunkt; Mixer wie ein Betaflight-Custom-Mixer: `mixRoll_i = x_i / max|x|`, `mixPitch_i = −z_i / max|z|`,
  `mixYaw_i = spin_i`; getrennte Hebelarme für Roll und Nick in den Autoritäten.
- **Trägheit aus der Geometrie**: Arme als Stäbe vom Zentrum zu jedem Motor (Anteil `massSplit.arms`), Platte als
  Rechteck über die Hülle der Arme (`massSplit.plate`), Duct-Ringe um jeden Prop (`massSplit.duct`), Akku als Quader
  (`packSize` oder wie bisher aus der Dichte), Motoren/Props als Punktmassen.
- Hülle und Hitbox aus den echten Prop-Kreisen; `hitboxWidth` = größte Ausdehnung.
- Objektiv: `cameraMm` → `AirframeSpec.lensRight/lensUp/lensForward` relativ zum Schwerpunkt; ohne `cameraMm` die
  Altformel (in Phase 0 schon als Felder vorhanden). B stellt `PilotSession#lensForwardOffset`,
  `SpectateSession#lensForwardOffset` und `FpvView` (`LENS_HEIGHT`) auf diese Felder um (bitgleich für eingebaute).

### 9.3 Aerodynamik und Ducts
Seiten-/Draufsichtfläche aus `aero` (sonst Konstanten × Stirnfläche); `DuctSpec`-Felder ersetzen `DUCT_GAIN`,
`DUCT_CLEARANCE`, `K_TL_DUCT`, `WASH_LOSS_DUCT`, `WASH_NOISE_DUCT`, `RESTITUTION_DUCT`, `FRICTION_DUCT` je Rahmen.

### 9.4 Hitze
Modell `sim.Thermal` mit Parametern je Motor aus `MotorThermal` (Standardwerte = heutige Konstanten). Oberhalb
`derateStartC` begrenzt der ESC die Motorleistung linear bis auf 20 % bei `maxTempC` (Hysterese 10 K wie bisher);
Verschleiß `wearPerSecondAtMax × ((T − derateStartC)/(maxTempC − derateStartC))²` je Sekunde oberhalb der Schwelle.
Neue Events `MOTOR_DERATE` (Beginn/Ende) und `MOTOR_THERMAL_WEAR` (Menge, Motor) → `drone.DroneWear` schreibt den
Verschleiß wie Crash-Verschleiß in `PartWear` (B). Telemetrie `motorTempC` gibt es bereits (OSD kann sie zeigen).

### 9.5 Akku, ESC, Props
- Akku: Kurve (`ocvShape`), Grenzen und Dichte aus `CellChemistry` (ersetzt `CellCurve`-Tabellen und `DENSITY_*`
  bitgleich); Zellzahl bis 12; `packSize` für Trägheit/Hülle.
- ESC: `escBurstAmps`, `escResistanceOhm`, `brownoutVoltage` aus der Definition (Standard = Altformeln).
  Spannungsbereich (Stand Integration 0.4.0): Hat der Akku mehr Zellen als der Stack erlaubt, brennt der ESC beim
  Scharfschalten durch (`ESC_BURNED`, Stack am Item kaputt, nur mit Welt-Regel `propwash:crash_damage`), Warnung
  `ESC_OVERVOLTAGE` in der Werkbank; die Welt-Regel `propwash:hardware_limits` schaltet es ab. Keine
  eingebaute Kombination überspannt (JUnit `EscVoltageTest`).
- Props: `stiffness` s wirkt auf Propwash-Rauschen (`washNoise × s^−0,5`) und Wirkungsgradverlust unter hoher Last;
  `durability` teilt die Crash-Dosis in `CrashDamage` (Motoren/Rahmen analog über `basics.durability`).

### 9.6 Analyse (`BuildAnalysis`)
Neue Komponenten (schon im Record, in Phase 0 `NaN`): `sustainedMotorTempC` (Beharrungstemperatur bei 100 % Gas im
Stand, 25 °C) und `propsInViewPercent` (Anteil des 16:9-Kamerabilds, den die Prop-Scheiben im FPV-Bild verdecken; seit
API 1.1 aus genau dem Aufbau des Renderers: Objektiv, Standard-Uptilt des Rahmens, Motorpositionen und Nabenhöhen wie
gezeichnet, seit API 1.4 das Sichtfeld der Kamera im Videomodul; `sim.FpvRig` + `sim.CameraOcclusion`, rein, Spec 17.7). Neue `BuildWarning`s:
`MOTOR_THERMAL` (Beharrung > `derateStartC`), `PROPS_IN_VIEW` (ab 30 % seit API 1.1, vorher 20 %; bei 5 % hätten Whoop, Cinewhoop und
Long-Range die Warnung bekommen), `ESC_OVERVOLTAGE`.

Alle Warnungsschlüssel von `BuildStats#warnings()` (= `BuildWarning` klein geschrieben, Reihenfolge der Konstanten,
`BuildStats.WARNINGS`; Texte `BuildStats.warningTranslationKey(w)` = `propwash.content.workbench.warning.<w>`; JUnit
`DocTablesTest` hält diese Liste und die im Handbuch gleich):

<!-- build-warnings:begin -->
`cannot_hover`, `underpowered`, `sluggish_motors`, `heavy_sag`, `esc_current_limited`, `battery_overload`, `motor_thermal`, `props_in_view`, `esc_overvoltage`
<!-- build-warnings:end -->

**Kennzahlen für Addons (API 1.4, nur Analyse, kein Einfluss aufs Flugmodell):** `BuildStats` bekommt am Ende
`propwashNoise` (Faktor des Propwash-Rauschens gegenüber Steifigkeit 1,0 bei sonst gleichem Aufbau: `stiffness^−0,5`,
genau wie `sim.ParamDerivation` das Rauschen ableitet; eingebaute Props 1,0), `fullThrottleCellVoltage` (Zellspannung
bei Vollgas im Stand mit vollem Akku, ohne Polarisation, V; dieselbe Zahl, gegen die `HEAVY_SAG` die Abschaltspannung prüft), `rollAuthorityRadS2`,
`pitchAuthorityRadS2` und `yawAuthorityRadS2` (Winkelbeschleunigung in rad/s² bei vollem Differenzausschlag des Mixers um
den Schwebepunkt — die linearisierte Regelstrecke, die auch die PID-Ableitung benutzt: Hebelarme aus der Motorlage,
Trägheit aus der Geometrie, Schub- bzw. Drehmoment-Steigung der Motoren; Gieren stationär über das Luftwiderstandsmoment).
Der Konstruktor von 1.2 (24 Werte) und der von 1.0 (14 Werte) bleiben und setzen die neuen Werte auf `NaN`.

### 9.7 Skalierung
Charakterisierungstests mit `PartIndex.detached(…)` (nie echte Anmeldung in JUnit, sonst zählen andere Tests falsch):
X-Class 13″ 8S (4214/400 KV, 13×7 Zweiblatt) schwebt stabil bei 25–45 % Gas, Flugzeit 6–15 min; 7″ 8S Li-Ion
Long-Range ≥ 25 min Schweben; 2″-Toothpick 1S–2S; Deadcat/Stretched-X fliegen ohne Dauerdrift (Mixer gleicht den
Schwerpunkt aus). Fester Sim-Takt bleibt stabil (kein NaN, Motor-Zeitkonstante begrenzt wie bisher).

---

## 10. Rendering und Assets (C)

### 10.1 Modelle aus jedem Namensraum
`PartModel` bildet `ns:pfad` der Kategorie `k` auf `ns:drone_render/k/<pfad>` (bzw. `visual.renderModel`) samt
Varianten ab: Props `_ccw`, `_blur`, `_worn`, `_damaged`, `_broken`, jeweils `_ccw`; Lack-Variante `_paint` an jedem
Modell. Fehlt ein Modell (Resource-Reload prüft das), zeichnet C die **prozedurale Form**; fehlt nur die
Lack-Variante, bleibt das Teil in Originalfarbe. `PartModel` wird dynamisch (keine statische Liste beim Klassenladen).

### 10.2 Layout
`DroneLayout` entsteht aus der Definition: Motoren aus `arms` (sonst True-X aus dem Radstand), Akku aus
`battery`/Packmaßen, Stack/Video aus `electronicsMm`/`cameraMm`, Kamera-Uptilt aus `cameraTiltDeg`, Zubehör-Anker aus
`accessoryAnchors`. `assets/<ns>/drone_layout.json` (gleiches Schema wie heute) darf die **Optik** je Rahmen/Motor
nachjustieren; Schlüssel ohne `:` meinen den eigenen Namensraum, mit `:` eine volle Id (Ressourcenpakete dürfen fremde
Rahmen anpassen). Alle Namensräume werden beim Reload zusammengeführt, `propwash` zuerst. Das Flugmodell liest diese
Datei nie. `drone.CameraMount` liest den Standard-Uptilt aus der Definition (eingebaute: gleiche Werte wie die heutige
`drone_layout.json`); der Winkel der Drohne steht in ihrem Tuning (Abschnitt 11.6).

### 10.3 Lack
`assets/<ns>/drone_paint.json` (Schema wie heute; Schlüssel `k/<pfad>` im eigenen Namensraum, mit `:` voll) wird je
Namensraum gelesen und zusammengeführt.

### 10.4 Prozedurale Formen und Symbole
- `FallbackShapes` baut jeden Rahmen aus seiner Geometrie: Arme zu jedem Motor, Platte, Ducts als Ringe, H-Frame als
  zwei Längsholme, Deadcat mit gespreizten Vorderarmen; Motoren aus Statormaßen und Akzentfarbe; Props aus Durchmesser,
  Blattzahl, Drehrichtung; Akkus aus Packmaßen und Akzentfarbe; Kamera aus `cameraWidthMm`; Stack aus `boardSizeMm`.
- Sonder-Item-Renderer **`propwash:part`** (neu): zeichnet das Drohnen-Modell eines Teils (oder die prozedurale Form)
  als Inventar-Symbol; Addons können so auf eigene Inventar-Modelle verzichten
  (`{"model": {"type": "minecraft:special", "base": "propwash:item/part_base", "model": {"type": "propwash:part"}}}`,
  genaue Form legt C fest und dokumentiert sie in `docs/ASSETS.md`).
- `DroneItemRenderer`, `DroneGhost`, Flugschul-Diorama, Werkbank-/Konfigurator-Vorschau funktionieren mit jedem
  Bauplan; X-Class-Drohnen werden in Slots und Szenen weiter auf lesbare Größe gebracht. Seit API 1.4 zeichnet ein
  Drohnen-Item, das im Slot unter halbe Größe verkleinert wird, prozedurale Rahmen und Props in der kräftigeren
  Symbolform (`FallbackShapes.frameIcon`, `propWide`).
- **API 1.4:** Kategorie-Basismodelle `propwash:item/part_base/<kategorie>` (Anzeige-Transformationen der eingebauten
  Teile dieser Kategorie, erzeugt von `tools/assets/parts/part_bases.py`) als `base` für den Sonder-Renderer.
- **API 1.4:** Die Lackierung erreicht prozedurale Formen: `DroneVisuals.paintShape` färbt die Grundfarbe der Form
  (Rahmen-Carbon bzw. Whoop-Kunststoff, Duct, Motor-Akzent, Prop-, Akku- und Kamerafarbe) je Lack-Kanal um
  (`PaintedShapes`, Cache 512 Formen); Teile mit Modell bleiben bei ihren Tint-Ebenen.
- **API 1.4, zugesichert:** Die Vanilla-`transformation` einer Render-Definition `items/drone_render/…` wird angewandt
  (`ItemModel.update`), verkleinerte Modelle großer Rahmen erscheinen also in echter Größe; die Hülle eines Teils wird
  erst ab ±8 Modell-Blöcken (±1024 mm) gekappt (`VisualHull.clampPartBounds`, vorher ±2). Selbsttest
  „drone-render: skalierte Drohnen-Modelle (transformation) in echter Größe, Hülle ungekappt“.

### 10.5 Props im FPV-Bild
Neu: Die eigenen Props (Scheibe bzw. stehende Blätter bei Leerlauf) werden im FPV-Bild tiefenrichtig an ihrer
optischen Position gezeichnet (Objektivlage aus `AirframeSpec.lens*`, Prop-Positionen aus dem `DroneLayout`). Ein
Deadcat zeigt so keine Props, ein 5″ mit steilem Uptilt die Spitzen am Rand. Schalter „Props im FPV-Bild“ im Tab
„Darstellung“ (Standard an). Kein Einfluss auf das Flugmodell.

---

## 11. Baupläne, Sharecode-Unterstützung, Import

### 11.1 Daten
- `BuildSpec` (Ids aller Teile + Zubehör je `AccessorySlot`), `PaintSpec` (12 Lack-Plätze `PaintSpec.SLOTS`, RGB),
  `TuneSpec` (offene Schlüssel/Wert-Tabelle des FC wie `FlightTune`), `DroneData(build, paint, tune, name)`.
- **Nie enthalten**: Akkustand, Verschleiß, Drohnen-UUID, Bindungen, Funkprofile, Schalter-/Tastenbelegung.
- `TuneGroup.of(key)` ordnet jeden Tuning-Schlüssel stabil einer Gruppe zu:
  | Gruppe | Schlüssel |
  |---|---|
  | `RATES` | `rates.*` |
  | `PID` | `pid.*` außer den folgenden (P/I/D je Achse, `pid.source`, Angle/Horizon, I-Term-Relax, Anti-Gravity, Airmode) |
  | `FEEDFORWARD` | `pid.<achse>.ff` |
  | `TPA` | `pid.tpa*` |
  | `THROTTLE` | `pid.throttle*`, `pid.motorIdlePercent`, `pid.thrustLinearPercent`, `pid.vbatSagCompensationPercent` |
  | `FILTERS` | `filters.*` |
  | `HOLD` | `hold.*` |
  | `CAMERA` | `camera.*` (Uptilt der Drohne, `TuneSpec.CAMERA_UPTILT`, seit 1.4 auf den Kamerahalter begrenzt) |
  | `LED` | `led.*` |
  | `VTX` | `vtx.*` (Kanal — gehört nie in einen Sharecode) |
  | `OTHER` | alles andere |
  `TuneGroup.FLIGHT` = Rates, PID, Feedforward, TPA, Filter, Throttle (Standard eines Imports). `TuneSpec.only`,
  `without`, `overlay(imported, groups)` setzen gezielt Gruppen; ein Import mit `CAMERA` in den Gruppen überträgt den
  Kamerawinkel (fehlt er im Import, gilt danach der Standard des Rahmens).
- OSD-Layout (nur Client): `OsdLayoutSpec(version, json)` mit Schema (D, Version 1):
  `{"v":1,"preset":"standard","style":{"opacity":60,"line_opacity":60,"scale":1.0,"color":"#ffffff","warning_color":"#ff3b30","outline":"thin","background":"off","colored_alerts":true,"pitch_ladder":true,"ladder_step_deg":10.0},"elements":{"<element>":{"on":true,"col":1,"row":2,"color":"#ffffff","scale":1.0}}}`.
  Nicht enthalten: Vanilla-HUD ausblenden, analoges Bildzerreißen (Spielervorlieben).

### 11.2 `BuildAccess` (`PropwashApi.builds()`)
| Methode | Vertrag |
|---|---|
| `isDrone(stack)` | Propwash-Drohnen-Item |
| `read(stack)` | `DroneData` aus einem Drohnen-Item (fehlender Bauplan → FREESTYLE wie `loadFromStack`); leer bei anderem Item |
| `createDrone(data)` | neues Drohnen-Item wie ein Preset: voller Akku, neu (kein Verschleiß), Lackierung samt Icon-Tints, Tuning (`DronePiloting.sanitize`), Name; `IllegalArgumentException` bei unbekannten oder unpassenden Teilen (unpassend nach der Welt-Regel `propwash:part_compatibility`; ist sie aus, nur noch fehlende Teile und Zubehör im falschen Platz). Für Server-Code (Kreativ) und Vorschauen auf dem Client |
| `withTune(drone, tune)` / `withPaint(drone, paint)` | Kopie des Items mit neuem Tuning (bereinigt) bzw. neuer Lackierung; ändert keine anderen Komponenten |
| `check(spec)` | `BuildCheck`: unbekannte Ids, Probleme, Warnungen (Schlüssel = `DroneBuild.Issue` klein geschrieben, Text `propwash.build.issue.<key>`). Seit 1.5 nach der Welt-Regel `propwash:part_compatibility`: ist sie aus, stehen die aufgehobenen Probleme (alles außer `missing_part`) unter `warnings()` und `buildable()` ist wahr |
| `partCompatibility()` (API 1.5, Default-Methode: `true`) | ob auf dieser Seite gerade „Teile müssen zusammenpassen“ gilt: Server-Thread = Gamerule des Servers, Client = vom Server synchronisierter Wert, ohne Welt `true` |
| `analyze(spec)` | `BuildStats` aus `BuildAnalysis` (seit 1.2 mit gemischt/aggressiv, Strömen, Reisegeschwindigkeit, Landespannung, Grundlast, Motortemperatur nach 60 s und nach gemischtem Flug), leer bei unbekannten Teilen |
| `analyze(spec)`, neue Kennzahlen (API 1.4) | `propwashNoise`, `fullThrottleCellVoltage`, `rollAuthorityRadS2`, `pitchAuthorityRadS2`, `yawAuthorityRadS2` (Abschnitt 9.6) |
| `cameraView(spec)` (API 1.1, Default-Methode) | `CameraView` aus `sim.FpvRig`: Objektiv, Uptilt, Brillen-Sichtfeld, 16:9, vier Propscheiben (Mitte, Radius bis zur Blattspitze, Nabenradius) in Rahmen-mm und `propsInViewPercent`; leer bei unbekannten Teilen |
| `defaultTune(spec)` | vollständige Standardwerte des Flugmodells für diesen Bauplan (alle Schlüssel aus 11.5 außer `vtx.*`); `IllegalArgumentException` bei unbekannten Teilen oder Zubehör im falschen Platz (vorher mit `check(spec)` prüfen) |
| `presets()` / `registerPreset` | eingebaute (`propwash:whoop` …) + angemeldete |
| `applyImport(player, request)` | Server-Thread; wendet einen Import nach den Regeln aus 11.3 an |
| `partCondition(drone, kind)` (API 1.4) | Zustand 0…1 (1 = neu, 0 = kaputt) eines Teils am Drohnen-Item aus `propwash:part_wear`: Rahmen = schwächster Arm, Motor/Prop = schwächster der vier, Stack, Video, Akku; leer für `ACCESSORY` (Zubehör nutzt sich nicht ab) und für Stapel, die keine Drohne sind. Default-Methode: leer |
| `paintCost(drone, target)` (API 1.4) | Farbstoffe, die das Umlackieren von der Lackierung des Stapels (Drohnen-Item, in der Werkbank das Rahmen-Item) auf `target` im Überlebensmodus kostet — wie das Lack-Fenster und der Import rechnen (`PaintCost`): Farbstoff (`"red"`, `"light_blue"` …, Vanilla-Farbnamen) → Anzahl, eigene Hex-Farben zusammen unter `"custom"` (jede kostet einen beliebigen Farbstoff). Leer = kostenlos (gleiche Lackierung, nur zurückgesetzte Plätze). Kreativ ist immer kostenlos; das prüft der Aufrufer. Default-Methode: leer |

### 11.3 Import (Server entscheidet; Besitzer A für die Regeln, D für Netz und Fassade)
Ablauf: Addon-Client → `PropwashClientApi.sendImport(request)` bzw. `WorkbenchContext.importIntoWorkbench(…)` →
C2S `propwash:api_import` (Anfrage-Id, Container-Id, `ImportRequest`) → Server: Größen-/Ratenprüfung (höchstens
2 Anfragen/s je Spieler), `BuildAccess.applyImport` → `content.ImportService.apply` → Event `BUILD_IMPORTED` →
S2C `propwash:api_import_result` (Anfrage-Id, `ImportResult`) → `CompletableFuture` auf dem Client erfüllt
(Render-Thread). Ebenen in der Reihenfolge PARTS → PAINT → TUNE → NAME; jede Ebene ganz oder gar nicht; scheitert eine
Ebene, werden die folgenden nicht angewandt.

| Ziel | Ebene | Überleben | Kreativ (`hasInfiniteMaterials`) |
|---|---|---|---|
| `WORKBENCH` (offenes `DroneWorkbenchMenu` mit passender Container-Id, sonst `NO_TARGET`) | PARTS | Slots, die schon das gewünschte Teil halten, bleiben; übrige belegte Slots wandern ins Inventar (fehlt Platz → `NOT_ALLOWED`, nichts geändert); benötigte Teile (Motoren/Props je 4) kommen aus dem Inventar, bestes Zustand zuerst, Akku mit höchstem Ladestand; fehlt etwas → `MISSING_PARTS` mit je fehlendem Stück einer Id | Teile werden neu erzeugt (neu, Akku voll) |
| | PAINT | über `DroneWorkbenchMenu#applyPaint` (Farbstoffe wie im Lack-Fenster; fehlen welche → `MISSING_DYES`) | gratis |
| | TUNE | als „ausstehendes Tuning“ im Menü; die entnommene Drohne trägt es (bereinigt) | gleich |
| | NAME (API 1.4) | Name am Rahmen-Item im Rahmen-Slot (dort liegt der Name auch nach dem Zerlegen), die entnommene Drohne trägt ihn; gekürzt wie überall auf 24 Zeichen; ohne Name in `DroneData` bleibt der bisherige; kein Rahmen im Slot → `NO_TARGET` | gleich |
| `HELD_DRONE` (Drohnen-Item in der Haupthand, sonst `NO_TARGET`) | PAINT | Kosten wie Lack-Fenster (`PaintCost`), Farbstoffe aus dem Inventar | gratis |
| | TUNE | `current.overlay(imported, tuneGroups)`, bereinigt | gleich |
| | NAME (API 1.4) | wie Umbenennen im Konfigurator: Name am Item, gebundene Funken im Inventar übernehmen ihn; ohne Name bleibt der bisherige | gleich |
| | PARTS | nicht erlaubt (`ImportRequest` wirft schon beim Bauen) | — |
| `NEW_DRONE` (API 1.4) | PARTS (Pflicht) + PAINT/TUNE/NAME nach Wahl | nur für Operatoren (Berechtigungsstufe 2), sonst `NOT_ALLOWED` | neue Drohne wie `BuildAccess#createDrone` ins Inventar (voll → vor die Füße); gewählte Ebenen aus `DroneData`, nicht gewählte auf Standard; nicht baubar nach der Welt-Regel `propwash:part_compatibility` des Servers → `INVALID` |

Fabriken: `wholeBuild(data)` (Werkbank, alle Ebenen — seit 1.4 also auch `NAME`), `tuneOnHeldDrone(data)`,
`paintOnHeldDrone(data)`, seit 1.4 `onHeldDrone(data, layers)` (gehaltene Drohne, beliebige Ebenen außer PARTS, Tuning-
Gruppen `TuneGroup.FLIGHT`) und `createDrone(data)` (Ziel `NEW_DRONE`, alle Ebenen, alle Tuning-Gruppen — die Drohne
bekommt genau die `DroneData`, wie `createDrone` auf dem Server). Client: `PropwashClientApi.sendCreateDrone(data)` =
`sendImport(ImportRequest.createDrone(data))`, dieselbe Größen- und Ratenprüfung wie jeder Import.

**Verhaltensänderung durch `NAME`:** `ImportRequest.wholeBuild` und `EnumSet.allOf(ImportLayer.class)` enthalten seit
1.4 die Ebene `NAME`. Ein Addon für 1.3, das so importiert, setzt damit jetzt auch den Namen — aber nur, wenn die
`DroneData` einen Namen trägt; ein vorhandener Name wird nie gelöscht. Wer das nicht will, wählt die Ebenen ausdrücklich
(`EnumSet.of(PARTS, PAINT, TUNE)`).

Status: `APPLIED` (mit `applied`-Ebenen), `UNCHANGED`, `UNKNOWN_PARTS` (Ids nicht angemeldet), `MISSING_PARTS`,
`MISSING_DYES`, `NO_TARGET`, `NOT_ALLOWED` (Rechte, Inventar voll, Spieler im Zuschauermodus, `NEW_DRONE` ohne Kreativ
oder Operator), `INVALID` (kaputte Anfrage, `NEW_DRONE` nicht baubar), `BUSY` (Ratenbegrenzung). Texte `propwash.api.import.<status>` (D, beide Sprachen).


### 11.4 Empfohlene Sharecode-Oberfläche des Addons (nicht in Propwash)
Werkbank-Knopf „Code importieren“ → Theme-Fenster mit Eingabefeld, Vorschau (`renderPreview`), Teile-Check
(`builds().check`) inklusive fehlender Teile, Häkchen „Ganzer Bauplan / Nur Tuning auf gehaltene Drohne / Nur
Lackierung“ und „OSD-Layout übernehmen“ → `importIntoWorkbench` bzw. `sendImport`. Export: `builds().read(stack)` bzw.
`WorkbenchContext.result()` + `osd().current()`.

### 11.5 Tuning-Schlüssel (Katalog, API 1.4)
Alle Schlüssel, die `TuneSpec`, `BuildAccess#defaultTune`, `withTune` und der Import kennen — erzeugt aus den Tabellen des
Flight-Controllers (`ui.TuneKeys`, `drone.TuneMapping`, `drone.led.LedSettings`, `video.VtxSettings`) und von JUnit
`DocTablesTest` gegen diese Tabelle geprüft (weicht sie ab, schreibt der Test den richtigen Block nach
`common/build/docs/tune-keys.md`). Werte stehen so im `TuneSpec`, wie der Code sie speichert (Punkt als
Dezimaltrennzeichen): Auswahlen als Zahl des Wertcodes, Schalter als 0/1; „Anteil“ heißt, der Konfigurator zeigt den Wert
mal dem Anzeigefaktor in der genannten Einheit. Bereich und Schritt sind die des Konfigurators. `withTune`, der Import
und `createDrone` bereinigen das Tuning wie der Konfigurator (`DronePiloting.sanitize`): Werte werden auf den Bereich des
Flight-Controllers begrenzt, unbekannte Schlüssel verworfen, und gespeichert bleibt nur, was vom Standard des Bauplans
abweicht. `vtx.*` steht nie in `defaultTune` und gehört nie in einen Sharecode.

<!-- tune-keys:begin -->
| Schlüssel | Gruppe | Art | Bereich | Schritt | Einheit | Werte und Hinweise |
|---|---|---|---|---|---|---|
| `rates.type` | `RATES` | Auswahl | 0–1 | 1 | – | 0 = `betaflight`, 1 = `actual` |
| `rates.roll.rcRate` | `RATES` | Zahl | 0.01–2.55 | 0.01 | – | – |
| `rates.roll.superRate` | `RATES` | Zahl | 0–0.99 | 0.01 | – | – |
| `rates.roll.rcExpo` | `RATES` | Zahl | 0–1 | 0.01 | – | – |
| `rates.roll.centerRateDps` | `RATES` | Zahl | 10–1998 | 5 | °/s | – |
| `rates.roll.maxRateDps` | `RATES` | Zahl | 10–1998 | 10 | °/s | – |
| `rates.roll.actualExpo` | `RATES` | Zahl | 0–1 | 0.01 | – | – |
| `rates.pitch.rcRate` | `RATES` | Zahl | 0.01–2.55 | 0.01 | – | – |
| `rates.pitch.superRate` | `RATES` | Zahl | 0–0.99 | 0.01 | – | – |
| `rates.pitch.rcExpo` | `RATES` | Zahl | 0–1 | 0.01 | – | – |
| `rates.pitch.centerRateDps` | `RATES` | Zahl | 10–1998 | 5 | °/s | – |
| `rates.pitch.maxRateDps` | `RATES` | Zahl | 10–1998 | 10 | °/s | – |
| `rates.pitch.actualExpo` | `RATES` | Zahl | 0–1 | 0.01 | – | – |
| `rates.yaw.rcRate` | `RATES` | Zahl | 0.01–2.55 | 0.01 | – | – |
| `rates.yaw.superRate` | `RATES` | Zahl | 0–0.99 | 0.01 | – | – |
| `rates.yaw.rcExpo` | `RATES` | Zahl | 0–1 | 0.01 | – | – |
| `rates.yaw.centerRateDps` | `RATES` | Zahl | 10–1998 | 5 | °/s | – |
| `rates.yaw.maxRateDps` | `RATES` | Zahl | 10–1998 | 10 | °/s | – |
| `rates.yaw.actualExpo` | `RATES` | Zahl | 0–1 | 0.01 | – | – |
| `rates.rcDeadband` | `RATES` | Zahl | 0–0.2 | 0.005 | Anteil (Anzeige %) | Anzeige ×100 |
| `rates.yawDeadband` | `RATES` | Zahl | 0–0.2 | 0.005 | Anteil (Anzeige %) | Anzeige ×100 |
| `pid.source` | `PID` | Auswahl | 0–1 | 1 | – | 0 = `derived`, 1 = `custom` |
| `pid.roll.p` | `PID` | Zahl | 0–250 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.roll.i` | `PID` | Zahl | 0–250 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.roll.d` | `PID` | Zahl | 0–200 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.roll.ff` | `FEEDFORWARD` | Zahl | 0–500 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.pitch.p` | `PID` | Zahl | 0–250 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.pitch.i` | `PID` | Zahl | 0–250 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.pitch.d` | `PID` | Zahl | 0–200 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.pitch.ff` | `FEEDFORWARD` | Zahl | 0–500 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.yaw.p` | `PID` | Zahl | 0–250 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.yaw.i` | `PID` | Zahl | 0–250 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.yaw.d` | `PID` | Zahl | 0–200 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.yaw.ff` | `FEEDFORWARD` | Zahl | 0–500 | 1 | – | aus dem Bauplan abgeleitet, solange `pid.source` = 0 (ein abweichender Wert schaltet auf 1) |
| `pid.angleStrength` | `PID` | Zahl | 0–200 | 1 | – | Standard aus dem Bauplan abgeleitet |
| `pid.angleLimitDeg` | `PID` | Zahl | 10–85 | 1 | ° | – |
| `pid.horizonStrength` | `PID` | Zahl | 0–200 | 1 | – | – |
| `pid.itermRelax` | `PID` | Auswahl | 0–2 | 1 | – | 0 = `off`, 1 = `rp`, 2 = `rpy` |
| `pid.antiGravityGain` | `PID` | Zahl | 0–250 | 1 | – | – |
| `pid.tpaMode` | `TPA` | Auswahl | 0–1 | 1 | – | 0 = `d`, 1 = `pd` |
| `pid.tpaRatePercent` | `TPA` | Zahl | 0–100 | 1 | % | – |
| `pid.tpaBreakpoint` | `TPA` | Zahl | 0–0.95 | 0.01 | Anteil (Anzeige %) | Anzeige ×100; Standard aus dem Bauplan abgeleitet |
| `pid.airmode` | `PID` | Schalter | 0–1 | 1 | – | 0 = aus, 1 = an |
| `pid.motorIdlePercent` | `THROTTLE` | Zahl | 0–20 | 0.1 | % | – |
| `pid.throttleMid` | `THROTTLE` | Zahl | 0.05–0.95 | 0.01 | – | – |
| `pid.throttleExpo` | `THROTTLE` | Zahl | 0–1 | 0.01 | – | – |
| `pid.throttleLimitType` | `THROTTLE` | Auswahl | 0–2 | 1 | – | 0 = `off`, 1 = `scale`, 2 = `clip` |
| `pid.throttleLimitPercent` | `THROTTLE` | Zahl | 25–100 | 1 | % | – |
| `pid.thrustLinearPercent` | `THROTTLE` | Zahl | 0–100 | 1 | % | – |
| `pid.vbatSagCompensationPercent` | `THROTTLE` | Zahl | 0–100 | 1 | % | – |
| `pid.armMaxAngleDeg` | `PID` | Zahl | 0–180 | 1 | ° | Standard 25; 180 = scharf schalten in jeder Lage, auch in der Luft und kopfüber (wie `small_angle` in Betaflight) |
| `filters.pidLoop` | `FILTERS` | Auswahl | 0–2 | 1 | – | 0 = `hz_2000`, 1 = `hz_1000`, 2 = `hz_500` |
| `filters.gyroLpfHz` | `FILTERS` | Zahl | 20–1000 | 5 | Hz | 0 = aus |
| `filters.dtermLpf1Hz` | `FILTERS` | Zahl | 20–1000 | 5 | Hz | 0 = aus |
| `filters.dtermLpf2Hz` | `FILTERS` | Zahl | 20–1000 | 5 | Hz | 0 = aus |
| `filters.rcSmoothingHz` | `FILTERS` | Zahl | 5–200 | 1 | Hz | 0 = aus |
| `hold.altP` | `HOLD` | Zahl | 0–200 | 1 | – | – |
| `hold.altI` | `HOLD` | Zahl | 0–200 | 1 | – | – |
| `hold.altD` | `HOLD` | Zahl | 0–200 | 1 | – | – |
| `hold.climbRateCms` | `HOLD` | Zahl | 20–1000 | 10 | cm/s | – |
| `hold.throttleDeadbandPercent` | `HOLD` | Zahl | 2–40 | 1 | % | – |
| `hold.midThrottle` | `HOLD` | Schalter | 0–1 | 1 | – | 0 = aus, 1 = an |
| `hold.posP` | `HOLD` | Zahl | 0–200 | 1 | – | – |
| `hold.posI` | `HOLD` | Zahl | 0–200 | 1 | – | – |
| `hold.posD` | `HOLD` | Zahl | 0–200 | 1 | – | – |
| `hold.posMaxAngleDeg` | `HOLD` | Zahl | 5–45 | 1 | ° | – |
| `hold.stickDeadbandPercent` | `HOLD` | Zahl | 2–30 | 1 | % | – |
| `hold.minSatellites` | `HOLD` | Zahl | 5–12 | 1 | – | – |
| `camera.uptiltDeg` | `CAMERA` | Zahl | 0–80 | – | ° | Standard aus `camera.tilt_deg` des Rahmens |
| `led.mode` | `LED` | Auswahl | 0–4 | 1 | – | 0 = `static`, 1 = `blink`, 2 = `armed`, 3 = `low_battery`, 4 = `rainbow` |
| `led.color` | `LED` | Auswahl | 0–10 | 1 | – | 0 = `white`, 1 = `warm_white`, 2 = `red`, 3 = `orange`, 4 = `yellow`, 5 = `green`, 6 = `cyan`, 7 = `blue`, 8 = `violet`, 9 = `magenta`, 10 = `pink` |
| `led.brightnessPercent` | `LED` | Zahl | 10–100 | 5 | % | – |
| `led.headlight` | `LED` | Schalter | 0–1 | 1 | – | 0 = aus, 1 = an |
| `vtx.channel` | `VTX` | Zahl | 0–15 | 1 | – | Index in der Kanalliste des Videosystems (analog R1–R8, F1–F8; digital CH1–CH8); fehlt = automatisch |
| `vtx.manual` | `VTX` | Schalter | 0–1 | 1 | – | 1 = Kanal fest eingestellt |
<!-- tune-keys:end -->

### 11.6 Kamera: Sichtfeld und Uptilt (API 1.4)
- **Sichtfeld:** `VideoDefinition#cameraFovDeg` (`camera_fov_deg`, horizontal) ist das FPV-Sichtfeld mit analoger und
  digitaler Brille und das Sichtfeld von `cameraView`/`propsInViewPercent`. Ohne Wert gilt der Standard des Systems
  (analog 120°, digital 130°). Nur die Kreativ-Brille darf ein eigenes Sichtfeld wählen (Spielereinstellung, keine API).
- **Uptilt:** Der Kamerawinkel gehört zur Drohne. Er steht im Tuning (`TuneSpec.CAMERA_UPTILT` = `camera.uptiltDeg`,
  Gruppe `CAMERA`); fehlt er, gilt der Standard des Rahmens (`camera.tilt_deg`). Propwash begrenzt jeden gespeicherten
  oder importierten Wert auf den Kamerahalter des Rahmens: Bereich der Rahmenklasse (Whoop 10–35°, Toothpick 10–40°,
  Cinewhoop 0–35°, Freestyle 0–50°, Race 15–70°, Long Range 0–40°, Cinelifter 0–30°, X-Class 0–50°), immer erweitert um
  den Standard des Rahmens. `BuildAccess#uptiltRange(frame)` liefert den Bereich eines Rahmens, `CameraView#uptiltRange()`
  den des Bauplans.
- `cameraView(spec)` rechnet mit dem Standard-Uptilt (wie die Werkbank), `cameraView(spec, uptiltDeg)` mit einem anderen
  Winkel (auf den Halter begrenzt; `propsInViewPercent` dann für diesen Winkel).
- Beim Zerlegen bleibt der Winkel am Rahmen-Item (Rahmen mit `propwash:flight_tune` nur aus `camera.uptiltDeg`), das
  übrige Tuning am Stack; `read(stack)` liefert ihn wie jedes andere Tuning der Drohne.

---

## 12. Erweiterungspunkte

### 12.1 Konfigurator-Tabs
`PropwashClientApi.registerConfigTab(Supplier<? extends ConfigTab>)` = `ConfigTabs.register`. Vertrag des Tabs wie
FOUNDATION §15 „Konfigurator-Tabs“; Addon-Tabs benutzen eine Id mit Namensraum (`"justmoreparts:sharecode"`) und eine
freie `order()` ≥ 1000; Theme-Pflicht (exportierte Theme-Klassen).

### 12.2 Werkbank
- `WorkbenchExtension` (`id()`, `order()`, `init(WorkbenchContext)`) über `registerWorkbenchExtension`. `init` läuft bei
  jedem Aufbau des Werkbank-Screens (auch nach Größenänderung) auf dem Render-Thread; Ausnahmen werden geloggt und
  isoliert.
- `WorkbenchContext`: `screen()`, `containerId()`, `creative()`, `slot(kind, position)` (Id des Teils im Slot),
  `result()` (fertige Drohne als `DroneData`, wenn baubar), `addButton(label, tooltip, onPress)`,
  `openChild(parent -> screen)` (Kind-Fenster, Esc/„Zurück“ kehrt zur Werkbank zurück; schließt sich, wenn der Server
  das Menü schließt — wie das Lack-Fenster), `importIntoWorkbench(data, layers, tuneGroups)`.
- Platzierung (A): bis zu zwei Knöpfe links neben „Lackierung“ in der Kopfleiste, weitere hinter einem Knopf „⋯“ mit
  Theme-Liste; Tooltips mit Titelzeile wie beim Lack-Knopf; keine Überlappung bei GUI-Skala 1–4.
- **API 1.4:** Marke und Titel sind reserviert (der Titel wird nie gekürzt); `headerSpace()` meldet den Rest bis
  „Lackierung“ in GUI-Pixeln, `buttonWidth(label)` die Breite eines Text-Knopfs (Text + `BUTTON_PADDING` 14, höchstens
  `MAX_BUTTON_WIDTH` 80), Abstand `BUTTON_GAP` 3. `addButton(label, shortLabel, tooltip, onPress)`: Kurzbeschriftung,
  wenn die volle nicht passt (`content.client.HeaderLayout`: erst Kurzformen, beim breitesten Knopf beginnend, dann
  Überlauf in „⋯“). `addIconButton(sprite, tooltip, onPress)`: 13 × 13-px-Sprite (`ICON_BUTTON_SIZE`) im Knopfstil
  (`FlatButton#icon`, `ThemedWidgets.iconButton`). `missingParts(data): List<Identifier>` – synchron auf dem Client nach
  `ImportService.missingParts` (Slots der Werkbank, dann Inventar; kreativ leer; unbekannte Ids allein), dieselbe
  Rechnung wie der Server beim Import. Fremde `WorkbenchContext`-Implementierungen erben neutrale Standards.

### 12.3 Flugschule
`PropwashClientApi.registerFlightSchoolScene(Supplier<Scene>)` = `GuideScenes.register`. Szenen-Ids mit Namensraum
(`"justmoreparts:frames"`), Texte unter `<ns>.guide.scene.<name>…` (D passt `Scene`-Schlüssel an; Propwash-Szenen
behalten ihre Schlüssel). Szenen dürfen fremde Bauteile als Drohnen-Akteure benutzen.

Halten-Taste (API 1.3): `GuideOpener.scenesFor(stack)` gilt für Items **jedes** Namensraums. Eine Szene passt zu einem
Item, wenn ihr `ItemMatch` es nennt (Ids, Symbol, `anyPart()` für jedes Item mit `propwash:part`) oder eine Verknüpfung
in `GuideScenes` greift:

| Verknüpfung | API | Wirkung |
|---|---|---|
| Item-Ids | `linkFlightSchoolItems(szene, ids…)` = `GuideScenes.linkItems` | genau diese Items |
| Teil-Arten eines Namensraums | `linkFlightSchoolParts(szene, ns, arten…)` = `GuideScenes.linkParts` | jedes Teil-Item (`PartRef.category().kind()`) des Namensraums mit einer der Arten; ohne Arten alle |
| eingebaut, jeder Namensraum | — | `battery` ← Akku, `video` ← Video, `configurator` ← Stack, `repair` ← Prop |

Verknüpfungen sind nur Ergänzungen, wirken auch für später angemeldete Szenen und Teile und respektieren die
Sichtbarkeit der Szene. `flightSchoolScenes(stack)` (Ids mit Namensraum, Reihenfolge der Übersicht) und
`openFlightSchool(stack, parent)` (wie die Halten-Taste, `false` ohne Szene) geben Addons denselben Weg wie die Taste;
`api.client.FlightSchoolScenes` nennt die Ids der eingebauten Szenen (seit 1.5 mit `TRICKS` für die Lektion „Freestyle-Tricks“, `GRIPPER` für die Greifer-Szene und `MODE_3D` für die Lektion „3D-Modus“).

### 12.4 Kreativ-Tab, Presets
Abschnitt 6.5.

### 12.5 Client-Zugriffe
`osd()` (`OsdLayoutAccess`: `current`, `defaults`, `apply` — speichert in der OSD-Config wie der OSD-Tab),
`tune()` (`ClientTune` = `drone.client.TuneAccess`: `available`, `current`, `defaults`, `apply`), `heldDrone()` (direkt an
`PropwashClientApi`, nicht an `tune()`), `sendImport(request)`, seit 1.4 `sendCreateDrone(data)` (Abschnitt 11.3),
`renderPreview(graphics, drone, x0, y0, x1, y1, yaw)` (= `DronePreview.render` mit Lackierung). Seit 1.4:
`renderPreview(…, yaw, pixelsPerMillimetre)` im festen Maßstab (`DronePreview.renderAtScale`), `BuildAccess#footprintMm(spec)`
(weitester Motorachsen-Abstand + Prop-Durchmesser aus `FpvRig`; Standard-Implementierung schätzt aus den Definitionen),
`OsdLayoutAccess#validate(spec)` (Regeln von `apply` ohne Übernehmen), `openConfigTab(tabId, parent): boolean`
(`ConfiguratorScreen.openTab`; `false` bei unbekanntem Tab oder gesperrtem Öffnen). Exportiert: `ui.client.widget.TextField`
(Theme-Textfeld, auch außerhalb eines `ThemedScreen`). `forceShiftHelp(Boolean)` erzwingt die Shift-Hilfe der Teil-Tooltips
(= `content.TooltipShift.override`, `null` folgt der Taste).

### 12.6 Events (`api.event.PropwashEvents`)
| Event | Thread | Wann |
|---|---|---|
| `PART_REGISTERED(definition)` | Init-Thread des anmeldenden Mods | nach jeder fremden Anmeldung |
| `REGISTRY_FROZEN(registry)` | Init/Hauptthread | einmal beim Einfrieren |
| `DRONE_ASSEMBLED(player, drone, data)` | Server | Drohne aus dem Werkbank-Ausgang genommen |
| `DRONE_DISASSEMBLED(player, data)` | Server | Drohne in der Werkbank zerlegt |
| `BUILD_IMPORTED(player, request, result)` | Server | nach jeder Import-Anfrage (jeder Status) |
| `ESC_BURNED(player, drone, data, cells)` (API 1.4) | Server | der ESC einer geflogenen Drohne ist durchgebrannt (Akku mit mehr Zellen, als der Stack verträgt; Abschnitt 9.5): genau einmal, wenn der Server den Stack auf 0 setzt (nur mit den Welt-Regeln `propwash:crash_damage` und `propwash:hardware_limits`, bis 0.4.0 Server-Regel `partWear` und Hardware-Grenzen des Piloten). `drone` = Kopie der Drohne als Item (wie beim Aufheben, mit Verschleiß), `data` = ihre `DroneData`, `cells` = Zellzahl des Akkus; `player` = der Pilot. Listener-Ausnahmen werden geloggt und isoliert |

---

## 13. Beispiel-Addon (devtest, D)

Namensraum `propwash_example` im Quell-Set `devtest` (nicht im Release-Jar), Einstieg `dev.example.ExampleAddon.init()`
(aus `DevBootstrap`, gemeinsam) und `dev.example.client.ExampleAddonClient.init()` (aus `DevClientBootstrap`, immer —
nicht nur im Selbsttest). Inhalt: je ein Teil per Java und per JSON (`common/src/devtest/resources/data/propwash_example/
propwash_parts/…`, von `PropwashApi.parts().loadBundled("propwash")` geladen): Deadcat-5″, Stretched-X-Race, X-Class 13″,
Motoren 2806.5 und 4214, Carbon-Prop 13″, 8S-Li-Ion, LiHV-Chemie-Beispiel, Stack 3–8S; Items über
`registerItems("propwash_example")`; ein Preset; ein Konfigurator-Tab, ein Werkbank-Knopf (Beispiel-Import), eine
Flugschul-Szene und ein eigener Kreativ-Tab „Propwash: API-Beispiel“ (`propwash:main-propwash_example`, Symbol X-Class
13″, nur während der eigenen Schritte gefüllt, sonst leer und ausgeblendet). Regeln: `creative: false` und Tab/Knopf
so, dass **kein bestehender Selbsttest und kein Bildschirmfoto** sich ändert (nur eigene Bildschirmfotos `api_*.png`).
Selbsttests `apiimpl.client.ApiSelfTests`.

---

## 14. Umsetzungsplan — vier parallele Implementierer

### 14.1 Ausgangslage
Alle vier zweigen von **diesem Commit** auf `feature/parts-api` ab. Er enthält: Phase 0 (Abschnitt 2.2), das
vollständige Paket `api` (eingefroren), Stubs aller Nahtstellen (Tabelle 14.3), verdrahtete Einstiege
(`PropwashCommon`: `PartsModule.init()` nach Core, `ApiModule.init()` am Ende; `PropwashClient`: `PartsClient.init()`,
`ApiClient.init()`; `DevBootstrap`/`DevClientBootstrap`: `ExampleAddon`, `ExampleAddonClient`, `PartsSelfTests`,
`PartsFlightSelfTests`, `ApiSelfTests`), `AirframeGoldenTest`, `PartIndexTest`, `ApiContractTest`.

### 14.2 Besitz (nur der Besitzer ändert eine Datei; Lesen und Aufrufen ist frei)
| Implementierer | Pakete / Dateien |
|---|---|
| **A — Register & Daten** | `build/**` (alle Teile, `PartIndex`, `DroneBuild`, `BatteryFit`, `AccessoryAnchor`, `Accessories`, `MountSize`, `PartWear`, Lack-Records); `core/DroneBuildCodecs`, `core/PartCategory`, `core/PartRef`, `core/BoundDrone`, `core/CoreComponents` (nur falls nötig); `parts/**` (+ `parts/client/**`, devtest `parts/client/PartsSelfTests`); `content/**` (+ `content/client/**`, inkl. `ImportService`, `DroneWorkbenchScreen`-Host, `ContentTab`, `PartTooltips`, `ItemHelp`, `Recycling`, `Batteries`, Ladegerät); `race/GhostLook`, `race/client/GhostStore`, `race/client/LeaderboardScreen`; `network/**` und `Constants.PROTOCOL_VERSION`; `platform/**` (neue Methode für Mod-Ressourcen); Loader-Glue `fabric/src/**`, `neoforge/src/**`; `tools/data/survival.py` (Tags `#propwash:parts*`); `common/src/lang/content/`, neu `common/src/lang/parts/`; Tests `common/src/test/**/{build,core,content,parts,race}`; Doku `docs/design/parts-api-data.md` |
| **B — Flugmodell & Analyse** | `sim/**`; `trick/TrickMode` (+ `trick/**` falls nötig); `audio/MotorTimbre`; `drone/DroneWear`; `drone/client/PilotSession` (nur Objektiv), `drone/client/FpvView` (nur Objektiv), `video/client/SpectateSession` (nur Objektiv); `drone/client/SimulationConfigTab` (Schalter Hitzegrenzen); devtest `drone/client/PartsFlightSelfTests`; `common/src/lang/drone/` nur neue Schlüssel des Simulation-Tabs; Tests `common/src/test/**/{sim,trick,audio}`; Doku `docs/design/flight-model-spec.md` (neuer Abschnitt „Beliebige Bauteile“) |
| **C — Rendering & Assets** | `drone/client/render/**` (inkl. `DroneRenderSelfTests`, neuer Sonder-Renderer `propwash:part`, Props im FPV-Bild); `client/render/DronePreview`; `drone/client/CameraUptilt`; `guide/client/render/**`; neue Mixins in `mixin/drone/client/**` + `propwash.drone.mixins.json` (falls für FPV-Props nötig); `assets/propwash/items/**`, `models/**`, `drone_layout.json`, `drone_paint.json`; `tools/assets/**`; `common/src/lang/drone-render/`; Tests `common/src/test/**/drone/client/render`; Doku `docs/ASSETS.md` (Abschnitt „Teile aus anderen Namensräumen“) |
| **D — Fassade, Erweiterungen, Doku** | `api/**` (nur ergänzend, Abschnitt 3.3; bestehende Signaturen nicht ändern); `apiimpl/**` (+ Import-Payloads, `ClientApiImpl`, `WorkbenchExtensions`, devtest `apiimpl/client/ApiSelfTests`); `guide/GuideScenes`, `guide/scene/**` (Namensraum-Ids); `osd/**` (neuer `osd/OsdLayoutCodec`, kleine Zugriffe in `OsdConfig`); `ui/client/**` (nur Sichtbarkeit exportierter Klassen); devtest `dev/example/**` + `common/src/devtest/resources/{data,assets}/propwash_example/**`, eigene Bildschirmfotos im Rundgang (`dev/client/ShowcaseTour` nur anhängen); neu `common/src/lang/api/`; Tests `common/src/test/**/{api,apiimpl,osd}`; Doku: neu `docs/addons.md` (Entwicklerhandbuch), `docs/FOUNDATION.md` §15 (Eintrag „Parts-API“, einziger Bearbeiter), `docs/ARCHITECTURE.md` (einziger Bearbeiter), `README.md`, `CHANGELOG.md` (0.4.0), `docs/modrinth/**` |

Gemeinsame Dateien: `changelogs/changelog-<datum>.md` (jeder hängt eigene Zeilen an, Merge nach FOUNDATION §10),
generierte Sprachdateien (nie von Hand). `PropwashCommon`, `PropwashClient`, `DevBootstrap`, `DevClientBootstrap` sind
fertig verdrahtet — niemand ändert sie.

### 14.3 Nahtstellen (Stub in diesem Commit → Besitzer implementiert, andere rufen nur auf)
| Nahtstelle | Stand | Besitzer | Benutzer |
|---|---|---|---|
| `build.PartIndex` (`register`, `detached`, `byId/byKey`, `all`, `fingerprint`, `freeze`) | fertig | A (erweitert: `missing`, Freeze-Prüfungen) | alle |
| `parts.PartRegistryImpl` (`PropwashApi.parts()`) | fertig bis auf `loadBundled` | A | D, Addons |
| `parts.BundledParts.load(modId)` | Stub | A | `PartRegistryImpl` |
| `parts.AddonItems` (`PropwashApi.partItems()`) | Lookups fertig, Anmeldung Stub | A | D, Addons |
| `content.ImportService.apply(player, request)` | Stub (`NOT_ALLOWED`) | A | D (`BuildAccessImpl.applyImport`) |
| `apiimpl.Conversions` (`spec`, `build`, `paint`, `tune`, `unknownParts`) | fertig | D | A, D |
| `apiimpl.ApiExtensions` (Kreativ-Einträge, Presets) | fertig | D | A (`ContentTab`) |
| `apiimpl.BuildAccessImpl` | `isDrone`, `presets`, `registerPreset`, `applyImport` fertig, Rest Stub | D | Addons |
| `apiimpl.client.WorkbenchExtensions` (`register`, `all`, `initAll`) | fertig | D | A (`DroneWorkbenchScreen`) |
| `apiimpl.client.ClientApiImpl.sendWorkbenchImport(containerId, request)` | Stub | D | A (`WorkbenchContext`-Umsetzung) |
| `apiimpl.client.ClientApiImpl` (`OSD`, `TUNE`, `heldDrone`, `sendImport`, `renderPreview`) | Stub | D | Addons |
| `sim.AirframeSpec.lensRight/lensUp/lensForward` | fertig (Altformel) | B (verallgemeinert) | C (FPV-Props), B |
| `sim.BuildAnalysis.sustainedMotorTempC/propsInViewPercent` | `NaN` | B | A (Bericht), D (`BuildStats`) |
| `PropwashEvents.PART_REGISTERED` | wird gefeuert | — | — |
| `PropwashEvents.REGISTRY_FROZEN`, `DRONE_ASSEMBLED`, `DRONE_DISASSEMBLED` | nicht gefeuert | A | Addons |
| `PropwashEvents.BUILD_IMPORTED` | nicht gefeuert | D | Addons |
| `dev.example.*`, `apiimpl.client.ApiSelfTests` | leer | D | — |
| `parts.client.PartsSelfTests` | leer | A | — |
| `drone.client.PartsFlightSelfTests` | leer | B | — |

### 14.4 Aufgaben und Abnahme je Implementierer

**A — Register & Daten**
1. `PartIndex` fertigstellen: Prüfungen beim Anmelden (Id-Regeln, doppelte Ids über alle Kategorien, bekannte
   Chemie), Einfrieren an den Loader-Punkten samt Querprüfung (verwendete Halterungen, Recycling-Items, Teile ohne
   Item im selben Namensraum → Warnung), Log-Zusammenfassung, `REGISTRY_FROZEN`; `missing(kind, id)` (Abschnitt 7);
   `PartInfo#missing()`.
2. JSON: Codecs für alle Definitionen (Feldnamen Abschnitt 5/6.3), `BundledParts.load` über einen neuen
   Plattformdienst (Mod-Ressourcen je Mod-Id, Fabric `ModContainer`, NeoForge Mod-Datei — APIs aus `.ref/`), Fehler
   gesammelt. JUnit: jede eingebaute Definition kodieren → dekodieren → `equals`; kaputte Beispiele liefern die
   erwarteten Meldungen.
3. Items: `AddonItems.registerItems/registerItem` mit Warteschlange und Flush auf beiden Loadern (Fabric sofort bzw. im
   eigenen Flush; NeoForge im `RegisterEvent`), `PartItem`/`BatteryItem`, Stapelgrößen. `ContentItems` bleibt für
   eingebaute Teile und meldet nur `builtin()`-Teile an (schon so).
4. Codecs: unbekannte Schlüssel → Platzhalter (NBT, Netz, `PartRef`, `BoundDrone`, `GhostLook`); `MAX_KEY_LENGTH`.
5. `DroneBuild`: neue Issues (Abschnitt 5.9), `buildable()` beachtet Platzhalter.
6. Werkbank: Host für Erweiterungen (12.2), Zerlegen/Zusammenbauen mit fremden Items, Events
   `DRONE_ASSEMBLED/DISASSEMBLED`, ausstehendes Tuning, `ImportService` (11.3).
7. Tooltips/Hilfe/Recycling/Ladegerät/Kreativ-Tab/Tags/Geisterdateien nach Abschnitt 8.
8. Handshake `propwash:parts` (7), Protokoll 7, Sprachtexte (Mismatch, neue Issues, Tooltip-Zeilen).
9. Selbsttests (`PartsSelfTests`): Platzhalter-Rundreise (Drohne mit unbekanntem Teil speichern/laden/zerlegen),
   Items fremder Teile (aus dem Beispiel-Addon) in Werkbank, Tooltip, Kreativ-Tab, Recycling, Ladegerät; Import in die
   Werkbank im Überleben (fehlende Teile, Farbstoffe) und im Kreativmodus.
Abnahme: `tools/fastcheck.sh all` und `test` grün, `AirframeGoldenTest` unverändert grün, `survival.py --check` grün,
`DroneBuildCodecTest`/Speicherformate byte-gleich.

**B — Flugmodell & Analyse**
1. `ParamDerivation`/`AirframeSpec` aus allen Definitionsfeldern (Abschnitt 9), Altpfad für Rahmen ohne `arms`
   bitgleich; `FrameGeometry`/`CellCurve`/`ModelConstants`-Tabellen aus Definitionen.
2. Mixer und Autoritäten für beliebige vier Arme; 3D-Schwerpunkt; Trägheit aus Geometrie; Hülle/Hitbox.
3. Hitze: Parameter je Motor, Drosselung, Verschleiß, Events, `SimTuning.thermalLimits`, `DroneWear`,
   `ThermalNeutralityTest`.
4. Chemie, ESC-Werte, Prop-Material/Steifigkeit/Haltbarkeit, `CrashDamage`/`WearEffects` mit `durability`.
5. Objektivlage (9.2) inkl. der drei Aufrufer; `CameraOcclusion`; `BuildAnalysis`-Felder und -Warnungen.
6. `TrickMode` nach Rolle, `MotorTimbre` für große Props.
7. JUnit: Golden grün; Charakterisierung der Addon-Klassen (9.7) mit `PartIndex.detached`; Deadcat hält die Lage ohne
   Drift; Mixer-Tests für Stretched/Squashed/H.
8. Selbsttest (`PartsFlightSelfTests`): Beispiel-X-Class und Deadcat fliegen im Spiel (Test-Knüppel), Hitze-Drosselung
   bei überladenem Motor mit OSD-Warnung, Verschleiß landet am Item.
Abnahme: fastcheck grün, Golden unverändert, Neutralitätstest grün, neue Charakterisierungs-Tabellen in
`flight-model-spec.md`.

**C — Rendering & Assets**
1. `PartModel` dynamisch und namensraumfähig; Reload prüft vorhandene Modelle/Varianten.
2. `DroneLayout` aus Definitionen + `drone_layout.json` aller Namensräume; `drone_paint.json` aller Namensräume;
   Standard-Uptilt aus der Definition (eingebaute bitgleich zu heute; heute `drone.CameraMount`).
3. `FallbackShapes` generisch (Arme, Ducts, H-Frame, Deadcat, Stator, Packmaße, Platine, Kamera).
4. Sonder-Renderer `propwash:part` für Inventar-Symbole; `DroneItemRenderer`/`DroneGhost`/Flugschule mit fremden Teilen.
5. Props im FPV-Bild (10.5) mit Schalter im Tab „Darstellung“.
6. Asset-Werkzeug: Vorlage/Anleitung für Addon-Modelle (`tools/assets/parts/`), `docs/ASSETS.md`.
7. JUnit: Layout aus Definitionen für eingebaute Rahmen = heutige Fallback-/Datei-Werte; prozedurale Formen für
   Beispiel-Definitionen ohne NaN und innerhalb der Hülle.
8. Selbsttests in `DroneRenderSelfTests`: fremde Teile ohne Modelle rendern prozedural (Entity, Item, Werkbank, Geist),
   mit Modellen aus `propwash_example`; FPV-Props sichtbar beim 5″, unsichtbar beim Deadcat; Bildschirmfotos
   `drone_closeup_deadcat.png`, `drone_closeup_xclass.png`, `fpv_props_in_view.png`, `inventory_addon_parts.png`.
Abnahme: fastcheck grün, alle bestehenden Render-Selbsttests und Bildschirmfotos unverändert (außer den neuen).

**D — Fassade, Erweiterungen, Doku**
1. `BuildAccessImpl` vollständig (11.2), `ClientApiImpl` (OSD, Tune, Import, Vorschau, gehaltene Drohne),
   Import-Payloads mit Ratenbegrenzung und `BUILD_IMPORTED`.
2. `osd/OsdLayoutCodec` (Schema 11.1, Rundreise, unbekannte Elemente übersprungen).
3. Flugschule mit Namensraum-Szenen; Kreativ-Einträge/Presets in `ApiExtensions` (fertig) dokumentieren.
4. Beispiel-Addon (13) inklusive JSON-Dateien, Sprachdateien (`propwash_example`, de/en) und eigener Bildschirmfotos
   `api_workbench_extension.png`, `api_config_tab.png`.
5. Selbsttests `ApiSelfTests`: jede Methode von `PropwashApi`/`PropwashClientApi` einmal echt, Import-Rundreise
   (Export → `DroneData` → Import ins Werkbank-Menü → gleiche Drohne), Tuning nur Gruppen, OSD-Rundreise, Events.
6. JUnit: `TuneGroup` für jeden bekannten Schlüssel (schon da), `OsdLayoutCodec`, `Conversions`-Rundreisen.
7. Doku: `docs/addons.md` (Schritt für Schritt ein Addon mit Gradle-Abhängigkeit, Einstiegen, Java/JSON, Assets,
   Sprachdateien, Sharecode-Beispiel), FOUNDATION §15 „Parts-API“, ARCHITECTURE-Paketliste (`api`, `apiimpl`, `parts`),
   README, CHANGELOG 0.4.0, Modrinth-Text.
Abnahme: fastcheck grün, `ApiContractTest` grün, Beispiel-Addon läuft auf beiden Loadern im Selbsttest.

### 14.5 Regeln für alle
- Kein Gradle, kein Minecraft-Start: `tools/fastcheck.sh all` und `tools/fastcheck.sh test`; Selbsttests trotzdem
  schreiben (laufen in der Integration).
- Keine Kommentare (`python3 tools/strip_comments.py` vor jedem Commit), Mixins nur unter `mixin.<modul>`, Texte nur in
  Sprachdateien (de_de mit Umlauten und ß, en_us), Theme für jedes Fenster, stumme Test-Instanzen.
- APIs von Minecraft/Loadern nur aus `.ref/` bzw. `docs/api/*.md`.
- In JUnit nie echte Teile anmelden (`PartIndex.register`), nur `PartIndex.detached` — sonst zählen fremde Tests falsch.
- `api/**`-Signaturen sind eingefroren; fehlt etwas: Architektur-Wunsch an D, nur ergänzen.
- Deutsche Commit-Nachrichten, nie pushen, nie hochladen.

### 14.6 Integration
Reihenfolge beim Zusammenführen: A → B → C → D (D zuletzt, weil Doku und Beispiel-Addon alle Teile zeigen). Konflikt-
Hotspots: keine gemeinsamen Java-Dateien laut 14.2; Tages-Changelog und generierte Sprachdateien nach FOUNDATION §10.
Nach dem Zusammenführen: beide Loader bauen, Selbsttest, Start- und Server-Rauchtest, `survival.py --check`,
`world_courses.py --check`, Version 0.4.0.

---

## 15. Offene Punkte (nach 0.4.0)
- Hexa/Okto (`MotorArm.COUNT` als Definitionsfeld, Werkbank-Slots, `PartWear`).
- Eigene Videosysteme und Brillen je System; Kamera-FOV aus dem Teil ins FPV übernehmen (Spielerwahl bleibt Obergrenze).
- Definitionen per Datenpaket überschreiben (mit Sync an die Clients).
- Addon-Einträge in Propwash-Beutetabellen über einen Beute-Hook (Addon-Tabellen in Vanilla-Truhen gehen seit API 1.4,
  Abschnitt 6.6; Propwashs eigene Tabellen bleiben geschlossen).
